package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
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
 * <p><strong>The source document is {@link #goodsReceiptLineId}</strong>, added in step 8 once there
 * was something to point at — brief §5 lists one, and ADR 0004 settles that the Goods Receipt is what
 * creates a lot. It is nullable, and the null case is real rather than defensive: the phase 2b
 * migration from Manager brings in stock that no NovoCore delivery ever created.
 *
 * <p><strong>The cost is two figures, and {@link #unitCost()} is their sum</strong> — step 10, ADR
 * 0010. Brief §5 says a lot's unit cost includes allocated landed costs, and that is what
 * {@link #unitCost()} answers: it is what FIFO costs at, what a write-off derecognises, and what the
 * Inventory control account carries. But the allocation itself has to be computed against what the
 * goods actually cost, or a second freight invoice would take its proportions from a figure the
 * first one moved — so {@link #receivedUnitCost} is stored and never changes, and
 * {@link #allocatedLandedUnitCost} accumulates beside it. Two independent facts rather than a total
 * and one of its parts, which is why neither is derived from the other.
 *
 * @param serialTracked whether this lot's items are individually identified. Frozen at receipt: it
 *     records how the lot arrived, and a product's flag changing later does not rewrite history.
 * @param quantityReceived what arrived. Derived from {@link #units} when serial-tracked.
 * @param quantityRemaining what is left. Equal to {@link #quantityReceived} until step 8 starts
 *     consuming lots FIFO, and derived from unit statuses when serial-tracked.
 * @param receivedUnitCost what one unit cost when the stock came in, six decimals. <strong>Frozen
 *     for the life of the lot.</strong> Where a supplier's invoice later disagreed with it, ADR 0008
 *     put the difference in {@code Purchase price variance} rather than in the lot, so this is what
 *     stock was taken in at rather than what the final invoice said.
 * @param allocatedLandedUnitCost freight and duty allocated onto one unit of this lot since (brief
 *     §4). Zero until something allocates.
 * @param roastDate for coffee. Null for everything else, and the reason brief §9 can have a Roast
 *     Date Report at all.
 * @param location where pooled stock sits, and <strong>null when serial-tracked</strong> — each unit
 *     carries its own then. Use {@link #locationIfPooled()}.
 */
public record InventoryLotView(
        long id,
        long productId,
        @Mandatory String productSku,
        boolean serialTracked,
        @Mandatory Quantity quantityReceived,
        @Mandatory Quantity quantityRemaining,
        @Mandatory UnitCost receivedUnitCost,
        @Mandatory UnitCost allocatedLandedUnitCost,
        @Mandatory LocalDate acquisitionDate,
        LocalDate roastDate,
        StockLocation location,
        @Mandatory List<SerializedUnitView> units,
        Long goodsReceiptLineId) {

    public InventoryLotView {
        Objects.requireNonNull(productSku, "productSku");
        Objects.requireNonNull(quantityReceived, "quantityReceived");
        Objects.requireNonNull(quantityRemaining, "quantityRemaining");
        Objects.requireNonNull(receivedUnitCost, "receivedUnitCost");
        Objects.requireNonNull(allocatedLandedUnitCost, "allocatedLandedUnitCost");
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

    /**
     * What one unit of this lot is <em>carried</em> at — the received cost plus everything allocated
     * onto it since. Brief §5's "includes allocated landed costs".
     *
     * <p>This is the figure that costs a sale, derecognises a write-off and values what is left. Use
     * {@link #receivedUnitCost} only where the question is genuinely about what was paid: the basis
     * for a further landed-cost allocation, and the last purchase price.
     */
    public UnitCost unitCost() {
        return receivedUnitCost.plus(allocatedLandedUnitCost);
    }

    /** Whether any landed cost has been allocated onto this lot. */
    public boolean hasAllocatedLandedCost() {
        return !allocatedLandedUnitCost.isZero();
    }

    /**
     * The value this lot's share of a landed cost is computed against — <strong>ADR 0010</strong>.
     *
     * <p>Everything that arrived, extended at what it cost to buy: the whole quantity, not what is
     * left, because the freight was paid to bring all of it in and the part that has since sold does
     * not stop having been carried. And at the <em>received</em> cost, so that a second freight
     * invoice against the same lots divides them in the same proportion as the first rather than in
     * a proportion the first one moved.
     */
    public java.math.BigDecimal landedCostBasis() {
        return receivedUnitCost.extendExactly(quantityReceived);
    }

    /**
     * The delivery line this lot came from. Empty for stock that no NovoCore Goods Receipt created —
     * the phase 2b migration's opening balances, and nothing else once that is done.
     */
    public Optional<Long> sourceReceiptLine() {
        return Optional.ofNullable(goodsReceiptLineId);
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
     * The remaining stock's cost, rounded once — and <strong>exactly what the Inventory control
     * account says this lot is worth</strong>, at every moment, by construction (ADR 0015).
     *
     * <p>It was not always both. Until step 13's fix this was a valuation read one way while
     * postings computed the movement another way, and the two drifted apart by a cent per movement
     * on any lot whose unit cost is not a whole number of cents — which is every lot a landed cost
     * has been allocated onto. {@link LotValuation} is now the single definition and every posting
     * that moves this lot's stock puts the change in <em>this figure</em> on the Inventory line, so
     * the two cannot diverge and a fully consumed lot leaves nothing behind.
     *
     * <p>Consequently the rounding is {@link LotValuation#ROUNDING} rather than a mode chosen at the
     * call site: see there for why a lot's own valuation deliberately does not follow
     * {@code ledger.rounding.mode}.
     */
    public Money remainingValue() {
        return LotValuation.carryingValue(unitCost(), quantityRemaining);
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
