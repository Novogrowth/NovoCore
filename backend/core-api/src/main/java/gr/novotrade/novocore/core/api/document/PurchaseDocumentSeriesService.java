package gr.novotrade.novocore.core.api.document;

import java.util.List;
import java.util.Optional;

/**
 * The business's own purchase document series.
 *
 * <p>⚠️ <strong>No {@code changeChannel}</strong>, and its absence is the same decision as the
 * missing column: channel is where a <em>sale</em> came from and never applies to a purchase.
 *
 * <p>No number allocation, and ships empty — as for {@link SalesDocumentSeriesService}.
 */
public interface PurchaseDocumentSeriesService {

    List<PurchaseDocumentSeriesView> all();

    List<PurchaseDocumentSeriesView> active();

    List<PurchaseDocumentSeriesView> ofDocumentType(long documentTypeId);

    Optional<PurchaseDocumentSeriesView> find(long id);

    /** @throws DocumentSeriesNotFoundException if absent */
    PurchaseDocumentSeriesView require(long id);

    /**
     * @throws InvalidDocumentSeriesException if the abbreviation duplicates one, or the
     *     transformation target is this series
     * @throws DocumentTypeNotFoundException if the document type does not exist
     */
    PurchaseDocumentSeriesView create(NewPurchaseDocumentSeries request);

    PurchaseDocumentSeriesView describe(long id, String description);

    /** @throws InvalidDocumentSeriesException if the target is this series */
    PurchaseDocumentSeriesView mapTransformationTarget(long id, Long targetSeriesId);

    void deactivate(long id);

    void reactivate(long id);
}
