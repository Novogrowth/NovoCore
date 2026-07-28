package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A physical delivery — brief §6's verification step, and by ADR 0004 the event that creates
 * inventory lots.
 *
 * <p>Immutable (Q39, ADR 0008): the posting reflects a physical stock movement, so editing it would
 * change what the accounts say arrived without changing the lots that arrived. Corrected by a
 * reversing document, which carries no lines and un-receives the original's lots.
 *
 * <p>{@link #journalEntryId} is nullable, and the case is the write-off's: every line arrived at a
 * unit cost of zero, so nothing was capitalised and the ledger rightly refuses a zero-amount entry.
 */
@Entity
@Table(name = "goods_receipt")
class GoodsReceipt extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One delivery, one supplier — see {@code NewGoodsReceipt} for why this is not per line. */
    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "delivery_note_number", length = 60)
    private String deliveryNoteNumber;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @OneToMany(mappedBy = "receipt", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<GoodsReceiptLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected GoodsReceipt() {
    }

    GoodsReceipt(long supplierId, String deliveryNoteNumber, LocalDate receiptDate,
            String description, Long reversalOfId) {
        this.supplierId = supplierId;
        this.deliveryNoteNumber = deliveryNoteNumber;
        this.receiptDate = receiptDate;
        this.description = description;
        this.reversalOfId = reversalOfId;
    }

    GoodsReceiptLine addLine(GoodsReceiptLine line) {
        line.attachTo(this, lines.size());
        lines.add(line);
        return line;
    }

    void postedAs(Long entryId) {
        this.journalEntryId = entryId;
    }

    Long getId() {
        return id;
    }

    Long getSupplierId() {
        return supplierId;
    }

    String getDeliveryNoteNumber() {
        return deliveryNoteNumber;
    }

    LocalDate getReceiptDate() {
        return receiptDate;
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

    List<GoodsReceiptLine> getLines() {
        return lines.stream()
                .sorted(Comparator.comparingInt(GoodsReceiptLine::getLineNumber))
                .toList();
    }
}
