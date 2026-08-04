package gr.novotrade.novocore.core.web.document;

import gr.novotrade.novocore.core.api.document.NewSalesDocumentSeries;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.AbbreviationRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.DocumentDescriptionRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.GetsMarkRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.SeriesDocumentTypeRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.SeriesChannelRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.TransformationTargetRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.SortCodeRequest;
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
 * <h2>Channel</h2>
 *
 * <p>{@code PUT .../channel} sets it and {@code DELETE .../channel} removes it — and removing it is
 * a real configuration rather than a field being blanked: <strong>the self-supply series are not a
 * sales channel at all</strong>, because the customer is the issuer.
 *
 * <p>⚠️ In <strong>R1b</strong> this stops being decoration. An invoice's channel will come
 * <em>from</em> its series rather than being independently settable, so ΑΛΠW being the web series
 * makes an invoice in it a web sale by definition rather than by someone remembering to tick a box —
 * and F5 will therefore have no channel field. {@code sales_invoice.channel} is {@code NOT NULL} and
 * that constraint stays: R1b refuses to record an invoice against a channel-less series rather than
 * relaxing it, because self-supply has no posting rule yet and the constraint is what holds that
 * question open. R3 resolves both together.
 */
@RestController
@Requires(section = Section.SALES)
class SalesDocumentSeriesController {

    private final SalesDocumentSeriesService series;

    SalesDocumentSeriesController(SalesDocumentSeriesService series) {
        this.series = series;
    }

    /** {@code documentTypeId} is what a document form narrows to once a type has been chosen. */
    @GetMapping(path = "/api/sales-document-series", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SalesDocumentSeriesView> series(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long documentTypeId) {
        if (documentTypeId != null) {
            return ListResponse.of(series.ofDocumentType(documentTypeId));
        }
        return ListResponse.of(Boolean.TRUE.equals(active) ? series.active() : series.all());
    }

    @GetMapping(path = "/api/sales-document-series/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    SalesDocumentSeriesView oneSeries(@PathVariable long id) {
        return series.require(id);
    }

    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/sales-document-series",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    SalesDocumentSeriesView create(@RequestBody NewSalesDocumentSeries request) {
        return series.create(request);
    }

    @PatchMapping(path = "/api/sales-document-series/{id}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView describe(
            @PathVariable long id, @RequestBody DocumentDescriptionRequest request) {
        return series.describe(id, request.description());
    }

    /**
     * Reorders the row in every list and picker that offers it.
     *
     * <p>⚠️ <strong>Freely editable, and that is the difference from every other correction route
     * on this controller.</strong> A sort code appears on no document and carries no legal meaning,
     * so there is no in-use freeze on it — reordering is a normal act rather than a correction.
     */
    @PatchMapping(path = "/api/sales-document-series/{id}/sort-code",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView changeSortCode(
            @PathVariable long id, @RequestBody SortCodeRequest request) {
        return series.changeSortCode(id, request.sortCode());
    }

    // -------------------------------------------------------------------------------------------
    // ⚠️ R2 — editable while unused, frozen once used. Three routes that did not exist before.
    //
    // The abbreviation is what a document prints, so it IS the identity once a document exists —
    // which is why this was originally left with no route at all. But the owner authors nineteen
    // types and their series by hand, in Greek, and a typo caught five seconds later had no
    // correction path: deactivate-and-recreate burns the abbreviation permanently, because
    // sales_document_series_abbreviation_unique is not partial. The freeze is therefore conditional
    // on the row being referenced rather than absolute, and `inUse` on the view is what lets a
    // screen show the control disabled with the reason instead of hiding it.
    // -------------------------------------------------------------------------------------------

    /** Refused with the reason once a sales invoice names this series. */
    @PatchMapping(path = "/api/sales-document-series/{id}/abbreviation",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView changeAbbreviation(
            @PathVariable long id, @RequestBody AbbreviationRequest request) {
        return series.changeAbbreviation(id, request.abbreviation());
    }

    /**
     * Repoints the series at a different document type.
     *
     * <p>⚠️ Refused once documents exist: the type decides whether recording one consumed
     * inventory, so changing it afterwards would restate what already happened.
     */
    @PutMapping(path = "/api/sales-document-series/{id}/document-type",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView changeDocumentType(
            @PathVariable long id, @RequestBody SeriesDocumentTypeRequest request) {
        return series.changeDocumentType(id, request.documentTypeId());
    }

    /** ⚠️ A wrong value here is not noticed on entry — it is noticed at F5, months later. */
    @PutMapping(path = "/api/sales-document-series/{id}/gets-mark",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView changeGetsMark(
            @PathVariable long id, @RequestBody GetsMarkRequest request) {
        return series.changeGetsMark(id, request.getsMark());
    }

    @PutMapping(path = "/api/sales-document-series/{id}/channel",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView changeChannel(
            @PathVariable long id, @RequestBody SeriesChannelRequest request) {
        return series.changeChannel(id, request.channel());
    }

    /** Marks the series as not a sales channel — which the self-supply series genuinely are not. */
    @DeleteMapping(path = "/api/sales-document-series/{id}/channel",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView clearChannel(@PathVariable long id) {
        return series.changeChannel(id, null);
    }

    /**
     * Which series a document here may be transformed into.
     *
     * <p>⚠️ Only the allowed-target reference is stored. The transformation itself — correcting a
     * mistake into the right series in one action, with products and customer auto-filled and never
     * re-keyed — needs the Go adapter.
     */
    @PutMapping(path = "/api/sales-document-series/{id}/transformation-target",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView mapTransformationTarget(
            @PathVariable long id, @RequestBody TransformationTargetRequest request) {
        return series.mapTransformationTarget(id, request.targetSeriesId());
    }

    @DeleteMapping(path = "/api/sales-document-series/{id}/transformation-target",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentSeriesView clearTransformationTarget(@PathVariable long id) {
        return series.mapTransformationTarget(id, null);
    }

    @PostMapping(path = "/api/sales-document-series/{id}/deactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        series.deactivate(id);
    }

    @PostMapping(path = "/api/sales-document-series/{id}/reactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        series.reactivate(id);
    }
}
