package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.Objects;
import java.util.Optional;

/**
 * One component of a bundle line, as it was allocated on the day of the sale.
 *
 * <p>Brief §5's second level of a bundle sale: the bundle line is what the customer bought, and these
 * are what left the shelf and what cost of goods sold was computed against. They are the same money
 * seen from two directions — {@code BundleDecomposition} enforces that the components sum exactly to
 * the bundle line — so a report presents one level or the other and gets the same revenue. Adding
 * them together is visibly double-counting.
 *
 * <p><strong>Materialised, never recomputed.</strong> These are a copy of what was allocated when the
 * sale was recorded, not a live read of the bundle's current definition. That is what makes brief §5's
 * "alias forward, never rewrite history" true here without an alias table: dissolving the bundle
 * afterwards, or redefining its components, changes nothing about an invoice already recorded.
 *
 * <p><strong>Consequently a report must read this and never {@code BundleService.componentsOf}.</strong>
 * The current definition can differ from the one that was sold, or be gone entirely.
 */
public record SalesInvoiceLineComponentView(
        long id,
        int componentNumber,
        long productId,
        String productSku,
        Quantity quantity,
        Money allocatedAmount,
        Long stockConsumptionId) {

    public SalesInvoiceLineComponentView {
        Objects.requireNonNull(productSku, "productSku");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(allocatedAmount, "allocatedAmount");
    }

    /** The stock this component took out, or empty when it took none — a service, or a zero-cost lot. */
    public Optional<Long> stockConsumption() {
        return Optional.ofNullable(stockConsumptionId);
    }
}
