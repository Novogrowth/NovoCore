package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Request to record stock arriving.
 *
 * <p>The two shapes are separate factories rather than one call with optional arguments, because the
 * choice is not the caller's: it follows from whether the product is serial-tracked, and getting it
 * wrong is refused rather than absorbed.
 *
 * <p><strong>A quantity is refused on a serialized request, not merely ignored.</strong> The unit
 * count <em>is</em> the quantity, so a supplied one is either redundant or a disagreement — and a
 * request stating "5" alongside four serial numbers has to fail, not pick one. Same reasoning as
 * every other place in this schema where a second copy of a derivable number is left out.
 *
 * @param location where the stock is going. Required in both shapes: for pooled stock it lands on the
 *     lot, for serialized stock on each unit. It is normally {@link StockLocation#INVENTORY}, but a
 *     delivery that arrives already broken should be recordable straight into Damaged Goods rather
 *     than received onto the shelf and moved a second later.
 * @param serialNumbers one per unit, in the serialized shape only. Duplicates within the request are
 *     refused, as is a serial another unit already carries.
 * @param goodsReceiptLineId the delivery line this lot came from — brief §5's source document
 *     reference, and the step 8 obligation V12 deliberately deferred rather than adding a column that
 *     would mean nothing for a whole step. <strong>Nullable, and the null case is real</strong>: the
 *     phase 2b migration from Manager brings in stock that no NovoCore delivery ever created, and
 *     null says exactly that rather than pretending to a document that does not exist.
 */
public record NewInventoryLot(
        long productId,
        Quantity quantity,
        UnitCost unitCost,
        LocalDate acquisitionDate,
        LocalDate roastDate,
        StockLocation location,
        List<String> serialNumbers,
        Long goodsReceiptLineId) {

    public NewInventoryLot {
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(acquisitionDate, "acquisitionDate");
        Objects.requireNonNull(location, "location");
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);

        if (quantity != null && !serialNumbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "A serialized receipt states its serial numbers and nothing else: the unit count "
                            + "is the quantity. Supplying both leaves two numbers that can disagree, "
                            + "and this request supplies " + quantity + " against "
                            + serialNumbers.size() + " serial numbers.");
        }
        if (quantity == null && serialNumbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "A receipt needs either a quantity (pooled stock) or at least one serial number "
                            + "(serial-tracked stock).");
        }
        if (goodsReceiptLineId != null && goodsReceiptLineId <= 0) {
            throw new IllegalArgumentException(
                    "goodsReceiptLineId must be a positive NovoCore id, got " + goodsReceiptLineId);
        }
    }

    /** Pooled stock: a quantity at a location, no individual identities. */
    public static NewInventoryLot pooled(long productId, Quantity quantity, UnitCost unitCost,
            LocalDate acquisitionDate, StockLocation location) {
        return new NewInventoryLot(
                productId, quantity, unitCost, acquisitionDate, null, location, List.of(), null);
    }

    /** Serial-tracked stock: one unit per serial number, and no separate quantity. */
    public static NewInventoryLot serialized(long productId, UnitCost unitCost,
            LocalDate acquisitionDate, StockLocation location, List<String> serialNumbers) {
        return new NewInventoryLot(
                productId, null, unitCost, acquisitionDate, null, location, serialNumbers, null);
    }

    /** The same request with a roast date, for coffee. */
    public NewInventoryLot roastedOn(LocalDate newRoastDate) {
        return new NewInventoryLot(productId, quantity, unitCost, acquisitionDate, newRoastDate,
                location, serialNumbers, goodsReceiptLineId);
    }

    /** The same request, recording the delivery line that brought the stock in. */
    public NewInventoryLot receivedOn(long receiptLineId) {
        return new NewInventoryLot(productId, quantity, unitCost, acquisitionDate, roastDate,
                location, serialNumbers, receiptLineId);
    }

    /** True when this request describes serial-tracked stock. */
    public boolean isSerialized() {
        return !serialNumbers.isEmpty();
    }
}
