# Step 15 — Dummy data validation: proposal

*For review before any code is written. Same pattern as `step-14-rest-surface-proposal.md`.*

---

## 1. What this step is, and what it is not

**It is:** driving NovoCore's 133 REST routes through a sequence of operations a person would
actually perform, against a fresh database, and then asking whether what came out the other end is a
coherent, correct financial system.

**It is not:**

- a second test suite for the domain — `WholeScenarioIT` already plays a trading year at the
  *service* layer and sweeps 21 invariants over it. Step 15 does not re-prove the domain; it proves
  that the **HTTP layer is a faithful, usable route to it**;
- a performance or volume exercise (see §9);
- the frontend, or anything shaped like it.

**The distinction that matters.** Step 14's tests assert *what they were written to assert*: that
`POST /api/purchase-invoices` returns 201 and the body reads back. None of them asks whether a
person can get from "goods arrived" to "supplier paid, books balanced" using only the API. Those are
different questions, and only the second one tells us the API is finished.

---

## 2. The evidence that this is needed, rather than a formality

Step 14's four endpoint suites touch **25 distinct paths**. Grepping every HTTP-level test in `app`
for `/api/...` string literals, here is what has *never been called over HTTP at all*:

| Route family | Routes | Called over HTTP? |
|---|---|---|
| Sales invoices | 5 | **No** |
| Credit notes | 4 | **No** |
| Settlements, allocations, open items, customer credits | 11 | **No** |
| Bank transfers | 4 | **No** |
| Freight allocation | 6 | **No** |
| Bundles | 6 | **No** |
| Suppliers | 11 | **No** |
| Account groups, account writes, reordering | 9 | **No** |
| Products, customers, purchasing, inventory, assets, outbox, tax lookups | ~77 | Partially |

So **roughly half the surface has never seen an HTTP request**, and the untested half is the half
that moves money: every sale, every credit note, every payment received, every allocation. The
services underneath are well covered; the controllers, request bodies, JSON binding, error mapping
and permission declarations on those routes are not covered by anything.

That is the concrete form of the argument recorded at the last close-out, and it is stronger than it
looked from the close-out note.

---

## 3. Decisions that need a ruling before any code

### D1 — Where the driver lives, and whether it leaves data behind ⚠️ *the big one*

Three options.

**(a) A Failsafe integration test only** — `TradingYearOverHttpIT` in `app`, running under
`mvn verify` against Testcontainers. Permanent regression value; every future change re-runs it. The
data vanishes with the container.

**(b) A runnable seeder only** — a `@Profile("seed")` `CommandLineRunner` pointed at the live Compose
stack over HTTPS, leaving a populated database behind. Good for step 16 (a frontend built against
empty tables is built blind); no regression value, and nothing re-runs it.

**(c) Both, from one scenario definition.** ⭐ **Recommended.** The scenario is written once against
`ApiClient` — which already knows how to log in, carry the session cookie and echo the CSRF token —
and takes a base URL. The test driver points it at the random test port; the seeder points it at
`https://localhost`. One narrative, two drivers, no second copy to fall out of date.

The cost of (c) over (a) is small — the seeder is a thin main method plus a profile — but it is real,
and it is arguably step 16's need rather than step 15's. **It is proposed as its own commit (15c) so
it can be cut without touching anything else.**

### D2 — The scenario is a narrative, not a route checklist

A checklist that calls all 133 routes in declaration order would prove that each returns a
non-error. It would not find the thing step 15 exists to find: that a route returns 201 and omits the
id the *next* call needs, or that recording a receipt against an invoice takes three calls where the
operator expects one. **Order and dependency are the content of this test.**

Route coverage is measured separately (§6) rather than driving the design.

### D3 — Fixed dates, fixed data, no randomness

No `LocalDate.now()`, no `Math.random()`, no generated names anywhere in the scenario. Every date,
quantity and price is a literal in a fixed trading window, so a failure reproduces exactly and every
asserted figure is a number somebody can check by hand. Same trust argument as ADR 0014's fixed
default seed, and the same reason: **a red run must always mean a defect.**

Property-based exploration has its place and it is not here — it is at the service layer, where step
13 put it.

