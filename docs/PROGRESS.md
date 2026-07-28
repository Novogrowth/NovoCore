# NovoCore — Build Progress

*Live status. Overwritten each session close-out, not appended to. Last updated: 2026-07-28.*

*Close-out now also pushes to `origin` automatically (`CLAUDE.md`), so this file no longer tracks
unpushed commits.*

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
| 4 | Users, auth, permissions | **Done, committed** — Q21 and Q22 answered, see below |
| 4b | First REST endpoint (chart of accounts, read-only) | **Done, committed** — boundary validation, see below |
| 5 | Product, Customer, Supplier, Asset | **Done, committed** — Q5, Q8, Q9, Q12 answered, see below |
| 6 | Inventory Lot/Unit, Location, stock queries, bundles | **Done, committed** — Q7, Q25, Q11 answered, see below |
| 7 | Journal engine, debits=credits invariant | **Done, committed** `8e7e10e` — Q13, Q14, Q19, Q26, Q15, Q16 answered, see below |
| 8 | Purchase Invoice, Goods Receipt, GR/IR, purchase price variance, FIFO | **Done, committed** `c6e2513` — ADR 0004's open item, Q17 and Q39 answered as **ADR 0008**, see below |
| 9 | Sales Invoice, Credit Note, Receipt, Payment, Bank Transfer, open items, rounding | **Next.** Not started. Not blocked on anything; several obligations are waiting for it |
| 10 | Freight / landed cost allocation | Not started. Blocked on Q18, whose shape ADR 0008 now constrains |
| 11 | Email service | Not started. Needs SMTP credentials |
| 12 | Automated backups | Not started. Needs Drive paths/credentials, Q24 |
| 13 | Test suite consolidation sweep | Not started |

**Tests: 610 passing, `mvn clean verify` exit 0.** 178 unit (core-api), 405 core integration,
4 app unit, 12 app integration, 11 architecture. Nothing was failing at the start of this session,
and nothing is failing now.

`mvn test` runs the 193 non-container tests in ~6 seconds and needs no Docker. `mvn verify`
additionally runs the `*IT` tests under Failsafe against a real PostgreSQL 17 container.

---

## 🚫 Pre-launch blockers

Not open questions — **decided, and deliberately unresolved for now, with a condition attached.**
These must be closed before the stated trigger, not merely before Phase 1 ends.

### PLB-1 — No 2FA. Blocks any external or remote access. (Q30)

**Decision: no 2FA for now**, because the application is not internet-facing. That is a deliberate
choice with an explicit condition, not a deferral by default.

**This must be revisited and resolved before *any* external or remote access is enabled**,
including:

- exposing NovoCore to the public internet (Caddy already obtains a publicly trusted certificate
  automatically once `NOVOCORE_SITE_ADDRESS` is a real hostname, so this is one environment
  variable away from being live);
- **Remote/Order Staff logging in from outside the local network** — which is the whole point of
  that role, so this trigger is likely to arrive sooner than a general public launch;
- any VPN-less remote access for an owner or admin.

Why it matters here specifically: a full-access role can reach every financial record in the
system, and the only thing standing in front of it is one password. Session cookies are hardened
(`HttpOnly`, `Secure`, `SameSite=Strict`), which addresses cookie theft but does nothing about a
stolen or reused password.

Scope when resolved: TOTP is the obvious candidate, and the decision needs to cover whether it is
mandatory for full-access roles only or for everyone, plus recovery codes — a second factor with
no recovery path locks the owner out of their own financial system.

## ⚠️ To be aware of immediately

1. **`docker/.env` is gitignored and machine-local.** It holds a generated 48-character
   database password. A fresh clone must run `cp docker/.env.example docker/.env` and set
   `NOVOCORE_DB_PASSWORD`, or nothing starts. This is deliberate — there is no fallback
   password anywhere.
2. **A fresh machine also needs the toolchain**: JDK 25 and a Docker daemon. Maven is not
   required — `backend/mvnw` is committed. `mvn verify` needs Docker for the `*IT` tests;
   `mvn test` does not.

## Git state

**Close-out now always pushes** (`CLAUDE.md`, session close-out step 4), so local `main` and
`origin/main` agree at the end of every session. This section therefore records *which commit each
step landed in* and no longer tracks what is unpushed — that list was itself a source of drift, and
at the start of this session it was wrong: it claimed `a09428e` and `920044c` were local when both
were already on `origin`.

| Commit | Step |
|---|---|
| `22bb361` | Step 1 — skeleton, guardrails, container stack |
| `cb93fc8` | Step 2 — primitives, migrations V1–V3, Settings, Audit, Attachments |
| `f2ed289` | Step 3 — chart of accounts, migration V4 |
| `15627d2` | Step 3b — VAT classes, exemption reasons, charge types, migration V5 |
| `a1da425` | Step 4 — users, roles, permissions, session auth, migration V6 |
| `91543fa` | Step 4b — first REST endpoint, web boundary rule made real |
| `efe897e` | Q27 — `Delivery income` / `COD fee income` accounts, ChargeType seed, migration V7 |
| `09ea0d5` | The real AADE VAT exemption reason seed, migration V8 |
| `ae7c31f` | Step 5 — Product, Customer, Supplier, Asset, migration V9 |
| `9c25993` | VAT rate bound's blind spot closed, migration V10 |
| `2dce5df` | Q34 — units of measure as a runtime-editable table, migration V11 |
| `7182831` | Step 6 — inventory lots, serialized units, locations, bundles, migrations V12–V13 |
| `8e7e10e` | Step 7 — journal engine, VAT accounts, stock write-offs, migrations V14–V15 |
| `c6e2513` | Step 8 — purchase invoices, goods receipts, GR/IR clearing, purchase price variance, FIFO consumption, migration V16 |

Interleaved with these are small docs-only commits (`e25fcee`, `a09428e`, `920044c`, `de16e58`,
`b065901`, `8c27cb4`, `2c3fa8a`, `21b2231`, `d1111d0`) and this session's close-out commit.

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
  constraints. Same for the VAT class rules in `VatClassIT`.
- **The currency-companion rule proven to actually fail**, same method: a temporary migration
  adding a `probe_money` table with a `numeric(19,2)` column and no `_currency` companion tripped
  `SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency`, naming the offending column, while
  correctly ignoring a properly paired column in the same table. Probe deleted.
- **Hibernate's `ddl-auto: validate` caught a real mismatch during step 5** — the entity mapped
  `selling_price_currency` as a `varchar` against a `char(3)` column and the context refused to
  start. Fixed with `@JdbcTypeCode(SqlTypes.CHAR)` rather than by widening the column. Worth
  recording because it is the first time that setting has earned its keep.
- **The `..core.web..` boundary rule proven to actually fail**, same method: a probe class in
  `..core.web..` referencing a public core-internal class tripped it, naming both the offending
  field and constructor parameter. Probe deleted. Its `allowEmptyShould` allowance is gone, so the
  rule can no longer pass vacuously.
- **Authentication end to end over real HTTP**: 401 unauthenticated, 403 for Remote/Order Staff,
  200 for the Owner, logout invalidating the session, CSRF enforced, and the session cookie's
  `HttpOnly` / `Secure` / `SameSite=Strict` asserted against the real `Set-Cookie` header.
- **The startup refusal when no user exists and no initial owner is supplied** — unit-tested,
  including the partial-credentials case.
- **The widened monetary-currency rule proven to actually fail**, same method again: a temporary
  `probe_cost` table with an unpaired `landed_cost numeric(19,6)` tripped
  `SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency`, naming exactly that column while
  correctly ignoring a properly paired `unit_cost`, a rate and a quantity in the same table. Probe
  deleted.
- **`CLAUDE.md` rule 6 is proven to be a database guarantee, not a service check.** Raw-SQL probes
  in `JournalIT` write straight to the tables and are refused: an unbalanced entry, an entry with no
  lines at all, a one-line entry, an entry spanning two currencies, a zero line amount, a deleted
  entry, a changed `source`, a dangling sub-ledger reference, and a VAT class on an account that is
  not a VAT account. **The balance probes use a `DO` block**, because the constraint is *deferred*
  and under autocommit each separate statement would be its own transaction.
- **Q13's correction policy is proven to be enforced in the database too** — the `UPDATE` and
  `DELETE` halves of `immutableSourcesAreRefused` bypass the service entirely — and **the two
  statements of the policy are proven to agree**: a test calls
  `journal_source_is_amendable(varchar)` for every value of the Java `JournalSource` enum and
  compares against `isAmendable()`, and a second test checks the `journal_entry_source_known` CHECK
  lists exactly the enum's values and no others.
- **The two-shapes-of-lot invariant is enforced by the database**, proven by raw-SQL probes in
  `InventoryIT` that bypass the service: a quantity with no location, a location with no quantity, a
  lot with more remaining than received, a negative remaining, a negative unit cost, a bundle that is
  also serial-tracked, a serialized service product, a self-referencing bundle component and a
  duplicate component are all refused by CHECK or UNIQUE constraints, each named in the assertion.
- **Step 8's purchasing invariants likewise**, by raw-SQL probes in `PurchaseInvoiceIT`: a duplicate
  supplier invoice number (case-insensitively, by trigger), an invoice line that is neither an
  inventory nor an expense shape, a line stating no VAT treatment, a second GR/IR match for the same
  pair, and a second lot claiming one delivery line. **And the consumption-source CHECK is held to
  `JournalSource.mayConsumeStock()`** the same way `journal_source_is_amendable` is held to
  `isAmendable()` — per value, and by counting the constraint's literals so a value added to the
  database alone cannot hide.

## Not yet verified

- **Backup restore.** Brief §13 already flags this. Nothing exists yet (step 12).
- **The REST surface is one read-only endpoint.** `GET /api/chart-of-accounts` and nothing else, so
  the frontend still has essentially nothing to call. Everything else — products, customers,
  settings, users — has a service and no HTTP route.
- **Nobody has logged in through a browser.** Authentication is proven by an integration test over
  real HTTP, not by a human using the generated Spring Security login page against the Compose
  stack. The frontend has no login screen.
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
  was dormant until step 7, which brought the first monetary columns; its no-floating-point rule
  was live from the start.
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
`SchemaConventionsIT` was updated to say so. Its scale rule became live here; the `numeric(19,2)`
half waited until step 5's `product.selling_price` and now covers the journal's own amounts.

