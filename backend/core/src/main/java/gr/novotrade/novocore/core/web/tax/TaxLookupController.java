package gr.novotrade.novocore.core.web.tax;

import gr.novotrade.novocore.core.api.charge.ChargeTypeService;
import gr.novotrade.novocore.core.api.charge.ChargeTypeView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.tax.NewVatClass;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonView;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Required;
import gr.novotrade.novocore.core.web.Requires;
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
 * VAT classes, exemption reasons and charge types.
 *
 * <p>These are what every other form is made of. A product needs a VAT class id, a customer needs an
 * exemption reason when its status is {@code EXEMPT}, an invoice line needs a charge type: without
 * these routes the master-data endpoints exist and cannot be driven from a browser.
 *
 * <h2>⚠️ There is no route to change a rate, and there never will be</h2>
 *
 * <p>Step 14 shipped this read-only and said administering VAT classes deserved a considered screen
 * rather than a POST added for symmetry. Step 16b adds the administration, and the half of that
 * reasoning which was about <em>rates</em> is not a matter of screens at all — it is why
 * {@code VatClassService} offers no such method:
 *
 * <p><strong>A rate is never edited in place.</strong> Editing one would retroactively change what
 * every invoice already issued under that class appears to have charged. A rate change is a
 * <em>new class plus a deactivation</em> — which is exactly what the Greek statutes that keep
 * changing these rates produce in practice. So {@link #createVatClass} and {@link #deactivateVatClass}
 * together are the rate-change workflow, and a frontend should not build an editable rate field.
 *
 * <p>{@link #describeVatClass} exists because a description is a label, not a tax fact: correcting a
 * typo in "Reduced 13% — island" changes nothing about what was charged.
 */
@RestController
@Requires(section = Section.TAX_AND_CHARGES)
class TaxLookupController {

    private final VatClassService vatClasses;
    private final VatExemptionReasonService exemptionReasons;
    private final ChargeTypeService chargeTypes;

    TaxLookupController(VatClassService vatClasses, VatExemptionReasonService exemptionReasons,
            ChargeTypeService chargeTypes) {
        this.vatClasses = vatClasses;
        this.exemptionReasons = exemptionReasons;
        this.chargeTypes = chargeTypes;
    }

    /**
     * The VAT classes.
     *
     * <p>Nine rows, eight distinct percentages — 4% appears twice, as a rate in its own right and as
     * the island-reduced counterpart of 6% under a different legal basis. <strong>The code is the
     * identity, never the rate</strong>, which is why there is no lookup by rate here and none in
     * the service either.
     */
    @GetMapping(path = "/api/vat-classes", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<VatClassView> vatClasses(@RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        boolean activeOnly = Boolean.TRUE.equals(active);
        if (search != null) {
            return ListResponse.of(vatClasses.search(search, activeOnly));
        }
        return ListResponse.of(activeOnly ? vatClasses.active() : vatClasses.all());
    }

    @GetMapping(path = "/api/vat-classes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    VatClassView vatClass(@PathVariable long id) {
        return vatClasses.require(id);
    }

    /**
     * The AADE exemption reasons — 29 real rows in the recodified article numbering.
     *
     * <p>Note that {@code mydataCode} is nullable and NULL means "no mapping exists" rather than
     * "not filled in yet" — the OSS/IOSS reasons genuinely have none. A phase 7 transmission must
     * refuse a NULL rather than composing a substitute; a picker here may show them all.
     */
    @GetMapping(path = "/api/vat-exemption-reasons", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<VatExemptionReasonView> exemptionReasons(
            @RequestParam(required = false) Boolean active) {
        return ListResponse.of(
                Boolean.TRUE.equals(active) ? exemptionReasons.active() : exemptionReasons.all());
    }

    /**
     * Charge types — fees charged to the customer as revenue, each with its own income account.
     *
     * <p>A fee's VAT rate is independent of the products on the invoice (Q33, confirmed with the
     * accountant): a 13% order still carries 24% delivery. Nothing here or downstream should derive
     * a fee's rate from the lines around it.
     */
    @GetMapping(path = "/api/charge-types", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<ChargeTypeView> chargeTypes(@RequestParam(required = false) Boolean active) {
        return ListResponse.of(
                Boolean.TRUE.equals(active) ? chargeTypes.active() : chargeTypes.all());
    }

    // -------------------------------------------------------------------------------------------
    // Administering VAT classes — step 16b
    //
    // Charge types and exemption reasons stay read-only. Their services have write operations, and
    // exposing them is a separate decision: the 29 exemption reasons are AADE's list rather than
    // ours, and a charge type wires a fee to an income account, which is the kind of thing that
    // wants a screen explaining what happens if you point it at the wrong one.
    // -------------------------------------------------------------------------------------------

    /**
     * Creates a VAT class. <strong>A rate change is this plus a deactivation</strong> — see the class
     * javadoc.
     */
    @PostMapping(path = "/api/vat-classes",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    VatClassView createVatClass(@RequestBody NewVatClass request) {
        return vatClasses.create(request);
    }

    /** The description only. The rate and the code are the identity and neither is editable. */
    @PatchMapping(path = "/api/vat-classes/{id}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    VatClassView describeVatClass(@PathVariable long id, @RequestBody DescriptionRequest request) {
        return vatClasses.describe(id, request.description());
    }

    /**
     * Points a mainland rate at its island-reduced counterpart (αρ.31 ν.5057/2023).
     *
     * <p>Enforced one level deep, lower-rated, one-to-one and never self-referencing — so the
     * mapping cannot cycle and resolution stays single-pass. <strong>No automatic switching by
     * shipping destination</strong>: the mapping is data a human applies, not a rule the system runs.
     */
    @PutMapping(path = "/api/vat-classes/{id}/reduced-counterpart",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    VatClassView mapReducedCounterpart(
            @PathVariable long id, @RequestBody ReducedCounterpartRequest request) {
        return vatClasses.mapToReducedCounterpart(id, request.reducedCounterpartId());
    }

    /**
     * Removes the mapping. {@code DELETE} rather than a {@code PUT} of null, because the resource
     * being removed is the mapping itself and a body carrying only {@code null} says nothing.
     */
    @DeleteMapping(path = "/api/vat-classes/{id}/reduced-counterpart",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    VatClassView clearReducedCounterpart(@PathVariable long id) {
        return vatClasses.clearReducedCounterpart(id);
    }

    /** Retires a class. Half of the rate-change workflow; nothing is ever deleted. */
    @PostMapping(path = "/api/vat-classes/{id}/deactivate")
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivateVatClass(@PathVariable long id) {
        vatClasses.deactivate(id);
    }

    @PostMapping(path = "/api/vat-classes/{id}/reactivate")
    @Requires(section = Section.TAX_AND_CHARGES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivateVatClass(@PathVariable long id) {
        vatClasses.reactivate(id);
    }

    // -------------------------------------------------------------------------------------------

    record DescriptionRequest(String description) {

        DescriptionRequest {
            Required.text(description, "description");
        }
    }

    record ReducedCounterpartRequest(Long reducedCounterpartId) {

        ReducedCounterpartRequest {
            Required.field(reducedCounterpartId, "reducedCounterpartId");
        }
    }
}
