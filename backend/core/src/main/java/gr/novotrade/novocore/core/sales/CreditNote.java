package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.shared.Money;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

/**
 * A credit note. <strong>Q26: its own transaction type, not a negative sales invoice.</strong>
 *
 * <p>It references the invoice it corrects, which supplies the customer and the channel and therefore
 * which {@code Sales returns} account it debits. Immutable once issued (Q13) — the same policy as the
 * invoice it corrects, for the same reason: it is a document that has been given to somebody else.
 */
@Entity
@Table(name = "credit_note")
class CreditNote extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_invoice_id", nullable = false)
    private Long salesInvoiceId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "document_number", nullable = false, length = 60)
    private String documentNumber;

    @Column(name = "credit_note_date", nullable = false)
    private LocalDate creditNoteDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "stated_total")
    private BigDecimal statedTotal;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "stated_total_currency", length = 3)
    private String statedTotalCurrency;

    @Column(name = "rounding_amount", nullable = false)
    private BigDecimal roundingAmount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "rounding_amount_currency", nullable = false, length = 3)
    private String roundingAmountCurrency;

    @Column(name = "rounding_needed_review", nullable = false)
    private boolean roundingNeededReview;

    @Column(name = "rounding_accepted_by", length = 100)
    private String roundingAcceptedBy;

    @Column(name = "rounding_accepted_at")
    private Instant roundingAcceptedAt;

    @Column(name = "rounding_note", length = 500)
    private String roundingNote;

    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @OneToMany(mappedBy = "creditNote", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<CreditNoteLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected CreditNote() {
    }

    CreditNote(long salesInvoiceId, long customerId, String documentNumber,
            LocalDate creditNoteDate, String description, Money statedTotal, Money roundingAmount,
            boolean roundingNeededReview, String roundingAcceptedBy, Instant roundingAcceptedAt,
            String roundingNote, Long reversalOfId) {
        this.salesInvoiceId = salesInvoiceId;
        this.customerId = customerId;
        this.documentNumber = documentNumber;
        this.creditNoteDate = creditNoteDate;
        this.description = description;
        this.statedTotal = statedTotal == null ? null : statedTotal.amount();
        this.statedTotalCurrency =
                statedTotal == null ? null : statedTotal.currency().getCurrencyCode();
        this.roundingAmount = roundingAmount.amount();
        this.roundingAmountCurrency = roundingAmount.currency().getCurrencyCode();
        this.roundingNeededReview = roundingNeededReview;
        this.roundingAcceptedBy = roundingAcceptedBy;
        this.roundingAcceptedAt = roundingAcceptedAt;
        this.roundingNote = roundingNote;
        this.reversalOfId = reversalOfId;
    }

    CreditNoteLine addLine(CreditNoteLine line) {
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

    Long getSalesInvoiceId() {
        return salesInvoiceId;
    }

    Long getCustomerId() {
        return customerId;
    }

    String getDocumentNumber() {
        return documentNumber;
    }

    LocalDate getCreditNoteDate() {
        return creditNoteDate;
    }

    String getDescription() {
        return description;
    }

    Money getStatedTotal() {
        return statedTotal == null
                ? null : Money.of(statedTotal, Currency.getInstance(statedTotalCurrency));
    }

    Money getRoundingAmount() {
        return Money.of(roundingAmount, Currency.getInstance(roundingAmountCurrency));
    }

    boolean isRoundingNeededReview() {
        return roundingNeededReview;
    }

    String getRoundingAcceptedBy() {
        return roundingAcceptedBy;
    }

    Instant getRoundingAcceptedAt() {
        return roundingAcceptedAt;
    }

    String getRoundingNote() {
        return roundingNote;
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

    List<CreditNoteLine> getLines() {
        return lines.stream()
                .sorted(Comparator.comparingInt(CreditNoteLine::getLineNumber))
                .toList();
    }

    /** What is owed back to the customer for this document — the sum of its lines, gross. */
    Money grossTotal() {
        Money total = Money.zero(Currency.getInstance(roundingAmountCurrency));
        for (CreditNoteLine line : lines) {
            total = total.plus(line.getNetAmount()).plus(line.getVatAmount());
        }
        return total.plus(getRoundingAmount());
    }
}
