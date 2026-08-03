package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import gr.novotrade.novocore.core.api.sales.TransmissionStatus;
import gr.novotrade.novocore.core.api.shared.Money;
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
 * A sale, recorded rather than issued.
 *
 * <p>Immutable once posted (Q13, ADR 0006), for the purchase invoice's reason doubled: Prosvasis Go
 * issued the document until roadmap phase 11, so it exists outside NovoCore <em>and</em> it has been
 * given to a customer. Correction is a reversing document; a customer bringing goods back is a credit
 * note, which is a different fact entirely.
 *
 * <p><strong>No open amount and no paid flag.</strong> What is still owed is the gross less what has
 * been allocated against it, computed on read by {@code SettlementService} — the same stance that
 * keeps a balance off {@code account} and a stock figure off {@code product}.
 */
@Entity
@Table(name = "sales_invoice")
class SalesInvoice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 30)
    private SalesChannel channel;

    /** Decides which account the invoice debits, and therefore whether it is an open item at all. */
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_method", nullable = false, length = 30)
    private SettlementMethod settlementMethod;

    /** The number the issuing system printed. Unique among invoices that still stand. */
    @Column(name = "document_number", nullable = false, length = 60)
    private String documentNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "description", length = 500)
    private String description;

    /** What the source document says the gross is. Null when nothing external stated one. */
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

    /**
     * Q15's remainder. Stored rather than derived from the threshold on read, because
     * {@code ledger.rounding.threshold} is operator-changeable and a later change must not
     * retroactively alter which invoices somebody had to agree to.
     */
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

    // -------------------------------------------------------------------------------------------
    // Statutory identifiers — ADR 0016. RECORDED, never obtained.
    //
    // ⚠️ These are CORE fields and that is not a violation of "external reference IDs never live on
    // core entities". Go's internal document id identifies a row in GO'S database and is meaningless
    // once Go is replaced; the ΜΑΡΚ identifies THIS document to the Greek state, permanently, and is
    // printed on the document itself. The test that separates them is whether the value survives the
    // vendor being replaced.
    //
    // ⚠️ R1a is SCHEMA AND VALIDATION ONLY. Nothing here writes any of these — there is no setter,
    // because there is nothing in this phase entitled to supply one. Novocore learns a ΜΑΡΚ from
    // Prosvasis Go (step 18) or from a Πάροχος (step 40), and until an adapter exists, a Java path
    // that could set one would be a path with no legitimate caller.
    // -------------------------------------------------------------------------------------------

    /** AADE's ΜΑΡΚ. {@code bigint} because AADE's own {@code response-v2.0.1.xsd} types it long. */
    @Column(name = "mark")
    private Long mark;

    @Column(name = "uid", length = 100)
    private String uid;

    @Column(name = "qr_url", length = 500)
    private String qrUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "transmission_status", nullable = false, length = 30)
    private TransmissionStatus transmissionStatus = TransmissionStatus.UNKNOWN;

    /**
     * The numbering series. Null through R1a — every existing invoice predates series, and R1a
     * cannot change what any existing test asserts. R1b makes it the source of the channel.
     */
    @Column(name = "series_id")
    private Long seriesId;

    @OneToMany(mappedBy = "invoice", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<SalesInvoiceLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected SalesInvoice() {
    }

    SalesInvoice(long customerId, SalesChannel channel, SettlementMethod settlementMethod,
            String documentNumber, LocalDate invoiceDate, String description, Money statedTotal,
            Money roundingAmount, boolean roundingNeededReview, String roundingAcceptedBy,
            Instant roundingAcceptedAt, String roundingNote, Long reversalOfId) {
        this.customerId = customerId;
        this.channel = channel;
        this.settlementMethod = settlementMethod;
        this.documentNumber = documentNumber;
        this.invoiceDate = invoiceDate;
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

    SalesInvoiceLine addLine(SalesInvoiceLine line) {
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

    Long getCustomerId() {
        return customerId;
    }

    SalesChannel getChannel() {
        return channel;
    }

    SettlementMethod getSettlementMethod() {
        return settlementMethod;
    }

    String getDocumentNumber() {
        return documentNumber;
    }

    LocalDate getInvoiceDate() {
        return invoiceDate;
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

    /** Sorted here as well as by {@code @OrderBy}, for the reason {@code InventoryLot.getUnits} gives. */
    List<SalesInvoiceLine> getLines() {
        return lines.stream()
                .sorted(Comparator.comparingInt(SalesInvoiceLine::getLineNumber))
                .toList();
    }

    /** What the customer owes for this document — the sum of its lines, gross. */
    Money grossTotal() {
        Money total = Money.zero(Currency.getInstance(roundingAmountCurrency));
        for (SalesInvoiceLine line : lines) {
            total = total.plus(line.getNetAmount()).plus(line.getVatAmount());
        }
        return total.plus(getRoundingAmount());
    }
    Long getMark() {
        return mark;
    }

    String getUid() {
        return uid;
    }

    String getQrUrl() {
        return qrUrl;
    }

    TransmissionStatus getTransmissionStatus() {
        return transmissionStatus;
    }

    Long getSeriesId() {
        return seriesId;
    }

}
