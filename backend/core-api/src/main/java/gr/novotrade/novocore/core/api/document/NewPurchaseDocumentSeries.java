package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/**
 * Request to add a purchase document series. ⚠️ No channel field, deliberately — see
 * {@link PurchaseDocumentSeriesView}.
 */
public record NewPurchaseDocumentSeries(
        @Mandatory String abbreviation,
        @Mandatory String description,
        @Mandatory Long documentTypeId,
        @Mandatory Boolean getsMark,
        Long transformableIntoSeriesId) {

    public NewPurchaseDocumentSeries {
        Required.text(abbreviation, "abbreviation");
        Required.text(description, "description");
        Required.field(documentTypeId, "documentTypeId");
        Required.field(getsMark, "getsMark");
    }
}
