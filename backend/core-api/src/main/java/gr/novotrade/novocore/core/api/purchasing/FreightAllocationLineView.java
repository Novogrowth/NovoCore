package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One lot's share of an allocated landed cost, split into the half that changed a carrying value and
 * the half that could not — <strong>Q18's whole answer, per lot</strong> (ADR 0010).
 *
 * @param lotId the lot this share went to
 * @param quantityReceived what the lot took in
 * @param quantityRemainingAtAllocation what was still in the lot when this posted. Stored, because
 *     it is the one figure here that a later read cannot reconstruct — and it is what a reversal is
 *     checked against: if the lot has moved since, the freight is already inside a posted cost and
 *     the allocation is no longer un-makeable.
 * @param capitalised the share belonging to stock still on hand. Debited to {@code Inventory}
 *     against the lot, and the reason its unit cost rose.
 * @param variance the share belonging to stock that had already left. Debited to
 *     {@code Landed cost variance}, because the posted cost of goods sold it should have been part
 *     of is not reachable — ADR 0008's principle, arriving from the other direction.
 * @param unitCostIncrease what this added to one unit of the lot: the whole share divided by
 *     everything the lot received, rounded once at six decimals. Stored rather than recomputed
 *     because {@code ledger.rounding.mode} is operator-changeable, and because a reversal has to
 *     take back exactly what was applied.
 */
public record FreightAllocationLineView(
        long id,
        int lineNumber,
        long lotId,
        long productId,
        String productSku,
        Quantity quantityReceived,
        Quantity quantityRemainingAtAllocation,
        UnitCost receivedUnitCost,
        Money capitalised,
        Money variance,
        UnitCost unitCostIncrease) {

    public FreightAllocationLineView {
        Objects.requireNonNull(productSku, "productSku");
        Objects.requireNonNull(quantityReceived, "quantityReceived");
        Objects.requireNonNull(quantityRemainingAtAllocation, "quantityRemainingAtAllocation");
        Objects.requireNonNull(receivedUnitCost, "receivedUnitCost");
        Objects.requireNonNull(capitalised, "capitalised");
        Objects.requireNonNull(variance, "variance");
        Objects.requireNonNull(unitCostIncrease, "unitCostIncrease");
    }

    /**
     * What this lot's share was computed in proportion to: everything the lot received, extended at
     * the cost it was <em>received</em> at, exactly and unrounded.
     *
     * <p><strong>A derived accessor since step 15b, and it used to be a record component.</strong>
     * Step 15's narrative was the first thing ever to drive a freight allocation over HTTP, and the
     * JSON sweep refused the response: an exact product of two six-decimal figures is up to twelve
     * decimals, and it was crossing the wire as the JSON number {@code 540.0} — a double in any
     * browser, which is {@code CLAUDE.md} rule 5 broken at the boundary.
     *
     * <p>Making it a method rather than a component takes it off the wire while keeping it for every
     * Java caller, because Jackson serialises a record's components and not its other accessors —
     * the same reason {@code InventoryLotView.landedCostBasis()} never appeared in a response. That
     * is the right answer here specifically because <strong>this figure is derived and nothing
     * needs it transmitted</strong>: both of its inputs are on this very line, and both are frozen
     * for the life of the lot, which is one of the reasons they are frozen.
     *
     * <p>The alternatives were worse. A targeted Jackson annotation cannot go on this record —
     * {@code core-api} is deliberately free of framework types (ADR 0003) — and a global
     * {@code BigDecimal}-as-string serialiser was considered and rejected when {@link
     * gr.novotrade.novocore.core.api.shared.Rate} was introduced, in favour of naming what a value
     * actually is. Nothing in production read this field; one test did, and it now recomputes it,
     * which is itself the proof that it was never carrying information.
     */
    public BigDecimal basis() {
        return receivedUnitCost.extendExactly(quantityReceived);
    }

    /** This lot's whole share of the freight. Never stored: it is the sum of the two halves. */
    public Money share() {
        return capitalised.plus(variance);
    }

    /** What had already left the lot when this posted — the quantity the variance half is about. */
    public Quantity quantityGoneAtAllocation() {
        return quantityReceived.minus(quantityRemainingAtAllocation);
    }

    /** True when none of this lot's share could be capitalised: all of it had already sold. */
    public boolean wentEntirelyToVariance() {
        return capitalised.isZero() && !variance.isZero();
    }
}
