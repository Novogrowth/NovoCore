package gr.novotrade.novocore.core.web.supplier;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
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
 * Suppliers.
 *
 * <p>The same shape as customers, minus the VAT class override — that is the customer level of the
 * precedence rule and a supplier has no equivalent, since we do not charge them.
 *
 * <p><strong>Its own section, not part of {@code PRODUCTS}</strong>, even though Remote/Order
 * Staff's field restrictions hide the supplier <em>of a product</em>. Those are different questions:
 * the restriction narrows a product response, while this governs the supplier directory itself, and
 * granting one should not grant the other.
 *
 * <p>Matching is split by certainty exactly as it is for customers — an exact VAT-number lookup that
 * may be applied automatically, and suggestions that a human confirms ({@code CLAUDE.md} rule 7).
 */
@RestController
@Requires(section = Section.SUPPLIERS)
class SupplierController {

    private final SupplierService suppliers;

    SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    /**
     * Suppliers, optionally filtered.
     *
     * <p><strong>{@code search} is not {@code match-suggestions} under another name</strong>, even
     * though the two look at the same three columns. This is a filter box over a list the operator
     * is already looking at. That route feeds the never-silently-guess flow of {@code CLAUDE.md}
     * rule 7, where each candidate is a proposed identity for a party on an incoming document and is
     * confirmed by a human one at a time. Keeping them separate is what lets the filter box be made
     * looser later without loosening what gets offered for confirmation.
     */
    @GetMapping(path = "/api/suppliers", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SupplierView> suppliers(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        boolean activeOnly = Boolean.TRUE.equals(active);
        if (search != null) {
            return ListResponse.of(suppliers.search(search, activeOnly));
        }
        return ListResponse.of(activeOnly ? suppliers.active() : suppliers.all());
    }

    @GetMapping(path = "/api/suppliers/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    SupplierView supplier(@PathVariable long id) {
        return suppliers.require(id);
    }

    /** Exact match on an authority-issued identifier; may be applied without asking. */
    @GetMapping(path = "/api/suppliers/by-vat-number/{vatNumber}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SupplierView> byVatNumber(@PathVariable String vatNumber) {
        return ListResponse.of(suppliers.findByVatNumber(vatNumber)
                .map(List::of)
                .orElseGet(List::of));
    }

    /** Candidates on name, email or phone. Every one needs confirming. */
    @GetMapping(path = "/api/suppliers/match-suggestions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SupplierView> matchSuggestions(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        return ListResponse.of(suppliers.suggestMatches(name, email, phone));
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/suppliers",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SUPPLIERS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    SupplierView create(@RequestBody NewSupplier request) {
        return suppliers.create(request);
    }

    @PatchMapping(path = "/api/suppliers/{id}/name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SUPPLIERS, level = AccessLevel.FULL)
    SupplierView rename(@PathVariable long id, @RequestBody NameRequest request) {
        return suppliers.rename(id, request.name());
    }

    @PatchMapping(path = "/api/suppliers/{id}/contact-details",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SUPPLIERS, level = AccessLevel.FULL)
    SupplierView changeContactDetails(
            @PathVariable long id, @RequestBody ContactDetailsRequest request) {
        return suppliers.changeContactDetails(id, request.email(), request.phone());
    }

    @PatchMapping(path = "/api/suppliers/{id}/vat-number",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SUPPLIERS, level = AccessLevel.FULL)
    SupplierView changeVatNumber(@PathVariable long id, @RequestBody VatNumberRequest request) {
        return suppliers.changeVatNumber(id, request.vatNumber());
    }

    /**
     * The VAT status, and its exemption reason where the status demands one.
     *
     * <p>{@code VatStatus} is shared with Customer (Q9) so the two lists cannot diverge, and it has
     * five values rather than four: an export and an intra-EU B2B supply are both VAT-free under
     * different articles and are reported differently, so folding export into "other" would lose
     * exactly what has to be stated on the document.
     *
     * <p>This is also what a purchase invoice's reverse-charge flag must agree with — required for
     * {@code INTRA_EU_B2B}, refused otherwise, never inferred.
     */
    @PatchMapping(path = "/api/suppliers/{id}/vat-status",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SUPPLIERS, level = AccessLevel.FULL)
    SupplierView changeVatStatus(@PathVariable long id, @RequestBody VatStatusRequest request) {
        return suppliers.changeVatStatus(id, request.vatStatus(), request.vatExemptionReasonId());
    }

    @PostMapping(path = "/api/suppliers/{id}/deactivate")
    @Requires(section = Section.SUPPLIERS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        suppliers.deactivate(id);
    }

    @PostMapping(path = "/api/suppliers/{id}/reactivate")
    @Requires(section = Section.SUPPLIERS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        suppliers.reactivate(id);
    }

    // -------------------------------------------------------------------------------------------

    record NameRequest(String name) {
    }

    record ContactDetailsRequest(String email, String phone) {
    }

    record VatNumberRequest(String vatNumber) {
    }

    record VatStatusRequest(VatStatus vatStatus, Long vatExemptionReasonId) {

        VatStatusRequest {
            Required.field(vatStatus, "vatStatus");
        }
    }
}