### D4 — Setup that has no route is done at the service layer, deliberately

The scenario needs a second user (Remote/Order Staff) with a password, to prove the permission
model under real use. **Users, roles, settings and journal writing have no HTTP routes** — step 14
deferred them on purpose. `MasterDataEndpointIT` already creates a restricted user by autowiring
`UserService` and `RoleService`, and step 15 does the same.

**What must not happen is adding those routes here to make the scenario tidy.** That is step 14 scope
that was deliberately deferred, and smuggling it into a validation step is exactly the drift
`CLAUDE.md`'s scope discipline exists to prevent. The setup being service-level is stated in the test
rather than hidden.

### D5 — Findings are fixed inside step 15, not deferred

Step 15's whole purpose is to find API-shape problems before a frontend is built on them. A finding
that gets a Q-number and a "later" is a finding step 16 will hit anyway, with two candidate causes.
So the proposed rule is:

- **shape defects** (missing filter, missing id in a response, a command needing two calls) — fixed
  in step 15, each with a test;
- **decisions** (something that needs a ruling, not a fix) — a Q-number, raised before the fix, same
  as every previous step;
- **accepted limitations** — recorded in `HISTORY.md` with the reason, not silently left.

This is what makes step 15's size uncertain, and it is the right uncertainty to accept.

### D6 — The bar is the *invariant sweep*, not the HTTP status codes

Stated fully in §5. The headline: the checks that decide whether step 15 passed are the same ones
`WholeScenarioIT` runs — asked of a database that only ever received HTTP requests.

---

## 4. The scenario, and how much data

### The narrative — one trading quarter, `2026-01-05` to `2026-03-31`

A quarter rather than a year, on purpose: three *closed months* is what exercises date-range filter
boundaries (`?from=&to=`) with something on each side, and monthly VAT periods are the unit the
accountant package will later work in. A year of the same document count would be thinner per month
and test the filters less.

**Java Jives' actual business shape**, so the data is recognisable rather than abstract:

| Month | What happens |
|---|---|
| January | Suppliers and products set up. First delivery arrives **before** its invoice (GR/IR one way). Second invoice arrives **before** the goods (GR/IR the other way). A freight invoice lands and is allocated across two lots, one of which has already partly sold — the ADR 0010/0011 case. Retail sales through Store & Phone; two serial-tracked machines sold by name. |
| February | eCommerce and Skroutz channels open. A bundle (machine + grinder + beans) is defined and sold as one line. A B2B customer buys on account. A supplier is paid, partially. A customer overpays and the surplus becomes a customer credit. An intra-EU B2B sale under reverse charge. |
| March | A machine comes back — credit note restoring stock into a lot that has since been re-costed. A price-only credit note on a different invoice. A pallet of beans written off as expiry. A sale of stock we did not have (oversell, shortfall recorded). Bank transfers between accounts. A settlement is amended below its allocated total, cascading a release. Month-end: everything settled that should be, GR/IR deliberately left holding one real timing gap. |

Every one of those is a case some ADR or Q-number decided. The narrative is chosen so the API is
driven through the decisions the domain actually made, not through the easy paths.

### Volume

| Thing | Count | Why this much |
|---|---|---|
| Suppliers | 3 | One domestic, one intra-EU, one freight-only |
| Products | 15 | Incl. 2 serial-tracked, 2 bundles, 1 service, 1 unpriced |
| Customers | 12 | Incl. the seeded retail customer, 1 intra-EU B2B, 2 near-duplicates for `match-suggestions` |
| Purchase invoices / goods receipts | 8 / 8 | Deliberately not paired 1:1 |
| Sales invoices | 22 | Across all three channels |
| Credit notes | 3 | Stock-restoring, price-only, and one that must refuse to reverse |
| Settlements / allocations | 14 / ~20 | Both directions, partial, over-, and one amended |
| Journal entries produced | ~120 | Enough that the sweeps mean something |

**Deliberately small enough to read.** When an invariant fails, somebody has to open the database and
find out why; 120 entries is a scale a person can hold. Thousands of rows would prove nothing extra
about correctness and would make every failure an archaeology exercise. Volume is §9.

