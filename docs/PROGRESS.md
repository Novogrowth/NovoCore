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
| 3 | Chart of accounts | **Not started.** All design decisions resolved — see below |
| 4 | Users, auth, permissions | Not started. Blocked on Q21, Q22 |
| 5 | Product, Customer, Supplier, Asset | Not started. Blocked on Q4, Q5 |
| 6 | Inventory Lot/Unit, Location, computed stock | Not started |
| 7 | Journal engine, debits=credits invariant | Not started. Blocked on Q13, Q14 |
| 8 | Purchase Invoice, Goods Receipt, GR/IR, FIFO | Not started |
| 9 | Sales Invoice, Receipt, Payment, Bank Transfer, open items, rounding | Not started |
| 10 | Freight / landed cost allocation | Not started. Blocked on Q18 |
| 11 | Email service | Not started. Needs SMTP credentials |
| 12 | Automated backups | Not started. Needs Drive paths/credentials, Q24 |
| 13 | Test suite consolidation sweep | Not started |

**Tests: 99 passing, `mvn verify` exit 0.** 48 unit (core-api), 37 core integration, 3 app
integration, 11 architecture. Nothing was failing at the last close-out.

`mvn test` runs the 59 non-container tests in ~4 seconds and needs no Docker. `mvn verify`
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

All work is **pushed to `origin/main` at `e25fcee`** — local and remote agree, no divergence.
The three commits fast-forwarded onto `main` with no merge commit:

| Commit | What |
|---|---|
| `22bb361` | Step 1 — skeleton, guardrails, container stack |
| `cb93fc8` | Step 2 — primitives, migrations V1–V3, Settings, Audit, Attachments |
| `e25fcee` | Session close-out — this file, the primer, `CLAUDE.md` |

Local branch `phase-1/core-skeleton` still exists and is fully merged; safe to delete.
Convention going forward is **one commit per build step**, so history stays checkpoint-able.

---

## Verified working

- `mvn verify` green from a clean state, Java 25 enforced by maven-enforcer.
- Docker Compose stack (`docker compose -f compose.yml -f compose.dev.yml up --build`): all
  three containers healthy, PostgreSQL gating the app's start via its healthcheck, Flyway
  applying migrations.
- HTTPS through Caddy at `https://localhost` with HSTS and an HTTP→HTTPS 308 redirect.
- `/actuator*` blocked at the proxy (empty-body 404 from Caddy) while reachable internally —
  checked by comparing response bodies, not just status codes.
- ArchUnit rules proven to actually fail: a probe class with a `double` field tripped all three
  money rules before being deleted.

## Not yet verified

- **Backup restore.** Brief §13 already flags this. Nothing exists yet (step 12).
- **No REST controllers exist at all.** No HTTP surface beyond actuator; `..core.web..` is
  empty, so the frontend has nothing to call.
- **PostgreSQL 18.** Pinned to `postgres:17-alpine` in both `backend/pom.xml`
  (`postgres.docker.image`) and `docker/compose.yml`. Both must move together.

---

## Step 3 — fully specified, not written

Every open question on the chart of accounts is answered. The next session writes the
entities and the seed migration; no further input is needed.

### To build

- `AccountGroup` entity: name, `displayOrder`. A real entity rather than flat text, because
  manual drag-and-drop ordering needs somewhere to store a group's position. Two levels only —
  not a self-referencing Account tree.
- `Account` entity: name, type, kind, group reference, `displayOrder`, ΕΛΠ mapping (null for
  now), active flag, `expectedToClear`, and for Control-kind accounts a declared
  `SubLedgerType`. **Code is left blank** — the field exists, values do not.
- **Normal balance side is derived from type, never stored.** Six types:
  `ASSET`, `CONTRA_ASSET`, `LIABILITY`, `EQUITY`, `INCOME`, `EXPENSE`. `CONTRA_ASSET` exists
  because accumulated depreciation is an Asset-type account with a *credit* normal balance —
  without it the derivation is wrong for that account and fixed assets report at roughly double
  their carrying value.
- Kinds: `STANDARD`, `BANK_CASH`, `PARTNER_CLEARING`, `CONTROL`.
- `expectedToClear` flag (chosen over a fifth kind) for accounts whose residual balance is a
  real discrepancy, so Clearing Checks can find them in phase 8 without a hardcoded list.
- A **schema-convention test** asserting money columns are `numeric(19,2)` and
  quantity/unit-cost columns `numeric(19,6)`, now that the first such columns will exist.
  Migration V1 documents the convention; nothing enforces it yet.

### Seed content

Sourced from the Manager.io export, plus new accounts that map from nothing in Manager.
Sub-ledger column applies only to Control-kind accounts.

