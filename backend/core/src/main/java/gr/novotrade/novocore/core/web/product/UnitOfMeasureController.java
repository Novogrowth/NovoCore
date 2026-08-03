package gr.novotrade.novocore.core.web.product;

import gr.novotrade.novocore.core.api.product.NewUnitOfMeasure;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.api.shared.Required;
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
 * Units of measure — the picker on a product form, and the table behind it.
 *
 * <p>Declared under {@link Section#PRODUCTS} rather than {@code TAX_AND_CHARGES}, although the
 * myDATA code on each row is a tax concern: this list is read when creating a product and by nobody
 * else, and a section is about who needs to see something, not about which authority defines it.
 *
 * <p>A runtime-editable table rather than an enum since V11 (Q34), because myDATA's unit codes are
 * AADE's data and an enum constant cannot own them. Step 14 shipped it read-only; step 16b adds the
 * editing that "runtime-editable" was always supposed to mean.
 *
 * <p><strong>A unit a product still uses cannot be deactivated</strong> — the service refuses and
 * names the products. That refusal is the reason this is safe to expose: the destructive-looking
 * operation is the one the core already guards.
 */
@RestController
@Requires(section = Section.PRODUCTS)
class UnitOfMeasureController {

    private final UnitOfMeasureService unitsOfMeasure;

    UnitOfMeasureController(UnitOfMeasureService unitsOfMeasure) {
        this.unitsOfMeasure = unitsOfMeasure;
    }

    /**
     * <p>{@code search} matches the code and the name anywhere in the string, ignoring case and
     * accents. It does <strong>not</strong> match the myDATA code: that column is NULL on every
     * seeded unit, so searching it would match nothing on every installation that exists today —
     * {@code /without-mydata-code} below is the route that asks the real question about it.
     */
    @GetMapping(path = "/api/units-of-measure", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<UnitOfMeasureView> unitsOfMeasure(@RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        boolean activeOnly = Boolean.TRUE.equals(active);
        if (search != null) {
            return ListResponse.of(unitsOfMeasure.search(search, activeOnly));
        }
        return ListResponse.of(activeOnly ? unitsOfMeasure.active() : unitsOfMeasure.all());
    }

    /**
     * The rows still waiting on an AADE myDATA code (Q38).
     *
     * <p>⚠️ <strong>A real outstanding item, not a diagnostic.</strong> {@code mydataCode} is
     * nullable because the codes are pending the accountant, and phase 7 cannot transmit a line whose
     * unit has none. Until step 16b there was no way to see the list without opening {@code psql},
     * which is why a question waiting on somebody else was invisible to the person who has to ask
     * them.
     */
    @GetMapping(path = "/api/units-of-measure/without-mydata-code",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<UnitOfMeasureView> withoutMydataCode() {
        return ListResponse.of(unitsOfMeasure.withoutMydataCode());
    }

    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/units-of-measure",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    UnitOfMeasureView create(@RequestBody NewUnitOfMeasure request) {
        return unitsOfMeasure.create(request);
    }

    /** The name only. The code is the identity and is not editable. */
    @PatchMapping(path = "/api/units-of-measure/{id}/name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    UnitOfMeasureView rename(@PathVariable long id, @RequestBody NameRequest request) {
        return unitsOfMeasure.rename(id, request.name());
    }

    /** Records the AADE code once the accountant supplies it — the answer to Q38, one row at a time. */
    @PatchMapping(path = "/api/units-of-measure/{id}/mydata-code",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    UnitOfMeasureView recordMydataCode(
            @PathVariable long id, @RequestBody MydataCodeRequest request) {
        return unitsOfMeasure.recordMydataCode(id, request.mydataCode());
    }

    /**
     * Whether quantities in this unit may have a fractional part.
     *
     * <p>⚠️ Not cosmetic: it decides whether half a unit is a valid quantity anywhere it is used.
     * Kilograms yes, machines no.
     */
    @PatchMapping(path = "/api/units-of-measure/{id}/fractional-quantity",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    UnitOfMeasureView changeFractionalQuantityAllowed(
            @PathVariable long id, @RequestBody FractionalQuantityRequest request) {
        return unitsOfMeasure.changeFractionalQuantityAllowed(id, request.allowed());
    }

    /** Refused while any product still uses it, naming them — nothing is ever deleted. */
    @PostMapping(path = "/api/units-of-measure/{id}/deactivate")
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        unitsOfMeasure.deactivate(id);
    }

    @PostMapping(path = "/api/units-of-measure/{id}/reactivate")
    @Requires(section = Section.PRODUCTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        unitsOfMeasure.reactivate(id);
    }

    // -------------------------------------------------------------------------------------------

    record NameRequest(@Mandatory String name) {

        NameRequest {
            Required.text(name, "name");
        }
    }

    /** The code as AADE publishes it, stored verbatim — never composed from a description. */
    record MydataCodeRequest(@Mandatory String mydataCode) {

        MydataCodeRequest {
            Required.text(mydataCode, "mydataCode");
        }
    }

    /**
     * @param allowed boxed, so an omitted field is a 400 naming it rather than arriving as
     *     {@code false} and silently forbidding fractional quantities on a unit nobody meant to change
     */
    record FractionalQuantityRequest(@Mandatory Boolean allowed) {

        FractionalQuantityRequest {
            Required.field(allowed, "allowed");
        }
    }
}
