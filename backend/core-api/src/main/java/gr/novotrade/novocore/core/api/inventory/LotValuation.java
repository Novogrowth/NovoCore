package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * What a lot is carried at, and what the ledger must move when its stock does — <strong>the one
 * definition, ADR 0015</strong>.
 *
 * <p><strong>The rule.</strong> A lot's carrying value is its remaining quantity extended at its
 * unit cost, rounded exactly once. Every posting that moves that stock — a receipt, a sale, a
 * write-off, a return, a landed-cost allocation — puts <em>the change in this figure</em> on the
 * Inventory line. Not the quantity moved extended at the cost and rounded, which is a different
 * number.
 *
 * <p><strong>Why it has to be this way, in one worked example.</strong> A lot of 22 units at
 * €12.505000 is received: the receipt debits Inventory with the delivery rounded once, €275.11.
 * Selling one unit at a time and rounding each sale on its own credits €12.51 twenty-two times,
 * which is €275.22. The lot ends empty and Inventory ends at <strong>−€0.11</strong>, with no
 * document behind it, nothing to reconcile it against and no report that would explain it. Because
 * {@code HALF_UP} rounds away from zero the drift is systematically negative, so it accumulates
 * across lots rather than cancelling. Posting the change in carrying value instead credits
 * €275.11 − €262.61 = €12.50 for the first unit and €12.51 for the next, and the lot self-liquidates
 * to exactly zero.
 *
 * <p>This is not a new principle in this codebase. It is the one
 * {@link gr.novotrade.novocore.core.api.shared.ProportionalAllocation} already applies to freight
 * and to bundles: <em>the parts are the same amount as the whole, exactly, and our own arithmetic
 * never produces a rounding difference for something else to absorb.</em>
 *
 * <p><strong>The rounding mode is fixed here and is deliberately not
 * {@code ledger.rounding.mode}.</strong> The whole point is that a lot's value at two different
 * moments must be measured the same way, and a setting an operator can change cannot promise that:
 * a change part-way through a lot's life would leave exactly the kind of unexplainable cent this
 * class exists to remove. {@code ledger.rounding.mode} continues to govern document rounding — VAT,
 * stated totals, what brief §6 asks it for — which is a question about somebody else's paperwork
 * rather than about our own asset. {@code HALF_UP} matches both the seeded default and what
 * {@code InventoryLotView.remainingValue()} has always used, so nothing changes today; what changes
 * is that nothing can change it tomorrow.
 *
 * <p><strong>One residual, stated rather than hidden.</strong> A reversal must post the exact mirror
 * of the entry it reverses (Q13, ADR 0006, enforced by {@code JournalService.post}). If the lot has
 * moved since, that mirror is no longer the change in carrying value, and posting it would break the
 * invariant by a cent. Rather than accept that, {@code InventoryService} refuses the reversal in
 * exactly — and only — the case where the two differ, and names the remedy. See
 * {@code reverseConsumption}.
 */
public final class LotValuation {

    /**
     * The one rounding mode for a lot's carrying value.
     *
     * <p>Public because the invariant is a claim tests and reports need to be able to restate, and a
     * second copy of this constant elsewhere would be the first step back to two answers.
     */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private LotValuation() {
    }

    /**
     * What a lot holding {@code quantity} at {@code unitCost} is carried at.
     *
     * <p>Zero quantity is zero value, which is the property that makes a fully consumed lot leave
     * nothing behind in the Inventory control account.
     */
    public static Money carryingValue(UnitCost unitCost, Quantity quantity) {
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(quantity, "quantity");
        return unitCost.extend(quantity, ROUNDING);
    }

    /**
     * What leaves the lot when its stock falls from {@code before} to {@code after} — the amount a
     * sale, a write-off or the consumed half of anything else must take off Inventory.
     *
     * <p>Never negative in the cases that use it, but not guarded against it: the arithmetic is the
     * same either way and a caller that has its direction wrong is better served by an entry that
     * refuses to balance than by an exception here about a sign.
     */
    public static Money valueReleased(UnitCost unitCost, Quantity before, Quantity after) {
        return carryingValue(unitCost, before).minus(carryingValue(unitCost, after));
    }

    /**
     * What the lot absorbs when its stock rises from {@code before} to {@code after} — a return, or
     * a delivery being received.
     */
    public static Money valueAbsorbed(UnitCost unitCost, Quantity before, Quantity after) {
        return carryingValue(unitCost, after).minus(carryingValue(unitCost, before));
    }

    /**
     * What the lot absorbs when its <em>cost</em> rises and its quantity does not — a landed-cost
     * allocation raising the unit cost of the stock still on hand (ADR 0010).
     *
     * <p>The counterpart of the other two: the same single definition of a carrying value, differenced
     * across the other axis. This is what makes the capitalised half of a freight allocation exactly
     * the amount by which the lot's value went up, rather than a proportional estimate of it that can
     * miss by a cent.
     */
    public static Money valueRecosted(UnitCost before, UnitCost after, Quantity quantity) {
        return carryingValue(after, quantity).minus(carryingValue(before, quantity));
    }
}
