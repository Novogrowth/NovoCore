# ADR 0010 — Landed cost allocated after a sale posts to a variance account; the received cost is the basis

**Date:** 2026-07-28
**Status:** Accepted — implemented in build step 10

Answers **Q18**, the only question that was blocking a numbered step. ADR 0008 already fixed the
shape of the answer; this fixes the mechanism.

## The question

Brief §4 puts freight and duty into `Freight / Landed Cost — Unallocated` on receipt and then
allocates them proportionally by value into the lots they delivered, so that a lot's unit cost is
what the goods really cost to have on a shelf. Allocation therefore raises a lot's unit cost after
the fact — and freight routinely arrives after some of the goods it carried have been sold. Those
units were costed out at the old figure into a cost-of-goods-sold entry that has already posted.

ADR 0008 settled that a posting reflecting a physically verified event is not reached back into once
other things depend on it. That ruled out the obvious answer without supplying one.

## Decision 1 — a lot's share is split by what is still in the lot

Each lot's share of the allocated cost is divided in the proportion the lot itself is divided:

| | |
|---|---|
| the part belonging to stock **still on hand** | raises that lot's unit cost, normally |
| the part belonging to stock **already gone** | posts to a new `Landed cost variance` account |

The second half is the whole of Q18. It cannot raise a unit cost, because the units it is about are
not in the lot any more; and it cannot be added to the cost of goods sold that took them out,
because that entry is immutable (ADR 0006) and because re-costing consumption already costed out is
exactly what ADR 0008 refuses. So it posts openly, to an account it can be reported from.

`Landed cost variance` is the exact counterpart of ADR 0008's `Purchase price variance`, one
position along in the same group, and it inherits that account's reasoning whole: in the COGS group
so gross margin reflects it, and **not** `expected_to_clear`, because a variance balance is a result
to look at rather than a discrepancy waiting to be cleared.

### What follows

- **A fully-sold lot may still be named in an allocation**, and all of its share goes to variance.
  That is the case Q18 exists for. Refusing it would be worse: the freight was genuinely incurred
  against those goods, and leaving it in an expected-to-clear account forever states nothing.
- **A balance on this account is a signal, not an error.** It says freight is routinely being
  allocated after the goods have sold, which is a purchasing-process question.
- **A share too small to move a unit's cost goes entirely to variance** rather than debiting
  Inventory for a cost no lot ends up carrying.

## Decision 2 — the basis is the received cost, so a lot now stores two figures

Proportional-by-value needs a value per lot, and it is everything the lot received extended at the
cost it was **received** at — not at what it is currently carried at.

The difference only shows up on the second allocation against the same lots, and then it is
decisive: if the basis were the carrying cost, the first freight invoice would have moved the
proportions the second one divides by, so two invoices covering one shipment would split differently
depending on the order they were entered in. Nobody would ever see that happen; they would simply
have costs that could not be reproduced.

So `inventory_lot.unit_cost` becomes `received_unit_cost` and **stops changing**, and a second column
accumulates what has been allocated onto it:

    received_unit_cost          what the goods cost. Frozen for the life of the lot.
    allocated_landed_unit_cost  freight and duty allocated onto one unit since. Starts at zero.
    (their sum)                 what the lot is CARRIED at — brief §5's "unit cost includes
                                allocated landed costs". Computed on read, never stored.

Two independent facts and a derived total, rather than a total and one of its parts. Storing the
carrying cost as well would be the third number that must agree with the other two — the argument
that keeps `normal_balance_side` off `account`, a quantity off a serial-tracked lot, and a balance
off every account in this schema.

Freezing the received cost also makes an allocation's own arithmetic reconstructible from frozen
inputs, which is why `freight_allocation_line` stores no basis.

`goods_receipt_line.unit_cost` stays what it was and is **not** the same fact: that column is what a
*delivery document* said and is one half of every GR/IR variance ADR 0008 computes, while
`received_unit_cost` is what a *lot* was opened at. They coincide for every lot a Goods Receipt
created, and they cannot for the phase 2b opening stock that no delivery created — which is exactly
the case a rule reading the delivery document instead would have no answer for.

