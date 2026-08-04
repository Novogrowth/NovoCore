package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/**
 * Request to add a purchase document series. ⚠️ No channel field, deliberately — see
 * {@link PurchaseDocumentSeriesView}.
 *
 * @param sortCode ⚠️ Ordering only — see the view. <strong>Required</strong>, because the
 *     column is {@code NOT NULL}: unlike {@code sales_invoice.series_id}, a sort code has no
 *     truth value, so giving one fabricates nothing.
 */
public record NewPurchaseDocumentSeries(
        @Mandatory String abbreviation,
        @Mandatory String description,
        @Mandatory Long documentTypeId,
        @Mandatory Boolean getsMark,
        Long transformableIntoSeriesId,
        @Mandatory Integer sortCode) {

    public NewPurchaseDocumentSeries {
        Required.field(sortCode, "sortCode");
        Required.text(abbreviation, "abbreviation");
        Required.text(description, "description");
        Required.field(documentTypeId, "documentTypeId");
        Required.field(getsMark, "getsMark");
    }
}
