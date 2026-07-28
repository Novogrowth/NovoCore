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
 */
public record NewInventoryLot(
        long productId,
        Quantity quantity,
        UnitCost unitCost,
        LocalDate acquisitionDate,
        LocalDate roastDate,
        StockLocation location,
        List<String> serialNumbers) {

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
    }

    /** Pooled stock: a quantity at a location, no individual identities. */
    public static NewInventoryLot pooled(long productId, Quantity quantity, UnitCost unitCost,
            LocalDate acquisitionDate, StockLocation location) {
        return new NewInventoryLot(
                productId, quantity, unitCost, acquisitionDate, null, location, List.of());
    }

    /** Serial-tracked stock: one unit per serial number, and no separate quantity. */
    public static NewInventoryLot serialized(long productId, UnitCost unitCost,
            LocalDate acquisitionDate, StockLocation location, List<String> serialNumbers) {
        return new NewInventoryLot(
                productId, null, unitCost, acquisitionDate, null, location, serialNumbers);
    }

    /** The same request with a roast date, for coffee. */
    public NewInventoryLot roastedOn(LocalDate newRoastDate) {
        return new NewInventoryLot(productId, quantity, unitCost, acquisitionDate, newRoastDate,
                location, serialNumbers);
    }

    /** True when this request describes serial-tracked stock. */
    public boolean isSerialized() {
        return !serialNumbers.isEmpty();
    }
}
