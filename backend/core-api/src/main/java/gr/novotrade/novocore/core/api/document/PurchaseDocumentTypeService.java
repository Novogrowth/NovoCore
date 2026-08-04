package gr.novotrade.novocore.core.api.document;

import java.util.List;
import java.util.Optional;

/**
 * The business's own purchase document types.
 *
 * <p>The mirror of {@link SalesDocumentTypeService}, and a separate service rather than one with a
 * side parameter because the two lists point at different halves of annex 8.1 and are administered
 * by different people.
 *
 * <p>⚠️ <strong>The table ships EMPTY</strong>, for the same reasons.
 */
public interface PurchaseDocumentTypeService {

    List<PurchaseDocumentTypeView> all();

    List<PurchaseDocumentTypeView> active();

    /** Types whose stock behaviour has not been decided. Necessarily inactive; not retired. */
    List<PurchaseDocumentTypeView> drafts();

    Optional<PurchaseDocumentTypeView> find(long id);

    /** @throws DocumentTypeNotFoundException if absent */
    PurchaseDocumentTypeView require(long id);

    /**
     * Adds a type. Created active when both stock flags are supplied, an inactive draft otherwise.
     *
     * @throws InvalidDocumentTypeException if the description duplicates one, or the AADE type
     *     named is not one Novocore treats as received
     */
    PurchaseDocumentTypeView create(NewPurchaseDocumentType request);

    PurchaseDocumentTypeView describe(long id, String description);

    /**
     * Reorders the row. ⚠️ <strong>Freely editable</strong> — this is deliberately NOT the
     * editable-while-unused freeze, because a sort code appears on no document and carries no
     * legal meaning. Reordering a list is a normal act, not a correction.
     *
     * @throws InvalidDocumentTypeException if another row in the same table already holds that sort code — it is
     *     unique, so the ordering is deterministic
     */
    PurchaseDocumentTypeView changeSortCode(long id, int sortCode);

    /**
     * ⚠️ {@code affectsStock} is meaningful here and is not a mirror of the sales flag —
     * {@code 2041 Δελτίο Παραλαβής} brings stock in with no payable behind it. See
     * {@link PurchaseDocumentTypeView#affectsStock()}.
     */
    PurchaseDocumentTypeView changeStockBehaviour(
            long id, boolean affectsStock, boolean transfersStock);

    PurchaseDocumentTypeView changeMydataTransmissionRequired(long id, boolean required);

    /**
     * @throws InvalidDocumentTypeException if the AADE type is one Novocore treats as issued rather
     *     than received, or is one of the entity-adjusting codes
     */
    PurchaseDocumentTypeView mapToAadeInvoiceType(long id, Long aadeInvoiceTypeId);

    void deactivate(long id);

    /** @throws InvalidDocumentTypeException if the type is still a draft */
    void reactivate(long id);
}
