package gr.novotrade.novocore.core.web.document;

import gr.novotrade.novocore.core.api.document.DeliveryMethodService;
import gr.novotrade.novocore.core.api.document.DeliveryMethodView;
import gr.novotrade.novocore.core.api.document.NewDeliveryMethod;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.web.document.DocumentReferenceRequests.DocumentDescriptionRequest;
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
 * How goods reach the customer. The business's own list; ⚠️ <strong>ships empty.</strong>
 *
 * <p>Not an AADE codification, and worth saying because it sits beside two things that are: annex
 * 8.14 {@code Σκοπός Διακίνησης} is the transport <em>purpose</em> and belongs with 18b, which is a
 * different question from who carries the parcel. Nothing here is transmitted.
 *
 * <p>Under {@link Section#SALES} because a delivery method is chosen when a sale is recorded.
 */
@RestController
@Requires(section = Section.SALES)
class DeliveryMethodController {

    private final DeliveryMethodService deliveryMethods;

    DeliveryMethodController(DeliveryMethodService deliveryMethods) {
        this.deliveryMethods = deliveryMethods;
    }

    @GetMapping(path = "/api/delivery-methods", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<DeliveryMethodView> deliveryMethods(@RequestParam(required = false) Boolean active) {
        return ListResponse.of(
                Boolean.TRUE.equals(active) ? deliveryMethods.active() : deliveryMethods.all());
    }

    @GetMapping(path = "/api/delivery-methods/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    DeliveryMethodView deliveryMethod(@PathVariable long id) {
        return deliveryMethods.require(id);
    }

    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/delivery-methods",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    DeliveryMethodView create(@RequestBody NewDeliveryMethod request) {
        return deliveryMethods.create(request);
    }

    /** The description only. The abbreviation is the identity and is what a document prints. */
    @PatchMapping(path = "/api/delivery-methods/{id}/description",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    DeliveryMethodView describe(
            @PathVariable long id, @RequestBody DocumentDescriptionRequest request) {
        return deliveryMethods.describe(id, request.description());
    }

    @PostMapping(path = "/api/delivery-methods/{id}/deactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        deliveryMethods.deactivate(id);
    }

    @PostMapping(path = "/api/delivery-methods/{id}/reactivate")
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        deliveryMethods.reactivate(id);
    }
}
