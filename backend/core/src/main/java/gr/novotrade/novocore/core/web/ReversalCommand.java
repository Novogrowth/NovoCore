package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.shared.Required;
import java.time.LocalDate;

/**
 * The body of every {@code POST /{id}/reversal} in this API.
 *
 * <p>One record rather than one per controller, because the six reversible documents — purchase
 * invoice, goods receipt, freight allocation, sales invoice, credit note, bank transfer — take
 * exactly the same two things, and their services all declare the same
 * {@code reverse(id, date, reason)} signature. Six copies of a two-field record is the kind of
 * duplication that stays right until one of them gains a field.
 *
 * <p><strong>The reason is not optional.</strong> A reversal that says nothing about why leaves the
 * ledger internally consistent and unexplainable, which is the worst of both — the services refuse a
 * blank one, and this record exists so every route asks for it in the same shape.
 *
 * <p>Note what is <em>not</em> here: no "delete" and no flag to suppress the mirror posting.
 * Documents are immutable (ADR 0006); a reversal creates a new entry and both documents stand.
 *
 * <p><strong>Both fields are required here rather than only in the services below</strong>, and this
 * one record is why the check is worth putting on a record at all: six routes bind it, so six 500s
 * became one statement. See {@link Required} for the defect that produced it.
 *
 * @param reversalDate the date the reversing entry posts on — not necessarily the original's
 * @param reason why, in words, for whoever reads the ledger later
 */
public record ReversalCommand(LocalDate reversalDate, String reason) {

    public ReversalCommand {
        Required.field(reversalDate, "reversalDate");
        Required.text(reason, "reason");
    }
}
