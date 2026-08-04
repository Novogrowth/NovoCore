package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.PageResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Recording sales — brief §6's Sales Invoice typed transaction.
 *
 * <p><strong>A sale posts two entries, not one.</strong> Revenue in one
 * ({@code SALES_INVOICE}-sourced: debit the settlement account or Accounts receivable, credit the
 * channel's Sales account or {@code Services} per line, credit Output VAT per class), and cost in
 * another, posted by {@code InventoryService} because reducing lots without posting is the
 * "half is worse than neither" problem the write-off settled. The two are linked by the consumption
 * record each line points at. This is an ordinary arrangement and it is stated here so nobody
 * rediscovers it as a surprise.
 *
 * <p><strong>The VAT dimension is supplied here</strong> — the step 7 obligation. It is
 * <em>optional</em> at the ledger, because the periodic VAT settlement entry legitimately has none, so
 * nothing forces an invoice to carry it and a VAT return assembled without it would silently
 * understate. Every Output VAT line this service posts carries its {@code VatDimension}: the class it
 * was computed at and the taxable base it was computed from. An exempt line posts no VAT line at all,
 * and its exemption reason lives on the invoice line — which makes exempt turnover by reason a
 * document-level report rather than a ledger-level one.
 *
 * <p><strong>Immutable once posted</strong> (Q13, ADR 0006). Correction is {@link #reverse}, which
 * posts the mirror, un-consumes the stock it consumed and releases the units it sold — all in one
 * transaction, because the ledger cannot see the last two. A <em>return</em> is not a correction and
 * is not this: that is a credit note.
 *
 * <p><strong>Permissions.</strong> {@link Section#SALES}. As everywhere else in the core, the section
 * is stated here and checked at the controller, because these methods are also called by the core's
 * own rules with no user in front of them. There is no controller yet.
 */
public interface SalesInvoiceService {

    // ---------------------------------------------------------------------------------------
    // Recording
    // ---------------------------------------------------------------------------------------

    /**
     * Records a sale: posts the revenue, takes the stock out, and marks any serialized units sold.
     *
     * <p>What happens per line, in the order a caller will hit it:
     *
     * <ul>
     *   <li>the VAT class is resolved by {@code VatClassPrecedence} — invoice line beats customer
     *       beats product — and the winning <em>level</em> is stored, so the rate stays explicable
     *       after the customer's override changes;
     *   <li>net is the quantity extended at the unit price, rounded once with the mode from
     *       {@code ledger.rounding.mode}; VAT is that net at the class's rate, rounded once;
     *   <li>a bundle is decomposed by {@code BundleService} and the component lines are
     *       <strong>stored</strong>, not recomputed later — brief §5's two linked levels;
     *   <li>stock leaves <strong>if the document type says it does</strong> — see below: pooled goods
     *       FIFO, a serial-tracked line by the units it named, each of which is marked {@code SOLD}
     *       and carries the customer and this line (brief §5);
     *   <li>the gross is compared against {@link NewSalesInvoice#statedTotal()} and the difference
     *       posted to {@code Rounding differences} — see below.
     * </ul>
     *
     * <h2>⚠️ The series decides three things, and two of them are refusals (R1b)</h2>
     *
     * <p>{@link NewSalesInvoice#seriesId()} is mandatory, and everything below follows from it rather
     * than from anything a caller states separately.
     *
     * <p><strong>1. Channel is derived, not settable.</strong> The invoice's {@link SalesChannel} is
     * the series' own — ΑΛΠW is the web series, so an invoice in it is a web sale by definition — and
     * it is what decides which {@code Sales} account the revenue credits. ⚠️ <strong>There is no
     * channel field on this request and therefore none on any screen that binds it, F5 included.</strong>
     *
     * <p><strong>2. A channel-less series is REFUSED.</strong> {@code sales_invoice.channel} is
     * {@code NOT NULL} and is deliberately <em>not</em> relaxed. A series with no channel is not a
     * sales channel at all — the self-supply series (Στοιχείο Αυτοπαράδοσης / Ιδιοχρησιμοποίησης)
     * are exactly that, since the customer is the issuer. Novocore cannot record one yet: self-supply
     * has no posting rule, the revenue leg has no candidate account, and which accounts carry each leg
     * is an accountant's question. <strong>The refusal is what keeps that question open</strong>
     * instead of papering over it with a widened column and a made-up channel. Roadmap step R3
     * answers it.
     *
     * <p><strong>3. Whether stock moves at all, and this is SILENT.</strong> The series names a
     * document type, and {@code affectsStock} on that type decides. ΑΛΠ and ΤΠΔΑ combine sale and
     * transport, so stock moves; a plain Τιμολόγιο is purely sales and <strong>creates no stock
     * consumption whatever</strong> — no row, no pending state, no marker, no warning. That is a
     * decision rather than an omission. ⚠️ <strong>Its consequence is a known limitation:</strong>
     * until a dispatch document exists (18b), stock figures are incomplete for every non-stock-moving
     * sales document, which is a routine share of real sales.
     *
     * <p><strong>An inactive series, or an active series of an inactive type, is refused</strong> —
     * the same rule products and VAT classes already follow, and the reason R1a left document-type
     * deactivation unguarded: nothing referenced a type until now.
     *
     * <p><strong>Stock never blocks a sale</strong> (Q17, ADR 0008). Pooled stock may go negative in
     * aggregate; the shortfall is recorded on the consumption and is queryable. A <em>serialized</em>
     * line is different and does refuse: a named machine either is on the shelf or is not, and there
     * is no aggregate for it to be negative in.
     *
     * <p><strong>Rounding, per document</strong> (Q15). A difference at or below
     * {@code ledger.rounding.threshold} posts automatically. A larger one <em>refuses the invoice</em>
     * unless {@link NewSalesInvoice#roundingAcceptedBy()} names who agreed to it — {@code CLAUDE.md}
     * rule 7's "suggest and require one-click confirmation", applied where the person who can explain
     * the difference is standing.
     *
     * @throws InvalidSalesInvoiceException if the customer or a product is unknown or inactive; if a
     *     line mixes currencies with the rest; if a line's shape disagrees with whether its product is
     *     serial-tracked; if a cash sale reaches {@code SettingKeys.CASH_PAYMENT_LIMIT}; if the
     *     document number duplicates an invoice that still stands; if a rounding difference above
     *     the threshold has not been accepted; or — R1b — if the series is inactive, its document
     *     type is inactive, or the series has no sales channel
     * @throws gr.novotrade.novocore.core.api.document.DocumentSeriesNotFoundException if
     *     {@link NewSalesInvoice#seriesId()} names no series
     * @throws gr.novotrade.novocore.core.api.tax.VatClassNotDeterminableException if a line's rate
     *     cannot be resolved at any level — there is deliberately no fallback rate
     * @throws gr.novotrade.novocore.core.api.inventory.InvalidStockConsumptionException if a named
     *     unit is not ours to sell — sold already, written off, or at a location stock may not be sold
     *     from. It propagates rather than being wrapped, because the refusal is about the stock and
     *     the message that says so is the useful one.
     * @throws gr.novotrade.novocore.core.api.inventory.SerializedUnitNotFoundException if a line names
     *     a serial number no unit holds
     */
    SalesInvoiceView record(NewSalesInvoice request);

    /**
     * What {@link #record} would produce for this request, <strong>computed and not posted</strong>.
     *
     * <p>Exists so an entry screen never has to do this arithmetic itself. VAT resolution throws
     * rather than assuming a rate, net and VAT are rounded once each at a mode read from Settings,
     * and the rounding difference is compared against a threshold that also lives in Settings — a
     * second implementation of any of that, in a language whose numbers are IEEE-754 doubles, is the
     * shape of the defect ADR 0015 exists to record.
     *
     * <p><strong>The same code produces both.</strong> This is not a parallel calculation that
     * agrees today; {@code preview} and {@code record} share one method, and a test drives both with
     * one request and compares every figure.
     *
     * <p>Nothing is written — and deliberately not by posting and rolling back, which would burn
     * document numbers and leave audit entries behind, since those are written {@code REQUIRES_NEW}
     * and survive a rolled-back caller.
     *
     * <p><strong>It refuses what {@code record} would refuse</strong>, with the same messages, which
     * is most of its value. The single exception is a rounding difference above the threshold with
     * nobody named as accepting it: {@code record} refuses that, and this reports it as
     * {@link SalesInvoicePreview#roundingNeedsAcceptance} so the screen can show the difference and
     * offer the acceptance rather than having to guess at it.
     *
     * @throws InvalidSalesInvoiceException for every refusal listed on {@link #record} except the
     *     unaccepted rounding difference
     * @throws gr.novotrade.novocore.core.api.tax.VatClassNotDeterminableException if a line's rate
     *     cannot be resolved at any level — which is exactly when an operator most needs to be told
     *     before the invoice is issued rather than after
     */
    SalesInvoicePreview preview(NewSalesInvoice request);

    /**
     * Reverses a sale that should not have been recorded, posting the mirror and undoing everything
     * it did.
     *
     * <p>Q13's reversal half, and it has to be here rather than at the ledger for the reason
     * {@code JournalSource.isReversibleThroughTheLedgerAlone()} names: reversing the money without
     * putting the stock back would leave goods off the shelf that the balance sheet no longer
     * carries, and without releasing a serialized unit would leave a machine sold to somebody on a
     * document that no longer exists.
     *
     * <p><strong>This is not how a return is handled.</strong> A customer bringing goods back is a
     * credit note: the sale happened, and rewriting it to say otherwise would delete a real event and
     * the VAT that was charged on it. Reversal is for an invoice recorded in error.
     *
     * @throws InvalidSalesInvoiceException if the invoice is itself a reversal, has already been
     *     reversed, has a credit note against it, has anything allocated against it, or if stock it
     *     consumed can no longer be put back
     * @throws SalesInvoiceNotFoundException if there is no such invoice
     */
    SalesInvoiceView reverse(long invoiceId, LocalDate reversalDate, String reason);

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    Optional<SalesInvoiceView> find(long invoiceId);

    /** @throws SalesInvoiceNotFoundException if absent */
    SalesInvoiceView require(long invoiceId);

    /** Every sale to one customer, oldest first. */
    List<SalesInvoiceView> ofCustomer(long customerId);

    /** Sales in a date range, both ends inclusive, oldest first. Includes reversals. */
    List<SalesInvoiceView> between(LocalDate from, LocalDate to);

    /**
     * One page of the sales in a date range — <strong>the form a screen should use</strong>.
     *
     * <p>{@link #between} returns every invoice in the range and is kept for the core's own callers
     * and for a report that genuinely needs the lot. This is what a table calls, because a quarter
     * of real invoicing is thousands of rows and a year is tens of thousands.
     *
     * <p>The ordering is total: whatever {@link SalesInvoiceSort} is chosen, the id breaks ties, so
     * successive pages cannot repeat a row or skip one — which an ordering on invoice date alone
     * genuinely can, since PostgreSQL may return tied rows in a different order per query.
     *
     * @throws IllegalArgumentException if the request names a sort this list does not offer, which
     *     is an internal failure: the routes bind the parameter to {@link SalesInvoiceSort} and
     *     refuse an unknown value before reaching here
     */
    PageResponse<SalesInvoiceView> pageBetween(LocalDate from, LocalDate to, PageRequest page);

    /** One page of the sales to one customer. Same ordering guarantee as {@link #pageBetween}. */
    PageResponse<SalesInvoiceView> pageOfCustomer(long customerId, PageRequest page);

    /** The invoice one journal entry belongs to — the queried direction of the stored link. */
    Optional<SalesInvoiceView> findByJournalEntry(long journalEntryId);

    /**
     * Invoices whose rounding difference somebody had to accept — <strong>Q15's query</strong>.
     *
     * <p>The compensating control for allowing the difference at all, and the same shape as
     * {@code consumptionsWithShortfall()} and {@code lotsAt(DAMAGED_GOODS)}: the model permits a state
     * that a human should look at, so there is a query that finds it. <strong>Deliberately a query
     * over a flag on the record rather than a review queue</strong> — a queue is a second copy of
     * state that has to be created when the condition arises and removed when it is resolved, and the
     * day those fall out of step it shows work already done or hides work that is not.
     */
    List<SalesInvoiceView> withAcceptedRoundingDifference(LocalDate from, LocalDate to);

    /** What the rounding differences in a period come to, netted. Either sign. */
    Money totalRoundingBetween(LocalDate from, LocalDate to);
}
