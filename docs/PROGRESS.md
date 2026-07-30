# NovoCore — Build Progress

*Live status. Overwritten each session close-out, not appended to. Last updated: 2026-07-30.*

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
| 9 | Sales Invoice, Credit Note, Receipt, Payment, Bank Transfer, open items, rounding | **Done, committed** `29e9dcd` — Q10, Q15's remainder, Q16, Q26 answered as **ADR 0009**; Q31 confirmed; all seven obligations discharged, see below |
| 10 | Freight / landed cost allocation | **Done, committed** `cf6f1e4` + `6f06cf8` — Q18 answered as **ADR 0010**, and a defect it introduced closed as **ADR 0011**, see below |
| 11 | Email service | **Done, committed** `b542cf7` + `0790c74` — SMTP credentials supplied and stored in Settings, see below |
| 12 | Automated backups | **Done, and operationally verified 2026-07-29** — V23, ADR 0013. Real encrypted dump uploaded to both Drives; all three owner action items closed |
| 13 | Test suite consolidation sweep | **Done** — property tests on the money types and on FIFO, one whole-scenario invariant sweep, **ADR 0014**, and **one real defect found and then fixed (Q45 / ADR 0015)**, see below |
| 14 | **The REST surface** — 133 routes, and Q44 answered in full | **Done, committed** `423bf34` + `e6354d6` + `b8aa9e2` + `f2e8e06` — three sub-steps as agreed, plus the BundleService follow-up. **Migration V25.** See below |
| 15 | **Dummy data validation** — the API driven end to end over HTTP | **Done** — 15a and 15b complete. **Nine real defects found and fixed**, migration **V26**. Route coverage **128/133 driven, 5 excused with reasons**, asserted rather than reported. See below |
| 16a | **Backend prerequisites for the frontend** — four items agreed before any step 16 work | **Done** — `/me`, preview endpoints, the OpenAPI spec + drift check, and the paging contract. Migration **V27**, plus **session eviction**, a defect found while building the first item. See below |

**Tests: 1188 passing, 0 skipped, `mvn clean verify` exit 0. 137 routes.** Counted from a local run
on this machine, and **0 skipped is new**: the PostgreSQL 17 client tools are now installed here, so
`BackupIT`'s 16 tests and the two backup legs run locally as well as on CI. Previous close-outs
recorded 17 skipping on this machine; that is no longer true and the note has been corrected rather
than carried forward.

Step 16a added 36 (1152 → 1188) and four routes (133 → 137): `GET /api/me`,
`PATCH /api/me/language`, `POST /api/sales-invoices/preview`, `POST /api/credit-notes/preview`.

---

## ▶ Next session — mechanical follow-through, no new design

**Finish tier-A paging across the remaining five services.** The contract is settled and proven on
sales invoices; what is left is the same shape applied again, with no decision outstanding:

| Service | Routes | Sort enum to add |
|---|---|---|
| `PurchaseInvoiceService` | `GET /api/purchase-invoices` | invoice date, supplier number, recorded-at |
| `GoodsReceiptService` | `GET /api/goods-receipts` | receipt date, recorded-at |
| `SettlementService` | `GET /api/settlements` | settlement date, recorded-at |
| `InventoryService` | `GET /api/inventory/lots`, `/consumptions` | acquisition date, recorded-at |
| `EmailSender` | `GET /api/email/outbox` | queued-at, status |

**The recipe, in order, per service:** a `…Sort` enum in `core-api` next to its service interface →
a `page…` method on the interface taking `PageRequest` and returning `PageResponse` → a `Page<E>`
finder on the repository with **no `OrderBy` in the method name** (the ordering comes from the
`Pageable`) → a `SORTABLE` map plus a natural order in the impl, calling `SpringPaging` → the route
gaining `page`/`size`/`sort`/`direction` through `Paging.of`.

**Two things to check for each**, both of which bit on sales invoices:

1. **Every sort key must be a real column.** `GROSS_TOTAL` was removed from `SalesInvoiceSort`
   because an invoice's gross is summed in Java from its lines. The same trap exists on purchase
   invoices, goods receipts and settlements — check before adding the constant, not after.
2. **Regenerate the spec at the end and read the diff.** It should be additions only; a deletion
   means a response shape changed rather than gained a field.

Then `mvn verify -Dnovocore.openapi.write=true`, commit the spec with the change.

Step 15 added 113 in total (1039 → 1152), and the count understates the change twice over:
`WholeScenarioIT`'s 21 invariant tests were **replaced** by 12 shared ones fed to a `@TestFactory`,
and several of the new "tests" are sweeps over all 133 routes reported as one. See the step 15
section.

Step 13 added 94: 53 in `core-api` (the property harness, its own self-test, and properties over
`Money`, `Quantity`, `UnitCost` and `ProportionalAllocation`) and 41 in `core` (12 FIFO properties
against a real database, 21 whole-scenario invariants, and 8 worked examples for the defect the
properties found).

Step 14 added 79: 22 core unit (the money serialisers and the endpoint-declaration check, neither
needing Docker), 14 core integration (Q44's access path, bundle redaction, the section-list
agreement), 39 app integration (four endpoint suites over the real filter chain) and 4 architecture.

**Step 11 introduced the first non-container tests in `core`** (`RetryPolicyTest`,
`SmtpConfigurationTest`). Until now everything in that module needed Docker; these do not, which is
why the `core` row in the count above gained a "unit" half.

`mvn test` runs the non-container tests in ~6 seconds and needs no Docker. `mvn verify`
additionally runs the `*IT` tests under Failsafe against a real PostgreSQL 17 container.

### CI — green, and `BackupIT` now runs for real on it (`5a6dfa5`, 2026-07-29)

The backend workflow was **failing on `mvn verify`** at the start of this session, on
`BackupIT.verifyConfigurationIsHonest`: `pg_dump` reported `16.14` where the test requires 17.

**The cause was a config assumption, not a broken install.** `.github/workflows/backend.yml`
installed `postgresql-client-17` from PGDG and then trusted the bare name `pg_dump` to mean it. It
does not: `ubuntu-latest` already carries PostgreSQL 16, and `/usr/bin/pg_dump` is
`postgresql-common`'s `pg_wrapper`, which selects a version from the local cluster rather than the
newest one installed. So a green install step was followed by a 16 binary.

The fix is to **name the versioned directory** — `/usr/lib/postgresql/17/bin` appended to
`$GITHUB_PATH` — rather than rely on any resolution rule, plus a **separate step that asserts all
three binaries report 17** and fails naming the offender. Separate because `$GITHUB_PATH` only
applies from the next step, and because a recurrence should fail at the install with the cause named
rather than in an integration test two minutes later. **No application code changed**, and the
runner's own PostgreSQL 16 packages were left installed — removing them would fix this build by
breaking anything else on the runner that expects them.

