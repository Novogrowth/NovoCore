package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/**
 * Request to add a purchase document type.
 *
 * <p>Same draft rule as {@link NewSalesDocumentType}: omitting a stock flag creates an inactive
 * draft rather than recording a decision nobody took.
 *
 * @param affectsStock ⚠️ meaningful here, not a mirror of the sales column — see
 *     {@link PurchaseDocumentTypeView#affectsStock()} for the ΤΔΑΑ / Δελτίο Παραλαβής example.
 * @param aadeInvoiceTypeId null where the document is operational rather than a tax document.
 *
 * @param sortCode ⚠️ Ordering only — see the view. <strong>Required</strong>, because the
 *     column is {@code NOT NULL}: unlike {@code sales_invoice.series_id}, a sort code has no
 *     truth value, so giving one fabricates nothing.
 */
public record NewPurchaseDocumentType(
        @Mandatory String description,
        // ⚠️ NOT @ConditionallyMandatory, and the distinction is one 8a's rule taught by
        // failing the build. That annotation means "guarded, but behind a branch" — it exempts a
        // component from the cross-check that every guard is declared. These two are not guarded
        // at all and are not meant to be: null is a VALID value meaning "undecided", which is the
        // whole reason the column is nullable. An optional field carries no annotation.
        Boolean affectsStock,
        Boolean transfersStock,
        @Mandatory Boolean requiresMydataTransmission,
        Long aadeInvoiceTypeId,
        @Mandatory Integer sortCode) {

    public NewPurchaseDocumentType {
        Required.field(sortCode, "sortCode");
        Required.text(description, "description");
        Required.field(requiresMydataTransmission, "requiresMydataTransmission");
    }
}
