# ADR 0009 — Documents post and allocations do not; rounding is confirmed at entry, not queued; the retail customer is structural

**Date:** 2026-07-28
**Status:** Accepted — implemented in build step 9

Three decisions taken together in step 9. The first is the one the other two lean on, and it is
the whole reason the sales side came out as small as it did: **open item matching is a layer over
Accounts receivable and Accounts payable, not a second ledger beside them.**

---

## Decision 1 — documents post; allocations post nothing

Brief §6 requires every invoice to carry a computed open amount, and a Receipt or Payment to
carry one or more allocations against documents. The question nobody had asked out loud was what
an allocation *posts*.

**Nothing.** A sales invoice posts (debit Accounts receivable, credit revenue, credit output
VAT). A receipt posts (debit the bank, credit Accounts receivable). Saying which one paid the
other would be an entry debiting and crediting the same control account for the same amount.

### Why it matters more than it looks

Everything unusual about `SettlementService` follows from this one line:

* **No document stores an open amount and nothing stores a "paid" flag.** An open amount is the
  document's gross less what has been allocated against it, computed on every read — the same
  stance that keeps a balance off `account`, a stock figure off `product`, and a shortfall off
  `stock_consumption`. Two numbers that must agree are two numbers that can disagree.
* **An allocation can be reduced or released freely**, because nothing was posted for it. That is
  what makes **Q13's second half implementable at all**: editing a Receipt below its
  already-allocated total releases allocations most-recent-first, and none of that touches a
  posted entry. Had an allocation posted, every release would have needed a reversal, and a
  correction to a receipt would have produced a cascade of entries describing bookkeeping rather
  than money.
* **`release` deletes a row**, which is the one place in this schema that happens. It is right
  here for the reason the rest of the schema refuses it: an allocation is not a record of an
  event, it is a statement about the current relationship between two documents. The audit log
  records the release.

### A consequence: Receipt and Payment share one table

They are structurally identical — money moving between one of our accounts and one counterparty's
sub-ledger, with allocations against that counterparty's documents. `SettlementDirection` decides
the side of the entry and which `JournalSource` it carries, so **Q13's per-source correction
policy is untouched and the ledger cannot tell**. `PartyType` is a separate dimension because all
four combinations are real: a receipt from a customer, a payment to a supplier, a refund *to* a
customer against a credit note, and a refund *from* a supplier. Folding the two into one enum
would have made the last two unrepresentable, which is how a refund ends up recorded as a
negative receipt.

**The trigger for splitting them** is the first column that belongs to one and not the other.
There is none today.

---

## Decision 2 (Q15's remainder) — a flagged item is confirmed at entry and recorded on the record; there is no review queue

Q15 settled that rounding is compared **per document**. What it left open — deliberately, twice,
since step 8 used a flag plus a query for Q17's shortfall rather than answering this by accident —
is *where a flagged-for-review item lives*.

**Answered: it is refused at the point of entry and requires an explicit confirmation, which is
stored on the record. There is no queue.**

Concretely, for a sales invoice or credit note whose lines disagree with the total the source
document states:

| difference | what happens |
|---|---|
| zero | nothing |
| at or below `ledger.rounding.threshold` | posts to `Rounding differences` automatically |
| above the threshold | **the document is refused**, naming both totals, until the caller supplies who accepts the difference and why |

### Why not a queue

1. **A queue is a second copy of state.** It has to be created when the condition arises and
   removed when it is resolved, and the day those two fall out of step it shows work that is
   already done or hides work that is not. That is the argument this schema has already made
   against `normal_balance_side`, against stored balances, and against a `superseded` flag beside
   `reversal_of_id`.
2. **The person who can explain the difference is the one holding the document**, not whoever
   opens a queue next week. Confirming at entry is `CLAUDE.md` rule 7 applied where it works best:
   auto-resolve what is certain (a residual cent), require one-click confirmation for everything
   else.
3. **A bare flag loses the resolution.** So the confirmation is stored — `rounding_accepted_by`,
   `_at`, `_note` — and "somebody looked at this and decided it was fine" becomes a fact in the
   data rather than the absence of a queue row.

