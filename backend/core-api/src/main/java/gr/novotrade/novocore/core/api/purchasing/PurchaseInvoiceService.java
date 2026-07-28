package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Supplier invoices: recording them, correcting them, and reading the GR/IR position they leave.
 *
 * <p><strong>Recorded, never issued.</strong> Prosvasis Go issues our sales documents until roadmap
 * phase 11, and our suppliers issue theirs. NovoCore's job here is to say what we owe and why, so
 * every invoice is somebody else's document with its own number — which is what makes it immutable
 * (Q13) and correctable only by reversal.
 *
 * <p><strong>What an invoice posts</strong> (ADR 0004, Q14, ADR 0008), all in one entry:
 *
 * <ul>
 *   <li>each inventory line debits <strong>GR/IR clearing</strong>, against the supplier — never
 *       Inventory directly, because the Goods Receipt is the inventory event;
 *   <li>each expense line debits the account it names;
 *   <li>each matched quantity clears GR/IR at the <em>receipt's</em> cost, and the difference against
 *       the invoice price posts to <strong>Purchase price variance</strong>. The lot is not touched;
 *   <li>VAT debits <strong>Input VAT</strong>, one line per VAT class, carrying the class and the base
 *       it was computed on (Q14). A reverse-charged line also credits <strong>Output VAT</strong> for
 *       the same class and base, which nets to zero in cash and leaves both figures declarable;
 *   <li>the whole gross credits <strong>Accounts payable</strong>, against the supplier.
 * </ul>
 *
 * <p><strong>Matching happens when the second document is created, and not afterwards.</strong> If the
 * goods arrived first, this invoice names the receipt lines it pays for; if the invoice arrived first,
 * the Goods Receipt names these lines instead. There is deliberately no separate "match these later"
 * operation: it would need its own journal entry belonging to no document, and an unmatched GR/IR
 * balance is already exactly what ADR 0004 says it is — a real discrepancy for phase 8's Clearing
 * Checks to surface, which {@link #linesAwaitingDelivery()} is the query for.
 *
 * <p><strong>Permissions.</strong> Everything here is {@link Section#PURCHASING}, checked at the
 * controller as elsewhere. There is no controller yet.
 */
public interface PurchaseInvoiceService {

    // ---------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------

    /**
     * Records a supplier's invoice and posts it, in one transaction.
     *
     * <p>Validation, in the order a caller will hit it:
     *
     * <ul>
     *   <li>the supplier exists and is active;
     *   <li>this supplier has not already sent us an invoice with this number — a duplicate is the
     *       hazard the myDATA import makes routine rather than theoretical;
     *   <li>every line is in one currency (ADR 0005 never converts);
     *   <li>an inventory line's product exists, is active, and is stocked — a service or a bundle
     *       cannot be received, so it cannot be bought as stock either;
     *   <li>an expense line's account exists, is active, and is not a Control account: a control
     *       account's balance is the sub-ledger it summarises, and a free-hand line into one is how a
     *       control account stops reconciling;
     *   <li>every line's VAT class or exemption reason exists and is active;
     *   <li>reverse charge agrees with the supplier's {@code VatStatus} — required for an intra-EU B2B
     *       supplier, refused for any other, and never inferred either way;
     *   <li>every match names a real, unreversed goods receipt line for the same product and supplier,
     *       and does not claim more than that line has left unmatched.
     * </ul>
     *
     * @throws InvalidPurchaseInvoiceException for any of the above
     * @throws gr.novotrade.novocore.core.api.supplier.SupplierNotFoundException if the supplier is
     *     unknown
     * @throws GoodsReceiptNotFoundException if a match names no such receipt line
     */
    PurchaseInvoiceView record(NewPurchaseInvoice request);

    /**
     * Reverses a posted invoice: posts the mirror entry and records a reversing document.
     *
     * <p>Q13's only correction route for an invoice, and {@code JournalService.reverse} refuses this
     * source outright — reversing the entry alone would leave the GR/IR matches this invoice created
     * still claiming to have cleared something, and the variance it posted still attributed to a
     * document that no longer stands.
     *
     * <p>The reversing document carries no lines of its own. Everything a reader needs is on the
     * original, which stays exactly as it was posted; duplicating its lines would give one invoice two
     * statements of the same purchase.
     *
     * <p><strong>Refused when the matched stock has moved on.</strong> If a lot this invoice priced
     * has since been consumed or written off, the variance is already inside posted COGS, and a
     * reversal claiming otherwise would be the retroactive re-costing ADR 0008 exists to prevent. The
     * correction is then a supplier credit note, which is step 9's.
     *
     * @throws InvalidPurchaseInvoiceException if the invoice is itself a reversal, has already been
     *     reversed, or its matched lots have moved on
     * @throws PurchaseInvoiceNotFoundException if there is no such invoice
     */
    PurchaseInvoiceView reverse(long invoiceId, LocalDate reversalDate, String reason);

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    Optional<PurchaseInvoiceView> find(long invoiceId);

    /** @throws PurchaseInvoiceNotFoundException if absent */
    PurchaseInvoiceView require(long invoiceId);

    /** Every invoice from one supplier, oldest first. */
    List<PurchaseInvoiceView> ofSupplier(long supplierId);

    /** Invoices with a document date in the range, both ends inclusive, oldest first. */
    List<PurchaseInvoiceView> between(LocalDate from, LocalDate to);

    /**
     * The invoice this journal entry came from, if any.
     *
     * <p>The reverse of the stored link — see {@link PurchaseInvoiceView}'s note on why the entry
     * carries no {@code source_id}. A ledger reader with an entry id asks each document service in
     * turn; there is deliberately no generic "what produced this entry" across all types, because a
     * union over document tables would have to be edited every time a transaction type is added and
     * would fail silently when somebody forgot.
     */
    Optional<PurchaseInvoiceView> findByJournalEntry(long journalEntryId);

    // ---------------------------------------------------------------------------------------
    // The GR/IR position — ADR 0004
    // ---------------------------------------------------------------------------------------

    /**
     * Invoice lines paid for but not yet delivered, oldest first — <strong>invoiced, not received</strong>.
     *
     * <p>One of the two halves of a non-zero GR/IR balance, and the reason ADR 0004 calls that balance
     * meaningful rather than noise. Phase 8's Clearing Checks reads this; so does anyone chasing a
     * supplier for goods already paid for.
     */
    List<PurchaseInvoiceLineView> linesAwaitingDelivery();

    /** Every match this invoice made against a delivery, with the variance each one posted. */
    List<GrIrMatchView> matchesOf(long invoiceId);

    /**
     * What the purchase price variance account has absorbed over a period, by invoice.
     *
     * <p>Here rather than in a report for {@code vatTotals}'s reason: an account nothing reads back
     * from is indistinguishable from one nobody thought about, and the whole argument for posting the
     * difference to its own account (ADR 0008) is that it stays visible.
     */
    List<PurchaseInvoiceView> variancesBetween(LocalDate from, LocalDate to);

    /** The total variance over a period — the figure the account's balance should agree with. */
    Money totalVarianceBetween(LocalDate from, LocalDate to);
}
