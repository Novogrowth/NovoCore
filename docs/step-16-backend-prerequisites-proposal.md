# Step 16 prerequisites — four backend decisions before any frontend foundation work

*Proposal, 2026-07-30. Nothing here is built. Same pattern as `step-14-rest-surface-proposal.md`:
decisions numbered `D1…`, each with what was found, what is proposed, and what was considered and
rejected. Genuine product questions are raised as `Q46`+; everything else is a build decision.*

**Scope discipline.** All four items are backend preparation. **No frontend code is touched by any
of them.** The TypeScript side is specified here only where the backend contract has to know what it
is serving, and building it is step 16's work, not this.

---

## Confirmed first: none of the four has been addressed

Checked in the code, not inferred from the documents.

| Item | State today | Evidence |
|---|---|---|
| 1. Typed API client | **Nothing.** No OpenAPI dependency, no spec, no generator | `springdoc`/`openapi`/`swagger` match zero files under `backend/` |
| 2. Paging / sort / filter | **Nothing paged.** ~40 list routes, all returning every row | No `Pageable`/`PageRequest` anywhere in `..core.web..`; `ListResponse`'s own javadoc says step 14 shipped unpaged deliberately |
| 3. Preview / dry-run | **Nothing.** No route computes without posting | `preview`/`dry-run`/`dryRun` match zero files |
| 4. `/me` | **Nothing.** No identity route at all | `/api/me` matches zero files. There is no language or locale concept anywhere in `core-api` |

The redaction convention is the one that is *partly* settled — see **D14**, because what is settled
is not one of the three options in the question.

---

# Item 1 — Typed API client generation

## ✅ RESOLVED 2026-07-30 — springdoc tried, failed the acceptance test, fallback built

**D1's timeboxed attempt was made and the answer was decisive.** springdoc 3.0.3 resolves and the
application starts, so the Boot 4 half is fine — but it pulls **Jackson 2** (`jackson-databind
2.21.4`) alongside our Jackson 3, and swagger-core introspects models with the Jackson 2
`ObjectMapper`. It therefore cannot see `NovoCoreJsonModule` at all and falls back to reflecting Java
accessors. What `GET /v3/api-docs` actually returned:

```
Money    {"amount": {"type":"number"},
          "currency": {"currencyCode":…,"displayName":…,"symbol":…,
                       "defaultFractionDigits":…,"numericCode":…},
          "zero": bool, "negative": bool, …}
Quantity {"value": {"type":"number"}, "zero": bool, "negative": bool, "positive": bool}
Rate     {"percent": {"type":"number"}, "zero": bool}
```

Against the real wire format — `{"amount":"12.50","currency":"EUR"}`, `"3.000000"`, `"24.000000"`.

**Worse than the predicted failure.** The prediction was `amount: number`; the reality is a
*different structure* for three of the four, with derived accessors (`zero`, `negative`, `positive`)
invented as fields. Per the agreed rule, no time was spent configuring it into compliance — the
dependency was removed and D2's fallback built. **The probe's output is preserved verbatim in
`OpenApiSchema`'s javadoc**, because "we chose to write it ourselves" is worth much less than the
evidence that the alternative described a money amount as a JavaScript number.

**Built instead (D2, D3):** `OpenApiSpecIT` walks the same `RequestMappingHandlerMapping` that
`RouteCoverage` and `EndpointDeclarationCheck` already read, and `OpenApiSchema` maps types by
reading our own serialisers. **137 operations, 150 schemas**, written to `docs/api/openapi.json`.

- **An unknown type fails the build** rather than being emitted as a permissive `{}` or a guessed
  `object`. A bare `BigDecimal` reaching the surface is refused by name, pointing at the `Rate` type
  step 15a added for exactly that. Two shapes were found and added deliberately this way
  (`ResponseEntity<T>`, `Resource`), which is the mechanism working.
