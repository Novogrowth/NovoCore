package gr.novotrade.novocore.core.web.document;

import gr.novotrade.novocore.core.api.document.NewPurchaseDocumentSeries;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.PurchaseDocumentSeriesView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.DocumentDescriptionRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.TransformationTargetRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The business's own sales document series — ⚠️ <strong>full CRUD, and the table ships empty.</strong>
 *
 * <h2>⚠️ There is no route that hands out a number, and there will be none before step 40</h2>
 *
 * <p>Novocore <strong>records</strong> what the issuing system printed. Legal issuance runs through
 * Prosvasis Go today and a certified Πάροχος at step 40, and the document receives its number and
 * its ΜΑΡΚ there. A {@code POST .../next-number} would be the first half of a gap-prevention problem
 * this system does not have; the machinery belongs at step 40 and nowhere earlier. "Integers from 1,
 * continuous" is an expectation about the issuing system, recorded rather than enforced, and gap
 * detection belongs with step 25.
 *
 * <h2>⚠️ There is no channel route here, and its absence is the decision</h2>
 *
 * <p>Channel is where a <em>sale</em> came from; it never applies to a purchase. There is no column,
 * no accessor, no service method and no route — rather than a nullable one that could only ever be
 * null, which would invite someone to fill it. {@link SalesDocumentSeriesController} has the pair.
 */
@RestController
@Requires(section = Section.PURCHASING)
class PurchaseDocumentSeriesController {

    private final PurchaseDocumentSeriesService series;

    PurchaseDocumentSeriesController(PurchaseDocumentSeriesService series) {
        this.series = series;
    }

    /** {@code documentTypeId} is what a document form narrows to once a type has been chosen. */
    @GetMapping(path = "/api/purchase-document-series", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<PurchaseDocumentSeriesView> series(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long documentTypeId) {
        if (documentTypeId != null) {
            return ListResponse.of(series.ofDocumentType(documentTypeId));
        }
        return ListResponse.of(Boolean.TRUE.equals(active) ? series.active() : series.all());
    }

    @GetMapping(path = "/api/purchase-document-series/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    PurchaseDocumentSeriesView oneSeries(@PathVariable long id) {
        return series.require(id);
    }

    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/purchase-document-series",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseDocumentSeriesView create(@RequestBody NewPurchaseDocumentSeries request) {
        return series.create(request);
    }

    /** The description only. The abbreviation is what a document prints and is the identity. */
    @PatchMapping(path = "/api/purchase-document-series/{id}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    PurchaseDocumentSeriesView describe(
            @PathVariable long id, @RequestBody DocumentDescriptionRequest request) {
        return series.describe(id, request.description());
    }

    /**
     * Which series a document here may be transformed into.
     *
     * <p>⚠️ Only the allowed-target reference is stored. The transformation itself — correcting a
     * mistake into the right series in one action, with products and customer auto-filled and never
     * re-keyed — needs the Go adapter.
     */
    @PutMapping(path = "/api/purchase-document-series/{id}/transformation-target",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    PurchaseDocumentSeriesView mapTransformationTarget(
            @PathVariable long id, @RequestBody TransformationTargetRequest request) {
        return series.mapTransformationTarget(id, request.targetSeriesId());
    }

    @DeleteMapping(path = "/api/purchase-document-series/{id}/transformation-target",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    PurchaseDocumentSeriesView clearTransformationTarget(@PathVariable long id) {
        return series.mapTransformationTarget(id, null);
    }

    @PostMapping(path = "/api/purchase-document-series/{id}/deactivate")
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        series.deactivate(id);
    }

    @PostMapping(path = "/api/purchase-document-series/{id}/reactivate")
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        series.reactivate(id);
    }
}
