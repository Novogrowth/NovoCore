package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/**
 * Request to add a sales document series.
 *
 * @param channel null means this series is not a sales channel — see
 *     {@link SalesDocumentSeriesView#channel()}. R1 <em>references</em> the existing
 *     {@link SalesChannel}; it does not create the concept, which has reached the ledger since
 *     step 3 with the Sales and Sales-returns accounts already split per channel.
 * @param transformableIntoSeriesId null where no transformation target has been configured. A
 *     series may not transform into itself.
 *
 * @param sortCode ⚠️ Ordering only — see the view. <strong>Required</strong>, because the
 *     column is {@code NOT NULL}: unlike {@code sales_invoice.series_id}, a sort code has no
 *     truth value, so giving one fabricates nothing.
 */
public record NewSalesDocumentSeries(
        @Mandatory String abbreviation,
        @Mandatory String description,
        @Mandatory Long documentTypeId,
        SalesChannel channel,
        @Mandatory Boolean getsMark,
        Long transformableIntoSeriesId,
        @Mandatory Integer sortCode) {

    public NewSalesDocumentSeries {
        Required.field(sortCode, "sortCode");
        Required.text(abbreviation, "abbreviation");
        Required.text(description, "description");
        Required.field(documentTypeId, "documentTypeId");
        Required.field(getsMark, "getsMark");
    }
}