---

## Step 4 — done (users, auth, permissions)

Q21 and Q22 both answered, so this was built as specified rather than against a placeholder.

### Q22 — server-side sessions with an HttpOnly cookie (approved)

- **Spring Security with form login**, session-based. `NOVOCORESESSION` cookie is `HttpOnly`,
  `SameSite=Strict`, `Secure`, 8-hour timeout, with a new session id issued on login so a fixated
  identifier cannot become an authenticated one. All three attributes are asserted against the
  real `Set-Cookie` header over HTTP.
- **CSRF is on**, with the token in a JavaScript-readable cookie so a frontend can echo it back.
  Non-negotiable given cookie auth: without it any site the user visits while logged in can make
  their browser send an authenticated request. Deferred token loading is switched off so the
  cookie exists on the first response.
- **Login and logout return status codes, not redirects** (204/401). A `fetch()` cannot do anything
  useful with a 302 to a login page — it follows it and gets HTML with status 200, which looks
  like success. `/api/**` likewise returns 401 rather than redirecting.
- **No login controller was written.** Authentication uses Spring Security's own `/login` and
  `/logout`, so this step added no hand-written API surface.
- **Password hashes never leave the core.** `UserService.authenticate(username, rawPassword)` takes
  the plain password and returns a user or nothing, so hashing and comparison both happen inside
  the core. The conventional `UserDetailsService` arrangement hands the hash to the framework,
  putting it on the boundary and into every stack trace on the authentication path. A custom
  `AuthenticationProvider` in `app` calls the core instead, and `NovoCorePrincipal.getPassword()`
  returns null — it is what lives in the session.
- **Login failures are indistinguishable.** Unknown username, wrong password, deactivated user and
  deactivated role all return empty, and the unknown-username path still runs a hash comparison so
  it does not return measurably faster. The reason is recorded in the audit log, where the
  distinction legitimately belongs; both success and failure are logged.
- **Hashes are algorithm-prefixed** (`{bcrypt}$2a$...`) via a delegating encoder, so a future move
  to a stronger algorithm does not invalidate existing passwords.
- **⚠️ Password policy is a stated default, not a decision.** Twelve characters minimum, no
  composition rules (NIST SP 800-63B: composition rules push people to predictable substitutions).
  **2FA is not implemented.** Q22 approved the session mechanism and left both open — see below.

### Q21 — Remote/Order Staff, built as the concrete case

Brief §7 also requires **multiple custom roles from the start**, so roles are **data** while the
things being granted are **code**:

- `Section` and `ProtectedField` are enums — which parts of the application exist is determined by
  what has been built, not by configuration.
- `app_role` + `role_section_grant` + `role_field_restriction` are tables, so creating a role is an
  operation rather than a migration.
- **Access is default-deny.** "Everything else is invisible" needs no enumeration and stays true as
  sections are added; a new section is invisible until granted.
- **Owner and Admin use a `full_access` flag, not stored grants per section.** With stored grants a
  section added in a later release would be invisible to the owner of the system until someone
  inserted a row. Both are **system roles**: unmodifiable and undeletable, so removing
  `USERS_AND_ROLES` from the last role that has it cannot lock everyone out.
- **Remote/Order Staff is seeded exactly as answered** — `FULL` on Sales Order Fulfillment,
  Customers and Back-in-Stock Reminders; `VIEW` on Products; `PRODUCT_LAST_PURCHASE_PRICE`,
  `PRODUCT_SUPPLIER` and `PRODUCT_SUPPLIER_SKU` hidden; nothing else. Deliberately **not** a system
  role, so it stays adjustable at runtime.
- **Field restrictions narrow, never widen.** A role that cannot view Products does not see a
  product's cost even with no restriction recorded against the field.
- **An inactive role or user grants nothing**, independently of the other.
- The permission decision lives on `RoleView` as pure logic, so it is exhaustively tested with no
  database — including a sweep over *every* section, which means a section added later is covered
  by the test the day it appears.

### ⚠️ Step 5 obligation: the field mechanism has nothing to guard yet

`ProtectedField`'s three entries are live configuration, not placeholders — the grants and
restrictions are seeded and enforced. But **Products do not exist until step 5**, so no response is
currently redacted by them. When `ProductView` is built it **must** consult
`RoleView.canSee(ProtectedField)` for each of the three. That is the one piece of Q21 that could
still silently not happen.

### First-login bootstrap

**No user account is seeded and there is no default password** — the same stance as the database
credential. The first Owner comes from `NOVOCORE_BOOTSTRAP_OWNER_USERNAME` /
`NOVOCORE_BOOTSTRAP_OWNER_PASSWORD`, and **the application refuses to start** if the user table is
empty and those are unset, naming both variables. Once a user exists the variables are ignored and
should be removed. `docker/.env.example` and `compose.yml` carry them.

### `auditorAware` now records the real user

Step 2 left this returning `system` unconditionally with a note that step 4 would replace it. It
now reads the authenticated user via a `CurrentUser` interface in `core-api`, implemented in `app`
against the security context — the seam that keeps the core unaware Spring Security exists.
Unattended work (Flyway seed, future backup and depreciation runs) still records `system`, which is
honest rather than attributing it to whoever logged in last. Resolved through an `ObjectProvider`
so the core's own tests, which have no web layer, still work.

---

## Step 4b — done (the first REST endpoint)

One endpoint: `GET /api/chart-of-accounts`, read-only, returning `List<AccountGroupView>` through
`ChartOfAccountsService`. **Scoped deliberately narrow — this is boundary validation, not the start
of the frontend API.** No other endpoint was added.

**It did its job.** The `..core.web..` ArchUnit rule previously carried `allowEmptyShould(true)` and
passed while checking nothing. That allowance is now removed, and the rule was **proven to fail**: a
temporary probe class in `..core.web..` referencing a public core-internal class tripped it, naming
both the field and the constructor parameter. Probe deleted.

The controller has no repository, no entity, no `@Transactional` and no mapping code — a service
interface and a permission check. Authorisation is an explicit `requireView(Section.CHART_OF_ACCOUNTS)`
rather than a `@PreAuthorize` string, because a typed enum cannot be misspelled and a misspelled
expression that fails open is the worst available outcome. With many controllers this should become
a shared interceptor.

Proven end to end over real HTTP: 401 unauthenticated, **403 for Remote/Order Staff**, 200 with the
chart for the Owner, session invalidated by logout, CSRF enforced, and the refusal body leaking
neither the contents nor the permission model.

### Consequence: the core's test context excludes the web layer

`CoreTestApplication` now excludes `..core.web..` from component scanning. The controller depends on
`CurrentUser`, which only `app` implements, so scanning it in the core's own tests failed the whole
context. Excluding it is the honest answer — those tests exercise services against a real database,
and the endpoint is tested in `app` where the full wiring exists. **A permissive fallback
`CurrentUser` bean in the core was considered and rejected**: a security component that substitutes
a default when its real implementation is missing is precisely what later fails open.

### Notes for whoever adds the second controller

- Response types are core-api DTOs, not separate web records. Right for a read-only projection
  already shaped for the outside world; wrong the first time a response needs a shape the core has
  no reason to have.
- `WebExceptionHandler` maps the core's permission exceptions to 401/403. It exists because those
  exceptions live in `core-api`, which may not have a Spring dependency, so `@ResponseStatus` on
  them is not an option.

---

## V7 and V8 — the two queued items, done

**V7 (`efe897e`) closes Q27.** `Delivery income` and `COD fee income` added to the Income group
(65 → 67 accounts), and the two `ChargeType` rows seeded against them, so `charge_type` is no
longer empty. V7 opens a display-order gap at 6 and 7 **by position rather than by name**, so the
sales-related lines read together and `Other income` stays last — a residual bucket in the middle
of a list invites postings that should have gone somewhere specific. No channel split, and no
`AccountSystemKey`: these accounts are located through `charge_type.incomeAccountId`, which is an
operator decision per fee rather than a rule compiled into the software.

**✅ Q33 settled, and confirmed with the accountant: a fee's VAT rate is independent of the products
on the invoice.** Both charge types default to 24%, and that is the operative rate rather than a
placeholder — a 13% order still carries 24% delivery. It was raised here as a possible defect, on
the general principle that an ancillary charge follows the main supply's rate; **the accountant
confirmed the treatment as built**, so this is a settled decision rather than a recommendation that
was overruled. Consequently **nothing should later be built to derive a fee's rate from the lines
around it.** The per-line override still exists for a deliberate exception; what is deliberately
absent is anything automatic. V7's comment and `ChargeTypeIT` both state the decision rather than
the former limitation.

**V8 (`09ea0d5`) seeds the real AADE VAT exemption reasons** — 29 rows from Prosvasis Go's
"Διατάξεις απαλλαγής Φ.Π.Α." screen, in the **recodified** Κώδικας ΦΠΑ article numbering (άρθρο 2
και 3, 5, 17, … 58) rather than the older numbering most documentation still uses. Three findings:

1. **Codes 24 and 28 are absent from Go's list.** Gaps were anticipated, but these are missing from
   *Go* rather than known to be retired by AADE. See Q35.
2. **Codes 29, 30 and 31 — the OSS and IOSS reasons — have no myDATA code in Go**, so
   `mydata_code` is now **nullable**. NULL means "no mapping exists", not "not filled in yet".
   Composing a string would fabricate a value that later gets transmitted, and omitting the three
   rows would leave an exempt OSS/IOSS sale with no reason to select. **Phase 7 obligation:
   transmission must refuse a NULL**, which is what `VatExemptionReasonView.requireMydataCode()`
   exists to do. See Q36.
3. **Storing the myDATA string verbatim was load-bearing**, which step 3b said a test should check
   once the real rows landed. It was: codes 12 and 13 name "Πλοία Ανοικτής Θαλάσσης" in their
   description and **not** in their myDATA string. Composing the value would have transmitted those
   two wrong. A test asserts exactly which rows break the pattern.

Descriptions drop Go's numeric prefix (`"1 - Χωρίς ΦΠΑ …"`), since the code is its own column here
— keeping it would render as "1 - 1 - …". That also sidesteps a source quirk: Go's row for code 9
appears to carry the prefix `"8 - "`.

---

