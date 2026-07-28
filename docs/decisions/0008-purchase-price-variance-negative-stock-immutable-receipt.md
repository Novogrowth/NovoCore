# ADR 0008 — Purchase price variance, negative stock is flagged not blocked, Goods Receipt is immutable

**Date:** 2026-07-28
**Status:** Accepted — implemented in build step 8

Three answers that were blocking step 8. They are recorded together because they share one
principle, stated once here and referred to by each: **a posting that reflects a physically
verified event does not change after other things have come to depend on it.**

## Decision 1 — a price difference posts to a purchase price variance account

ADR 0004 left this open: when a Goods Receipt precedes its invoice, the lot's unit cost is
provisional, and if the invoice then carries a different price, does that adjust the lot cost
retroactively or post to a variance account?

**The lot keeps the unit cost it was received at.** When the invoice lands at a different
price, the difference posts to its own `Purchase price variance` account, where it is visible
and reportable.

### Why

Retroactively changing a lot's cost after FIFO has already consumed some of it into posted COGS
is the same problem as editing a posted entry, expressed as a number instead of a document. The
consumed portion has already been costed out; changing the lot's unit cost either leaves that
posted COGS disagreeing with the lot it came from, or forces a compensating adjustment to an
entry that is immutable by ADR 0006. The variance account is that compensating amount, posted
openly and to a place it can be reported from, rather than smuggled into the lot.

It also keeps the lot honest about what it is: a record of what arrived, at the cost we believed
at the moment it arrived. That belief being wrong is a fact worth having a figure for — a
supplier who consistently invoices above the agreed price is a purchasing problem, and a variance
account is where that becomes visible. Folding it into the lot would make it invisible by
construction.

### What follows

- The variance is only ever created by the goods-first direction. A Goods Receipt matched to an
  invoice line that already exists takes its unit cost **from the invoice**, so there is nothing
  to vary.
- `Purchase price variance` is a new account, in the COGS group alongside `Cost of goods sold`
  and `Inventory write-off / shrinkage`, so gross margin reflects it. It is **not**
  `expected_to_clear`: a variance balance is a real result, not a discrepancy waiting to be
  cleared.
- It carries either balance. An invoice below the expected price is a credit variance, and
  forcing it positive would hide the good news alongside the bad.

### Consequence for Q18 (step 10, landed cost)

Recorded here because it is the same question wearing different clothes: **landed-cost
allocation must allocate against the lot's received cost and must not retroactively touch
consumption that has already been costed out.** Whatever step 10 does with the portion of a lot
that has already been sold, it is not "recompute the COGS that was posted". Q18 remains open as
to the exact mechanism; this constrains the shape of its answer.

## Decision 2 — Q17: a sale may drive aggregate stock negative, and is flagged for review

**Allowed, not blocked, and never silent.**

### Why not block

Brief §6 already frames goods arriving before their invoice as routine, because myDATA lag makes
it routine. Blocking a real sale from posting over paperwork timing would contradict the design
that ADR 0004 exists to support — and the sale genuinely happened; refusing to record it does not
make the stock reappear.

### Why not silently allow

The same reason the rounding residual has a threshold and Damaged Goods has an aging check: a
condition that is tolerated and invisible is a condition nobody fixes. This follows
`CLAUDE.md` rule 7's pattern — visible, not blocking, not ignored.

### What it means concretely

A consumption takes what the lots have, FIFO, and records the remainder as a **shortfall** on the
consumption itself. Three consequences, each deliberate:

- **Stock reads negative.** `stockOf` subtracts outstanding shortfalls, so a product that has
  sold two more than it ever received reports −2 rather than 0. A single *lot* still cannot go
  below zero (the step 6 CHECK stands); the negative lives on the consumption, which is where the
  fact actually is.
- **No COGS is posted for the shortfall.** There is no lot to take a cost from, and inventing one
  from the last purchase price would be exactly the silent guess rule 7 forbids. So COGS is
  understated for as long as the shortfall stands, and the flag is what says so.
- **A later Goods Receipt does not retro-cost it.** That would be decision 1 in reverse. The
  correction is to reverse the consumption and re-consume once the stock exists, which restores
  the lots and posts the mirror in one transaction.

The flag is a field on the record (`shortfall_quantity > 0`) plus the query
`InventoryService.consumptionsWithShortfall()`. It is deliberately **not** a review queue: Q15's
remainder — whether a flagged item lives in a queue or as a flag on the record — is still open,
and inventing a queue here would answer it by accident for every other flagged thing in the
system.

## Decision 3 — Q39: a Goods Receipt is immutable, corrected by reversal

`JournalSource.GOODS_RECEIPT` is **not amendable**, and **not reversible through the ledger
alone**.

### Why

Same reasoning as the inventory write-off, which ADR 0006 added to the immutable list for a
reason stronger than the invoice's: the posting reflects a physical stock movement, and editing
the entry would change what the accounts say arrived without changing the lots that arrived.
Once a lot exists, other things depend on it — FIFO consumption order, a lot's remaining
quantity, a serialized unit's identity — and a receipt that could be edited underneath them is a
lot whose cost and quantity are not facts.

It is not ledger-reversible alone for the same reason the write-off is not: reversing the money
without un-receiving the lots would leave stock on the shelf that the balance sheet no longer
carries. `GoodsReceiptService.reverse` does both, in one transaction, and **refuses if anything
has already happened to the lots** — consumed, written off, or a unit no longer in stock.
Refused rather than partially undone: the stock has moved on, so the correction is a new
document, not a claim that the delivery never happened.

## Status of the three questions

| | |
|---|---|
| ADR 0004's open item | Closed by decision 1 |
| Q17 | Closed by decision 2 |
| Q39 | Closed by decision 3 |
| Q18 | Still open, constrained by decision 1's closing note |
