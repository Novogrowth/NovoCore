package gr.novotrade.novocore.core.api.bundle;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.List;
import java.util.Objects;

/**
 * A bundle sale, expressed at both levels at once. <strong>Brief §5's one core-level rule that
 * decomposes a bundle into component lines</strong> — the thing that today exists three times over as
 * separate ad hoc handling in WooCommerce, Skroutz and Go.
 *
 * <p><strong>Both levels, linked, not duplicated.</strong> The bundle level is what the customer
 * bought and what the invoice line says; the component level is what left the shelf, what COGS is
 * computed against, and what per-product revenue reporting needs. They are the same money seen from
 * two directions, and the constructor enforces exactly that: {@link #componentLines} sum to
 * {@link #bundleTotal}, always. So a report presents one level or the other and gets the same revenue;
 * adding them together double-counts, and the invariant here is what makes that a statement about the
 * data rather than a hope about the reader.
 *
 * @param bundleQuantity how many bundles were sold
 * @param bundleTotal the bundle line's own value — what the customer is charged for the bundles,
 *     which is normally less than the components' standalone values add up to. That discount is the
 *     entire reason the allocation has to be proportional rather than a copy of the list prices.
 */
public record BundleDecomposition(
        long bundleProductId,
        @Mandatory String bundleSku,
        @Mandatory Quantity bundleQuantity,
        @Mandatory Money bundleTotal,
        @Mandatory List<BundleComponentLine> componentLines) {

    public BundleDecomposition {
        Objects.requireNonNull(bundleSku, "bundleSku");
        Objects.requireNonNull(bundleQuantity, "bundleQuantity");
        Objects.requireNonNull(bundleTotal, "bundleTotal");
        Objects.requireNonNull(componentLines, "componentLines");
        componentLines = List.copyOf(componentLines);

        if (componentLines.isEmpty()) {
            throw BundleNotDecomposableException.noComponents(bundleProductId);
        }

        Money allocated = Money.zero(bundleTotal.currency());
        for (BundleComponentLine line : componentLines) {
            allocated = allocated.plus(line.allocatedAmount());
        }
        if (!allocated.equals(bundleTotal)) {
            throw new IllegalArgumentException(
                    "Bundle '" + bundleSku + "' totals " + bundleTotal + " but its component lines "
                            + "allocate " + allocated + ". The two levels of a bundle are the same "
                            + "revenue, so they must agree to the cent — otherwise a report is right "
                            + "at one level and wrong at the other with nothing to say which.");
        }
    }

    /**
     * The component lines that consume stock. A service component — installation, training — has
     * revenue allocated to it but nothing to take off a shelf.
     */
    public List<BundleComponentLine> stockedLines() {
        return componentLines.stream().filter(BundleComponentLine::stocked).toList();
    }
}
