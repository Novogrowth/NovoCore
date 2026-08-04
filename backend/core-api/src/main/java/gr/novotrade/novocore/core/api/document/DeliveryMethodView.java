package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;

/**
 * How goods reach the customer — a courier, our own vehicle, collection from the shop.
 *
 * <p>⚠️ Not an AADE codification. Annex 8.14 {@code Σκοπός Διακίνησης} is the transport
 * <em>purpose</em> and belongs with 18b, which is a different question from who carries the parcel.
 * This is the business's own list, authored by the business, and it ships empty.
 *
 * @param inUse whether anything names this delivery method — the predicate that would freeze
 *     {@code abbreviation}, as on the two series records.
 *     <p>⚠️⚠️ <strong>Always false today, and for a stronger reason than the purchase series':
 *     NOTHING IN THE SCHEMA REFERENCES THIS TABLE AT ALL.</strong> Measured 2026-08-04 — zero
 *     foreign keys point at {@code delivery_method}. It is a list the business maintains ahead of
 *     the document that will use it (18b, dispatch).
 *     <p>The component exists so the three records answer the same question the same way and one
 *     screen mechanism serves all three. {@code DocumentReferenceGraphIT} pins the emptiness, so
 *     whoever gives a document a delivery method gets a red build naming this field rather than a
 *     silently vacuous freeze.
 */
public record DeliveryMethodView(
        long id,
        @Mandatory String abbreviation,
        @Mandatory String description,
        boolean inUse,
        boolean active) {

    public DeliveryMethodView {
        Objects.requireNonNull(abbreviation, "abbreviation");
        Objects.requireNonNull(description, "description");
    }
}
