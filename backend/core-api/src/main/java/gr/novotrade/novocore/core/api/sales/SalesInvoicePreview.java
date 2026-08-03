package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.util.List;
import java.util.Objects;

/**
 * What {@link SalesInvoiceService#record} <em>would</em> produce, computed and not posted.
 *
 * <h2>Why this exists: the arithmetic has exactly one implementation</h2>
 *
 * <p>An entry screen has to show a running total, the VAT per line and the difference against the
 * document in the operator's hand — before anything is recorded. The obvious way to get that is for
 * the client to do the sums. It must not:
 *
 * <ul>
 *   <li>VAT resolution is {@code VatClassPrecedence} — invoice line beats customer beats product —
 *       and it <strong>throws rather than assuming 24%</strong>, deliberately, because a silent
 *       default produces a plausible invoice at a rate nobody chose. A second implementation in
 *       another language would either reproduce that refusal exactly or quietly become the fallback
 *       rate this system has never had.
 *   <li>Rounding is compared per document against a threshold that lives in Settings, and the
 *       arithmetic is {@code BigDecimal} at a mode read from Settings. In a browser it would be
 *       IEEE-754 doubles — the shape of the defect Q45 (ADR 0015) exists to record.
 * </ul>
 *
 * <p><strong>So this is produced by the same code path as the real thing.</strong> Not a parallel
 * implementation that agrees today: {@code record} and {@code preview} call one method, and a test
 * drives both with the same request and asserts every figure matches. A preview computed separately
 * would drift, and it would drift <em>silently</em> — the operator sees a correct-looking total and
 * the posted document says something else.
 *
 * <h2>It posts nothing, and is not a rolled-back post</h2>
 *
 * <p>Nothing is written, and in particular this is <strong>not</strong> implemented by recording and
 * rolling back. That would burn document numbers, interact badly with the deferred debits=credits
 * trigger, and — the decisive one — leave real audit entries behind, because {@code AuditLogService}
 * writes with {@code REQUIRES_NEW} and its entries are <em>proven</em> to survive a rolled-back
 * caller. An audit log recording invoices that were never created is worse than no preview.
 *
 * <h2>Refusals come back as refusals, with one exception</h2>
 *
 * <p>An inactive customer, a duplicate document number, mixed currencies, an unresolvable VAT rate
 * or a cash sale over the limit all raise here exactly what they raise on {@code record} — that is
 * most of what a preview is for.
 *
 * <p>The exception is {@link #roundingNeedsAcceptance}. A difference above the threshold
 * <em>refuses</em> the real invoice until somebody accepts it, and an entry screen has to be able to
 * show the operator the difference and offer the acceptance before they submit. Refusing the preview
 * as well would make that flow impossible to build. So it is reported, and the refusal still happens
 * on {@code record} if the acceptance is not supplied.
 *
 * <h2>Stock is deliberately absent</h2>
 *
 * <p>No stock check and no shortfall warning. Stock never blocks a sale (Q17, ADR 0008), so it could
 * only ever be advisory — and getting it right means decomposing bundles into components and
 * resolving named serial numbers, either of which a wrong answer would mislead about. An advisory
 * figure that is sometimes wrong is worse than none.
 *
 * @param lines one entry per requested line, in the order supplied, each carrying the rate that was
 *     resolved and <strong>which level supplied it</strong> — so "why is this line at 13%?" is
 *     answerable on screen rather than after the fact
 * @param net the lines' net total
 * @param vat the lines' VAT total
 * @param gross {@code net + vat} — what the lines come to before any rounding difference
 * @param statedTotal what the caller said the document says, echoed back, or null if none was given
 * @param roundingDifference {@code statedTotal - gross}, zero when they agree or when no stated
 *     total was supplied. Either sign.
 * @param roundingThreshold the setting this difference was compared against, returned so a screen
 *     can explain the decision rather than restate the number from somewhere else
 * @param roundingNeedsAcceptance true when the difference exceeds the threshold and no acceptance
 *     was supplied. <strong>{@code record} will refuse this request as it stands</strong>; supply
 *     {@code roundingAcceptedBy} and a note, or fix the lines
 * @param receivable {@code gross + roundingDifference} — what Accounts receivable would be debited,
 *     which equals {@code statedTotal} whenever one was supplied, because AR has to agree with the
 *     document the customer is holding
 */
public record SalesInvoicePreview(
        @Mandatory List<SalesInvoicePreviewLine> lines,
        @Mandatory Money net,
        @Mandatory Money vat,
        @Mandatory Money gross,
        Money statedTotal,
        @Mandatory Money roundingDifference,
        @Mandatory Money roundingThreshold,
        boolean roundingNeedsAcceptance,
        @Mandatory Money receivable) {

    public SalesInvoicePreview {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        Objects.requireNonNull(net, "net");
        Objects.requireNonNull(vat, "vat");
        Objects.requireNonNull(gross, "gross");
        Objects.requireNonNull(roundingDifference, "roundingDifference");
        Objects.requireNonNull(roundingThreshold, "roundingThreshold");
        Objects.requireNonNull(receivable, "receivable");
    }
}
