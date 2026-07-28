package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.inventory.WriteOffReason;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
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
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Stock derecognised without a sale, carrying the {@link WriteOffReason} that justified the chart of
 * accounts having <em>one</em> {@code Inventory write-off / shrinkage} account rather than three.
 *
 * <p><strong>Why a table and not a field on the journal entry.</strong> The reason is domain-specific and
 * the ledger is generic: a {@code write_off_reason} column on {@code journal_entry} would be NULL on every
 * other kind of entry, and the next transaction type with its own field would add another.
 *
 * <p><strong>{@link #journalEntryId} is nullable, and the case is real.</strong> A lot may be carried at a
 * unit cost of zero — a supplier's free sample, promotional stock, a warranty replacement received at no
 * charge, all of which {@code UnitCost} explicitly allows. Writing that off derecognises nothing, and a
 * zero-amount journal entry is refused by the ledger for good reason, so the stock leaves and no entry
 * exists. That is the honest record rather than a gap.
 *
 * <p><strong>No amount column, but a unit cost since V19.</strong> What was POSTED is in the entry and
 * stays there: it is a consequence of our own posting, and storing it here as well would be a second copy
 * of it. What the stock was CARRIED at when it left is a different thing — a historical input rather than
 * a historical output — and step 10 made a lot's carrying cost move, so it is no longer recoverable from
 * the lot afterwards. {@code stock_consumption_line} has stored its own for exactly this reason since step
 * 8; this is the same rule, arriving when the need did (ADR 0011).
 */
@Entity
@Table(name = "stock_write_off")
class StockWriteOff extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A real association: same package, same slice — the reason {@code UnitOfMeasure} is one too. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private InventoryLot lot;

    /**
     * Present for a serialized write-off. Brief §5: it names the specific unit and uses that unit's own
     * actual cost, with no FIFO logic, because a serialized unit is not pooled stock.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serialized_unit_id")
    private SerializedUnit serializedUnit;

    /** Always 1 for a serialized write-off, by CHECK constraint. */
    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    /**
     * What the lot was carried at when this derecognised it — see the class comment. Frozen, and what
     * {@code reverseWriteOff} compares against to find out whether the lot has been re-costed since.
     */
    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "unit_cost_currency", nullable = false, length = 3)
    private String unitCostCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private WriteOffReason reason;

    @Column(name = "write_off_date", nullable = false)
    private LocalDate writeOffDate;

    @Column(name = "note", length = 500)
    private String note;

    /**
     * A plain id: {@code JournalEntry} is package-private in the ledger slice, so this is the only option
     * available — the same pattern {@code ChargeType} established. The foreign key still exists in the
     * database.
     */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** Set on the row that restores stock rather than removing it. Mirrors the journal's own link. */
    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    /** For JPA only. */
    protected StockWriteOff() {
    }

    StockWriteOff(InventoryLot lot, SerializedUnit serializedUnit, Quantity quantity,
            UnitCost unitCost, WriteOffReason reason, LocalDate writeOffDate, String note,
            Long journalEntryId, Long reversalOfId) {
        this.lot = lot;
        this.serializedUnit = serializedUnit;
        this.quantity = quantity.value();
        this.unitCost = unitCost.value();
        this.unitCostCurrency = unitCost.currency().getCurrencyCode();
        this.reason = reason;
        this.writeOffDate = writeOffDate;
        this.note = note;
        this.journalEntryId = journalEntryId;
        this.reversalOfId = reversalOfId;
    }

    Long getId() {
        return id;
    }

    InventoryLot getLot() {
        return lot;
    }

    /** Null for a pooled write-off. */
    SerializedUnit getSerializedUnit() {
        return serializedUnit;
    }

    Quantity getQuantity() {
        return Quantity.of(quantity);
    }

    /** What the lot was carried at when this write-off happened. Never recomputed from the lot. */
    UnitCost getUnitCost() {
        return new UnitCost(unitCost, java.util.Currency.getInstance(unitCostCurrency));
    }

    WriteOffReason getReason() {
        return reason;
    }

    LocalDate getWriteOffDate() {
        return writeOffDate;
    }

    String getNote() {
        return note;
    }

    /** Null exactly when the stock was carried at zero — see the class comment. */
    Long getJournalEntryId() {
        return journalEntryId;
    }

    Long getReversalOfId() {
        return reversalOfId;
    }

    boolean isReversal() {
        return reversalOfId != null;
    }

    boolean isSerialized() {
        return serializedUnit != null;
    }
}
