package gr.novotrade.novocore.core.web.codification;

import gr.novotrade.novocore.core.api.codification.AadePaymentMethodService;
import gr.novotrade.novocore.core.api.codification.AadePaymentMethodView;
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
 * Annex 8.12's payment-method articles — ⚠️ <strong>read plus describe, deactivate, reactivate. No
 * create, on any installation, ever.</strong>
 *
 * <p>The <strong>third</strong> instance of the seed-only convention, after
 * {@code AadeInvoiceTypeController} and R2b's payment-method screen — which has now <em>left</em> the
 * convention, because payment methods turned out to be a business list. That is worth noticing rather
 * than glossing: the convention did not change, the classification of one list did, and this class is
 * where the seed-only half of that list ended up.
 *
 * <p>The screen over this owes all three of the convention's points: no Add control, a permanent line
 * saying <strong>AADE</strong> authors the rows, and an absence test naming the omission as permanent
 * rather than "not yet".
 *
 * <p>Governed by {@link Section#SALES}, matching {@code PaymentMethodController}: the two are read
 * together, and an article is chosen while authoring a payment method.
 */
@RestController
@Requires(section = Section.SALES)
class AadePaymentMethodController {

    private final AadePaymentMethodService articles;

    AadePaymentMethodController(AadePaymentMethodService articles) {
        this.articles = articles;
    }

    @GetMapping(path = "/api/aade-payment-methods", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<AadePaymentMethodView> aadePaymentMethods(
            @RequestParam(required = false) Boolean active) {
        return ListResponse.of(Boolean.TRUE.equals(active) ? articles.active() : articles.all());
    }

    @GetMapping(path = "/api/aade-payment-methods/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    AadePaymentMethodView aadePaymentMethod(@PathVariable long id) {
        return articles.require(id);
    }

    // -------------------------------------------------------------------------------------------
    // ⚠️ No POST. A row here is AADE's to define; a new code is a migration with the artefact it was
    // read from beside it. StatutoryCodificationRulesTest makes that a build failure.
    // -------------------------------------------------------------------------------------------

    @PatchMapping(path = "/api/aade-payment-methods/{id}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    AadePaymentMethodView describe(
            @PathVariable long id, @RequestBody AadePaymentMethodDescriptionRequest request) {
        return articles.describe(id, request.description());
    }

    @PostMapping(path = "/api/aade-payment-methods/{id}/deactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        articles.deactivate(id);
    }

    @PostMapping(path = "/api/aade-payment-methods/{id}/reactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        articles.reactivate(id);
    }

    /** Entity-prefixed deliberately: 8a made a schema-name collision a build failure. */
    record AadePaymentMethodDescriptionRequest(@Mandatory String description) {

        AadePaymentMethodDescriptionRequest {
            Required.text(description, "description");
        }
    }
}
