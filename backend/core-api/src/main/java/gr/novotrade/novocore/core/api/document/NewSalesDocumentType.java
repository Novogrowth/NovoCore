package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/**
 * Request to add a sales document type.
 *
 * <p>⚠️ <strong>A type whose stock flags are omitted is created as a DRAFT — present, editable and
 * inactive.</strong> That is not a silent default in disguise: the alternative was to refuse the
 * request, which would make it impossible to save a type before the stock question has been
 * answered, and answering it with {@code false} would record a decision nobody took. The state is
 * visible on the view ({@code active = false}, {@code isDraft() = true}) and
 * {@code reactivate} refuses while a flag is undecided, naming it.
 *
 * @param affectsStock null means undecided. See {@link SalesDocumentTypeView#affectsStock()}.
 * @param transfersStock null means undecided.
 * @param aadeInvoiceTypeId null where the document is operational rather than a tax document.
 *     ⚠️ Six of the owner's nineteen types are exactly that, so null here is ordinary rather than
 *     exceptional, and there is deliberately no sentinel code to use instead.
 */
public record NewSalesDocumentType(
        @Mandatory String description,
        // ⚠️ NOT @ConditionallyMandatory, and the distinction is one 8a's rule taught by
        // failing the build. That annotation means "guarded, but behind a branch" — it exempts a
        // component from the cross-check that every guard is declared. These two are not guarded
        // at all and are not meant to be: null is a VALID value meaning "undecided", which is the
        // whole reason the column is nullable. An optional field carries no annotation.
        Boolean affectsStock,
        Boolean transfersStock,
        @Mandatory Boolean requiresMydataTransmission,
        Long aadeInvoiceTypeId) {

    public NewSalesDocumentType {
        Required.text(description, "description");
        Required.field(requiresMydataTransmission, "requiresMydataTransmission");
    }

    /** A type whose stock behaviour is already known — the ordinary case once R2 exists. */
    public static NewSalesDocumentType decided(String description, boolean affectsStock,
            boolean transfersStock, boolean requiresMydataTransmission, Long aadeInvoiceTypeId) {
        return new NewSalesDocumentType(description, affectsStock, transfersStock,
                requiresMydataTransmission, aadeInvoiceTypeId);
    }
}
