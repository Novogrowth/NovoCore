package gr.novotrade.novocore.core.api.bundle;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.Objects;

/**
 * One component line produced by decomposing a bundle. What inventory consumes, what COGS is computed
 * from, and what an invoice shows if it shows components.
 *
 * @param bundleProductId the bundle this line came out of. <strong>This is the link</strong> brief §5
 *     asks for when it says revenue reporting shows both levels "linked, not duplicated": a component
 *     line always knows which bundle line it belongs to, so a report can roll up either way without
 *     holding two copies of the same sale.
 * @param quantity the total for this line — the component's per-bundle quantity multiplied by the
 *     number of bundles, not the per-bundle figure.
 * @param allocatedAmount this component's share of the bundle line's value, from
 *     {@link gr.novotrade.novocore.core.api.shared.ProportionalAllocation}. The shares sum to the bundle's total exactly.
 */
public record BundleComponentLine(
        long bundleProductId,
        long componentProductId,
        @Mandatory String componentSku,
        @Mandatory String componentName,
        @Mandatory Quantity quantity,
        @Mandatory Money allocatedAmount,
        boolean stocked) {

    public BundleComponentLine {
        Objects.requireNonNull(componentSku, "componentSku");
        Objects.requireNonNull(componentName, "componentName");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(allocatedAmount, "allocatedAmount");
    }
}
