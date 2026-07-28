package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A freight or duty cost allocated out of {@code Freight / Landed Cost — Unallocated} and into the
 * lots it delivered — brief §4, Q18, ADR 0010.
 *
 * <p>Immutable once posted, corrected by a reversing document ({@link #reversalOfId}) that carries no
 * lines of its own: the original's lines hold the per-unit increment each lot took, which is exactly
 * what a reversal has to give back, so duplicating them would give one allocation two statements of
 * itself.
 *
 * <p><strong>The source is a real association</strong>, unlike {@code PurchaseInvoice}'s supplier,
 * because {@code PurchaseInvoiceLine} is in this package — the same slice, the same reason
 * {@code UnitOfMeasure} is an association from {@code Product} and a VAT class is not.
 */
@Entity
@Table(name = "freight_allocation")
class FreightAllocation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The freight cost being allocated: one expense line pointed at the unallocated account. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_invoice_line_id", nullable = false)
    private PurchaseInvoiceLine sourceLine;

    @Column(name = "allocation_date", nullable = false)
    private LocalDate allocationDate;

    @Column(name = "description", length = 500)
    private String description;

    /** Never null: an allocation always debits something and credits something. */
    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @OneToMany(mappedBy = "allocation", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<FreightAllocationLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected FreightAllocation() {
    }

    FreightAllocation(PurchaseInvoiceLine sourceLine, LocalDate allocationDate, String description,
            Long reversalOfId) {
        this.sourceLine = sourceLine;
        this.allocationDate = allocationDate;
        this.description = description;
        this.reversalOfId = reversalOfId;
    }

    FreightAllocationLine addLine(FreightAllocationLine line) {
        line.attachTo(this, lines.size());
        lines.add(line);
        return line;
    }

    void postedAs(long entryId) {
        this.journalEntryId = entryId;
    }

    Long getId() {
        return id;
    }

    PurchaseInvoiceLine getSourceLine() {
        return sourceLine;
    }

    LocalDate getAllocationDate() {
        return allocationDate;
    }

    String getDescription() {
        return description;
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
    List<FreightAllocationLine> getLines() {
        return lines.stream()
                .sorted(Comparator.comparingInt(FreightAllocationLine::getLineNumber))
                .toList();
    }
}