## Step 5 — done (Product, Customer, Supplier, Asset)

Migration `V9`, four entities, four services, and all four blocking questions answered.

### The four answers, as built

- **Q5 — one product, one supplier.** A plain nullable foreign key, no many-to-many. The supplier
  SKU is **refused without a supplier**, in the service and by a CHECK constraint: that meaningless
  state is the whole content of the question. A test asserts no join table exists.
- **Q8 — a single email and a single phone**, on Customer and Supplier alike.
- **Q9 — `VatStatus`, shared by both parties** so the two lists cannot diverge. **Five values, not
  four:** `NON_EU_EXPORT` is split out of `OTHER`, because an export and an intra-EU B2B supply are
  both VAT-free **under different articles** and are reported differently — "other" would lose
  exactly what has to be stated on the document. `INTRA_EU_B2B` requires a VAT number and `EXEMPT`
  requires an exemption reason; both are definitional rather than policy, and both are CHECK
  constraints as well as service checks. `OTHER` exists so an unusual party can be recorded
  truthfully, and **nothing defaults to it**. No VIES validation, as instructed.
- **Q12 — a manually set depreciation rate on Asset, nullable.** Null means "the statutory rate is
  not known yet", which is the register's actual state. `AssetService.withoutDepreciationRate()`
  exists so that stops being forgettable, and `AssetView.canDepreciate()` is what a run must check
  instead of substituting a default. **No rate was invented and no category table was created** —
  both wait on the accountant, the way the VAT class list did.

### Deliberately omitted from Asset, with reasons

- **Useful life** — for straight-line it is `100 / rate`, so storing both invites them to disagree.
  Same argument that keeps `normal_balance_side` out of the chart of accounts.
- **Salvage value** — Greek tax depreciation writes down to zero, and it would be the one monetary
  field on an otherwise ledger-derived record.
- **A depreciation method field** — straight-line only (brief §5). A single-valued column is dead
  weight; a second method arriving is a migration with a decision attached.
- **Any monetary field at all.** Both fixed-asset control accounts declare `ASSET` as their
  sub-ledger, so every posting names its asset and cost and accumulated depreciation are **sums of
  journal lines**. Consequence, stated plainly at the time: **until step 7 this was a register, not
  a valuation** — the same shape as a product having no stock until lots exist. The ledger exists
  now, so an asset's carrying value is `subLedgerBalanceOf` on its `ASSET` reference; nothing posts
  to it yet, because the depreciation run is still open.

### The depreciation rate is bounded 1–100, and the lower bound is the point

A plain 0–100 range **cannot** catch `0.1` written for 10%: it sits comfortably inside, and the
charge would be a hundred times too small every year with nothing complaining. 1% is a hundred-year
life, which no statutory category has. **A test caught this**: the first version of the validation
claimed in its message to reject fractions and did not. Worth knowing that
`vat_class_rate_is_a_percentage` has the same blind spot — its 0–100 bound does not catch `0.24`
written for 24% either.

### Step 4's field-restriction obligation is discharged

`ProductView.redactedFor(RoleView)` is the **single** implementation, delegating every decision to
`RoleView.canSee`. Tested against the real seeded `REMOTE_ORDER_STAFF` role loaded from the
database, and — as pure logic in `core-api` — against a last purchase price that cannot exist in
real data until step 6, so all three restricted fields are covered now rather than two of them.

**One rule beyond the stored restrictions: hiding the supplier hides the supplier's SKU too**,
since a supplier code identifies the supplier indirectly. Narrowing only, which is the safe
direction and the direction field restrictions are allowed to move in.

