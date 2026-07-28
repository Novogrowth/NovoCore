package gr.novotrade.novocore.core.api.product;

import java.util.Objects;

/**
 * Request to add a unit of measure.
 *
 * @param mydataCode may be null, meaning no AADE mapping is known yet. Null is the honest value
 *     there; a composed substitute would be a fabricated code that later gets transmitted.
 */
public record NewUnitOfMeasure(
        String code,
        String name,
        boolean fractionalQuantityAllowed,
        String mydataCode) {

    public NewUnitOfMeasure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }

    /** A unit whose AADE code is not known yet — the state every seeded unit is in. */
    public static NewUnitOfMeasure withoutMydataCode(
            String code, String name, boolean fractionalQuantityAllowed) {
        return new NewUnitOfMeasure(code, name, fractionalQuantityAllowed, null);
    }
}
