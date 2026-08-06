# Step 14 — the REST surface: proposal for review

*Not built. Nothing in this document has been implemented. Written 2026-07-29 for review before
any code is written.*

Everything below is grounded in the service interfaces that actually exist in `core-api` as of
`516964c` — every endpoint names the method it calls, so a reader can check that nothing here
invents a capability the core does not have.

---

## 1. What this step is, and what it is not

Steps 5–13 built services and no HTTP routes. `GET /api/chart-of-accounts` (step 4b) is the entire
REST surface, and it exists to make the `..core.web..` ArchUnit rule load-bearing rather than to
start the API. So the frontend — scaffolded and untouched since — has nothing to call.

Step 14 is the API for the workflows that drive the system end to end: master data, purchasing,
sales, settlement, stock, and a read-only view of the email outbox. **Q44's access-path check is
folded in**, because the outbox is the first route that reaches a document with visibility rules of
its own, and the decision to re-check was recorded in step 11's revision against exactly this moment.

**Deliberately not in this step:** users and roles, settings, the audit log, backup administration,
journal *writing* (manual journal entries), VAT-class and exemption-reason *administration*,
depreciation. Each has a service and can get a route later; none is needed to drive a trading
workflow, and adding them here triples the review surface for no operational gain.

---

## 2. Decisions that need a ruling before any code

These matter more than the endpoint list. Several of them are decisions the codebase has already
half-made and left a note about.

### D1 — Where controllers live

**Proposal: keep them in `..core.web..`, one package per slice** (`..core.web.product`,
`..core.web.purchasing`, …), as step 4b established.

The alternative is a separate `web` Maven module. Rejected for now: the ArchUnit rule that makes
`..core.web..` honest already exists and is proven to fail, the controllers depend only on
`core-api` types, and a new module buys nothing the rule does not already enforce. Worth revisiting
if the web layer ever grows its own DTOs in bulk (see D6).

### D2 — Authorisation: fail-closed declaration, not per-method copy-paste

Step 4b wrote `currentUser.require().requireView(Section.CHART_OF_ACCOUNTS)` inline, and its own
javadoc says: *"With many controllers this should become a shared interceptor."* This step is where
"many" arrives — roughly 100 handler methods.

The hazard with a shared mechanism is the opposite of the one 4b was avoiding. 4b's argument was
that a typed enum cannot be misspelled where a `@PreAuthorize("...")` string can, and a misspelled
expression that fails open is the worst outcome. An annotation keeps that property (it takes the
enum constant, not a string) but introduces a new one: **an annotation that is simply forgotten also
fails open.**

**Proposal — a declaration that cannot be omitted:**

1. A typed annotation, `@Requires(section = Section.PURCHASING, level = AccessLevel.VIEW)`, applied
   per handler method or per controller class.
2. A `HandlerInterceptor` that reads it and calls the existing `RoleView.requireView` /
   `requireEdit` — the same primitives, one call site.
3. **A handler under `/api/**` with no declaration is refused with 403 at runtime**, not allowed
   through.
4. **Plus a startup check** (or an architecture test — see the open question below) that enumerates
   every mapped handler under `/api/**` and fails if one carries no declaration. Failing at startup
   turns "someone forgot" from a silent hole into a build/boot failure, which is how this codebase
   has treated every other invariant of this weight.

Step 4b's `requireView` call is then deleted from `ChartOfAccountsController` and replaced by the
annotation, so there is one mechanism rather than two.

*Open sub-question for you:* startup check, ArchUnit test, or both? An ArchUnit test fails the build
(better) but can only see annotations, not Spring's actual handler mapping; a startup check sees the
real mapping but fails later. **Recommendation: both.** They catch different mistakes and neither is
expensive.

### D3 — How `Money`, `Quantity` and `UnitCost` are represented in JSON ⚠️ *the one that is hardest to change later*

This is the single most consequential decision in the step, because every document, balance and
stock figure crosses it, and changing it after the frontend is written means changing both sides.

`Money` is a `BigDecimal` plus a currency. `Quantity` and `UnitCost` are 6-decimal `BigDecimal`s.
`CLAUDE.md` rule 5 is absolute about `double` in Java — **and JSON has no decimal type.** A number
literal in JSON is parsed by JavaScript as an IEEE-754 double. So a naïve serialisation writes
`12.505` and the browser reads a double, which is precisely the rule the backend refuses to break,
broken at the boundary.

**Proposal:**

```json
"sellingPrice": { "amount": "12.50", "currency": "EUR" }
"quantity":     "3.000000"
"unitCost":     { "amount": "12.505000", "currency": "EUR" }
```

