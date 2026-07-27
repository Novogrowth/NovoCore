# NovoCore — Build Progress

*Live status. Overwritten each session close-out, not appended to. Last updated: 2026-07-27.*

Phase 1 (the core) is in progress. Build order and step numbering are as agreed at Phase 1
kickoff; they differ slightly from the brief's roadmap in that permissions were moved earlier
(step 4, before the ledger) and a Settings service was added (step 2).

---

## Where things stand

| Step | What | Status |
|---|---|---|
| 0 | Toolchain, ADRs | **Done** |
| 1 | Skeleton, guardrails, container stack, CI | **Done, committed** `22bb361` |
| 2 | Money/Quantity/SubLedgerRef, schema conventions, Settings, Audit, Attachments | **Done, committed** `cb93fc8` |
| 3 | Chart of accounts | **Done, committed** — see below |
| 3b | VAT classes, VAT exemption reasons, charge types | **Done, committed** — inserted step, see below |
| 4 | Users, auth, permissions | Not started. Blocked on Q21, Q22 |
| 5 | Product, Customer, Supplier, Asset | Not started. Blocked on Q5 (Q4 now resolved) |
| 6 | Inventory Lot/Unit, Location, computed stock | Not started. **Carries two step-3 obligations — see below** |
| 7 | Journal engine, debits=credits invariant | Not started. Blocked on Q13, Q14 |
| 8 | Purchase Invoice, Goods Receipt, GR/IR, FIFO | Not started |
| 9 | Sales Invoice, Receipt, Payment, Bank Transfer, open items, rounding | Not started |
| 10 | Freight / landed cost allocation | Not started. Blocked on Q18 |
| 11 | Email service | Not started. Needs SMTP credentials |
| 12 | Automated backups | Not started. Needs Drive paths/credentials, Q24 |
| 13 | Test suite consolidation sweep | Not started |

**Tests: 195 passing, `mvn clean verify` exit 0.** 68 unit (core-api), 113 core integration,
3 app integration, 11 architecture. Nothing was failing at the last close-out.

`mvn test` runs the 79 non-container tests in ~5 seconds and needs no Docker. `mvn verify`
additionally runs the `*IT` tests under Failsafe against a real PostgreSQL 17 container.

---

## ⚠️ To be aware of immediately

1. **`docker/.env` is gitignored and machine-local.** It holds a generated 48-character
   database password. A fresh clone must run `cp docker/.env.example docker/.env` and set
   `NOVOCORE_DB_PASSWORD`, or nothing starts. This is deliberate — there is no fallback
   password anywhere.
2. **A fresh machine also needs the toolchain**: JDK 25 and a Docker daemon. Maven is not
   required — `backend/mvnw` is committed. `mvn verify` needs Docker for the `*IT` tests;
   `mvn test` does not.

## Git state

| Commit | What |
|---|---|
| `22bb361` | Step 1 — skeleton, guardrails, container stack |
| `cb93fc8` | Step 2 — primitives, migrations V1–V3, Settings, Audit, Attachments |
| `e25fcee` | Session close-out — PROGRESS.md, the primer, `CLAUDE.md` |
| `a09428e` | Docs — recorded that the work was pushed |
| `920044c` | Docs — reordered the `CLAUDE.md` close-out rule (docs first, single commit last) |
| `f2ed289` | Step 3 — chart of accounts, migration V4 |
| `de16e58` | Docs — recorded the step-3 commit hash |
| `15627d2` | Step 3b — VAT classes, exemption reasons, charge types, migration V5 |

**`22bb361` … `e25fcee` are pushed to `origin/main`. Everything after that is local only** —
`a09428e`, `920044c`, `f2ed289`, `de16e58` and the step-3b commit have not been pushed. Push when
asked; close-out commits locally and does not push.

Local branch `phase-1/core-skeleton` still exists and is fully merged; safe to delete.
Convention going forward is **one commit per build step**, so history stays checkpoint-able.

---

## Verified working

- `mvn clean verify` green, Java 25 enforced by maven-enforcer.
- Docker Compose stack (`docker compose -f compose.yml -f compose.dev.yml up --build`): all
  three containers healthy, PostgreSQL gating the app's start via its healthcheck, Flyway
  applying migrations.
