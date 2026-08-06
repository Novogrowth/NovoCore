package gr.novotrade.novocore.core.web.sales;

import gr.novotrade.novocore.core.api.sales.NewPaymentMethod;
import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.PaymentMethodView;
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
 * The business's payment methods — ⚠️ <strong>FULL CRUD since R4.</strong>
 *
 * <h2>⚠️ This class used to say "no create, and there never will be"</h2>
 *
 * <p>R2b built these as a seed-only statutory list, one row per {@code SettlementMethod} enum value,
 * on the concrete reason that a new method needs an account key and two behaviour flags no form can
 * supply. <strong>The requirement was wrong, not the implementation.</strong> Payment methods are a
 * business list that <em>references</em> an AADE codification — R1a's two layers, one entity over —
 * so the list ships empty and the owner authors every row.
 *
 * <p>⭐ The old argument survives and changed sign: what it named as the reason creation was
 * impossible is now the field list of {@link NewPaymentMethod}. The account is supplied as a real
 * account rather than an {@code AccountSystemKey}, and both flags are <em>derived</em> — settling
 * immediately from the account's kind, the cash limit from the AADE article.
 *
 * <p>📌 <strong>The seed-only convention no longer applies here</strong>, and its three points are
 * withdrawn together: there is an Add control, no line saying who authors the rows (the owner does),
 * and the absence test that pinned the omission as permanent is deleted rather than weakened. The
 * convention itself stands and still governs {@code AadeInvoiceTypeController} and the new
 * {@code AadePaymentMethodController}.
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

    @GetMapping(path = "/api/payment-methods/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    PaymentMethodView paymentMethod(@PathVariable long id) {
        return paymentMethods.require(id);
    }

    @PostMapping(path = "/api/payment-methods",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    PaymentMethodView createPaymentMethod(@RequestBody NewPaymentMethod request) {
        return paymentMethods.create(request);
    }

    @PatchMapping(path = "/api/payment-methods/{id}/abbreviation",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    PaymentMethodView changeAbbreviation(
            @PathVariable long id, @RequestBody PaymentMethodAbbreviationRequest request) {
        return paymentMethods.changeAbbreviation(id, request.abbreviation());
    }

    @PatchMapping(path = "/api/payment-methods/{id}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    PaymentMethodView describe(
            @PathVariable long id, @RequestBody PaymentMethodDescriptionRequest request) {
        return paymentMethods.describe(id, request.description());
    }

    /** ⚠️ Frozen once a document has been settled by this method — the article decides what it declared. */
    @PatchMapping(path = "/api/payment-methods/{id}/aade-article",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    PaymentMethodView changeArticle(
            @PathVariable long id, @RequestBody PaymentMethodArticleRequest request) {
        return paymentMethods.changeArticle(id, request.aadePaymentMethodId());
    }

    /** ⚠️ Frozen once used — changing where a method posted, after it has posted, would break the ledger. */
    @PatchMapping(path = "/api/payment-methods/{id}/account",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    PaymentMethodView changeAccount(
            @PathVariable long id, @RequestBody PaymentMethodAccountRequest request) {
        return paymentMethods.changeAccount(id, request.accountId());
    }

    /** Reorders it. ⚠️ Freely editable, and NOT frozen — a sort code appears on no document. */
    @PatchMapping(path = "/api/payment-methods/{id}/sort-code",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    PaymentMethodView changeSortCode(
            @PathVariable long id, @RequestBody PaymentMethodSortCodeRequest request) {
        return paymentMethods.changeSortCode(id, request.sortCode());
    }

    /**
     * Takes a method out of use for new documents.
     *
     * <p>⚠️ <strong>Setting is refused, holding is not.</strong> Invoices already settled by this
     * method are untouched; what changes is that recording a new one against it is refused.
     */
    @PostMapping(path = "/api/payment-methods/{id}/deactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        paymentMethods.deactivate(id);
    }

    @PostMapping(path = "/api/payment-methods/{id}/reactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        paymentMethods.reactivate(id);
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

    record PaymentMethodAbbreviationRequest(@Mandatory String abbreviation) {

        PaymentMethodAbbreviationRequest {
            Required.text(abbreviation, "abbreviation");
        }
    }

    /** Boxed, so an omitted field is a 400 naming it rather than arriving as {@code 0}. */
    record PaymentMethodArticleRequest(@Mandatory Long aadePaymentMethodId) {

        PaymentMethodArticleRequest {
            Required.field(aadePaymentMethodId, "aadePaymentMethodId");
        }
    }

    /** Boxed, for the same reason. */
    record PaymentMethodAccountRequest(@Mandatory Long accountId) {

        PaymentMethodAccountRequest {
            Required.field(accountId, "accountId");
        }
    }

    /** Boxed, so an omitted field is a 400 naming it rather than arriving as {@code 0}. */
    record PaymentMethodSortCodeRequest(@Mandatory Integer sortCode) {

        PaymentMethodSortCodeRequest {
            Required.field(sortCode, "sortCode");
        }
    }
}
