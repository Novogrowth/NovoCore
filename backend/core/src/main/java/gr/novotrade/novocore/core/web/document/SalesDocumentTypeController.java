package gr.novotrade.novocore.core.web.document;

import gr.novotrade.novocore.core.api.document.NewSalesDocumentType;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeService;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.AadeInvoiceTypeMappingRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.DocumentDescriptionRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.MydataTransmissionRequest;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.StockBehaviourRequest;
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
 * The business's own sales document types — ⚠️ <strong>full CRUD, and the table ships empty.</strong>
 *
 * <p>This is not the AADE codification. That is {@code /api/aade-invoice-types}, which is seed-only
 * and has no POST; this list is the owner's, points at it through a <em>nullable</em> reference, and
 * exists because six of his nineteen document types have no AADE invoice type at all — Προσφορά,
 * Δελτίο Αποστολής, Παραγγελία are operational documents, not tax documents.
 *
 * <p>⚠️ <strong>Nothing is seeded here on purpose.</strong> The owner creates his own through R2's
 * screens, choosing each AADE type himself: he knows which is which better than an inference does,
 * an inferred Go→AADE mapping would be a guess written into a statutory field, and creating them by
 * hand exercises this surface immediately.
 *
 * <h2>Drafts, and why {@code POST} can produce an inactive row</h2>
 *
 * <p>{@code affectsStock} and {@code transfersStock} are <strong>nullable</strong>, because a
 * {@code false} there is a decision that stock does not move and is indistinguishable from a field
 * nobody answered — and R1b branches the consumption path on exactly that value. A type created
 * without them is an inactive <em>draft</em>: saveable, editable, and not offered by any form until
 * {@code PUT .../stock-behaviour} answers the question. {@code POST .../reactivate} refuses while it
 * is unanswered, and the database CHECK says the same thing from the other side.
 *
 * <p>{@code GET /api/sales-document-types/drafts} exists for the reason
 * {@code GET /api/units-of-measure/without-mydata-code} does: an unfinished decision only visible in
 * {@code psql} is one nobody finishes.
 *
 * <p>Declared under {@link Section#SALES} rather than Settings, on the principle
 * {@code UnitOfMeasureController} states: a section is about who needs to see something. This list
 * is read when recording a sale and by nobody else.
 */
@RestController
@Requires(section = Section.SALES)
class SalesDocumentTypeController {

    private final SalesDocumentTypeService documentTypes;

    SalesDocumentTypeController(SalesDocumentTypeService documentTypes) {
        this.documentTypes = documentTypes;
    }

    @GetMapping(path = "/api/sales-document-types", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SalesDocumentTypeView> documentTypes(
            @RequestParam(required = false) Boolean active) {
        return ListResponse.of(
                Boolean.TRUE.equals(active) ? documentTypes.active() : documentTypes.all());
    }

    /** Types whose stock behaviour is still undecided — a real to-do list, not a diagnostic. */
    @GetMapping(path = "/api/sales-document-types/drafts",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SalesDocumentTypeView> drafts() {
        return ListResponse.of(documentTypes.drafts());
    }

    @GetMapping(path = "/api/sales-document-types/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    SalesDocumentTypeView documentType(@PathVariable long id) {
        return documentTypes.require(id);
    }

    // -------------------------------------------------------------------------------------------

    /** Created active when both stock flags are supplied, an inactive draft otherwise. */
    @PostMapping(path = "/api/sales-document-types",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    SalesDocumentTypeView create(@RequestBody NewSalesDocumentType request) {
        return documentTypes.create(request);
    }

    @PatchMapping(path = "/api/sales-document-types/{id}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentTypeView describe(
            @PathVariable long id, @RequestBody DocumentDescriptionRequest request) {
        return documentTypes.describe(id, request.description());
    }

    /**
     * Both flags at once. ⚠️ Not cosmetic: it decides whether recording a document of this type
     * consumes inventory.
     */
    @PutMapping(path = "/api/sales-document-types/{id}/stock-behaviour",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentTypeView changeStockBehaviour(
            @PathVariable long id, @RequestBody StockBehaviourRequest request) {
        return documentTypes.changeStockBehaviour(
                id, request.affectsStock(), request.transfersStock());
    }

    @PutMapping(path = "/api/sales-document-types/{id}/mydata-transmission",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentTypeView changeMydataTransmissionRequired(
            @PathVariable long id, @RequestBody MydataTransmissionRequest request) {
        return documentTypes.changeMydataTransmissionRequired(id, request.required());
    }

    /**
     * Points the type at an AADE invoice type.
     *
     * <p>Refused if the code is one Novocore treats as <em>received</em>, or one of the six
     * entity-adjusting codes — the XSD has a single enumeration covering both directions, so
     * nothing in AADE's own artefacts would stop a sales type naming "Ενοίκιο Έξοδο".
     */
    @PutMapping(path = "/api/sales-document-types/{id}/aade-invoice-type",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentTypeView mapToAadeInvoiceType(
            @PathVariable long id, @RequestBody AadeInvoiceTypeMappingRequest request) {
        return documentTypes.mapToAadeInvoiceType(id, request.aadeInvoiceTypeId());
    }

    /**
     * Removes the mapping — the type is operational rather than a tax document.
     *
     * <p>{@code DELETE} rather than a {@code PUT} of null, on {@code VatClassController}'s
     * precedent: the resource being removed is the mapping itself, and a body carrying only
     * {@code null} says nothing.
     */
    @DeleteMapping(path = "/api/sales-document-types/{id}/aade-invoice-type",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesDocumentTypeView clearAadeInvoiceType(@PathVariable long id) {
        return documentTypes.mapToAadeInvoiceType(id, null);
    }

    /** Retires a type. Documents already recorded under it are untouched and stay explicable. */
    @PostMapping(path = "/api/sales-document-types/{id}/deactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        documentTypes.deactivate(id);
    }

    /** Refused while the type is a draft, naming what is still undecided. */
    @PostMapping(path = "/api/sales-document-types/{id}/reactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        documentTypes.reactivate(id);
    }
}