- HTTPS through Caddy at `https://localhost` with HSTS and an HTTP→HTTPS 308 redirect.
- `/actuator*` blocked at the proxy (empty-body 404 from Caddy) while reachable internally —
  checked by comparing response bodies, not just status codes.
- ArchUnit rules proven to actually fail: a probe class with a `double` field tripped all three
  money rules before being deleted.
- **`SchemaConventionsIT` proven to actually fail**, same method: a temporary migration adding
  `double precision`, `real`, `money`, `numeric(10,4)` and unbounded `numeric` columns tripped
  both rules and named all five, while correctly ignoring the `numeric(19,2)` and
  `numeric(19,6)` columns alongside them. Probe deleted.
- **The chart-of-accounts invariants are enforced by the database, not only by Java** — proven
  by raw-SQL probes in `ChartOfAccountsIT` that bypass the service and are rejected by CHECK
  constraints.

## Not yet verified

- **Backup restore.** Brief §13 already flags this. Nothing exists yet (step 12).
- **No REST controllers exist at all.** No HTTP surface beyond actuator; `..core.web..` is
  empty, so the frontend has nothing to call — including for the chart of accounts, which now
  has a full service but no way to reach it from the UI.
- **PostgreSQL 18.** Pinned to `postgres:17-alpine` in both `backend/pom.xml`
  (`postgres.docker.image`) and `docker/compose.yml`. Both must move together.

---

## Step 3 — done

`AccountGroup` and `Account` entities, seven types, four kinds, `expectedToClear`,
`displayOrder`, migration `V4__chart_of_accounts.sql` with the full seed (**65 accounts across
13 groups**), `ChartOfAccountsService` on `core-api`, 29 integration tests, and the
schema-convention test.

### What was built beyond the original step-3 spec

Four things that were not in the spec as written last session. All four were flagged and
approved (or are consequences of an approved decision) rather than added quietly.

1. **A seventh account type, `CONTRA_INCOME`.** Forced by the decision to add contra-revenue
   accounts. Sales returns are income-classified with a *debit* normal balance; since the
   normal balance side is derived from the type and never stored, there is no way to represent
   that with six types. Typing returns as `EXPENSE` would put them below the revenue line,
   overstating gross revenue and putting something that is not a cost into expenses. This is
   exactly the argument that produced `CONTRA_ASSET` for accumulated depreciation, one
   statement down.
2. **`AccountSystemKey`** — a stable machine identifier on the eleven accounts NovoCore's own
   posting rules must locate (Rounding, GR/IR clearing, Unclassified, Freight — Unallocated,
   Inventory write-off, the four control accounts, accumulated depreciation, COGS). Needed
   because account codes are deliberately blank and names are operator-editable, so a posting
   rule looking either up would break the first time someone renamed an account. A keyed
   account can be renamed and reordered freely; it cannot be deactivated, and its key is never
   settable from application code. Extending the list is deliberately a migration.
3. **`spring.flyway.encoding: UTF-8` stated explicitly** rather than relying on the default.
   The seed already contains em-dashes and Greek arrives in step 5; resolved from a platform
   default on Windows this would apply mojibake and then diverge Flyway's checksum between
   environments.
4. **Three contra-revenue accounts rather than one.** The approved decision named a single
   `Sales returns & allowances` account, but the stated reason for it was per-channel return
   rate — and channel exists nowhere in the model except in which Sales account gets credited,
   so one shared account would collapse exactly that visibility. Seeded one per channel,
   mirroring the Sales split. Collapsing to one later is a seed-only change.

### Decisions applied this session

- Channel split kept as Store / eCommerce / Skroutz, with **"Sales — Store & Phone"** named
  explicitly so the account states what it contains instead of relying on an undocumented
  convention about where phone orders go.
- **`Sales returns` added as contra-revenue** (three accounts, above). Credit notes debit these
  rather than netting into the channel Sales accounts.
- **Inventory write-off is one account with a reason code**, not three accounts — which of
  shrinkage / damage / expiry a write-off was belongs on the transaction, not in the chart.