`rounding_needed_review` is **stored rather than derived** from the threshold on read, because the
threshold is operator-changeable and a later change must not retroactively alter which invoices
somebody had to agree to. Same argument as freezing an invoice line's net.

### The rule this generalises to

> Where the ambiguity is visible **at entry**, confirm at entry and record the confirmation. Where
> the condition is a consequence the operator cannot fix at entry — Q17's stock shortfall, which
> is what it is whether or not anyone agrees to it — flag it on the record and provide a query.
> **Neither is a queue.** Phase 8's Clearing Checks reads queries, and there is no table for it to
> read a stale row out of.

### And the difference always posts

Because Prosvasis Go issues the invoice until roadmap phase 11, the source document's gross is
what the customer actually owes. So the difference posts to `Rounding differences` in every case,
and the threshold decides only whether a human had to agree first. Posting our own computed total
and merely flagging the difference would leave Accounts receivable disagreeing with the document
the customer holds — the one outcome open-item matching cannot survive.

---

## Decision 3 (Q10) — the generic retail customer is seeded, and it is structural rather than a record somebody made

Step 5 deliberately seeded no generic retail customer, on the grounds that a catch-all absorbs
every unmatched sale and then cannot be untangled.

**Answered: seed it, with the treatment the chart of accounts gives its system accounts.** The
alternative is not "no catch-all" — it is a person creating one by hand on the first day of
trading, which produces exactly the row step 5 feared, only with nothing in the software able to
tell which row it is.

So `customer.system_key` exists for one value, `RETAIL_WALK_IN`, and it does three things:

* **It makes the row findable by machine**, which is what stops a second one being created. The
  column is `insertable = false, updatable = false` on the entity, so no service path can set one
  — the same stance `AccountSystemKey` takes.
* **It makes the row protected.** It cannot be deactivated (a CHECK, not merely a service
  refusal, so it holds against a `psql` session), and when merge is built it must refuse this row
  **on both sides**. Brief §5's "alias forward, never rewrite history" is about two records of one
  real party; this is the *absence* of a party. Aliasing it into somebody would attribute every
  anonymous till sale ever made to one named person, and aliasing somebody into it would erase a
  real customer's history into an anonymous bucket. The rule is stated on `CustomerSystemKey` so
  whoever builds merge consults it rather than rediscovering the argument.
* **It fixes the VAT treatment.** `DOMESTIC`, no VAT number, no exemption reason, all three by
  CHECK. It cannot sensibly carry a VAT number because it is not one identifiable party, and it
  cannot be `INTRA_EU_B2B` or `EXEMPT` because both are claims about a specific counterparty.

**What it is not: a default.** Nothing falls back to it and no sale is assigned to it
automatically. A till operator choosing "retail, no details" is stating a real answer to who
bought it, which is exactly what makes it different from the silent catch-all step 5 refused.

---

## Two smaller decisions recorded here, because they follow from the same principle as ADR 0008

### A stock return is not a reversal

A credit note that brings goods back does **not** reverse the consumption that sold them. Reversal
says the consumption should never have happened: whole quantity, at most once, exact ledger
mirror. A return says the sale was real and the goods came back: it may be partial, it may happen
more than once against one sale, and it posts an ordinary entry (debit Inventory, credit COGS, one
line per lot). Both are rows in `stock_consumption`, distinguished by which of `reversal_of_id`
and `returns_consumption_id` is set — a CHECK refuses both, and a trigger holds the total returned
within what was taken.

Stock comes back **at the cost it left at**, read off the consumption's own stored lines and never
off the lot as it stands now. Step 10 will move a lot's unit cost when freight is allocated, and
returning goods at a later cost would revalue stock through a credit note.

### A credit note that restored stock is not reversible

ADR 0008's principle, applied to the other direction: a posting that reflects a physically
verified event is not un-made once other things depend on it. Goods that came back are on a shelf,
in a lot FIFO may already have sold from again. A credit note whose *money* was wrong is corrected
by a fresh document; goods that went back out are a sale. A price-only credit note reverses
normally, because nothing physical moved.
