package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

/**
 * One inventory lot. Brief §5: every purchase creates one.
 *
 * <p>In the {@code product} package rather than an {@code inventory} one of its own, because it is the
 * same slice of the core: the chart of accounts declares the Inventory control account's sub-ledger as
 * <em>Product-Lot</em>, one thing, and a product's stock is not a separate aggregate reached through a
 * published service. That is what lets {@code ProductServiceImpl} read a last purchase price straight
 * off a lot without either service depending on the other — the two would otherwise be a bean cycle.
 *
 * <p><strong>Two shapes, and the nullable columns are the mechanism.</strong> Pooled stock stores its
 * quantities and its {@link #location} here. Serial-tracked stock stores neither: the quantity is the
 * count of its {@link #units}, and each unit carries its own location, because three identical machines
 * received together are one lot and one of them going out for repair does not move the other two.
 * Storing a quantity on a serial-tracked lot would be a second copy of a number the units already
 * state — the argument that keeps a cost off {@code Asset} and {@code normal_balance_side} off
 * {@code Account}. A CHECK constraint refuses any third shape.
 *
 * <p><strong>The cost is two figures and the carrying cost is their sum</strong> — step 10, ADR 0010.
 * {@link #receivedUnitCost} is what the goods cost and never changes;
 * {@link #allocatedLandedUnitCost} accumulates the freight and duty allocated onto them. The received
 * half is frozen because it is the basis every allocation divides by: if allocation were computed
 * against the carrying cost, a second freight invoice would split the same lots in proportions the
 * first one had already moved, and the result would depend on the order the two were entered in.
 */
