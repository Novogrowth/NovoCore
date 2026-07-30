package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Money;
import java.util.List;
import java.util.Objects;

/**
 * What {@link CreditNoteService#issue} <em>would</em> produce, computed and not posted.
 *
 * <p>The counterpart of {@link SalesInvoicePreview}, and it exists for the same reason: the credit
 * screen has to show a total and the VAT being returned before anything is issued, and a client
 * computing that itself would be a second implementation of arithmetic that must have exactly one.
 *
 * <h2>What a credit note computes that an invoice does not</h2>
 *
 * <p><strong>The rate is not re-resolved.</strong> A credit line credits back the VAT the original
 * sale actually charged, taken from the invoice line it names — not what {@code VatClassPrecedence}
 * would decide today, because the customer's override may have changed since the sale. That is a
 * rule a frontend could not reproduce even in principle: it would need the invoice line's stored
 * class and would be guessing at which one a request refers to.
 *
 * <p>The same applies to the ceiling on each line: a credit may not return more than the invoice
 * line sold, and what is left to credit depends on every credit note already issued against it.
 * That is a database question, and this is the answer to it.
 *
 * <h2>Same guarantees as the invoice preview</h2>
 *
 * <p>Produced by the same {@code compute()} both {@code issue} and {@code preview} call, so it
 * cannot drift from what gets posted — asserted, not assumed. It writes nothing, and deliberately
 * not by posting and rolling back. It refuses what {@code issue} refuses, with one exception:
 * {@link #roundingNeedsAcceptance} is reported rather than raised, so the screen can show the
 * difference and offer the acceptance before the operator submits.
 *
 * <p><strong>Stock restoration is not previewed.</strong> A credit note may put goods back, and
 * whether it does is the caller's own {@code stockReturned} flag rather than anything computed —
 * there is nothing to tell the operator that they did not just say. What returning stock into a
 * re-costed lot does to the ledger (ADR 0011's catch-up) is real but is not a figure on this
 * document.
 *
 * @param lines one entry per requested line, in the order supplied
 * @param net the lines' net total — what will be debited to the channel's {@code Sales returns}
 *     account rather than netted against revenue, so return rate stays visible per channel
 * @param vat the lines' VAT total, at the rates the original sale charged
 * @param gross {@code net + vat}
 * @param statedTotal what the caller said the document says, echoed back, or null if none was given
 * @param roundingDifference {@code statedTotal - gross}, zero when they agree or when no stated
 *     total was supplied. Either sign.
 * @param roundingThreshold the setting this difference was compared against
 * @param roundingNeedsAcceptance true when the difference exceeds the threshold and nobody has
 *     accepted it. <strong>{@code issue} will refuse this request as it stands.</strong>
 * @param payable {@code gross + roundingDifference} — what will be credited back, always to Accounts
 *     receivable even against a cash sale
 */
public record CreditNotePreview(
        List<CreditNotePreviewLine> lines,
        Money net,
        Money vat,
        Money gross,
        Money statedTotal,
        Money roundingDifference,
        Money roundingThreshold,
        boolean roundingNeedsAcceptance,
        Money payable) {

    public CreditNotePreview {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        Objects.requireNonNull(net, "net");
        Objects.requireNonNull(vat, "vat");
        Objects.requireNonNull(gross, "gross");
        Objects.requireNonNull(roundingDifference, "roundingDifference");
        Objects.requireNonNull(roundingThreshold, "roundingThreshold");
        Objects.requireNonNull(payable, "payable");
    }
}
