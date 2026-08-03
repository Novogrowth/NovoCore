package gr.novotrade.novocore.core.api.product;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;
import java.util.Objects;

/**
 * Request to add a unit of measure.
 *
 * @param mydataCode may be null, meaning no AADE mapping is known yet. Null is the honest value
 *     there; a composed substitute would be a fabricated code that later gets transmitted.
 */
public record NewUnitOfMeasure(
        @Mandatory String code,
        @Mandatory String name,
        @Mandatory Boolean fractionalQuantityAllowed,
        String mydataCode) {

    public NewUnitOfMeasure {
        Required.field(fractionalQuantityAllowed, "fractionalQuantityAllowed");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }

    /** A unit whose AADE code is not known yet — the state every seeded unit is in. */
    public static NewUnitOfMeasure withoutMydataCode(
            String code, String name, boolean fractionalQuantityAllowed) {
        return new NewUnitOfMeasure(code, name, fractionalQuantityAllowed, null);
    }
}
