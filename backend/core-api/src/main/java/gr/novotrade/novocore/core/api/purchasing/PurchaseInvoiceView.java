package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A recorded supplier invoice.
 *
 * <p><strong>The link to the ledger is stored one way only</strong> — here, on the document — and the
 * other direction is a query, the same stance V15 took on the reversal link. A {@code source_id} on
 * {@code journal_entry} was considered and rejected: an entry from an immutable source cannot be
 * updated after it is posted, so the id would have to be known before the document existed, and
 * storing it on both sides would be two copies of one fact free to disagree. {@code JournalSource} on
 * the entry says what kind of document produced it; {@code findByJournalEntry} says which one.
 *
 * @param journalEntryId never null. Unlike a stock write-off, which posts nothing when the lot was
 *     carried at zero, an invoice always creates a payable — a supplier who invoices zero has not sent
 *     an invoice.
 * @param variance the purchase price variance this invoice posted, if any (ADR 0008). Positive when the
 *     supplier charged more than the goods were received at, negative when less, absent when nothing
 *     was matched or the prices agreed. Kept on the document because "which invoices moved the
 *     variance account, and by how much" is the question the account exists to answer.
 * @param reversedByInvoiceId the reversing document, if this one has been corrected. Stored the other
 *     way round — see {@link #reversalOfInvoiceId} — and queried back.
 */
public record PurchaseInvoiceView(
        long id,
        long supplierId,
        String supplierName,
        String supplierInvoiceNumber,
        LocalDate invoiceDate,
        String description,
        Money netTotal,
        Money vatTotal,
        Money grossTotal,
        Money variance,
        long journalEntryId,
        Long reversalOfInvoiceId,
        Long reversedByInvoiceId,
        List<PurchaseInvoiceLineView> lines) {

    public PurchaseInvoiceView {
        Objects.requireNonNull(supplierName, "supplierName");
        Objects.requireNonNull(supplierInvoiceNumber, "supplierInvoiceNumber");
        Objects.requireNonNull(invoiceDate, "invoiceDate");
        Objects.requireNonNull(netTotal, "netTotal");
        Objects.requireNonNull(vatTotal, "vatTotal");
        Objects.requireNonNull(grossTotal, "grossTotal");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    /** True when this document is the correction of another rather than an invoice in its own right. */
    public boolean isReversal() {
        return reversalOfInvoiceId != null;
    }

    public boolean isReversed() {
        return reversedByInvoiceId != null;
    }

    /** True when this invoice still counts — neither a reversal nor reversed. */
    public boolean isInForce() {
        return !isReversal() && !isReversed();
    }

    /** The inventory lines still waiting on a delivery — ADR 0004's open receiving amount. */
    public List<PurchaseInvoiceLineView> linesAwaitingDelivery() {
        return lines.stream().filter(PurchaseInvoiceLineView::isAwaitingDelivery).toList();
    }

    public Optional<Money> varianceIfAny() {
        return variance == null || variance.isZero() ? Optional.empty() : Optional.of(variance);
    }
}
