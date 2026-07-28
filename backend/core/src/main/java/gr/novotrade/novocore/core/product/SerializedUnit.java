package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.support.AuditableEntity;
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
import jakarta.persistence.Table;

/**
 * One individually identified item inside a lot — brief §5's Unit. A specific machine, by serial number.
 *
 * <p><strong>Carries its own location.</strong> That is the whole reason a serial-tracked lot has none:
 * a lot-level location cannot say "one of these three is out for repair" without splitting the lot.
 *
 * <p><strong>The customer and invoice link arrived in step 9</strong>, with the document that justifies
 * it. Step 6 deliberately added no nullable customer id early, because a unit marked sold to somebody
 * with no document behind it is a claim nothing can substantiate. Both halves move together and exist
 * exactly when the status is {@link SerializedUnitStatus#SOLD} — biconditional, by CHECK.
 */
@Entity
@Table(name = "serialized_unit")
class SerializedUnit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private InventoryLot lot;

    /** Unique across all stock — see {@code InventoryService.findUnitBySerialNumber} for why. */
    @Column(name = "serial_number", nullable = false, length = 80)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SerializedUnitStatus status = SerializedUnitStatus.IN_STOCK;

    @Enumerated(EnumType.STRING)
    @Column(name = "location", nullable = false, length = 20)
    private StockLocation location;

    /** Brief §5's link on a sold unit. Present exactly when {@link #status} is SOLD. */
    @Column(name = "sold_to_customer_id")
    private Long soldToCustomerId;

    @Column(name = "sold_on_invoice_line_id")
    private Long soldOnInvoiceLineId;

    /** For JPA only. */
    protected SerializedUnit() {
    }

    SerializedUnit(InventoryLot lot, String serialNumber, StockLocation location) {
        this.lot = lot;
        this.serialNumber = serialNumber;
        this.status = SerializedUnitStatus.IN_STOCK;
        this.location = location;
    }

    Long getId() {
        return id;
    }

    InventoryLot getLot() {
        return lot;
    }

    String getSerialNumber() {
        return serialNumber;
    }

    SerializedUnitStatus getStatus() {
        return status;
    }

    StockLocation getLocation() {
        return location;
    }

    Long getSoldToCustomerId() {
        return soldToCustomerId;
    }

    Long getSoldOnInvoiceLineId() {
        return soldOnInvoiceLineId;
    }

    boolean isOnHand() {
        return status.isOnHand();
    }

    /**
     * Marks this unit sold, naming the buyer and the line that sold it — brief §5, and the step 6
     * obligation that left {@link SerializedUnitStatus#SOLD} unreachable.
     *
     * <p>The status and the link are set together, which is what makes the biconditional CHECK
     * satisfiable and means there is no window in which a unit is sold to nobody.
     */
    void sell(long customerId, long salesInvoiceLineId) {
        this.status = SerializedUnitStatus.SOLD;
        this.soldToCustomerId = customerId;
        this.soldOnInvoiceLineId = salesInvoiceLineId;
    }

    /**
     * Takes a sold unit back — a customer return, or the reversal of the sale that sold it.
     *
     * <p>The sale link is cleared with the status, because brief §5 puts that link on a <em>sold</em>
     * unit and a machine sitting back on the shelf is not sold to anybody. Its history is in the
     * credit note and the invoice, which both still say what happened.
     */
    void returnFromCustomer() {
        this.status = SerializedUnitStatus.IN_STOCK;
        this.soldToCustomerId = null;
        this.soldOnInvoiceLineId = null;
    }

    /** Posts nothing, for the reason {@code InventoryLot.moveTo} gives. */
    void moveTo(StockLocation destination) {
        this.location = destination;
    }

    /**
     * Derecognises this unit. The step 6 obligation that {@link SerializedUnitStatus#WRITTEN_OFF} was
     * declared for and could not reach until the ledger existed.
     *
     * <p><strong>The location is deliberately left alone.</strong> A written-off machine is often still
     * physically in the Damaged Goods area waiting to be disposed of, and the stock count already excludes
     * it through {@code status = IN_STOCK} rather than through where it is. Clearing the location would
     * lose the one fact somebody looking for it still needs.
     */
    void writeOff() {
        this.status = SerializedUnitStatus.WRITTEN_OFF;
    }

    /** Puts a written-off unit back on hand — the reversal of a write-off. */
    void restoreToStock() {
        this.status = SerializedUnitStatus.IN_STOCK;
    }

    /**
     * Un-makes this unit's arrival — the reversal of the Goods Receipt that created it (Q39).
     *
     * <p>A status of its own rather than a write-off, because no loss occurred, and this is the one
     * status that releases the serial number: the commonest reason to reverse a delivery is that it
     * was entered wrong, and re-entering the same machines correctly must not be blocked by the
     * mistake.
     */
    void unreceive() {
        this.status = SerializedUnitStatus.UNRECEIVED;
    }
}