### What follows

**The step 6 obligation is discharged.** `InventoryService.lastPurchaseCostOf` now returns the
received cost, so a product does not read as having gone up in price because its last delivery came
by air. One residual difference is stated rather than hidden: where a delivery preceded its invoice
and the invoice then charged something else, ADR 0008 keeps the lot at the cost it was received at,
so this is the last price we *believed* rather than necessarily the last price a supplier *invoiced*.
Making it the latter would mean the inventory slice reading the purchasing slice, which depends on
it.

## Decision 3 — an allocation names the cost it is spending

An allocation is made out of a **purchase invoice expense line pointed at
`Freight / Landed Cost — Unallocated`**, not out of a bare amount. Naming the source is what makes
"how much of this freight is still unallocated" a question with an answer, and what stops an
allocation crediting more out of that account than anything put into it. Same shape as a GR/IR match,
which names the delivery line it settles.

- **One source line per allocation.** A freight invoice with several chargeable lines is allocated
  once per line, so each line's remainder stays individually answerable.
- **Many lots across many purchase invoices**, which is the ordinary case for a consolidated
  shipment.
- **The lots are named, never guessed.** Inferring which stock a container held from suppliers and
  dates is the silent resolution `CLAUDE.md` rule 7 forbids, and it would be wrong for exactly the
  consolidated case this feature exists for.
- A lot received at **zero cost** is refused rather than silently allocated nothing:
  proportional-by-value gives it no share, so naming it would claim to have costed stock that got
  none.
- A lot from a **reversed Goods Receipt** is refused: that delivery was un-made, so nothing was
  carried to it.

## Decision 4 — immutable, corrected by reversal, and refused once the lots have moved

`JournalSource.FREIGHT_ALLOCATION` is not amendable and not reversible through the ledger alone.

The reason is the Goods Receipt's rather than the invoice's: the posting changes what lots are
carried at, and every FIFO consumption from that moment on costs at the new figure. Editing the entry
would change the accounts without changing the lots.

`FreightAllocationService.reverse` takes the per-unit cost back off each lot and posts the mirror in
one transaction, and **refuses once any of those lots has moved** — checked against
`quantity_remaining_at_allocation`, the one figure on a line that a later read cannot reconstruct.
Stock that left after the allocation was costed out at the raised figure, so the freight is already
inside a posted cost of goods sold; crediting Inventory back would be crediting stock that is not
there. Refused rather than partly undone, exactly as `GoodsReceiptService.reverse` is: the correction
is a new allocation onto the lots the cost really belonged to.

**Known limit, recorded rather than engineered around.** The check is on the quantity, not on
movement history, so a lot that sold two and had two returned reads as unmoved and would reverse. The
alternative is a per-lot movement log that exists only to answer this, and the case is narrow enough
that saying so beats building one.

## The arithmetic, and where each rounding is

- The split **across lots** is `ProportionalAllocation` — exact integer cents, largest remainder — so
  the shares sum to the amount allocated and the entry balances by construction.
- The split of one lot's share into **capitalised and variance** is the same class again, weighted by
  what is left against what has gone. Both halves are therefore non-negative and sum exactly, which
  matters: a residual computed as `share − capitalised` could come out a cent negative for a very
  large quantity, and the variance account only ever takes a debit.
- The only genuine rounding is the **per-unit increment**, six decimals with the mode from
  `ledger.rounding.mode`. A lot's carrying value therefore moves by within a cent of what was debited
  to Inventory for it — the same order of residual a partial consumption already leaves, and accepted
  for the same reason.

`ProportionalAllocation` moved to `core-api/shared` in this step. It was `BundleAllocation` in the
bundle slice, and freight allocation is the same arithmetic; a second copy would have been a second
set of rounding behaviour, which is the kind of difference that surfaces later as a report a cent out
with no way to say which half is wrong.

## Status of the question

| | |
|---|---|
| Q18 | Closed by decisions 1–4 |
| Step 6's last-purchase-price obligation | Closed by decision 2 |
| ADR 0008's constraint on Q18 | Honoured: nothing reaches back into posted COGS |