@Entity
@Table(name = "inventory_lot")
class InventoryLot extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A real association: same package, same slice — the reason {@code UnitOfMeasure} is one too. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Null exactly when serial-tracked. */
    @Column(name = "quantity_received")
    private BigDecimal quantityReceived;

    /** Null exactly when serial-tracked. Equal to received until step 8 consumes lots FIFO. */
    @Column(name = "quantity_remaining")
    private BigDecimal quantityRemaining;

    /**
     * What one unit cost when the stock came in. <strong>Never written again</strong> — ADR 0010: it
     * is the basis every landed-cost allocation divides by, so a second freight invoice against the
     * same lots has to see the same proportions the first one did.
     */
    @Column(name = "received_unit_cost", nullable = false)
    private BigDecimal receivedUnitCost;

    /** {@code char(3)}, so the JDBC type has to be stated — see {@code Product.sellingPriceCurrency}. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "received_unit_cost_currency", nullable = false, length = 3)
    private String receivedUnitCostCurrency;

    /**
     * Freight and duty allocated onto one unit of this lot since (brief §4). Zero until something
     * allocates; the lot is carried at this plus {@link #receivedUnitCost}, computed on read.
     */
    @Column(name = "allocated_landed_unit_cost", nullable = false)
    private BigDecimal allocatedLandedUnitCost;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "allocated_landed_unit_cost_currency", nullable = false, length = 3)
    private String allocatedLandedUnitCostCurrency;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    /** Coffee only. Null everywhere else. */
    @Column(name = "roast_date")
    private LocalDate roastDate;

    /** Null exactly when serial-tracked — the units carry it then. */
    @Enumerated(EnumType.STRING)
    @Column(name = "location", length = 20)
    private StockLocation location;

    /**
     * Brief §5's source document reference, added in step 8 once there was something to point at.
     *
     * <p>A plain id rather than an association: {@code GoodsReceiptLine} lives in the purchasing slice,
     * which this package cannot see — the same boundary that makes {@code Product.defaultVatClassId}
     * an id. Nullable, and the null case is real: phase 2b migrates opening stock that no NovoCore
     * delivery created.
     */
    @Column(name = "goods_receipt_line_id")
    private Long goodsReceiptLineId;

    /**
     * Cascaded on purpose: a serialized lot and its units are created in one transaction, which is what
     * makes "the unit count is the quantity" true by construction rather than by a check that runs
     * later. No {@code REMOVE} and no {@code orphanRemoval} — nothing deletes stock, it is written off.
     */
    @OneToMany(mappedBy = "lot", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @OrderBy("serialNumber ASC")
    private List<SerializedUnit> units = new ArrayList<>();

    /** For JPA only. */
    protected InventoryLot() {
    }

    private InventoryLot(Product product, BigDecimal quantityReceived, UnitCost unitCost,
            LocalDate acquisitionDate, LocalDate roastDate, StockLocation location,
            Long goodsReceiptLineId) {
        this.product = product;
        this.quantityReceived = quantityReceived;
        this.quantityRemaining = quantityReceived;
        this.receivedUnitCost = unitCost.value();
        this.receivedUnitCostCurrency = unitCost.currency().getCurrencyCode();
        // Explicit rather than left to a column default. Nothing has been allocated onto a lot that
        // has just been received, and the currency has to agree with the received half — a CHECK
        // refuses it otherwise, because a lot whose two halves disagreed could not be added up at all.
        this.allocatedLandedUnitCost = BigDecimal.ZERO;
        this.allocatedLandedUnitCostCurrency = this.receivedUnitCostCurrency;
        this.acquisitionDate = acquisitionDate;
        this.roastDate = roastDate;
        this.location = location;
        this.goodsReceiptLineId = goodsReceiptLineId;
    }

    /** Pooled stock: a quantity at a location. */
    static InventoryLot pooled(Product product, Quantity quantityReceived, UnitCost unitCost,
            LocalDate acquisitionDate, LocalDate roastDate, StockLocation location,
            Long goodsReceiptLineId) {
        return new InventoryLot(product, quantityReceived.value(), unitCost, acquisitionDate,
                roastDate, location, goodsReceiptLineId);
    }

    /**
     * Serial-tracked stock: no quantity and no location of its own. Units are added by
     * {@link #addUnit} and persisted with the lot.
     */
    static InventoryLot serialTracked(Product product, UnitCost unitCost, LocalDate acquisitionDate,
            LocalDate roastDate, Long goodsReceiptLineId) {
        return new InventoryLot(product, null, unitCost, acquisitionDate, roastDate, null,
                goodsReceiptLineId);
    }

    SerializedUnit addUnit(String serialNumber, StockLocation unitLocation) {
        SerializedUnit unit = new SerializedUnit(this, serialNumber, unitLocation);
        units.add(unit);
        return unit;
    }

    Long getId() {
        return id;
    }

    Product getProduct() {
        return product;
    }

    boolean isSerialTracked() {
        return quantityReceived == null;
    }

    /**
     * What arrived — counted from the units when serial-tracked, so there is one source of the number
     * rather than two that must agree.
     */
    Quantity getQuantityReceived() {
        if (quantityReceived != null) {
            return Quantity.of(quantityReceived);
        }
        return Quantity.of(units.size());
    }

    /** What is left. Derived from unit statuses when serial-tracked, for the same reason. */
    Quantity getQuantityRemaining() {
        if (quantityRemaining != null) {
            return Quantity.of(quantityRemaining);
        }
        return Quantity.of(units.stream().filter(SerializedUnit::isOnHand).count());
    }

    /** What was paid for the goods. Frozen — see the field. */
    UnitCost getReceivedUnitCost() {
        return new UnitCost(receivedUnitCost, Currency.getInstance(receivedUnitCostCurrency));
    }

    /** Freight and duty allocated onto one unit since. Zero until an allocation posts. */
    UnitCost getAllocatedLandedUnitCost() {
        return new UnitCost(
                allocatedLandedUnitCost, Currency.getInstance(allocatedLandedUnitCostCurrency));
    }

    /**
     * What one unit is <em>carried</em> at — brief §5's "unit cost includes allocated landed costs".
     *
     * <p>Computed, never stored: a third column holding the sum would be the number that must agree
     * with the other two. This is what FIFO costs at, what a write-off derecognises, and what
     * Inventory carries.
     */
    UnitCost getUnitCost() {
        return getReceivedUnitCost().plus(getAllocatedLandedUnitCost());
    }

    /**
     * Raises what this lot is carried at by an allocated landed cost (ADR 0010).
     *
     * <p>Adds to the allocated half and leaves the received half alone, which is what keeps a later
     * allocation's proportions reproducible. Bounds and currency are checked by the service so the
     * failure names the lot and the amounts; the CHECK constraints are the guarantee.
     */
    void applyLandedCost(UnitCost perUnit) {
        this.allocatedLandedUnitCost = getAllocatedLandedUnitCost().plus(perUnit).value();
    }

    /** Takes an allocated landed cost back off — the reversal of {@link #applyLandedCost}. */
    void removeLandedCost(UnitCost perUnit) {
        this.allocatedLandedUnitCost = getAllocatedLandedUnitCost().minus(perUnit).value();
    }

    LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    LocalDate getRoastDate() {
        return roastDate;
    }

    /** Null when serial-tracked. */
    StockLocation getLocation() {
        return location;
    }

    /** Null for stock no NovoCore Goods Receipt created — phase 2b's opening balances. */
    Long getGoodsReceiptLineId() {
        return goodsReceiptLineId;
    }

    /**
     * The units, by serial number.
     *
     * <p>Sorted here rather than relying on {@code @OrderBy} alone. That annotation orders the list
     * when Hibernate loads it, and does nothing for a lot that was built in memory a moment ago by
     * {@link #addUnit} — so a freshly received lot would come back in whatever order the serials were
     * scanned in, and the same lot read back later would come back sorted. One projection returning two
     * orders is the kind of difference a test written against the second case never sees.
     */
    List<SerializedUnit> getUnits() {
        return units.stream()
                .sorted(Comparator.comparing(SerializedUnit::getSerialNumber))
                .toList();
    }

    /**
     * Moves pooled stock. Posts nothing — the step 3 decision: stock at Damaged Goods is unsellable and
     * still an asset at cost, and only the write-off derecognises it.
     *
     * <p>Refused on a serial-tracked lot by the service, which has the message; here it would silently
     * set a column the CHECK constraint forbids.
     */
    void moveTo(StockLocation destination) {
        this.location = destination;
    }

    /**
     * Takes pooled quantity out of this lot.
     *
     * <p><strong>Never called on a serial-tracked lot</strong>, whose remaining quantity is the count of
     * its on-hand units and is not a column: writing one would be refused by
     * {@code inventory_lot_pooled_columns_go_together}. The service checks the shape and has the message;
     * this is the assertion that the two agree.
     *
     * <p>Bounds are checked by the service so the failure names the lot and the amounts, and again by
     * {@code inventory_lot_remaining_within_received}, which is the guarantee.
     */
    void consume(Quantity amount) {
        requirePooled("consumed from");
        this.quantityRemaining = quantityRemaining.subtract(amount.value());
    }

    /**
     * Puts pooled quantity back — the reversal of a write-off.
     *
     * <p>Bounded above by what was received, not by what is currently missing: restoring more than the
     * lot ever held would be inventing stock, and the CHECK refuses it.
     */
    void restore(Quantity amount) {
        requirePooled("restored to");
        this.quantityRemaining = quantityRemaining.add(amount.value());
    }

    private void requirePooled(String what) {
        if (isSerialTracked()) {
            throw new IllegalStateException(
                    "Lot " + id + " is serial-tracked, so quantity cannot be " + what + " it directly: "
                            + "its quantity is the count of its on-hand units. Change the unit's status "
                            + "instead. Reaching here means the service's shape check was bypassed.");
        }
    }
}
