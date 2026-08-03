package gr.novotrade.novocore.core.api.bundle;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.Objects;

/**
 * One line of a bundle definition: which product, and how many of it per bundle.
 *
 * @param quantity how many per <em>one</em> bundle. Must be positive, and must be whole unless the
 *     component's unit of measure allows a fraction — 250 grams of coffee in a gift set is fine, two
 *     and a half grinders is a typing mistake.
 */
public record NewBundleComponent(long componentProductId, @Mandatory Quantity quantity) {

    public NewBundleComponent {
        Objects.requireNonNull(quantity, "quantity");
    }

    /** The common case: one of this product per bundle. */
    public static NewBundleComponent one(long componentProductId) {
        return new NewBundleComponent(componentProductId, Quantity.of(1L));
    }

    public static NewBundleComponent of(long componentProductId, long quantity) {
        return new NewBundleComponent(componentProductId, Quantity.of(quantity));
    }
}