---

## 5. What "validated" means — six classes of check

Each answers a different question. A step-15 pass requires all six.

### 5.1 The books are correct — the shared invariant sweep ⭐ *the headline*

`WholeScenarioIT`'s 21 sweeps are **extracted into a shared component** (`LedgerInvariants`, taking a
`JdbcTemplate` and the core services) and run **unchanged** against the database the HTTP scenario
built. One definition, two callers — not a second copy that drifts.

This is the strongest available statement, and it is deliberately not about the API at all: not
"every request returned 2xx" but **"the books the API produced are correct."**

- no journal entry anywhere is unbalanced, empty or one-sided — **asked in raw SQL**, bypassing every
  service, view and Java-side check;
- no line states nothing (every amount strictly positive);
- the trial balance balances;
- **every control account equals the sum of its own sub-ledger** — AR, AP, Inventory, Fixed Assets,
  GR/IR, accumulated depreciation;
- every line on a Control account names a sub-ledger row **that exists**;
- the Inventory account equals what every lot says it is carrying (ADR 0015);
- GR/IR holds exactly the deliveries awaiting an invoice and vice versa, and nothing else;
- both variance accounts carry exactly the differences the documents recorded;
- the freight invoice is fully spent, unallocated is clear;
- Output and Input VAT are separate and each equals its own lines;
- open items equal the AR and AP control accounts;
- no settlement allocates more than it received;
- the oversold product reads negative and cost nothing it did not have.

Plus one new sweep that only makes sense here: **the ledger is not trivial** — assert the scenario
actually produced ≥100 entries across ≥8 sources, so a sweep cannot pass vacuously on an empty
database. (`WholeScenarioIT` has this already; it carries over for the same reason.)

### 5.2 Nothing is orphaned

FK constraints already cover most of this, so the check is aimed at the references that are **not**
foreign keys and therefore can genuinely dangle:

- polymorphic sub-ledger refs on journal lines (trigger-checked — assert the trigger's claim from
  outside it);
- `attachment.entity_type` / `entity_id` → the record it names, **and** that every `entity_type`
  present is registered in `AttachmentOwnerType` (step 14's fail-closed registry — an unregistered
  type means nobody can download those attachments, and it fails silently until someone tries);
- `email_outbox_attachment.attachment_id` → a live attachment or a deliberate NULL;
- `journal_entry.reversal_of_entry_id` → an entry that exists and is reversed at most once;
- `gr_ir_match` → both ends live;
- `stock_consumption` and `stock_write_off` → lots that exist;
- every lot with movements has a source document.

Each assertion **names what it checked**, so a passing run states its coverage rather than implying
it.

### 5.3 The API is coherent, not merely accepting

The check that catches "201 Created, then invisible":

- **Read-back equivalence.** Every document created by `POST` is re-fetched by `GET /{id}` and the
  response must reproduce what was sent — amounts, currencies, line count, totals, dates, the
  document's own number. A field silently dropped on the way in is invisible to a 201.
- **List membership.** Every created document appears in its own list endpoint, and in each filtered
  list whose filter it satisfies (`?customerId=`, `?from=&to=`, `?status=`). Date filters are asserted
  **at the boundaries** — a document on `from` and one on `to` are both included, one a day outside is
  not. Off-by-one on a date range is the classic silent defect and no current test would see it.
- **Money never crosses as a JSON number.** Asserted mechanically over **every response body the
  scenario produces**, not on chosen fields: walk the JSON tree and fail if any number node has a
  fractional part. D3 named this the hardest thing to change later; this is the total version of that
  check. *(If it fires on something legitimate, that is a finding worth reading, not an assertion to
  weaken.)*
- **Every id a subsequent call needs is present in the response that created it.** Mechanically true
  by construction here — the scenario cannot proceed otherwise — which is the point: the narrative
  *is* the assertion.

### 5.4 Refusals are correct and legible

A happy path cannot test this, and a frontend cannot be built against an API whose 422 has an empty
body. A fixed matrix of deliberate bad requests, each asserting **both the status and that the body
names the reason**:

| What | Expected |
|---|---|
| Amend a posted sales invoice | 409, message says the document is immutable |
| Reverse an already-reversed entry | 409 |
| A rounding difference above the threshold | 422 **carrying the core's message**, then the same request with acceptance → 201 (Q15) |
| Reverse charge on a domestic supplier | 422, names the `VatStatus` mismatch |
| A line with neither VAT class nor exemption reason | 422 |
| A product with no VAT class, invoiced | 422, **not** a silent 24% |
| Sell a bundle containing a serial-tracked component | 422, names Q42's reason |
| Reverse a credit note that restored stock | 422, names ADR 0009's reason |
| Reverse a consumption on a re-costed lot | 422, names ADR 0015's remedy |
| Any id that does not exist | 404, no leak of what does |
| A JSON number where money belongs | 400 (D3: refused, never rounded) |
| Unauthenticated | 401, not a redirect |

### 5.5 The permission model holds under real use

A meaningful slice of the narrative is re-run as **Remote/Order Staff**, not just as Owner:

- every route outside the role's grants returns **403**, swept over all 133 routes rather than a
  chosen few — the denominator comes from Spring's own handler mapping (§6), so a route added later
  is covered the day it appears;
- the routes it *does* reach return **redacted** data: no supplier, no supplier SKU, no last purchase
  price, no lot cost — asserted **against the raw bytes**, because a deserialised assertion passes
  either way;
- the Q44 path holds: a purchase-invoice PDF emailed out is **not** downloadable by a role that
  cannot open the invoice.

Redaction is currently a named convention plus three ArchUnit rules. This is the first behavioural
proof of it across the whole surface, and step 12's audit-log lesson says that is the only kind that
counts.

### 5.6 It survives a backup and restore

The HTTP-built database is dumped, encrypted, restored into a fresh database, and **§5.1's entire
sweep is re-run there**. The mechanism exists (step 12) and `WholeScenarioIT` already does this at
the service layer, so the marginal cost is near zero and it is what makes the data real rather than
notional.

### The exit criterion, in one sentence

> **Step 15 passes when a fresh database, driven only through HTTP by a sequence a person would
> actually perform, ends with every invariant in §5.1 green, no dangling reference in §5.2, every
> document readable back as it was written, every refusal carrying a message an operator could act
> on, the permission model intact across all 133 routes, and the whole thing restorable — with every
> route either exercised or named and excused.**

---

## 6. The route-coverage ledger

The narrative will naturally exercise perhaps 70–90 of the 133 routes. Some will be genuinely
unreachable from a trading narrative (`/accounts/{id}/deactivate`, group reordering, the outbox retry).

**A validation that covers 70 of 133 and says "validated" is worse than one that says which 63 it
did not touch.** So:

- the denominator is read from Spring's `RequestMappingHandlerMapping` at runtime — the **same source
  `EndpointDeclarationCheck` already reads** — so it cannot drift from the real surface;
- every request the scenario makes is recorded against the route that handled it;
- at the end, the test **prints the uncovered routes** and fails unless each appears in an explicit,
  named exclusion list with a one-line reason.

That last clause is what makes it a check rather than a report. `CLAUDE.md`'s "no silent caps"
discipline, applied to coverage.

---

## 7. Proposed sequencing — three commits