- **The permission model travels with the contract**: every operation carries
  `x-novocore-section` and `x-novocore-level` from its own `@Requires`, and `/api/me` correctly
  reports `AUTHENTICATED_ONLY`. springdoc could not have known either.
- **The drift check is the point**: the spec is committed, regenerated on every build, and the build
  fails if it differs — **proven** by tampering with one `operationId`. Accepting a change is
  `mvn verify -Dnovocore.openapi.write=true` plus a commit, so a contract change is visible in the
  diff and reviewed like any other.
- **The acceptance test is separate from the drift check**, deliberately: drift says the spec matches
  the code, `moneyIsAlwaysAString` says the spec is not lying about the thing that matters most. It
  asserts the four types *and* that **no schema anywhere in the document is typed `number`**.
- ⚠️ **One portability bug found and fixed while building it.** Jackson's pretty-printer indents with
  the *system* line separator, so the file was CRLF on Windows and would have been LF on the CI
  runner — the drift check would then have failed on every build that ran somewhere other than where
  the file was last written, reporting a contract change that had not happened. Output is normalised
  to LF at generation.

*The original proposal follows, unchanged.*

## What was found

`ListResponse`, `ProductView` and the rest are `core-api` records serialised by Jackson. There is no
machine-readable description of the surface. The frontend would hand-write ~60 response types and
~40 request types against a backend that gained 133 routes in one step and is still growing. Silent
drift is not a risk here, it is a certainty.

**The stack matters more than usual.** Spring Boot **4.1.0**, Java 25, and — the load-bearing
detail — **Jackson 3** (`tools.jackson.*`, which is what `NovoCoreJsonModule` is written against).

## D1 — springdoc is the obvious answer and it has a specific, disqualifying-if-unfixable problem

This is the fourth time this codebase has met the "library binds to the previous major version"
situation (ADR 0002 archunit-junit5, greenmail-junit5, ADR 0014 jqwik). It is met the same way:
**verify against reality, do not assume.**

What the verification turned up:

