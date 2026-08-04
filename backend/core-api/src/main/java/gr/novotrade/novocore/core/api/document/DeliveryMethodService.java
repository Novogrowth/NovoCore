package gr.novotrade.novocore.core.api.document;

import java.util.List;
import java.util.Optional;

/**
 * How goods reach the customer. The business's own list; ships empty.
 *
 * <p>⚠️ Not a statutory codification — see {@link DeliveryMethodView} for why annex 8.14 is a
 * different question.
 */
public interface DeliveryMethodService {

    List<DeliveryMethodView> all();

    List<DeliveryMethodView> active();

    Optional<DeliveryMethodView> find(long id);

    /** @throws DeliveryMethodNotFoundException if absent */
    DeliveryMethodView require(long id);

    /** @throws InvalidDeliveryMethodException if the abbreviation duplicates one */
    DeliveryMethodView create(NewDeliveryMethod request);

    DeliveryMethodView describe(long id, String description);

    /**
     * Corrects the abbreviation — R2's correction path.
     *
     * <p>⚠️ The "in use" refusal <strong>cannot fire today</strong>: no table in this schema has a
     * foreign key to {@code delivery_method} at all. See {@link DeliveryMethodView#inUse()}.
     *
     * @throws InvalidDeliveryMethodException if the new abbreviation duplicates another
     */
    DeliveryMethodView changeAbbreviation(long id, String abbreviation);

    void deactivate(long id);

    void reactivate(long id);
}