- **Inventory write-off moved from General Expenses to the COGS group**, kept as its own
  account separate from `Cost of goods sold`, so gross margin reflects the loss honestly while
  sale-driven COGS stays uncontaminated.
- **`Rounding differences` added** — `STANDARD`, `EXPENSE`, in General Expenses, able to carry
  either balance. It was missing from the seed spec entirely, while V2 had already seeded
  `ledger.rounding.threshold` describing automatic posting to "the Rounding account" and brief
  §7 requires it. Step 9 would have had nowhere to post.
- **Damaged Goods → write-off stays posting-free** (see the obligation below).

### ⚠️ Obligations this step created for later steps

Both are recorded here deliberately so they are not rediscovered cold.

- **Step 6 — a reason field on the inventory write-off transaction.** Because there is one
  write-off account rather than three, the shrinkage / damage / expiry distinction has nowhere
  to live except on the transaction. This is **not optional and not out of scope**: without it,
  the single account is strictly less informative than three would have been, and the reason
  the single account was chosen disappears.
- **Phase 8 — Clearing Checks must surface lots aging in the Damaged Goods location.** Moving
  a lot to the Damaged Goods `Location` posts nothing: the stock is unsellable but still an
  asset at cost, and only the write-off derecognises it. Nothing forces that second step, so
  without a check the balance sheet carries worthless stock at full cost indefinitely. Keeping
  the move posting-free was the explicit decision (impairment-on-move contradicts the brief's
  plain Location model); this check is the agreed compensating control.

### Design notes worth keeping

- **Normal balance side is derived from the type and never stored.** There is no
  `normal_balance_side` column, and a test asserts its absence. Two columns that must agree are
  two columns that can disagree.
- **`type` and `kind` are independent dimensions.** Accumulated depreciation is `CONTRA_ASSET`
  *and* `CONTROL`, which is why they are not one enum.
- **No account balance is stored anywhere.** A balance is the sum of an account's journal lines,
  computed on read from step 7. Consequently **step 3 introduced no monetary columns at all** —
  the assumption last session that it would was wrong. The schema-convention test's scale rule
  is therefore dormant until step 7; its no-floating-point rule is live now.
- **`account_control_iff_sub_ledger` is a biconditional CHECK.** A Control account without a
  sub-ledger has nothing to reconcile against; a sub-ledger on a non-Control account is a rule
  never enforced on its lines. Both directions are refused.
- **A blank account code is refused** (`code IS NULL OR btrim(code) <> ''`), so "no code" has
  exactly one representation and two accounts cannot collide on the unique index by both
  carrying `''`.
- **Account names are unique within a group, not globally.**
- **There is no delete**, only `deactivate`. With no period locking there is no point at which
  an account is safely finished with.
- **A reorder must name every member exactly once** — a partial list is refused rather than
  leaving the remainder in an order nobody chose (`CLAUDE.md` rule 7).
- `Cost of goods sold` is `STANDARD`, not `CONTROL`, but its lines still carry Inventory-Lot
  sub-ledger references: Control-ness governs whether a reference is *required*, not whether one
  may be present.

### Accepted imperfections in the seed

- `Interest received` stays under `Income`, above EBITDA, so **EBITDA is approximate**. Left as
  Manager has it; reversible.
- **No current-portion split** on the NBG loan. Proper practice splits the next 12 months into
  Current Liabilities; not requested, and would need the repayment schedule.
- `VAT payable` is seeded as a single account. Almost certainly insufficient — see Q14.
- **PayPal/Stripe as Partner Clearing under Cash & Cash Equivalents** was an explicit decision
  after the alternative was flagged. Consequence: processor fees post as expense on receipt.
  The accountant may prefer processor balances presented as receivables rather than cash
  equivalents; that is presentation and reversible.
- **`Amortization` is seeded although nothing can post to it** — the Asset entity has no
  intangibles concept. Present so the statement layout is right if that changes.

### Deliberately excluded

- **Inter Account Transfers** — dropped per brief §4. A transfer between own bank accounts is
  two Asset-account entries. Manager had it under Equity, which is the error the brief corrects.
  If it carries a balance in Manager, phase 2b migration needs a destination for it.
