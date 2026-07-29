package gr.novotrade.novocore.core.web.product;

import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Units of measure — <strong>read-only</strong>, for the picker on a product form.
 *
 * <p>Declared under {@link Section#PRODUCTS} rather than {@code TAX_AND_CHARGES}, although the
 * myDATA code on each row is a tax concern: this list is read when creating a product and by nobody
 * else, and a section is about who needs to see something, not about which authority defines it.
 *
 * <p>A runtime-editable table rather than an enum since V11 (Q34), because myDATA's unit codes are
 * AADE's data and an enum constant cannot own them. Administering the table is not exposed here for
 * the same reason VAT classes are not: nothing in step 14's workflows needs it, and the row a
 * product still uses cannot simply be deactivated — the service refuses, naming the products.
 */
@RestController
@Requires(section = Section.PRODUCTS)
class UnitOfMeasureController {

    private final UnitOfMeasureService unitsOfMeasure;

    UnitOfMeasureController(UnitOfMeasureService unitsOfMeasure) {
        this.unitsOfMeasure = unitsOfMeasure;
    }

    @GetMapping(path = "/api/units-of-measure", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<UnitOfMeasureView> unitsOfMeasure(@RequestParam(required = false) Boolean active) {
        return ListResponse.of(
                Boolean.TRUE.equals(active) ? unitsOfMeasure.active() : unitsOfMeasure.all());
    }
}
