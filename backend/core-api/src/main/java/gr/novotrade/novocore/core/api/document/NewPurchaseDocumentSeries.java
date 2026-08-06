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
 *
 * @param abbreviation ⚠️ <strong>An accent or a lowercase letter here re-opens closed collation
 *     work — roadmap F5b.</strong> The reasoning, the measurement and its three negative controls
 *     are written out once at {@link NewSalesDocumentSeries#abbreviation()}; it applies identically
 *     here, and the same measurement covers both.
 *     <p>⚠️ <strong>Nothing enforces it</strong> — {@code varchar(20)}, not-blank and unique only,
 *     and no pattern on the screen. Recorded, not constrained.
 *     <p>📌 The purchase side is the more likely place for it to happen first: a supplier's own
 *     numbering is <em>their</em> format, not this business's, and {@code F6} is where a purchase
 *     document first carries a series.
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
