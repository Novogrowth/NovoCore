package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.security.Section;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Credit notes. <strong>Q26, answered: its own transaction type, not a negative sales invoice.</strong>
 *
 * <p>Three consequences of that answer, all visible here:
 *
 * <ul>
 *   <li><strong>It posts to contra-revenue</strong>, debiting the {@code Sales returns} account of the
 *       original invoice's channel rather than reducing that channel's {@code Sales} account. Step 3
 *       created three returns accounts precisely so return rate stays visible per channel, and netting
 *       a return into Sales would collapse exactly that.
 *   <li><strong>It references the invoice it corrects</strong>, and each line references the invoice
 *       line it credits — which is what supplies the rate, the product and the channel, so a credit
 *       note cannot credit at a rate the sale never charged.
 *   <li><strong>It is immutable once issued</strong> (Q13), the same policy as the invoice it
 *       corrects, for the same reason: it is a document that has been given to somebody else.
 * </ul>
 *
 * <p><strong>Returning stock is a return, not a reversal.</strong> When a line says the goods came
 * back, the quantity goes back into the lots it left, at the cost it left at, through
 * {@code InventoryService.returnConsumed}. That is deliberately <em>not</em>
 * {@code reverseConsumption}: reversal says the consumption should never have happened, can only
 * happen once, and posts an exact ledger mirror. A return says the sale was real and the goods came
 * back, may be partial, and may happen more than once against one sale.
 *
 * <p><strong>Permissions.</strong> {@link Section#SALES}, shared with the invoice — someone recording
 * a credit note has to be able to read the sale it corrects, or the reference cannot be made.
 */
public interface CreditNoteService {

    /**
     * Issues a credit note against a sale: posts the return and puts back any stock that came with it.
     *
     * <p>Posts debit {@code Sales returns} (the original invoice's channel) per line, debit
     * {@code Output VAT} per class carrying its {@code VatDimension}, credit {@code Accounts
     * receivable} with the gross. <strong>Always Accounts receivable</strong>, even when the original
     * invoice was born settled in cash: the money is owed back to the customer until it is actually
     * refunded, and posting the credit straight against the cash box would take money out of the till
     * that nobody handed over.
     *
     * <p>Rounding is compared per document exactly as it is on the invoice (Q15), including the
     * refusal above {@code ledger.rounding.threshold} until somebody accepts the difference.
     *
     * @throws InvalidCreditNoteException if the invoice is unknown, is a reversal or has been
     *     reversed; if a line credits more than its invoice line sold, or more than is left of it
     *     after earlier credit notes; if a line returns stock the sale never took out; if the document
     *     number duplicates a credit note that still stands; or if a rounding difference above the
     *     threshold has not been accepted
     * @throws SalesInvoiceNotFoundException if the invoice or one of the referenced lines is unknown
     */
    CreditNoteView issue(NewCreditNote request);

    /**
     * What {@link #issue} would produce for this request, <strong>computed and not posted</strong>.
     *
     * <p>The counterpart of {@code SalesInvoiceService.preview}, with the same guarantees: produced
     * by the code {@code issue} itself runs, so it cannot drift from what gets posted; it writes
     * nothing, and not by rolling back; and it refuses what {@code issue} refuses, with the single
     * exception of an unaccepted rounding difference, which it reports as
     * {@link CreditNotePreview#roundingNeedsAcceptance} so a screen can offer the acceptance.
     *
     * <p>Worth previewing in its own right rather than treating as a variant of the invoice: a
     * credit line credits back <strong>the VAT the sale actually charged</strong>, read off the
     * invoice line, rather than what the precedence rule would decide today — the customer's
     * override may have changed since. A client could not reproduce that, and would silently return
     * the wrong VAT on any customer whose rate has moved.
     *
     * @throws InvalidCreditNoteException for every refusal listed on {@link #issue} except the
     *     unaccepted rounding difference — including a line crediting more than its invoice line
     *     has left to credit, which depends on every credit note already issued against it
     * @throws SalesInvoiceNotFoundException if the invoice named does not exist
     */
    CreditNotePreview preview(NewCreditNote request);

    /**
     * Reverses a credit note issued in error, posting the mirror and taking back out any stock it put
     * back.
     *
     * <p>Not reversible through the ledger alone, for the sales invoice's reason: the credit note may
     * have restored stock, and reversing only the money would leave the goods on the shelf with
     * nothing carrying them.
     *
     * @throws InvalidCreditNoteException if it is itself a reversal, has already been reversed, has
     *     anything allocated against it, or if stock it restored has since moved on
     * @throws CreditNoteNotFoundException if there is no such credit note
     */
    CreditNoteView reverse(long creditNoteId, LocalDate reversalDate, String reason);

    Optional<CreditNoteView> find(long creditNoteId);

    /** @throws CreditNoteNotFoundException if absent */
    CreditNoteView require(long creditNoteId);

    /** Every credit note against one sale, oldest first. */
    List<CreditNoteView> againstInvoice(long salesInvoiceId);

    /** Every credit note for one customer, oldest first. */
    List<CreditNoteView> ofCustomer(long customerId);

    /** Credit notes in a date range, both ends inclusive, oldest first. Includes reversals. */
    List<CreditNoteView> between(LocalDate from, LocalDate to);

    Optional<CreditNoteView> findByJournalEntry(long journalEntryId);
}