- **springdoc 3.x targets Spring Boot 4**, so the framework half is fine.
- **springdoc 3.0.1 still depended on Jackson 2** and failed at startup with
  `ClassNotFoundException: com/fasterxml/jackson/databind/node/ObjectNode` on a Boot 4 application
  that has only Jackson 3 ([issue #3200](https://github.com/springdoc/springdoc-openapi/issues/3200),
  now closed; 3.0.3 is current). Whether "closed" means *fixed* or means *put Jackson 2 on your
  classpath* is not answerable from release notes and **must be settled by adding the dependency and
  running the app.**
- **And this is the part that decides the approach regardless of the above:** swagger-core
  introspects models with **Jackson 2's** `ObjectMapper`. It therefore cannot see
  `NovoCoreJsonModule`, which is a Jackson **3** module. So springdoc would document `Money` by
  reflecting its Java shape — `BigDecimal amount` — and emit `"amount": { "type": "number" }`.

That last point is not a cosmetic defect. **The generated TypeScript would tell the frontend that a
money amount is a `number`**, which is precisely the defect step 15a spent a commit eliminating on
the backend (`7c4c2c4`, the `Rate` value type, found by `JsonNumberSweep`). A contract generator
whose output contradicts `CLAUDE.md` rule 5 at the exact boundary the rule exists to defend is worse
than no generator, because it is authoritative-looking.

**Proposal: try springdoc, timeboxed, behind a hard acceptance test — and have the fallback
designed before starting.**

The acceptance test is not "the app starts" or "Swagger UI renders". It is:

1. The generated spec types **every** `Money`, `UnitCost`, `Quantity` and `Rate` amount as a JSON
   **string**, in the `{amount, currency}` shape for the first two and a bare string for the last
   two — i.e. it agrees with `NovoCoreJsonModule`'s javadoc, which is the actual contract.
2. The spec contains all 133 routes, checked against the same `RequestMappingHandlerMapping`
   enumeration `RouteCoverage` already does — so a route missing from the spec fails the build
   rather than silently vanishing from the client.
3. Jackson 2 is either absent, or its presence is a stated, understood cost rather than a surprise
   discovered later.

Reaching (1) with springdoc means registering explicit schema overrides for the four types. **That
work is unavoidable on either route** — a generator cannot infer the wire shape of a custom
serializer — so the fallback is cheaper than it looks.

## D2 — The fallback: generate the spec ourselves, from the machinery that already exists

If springdoc cannot clear D1's acceptance test cleanly, **write the generator**, the way ADR 0014
wrote the property harness rather than taking jqwik.

This is less alarming than it sounds, because the hard parts are already built:

- `EndpointDeclarationCheck` and `RouteCoverage` already walk every handler method.
- `@Requires` already states each route's section and level — which is *better* documentation than
  springdoc could infer, and it belongs in the spec.
- The response types are records. Reflecting a record's components into an OpenAPI schema is
  mechanical, and the four exceptions are a four-entry map.
- The error bodies are already uniform (RFC 7807, `WebExceptionHandler`), so they are described once
  and referenced everywhere.

The output is an OpenAPI 3.1 document written by a test, which is where the repo already puts this
kind of machinery.

**Recommendation between them:** attempt springdoc first — a maintained dependency beats our own
code when it works — but do not spend a session forcing it. The fallback is genuinely viable and it
produces a *more* accurate spec, since it reads our own serializers rather than guessing at them.

## D3 — The spec is committed, and CI fails when it drifts

Generating a spec that nobody compares against anything achieves nothing.

**Proposal: `docs/api/openapi.json` is a committed artefact, regenerated by the build, and CI fails
if the regenerated spec differs from the committed one.** That is the mechanism that turns "a broken
frontend build signals a contract change" from an aspiration into a fact:

- A backend change that alters the surface fails CI until the developer commits the new spec —
  so the contract change is *visible in the diff*, reviewed like any other change.
- The frontend regenerates from a file in the repo, so it never needs a running backend to build.
- The spec's diff is the changelog for the API, which nothing currently produces.

## D4 — `openapi-typescript` for types; the fetch layer is hand-written

**Proposal: generate types only. Do not generate fetch functions.**

The reason is concrete rather than stylistic. NovoCore's request layer has four behaviours no
generator knows about:

1. **CSRF** — the token is in a JavaScript-readable cookie and must be echoed on every mutating
   request (step 4's decision, non-negotiable given cookie auth).
2. **401 handling** — `/api/**` returns 401 rather than redirecting, deliberately, so a `fetch()`
   can act on it. Something has to act on it.
3. **The RFC 7807 error body** — including the two shapes step 15 spent three defects making
   uniform. Reading them is a typed concern the client should own once.
4. **Money as strings** — the single most important thing the frontend must not get wrong.

A generated client would need wrapping for all four, which leaves a generated layer that adds
nothing and a second artefact to keep in sync. `openapi-typescript` emits types and no runtime,
which is exactly the half that is hard to hand-maintain.

**Plus one convention that is worth writing down now:** the TS side should treat a money amount as a
**branded string type**, not `string`, so `+amount` and `amount * qty` are compile errors rather
than the frontend reinventing the double. That is rule 5 expressed in the only place it is currently
unexpressed, and it costs one type alias.

**Scope for item 1:** the spec generator, the four value-type schema overrides, the committed spec,
the CI drift check. The `openapi-typescript` invocation lives in `/frontend/package.json` and is
step 16's.

---

# Item 2 — Paging, sorting and filtering

## What was found

**~40 list routes, none paged.** `ListResponse`'s javadoc already states the position and the
trigger:

> `GET /api/products` genuinely returns every product in one response, and that is fine at hundreds
> and not at tens of thousands. […] The trigger to act is the first list that is slow, and the
> envelope is what makes acting cheap.

**The trigger has arrived, and it is not slowness — it is the table layer.** A table component
written against an unpaged contract and later retrofitted is a rewrite of every screen. The
question was correctly identified as blocking.

Date-range filters already exist wherever a service offers `between(from, to)`, which covers most
transaction lists. The genuinely unbounded reads are master data and the ledger.

## D5 — Three tiers, two contracts — not one contract for everything

Not every list needs paging, and paging the chart of accounts would be noise. But **two different
list shapes across the surface forks the frontend's table layer**, which is the cost worth avoiding.

| Tier | Lists | Growth | Paged? |
|---|---|---|---|
| **A — unbounded** | journal entries and lines, inventory lots, stock consumptions, sales invoices, credit notes, purchase invoices, goods receipts, settlements, open items, audit log, email outbox | tens of thousands within a year | **Yes, now** |
| **B — bounded by the business** | products, customers, suppliers, assets, bundles, freight allocations | hundreds to low thousands | **Envelope now, paging when needed** |
| **C — genuinely fixed** | chart of accounts (67), VAT classes (9), exemption reasons (29), units of measure, charge types, sections | fixed by statute or by us | **No, ever** |

**Proposal: one envelope for A and B, no page block for C.** A and B are then indistinguishable to
the table component, and C's difference is *visible in the type* — the `page` block is absent — so
it is a documented difference rather than a silent one.

This is the same argument `ListResponse` already makes, made concrete: **define the final contract
now, implement paging on tier A now, and let tier B gain it later with no frontend change.** That
keeps this step finishable while giving the table layer the shape it will keep.

## D6 — Offset/limit with a total, not cursors

**Proposal: `?page=&size=&sort=`, returning `{items, page: {number, size, totalElements, totalPages}}`.**

Reasons, in order of weight:

1. **An accounting table needs "page 7 of 34" and a row count.** That is what these screens look
   like; a cursor cannot produce it.
2. **Arbitrary column sorting.** Cursor paging requires the cursor to encode the sort key, so
   letting a user sort by any column means a different cursor per column.
3. **Deep-offset cost is irrelevant at this scale.** One self-hosted PostgreSQL, tens of thousands
   of rows. The trigger to revisit is a table past roughly a million rows, which is a decade away
   and would be a stated decision, not a surprise.

Rejected: Spring's `Page`/`Pageable` types crossing the API boundary. `Pageable` is a Spring Data
type and `core-api` must not depend on Spring Data — ADR 0003's boundary. The core's own
`PageRequest`/`PageResponse` records are a few lines and keep the boundary intact, which is the same
reasoning that gave `ChargeType` plain `Long` ids instead of JPA associations.

## D7 — Sort keys are an allowlist per endpoint, never a column name

A `sort` parameter that reaches SQL is an injection surface and a coupling of the wire to the
schema. **Proposal: each list endpoint declares the fields it can be sorted by, as an enum; anything
else is `InvalidRequestException`** — which, per the named anti-pattern in `CLAUDE.md`, is the type
that actually reaches the caller with a message saying what the valid values are.

The allowlist also lands in the OpenAPI spec (item 1), so the frontend's sort headers are typed
rather than guessed.

## D8 — Filters stay named parameters; no generic filter DSL

The existing pattern is explicit params (`customerId`, `salesInvoiceId`, `from`, `to`) with mutually
exclusive combinations refused by name. **Proposal: keep it.** A generic filter grammar would be a
second query language to secure and to test, and it would appear in the OpenAPI spec as an opaque
string — losing exactly the typing item 1 exists to buy.

## The honest cost

This is the largest of the four. Paging tier A means changing ~15 service interface methods, their
implementations and their repositories, plus their tests — and every one of those is on a
money-touching path. It is comfortably a session on its own, possibly two, and it should be its own
commit per the one-commit-per-step convention.

**Q46 (raised, not blocking): does tier B get paged in this step or later?** The recommendation is
later — the envelope makes it free to defer and the row counts do not justify it — but it is a call
worth making explicitly rather than by omission, because "later" has a way of meaning "when it is
already painful".

---

# Item 3 — Preview / dry-run endpoints

## The premise is correct, and stronger than stated

The frontend recomputing VAT, rounding or freight allocation would not merely risk drift. It would
require a **second implementation of `VatClassPrecedence` in TypeScript** — the rule whose entire
design point is that it exists once, returns *which level won*, and **throws rather than assuming
24%** because "a silent default produces a plausible invoice at a rate nobody chose". A TypeScript
copy would either reproduce that refusal exactly or quietly become the fallback rate the system has
deliberately never had.

And it would put decimal arithmetic in JavaScript, which is the Q45 defect's exact shape at a new
boundary.

**So: no arithmetic in the frontend at all. Entry screens display what the backend computed.**

## D9 — Preview calls the same code as record, or it is worse than nothing

**This is the load-bearing constraint and everything else follows from it.** A preview that computes
along a parallel path is a second implementation that will drift, and it drifts *silently* — the
operator sees a correct-looking total and the posted document says something else.

**The good news, verified in the code rather than hoped for:** `SalesInvoiceServiceImpl.record`
already separates the phases. `price(line, customer, channel, currency, roundingMode)` produces a
`PricedLine`; `post(...)` writes the entry; `consumeStock(...)` moves the stock. **A preview is an
extraction of the first phase, not a reimplementation of it.**

Proposed guard, in step 15's style: **for each previewable document type, a test drives
`POST …/preview` and `POST …` with the same request over HTTP and asserts every computed figure is
identical.** Not a structural rule — a behavioural one, for the same reason the audit-log fix needed
behavioural tests: deleting the shared call and duplicating the arithmetic would be structurally
spotless and would reintroduce exactly the defect the rule exists to prevent.

## D10 — Preview must not post, and must not post-then-roll-back

The tempting implementation is to run `record` in a transaction and roll it back. **It must not be
done, and there is a specific reason beyond tidiness:**

`AuditLogServiceImpl` writes with `REQUIRES_NEW` — that is the whole point of the fix in `a4ec7db`,
and it is *proven behaviourally* that an audit entry **survives a rolled-back caller transaction**.
So a rollback-based preview would leave real audit entries for documents that were never created,
which is the audit log lying about what happened. It would also burn document sequence values and
would interact badly with the deferred debits=credits constraint trigger, which fires at commit.

**Proposal: preview is a genuinely read-only path** — resolve, price, total, compare against the
stated total, and return. It writes nothing.

## D11 — Preview requires the same permission as the create route

**`@Requires(section = …, level = AccessLevel.FULL)`, not `VIEW`.** A preview computes a priced
document from cost and pricing data; served at `VIEW` it becomes a weaker second path to exactly the
information the section split exists to protect. This is the same argument that makes a referenced
email attachment re-check its owning record's section (Q44's second half).

## D12 — Which documents get one, and in what order

| Order | Document | Why |
|---|---|---|
| **1** | **Sales Invoice** | The primary entry screen. VAT per line by precedence, per-line net/VAT rounded once, bundle decomposition, and the rounding comparison against `statedTotal` — including **whether the difference exceeds the threshold and will therefore need acceptance**, which is a UI flow that cannot be built without knowing the answer before submitting |
| **2** | **Credit Note** | Same screen shape; credits the VAT the sale actually charged rather than re-resolving, which is not something a frontend could reproduce even in principle |
| **3** | **Purchase Invoice** | VAT plus the purchase price variance residual — the figure that makes the line's debits sum to what was charged |
| **4** | **Freight Allocation** | The least predictable arithmetic in the system: the capitalised/variance split by what is still in each lot (ADR 0010/0011/0015). Operators should see the split before committing to it |

**Not proposed:** Goods Receipt (little arithmetic beyond the lot cost it is given), settlements and
bank transfers (no computation worth previewing), manual journal entries (the operator supplies both
sides and the debits=credits check is the whole answer, which a 422 already gives).

**Proposed shape**, identical request body to the create route so the frontend has one form:

```
POST /api/sales-invoices/preview     →  200  (never 201 — nothing was created)
```

returning the computed lines (net, VAT, resolved class **and which precedence level supplied it**),
the totals, the rounding difference against `statedTotal`, whether it is within the threshold, and
any warnings that are not refusals — notably a stock shortfall, which per Q17/ADR 0008 never blocks
a sale but which the operator should see before recording one.

**Errors are the same errors.** A preview that would be refused returns the refusal, with the same
message — that is most of its value.

---

# Item 4 — `/api/me`, and the redacted-field convention

## D13 — `GET /api/me`

**Proposal**, returning in one response:

- **identity** — id, username, displayName, active;
- **role** — name, `fullAccess`, `systemRole`;
- **grants** — **every** `Section` with its resolved `AccessLevel` *and* its `isAvailable()` flag.
  Not just the granted ones: `Section.isAvailable()` exists precisely so a UI can distinguish "you
  may not see this" from "this does not exist yet", **and nothing currently exposes it**. Returning
  only visible sections throws that away;
- **`restrictedFields`** — empty today (V26), carried for when it is not;
- **language** — see Q47.

`UserView` and `RoleView` already compute all of this (`accessTo`, `visibleSections`,
`hiddenFieldsIn`). This is an exposure of existing logic, not new logic.

**One thing that must be stated in writing:** `/api/me` is the only route on the surface with **no
section requirement** — it is the route that tells you which sections you have, so it cannot require
one. It requires authentication and nothing more. That makes it an exception to
`EndpointDeclarationCheck` and to `PermissionSweepIT`'s route-prefix table, and it must be **excused
explicitly**, the same way step 15's five uncovered routes are excused by name — not permitted by a
loophole someone widens later.

**And a note for the frontend, worth recording now:** `restrictedFields` is a rendering hint. The
backend redacts regardless. Nothing in the UI may treat it as the enforcement point.

## D14 — The redaction convention is already settled, and it is none of the three options

The question offers omitted key / null / empty string. **What the code does today is the fourth
option, and it is the right one — but it is currently undocumented and untested at the wire.**

What actually happens:

1. `ProductView.redactedFor(role)` sets the hidden field to **null** and adds it to a
   **`hiddenFields`** set carried on the same response.
2. `application.yml` sets `default-property-inclusion: non_null`. **So on the wire the key is
   absent**, not null.

The convention is therefore: **a redacted field's key is omitted, and the field is named in
`hiddenFields`.**

**`hiddenFields` is what makes this work, and it is not decorative.** Without it, "hidden from you"
and "not set" are byte-identical — and both states genuinely occur on the same field:
`lastPurchasePrice` is absent for a product never received *and* absent for a viewer who may not see
cost. Two states that look identical on a screen and have completely different fixes. `ProductView`'s
javadoc already says exactly this; the reasoning is sound and this proposal is to **ratify it**
rather than replace it.

**Considered and rejected: turning off `non_null` and sending explicit nulls.** It would let
"present as null" mean something. But it changes **every response body across all 133 routes**, and
it breaks a contract already asserted in `ReadBackChecks` — that an unknown depreciation rate
arrives as *nothing at all*, never as a `0` somebody could depreciate by. The cost is large, the
benefit is duplicated by `hiddenFields`, and the current behaviour is better documented than the
alternative would be.

**Rejected outright: empty string.** `""` is a value. It would flow into a text field, be saved back
on the next PATCH, and overwrite the real value with a blank. That is a data-loss defect waiting for
its first edit screen.

## D15 — Enforce it, because nothing currently exercises it

**Since V26, no field is restricted for any role**, so the redaction path is intact and **never
executed on the wire**. The mechanism will be reintroduced eventually and there is currently nothing
that would catch it being broken in the meantime.

**Proposal, three parts:**

1. **A wire-level test**: create a role at runtime with a field restriction (which `ProductIT` and
   `SecurityIT` already do post-V26), fetch a product over HTTP as that role, and assert that every
   field named in `hiddenFields` is **absent from the JSON body** and that `hiddenFields` names it.
   That is the convention, stated as an assertion rather than as prose.
2. **Proven to fail**, like every other guard in this repo — against a view that blanks a field and
   forgets to record it in `hiddenFields`, which is the realistic mistake.
3. **Written into `CLAUDE.md`**, next to the two named anti-patterns, as a one-line rule: *a field
   withheld by permission is omitted from the body and named in `hiddenFields`; a field that is
   merely unset is omitted and not named.*

The OpenAPI spec (item 1) must then mark every protected field **optional** in TypeScript, and the
frontend reads `hiddenFields` to decide between "—" and "hidden". That is the whole frontend
contract, and it is one sentence because the backend settled it properly the first time.

## Q47 — Language preference: where does it live, and what is it for?

**There is no language or locale concept anywhere in the backend.** So `/me` cannot return one
without a decision. Two halves, and the second matters more:

**(a) Where it is stored.** Recommendation: **a column on `app_user`** (migration V27), not a
Setting. Settings are global operator configuration; a language preference is per-user and must
follow the user across devices, which `localStorage` would not. It needs a
`PATCH /api/me/preferences` to change it, which is also the first route that lets a user change
anything about themselves.

**(b) What the backend does with it — and this is the scope question.** Recommendation:
**nothing, for now.** `/me` carries it as a hint the frontend uses to pick its own strings, and
**every backend message stays English.**

The reason to be firm about this: localising backend messages means localising *every* message in
the system — and this codebase's error messages are unusually load-bearing prose. Three of step 15's
nine defects were about messages reaching the caller at all, and the remedy in each case was a
carefully worded explanation of what the operator did wrong. Translating that surface is a real
project, and it is not a prerequisite for a frontend foundation. If it is wanted, it should be a
step with its own estimate, not a side effect of adding a column.

**Which languages?** Greek and English is the obvious pair, but it is a product decision and nothing
should be seeded on a guess — the same stance as the depreciation rates and the VAT class list.

---

# Proposed order and shape of the work

| # | Item | Rough size | Depends on |
|---|---|---|---|
| 1 | **`/api/me` + the redaction convention** (D13–D15, Q47's storage half) | Small | Q47(a) answered |
| 2 | **Preview endpoints** (D9–D12), sales invoice and credit note first | Medium | — |
| 3 | **OpenAPI spec + drift check** (D1–D3) | Medium, with a real risk of becoming D2 | 1 and 2 done, so the spec covers the final surface |
| 4 | **Paging** (D5–D8), tier A | Large, possibly two sessions | 3, so the spec regenerates once at the end |

The ordering is deliberate. `/me` is small and unblocks the frontend's shell and routing immediately.
Paging is last because it changes the most signatures, and generating the spec before it would mean
regenerating it after — whereas paging *after* the spec exists means the drift check proves the
paging change is the only contract change in that commit, which is precisely what D3 is for.

**Each of the four is its own commit**, per the one-commit-per-build-step convention. None of them
touches `/frontend/`.

## Open questions requiring an answer before work starts

- **Q46** — Does tier B (master data lists) get paged in this step, or later behind the envelope?
  *Recommendation: later.* Not blocking — the envelope is defined either way.
- **Q47(a)** — Language preference stored as a column on `app_user`? *Recommendation: yes,
  migration V27.* **Blocking for item 1 of the order above.**
- **Q47(b)** — Is the backend expected to localise its own messages? *Recommendation: no, not now,
  and it should be its own step if wanted.* **Blocking**, because a "yes" changes item 1 from small
  to large.

Everything else in this document is a build decision and needs approval rather than an answer.
