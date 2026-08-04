package gr.novotrade.novocore.core.api.document;

import gr.novotrade.novocore.core.api.sales.SalesChannel;
import java.util.List;
import java.util.Optional;

/**
 * The business's own sales document series.
 *
 * <p>⚠️ <strong>There is no number allocation here and there will be none before step 40.</strong>
 * No sequence, no counter, no {@code nextNumber()}. Novocore records what the issuing system
 * printed; a method that handed out a number would be the first half of a gap-prevention problem
 * this system does not have and must not acquire early.
 *
 * <p>⚠️ <strong>Ships EMPTY.</strong> The owner creates his own.
 */
public interface SalesDocumentSeriesService {

    List<SalesDocumentSeriesView> all();

    List<SalesDocumentSeriesView> active();

    /** The series of one document type — what a document form narrows to once a type is chosen. */
    List<SalesDocumentSeriesView> ofDocumentType(long documentTypeId);

    Optional<SalesDocumentSeriesView> find(long id);

    /** @throws DocumentSeriesNotFoundException if absent */
    SalesDocumentSeriesView require(long id);

    /**
     * @throws InvalidDocumentSeriesException if the abbreviation duplicates one, or the
     *     transformation target is this series
     * @throws DocumentTypeNotFoundException if the document type does not exist
     */
    SalesDocumentSeriesView create(NewSalesDocumentSeries request);

    SalesDocumentSeriesView describe(long id, String description);

    /**
     * Reorders the row. ⚠️ <strong>Freely editable</strong> — this is deliberately NOT the
     * editable-while-unused freeze, because a sort code appears on no document and carries no
     * legal meaning. Reordering a list is a normal act, not a correction.
     *
     * @throws InvalidDocumentSeriesException if another row in the same table already holds that sort code — it is
     *     unique, so the ordering is deterministic
     */
    SalesDocumentSeriesView changeSortCode(long id, int sortCode);

    /**
     * Corrects the abbreviation of a series <strong>nothing has been recorded in</strong>.
     *
     * <p>⚠️ <strong>Added in R2, and the gap it closes is worth stating.</strong> Until then no route
     * changed this at all, so a typo in a hand-authored Greek abbreviation had no correction path:
     * the only remedy was deactivate-and-recreate, which burns the abbreviation permanently because
     * {@code sales_document_series_abbreviation_unique} is not partial. Indefensible on a series
     * created five seconds ago; correct on one that has recorded documents, which is why the freeze
     * is conditional rather than absolute.
     *
     * @throws InvalidDocumentSeriesException if a sales invoice already names this series, or the
     *     new abbreviation duplicates another
     */
    SalesDocumentSeriesView changeAbbreviation(long id, String abbreviation);

    /**
     * Repoints a series at a different document type.
     *
     * @throws InvalidDocumentSeriesException if a sales invoice already names this series — the type
     *     decides whether recording a document consumed inventory, so changing it afterwards would
     *     restate what already happened
     * @throws DocumentTypeNotFoundException if the new type does not exist
     */
    SalesDocumentSeriesView changeDocumentType(long id, long documentTypeId);

    /**
     * Corrects whether documents in this series receive a ΜΑΡΚ.
     *
     * <p>⚠️ A wrong value here is not noticed on entry. It is noticed at <strong>F5</strong>, on a
     * row that has been in the system for months — which is the argument for a correction path.
     *
     * @throws InvalidDocumentSeriesException if a sales invoice already names this series
     */
    SalesDocumentSeriesView changeGetsMark(long id, boolean getsMark);

    /**
     * Sets or clears the sales channel.
     *
     * <p>⚠️ Null is a real value: the self-supply series are not a sales channel at all. In R1b
     * this field stops being decoration and becomes the invoice's channel.
     */
    SalesDocumentSeriesView changeChannel(long id, SalesChannel channel);

    /**
     * Sets or clears which series a document here may be transformed into.
     *
     * @throws InvalidDocumentSeriesException if the target is this series
     */
    SalesDocumentSeriesView mapTransformationTarget(long id, Long targetSeriesId);

    void deactivate(long id);

    void reactivate(long id);
}
