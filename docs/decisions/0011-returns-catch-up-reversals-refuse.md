# ADR 0011 — Stock coming back into a re-costed lot: returns catch up, reversals refuse

**Date:** 2026-07-28
**Status:** Accepted — implemented in build step 10, alongside ADR 0010

Closes a defect ADR 0010 introduced. Recorded as its own decision rather than folded into ADR 0010
because it is a different question with a different answer, and because the asymmetry between the two
halves is the part worth remembering.

## How it was found

Step 10 was reviewed with a specific question: can reversing a freight allocation on a lot that had
intervening sale-then-return activity produce an actual wrong figure, or only an incomplete audit
trail? The question was answered by building the scenarios and measuring, rather than by reading the
code.

**The scenario asked about is sound**, and the reason is worth stating because it is what makes the
rest of the design hold together. A sale stores the cost it took stock out at on its
`stock_consumption_line`; a return reads that same stored figure back (ADR 0009, so that a credit
note cannot revalue stock). So a sale and its full return net to exactly zero in both Inventory and
cost of goods sold, and by the time an allocation reversal runs there is no freight inside any posted
COGS to be inconsistent with. The reversal guard passes because the situation is genuinely safe.

Reversal is also **value-neutral** on the relationship between the Inventory control account and what
the lots carry: its guard forces `remaining` to equal `remainingAtAllocation`, and the mirror credit
equals `capitalised`, which was computed from that same `remaining`. It cannot create a divergence
and cannot hide one.

**The probe found a different case that is genuinely broken, and it needs no reversal at all.**

## The defect

Before ADR 0010 a lot's unit cost never changed, so "the cost stock left at" and "the cost the lot
carries now" were the same number. ADR 0010 made the second one move. Three operations put stock back
into a lot — `returnConsumed`, `reverseConsumption`, `reverseWriteOff` — and all three restore it at
the cost it left at. If a freight allocation landed while the stock was out, the lot now carries those
units higher than the figure debited back, and the Inventory control account and the lot's valuation
disagree permanently.

Measured, on a lot of 10 at €10 where 2 are sold, €100 of freight is then allocated (€80 capitalised
on the 8 remaining, €20 to `Landed cost variance` for the 2 that had gone), and the 2 come back:

| | lot valuation | Inventory ledger |
|---|---|---|
| after the allocation | €160.00 | €160.00 |
| after the 2 come back | €200.00 | **€180.00** |

The €20 is exactly the variance posted on the grounds that those units had gone. They came back. The
same €20 gap appeared on all three paths. It never clears: when the lot exhausts, COGS takes €200 out
of a position holding €180.

## Decision — the two cases are not the same fact

### A return catches the freight up

A return says **the sale was real and the goods came back**. The allocation's split was correct at the
time: those units genuinely had gone. What is owed is the freight the returning units did not bear,
and it is owed to the account that took it:

    returned quantity × (the lot's allocated landed cost NOW
                         − the allocated landed cost embedded in what the stock left at)

posted as a debit to `Inventory` and a credit to `Landed cost variance`, in the same entry as the
return. Zero whenever the lot's cost has not moved, which is the ordinary case.

Recovering the second term is exact and needs nothing stored: what the stock left at is on the
consumption line, the lot's received cost never changes (ADR 0010), so subtracting one from the other
gives precisely what had been allocated at that moment.

**Cost of goods sold is still credited exactly what was debited**, so ADR 0009's rule is untouched and
the sale is not restated. And `Landed cost variance` goes back to meaning what it claims: freight
attributable to stock that has gone *for good*.

Why not refuse, as the reversals do? The remedy would work — unwind the allocation, process the
return, re-allocate — but a return is a routine, operator-facing event driven by a credit note, and
the person processing one at the till is not the person who allocates freight. Refusing would be
refusing ordinary business over a document somebody else has not filed yet, and the predictable
outcome is a return that never gets recorded.

### A reversal refuses

A reversal says **the movement never happened**. If that is true then those units were in the lot all
along, so a freight allocation that has since split its share on the basis that they had gone did not
merely post its counterpart to the wrong account — **it computed the wrong split**. Patching the
difference would leave a posted allocation stating something untrue about which stock was where, which
is ADR 0008's objection exactly.

So `reverseConsumption` and `reverseWriteOff` refuse once the lot has been re-costed since, and the
refusal names the remedy — which is exact rather than a dead end:

1. reverse the freight allocation (permitted: the lot's *remaining quantity* has not moved since it
   posted, which is what its own guard tests);
2. reverse the consumption or write-off;
3. allocate the freight again, which now capitalises the whole share, correctly.

There is a test that walks that sequence and checks the end state is exactly right, because a refusal
whose named remedy has never been tried is a refusal that might not have one.

## Consequence — a write-off now records its unit cost (V19)

The consumption path could already answer "what did this stock leave at": `stock_consumption_line`
stores its `unit_cost`, and step 8 said in as many words that it does so because step 10 would move a
lot's cost. The write-off could not — V15 deliberately stored no amount, on the argument that a
write-off's amount is purely a consequence of our own posting and the entry is the honest source.

That argument was written when a lot's cost could not move. Now the entry gives the *amount* and no
longer gives the *cost*: recovering it means dividing a rounded two-decimal amount by a quantity,
which is not the six-decimal figure that went in. So `stock_write_off` stores its unit cost, for
exactly the reason `stock_consumption_line` does.

**The posted amount is still not stored.** One is a historical input, the other a historical output,
and only the input became unrecoverable.

## What this does not fix

The reversal guard on a freight allocation still tests the lot's *quantity*, not its movement history,
so a lot that sold two and had two returned reads as unmoved and will reverse. That is now known to be
**safe rather than merely tolerated** — the first scenario above is precisely that case, and it is
consistent end to end. The limitation was worth stating when it was unexamined; having examined it,
there is nothing left to fix.

## Status

| | |
|---|---|
| Sale-then-return around an allocation reversal | Sound; proven, not assumed |
| Return into a re-costed lot | Fixed by the catch-up posting |
| Consumption / write-off reversal into a re-costed lot | Refused, with a tested remedy |
| A write-off's historical unit cost | Stored (V19) |
