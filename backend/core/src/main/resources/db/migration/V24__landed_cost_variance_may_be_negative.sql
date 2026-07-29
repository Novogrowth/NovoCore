-- V24 — a freight allocation line's variance half may be negative (ADR 0015)
--
-- WHAT CHANGES: exactly one CHECK constraint on `freight_allocation_line`. No column is added,
-- removed or retyped, and no row is rewritten.
--
-- WHY.
--
-- V18 split a lot's share of a freight invoice into two POSTED halves and refused a negative on
-- either, with a reason that was correct for how they were then computed:
--
--     "the allocated amount is positive and the split of a positive amount has no negative part"
--
-- Both halves were proportional shares of a positive amount, so neither could be negative. ADR 0015
-- changes what the capitalised half IS. It is no longer a proportional estimate of what the stock
-- still on hand ought to absorb; it is *exactly* how much this allocation raises that lot's carrying
-- value — the figure that will actually land on the Inventory control account when the per-unit cost
-- is applied. That is what makes the Inventory account and the lot agree by construction, which is
-- the whole of ADR 0015 and the fix for Q45.
--
-- The variance half is then the remainder, `share - capitalised`, so that the two still sum to the
-- share and the credit to `Freight / Landed Cost — Unallocated` still clears exactly. And the
-- remainder can be one cent negative.
--
-- HOW, precisely, because "rounding" is not an explanation.
--
-- A lot's share is divided by everything it received to give a six-decimal per-unit cost. That
-- division is itself rounded, so the per-unit cost extended back across the lot is the share plus a
-- fraction far below a cent. Almost always that fraction disappears in the single rounding of the
-- carrying value. It survives when the lot's existing value happens to sit within that fraction of a
-- half-cent boundary, and then the carrying value rises by one cent more than the share — leaving
-- the remainder at -0.01.
--
-- Reachable, rare, and now representable rather than a constraint violation on a Tuesday in 2027.
--
-- WHERE IT POSTS. `Landed cost variance` is credited rather than debited in that case, which needs
-- nothing new: the account already takes credits, because ADR 0011's catch-up credits it whenever
-- returned stock re-enters a re-costed lot. What the balance means is unchanged — freight that is
-- not carried on a unit cost — and a cent of it arriving from the other direction is the honest
-- record of a per-unit cost that could not express the total exactly.
--
-- The capitalised half stays non-negative and its CHECK is untouched: an allocation raises a lot's
-- carrying value or leaves it alone, never lowers it. Un-allocating is a reversal, which posts its
-- own mirrored entry.

ALTER TABLE freight_allocation_line
    DROP CONSTRAINT freight_allocation_line_variance_not_negative;

COMMENT ON COLUMN freight_allocation_line.variance_amount IS
    'The part of this lot''s share that did not go onto the lot: freight belonging to stock already '
    'sold (ADR 0010), plus or minus the cent that expressing a total as a six-decimal per-unit cost '
    'cannot represent (ADR 0015). Debited to Landed cost variance when positive, credited when '
    'negative. Its sum with capitalised_amount is the lot''s whole share, always.';
