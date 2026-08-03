package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.Objects;

/**
 * One lot's contribution to a consumption — brief §6's "one line per lot consumed".
 *
 * <p>The lot's own unit cost, not an average and not the product's latest: that is what FIFO means,
 * and it is why the cost of one sale is a list of figures rather than a single one. Three units taken
 * from a lot bought in March and two from a lot bought in June cost what March and June cost.
 *
 * @param cost the lot's unit cost extended across {@link #quantity} and rounded once. What the COGS
 *     line for this lot actually carried.
 */
public record StockConsumptionLineView(
        long id,
        long lotId,
        @Mandatory Quantity quantity,
        @Mandatory UnitCost unitCost,
        @Mandatory Money cost) {

    public StockConsumptionLineView {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(cost, "cost");
    }
}