- **DDP** — superseded by Freight / Landed Cost — Unallocated (brief §4).
- **Suspense** — replaced by Unclassified — Needs Review.
- **EBITDA, EBIT, Net profit (loss)** — computed subtotals, not ledger accounts.

---

## Step 3b — done

An inserted step, not in the original Phase 1 numbering. Real VAT data arrived from Prosvasis Go
and the AADE/myDATA documentation, which resolved Q4 and brought one new piece of scope
(charge types) that depends on VAT classes existing. Migration `V5`.

### VatClass — a real entity, not an enum

Runtime-editable lookup: `code`, `description`, `ratePercent`, `active`, and a nullable
self-referencing `reducedCounterpart`. **Seeded with the nine real Prosvasis Go classes** —
`0`, `1030`, `1040`, `1041`, `1060`, `1091`, `1131`, `1170`, `1410`.

- **Nine rows, eight distinct percentages.** 4% appears twice: `1040` as a rate in its own right
  and `1041` as the island-reduced counterpart of 6% under αρ.31 ν.5057/2023. Same percentage,
  different legal basis, different code.
- **The code is the identity, never the rate.** Because of the above, a lookup by rate is
  ambiguous by construction, so `VatClassService` deliberately has no `findByRate` — a test
  asserts its absence. A method that is right most of the time is worse than one that does not
  exist.
- **Island-reduced mappings seeded** mainland → reduced: 24→17, 13→9, 6→4 (`1041`, not `1040`),
  4→3. The 0% class has no counterpart. Enforced one level deep, lower-rated, one-to-one, and
  never self-referencing — in the service with named messages, and by `CHECK`/`UNIQUE`
  constraints in the database, proven by raw-SQL probes.
- **Recorded as data only.** Nothing chooses a rate by shipping destination; that is future
  scope, as instructed.
- **All eight reduced/mainland rates seeded, not just the mainland four**, because we do ship to
  islands under the reduced regime.
- **The rate is not editable in place** — there is no mutator and a test asserts none exists.
  Editing would retroactively change what every invoice already issued under that class appears
  to have charged. A rate change is a new class plus deactivation of the old one.
- **Rate stored as a percentage** (`24.000000`, not `0.24`) in `numeric(19,6)`, with a `CHECK`
  refusing anything outside 0–100 so a fraction fails loudly rather than undercharging by 100×.

### The VAT precedence rule, stated as code

`VatClassPrecedence` in `core-api` implements **invoice line beats customer beats product**,
returning both the winning class and a `VatClassSource` saying which level supplied it — so
"why is this line at 13%?" is answerable about a real invoice.

**There is deliberately no fallback rate.** If no level specifies a class, it throws
`VatClassNotDeterminableException` rather than assuming 24%. A silent default produces a
plausible invoice at a rate nobody chose, and an undercharge is not recoverable from the customer
after issue. Tested exhaustively over all eight present/absent combinations, because the rule is
three null checks whose *ordering* carries the entire meaning.

It takes ids rather than objects so it can be applied before Product, Customer and Sales Invoice
exist. **This creates a step 5 obligation:** Product needs a default VAT class, and Customer
needs a *nullable* VAT class override — which overlaps Q9 (Customer has no VAT status field
although Supplier does).

### VatExemptionReason — structure built, deliberately unseeded

`code` (integer), `description`, `mydataCode`, `inputVatDeductible`, `active`. **No seed data** —
the ~29 verified rows are still to come.

- **A separate entity from VatClass, not a 0% rate.** Zero-rated charges 0% under a rate that
  exists; exempt is outside VAT because a named article of the Κώδικας ΦΠΑ says so. Reported
  differently to myDATA.
- **`mydataCode` is stored verbatim**, not composed from `code + "-" + description` at use time.
  It is what goes on the wire, and reproducing AADE's exact punctuation by concatenation is a bet
  worth not taking. `VatExemptionReasonView.mydataCodeMatchesDescription()` exists so a test can
  check whether the composition actually holds **once the real rows land** — worth running then.
- **`code` is an integer, not text.** myDATA's own field is numeric, and text would sort "10"
  before "2" in a picker of ~29 entries. **If any real row's code is not a plain integer, say so
  and it becomes a `varchar` migration.**
