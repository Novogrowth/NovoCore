package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;
import java.util.Optional;

/**
 * One of the business's own purchase document types.
 *
 * <p>⚠️ <strong>Not a statutory codification</strong>, for the same reasons as
 * {@link SalesDocumentTypeView} — see that record and {@code StatutoryCodification}.
 *
 * @param affectsStock ⚠️ <strong>Meaningful on the purchase side, and not a copy of the sales
 *     column.</strong> The owner's own evidence is the clearest justification this column has
 *     anywhere: {@code 2062 ΤΔΑΑ} (Τιμολόγιο Δελτίο Αποστολής Αγοράς) is used daily and brings
 *     stock in with a payable behind it, while {@code 2041 Δελτίο Παραλαβής} is the exception case
 *     — a machine sent to a supplier for service and returned. <strong>That is a purchase document
 *     bringing stock IN with no payable behind it</strong>, which nothing else in the model can
 *     express.
 *     <p>Null means nobody has decided, not false.
 * @param transfersStock whether the document also transfers stock, e.g. goods sent out to a
 *     supplier. Null carries the same meaning as on {@link #affectsStock}.
 * @param requiresMydataTransmission whether a document of this type must reach AADE.
 * @param aadeInvoiceTypeId the statutory type this maps to, or empty where the document is
 *     operational — Δελτίο ποσοτικής παραλαβής, Δελτίο Παραλαβής, ΔΑ Αποστολής Σε Προμηθευτή.
 *     Empty is a real answer, never a sentinel row.
 * @param aadeInvoiceTypeCode the same type's code, so a screen never renders a raw id.
 *
 * @param sortCode ⚠️ <strong>Ordering only, and not an identifier.</strong> Assigned by the business
 *     so the list an employee sees is in a sensible order. <strong>Freely editable</strong> — unlike
 *     the abbreviation, because it appears on no document and carries no legal meaning — and never
 *     derived from Prosvasis Go's numbers. An {@code int}, because a text sort puts {@code 1000}
 *     before {@code 900}. See {@code V34}.
 */
public record PurchaseDocumentTypeView(
        long id,
        @Mandatory String description,
        Boolean affectsStock,
        Boolean transfersStock,
        boolean requiresMydataTransmission,
        Long aadeInvoiceTypeId,
        String aadeInvoiceTypeCode,
        int sortCode,
        boolean active) {

    public PurchaseDocumentTypeView {
        Objects.requireNonNull(description, "description");
    }

    public Optional<Long> aadeInvoiceTypeIdIfAny() {
        return Optional.ofNullable(aadeInvoiceTypeId);
    }

    /**
     * True when the type exists but its stock behaviour has not been decided — a draft rather than
     * a retired type. The two are both {@code active = false} and have different fixes.
     */
    public boolean isDraft() {
        return affectsStock == null || transfersStock == null;
    }
}
