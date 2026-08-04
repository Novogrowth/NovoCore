package gr.novotrade.novocore.core.web.sales;

import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.PaymentMethodView;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
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
 * The business's payment methods — ⚠️ <strong>read plus describe, reorder, deactivate, reactivate.
 * No create, no delete.</strong>
 *
 * <h2>⚠️ Why this exists at all: a scoping error, not an implementation gap</h2>
 *
 * <p>The owner's nine-table specification asked for
 * {@code Τρόποι πληρωμής [ID, abbreviation, description, active/inactive, myDATA code]}. Establishing
 * that {@link SettlementMethod} is an enum was carried into R2's scope as "nothing to edit", and
 * payment methods got no screen — while delivery methods, a near-identical row in the same
 * specification, got a full CRUD one. <strong>The enum decision was right for the wrong scope.</strong>
 *
 * <h2>The seed-only convention, second instance</h2>
 *
 * <p>This is {@code AadeInvoiceTypeController}'s shape, and it follows the convention that screen
 * established: <strong>no create control, a permanent line on the screen saying who authors the
 * rows, and an absence test that names the omission as permanent rather than "not yet".</strong>
 *
 * <p>The reason no create exists is concrete rather than ceremonial: a new payment method needs an
 * {@code AccountSystemKey}, a {@code settlesImmediately} and a {@code subjectToCashLimit}, and no
 * form can supply those. 📌 The owner's own Go list contains <strong>Cheque</strong> and
 * <strong>Foreign bank account</strong>, which are <em>not</em> values of this enum — adding either
 * is exactly that code change.
 *
 * <p>Governed by {@link Section#SALES}: a payment method is chosen when a sale is recorded, the same
 * reason delivery methods are.
 */
@RestController
@Requires(section = Section.SALES)
class PaymentMethodController {

    private final PaymentMethodService paymentMethods;

    PaymentMethodController(PaymentMethodService paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    @GetMapping(path = "/api/payment-methods", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<PaymentMethodView> paymentMethods(@RequestParam(required = false) Boolean active) {
        return ListResponse.of(
                Boolean.TRUE.equals(active) ? paymentMethods.active() : paymentMethods.all());
    }

    /** ⚠️ {@code {method}} is the enum constant — there is no surrogate id, by design. */
    @GetMapping(path = "/api/payment-methods/{method}", produces = MediaType.APPLICATION_JSON_VALUE)
    PaymentMethodView paymentMethod(@PathVariable SettlementMethod method) {
        return paymentMethods.require(method);
    }

    // -------------------------------------------------------------------------------------------
    // ⚠️ No POST. See the class javadoc — a new value is a code change, not a form submission.
    // -------------------------------------------------------------------------------------------

    @PatchMapping(path = "/api/payment-methods/{method}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    PaymentMethodView describe(
            @PathVariable SettlementMethod method, @RequestBody PaymentMethodDescriptionRequest request) {
        return paymentMethods.describe(method, request.description());
    }

    /** Reorders it. ⚠️ Freely editable — a sort code appears on no document. */
    @PatchMapping(path = "/api/payment-methods/{method}/sort-code",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    PaymentMethodView changeSortCode(
            @PathVariable SettlementMethod method, @RequestBody PaymentMethodSortCodeRequest request) {
        return paymentMethods.changeSortCode(method, request.sortCode());
    }

    /**
     * Takes a method out of use for new documents — the owner will retire the ones he does not use.
     *
     * <p>⚠️ <strong>Setting is refused, holding is not.</strong> Invoices already settled by this
     * method are untouched; what changes is that recording a new one against it is refused.
     */
    @PostMapping(path = "/api/payment-methods/{method}/deactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable SettlementMethod method) {
        paymentMethods.deactivate(method);
    }

    @PostMapping(path = "/api/payment-methods/{method}/reactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable SettlementMethod method) {
        paymentMethods.reactivate(method);
    }

    /**
     * Named with the entity prefix deliberately: 8a made a schema-name collision a build failure
     * after finding four of them, and {@code DescriptionRequest} is already claimed.
     */
    record PaymentMethodDescriptionRequest(@Mandatory String description) {

        PaymentMethodDescriptionRequest {
            Required.text(description, "description");
        }
    }

    /** Boxed, so an omitted field is a 400 naming it rather than arriving as {@code 0}. */
    record PaymentMethodSortCodeRequest(@Mandatory Integer sortCode) {

        PaymentMethodSortCodeRequest {
            Required.field(sortCode, "sortCode");
        }
    }
}