> ⚠️ **A named convention, not an enforced one.** `ProductService` has plain read methods
> (unredacted, for the core's own costing and posting rules) and `...For(viewer)` variants that
> redact. **Anything answering a request from a person must use the `...For` variants.** Making
> redaction mandatory would mean inventing a pretend-role for the posting rules to pass, and a
> "system role that sees everything" is precisely the thing a controller later reuses. The `For`
> suffix is in the name so its absence is visible at the call site — but the first Products
> controller must be reviewed for this specifically.

### `product.selling_price` is the schema's first monetary column

Which settles what V1 left open. The convention is now stated and enforced:

    <name>            numeric(19,2)   the amount
    <name>_currency   char(3)         its ISO 4217 code, present exactly when the amount is

Tied by a biconditional CHECK, and `SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency`
enforces the pairing **across the whole schema**, so step 7's monetary columns inherit the rule
rather than re-deciding it once there are dozens. Proven to fail against a probe table.

A **zero price is refused**; null is how "not priced yet" is said. Zero and unset look identical on
a screen, and zero produces an invoice line worth nothing without anyone choosing to give the goods
away. Null is permitted because a product imported from an external catalogue or created
barcode-first may genuinely not have a price — the refusal belongs at invoicing, not at creation.

### Other decisions worth keeping

- **`ProductType`** is `GOODS` / `SERVICE`, and it decides real behaviour: a service has no lots,
  credits `Services` rather than a channel `Sales` account, and costs against `Cost of service
  sold` — three accounts the seeded chart already distinguishes.
- **Matching is split by certainty** (`CLAUDE.md` rule 7). `findByVatNumber` is an exact match on
  an authority-issued identifier and may be applied automatically; `suggestMatches` returns
  candidates a human confirms. A blank VAT number matches **nothing** rather than the first party
  without one — that would be an automatic match on the absence of the identifier that makes
  automatic matching safe.
- **Customer names are not unique; VAT numbers are.** Two unrelated retail customers genuinely can
  share a name, and refusing the second would push whoever is serving them into inventing a suffix.
- **Phone numbers are compared as stored.** Nothing normalises `+30` / `0030` / bare local yet, so a
  differently formatted number will not match. That belongs with the adapters that import contact
  data, where the source format is known.
- **`suggestMatches` runs one derived query per supplied criterion and merges in Java.** The
  compact single-JPQL-query version does not work: a named parameter appearing only inside
  `:x IS NOT NULL` gives Hibernate nothing to infer a type from, it binds as `bytea`, and
  PostgreSQL rejects `lower(bytea)` at runtime. A test caught it.
- **Cross-slice references are plain ids**, validated through the published services — the same
  pattern `ChargeType` established, and the only route available since each slice's entities are
  package-private.
- **Sections `PRODUCTS` and `CUSTOMERS` are now available; `SUPPLIERS` and `FIXED_ASSETS` are new.**
  No grants were seeded: access is default-deny, so the two new sections are invisible to
  Remote/Order Staff without saying so, and visible to Owner and Admin at once via `full_access`.
- **The migration README was corrected** — it still said there were no migrations yet, and told
  writers to use PostgreSQL DOMAINs that V1 explicitly rejected.

### Not built, deliberately — each asserted absent by a test

So they read as decisions rather than oversights, and so a later step cannot quietly assume one
exists:

- **No bundle flag** (Q11 still open). A flag nothing honours reads as a half-built feature.
- **No customer merge.** Brief §5's alias-forward needs an alias table and a decision about
  postings already made under the retired id; neither exists until the ledger does. Half of it is
  worse than none — a merge that appears to work and loses references.
- **No generic retail customer** (Q10 unanswered). A seeded catch-all is the row that quietly
  absorbs every unmatched sale and then cannot be untangled.
- **No address fields** on Customer or Supplier. Go issues the invoices until phase 11, so nothing
  needs to print one yet. See Q37.
- **No stock in any form**, and no `last_purchase_price` column (Q6 answered by implementation:
  computed, like stock). `ProductView.lastPurchasePriceIfAny()` exists and is always empty until
  step 6.

### ⚠️ Obligations this step created

- **Step 6 — `ProductType.SERVICE` must not answer "zero" when asked for stock.** A service has no
  lots. Zero and "not applicable" look identical on a screen and would produce a back-in-stock
  reminder for a service.
- **Step 6 — `UnitOfMeasure.allowsFractionalQuantity()` exists and nothing enforces it.** Three of
  a product sold by the piece is three; 2.5 pieces is a data-entry error worth catching. The rule
  is stated on the unit so step 6 reads it off there rather than re-deriving its own list.
- ~~**Step 7 — the `Depreciation` expense account has no `AccountSystemKey`**~~ — **done in step 7.**
  `DEPRECIATION_EXPENSE` was added in V14, so the expense side of a depreciation posting has a stable
  handle instead of being found by an editable name. **Nothing posts to it**: whether the periodic
  run is Phase 1 scope is still open, and the statutory rates are still with the accountant.
- **The first Products controller must use the `...For` variants.** See the warning above.

---

## V10 and V11 — the two follow-ups from step 5's review

### V10 (`9c25993`) — the VAT rate bound had the same blind spot as the depreciation rate

Flagged by the user after step 5's rate bug, and worth recording as a pattern rather than an
incident. **V5's 0–100 CHECK did not do what its comment claimed.** It said a rate entered as a
fraction would "fail loudly instead of undercharging 100×"; in fact `0.24` sits comfortably inside
0–100, so it was accepted as a quarter of one percent and produced exactly that undercharge.
`VatClassViewTest` even asserted the behaviour and documented the gap as a known limit instead of
closing it.

**The rule is now "exactly 0, or between 1 and 100"** — not a flat minimum, because the `'0'` class
(Μηδενικός Συντελεστής ΦΠΑ) is real, seeded, and legally distinct from an exempt line, so `>= 1`
would have refused real data. Nothing charges a fraction of a percent, so the whole interval (0, 1)
is unreachable by legitimate data and is available as a trap. Stated once as
`VatClassView.isAcceptableRate`, applied by the service, and enforced by the database, so all three
agree by construction. Every seeded rate already satisfied it.

**Worth generalising:** a range check whose bounds both sit outside the plausible-typo range catches
nothing. Both times the mistake was the same — the *upper* bound was chosen carefully and the lower
one was left at "not negative". Any future rate column should be checked against this.

### V11 (`2dce5df`) — units of measure became a table (Q34)

Step 5 built `UnitOfMeasure` as an enum; the user approved converting it. The decisive argument is
the one that made `VatExemptionReason` a table: **myDATA has its own unit codes, which are AADE's
data, and an enum constant cannot own them.** Prosvasis Go also holds "Μονάδες μέτρησης" as an
editable list, so an operator already expects to add one without a deployment. Converted now rather
than after step 6, because lots carry quantities and the reference gets harder to move with every
table pointing at it.

- **`mydata_code` is nullable and every seeded row has NULL** — same stance as the OSS/IOSS
  exemption reasons. `UnitOfMeasureView.requireMydataCode()` makes phase 7 fail naming the unit
  rather than transmitting a composed code, and `withoutMydataCode()` answers "which units cannot be
  transmitted?" before AADE asks it.
- **A myDATA code is write-once.** One that has been transmitted describes documents already filed
  under it, so a wrong mapping means deactivate-and-replace, not edit.
- **`allowsFractionalQuantity` moved from enum constant to column**, since it is a judgement about
  how the business sells rather than a physical fact. Still unenforced until quantities exist.
- **`UnitOfMeasure` is a real `@ManyToOne` from `Product`**, unlike the VAT class and supplier which
  are plain ids. Not an inconsistency: it lives in the same package, so it is the same slice of the
  core rather than another aggregate reached through a published service.
- **Deactivating a unit a product still uses is refused**, not cascaded — a product whose unit was
  retired carries a quantity that no longer states what it counts.
- The column conversion is **add / backfill / constrain / drop**. The product table is expected to be
  empty everywhere, but a migration that silently needs an empty table is one that fails on exactly
  the machine where someone has been working.

> ⚠️ **New step 6 obligation:** `ProductService.changeUnitOfMeasure` must refuse a change on a
> product that has stock. Reinterpreting 12 pieces as 12 kilograms is not a units change, it is a
> different quantity. There is nothing to guard yet; the obligation is stated in the interface and
> the implementation so it is findable.

> ⚠️ **Still needed from the accountant, now with a home to go in:** the verified AADE unit codes
> (Q34 follow-on), alongside the exemption codes in Q35/Q36 and the depreciation rates.

---

## Step 6 — done (inventory lots, serialized units, locations, bundles)

Commit `7182831`, migrations `V12` and `V13`. All three blocking questions were answered at the
start of the session, so this was built to the answers rather than around them.

### The three answers, as built

- **Q7 — stock per location, plus a computed sellable figure; sellable is the Inventory location
  only, excluding Damaged Goods and Service.** `InventoryService.stockOf` returns `StockLevels`,
  which carries **every** location with zero where there is none, and derives `sellable()` from
  `StockLocation.sellableLocations()` rather than reading `INVENTORY` directly — so a second
  sellable location is one edit on the enum instead of a search for hardcoded values. This is why
  V9 was right to refuse a stock column: nine on hand and three sellable is the ordinary case, and
  a single number has to pick one of them to be wrong about.
- **Q25 — a fixed enum.** `WriteOffReason`: `SHRINKAGE` / `DAMAGE` / `EXPIRY` / `OTHER`. Free text
  would give "damaged", "Damaged", "broken in transit" and "ΦΘΟΡΑ" as four categories, which is the
  same as having none, and reportability is the entire reason the single write-off account was
  chosen over three. `OTHER` exists so nobody is forced to pick the nearest-looking value and
  corrupt the four that matter.
- **Q11 — bundles built now, to brief §5 in full.** We currently sell bundled products, so this was
  not speculative scope. All five requirements: own SKU, no stock of its own, proportional
  allocation, decomposition into component lines, and the link between the two revenue levels.

### `StockLocation` is an enum, not a runtime-editable table

The opposite call from `VatClass` and `UnitOfMeasure`, and deliberately so. Those became tables
because the authoritative list belongs to AADE or to Prosvasis Go. These three values are
NovoCore's own, and **every one of them has behaviour attached that only NovoCore can supply**:
`INVENTORY` is what sellability is computed from, and `DAMAGED_GOODS` is what phase 8's Clearing
Checks must single out. A row an operator added at runtime would be storable and unhandled.

Named `StockLocation` rather than `Location`, because a bare `Location` reads as an address and
Customer and Supplier will need one of those (Q37). **Not a warehouse** — several physical
warehouses are a different concept, absent from the brief, and would arrive alongside this rather
than as extra values in it.

### Two shapes of lot, and the nullable columns are the mechanism

The load-bearing design decision of this step. A lot is one of exactly two things:

| | quantity columns | `location` | `serialized_unit` rows |
|---|---|---|---|
| **Pooled** | on the lot | on the lot | none |
| **Serial-tracked** | **absent** | **absent** | one per unit, each with its own location |

The rule across both: **location lives wherever the quantity does.** A serial-tracked lot stores no
quantity because the quantity *is* the count of its units — storing it as well would be two numbers
that must agree and are therefore free to disagree after the first sale, the same argument that
keeps `normal_balance_side` off `account`, a useful life off `asset`, and a cost off `asset`
entirely. `InventoryLotView` still exposes concrete quantities for either shape, computed by
counting, so a caller does not have to know which kind it is asking about.

One CHECK (`inventory_lot_pooled_columns_go_together`) refuses any third shape, proven by raw-SQL
probes. The consequences are symmetric and each fails loudly: **`moveLot` refuses a serial-tracked
lot** (move its units — one machine going out for repair does not move the others) and **`moveUnit`
refuses anything not on hand**.

### `UnitCost` — the type `Money` has been pointing at since step 2

Six decimals, its own type, in `core-api/shared`. `Money` is exactly two decimals and *rejects*
anything more precise; a unit cost cannot live inside that, because brief §4's proportional
landed-cost allocation produces repeating decimals per unit and rounding them before they are
multiplied back out overstates the lot for no reason anyone can later find. Two euros of freight
over three units is `0.666667`, and `extend()` is the only route back to `Money` — so every place
that gives up precision does it in a method that names its rounding mode.

**Zero is allowed, negative is not.** A supplier's free sample is a real lot, and unlike a zero
*selling price* it gives nothing away by being recorded. A negative unit cost is not a fact about
any lot: a purchase credit reduces the quantity or reverses the receipt.

`inventory_lot.unit_cost` is therefore **the schema's first monetary `numeric(19,6)` column** — the
others at that scale are a VAT rate, a depreciation rate and a quantity, none of which have a
currency. See the schema-convention note below.

### Bundles, and the allocation arithmetic

`BundleAllocation.proportionally(total, weights)` is **exact integer arithmetic in cents** with
largest-remainder distribution. The obvious implementation — divide, multiply, round each line —
loses a cent or two on almost every split, and those are exactly the residuals brief §6 then has to
reconcile. Instead each part's numerator is a whole number, floored by integer division, and the
leftover cents go to the parts whose exact share was cut by the most, with ties broken by position
so the answer is reproducible rather than dependent on iteration order.

Two consequences worth keeping:

- **It never needs a rounding mode and never produces a rounding difference.** `Rounding
  differences` is for reconciling against an *external* document, not for absorbing our own
  arithmetic.
- **`BundleDecomposition` enforces in its constructor that the component lines sum to the bundle
  line.** That is what makes brief §5's "linked, not duplicated" a property of the data rather than
  a hope about whoever writes the phase 8 report: either level gives the same revenue, and adding
  them together is visibly double-counting.

The rest of the bundle rules:

- **Components are one level deep** — a component may not itself be a bundle. Same rule and same
  reasoning as V5's island-reduced VAT counterpart: it makes a cycle impossible by construction
  rather than by a recursive check that has to be got right, and it keeps allocation single-pass.
  Enforced in the service, because a CHECK cannot read the other row's flag; the self-reference half
  *is* a CHECK.
- **`define` replaces the whole component list**, never merges. A partial change leaves the rest in
  a state nobody chose — the argument that makes a chart-of-accounts reorder name every member. It
  also means the flag and the components are set in one transaction, so **a bundle never exists
  empty**.
- **A bundle has no stock of its own.** It cannot receive a lot, and a product that already has lots
  cannot become one — either way the same goods would be counted twice. `stockOf` on a bundle
  computes how many could be assembled per location, limited by whichever component runs out first,
  by **integer** division: half a component is not half a bundle.
- **A bundle may be GOODS or SERVICE and its components may be either.** A machine sold with its
  installation is a real bundle; the installation takes allocated revenue and nothing off a shelf,
  so only *stocked* components constrain availability. A bundle with no stocked components refuses
  to report stock rather than answering zero, which would say the opposite of what is true.
- **An unpriced component refuses decomposition** rather than weighing zero — a zero weight would
  push the whole bundle's revenue onto the priced components and report the unpriced one as pure
  margin. Same stance as having no fallback VAT rate. `bundlesWithUnpricedComponents()` exists so
  that is found before a sale rather than during one, mirroring
  `AssetService.withoutDepreciationRate()`.

### Serial numbers are unique across all stock

Strictly a stronger claim than the world supports — two manufacturers could issue the same string —
and still the right constraint. Within one business's stock, the same serial appearing twice is
overwhelmingly a duplicate scan or a unit received twice, and catching that is worth more than
accommodating a collision nobody has seen. A real one becomes a per-product uniqueness rule as a
deliberate migration, rather than being discovered as a silent overwrite of a warranty record.

### `Section.INVENTORY` is new, and separate from `PRODUCTS` because of cost

Stock **levels** are a product-level read: Remote/Order Staff has VIEW on Products and genuinely
needs to know whether there are three left. A **lot** carries its unit cost, which is exactly what
`PRODUCT_LAST_PURCHASE_PRICE` exists to keep from that role. So granting the ability to see stock
must not grant the ability to see what it cost. No grants were seeded — access is default-deny.

As in step 4b, the section each `InventoryService` method belongs to is **stated in its Javadoc and
not enforced by the service**; the check belongs at the controller, and there is no controller yet.

### Obligations discharged this step

- ~~**Step 3 — a reason field on the inventory write-off**~~ — the enum exists (see the caveat
  below about it having no consumer yet).
- ~~**Step 5 — `SERVICE` must not answer "zero" for stock**~~ — `StockNotApplicableException`,
  which says "not applicable" instead. Zero is indistinguishable from sold out on a screen and would
  put a repair service into a back-in-stock reminder.
- ~~**Step 5 / V11 — enforce `allowsFractionalQuantity`**~~ — enforced on lot receipts and on bundle
  component quantities, read off the unit rather than from a list kept elsewhere.
- ~~**V11 — `changeUnitOfMeasure` must refuse a product with stock**~~ — done, and
  `changeSerialTracking` carries the same guard for the same reason, plus a stronger one: a pooled
  quantity of five has no serial numbers to recover.
- ~~**Q6 — last purchase price computed rather than stored**~~ — now populated from the most recent
  lot's unit cost, by acquisition date rather than insertion order (a backdated receipt must not
  win). Batched into one `DISTINCT ON` query for list reads rather than one per row.

