package gr.novotrade.novocore.core.api.codification;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;

/**
 * One AADE myDATA invoice type — a row of the statutory codification.
 *
 * @param code AADE's own code, verbatim from {@code SimpleTypes-v2.0.1.xsd}: {@code "1.1"},
 *     {@code "13.30"}, {@code "4"}. A string and not a number, because {@code 13.3} and
 *     {@code 13.30} are different codes and no numeric type distinguishes them.
 * @param description the Greek description from annex 8.1, read from a rasterised page. ⚠️ For
 *     codes {@code 4} and {@code 12} the annex's description cell is <strong>empty</strong>, and
 *     the only text AADE gives them is the group label {@code "Για Μελλοντική Χρήση"} — For Future
 *     Use. That label is what they carry, read from the artefact rather than invented.
 * @param active whether AADE still publishes this code. ⚠️ <strong>Not</strong> whether this
 *     business issues documents of this type — that is what the sales and purchase document-type
 *     lists answer, and they are separate tables for exactly this reason.
 */
public record AadeInvoiceTypeView(
        long id,
        @Mandatory String code,
        @Mandatory String description,
        @Mandatory AadeInvoiceGroup group,
        boolean active) {

    public AadeInvoiceTypeView {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(group, "group");
    }

    // ⚠️ THERE IS DELIBERATELY NO `issuedByUs()` HERE, AND ITS ABSENCE COST A DEFECT TO LEARN.
    //
    // R1a shipped one — a one-line delegate to AadeInvoiceGroup.issuedByUs(), which THROWS for the
    // six ENTITY_ADJUSTING codes because they are neither issued nor received. Every service-layer
    // test passed. `GET /api/aade-invoice-types` answered **500 "Failed to write request"**, because
    // Jackson serialises a record's no-arg public accessors as properties and called it on all 55
    // rows.
    //
    // Two things about that are worth keeping:
    //
    //   1. ⚠️ **A record this codebase serialises must not carry an accessor that can throw.** The
    //      exception is correct — asking a payroll adjusting entry which party issued it IS a
    //      programming error — but a serialiser asks every accessor, so "only wrong callers reach
    //      it" stopped being true the moment the record went on the wire.
    //   2. ⚠️ **The spec and the wire disagreed, silently.** `OpenApiSchema` describes RECORD
    //      COMPONENTS, so it documented five properties; Jackson would have written six. The 500 is
    //      what made that visible, and a derived accessor that merely returned a value would have
    //      added an undocumented field to the response and nothing would have said so.
    //
    // The question still has an answer and it lives where it belongs: `view.group().issuedByUs()`.
}
