# ADR 0015 — A lot's movements post the change in its carrying value

**Date:** 2026-07-29
**Status:** Accepted
**Answers:** Q45. **Revises:** the posting arithmetic of ADR 0008, ADR 0010 and ADR 0011, none of
their decisions.

## Decision

A lot's **carrying value** is its remaining quantity extended at its unit cost, rounded exactly
once. It has one definition, `LotValuation.carryingValue`, and one rounding mode.

**Every posting that moves a lot's stock puts the change in that figure on the Inventory line** — a
receipt, a sale, a write-off, a return, a landed-cost allocation. Not the quantity moved extended at
the cost and rounded, which is a different number.

Consequently `InventoryLotView.remainingValue()` and the Inventory control account's position for
that lot are equal at every moment, by construction, and a fully consumed lot leaves exactly nothing
behind.

## Context — what was actually wrong

Found by the FIFO property tests in step 13, reproduced with a throwaway probe, and measured rather
than reasoned about:

    22 units @ 12.505000, sold one at a time
      Goods Receipt debited Inventory        275.11     (the delivery, rounded once)
      each sale credited                      12.51     (12.505 rounded, HALF_UP)
      22 sales credited                      275.22
      >>> lot empty, Inventory residue        -0.11

    3 units @ 10.666667 — the shape every landed-cost-allocated lot has
      >>> lot empty, Inventory residue        -0.01

Two roundings at different granularities that do not add up. The residue is real journal lines on a
real account, with no document behind it, nothing to reconcile it against and no report that would
explain it. Because `HALF_UP` rounds away from zero it is systematically negative, so it accumulates
across lots rather than cancelling out — the Inventory line on the balance sheet and COGS both drift.

**It was reachable by design, not by accident.** `UnitCost` carries six decimals precisely so ADR
0010's landed-cost allocation can divide a freight invoice across lots without losing precision:
€2.00 over three units is 0.666667. Every re-costed lot was a candidate.

## Why this shape of answer

This is not a new principle here. It is the one `ProportionalAllocation` already applies to bundles
and to freight, and states in its own class comment:

> this never needs a rounding mode and never produces a rounding difference. `Rounding differences`
> is for reconciling against an *external* document, not for absorbing our own arithmetic.

Two alternatives were rejected:

- **Post the residue to `Rounding differences` when a lot empties.** That account is for reconciling
  against somebody else's paperwork. Using it to absorb our own arithmetic is exactly what the
  sentence above forbids, and it would make the account's balance uninterpretable.
- **Accept it with a tolerance in the tests.** How a wrong number becomes permanent.

## The rounding mode is fixed, and deliberately not `ledger.rounding.mode`

`LotValuation.ROUNDING` is `HALF_UP`, a constant.

The whole point is that a lot's value at two different moments must be measured the same way, and a
setting an operator can change cannot promise that: a change part-way through a lot's life would
leave exactly the kind of unexplainable cent this ADR removes. `ledger.rounding.mode` continues to
govern **document** rounding — VAT, stated totals, what brief §6 asks it for — which is a question
about somebody else's paperwork rather than about our own asset.

`HALF_UP` is both the seeded default and what `remainingValue()` has always used, so **nothing about
today's numbers changes**; what changes is that nothing can change them tomorrow.

Consequence worth noting: `InventoryServiceImpl` no longer reads `ledger.rounding.mode` at all, and
its `SettingsService` dependency is gone.

## Consequences, path by path

- **Consumption and write-off.** The amount is `carryingValue(before) − carryingValue(after)`. Both
  post before the stock moves, so the lot still reports the "before" — which was already the
  documented order and is now load-bearing.
- **Return.** Inventory takes back `carryingValue(after) − carryingValue(before)` at the cost the lot
  carries **now**. ADR 0011's landed-cost catch-up is unchanged and is subtracted from it; **Cost of
  goods sold takes the remainder** and therefore absorbs the sub-cent residue. A return's COGS credit
  can now differ from the original debit by a cent when the lot moved in between. That is the right
  place for it: invisible in COGS, and the whole defect in Inventory. `returnConsumed` was also
  restructured to restore the stock *after* posting, matching consumption and write-off — a pooled
  lot used to be restored first, which would have made the posting see the "after".
- **Freight allocation.** The capitalised half is no longer a proportional estimate of what the stock
  on hand should absorb; it is exactly how much the allocation raises the lot's carrying value, and
  the variance half is the remainder. ADR 0010 is untouched — the split is still "what the stock on
  hand carries" against "what belongs to stock already gone" — but the first half is now stated
  exactly. **The remainder can be one cent negative**, because a six-decimal per-unit cost cannot
  always express a total; migration **V24** relaxes the CHECK that forbade it and explains why, and
  `Landed cost variance` is credited in that case, which needs nothing new since ADR 0011's catch-up
  already credits it.
- **Cost is now path-independent.** Twenty-two single sales and one sale of twenty-two cost the same
  €275.11. They did not before, and both figures claimed to be the cost of the same lot.
- **Two identical units out of one lot can post different costs** — 12.50 then 12.51. They must, if the
  lot is to end at zero. Stated outright in `LotCarryingValueIT` rather than left to be discovered.
- **`StockConsumptionLineView.cost()` is read off the journal entry** rather than recomputed. The
  posted amount depends on what the lot held at the time and is not recoverable from the line's own
  quantity and cost once the lot has moved on. No new column: the entry already holds it, and reading
  it back cannot drift from what was posted.

## The one place the rules pull apart, and the guard for it

A reversal must post the **exact mirror** of the entry it reverses (Q13, ADR 0006, enforced by
`JournalService.post`). The mirror is the amount that came off Inventory when the original posted,
and that is the right amount to put back only if the lot is where it was. If something else consumed
it since, the lot's carrying value has moved to a different point on the rounding staircase and the
mirror is a cent out.

So `reverseConsumption` and `reverseWriteOff` compare the two directly and **refuse if and only if
they differ**, naming the remedy — the shape ADR 0011 already established for the re-costed case.
This is deliberately not a blanket restriction on reversing sales:

- it **cannot** fire for a lot whose unit cost is a whole number of cents, which is most of them;
- it does not fire when reversing the most recent movement on a lot, whatever the cost;
- it fires only when reversing *behind* a later movement on a sub-cent-cost lot.

Posting the mirror anyway and accepting the cent was rejected: it is the same unexplainable residue
in the same account, merely rarer.

## Verification

- `LotCarryingValueIT` holds the worked examples, including the reported reproducer. **Proven to
  actually fail**: the old formula was reinstated and five of its eight tests went red, one of them
  at €275.22 against €275.11 — the reported drift, to the cent.
- `FifoPropertiesIT`'s ledger-agreement and self-liquidation properties were **restricted to
  whole-cent costs** while Q45 stood, because below the cent they were false. That restriction is
  removed: they now run over every cost shape, including 0.333333, 10.666667 and 12.505, across
  twenty generated histories each.
- `WholeScenarioIT`'s whole-database sweep is unchanged and still green, which is what says the fix
  did not move anything it should not have.
