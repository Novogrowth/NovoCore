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
| 6 | Inventory Lot/Unit, Location, computed stock | **Next.** Not started. **Carries two step-3 obligations plus step 5's — see below** |
| 7 | Journal engine, debits=credits invariant | Not started. Blocked on Q13, Q14 |
| 8 | Purchase Invoice, Goods Receipt, GR/IR, FIFO | Not started |
| 9 | Sales Invoice, Receipt, Payment, Bank Transfer, open items, rounding | Not started |
| 10 | Freight / landed cost allocation | Not started. Blocked on Q18 |
| 11 | Email service | Not started. Needs SMTP credentials |
| 12 | Automated backups | Not started. Needs Drive paths/credentials, Q24 |
| 13 | Test suite consolidation sweep | Not started |

**Tests: 370 passing, `mvn clean verify` exit 0.** 112 unit (core-api), 231 core integration,
4 app unit, 12 app integration, 11 architecture. Nothing was failing at the last close-out, and
nothing is failing now.

`mvn test` runs the 127 non-container tests in ~6 seconds and needs no Docker. `mvn verify`
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

Interleaved with these are small docs-only commits (`e25fcee`, `a09428e`, `920044c`, `de16e58`,
`b065901`, `8c27cb4`) and this session's close-out commit.

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

**✅ Q33 settled: a fee's VAT rate is independent of the products on the invoice.** Both charge
types default to 24%, and that is the operative rate rather than a placeholder — a 13% order still
carries 24% delivery. Raised as a possible defect (Greek practice treats an ancillary charge as
following the main supply's rate) and answered explicitly the other way, so **nothing should later
be built to derive a fee's rate from the lines around it.** The per-line override still exists for
a deliberate exception; what is deliberately absent is anything automatic. V7's comment and
`ChargeTypeIT` both state the decision rather than the former limitation.

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
  journal lines**. Consequence, stated plainly: **until step 7 this is a register, not a
  valuation** — the same shape as a product having no stock until lots exist.

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
- **Step 7 — the `Depreciation` expense account has no `AccountSystemKey`.** The two fixed-asset
  control accounts do, but the expense side of a depreciation posting has no stable handle, so a
  posting rule would have to look it up by name. Extending `AccountSystemKey` is deliberately a
  migration; this is the flag that it needs one.
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

- ~~**Q33**~~ — **a fee's VAT rate is independent of the products purchased.** Nothing to build;
  the seeded 24% default is the answer. See the V7 section.
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
- **Q7** *(still open, now blocks step 6)* Stock is not one number: Location lives on the lot and
  sellability depends on stock at a *sellable* location. Confirm the API exposes stock per location
  plus a "sellable" figure. **Nothing about stock was built in step 5** precisely because of this.
- **Q10** *(still open)* Confirm whether the shared generic "Πελάτης Λιανικής" retail record should
  be seeded. **Not seeded** — a catch-all customer absorbs every unmatched sale and then cannot be
  untangled, so it waits for the answer.
- **Q11** *(still open)* **Bundle/Composite products** are in brief §5's core entities but were
  absent from the agreed Phase 1 scope list. Build now or defer? **No bundle flag exists**, so
  answering "build" is a migration plus decomposition logic, not a flag flip.
- **Q12 leftover** *(still open)* Is the periodic depreciation **posting run** in Phase 1, or only
  the register and the calculation? The register is built; nothing posts.

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

## Next action — read this first

**Step 6 (Inventory Lot/Unit, Location, computed stock) is the next numbered step.** Step 5 is done,
committed and pushed.

### Step 6 is blocked on two answers

1. **Q7 — how stock is exposed.** Per location, plus a single "sellable" figure? Nothing about stock
   was built in step 5 because of this, so it is the first thing step 6 needs.
2. **Q25 — is the write-off reason a fixed enum or free text?** A step-3 obligation, not optional:
   with one write-off account instead of three, the shrinkage/damage/expiry distinction has nowhere
   else to live.

**The user will supply answers to Q7, Q25 and Q11 at the start of the next session.** Q11 (bundles)
matters before step 6 rather than after, since a bundle decomposes into component lines for
inventory and COGS.

### Obligations step 6 must honour

- **A reason field on the inventory write-off transaction** (step 3, not optional).
- **`ProductType.SERVICE` must not answer "zero" when asked for stock** — it has no lots, and zero
  is indistinguishable from "not applicable" on a screen.
- **Enforce `UnitOfMeasureView.allowsFractionalQuantity()`** — 2.5 pieces is a data-entry error.
- **`ProductService.changeUnitOfMeasure` must refuse a product that has stock** — reinterpreting 12
  pieces as 12 kilograms is a different quantity, not a units change.

### Waiting on the accountant, and blocking real data rather than code

- **Statutory depreciation rates per asset category**, plus the category taxonomy. The field exists
  and is nullable; **do not create real assets with real values until these are confirmed.**
- **AADE exemption codes 24 and 28** (Q35), **the OSS/IOSS myDATA codes** (Q36), and **the myDATA
  unit-of-measure codes** (Q38) — all before phase 7, all NULL and fail-loud in the meantime.

### Also still open, not blocking step 6

- **Q28 — dispatch purpose placement.** Recommendation is a core-owned `GoodsDispatch` in phase 4,
  conditional on whether Go already issues Δελτία Αποστολής and whether the AADE digital delivery
  note regime applies (accountant question). Nothing built.
- **Q31 — single role per user.** Cheapest to change now; it was already more expensive after step 5
  and gets worse with every entity added.
- **Q32 — the 8-hour session timeout.**
- **Q37 — addresses on Customer and Supplier**, plus human-facing codes and multiple selling prices.
- **Q10 — the generic retail customer**, and **Q13/Q14**, which still block step 7.

### Standing note

The REST surface is deliberately still one endpoint. Building out the rest of the API needs its own
scoping conversation, not incremental drift. **PLB-1 (2FA) must be closed before any remote access
is enabled** — including Remote/Order Staff logging in from outside the local network, which is that
role's entire purpose.
