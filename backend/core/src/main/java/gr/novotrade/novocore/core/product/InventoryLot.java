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

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    /** {@code char(3)}, so the JDBC type has to be stated — see {@code Product.sellingPriceCurrency}. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "unit_cost_currency", nullable = false, length = 3)
    private String unitCostCurrency;

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
            LocalDate acquisitionDate, LocalDate roastDate, StockLocation location) {
        this.product = product;
        this.quantityReceived = quantityReceived;
        this.quantityRemaining = quantityReceived;
        this.unitCost = unitCost.value();
        this.unitCostCurrency = unitCost.currency().getCurrencyCode();
        this.acquisitionDate = acquisitionDate;
        this.roastDate = roastDate;
        this.location = location;
    }

    /** Pooled stock: a quantity at a location. */
    static InventoryLot pooled(Product product, Quantity quantityReceived, UnitCost unitCost,
            LocalDate acquisitionDate, LocalDate roastDate, StockLocation location) {
        return new InventoryLot(product, quantityReceived.value(), unitCost, acquisitionDate,
                roastDate, location);
    }

    /**
     * Serial-tracked stock: no quantity and no location of its own. Units are added by
     * {@link #addUnit} and persisted with the lot.
     */
    static InventoryLot serialTracked(Product product, UnitCost unitCost, LocalDate acquisitionDate,
            LocalDate roastDate) {
        return new InventoryLot(product, null, unitCost, acquisitionDate, roastDate, null);
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

    UnitCost getUnitCost() {
        return new UnitCost(unitCost, Currency.getInstance(unitCostCurrency));
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
