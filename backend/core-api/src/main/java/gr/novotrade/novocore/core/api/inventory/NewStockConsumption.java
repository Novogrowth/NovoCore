package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Request to take stock out of inventory as a cost of sale.
 *
 * <p><strong>Two shapes, and the product decides which.</strong> Pooled stock states a quantity and
 * FIFO chooses the lots. Serial-tracked stock names the units, and there is no FIFO at all — brief §5
 * is explicit that a serialized item uses its own actual cost, because FIFO exists to handle units
 * that cannot be told apart and these can. Which machine left the shelf is a fact somebody scanned,
 * not something a costing rule may decide.
 *
 * <p><strong>The serialized shape requires a {@link SaleReference}</strong>, which is the step 6
 * obligation discharged: marking a unit {@code SOLD} means recording who bought it and on what (brief
 * §5), and step 6 refused to add a nullable customer id that would let a unit be sold to nobody.
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
 * @param serialNumbers the units being taken, for a serial-tracked product; empty for pooled stock.
 *     When present, {@link #quantity()} is their count.
 */
public record NewStockConsumption(
        long productId,
        @Mandatory Quantity quantity,
        @Mandatory LocalDate consumptionDate,
        @Mandatory JournalSource source,
        String note,
        List<String> serialNumbers,
        SaleReference saleReference) {

    public NewStockConsumption {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(consumptionDate, "consumptionDate");
        Objects.requireNonNull(source, "source");
        note = (note == null || note.isBlank()) ? null : note.trim();
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);

        if (productId <= 0) {
            throw new IllegalArgumentException(
                    "productId must be a positive NovoCore id, got " + productId);
        }
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException(
                    "Consumed quantity " + quantity + " is not positive. Returning stock is "
                            + "returnConsumed, and undoing a consumption is reverseConsumption; "
                            + "neither is a negative consumption.");
        }
        if (!source.mayConsumeStock()) {
            throw new IllegalArgumentException(
                    source + " may not consume stock. Consuming reduces lots and posts cost of goods "
                            + "sold in one transaction, so the sources that may do it are named on "
                            + "JournalSource.mayConsumeStock() and a new one has to opt in there "
                            + "deliberately. A write-off is a loss rather than a cost of sale and has "
                            + "its own path, with its own reason code.");
        }
        if (!serialNumbers.isEmpty()) {
            if (saleReference == null) {
                throw new IllegalArgumentException(
                        "A serialized consumption must say who bought the units and on what. Brief §5 "
                                + "puts the customer and invoice on a sold unit, and a unit marked "
                                + "SOLD with neither is a claim nothing can substantiate — which is "
                                + "exactly why step 6 left that status unreachable rather than "
                                + "half-wiring it.");
            }
            if (Quantity.of(serialNumbers.size()).compareTo(quantity) != 0) {
                throw new IllegalArgumentException(
                        "This consumption names " + serialNumbers.size() + " units but states a "
                                + "quantity of " + quantity + ". For serial-tracked stock the "
                                + "quantity IS the count of units, so the two cannot differ.");
            }
        } else if (saleReference != null) {
            throw new IllegalArgumentException(
                    "A pooled consumption named a customer and an invoice line. Brief §5 puts that "
                            + "link on a serialized unit, and pooled stock has no unit to carry it — "
                            + "which document consumed the stock is on the document, one direction.");
        }
    }

    /** Pooled stock, taken FIFO. */
    public static NewStockConsumption of(
            long productId, Quantity quantity, LocalDate consumptionDate, JournalSource source) {
        return new NewStockConsumption(
                productId, quantity, consumptionDate, source, null, List.of(), null);
    }

    /** Named units, at their own costs, marked sold to the buyer on the line that sold them. */
    public static NewStockConsumption ofUnits(long productId, List<String> serialNumbers,
            LocalDate consumptionDate, JournalSource source, SaleReference saleReference) {
        return new NewStockConsumption(productId, Quantity.of(serialNumbers.size()), consumptionDate,
                source, null, serialNumbers, saleReference);
    }

    public NewStockConsumption noted(String consumptionNote) {
        return new NewStockConsumption(productId, quantity, consumptionDate, source, consumptionNote,
                serialNumbers, saleReference);
    }

    /** True when this names specific units rather than asking FIFO for a quantity. */
    public boolean isSerialized() {
        return !serialNumbers.isEmpty();
    }

    public Optional<SaleReference> sale() {
        return Optional.ofNullable(saleReference);
    }
}
