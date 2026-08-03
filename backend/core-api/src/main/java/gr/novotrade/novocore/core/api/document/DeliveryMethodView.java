package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;

/**
 * How goods reach the customer — a courier, our own vehicle, collection from the shop.
 *
 * <p>⚠️ Not an AADE codification. Annex 8.14 {@code Σκοπός Διακίνησης} is the transport
 * <em>purpose</em> and belongs with 18b, which is a different question from who carries the parcel.
 * This is the business's own list, authored by the business, and it ships empty.
 */
public record DeliveryMethodView(
        long id,
        @Mandatory String abbreviation,
        @Mandatory String description,
        boolean active) {

    public DeliveryMethodView {
        Objects.requireNonNull(abbreviation, "abbreviation");
        Objects.requireNonNull(description, "description");
    }
}
