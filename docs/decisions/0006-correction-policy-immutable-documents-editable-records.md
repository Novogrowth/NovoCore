# ADR 0006 — Correction policy: documents are immutable, our own records are editable

**Date:** 2026-07-28
**Status:** Accepted — implemented in build step 7 (migration V15)

Answers **Q13**, and its follow-on **Q26**.

## Decision

A posted journal entry can be corrected in exactly one of two ways, and **which one depends on the
transaction type that produced it**, not on how long ago it was posted or on who is asking.

**Immutable once posted — correction is a reversing entry:**

- Purchase Invoice
- Sales Invoice
- Credit Note *(Q26: its own transaction type, not a negative Sales Invoice; it references the
  invoice it corrects, posts against the per-channel `Sales returns` contra-revenue account, and
  takes the same policy as the invoice it corrects)*
- Inventory write-off *(not named in Q13; settled the same way below)*

**Editable in place, with the previous state written to the audit log:**

- Receipt
- Payment
- Bank Transfer
- Manual Journal Entry

**Nothing is ever deleted**, from either table, whatever the source.

## Context

Brief §6 states "no formal period locking". Without a period lock there is no date after which the
ledger is closed, so "may a posted entry be edited?" cannot be answered with "not once the period is
closed" — the usual answer. It has to be answered per transaction type.

The line that was drawn is **whether the record exists outside NovoCore**.

An invoice has been issued to somebody else and, from roadmap phase 7, transmitted to AADE. Its
content is a matter of external record. Editing it here would make NovoCore quietly disagree with
what the counterparty and the tax authority hold, and nothing in the system would show that a
different figure had ever been stated. A reversal says three things an edit cannot: what was
recorded, that it was withdrawn, and what replaced it.

A Receipt is our own note that money moved. A mistyped amount on one is a correction, not a
re-issue, and forcing a reversal pair for every fat-fingered receipt would fill the ledger with
noise that obscures the reversals that mean something.

**The inventory write-off was added to the immutable list** although Q13 did not name it. Its reason
is stronger than the invoice's: a write-off reduced a lot's quantity, so editing the entry would
change the loss recognised without changing the stock it came out of. Its correction is
`InventoryService.reverseWriteOff`, which restores the quantity and posts the mirror entry in one
transaction.

### Alternatives considered

1. **Immutable for everything.** The original recommendation in `HISTORY.md`, and the safest.
   Rejected because it makes the common case — a mistyped receipt — as expensive as the rare one,
   and a ledger where most reversals are typos is one where nobody reads reversals.
2. **Editable for everything, relying on the audit log.** Rejected: the audit log is NovoCore's own
   record, and the problem with editing an invoice is that the *other party's* copy no longer
   matches. An internal trail does not fix an external disagreement.
3. **Editable within N days.** Rejected as an arbitrary threshold that would need a decision nobody
   has data for, and that changes an entry's mutability without anything happening to the entry.

## Consequences

- **`JournalSource` carries the policy**, as `isAmendable()`. The source is stored on the entry
  rather than inferred from its lines, because the policy has to be answerable before anyone tries
  to change it, and the database refuses to let the source change — otherwise an immutable entry
  could be relabelled into an editable one and then edited.

- **The policy is enforced in the database, not only in the service.** "Immutable" that holds only
  for callers who came through the service layer is not immutability. `V15` states the rule once as
  the SQL function `journal_source_is_amendable(varchar)`, used by triggers on both tables, and a
  test calls that function for every value of the Java enum and compares — so the two statements of
  the policy cannot drift apart.

- **An amendment replaces the whole line list, never merges**, and writes the previous date,
  description and lines to the audit log *before* overwriting them. Q13 names the audit log as the
  mechanism that makes editing in place acceptable; without the before-state recorded, an edit is
  indistinguishable from the entry having always said the new thing.

- **A reversal is the exact mirror, and this is verified.** Same accounts, same amounts, same
  sub-ledger and VAT references, opposite sides. A reversal is posted through the ordinary `post`
  path with `reversalOfEntryId` set, and the service checks the lines really are the mirror — which
  is what lets a service that owns state outside the ledger (the write-off today; Receipts and
  Invoices in steps 8–9) reverse its own document without the ledger needing a second, unguarded
  write path.

- **An entry can be reversed at most once** (UNIQUE constraint), and neither half of a reversal pair
  can be amended. Reversing a *reversal* is permitted and needs no special rule — "I reversed the
  wrong entry" is a real mistake.

- **`JournalService.reverse` refuses a source that owns state outside the ledger**, naming the
  service to use instead. Reversing a receipt's money without releasing its allocations would leave
  invoices reported as settled by a receipt that no longer exists.

- **The reversing entry inherits the original's source**, so it inherits the same policy. A reversal
  of a sales invoice is still sales-invoice-sourced and is itself immutable.

- **Step 9 obligation, from the second half of Q13's answer:** editing a Receipt or Payment below
  its already-allocated total must reduce allocations **starting with the most recently applied one,
  working backward**. Nothing enforces that yet because allocations do not exist until step 9.

- **Step 8 obligation:** `GOODS_RECEIPT` is deliberately absent from `JournalSource`. ADR 0004
  settles that a Goods Receipt posts, so it will need a value — but whether it is amendable has not
  been asked, and adding a value is deliberately a migration so that the question gets asked rather
  than defaulted.
