package gr.novotrade.novocore.core.api.bundle;

import gr.novotrade.novocore.core.api.product.UnitOfMeasureView;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * One component of a bundle, as defined.
 *
 * @param quantityPerBundle how many of this component one bundle contains
 * @param standalonePrice the component's own selling price, or null where it has none. Null is what
 *     makes a bundle undecomposable: the allocation is proportional to standalone value, and a
 *     component with no price contributes an unknown share rather than a zero one. See
 *     {@code BundleService.bundlesWithUnpricedComponents()}.
 * @param stocked whether this component constrains how many bundles can be assembled. A service
 *     component — an installation, a training session — does not.
 */
public record BundleComponentView(
        long bundleProductId,
        long componentProductId,
        @Mandatory String componentSku,
        @Mandatory String componentName,
        @Mandatory Quantity quantityPerBundle,
        @Mandatory UnitOfMeasureView componentUnitOfMeasure,
        Money standalonePrice,
        boolean stocked,
        boolean componentActive) {

    public BundleComponentView {
        Objects.requireNonNull(componentSku, "componentSku");
        Objects.requireNonNull(componentName, "componentName");
        Objects.requireNonNull(quantityPerBundle, "quantityPerBundle");
        Objects.requireNonNull(componentUnitOfMeasure, "componentUnitOfMeasure");
    }

    public Optional<Money> standalonePriceIfAny() {
        return Optional.ofNullable(standalonePrice);
    }

    /**
     * This component's standalone value inside one bundle — its own price extended across its
     * quantity. The weight proportional allocation uses.
     *
     * @throws BundleNotDecomposableException if the component has no standalone price
     */
    public Money standaloneValuePerBundle() {
        if (standalonePrice == null) {
            throw BundleNotDecomposableException.unpricedComponent(
                    bundleProductId, componentSku);
        }
        // timesExact would refuse a fractional quantity that needs rounding — which is precisely the
        // 0.250 kg case — so the multiplication goes through the quantity, which rounds once.
        return standalonePrice.times(quantityPerBundle.value(), RoundingMode.HALF_UP);
    }
}
