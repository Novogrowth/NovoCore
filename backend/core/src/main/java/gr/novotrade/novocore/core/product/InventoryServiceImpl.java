package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.inventory.InvalidInventoryLotException;
import gr.novotrade.novocore.core.api.inventory.InvalidStockConsumptionException;
import gr.novotrade.novocore.core.api.inventory.InvalidStockWriteOffException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotNotFoundException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.NewStockConsumption;
import gr.novotrade.novocore.core.api.inventory.NewStockWriteOff;
import gr.novotrade.novocore.core.api.inventory.SaleReference;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitNotFoundException;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitView;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionLineView;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionNotFoundException;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionView;
import gr.novotrade.novocore.core.api.inventory.StockLevels;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.StockNotApplicableException;
import gr.novotrade.novocore.core.api.inventory.StockWriteOffNotFoundException;
import gr.novotrade.novocore.core.api.inventory.StockWriteOffView;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.product.ProductNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
    private static final String WRITE_OFF_ENTITY_TYPE = "StockWriteOff";
    private static final String CONSUMPTION_ENTITY_TYPE = "StockConsumption";

    private final ProductRepository products;
    private final InventoryLotRepository lots;
    private final SerializedUnitRepository units;
    private final BundleComponentRepository bundleComponents;
    private final StockWriteOffRepository writeOffs;
    private final StockConsumptionRepository consumptions;
    private final StockConsumptionLineRepository consumptionLines;
    private final ChartOfAccountsService chartOfAccounts;
    private final JournalService journal;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    InventoryServiceImpl(ProductRepository products, InventoryLotRepository lots,
            SerializedUnitRepository units, BundleComponentRepository bundleComponents,
            StockWriteOffRepository writeOffs, StockConsumptionRepository consumptions,
            StockConsumptionLineRepository consumptionLines, ChartOfAccountsService chartOfAccounts,
            JournalService journal, SettingsService settings, AuditLogService auditLog) {
        this.products = products;
        this.lots = lots;
        this.units = units;
        this.bundleComponents = bundleComponents;
        this.writeOffs = writeOffs;
        this.consumptions = consumptions;
        this.consumptionLines = consumptionLines;
        this.chartOfAccounts = chartOfAccounts;
        this.journal = journal;
        this.settings = settings;
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
        applyShortfall(byLocation, consumptions.outstandingShortfallOf(product.getId()));
        return new StockLevels(product.getId(), byLocation);
    }

    /**
     * Q17, made visible in the figure itself (ADR 0008).
     *
     * <p>What FIFO could not fill is subtracted from the sellable location, so a product that has sold
     * two more than it ever received reads −2 rather than 0. Reading zero would be the same failure as
     * answering zero for a service: technically a number, and the opposite of informative.
     *
     * <p>Subtracted from the <em>sellable</em> location because that is where the goods would have
     * been: a shortfall arises from a sale, and a sale takes stock off the shelf. If a second sellable
     * location is ever added this needs a rule for splitting between them, which is why it reads the
     * set rather than naming {@code INVENTORY} — the failure is then loud rather than silent.
     */
    private static void applyShortfall(
            Map<StockLocation, Quantity> byLocation, BigDecimal shortfall) {
        if (shortfall == null || shortfall.signum() <= 0) {
            return;
        }
        Set<StockLocation> sellable = StockLocation.sellableLocations();
        if (sellable.size() != 1) {
            throw new IllegalStateException(
                    "There are now " + sellable.size() + " sellable locations, so a stock shortfall "
                            + "no longer has one obvious place to be subtracted from. Decide how a "
                            + "consumption's shortfall splits between them rather than letting this "
                            + "pick the first.");
        }
        StockLocation shelf = sellable.iterator().next();
        Quantity present = byLocation.getOrDefault(shelf, Quantity.ZERO);
        byLocation.put(shelf, present.minus(Quantity.of(shortfall)));
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
                        "receivedUnitCost", saved.getReceivedUnitCost().toString(),
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
                request.roastDate(), request.location(), request.goodsReceiptLineId());
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
            // UNRECEIVED units are excluded, and the index behind this is partial for the same reason:
            // a reversed delivery releases its serials, because the commonest reason to reverse one is
            // that it was entered wrong and re-entering it correctly must not be blocked.
            if (units.existsBySerialNumberIgnoreCaseAndStatusNot(
                    serial, SerializedUnitStatus.UNRECEIVED)) {
                throw new InvalidInventoryLotException(
                        "Serial number '" + serial + "' is already held by another unit. Receiving it "
                                + "again would give one machine two records and split its history.");
            }
            serials.add(serial);
        }

        InventoryLot lot = InventoryLot.serialTracked(product, request.unitCost(),
                request.acquisitionDate(), request.roastDate(), request.goodsReceiptLineId());
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
        // The RECEIVED cost, not what the lot is carried at — the step 6 obligation, discharged in
        // step 10. Brief §5 puts allocated landed costs into a lot's carrying cost, so the moment
        // freight could be allocated the carrying figure stopped being a purchase price: a product
        // whose last delivery came by air freight would have read as though the supplier had raised
        // their price.
        return lots.findFirstByProductIdOrderByAcquisitionDateDescIdDesc(productId)
                .map(InventoryLot::getReceivedUnitCost);
    }

    // ---------------------------------------------------------------------------------------
    // Landed costs — Q18, answered (ADR 0010)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public InventoryLotView applyLandedCost(long lotId, UnitCost perUnit) {
        Objects.requireNonNull(perUnit, "perUnit");
        InventoryLot lot = lots.findById(lotId)
                .orElseThrow(() -> new InventoryLotNotFoundException(lotId));
        requireSameCurrencyAsLot(lot, perUnit);

        if (perUnit.isZero()) {
            // Refused rather than accepted as a no-op: an allocation line that moves nothing is
            // either a share too small to reach a unit or a caller that has not worked out its
            // proportions, and the second one should not look like success.
            throw new InvalidInventoryLotException(
                    "Nothing to allocate onto lot " + lotId + ": the per-unit landed cost is zero. A "
                            + "share too small to raise a unit's cost belongs entirely in landed cost "
                            + "variance rather than in a lot that is not going to change.");
        }

        UnitCost before = lot.getUnitCost();
        lot.applyLandedCost(perUnit);

        auditLog.record("inventory-lot.landed-cost-applied", LOT_ENTITY_TYPE, String.valueOf(lotId),
                Map.of(
                        "sku", lot.getProduct().getSku(),
                        "perUnit", perUnit.toString(),
                        "receivedUnitCost", lot.getReceivedUnitCost().toString(),
                        "carriedBefore", before.toString(),
                        "carriedAfter", lot.getUnitCost().toString()));

        return toView(lot);
    }

    @Override
    @Transactional
    public InventoryLotView removeLandedCost(long lotId, UnitCost perUnit) {
        Objects.requireNonNull(perUnit, "perUnit");
        InventoryLot lot = lots.findById(lotId)
                .orElseThrow(() -> new InventoryLotNotFoundException(lotId));
        requireSameCurrencyAsLot(lot, perUnit);

        if (lot.getAllocatedLandedUnitCost().compareTo(perUnit) < 0) {
            // Named here rather than left to UnitCost's own refusal, which would say only that a unit
            // cost cannot be negative and not which lot or which allocation.
            throw new InvalidInventoryLotException(
                    "Lot " + lotId + " carries " + lot.getAllocatedLandedUnitCost() + " of allocated "
                            + "landed cost, so " + perUnit + " cannot be taken back off it: the lot "
                            + "would be carried below what was paid for the goods. Reaching here means "
                            + "an allocation is being reversed twice, or one that was never applied.");
        }

        UnitCost before = lot.getUnitCost();
        lot.removeLandedCost(perUnit);

        auditLog.record("inventory-lot.landed-cost-removed", LOT_ENTITY_TYPE, String.valueOf(lotId),
                Map.of(
                        "sku", lot.getProduct().getSku(),
                        "perUnit", perUnit.toString(),
                        "carriedBefore", before.toString(),
                        "carriedAfter", lot.getUnitCost().toString()));

        return toView(lot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryLotView> lotsWithAllocatedLandedCost() {
        return lots.findWithAllocatedLandedCost().stream().map(this::toView).toList();
    }

    private static void requireSameCurrencyAsLot(InventoryLot lot, UnitCost perUnit) {
        UnitCost received = lot.getReceivedUnitCost();
        if (!received.currency().equals(perUnit.currency())) {
            throw new InvalidInventoryLotException(
                    "Lot " + lot.getId() + " was received in "
                            + received.currency().getCurrencyCode() + " and this landed cost is in "
                            + perUnit.currency().getCurrencyCode() + ". NovoCore does not convert "
                            + "between currencies and will not silently pick one (ADR 0005).");
        }
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

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryLotView> findLotByReceiptLine(long goodsReceiptLineId) {
        return lots.findByGoodsReceiptLineId(goodsReceiptLineId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryLotView> lotsFromReceiptLines(Collection<Long> goodsReceiptLineIds) {
        Objects.requireNonNull(goodsReceiptLineIds, "goodsReceiptLineIds");
        if (goodsReceiptLineIds.isEmpty()) {
            return List.of();
        }
        return lots.findByGoodsReceiptLineIdIn(goodsReceiptLineIds).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional
    public void unreceive(long lotId) {
        InventoryLot lot = lots.findById(lotId)
                .orElseThrow(() -> new InventoryLotNotFoundException(lotId));

        // Refused, not partially undone (ADR 0008). If any of it has moved, the goods went somewhere,
        // and a document claiming the delivery never happened would be a lie about physical stock. The
        // refusal names what got in the way, because "reverse the receipt" and "write off what is
        // left" are different corrections and the operator has to know which one they are being
        // pushed towards.
        if (writeOffs.findByLotIdOrderByIdAsc(lotId).stream().anyMatch(w -> !w.isReversal())) {
            throw new InvalidInventoryLotException(
                    "Lot " + lotId + " has been written off, so the delivery that created it cannot "
                            + "be un-made: a loss has already been recognised against stock this "
                            + "would claim never arrived. Reverse the write-off first, or record a "
                            + "supplier return instead.");
        }
        if (consumptionLines.existsByLotId(lotId)) {
            throw new InvalidInventoryLotException(
                    "Lot " + lotId + " has been consumed, so the delivery that created it cannot be "
                            + "un-made: the cost is already inside posted COGS. Reverse the "
                            + "consumption first, or record a supplier return, which says what "
                            + "actually happened.");
        }

        if (lot.isSerialTracked()) {
            for (SerializedUnit unit : lot.getUnits()) {
                if (!unit.isOnHand()) {
                    throw new InvalidInventoryLotException(
                            "Unit '" + unit.getSerialNumber() + "' of lot " + lotId + " is "
                                    + unit.getStatus() + ", so this delivery cannot be un-made — that "
                                    + "machine has moved on and something would have to account for "
                                    + "where it went.");
                }
                if (unit.getLocation() != StockLocation.INVENTORY) {
                    throw new InvalidInventoryLotException(
                            "Unit '" + unit.getSerialNumber() + "' of lot " + lotId + " has been "
                                    + "moved to " + unit.getLocation() + " since it arrived. Somebody "
                                    + "has handled it, so the honest correction is a write-off or a "
                                    + "supplier return rather than un-making the delivery.");
                }
                unit.unreceive();
            }
        } else {
            if (lot.getQuantityRemaining().compareTo(lot.getQuantityReceived()) != 0) {
                throw new InvalidInventoryLotException(
                        "Lot " + lotId + " received " + lot.getQuantityReceived() + " and has "
                                + lot.getQuantityRemaining() + " left, so some of it has already gone "
                                + "somewhere. A delivery that has been partly dispersed cannot be "
                                + "un-made.");
            }
            lot.consume(lot.getQuantityRemaining());
        }

        auditLog.record("inventory-lot.unreceived", LOT_ENTITY_TYPE, String.valueOf(lotId), Map.of(
                "sku", lot.getProduct().getSku(),
                "quantity", lot.getQuantityReceived().toString(),
                "serialTracked", String.valueOf(lot.isSerialTracked())));
    }

    // ---------------------------------------------------------------------------------------
    // FIFO consumption — Q17, answered (ADR 0008)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public StockConsumptionView consume(NewStockConsumption request) {
        Objects.requireNonNull(request, "request");
        Product product = loadProduct(request.productId());
        requireConsumable(product, request.quantity());
        if (product.isSerialTracked() != request.isSerialized()) {
            throw new InvalidStockConsumptionException(product.isSerialTracked()
                    ? "Product '" + product.getSku() + "' is serial-tracked, so a sale must name the "
                            + "units that left the shelf. FIFO exists for stock that cannot be told "
                            + "apart, and these can — brief §5 costs a serialized item at its own "
                            + "actual cost."
                    : "Product '" + product.getSku() + "' is pooled, so a sale states a quantity and "
                            + "FIFO chooses the lots. Serial numbers would name units that do not "
                            + "exist.");
        }
        if (request.isSerialized()) {
            return consumeUnits(product, request);
        }

        // FIFO's candidate list, in the one order lotsOf already defines — acquisition date, then id —
        // narrowed to lots whose stock is actually sellable. Damaged Goods is still an asset, and
        // quietly selling it is not a decision a costing rule gets to make.
        List<InventoryLot> candidates =
                lots.findByProductIdOrderByAcquisitionDateAscIdAsc(product.getId()).stream()
                        .filter(lot -> lot.getQuantityRemaining().isPositive())
                        .filter(lot -> lot.getLocation() != null
                                && StockLocation.sellableLocations().contains(lot.getLocation()))
                        .toList();

        Quantity outstanding = request.quantity();
        Map<InventoryLot, Quantity> taken = new LinkedHashMap<>();
        for (InventoryLot lot : candidates) {
            if (!outstanding.isPositive()) {
                break;
            }
            Quantity fromThisLot = outstanding.min(lot.getQuantityRemaining());
            taken.put(lot, fromThisLot);
            outstanding = outstanding.minus(fromThisLot);
        }

        Quantity filled = request.quantity().minus(outstanding);
        StockConsumption record = new StockConsumption(product, request.quantity(), filled,
                request.consumptionDate(), request.source(), request.note(), null);
        taken.forEach((lot, quantity) -> record.addLine(lot, quantity, lot.getUnitCost()));

        // Post before the stock moves, for writeOff's reason: the transaction guarantees both or
        // neither, and this order means a rejected posting never leaves a half-applied request behind.
        record.postedAs(postConsumption(record, product, request.consumptionDate(), request.source()));
        taken.forEach(InventoryLot::consume);

        StockConsumption saved = consumptions.save(record);

        auditLog.record("stock-consumption.recorded", CONSUMPTION_ENTITY_TYPE,
                String.valueOf(saved.getId()), consumptionDetail(saved));
        return toView(saved, entriesOf(List.of(saved)));
    }

    /**
     * Selling named machines — <strong>the step 6 obligation, discharged</strong>.
     *
     * <p>No FIFO, deliberately: brief §5 says a serialized item is costed at its own actual cost,
     * because FIFO exists for units that cannot be told apart and these can. So the lots are whichever
     * lots the named units happen to belong to, and each unit's cost is its lot's.
     *
     * <p><strong>No shortfall either, and that is not an inconsistency with Q17.</strong> Aggregate
     * stock can go negative because "how many are there" is a number that can be wrong; "is machine
     * 1234 on the shelf" is not. A unit that is not on hand is refused rather than flagged, because
     * there is nothing a later Goods Receipt could arrive to back it with.
     */
    private StockConsumptionView consumeUnits(Product product, NewStockConsumption request) {
        SaleReference sale = request.sale().orElseThrow();

        List<SerializedUnit> selling = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : request.serialNumbers()) {
            String serial = raw == null ? "" : raw.trim();
            if (serial.isEmpty()) {
                throw new InvalidStockConsumptionException(
                        "A serial number on this sale is blank. A unit with no serial is the pooled "
                                + "stock this product is not.");
            }
            if (!seen.add(serial.toLowerCase(Locale.ROOT))) {
                throw new InvalidStockConsumptionException(
                        "Serial number '" + serial + "' appears twice on this sale. One physical "
                                + "machine cannot be sold twice on one line.");
            }
            SerializedUnit unit = units.findBySerialNumberIgnoreCaseAndStatusNot(
                            serial, SerializedUnitStatus.UNRECEIVED)
                    .orElseThrow(() -> SerializedUnitNotFoundException.forSerialNumber(serial));

            if (!Objects.equals(unit.getLot().getProduct().getId(), product.getId())) {
                throw new InvalidStockConsumptionException(
                        "Unit '" + serial + "' is a '" + unit.getLot().getProduct().getSku()
                                + "', not a '" + product.getSku() + "'. Selling it on this line would "
                                + "take the cost out of the wrong product's stock.");
            }
            if (!unit.isOnHand()) {
                throw new InvalidStockConsumptionException(
                        "Unit '" + serial + "' is " + unit.getStatus() + ", so it is not ours to "
                                + "sell. A named machine either is on the shelf or is not — unlike a "
                                + "pooled quantity, there is no aggregate for it to go negative in "
                                + "and nothing a later delivery could arrive to back it with.");
            }
            if (!StockLocation.sellableLocations().contains(unit.getLocation())) {
                throw new InvalidStockConsumptionException(
                        "Unit '" + serial + "' is at " + unit.getLocation() + ", which is not stock "
                                + "that may be sold. Selling out of Damaged Goods is not a decision a "
                                + "costing rule gets to make quietly — move it back first if it is "
                                + "genuinely saleable.");
            }
            selling.add(unit);
        }

        // One line per lot, brief §6's shape, even though the units were named individually: two
        // machines from one delivery are one lot's cost coming out.
        Map<InventoryLot, Quantity> taken = new LinkedHashMap<>();
        for (SerializedUnit unit : selling) {
            taken.merge(unit.getLot(), Quantity.of(1), Quantity::plus);
        }

        StockConsumption record = new StockConsumption(product, request.quantity(),
                request.quantity(), request.consumptionDate(), request.source(), request.note(), null);
        taken.forEach((lot, quantity) -> record.addLine(lot, quantity, lot.getUnitCost()));

        record.postedAs(postConsumption(record, product, request.consumptionDate(), request.source()));
        // Brief §5's customer/invoice link, set with the status so there is no window in which a unit
        // is SOLD to nobody — which is the state step 6 refused to make reachable.
        selling.forEach(unit -> unit.sell(sale.customerId(), sale.salesInvoiceLineId()));

        StockConsumption saved = consumptions.save(record);

        Map<String, String> detail = new LinkedHashMap<>(consumptionDetail(saved));
        detail.put("units", String.join(", ", request.serialNumbers()));
        detail.put("soldToCustomer", String.valueOf(sale.customerId()));
        detail.put("salesInvoiceLine", String.valueOf(sale.salesInvoiceLineId()));
        auditLog.record("stock-consumption.recorded", CONSUMPTION_ENTITY_TYPE,
                String.valueOf(saved.getId()), detail);

        return toView(saved, entriesOf(List.of(saved)));
    }

    @Override
    @Transactional
    public StockConsumptionView returnConsumed(
            long consumptionId, Quantity quantity, LocalDate returnDate, String note) {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(returnDate, "returnDate");
        StockConsumption original = loadConsumption(consumptionId);

        if (!quantity.isPositive()) {
            throw new InvalidStockConsumptionException(
                    "Returned quantity " + quantity + " is not positive. Taking more stock out is a "
                            + "consumption, not a negative return.");
        }
        if (!original.isOutbound()) {
            throw new InvalidStockConsumptionException(
                    "Consumption " + consumptionId + " is itself a "
                            + (original.isReversal() ? "reversal" : "return")
                            + ", so nothing was sold out of it to come back.");
        }
        consumptions.findByReversalOfId(consumptionId).ifPresent(existing -> {
            throw new InvalidStockConsumptionException(
                    "Consumption " + consumptionId + " was reversed by consumption "
                            + existing.getId() + ", so as far as the ledger is concerned it never "
                            + "happened. There is nothing to return.");
        });

        Quantity alreadyReturned = returnedQuantityOf(consumptionId);
        Quantity remaining = original.getQuantityFilled().minus(alreadyReturned);
        if (quantity.compareTo(remaining) > 0) {
            throw new InvalidStockConsumptionException(
                    "Consumption " + consumptionId + " took " + original.getQuantityFilled()
                            + " out of stock and " + alreadyReturned + " has already come back, so "
                            + quantity + " cannot. Returning more than was sold would create stock "
                            + "out of a credit note.");
        }

        // Reverse of the order FIFO took them: the last lot reached into gives its quantity back
        // first, so a partial return leaves the queue in the state a smaller sale would have.
        List<StockConsumptionLine> takenLast = new ArrayList<>(original.getLines());
        Collections.reverse(takenLast);

        StockConsumption returned = new StockConsumption(original.getProduct(), quantity, quantity,
                returnDate, original.getSource(), note, null);
        returned.returns(consumptionId);

        Quantity outstanding = quantity;
        for (StockConsumptionLine line : takenLast) {
            if (!outstanding.isPositive()) {
                break;
            }
            InventoryLot lot = line.getLot();
            Quantity alreadyBack = returnedIntoLot(consumptionId, lot.getId());
            Quantity availableFromThisLine = line.getQuantity().minus(alreadyBack);
            if (!availableFromThisLine.isPositive()) {
                continue;
            }
            Quantity intoThisLot = outstanding.min(availableFromThisLine);
            if (lot.getQuantityRemaining().plus(intoThisLot)
                    .compareTo(lot.getQuantityReceived()) > 0) {
                throw new InvalidStockConsumptionException(
                        "Restoring " + intoThisLot + " to lot " + lot.getId() + " would leave more in "
                                + "it than it ever received. Something has happened to the lot since, "
                                + "so the honest record is a new receipt rather than a return.");
            }
            if (!lot.isSerialTracked()) {
                // A serial-tracked lot stores no quantity — the quantity IS the count of its on-hand
                // units (V12's rule: location lives wherever the quantity does). So the stock comes
                // back by the units changing status, which restoreSerializedUnits does below;
                // restoring a number here would be a second copy of what the units already say.
                lot.restore(intoThisLot);
            }
            // The cost the stock LEFT at, off the consumption's own line — never off the lot as it
            // stands now, which step 10 will move when freight is allocated.
            returned.addLine(lot, intoThisLot, line.getUnitCost());
            outstanding = outstanding.minus(intoThisLot);
        }

        if (outstanding.isPositive()) {
            // Reachable only if the consumption filled less than it claimed, which the schema refuses.
            throw new IllegalStateException(
                    "Consumption " + consumptionId + " could not give back " + outstanding
                            + ": its lines account for less than it says it filled.");
        }

        returned.postedAs(postReturn(returned, original.getProduct(), returnDate,
                original.getSource(), consumptionId));

        // Serialized units come back on hand and stop being sold to anybody — brief §5 puts that link
        // on a SOLD unit, and a machine on the shelf is not one.
        if (original.getProduct().isSerialTracked()) {
            restoreSerializedUnits(original, quantity);
        }

        StockConsumption saved = consumptions.save(returned);

        auditLog.record("stock-consumption.returned", CONSUMPTION_ENTITY_TYPE,
                String.valueOf(consumptionId), Map.of(
                        "returnedBy", String.valueOf(saved.getId()),
                        "quantity", quantity.toString(),
                        "previouslyReturned", alreadyReturned.toString(),
                        "returnDate", returnDate.toString(),
                        "posted", String.valueOf(saved.getJournalEntryId() != null)));

        return toView(saved, entriesOf(List.of(saved)));
    }

    @Override
    @Transactional(readOnly = true)
    public Quantity returnedQuantityOf(long consumptionId) {
        return Quantity.of(consumptions.returnedQuantityOf(consumptionId));
    }

    /** How much of one lot a given consumption has already had returned into it. */
    private Quantity returnedIntoLot(long consumptionId, long lotId) {
        Quantity total = Quantity.ZERO;
        for (StockConsumption back
                : consumptions.findByReturnsConsumptionIdOrderByIdAsc(consumptionId)) {
            for (StockConsumptionLine line : back.getLines()) {
                if (Objects.equals(line.getLot().getId(), lotId)) {
                    total = total.plus(line.getQuantity());
                }
            }
        }
        return total;
    }

    /**
     * Puts the named machines back on the shelf, most recently sold first.
     *
     * <p>Which units come back is not stated by the credit note today — it names a quantity of a line
     * — so the newest sale of the fewest units is undone. That is the same shape as the pooled case
     * restoring the last lot first, and it is right for the reason that matters: every unit on one
     * line is the same product at the same lot cost, so the accounting is identical whichever machine
     * physically returns, and the serial the customer actually brought back is on the credit note the
     * shop wrote.
     */
    private void restoreSerializedUnits(StockConsumption original, Quantity quantity) {
        List<SerializedUnit> sold = units.findByLotProductIdOrderBySerialNumberAsc(
                        original.getProduct().getId()).stream()
                .filter(unit -> unit.getStatus() == SerializedUnitStatus.SOLD)
                .filter(unit -> original.getLines().stream()
                        .anyMatch(line -> Objects.equals(
                                line.getLot().getId(), unit.getLot().getId())))
                .toList();

        int wanted = quantity.value().intValueExact();
        if (sold.size() < wanted) {
            throw new InvalidStockConsumptionException(
                    "This return covers " + wanted + " units of '" + original.getProduct().getSku()
                            + "' but only " + sold.size() + " of the lots it came out of are still "
                            + "recorded as sold. A machine cannot come back twice.");
        }
        sold.subList(0, wanted).forEach(SerializedUnit::returnFromCustomer);
    }

    /**
     * Posts the cost back: debit {@code INVENTORY}, credit {@code COST_OF_GOODS_SOLD}, one line per
     * lot, at the cost the stock left at — <strong>plus the landed-cost catch-up, ADR 0011</strong>.
     *
     * <p>An ordinary entry rather than a mirror, because a return is not a reversal — see
     * {@code InventoryService.returnConsumed}. Nothing is posted when every lot involved was carried
     * at zero, exactly as nothing was posted on the way out.
     *
     * <p><strong>The catch-up is what keeps Inventory equal to what the lots carry.</strong> Stock
     * comes back at the cost it left at, which is right and is what stops a credit note revaluing
     * stock (ADR 0009) — but if a freight allocation landed while it was out, the lot now carries
     * those units higher than the figure being debited back, and the difference is exactly the share
     * that allocation wrote off to {@code LANDED_COST_VARIANCE} on the grounds that they had gone.
     * They came back, so it comes back: debit Inventory, credit that account. Cost of goods sold is
     * still credited precisely what was debited, so nothing about the sale is restated.
     */
    private Long postReturn(StockConsumption record, Product product, LocalDate date,
            JournalSource source, long originalConsumptionId) {
        RoundingMode roundingMode = settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE);
        AccountView cogs = chartOfAccounts.requireAccount(AccountSystemKey.COST_OF_GOODS_SOLD);
        AccountView inventory = chartOfAccounts.requireAccount(AccountSystemKey.INVENTORY);
        AccountView landedCostVariance =
                chartOfAccounts.requireAccount(AccountSystemKey.LANDED_COST_VARIANCE);

        List<NewJournalLine> lines = new ArrayList<>();
        for (StockConsumptionLine line : record.getLines()) {
            SubLedgerRef lotRef = SubLedgerRef.inventoryLot(line.getLot().getId());

            Money cost = line.getUnitCost().extend(line.getQuantity(), roundingMode);
            if (cost.isPositive()) {
                lines.add(NewJournalLine.debit(inventory.id(), cost).forSubLedger(lotRef));
                lines.add(NewJournalLine.credit(cogs.id(), cost)
                        .forSubLedger(lotRef)
                        .describedAs(line.getQuantity() + " x " + product.getSku() + " returned"));
            }

            Money catchUp = landedCostCatchUp(line, roundingMode);
            if (catchUp.isPositive()) {
                lines.add(NewJournalLine.debit(inventory.id(), catchUp).forSubLedger(lotRef));
                lines.add(NewJournalLine.credit(landedCostVariance.id(), catchUp)
                        .forSubLedger(lotRef)
                        .describedAs("Landed cost on returned stock — " + product.getSku()
                                + " (lot " + line.getLot().getId() + ")"));
            }
        }
        if (lines.isEmpty()) {
            return null;
        }

        return journal.post(NewJournalEntry.of(date,
                "Stock returned — " + record.getQuantityFilled() + " x " + product.getSku()
                        + " (against consumption " + originalConsumptionId + ")",
                source, lines)).id();
    }

    /**
     * The freight a returning unit did not bear, and now does — <strong>ADR 0011</strong>.
     *
     * <p>The stock left carrying {@code line.getUnitCost()}, which is the lot's received cost plus
     * whatever had been allocated onto it by then. The received half never changes (ADR 0010), so
     * subtracting it recovers exactly what had been allocated at that moment, and the difference
     * against what is allocated now is the catch-up. Zero whenever nothing has been allocated since,
     * which is the ordinary case.
     */
    private Money landedCostCatchUp(StockConsumptionLine line, RoundingMode roundingMode) {
        InventoryLot lot = line.getLot();
        UnitCost received = lot.getReceivedUnitCost();
        UnitCost leftAt = line.getUnitCost();
        if (leftAt.compareTo(received) < 0) {
            throw new IllegalStateException(
                    "Lot " + lot.getId() + " was received at " + received + " and stock left it at "
                            + leftAt + ", which is less. A lot is carried at its received cost plus "
                            + "allocated landed costs and the received half never changes, so this "
                            + "cannot happen — reaching here means something wrote it.");
        }
        UnitCost allocatedWhenItLeft = leftAt.minus(received);
        UnitCost allocatedNow = lot.getAllocatedLandedUnitCost();
        if (allocatedNow.compareTo(allocatedWhenItLeft) < 0) {
            // Unreachable: un-allocating requires the lot's remaining quantity to be what it was at
            // allocation time, and stock that is out of the lot awaiting return has changed it.
            throw new IllegalStateException(
                    "Lot " + lot.getId() + " carried " + allocatedWhenItLeft + " of allocated landed "
                            + "cost when this stock left and carries " + allocatedNow + " now, which "
                            + "is less. A freight allocation cannot be reversed while stock it costed "
                            + "is out of the lot, so reaching here means that guard was bypassed.");
        }
        return allocatedNow.minus(allocatedWhenItLeft).extend(line.getQuantity(), roundingMode);
    }

    /**
     * Refuses to un-make a stock movement into a lot that has been re-costed since — ADR 0011.
     *
     * <p>A reversal says the movement never happened. If that is true then the stock was in the lot
     * all along, so a freight allocation that has since split its share on the basis that those units
     * had gone did not merely post its counterpart to the wrong account — it computed the wrong split.
     * Patching the difference would leave a posted allocation stating something untrue about which
     * stock was where, which is ADR 0008's objection exactly.
     *
     * <p>The remedy is exact rather than a dead end, so the message names it: reverse the allocation
     * first (permitted, because the lot's remaining quantity has not moved since <em>it</em> posted),
     * then reverse this, then allocate again — which capitalises the whole share, correctly.
     *
     * <p>A <em>return</em> takes the other path deliberately: it says the sale was real, so the
     * allocation's split was right at the time, and the catch-up is all that is owed.
     */
    private static boolean hasBeenRecostedSince(InventoryLot lot, UnitCost costWhenItLeft) {
        return lot.getUnitCost().compareTo(costWhenItLeft) != 0;
    }

    /** The message both refusals share. Two exception types, one rule, stated once. */
    private static String recostedRefusal(InventoryLot lot, UnitCost costWhenItLeft, String what) {
        return "Lot " + lot.getId() + " was carried at " + costWhenItLeft + " when this " + what
                + " happened and is carried at " + lot.getUnitCost() + " now, because a landed cost "
                + "has been allocated onto it since. Un-making the movement would say those units "
                + "were in the lot all along, which is not what that allocation was computed against "
                + "— so it would leave the allocation stating something untrue rather than merely a "
                + "figure to patch. Reverse the freight allocation first (its own guard permits that, "
                + "since the lot's quantity has not moved since it posted), then reverse this, then "
                + "allocate the freight again.";
    }

    @Override
    @Transactional
    public StockConsumptionView reverseConsumption(
            long consumptionId, LocalDate reversalDate, String note) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        StockConsumption original = loadConsumption(consumptionId);

        if (original.isReversal()) {
            throw new InvalidStockConsumptionException(
                    "Consumption " + consumptionId + " is itself the reversal of consumption "
                            + original.getReversalOfId() + ". Reversing it would take the stock out "
                            + "again while claiming to be a correction; record a new consumption "
                            + "instead, so the ledger says what actually happened.");
        }
        if (original.isReturn()) {
            // Refused rather than handled, because this method restores quantities and a return row
            // is stock that came IN — reversing it would have to take stock out, which is the exact
            // opposite of every line below. A return also reflects goods physically arriving back on
            // a shelf, which ADR 0008's principle says is not un-made after other things depend on it.
            throw new InvalidStockConsumptionException(
                    "Consumption " + consumptionId + " is a return against consumption "
                            + original.getReturnsConsumptionId() + ", not stock leaving. Goods that "
                            + "came back are physically on a shelf; if they have gone out again, that "
                            + "is a new sale rather than the un-making of a return.");
        }
        consumptions.findByReversalOfId(consumptionId).ifPresent(existing -> {
            throw new InvalidStockConsumptionException(
                    "Consumption " + consumptionId + " has already been reversed by consumption "
                            + existing.getId() + ". Reversing it twice would put the stock back twice "
                            + "and credit the cost twice, with both halves looking correct.");
        });
        if (!original.getQuantityFilled().isPositive()) {
            // A consumption that filled nothing restored nothing, so there is no stock to put back and
            // no cost to credit. Saying so is more honest than writing a row of zeroes, which the
            // requested-quantity CHECK would refuse anyway.
            throw new InvalidStockConsumptionException(
                    "Consumption " + consumptionId + " filled nothing — its whole quantity is an "
                            + "unbacked shortfall — so there is no stock to put back and no cost to "
                            + "credit. Nothing needs reversing; what it is waiting for is the Goods "
                            + "Receipt that should have preceded it.");
        }

        for (StockConsumptionLine line : original.getLines()) {
            InventoryLot lot = line.getLot();
            // ADR 0011. The mirror this is about to post credits COGS and debits Inventory at the
            // cost the stock left at; if the lot has been re-costed since, that debit no longer
            // matches what the lot would carry the restored units at.
            if (hasBeenRecostedSince(lot, line.getUnitCost())) {
                throw new InvalidStockConsumptionException(
                        recostedRefusal(lot, line.getUnitCost(), "consumption"));
            }
            if (lot.getQuantityRemaining().plus(line.getQuantity())
                    .compareTo(lot.getQuantityReceived()) > 0) {
                // Reachable: something else may have consumed the lot in between. Refused rather than
                // clamped, because clamping would restore less stock than the entry it is about to
                // reverse accounts for.
                throw new InvalidStockConsumptionException(
                        "Restoring " + line.getQuantity() + " to lot " + lot.getId() + " would leave "
                                + lot.getQuantityRemaining().plus(line.getQuantity()) + " of a lot "
                                + "that only ever received " + lot.getQuantityReceived() + ". "
                                + "Something has happened to the lot since, so the correction is a "
                                + "new receipt rather than a reversal.");
            }
        }

        Long reversingEntryId = null;
        if (original.getJournalEntryId() != null) {
            long originalEntryId = original.getJournalEntryId();
            reversingEntryId = journal.post(NewJournalEntry.reversalOf(
                    originalEntryId,
                    reversalDate,
                    "Reversal of stock consumption " + consumptionId + " — "
                            + original.getProduct().getSku(),
                    original.getSource(),
                    journal.mirrorOf(originalEntryId))).id();
        }

        StockConsumption reversal = new StockConsumption(original.getProduct(),
                original.getQuantityFilled(), original.getQuantityFilled(), reversalDate,
                original.getSource(), note, consumptionId);
        for (StockConsumptionLine line : original.getLines()) {
            line.getLot().restore(line.getQuantity());
            reversal.addLine(line.getLot(), line.getQuantity(), line.getUnitCost());
        }
        reversal.postedAs(reversingEntryId);

        StockConsumption saved = consumptions.save(reversal);

        auditLog.record("stock-consumption.reversed", CONSUMPTION_ENTITY_TYPE,
                String.valueOf(consumptionId), Map.of(
                        "reversedBy", String.valueOf(saved.getId()),
                        "quantity", original.getQuantityFilled().toString(),
                        "reversalDate", reversalDate.toString(),
                        "posted", String.valueOf(reversingEntryId != null)));

        return toView(saved, entriesOf(List.of(saved)));
    }

    @Override
    @Transactional(readOnly = true)
    public StockConsumptionView requireConsumption(long consumptionId) {
        StockConsumption record = loadConsumption(consumptionId);
        return toView(record, entriesOf(List.of(record)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockConsumptionView> consumptionsOf(long productId) {
        return toConsumptionViews(consumptions.findByProductIdOrderByIdAsc(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockConsumptionView> consumptionsBetween(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with no consumptions in it.");
        }
        return toConsumptionViews(
                consumptions.findByConsumptionDateBetweenOrderByConsumptionDateAscIdAsc(from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockConsumptionView> consumptionsWithShortfall() {
        return toConsumptionViews(consumptions.findOutstandingShortfalls());
    }

    /**
     * Refuses the requests that do not describe anything, and <strong>deliberately does not refuse an
     * insufficient quantity</strong> — that is Q17, and the answer is to allow it and flag it.
     */
    private static void requireConsumable(Product product, Quantity quantity) {
        if (product.isBundle()) {
            throw new InvalidStockConsumptionException(
                    "Product '" + product.getSku() + "' is a bundle, which has no stock of its own "
                            + "(brief §5). Consume its components: the bundle line and the component "
                            + "lines are linked rather than duplicated, and taking stock off both "
                            + "would count the same goods twice.");
        }
        if (!product.getType().isStocked()) {
            throw new InvalidStockConsumptionException(
                    "Product '" + product.getSku() + "' is a " + product.getType() + ", which has no "
                            + "inventory lots and therefore no cost of goods sold. A service costs "
                            + "against Cost of service sold, which is not a FIFO question.");
        }
        UnitOfMeasure unit = product.getUnitOfMeasure();
        if (!unit.isFractionalQuantityAllowed()
                && quantity.value().stripTrailingZeros().scale() > 0) {
            throw new InvalidStockConsumptionException(
                    "Quantity " + quantity + " has a fraction, and product '" + product.getSku()
                            + "' is measured in '" + unit.getCode() + "', which does not allow one.");
        }
    }

    /**
     * Posts the cost, or returns null when there is none to post.
     *
     * <p><strong>One line per lot on each side</strong> (brief §6): debit {@code COST_OF_GOODS_SOLD}
     * and credit {@code INVENTORY}, both carrying the lot's {@code SubLedgerRef}. The debit carries a
     * lot reference although COGS is a Standard rather than a Control account — knowing which lots a
     * cost came out of is the whole reason for having lots, and the ledger permits a reference on a
     * non-Control account for exactly this.
     *
     * <p>Nothing is posted when every lot consumed was carried at zero, or when nothing could be
     * filled at all. Both are real: a free sample being sold derecognises nothing, and an unbacked
     * shortfall has no lot to take a cost from.
     */
    private Long postConsumption(StockConsumption record, Product product, LocalDate date,
            JournalSource source) {
        RoundingMode roundingMode = settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE);

        AccountView cogs = chartOfAccounts.requireAccount(AccountSystemKey.COST_OF_GOODS_SOLD);
        AccountView inventory = chartOfAccounts.requireAccount(AccountSystemKey.INVENTORY);

        List<NewJournalLine> lines = new ArrayList<>();
        for (StockConsumptionLine line : record.getLines()) {
            Money cost = line.getUnitCost().extend(line.getQuantity(), roundingMode);
            if (!cost.isPositive()) {
                // A lot carried at zero derecognises nothing, and the ledger rightly refuses a
                // zero-amount line. The stock still leaves; the consumption line still records it.
                continue;
            }
            SubLedgerRef lotRef = SubLedgerRef.inventoryLot(line.getLot().getId());
            lines.add(NewJournalLine.debit(cogs.id(), cost)
                    .forSubLedger(lotRef)
                    .describedAs(line.getQuantity() + " x " + product.getSku()));
            lines.add(NewJournalLine.credit(inventory.id(), cost).forSubLedger(lotRef));
        }
        if (lines.isEmpty()) {
            return null;
        }

        String description = "Cost of goods sold — " + record.getQuantityFilled() + " x "
                + product.getSku()
                + (record.getShortfall().isPositive()
                        ? " (" + record.getShortfall() + " unbacked — stock went negative)" : "");

        return journal.post(NewJournalEntry.of(date, description, source, lines)).id();
    }

    private StockConsumption loadConsumption(long consumptionId) {
        return consumptions.findById(consumptionId)
                .orElseThrow(() -> new StockConsumptionNotFoundException(consumptionId));
    }

    private List<StockConsumptionView> toConsumptionViews(List<StockConsumption> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, JournalEntryView> byEntryId = entriesOf(records);
        Map<Long, Long> reversedBy = new LinkedHashMap<>();
        for (Object[] pair : consumptions.findReversalPairs(
                records.stream().map(StockConsumption::getId).toList())) {
            reversedBy.put((Long) pair[0], (Long) pair[1]);
        }
        return records.stream()
                .map(record -> toView(record, byEntryId, reversedBy.get(record.getId())))
                .toList();
    }

    /** One query for every entry a batch of consumptions points at, rather than one per row. */
    private Map<Long, JournalEntryView> entriesOf(List<StockConsumption> records) {
        List<Long> entryIds = records.stream()
                .map(StockConsumption::getJournalEntryId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, JournalEntryView> byId = new LinkedHashMap<>();
        for (JournalEntryView entry : journal.findEntries(entryIds)) {
            byId.put(entry.id(), entry);
        }
        return byId;
    }

    private StockConsumptionView toView(
            StockConsumption record, Map<Long, JournalEntryView> entries) {
        return toView(record, entries,
                consumptions.findByReversalOfId(record.getId())
                        .map(StockConsumption::getId).orElse(null));
    }

    private static StockConsumptionView toView(StockConsumption record,
            Map<Long, JournalEntryView> entries, Long reversedByConsumptionId) {
        Product product = record.getProduct();

        // Read off the entry, never recomputed from the lots: brief §5 says a lot's unit cost includes
        // allocated landed costs, so once step 10 allocates freight a recomputation would give a
        // different figure from the one that posted, with no way to tell which was historical.
        Money totalCost = null;
        if (record.getJournalEntryId() != null) {
            JournalEntryView entry = entries.get(record.getJournalEntryId());
            if (entry == null) {
                throw new IllegalStateException(
                        "Consumption " + record.getId() + " names journal entry "
                                + record.getJournalEntryId() + ", which does not exist. A foreign key "
                                + "makes that impossible, so the entry was not fetched.");
            }
            totalCost = entry.totalDebits();
        }

        List<StockConsumptionLineView> lineViews = record.getLines().stream()
                .map(line -> new StockConsumptionLineView(
                        line.getId(),
                        line.getLot().getId(),
                        line.getQuantity(),
                        line.getUnitCost(),
                        // Per line the rounding is the same one the posting used, stated here so a
                        // reader can see how the total decomposes rather than only its sum.
                        line.getUnitCost().extend(line.getQuantity(), RoundingMode.HALF_UP)))
                .toList();

        return new StockConsumptionView(
                record.getId(),
                product.getId(),
                product.getSku(),
                record.getQuantityRequested(),
                record.getQuantityFilled(),
                record.getShortfall(),
                record.getConsumptionDate(),
                record.getSource(),
                record.getNote(),
                totalCost == null ? Money.zero(Money.EUR) : totalCost,
                record.getJournalEntryId(),
                record.getReversalOfId(),
                reversedByConsumptionId,
                record.getReturnsConsumptionId(),
                lineViews);
    }

    private static Map<String, String> consumptionDetail(StockConsumption record) {
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("sku", record.getProduct().getSku());
        detail.put("requested", record.getQuantityRequested().toString());
        detail.put("filled", record.getQuantityFilled().toString());
        detail.put("lots", String.valueOf(record.getLines().size()));
        detail.put("source", record.getSource().name());
        detail.put("consumptionDate", record.getConsumptionDate().toString());
        if (record.getShortfall().isPositive()) {
            // Named in the log rather than left to be inferred from two numbers: Q17 allows this and
            // the whole point of allowing it is that somebody finds out.
            detail.put("shortfall", record.getShortfall().toString());
        }
        detail.put("journalEntry", record.getJournalEntryId() == null
                ? "none — nothing carried a cost"
                : String.valueOf(record.getJournalEntryId()));
        return detail;
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
        // An UNRECEIVED unit is not a machine anybody can look up: the delivery that created it was
        // reversed, so as far as the business is concerned it was never here.
        return units.findBySerialNumberIgnoreCaseAndStatusNot(
                        serialNumber.trim(), SerializedUnitStatus.UNRECEIVED)
                .map(this::toView);
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
    // Write-offs — the step 3 and step 6 obligation
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public StockWriteOffView writeOff(NewStockWriteOff request) {
        Objects.requireNonNull(request, "request");
        InventoryLot lot = lots.findById(request.lotId())
                .orElseThrow(() -> new InventoryLotNotFoundException(request.lotId()));

        if (lot.isSerialTracked() != request.isSerialized()) {
            throw new InvalidStockWriteOffException(lot.isSerialTracked()
                    ? "Lot " + lot.getId() + " is serial-tracked, so a write-off names the unit it "
                            + "concerns. Brief §5: it uses that unit's own actual cost, with no FIFO "
                            + "logic, because a serialized unit is not pooled stock."
                    : "Lot " + lot.getId() + " is pooled stock, so a write-off states a quantity. "
                            + "There are no individual units to name.");
        }

        SerializedUnit unit = request.isSerialized() ? loadUnitOf(lot, request) : null;
        Quantity quantity = request.isSerialized()
                ? Quantity.of(1)
                : validatedPooledQuantity(lot, request.quantity());

        // The cost is read BEFORE anything moves and carried into the record: V19 stores it for
        // stock_consumption_line.unit_cost's reason, since step 10 lets a lot's carrying cost move.
        UnitCost carriedAt = lot.getUnitCost();
        StockWriteOff record = record(lot, unit, quantity, carriedAt, request.reason(),
                request.writeOffDate(), request.note(),
                postWriteOff(lot, quantity, request), null);

        // The stock moves only after the posting has been accepted. Either both happen or neither
        // does — the transaction guarantees that — but doing it in this order means a rejected posting
        // never leaves a half-applied request behind in the entity graph either.
        if (unit != null) {
            unit.writeOff();
        } else {
            lot.consume(quantity);
        }

        auditLog.record("stock-write-off.recorded", WRITE_OFF_ENTITY_TYPE,
                String.valueOf(record.getId()), writeOffDetail(record));
        return toView(record, entriesById(List.of(record)));
    }

    @Override
    @Transactional
    public StockWriteOffView reverseWriteOff(long writeOffId, LocalDate reversalDate, String note) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        StockWriteOff original = loadWriteOff(writeOffId);

        if (original.isReversal()) {
            throw new InvalidStockWriteOffException(
                    "Write-off " + writeOffId + " is itself the reversal of write-off "
                            + original.getReversalOfId() + ". Reversing it would write the stock off "
                            + "again while claiming to be a correction; record a new write-off "
                            + "instead, so the ledger says what actually happened.");
        }
        writeOffs.findByReversalOfId(writeOffId).ifPresent(existing -> {
            throw new InvalidStockWriteOffException(
                    "Write-off " + writeOffId + " has already been reversed by write-off "
                            + existing.getId() + ". Reversing it twice would restore the stock twice "
                            + "and credit the loss twice, with both halves looking correct.");
        });

        InventoryLot lot = original.getLot();
        Quantity quantity = original.getQuantity();
        SerializedUnit unit = original.getSerializedUnit();

        // ADR 0011, as reverseConsumption does: this posts the exact mirror of what the write-off
        // derecognised, so a lot re-costed since would take the stock back at a figure that no longer
        // matches what it now carries those units at.
        if (hasBeenRecostedSince(lot, original.getUnitCost())) {
            throw new InvalidStockWriteOffException(
                    recostedRefusal(lot, original.getUnitCost(), "write-off"));
        }

        if (unit != null) {
            if (unit.getStatus() != SerializedUnitStatus.WRITTEN_OFF) {
                throw new InvalidStockWriteOffException(
                        "Unit '" + unit.getSerialNumber() + "' is " + unit.getStatus() + " rather than "
                                + "written off, so there is nothing to restore. Something else has "
                                + "happened to it since.");
            }
        } else if (lot.getQuantityRemaining().plus(quantity)
                .compareTo(lot.getQuantityReceived()) > 0) {
            // Reachable: the lot may have been consumed elsewhere between the write-off and its
            // reversal. Refused rather than clamped, because clamping would silently restore less
            // stock than the entry it is about to reverse accounts for.
            throw new InvalidStockWriteOffException(
                    "Restoring " + quantity + " to lot " + lot.getId() + " would leave "
                            + lot.getQuantityRemaining().plus(quantity) + " of a lot that only ever "
                            + "received " + lot.getQuantityReceived() + ". Something has consumed the "
                            + "lot since this write-off, so the correction is a new receipt rather "
                            + "than a reversal.");
        }

        Long reversingEntryId = null;
        if (original.getJournalEntryId() != null) {
            long originalEntryId = original.getJournalEntryId();
            reversingEntryId = journal.post(NewJournalEntry.reversalOf(
                    originalEntryId,
                    reversalDate,
                    "Reversal of stock write-off " + writeOffId + " on lot " + lot.getId(),
                    JournalSource.INVENTORY_WRITE_OFF,
                    journal.mirrorOf(originalEntryId))).id();
        }

        if (unit != null) {
            unit.restoreToStock();
        } else {
            lot.restore(quantity);
        }

        StockWriteOff reversal = record(lot, unit, quantity, original.getUnitCost(),
                original.getReason(), reversalDate, note, reversingEntryId, writeOffId);

        auditLog.record("stock-write-off.reversed", WRITE_OFF_ENTITY_TYPE,
                String.valueOf(writeOffId), Map.of(
                        "reversedBy", String.valueOf(reversal.getId()),
                        "quantity", quantity.toString(),
                        "reversalDate", reversalDate.toString(),
                        "posted", String.valueOf(reversingEntryId != null)));

        return toView(reversal, entriesById(List.of(reversal)));
    }

    @Override
    @Transactional(readOnly = true)
    public StockWriteOffView requireWriteOff(long writeOffId) {
        StockWriteOff record = loadWriteOff(writeOffId);
        return toView(record, entriesById(List.of(record)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockWriteOffView> writeOffsOf(long lotId) {
        return toViews(writeOffs.findByLotIdOrderByIdAsc(lotId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockWriteOffView> writeOffsBetween(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with no write-offs in it.");
        }
        return toViews(writeOffs.findByWriteOffDateBetweenOrderByWriteOffDateAscIdAsc(from, to));
    }

    /**
     * Posts the loss, or returns null when there is none to post.
     *
     * <p>Debit {@code INVENTORY_WRITE_OFF}, credit {@code INVENTORY}, both carrying the lot's
     * {@code SubLedgerRef} — the credit because Inventory is a Control account and must, the debit
     * because knowing which lot a loss came out of is what having lots is for.
     *
     * <p><strong>Nothing is posted when the extended cost is zero.</strong> A lot carried at a unit cost
     * of zero is a real thing that {@code UnitCost} explicitly allows — a free sample, promotional stock,
     * a warranty replacement — and writing it off derecognises nothing, because nothing was carried. The
     * ledger refuses a zero-amount line for good reason, so the honest record is a write-off with no
     * entry rather than an entry that says nothing happened.
     */
    private Long postWriteOff(InventoryLot lot, Quantity quantity, NewStockWriteOff request) {
        // The single rounding, with its mode read from settings rather than chosen here: brief §6 makes
        // the mode configurable, and this is its first real consumer.
        RoundingMode roundingMode =
                settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE);
        Money amount = lot.getUnitCost().extend(quantity, roundingMode);
        if (!amount.isPositive()) {
            return null;
        }

        AccountView expense = chartOfAccounts.requireAccount(AccountSystemKey.INVENTORY_WRITE_OFF);
        AccountView inventory = chartOfAccounts.requireAccount(AccountSystemKey.INVENTORY);
        SubLedgerRef lotRef = SubLedgerRef.inventoryLot(lot.getId());

        String what = quantity + " x " + lot.getProduct().getSku() + " (lot " + lot.getId() + ")";
        String description = "Stock write-off — " + request.reason() + ": " + what
                + request.noteIfAny().map(note -> " — " + note).orElse("");

        return journal.post(NewJournalEntry.of(
                request.writeOffDate(),
                description,
                JournalSource.INVENTORY_WRITE_OFF,
                List.of(
                        NewJournalLine.debit(expense.id(), amount)
                                .forSubLedger(lotRef)
                                .describedAs(request.reason().name()),
                        NewJournalLine.credit(inventory.id(), amount)
                                .forSubLedger(lotRef)))).id();
    }

    private StockWriteOff record(InventoryLot lot, SerializedUnit unit, Quantity quantity,
            UnitCost unitCost, gr.novotrade.novocore.core.api.inventory.WriteOffReason reason,
            LocalDate date, String note, Long journalEntryId, Long reversalOfId) {
        return writeOffs.save(new StockWriteOff(
                lot, unit, quantity, unitCost, reason, date, note, journalEntryId, reversalOfId));
    }

    private SerializedUnit loadUnitOf(InventoryLot lot, NewStockWriteOff request) {
        long unitId = request.serializedUnitId();
        SerializedUnit unit = units.findById(unitId)
                .orElseThrow(() -> new SerializedUnitNotFoundException(unitId));
        if (!unit.getLot().getId().equals(lot.getId())) {
            throw new InvalidStockWriteOffException(
                    "Unit '" + unit.getSerialNumber() + "' belongs to lot " + unit.getLot().getId()
                            + ", not lot " + lot.getId() + ". The cost written off is the lot's, so "
                            + "naming the wrong one would derecognise the wrong amount.");
        }
        if (!unit.isOnHand()) {
            throw new InvalidStockWriteOffException(
                    "Unit '" + unit.getSerialNumber() + "' is " + unit.getStatus() + ", so it is not "
                            + "ours to write off.");
        }
        return unit;
    }

    private Quantity validatedPooledQuantity(InventoryLot lot, Quantity quantity) {
        if (!quantity.isPositive()) {
            throw new InvalidStockWriteOffException(
                    "Write-off quantity " + quantity + " is not positive. A write-off of nothing is "
                            + "not a write-off, and a negative one is a receipt.");
        }
        requireExpressibleQuantityForWriteOff(lot.getProduct(), quantity);
        if (quantity.compareTo(lot.getQuantityRemaining()) > 0) {
            throw new InvalidStockWriteOffException(
                    "Lot " + lot.getId() + " has " + lot.getQuantityRemaining() + " left, so "
                            + quantity + " cannot be written off it. Stock that was never there "
                            + "cannot be lost.");
        }
        return quantity;
    }

    private static void requireExpressibleQuantityForWriteOff(Product product, Quantity quantity) {
        UnitOfMeasure unit = product.getUnitOfMeasure();
        if (unit.isFractionalQuantityAllowed()) {
            return;
        }
        if (quantity.value().stripTrailingZeros().scale() > 0) {
            throw new InvalidStockWriteOffException(
                    "Quantity " + quantity + " has a fraction, and product '" + product.getSku()
                            + "' is measured in '" + unit.getCode() + "', which does not allow one.");
        }
    }

    private StockWriteOff loadWriteOff(long writeOffId) {
        return writeOffs.findById(writeOffId)
                .orElseThrow(() -> new StockWriteOffNotFoundException(writeOffId));
    }

    private List<StockWriteOffView> toViews(List<StockWriteOff> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, JournalEntryView> byEntryId = entriesById(records);
        Map<Long, Long> reversedBy = new LinkedHashMap<>();
        for (Object[] pair : writeOffs.findReversalPairs(
                records.stream().map(StockWriteOff::getId).toList())) {
            reversedBy.put((Long) pair[0], (Long) pair[1]);
        }
        return records.stream()
                .map(record -> toView(record, byEntryId, reversedBy.get(record.getId())))
                .toList();
    }

    /** One query for every entry a batch of write-offs points at, rather than one per row. */
    private Map<Long, JournalEntryView> entriesById(List<StockWriteOff> records) {
        List<Long> entryIds = records.stream()
                .map(StockWriteOff::getJournalEntryId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, JournalEntryView> byId = new LinkedHashMap<>();
        for (JournalEntryView entry : journal.findEntries(entryIds)) {
            byId.put(entry.id(), entry);
        }
        return byId;
    }

    private StockWriteOffView toView(StockWriteOff record, Map<Long, JournalEntryView> entries) {
        return toView(record, entries,
                writeOffs.findByReversalOfId(record.getId()).map(StockWriteOff::getId).orElse(null));
    }

    private static StockWriteOffView toView(StockWriteOff record,
            Map<Long, JournalEntryView> entries, Long reversedByWriteOffId) {
        InventoryLot lot = record.getLot();
        SerializedUnit unit = record.getSerializedUnit();

        // Read off the entry, never recomputed from the lot's unit cost: brief §5 says a lot's unit cost
        // includes allocated landed costs, so once step 10 allocates freight a recomputation would give a
        // different figure from the one that actually posted, with no way to tell which was historical.
        Money postedAmount = null;
        if (record.getJournalEntryId() != null) {
            JournalEntryView entry = entries.get(record.getJournalEntryId());
            if (entry == null) {
                throw new IllegalStateException(
                        "Write-off " + record.getId() + " names journal entry "
                                + record.getJournalEntryId() + ", which does not exist. A foreign key "
                                + "makes that impossible, so the entry was not fetched.");
            }
            postedAmount = entry.totalDebits();
        }

        return new StockWriteOffView(
                record.getId(),
                lot.getId(),
                lot.getProduct().getId(),
                lot.getProduct().getSku(),
                unit == null ? null : unit.getId(),
                unit == null ? null : unit.getSerialNumber(),
                record.getQuantity(),
                record.getUnitCost(),
                record.getReason(),
                record.getWriteOffDate(),
                record.getNote(),
                record.getJournalEntryId(),
                postedAmount,
                record.getReversalOfId(),
                reversedByWriteOffId);
    }

    private static Map<String, String> writeOffDetail(StockWriteOff record) {
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("sku", record.getLot().getProduct().getSku());
        detail.put("lot", String.valueOf(record.getLot().getId()));
        detail.put("quantity", record.getQuantity().toString());
        detail.put("reason", record.getReason().name());
        detail.put("writeOffDate", record.getWriteOffDate().toString());
        detail.put("journalEntry", record.getJournalEntryId() == null
                // Distinguished in the log rather than left blank: "no entry" and "entry missing" are
                // different situations and one of them is a bug.
                ? "none — lot carried at zero cost"
                : String.valueOf(record.getJournalEntryId()));
        if (record.getSerializedUnit() != null) {
            detail.put("serialNumber", record.getSerializedUnit().getSerialNumber());
        }
        return detail;
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
                lot.getReceivedUnitCost(),
                lot.getAllocatedLandedUnitCost(),
                lot.getAcquisitionDate(),
                lot.getRoastDate(),
                lot.getLocation(),
                unitViews,
                lot.getGoodsReceiptLineId());
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
                lot.getUnitCost(),
                unit.getSoldToCustomerId(),
                unit.getSoldOnInvoiceLineId());
    }
}
