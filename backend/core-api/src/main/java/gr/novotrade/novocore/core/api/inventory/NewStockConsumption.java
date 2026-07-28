package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Request to take stock out of inventory as a cost of sale, consuming lots FIFO.
 *
 * <p><strong>Pooled stock only.</strong> A serial-tracked product is refused, naming step 9: consuming
 * an identified unit means marking it {@code SOLD}, and brief §5 requires the customer and invoice to
 * be recorded on it when that happens. A nullable customer id added here would let a unit be marked
 * sold to nobody, with no document behind it — which is precisely why step 6 left
 * {@code SerializedUnitStatus.SOLD} declared and unreachable rather than half-wiring it.
 *
 * <p><strong>FIFO order is not this request's to choose.</strong> Lots are consumed in the order
 * {@code InventoryService.lotsOf} already defines — acquisition date, then id — stated once, as an
 * index in V12, so a backdated receipt sorts where it belongs rather than where it was typed. Only
 * lots at a sellable location are candidates: stock in Damaged Goods is still an asset, and selling
 * it is not something a costing rule may quietly decide to do.
 *
 * @param source what is consuming the stock. Required, and refused unless
 *     {@link JournalSource#mayConsumeStock()} — consumption reduces lots <em>and</em> posts cost of
 *     goods sold, so an unrestricted source would let anything at all derecognise inventory as a cost
 *     of sale. It also decides the correction policy of the entry that results (Q13).
 * @param consumptionDate the accounting date of the cost. Normally the date of the document consuming
 *     the stock, so COGS lands in the period its revenue did.
 */
public record NewStockConsumption(
        long productId,
        Quantity quantity,
        LocalDate consumptionDate,
        JournalSource source,
        String note) {

    public NewStockConsumption {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(consumptionDate, "consumptionDate");
        Objects.requireNonNull(source, "source");
        note = (note == null || note.isBlank()) ? null : note.trim();

        if (productId <= 0) {
            throw new IllegalArgumentException(
                    "productId must be a positive NovoCore id, got " + productId);
        }
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException(
                    "Consumed quantity " + quantity + " is not positive. Returning stock is "
                            + "reverseConsumption, not a negative consumption.");
        }
        if (!source.mayConsumeStock()) {
            throw new IllegalArgumentException(
                    source + " may not consume stock. Consuming reduces lots and posts cost of goods "
                            + "sold in one transaction, so the sources that may do it are named on "
                            + "JournalSource.mayConsumeStock() and a new one has to opt in there "
                            + "deliberately. A write-off is a loss rather than a cost of sale and has "
                            + "its own path, with its own reason code.");
        }
    }

    public static NewStockConsumption of(
            long productId, Quantity quantity, LocalDate consumptionDate, JournalSource source) {
        return new NewStockConsumption(productId, quantity, consumptionDate, source, null);
    }

    public NewStockConsumption noted(String consumptionNote) {
        return new NewStockConsumption(
                productId, quantity, consumptionDate, source, consumptionNote);
    }
}