- **`inputVatDeductible` is uniformly "Όχι" in everything seen so far.** Kept because it is a
  genuine per-reason distinction in AADE's table; a test proves the column can carry `true` so it
  is not a constant waiting to be optimised away.
- Neither code nor myDATA string is editable — a retired reason is deactivated, not corrected.
- Tests use codes in the 9000s so they cannot collide with AADE's real 1–31 range.

### ChargeType — new scope, structure built, unseeded

`name`, `defaultVatClassId`, `incomeAccountId`, `active`.

- **The income-side guard is the reason this service exists** rather than a bare repository: the
  account must be `INCOME`-type. `EXPENSE` is refused (wiring a delivery fee to
  `Transportation costs` to "net it off" understates revenue and cost together and leaves a gross
  margin that looks plausible and is wrong), and `CONTRA_INCOME` is refused too (that side is for
  sales returns).
- **Unseeded pending Q27** — see below. Seeding against the wrong income account would mean
  migrating posted history later.
- **Nothing consumes it yet.** Sales Invoice line items are step 9.

### Design note: the slice boundary holds inside the core

`ChargeType` holds plain `Long` ids for its VAT class and account, not JPA associations, because
`VatClass` and `Account` are package-private within their own slices. That is not a style choice
— it is the only option available, so ADR 0003's boundary holds *between slices of the core*, not
just between the core and its adapters, without needing another ArchUnit rule. The ids are
validated through `VatClassService` and `ChartOfAccountsService`, the same published interfaces
an adapter would use, and the FK constraints still exist in the database.

### Design note: a third meaning for `numeric(19,6)`

`vat_class.rate_percent` is the schema's **first `numeric` column**, and it is a *rate* — neither
of the two shapes V1 named. The convention now reads: `numeric(19,2)` for a posted **amount**
(two decimals because that is what a cent is), `numeric(19,6)` for a **multiplier** — quantity,
unit cost, or rate — which must not itself lose precision before the product is rounded once.
`SchemaConventionsIT` was updated to say so. Its scale rule is therefore now live; the
`numeric(19,2)` half still waits for step 7.

---

## Open questions, by the step they block

Numbering follows the original Phase 1 question list so references stay stable.
**Resolved:** Q1–Q3 (chart of accounts), Q20 (money scale), **Q4 (VAT classes — real rate list
supplied and seeded, built as a runtime-editable entity; precedence rule stated as code)**.

### ⚠️ Waiting on a decision before anything else can be built

- **Q27** *(new)* **Which income account each ChargeType posts to.** Recommendation given:
  **dedicated accounts** — `Delivery income` and `COD fee income` in the Income group — rather
  than the existing `Other income`. Reasons: these will appear on most invoices, so routing them
  to a residual bucket makes that bucket the largest income line and destroys its diagnostic
  value; and `Delivery income` needs to be comparable against the existing `Transportation costs`
  expense account to answer "is shipping costing us money?", which is impossible once it is
  merged into Other income. Not a blanket policy — `ChargeType.incomeAccountId` is per-type
  precisely so low-volume future fees can point at `Other income` instead. **Needs a V6 migration
  adding 2 accounts (65 → 67) plus the two ChargeType rows.** Related sub-question, recommendation
  is no: do *not* split delivery income by channel the way Sales is split — the channel split was
  a brief mandate for Sales specifically, and shipping revenue by channel is answerable from the
  invoice once invoices exist.
- **Q28** *(new)* **Where "Σκοπός διακίνησης" (dispatch purpose) belongs.** Analysis and
  recommendation below; **nothing built**. Correctly identified as unrelated to VAT — it is not
  folded into either VAT entity.
- **The VatExemptionReason seed** — ~29 verified rows still to be supplied. Structure is ready.

### Q28 in full — dispatch purpose

**It is an attribute of an outbound goods movement**, not of an invoice and not of a receipt.
That placement decides the rest:

- **Not Goods Receipt (step 8).** That is *inbound*. The supplier authors their own dispatch note
  and states their own purpose; we read theirs, we never state ours on a receipt.
