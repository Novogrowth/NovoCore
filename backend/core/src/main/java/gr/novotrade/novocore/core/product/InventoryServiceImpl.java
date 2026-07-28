package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.inventory.InvalidInventoryLotException;
import gr.novotrade.novocore.core.api.inventory.InvalidStockWriteOffException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotNotFoundException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.NewStockWriteOff;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitNotFoundException;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitView;
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

    private final ProductRepository products;
    private final InventoryLotRepository lots;
    private final SerializedUnitRepository units;
    private final BundleComponentRepository bundleComponents;
    private final StockWriteOffRepository writeOffs;
    private final ChartOfAccountsService chartOfAccounts;
    private final JournalService journal;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    InventoryServiceImpl(ProductRepository products, InventoryLotRepository lots,
            SerializedUnitRepository units, BundleComponentRepository bundleComponents,
            StockWriteOffRepository writeOffs, ChartOfAccountsService chartOfAccounts,
            JournalService journal, SettingsService settings, AuditLogService auditLog) {
        this.products = products;
        this.lots = lots;
        this.units = units;
        this.bundleComponents = bundleComponents;
        this.writeOffs = writeOffs;
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

        StockWriteOff record = record(lot, unit, quantity, request.reason(),
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

        StockWriteOff reversal = record(lot, unit, quantity, original.getReason(), reversalDate,
                note, reversingEntryId, writeOffId);

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
            gr.novotrade.novocore.core.api.inventory.WriteOffReason reason, LocalDate date,
            String note, Long journalEntryId, Long reversalOfId) {
        return writeOffs.save(new StockWriteOff(
                lot, unit, quantity, reason, date, note, journalEntryId, reversalOfId));
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