### ⚠️ Obligations this step created

- ~~**Step 7 — the write-off transaction must carry `WriteOffReason`**~~ — **done in step 7.**
  `stock_write_off` carries it and reduces the lot (or the named unit) *and* posts in one
  transaction. Brief §5's exception is honoured **by construction**: a write-off always names its
  lot, so nothing picks one for the caller and no FIFO logic can creep in.
- **Step 8 — a lot needs its source document reference.** Brief §5 lists one and ADR 0004 already
  settles that the Goods Receipt is what creates a lot; neither exists yet, so no nullable column
  was added early. Also step 8's: **FIFO consumption must use the order `lotsOf` already defines**
  (acquisition date, then id) rather than inventing its own, and **Q17** — whether aggregate stock
  may go negative — belongs with that consumption. A single lot already cannot go below zero, by
  CHECK.
- **Step 9 — the serialized unit's sale link.** `SerializedUnitStatus.SOLD` is declared and
  unreachable: brief §5 wants the customer/invoice link recorded on the unit once sold, and a
  nullable customer id added now would let a unit be marked sold to somebody with no document behind
  it. The stock count is already written against the status column, so it will be right the day a
  unit is sold without anyone revisiting the query. Also step 9's: **`BundleService.dissolve` on a
  bundle that has been sold** would strand decomposed component lines pointing at something that is
  no longer a bundle — brief §5's "alias forward, never rewrite history" is the shape of the answer
  and it needs the ledger.
- **Step 10 — last purchase price must stop coming from the lot.** Brief §5 says a lot's unit cost
  *includes allocated landed costs*, so the day freight allocation exists this figure stops being
  the **purchase** price and has to come from the purchase invoice line instead. It is correct today
  because nothing allocates yet, and the day it stops being correct is knowable in advance. Recorded
  in `InventoryService.lastPurchaseCostOf`'s Javadoc as well as here.
- **Phase 8 — the Damaged Goods aging check now has its query.** `InventoryService.lotsAt` and
  `unitsAt` are what it reads, and `lotsAt` deliberately covers serial-tracked lots (via any on-hand
  unit at that location) and deliberately excludes exhausted lots, which are history rather than
  stock aging at cost.

### Two defects the tests caught, both fixed at the root

Recorded because the pattern matters more than the incidents.

1. **Multiplying two quantities overflowed the scale.** `quantityPerBundle.value().multiply(
   bundleQuantity.value())` is 6dp × 6dp = 12dp, and `Quantity` allows six — so *one* bundle
   containing *one* grinder failed on its own trailing zeros. The patch would have been a
   `setScale` at the call site. The fix is `Quantity.times(Quantity)`, stated once: it strips
   trailing zeros, and **throws rather than rounding** if the product genuinely needs more than six
   places, because a quantity is a physical count and an inexpressible one is a modelling error, not
   a rounding question. Steps 8–10 will multiply quantities constantly and now cannot re-make this
   mistake.
2. **`InventoryLot.getUnits()` returned two different orders.** `@OrderBy` sorts the list Hibernate
   *loads*, and does nothing for a lot built in memory a moment earlier — so a freshly received lot
   came back in scan order and the same lot re-read came back sorted. One projection returning two
   orders is precisely the difference a test written against the second case never sees. Now sorted
   in the getter, so both agree.

### The schema-convention rule was widened, and proven to fail

`SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency` previously covered `numeric(19,2)` only.
`unit_cost` is monetary at six decimals, so the rule had to grow — but **scale alone cannot be the
discriminator**, because a VAT rate and a quantity share it and are not money. The discriminator is
the **name**: a monetary multiplier is named `..._cost`, and the rule requires a currency companion
for any `numeric(19,6)` column whose name ends that way.

**Proven to actually fail**, the same method this repo has used throughout: a temporary probe
migration added a `probe_cost` table with an unpaired `landed_cost`, a correctly paired `unit_cost`,
a rate and a quantity. The rule failed naming exactly `probe_cost.landed_cost` and ignored the other
three. Probe deleted. A future monetary six-decimal column that is genuinely not a cost means
widening the rule, not naming the column around it — which the migration README now says.

---

## Step 7 — done (the journal engine)

Commit `8e7e10e`, migrations `V14` and `V15`, plus **ADR 0006** (correction policy) and **ADR 0007**
(VAT posting). Six questions were answered before the step started — Q13, Q14, Q19, Q26, Q15 and
Q16 — so this was built to the answers rather than around them.

### Rule 6, and what "structurally" turned out to mean

**Debits equal credits is a `DEFERRABLE INITIALLY DEFERRED` constraint trigger**, checked at commit.
A CHECK cannot express it — it spans rows — and deferral is load-bearing rather than a nicety: an
entry is legitimately unbalanced between its first line and its last, so a per-statement check would
make writing one impossible.

**Two triggers, not one.** A trigger on `journal_line` never fires for an entry that has *no* lines,
so without a second one on `journal_entry` an empty entry would be perfectly storable. The
entry-level trigger also enforces the minimum of two lines and refuses an entry spanning two
currencies.

**Stored `total_debits` / `total_credits` columns with a single-row CHECK were considered and
rejected.** They would have made the invariant far easier to express, and they would be a second
copy of what the lines already say — the argument that keeps `normal_balance_side` off `account` and
a quantity off a serial-tracked lot.

**A named side plus a strictly positive amount, never a signed one.** Signed makes the invariant a
question of whether every producer of a line remembered to negate; the named side makes it a sum per
side, which no accidental sign can satisfy. Zero is refused as firmly as negative — a zero line
balances while stating nothing, so an entry padded with them would satisfy rule 6 and mean nothing.
A `debit_amount`/`credit_amount` pair was also rejected, on a schema ground: ADR 0005 needs a
currency companion per monetary column, and two amount columns give either two currencies that must
agree or one that breaks the naming convention `SchemaConventionsIT` enforces.

### Q13, answered — ADR 0006

Invoices and credit notes are **immutable once posted**; receipts, payments, bank transfers and
manual journal entries are **editable in place**, with the previous date, description and lines
written to the audit log *before* being overwritten. The line drawn is whether the record exists
outside NovoCore: an invoice has been issued to somebody else and will be transmitted to AADE, so
editing it makes NovoCore disagree with what the counterparty holds.

- **The policy is stated once, in SQL.** `journal_source_is_amendable(varchar)` is what both triggers
  call, and a test calls it for every value of the Java enum and compares. "Immutable" that holds
  only for callers who came through the service is not immutability.
- **⚠️ Immutability was extended to the inventory write-off** — not covered by Q13's wording, flagged
  as an addition rather than folded in quietly, and **explicitly approved**. The reason is stronger
  than the invoice's: editing the entry would change the loss recognised without changing the lot
  quantity it came out of.
- **Nothing is ever deleted**, from either table, whatever the source.
- **A reversal is verified to be the exact mirror** of its original — same accounts, amounts and
  references, opposite sides. That is what makes `post(..., reversalOfEntryId)` a safe path for a
  service reversing its own document rather than a second and weaker write path. Line *descriptions*
  are excluded from the comparison, since a reversal legitimately re-words them.
- **`reverse` refuses a source that owns state the ledger cannot see**, naming the service to use
  instead. Reversing a receipt's money without releasing its allocations would leave invoices
  reported as settled by a receipt that no longer exists.
- Reversing a **reversal** is permitted and needs no special rule; the "reversed at most once"
  UNIQUE constraint already stops the real mistake.

### Q14, answered — ADR 0007

**Separate `Output VAT` (liability) and `Input VAT` (asset), never netted.** `V14` repurposes V4's
single `VAT payable` into the output side and adds the input side; repurposing is safe **only**
because nothing had posted anywhere yet, and an account that has been posted to is never repurposed.
Neither is `expected_to_clear` — a balance between filings is the ordinary state of affairs, and
flagging them would put a permanent false positive into phase 8's Clearing Checks.

**The load-bearing consequence: a journal line carries `vat_class_id` and `taxable_base`.** Q14 says
VAT is computed per line and summed by rate — and "summed by rate" is only meaningful if the sum can
be told apart afterwards. Without the class on the line, two Output VAT lines at different rates are
two indistinguishable amounts against one account, and one could have posted a single total and lost
nothing. It also cannot live only on the invoice, because a Manual Journal Entry can post to a VAT
account directly and a figure assembled from documents alone would omit it. The **class** is stored,
never the rate, because `1040` and `1041` both charge 4%.

- **Permitted, not required, on the two VAT accounts; forbidden everywhere else**, by trigger.
  Required would break the periodic settlement, which moves money at no rate at all.
- **Reverse charge needed no new structure** — two lines in one ordinary entry, one to each VAT
  account, same class and base. Both figures stay separately reportable while netting to zero in
  cash, which is exactly what netting the accounts would have destroyed.
- **An exempt line posts no VAT line at all**; its exemption reason belongs on the invoice line,
  because there is no VAT line to hang it on.
- **`vatTotals(from, to)`** reads the dimension back, netted per direction so a credit note reduces
  output VAT rather than appearing as a second figure. It is here rather than with the reports
  because a column nothing reads is indistinguishable from a column nobody thought about. There is
  deliberately **no third "VAT payable to authorities" account**: settlement is debit Output, credit
  Input, net to the bank, and NovoCore never accrues a return it has no duty to file.

### The write-off obligation, discharged

`stock_write_off` carries the `WriteOffReason` the single write-off account was chosen against three
for. It reduces the lot (or the named unit) **and posts, in one transaction** — either alone is
worse than neither. `SerializedUnitStatus.WRITTEN_OFF` is reachable at last.

- **Brief §5's serialized exception is honoured by construction**: a write-off always names its lot,
  so nothing picks one for the caller and no FIFO logic can creep in. A test posts two lots at
  different costs and proves the named unit's own cost is used.
- **A lot carried at zero cost posts nothing, and the stock still leaves.** A free sample
  derecognises nothing because nothing was carried, and the ledger rightly refuses a zero-amount
  line, so `journalEntryId` is nullable and `derecognisedNothing()` is the honest reading. This is a
  real case, not a defensive one — `UnitCost` explicitly allows zero.
