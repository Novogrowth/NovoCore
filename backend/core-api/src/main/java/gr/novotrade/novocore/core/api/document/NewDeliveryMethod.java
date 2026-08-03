package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/** Request to add a delivery method. */
public record NewDeliveryMethod(
        @Mandatory String abbreviation,
        @Mandatory String description) {

    public NewDeliveryMethod {
        Required.text(abbreviation, "abbreviation");
        Required.text(description, "description");
    }
}