- **Amounts and quantities are JSON strings, never numbers.** The string is exact, and the frontend
  parses it with a decimal library (or simply displays it) rather than through `Number`.
- **`Money` keeps its currency in the payload** rather than being flattened to a bare amount, so the
  wire format matches ADR 0005 and the schema's currency-companion rule instead of quietly assuming
  EUR at the one layer that talks to the outside world.
- Serialisers/deserialisers are written once, registered globally, and **tested with a value that a
  double cannot represent** (`12.505`, the exact figure that produced Q45) — the same standard of
  proof the ArchUnit and schema rules are held to.

The alternative — integer minor units (`1250`) — is exact too, and rejected because it makes every
6-decimal quantity and unit cost a different scale from every amount, so the frontend has to know
which fields are cents and which are micro-units. A string carries its own scale.

### D4 — Endpoints mirror the services' commands; there is no whole-object `PUT`

The services are deliberately not CRUD. `ProductService` has `rename`, `changeSellingPrice`,
`changeDefaultVatClass`, `changeSupplier`, `changeEan`, `changeUnitOfMeasure`,
`changeSerialTracking`, `deactivate`, `reactivate` — nine named operations, each with its own
validation, and **no `update(product)`**.

A `PUT /api/products/{id}` taking a whole product would have to invent a diff-and-dispatch step in
the web layer, deciding which of the nine to call. That is domain logic in a controller, it silently
turns "the field was absent" into "set it to null", and it routes around validation the services
state per operation.

**Proposal: `PATCH /api/products/{id}/selling-price` and friends** — one route per service command,
named after it. Verbose in the endpoint count and honest about what can actually happen. Where a
command is an event rather than a field change (`dispose`, `reverse`, `deactivate`, `retry`,
`release`), it is a `POST` to a sub-resource.

### D5 — Error mapping, extended from 401/403 to the full set

`WebExceptionHandler` today maps only `SectionAccessDeniedException` → 403 and
`NotAuthenticatedException` → 401. The core throws a family of typed exceptions that all currently
become 500s.

| Core exception | Status | Body |
|---|---|---|
| `*NotFoundException` (14 of them) | **404** | `ProblemDetail`, generic |
| `Invalid*Exception` (13 of them) | **422** | `ProblemDetail`, **message included** |
| `UnbalancedJournalEntryException` | **422** | message included |
| `JournalEntryNotAmendableException` | **409** | message included |
| `VatClassNotDeterminableException` | **422** | message included |
| `EmailAttachmentUnavailableException` | **410 Gone** | message included |
| `AttachmentTooLargeException` | **413** | message included |
| `SectionAccessDeniedException` | 403 | **generic — unchanged** |
| `NotAuthenticatedException` | 401 | generic — unchanged |
| `IllegalArgumentException` | **400** | **generic**, logged |

**Two deliberate asymmetries.** The permission refusals stay generic, for the reason the existing
javadoc gives: a specific message confirms the section exists and describes the permission model.
The *validation* refusals do the opposite — they carry the core's message, because those messages
are written to be read ("a supplier SKU without a supplier", "cash settlements are limited to €500")
and an operator who cannot see why the document was refused cannot fix it. Bare
`IllegalArgumentException` stays generic because it is a programming error, not an operator one.

409 for a not-amendable entry rather than 422 is the one debatable row: it is a conflict with the
document's state, not a malformed request.

### D6 — Responses are `core-api` views, with one named exception

Step 4b returned `AccountGroupView` directly and recorded when that stops being right: *"the first
time a response needs a shape the core has no reason to have."*

**Proposal: continue returning core-api views**, with one exception, stated now rather than
discovered: **list responses are wrapped**, `{ "items": [...] }`, not a bare JSON array. A bare array
cannot later gain a total count, a next-page cursor or a "some rows were redacted" flag without
breaking every caller, and D8 says paging is coming.

Request bodies are the existing `New*` records where they fit (`NewProduct`, `NewPurchaseInvoice`,
…) and small web-local records where the command takes loose parameters (`rename(id, name)` becomes
a body of `{"name": "..."}`).

### D7 — The product redaction convention becomes an enforced rule

`HISTORY.md` flags this explicitly and says the first Products controller must be reviewed for it:

