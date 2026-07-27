# ADR 0004 — Goods Receipt is the inventory event; a GR/IR clearing account absorbs timing

**Date:** 2026-07-27
**Status:** Accepted — implementation deferred to build step 8

## Decision

**Goods Receipt creates Inventory Lots**, not Purchase Invoice posting. The two documents
are independent events, and a **Goods Receipt / Invoice Receipt (GR/IR) clearing account**
absorbs the gap between them in whichever order they arrive.

- Goods arrive first → Goods Receipt debits Inventory, credits GR/IR clearing.
- Invoice arrives first → Purchase Invoice debits GR/IR clearing, credits Accounts Payable.
- Once both sides have landed for a line, GR/IR nets to zero for that line.

A non-zero GR/IR balance is therefore meaningful: it is either goods received but not yet
invoiced, or invoiced but not yet received. Like the Freight/Landed Cost — Unallocated
account in brief §4, a residual balance is a real discrepancy to investigate, not
something to leave sitting.

## Context

Brief §5 says "every purchase creates a lot", and §6 makes Goods Receipt "a physical
delivery-verification step, distinct from simply posting the invoice". Read literally
together, these suggest the Purchase Invoice creates lots and Goods Receipt is
verification-only with no ledger effect.

That reading was rejected because brief §6 also states that **myDATA timing may lag
physical delivery**, and §6's AADE-first import makes myDATA the default source of
purchase invoices. So goods routinely arrive *before* their invoice exists in NovoCore.
An invoice-creates-lots design cannot represent stock that is physically present but not
yet invoiced — it would show zero stock for goods sitting on the shelf, which then blocks
selling them.

The mirror problem also matters: if the invoice posted inventory on arrival of the
*document*, stock would be overstated for goods still in transit — and brief §6 explicitly
handles partial delivery across multiple days, during which the overstatement would be
live and would corrupt FIFO costing order.

Two alternatives were considered and rejected:

1. **Purchase Invoice creates lots directly in Inventory; Goods Receipt is
   verification-only.** The most literal reading of the brief. Rejected: overstates stock
   between invoice posting and delivery, and cannot represent goods-before-invoice at all.
2. **Purchase Invoice creates lots at an "In-Transit" location; Goods Receipt moves them
   to Inventory.** Uses the Location field the brief already defines, and keeps sellable
   stock correct without a clearing account. Rejected for the same reason as (1): it still
   requires the invoice to exist first, which the myDATA lag makes unreliable.

Option (1)'s appeal is simplicity, and it is worth noting what the chosen design costs:
GR/IR is a real clearing account that needs monitoring, and Goods Receipt becomes a
ledger-posting document rather than a checklist.

## Consequences

- Goods Receipt posts journal entries. It is a typed transaction, not just a verification
  record.
- Lot unit cost at Goods Receipt time may be provisional (taken from the purchase order or
  an expected cost) when the invoice has not arrived. When the invoice lands with a
  different price, the difference resolves against GR/IR. **Open:** whether a price
  difference adjusts the lot's unit cost retroactively or posts to a purchase price
  variance account — this interacts with the same problem as landed-cost allocation after
  consumption (question 18, still open) and must be settled before step 8.
- The GR/IR account must be seeded in the chart of accounts (step 3), and is not currently
  named in brief §4 — brief §4's account list needs it added.
- Brief §5's "every purchase creates a lot" should be reworded to "every goods receipt
  creates a lot" when the brief is next revised.
- "Open receiving amount" per invoice line (brief §6) now has real ledger meaning: it is
  the quantity whose GR/IR entry has not yet been matched by a receipt.
