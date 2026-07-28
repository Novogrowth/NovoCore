package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.inventory.InvalidInventoryLotException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotNotFoundException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitNotFoundException;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitView;
import gr.novotrade.novocore.core.api.inventory.StockLevels;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.StockNotApplicableException;
import gr.novotrade.novocore.core.api.product.ProductNotFoundException;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory lots, serialized units, and the stock levels computed from them.
 *
 * <p>In the {@code product} package because lots are the same slice of the core as products — the
 * Inventory control account's sub-ledger is Product-Lot, one thing. That is also what makes this
 * possible at all without a bean cycle: a lot's validity depends on its product, and a product's last
 * purchase price comes from a lot, so both read the other's repository rather than the other's service.
 *
 * <p>For the same reason, a bundle's availability is computed here rather than in
 * {@code BundleServiceImpl}: a caller asking how many of something there are should not have to know
 * whether it is a bundle, so {@link #stockOf} answers for both and reads the component list directly.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private static final String LOT_ENTITY_TYPE = "InventoryLot";
    private static final String UNIT_ENTITY_TYPE = "SerializedUnit";

    private final ProductRepository products;
    private final InventoryLotRepository lots;
    private final SerializedUnitRepository units;
    private final BundleComponentRepository bundleComponents;
    private final AuditLogService auditLog;

    InventoryServiceImpl(ProductRepository products, InventoryLotRepository lots,
            SerializedUnitRepository units, BundleComponentRepository bundleComponents,
            AuditLogService auditLog) {
        this.products = products;
        this.lots = lots;
        this.units = units;
        this.bundleComponents = bundleComponents;
        this.auditLog = auditLog;
    }

    // ---------------------------------------------------------------------------------------
    // Stock levels (Q7)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public StockLevels stockOf(long productId) {
        return stockOf(loadProduct(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public Quantity sellableStockOf(long productId) {
        return stockOf(productId).sellable();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLevels> stockOfAll(List<Long> productIds) {
        Objects.requireNonNull(productIds, "productIds");
        if (productIds.isEmpty()) {
            return List.of();
        }
        // Products with no stock concept are absent from the result rather than represented by zero —
        // the same distinction StockNotApplicableException draws, kept when asking about many at once.
        return products.findAllById(productIds).stream()
                .filter(this::hasStockConcept)
                .map(this::stockOf)
                .toList();
    }

    private StockLevels stockOf(Product product) {
        if (product.isBundle()) {
            return bundleStock(product);
        }
        if (!product.getType().isStocked()) {
            throw StockNotApplicableException.forService(product.getId(), product.getSku());
        }
        Map<StockLocation, Quantity> byLocation = new EnumMap<>(StockLocation.class);
        if (product.isSerialTracked()) {
            // A count, not a sum: for serialized stock the quantity IS the number of units on hand.
            for (Object[] row : units.countOnHandByLocation(
                    product.getId(), SerializedUnitStatus.IN_STOCK)) {
                byLocation.put((StockLocation) row[0], Quantity.of(((Number) row[1]).longValue()));
            }
        } else {
            for (Object[] row : lots.sumRemainingByLocation(product.getId())) {
                byLocation.put((StockLocation) row[0], Quantity.of((BigDecimal) row[1]));
            }
        }
        return new StockLevels(product.getId(), byLocation);
    }

    /**
     * A bundle's availability: how many could be assembled at each location, limited by whichever
     * component runs out first.
     *
     * <p>Brief §5's "no stock of its own (computed from components)". Only <em>stocked</em> components
     * constrain it — a bundled installation service has revenue allocated to it and nothing to take off
     * a shelf — and a bundle with no stocked components is not limited by stock at all, which is why
     * that case refuses rather than answering zero.
     *
     * <p>Integer division, deliberately: half a component is not half a bundle. Two grinders and one
     * spare basket make one complete bundle, not one and a half.
     */
    private StockLevels bundleStock(Product bundle) {
        List<BundleComponent> stockedComponents =
                bundleComponents.findByBundleIdOrderByComponentSkuAsc(bundle.getId()).stream()
                        .filter(component -> hasStockConcept(component.getComponent()))
                        .toList();
        if (stockedComponents.isEmpty()) {
            throw StockNotApplicableException.forServiceOnlyBundle(bundle.getId(), bundle.getSku());
        }

        Map<StockLocation, Quantity> assemblable = new EnumMap<>(StockLocation.class);
        for (StockLocation location : StockLocation.values()) {
            Quantity limit = null;
            for (BundleComponent component : stockedComponents) {
                Quantity available = stockOf(component.getComponent()).at(location);
                Quantity possible = Quantity.of(available.value()
                        .divideToIntegralValue(component.getQuantityPerBundle().value()));
                limit = limit == null ? possible : limit.min(possible);
            }
            assemblable.put(location, limit);
        }
        return new StockLevels(bundle.getId(), assemblable);
    }

    /**
     * Whether asking this product for stock has an answer at all.
     *
     * <p>False for a service, and for a bundle assembled entirely from services. Both would otherwise
     * be answered with zero, which reads as "sold out" and is the opposite of true.
     */
    private boolean hasStockConcept(Product product) {
        if (product.isBundle()) {
            return bundleComponents.findByBundleIdOrderByComponentSkuAsc(product.getId()).stream()
                    .anyMatch(component -> hasStockConcept(component.getComponent()));
        }
        return product.getType().isStocked();
    }

    // ---------------------------------------------------------------------------------------
    // Receiving
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public InventoryLotView receive(NewInventoryLot request) {
        Objects.requireNonNull(request, "request");
        Product product = loadProduct(request.productId());

        if (!product.isActive()) {
            throw new InvalidInventoryLotException(
                    "Product '" + product.getSku() + "' is inactive, so stock cannot be received "
                            + "against it. A product is deactivated precisely so nothing new happens "
                            + "to it.");
        }
        if (product.isBundle()) {
            throw new InvalidInventoryLotException(
                    "Product '" + product.getSku() + "' is a bundle, which has no stock of its own "
                            + "(brief §5): its availability is computed from its components. Receiving "
                            + "a lot against it would count the same goods twice, once as the bundle "
                            + "and once as its parts.");
        }
        if (!product.getType().isStocked()) {
            throw new InvalidInventoryLotException(
                    "Product '" + product.getSku() + "' is a " + product.getType() + ", which has no "
                            + "inventory lots.");
        }
        if (product.isSerialTracked() != request.isSerialized()) {
            throw new InvalidInventoryLotException(product.isSerialTracked()
                    ? "Product '" + product.getSku() + "' is serial-tracked, so a receipt must state "
                            + "one serial number per unit rather than a bare quantity."
                    : "Product '" + product.getSku() + "' is pooled stock, so a receipt states a "
                            + "quantity. Serial numbers would create identities nothing tracks.");
        }

        InventoryLot lot = product.isSerialTracked()
                ? serializedLot(product, request)
                : pooledLot(product, request);
        InventoryLot saved = lots.save(lot);

        auditLog.record("inventory-lot.received", LOT_ENTITY_TYPE, String.valueOf(saved.getId()),
                Map.of(
                        "sku", product.getSku(),
                        "quantity", saved.getQuantityReceived().toString(),
                        "unitCost", saved.getUnitCost().toString(),
                        "location", request.location().name(),
                        "serialTracked", String.valueOf(product.isSerialTracked())));

        return toView(saved);
    }

    private InventoryLot pooledLot(Product product, NewInventoryLot request) {
        Quantity quantity = request.quantity();
        if (!quantity.isPositive()) {
            throw new InvalidInventoryLotException(
                    "Received quantity " + quantity + " is not positive. A lot of nothing is not a "
                            + "receipt, and a negative one is a reversal, which is a posting rather "
                            + "than a lot.");
        }
        requireExpressibleQuantity(product, quantity);
        return InventoryLot.pooled(product, quantity, request.unitCost(), request.acquisitionDate(),
                request.roastDate(), request.location());
    }

    private InventoryLot serializedLot(Product product, NewInventoryLot request) {
        // Case-insensitively, because a serial re-typed in lower case is the same machine and letting
        // it through would create a second warranty history for one unit.
        Set<String> seen = new LinkedHashSet<>();
        List<String> serials = new ArrayList<>();
        for (String raw : request.serialNumbers()) {
            if (raw == null || raw.isBlank()) {
                throw new InvalidInventoryLotException(
                        "A serial number must not be blank: a unit with no serial is exactly the "
                                + "pooled stock this product is not.");
            }
            String serial = raw.trim();
            if (!seen.add(serial.toLowerCase(Locale.ROOT))) {
                throw new InvalidInventoryLotException(
                        "Serial number '" + serial + "' appears twice in this receipt. One physical "
                                + "unit cannot arrive twice, so this is a duplicated scan.");
            }
            if (units.existsBySerialNumberIgnoreCase(serial)) {
                throw new InvalidInventoryLotException(
                        "Serial number '" + serial + "' is already held by another unit. Receiving it "
                                + "again would give one machine two records and split its history.");
            }
            serials.add(serial);
        }

        InventoryLot lot = InventoryLot.serialTracked(product, request.unitCost(),
                request.acquisitionDate(), request.roastDate());
        for (String serial : serials) {
            lot.addUnit(serial, request.location());
        }
        return lot;
    }

    /**
     * The V11 obligation, discharged: the rule is read off the unit rather than kept as a list here.
     *
     * <p>Three of something sold by the piece is three; 2.5 pieces is a data-entry error, and averaging
     * it out later is how it becomes permanent.
     */
    private static void requireExpressibleQuantity(Product product, Quantity quantity) {
        UnitOfMeasure unit = product.getUnitOfMeasure();
        if (unit.isFractionalQuantityAllowed()) {
            return;
        }
        if (quantity.value().stripTrailingZeros().scale() > 0) {
            throw new InvalidInventoryLotException(
                    "Quantity " + quantity + " has a fraction, and product '" + product.getSku()
                            + "' is measured in '" + unit.getCode() + "', which does not allow one.");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Reading lots
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryLotView> findLot(long lotId) {
        return lots.findById(lotId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryLotView requireLot(long lotId) {
        return findLot(lotId).orElseThrow(() -> new InventoryLotNotFoundException(lotId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryLotView> lotsOf(long productId) {
        return lots.findByProductIdOrderByAcquisitionDateAscIdAsc(productId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryLotView> openLotsOf(long productId) {
        return lotsOf(productId).stream().filter(InventoryLotView::isOpen).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryLotView> lotsAt(StockLocation location) {
        Objects.requireNonNull(location, "location");

        List<InventoryLot> matching = new ArrayList<>(
                lots.findByLocationOrderByAcquisitionDateAscIdAsc(location).stream()
                        // An exhausted lot is history, not stock aging at a location. The phase 8
                        // Damaged Goods check is about what is still carried at cost.
                        .filter(lot -> lot.getQuantityRemaining().isPositive())
                        .toList());

        // The serialized half: a lot appears when any of its on-hand units is there, since a
        // serial-tracked lot has no location of its own.
        Set<Long> serializedLotIds = new LinkedHashSet<>();
        for (SerializedUnit unit : units.findByStatusAndLocationOrderBySerialNumberAsc(
                SerializedUnitStatus.IN_STOCK, location)) {
            serializedLotIds.add(unit.getLot().getId());
        }
        if (!serializedLotIds.isEmpty()) {
            matching.addAll(lots.findAllById(serializedLotIds));
        }

        matching.sort(Comparator
                .comparing(InventoryLot::getAcquisitionDate)
                .thenComparing(InventoryLot::getId));
        return matching.stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UnitCost> lastPurchaseCostOf(long productId) {
        return lots.findFirstByProductIdOrderByAcquisitionDateDescIdDesc(productId)
                .map(InventoryLot::getUnitCost);
    }

    // ---------------------------------------------------------------------------------------
    // Moving stock — posts nothing, by the step 3 decision
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public InventoryLotView moveLot(long lotId, StockLocation destination) {
        Objects.requireNonNull(destination, "destination");
        InventoryLot lot = lots.findById(lotId)
                .orElseThrow(() -> new InventoryLotNotFoundException(lotId));

        if (lot.isSerialTracked()) {
            throw new InvalidInventoryLotException(
                    "Lot " + lotId + " is serial-tracked, so move its units instead: one machine going "
                            + "out for repair does not move the others, which is exactly why a "
                            + "serial-tracked lot has no location of its own.");
        }
        if (!lot.getQuantityRemaining().isPositive()) {
            throw new InvalidInventoryLotException(
                    "Lot " + lotId + " is exhausted, so there is nothing in it to move.");
        }
        if (lot.getLocation() == destination) {
            return toView(lot);
        }

        StockLocation previous = lot.getLocation();
        lot.moveTo(destination);

        auditLog.record("inventory-lot.moved", LOT_ENTITY_TYPE, String.valueOf(lotId), Map.of(
                "sku", lot.getProduct().getSku(),
                "quantity", lot.getQuantityRemaining().toString(),
                "from", previous.name(),
                "to", destination.name()));

        return toView(lot);
    }

    @Override
    @Transactional
    public SerializedUnitView moveUnit(long unitId, StockLocation destination) {
        Objects.requireNonNull(destination, "destination");
        SerializedUnit unit = units.findById(unitId)
                .orElseThrow(() -> new SerializedUnitNotFoundException(unitId));

        if (!unit.isOnHand()) {
            throw new InvalidInventoryLotException(
                    "Unit '" + unit.getSerialNumber() + "' is " + unit.getStatus() + ", so it is not "
                            + "ours to move.");
        }
        if (unit.getLocation() == destination) {
            return toView(unit);
        }

        StockLocation previous = unit.getLocation();
        unit.moveTo(destination);

        auditLog.record("serialized-unit.moved", UNIT_ENTITY_TYPE, String.valueOf(unitId), Map.of(
                "serialNumber", unit.getSerialNumber(),
                "from", previous.name(),
                "to", destination.name()));

        return toView(unit);
    }

    // ---------------------------------------------------------------------------------------
    // Serialized units
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SerializedUnitView> unitsOf(long lotId) {
        return units.findByLotIdOrderBySerialNumberAsc(lotId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SerializedUnitView> unitsOfProduct(long productId) {
        return units.findByLotProductIdOrderBySerialNumberAsc(productId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SerializedUnitView> findUnitBySerialNumber(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            // A blank scan matches nothing, for the reason findByEan gives: matching the first unit
            // without one would turn a misread into a confidently wrong machine.
            return Optional.empty();
        }
        return units.findBySerialNumberIgnoreCase(serialNumber.trim()).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public SerializedUnitView requireUnit(long unitId) {
        return units.findById(unitId).map(this::toView)
                .orElseThrow(() -> new SerializedUnitNotFoundException(unitId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SerializedUnitView> unitsAt(StockLocation location) {
        Objects.requireNonNull(location, "location");
        return units.findByStatusAndLocationOrderBySerialNumberAsc(
                        SerializedUnitStatus.IN_STOCK, location).stream()
                .map(this::toView)
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private Product loadProduct(long productId) {
        return products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private InventoryLotView toView(InventoryLot lot) {
        Product product = lot.getProduct();
        List<SerializedUnitView> unitViews = lot.isSerialTracked()
                ? lot.getUnits().stream().map(this::toView).toList()
                : List.of();
        return new InventoryLotView(
                lot.getId(),
                product.getId(),
                product.getSku(),
                lot.isSerialTracked(),
                lot.getQuantityReceived(),
                lot.getQuantityRemaining(),
                lot.getUnitCost(),
                lot.getAcquisitionDate(),
                lot.getRoastDate(),
                lot.getLocation(),
                unitViews);
    }

    private SerializedUnitView toView(SerializedUnit unit) {
        InventoryLot lot = unit.getLot();
        return new SerializedUnitView(
                unit.getId(),
                lot.getId(),
                lot.getProduct().getId(),
                unit.getSerialNumber(),
                unit.getStatus(),
                unit.getLocation(),
                // A serialized write-off or sale uses this unit's own actual cost, not FIFO (brief §5),
                // so the figure travels with the unit rather than being fetched again later.
                lot.getUnitCost());
    }
}
