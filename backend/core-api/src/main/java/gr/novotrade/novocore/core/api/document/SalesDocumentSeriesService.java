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
