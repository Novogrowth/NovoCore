package gr.novotrade.novocore.core.web.codification;

import gr.novotrade.novocore.core.api.codification.AadeInvoiceGroup;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeService;
import gr.novotrade.novocore.core.api.codification.AadeInvoiceTypeView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AADE myDATA invoice-type codification — 55 rows, seeded from the artefacts.
 *
 * <h2>⚠️ There is no POST, and there never will be</h2>
 *
 * <p>This is a {@code StatutoryCodification}: AADE authors the rows, Flyway writes them, and the
 * only operations are activate, deactivate and describe. If AADE publishes a new code that is a
 * migration with the artefact it was read from sitting beside it — not a form somebody fills in,
 * because a code typed into a form is transmitted to the tax authority and a wrong one is a
 * compliance defect rather than a data-entry mistake. {@code StatutoryCodificationRulesTest} makes
 * the absence of a create path a build failure instead of a convention.
 *
 * <p>⚠️ <strong>This is NOT the business's document type list.</strong> That is
 * {@code /api/sales-document-types} and {@code /api/purchase-document-types}, which are the
 * business's own, user-creatable, and point <em>at</em> this one. Six of the owner's nineteen
 * document types have no row here at all, because they are operational documents rather than tax
 * documents.
 *
 * <p>Declared under {@link Section#TAX_AND_CHARGES} rather than under Sales or Purchasing, although
 * a document form is what reads it: it is a tax authority's list, exactly like
 * {@code /api/vat-exemption-reasons}, and it is needed by <em>both</em> a sales and a purchase
 * document form — so neither of those sections could hold it without the other losing access.
 */
@RestController
@Requires(section = Section.TAX_AND_CHARGES)
class AadeInvoiceTypeController {

    private final AadeInvoiceTypeService invoiceTypes;

    AadeInvoiceTypeController(AadeInvoiceTypeService invoiceTypes) {
        this.invoiceTypes = invoiceTypes;
    }

    /**
     * The codification.
     *
     * <p>{@code side=ISSUED} narrows to the 34 codes Novocore treats as documents we issue and
     * {@code side=RECEIVED} to the 15 we receive — which is what a document-type form wants, since
     * offering all 55 when defining a <em>sales</em> type would put "Ενοίκιο Έξοδο" in the picker.
     * {@code group} narrows to one annex 8.1 group exactly.
     *
     * <p>⚠️ The six {@link AadeInvoiceGroup#ENTITY_ADJUSTING} codes are in neither side. They are
     * the entity's own journal entries — payroll, depreciation, income and expense adjustments —
     * with no counterparty at all, so they belong to no document list. That is a fact about the
     * codification and not an omission here, which is why {@code side} is a filter and the
     * unfiltered list is the whole 55.
     *
     * <p>Rows are returned in the XSD's own enumeration order, which is also annex 8.1's reading
     * order. Deliberately <strong>not</strong> sorted by code: the codes are dotted strings, so a
     * text sort puts {@code 10.1} before {@code 2.1} and {@code 13.31} before {@code 13.4}.
     */
    @GetMapping(path = "/api/aade-invoice-types", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AadeInvoiceTypeView> invoiceTypes(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) AadeInvoiceGroup group,
            @RequestParam(required = false) DocumentSide side) {

        if (group != null) {
            return ListResponse.of(filterActive(invoiceTypes.inGroup(group), active));
        }
        if (side != null) {
            return ListResponse.of(filterActive(
                    side == DocumentSide.ISSUED ? invoiceTypes.issued() : invoiceTypes.received(),
                    active));
        }
        return ListResponse.of(
                Boolean.TRUE.equals(active) ? invoiceTypes.active() : invoiceTypes.all());
    }

    @GetMapping(path = "/api/aade-invoice-types/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    AadeInvoiceTypeView invoiceType(@PathVariable long id) {
        return invoiceTypes.require(id);
    }

    // -------------------------------------------------------------------------------------------
    // The three operations a statutory codification permits. No POST — see the class javadoc.
    // -------------------------------------------------------------------------------------------

    /**
     * Corrects the description.
     *
     * <p>The code is the identity and has no route. ⚠️ Codes {@code 4} and {@code 12} carry the
     * label {@code "Για Μελλοντική Χρήση"} because annex 8.1's description cell for them is empty
     * — that is what AADE published, not a placeholder somebody left behind.
     */
    @PatchMapping(path = "/api/aade-invoice-types/{id}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    AadeInvoiceTypeView describe(@PathVariable long id, @RequestBody AadeInvoiceTypeDescriptionRequest request) {
        return invoiceTypes.describe(id, request.description());
    }

    /** Takes a code out of circulation — AADE retired it. Nothing is ever deleted. */
    @PostMapping(path = "/api/aade-invoice-types/{id}/deactivate")
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        invoiceTypes.deactivate(id);
    }

    @PostMapping(path = "/api/aade-invoice-types/{id}/reactivate")
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        invoiceTypes.reactivate(id);
    }

    // -------------------------------------------------------------------------------------------

    private static java.util.List<AadeInvoiceTypeView> filterActive(
            java.util.List<AadeInvoiceTypeView> types, Boolean active) {
        return Boolean.TRUE.equals(active)
                ? types.stream().filter(AadeInvoiceTypeView::active).toList()
                : types;
    }

    /**
     * Which half of annex 8.1 a caller wants.
     *
     * <p>⚠️ Novocore's own distinction, not AADE's: the XSD has one enumeration covering both
     * directions and the split comes from reading annex 8.1's group headings.
     */
    enum DocumentSide {
        /** The two issuer groups — 34 codes. */
        ISSUED,
        /** The two recipient groups — 15 codes. */
        RECEIVED
    }

    /**
     * Named with the entity prefix rather than {@code DescriptionRequest}, deliberately: that name
     * is already claimed by {@code TaxLookupController} for VAT classes, and 8a made a schema-name
     * collision a build failure after finding four of them — one of which merged seven records that
     * differ in exactly the property the spec publishes.
     */
    record AadeInvoiceTypeDescriptionRequest(@Mandatory String description) {

        AadeInvoiceTypeDescriptionRequest {
            Required.text(description, "description");
        }
    }
}