- **Not inside the Sales Order Fulfillment module either**, even though that is where the ACS
  voucher generation and QZ Tray printing already live. Dispatches also happen for supplier
  returns, transfers between our own locations, goods sent out for repair (brief §9's
  Service/Technician Management), consignment and sampling. If the purpose lives inside the sales
  module, every non-sale dispatch has no home, and a module ends up owning a core concept —
  against `CLAUDE.md` rule 1.
- **Recommendation: a core-owned `GoodsDispatch`** — the outbound counterpart to Goods Receipt —
  carrying a `DispatchPurpose` core lookup entity, same shape as `VatExemptionReason` since it is
  likewise a codified AADE list. Sales Order Fulfillment then becomes one *consumer* that creates
  a dispatch with purpose = sale, alongside the other cases.
- **Which phase: roadmap phase 4**, with Purchase Orders + Sales Order Fulfillment — that is when
  goods first physically leave under NovoCore's control.

**Two things must be settled before that is final, and both could move it to phase 11:**

1. **Does Prosvasis Go currently issue the Δελτίο Αποστολής?** Go is the invoicing system of
   record until phase 11. If it already issues dispatch notes, NovoCore does not need to author
   them until Go is retired, and phase 4 only needs to print the *courier voucher* — which is not
   a legal dispatch document. That would make this phase 11 scope, not phase 4.
2. **Does the AADE Ψηφιακό Δελτίο Αποστολής (digital delivery note) regime apply to us?** If
   NovoCore must *transmit* delivery notes rather than print them, that is an AADE
   Provider/myDATA concern (phases 7 and 11) and the purpose codes must be correct before then.
   **This is an accountant question** — same bucket as the already-open AADE Πάροχος scope item.

### Blocking step 4 — auth and permissions
- **Q21** Field-level restriction needs a concrete list: which fields must Remote/Order Staff
  not see? Brief §7 names the sections but no fields.
- **Q22** Auth mechanism unspecified. Recommendation on the table: server-side sessions with an
  HttpOnly cookie rather than JWT, for a single self-hosted app. Password policy? 2FA?
- **Q23** Remote Staff's sections (Sales Order Fulfillment, Back-in-Stock) are phase 4 and 9
  modules. Plan is to register them as reserved section keys with nothing behind them.

### Blocking step 5 — core entities
- ~~**Q4** VAT class list~~ — **resolved and built.** See step 3b.
- **Step-3b obligation:** Product needs a default VAT class reference, and Customer needs a
  *nullable* VAT class override, so that `VatClassPrecedence` has real levels to read. Overlaps
  Q9 below.
- **Q5** *(hard blocker)* Product has "Supplier's SKU" but **no Supplier link** — meaningless
  without knowing which supplier. Add a reference (one? many?) or drop the field.
- **Q6** `last purchase price` is derivable from lots, like `Stock` which the brief says is
  never stored. Compute it too, for consistency?
- **Q7** Stock is not one number: Location lives on the lot and sellability depends on stock at
  a *sellable* location. Confirm the API exposes stock per location plus a "sellable" figure.
- **Q8** Customer fields omit email and phone, yet the identity model matches on exactly those.
  They need structuring (multiple per customer) for matching to work.
- **Q9** Customer has no VAT status field although Supplier does. Exempt/intra-EU customers.
  **Now partly answered by step 3b:** Customer gets a nullable VAT class override for the
  precedence rule, and an exempt customer needs a `VatExemptionReason` reference rather than a
  rate. Still open is whether "VAT status" is anything more than those two fields.
- **Q10** Confirm the shared generic "Πελάτης Λιανικής" retail record is seeded.
- **Q11** **Bundle/Composite products** are in brief §5's core entities but were absent from the
  agreed Phase 1 scope list. Build now or defer?
- **Q12** Asset has a depreciation *rate* but no useful life, salvage value, depreciation start
  date, disposal fields, or the three linked accounts. Also: is the periodic depreciation
  *posting run* in Phase 1, or only the entity and calculation?

### Blocking step 6 — inventory
- **Step-3 obligation:** the write-off reason field. See "Obligations" above.
- **Q25** *(new)* Does the write-off reason need to be a fixed enum (shrinkage / damage /
  expiry) or free text with a suggested list? An enum makes it reportable, which is the point;
  free text makes it useless for the purchasing-vs-theft distinction it exists to draw.