- **No amount column on the write-off.** What was posted is in the entry, and recomputing it from the
  lot's unit cost would give a different answer once step 10 allocates freight.
- **Reversal restores the quantity or the unit status and posts the mirror together**, refuses a
  second reversal, refuses reversing a reversal, and refuses restoring more than the lot ever
  received (reachable, if something consumed the lot in between).
- The **location of a written-off unit is deliberately left alone** — the machine is often still
  physically in Damaged Goods, and the stock count excludes it by status rather than by place.
- `writeOffsOf` and `writeOffsBetween` **include reversals**: netting them out is the reader's
  decision, not the query's.

### Other decisions worth keeping

- **`GOODS_RECEIPT` is deliberately absent from `JournalSource`.** ADR 0004 settles that a Goods
  Receipt posts, so it will need a value — but whether it is amendable has not been asked, and
  adding one is deliberately a migration so the question gets asked. **Step 8 obligation.**
- **No `source_id` on an entry.** `source` says what *kind* of transaction produced it, because Q13's
  policy is keyed on that and needs it now. There is nothing to point at until steps 8 and 9 — the
  same stance V12 took on a lot's source document. **Step 8 obligation.**
- **No entry number.** The id is the handle. A human-facing sequential number is a real thing an
  accountant asks for and carries a format decision (per-year reset? prefix per source?) nobody has
  been asked. Recorded as an open question rather than guessed at.
- **`entry_date` has a floor of 2000-01-01 and no upper bound.** The floor catches the transposed or
  two-digit year, which a plain "is a date" check cannot — the same lesson V10 recorded about the VAT
  rate, that a bound only earns its keep if it sits inside the plausible-typo range. No upper bound,
  because a forward-dated accrual is legitimate and there is no period locking.
- **A dangling sub-ledger reference is refused by trigger**, using dynamic SQL, because the reference
  is polymorphic and cannot be a foreign key. Doing it in Java would have made the ledger depend on
  the inventory service, which already depends on the ledger to post a write-off — a bean cycle for a
  check the database makes directly.
- **`journal_line_number_unique_in_entry` is DEFERRABLE.** An amendment replaces the whole line list,
  so line number 0 is inserted while the old one is still present. Relying on Hibernate ordering
  orphan removals before inserts would be relying on a library's implementation detail to keep a
  schema constraint satisfiable.
- **Balances are computed on every read** and carry the date they were computed for.
  `AccountBalance` keeps both totals rather than only their difference, because an account with
  equal, large debits and credits is a different situation from one with no activity while both net
  to zero. A balance on the *wrong* side reads negative rather than being made positive — a credit
  balance on a bank account is an overdraft and a debit balance on Accounts payable is an overpaid
  supplier, and an absolute value would hide both.
- **`subLedgerBalanceOf` is debit-positive**, not presented on a normal side: one sub-ledger
  reference legitimately appears on accounts with opposite normal sides — an asset's cost and its
  accumulated depreciation both carry the same `ASSET` reference — so flipping per account first
  would make their net the sum of the two rather than the carrying value.
- **`linesOf` returns lines, not a ledger type with a running balance.** A running balance depends on
  where the reader started and is meaningless against a filtered list; that is presentation, and it
  belongs to phase 8's report.
- **`Section.JOURNAL` is separate from `CHART_OF_ACCOUNTS`**, for the reason `INVENTORY` is separate
  from `PRODUCTS`: seeing the list of accounts is close to harmless, while seeing what has posted to
  them is every financial figure in the business. No grants seeded — default-deny.
- **`AccountSystemKey` gained three values.** `OUTPUT_VAT` and `INPUT_VAT` are Q14's;
  **`DEPRECIATION_EXPENSE` discharges the step 5 obligation** — it is the handle a depreciation run
  will need, and creating it is not building the run.

### ⚠️ Obligations this step created

- **Step 8 — `GOODS_RECEIPT` needs a `JournalSource` value and an amendability answer**, and an entry
  needs its `source_id` once there is a document table to point at. Both above.
- **Step 9 — Q13's second half is unimplemented.** Editing a Receipt or Payment below its
  already-allocated total must reduce allocations **starting with the most recently applied one,
  working backward**. Nothing enforces that yet because allocations do not exist. This is the item
  most easily forgotten, in the same way the write-off reason was.
- **Step 9 — the invoice postings must supply the VAT dimension.** It is *optional* at the ledger
  (the settlement entry legitimately has none), so nothing forces a sales invoice to carry it, and a
  VAT return assembled without it would silently understate. Exempt lines must carry their
  `VatExemptionReason` on the invoice line, which makes exempt turnover by reason a document-level
  report rather than a ledger-level one.
- **The first ledger controller must expose `postManualEntry`, not `post`.** `post` takes a source
  and is the entry point every typed transaction uses from inside the core; `postManualEntry` takes
  no source and is the shape a request from a person can be turned into. Same class of caution as the
  `ProductService` `...For(viewer)` convention.
- **`deactivate` on the chart of accounts still does not check the balance.** Its Javadoc said it
  could not, because there was no ledger; there is one now. The intended behaviour was stated then —
  *warn* rather than refuse, since taking a populated account out of use before a rearrangement is
  legitimate — and nothing implements it yet.

---

## Step 8 — done (purchase invoices, goods receipts, GR/IR, variance, FIFO)

Commit `c6e2513`, migration `V16`, and **ADR 0008**, which answers all three blocking questions
together because they share one principle: **a posting that reflects a physically verified event does
not change after other things have come to depend on it.**

### The three answers, as built

- **ADR 0004's open item — a purchase price variance account, not retroactive lot re-costing.** The
  lot keeps the unit cost it was received at; when the invoice lands at a different price the
  difference posts to `Purchase price variance`. Re-costing a lot that FIFO has already consumed into
  posted COGS is the same problem as editing a posted entry, expressed as a number instead of a
  document. The account is in the **COGS group** so gross margin reflects it, is **not**
  `expected_to_clear` (a variance balance is a result, not a discrepancy), and carries either sign —
  an invoice *below* the expected price is a credit variance, and forcing it positive would hide the
  good news with the bad.
- **Q17 — aggregate stock may go negative, flagged not blocked.** A single lot still cannot, by the
  V12 CHECK. What FIFO cannot fill is the difference between `quantity_requested` and
  `quantity_filled` on the consumption, `stockOf` subtracts outstanding shortfalls so a product
  genuinely reads **−2** rather than 0, and `consumptionsWithShortfall()` is the query phase 8's
  Clearing Checks reads. **No COGS is posted for the shortfall** — there is no lot to take a cost
  from, and reaching for the last purchase price would be the silent guess rule 7 forbids — so COGS
  is understated while a shortfall stands and the flag is what says so.
- **Q39 — a Goods Receipt is immutable, corrected by reversal.** `GOODS_RECEIPT` is deliberately
  absent from `journal_source_is_amendable`, so the existing enum↔function test proves the two agree
  without the function changing at all. `GoodsReceiptService.reverse` un-receives the lots and posts
  the mirror together, and **refuses outright** once anything has happened to them.

### The variance can only ever arise in one direction, and that is the design

A receipt line matched to an invoice line that already exists **takes its unit cost from that
invoice** — `NewGoodsReceiptLine` refuses to state one, and the refusal says why. So invoice-first
clears GR/IR exactly and produces no variance at all; goods-first is the case ADR 0008 exists for.
That asymmetry is worth remembering, because it is what makes "the lot keeps its cost" a rule rather
than a compromise: the lot's cost is only ever provisional when nothing better existed at the time.

### GR/IR matching happens at document creation, and there is deliberately no later matching

Whichever document is created second names what it settles: a Goods Receipt names the invoice line it
delivers against, or a Purchase Invoice names the receipt lines it pays for. There is **no separate
"match these two later" operation** — it would need its own journal entry belonging to no document,
and an unmatched GR/IR balance is already exactly what ADR 0004 says it is. Both halves are queryable
(`linesAwaitingDelivery` / `linesAwaitingInvoice`) and that is what phase 8 reads. **⚠️ Adding
after-the-fact matching is a real feature with a real decision attached (whose document is the
variance entry?); it belongs with Clearing Checks, not with a later step reaching for it casually.**

`gr_ir_match` is a table rather than a nullable foreign key either way, because brief §6 handles
partial delivery across several days: one invoice line is routinely settled by several receipts, and
a supplier billing in instalments splits one receipt across two invoices. **It carries no money** —
the variance is stored per invoice *line*, computed as the residual that makes that line's debits sum
exactly to what the supplier charged, so an unbalanced invoice is impossible by construction.

### A rounding residual that is accepted rather than engineered away

Every posted amount is rounded once at its own line. So when one receipt line is matched by two
invoices, the two rounded portions can sum to a cent more or less than the single rounded amount the
receipt credited, and **that cent stays in GR/IR clearing**. Accepted deliberately: GR/IR is
`expected_to_clear` and `ledger.rounding.threshold` already exists to say what residual is noise,
which is the mechanism brief §6 defines for exactly this. The alternative — a running-total clearing
scheme with per-line cleared amounts — adds a column that must agree with the postings and buys a
cent.

### The step 7 obligation about `source_id`, discharged by deciding against it

**There is no `source_id` on `journal_entry`, and that is the answer rather than an omission.** Two
reasons: an entry from an immutable source cannot be `UPDATE`d after posting, so the id would have to
be known before the document existed; and storing the link on both sides would be two copies of one
fact, free to disagree — the argument that keeps `normal_balance_side` off `account`. The link lives
on the **document** (`journal_entry_id`, UNIQUE), one direction only, exactly as V15 stores one
direction of the reversal link and queries the other. `source` still says what *kind* of document
produced an entry, which is what Q13's policy is keyed on, and each document service answers
`findByJournalEntry`.

### A fourth serialized-unit status, and a freed serial number

Reversing a delivery of serial-tracked machines had no truthful status available: `IN_STOCK` is
false, `SOLD` is false, and `WRITTEN_OFF` would put a loss that never happened into the shrinkage
report the single write-off account was chosen over three *for*. So **`UNRECEIVED`**, and it is the
one status that **does not hold its serial number**: the commonest reason to reverse a delivery is
that it was entered wrong, and re-entering the same machines correctly must not be blocked by the
mistake. The V12 UNIQUE constraint became a **partial unique index**, and
`SerializedUnitStatus.holdsItsSerialNumber()` is the single statement of the rule that the Java check
and the index both read.

