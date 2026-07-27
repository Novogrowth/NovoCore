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
| 4 | Users, auth, permissions | Not started. Blocked on Q21, Q22 |
| 5 | Product, Customer, Supplier, Asset | Not started. Blocked on Q4, Q5 |
| 6 | Inventory Lot/Unit, Location, computed stock | Not started. **Carries two step-3 obligations — see below** |
| 7 | Journal engine, debits=credits invariant | Not started. Blocked on Q13, Q14 |
| 8 | Purchase Invoice, Goods Receipt, GR/IR, FIFO | Not started |
| 9 | Sales Invoice, Receipt, Payment, Bank Transfer, open items, rounding | Not started |
| 10 | Freight / landed cost allocation | Not started. Blocked on Q18 |
| 11 | Email service | Not started. Needs SMTP credentials |
| 12 | Automated backups | Not started. Needs Drive paths/credentials, Q24 |
| 13 | Test suite consolidation sweep | Not started |

**Tests: 131 passing, `mvn verify` exit 0 from clean.** 48 unit (core-api), 69 core integration,
3 app integration, 11 architecture. Nothing was failing at the last close-out.

`mvn test` runs the 59 non-container tests in ~4 seconds and needs no Docker — unchanged by
step 3, whose tests are all container-backed. `mvn verify` additionally runs the `*IT` tests
under Failsafe against a real PostgreSQL 17 container.

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
| *(step 3)* | Step 3 — chart of accounts, migration V4, and this file |

**`22bb361` … `e25fcee` are pushed to `origin/main`. Everything after that is local only** —
`a09428e`, `920044c` and the step-3 commit have not been pushed. Push when asked; close-out
commits locally and does not push.

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

## Open questions, by the step they block

Numbering follows the original Phase 1 question list so references stay stable.
**Resolved:** Q1–Q3 (chart of accounts), Q20 (money scale: `numeric(19,2)` postings,
`numeric(19,6)` unit costs and quantities).

### Blocking step 4 — auth and permissions
- **Q21** Field-level restriction needs a concrete list: which fields must Remote/Order Staff
  not see? Brief §7 names the sections but no fields.
- **Q22** Auth mechanism unspecified. Recommendation on the table: server-side sessions with an
  HttpOnly cookie rather than JWT, for a single self-hosted app. Password policy? 2FA?
- **Q23** Remote Staff's sections (Sales Order Fulfillment, Back-in-Stock) are phase 4 and 9
  modules. Plan is to register them as reserved section keys with nothing behind them.

### Blocking step 5 — core entities
- **Q4** *(hard blocker)* **VAT class list absent.** Product has a "VAT Class" and Supplier a
  "VAT status", but no rates exist anywhere in the brief. Need the real classes (24/13/6/0 and
  exempt, plus reduced island rates if applicable) and whether it is an entity or an enum.
- **Q5** *(hard blocker)* Product has "Supplier's SKU" but **no Supplier link** — meaningless
  without knowing which supplier. Add a reference (one? many?) or drop the field.
- **Q6** `last purchase price` is derivable from lots, like `Stock` which the brief says is
  never stored. Compute it too, for consistency?
- **Q7** Stock is not one number: Location lives on the lot and sellability depends on stock at
  a *sellable* location. Confirm the API exposes stock per location plus a "sellable" figure.
- **Q8** Customer fields omit email and phone, yet the identity model matches on exactly those.
  They need structuring (multiple per customer) for matching to work.
- **Q9** Customer has no VAT status field although Supplier does. Exempt/intra-EU customers.
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

Step 4: users, auth, permissions. **Blocked — needs Q21 and Q22 answered first.** Q22 in
particular is a decision, not a detail: the recommendation on the table is server-side sessions
with an HttpOnly cookie rather than JWT, for a single self-hosted app.

If step 4 stays blocked, the unblocked alternative is **step 6's prerequisites**, or bringing
forward part of step 13 — but note that no REST surface exists at all yet, so the frontend
still has nothing to call regardless of which backend step comes next. Worth deciding whether a
thin read-only chart-of-accounts endpoint should come early, purely to prove the web layer's
ArchUnit boundary is real before there are ten controllers to retrofit.