### Blocking steps 7–10 — the ledger
- **Q13** *(hard blocker, needs discussion)* **Correction policy unspecified.** With no period
  locking, can a posted entry be edited in place, or is correction reversal-only? Strong
  recommendation: immutable once posted, corrections via reversing entries, plus the audit log
  that now exists.
- **Q14** *(hard blocker, real design gap)* **VAT posting mechanics are undefined.** Nothing in
  the brief says how input and output VAT post. NovoCore has no filing duty but the ledger must
  still carry VAT correctly on every purchase and sales invoice. Needs the account structure and
  the per-line computation rule. This is a design conversation, not a one-line answer.
  **Step 3b narrowed it but did not close it:** the rates now exist, `VatClassView.vatOn` does the
  multiply-and-round-once arithmetic in one place, and the precedence rule picks the class. What
  is still undefined is *where it posts* — one `VAT payable` account or several, input vs output
  separation, and whether the computation is per line or per document. Also still open: how an
  exempt line posts, now that exemption reasons are modelled.
- **Q15** Rounding: is the independent recomputation compared against the document total only,
  or line by line? And "flagged for review" needs somewhere to live — is a review queue in
  Phase 1 scope, or just a flag on the record? *(The destination account now exists.)*
- **Q16** Overpayment producing "unallocated customer credit" — a standalone credit document
  that later invoices allocate against, or just an AR balance?
- **Q17** Can stock go negative (sale posted before the receipt exists)? Block, warn, or allow?
- **Q18** Landed-cost allocation mutates a lot's unit cost after the fact. If any of that lot is
  already sold, posted COGS is now wrong. Block allocation after consumption, or post a COGS
  adjustment? The brief does not address it.
- **Q19** Confirm all six typed transactions are Phase 1 (Purchase Invoice, Sales Invoice,
  Receipt, Payment, Bank Transfer, Manual Journal Entry), with Sales Invoice as a *recording*
  transaction since Go still issues until phase 11.
- **Q26** *(new)* Credit notes now debit a per-channel `Sales returns` account. Confirm a credit
  note is a distinct typed transaction rather than a negative Sales Invoice — it interacts with
  Q13's correction policy and Q16's unallocated credit.
- **ADR 0004 open item** — when a Goods Receipt precedes its invoice, the lot's unit cost is
  provisional. If the invoice then carries a different price, does that adjust the lot cost
  retroactively or post to a purchase price variance account? Interacts with Q18. Settle before
  step 8.

### Blocking phase 8 — Clearing Checks
- **Step-3 obligation:** surface lots aging in the Damaged Goods location. See "Obligations".

### Blocking step 12 — backups
- **Q24** Delivery mechanism: Google Drive API with credentials held by NovoCore, or `rclone` on
  the host? (No Python, per `CLAUDE.md`.) Plus retention policy and whether dumps are encrypted
  at rest. Also need the two actual Drive destinations.

---

## Next action

Three things are waiting on input rather than on work:

1. **Q27 — the ChargeType income account decision.** Smallest and most immediately unblocking:
   one answer produces a V6 migration seeding two accounts and two charge types.
2. **The VatExemptionReason seed** — ~29 verified rows. Structure is ready and waiting.
3. **Q28 — dispatch purpose placement.** Recommendation is phase 4 as a core-owned
   `GoodsDispatch`, conditional on the two questions above it (does Go already issue Δελτία
   Αποστολής; does the digital delivery note regime apply).

**Step 4** (users, auth, permissions) remains the next numbered step and is still **blocked on
Q21 and Q22**. Q22 is a decision, not a detail: the recommendation on the table is server-side
sessions with an HttpOnly cookie rather than JWT, for a single self-hosted app.

**Step 5** is now blocked only on Q5 (Product↔Supplier link), Q4 having been resolved — so it is
closer to startable than step 4 is.

Standing note regardless of which step comes next: **no REST surface exists at all yet**, so the
frontend has nothing to call. Worth deciding whether a thin read-only endpoint over the chart of
accounts and the VAT classes should come early, purely to prove the web layer's ArchUnit boundary
is real while there is one controller to get right rather than ten to retrofit.