### The duplicate-invoice-number rule is a trigger, and the reason generalises

A partial unique index cannot express it. Two documents legitimately share a supplier's number — the
reversing document carries the original's, and once an invoice has been reversed, re-entering it
correctly under the same number is the ordinary thing to want. **Whether a row is superseded depends
on whether another row points at it**, which no index over this row's own columns can see, and a
`superseded` flag maintained beside `reversal_of_id` would be the second copy of a fact this schema
keeps refusing to create. Recorded in the migration README as a rule, not an incident.

### What was built beyond the four things the step named, and why

Both were flagged as judgement calls rather than added quietly:

1. **Expense lines on a purchase invoice.** Without them a coffee retailer cannot record electricity,
   rent or an accountant's fee at all, and step 9 is the sales side. The line **names its account**;
   nothing is inferred. **Brief §7's automatic categorisation is deliberately not built** — the
   suggest-from-product-then-supplier-then-`Unclassified` rule is about *choosing* a destination for
   an invoice nobody typed, and it belongs with the myDATA import that creates those. Nothing lands in
   `Unclassified — Needs Review` by accident here, which is the failure mode a half-built version
   would have.
2. **Reverse charge on the purchase side.** A Greek retailer importing from the EU is ordinary, and
   posting input VAT the supplier never charged would reclaim tax nobody paid. Q14 already settled
   that it needs no new structure. **It is a flag on the line and is never inferred**, but it must
   agree with the supplier's `VatStatus` — required for `INTRA_EU_B2B`, refused for anything else,
   and a disagreement is refused in both directions rather than resolved.

Every invoice line states **either** a VAT class **or** a VAT exemption reason, never both and never
neither: a line with no treatment cannot be filed from, and one with both states two legal positions
about the same money. A purchase from outside the EU, where import VAT settles at customs, is an
exempt line carrying the article that actually applies.

### Obligations discharged this step

- ~~**Step 8 — a lot needs its source document reference**~~ — `inventory_lot.goods_receipt_line_id`,
  nullable and UNIQUE. Null means no NovoCore delivery created it, which is phase 2b's opening stock
  and nothing else once that is done. **One direction only**: `goods_receipt_line` has no `lot_id`.
- ~~**Step 8 — FIFO must use `lotsOf`'s order**~~ — acquisition date then id, read off the same
  repository method, narrowed to lots at a *sellable* location. Selling out of Damaged Goods is not a
  decision a costing rule gets to make quietly, so a product with five damaged units still records a
  shortfall.
- ~~**Step 8 — `GOODS_RECEIPT` needs a `JournalSource` value and an amendability answer**~~ — both,
  above.
- ~~**Step 8 — an entry needs its `source_id`**~~ — **decided against, with reasons.** See above.
- ~~**`deactivate` does not check the balance**~~ — it does now, and **warns rather than refuses**,
  which is what step 3 said it should do once a ledger existed. The warning is the *return value*, so
  a caller cannot fail to receive it, and it is also written to the audit log. `JournalService` is
  resolved through an `ObjectProvider` because `JournalServiceImpl` depends on
  `ChartOfAccountsService` — a constructor dependency the other way would be a bean cycle.

### ⚠️ Obligations this step created

- **Step 9 — a sales invoice will produce two entries, not one.** `InventoryService.consume` posts
  its own COGS entry, because reducing lots without posting is the "half is worse than neither"
  problem the write-off settled. So a sale posts revenue in one entry and cost in another, linked by
  the consumption record. That is an ordinary arrangement and it is stated here so step 9 does not
  discover it as a surprise.
- **Step 9 — serialized consumption.** `consume` refuses a serial-tracked product outright, naming
  step 9: selling an identified unit means marking it `SOLD`, and brief §5 requires the customer and
  invoice on it at that point. `SerializedUnitStatus.SOLD` is still unreachable.
- **Step 10 — Q18 is now constrained.** Landed-cost allocation must allocate against the lot's
  **received** cost and must not reach back into consumption already costed out. Whatever step 10
  does with the sold portion of a lot, it is not "recompute the COGS that was posted". The mechanism
  is still open; its shape is not.
- **Phase 8 — two new checks have their queries.** `consumptionsWithShortfall()` for Q17's flag, and
  `linesAwaitingDelivery()` / `linesAwaitingInvoice()` for the two halves of a non-zero GR/IR
  balance. The checks themselves are still phase 8's to write.
- **The first purchasing controller** must expose the document services and not
  `InventoryService.receive`/`unreceive`, which are the lower layer — the same class of caution as
  `postManualEntry` versus `post` and the `ProductService` `...For(viewer)` convention.

---

## Open questions, by the step they block

Numbering follows the original Phase 1 question list so references stay stable.
**Resolved:** Q1–Q3 (chart of accounts), Q20 (money scale), **Q4 (VAT classes — real rate list
supplied and seeded, built as a runtime-editable entity; precedence rule stated as code)**.

### ✅ Resolved and built this session

- ~~**Q27**~~ dedicated `Delivery income` / `COD fee income` accounts — **built** in V7.
- ~~**The VatExemptionReason seed**~~ — **built** in V8, 29 real rows.
- ~~**Q5**~~ Product↔Supplier — one supplier, plain foreign key, no many-to-many.
- ~~**Q8**~~ single email and single phone per customer.
- ~~**Q9**~~ `VatStatus` classification plus a VAT number field; VIES deferred to phase 7.
- ~~**Q12**~~ a manually set depreciation rate on Asset; automatic pre-fill deferred.
- ~~**Q6**~~ last purchase price is **computed, not stored**, for consistency with stock.

### ✅ Also resolved this session

- ~~**Q33**~~ — **a fee's VAT rate is independent of the products purchased, confirmed with the
  accountant.** Nothing to build; the seeded 24% default is the answer. See the V7 section.
- ~~**Q34**~~ — **converted to a table** (V11). See above.
- ~~**The VAT rate bound**~~ — **fixed** (V10). Not a question, a defect the user flagged.

### ⚠️ Still waiting on input

- **Q35** **AADE exemption codes 24 and 28 are absent from Go's list.** Confirm with the
  accountant whether AADE defines them and whether we need them, before the myDATA adapter is built
  (phase 7). If so it is two `INSERT`s, not a restructuring.
- **Q36** **The OSS and IOSS reasons (codes 29–31) have no myDATA code.** Seeded as NULL
  deliberately — **approved as built**, with phase 7 required to refuse transmission on a NULL
  rather than guess. The real values are to be confirmed with the accountant before then.
- **Q38** *(new)* **The AADE myDATA unit-of-measure codes.** `unit_of_measure.mydata_code` exists
  and every row is NULL. Same shape and same phase-7 obligation as Q36. Add them to the accountant
  list alongside the exemption codes and the depreciation rates.
- **Q37** **Customer and Supplier have no address fields.** Not needed while Go issues the
  invoices, but needed by phase 11 at the latest, and possibly sooner for courier vouchers in phase
  4. Also unasked: whether Customer and Supplier want human-facing codes (the internal id is a
  bigint), and whether more than one selling price per product is ever needed.
- **⚠️ Statutory depreciation rates and the asset category taxonomy** — **needs the accountant**,
  the same way the VAT class list did. The rate field exists per asset and is nullable; no rates
  and no category table were invented. Do not create real assets with real values until these are
  confirmed. When they arrive, the natural home for defaults is an `AssetCategory` lookup carrying a
  default rate — which is also where Q12's deferred pre-fill would live.
- **Q28** **Where "Σκοπός διακίνησης" (dispatch purpose) belongs.** Analysis and recommendation
  below; **nothing built**. Correctly identified as unrelated to VAT — it is not folded into
  either VAT entity.

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

### Resolved in step 4
- ~~**Q21** field-level restriction list~~ — **answered and built.** Remote/Order Staff's exact
  grants and the three hidden Product fields are seeded and enforced.
- ~~**Q22** auth mechanism~~ — **approved and built.** Server-side sessions, HttpOnly cookie.
- ~~**Q23** reserved section keys~~ — **done.** `SALES_ORDER_FULFILLMENT` and
  `BACK_IN_STOCK_REMINDERS` exist as `Section` values flagged unavailable, so they can be granted
  before the modules exist and a UI can tell "you may not see this" from "this isn't built yet".

### Left over from Q22
- ~~**Q29** password policy~~ — **approved as defaulted.** 12 characters minimum, no composition
  rules, per NIST SP 800-63B. Settled; no further work.
- **Q30** **2FA — decided (no, for now) with a condition. Escalated to a pre-launch blocker: see
  PLB-1 at the top of this file.** Not an open question and not a deferral by default; it has a
  named trigger that must close it.
- **Q31** *(still open)* **Single role per user.** Brief §7's "multiple custom roles from the start"
  was read as the system supporting many role *definitions*, not many roles per person — the
  natural reading for a company this size, and what is built. **Say so if that reading is wrong**;
  it is a schema change, cheap now and much less cheap after step 5.
- **Q32** *(still open)* Session timeout is 8 hours. Reasonable for a working day; confirm or
  change.

### Resolved in step 5 — core entities
- ~~**Q4** VAT class list~~ — resolved and built in step 3b.
- ~~**Step-3b obligation**~~ — **done.** `Product.defaultVatClassId` is required (there is no
  fallback rate, so a product without one could not be invoiced), and
  `Customer.vatClassOverrideId` is nullable. `VatClassPrecedence` now has real stored levels.
- ~~**Step-4 obligation**~~ — **done.** `ProductView.redactedFor(RoleView)`, tested against the
  real seeded role. See the warning in the step 5 section about the `...For` naming convention.
- ~~**Q5**~~, ~~**Q6**~~, ~~**Q8**~~, ~~**Q9**~~, ~~**Q12**~~ — **answered and built.** See above.
- ~~**Q7**~~ — **answered and built in step 6.** Stock per location plus a computed sellable figure;
  sellable is the Inventory location only.
