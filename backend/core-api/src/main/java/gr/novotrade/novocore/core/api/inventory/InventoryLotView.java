package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One inventory lot — brief §5's "every purchase creates a lot", and the {@code INVENTORY_LOT}
 * sub-ledger the Inventory control account reconciles against.
 *
 * <p><strong>Quantities on this view are always concrete; the columns behind them are not.</strong>
 * For pooled stock the lot stores its quantities. For a serial-tracked lot it stores none at all —
 * the quantity <em>is</em> the count of its units, so storing a second copy of it would be two
 * numbers that must agree and are therefore free to disagree, the same argument that keeps
 * {@code normal_balance_side} out of the chart of accounts and a cost off {@code Asset}. The view
 * fills the figure in by counting, because a caller asking how many are left should not have to know
 * which kind of lot it is asking.
 *
 * <p><strong>No source document.</strong> Brief §5 lists one, and there is nothing to point at: the
 * Purchase Invoice and the Goods Receipt arrive in step 8. That is a step 8 obligation rather than a
 * nullable column added early — ADR 0004 already settles that the Goods Receipt is what creates a
 * lot, so the reference gets added by the step that has something to put in it.
 *
 * @param serialTracked whether this lot's items are individually identified. Frozen at receipt: it
 *     records how the lot arrived, and a product's flag changing later does not rewrite history.
 * @param quantityReceived what arrived. Derived from {@link #units} when serial-tracked.
 * @param quantityRemaining what is left. Equal to {@link #quantityReceived} until step 8 starts
 *     consuming lots FIFO, and derived from unit statuses when serial-tracked.
 * @param unitCost the cost of one unit, six decimals. Brief §5: "includes allocated landed costs" —
 *     which is a statement about step 10, since nothing allocates yet. Until then this is exactly
 *     what was paid.
 * @param roastDate for coffee. Null for everything else, and the reason brief §9 can have a Roast
 *     Date Report at all.
 * @param location where pooled stock sits, and <strong>null when serial-tracked</strong> — each unit
 *     carries its own then. Use {@link #locationIfPooled()}.
 */
public record InventoryLotView(
        long id,
        long productId,
        String productSku,
        boolean serialTracked,
        Quantity quantityReceived,
        Quantity quantityRemaining,
        UnitCost unitCost,
        LocalDate acquisitionDate,
        LocalDate roastDate,
        StockLocation location,
        List<SerializedUnitView> units) {

    public InventoryLotView {
        Objects.requireNonNull(productSku, "productSku");
        Objects.requireNonNull(quantityReceived, "quantityReceived");
        Objects.requireNonNull(quantityRemaining, "quantityRemaining");
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(acquisitionDate, "acquisitionDate");
        Objects.requireNonNull(units, "units");
        units = List.copyOf(units);

        // The two shapes, stated once. A pooled lot has a location and no units; a serial-tracked lot
        // has units and no location of its own. Anything else is a lot nothing can count correctly,
        // so it fails here as well as at the CHECK constraint.
        if (serialTracked && location != null) {
            throw new IllegalArgumentException(
                    "Lot " + id + " is serial-tracked, so its location cannot be on the lot: each "
                            + "unit carries its own, because one machine going out for repair does "
                            + "not move the others.");
        }
        if (!serialTracked && location == null) {
            throw new IllegalArgumentException(
                    "Lot " + id + " is pooled stock and must have a location.");
        }
        if (!serialTracked && !units.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lot " + id + " is pooled stock and cannot have serialized units.");
        }
    }

    /** Empty when serial-tracked, because the units carry the location then. */
    public Optional<StockLocation> locationIfPooled() {
        return Optional.ofNullable(location);
    }

    public Optional<LocalDate> roastDateIfAny() {
        return Optional.ofNullable(roastDate);
    }

    /** What has left this lot — sold, consumed or written off. Zero until step 8. */
    public Quantity quantityConsumed() {
        return quantityReceived.minus(quantityRemaining);
    }

    /** True when this lot still has something in it. What FIFO consumption will filter on. */
    public boolean isOpen() {
        return quantityRemaining.isPositive();
    }

    /**
     * The remaining stock's cost, rounded once.
     *
     * <p>{@link RoundingMode#HALF_UP} because this is a valuation being read, not a posting being
     * made. When step 7 posts a lot's cost it states its own mode at the call site, where the
     * accounting consequence lives.
     */
    public Money remainingValue() {
        return unitCost.extend(quantityRemaining, RoundingMode.HALF_UP);
    }

    /** The units still on hand. Empty for pooled stock, which has no units to name. */
    public List<SerializedUnitView> unitsOnHand() {
        return units.stream().filter(SerializedUnitView::isOnHand).toList();
    }

    /**
     * Where this lot's stock is, as a single answer for either shape.
     *
     * <p>For pooled stock that is the lot's own location. For serial-tracked stock there may be more
     * than one, which is exactly why the caller should be using {@link StockLevels} instead — this is
     * here for the pooled case and for a lot whose units happen to agree.
     */
    public boolean isAt(StockLocation candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!serialTracked) {
            return candidate == location;
        }
        return unitsOnHand().stream().anyMatch(unit -> unit.location() == candidate);
    }
}