**Verified against the actual environment, not by reasoning.** Run
[30446419236](https://github.com/Novogrowth/NovoCore/actions/runs/30446419236) is `BUILD SUCCESS`,
866 tests, 0 failures, 0 errors, **0 skipped**, and its log reads:

    pg_dump resolves to: /usr/lib/postgresql/17/bin/pg_dump
    which -a pg_dump  →  /usr/lib/postgresql/17/bin/pg_dump
                         /usr/bin/pg_dump          ← the 16 wrapper, still there, now behind
    pg_dump    (PostgreSQL) 17.10 (Ubuntu 17.10-1.pgdg24.04+1)
    pg_restore (PostgreSQL) 17.10 (Ubuntu 17.10-1.pgdg24.04+1)
    psql       (PostgreSQL) 17.10 (Ubuntu 17.10-1.pgdg24.04+1)

**What this buys beyond a green tick: `BackupIT` ran, 16 tests, 0 skipped.** Step 12's restore check
genuinely dumped, created a database, restored into it and asserted the restored ledger balances —
**on CI, with a real PostgreSQL 17 client against a real PostgreSQL 17 server.** The outcome the
workflow's own comment names as the worst available (a green build that silently stopped covering
brief §13's outstanding risk) did not happen, and is now guarded against by the version assertion
rather than by the test alone.

**⚠️ This does not discharge the `docker compose up --build` action item.** What CI proves is that a
17 client dumps and restores a 17 server correctly. The **runtime** image is a different artefact:
`docker/Dockerfile`'s `postgresql-client-17` line is still written and never executed. The risk is
lower — the client/server pairing is no longer an untested assumption — but the Dockerfile edit
itself remains unexercised, and only a real build proves it.

---

## Step 16a — done (the four backend prerequisites)

Four items raised before any frontend foundation work, proposed in
`docs/step-16-backend-prerequisites-proposal.md` and approved item by item. **None touched
`/frontend/`.** All four are complete.

**1. `GET /api/me` + `PATCH /api/me/language` (V27).** Identity, role, **every** `Section` with its
level *and* `isAvailable()` — so a UI can tell "you may not see this" from "this is not built yet",
which `Section` already modelled and nothing exposed. Q47(a): language is a column on `app_user`, not
a Setting, because a preference belongs to a person and must follow them between devices. Nullable
with no default — "has not chosen" is a real answer. Q47(b): **the backend localises nothing**, so
the CHECK constrains the tag's *shape* and names no language; the frontend decides what it offers.
The trigger to revisit is written into V27.

**`@AuthenticatedOnly` rather than a hole in three checks.** `/api/me` is the route that tells a
caller which sections they hold, so gating it behind one is circular. Instead of "unless the path is
/api/me" inside the interceptor, the startup check and the ArchUnit rule, the rule became: **every
`/api/**` handler carries exactly one of `@Requires` or `@AuthenticatedOnly`**, and both is refused
at startup. The set of controllers allowed to use it is asserted **in both directions** — a new one
fails, and a listed one that stops using it also fails — both proven against probes.

**2. Preview endpoints — an extraction, never a second implementation.** `POST
/api/sales-invoices/preview` and `POST /api/credit-notes/preview`. Everything `record`/`issue` worked
out before writing became `compute()`, returning one value both paths consume, so they *cannot*
disagree — asserted by driving one request through both and comparing every line's net, VAT, gross,
class and precedence level. **Proven to fail** against a preview that adds a cent.

Not implemented as post-then-rollback, deliberately: that would burn document numbers, fight the
deferred debits=credits trigger, and **leave real audit entries behind**, since those are written
`REQUIRES_NEW` and are proven to survive a rolled-back caller.

One refusal is *reported* rather than raised — a rounding difference above the threshold with nobody
accepting it. `record` still refuses; preview returns `roundingNeedsAcceptance` with the difference
and the threshold used, because an entry screen has to show the operator the difference and offer the
acceptance **before** they submit, and cannot if asking what the difference is refuses to answer.

The credit note earns its own preview: it credits back **the VAT the sale actually charged**, read
off the invoice line, not what the precedence rule resolves today. A test moves the customer's
override between the sale and the credit and asserts the VAT still matches what was taken.

**3. The OpenAPI spec — springdoc tried, rejected on evidence, generator written.** springdoc 3.0.3
resolves and the app starts, but it pulls **Jackson 2** alongside our Jackson 3 and swagger-core
introspects with the Jackson 2 mapper — so it cannot see `NovoCoreJsonModule` and reflects Java
accessors. It produced `Money.amount` as a **number**, a whole `java.util.Currency` object for the
currency, and `Quantity` as `{value: number, zero, negative, positive}` against a real wire format of
a bare `"3.000000"`. **The probe's output is preserved verbatim in `OpenApiSchema`'s javadoc**,
because the evidence is worth more than the conclusion.

Built instead: `OpenApiSpecIT` walks the same `RequestMappingHandlerMapping` that `RouteCoverage`
already reads. **137 operations, 150 schemas**, in `docs/api/openapi.json`. **An unknown type fails
the build** rather than being guessed — a bare `BigDecimal` is refused by name, pointing at the
`Rate` type. Every operation carries `x-novocore-section` / `x-novocore-level` from its own
`@Requires`, so the permission model ships with the contract. The spec is committed and the build
fails on drift, **proven** by tampering with one `operationId`. `moneyIsAlwaysAString` is a
*separate* test from the drift check, deliberately: drift says the spec matches the code, that says
the spec is not lying about the thing that matters most — a generator can pass the first and fail the
second, which is exactly what springdoc did.

⚠️ **A portability bug found while building it:** Jackson's pretty-printer indents with the *system*
line separator, so the file was CRLF on Windows and would have been LF on the CI runner — the drift
check would then have failed on every build that ran somewhere other than where the file was last
written, reporting a contract change that had not happened. Normalised at generation, plus a
`.gitattributes` rule.

**4. Paging — the contract, and sales invoices as the worked example.** `PageRequest` /
`PageResponse` / `SortDirection` in `core-api`, **not Spring Data's** (ADR 0003); `SpringPaging` is
the one place the framework's paging meets ours. Offset paging **with a total, not a cursor**,
because an accounting table needs "page 7 of 34" and a row count. `ListResponse` gains an *optional*
`page`, absent on an unpaged list — the regenerated spec was **152 insertions and zero deletions**,
which is that backward-compatibility claim checked rather than asserted.

**The ordering is total, and that is the subtle part.** A sort on invoice date leaves rows tied and
PostgreSQL may return tied rows differently per query, so successive pages could show one row twice
and never show another — plausible on screen, wrong. `SpringPaging` appends the id to every ordering
in the sort's own direction, and a test walks a 12-row list four at a time **with all twelve tied on
the sort column**, asserting each was seen exactly once.

Sort keys are a per-endpoint enum, so an unknown value is refused by Spring before our code runs and
the accepted values land in the spec; the service *also* maps names to properties explicitly, which
is the guard that holds if a service is called from elsewhere. `Paging.of` translates
`PageRequest`'s `IllegalArgumentException` into `InvalidRequestException` — `PageRequest` is right to
throw it, and `WebExceptionHandler` is right to discard it, which is precisely the named
anti-pattern; one helper keeps it from being got right in most routes.

⚠️ **`GROSS_TOTAL` had to be removed from the sort enum.** An invoice's gross is not a column —
`grossTotal()` sums its lines in Java. Ordering by it needs a correlated subquery per page or a
stored total that could disagree with the lines. **The same trap exists on purchase invoices, goods
receipts and settlements** — check before adding a constant, not after.

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

## ✅ Closed — a permission change now takes effect immediately (2026-07-30)

**Found while building `/api/me`, fixed the same day by session eviction.** Kept in full below
because the defect is more instructive than the fix, and because the reasoning decides what happens
if NovoCore is ever run as more than one instance.

**Decision: eviction, not a short-lived cache.** A time-boxed refresh of the principal shrinks the
window without closing it, and a window is exactly what must not exist when an account is being cut
off deliberately — "revoked but still working for another minute" is not meaningfully better than
"for another hour" for a departing employee or a compromised account.

**Built:** `UserSessions` in `core-api` (the same seam as `CurrentUser`), implemented by
`NovoCoreSessionRegistry` in `app`. `UserService.deactivate` and `changeRole` both call
`endAllFor(userId)` **inside their own transaction**, so a rolled-back revocation cannot log
somebody out of an account that is still active, and a committed one cannot leave them logged in.
The number of sessions ended is recorded on the audit entry.

**Three design points worth keeping:**

- **Our own registry, not Spring Security's `SessionRegistry`.** The framework's keys its map by the
  *principal object*, so lookup depends on `NovoCorePrincipal` equality — and our principal wraps a
  `UserView` carrying display name, language and the whole resolved role. Any of those changing
  would change the key and orphan the sessions registered under the old one: **eviction would report
  success while ending nothing.** Keying by user id, a long that never changes, removes the question.
- **`UserSessions` is a required constructor argument, not an `ObjectProvider`.** A no-op fallback
  would let the application start with eviction silently doing nothing. It fails to start instead —
  the same stance as the initial-owner bootstrap. The core's *test* context declares a no-op
  explicitly, where the claim that there are no sessions to end is simply true.
- **`HttpSessionEventPublisher` is not optional plumbing.** Without it the registry never learns a
  session ended and its map grows for the life of the process.

**Proven by `SessionEvictionIT`, over real HTTP on sessions that really logged in** — the service
layer and the session disagreeing *was* the defect, so a service-level test would have passed
against the broken version. Five tests: deactivation ends the session on the next request; a role
change ends it so the old grants cannot outlive the move; **every** session of that user ends, not
just one; other users are unaffected; and deactivating somebody who was never logged in is
uneventful. **Four of the five were confirmed to fail** with the eviction call removed.

**⚠️ Residual, stated rather than discovered later: the registry is per-process and in memory.** One
JVM, one self-hosted instance — the assumption the rest of the deployment already makes. Running
NovoCore as more than one instance makes this insufficient and the sessions have to move somewhere
shared.

### The defect as it was

**Pre-existing since step 4, not introduced by the `/api/me` work.**

`CoreAuthenticationProvider` builds a `NovoCorePrincipal` at login holding a **`UserView` snapshot**,
which Spring Security stores in the session. `SecurityContextCurrentUser.find()` returns that
snapshot, and `SectionAccessInterceptor` checks permissions against it. So for the life of a session
— **up to 8 hours** — the following have no effect on a user who is already logged in:

- revoking a section grant, or lowering it from `FULL` to `VIEW`;
- moving a user to a different role;
- deactivating the role, or **deactivating the user**;
- restricting a `ProtectedField` (the mechanism V26 emptied but left intact).

The last two are the ones worth stating plainly: **deactivating an account does not log that account
out**, and the operator doing it has no indication of that. `UserService.deactivate` refuses to
remove the last full-access user, so the system cannot be locked out — but a departing employee's
live session keeps working until it expires.

**How it was found**, which is worth keeping: `GET /api/me` returned the language a `PATCH` had just
set, and then the *next* `GET /api/me` did not. The read-back was coming from the session, not the
database. That is the same failure the whole permission model has, surfacing on the one route where
it is immediately visible.

**`/api/me` was fixed first, on its own.** `MeController.me()` reads the user record fresh
(`users.require(currentUser.require().id())`), because a route whose entire job is reporting current
identity and grants must not report yesterday's. `MeIT.grantsAreReadFreshRatherThanFromTheSession`
asserts it and was **proven to fail** against the snapshot-reading version. That fix stands
independently of the eviction above and is still worth having: eviction ends a session on a
*revocation*, while this keeps `/api/me` honest about every other change — a rename, a language, a
grant that was *widened* rather than removed, none of which end a session.

**What is still true after the fix.** Eviction closes the revocation cases, which are the ones with
a security consequence. It does not make the session's snapshot live: a user whose display name or
grants change in a way that does *not* end their session still carries the old snapshot in
`SectionAccessInterceptor` until they log in again. That is now a correctness wrinkle rather than a
security hole — widening a grant mid-session takes effect at next login — and it is left alone
deliberately, because closing it means a database read on every request and the case for paying that
has not been made.

## ⚠️ To be aware of immediately

1. **`docker/.env` is gitignored and machine-local, and holds exactly three variables**:
   `NOVOCORE_DB_PASSWORD`, `NOVOCORE_SITE_ADDRESS` and `NOVOCORE_BACKUP_ENCRYPTION_KEY`. Every
   one-time bootstrap variable has been removed once consumed — the three from step 11 (see item 3)
   and the four Drive OAuth secrets, which now live in Settings. A fresh clone must run
   `cp docker/.env.example docker/.env` and set `NOVOCORE_DB_PASSWORD`, or nothing starts. This is
   deliberate — there is no fallback password anywhere.

   **The encryption key is the one variable that is not a hand-off and never goes away.** It is read
   on every backup and every restore, and it cannot move into Settings because the `setting` table is
   inside the dump. It **is** recorded in a password manager as of 2026-07-29, which is what makes
   the backups meaningful rather than decorative.

   ⚠️ **The `.env` copy is 43 characters, not the 44 it was generated as** — the trailing `=` base64
   padding was dropped in transit. This is harmless and was checked rather than assumed: both forms
   decode to **byte-identical** 32-byte key material, and Java's `Base64.getDecoder()` accepts
   unpadded input. So a restore works with either copy. Recorded because a future reader comparing
   the password-manager entry against `.env` will notice the difference and should not conclude the
   key was corrupted.
2. **A fresh machine also needs the toolchain**: JDK 25 and a Docker daemon. Maven is not
   required — `backend/mvnw` is committed. `mvn verify` needs Docker for the `*IT` tests;
   `mvn test` does not.
3. **The first Owner account exists: `kostas`, and its password has been rotated once.** Created
   2026-07-28 by `InitialOwnerBootstrap`, then changed the same day through
   `UserService.changePassword` — the real service, so the password policy, the delegating encoder
   and the `user.password-changed` audit entry all applied, exactly as they would if a screen
   existed. **The current password lives only in the chat session that generated it.** There is
   still **no change-password screen**, so rotating it again means the same route: a one-off run of
   `UserService.changePassword` against the live database.
4. **All three one-time bootstrap variables have been removed from `.env`**, having served their
   purpose: `NOVOCORE_SMTP_PASSWORD` (consumed into the `smtp.password` setting) and
   `NOVOCORE_BOOTSTRAP_OWNER_USERNAME` / `_PASSWORD` (consumed into the user table). The app was
   recreated without them and starts clean. **The SMTP password now lives only in the database** —
   changing it means changing the setting, not the environment.
5. **⚠️ `docker/.env` has a UTF-8 BOM and CRLF line endings.** This cost real time: a
   `grep '^NOVOCORE_DB_PASSWORD='` silently matched nothing, because the BOM sits between the start
   of the file and the first key, so the value came back empty and the failure looked like a
   password problem. Docker Compose copes with both. **Anything else reading this file must strip
   the BOM and the CRs** — `sed '1s/^\xEF\xBB\xBF//' docker/.env | tr -d '\r'`.
6. **⚠️ A test cannot be pointed at a non-Testcontainers database just by setting
   `spring.datasource.*`.** `PostgresTestContainerConfiguration` lives in
   `..core.testsupport..`, which is inside the package `CoreTestApplication` component-scans — and
   because that `@ComponentScan` is declared explicitly, it does **not** carry Boot's
   `TypeExcludeFilter`, which is what normally keeps `@TestConfiguration` classes out of a scan.
   So the container bean is registered by scanning, and its `@ServiceConnection` **overrides any
   datasource properties the test sets**.

   Two consequences. First, `AbstractCoreIntegrationTest`'s `@Import` of that configuration is
   **redundant** — the container would be there regardless. Second, and the reason this is a
   warning rather than a curiosity: a test that sets a datasource URL gets a container anyway and
   **reports the URL it asked for while talking to somewhere else**. It cost real time during the
   owner password rotation, where the symptom was an empty user table on a database that
   demonstrably had a user in it. The diagnostic that settles it in one line is
   `SELECT current_database()` — a Testcontainers PostgreSQL answers `test`.

   To genuinely reach another database, declare a boot configuration that excludes
   `..core.testsupport..` from the scan, and put it in the `gr.novotrade.novocoretest` sibling
   package for the reason `CoreTestApplication` documents at length.

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
| `29e9dcd` | Step 9 — sales invoices, credit notes, settlements, bank transfers, open-item matching, rounding, migration V17 |
| `cf6f1e4` | Step 10 — freight / landed cost allocation, the lot's two cost figures, migration V18 (**ADR 0010**) |
| `6f06cf8` | Step 10 (cont.) — stock returning into a re-costed lot, migration V19 (**ADR 0011**) |
| `b542cf7` | Step 11 — the shared email service, outbox, dispatcher, retry, migration V20 |
| `0790c74` | Step 11 (cont.) — the second route into the batch-wide stall, and the credential cleanup |
| `8af7078` | Step 11 (rev.) — an emailed document is referenced, not copied; Q43 answered, Q44's access path decided, migrations V21–V22 (**ADR 0012**) |
| `855643b` | Step 12 — automated backups, encrypted, off-site and proven restorable, migration V23 (**ADR 0013**) |
| `24a3cd7` | Proxy self-invocation made a build failure — `SelfInvocationRulesTest`, and the two real defects it found |
| `a4ec7db` | The audit-log fix proven behaviourally rather than structurally |
| `5a6dfa5` | CI — `pg_dump` made to actually mean 17 on the runner (workflow only, no application code) |
| `e907a9e` | Step 12 commissioned — backups running for real, off-site and proven |
| `9c7ed41` | Step 13 — property-based tests, the whole-scenario invariant sweep, **ADR 0014**, and **Q45** raised |
| `951929f` | Q45 fixed — a lot's movements post the change in its carrying value, migration V24 (**ADR 0015**) |
| `423bf34` | Step 14a — REST foundations (`@Requires`, money as strings, the full error mapping, three architecture rules) and the master-data surface |
| `e6354d6` | Step 14b — purchasing and inventory endpoints |
| `b8aa9e2` | Step 14c — sales, settlements, the outbox, **Q44 in full**, migration V25 |
| `f2e8e06` | Step 14c (cont.) — `BundleService`'s `For` variants, closing the last redaction asymmetry |
| `7c4c2c4` | **`Rate`** — a percentage is a type, not a bare `BigDecimal` (found by step 15a's JSON sweep) |
| `908b226` | **Step 15a** — the validation harness: `LedgerInvariants`, `HttpTransport`, `JsonNumberSweep`, `RouteCoverage` |
| `5bf069c` | Freight `basis` off the wire — a derived accessor, not a record component (found by 15b) |
| `fc217ea` | AR and the open items agree again — three separate causes (found by 15b) |
| `d8c9e77` | **Step 15b (part)** — the trading-quarter narrative, driven entirely over HTTP |
| `6d85c89` | Open items include customer credits, and the invariant now sums signed by type |
| `1421dfb` | Step 15b — the quarter-end review, and the two error-reporting defects it found |
| `b65f7b2` | **Q21 revised** — no field restricted from any role, migration **V26** |
| `1a4b294` | **Step 15b complete** — the refusal matrix, the permission sweep, read-back and date boundaries, restore, `assertEveryRouteCoveredExcept`, and **defects 7, 8 and 9** with the guard the recurrence earned |
| `3158239` | **Step 16a (1)** — `GET /api/me`, `PATCH /api/me/language`, `@AuthenticatedOnly`, migration **V27** |
| `0df73c3` | **Step 16a (2a)** — `POST /api/sales-invoices/preview` |
| `bc0c088` | **Step 16a (2b)** — `POST /api/credit-notes/preview` |
| `fad0d11` | **Session eviction** — revoking access ends the session that holds it (defect found by item 1) |
| `416ca82` | **Step 16a (3)** — the OpenAPI spec, generated from our own serialisers, with the CI drift check |
| `2d37a68` | The generated API contract is LF in the working copy too |
| `8c23e0b` | **Step 16a (4)** — the paging contract, and sales invoices as the worked tier-A example |

Interleaved with these are small docs-only commits (`e25fcee`, `a09428e`, `920044c`, `de16e58`,
`b065901`, `8c27cb4`, `2c3fa8a`, `21b2231`, `d1111d0`, `610f785`, `836a4eb`) and this session's
close-out commit.

**Step 10 is two commits, deliberately.** The convention is one per build step, and `cf6f1e4` is a
complete, green step on its own; `6f06cf8` is a distinct decision found by *reviewing* it, with its
own ADR and its own migration. Folding it in would have buried that story inside another commit's
message, which is the opposite of what the convention is for.

**Step 11 is two commits for the same reason.** `b542cf7` is a complete, green step; `0790c74`
corrects a claim that step made — the poison-pill guard was narrower than its commit message said —
and carries the credential cleanup with it. **This has now happened on two consecutive steps, and
both times the second commit came from reviewing the first rather than from testing it.** Worth
treating as a habit rather than a coincidence: the review pass after a step looks green is earning
its keep.

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
- **Step 10's landed-cost invariants**, by raw-SQL probes in `FreightAllocationIT`: a lot whose two
  cost halves name different currencies, a lot carried below what was paid for it, two allocation
  lines for one lot, and a second reversal of one allocation are all refused by the database.
- **The Inventory control account agrees with what the lots carry**, asserted directly rather than
  inferred — `inventoryLedgerPositionOf` sums the Inventory-side lines for one lot's sub-ledger
  reference and is compared against `remainingValue()` across every shape ADR 0011 examines. This is
  the assertion that found the defect ADR 0011 fixes, and the one that proves it fixed.
- **Step 8's purchasing invariants likewise**, by raw-SQL probes in `PurchaseInvoiceIT`: a duplicate
  supplier invoice number (case-insensitively, by trigger), an invoice line that is neither an
  inventory nor an expense shape, a line stating no VAT treatment, a second GR/IR match for the same
  pair, and a second lot claiming one delivery line. **And the consumption-source CHECK is held to
  `JournalSource.mayConsumeStock()`** the same way `journal_source_is_amendable` is held to
  `isAmendable()` — per value, and by counting the constraint's literals so a value added to the
  database alone cannot hide.
- **The whole system, played as one trading year and then swept** (step 13, `WholeScenarioIT`).
  After buying before and after invoicing, allocating freight onto partly-sold stock, decomposing a
  bundle, overselling, crediting stock back, settling both ways, reversing and writing off: **no
  entry in the database is unbalanced**, asked in raw SQL; the trial balance balances; every control
  account equals the sum of its own sub-ledger; GR/IR, both variance accounts, AR, AP and Inventory
  each agree with the documents behind them; and the whole thing restores into a fresh database and
  still balances there.
- **The value types and FIFO, over generated input rather than chosen examples** (step 13). The
  properties are listed in the step 13 section; the ones worth knowing exist are that `compareTo`
  agrees with `equals` on `Money`, that the currency guard holds on every operation, that
  `ProportionalAllocation` agrees with an independently written largest-remainder split, and that
  FIFO's allocation agrees with an independently written FIFO over twenty random histories.
- **The property runner itself is proven to fail** (`PropertyTest`), the same way the ArchUnit rules
  and `SchemaConventionsIT` are — a checker that is silently broken is worse than no checker.

- **A lot's carrying value and the Inventory control account agree by construction** (ADR 0015),
  at every point in a lot's life and for every unit cost, not only the whole-cent ones — and a fully
  consumed lot leaves exactly zero behind. Asserted both as worked examples with the measured
  numbers (`LotCarryingValueIT`, **proven to fail against the old formula**) and as properties over
  twenty generated histories per run (`FifoPropertiesIT`).

## Not yet verified

- ~~**Backup restore.**~~ **Closed, in code and in operation.** Proven in the suite and on CI
  (`5a6dfa5`) — a real `pg_dump`, a real `pg_restore` into a real scratch database, and an assertion
  that the restored ledger *balances* — and since 2026-07-29 the **whole regime runs for real**: an
  encrypted artefact produced by the deployed container and uploaded to both Google Drive accounts.
  Brief §13's long-standing "restore untested" risk is **closed**. The one part still not
  independently exercised is the **weekly restore check running against a downloaded off-site
  artefact** — the check has only ever restored from the local copy, which is the same file, so this
  is a thin residual rather than a gap.
- ~~**The REST surface is one read-only endpoint.**~~ **Closed — step 14 built 133 routes.**
  ~~**And nothing has driven them as a sequence.**~~ **Closed by step 15**: a trading quarter now runs
  entirely over HTTP and the ledger it produces satisfies every universal invariant. **128 of the 133
  routes are driven, and the remaining 5 are excused in writing** — `assertEveryRouteCoveredExcept`
  makes that an assertion, and it also fails on an excuse for a route that no longer exists or that
  was in fact covered, so the list cannot outlive what it describes.

  What is *still* unverified is narrower than it was, and worth stating precisely: **no human has used
  a browser.** The frontend has no login screen and calls none of this. Every route that runs, runs
  because a test asked it to.
- ~~**Nobody has logged in.**~~ **Done, against the running Compose stack over HTTPS**: 401
  unauthenticated, 204 on login as the new `kostas` owner with a CSRF token, 200 returning the
  full chart of accounts. So the whole path — Caddy, TLS, session cookie, CSRF, the core's own
  password verification, `requireView` — works against real containers and not only in a test.
  **Still true: no human has used a browser**, and **the frontend has no login screen.**
- **The implicit-TLS path is verified by hand, not by the suite.** The automated tests run against
  an in-process SMTP server over plain SMTP, so what they prove about TLS is the property mapping
  (`SmtpConfigurationTest`), not a real handshake. The real 465 path was checked twice by throwaway
  probes that were deleted: one authenticating without sending, and one sending real mail. **A
  change to `SmtpConfiguration`'s TLS properties would not be caught by `mvn verify`** — re-probe
  by hand, or point a test at a TLS-capable server.
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
- ~~**Step 9 — the serialized unit's sale link**~~ — **done in step 9.** `SerializedUnitStatus.SOLD` is declared and
  unreachable: brief §5 wants the customer/invoice link recorded on the unit once sold, and a
  nullable customer id added now would let a unit be marked sold to somebody with no document behind
  it. The stock count is already written against the status column, so it will be right the day a
  unit is sold without anyone revisiting the query. Also step 9's: **`BundleService.dissolve` on a
  bundle that has been sold** would strand decomposed component lines pointing at something that is
  no longer a bundle — brief §5's "alias forward, never rewrite history" is the shape of the answer
  and it needs the ledger.
- ~~**Step 10 — last purchase price must stop coming from the lot**~~ — **done in step 10.** It now
  returns the lot's **received** cost, which ADR 0010 froze for the life of a lot precisely so this
  question has an answer inside the inventory slice. The residual — that the received cost is the last
  price we *believed*, not necessarily the last a supplier *invoiced* (ADR 0008) — is recorded in the
  step 10 section and was deliberately not built around.
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
- ~~**Step 9 — Q13's second half**~~ — **done in step 9** (`SettlementService.amend` releases most-recent-first). Originally: Editing a Receipt or Payment below its
  already-allocated total must reduce allocations **starting with the most recently applied one,
  working backward**. Nothing enforces that yet because allocations do not exist. This is the item
  most easily forgotten, in the same way the write-off reason was.
- ~~**Step 9 — the invoice postings must supply the VAT dimension**~~ — **done in step 9.** It is *optional* at the ledger
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

- ~~**Step 9 — a sales invoice produces two entries, not one**~~ — **as built.** `InventoryService.consume` posts
  its own COGS entry, because reducing lots without posting is the "half is worse than neither"
  problem the write-off settled. So a sale posts revenue in one entry and cost in another, linked by
  the consumption record. That is an ordinary arrangement and it is stated here so step 9 does not
  discover it as a surprise.
- ~~**Step 9 — serialized consumption**~~ — **done in step 9**, with no FIFO and no shortfall. `consume` refuses a serial-tracked product outright, naming
  step 9: selling an identified unit means marking it `SOLD`, and brief §5 requires the customer and
  invoice on it at that point. `SerializedUnitStatus.SOLD` is still unreachable.
- ~~**Step 10 — Q18 is now constrained**~~ — **answered in step 10 as ADR 0010**, within the shape
  this constraint set: allocation is computed against the lot's received cost, and the share belonging
  to stock already costed out goes to `Landed cost variance` rather than back into a posted COGS.
- **Phase 8 — two new checks have their queries.** `consumptionsWithShortfall()` for Q17's flag, and
  `linesAwaitingDelivery()` / `linesAwaitingInvoice()` for the two halves of a non-zero GR/IR
  balance. The checks themselves are still phase 8's to write.
- **The first purchasing controller** must expose the document services and not
  `InventoryService.receive`/`unreceive`, which are the lower layer — the same class of caution as
  `postManualEntry` versus `post` and the `ProductService` `...For(viewer)` convention.

---

## Step 9 — done (sales invoices, credit notes, settlements, open items, rounding)

Commit `29e9dcd`, migration `V17`, and **ADR 0009**. Step 9 was the first step blocked on no
question; what it carried instead was a list of seven obligations recorded across steps 3 to 8.
**All seven are discharged**, and four questions were answered on the way in — Q10, Q15's
remainder, Q16 and Q26 — plus Q31 confirmed with no change needed.

### The one idea the rest of the step leans on

**Open item matching is a layer over Accounts receivable and Accounts payable, not a second ledger
beside them. Documents post; allocations post nothing.** A sales invoice posts, a receipt posts,
and saying which one paid the other would be an entry debiting and crediting the same control
account for the same amount.

Everything unusual about the settlement slice follows from that one line:

- **No document stores an open amount and nothing stores a "paid" flag.** An open amount is gross
  less what has been allocated, computed on every read — the stance that keeps a balance off
  `account` and a stock figure off `product`.
- **An allocation can be reduced or released freely**, which is exactly what makes **Q13's second
  half implementable at all**. Had an allocation posted, every release would need a reversal and a
  corrected receipt would produce a cascade of entries describing bookkeeping rather than money.
- **`release` deletes a row** — the one place in this schema that happens, and right here for the
  reason the rest refuses it: an allocation is not a record of an event, it is a statement about
  the current relationship between two documents. The audit log records it.

### The seven obligations, discharged

1. ~~**Q13's second half**~~ — `SettlementService.amend` releases allocations **most-recent-first**,
   reducing the last one partially when that is enough, every release audit-logged with the
   allocation it touched. Most-recent-first because the earlier allocations are the ones somebody
   deliberately matched. `allocation_order` exists because `created_at` cannot answer "which was
   last" when several are created in one transaction.
2. ~~**The invoice postings must supply the VAT dimension**~~ — every Output VAT line carries its
   class and taxable base, per line and summed by class. Proven by a test that puts two rates on
   one invoice and reads them back separately through `vatTotals`.
3. ~~**A sale produces two entries**~~ — revenue from `SalesInvoiceService`, cost of goods sold from
   `InventoryService.consume`, linked by the consumption the line points at.
4. ~~**Serialized consumption**~~ — a line for a serial-tracked product names its machines. Each is
   costed at **its own lot's cost with no FIFO** (brief §5's explicit exception), marked `SOLD`, and
   carries the buyer and the invoice line. Biconditional by CHECK, so the status cannot be reached
   without the document and the document cannot be recorded on a unit that was not sold.
   **No shortfall on this path**: aggregate stock may go negative because "how many are there" can
   be wrong, but "is machine 1234 on the shelf" cannot be, and nothing a later delivery brings could
   back it. So it refuses.
5. ~~**`BundleService.dissolve` on a sold bundle**~~ — discharged by making it **safe** rather than
   by refusing it. The decomposition is **materialised** on the invoice at the moment of sale, so a
   recorded invoice does not depend on the definition still existing. Brief §5's "alias forward,
   never rewrite history" holds with no alias table, because there is no history to rewrite.
   ⚠️ **The obligation this creates in exchange:** a dual-level revenue report must read
   `SalesInvoiceLineView.components()` and **never** `BundleService.componentsOf`.
6. ~~**Q15's remainder**~~ — answered properly, see below.
7. ~~**Q16 and Q26**~~ — both built, see below.

### Q15's remainder, answered: confirm at entry, record the confirmation, no queue

| difference against the document's stated total | what happens |
|---|---|
| zero | nothing |
| at or below `ledger.rounding.threshold` | posts to `Rounding differences` automatically |
| above the threshold | **the document is refused**, naming both totals, until the caller says who accepts it and why |

Three reasons a queue lost:

1. **A queue is a second copy of state** — created when the condition arises, removed when it is
   resolved, and wrong the day those fall out of step. The same argument this schema has made
   against `normal_balance_side`, stored balances, and a `superseded` flag.
2. **The person who can explain the difference is holding the document**, not whoever opens a queue
   next week. Confirming at entry is rule 7 applied where it works best.
3. **A bare flag loses the resolution**, so the confirmation is stored (`rounding_accepted_by`,
   `_at`, `_note`) and "somebody looked at this" becomes a fact in the data.

`rounding_needed_review` is **stored, not derived**, because the threshold is operator-changeable
and a later change must not retroactively alter which invoices somebody had to agree to.

**The difference always posts**, so Accounts receivable agrees with the document the customer
holds — the one outcome open-item matching cannot survive is disagreeing with it.

**The rule this generalises to:** where the ambiguity is visible at entry, confirm at entry and
record the confirmation; where it is a consequence the operator cannot fix at entry (Q17's
shortfall), flag it on the record and provide a query. **Neither is a queue.**

### Q10, answered: the retail customer is seeded and structural

`customer.system_key`, one value, `RETAIL_WALK_IN`, seeded as "Πελάτης Λιανικής". Step 5's fear was
right and the answer is not "no catch-all" — it is that the alternative is a person creating one by
hand on day one, which produces exactly that row with nothing able to tell which one it is.

- **Findable by machine.** The column is `insertable = false, updatable = false` on the entity, so
  no service path can create a second keyed row — `AccountSystemKey`'s stance.
- **Protected.** Undeactivatable by CHECK (not merely by the service, so it holds against `psql`),
  and **refused on both sides of a merge**. Brief §5's alias-forward is about two records of one
  real party; this is the *absence* of a party, so aliasing it into somebody would attribute every
  anonymous till sale to one named person. Merge is still not built; the rule is recorded on
  `CustomerSystemKey` so whoever builds it consults rather than rediscovers it.
- **VAT treatment fixed** at `DOMESTIC`, no VAT number, no exemption reason — three CHECKs.
- **Not a default.** Nothing falls back to it.

### The sales invoice

- **Recorded, not issued.** Go is the invoicing system of record until phase 11, so
  `document_number` is Go's and `stated_total` is what Go's document says. Immutable (Q13), and
  doubly right here since the document exists outside NovoCore.
- **`SalesChannel` picks the revenue account** — the only place channel exists in the model, which
  is why step 3 split Sales and Sales returns three ways. A `SERVICE` product credits `Services`
  instead; `ProductType` deciding real behaviour, for the third time.
- **`SettlementMethod` picks the debit account** — brief §6's settlement automation. Cash and the
  partner clearing methods debit their own account and the invoice is **born fully settled**;
  `BANK_DEPOSIT` and `ON_ACCOUNT` debit AR and it is an open item. Bank deposit deliberately does
  not settle on entry: a customer saying they transferred is not the money arriving.
- **The cash limit is hard-blocked**, not flagged — the one place in this design where a check
  refuses instead of asking for confirmation, because the confirmation nobody can give is the
  legality of the transaction (N. 5301/2026, penalties to double the cash amount).
- **`vat_class_source` is stored**, so "why is this line at 13%?" stays answerable a year later,
  after the customer's override has changed and the product's default with it.

### The credit note (Q26)

Its own transaction type, referencing the invoice it corrects and each line referencing the invoice
line it credits — which is what supplies the rate, the product and the channel. It debits the
channel's `Sales returns` account, **always credits Accounts receivable** even against a cash sale
(the money is owed back until it is refunded; crediting the till takes out money nobody handed
over), and is immutable once issued.

**The VAT credited is the rate the sale charged**, never re-resolved. A credit note issued after
the customer's override changed would otherwise return VAT at 13% against output collected at 24%.

### A stock return is not a reversal

New in step 9 and worth stating as a distinction rather than a mechanism:

| | reversal | return |
|---|---|---|
| says | this consumption should not have happened | the sale was real and the goods came back |
| quantity | the whole of it | any part |
| how often | at most once, by UNIQUE | as often as goods come back |
| posts | an exact ledger mirror | an ordinary entry, debit Inventory credit COGS |

Both are rows in `stock_consumption`, told apart by which of `reversal_of_id` and
`returns_consumption_id` is set; a CHECK refuses both, and a trigger holds the total returned within
what was taken. Stock returns **at the cost it left at**, read off the consumption's own stored
lines — step 10 will move a lot's unit cost, and returning at a later cost would revalue stock
through a credit note. `reverseConsumption` now refuses a return row outright, because restoring
quantities is the opposite of what un-making a return would need.

**A credit note that restored stock is not reversible** — ADR 0008's principle in the other
direction. The goods are on a shelf, in a lot FIFO may have sold from again. A price-only credit
note reverses normally.

### Receipts and Payments share one table, deliberately

Structurally one thing: money moving between one of our accounts and one counterparty's sub-ledger.
`SettlementDirection` decides the side of the entry and which `JournalSource` it carries, so **Q13's
per-source policy is untouched and the ledger cannot tell**. `PartyType` is a separate dimension
because all four combinations are real — receipt from a customer, payment to a supplier, refund *to*
a customer against a credit note, refund *from* a supplier. Folding them into one enum makes the
last two unrepresentable, which is how a refund gets recorded as a negative receipt.

**When to split them:** the first column that belongs to one and not the other. There is none today.

### Q16, answered: unallocated credit is a standalone document that posts nothing

The money is already in Accounts receivable — the receipt that overpaid put it there.
`customer_credit` says whose it is and that it is available, which is the open-item layer's job.

**It is created only when the caller says so.** A receipt whose allocations come to less than its
amount means either "the customer overpaid" or "nobody has finished matching this remittance", and
guessing between them is what rule 7 forbids. The unmatched case stays queryable
(`withUnallocatedAmount()`), which is brief §6's "unmatched lines flagged for Clearing Checks".

### Thirteen new account system keys, and why a "deliberately small" set grew by that much

The criterion has not moved — a key exists when NovoCore's own posting rules must locate a specific
account at runtime. Step 9 is simply the first step where the software chooses a revenue account
and a settlement account without a person naming one: three channel Sales, three Sales returns,
`Services`, and the six accounts `SettlementMethod` resolves (Cash, three partner clearing, PayPal,
Stripe). **No key for `Cost of service sold`** — nothing computes a service's cost, so no rule
resolves it, and a key is a promise that code which does not exist can reach the account.

### Two new sections

`SALES` and `SETTLEMENTS`, separate from `CUSTOMERS` for the reason `PURCHASING` is separate from
`SUPPLIERS`, and separate from each other because a settlement reaches both sub-ledgers at once. No
grants seeded — default-deny.

### Q31, confirmed: single role per user stays

No change needed to what step 4 built. Confirmed against the current team shape (Owner, Admin, one
operational Remote/Order Staff role); no foreseeable need for anyone to hold two roles. It stops
being the cheap-now/expensive-later item it has been since step 5.

### ⚠️ Obligations this step created

- **A dual-level revenue report reads the stored components, never `componentsOf`.** See obligation
  5 above. This is phase 8's, and it is the one thing that makes materialised decomposition safe.
- **Phase 6 — the Bank Aggregator feeds this open-item layer.** `BANK_DEPOSIT` invoices stay open
  by design until the adapter confirms the matching incoming transaction; `withUnallocatedAmount()`
  is what auto-matching will read and reduce.
- **Phase 8 — three more checks have their queries.** `withAcceptedRoundingDifference` (Q15's
  flag), `withUnallocatedAmount` (unmatched remittances), and `openItemsFor` (aged debtors).
- **The first sales controller** must expose the document services and not
  `InventoryService.consume`/`returnConsumed`, which are the lower layer — the same class of caution
  as `postManualEntry` versus `post`.

### Two defects the tests caught, both fixed at the root

Recorded because the pattern matters more than the incidents, and both were found by tests written
against the intended behaviour rather than against the code.

1. **A return into a serial-tracked lot tried to restore a quantity.** A serial-tracked lot stores
   none — the quantity *is* the count of its on-hand units (V12) — so `restore` threw. The fix is
   the rule stated where it belongs: for a serial-tracked lot the stock comes back by the units
   changing status, and restoring a number there would be a second copy of what the units say.
2. **A credit note's open amount ignored what it had been spent on.** A credit note is the one open
   item settleable from *either* side — a target when a refund pays it out, a source when it is set
   against an invoice — and counting only allocations *against* it would have let the same credit be
   both refunded in cash and applied to an invoice, each half looking correct alone.

---

## Step 10 — done (freight / landed cost allocation)

Commits `cf6f1e4` and `6f06cf8`, migrations `V18` and `V19`, and **ADR 0010** and **ADR 0011**.
Q18 — the only question that had been blocking a numbered step — is answered, and a defect the answer
introduced was found by review and closed in the same session.

### Q18, answered: a lot's share is split by what is still in the lot

Brief §4 allocates freight and duty out of `Freight / Landed Cost — Unallocated` into the lots they
delivered, proportionally by value. V4 created that account and flagged it `expected_to_clear`; step 8
gave a carrier's invoice somewhere to land. **Nothing had ever cleared it.** This is what does.

| the part of a lot's share belonging to | goes |
|---|---|
| stock **still on hand** | onto that lot's unit cost, normally |
| stock **already gone** | to a new `Landed cost variance` account |

The second row is the whole of Q18. That share cannot raise a unit cost, because the units it is about
are not in the lot; and it cannot be added to the cost of goods sold that took them out, because that
entry is immutable (ADR 0006) and re-costing consumption already costed out is what ADR 0008 refuses.
So it posts openly, to the exact counterpart of `Purchase price variance`, one position along in the
same group and not `expected_to_clear` for the same reason.

**A fully-sold lot may still be named**, and all of its share goes to variance. That is the case Q18
exists for; refusing it would leave real freight sitting in an expected-to-clear account forever.

### A lot's cost is now two figures, and the received half is frozen

`inventory_lot.unit_cost` became `received_unit_cost` and **stops changing**;
`allocated_landed_unit_cost` accumulates beside it; the carrying cost is their sum, computed on read
and deliberately not a third column.

The freezing is load-bearing rather than tidy. If allocation were computed against the *carrying* cost,
the first freight invoice would move the proportions the second one divides by, so two invoices
covering one shipment would split differently depending on the order they were entered in — and nobody
would ever see that happen, they would simply have costs that could not be reproduced. It also makes
each allocation's basis recomputable from frozen inputs, which is why `freight_allocation_line` stores
no basis column.

`goods_receipt_line.unit_cost` is untouched and is **not** the same fact: that is what a delivery
document said and one half of every GR/IR variance, while this is what a lot was opened at. They
coincide for every lot a delivery created and cannot for phase 2b's opening stock — which is exactly
the case a rule reading the delivery document instead would have no answer for.

### What an allocation is allocated out of

A **purchase invoice expense line pointed at `Freight / Landed Cost — Unallocated`**, not a bare
amount. Naming the source is what makes "how much of this freight is unallocated" answerable and what
stops the account being credited below what was debited into it — the GR/IR match's shape. One source
line per allocation, so a multi-line freight invoice keeps a remainder per line; many lots across many
purchase invoices, which is the consolidated-shipment case; and the lots are always **named**, never
inferred from suppliers and dates (rule 7).

Refused rather than resolved: a line booked to another account, an invoice that has been reversed, more
than the line charged, a lot received at zero cost (proportional-by-value gives it no share), a lot from
a reversed Goods Receipt, and any currency mismatch.

### ⚠️ ADR 0011 — the asymmetry between returns and reversals

**Found by review, measured rather than reasoned about.** The review question was whether reversing an
allocation on a lot with intervening sale-then-return activity could produce a wrong figure. It cannot,
and the reason matters: a sale stores the cost it took stock out at, a return reads that same figure
back (ADR 0009), so the pair nets to zero in both Inventory and COGS and there is no freight left inside
a posted COGS to be inconsistent with. Reversal is also value-neutral on the relationship, because its
guard forces `remaining` to equal `remainingAtAllocation` and the mirror credit equals the `capitalised`
computed from that same figure.

**But probing it found a real defect next door, needing no reversal at all.** Three operations put stock
back into a lot, all at the cost it left at; if an allocation landed while the stock was out, the lot
carries those units higher than what was debited back. Measured on a lot of 10 at €10 with 2 sold and
€100 allocated: lot valuation €200, Inventory €180, on all three paths, never clearing.

The fix is deliberately **not uniform**, and the asymmetry is the part to remember:

- **A return catches up.** It says the sale was real, so the allocation's split was right at the time
  and only the returning units' share is owed. Debit Inventory, credit `Landed cost variance`, in the
  same entry, computed from the consumption line's stored cost against the lot's frozen received cost —
  so nothing new is stored and it is zero whenever the lot has not moved. COGS is still credited exactly
  what was debited, so ADR 0009 stands. Refusing was available and rejected: a return is driven by a
  credit note at the till, and making it wait on a freight document ends in returns going unrecorded.
- **A reversal refuses.** It says the movement never happened — which would mean the allocation computed
  the wrong *split*, not merely posted its counterpart to the wrong account. `reverseConsumption` and
  `reverseWriteOff` refuse once the lot has been re-costed, naming the remedy: reverse the allocation
  (permitted, the quantity has not moved), reverse the movement, allocate again. **A test walks that
  sequence and checks the end state is exactly right**, because a refusal whose named remedy has never
  been tried is a refusal that might not have one.

**V19 exists because of this:** `stock_write_off` now stores its unit cost. V15 deliberately stored no
amount, on the argument that the entry is the honest source — true, and it stopped being sufficient the
day a lot's carrying cost could move, because the entry gives the rounded amount and not the six-decimal
cost behind it. `stock_consumption_line` has stored its own since step 8 for exactly this reason. The
posted **amount** is still not stored: a historical input has to be kept once it stops being
recoverable, a historical output was always in the entry.

### Obligations discharged this step

- ~~**Step 6 — last purchase price must stop coming from the lot's unit cost**~~ — `lastPurchaseCostOf`
  returns the **received** cost, so a product no longer reads as dearer because its last delivery came
  by air. **One residual difference is recorded rather than hidden:** where a delivery preceded its
  invoice and the invoice disagreed, ADR 0008 keeps the lot at the received cost, so this is the last
  price we *believed* rather than necessarily the last a supplier *invoiced*. Making it the latter needs
  a purchasing-side query — the inventory slice cannot read purchasing, which depends on it — and that
  was **not built**.
- ~~**`Freight / Landed Cost — Unallocated` has a key and nothing clears it**~~ — cleared now, and
  `linesAwaitingAllocation()` is the query phase 8 reads against its balance.

### Code-quality work folded in, both flagged rather than done quietly

1. **`BundleAllocation` became `ProportionalAllocation` in `core-api/shared`.** Freight allocation is
   the same arithmetic, and a second copy would have been a second set of rounding behaviour — the kind
   of difference that surfaces later as a report a cent out with nothing to say which half is wrong. One
   production call site and one test moved with it.
2. **`PurchaseInvoiceLineViews` extracted**, because a second service in the slice now projects invoice
   lines and two private copies of one projection diverge the first time a field is added to either.

### ⚠️ Obligations this step created

- **Phase 8 — one more check has its query.** `linesAwaitingAllocation()` against the
  `Freight / Landed Cost — Unallocated` balance, the same shape as the two GR/IR halves.
  `InventoryService.lotsWithAllocatedLandedCost()` answers "which stock is carrying freight" when a
  valuation comes out above the invoices behind it.
- **The first purchasing controller** must expose `FreightAllocationService` and **not**
  `InventoryService.applyLandedCost` / `removeLandedCost`, which are the lower layer — the same class of
  caution as `postManualEntry` versus `post`. Those two move a lot's cost with no entry behind them.
- **A stock valuation report should reconcile against the Inventory control account**, which is now a
  real check rather than a tautology: ADR 0011 made them agree, and the test that asserts it is the one
  that found the defect.

---

## Step 11 — done (the shared email service)

Commit `b542cf7`, migration `V20`. Credentials and both configuration decisions were supplied at
the start of the session, so this was built to the answers rather than around them.

### The two decisions, as built

- **All email configuration lives in Settings**, including the password — decided deliberately
  against the environment-variable alternative the last close-out raised. The exposure argument
  for the environment was that Settings sits inside the backup and step 12 copies backups to
  Google Drive; that does not apply, because access to that Drive is scoped to one person.
- **The sending address is `erp@novotrade.gr` and is unmonitored**, so **`smtp.reply-to` is
  `kostas@novotrade.gr` and applied to every outgoing message**. This is the whole point of the
  step, and it is why the setting is **required rather than optional**: treating it as optional
  would mean a missing value quietly routing every customer reply into a mailbox nobody opens — a
  failure with no symptom.

### The interface, and what it deliberately cannot do

`EmailSender` is the single door. `EmailMessage` **has no `from` and no `replyTo` field**, so a
caller cannot choose either; both are resolved from Settings at send time and applied identically.
A caller that could override them would be able to send as something else and route replies
somewhere unread, which is exactly the scattered-configuration failure `CLAUDE.md`'s shared-service
rule exists to prevent. A test asserts those record components do not exist.

**An ArchUnit rule confines `jakarta.mail` and `org.springframework.mail` to
`gr.novotrade.novocore.core.email`**, so "never configure SMTP or send email directly from within a
module" is a build failure rather than a convention. **Proven to fail**, the same way every other
guardrail here was: a probe class in `..core.settings..` holding a `JavaMailSenderImpl` tripped it,
naming the field, the constructor call and the return type. Probe deleted.

### Asynchronous by an outbox table, per rule 4

`send` writes a row **in the caller's transaction** and returns; it never opens a socket. A
scheduled dispatcher does the SMTP conversation with **no transaction open across the network**.

The transaction detail is the reason it is a table and not an in-memory queue: a message is queued
**if and only if** the operation that queued it committed. An approved Purchase Order always sends
its PDF; a rolled-back one never does, with no compensating logic anywhere. An in-memory queue gets
both wrong in opposite directions. A test rolls back a transaction around `send` and asserts no row
survives.

- **Three transactions per message, never one**: claim (commits before any socket opens), send,
  record. `EmailOutbox` is a separate bean from `EmailDispatcher` for a concrete reason, not
  tidiness — a `@Transactional` method called from another method of the same object bypasses the
  proxy entirely, so the annotations would have done nothing.
- **The claim uses `FOR UPDATE SKIP LOCKED`.** Currently belt and braces: one instance, one
  scheduler thread, `fixedDelay`. It costs a clause and prevents sending the same email twice the
  first time somebody runs two instances during a migration.
- **A crash between the server accepting a message and the row being marked sent produces a
  duplicate on retry.** Stated rather than hidden. It is the right direction to fail — a
  confirmation arriving twice is a nuisance, never arriving is a lost order — and avoiding it would
  mean holding a transaction open across a network conversation.
- **Scheduling is enabled in `app`, not in the core.** So the core's tests hold a fully wired
  dispatcher that never fires on its own and is driven by calling it. Nothing in the email tests
  sleeps, and the retry assertions are exact rather than approximate.

### Retry, and giving up in public

Exponential backoff doubling from 30s to a 15-minute ceiling, 5 attempts — roughly eight minutes,
which covers a mail server restart without leaving a genuinely undeliverable message retrying all
day. `max_attempts` is **copied onto the row at queue time**, so lowering the limit cannot
retroactively fail messages already waiting.

- **A message that runs out of attempts is `FAILED`, kept and queryable**, never deleted and never
  left `PENDING` forever (rule 8). A CHECK refuses a `FAILED` row with no stated reason — the row
  this table exists to prevent is a silent drop wearing a status.
- **Re-queueing is manual.** Nothing retries a failed message automatically, because it failed for
  a reason that is still true; automatic re-queueing turns that into a loop that hides the problem.
  `retry` resets the attempt count rather than granting one more, so a fix can actually be
  confirmed.
- **Only two failures are treated as permanent**: an address the server rejected outright, and a
  message that could not be constructed. Authentication rejection is *not* — a wrong password would
  otherwise mark a whole backlog `FAILED` the moment a password expired, turning one transient
  problem into dozens needing individual attention. The attempt limit surfaces it within minutes
  anyway.
- **An unconfigured system consumes no attempts at all.** Nothing is claimed, so the moment the
  configuration is corrected everything waiting goes out. The dispatcher logs the problem at WARN
  when it changes and DEBUG thereafter, so a system waiting for its password does not write a
  warning every fifteen seconds until the log becomes the thing nobody reads.

### `smtp.start-tls` replaced by `smtp.transport-security`

Step 2 declared a boolean `smtp.start-tls` and nothing ever wrote a value under it, so renaming
cost nothing. **A boolean has two states and there are three**, and the two encrypted ones are not
interchangeable: our server is implicit TLS on 465, and a STARTTLS client pointed at that port
**hangs rather than failing**, so the symptom is a timeout minutes later rather than a refusal.
That is why the property mapping has its own test.

Also stated explicitly rather than relied upon: hostname verification on, TLS 1.2/1.3 only,
STARTTLS **required** and not merely enabled (with only `enable`, a server that declines the
upgrade receives the password in the clear and the send still reports success), and **finite
network timeouts in every mode** — Jakarta Mail's own defaults are infinite, so one hung server
would otherwise block the dispatcher thread permanently with the outbox showing nothing wrong.

### The password is not in git, and that is not the same as not being in Settings

V20 seeds host, port, transport security, username, sender address, sender name, Reply-To and the
four retry settings. **It does not seed the password.** A migration is a file in git; a credential
in git is in git permanently, readable by anyone who ever clones the repository, present in every
CI checkout, and not removable by editing the file.

So the password reaches Settings once from `NOVOCORE_SMTP_PASSWORD` — the same route
`NOVOCORE_BOOTSTRAP_OWNER_PASSWORD` takes — and the variable can then be removed. **The decision
that it lives in Settings is unaffected: the environment is how it arrives, not where it is kept.**
`SmtpPasswordBootstrap` differs from `InitialOwnerBootstrap` in two deliberate ways: a missing
value **does not stop the application** (an instance that cannot send email is entirely usable,
unlike one nobody can log into), and a value that is set but ignored **is logged**, because
somebody editing `.env` to change a password and finding authentication still failing needs to be
told where the value actually lives.

A test asserts that **no migration file anywhere inserts `smtp.password`** — checked against the
files rather than the table, because "is it seeded?" is a question about what is committed, and the
live table is written to by the email service's own tests.

### Two defects found by tests, not by reasoning

Both are recorded because both were invisible to inspection and are the kind that come back.

1. **`EmailAttachment` sanitised the filename before checking for a line break.** Given
   `june.pdf\r\nContent-Type: text/html`, the directory strip ran to the last `/` — the one *inside
   the injected header* — leaving `html`, a name with no line break in it and nothing left to
   refuse. **Sanitising first can destroy the evidence that a value should have been rejected
   outright.** The check now runs on the raw input.
2. **One unusable outbox row stopped all email indefinitely.** Materialising the claimed batch threw
   inside the claim transaction, so the whole transaction aborted, nothing in the batch was sent,
   and the next cycle claimed the same batch and failed identically — with no message of its own
   ever marked failed. Found because a raw-SQL probe left exactly such a row behind and nine
   unrelated tests went red.

   **The first fix was narrower than its commit message claimed, and a review found a second door
   into the same stall.** Recorded in full below, because "we fixed the poison pill" is exactly the
   kind of half-true note that stops the next person looking.

### The batch-wide stall, and what actually closes it

Worth stating precisely, because the failure is severe (all email stops, silently, forever) and
reachable by more than one route.

**What the guard covers.** Everything thrown while rebuilding a stored row into an `EmailMessage`
— which is a *class* of failure, not the one case that was reproduced: any invalid `to`, `cc` or
`bcc` address, a blank or over-long subject, a null body, a bad attachment filename, an empty
attachment. A test now stores three differently-malformed rows in one batch (no `@`, no domain
suffix, and a bad address in `cc` rather than `to`), each of which passes every database CHECK, and
asserts all three fail individually while a healthy message in the same batch still goes out.

**The second door, found by review rather than by a test going red.** `attemptStarting` increments
`attempts`, and the schema has `CHECK (attempts <= max_attempts)`. A row sitting `PENDING` with
`attempts` already equal to `max_attempts` **satisfies every constraint at rest** — the service
cannot produce one, since `markAttemptFailed` flips to `FAILED` at the limit and `requeue` resets to
zero, but raw SQL or a restore can. Claiming such a row emits an `UPDATE` that violates the CHECK,
and **that violation lands at flush, when the transaction commits** — after the loop, outside any
`try`, and unrecoverable. It rolls back the entire claim transaction *including the
`markAttemptFailed` writes the catch block had just made: the same batch-wide stall, reached through
a door a `try` around the conversion cannot close, and this time not even leaving a record of which
row caused it.*

**Why prevention rather than catching.** A failed flush poisons the persistence context, so there is
no per-message recovery available once the invalid `UPDATE` has been queued. The fix is a guard
*before* the increment, so the invalid statement is never emitted. Its own test stores exactly such a
row and asserts the healthy message in the batch still sends.

**Known residual, not defended.** A row that cannot be *loaded* as an entity at all still aborts the
batch, because `findAllById` runs before anything can be guarded. In practice that needs a `status`
or `body_format` value the deployed Java does not know — the CHECK constraints make that impossible
within one version, so the realistic route is **deploying a version that adds an enum value, writing
rows with it, then rolling back to the older jar**. Defending it would mean claiming through a native
projection instead of the entity, which is a real cost against a narrow risk. Recorded so the choice
is visible; **if an enum value is ever added to `EmailStatus` or `EmailBodyFormat`, a downgrade is
not safe.**

### Test hygiene worth keeping

- The email tests **empty the outbox** before each test and **restore every setting they
  overwrite** afterwards. These integration tests share one database and are deliberately not
  transactional, and settings are global by nature — the suite's usual "use distinct keys" advice
  has no equivalent. Without the first, a later test's batch picks up messages earlier tests queued
  on purpose and every "this cycle sent exactly one" assertion silently becomes a statement about
  the whole class's history. That is how one test first failed, reporting three sent instead of one.
- **A raw-SQL probe written as a `'{...}'` array literal proved nothing.** PostgreSQL's array-literal
  parser treats a backslash as an escape, so the intended `\n` became a plain `n`, the CHECK had
  nothing to object to, and the row went in. Bind the value as a parameter and build the array with
  `ARRAY[?]`.
- **A test that sets a datasource URL still gets a Testcontainers database.** See item 6 of "To be
  aware of immediately" — the container configuration is picked up by component scanning, not by
  the `@Import` that appears to control it, and `@ServiceConnection` then overrides the properties.
  Worth knowing before writing any test meant to run against something other than a throwaway
  container.

### Verified by hand, beyond the suite

- **V20 applies on the Compose stack**, and `SmtpPasswordBootstrap` was observed in **both**
  branches: storing the password on the first start, then reporting the variable as ignored on the
  next.
- **The real credentials authenticate against `mail.novotrade.gr:465`** over implicit TLS with
  hostname verification on.
- **Two real emails were sent to `kostas@novotrade.gr`** through the full production path — queue,
  dispatcher, real SMTP — carrying Greek text and an attachment. Two rather than one because
  `-Dtest=` made Surefire run the throwaway probe as well as Failsafe. Both probes were deleted.

### Not built, deliberately

- **No HTTP route.** The outbox, the failure list and `verifyConfiguration` all have services and no
  controller, consistent with everything since step 4b. `verifyConfiguration` exists specifically so
  a Settings screen can answer "is email working?" honestly, and is waiting for that screen.
- **No templates, no HTML layout, no localisation.** `EmailMessage` carries a subject and a body,
  and whichever module sends something composes it. The first module with a real template is where
  that decision belongs.
- **No retention policy on sent messages.** Rows accumulate. Worth revisiting alongside step 12.
  **Narrowed by V21** — see below: the growth is now only the inline attachment bytes, and pruning
  them needs no schema change.
- **No `Section` for the outbox.** Nothing reads it over HTTP yet, and a permission guarding
  nothing is a half-built feature.

---

## Step 11, revisited — an emailed document is referenced, not copied (V21, ADR 0012)

Raised as a design question after step 11 landed: does `EmailAttachment` store its own copy of the
bytes, or reference an existing `AttachmentService` record?

**It duplicated.** `email_outbox_attachment.content` was `bytea NOT NULL`, with no link to
`attachment`. V20 said so deliberately and gave reasons that were half right — a generated Purchase
Order PDF and a monthly report genuinely have nothing to reference. What that reasoning missed is the
case that costs the most: a document that is **also** an `AttachmentService` record, where the same
file then sat in two tables and in every `pg_dump`, permanently. **Full reasoning in ADR 0012.**

- **Two shapes, exactly one per row.** `EmailAttachment.stored(id)` references a document and carries
  no bytes; `.pdf(...)` / `.of(...)` carry bytes for a file that exists nowhere else. Enforced by the
  record's constructor **and** by CHECK constraints, so raw SQL cannot write a row that is both or
  neither. Reference-only was considered and rejected: it would move report bytes rather than save
  them, and fill a "documents on core records" table with things that are neither.
- **The recipient is unaffected.** SMTP transmits real bytes either way; the dispatcher resolves a
  reference at send time and `compose` never sees the distinction.
- **Viewing is one action, identical for both shapes.** `attachmentsOf(emailId)` then
  `downloadAttachment(attachmentId)` — same id, same return type, no join for the caller and nothing
  to know about where the file lives. It stays one action if a file that is inline today becomes a
  stored document tomorrow.
- **A deleted document degrades, it does not break.** `ON DELETE SET NULL` — not CASCADE (which would
  delete the record that the message ever had an attachment) and not RESTRICT (which would let a mail
  from 2026 pin a document forever). The history entry still names the file, its size and its
  checksum, and reports it unavailable **with the reason**. Availability needs no extra query: a
  non-null `attachment_id` is itself the proof. Asking for the bytes anyway throws
  `EmailAttachmentUnavailableException`, deliberately distinct from the `IllegalArgumentException` for
  an id that never existed.
- **A document deleted *before* the message goes out fails it visibly and alone**, through the same
  per-message guard that isolates a poison row. A mail is never sent with an attachment silently
  missing — the one failure a recipient could not detect.
- **Validated at queue time, in the caller's transaction.** An attachment id naming nothing is a
  mistake in the calling code, so it fails the operation that made it. Deliberately unlike the SMTP
  configuration, which is *not* checked at queue time.
- **`content_source` is stored, not inferred.** Once the bytes are gone both shapes are a row with
  nothing in it; without the column the history could not say whether a document was deleted or an
  inline copy pruned. Same reasoning as step 9 storing `vat_class_source`.
- **One defect found by reviewing the tests rather than the code:** two of the raw-SQL CHECK probes
  violated *two* constraints at once, and PostgreSQL does not promise which it reports — the
  assertions would have passed or failed on constraint evaluation order. Each probe now breaks
  exactly one, with a well-formed row inserted afterwards to prove the statement shape itself is good.

Docker Desktop was not running at the start of this session — every IT failed with "Could not find a
valid Docker environment", which is worth recognising quickly since it looks like a mass failure of
the code under test.

---

## Q43 — answered and built (V22): rows forever, generated attachments 90 days

One question until V21, two afterwards. While the outbox copied every attachment, "how long do we keep
sent emails?" covered cheap metadata and expensive duplicated bytes together and could not have one
right answer. Referencing separated them, and the two halves took different numbers.

| | Setting | Answer |
|---|---|---|
| Message rows (recipients, subject, status, error, attachment metadata) | `email.retention.message-days` | `FOREVER` |
| Inline copies of **generated** attachments (PO PDFs, reports) | `email.retention.inline-attachment-days` | `90` |

Both live in **Settings**, changeable without a redeploy — the same argument that put SMTP there.

- **`EmailRetention` runs daily** (`@Scheduled` cron, default 03:30). Scheduling is enabled in `app`,
  not the core, so the core's tests drive it by calling `pruneNow()` and nothing sleeps — the same
  arrangement as `EmailDispatcher`.
- **The `UPDATE` is one statement; the guards are the substance.** A prune that removes too much is the
  failure that matters, and there are three ways to get it wrong. Each is a restriction with a test:
  - **`content_source = 'INLINE'`** — a referenced document's bytes belong to `AttachmentService` and
    are **never** pruned here. Widening this would make one service delete another's documents, with
    the symptom being a purchase invoice's PDF vanishing off the invoice because an email mentioned it
    91 days ago. Tested directly: a referenced attachment survives a prune that drops an inline one
    beside it, and the document is still readable afterwards.
  - **`status = 'SENT'`** — a **PENDING** message still needs its bytes (a system waiting months on a
    broken SMTP password must not have its attachments removed from under it), and a **FAILED** one
    keeps them because retrying it is the entire reason it was kept. A retry that cannot re-send the
    attachment is not a retry.
  - **`content IS NOT NULL`** — keeps it idempotent, so the daily run reports zero instead of
    rewriting rows it already cleared and filling the audit log with noise.
- **The state it produces was already built in V21** and needed no schema change, exactly as predicted:
  the history entry keeps the filename and size and reports the file unavailable, distinguished from a
  deleted document by `content_source`.
- **An unreadable setting stops the prune and deletes nothing**, loudly. The only setting in this
  service with no safe default — guessing "0 days" would delete everything and no logging would undo
  it. `FOREVER` is spelled out rather than encoded as blank or `0`, because a blank setting is
  indistinguishable from one deleted by accident.
- **Row deletion is built although it never runs** under `FOREVER`, so the setting is real rather than
  decorative. Attachment rows follow by `ON DELETE CASCADE`. This is a legitimate deletion:
  `CLAUDE.md`'s no-delete stance governs records people rely on, and a retention policy set
  deliberately is the opposite of an accidental loss.

**822 tests passing, `mvn clean verify` exit 0** (up from 802 at step 11's close: +13 for V21, +7 for
V22 retention).

---

## Step 12 — done (automated backups), V23, ADR 0013

A scheduled encrypted `pg_dump`, copied to two Google Drive accounts, pruned on the stated retention
rule, and **proved restorable** — which closes brief §13's "backup restore test" risk rather than
deferring it again. **Full reasoning in ADR 0013.**

### The encryption key is an environment variable, and cannot be anything else

`NOVOCORE_BACKUP_ENCRYPTION_KEY`, AES-256-GCM, applied before a single byte leaves the host. This is
the **opposite** of step 11's decision for the SMTP password and is not a reversal: **the `setting`
table is inside the dump**, so a key kept there would be encrypted inside the artefact it exists to
decrypt. There is no ordering of those steps that terminates.

**⚠️ The obligation this creates:** that key must be recorded **outside this system** — a password
manager. `docker/.env` is gitignored and machine-local, so if it exists only there, losing the host
loses the database *and* every backup of it at once. The app logs this on every start.

A 16-hex-character **fingerprint** is recorded per artefact, so restoring with a rotated key reports
"this is a key rotation" instead of a GCM tag failure, which reads as "your backup is corrupt".

### Off-site is reported separately from success — the headline that matters

`SUCCEEDED` means the artefact was written and checksummed. Whether a copy reached Drive is
per-destination (`backup_upload`), summarised by `BackupView.isOffsite()`. A dump that wrote to local
disk and reached nowhere protects against a dropped table and against nothing else; the service logs
an **error** for exactly that state.

- **An upload failure does not fail the backup** — the artefact is already safe, and discarding a
  good backup over a network error would also make "when did we last dump successfully?"
  unanswerable.
- **`NOT_CONFIGURED` is its own status.** Never set up needs a different response from tried and
  rejected, and every run records a row per destination so a missing off-site copy is visible rather
  than absent.
- **One destination failing never stops the other.**

### Retention, exactly as specified

**7 most recent successful backups, rolling, plus the last successful backup of each calendar month,
forever, uncapped.** Stated positively it needs no month-end logic: *a backup is its month's archive
iff no later successful backup exists in the same calendar month.* A month whose 31st failed archives
the 30th's; a month with no successful backup designates nothing.

- **The calendar zone (`Europe/Athens`) is load-bearing** — 01:30 on the 1st in Athens is the
  previous month in UTC, which would archive the wrong artefact twelve times a year, silently.
- **Only successful runs are candidates** — letting failures fill the rolling seven would evict the
  last good backups during the one week you would most want them.
- **Applied identically to local disk and both destinations.**
- **Pure logic in `BackupRetentionRule`**, unit-tested against explicit dates. Every other component
  can be fixed and re-run; this one's mistakes are already made.
- **`backup_run` rows outlive their artefacts**, so the history is a list of attempts rather than of
  surviving files.

### The restore check asserts the books, not the file

Creates a scratch database, `pg_restore`s into it, then asserts: schema version matches live, row
counts match live for `account` / `setting` / `journal_entry` / `journal_line`, and — the one that
matters — **the restored ledger balances**. Findings are kept on a passing check too, because a green
flag with nothing behind it is the unverified claim brief §13 already objected to. Scratch database
name whitelisted and **refused if it equals the live database**, since the check begins by dropping
it. Weekly by default; the nightly backup is separate.

### Notable implementation decisions

- **Plain `HttpClient`, not `google-api-services-drive`.** The failures that matter are protocol-level
  (expired refresh token, deleted folder, 403 quota) and `StubDriveServer` produces all of them over
  a real socket with no credentials — asserting the uploaded bytes equal the artefact on disk.
- **The plaintext dump never touches disk** — `pg_dump`'s stdout is piped straight through the
  cipher. The one exception is the restore check, owner-only and deleted in a `finally`, stated as a
  trade-off.
- **`CipherInputStream` is deliberately not used**: it swallows the GCM tag failure and would let a
  truncated backup decrypt to a short plaintext with no error. Both tampering and truncation have
  tests.
- **`postgresql-client-17` is in the runtime image and in CI.** The major version **must** match the
  server — `pg_dump` refuses to dump a newer one — so upgrading the postgres image means changing
  the Dockerfile in the same commit.
- **A new ArchUnit rule** confines `javax.crypto` and `ProcessBuilder` to `..core.backup..`.

### Defects found by running it, not by reasoning about it

1. **`RestoreVerifier` called its own `@Transactional` methods** — a self-invocation bypasses the
   proxy, so they would have done nothing, silently. Split into `RestoreCheckJournal`. The same
   lesson step 11 recorded for `EmailOutbox`, rediscovered by writing it the obvious way.
2. **Artefact names collided within one second** — dismissed while designing as pathological, hit by
   the test suite immediately, and reachable in production by a manual backup during the scheduled
   one. Fixed in production code with a `-2`/`-3` suffix.
3. **Avoided rather than fixed:** reading `spring.datasource.url` fails the whole context under
   Testcontainers' `@ServiceConnection`, and is the general case of a dump driven by a property that
   could drift from the pool — faithfully backing up the wrong database while looking healthy.
   `DatabaseConnectionProvider` reads the pool.
4. **The retention rule tests failed four ways at first and all four were the fixture**, which
   restarted backup ids at 1 per month. Worth recording because every one of them looked like a bug
   in the rule.

### Verified, and not verified

- **858 tests passing, `mvn clean verify` exit 0** (up from 822). `BackupIT` runs the real
  `pg_dump`, really encrypts, really restores into a real scratch database and really asserts the
  ledger balances. **(Now 866 after the self-invocation work; see the CI section at the top —
  `BackupIT`'s 16 tests run on CI too, 0 skipped, on a real 17 client/server pair.)**
- ~~**⚠️ Never run against real Google Drive.**~~ **Run for real on 2026-07-29** — both destinations
  `UPLOADED`. See "Step 12, commissioned" below.
- ~~**⚠️ The container image has not been rebuilt**~~ **Rebuilt 2026-07-29**, installing
  `postgresql-client-17 (17.10-1.pgdg26.04+1)`, applying V21–V23 to the live database and taking a
  real dump through it.
- **PostgreSQL 17 client tools were installed on this machine** at
  `C:\Users\kosta\tools\pg17\pgsql\bin` and added to the user PATH. Without them `BackupIT` **skips**
  rather than fails — deliberately, so a missing tool does not teach people to ignore red suites,
  but it does mean a silent loss of coverage worth knowing about.

---

## Proxy self-invocation — now a build failure, and it found two real defects

Raised at step 12's close: the `RestoreVerifier` self-invocation was the **second** time that exact
pattern had bitten the codebase, after step 11's `EmailOutbox`. Rather than rely on it being
rediscovered a third time, it is now enforced.

**`SelfInvocationRulesTest`** — two ArchUnit rules over the production class graph:

1. **A non-transactional method may not call its own class's `@Transactional` method.** The proxy is
   bypassed, so there is no transaction at all. This is the shape that bit us twice.
2. **Nothing may self-invoke a method declaring non-default propagation.** A self-called
   `REQUIRES_NEW` silently joins the caller's transaction instead of starting its own — wrong even
   when the caller *is* transactional, which is the case rule 1 has to permit.

Each rule has a **probe fixture proving it fails**, plus a fixture proving the recommended remedy
does not trip it. Rules nobody has watched reject something are indistinguishable from rules that
match nothing — the lesson from step 4b's vacuous `..core.web..` rule.

**Deliberately allowed:** a `@Transactional` method calling another on the same class with default
propagation. The inner call joins the outer transaction, which is what the code means. The first
draft forbade it and reported **44 violations**, essentially all harmless; a rule that cries wolf 44
times is one somebody deletes. Narrowing to the two shapes above turned that into 6 findings across
3 classes — and **two of them were real defects.**

### 🐛 `AuditLogServiceImpl` — pre-existing, and the serious one

`record(action, entityType, entityId)` and `recordSystemAction(...)` were **unannotated** and
self-invoked the four-argument `record(...)`, which is `@Transactional(propagation = REQUIRES_NEW)`.
That `REQUIRES_NEW` exists — and is documented in that class — so that *an audit entry survives the
rollback of the operation it describes*. Through a self-call it was never applied, so every entry
written via those two overloads **joined the caller's transaction and was rolled back with the very
operation it was recording.** A rejected journal entry or a refused permission is exactly what you
most want recorded, and exactly what was being lost. In the audit log, which is the record of last
resort.

**Fixed:** all three public overloads now carry the annotation and delegate to a private, unannotated
`write(...)`. No self-invocation of an annotated method remains.

**Verified behaviourally, not just structurally — and this distinction is the whole lesson.**
`AuditLogIT` had seven tests and **not one of them involved a rollback**, which is exactly why the
defect survived from the day the class was written. Two new tests now assert the property the
`REQUIRES_NEW` exists for:

- `entriesSurviveTheRollbackOfTheirOperation` — writes through **all three overloads** inside a
  transaction that then throws, and asserts all three entries are still there afterwards.
- `theEntryIsVisibleWhileTheCallerIsStillOpen` — reads through a **separate JDBC connection** while
  the caller's transaction is still open, proving the entry was genuinely committed by its own
  transaction rather than merely happening not to be rolled back. A `JdbcTemplate` would have
  joined the caller's transaction and passed either way.

**Both were confirmed to fail against the reintroduced bug** before being accepted — the defect was
temporarily restored, the tests failed, and the fix was put back. A regression test that has never
been seen to fail is a regression test nobody has verified.

**⚠️ The ArchUnit rules cannot protect this property.** Deleting the annotation from an overload and
calling the private `write(...)` directly is structurally spotless and reintroduces the defect in
full. Only these two tests would notice. Structural rules catch the *shape*; only a behavioural test
holds the *guarantee*.

### 🐛 `BackupRetentionService` — step 12, plus a latent lazy-loading failure

`apply()` is deliberately not transactional (it deletes files and calls Drive) and self-invoked a
`@Transactional` read. Moved to `BackupJournal.retentionCandidates()`.

Fixing it surfaced a second, worse defect on the same path: `removeArtefact` loaded a `BackupRun`
and read its **lazy `uploads` association outside any transaction**. It would have thrown on the
first real prune — which only happens once there are more than `daily-count` backups, a state no
test had ever produced. Now `BackupJournal.artefactToRemove` materialises plain data inside the
transaction, the way `EmailOutbox.claimDue` already documents, and **a new test drives a real prune
end to end** (artefact deleted from disk *and* from the destination, row surviving its artefact).

### `SettlementServiceImpl` — not a defect, restructured anyway

Three findings where private helpers called the public `@Transactional openAmountOf`. Harmless in
effect — every public entry point reaching those helpers is transactional, so the read joins that
transaction — but indistinguishable in bytecode from the shape that is *not* harmless. Split into a
private `openAmount(...)` computation with the public method as the thin transactional wrapper,
which says which is the entry point and keeps the rule sharp enough to be worth having.

### Recorded in `CLAUDE.md` as well

The rules cannot cover `@Async`, `@Cacheable`, `@PreAuthorize` or `@Retryable`, which fail
identically, nor a call reached through a captured lambda. `CLAUDE.md` names the general
anti-pattern, the remedy, and the related trap of returning lazily-associated entities from a
non-transactional method.

**866 tests passing, `mvn clean verify` exit 0** (up from 858; ArchUnit 13 → 18, plus the two
behavioural audit-log tests).

---

## Step 13 — done (the test suite consolidation sweep), ADR 0014

Three things, in the order they were built: a property-based testing harness and the properties it
exists for, property tests over FIFO against a real database, and one whole-scenario test that plays
a trading year and then sweeps every invariant the system has over the resulting database.

**952 tests at this point, `mvn clean verify` exit 0** — 960 once Q45 was fixed, below.

### jqwik could not be used, for the third time in a row, and for the same reason (ADR 0014)

jqwik is a JUnit Platform **test engine**, and `net.jqwik:jqwik-engine:1.10.1` — the newest release
— declares `junit-platform-engine:1.14.4`. Spring Boot 4.1 brings JUnit 6, whose platform artefacts
are `6.x`. There is no jqwik 2. **Verified against Maven Central rather than assumed.**

This is exactly the situation ADR 0002 resolved for `archunit-junit5` and step 11 resolved for
`greenmail-junit5`, and it is resolved the same way: *take the idea, not the artifact.* The
difference is that jqwik has no plain-library form — the engine **is** the product — so the harness
was written: `Gen`, `Property` and `ValueGenerators` in `..core.api.testsupport..`, about 500 lines,
published from `core-api` as a test-jar so `core` uses the same generators and the same shrinking.

**The seed is fixed by default.** 500 cases, the same ones on every machine and every CI run, so a
red build always means a defect rather than today's dice — `CLAUDE.md`'s "a check that cries wolf is
one somebody deletes", applied to a runner rather than to a rule. `-Dnovocore.property.seed=<n>`
explores further, deliberately, and **anything a new seed finds belongs in a named example test**
rather than being left to luck. The breadth a fixed seed costs is bought back in the generators:
roughly a third of every sample comes from a hand-written edge list — zero, one cent, the scale
limit, a rounding midpoint — because that is where these types break, and a uniformly random
twelve-digit decimal never lands there.

**The harness is proven to fail** (`PropertyTest`), for the reason the `..core.web..` ArchUnit rule
taught: a checker that is itself broken produces a green suite that proves nothing. It proves a
false property is reported, that a non-assertion exception counts as a failure, that the value
reported is the *shrunk* one, and that generation is reproducible. **Writing it immediately caught a
weakness in the shrinker**: the first version offered "half" and "one unit closer to zero" and
nothing between, so a property failing above 1000 shrank 12345 to 1543 and then crawled down by 0.01
until the round limit stopped it. A halving ladder replaced it and converges in tens of rounds.

### What the properties actually claim

Over `Money`, `Quantity`, `UnitCost` and `ProportionalAllocation` — the laws, not the examples. That
equality is numeric equality (the whole reason the scale is fixed on construction); that `compareTo`
agrees with `equals`, which nothing in the suite had ever said although `Money` is `Comparable` and
gets sorted; that the currency guard holds on **every** binary operation rather than the two an
example test happened to cover; that rounding never moves a value by a whole cent in any mode; that
`Quantity.times` refuses **exactly** when the product needs more than six decimals, not merely that
it can refuse.

`ProportionalAllocation` gains the most. Its two callers — a bundle's price pushed onto its
components, a freight invoice split across lots — are both wrong in ways nobody notices if a single
cent goes astray. The properties assert that the parts sum exactly to the whole, that **no part is
ever more than a cent from its exact share** (which is what distinguishes largest-remainder from
"floor everything and dump the residual on the last part" — both sum correctly, only one is an
allocation), that a weightless part takes nothing, and that negating the total negates every part.
It is also checked against **an independently written largest-remainder split** that shares no code
with the implementation. One tempting property is deliberately absent and says so: permuting the
weights does not permute the result, because ties are broken by position — asserting the stronger
claim would be asserting a bug.

### FIFO, over generated histories rather than chosen ones

`FifoPropertiesIT` generates a whole history — several deliveries at different dates, costs and
locations, then several sales, some of which oversell — and replays it against the real services,
twenty histories per property. It asserts conservation (`filled + shortfall == requested`, lines sum
to filled), that no lot leaves its own bounds, that aggregate stock reconciles to the lots less the
shortfalls, that the entry balances and every line names its lot, and that a shortfall is never
costed. FIFO's allocation is compared against **an independently written FIFO** computed from the
lots' captured before-state, which subsumes ordering, exhaustion and never-touching-Damaged-Goods —
those three are still asserted separately, because "the allocation differs" is a worse bug report
than "it sold out of Damaged Goods".

**The first thing it found was a mistake in its own fixture, and the finding is worth keeping.**
Building lots through `InventoryService.receive` creates stock with **no ledger entry behind it** —
ADR 0004 puts the Inventory debit on the Goods Receipt, which is the document that knows the
supplier the GR/IR clearing is against. The interface says so explicitly ("nothing outside the core
should be calling this"). `StockConsumptionIT` uses the same shortcut and is right to, because it
asserts nothing about the ledger; anything that does assert about the ledger must go through a
Goods Receipt.

**The second thing it found is Q45**, above: the Inventory rounding residue. That is a real defect
in posted money, and it is written up rather than fixed.

### One trading year, then every invariant at once

`WholeScenarioIT` builds a year — purchases arriving before and after their invoices, freight
allocated onto stock partly sold, a bundle decomposed, a sale nobody had the stock for, a credit
note that brings stock back, settlements both ways, a reversal, a write-off, a bank transfer — and
then asserts, as separate ordered tests so a break names itself:

- **No entry anywhere in the database is unbalanced, empty, one-sided or multi-currency**, asked in
  raw SQL straight against the tables. It bypasses every service, view and Java check, which is what
  makes it a statement about the *data*. It is the one assertion here that would still be worth
  keeping if everything else in the file were deleted.
- No journal line is zero or negative.
- **The ledger is not trivial** — over 15 entries and 60 lines. Guards the failure mode every
  whole-system test has: passing because it did nothing.
- The trial balance balances.
- **Every control account equals the sum of its own sub-ledger**, swept over the whole chart rather
  than over named accounts, and every Control-account line carries a reference.
- Inventory equals what every lot says it is carrying; GR/IR holds exactly the timing gap and is
  zero when both sides are clear; both variance accounts equal what the documents recorded.
- VAT precedence resolved at all three levels on one document, with each line recording *which*
  level won — the beans line is `CUSTOMER` even though the customer's rate and the product's rate
  are both 13%, because recording only the number would make "why is this line at 13%?"
  unanswerable.
- Output and input VAT are separate figures and each equals its own account.
- Open items equal AR and AP, which ADR 0009 requires by construction since allocations post
  nothing.
- The oversold product reads negative and is findable; the bundle is stored decomposed and the
  components sum to the line; a reversal is an exact mirror; the write-off both reduced stock and
  posted.
- **And the whole year backs up, restores into a fresh database, and still balances there.** This is
  worth more here than in `BackupIT`, where the restore check asserts that a nearly-empty ledger
  balances. (Skipped on a machine without `pg_dump`; it runs on CI.)

**It found a real gap in its own reasoning too**, which is the kind of thing only a whole-scenario
test can: `Landed cost variance` has **two** contributors, not one. The allocations put the share
belonging to already-sold stock into it (ADR 0010) and ADR 0011's catch-up takes some back out when
returned stock re-enters a re-costed lot. Comparing the account against the allocations alone was
wrong — 18.38 against 22.98, the 4.60 being exactly the credit note's four returned units. The test
now reads both contributions off the ledger by source and asserts they add up.

### Why this class gets its own database, and what that buys

`WholeScenarioIT` declares a `@DynamicPropertySource` for the backup leg, so Spring gives it its own
context and therefore its own container. That is a feature: the sweeps cover exactly the scenario
this class built and nothing else, so "Inventory equals the sum of what the lots carry" is an
equality rather than a delta against whatever the rest of the suite left behind.

---

## Step 13, part two — Q45 fixed (ADR 0015, migration V24)

Approved as recommended and built the same day. **960 tests, `mvn clean verify` exit 0.**

### What the rule now is

A lot's **carrying value** is its remaining quantity extended at its unit cost, rounded exactly once
— one definition, `LotValuation`, one rounding mode. **Every posting that moves a lot's stock puts
the change in that figure on the Inventory line.** Not the quantity moved extended at the cost and
rounded, which is a different number and was the whole of Q45.

So `InventoryLotView.remainingValue()` and the Inventory account's position for that lot are equal
at every moment by construction, and a fully consumed lot leaves exactly nothing behind.

### Four things worth knowing beyond the ADR

1. **The freight allocation had to change too**, and it was not in the approved list. It is what
   creates six-decimal unit costs in the first place (ADR 0010: €2.00 over three units is 0.666667)
   and it debits Inventory, so leaving it alone would have left the invariant false at the one place
   it matters most. Its capitalised half is no longer a proportional estimate of what the stock on
   hand should absorb — it is exactly how much the allocation raises the lot's carrying value.
   ADR 0010's decision is untouched; only its arithmetic is stated exactly rather than approximately.
2. **Migration V24**, which drops exactly one CHECK. With the capitalised half stated exactly, the
   variance half is the remainder — and the remainder can be one cent **negative**, because a
   six-decimal per-unit cost cannot always express a total. `Landed cost variance` is credited in
   that case, which needs nothing new: ADR 0011's return catch-up already credits it.
3. **The rounding mode is fixed at `HALF_UP` and deliberately does not follow
   `ledger.rounding.mode`.** A lot's value at two moments must be measured the same way, and a
   setting somebody can change cannot promise that — a change mid-life would leave exactly the cent
   this fix removes. `ledger.rounding.mode` still governs document rounding, which is what brief §6
   asks it for. Nothing about today's numbers changes; what changes is that nothing can change them
   tomorrow. Consequence: **`InventoryServiceImpl` no longer reads settings at all.**
4. **Reversal is the one place the two rules pull apart, and it is guarded rather than fudged.** A
   reversal must post the exact mirror (Q13, ADR 0006), and the mirror is only the right amount if
   the lot has not moved since. `reverseConsumption` and `reverseWriteOff` compare the two and
   **refuse if and only if they differ**, naming the remedy. It cannot fire for a whole-cent cost, it
   does not fire when reversing the most recent movement, and it fires only when reversing *behind* a
   later movement on a sub-cent-cost lot.

### Two consequences a reader should not be surprised by

- **Two identical units out of one lot can post different costs** — 12.50 then 12.51. They must, if
  the lot is to end at zero: 22 × 12.505 is 275.11 and no repeated cent figure divides it.
- **Cost is now path-independent.** Twenty-two single sales and one sale of twenty-two both cost
  €275.11. Before, they cost €275.22 and €275.11 and both claimed to be the cost of the same lot.

### Verification, and it is the point

- **`LotCarryingValueIT` is proven to actually fail.** The old formula was reinstated and five of
  its eight tests went red, one at €275.22 against €275.11 — the reported drift, to the cent. The
  three that stayed green are supposed to, and the file says which and why.
- **`FifoPropertiesIT`'s whole-cent restriction is removed.** The ledger-agreement and
  self-liquidation properties now run over 0.333333, 10.666667, 12.505 and 99.999999 across twenty
  generated histories each. That is the fix being checked against the class of input that found the
  bug, which is what was asked for.
- **`WholeScenarioIT` is unchanged and still green**, which is what says the fix moved nothing it
  should not have. No other test in the suite needed changing either — a fact worth recording,
  because it means every existing example test used whole-cent costs, which is why the defect
  survived twelve build steps.

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
- ~~**Q31**~~ — **confirmed in step 9: single role per user stays, no change needed.** Brief §7's
  "multiple custom roles from the start" reads as many role *definitions*, not many roles per
  person, which is what step 4 built. Confirmed against the current team shape (Owner, Admin, one
  operational Remote/Order Staff role) with no foreseeable need for anyone to hold two. It is no
  longer the cheap-now/expensive-later item it had been since step 5.
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
- ~~**Q10**~~ — **answered and built in step 9 (ADR 0009): seeded, and structural.** "Πελάτης
  Λιανικής" carries `CustomerSystemKey.RETAIL_WALK_IN`; its VAT treatment is fixed at `DOMESTIC` by
  CHECK, it cannot be deactivated by CHECK, and it is refused on both sides of a merge. Not a
  default — nothing falls back to it.
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

### Resolved in step 9 — sales, credit notes and the open-item layer
- ~~**Q10**~~ — **the shared retail customer, seeded and structural.** See above and ADR 0009.
- ~~**Q15's remainder**~~ — **answered: confirm at entry, record the confirmation, no review
  queue.** A rounding difference above `ledger.rounding.threshold` refuses the document until
  somebody accepts it; below it, it posts automatically; the difference always posts, so Accounts
  receivable agrees with what the customer holds. The rule generalises: visible-at-entry is
  confirmed at entry, a consequence the operator cannot fix (Q17's shortfall) is flagged and
  queried, and neither is a queue.
- ~~**Q16**~~ — **built: unallocated credit is a standalone `customer_credit` document that posts
  nothing**, created only when the caller states the remainder is credit rather than an unmatched
  remittance.
- ~~**Q26**~~ — **built: the credit note is its own transaction type**, referencing the invoice it
  corrects, debiting the per-channel `Sales returns` account, immutable once issued, crediting the
  VAT the sale actually charged.
- ~~**Q31**~~ — **confirmed, nothing to change.** See above.

### Resolved in step 10 — landed costs
- ~~**Q18**~~ — **answered and built. ADR 0010.** A lot's share of an allocated freight cost splits by
  what is still in the lot: the part belonging to stock on hand raises its unit cost, the part belonging
  to stock already gone posts to `Landed cost variance`. Nothing reaches back into posted COGS, which is
  the constraint ADR 0008 placed on the answer. The allocation basis is the lot's **received** cost,
  which is why that figure is now frozen for the life of a lot.
- ~~**Step 6's last-purchase-price obligation**~~ — **discharged**, with one residual difference
  recorded. See the step 10 section.
- **ADR 0011** *(new, not a question)* — **returns catch the freight up, reversals refuse.** A defect
  ADR 0010 introduced, found by reviewing step 10 rather than by a failing test, measured at €20 on a
  ten-unit lot. Recorded here because the asymmetry is the kind of thing a later reader would otherwise
  try to "make consistent".

### ✅ Q45 — answered and fixed (ADR 0015)

- **Q45** *(raised and closed on 2026-07-29)* — **a lot whose unit cost was not a whole number of
  cents left a permanent residue in the Inventory control account when it was fully consumed.**
  Found by the FIFO property tests, reproduced by a throwaway probe, then **fixed as ADR 0015**:
  every posting that moves a lot now puts *the change in the lot's carrying value* on the Inventory
  line, so `remainingValue()` and the account agree by construction and an emptied lot leaves
  nothing behind. The description below is kept as it was written, because the measurements are the
  reason the fix looks the way it does — see "Step 13, part two" above for what was actually built.

  **What happens.** A Goods Receipt debits Inventory with the whole delivery rounded once
  (`quantity × unitCost`, one rounding). Each consumption credits Inventory with *its own* line
  rounded once. Those two roundings are at different granularities, so they do not add up:

      22 units @ 12.505000, sold one at a time
        Goods Receipt debited Inventory        275.11
        each sale credited                      12.51   (12.505 rounded, HALF_UP)
        22 sales credited                      275.22
        >>> lot empty, Inventory residue        -0.11

      3 units @ 10.666667 (a landed-cost-allocated lot), sold one at a time
        >>> lot empty, Inventory residue        -0.01

  **Why it matters, and why it is not cosmetic.** The residue is real journal lines on a real
  account. It does not net out across lots — `HALF_UP` rounds away from zero, so the drift is
  systematically negative — and there is no document behind it, nothing to reconcile it against and
  no report that would explain it. The Inventory line on the balance sheet and COGS are both wrong
  by the accumulated amount, permanently. It also makes `InventoryLotView.remainingValue()` and the
  Inventory account disagree part-way through a lot's life, which is the invariant ADR 0011 exists
  to protect.

  **It is reachable by design, not by accident.** `UnitCost` carries six decimals precisely so
  ADR 0010's landed-cost allocation can divide freight across lots without losing precision —
  €2.00 over three units is 0.666667. Every re-costed lot is a candidate.

  **Recommended fix, for the decision rather than as a fait accompli:** post the *change in the
  lot's carrying value* rather than `quantity × unitCost` rounded. That is, a movement's amount is
  `round(remainingBefore × cost) − round(remainingAfter × cost)`. It makes the ADR 0011 invariant
  true by construction at every point, guarantees a lot self-liquidates to exactly zero, and needs
  no new account and no migration. The cost is that COGS per unit varies by a cent within a lot,
  which is the honest answer — the lot cost what it cost and all of it has to leave. Rejected
  alternatives: posting the residue to `Rounding differences` (that account is for reconciling
  against an *external* document, never for absorbing our own arithmetic — the same stance
  `ProportionalAllocation` takes) and accepting it with a tolerance (which is how a wrong number
  becomes permanent).

  ~~**Not fixed in step 13, deliberately.**~~ **Approved and fixed the same day.** The
  recommendation above was accepted as written, and ADR 0015 is it: `consume`, `writeOff`,
  `returnConsumed`, both reversals **and the freight allocation** now post the change in the lot's
  carrying value. The freight allocation was not in the original list and had to be: it is what
  creates sub-cent unit costs in the first place, and it posts to Inventory, so leaving it would
  have left the invariant false at the one place it matters most.

  ~~**What the suite says in the meantime.**~~ `FifoPropertiesIT`'s restriction to whole-cent costs
  **is gone**: the ledger-agreement and self-liquidation properties now run over every cost shape,
  which is what verifies the fix against the class of input that found the bug.

---

## Step 14 — done (the REST surface, and Q44 in full)

Four commits: `423bf34` (14a), `e6354d6` (14b), `b8aa9e2` (14c), `f2e8e06` (14c cont.). **133
routes** across 11 controller packages, one migration (**V25**), and 79 new tests.

Split into three sub-steps deliberately — one commit of that size would have been unreviewable, and
each of the three is green on its own. The full proposal, with the endpoint tables and the reasoning
behind every decision, is `docs/step-14-rest-surface-proposal.md`.

### The foundations, which are the part that will outlive the endpoints

- **`@Requires(section, level)` plus an interceptor** replaces step 4b's inline `requireView` call.
  4b's own javadoc said this should happen "with many controllers"; the risk it introduces is the
  opposite of the one 4b avoided — an annotation that is *forgotten* fails open, silently, and looks
  exactly like working code. **Three layers say otherwise**: an ArchUnit rule fails the build, a
  `ContextRefreshedEvent` check refuses to let the application start, and the interceptor refuses the
  request. All three were proven to fire.
- **Money crosses the wire as a string** — `{"amount": "12.50", "currency": "EUR"}`, quantities as
  bare strings. JSON has no decimal type and a number literal becomes an IEEE-754 double in a
  browser, which is `CLAUDE.md` rule 5 broken at the one layer facing outward. **A JSON number is
  refused, not accepted and rounded**: a client that sent one has already lost the value. Held to
  **12.505** specifically — the unit cost behind Q45 — including an assertion of exactly what a
  double does to it.
- **The error mapping went from two cases to the full set**: 404 absent, 422 refused, 409 immutable,
  410 pruned, 413, 503, 400. **Permission refusals stay generic** (they would describe the permission
  model); **validation refusals carry the core's own message**, because an operator who cannot see
  why a document was refused cannot fix it. `WebExceptionMappingTest` enumerates `core-api`'s
  exceptions and fails if one is unmapped, so the explicit list cannot fall behind.
- **Lists are wrapped** in `{"items": [...]}`. Step 14 ships **unpaged**, deliberately — no service
  method takes a limit — and the envelope is what makes adding paging later something other than a
  breaking change. **`GET /api/products` returns every product**, which is fine at hundreds and not
  at tens of thousands.
- **Commands, not CRUD.** One route per named service operation, because a whole-object `PUT` would
  have to diff and dispatch inside a controller and would turn an absent field into a null. 201 for
  creations, 204 for void commands.

### Three architecture rules, each closing something that fails silently

Every route declares a section; nothing in `..core.web..` calls an unredacted product read; nothing
in `..core.web..` calls the lower inventory layer. **Each was proven to fail against a probe, and the
probes deleted.**

The third is the one worth restating. **`receive`, `unreceive`, `consume`, `reverseConsumption`,
`applyLandedCost` and `removeLandedCost` have no HTTP route and cannot be called from one.** Each
moves stock and posts nothing on its own, because the document service that calls them posts the
entry in the same transaction. A route to `receive` would create a lot with no document; a route to
`consume` would take stock out with no sale. Either leaves the Inventory control account disagreeing
with what the lots carry — the invariant ADR 0015 restored. **Stock moves through documents.**

### Two placements that are decisions, not details

- **`GET /api/products/{id}/stock` is under `PRODUCTS`, not `INVENTORY`.** `StockLevels` carries
  quantities and no cost. An order picker with VIEW on Products needs to know there are three left; a
  *lot* is what says what those three cost. Putting it behind INVENTORY would either stop
  Remote/Order Staff doing its job or force a grant that hands over cost data with it.
- **An asset's carrying value gets no route at all.** It is `subLedgerBalanceOf`, which is
  `Section.JOURNAL` — every posting against the asset. Exposing it on the asset route would be a
  second, weaker path to ledger data, which is the exact failure Q44 exists to prevent elsewhere.

### ✅ Q44 — both halves, answered and built

**The section half.** `Section.EMAIL_OUTBOX` is new and deliberately **not** folded into `SETTINGS`:
changing the SMTP password and reading who was emailed about what are different grants — the
argument that separates `JOURNAL` from `CHART_OF_ACCOUNTS` and `INVENTORY` from `PRODUCTS`. Bodies
are already absent from `QueuedEmailView`, so what it governs is recipients, subjects, delivery state
and attachments.

**The access-path half.** `EmailSender.downloadAttachment` now takes the viewer as a **required
parameter with no unchecked overload beside it** — an unchecked path left available is the path that
eventually gets called — and re-checks a *referenced* attachment against the section governing the
record it belongs to.

**⚠️ Implementing it turned up what the decision could not have known: there was nothing to check
against.** `AttachmentService.entityType` is free text, so no mapping from a document to a section
existed. **`AttachmentOwnerType`** is that missing piece: one typed registry, **fail-closed on an
unrecognised type, denying even the Owner**. The consequence is deliberate — attaching documents to a
new kind of record means adding it there, or nobody can download them out of a sent email. That fails
visibly; the alternative fails silently. Denying the Owner too is the strict reading, chosen because
if only restricted roles were refused the missing registration would be invisible to whoever could
fix it.

**Proven behaviourally, not structurally.** `EmailAttachmentAccessIT` (8 tests) and
`OutboxEndpointIT` (6) assert outcomes — a clerk holding `EMAIL_OUTBOX` and not `PURCHASING` sees the
attachment's *name* and gets **403** for its bytes, while the Owner gets the file — and **both denial
tests were confirmed to fail against the check removed**, then it was restored. That is the step-12
audit-log lesson applied on purpose: a structurally spotless change can reintroduce the defect in
full, and only a behavioural test holds the guarantee.

### 🐛 "No migrations expected" was wrong, and how it was found matters

The plan said step 14 would need no migration, reasoning that a `Section` is a Java enum and grants
are default-deny. **`role_section_grant` carries a CHECK listing every known section by name**, so
`EMAIL_OUTBOX` existing only in Java **could not be granted at all** — every insert refused by the
database. Three tests failed; the reasoning had not.

The constraint is doing exactly what it was built for — the same pattern as
`journal_entry_source_known`: the database states the value list independently, so neither side can
drift unnoticed, and the price is that adding a section is a migration. **V25 pays it.**

**The durable fix is the guard, not the migration.** `SecurityIT` now holds the CHECK to the enum in
**both** directions and adds a third test that a grant can really be *stored* for every section,
reserved ones included — structural agreement not being the same as it working. The next person
adding a section finds out from a named test rather than from three unrelated failures.

### Three more defects and gaps the work turned up

1. **`ProductService.allFor(viewer)` returned `active()`** — contradicting its own name and leaving
   `all()` with no redacted counterpart at all. Corrected, and `activeFor`, `findBySkuFor`,
   `findByEanFor` and `bySupplierFor` added, so **every plain read now has a `...For` variant**.
   Without them a controller wanting active products or a SKU lookup would have had to filter a
   redacted list itself — domain logic in the web layer, filtering on a field that may have just been
   blanked.
2. **`EndpointDeclarationCheck` died at startup on `NoUniqueBeanDefinitionException`.** A real
   context has **two** `RequestMappingHandlerMapping` beans, Spring MVC's and the actuator's. It now
   checks every mapping, which is also the stricter answer — choosing one by name would leave the
   others unexamined without saying so. Covered by a test that fails against the old shape.
3. **`BundleService.allBundles()` and `bundlesWithUnpricedComponents()` returned unredacted
   `ProductView`s.** The behaviour was correct (the controller redacted by hand) and the *guarantee*
   was conventional, because the architecture rule was written against `ProductService` alone. Closed
   in `f2e8e06` with `allBundlesFor` / `bundlesWithUnpricedComponentsFor` and the rule extended,
   proven to fail against a probe.

### Deliberately not built in step 14

Users and roles, settings, the audit log, backup administration, **journal writing** (manual entries)
and VAT-class/exemption-reason *administration*. Each has a service and can get a route later; none
is needed to drive a trading workflow, and adding them would have tripled the review surface. The
journal has **read** routes nowhere either — `Section.JOURNAL` is close to granting everything, and
nothing in step 14's workflows needs it.

### Not blocking anything, but unanswered
- **Q40** **Does a journal entry need a human-facing entry number?** The id is the handle today. An
  accountant asking "what is entry 412" is a real request, and it carries a format decision nobody
  has been asked — per-year reset? a prefix per source? Nothing was guessed. The same question now
  applies to the purchase invoice and goods receipt, which likewise have no NovoCore-facing number:
  an invoice at least carries the *supplier's*, and a delivery may carry their note reference.
- **Q41** **After-the-fact GR/IR matching.** A match is made by whichever document is created
  second; nothing matches an existing invoice to an existing delivery later. That leaves a real
  balance sitting in GR/IR — which is exactly what ADR 0004 says a residual means, and phase 8's
  Clearing Checks is what surfaces it. Building it needs an answer to "whose document is the variance
  entry?", so it belongs with those checks rather than with a later step reaching for it casually.
- **Q42** *(new)* **A bundle containing a serial-tracked component cannot be sold as a bundle.**
  Step 9 refuses it, naming the reason: which machine left the shelf is a fact somebody scanned, and
  a bundle line names no serial numbers, so a bundle definition would be choosing units. The machine
  can be sold on its own line today. The real answer — if this ever comes up — is serial numbers per
  *component* on a bundle line, which is a request shape nobody has asked for. **Not blocking
  anything**; recorded so it is a decision rather than a surprise at the till.
- **A credit note that restored stock cannot be reversed.** Not a question — a decision (ADR 0009),
  applying ADR 0008's principle in the other direction: the goods are physically on a shelf, in a
  lot FIFO may have sold from again. A price-only credit note reverses normally. Recorded here
  because it is the kind of limitation that looks like an omission from the outside.

### Blocking phase 8 — Clearing Checks
- **Step-3 obligation:** surface lots aging in the Damaged Goods location. **Step 6 built the query
  it needs** — `InventoryService.lotsAt(DAMAGED_GOODS)` and `unitsAt(DAMAGED_GOODS)`, covering both
  shapes of lot and excluding exhausted ones. The *check* is still phase 8's to write.

### ~~Blocking step 12 — backups~~ — Q24 answered and built
- ~~**Q24**~~ — **answered 2026-07-29 and built.** Google **Drive API** (not `rclone`), **OAuth
  refresh token per account** (a service account has no Drive quota of its own, and Shared Drives
  need Workspace, which `novotrade.gr` is not). Dumps **are encrypted at rest**, AES-256-GCM, before
  anything leaves the host. Retention: **7 rolling + every calendar month's last, forever.** See the
  step 12 section below and ADR 0013.
- ~~**⚠️ Still outstanding:** the two Drive destinations' folder ids and OAuth credentials.~~
  **Supplied and verified 2026-07-29.** Both destinations are fully configured and both have
  uploaded a real artefact. The OAuth consent screen is published **In production** — deliberately,
  because a consent screen left in *Testing* expires refresh tokens after **7 days**, which would
  have produced a backup regime that worked for a week and then failed quietly. `drive.file` is a
  non-sensitive scope, so publishing needed no Google verification review. **Nothing about the
  credentials is in the repo**: the client id and folder ids are Settings rows, the client secret and
  refresh tokens are `secret` Settings rows, and the environment variables that carried them have
  been removed from `.env`.

### ✅ Step 12, commissioned — all three owner action items closed (2026-07-29)

**Step 12 is no longer "code-complete but unverified". It runs.** The caveat that stood in this file
since the step was built is removed rather than softened, because the thing it warned about has
happened for real.

| Item | State |
|---|---|
| 1. Encryption key into a password manager | **Done.** Generated with `openssl rand -base64 32`, in `docker/.env`, and recorded in a password manager. |
| 2. OAuth consent for both Drive accounts | **Done.** Both consented, both destinations `UPLOADED`. |
| 3. `docker compose up --build` proving the image | **Done.** `postgresql-client-17 (17.10-1.pgdg26.04+1)` installed, V21–V23 applied, real dump taken through it. |

**The evidence, from the database rather than from the logs:** `backup_run` id 8,
`novocore-20260729T160100-novocore.dump.enc`, 309,820 bytes, `SUCCEEDED`, with **both**
`backup_upload` rows reading `UPLOADED` and no error. Retention pruned run 1 under its own stated
policy ("outside the most recent 7, and a later backup exists in 2026-07").

**The key was never generated until this session.** Worth stating plainly because this file
previously claimed otherwise: item 1 used to read *"In progress. Until then the only copy is
`docker/.env`."* That was wrong — there was no copy anywhere, the variable was absent from `.env`
entirely, and therefore **no backup had ever been attempted, let alone failed.** The running stack
was still at schema V20 on an image built before step 12 existed: no `backup_run` table, no
`pg_dump` in the container, and not one log line mentioning backups. A doc that describes a
half-finished state is more dangerous than one that says nothing, because it stops anyone looking.

### 🐛 The commissioning bug: four secrets stored wrapped in literal angle brackets

Both Drive uploads failed identically with `HTTP 401: The provided client secret is invalid`, on a
run whose dump and encryption had both succeeded.

**Cause: two inconsistent placeholder styles in the instructions.** The `.env` block used
`<client secret>` and the SQL block used `PASTE_CLIENT_ID`. The angle-bracketed placeholders were
pasted *over* and the brackets came with them; the bare-word ones were replaced cleanly. So all four
secret values — both client secrets and both refresh tokens — were stored as `<GOCSPX-…>` and
`<1//0…>`, while the four non-secret values (client ids, folder ids) were clean.

**Diagnosed by structure, not by guessing.** The stored secret was 37 characters where a `GOCSPX-`
secret is 35. Rather than speculate about corruption or a regenerated secret in the Google console,
the stored values were inspected at their byte boundaries — `ascii(left(value,1)) = 60` and
`ascii(right(value,1)) = 62`, i.e. `<` and `>` — which named the exact defect in one query and
proved it applied to all four values and only those four. **No trip to the Google console was needed
and no re-consent was required**: the secret material inside the brackets was correct all along.

The fix strips exactly one character from each end, guarded by those same two `ascii` conditions so
it is safe to run twice:

```sql
UPDATE setting SET value = substring(value from 2 for length(value) - 2), ...
WHERE setting_key ~ '^backup\.drive\.(primary|secondary)\.(client-secret|refresh-token)$'
  AND ascii(left(value,1)) = 60 AND ascii(right(value,1)) = 62;
```

**⚠️ The lesson is about the verification step, not the paste.** A check *was* run over `.env`
before the rebuild, and it printed the length of every value — including the wrong one. It did not
catch the bug **because it never stated the expected length to compare against**. A 37-character
`GOCSPX-` secret was visibly wrong at that moment and nothing said so. Generalising: **a verification
that prints a value's shape without asserting what the shape should be is not a verification, it is
a display.** Every check written from here should carry its expectation — which is what the final
form of the settings query does (`value ~ '^GOCSPX-[A-Za-z0-9_-]{28}$'`, and 8-of-8 non-blank,
and *client ids identical / folder ids different / refresh tokens different*).

That last triple is worth keeping for its own sake: **two consents against the same Google account
produce two valid tokens, two working uploads, and two copies in one Drive** — an off-site regime
that looks correct and has a single point of failure. Nothing downstream would ever notice, so the
check has to exist at configuration time.

### ✅ Closed incident: a refresh token was pasted into a chat session

During commissioning the **secondary** account's refresh token was pasted into the assistant chat.
It was **revoked at `myaccount.google.com/permissions` and re-consented**, and — the part that is
easy to miss — **a fresh destination folder was created under the new grant.** That second step is
not optional: `drive.file` grants access per file to the app under a specific authorisation, so
revoking drops those grants and re-consenting does not reliably restore access to a folder created
under the old one. Reusing the original folder id would have produced a permissions failure at 02:00
rather than at configuration time. The current `backup.drive.secondary.folder-id` is the new folder;
the original is unused and may be deleted from Drive. **Closed, with no residual.**

The general rule this leaves: **an authorisation code is short-lived and single-use and barely worth
worrying about; a refresh token is durable and must be treated as compromised the moment it is
copied anywhere it was not meant to go.**

### Residuals — small, and stated rather than tidied away

- **Seven local-only artefacts from the failed runs** (`backup_run` 2–7 plus their successors) still
  sit in the backup volume with `FAILED` uploads. Harmless: retention's rolling window will age them
  out, and it already pruned run 1 correctly.
- **The throwaway commissioning container** ran with `NOVOCORE_BACKUP_CRON='0 * * * * *'`, which is
  why there are eight runs one minute apart. It was stopped; nothing persists it, and the scheduled
  cron remains the default 02:00.
- **Artefact names are stamped in `backup.calendar-zone` (Europe/Athens) while `started_at` is
  stored UTC**, so `…T160100…` corresponds to `13:01:00` in the table. Not a defect — worth knowing
  before someone reads it as a three-hour discrepancy.
- **The four settings were written by raw SQL**, because there is still no HTTP route to Settings.
  So **no audit-log entry exists for them**, and `updated_by` reads `system`, which is the honest
  option available rather than claiming a session that did not happen. Worth revisiting when the
  Settings screen lands.

---

## Step 15 — in progress (dummy data validation, over HTTP)

The full proposal, with the six classes of check and the exit criterion, is
`docs/step-15-validation-proposal.md`. Agreed at **full scope**, not the reduced version.

**What it is for, restated because it is easy to mistake for a second domain test suite.**
`WholeScenarioIT` proves the domain is right when driven through its *services*. Step 15 proves the
**REST surface is a faithful and usable route to it** — a different question, and one nothing had
asked: when this started, roughly **half of the 133 routes had never received an HTTP request**, and
the untested half was the half that moves money.

### Done

- **15a — the harness** (`908b226`). `LedgerInvariants`, extracted from `WholeScenarioIT` so the same
  invariants can be asked of an HTTP-built database; `HttpTransport`, the seam that lets one scenario
  run under Failsafe *or* against live Compose; `JsonNumberSweep` over every response; and
  `RouteCoverage`, whose denominator comes from Spring's own handler mapping. **All four proven to
  fail, not merely to pass.** One new invariant: `everySubLedgerReferenceIsLive` — the old check asked
  whether a reference was *present*, this asks whether it points at anything, which no trigger can
  guarantee after a row is deleted.
- **15b, part** (`d8c9e77`, `1421dfb`). A trading quarter driven entirely over HTTP: GR/IR both ways,
  a purchase price variance, freight split across a partly-sold lot, serial-tracked machines under
  reverse charge, a bundle, all three sales channels, an intra-EU exempt sale, a customer credit, a
  stock-restoring credit note into a re-costed lot, a write-off, an oversell, a reversal, a settlement
  amendment that cascades, then a quarter-end review and corrections. **All twelve universal
  invariants pass on a database that only ever received HTTP requests.**
- **Route coverage: 90/133 (68%)**, reported by the ledger rather than asserted, because
  `assertEveryRouteCoveredExcept` is still to come.

### ✅ 15b, completed 2026-07-30 — the five remaining items, in the recorded order

1. **The refusal matrix** (`RefusalMatrix`, 18 entries, one `DynamicTest` each). Every entry asserts
   three things and the third is the one with teeth: the status; that the body is
   **`application/problem+json`** with a `status` agreeing with the HTTP status and a non-blank
   `detail`; and that the detail names the reason **or deliberately does not**. Nothing in the
   repository asserted the media type before — this makes defect 6's fix permanent, on Spring's own
   refusals as well as ours. `mustNotSay` is the part worth keeping: a withholding policy tested only
   in the generous direction quietly becomes a leak. 409 and 410 are stated as unreachable from a
   trading narrative rather than quietly omitted.
2. **The permission sweep** (`PermissionSweepIT`), and the design decision is the point: **the
   expectation is stated independently**, in a route-prefix→`Section` table, rather than read back off
   the handler's own `@Requires`. A sweep that derives its expectation from the declaration proves only
   that the interceptor applies it, and would pass happily against an inventory route declared
   `PRODUCTS`. Three sweeps, each catching what the others structurally cannot:
   - **Remote/Order Staff over all 133** — reaches exactly **20**, refused on **113**, and every
     refusal is checked to leak neither section nor role nor level.
   - **A view-everywhere role** — every state-changing route must refuse it. Catches a mutating
     handler declared `level = VIEW`, which staff cannot detect outside Products.
   - **A granted-everywhere role**, by stored grants and *not* the `fullAccess` flag — every read must
     reach its handler. The only direction that catches a route guarded by a section no grant can
     satisfy.

   The table is asserted **exhaustive and free of dead rules**, so a new route family cannot arrive
   unclassified. Plus the one field-layer pair nothing asserted: an order picker sees
   `/api/products/{id}/stock` and is refused `/api/inventory/lots`, which is the entire argument for
   `INVENTORY` being a section separate from `PRODUCTS`. **V26's byte-level redaction is deliberately
   not duplicated here** — `MasterDataEndpointIT.Redaction` owns it, on both the single read and the
   list.
3. **Read-back and date boundaries** (`ReadBackChecks`). Eleven documents re-fetched and compared
   against the literals sent; nine date-filtered listings asked the three boundary questions against a
   document **on the boundary**, with the anchor date read from the response rather than assumed; and
   filters asserted to exclude as well as include, which is the half that catches a listing ignoring
   its filter entirely. **No off-by-one was found** — but it was asserted nowhere, and the quarter's
   own reads all span the whole period and so could never have told an inclusive bound from an
   exclusive one. It also pins a contract nothing else stated: `default-property-inclusion: non_null`
   means **an unset field is absent, not null**, so a client must read "missing" as "not set".
4. **Restore.** The HTTP-built quarter dumps, restores into a real scratch database, the restored
   ledger balances and matches row-for-row, and the twelve invariants are then re-run on the **live**
   database to show the cycle left the source untouched. Stated plainly in the test: the twelve are
   **not** re-run *inside* the scratch database, because the verifier owns and drops that connection
   and prising it open would mean changing step 12's production code to suit a test.
5. **`assertEveryRouteCoveredExcept`** — **128/133 driven, 5 excused.** The excuse list started at 43,
   and working through it was the useful part: almost every entry was not unreachable, merely
   unwritten. That became `TradingQuarter.quarterEndHousekeeping` — chart-of-accounts maintenance
   (**which nothing had ever written to over HTTP**), six reversals each on a document created for the
   purpose, a settlement allocated *afterwards* and one allocation released, master-data corrections,
   an asset lifecycle, stock moved. The 5 excused are all the email outbox, for one reason: **no route
   sends mail**, so a narrative that touches no service cannot put a message there — and
   `OutboxEndpointIT` drives all five.

### 🐛 Three more defects, found by the work above

| | What | Found by |
|---|---|---|
| 7 | **Sixteen routes answered `500` to a missing required field** — every reversal route among them — in Boot's legacy body shape, the one response on the surface that is not RFC 7807. A missing field arrived as null and met an `Objects.requireNonNull` written to catch a *programming* error. **The same root pattern as defect 5, one exception class along.** Fixed at the root by `Required`, declared in the request record's compact constructor, so `ReversalCommand` fixes six routes in one statement and a seventh reversible document gets it free; `WebExceptionHandler` now unwraps that cause and reports it as itself rather than as "malformed request body" | `PermissionSweepIT.noRouteFailsOnAnEmptyBody` |
| 8 | **A reversed freight allocation reported `0.00`** for the amount it took back out, because a reversal keeps no lines of its own. The ledger was right and the wire was not: any period report summing freight variance overstated by every reversal in it — a plausible number, wrong, with no document to point at. The view now negates the original's figures while keeping the lines-free structure | the `landedCostVarianceDecomposes` invariant, once the narrative reversed an allocation |
| 9 | **The whole email slice answered `400 "Bad request."` for an id that names nothing**, where every other route on the surface answers `404 "Not found."` — four sites, so on those routes alone a client could not tell a malformed request from a missing record. **The third occurrence of defect 5's pattern.** Fixed by `QueuedEmailNotFoundException` and `EmailAttachmentNotFoundException`, which also preserves ADR 0012's deliberate distinction from the 410 `EmailAttachmentUnavailableException` | one site by the new ArchUnit rule, **three by the behavioural sweep** — see below |

### 🛡️ The recurrence earned a guard: a client's mistake raised as a programming error

**Three occurrences inside one step** (defects 5, 7 and 9) of one root pattern: *an exception type
meaning "our code is wrong" used to tell a caller that their request is wrong.* The message is then
correctly discarded and the caller gets `400 "Bad request."` or a `500` — a response that looks
deliberate. Same shape as proxy self-invocation, which earned its ArchUnit rules after biting across
steps 11–12, and the remedy is likewise one sentence each time.

**Now named in `CLAUDE.md` and guarded three ways, each catching what the others cannot:**

- **`WebAuthorizationRulesTest.clientMistakesAreNotProgrammingErrors`** — no class in `..core.web..`
  may *construct* `IllegalArgumentException`. Build-time and precise, **proven to fail against a
  probe** that named the exact method and line. Blind to anything thrown below the web layer.
- **`PermissionSweepIT.noRouteRefusesWithoutSayingWhy`** — every route, reads with no parameters and
  writes with no body, must not answer a bare `"Bad request."`. **This found three of defect 9's four
  sites**, in the service layer where the ArchUnit rule structurally cannot look. That split is the
  argument for having both.
- **`PermissionSweepIT.noRouteFailsOnAnEmptyBody`** — no route may answer `5xx` to a missing field,
  whatever raised it: a `requireNonNull`, an unboxed null, an `orElseThrow` with the wrong supplier.

**Deliberately *not* a rule: `Objects.requireNonNull` is not banned anywhere.** It is correct on our
own arguments — `ListResponse` uses it properly — and no static rule can tell a caller's omission from
a programmer's. Forbidding it would produce exactly the cries-wolf rule the self-invocation work was
careful to avoid. What it cannot see is recorded in `CLAUDE.md` for review instead: a *wrong but
non-empty* value — an unparseable enum, an id naming another party's record, a date range running
backwards — reaches the handler and is only as good as the message written for it.

### 🐛 Six defects found earlier in the step, each with its own commit

The point of the step, and none of them was reachable from the service layer.

| | What | Commit |
|---|---|---|
| 1 | **Every VAT rate crossed the wire as a JSON number.** Step 14 applied the money-as-strings rule to `Money`/`UnitCost`/`Quantity` and not to the raw `BigDecimal` rates beside them. Found in a route four tests already called. Fixed by a **`Rate` value type**, which also gave the factor-of-100 bound one home instead of two. **No migration** — and that is tested per value, not assumed | `7c4c2c4` |
| 2 | **`FreightAllocationLineView.basis`** — an exact 12-decimal product crossing as `540.0`. Now a derived accessor rather than a record component, so it is off the wire and unchanged for Java callers. Zero production consumers; the one test that reads it passes **unmodified**, which is the evidence it carried nothing | `5bf069c` |
| 3 | **AR and the open items disagreed** — impossible by construction per ADR 0009. **Three separate causes**, isolated by measurement: a credit note against a born-settled invoice moved AR when its invoice never had; reducing a settlement stranded the customer credit it left; and the invariant itself was not counting unallocated credits | `fc217ea` |
| 4 | **`GET /api/open-items` omitted customer credits**, under-reporting a customer's position. Fixed before step 16 builds screens against it. It also exposed the invariant summing open amounts **unsigned** — right until now only because every credit note in every scenario happened to be fully allocated | `6d85c89` |
| 5 | **Seventeen parameter messages across nine controllers were discarded.** The controllers signalled a *client* mistake with `IllegalArgumentException`, which step 14 had already decided means a *programming* error: logged, caller gets `"Bad request."` `InvalidRequestException` now carries them | `1421dfb` |
| 6 | **Spring's own refusals returned a different body shape entirely** — Boot's legacy `{"timestamp","status","error","path"}`, no `detail`, so a client cannot read errors uniformly. Fixed with `spring.mvc.problemdetails.enabled` | `1421dfb` |

**⚠️ Defect 6's fix had a cost, and finding it is the argument for the whole step.** Turning
problemdetails on registers a second advice over the same framework exceptions, and Boot's won:
`HttpMessageNotReadableException` started answering `"Failed to read request"`, **replacing step 14's
most load-bearing message** — the one telling a client that an amount must be a JSON string and not a
number. The test asserting that rule went red immediately. `WebExceptionHandler` is now
`@Order(HIGHEST_PRECEDENCE)`. A fix that quietly broke the money contract is exactly what a
validation step exists to catch.

### The API corrected the scenario seven times, which counts as much as the defects

Each of these was the system being right and the narrative being wrong:

- **The €500 legal cash limit** (brief §6, N. 5301/2026) is a **hard block with no override**,
  enforced in three services. A 700.00 cash receipt was refused; a real operator banks it.
- **One customer's credit cannot settle another's invoice.**
- **A product's unit of measure cannot be changed once it has lots** — "reinterpreting a recorded
  quantity in a different unit is not a correction, it is a different quantity."
- **The consumption and write-off listings require a date range or an id**, and now say so.
- **A card or cash sale is born fully settled and never has an open amount**, so it cannot take a
  later allocation. `ON_ACCOUNT` is the only method that leaves a receivable — which is the whole
  point of it, and the housekeeping chapter now says so where it chose an invoice to allocate against.
- **A line already credited in full cannot be credited again**: "crediting more than was sold would
  reclaim output VAT that was never charged."
- **Stock that was never there cannot be lost** — a write-off was aimed at the filter lot, which
  March's oversell had emptied.

### ✅ Q21 revised — no field is restricted from any role (V26)

**Decision, not an omission.** V6 hid a product's cost, supplier and supplier SKU from Remote/Order
Staff; **V26 removes all three.** The business has no confidentiality need behind them — a bank
balance might reasonably stay hidden from a home-based worker, what a bag of beans cost does not.

**The consequence is wider than three rows.** `ProtectedField`'s three values are the only fields the
mechanism knows about and that role held the only restrictions, so **no role has any field restriction
and the inner layer of brief §7's two-layer model is now unused.**

**Expressed as deleting data, not code.** `ProtectedField` keeps all three values, the CHECK keeps
listing them, and `RoleView.canSee`, `ProductView.redactedFor`, the supplier-implies-supplier-SKU
narrowing and the three ArchUnit rules are untouched. Restricting one again is an `INSERT`. A future
case was named when this was decided: **a bank or partner-clearing balance.**

**⚠️ The trap this creates, and what closes it.** With no restriction in real data, a change that
stopped `ProductService`'s `...For` reads consulting the role would pass every test while removing the
guarantee — the shape of the audit-log defect step 12 found. So **every test that proved redaction via
the seeded role now creates a role and restricts a field at runtime** (`RoleService.restrictField`;
roles are data, which is what makes it possible without a migration): `ProductIT`,
`BundleIT` (both `allBundlesFor` cases, which is what step 14c's `f2e8e06` existed to guarantee),
`SecurityIT`, and `MasterDataEndpointIT`, which asserts against the bytes that staff and owner now
receive the **same** product on both the single read and the list — separate code paths, so "both show
everything" is as much a claim as "both hide it" was.

One thing learned in the doing: the first version of `SecurityIT`'s sweep asserted that *no role
anywhere* restricts anything and **failed on execution order** — the throwaway restricted roles the
other tests create share this database. The claim is about what V6 seeds, so it is scoped to that. A
test that passes or fails on ordering is worse than a narrower one.

### Two things worth carrying forward

- **`mvn compile` without `clean` reported `BUILD SUCCESS` against stale classes** after a type change
  in an upstream module, three separate times — once producing a class file that threw
  `"Unresolved compilation problems"` at runtime because the IDE had written it. **After changing a
  signature in `core-api`, build with `clean`** or the result means nothing.
- **`post(String, String)` and `post(String, Object)` overload silently.** A misplaced bracket sent ten
  request descriptions as request *bodies* and compiled cleanly. The narrative uses a single-shape
  helper now; the overload stays because raw-JSON requests are what the refusal matrix needs.

---
## Next action — read this first

**Steps 0–15 are complete, committed and pushed. `mvn clean verify` is green at 1152 tests, 0
skipped.**

**There is no known correctness defect in the ledger**, and step 15 is the strongest evidence this
project has for that claim: a full trading quarter, built by nothing but HTTP requests, satisfies all
twelve universal invariants — and still does after being dumped, restored into a fresh database and
swept again.

### ➡️ Next: step 16, the frontend.

**Step 15 is done, and it earned its place ahead of step 16 nine times over.** Every one of the nine
defects it found is one step 16 would otherwise have hit through a second layer, with two candidate
causes for every symptom. The three found last — a `500` on sixteen routes given a form with a field
missing, a reversal document reporting `0.00`, and a whole slice answering `400` where the rest of the
surface answers `404` — are exactly the things a form, a report and an error toast collide with on day
one.

**What step 16 can now rely on, which it could not before:** every route has been driven or excused in
writing; every refusal is RFC 7807 with a status a client can branch on; every role's access to every
route is asserted rather than assumed; no route answers a bare "Bad request." or a 500 to a bad form;
and `?from=`/`?to=` are proven inclusive at both ends.

**One thing it must still not rely on: no human has used a browser.** Every route that runs, runs
because a test asked it to. The frontend has no login screen.

**PLB-1 (2FA) is the only pre-launch blocker outstanding** — deferred, unchanged, and still blocking
any external or remote access.

**PLB-1 (2FA) stays deferred**, because its trigger condition has not arrived: it must be resolved
before *any* external or remote access, and there is none. That is a condition, not a date — see
the pre-launch blockers section above.

⚠️ **Two things to carry into whatever comes next.**

- Q45 survived twelve build steps because every example test in the suite used whole-cent costs. The
  generated tests found it in their first run. **Step 15 is the same lesson from a different
  direction**: the `Rate` defect, the `basis` defect and the AR discrepancy all survived because no
  test had ever driven those routes over HTTP, or driven that combination of documents at all. The
  argument is not "write more property tests" specifically — it is that **a checker only covers what
  it is actually pointed at**, and both steps found their defects by pointing an existing kind of
  check somewhere new.
- **A decision recorded with its reasoning can be reversed on evidence; one recorded as a bare rule
  cannot.** Two deliberate decisions were overturned this step — the credit note always crediting AR,
  and Q21's product redaction — and in both cases the *stated reason* was what made it possible to
  tell whether the reversal was sound. Both old reasons are kept in the javadoc rather than deleted.

### Credential housekeeping — done, nothing outstanding

Both items raised at the end of step 11 are closed. The `kostas` password was rotated through
`UserService.changePassword` against the live database and verified by logging in over HTTPS (the
old password now returns 401), and all three consumed bootstrap variables were removed from
`docker/.env`, after which the app was recreated and starts clean. See "To be aware of immediately"
above for the current state.

### ~~Step 12 needs Q24~~ — answered and built

Q24 was answered 2026-07-29 (Google Drive API, OAuth, encrypted at rest) and step 12 is built on it.
What remains is operational, not a decision: the two destinations' folder ids and OAuth credentials.

### Waiting on the accountant, and blocking real data rather than code

- **Statutory depreciation rates per asset category**, plus the category taxonomy. The field exists and
  is nullable; **do not create real assets with real values until these are confirmed.**
- **AADE exemption codes 24 and 28** (Q35), **the OSS/IOSS myDATA codes** (Q36), and **the myDATA
  unit-of-measure codes** (Q38) — all before phase 7, all NULL and fail-loud in the meantime.

### Also still open, not blocking anything

- **Q41 — after-the-fact GR/IR matching.** Belongs with phase 8's Clearing Checks; needs an answer to
  "whose document is the variance entry?".
- **Q42** — a bundle containing a serial-tracked component cannot be sold as a bundle. The machine sells
  on its own line.
- **Q28 — dispatch purpose placement.** Recommendation is a core-owned `GoodsDispatch` in phase 4,
  conditional on whether Go already issues Δελτία Αποστολής and whether the AADE digital delivery note
  regime applies (accountant question). Nothing built.
- **Q32 — the 8-hour session timeout.**
- **Q37 — addresses on Customer and Supplier**, plus human-facing codes and multiple selling prices.
- **Q40 — a human-facing document number** for the documents NovoCore owns. Step 10 adds one more to
  the list: a freight allocation has no number either, only an id.
- **Q12 leftover — is the periodic depreciation posting run Phase 1 scope**, or only the register and
  the calculation? Still waiting on the statutory rates either way.
- ~~**Q43**~~ — **answered and built (V22).** See the section below.
- ~~**Q44**~~ — **both halves answered and built in step 14c** (`b8aa9e2`). The section half is
  `Section.EMAIL_OUTBOX`, its own grant rather than part of `SETTINGS`; the access-path half is built
  in `EmailSenderImpl` and proven behaviourally, and it needed a piece the decision could not have
  anticipated — `AttachmentOwnerType`, because `entityType` is free text and there was nothing to
  check against. **See the step 14 section above.** The original decision record follows, unchanged.

#### ✅ Q44's access-path half — decided 2026-07-29, **built in step 14c**

**`EmailSender.downloadAttachment` must re-check the caller's permission against the underlying core
record before returning bytes for a *referenced* (stored) attachment**, using the authorization
already in place — `RoleView.requireView(Section…)` for the section and `RoleView.canSee(ProtectedField)`
for field-level restrictions, the same primitives `ProductView.redactedFor(RoleView)` composes.

**The principle:** *an email having been sent to someone does not change who is allowed to see the
source document afterward.* The outbox must not become a second, weaker access path to restricted
data. Without this check, a role that cannot open a purchase invoice could read that invoice's PDF out
of the email that sent it — the permission model intact on one route and bypassed on the other.

This is a **direct consequence of V21** and did not exist before it. While the outbox held its own copy
of the bytes, the mail's attachment was arguably the mail's own business; now it is a pointer into
`attachment`, which belongs to a core record with its own visibility rules. Referencing removed the
duplicated storage and, with it, the excuse for a duplicated access rule.

**Scope of the check, so it is not over- or under-applied when built:**

- **Referenced attachments only.** An inline generated PDF has no core record behind it, therefore no
  record-level permission to consult; it is governed by whatever `Section` the outbox itself gets.
- **The check is on the referenced document's `entity_type` / `entity_id`**, which
  `email_outbox_attachment.attachment_id` reaches via `AttachmentService.findMetadata`.
- **A deleted reference needs no check** — there is nothing left to authorise, and the entry already
  reports itself unavailable.
- **`attachmentsOf` returns metadata only** (filename, size, availability), no bytes. Whether a
  *filename* is itself restricted is a `Section` question, not this one.

**Nothing is built yet, and nothing is exposed**: there is still no HTTP route to the outbox at all, so
this is not a live vulnerability today. It is recorded here, in `EmailSender.downloadAttachment`'s
javadoc, and in ADR 0012 precisely so it is a requirement being implemented rather than a gap being
discovered.

### Standing note

The REST surface is deliberately still one endpoint — **the ledger, inventory, purchasing, sales,
landed costs and now the email outbox all have no HTTP route at all.** Building out the rest of the API
needs its own scoping conversation, not incremental drift. When it happens, these lower-layer methods
must **not** be what a controller exposes: `JournalService.post` (use `postManualEntry`),
`InventoryService.receive` / `unreceive` (use `GoodsReceiptService`), `InventoryService.consume` /
`returnConsumed` (use `SalesInvoiceService` and `CreditNoteService`), **`InventoryService.applyLandedCost`
/ `removeLandedCost` (use `FreightAllocationService`)**, and `ProductService`'s unredacted reads (use the
`...For(viewer)` variants). **PLB-1 (2FA) must be closed before any remote access is enabled** —
including Remote/Order Staff logging in from outside the local network, which is that role's entire
purpose.

**Step 11 adds one to that list, in the other direction.** Any feature that needs to send something
calls `EmailSender.send` and composes an `EmailMessage`. It **cannot** set a From or a Reply-To,
cannot configure SMTP, and cannot construct a mail session — an ArchUnit rule confines
`jakarta.mail` and `org.springframework.mail` to `..core.email..`. If `EmailSender` cannot express
what a module needs, **add to that interface rather than around it**, exactly as rule 3 says for
adapters.