- ~~**Q11**~~ — **answered and built in step 6.** Bundles, to brief §5 in full.
- **Q10** *(still open)* Confirm whether the shared generic "Πελάτης Λιανικής" retail record should
  be seeded. **Not seeded** — a catch-all customer absorbs every unmatched sale and then cannot be
  untangled, so it waits for the answer.
- **Q12 leftover** *(still open)* Is the periodic depreciation **posting run** in Phase 1, or only
  the register and the calculation? The register is built; nothing posts.

### Resolved in step 6 — inventory
- ~~**Step-3 obligation:** the write-off reason field~~ — **fully discharged in step 7.** The enum
  landed in step 6 with no consumer, which was recorded then as the item most likely to be silently
  forgotten; `stock_write_off` is now the transaction that carries it. See the step 7 section.
- ~~**Q25**~~ — **a fixed enum**: `SHRINKAGE` / `DAMAGE` / `EXPIRY` / `OTHER`. Reportability is the
  whole point, and free text would give four spellings of "damaged" as four categories.

### Resolved in step 7 — the ledger
- ~~**Q13**~~ — **answered and built. ADR 0006.** Invoices and credit notes immutable once posted,
  corrected by reversal; receipts, payments, transfers and manual entries editable in place with the
  previous state written to the audit log. Enforced in the database, not only in the service.
  **Approved extension: the inventory write-off is immutable too.** ⚠️ Its second half — editing a
  Receipt/Payment below its allocated total reduces allocations most-recent-first — is a **step 9
  obligation** and is not implemented, because allocations do not exist yet.
- ~~**Q14**~~ — **answered and built. ADR 0007.** Separate Output VAT (liability) and Input VAT
  (asset), never netted; per-line computation summed by rate, which is why a journal line carries its
  VAT class and taxable base; reverse charge as its own path, needing no new structure; exempt lines
  posting no VAT line at all. ⚠️ **Step 9 obligation:** the invoice postings must actually supply the
  VAT dimension, which is optional at the ledger.
- ~~**Q19**~~ — **confirmed.** All six typed transactions are Phase 1. `JournalSource` declares all of
  them plus the credit note and the write-off; six have no producer until steps 8 and 9.
- ~~**Q26**~~ — **answered.** A credit note is its own transaction type, not a negative Sales
  Invoice: it references the original, posts against the existing per-channel `Sales returns`
  account, and is immutable once issued — the same policy as the invoice it corrects.
  `JournalSource.CREDIT_NOTE` exists and carries that policy; the transaction itself is step 9.
- ~~**Q15**~~ — **answered: per document**, as the brief already stated. The recomputation is
  compared against the source document total, not line by line. Nothing built — the comparison
  belongs with the invoice transactions in step 9, and the destination account already exists.
  ⚠️ Still unanswered within Q15: **where a flagged-for-review item lives** — a review queue, or a
  flag on the record.
- ~~**Q16**~~ — **answered: a standalone credit document**, not a bare AR balance adjustment,
  consistent with treating every financial event as a trackable document with its own lifecycle.
  Nothing built; it belongs with open-item matching in step 9.

### Resolved in step 8 — purchasing and FIFO
- ~~**ADR 0004's open item**~~ — **answered and built. ADR 0008.** A purchase price variance account;
  the lot keeps the cost it was received at.
- ~~**Q17**~~ — **answered and built. ADR 0008.** Aggregate stock may go negative; the shortfall is
  recorded, subtracted from the sellable figure, and queryable. No COGS is posted for it and a later
  receipt does not retro-cost it.
- ~~**Q39**~~ — **answered and built. ADR 0008.** A Goods Receipt is immutable; correction is
  `GoodsReceiptService.reverse`, which un-receives the lots and posts the mirror together and refuses
  once they have been touched.

### Still blocking step 10
- **Q18** Landed-cost allocation mutates a lot's unit cost after the fact. If any of that lot is
  already sold, posted COGS is now wrong. Block allocation after consumption, or post an adjustment?
  The brief does not address it. **ADR 0008 constrains the shape of the answer**: allocate against
  the lot's received cost, and do not reach back into consumption already costed out. The mechanism
  is still genuinely open.

### Not blocking anything, but unanswered
- **Q40** **Does a journal entry need a human-facing entry number?** The id is the handle today. An
  accountant asking "what is entry 412" is a real request, and it carries a format decision nobody
  has been asked — per-year reset? a prefix per source? Nothing was guessed. The same question now
  applies to the purchase invoice and goods receipt, which likewise have no NovoCore-facing number:
  an invoice at least carries the *supplier's*, and a delivery may carry their note reference.
- **Q41** *(new)* **After-the-fact GR/IR matching.** A match is made by whichever document is created
  second; nothing matches an existing invoice to an existing delivery later. That leaves a real
  balance sitting in GR/IR — which is exactly what ADR 0004 says a residual means, and phase 8's
  Clearing Checks is what surfaces it. Building it needs an answer to "whose document is the variance
  entry?", so it belongs with those checks rather than with a later step reaching for it casually.

### Blocking phase 8 — Clearing Checks
- **Step-3 obligation:** surface lots aging in the Damaged Goods location. **Step 6 built the query
  it needs** — `InventoryService.lotsAt(DAMAGED_GOODS)` and `unitsAt(DAMAGED_GOODS)`, covering both
  shapes of lot and excluding exhausted ones. The *check* is still phase 8's to write.

### Blocking step 12 — backups
- **Q24** Delivery mechanism: Google Drive API with credentials held by NovoCore, or `rclone` on
  the host? (No Python, per `CLAUDE.md`.) Plus retention policy and whether dumps are encrypted
  at rest. Also need the two actual Drive destinations.

---

## Next action — read this first

**Step 9 (Sales Invoice, Credit Note, Receipt, Payment, Bank Transfer, open-item matching, rounding)
is the next numbered step.** Steps 0–8 are done, committed and pushed.

### Step 9 is not blocked on an answer, and that is a first

Q13, Q14, Q15, Q16, Q19 and Q26 were all answered in step 7, and Q17, Q39 and ADR 0004's open item in
step 8. What step 9 has instead is a **pile of obligations already written down**, which is a
different kind of risk: nothing will stop the work, so nothing will force these to be remembered
either.

1. **Q13's second half is unimplemented.** Editing a Receipt or Payment below its already-allocated
   total must reduce allocations **most-recent-first**. Nothing enforces it because allocations do not
   exist yet. This is the item most easily forgotten — the write-off reason was the same shape and
   took two steps to land.
2. **The invoice postings must supply the VAT dimension.** It is *optional* at the ledger (the
   periodic settlement legitimately has none), so nothing forces a sales invoice to carry it, and a
   VAT return assembled without it would silently understate. The purchase side does this now and is
   the worked example. Exempt lines carry their `VatExemptionReason` on the **invoice line**, which
   makes exempt turnover by reason a document-level report rather than a ledger-level one.
3. **A sale produces two entries.** `InventoryService.consume` posts its own COGS entry — revenue in
   one entry, cost in another, linked by the consumption record. Stated in the step 8 section; it is
   an ordinary arrangement and should not be discovered as a surprise.
4. **Serialized consumption is step 9's.** `consume` refuses a serial-tracked product outright and
   names step 9. Selling an identified unit means marking it `SOLD`, and brief §5 requires the
   customer and invoice recorded on the unit at that point. `SerializedUnitStatus.SOLD` is still
   unreachable, exactly as step 6 left it.
5. **`BundleService.dissolve` on a bundle that has been sold** would strand decomposed component
   lines. Brief §5's "alias forward, never rewrite history" is the shape of the answer.
6. **Q15's remainder is still open** — whether a flagged-for-review item lives in a review queue or as
   a flag on the record. Step 8 deliberately used a **flag plus a query** for Q17's shortfall rather
   than inventing a queue, precisely so this stays an open question rather than being answered by
   accident. Step 9 should settle it properly.
7. **Q16 and Q26 are decisions with nothing built.** Unallocated credit is a standalone credit
   document; a credit note is its own transaction type referencing the invoice it corrects and posting
   to the per-channel `Sales returns` account.

### Waiting on the accountant, and blocking real data rather than code

- **Statutory depreciation rates per asset category**, plus the category taxonomy. The field exists
  and is nullable; **do not create real assets with real values until these are confirmed.**
- **AADE exemption codes 24 and 28** (Q35), **the OSS/IOSS myDATA codes** (Q36), and **the myDATA
  unit-of-measure codes** (Q38) — all before phase 7, all NULL and fail-loud in the meantime.

### Also still open, not blocking step 9

- **Q18 — landed cost after consumption.** Blocks step 10. ADR 0008 constrains the shape of the
  answer without settling the mechanism.
- **Q41 — after-the-fact GR/IR matching.** New in step 8; belongs with phase 8's Clearing Checks.
- **Q28 — dispatch purpose placement.** Recommendation is a core-owned `GoodsDispatch` in phase 4,
  conditional on whether Go already issues Δελτία Αποστολής and whether the AADE digital delivery
  note regime applies (accountant question). Nothing built.
- **Q31 — single role per user.** Cheapest to change now; it has been getting more expensive with
  every entity added since step 5, and step 8 added seven tables.
- **Q32 — the 8-hour session timeout.**
- **Q37 — addresses on Customer and Supplier**, plus human-facing codes and multiple selling prices.
- **Q10 — the generic retail customer.** This one starts to bite in step 9: a retail sale needs
  somebody to be against.
- **Q40 — a human-facing document number**, for journal entries and now for purchase invoices and
  goods receipts too. Nothing was guessed.
- **Q12 leftover — is the periodic depreciation posting run Phase 1 scope**, or only the register and
  the calculation? Step 7 added the `DEPRECIATION_EXPENSE` system key the run would need, and
  deliberately did not build the run. Still waiting on the statutory rates either way.

### Standing note

The REST surface is deliberately still one endpoint — **the ledger, inventory and purchasing all have
no HTTP route at all.** Building out the rest of the API needs its own scoping conversation, not
incremental drift. When it happens, three lower-layer methods must **not** be what a controller
exposes: `JournalService.post` (use `postManualEntry`), `InventoryService.receive` and `unreceive`
(use `GoodsReceiptService`), and `ProductService`'s unredacted reads (use the `...For(viewer)`
variants). **PLB-1 (2FA) must be closed before any remote access is enabled** — including
Remote/Order Staff logging in from outside the local network, which is that role's entire purpose.