| | What | Green on its own? |
|---|---|---|
| **15a** | The harness: the scenario abstraction over `ApiClient`, `LedgerInvariants` extracted from `WholeScenarioIT` (which is refactored to use it and must stay green), the route-coverage ledger, the JSON money sweep. | Yes — `WholeScenarioIT` still passes, ledger reports 0 routes covered. |
| **15b** | The narrative and all six check classes. **This is where the findings come from**, and where any fixes for them land. | Yes |
| **15c** | The seeder against the live Compose stack (D1's second driver). **Cuttable.** | Yes |

Same reasoning as step 14: one commit of this size would be unreviewable, and 15a has value on its
own — extracting `LedgerInvariants` means the invariant sweep stops being welded to one test.

---

## 8. What I expect it to find

Stated in advance so the step is judged against a prediction rather than post-rationalised:

1. **Missing filters** — a list endpoint the narrative needs to query a way it cannot.
2. **A response missing an id** the next call needs, forcing an extra round trip.
3. **Date-range boundary handling** — inclusive/exclusive is not currently asserted anywhere.
4. **A 422 whose message is unusable** by an operator, or a refusal that arrives as a 500.
5. **A redaction gap** on one of the untested route families (sales, settlements, freight) — the
   ArchUnit rules cover `ProductService` and `BundleService` reads; a *lot cost* reaching a restricted
   role through a settlement or freight response is not structurally prevented.
6. **Nothing at all in the ledger**, which would be the good outcome and is genuinely plausible —
   the services are heavily tested and the controllers are thin.

If (6) is what happens, step 15 still earned its cost: the API will have been driven end to end and
the coverage ledger will say exactly how far.

---

## 9. Deliberately out of scope

- **Volume and performance.** `GET /api/products` returns every product unpaged (step 14's D8). Whether
  that holds at 5,000 products is a real question and a *different* one; answering it here would mean
  generating data nobody can read when an invariant fails. It is the question that would justify
  paging, and it belongs with paging.
- **Migration-shaped data.** Real Manager.io data is step 20 and needs the accountant's chart mapping.
- **Anything with no route** — users, roles, settings, journal writing, VAT administration. Step 14
  deferred them; step 15 does not un-defer them (D4).
- **Concurrency.** Two operators posting simultaneously is a real risk and not this step's; it needs a
  deliberate design conversation about locking, not a scenario.

---

## 10. Size, and an honest note on the estimate

The roadmap estimates step 15 at **0.7 h**. That figure looks like it was priced as "write something
that inserts some rows". What is proposed here — six classes of check, a shared invariant component,
a coverage ledger, a permission sweep across 133 routes, plus fixing what it finds — is on the
measured pattern of this project roughly **2.0–2.5 h**, in the band steps 13 and 14 landed in.

**Two ways to close that gap; the choice is yours, not mine.**

- **Full** (as proposed): all six classes. ~2.0–2.5 h.
- **Reduced**, ~1.0 h: the narrative (§4), the invariant sweep (§5.1), read-back and the JSON money
  sweep (§5.3), and the coverage ledger (§6). **Cut:** the refusal matrix (§5.4), the permission sweep
  (§5.5), restore (§5.6), the seeder (15c).

I would not recommend the reduced version, for one specific reason: **§5.4 and §5.5 test things no
other test in the repo tests.** The invariant sweep largely re-proves at HTTP level what
`WholeScenarioIT` proves at service level — valuable, but the *marginal* find rate is low. The
refusal matrix and the permission sweep are genuinely uncovered ground, and they are the two things a
frontend will collide with first. If the step has to shrink, shrinking §5.1 is the cheaper cut.

The estimate itself is **not** being overwritten — per `CLAUDE.md`, the `Est.` column is calibration
data and stays as written. This note is about what to build, not about editing the number.

---

## 11. Questions needing an answer before 15a starts

1. **D1 — does 15c (the seeder leaving data in the live Compose database) belong in step 15, or does
   it belong to step 16?** Recommendation: build it in 15, because a frontend built against empty
   tables is built blind and the marginal cost here is small.
2. **Full or reduced (§10)?** Recommendation: full, and if it must shrink, cut §5.1 rather than §5.4
   or §5.5.
3. **Realistic or obviously-fake names?** Recommendation: **obviously-dummy names with realistic
   shape** — recognisable product categories, structurally valid but reserved-range VAT numbers, and
   a marker on every party name. Real-looking dummy data in a system that will later hold real data is
   a mistake waiting for someone to make it.
4. **Does the scenario assume a fresh database, or must it be re-runnable against a populated one?**
   Recommendation: **fresh only**, and refuse to run otherwise, naming why. Idempotent seeding means
   inventing update-or-insert semantics that the API deliberately does not have (step 14's D4:
   commands, not CRUD), and a scenario that half-ran is worse than one that refused.
5. **Anything in §4's narrative you want added or removed** — it is chosen to hit the decisions the
   domain actually made, and it is easier to change now than after 15b.
