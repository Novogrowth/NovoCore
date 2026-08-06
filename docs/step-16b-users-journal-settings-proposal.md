# Step 16b — Users & Roles, Journal listing, Settings API

**Proposal for review. Nothing below is built.** Backend only; `/frontend/` is untouched.

Baseline: 137 routes, 1188 tests, 0 skipped, migration V27, `main` clean at `2ac6302`.

---

## 0. Four things the kickoff brief says that the repository contradicts

These are not quibbles — three of them change what gets built, and one of them is a hole in a
security control that this step would otherwise widen.

### 0.1 The cash payment limit is **already a Setting**, not a hard-coded rule

The brief says: *"The cash-payment legal limit (€500, Ν. 5301/2026) is currently a hard-coded
business rule, not a Settings value — leave it hard-coded."*

It is not hard-coded. `cash.payment.limit` is seeded in
[V2__settings.sql:48-51](backend/core/src/main/resources/db/migration/V2__settings.sql#L48-L51) and
read through `settings.requireEurAmount(...)` at three call sites — sales invoices, settlements and
bank transfers. So "leave it hard-coded" is not an option that exists; the only question is whether
the new Settings API **exposes** it.

**Decided: read-only, exposed under `Section.SETTINGS` at `VIEW` like every other catalogued
setting.** Writing it over HTTP is refused because it is the one refusal in this design that offers
no confirmation path — per
[SettlementMethod.java](backend/core-api/src/main/java/gr/novotrade/novocore/core/api/sales/SettlementMethod.java),
*"the confirmation nobody can give is legality."* A screen that lets an operator raise a statutory
limit makes breaking the law a two-click operation with an audit entry that reads like configuration.
It stays changeable by SQL, which is a deliberate act by somebody who knows what they are doing.

#### ⚠️ The first version of this section justified the read wrongly, and the correction matters

It argued the read exists so that *"the number behind a refusal is visible to whoever hit the
refusal."* That rationale does not survive contact with the permission model: whoever hits this
refusal is recording a cash sale or a cash receipt, so they hold `SALES` or `SETTLEMENTS` — and
`SETTINGS` is default-deny, seeded to nobody. Under `SETTINGS`/`VIEW` the value would be invisible to
every person the argument named.

**But the need it invoked is already met, twice, and better:**

1. **The refusal interpolates the limit.** All three call sites
   ([SalesInvoiceServiceImpl:583](backend/core/src/main/java/gr/novotrade/novocore/core/sales/SalesInvoiceServiceImpl.java#L583),
   [SettlementServiceImpl:877](backend/core/src/main/java/gr/novotrade/novocore/core/settlement/SettlementServiceImpl.java#L877),
   [BankTransferServiceImpl:226](backend/core/src/main/java/gr/novotrade/novocore/core/banking/BankTransferServiceImpl.java#L226))
   throw *"A cash sale of EUR 600.00 reaches the legal cash limit of EUR 500.00 and is blocked…"*,
   and all three exceptions map to **422 carrying the core's own message** — step 14's deliberate
   asymmetry, where a validation refusal explains itself and a permission refusal does not.
2. **The preview enforces it before submission.** `preview` calls `compute`, which calls
   `requireWithinCashLimit` at
   [SalesInvoiceServiceImpl:186](backend/core/src/main/java/gr/novotrade/novocore/core/sales/SalesInvoiceServiceImpl.java#L186).
   So step 16a's preview extraction means a cash-sale screen gets the refusal, naming the limit,
   **while the form is being filled in** — not after the operator has typed 600 and pressed save.

Both paths reach a caller holding `SALES` and nothing else. A lighter-weight exposure would therefore
serve an already-served need, and serve it worse: a bare scalar the operator has to connect to their
own situation, instead of a message attached to the specific transaction saying why it was refused.
It would also put a configuration value on a feature section — the scattered-configuration pattern
`SettingsService` exists to prevent (`CLAUDE.md`).

**There is already a precedent for this, which is what makes it a pattern rather than a rationalisation.**
`SalesInvoicePreview` returns `rounding().threshold()`, so `ledger.rounding.threshold` is *already*
visible to a `SALES` caller through the document it governs, with no `SETTINGS` grant. The rule this
codebase is already following: **a setting that governs a document reaches the operator through that
document; a settings read is for the administrator reviewing configuration.**

**So the reader this route actually serves is the administrator** — who holds `SETTINGS` by
definition, making default-deny correct rather than a contradiction. Keeping it catalogued also
matters in its own right: omit it and `GET /api/settings` becomes a list that silently lacks a
setting that exists, leaving an admin no way to learn its current value short of reading source or
opening `psql`. A settings screen with an invisible setting is its own defect.

**The catalogue entry must record *why* it is read-only** — that it is a statutory limit, not a
technically immutable value — or the missing write route reads like an oversight somebody later
"fixes".

### 0.2 Journal entry numbering (Q40) was **not** decided

The brief says: *"Human-facing journal entry numbering (continuous, source-prefixed) was decided but
never built."*

`HISTORY.md`, *Step 14 → Not blocking anything, but unanswered*, records the opposite, explicitly:

> **Q40** Does a journal entry need a human-facing entry number? The id is the handle today. […] it
> carries a format decision nobody has been asked — per-year reset? a prefix per source? Nothing was
> guessed.

and Q40 still appears under *Also still open*, widened by step 10 to cover freight allocations too.
The context primer says the same. So there is no decision to implement.

> ⚠️ **Corrected by U2a, 2026-08-06, and the way it was wrong is the point.** Both citations above
> were **line numbers** — `PROGRESS.md:3194` and `PROGRESS.md:3609` — and **both had drifted long
> before U2a touched anything**: 3194 had become Q1's guard-count table and 3609 F4's javadoc
> finding. Neither mentioned Q40. Nothing failed, and nothing could have. **A reference by line
> number into an append-only file is broken by construction** — see `CLAUDE.md`, *a citation by line
> number into an append-only file*. Cited by section now.

**Recommendation: defer, and say so in the listing.** Reasons, in order of weight:

1. **The format is genuinely undecided and unguessable.** Per-year reset or continuous forever?
   Prefix per source (`SI-`/`PI-`/`MJE-`) or one sequence? Zero-padded to what width? Each answer is
   visible on every printed page an accountant ever sees, and none of them is reversible once entries
   carry numbers.
2. **It is not a listing feature, it is a data feature.** It needs a migration, a column, a
   gap-free allocation strategy (a PostgreSQL sequence has gaps on rollback — for a *continuous*
   number that is a defect, not a detail), and a backfill of every entry already posted.
3. Q40 is broader than the journal. Purchase invoices, goods receipts and freight allocations have
   the same gap. Answering it for journal entries alone creates the inconsistency it exists to fix.

The listing will therefore show `id`, as everything else does today. **If you want it in scope, it
is a step of its own and I will propose it separately** rather than fold it in here.

### 0.3 🛡️ Narrowing a role does **not** end sessions today — and this step is what makes it reachable

The brief says session eviction is *"already built — this step just needs to keep triggering it
correctly from the new routes, not reimplement it."*

Half true. `UserSessions.endAllFor(userId)` is called from exactly two places, both in
[UserServiceImpl.java:223](backend/core/src/main/java/gr/novotrade/novocore/core/security/UserServiceImpl.java#L223)
and `:255` — `deactivate` and `changeRole`. **`RoleService` has no `UserSessions` dependency at
all.** So today:

| Operation | Ends sessions? | Should it? |
|---|---|---|
| `UserService.deactivate` | ✅ yes | yes |
| `UserService.changeRole` | ✅ yes | yes |
| `RoleService.grant(role, section, NONE)` — **revoking access** | ❌ **no** | **yes** |
| `RoleService.grant(role, section, VIEW)` where it was `FULL` | ❌ **no** | **yes** |
| `RoleService.restrictField(role, field, true)` | ❌ **no** | **yes** |
| `RoleService.grant(...)` widening | ❌ no | no — correct as is |
| `UserService.rename` / `changeLanguage` | ❌ no | no — correct as is |

This has been latent and harmless because there is no route to `RoleService.grant` — role editing is
direct SQL, and a `UPDATE` to `role_section_grant` never went through Java at all. **The moment this
step ships `PUT /api/roles/{id}/grants/{section}`, revoking a section from a role becomes a
one-click operation that leaves every holder of that role still using it** for up to eight hours.
That is the exact failure `UserSessions` was built to prevent, arriving through the door this step
opens.

**So this step must close it.** Proposed below in §1.4. It is not "reimplementing" eviction — the
mechanism is reused unchanged; what is missing is a call site and a way to enumerate the users
holding a role.

### 0.4 VAT Class and Unit of Measure are not `SETTINGS` — they already have sections

The brief groups them under Settings. The codebase already assigns them elsewhere, with read routes
shipped in step 14:

- `GET /api/vat-classes`, `/api/vat-exemption-reasons`, `/api/charge-types` →
  `Section.TAX_AND_CHARGES`
- `GET /api/units-of-measure` → `Section.PRODUCTS`

**Recommendation: add the write routes under the sections their reads already live in.** Moving them
to `SETTINGS` would mean either two sections governing one resource, or moving the existing reads —
which changes what `/api/me` reports, breaks the `PermissionSweepIT` prefix table, and contradicts
`Section.TAX_AND_CHARGES`'s own javadoc. "Settings" in the brief means *operator-editable
configuration*, which these are; it does not have to mean `Section.SETTINGS`.

`VatClassService` and `UnitOfMeasureService` already carry every write operation needed. This is
purely web-layer work.

---

## 1. Users & Roles

**Section: `USERS_AND_ROLES`. `VIEW` for reads, `FULL` for writes.** No route is hard-coded to
Owner/Admin — see §1.5 for why, and for the escalation guard that replaces it.

### 1.1 Routes — users (9)

| Method | Path | Level | Service call | Response |
|---|---|---|---|---|
| GET | `/api/users` | VIEW | `all()` / `active()` via `?active=true` | `ListResponse<UserView>`, unpaged |
| GET | `/api/users/{id}` | VIEW | `require(id)` | `UserView` |
| POST | `/api/users` | FULL | `create(NewUser)` | 201 `UserView` |
| PATCH | `/api/users/{id}/display-name` | FULL | `rename` | `UserView` |
| PATCH | `/api/users/{id}/role` | FULL | `changeRole` | `UserView` — **evicts** |
| PATCH | `/api/users/{id}/password` | FULL | `changePassword` | 204 — **evicts, see §1.3** |
| POST | `/api/users/{id}/deactivate` | FULL | `deactivate` | 204 — **evicts** |
| POST | `/api/users/{id}/reactivate` | FULL | `reactivate` | 204 |
| GET | `/api/sections` | VIEW | `Section.values()` | `ListResponse<SectionView>` |

`POST …/deactivate` rather than `DELETE`, matching the convention every other controller uses
(`/api/products/{id}/deactivate`, `/api/customers/{id}/deactivate`, …). Commands, not CRUD.

`GET /api/sections` returns `{name, available, description}` for all 17 values — what a role editor
renders its grid from. `/api/me` already returns the *current user's* levels; this is the catalogue.

**Deliberately not built:** an admin route for `changeLanguage`. `PATCH /api/me/language` already
exists and is the real use; an admin setting somebody else's display language is a route with no
caller. Say if you want it.

### 1.2 Routes — roles (8)

| Method | Path | Level | Service call | Response |
|---|---|---|---|---|
| GET | `/api/roles` | VIEW | `all()` / `active()` | `ListResponse<RoleView>` |
| GET | `/api/roles/{id}` | VIEW | `require(id)` | `RoleView` |
| GET | `/api/roles/{id}/users` | VIEW | new — see below | `ListResponse<UserView>` |
| POST | `/api/roles` | FULL | `create(NewRole)` | 201 `RoleView` |
| PATCH | `/api/roles/{id}/name` | FULL | `rename` | `RoleView` |
| PUT | `/api/roles/{id}/grants/{section}` | FULL | `grant` | `RoleView` — **may evict** |
| PUT | `/api/roles/{id}/field-restrictions/{field}` | FULL | `restrictField` | `RoleView` — **may evict** |
| POST | `/api/roles/{id}/deactivate` | FULL | `deactivate` | 204 |
| POST | `/api/roles/{id}/reactivate` | FULL | `reactivate` | 204 |

`{section}` and `{field}` are bound to the `Section` and `ProtectedField` enums, so an unknown value
is refused by Spring before any of our code runs and the accepted values land in the OpenAPI
document — the same reasoning `Paging` states for sort keys.

`PUT` rather than `POST` for the two grant routes because they are genuinely idempotent replacements
of one cell: granting `VIEW` on `SALES` twice is the same state. Body is
`{"accessLevel": "VIEW"}` / `{"restricted": true}`, each a record with `Required.field` in its
compact constructor so a missing field is a 422 with a message rather than a 500 (the named
anti-pattern, instance 2).

`GET /api/roles/{id}/users` is new and needed: `RoleService.deactivate` refuses while any user holds
the role, and an operator who hits that refusal needs to know *who*. Backed by a new
`UserService.inRole(long roleId)`.

**Field restrictions stay wired even though nothing uses them.** V26 removed every restriction, so
`restrictedFields` is empty for every role in real data. The route, the enum and `RoleView.canSee`
are untouched — restricting one again stays an `INSERT`, exactly as the V26 decision intended. The
tests build their own restricted role at runtime, as `ProductIT` and `SecurityIT` already do.

### 1.3 The password reset question

`PATCH /api/users/{id}/password` lets an administrator set another user's password. **It should end
that user's sessions**, and it does not today — `changePassword` has no `endAllFor` call. An admin
resetting somebody's password is, overwhelmingly, either containment of a compromise or an
offboarding; leaving the compromised session alive is the same defect as §0.3.

**Recommendation: add eviction to `changePassword`.**

**Separately, and this is a question:** there is still no *self-service* change-password route — the
primer records that rotating the owner's password in July meant a one-off call to `UserService`. It
is one route (`POST /api/me/password`, verifying the current password first, evicting every *other*
session but not the caller's). It is arguably outside "Users & Roles admin", and it is the single
most obviously missing route on the whole surface for a frontend about to build a login screen.
**Say whether it is in scope; I will not build it silently either way.**

### 1.4 Closing §0.3 — eviction on narrowing

Design, deliberately minimal:

- `RoleServiceImpl` gains the existing `UserSessions` bean as a constructor argument. It already has
  `UserRepository` (it uses `countByRoleId`), so enumerating holders is one new query method,
  `findIdsByRoleId`.
- `grant(roleId, section, level)` reads the role's current level for that section **before** writing.
  If `newLevel < oldLevel` on the `NONE < VIEW < FULL` ordering, every user holding the role has
  their sessions ended, inside the same transaction — the `UserSessions` contract requires it, so a
  rolled-back revocation cannot log people out and, more importantly, a committed one cannot leave
  them logged in.
- `restrictField(roleId, field, true)` — narrowing — evicts. `false` — widening — does not.
- Widening a grant does not evict. **This asymmetry is the point** and is asserted in both
  directions, not just tested one way.
- `deactivate(roleId)` needs no eviction: it already refuses while any user holds the role, so there
  is nobody to evict. A test asserts that, so the reasoning survives a future change to that refusal.

**No proxy self-invocation.** `grant` and `restrictField` are already `@Transactional` public methods
on `RoleServiceImpl`; the eviction is an inline call to an injected bean, not a self-call, so neither
`SelfInvocationRulesTest` rule is engaged. Stated because `CLAUDE.md` says to watch for it.

**Proven behaviourally, not structurally.** Each of the four new eviction assertions will be
confirmed to fail with the eviction call removed, then restored — the step-12 audit-log lesson
applied on purpose. A structural test ("`RoleServiceImpl` depends on `UserSessions`") would pass
against code that injects it and never calls it.

### 1.5 Permission for these routes — and the escalation this opens

**Recommendation: `@Requires(section = USERS_AND_ROLES, ...)` like every other route. Not a
hard-coded Owner/Admin check.**

In practice that *is* Owner/Admin-only today: no seeded role is granted `USERS_AND_ROLES`, and
access is default-deny. But it stays a grantable section, which matters for three reasons — it is
what `Section.USERS_AND_ROLES` exists for; `PermissionSweepIT`'s granted-everywhere role (which
asserts every read is admitted by *stored grants*, not by the `fullAccess` flag) would fail against a
route no grant can satisfy; and hard-coding "system roles only" would make brief §7's "multiple
custom roles from the start" untrue for the one section that administers roles.

**The consequence has to be stated rather than discovered.** A custom role with `FULL` on
`USERS_AND_ROLES` can create a role, grant it `JOURNAL`, and move itself into it — a privilege
escalation to everything. Two existing guards partly cover it: `RoleService.create` refuses to
create a `fullAccess` role, and system roles cannot be edited. Neither stops the above.

**Proposed guard: a user may not modify the role they themselves hold, and may not change their own
role.** Cheap, precise, and it turns escalation into a two-person act. It needs `CurrentUser` in
`RoleServiceImpl`/`UserServiceImpl`, which is already the pattern the redaction reads use.

**This is a decision, not a default — confirm it.** The counter-argument is real: it means a sole
Owner cannot fix their own role, and if it were ever the last full-access account that is a lockout.
Mitigated by the fact that system roles are unmodifiable anyway (so Owner and Admin are unaffected —
the guard only ever bites a *custom* role), which I think makes it safe. Flagging it because
"unmodifiable" and "locked out" are one bad interaction apart.

---

## 2. Journal entries — the listing

**Section: `JOURNAL`, `VIEW`.** Two routes, plus one existing gap closed.

### 2.1 Routes (3)

| Method | Path | Level | Response |
|---|---|---|---|
| GET | `/api/journal-entries` | VIEW | `ListResponse<JournalEntrySummaryView>` **paged** |
| GET | `/api/journal-entries/{id}` | VIEW | `JournalEntryView` (full, with lines) |
| GET | `/api/accounts/{id}/ledger` | VIEW | `ListResponse<JournalLineView>` **paged** |

The third is `JournalService.linesOf(accountId, from, to)` — the account ledger, which already exists
and has no route. It belongs with this step: a journal screen with no drill-into-an-account is half a
screen, and it is the one journal read genuinely unbounded (one bank account over a year is every
transaction that touched it). Under `JOURNAL`, not `CHART_OF_ACCOUNTS` — the section javadoc is
explicit that seeing the list of accounts and seeing what has posted to them are different grants.

**Journal writing is deliberately absent.** `postManualEntry`, `amend` and `reverse` all have
services and no routes; this step is the listing the brief asked for. Manual journal entry as a
*screen* is a design conversation (line editor, account picker, balance-as-you-type) that belongs
with the frontend step, not smuggled in behind a listing. Say if you want the write routes now.

### 2.2 ⚠️ The summary view, and why the list cannot return `JournalEntryView`

Two independent reasons, both hard:

1. **`left join fetch e.lines` and pagination do not mix.** Every existing entry query fetch-joins
   the lines. Hibernate cannot apply `LIMIT`/`OFFSET` to a query with a fetched collection, so it
   silently loads **the entire result set into memory** and pages it there (`HHH000104`). On the one
   table in this system expected to reach hundreds of thousands of rows, that is not a slow page —
   it is an out-of-memory on page 1.
2. **`JournalEntryView` refuses to exist without ≥2 lines** — its compact constructor throws,
   deliberately, calling a line-less entry "a projection bug rather than bad data". It cannot be
   reused line-free even if we wanted to.

**So: `JournalEntrySummaryView`**, new in `core-api`:

```java
record JournalEntrySummaryView(
        long id, LocalDate entryDate, String description, JournalSource source,
        Long reversalOfEntryId, Long reversedByEntryId,
        Money total,          // = total debits = total credits, one figure because they are equal
        int lineCount) { }
```

`total` is one figure, not two, because rule 6 makes them equal by construction — returning both
would invite a reader to compare them and conclude something from a difference that cannot occur.
Computed per page by a single `sum(...) group by entry_id` over the page's ids: two queries per page,
never N+1, and never the whole table.

### 2.3 Filters — proposed set

| Parameter | Type | Meaning |
|---|---|---|
| `from` / `to` | date, inclusive | on `entryDate`, **not** `createdAt` — a backdated entry belongs in the period it is dated to |
| `accountId` | long | entries with at least one line on this account |
| `source` | `JournalSource` | **all ten values, not the six the brief lists** |
| `subLedgerType` + `subLedgerId` | enum + long | entries touching one customer / supplier / lot / asset |

All optional and combinable; `from`/`to` deliberately optional, unlike `/api/sales-invoices` which
requires a range. A ledger screen's landing view is "everything, most recent first", and the list is
paged, so an unbounded query is bounded by the page. Requiring a range would force a frontend to
invent one.

**On `source`: the brief lists six types. There are ten.** `GOODS_RECEIPT`, `CREDIT_NOTE`,
`FREIGHT_ALLOCATION` and `INVENTORY_WRITE_OFF` are all real, all posting today, and all things an
accountant filters for. Binding the parameter to the enum means the filter cannot fall out of date.

**On the sub-ledger filter:** `JournalService.linesFor(SubLedgerRef)` already exists unpaged, and it
is what makes a Control account reconcilable rather than merely declared to be. Included because it
is the same query shape and near-free here; **say if you would rather it waited.**

### 2.4 Sort keys — `JournalEntrySort`

`ENTRY_DATE` (natural order), `RECORDED_AT` (`createdAt` — genuinely different from the entry date
and what "what did somebody type in yesterday?" means), `SOURCE`.

**Deliberately absent: sort by amount** — for the reason `SalesInvoiceSort` writes down about invoice
totals. An entry's total is not a column; it is the sum of its lines. Ordering by it means either a
correlated subquery on every page or a stored total that can disagree with the lines.

Natural order is `entryDate` ascending, matching `entriesBetween`, with `id` breaking ties as
`SpringPaging` enforces for every ordering. A screen wanting newest-first sends
`?sort=ENTRY_DATE&direction=DESC`.

### 2.5 New service methods

`JournalService` gains `pageOfEntries(JournalEntryFilter, PageRequest)` and
`pageOfLines(long accountId, LocalDate from, LocalDate to, PageRequest)`. The existing unpaged
`entriesBetween` / `linesOf` / `linesFor` stay — the core's own callers use them and they are not a
web surface. `JournalEntryFilter` is a record of the four optional filters, so adding a fifth later
is not a fifth overload.

---

## 3. Settings

### 3.1 ⚠️ The exclusion the brief asks for is not achievable with the service as it stands

The brief requires that the backup encryption key and the Drive OAuth credentials can never be
reached through a Settings route, *"structural rather than just a code-review reminder."*

The encryption key is already safe by construction — it is an environment variable and has **no row
in the `setting` table at all**, precisely because the table is inside the dump it decrypts
(ADR 0013). Nothing to exclude.

**The Drive credentials are a different story, and the naive Settings API breaches the requirement
on both verbs:**

- `SettingsService.listRedacted()` returns **every row**. `backup.drive.primary.client-secret` and
  `.refresh-token` are marked `secret` so their *values* redact — but
  `backup.drive.primary.folder-id` and `.client-id` are **not marked secret** (V23:267-282) and would
  be returned in the clear.
- `SettingsService.put(key, value)` takes **any key and any string**. A generic
  `PUT /api/settings/{key}` would happily write a refresh token, or invent a brand-new key, or set
  `ledger.rounding.threshold` to `"abc"` — which stores fine and then throws `SettingValueException`
  on the next document posted, from a write that reported success.

### 3.2 So: an allowlist, expressed as an enum, not a key/value passthrough

**`SettingsCatalog`** — a new enum in `core-api`, one constant per setting the API exposes, each
carrying its key, its type, whether it is writable, and whether it is write-only.

The route binds `{key}` to that enum. So a key outside the catalogue is refused **by Spring, before
any of our code runs** — the same mechanism as the sort parameter. Not a runtime check that could be
forgotten: a key not in the enum has no route, and the enumerated set appears in the OpenAPI
document.

**Proposed catalogue** — the full audit of the 28 seeded keys, with a disposition for every one:

| Key | Type | Exposed |
|---|---|---|
| `ledger.rounding.threshold` | Money EUR | read + write |
| `ledger.rounding.mode` | RoundingMode | read + write |
| `cash.payment.limit` | Money EUR | **read only — statutory, not technically immutable** (§0.1) |
| `attachment.max-size-bytes` | int | read + write |
| `smtp.host` / `.port` / `.username` / `.from-address` / `.from-name` / `.reply-to` | string / int | read + write |
| `smtp.transport-security` | `EmailTransportSecurity` | read + write |
| `smtp.password` | secret | **write only** — value never returned, redacted on read |
| `email.max-attempts`, `email.retry.backoff-seconds`, `email.retry.backoff-max-seconds`, `email.dispatch.batch-size` | int | read + write |
| `email.retention.message-days`, `email.retention.inline-attachment-days` | int or `FOREVER` | read + write |
| **the entire `backup.*` namespace (11 keys)** | — | **excluded, read and write** |

**Excluding all of `backup.*` rather than only the four credentials** is the substance of the
recommendation. Per-key exclusion would have to individually catch `folder-id` and `client-id`, which
are *not* flagged secret and would be trivially missed by anyone adding a third destination. A
namespace rule is one line, is assertable, and cannot rot. It also keeps
`backup.restore-check.database` and `backup.local-directory` off the surface — a database name and a
filesystem path, both consumed by a process that shells out to `pg_restore`, are not values to make
writable over HTTP in the same step that first exposes Settings at all.

The cost, stated plainly: **an operator cannot read the retention policy or the backup directory over
HTTP.** Backup administration has no routes today either (the primer lists it under "not built"), so
this leaves it exactly where it is rather than half-exposing it. It is its own step, with its own
section decision.

### 3.3 Routes (3)

| Method | Path | Level | Response |
|---|---|---|---|
| GET | `/api/settings` | **VIEW** | `ListResponse<SettingView>` — catalogued keys only, secrets redacted |
| GET | `/api/settings/{key}` | **VIEW** | `SettingView` |
| PUT | `/api/settings/{key}` | **FULL** | 204 |

**Permission — read `VIEW`, write `FULL`, both on `Section.SETTINGS`.** Not Owner/Admin-hard-coded,
for §1.5's reasons. Note that read at `VIEW` is a real grant to weigh: it exposes the SMTP host and
username (not the password), the rounding threshold and the retention policy. That is the section's
purpose and it is default-deny, so no role has it today.

`PUT` body is `{"value": "..."}` — a record with `Required.field`, so a missing field is a 422 with a
message.

### 3.4 The write path validates *before* it stores

A `SettingsAdminService` in `core` owns the catalogue and is the only thing the controller talks to.
On write it parses the value **with the same accessor that will later read it** — a Money for the
threshold, a `RoundingMode` for the mode, an `EmailTransportSecurity` name, an int, or the literal
`FOREVER` — and refuses with the reason if it does not parse.

This closes a real hole rather than adding ceremony: today `put` accepts `"0,03"` with a comma, and
the failure surfaces later, somewhere else, as a `SettingValueException` on the next invoice
somebody tries to post. `SettingValueException` and `SettingNotFoundException` are both already
mapped in `WebExceptionHandler` (lines 153 and 201), so a validation refusal already carries its
message — the named anti-pattern is not in play here, but only because somebody did this right in
step 14.

### 3.5 The structural guards

Five, each catching what the others cannot:

1. **`SettingsCatalogTest`: no catalogue key begins with `backup.`.** One line; makes §3.2's
   namespace rule a build failure rather than a convention.
2. **No catalogue key names a row with `secret = true`, except those declared write-only.** So a
   future credential added to the settings table cannot be exposed by carelessly adding a constant.
3. **Every catalogue key exists in the `setting` table** (or is declared not-seeded, as
   `smtp.password` is) — catches a typo'd constant at build time instead of as a 404 in production.
4. **`NOVOCORE_BACKUP_ENCRYPTION_KEY` has no row in `setting`, and no catalogue entry resembles it.**
   Asserting the absence, so the ADR 0013 invariant is checked rather than remembered.
5. **ArchUnit: no class in `..core.web..` may depend on `SettingsService`.** The controller goes
   through `SettingsAdminService`; a future controller reaching for `settings.put(...)` directly is a
   build failure. Proven against a probe, as the existing web rules are.

### 3.6 VAT classes and units of measure (§0.4) — 11 routes

Under their existing sections, from services that already exist:

**`TAX_AND_CHARGES`, `FULL`** — `POST /api/vat-classes`, `PATCH /api/vat-classes/{id}/description`,
`PUT /api/vat-classes/{id}/reduced-counterpart`, `DELETE …/reduced-counterpart`,
`POST /api/vat-classes/{id}/deactivate`, `…/reactivate`.

**⚠️ There is deliberately no route to change a rate**, because `VatClassService` offers none: a rate
change is a new class plus a deactivation, since editing one would retroactively change what
already-issued invoices appear to have charged. Worth stating so a frontend does not build the field.

**`PRODUCTS`, `FULL`** — `POST /api/units-of-measure`, `PATCH …/{id}/name`,
`PATCH …/{id}/mydata-code`, `PATCH …/{id}/fractional-quantity`, `POST …/{id}/deactivate`,
`…/reactivate`. Plus `GET /api/units-of-measure/without-mydata-code` at `VIEW` — the list waiting on
the accountant (Q38), which currently has no way to be seen.

Charge types and VAT exemption reasons have services too. **Not proposed**, to keep this step's
scope where the brief put it — say if you want them.

---

## 4. Migrations

**Expected: none.** Step 14's plan said that and was wrong, so here is what was actually checked
rather than assumed:

- **No new `Section` value** → the `role_section_grant` CHECK that caught step 14 is untouched.
- **No new setting keys** → the `setting_key_format` CHECK is untouched.
- **No new `JournalSource`** → the `journal_entry_source_known` CHECK is untouched.
- **No schema change** for eviction (in-memory registry) or for paging (query shape only).

**The one thing that would need a migration is Q40** — which is why §0.2 recommends deferring it
rather than discovering mid-build that "no migrations expected" was wrong for the second step running.

If a migration turns out to be needed it will be V28 and will be raised before it is written.

---

## 5. Test approach

Target: comparable rigour to steps 14–16a. **0 skipped.**

**Over HTTP, through the real filter chain** (the step-15 lesson: nine defects, none of them
reachable from the service layer):

- **`UserRoleEndpointIT`** — every route; 401 unauthenticated; 403 for a role without the section,
  checked to leak neither section nor level; 404 for an id that names nothing (not 400 — the step-15
  defect-9 shape); 422 with a message for a taken username, a short password, an inactive role.
- **`SessionEvictionIT`** (extends the existing eviction tests) — the asymmetry table in §0.3
  asserted **in both directions**: deactivate / changeRole / password-reset / **narrowed grant** /
  **newly restricted field** end sessions; rename / language / **widened grant** do not. **Each
  eviction assertion confirmed to fail with its call removed**, then restored.
- **`JournalEndpointIT`** — every filter and every combination; page stability across a tied sort
  (two entries sharing a date, asserting no row repeats and none is skipped across pages — the
  defect `SpringPaging`'s tiebreaker exists to prevent); an unknown sort refused at the boundary;
  `size=0` and `size=501` refused **with the core's message**, not a bare "Bad request.";
  and a **memory-shape assertion** for §2.2 — a query counter proving one page issues a bounded
  number of statements rather than loading the table.
- **`SettingsEndpointIT`** — read redaction; `smtp.password` never returned; a bad threshold refused
  **before** it is stored (asserting the old value survives, which is the actual guarantee); a
  `backup.*` key refused at the boundary; a key that does not exist refused at the boundary;
  `PUT /api/settings/cash.payment.limit` refused whatever the caller's grants.
- **The §0.1 rationale is asserted, not just written down.** A role with `SALES` FULL and **no**
  `SETTINGS` grant records a €600 cash sale and gets a 422 **whose body contains the limit**, and
  gets the same from `POST /api/sales-invoices/preview`. Without this, the correction in §0.1 is a
  claim in a document, and the day somebody stops interpolating the limit into that message the
  read-only-under-`SETTINGS` decision quietly becomes wrong with nothing failing.
- **`SettingsCatalogTest`** + one new ArchUnit rule — §3.5's five guards.
- **`VatClassEndpointIT` / `UnitOfMeasureEndpointIT`** — the write routes, including the refusals
  (a unit of measure a product still uses, a reduced counterpart that is not lower-rated).

**And the three sweeps that must be updated or they will fail:**

- **`PermissionSweepIT`** — its route-prefix→`Section` table is stated *independently* of the
  handlers, which is what makes it worth anything. Three prefixes to add: `/api/users`, `/api/roles`,
  `/api/sections` → `USERS_AND_ROLES`; `/api/journal-entries` and `/api/accounts/{id}/ledger` →
  `JOURNAL` (**note the second is a `/api/accounts` path under a different section from the rest of
  that prefix — the table needs the exception, and getting it wrong is exactly the sort of thing this
  sweep exists to catch**); `/api/settings` → `SETTINGS`.
- **`RouteCoverageIT` / `TradingQuarterOverHttpIT`** — the new routes are administration, not
  trading, so a quarter of invoicing will not drive them. Each needs either coverage or a **written
  excuse naming the test that does cover it**, as the seven existing excuses do. Excuses will be
  specific; `assertEveryRouteCoveredExcept` fails a stale one in both directions.
- **`OpenApiSpecIT`** — `docs/api/openapi.json` is committed and the build fails on drift.
  Regenerated and committed as part of this step; the "no schema is typed `number`" test covers the
  new `Money` field on `JournalEntrySummaryView`.

**Estimated surface after this step: ~171 routes** (137 + 17 users/roles + 3 journal + 3 settings +
11 tax/UoM).

---

## 6. Summary of what needs your decision

| # | Question | My recommendation |
|---|---|---|
| 1 | ~~`cash.payment.limit` — writable over HTTP?~~ | ✅ **Settled.** Not writable. Read stays under `SETTINGS`/`VIEW` — it serves the administrator, not the refused operator, who already gets the limit in the 422 and in the preview (§0.1) |
| 2 | Q40 journal entry numbering — in scope? | **Defer.** Undecided format, needs a migration + backfill, and it is broader than the journal (§0.2) |
| 3 | Eviction on a narrowed role grant | **Build it.** It is a real hole this step would otherwise widen (§0.3) |
| 4 | VAT class / UoM under their existing sections, not `SETTINGS`? | **Yes** (§0.4) |
| 5 | Self-service `POST /api/me/password`? | **Include it** — one route, and the most obviously missing one before a login screen — but it is scope, so your call (§1.3) |
| 6 | Guard: cannot modify your own role / change your own role | **Add it.** Turns escalation into a two-person act; safe because system roles are unmodifiable anyway (§1.5) |
| 7 | Exclude the whole `backup.*` namespace from Settings? | **Yes** — a namespace rule cannot rot the way a per-key list does; cost is that retention policy stays SQL-only (§3.2) |
| 8 | Journal *write* routes (manual entry, amend, reverse) | **Not in this step** — a manual entry screen is a design conversation, not a listing (§2.1) |
| 9 | Charge types + VAT exemption reason write routes | **Not in this step** unless you want them (§3.6) |
