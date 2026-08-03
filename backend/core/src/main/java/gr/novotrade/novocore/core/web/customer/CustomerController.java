package gr.novotrade.novocore.core.web.customer;

import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.web.Requires;
import java.util.List;
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
 * Customers.
 *
 * <h2>Matching is split by certainty, and the URLs keep it split</h2>
 *
 * <p>{@code CLAUDE.md} rule 7: auto-resolve only what is genuinely certain, suggest and require
 * confirmation for everything else. That distinction is a property of the two lookups here, so they
 * are two endpoints rather than one "search":
 *
 * <ul>
 *   <li>{@code /by-vat-number/{vatNumber}} is an exact match on an authority-issued identifier and
 *       <strong>may be applied automatically</strong>. A blank VAT number matches nothing.
 *   <li>{@code /match-suggestions} returns candidates on name, email or phone that
 *       <strong>a human must confirm</strong>.
 * </ul>
 *
 * <p>Folding them into one endpoint would erase that distinction at exactly the layer where a screen
 * decides whether to auto-fill or to ask — which is the decision the rule is about.
 *
 * <p><strong>Merge is not built</strong>, in the service or here. Half of it would be a merge that
 * appears to work and loses references.
 */
@RestController
@Requires(section = Section.CUSTOMERS)
class CustomerController {

    private final CustomerService customers;

    CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    /**
     * Customers, optionally filtered.
     *
     * <p>{@code search} matches name, VAT number, email and phone anywhere in the string. It does
     * <strong>not</strong> supersede {@code by-vat-number}, which stays exact: that lookup is
     * authoritative and may auto-link a party without asking, so it is the one place approximate
     * matching would be actively dangerous.
     */
    @GetMapping(path = "/api/customers", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<CustomerView> customers(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        boolean activeOnly = Boolean.TRUE.equals(active);
        if (search != null) {
            return ListResponse.of(customers.search(search, activeOnly));
        }
        return ListResponse.of(activeOnly ? customers.active() : customers.all());
    }

    @GetMapping(path = "/api/customers/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    CustomerView customer(@PathVariable long id) {
        return customers.require(id);
    }

    /**
     * Exact match on the VAT number — the one that may be applied without asking.
     *
     * <p>Answers with a list of zero or one rather than a 404, because "no customer has this VAT
     * number" is the ordinary answer during data entry and is data, not an error.
     */
    @GetMapping(path = "/api/customers/by-vat-number/{vatNumber}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<CustomerView> byVatNumber(@PathVariable String vatNumber) {
        return ListResponse.of(customers.findByVatNumber(vatNumber)
                .map(List::of)
                .orElseGet(List::of));
    }

    /**
     * Candidates on name, email or phone. <strong>Every one of these needs confirming.</strong>
     *
     * <p>Names are deliberately not unique — two retail customers can genuinely share one — so a
     * name match is a suggestion and never an identification.
     */
    @GetMapping(path = "/api/customers/match-suggestions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<CustomerView> matchSuggestions(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        return ListResponse.of(customers.suggestMatches(name, email, phone));
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/customers",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CUSTOMERS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    CustomerView create(@RequestBody NewCustomer request) {
        return customers.create(request);
    }

    @PatchMapping(path = "/api/customers/{id}/name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CUSTOMERS, level = AccessLevel.FULL)
    CustomerView rename(@PathVariable long id, @RequestBody CustomerNameRequest request) {
        return customers.rename(id, request.name());
    }

    /** One email and one phone (Q8), changed together because they are one contact record. */
    @PatchMapping(path = "/api/customers/{id}/contact-details",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CUSTOMERS, level = AccessLevel.FULL)
    CustomerView changeContactDetails(
            @PathVariable long id, @RequestBody CustomerContactDetailsRequest request) {
        return customers.changeContactDetails(id, request.email(), request.phone());
    }

    @PatchMapping(path = "/api/customers/{id}/vat-number",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CUSTOMERS, level = AccessLevel.FULL)
    CustomerView changeVatNumber(@PathVariable long id, @RequestBody CustomerVatNumberRequest request) {
        return customers.changeVatNumber(id, request.vatNumber());
    }

    /**
     * The VAT status and, where the status demands one, its exemption reason.
     *
     * <p>One route because the pair is definitional rather than a policy: {@code INTRA_EU_B2B}
     * requires a VAT number and {@code EXEMPT} requires an exemption reason, both enforced in the
     * service <em>and</em> by CHECK constraints. Setting them separately would create an order in
     * which the record is briefly illegal.
     */
    @PatchMapping(path = "/api/customers/{id}/vat-status",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CUSTOMERS, level = AccessLevel.FULL)
    CustomerView changeVatStatus(@PathVariable long id, @RequestBody CustomerVatStatusRequest request) {
        return customers.changeVatStatus(
                id, request.vatStatus(), request.vatExemptionReasonId());
    }

    /**
     * The customer level of the VAT precedence rule: invoice line beats customer beats product.
     *
     * <p>Null clears it. There is no fallback rate anywhere in this system — if no level supplies a
     * class the resolution throws rather than assuming 24%, because a silent default produces a
     * plausible invoice at a rate nobody chose and an undercharge cannot be recovered after issue.
     */
    @PatchMapping(path = "/api/customers/{id}/vat-class-override",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.CUSTOMERS, level = AccessLevel.FULL)
    CustomerView changeVatClassOverride(
            @PathVariable long id, @RequestBody VatClassOverrideRequest request) {
        return customers.changeVatClassOverride(id, request.vatClassId());
    }

    @PostMapping(path = "/api/customers/{id}/deactivate")
    @Requires(section = Section.CUSTOMERS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        customers.deactivate(id);
    }

    @PostMapping(path = "/api/customers/{id}/reactivate")
    @Requires(section = Section.CUSTOMERS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        customers.reactivate(id);
    }

    // -------------------------------------------------------------------------------------------

    record CustomerNameRequest(String name) {
    }

    record CustomerContactDetailsRequest(String email, String phone) {
    }

    record CustomerVatNumberRequest(String vatNumber) {
    }

    record CustomerVatStatusRequest(@Mandatory VatStatus vatStatus, Long vatExemptionReasonId) {

        CustomerVatStatusRequest {
            // The exemption reason stays optional here: it is required only for EXEMPT, which is
            // the service's rule to state, with the message about VAT that goes with it.
            Required.field(vatStatus, "vatStatus");
        }
    }

    /** Null clears the override, leaving the product's default to decide. */
    record VatClassOverrideRequest(Long vatClassId) {
    }
}
