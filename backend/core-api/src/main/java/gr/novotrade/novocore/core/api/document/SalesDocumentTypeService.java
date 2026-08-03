package gr.novotrade.novocore.core.api.document;

import java.util.List;
import java.util.Optional;

/**
 * The business's own sales document types.
 *
 * <p>⚠️ <strong>Full CRUD, deliberately, and this is the correction R1a made.</strong> An earlier
 * design had this list under the statutory-codification contract, seeded from AADE with no
 * {@code create}. The owner's real Prosvasis Go configuration disproved it twice over: six of his
 * nineteen document types have <strong>no AADE invoice type at all</strong> (Προσφορά, Δελτίο
 * Αποστολής, Παραγγελία — operational documents, not tax documents), and he has stated that more
 * types will be needed, so the build must support authoring them.
 *
 * <p>So this list is the business's, and it points at the codification rather than being one. See
 * {@code StatutoryCodification} for the distinction and why getting it wrong in either direction
 * costs something.
 *
 * <p>⚠️ <strong>The table ships EMPTY.</strong> The owner creates his own through R2's screens,
 * choosing each AADE type himself: he knows which is which better than an inference does, an
 * inferred Go→AADE mapping would be a guess written into a statutory field, and creating them by
 * hand exercises this mechanism the moment it exists.
 */
public interface SalesDocumentTypeService {

    /** Every type, active and inactive, by description. */
    List<SalesDocumentTypeView> all();

    /** Active types only — what a document form should offer. */
    List<SalesDocumentTypeView> active();

    /**
     * Types that exist but whose stock behaviour has not been decided.
     *
     * <p>Necessarily inactive, and a different thing from a retired type — the two look identical
     * in a list and have entirely different fixes. The same reasoning as
     * {@code GET /api/units-of-measure/without-mydata-code}: an unfinished decision that is only
     * visible in {@code psql} is one nobody finishes.
     */
    List<SalesDocumentTypeView> drafts();

    Optional<SalesDocumentTypeView> find(long id);

    /** @throws DocumentTypeNotFoundException if absent */
    SalesDocumentTypeView require(long id);

    /**
     * Adds a type.
     *
     * <p>⚠️ Created <strong>active</strong> when both stock flags are supplied, and as an inactive
     * <strong>draft</strong> when either is omitted. Refusing instead would make it impossible to
     * save a type before the stock question has been answered; defaulting a flag to {@code false}
     * would record a decision nobody took.
     *
     * @throws InvalidDocumentTypeException if the description duplicates one, or the AADE type
     *     named is not one Novocore treats as issued by us
     * @throws gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeNotFoundException if the
     *     AADE type named does not exist
     */
    SalesDocumentTypeView create(NewSalesDocumentType request);

    /** @throws InvalidDocumentTypeException if the description duplicates another type's */
    SalesDocumentTypeView describe(long id, String description);

    /**
     * Decides, or corrects, whether documents of this type move stock.
     *
     * <p>Both flags at once rather than one route each, because they are one decision: a type that
     * transfers stock necessarily affects it, and setting them separately would allow an incoherent
     * intermediate state to be saved and then activated.
     *
     * @throws InvalidDocumentTypeException if {@code transfersStock} is true while
     *     {@code affectsStock} is false
     */
    SalesDocumentTypeView changeStockBehaviour(
            long id, boolean affectsStock, boolean transfersStock);

    SalesDocumentTypeView changeMydataTransmissionRequired(long id, boolean required);

    /**
     * Points the type at an AADE invoice type, or clears the reference with a null.
     *
     * <p>⚠️ Null is an ordinary value here, not an error: six of the owner's nineteen types have no
     * statutory type. There is deliberately no sentinel code to use instead.
     *
     * @throws InvalidDocumentTypeException if the AADE type is one Novocore treats as received
     *     rather than issued, or is one of the entity-adjusting codes
     */
    SalesDocumentTypeView mapToAadeInvoiceType(long id, Long aadeInvoiceTypeId);

    /** Retires a type. Historical documents recorded under it are untouched. */
    void deactivate(long id);

    /**
     * @throws InvalidDocumentTypeException if the type is a draft — its stock behaviour must be
     *     decided before it can be offered, and the database refuses the row either way
     */
    void reactivate(long id);
}
