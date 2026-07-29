package gr.novotrade.novocore.core.web.tax;

import gr.novotrade.novocore.core.api.charge.ChargeTypeService;
import gr.novotrade.novocore.core.api.charge.ChargeTypeView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonView;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * VAT classes, exemption reasons and charge types — <strong>read-only</strong>.
 *
 * <p>These are what every other form is made of. A product needs a VAT class id, a customer needs an
 * exemption reason when its status is {@code EXEMPT}, an invoice line needs a charge type: without
 * these routes the master-data endpoints exist and cannot be driven from a browser.
 *
 * <p><strong>Administering them is deliberately absent.</strong> Creating a VAT class is a statutory
 * event, not a data-entry one — a rate is never edited in place, because that would retroactively
 * change what every invoice already issued under it appears to have charged, so a change is a new
 * class plus a deactivation. That workflow deserves a considered screen rather than a POST added for
 * symmetry, and nothing in step 14's workflows needs it.
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
    ListResponse<VatClassView> vatClasses(@RequestParam(required = false) Boolean active) {
        return ListResponse.of(
                Boolean.TRUE.equals(active) ? vatClasses.active() : vatClasses.all());
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
}