**Cash & Cash Equivalents** — Cash `BANK_CASH`; Alpha Bank `BANK_CASH`; Piraeus Bank
`BANK_CASH`; NBG `BANK_CASH`; PayPal `PARTNER_CLEARING`; Stripe `PARTNER_CLEARING`

**Current Assets** — Inventory `CONTROL`/Product-Lot; Accounts receivable `CONTROL`/Customer;
Freight / Landed Cost — Unallocated `STANDARD`, expected-to-clear; Unclassified — Needs Review
`STANDARD` (Asset), expected-to-clear; Partner Clearing — Skroutz `PARTNER_CLEARING`; Partner
Clearing — ACS Courier `PARTNER_CLEARING`; Partner Clearing — POS provider `PARTNER_CLEARING`

**Non-Current Assets** — Fixed assets at cost `CONTROL`/Asset; Fixed assets accumulated
depreciation `CONTRA_ASSET` + `CONTROL`/Asset; Security deposits `STANDARD`

**Current Liabilities** — Accounts payable `CONTROL`/Supplier; Goods Received / Invoice
Received clearing `CONTROL`/Supplier, expected-to-clear; VAT payable; Income tax payable;
Social security payable; Other taxes payable; Dividends payable; Employee clearing account
(all `STANDARD`)

**Non-Current Liabilities** — NBG loan `STANDARD`

**Equity** — Common stock; Retained earnings

**Income** — Sales — Store; Sales — eCommerce; Sales — Skroutz; Services; Subsidies; Interest
received; Other income

**COGS** — Cost of goods sold `STANDARD` *(its lines carry Inventory-Lot sub-ledger references
even though it is not a Control account — brief §6 requires one line per lot consumed)*; Cost
of service sold

**Selling Expenses** — Sales commissions; Advertising budget; Marketing software;
Transportation costs; Packaging materials; Bank commissions; Other selling expenses

**General Expenses** — Salaries; Social security; Rent; Electricity; Phone and internet; AI
stack; ERP; Internal consumption; Inventory write-off / shrinkage *(new)*; Other general
expenses

**Administrative Expenses** — Management fees; Accounting fees; Legal fees; Business trips; Misc

**Depreciation & Amortization** — Depreciation; Amortization *(kept although nothing can post
to it: the Asset entity has no intangibles concept)*

**Finance Costs** — Interest expense *(new; its own group so it sits below EBIT and leaves
EBITDA/EBIT meaningful)*

### Deliberately excluded

- **Inter Account Transfers** — dropped per brief §4. A transfer between own bank accounts is
  two Asset-account entries. Manager had it under Equity, which is the error the brief corrects.
  If it carries a balance in Manager, phase 2b migration needs a destination for it.
- **DDP** — superseded by Freight / Landed Cost — Unallocated (brief §4).
- **EBITDA, EBIT, Net profit (loss)** — computed subtotals, not ledger accounts.

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
  Phase 1 scope, or just a flag on the record?
- **Q16** Overpayment producing "unallocated customer credit" — a standalone credit document
  that later invoices allocate against, or just an AR balance?
- **Q17** Can stock go negative (sale posted before the receipt exists)? Block, warn, or allow?
- **Q18** Landed-cost allocation mutates a lot's unit cost after the fact. If any of that lot is
  already sold, posted COGS is now wrong. Block allocation after consumption, or post a COGS
  adjustment? The brief does not address it.
- **Q19** Confirm all six typed transactions are Phase 1 (Purchase Invoice, Sales Invoice,
  Receipt, Payment, Bank Transfer, Manual Journal Entry), with Sales Invoice as a *recording*
  transaction since Go still issues until phase 11.
- **ADR 0004 open item** — when a Goods Receipt precedes its invoice, the lot's unit cost is
  provisional. If the invoice then carries a different price, does that adjust the lot cost
  retroactively or post to a purchase price variance account? Interacts with Q18. Settle before
  step 8.

### Blocking step 12 — backups
- **Q24** Delivery mechanism: Google Drive API with credentials held by NovoCore, or `rclone` on
  the host? (No Python, per `CLAUDE.md`.) Plus retention policy and whether dumps are encrypted
  at rest. Also need the two actual Drive destinations.

---

## Next action

Build step 3: `AccountGroup` and `Account` entities, the six types and four kinds,
`expectedToClear`, `displayOrder`, migration `V4__chart_of_accounts.sql` with the seed above,
a `ChartOfAccountsService` on `core-api`, integration tests, and the schema-convention test.
Everything needed is specified above. Commit as its own step-3 commit — one commit per step.

Then step 4, which needs Q21 and Q22 answered first.