> `ProductService` has plain read methods (unredacted, for the core's own costing rules) and
> `...For(viewer)` variants that redact. **Anything answering a request from a person must use the
> `...For` variants.** … A named convention, not an enforced one.

Step 14 *is* the first Products controller, and this is the moment the convention either becomes a
rule or becomes a bug. **Proposal: an ArchUnit rule forbidding any class in `..core.web..` from
calling `ProductService.all()`, `active()`, `find(long)`, `require(long)`, `findBySku`,
`requireBySku`, `findByEan` or `bySupplier`** — the unredacted reads — and proven to fail against a
probe controller, the way every other rule in this repo has been.

**This surfaces a real gap.** The `For` family is incomplete: there is `allFor(viewer)`,
`findFor(id, viewer)` and `requireFor(id, viewer)`, but **no `activeFor`, `findBySkuFor`,
`findByEanFor` or `bySupplierFor`.** With the rule above, an "active products only" list or a SKU
lookup could not be served at all. Filtering the redacted list inside the controller is the wrong
fix — that is domain logic in the web layer, and it would read a field to filter on that may have
just been redacted.

**Proposal: add the four missing `For` variants to `ProductService`** as part of this step. That is
a change to a core service, so it is called out here rather than done quietly.

### D8 — No pagination in step 14, and say so

No list method in any service takes a limit or an offset (`EmailSender.failed(int)` and
`pending(int)` are the only bounded ones). Adding paging means changing every one of them plus their
repositories.

**Proposal: ship step 14 unpaged**, with D6's wrapped list envelope so paging can be added without
breaking callers, and with date-range filters wired wherever a service already offers `between(from,
to)` — which is most transaction lists, so the genuinely unbounded reads are the master-data ones.

**Stated plainly rather than left to be discovered: `GET /api/products` returns every product in one
response.** That is fine at hundreds and not at tens of thousands. The trigger to act is the first
list that is slow, and the envelope is what makes acting cheap.

### D9 — Q44's access-path check needs a mapping that does not exist ⚠️

The decision recorded in ADR 0012 and in `EmailSender.downloadAttachment`'s javadoc is that a
*referenced* attachment must re-check the caller's permission **against the core record the document
belongs to**. Implementing it turns up three things that were not visible when the decision was made:

**(a) `AttachmentService.entityType` is free text.** The signature is
`attach(String entityType, String entityId, …)` and `AttachmentMetadata.entityType` is a `String`.
There is no mapping from an entity type to a `Section`, and no enum of valid types — so there is
currently nothing to call `requireView` *with*.

*Proposal:* a single typed registry in `core-api` mapping owner type → `Section`
(`"PurchaseInvoice"` → `PURCHASING`, `"SalesInvoice"` → `SALES`, `"Product"` → `PRODUCTS`, …),
**with an unknown type denied, not allowed.** Fail-closed is the only defensible default: an
unrecognised owner type means the check does not know what it is guarding.

**(b) Nothing calls `AttachmentService.attach` yet.** No core record has attachments today, so the
registry starts nearly empty and the referenced-attachment path is currently unreachable in
practice. That does not make the check optional — it makes it cheap to get right now, and it is
exactly the situation the javadoc was written to prevent (a gap discovered later rather than a
requirement implemented on time).

**(c) Where the check lives.** Two options:

- *In the controller.* Keeps `EmailSender` caller-agnostic; but the guarantee is then per-route, and
  the next caller of `downloadAttachment` re-implements it or forgets.
- *In the service, taking the viewer explicitly:* `downloadAttachment(long id, RoleView viewer)`.

**Recommendation: the service, with an explicit `RoleView` parameter** — the same shape as
`ProductService.requireFor(id, viewer)`, which is the established pattern here. It makes the
guarantee unconditional for every future caller, it is testable with no security context, and it
does not put ambient identity inside a core service. The existing single-argument overload should be
**removed**, not kept alongside, so there is no unchecked path left to call by accident.

### D10 — The email outbox needs a `Section`, which is Q44's still-open half ⚠️ *blocking*

Q44's access-path half is decided; **its section half is not**, and D2 makes it blocking: a handler
with no section declaration is refused, so the outbox endpoints cannot exist until this is answered.

The failure list carries recipients and subjects — a customer-correspondence trail. Bodies are
already absent from `QueuedEmailView` by design.

**Recommendation: a new `Section.EMAIL_OUTBOX`**, rather than folding it into `SETTINGS`. SMTP
*configuration* is a settings concern; who was emailed, about what, and what failed is operational
history about customers and suppliers. Granting someone the ability to change the SMTP password
should not hand them the correspondence log, and vice versa — the same argument that separates
`JOURNAL` from `CHART_OF_ACCOUNTS` and `INVENTORY` from `PRODUCTS`.

Cost: one enum constant, no migration (access is default-deny, so it is invisible to every
non-full-access role until granted).

---

## 3. Proposed sequencing — three commits, not one

Roughly 100 endpoints. One commit would be unreviewable and would violate the spirit of "one commit
per build step" more than splitting does.

| Sub-step | Contents | Why this boundary |
|---|---|---|
| **14a — foundations + master data** | D2 authorisation mechanism, D3 money serialisation, D5 error mapping, D6 envelope, D7 ArchUnit rule + the four `For` variants; then lookups, chart of accounts, products/bundles, customers, suppliers, assets | Every later route depends on the foundations. Master data is where the redaction rule bites, so it gets reviewed with the mechanism fresh |
| **14b — purchasing + inventory** | Purchase invoices, goods receipts, GR/IR, freight allocation, lots, units, consumptions, write-offs | One document flow. Needs 14a's suppliers and products to be callable |
| **14c — sales, settlements, outbox** | Sales invoices, credit notes, settlements, allocations, open items, bank transfers, the outbox, **and Q44** | Needs 14a's customers. Q44 lands with the only screen it guards |

Each is green on its own (`mvn clean verify`), each has its own controller tests in `app` against a
real filter chain, and each is committed separately.

---

## 4. The endpoint list

Conventions throughout: all routes are under `/api`, all require authentication (the filter chain
already returns 401, not a redirect), all mutations require the CSRF token the existing
configuration issues, every route names its `Section` and required `AccessLevel`, and every list
response is `{ "items": [...] }` per D6.

`VIEW` = `RoleView.requireView`; `EDIT` = `requireEdit`.

### 4.1 Lookups — read-only, needed to populate every form

Not on your list, included with a reason: a Product form needs a VAT class id and a unit-of-measure
id, and an invoice line needs an exemption reason or a charge type. Without these the CRUD below
cannot be driven from a browser at all. Read-only — administering these lookups is out of scope.

| Method | Path | Service call | Section / level |
|---|---|---|---|
| GET | `/vat-classes?active=` | `VatClassService.all` / `.active` | TAX_AND_CHARGES / VIEW |
| GET | `/vat-classes/{id}` | `.require` | TAX_AND_CHARGES / VIEW |
| GET | `/vat-exemption-reasons?active=` | `VatExemptionReasonService.all` / `.active` | TAX_AND_CHARGES / VIEW |
| GET | `/charge-types?active=` | `ChargeTypeService.all` / `.active` | TAX_AND_CHARGES / VIEW |
| GET | `/units-of-measure?active=` | `UnitOfMeasureService.all` / `.active` | PRODUCTS / VIEW |

*Open question, small:* `units-of-measure` under `PRODUCTS` or `TAX_AND_CHARGES`? It is a product
attribute and its myDATA code is a tax concern. Proposed `PRODUCTS`, because that is who reads it.

### 4.2 Chart of accounts — `Section.CHART_OF_ACCOUNTS`

| Method | Path | Service call | Level |
|---|---|---|---|
| GET | `/chart-of-accounts` | `chart()` — **exists** | VIEW |
| GET | `/account-groups` | `groups()` | VIEW |
| GET | `/accounts?active=&kind=&subLedgerType=&expectedToClear=` | `allAccounts` / `activeAccounts` / `activeAccountsOfKind` / `activeControlAccountsFor` / `activeAccountsExpectedToClear` | VIEW |
| GET | `/accounts/settlement-targets` | `activeSettlementTargets` | VIEW |
| GET | `/accounts/{id}` | `requireAccount` | VIEW |
| POST | `/accounts` | `createAccount` | EDIT |
| POST | `/account-groups` | `createGroup` | EDIT |
| PATCH | `/accounts/{id}/name` | `renameAccount` | EDIT |
| PATCH | `/account-groups/{id}/name` | `renameGroup` | EDIT |
| POST | `/accounts/{id}/deactivate` | `deactivate` | EDIT |
| POST | `/accounts/{id}/reactivate` | `reactivate` | EDIT |
| PUT | `/account-groups/{id}/account-order` | `reorderAccountsWithinGroup` | EDIT |
| PUT | `/account-groups/order` | `reorderGroups` | EDIT |

**Note on `deactivate`:** it returns `Optional<Money>` — the residual balance, if the account is not
empty. That is a refusal-with-a-reason, not a void, and the response must carry it
(`{"deactivated": false, "residualBalance": {...}}`) rather than returning 204 and losing it.

**Note on the two reorder routes:** `PUT`, and the body must name **every** member exactly once —
the service refuses a partial list rather than leaving the remainder in an order nobody chose
(rule 7). The route is `PUT` precisely because it replaces the whole ordering.

### 4.3 Products and bundles — `Section.PRODUCTS`

**Every read here uses the `...For(viewer)` variants (D7).**

| Method | Path | Service call | Level |
|---|---|---|---|
| GET | `/products?active=&supplierId=&sku=&ean=` | `allFor` / **`activeFor`** / **`findBySkuFor`** / **`findByEanFor`** / **`bySupplierFor`** (bold = to be added, D7) | VIEW |
| GET | `/products/{id}` | `requireFor` | VIEW |
| GET | `/products/{id}/stock` | `InventoryService.stockOf` | VIEW |
| POST | `/products` | `create` | EDIT |
| PATCH | `/products/{id}/name` | `rename` | EDIT |
| PATCH | `/products/{id}/selling-price` | `changeSellingPrice` | EDIT |
| PATCH | `/products/{id}/vat-class` | `changeDefaultVatClass` | EDIT |
| PATCH | `/products/{id}/supplier` | `changeSupplier` | EDIT |
| PATCH | `/products/{id}/ean` | `changeEan` | EDIT |
| PATCH | `/products/{id}/unit-of-measure` | `changeUnitOfMeasure` | EDIT |
| PATCH | `/products/{id}/serial-tracking` | `changeSerialTracking` | EDIT |
| POST | `/products/{id}/deactivate` | `deactivate` | EDIT |
| POST | `/products/{id}/reactivate` | `reactivate` | EDIT |
| GET | `/products/{id}/components` | `BundleService.componentsOf` | VIEW |
| PUT | `/products/{id}/components` | `BundleService.define` | EDIT |
| DELETE | `/products/{id}/components` | `BundleService.dissolve` | EDIT |
| GET | `/bundles` | `allBundles` | VIEW |
| GET | `/bundles/unpriced-components` | `bundlesWithUnpricedComponents` | VIEW |
| GET | `/products/{id}/in-bundles` | `bundlesContaining` | VIEW |

**`GET /products/{id}/stock` is under `PRODUCTS`, not `INVENTORY`, and that is deliberate.**
`StockLevels` carries quantities per location and a sellable figure — no cost. `Section.INVENTORY`'s
own javadoc gives exactly this reason: an order picker with VIEW on Products needs to know there are
three left; a *lot* carries its unit cost, which is what the section separation protects. Putting
this route under INVENTORY would make Remote/Order Staff unable to do its job or force a grant that
hands over cost data.

**`PUT /products/{id}/components` is `PUT`, not `PATCH`** — `define` replaces the whole component
list and never merges, so a bundle is never left half-changed.

### 4.4 Customers — `Section.CUSTOMERS`

| Method | Path | Service call | Level |
|---|---|---|---|
| GET | `/customers?active=` | `all` / `active` | VIEW |
| GET | `/customers/{id}` | `require` | VIEW |
| GET | `/customers/by-vat-number/{vatNumber}` | `findByVatNumber` | VIEW |
| GET | `/customers/match-suggestions?name=&email=&phone=` | `suggestMatches` | VIEW |
| POST | `/customers` | `create` | EDIT |
| PATCH | `/customers/{id}/name` | `rename` | EDIT |
| PATCH | `/customers/{id}/contact-details` | `changeContactDetails` | EDIT |
| PATCH | `/customers/{id}/vat-number` | `changeVatNumber` | EDIT |
| PATCH | `/customers/{id}/vat-status` | `changeVatStatus` | EDIT |
| PATCH | `/customers/{id}/vat-class-override` | `changeVatClassOverride` | EDIT |
| POST | `/customers/{id}/deactivate` \| `/reactivate` | `deactivate` / `reactivate` | EDIT |

**The two lookup routes are separate on purpose** (rule 7). `by-vat-number` is an exact match on an
authority-issued identifier and may be applied automatically; `match-suggestions` returns candidates
a human must confirm. One combined "search" endpoint would erase that distinction at exactly the
layer where a UI decides whether to auto-fill or to ask.

### 4.5 Suppliers — `Section.SUPPLIERS`

Identical shape to customers, minus `vat-class-override`: `GET /suppliers?active=`,
`/suppliers/{id}`, `/suppliers/by-vat-number/{vatNumber}`, `/suppliers/match-suggestions`,
`POST /suppliers`, `PATCH .../name | /contact-details | /vat-number | /vat-status`,
`POST .../deactivate | /reactivate`. (11 routes.)

### 4.6 Fixed assets — `Section.FIXED_ASSETS`

| Method | Path | Service call | Level |
|---|---|---|---|
| GET | `/assets?status=IN_USE` | `all` / `inUse` | VIEW |
| GET | `/assets/depreciable` | `depreciable` | VIEW |
| GET | `/assets/without-depreciation-rate` | `withoutDepreciationRate` | VIEW |
| GET | `/assets/{id}` | `require` | VIEW |
| GET | `/assets/by-code/{code}` | `findByCode` | VIEW |
| POST | `/assets` | `create` | EDIT |
| PATCH | `/assets/{id}/name` | `rename` | EDIT |
| PATCH | `/assets/{id}/depreciation-rate` | `changeDepreciationRate` | EDIT |
| PATCH | `/assets/{id}/depreciation-start-date` | `changeDepreciationStartDate` | EDIT |
| POST | `/assets/{id}/disposal` | `dispose` | EDIT |
| POST | `/assets/{id}/reinstatement` | `reinstate` | EDIT |

`without-depreciation-rate` and `depreciable` are their own paths rather than query filters because
they are the lists somebody actually opens — the register's real state is that the statutory rates
are still pending the accountant, and `withoutDepreciationRate()` exists so that stops being
forgettable.

**An asset's carrying value is not here.** It is `JournalService.subLedgerBalanceOf`, which is
`Section.JOURNAL` — every posting against the asset. Exposing it on the asset route would be a
second, weaker path to ledger data, the same mistake Q44 exists to prevent on the outbox.

### 4.7 Purchasing — `Section.PURCHASING`

| Method | Path | Service call | Level |
|---|---|---|---|
| POST | `/purchase-invoices` | `record` | EDIT |
| GET | `/purchase-invoices?supplierId=&from=&to=` | `ofSupplier` / `between` | VIEW |
| GET | `/purchase-invoices/{id}` | `require` | VIEW |
| POST | `/purchase-invoices/{id}/reversal` | `reverse` | EDIT |
| GET | `/purchase-invoices/{id}/gr-ir-matches` | `matchesOf` | VIEW |
| GET | `/purchase-invoices/variances?from=&to=` | `variancesBetween` + `totalVarianceBetween` | VIEW |
| GET | `/purchase-invoice-lines/awaiting-delivery` | `linesAwaitingDelivery` | VIEW |
| POST | `/goods-receipts` | `record` | EDIT |
| GET | `/goods-receipts?supplierId=&from=&to=` | `ofSupplier` / `between` | VIEW |
| GET | `/goods-receipts/{id}` | `require` | VIEW |
| POST | `/goods-receipts/{id}/reversal` | `reverse` | EDIT |
| GET | `/goods-receipt-lines/awaiting-invoice` | `linesAwaitingInvoice` | VIEW |
| GET | `/goods-receipts/by-lot/{lotId}` | `findByLot` | VIEW |

**Reversal is `POST .../reversal`, never `DELETE`.** Documents are immutable (ADR 0006); a reversal
creates a new posting and both documents stand. A `DELETE` route would read as though the document
could go away.

**Freight / landed cost** — *not on your list; recommended, flag if you want it cut.* Without it the
`Freight / Landed Cost — Unallocated` account can never be cleared from a browser, which is the
account step 10 was built to make clearable.

| Method | Path | Service call | Level |
|---|---|---|---|
| POST | `/freight-allocations` | `allocate` | EDIT |
| GET | `/freight-allocations/{id}` | `require` | VIEW |
| GET | `/freight-allocations?purchaseInvoiceLineId=&lotId=&from=&to=` | `ofPurchaseInvoiceLine` / `ofLot` / `between` | VIEW |
| POST | `/freight-allocations/{id}/reversal` | `reverse` | EDIT |
| GET | `/purchase-invoice-lines/awaiting-allocation` | `linesAwaitingAllocation` | VIEW |
| GET | `/purchase-invoice-lines/{id}/unallocated-amount` | `unallocatedAmountOf` | VIEW |

### 4.8 Inventory — `Section.INVENTORY`

| Method | Path | Service call | Level |
|---|---|---|---|
| GET | `/inventory/lots?productId=&location=&open=` | `lotsOf` / `openLotsOf` / `lotsAt` | VIEW |
| GET | `/inventory/lots/{id}` | `requireLot` | VIEW |
| GET | `/inventory/lots/with-landed-cost` | `lotsWithAllocatedLandedCost` | VIEW |
| POST | `/inventory/lots/{id}/location` | `moveLot` | EDIT |
| GET | `/inventory/lots/{id}/units` | `unitsOf` | VIEW |
| GET | `/inventory/units?productId=&location=&serialNumber=` | `unitsOfProduct` / `unitsAt` / `findUnitBySerialNumber` | VIEW |
| POST | `/inventory/units/{id}/location` | `moveUnit` | EDIT |
| GET | `/inventory/consumptions?productId=&from=&to=` | `consumptionsOf` / `consumptionsBetween` | VIEW |
| GET | `/inventory/consumptions/with-shortfall` | `consumptionsWithShortfall` | VIEW |
| GET | `/inventory/consumptions/{id}` | `requireConsumption` | VIEW |
| POST | `/inventory/write-offs` | `writeOff` | EDIT |
| GET | `/inventory/write-offs?lotId=&from=&to=` | `writeOffsOf` / `writeOffsBetween` | VIEW |
| GET | `/inventory/write-offs/{id}` | `requireWriteOff` | VIEW |
| POST | `/inventory/write-offs/{id}/reversal` | `reverseWriteOff` | EDIT |

**⚠️ Seven `InventoryService` methods get no route, deliberately, and this is the most important
statement in this section.** `receive`, `consume`, `reverseConsumption`, `applyLandedCost`,
`removeLandedCost`, `unreceive` and `stockOfAll` are the *lower layer* — they are called by Goods
Receipt, Sales Invoice and Freight Allocation, which post the journal entry that goes with the stock
movement. An HTTP route to `receive` would create a lot with **no document and no posting**, leaving
the Inventory control account disagreeing with what the lots carry — exactly the invariant ADR 0015
and `WholeScenarioIT` exist to hold. Stock moves through documents, never directly.

`/inventory/lots/{id}/location` is a `POST` to a sub-resource rather than a `PATCH` on the lot,
because `moveLot` refuses a serial-tracked lot outright — it is an operation with its own rules, not
a field.

### 4.9 Sales — `Section.SALES`

| Method | Path | Service call | Level |
|---|---|---|---|
| POST | `/sales-invoices` | `record` | EDIT |
| GET | `/sales-invoices?customerId=&from=&to=` | `ofCustomer` / `between` | VIEW |
| GET | `/sales-invoices/{id}` | `require` | VIEW |
| POST | `/sales-invoices/{id}/reversal` | `reverse` | EDIT |
| GET | `/sales-invoices/rounding-differences?from=&to=` | `withAcceptedRoundingDifference` + `totalRoundingBetween` | VIEW |
| POST | `/credit-notes` | `issue` | EDIT |
| GET | `/credit-notes?customerId=&salesInvoiceId=&from=&to=` | `ofCustomer` / `againstInvoice` / `between` | VIEW |
| GET | `/credit-notes/{id}` | `require` | VIEW |
| POST | `/credit-notes/{id}/reversal` | `reverse` | EDIT |

**A rounding difference above the threshold refuses the document** until somebody accepts it
(Q15). So `POST /sales-invoices` has a real 422 path whose message must reach the operator, and the
acceptance is a field on the request body — not a second endpoint and not a review queue. Step 9
rejected a queue explicitly; the API must not reintroduce one.

### 4.10 Settlements — `Section.SETTLEMENTS`

| Method | Path | Service call | Level |
|---|---|---|---|
| POST | `/settlements` | `record` (direction in the body: receipt or payment) | EDIT |
| GET | `/settlements?partyType=&partyId=&from=&to=` | `ofParty` / `between` | VIEW |
| GET | `/settlements/{id}` | `require` | VIEW |
| GET | `/settlements/unallocated` | `withUnallocatedAmount` | VIEW |
| PATCH | `/settlements/{id}` | `amend` | EDIT |
| POST | `/settlements/{id}/allocations` | `allocate` | EDIT |
| POST | `/credit-notes/{id}/allocations` | `allocateCreditNote` | EDIT |
| POST | `/customer-credits/{id}/allocations` | `allocateCustomerCredit` | EDIT |
| DELETE | `/allocations/{id}` | `release` | EDIT |
| GET | `/open-items?partyType=&partyId=` | `openItemsFor` / `allOpenItems` | VIEW |
| GET | `/customer-credits?customerId=&open=` | `customerCreditsOf` / `openCustomerCredits` | VIEW |
| POST | `/bank-transfers` | `record` | EDIT |
| GET | `/bank-transfers?accountId=&from=&to=` | `involving` / `between` | VIEW |
| GET | `/bank-transfers/{id}` | `require` | VIEW |
| POST | `/bank-transfers/{id}/reversal` | `reverse` | EDIT |

**`PATCH /settlements/{id}` is the one document-shaped `PATCH` in the whole surface, and it is
correct.** ADR 0006 draws the line at whether the record exists *outside* NovoCore: invoices and
credit notes are immutable and corrected by reversal; receipts, payments and transfers are our own
and are edited in place with the previous state written to the audit log. Amending below the
allocated total releases allocations most-recent-first, each release audit-logged (Q13's second
half) — so this single route can cascade, and its tests need to prove it.

**`DELETE /allocations/{id}` is the only `DELETE` in the surface that removes a row**, and that is
right: ADR 0009 says an allocation is a statement about a current relationship, not a record of an
event, and `release` genuinely deletes. Everywhere else, `DELETE` would be wrong.

### 4.11 Email outbox — read-only — `Section.EMAIL_OUTBOX` *(pending D10)*

| Method | Path | Service call | Level |
|---|---|---|---|
| GET | `/email/outbox?status=FAILED\|PENDING&limit=` | `failed` / `pending` | VIEW |
| GET | `/email/outbox/{id}` | `find` | VIEW |
| GET | `/email/outbox/{id}/attachments` | `attachmentsOf` | VIEW |
| GET | `/email/attachments/{id}/content` | `downloadAttachment` — **the Q44 check** | VIEW **+ the record's own section** |
| POST | `/email/outbox/{id}/retry` | `retry` | EDIT |

`POST /email/outbox/{id}/retry` is the one write, and it is outside the "read-only" you asked for —
**flagged, cut it if you prefer.** The argument for keeping it: re-queueing is deliberately manual
(a message that gave up failed for a reason that is still true), and without a route the only way to
retry is a database session. The argument against: nothing has ever needed it yet.

**`GET /email/attachments/{id}/content` is where Q44 lands.** For a *referenced* attachment it
resolves the underlying document through `AttachmentService`, maps its owner type to a `Section` via
D9's registry, and calls `requireView` on that section — **in addition to** the outbox's own section.
An unknown owner type is refused. An *inline* attachment has no core record and is governed by the
outbox section alone. The bytes stream only after both checks pass.

**This must be proven by a test that fails against the unguarded version**, the way the audit-log
fix was: a role with EMAIL_OUTBOX but not PURCHASING gets 403 on a purchase invoice PDF that was
emailed, and 200 on an inline report. A structurally-correct implementation with no such test would
be worth nothing — that was the explicit lesson from `AuditLogServiceImpl`.

---

## 5. Approximate size

| Group | Routes |
|---|---|
| Lookups | 5 |
| Chart of accounts | 13 |
| Products + bundles | 19 |
| Customers | 11 |
| Suppliers | 11 |
| Assets | 11 |
| Purchasing (incl. freight) | 19 |
| Inventory | 14 |
| Sales | 9 |
| Settlements | 15 |
| Email outbox | 5 |
| **Total** | **≈132** |

Plus: the authorisation mechanism, the money serialisers, the extended error handler, the ArchUnit
rule, the four new `ProductService` methods, and `Section.EMAIL_OUTBOX`.

**Migrations: none expected.** Nothing here changes the schema. `Section.EMAIL_OUTBOX` is an enum
constant and grants are default-deny, so no seed is required either.

> ⚠️ **This turned out to be wrong, and the correction is worth more than the estimate was.**
> `role_section_grant` carries a CHECK listing every known section by name, so a `Section` value
> existing only in Java **cannot be granted at all** — every insert is refused by the database. 14c
> therefore needed **migration V25**. The constraint is doing exactly what it was built for (the
> same pattern as `journal_entry_source_known`): the database states the value list independently,
> so neither side can drift unnoticed, and the price is that adding a section is a migration.
> Found by three failing tests rather than by reasoning, which is the argument for the guard
> `SecurityIT` now carries.

---

## 6. What still needs your answer before 14a starts

1. **D3** — money as `{amount: "12.50", currency: "EUR"}` with string amounts. *(Hardest to change later.)*
2. **D2** — fail-closed section declaration, refusing any undeclared `/api/**` handler; ArchUnit + startup check.
3. **D7** — add `activeFor`, `findBySkuFor`, `findByEanFor`, `bySupplierFor` to `ProductService`, and enforce the redaction convention with an ArchUnit rule.
4. **D9** — a typed owner-type → `Section` registry, fail-closed on unknown; and `downloadAttachment(id, viewer)` replacing the single-argument form.
5. **D10** — a new `Section.EMAIL_OUTBOX`. **Blocking:** without it the outbox has no section to declare.
6. **Scope calls:** keep or cut the freight allocation routes (§4.7) and the outbox retry route (§4.11).
7. **Sequencing:** three sub-steps and three commits, or one.
