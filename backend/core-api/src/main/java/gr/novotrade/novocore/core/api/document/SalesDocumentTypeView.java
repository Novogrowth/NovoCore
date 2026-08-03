package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;
import java.util.Optional;

/**
 * One of the business's own sales document types.
 *
 * <p>⚠️ <strong>This is not a statutory codification.</strong> The owner authors these rows through
 * ordinary CRUD; the AADE codification is {@code AadeInvoiceTypeView}, and this list merely points
 * at it. An earlier design collapsed the two and could not represent a document type with no AADE
 * code — see {@code StatutoryCodification} for what disproved it.
 *
 * @param affectsStock whether recording a document of this type moves stock at all. ⚠️
 *     <strong>Null means nobody has decided</strong>, not false. A {@code false} here reads as a
 *     decision that stock does not move and is indistinguishable from a field left alone — which
 *     matters, because R1b branches the consumption path on this value.
 * @param transfersStock whether the document also transfers stock to the counterparty rather than
 *     only consuming it. Null carries the same meaning as on {@link #affectsStock}.
 * @param requiresMydataTransmission whether a document of this type must reach AADE. False for the
 *     operational documents — Προσφορά, Παραγγελία — which are not tax documents at all.
 * @param aadeInvoiceTypeId the statutory type this maps to, or empty. ⚠️ <strong>Empty is a real
 *     answer and not missing data:</strong> six of the owner's nineteen document types have no AADE
 *     invoice type, because they are operational rather than tax documents. Never modelled as a
 *     sentinel row or an "N/A" code — inventing an AADE code is what the seeding rule forbids.
 * @param aadeInvoiceTypeCode the same type's code, carried so a screen never has to render a raw
 *     id. Null exactly when {@link #aadeInvoiceTypeId} is.
 * @param active ⚠️ false is also the state of a <em>draft</em> — a type created before its stock
 *     behaviour was decided. See {@link #isDraft()}.
 */
public record SalesDocumentTypeView(
        long id,
        @Mandatory String description,
        Boolean affectsStock,
        Boolean transfersStock,
        boolean requiresMydataTransmission,
        Long aadeInvoiceTypeId,
        String aadeInvoiceTypeCode,
        boolean active) {

    public SalesDocumentTypeView {
        Objects.requireNonNull(description, "description");
    }

    /** The statutory type, or empty where this document is operational rather than a tax document. */
    public Optional<Long> aadeInvoiceTypeIdIfAny() {
        return Optional.ofNullable(aadeInvoiceTypeId);
    }

    /**
     * True when the type exists but its stock behaviour has not been decided.
     *
     * <p>Such a type is necessarily inactive — the database refuses an active row with an
     * undecided flag — so this distinguishes "retired" from "not finished", two states that look
     * identical in a list and have entirely different fixes.
     */
    public boolean isDraft() {
        return affectsStock == null || transfersStock == null;
    }
}
