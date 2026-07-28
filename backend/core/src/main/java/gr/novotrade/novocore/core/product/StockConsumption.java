package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.ledger.JournalSource;
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
import java.util.List;

/**
 * Stock leaving as a cost of sale, consumed FIFO — as against the write-off's loss.
 *
 * <p><strong>Why it has lines and {@link StockWriteOff} does not.</strong> A write-off always names
 * its lot, so there is exactly one and the record needs no children. A consumption asks for a quantity
 * of a <em>product</em> and FIFO decides which lots answer it, so which lots and how much of each is
 * precisely what this record exists to remember — brief §6's "one line per lot consumed", which is
 * also how the journal entry is shaped.
 *
 * <p><strong>{@link #quantityFilled} is stored, and the shortfall is not.</strong> The shortfall is
 * requested minus filled, and a third column would be a number that has to agree with the other two.
 * The filled quantity is not derived from the lines either: a consumption that filled nothing has no
 * lines at all, and summing them would make it indistinguishable from a record nobody wrote.
 */
@Entity
@Table(name = "stock_consumption")
class StockConsumption extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_requested", nullable = false)
    private BigDecimal quantityRequested;

    /** Below {@link #quantityRequested} exactly when aggregate stock went negative (Q17). */
    @Column(name = "quantity_filled", nullable = false)
    private BigDecimal quantityFilled;

    @Column(name = "consumption_date", nullable = false)
    private LocalDate consumptionDate;

    /** Restricted to sources whose {@code mayConsumeStock()} is true, by CHECK and by the service. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private JournalSource source;

    @Column(name = "note", length = 500)
    private String note;

    /** Null when nothing was posted: everything consumed was free, or nothing could be filled. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    /** Set on the row that PUTS STOCK BACK — the correction of a consumption. */
    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    /**
     * Cascaded for {@code InventoryLot}'s reason: a consumption and its lines are one transaction, so
     * "the lines are what was consumed" holds by construction. No {@code REMOVE} and no orphan
     * removal — nothing here is ever deleted.
     */
    @OneToMany(mappedBy = "consumption", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<StockConsumptionLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected StockConsumption() {
    }

    StockConsumption(Product product, Quantity quantityRequested, Quantity quantityFilled,
            LocalDate consumptionDate, JournalSource source, String note, Long reversalOfId) {
        this.product = product;
        this.quantityRequested = quantityRequested.value();
        this.quantityFilled = quantityFilled.value();
        this.consumptionDate = consumptionDate;
        this.source = source;
        this.note = note;
        this.reversalOfId = reversalOfId;
    }

    StockConsumptionLine addLine(InventoryLot lot, Quantity quantity, UnitCost unitCost) {
        StockConsumptionLine line =
                new StockConsumptionLine(this, lines.size(), lot, quantity, unitCost);
        lines.add(line);
        return line;
    }

    void postedAs(Long entryId) {
        this.journalEntryId = entryId;
    }

    Long getId() {
        return id;
    }

    Product getProduct() {
        return product;
    }

    Quantity getQuantityRequested() {
        return Quantity.of(quantityRequested);
    }

    Quantity getQuantityFilled() {
        return Quantity.of(quantityFilled);
    }

    /** What FIFO could not fill. Derived, never stored — see the class comment. */
    Quantity getShortfall() {
        return getQuantityRequested().minus(getQuantityFilled());
    }

    LocalDate getConsumptionDate() {
        return consumptionDate;
    }

    JournalSource getSource() {
        return source;
    }

    String getNote() {
        return note;
    }

    Long getJournalEntryId() {
        return journalEntryId;
    }

    Long getReversalOfId() {
        return reversalOfId;
    }

    boolean isReversal() {
        return reversalOfId != null;
    }

    /** Sorted here as well as by {@code @OrderBy}, for the reason {@code InventoryLot.getUnits} gives. */
    List<StockConsumptionLine> getLines() {
        return lines.stream()
                .sorted(Comparator.comparingInt(StockConsumptionLine::getLineNumber))
                .toList();
    }
}
