# Step 16 — Products, the first real screen

**Proposal for review. Nothing below is built.** Frontend only.

Baseline: foundations complete and reviewed at `8e258b3` — 120 tests, 174 routes generated, lint /
typecheck / knip / build / offline-check clean.

Products is first because it exercises the primitives most likely to be subtly wrong — money
display, redaction, per-field PATCH, enum labels — while the foundations are days old and cheap to
change. The pattern this screen establishes gets copied by every master-data screen after it, so the
pattern *is* the deliverable; the screen is how it gets proven.

---

## 0. What the API actually offers, which shapes everything below

Read from the spec, not assumed.

**There is no `PUT /api/products/{id}`.** Editing is **seven per-field PATCH routes** —
`name`, `ean`, `selling-price`, `supplier`, `unit-of-measure`, `vat-class`, `serial-tracking` — plus
`deactivate` / `reactivate`. There is no delete. **Every PATCH returns the complete updated
`ProductView`**, which is what makes the pattern in §2 cheap.

**`GET /api/products` is not paged** and takes `active`, `supplierId`, `sku`, `ean`. It returns
`ListResponse_ProductView` with no `page` object, so `DataTable` pages it in the browser and will
switch itself the day the backend pages it.

**`ProductView` resolves its unit of measure and leaves everything else as an id:**

| Field | Shape | Consequence |
|---|---|---|
| `unitOfMeasure` | full `UnitOfMeasureView` | name, code and `fractionalQuantityAllowed` in hand |
| `defaultVatClassId` | `int64` | needs `GET /api/vat-classes` — **`TAX_AND_CHARGES`** |
| `supplierId` | `int64` | needs `GET /api/suppliers` — **`SUPPLIERS`** |
| `sellingPrice` | `Money` (2dp) | `formatMoney` |
| `lastPurchasePrice` | `UnitCost` (**6dp**) | `formatUnitCost` — the function the review pass split out |
| `hiddenFields` | `ProtectedField[]` | per-record redaction, read with `hiddenInResponse` |

### 0.1 The one genuinely new problem: a product screen spans three sections

`REMOTE_ORDER_STAFF` holds `PRODUCTS` at `VIEW` and nothing else. So for that role — the seeded,
realistic case — **the VAT class and supplier lookups are both 403.** A product screen that fetches
them unconditionally shows an order picker two failed requests and either a spinner that never
resolves or an error toast about something they never asked for.

**Decision: a lookup is not fetched unless the grant for it is held.** `canView(TAX_AND_CHARGES)`
gates the VAT class query; `canView(SUPPLIERS)` gates the supplier query. Where the lookup is not
permitted, the field renders the raw identifier rather than a name — because "VAT class 3" is
honest and useful, while a blank cell says the product has no VAT class, which is false.

This is not a Products quirk. Sales invoices, purchase invoices and goods receipts all reference
parties and tax the same way, so this rule is written down once here and reused.

---

## 1. Routes and layout

Three routes, all already declared in the navigation tree (`/products` exists; the two below are
added to it, and `tree.test.ts` will hold them to the same spec check as everything else):

| Route | What |
|---|---|
| `/products` | The list, with a **Products / Bundles** tab pair |
| `/products/new` | Create |
| `/products/:id` | Detail and editing |

Detail is its own route rather than a sheet over the list: a product is a thing somebody links to,
comes back to, and keeps open beside a supplier's website. **Roast Dates is not a tab** — no API
exists, and inventing a placeholder tab recreates exactly what step 16b removed.

### The list

`DataTable` over `GET /api/products`, client-paged today, columns:

SKU · Name · Type (enum label) · Unit · Selling price · Last purchase price · Supplier · flags
(serial-tracked, bundle) · Active.

Filters: **active-only by default** with a toggle (an inactive product is a historical record, not
something you are looking for), plus supplier and a SKU/EAN search box mapped to the endpoint's own
`sku` / `ean` parameters rather than filtered in the browser.

The **Bundles tab** swaps the query to `GET /api/bundles` and keeps the same columns. It is the same
table over a different endpoint, not a second implementation.

### The detail

Header: SKU, name, and badges for inactive / bundle / serial-tracked. Then three panels:

- **Fields** — the seven editable ones, per §2.
- **Stock** — `GET /api/products/{id}/stock`, whose `byLocation` is a map of `StockLocation` to
  `Quantity`; the enum labels already exist in both languages and the quantities are strings
  rendered with `formatQuantity`. Read-only.
- **Bundle** — if `bundle`, its components from `GET /api/products/{id}/components`; otherwise the
  bundles this product appears in, from `GET /api/products/{id}/in-bundles`. **Read-only this pass**
  — `PUT`/`DELETE /components` is its own follow-up, because editing a bundle is a different
  interaction from editing a field.

Deactivate / reactivate sit in the header, behind a confirmation, and are the only destructive-ish
actions on the screen.

---

## 2. The pattern: one field, one request

**This is the part that outlives the screen.** Every master-data screen in NovoCore faces the same
API shape, so this is decided once.

A `<FieldEditor>` owns exactly one field. It renders read-only until clicked, edits in place, and
saves on blur or Enter — one PATCH, one field.

- **No batching.** A form that fires seven PATCHes on submit invents partial-failure states the
  backend has no transaction to prevent, and leaves the operator guessing which three of seven
  changes landed. Nothing on this screen ever produces that state.
- **The response is the new state.** Each PATCH returns the full `ProductView`, so the result is
  written straight into the cache (`setQueryData(['products', id])`) instead of triggering a
  refetch: one round trip, no flicker, and the value on screen is the value the server holds.
- **The list is invalidated, not patched.** After a successful save the `['products']` list query is
  marked stale so a return to the list shows the change.
- **A refusal belongs to its field.** The error renders under the field that caused it, carrying the
  backend's `detail` — which explains itself for a validation refusal and deliberately says nothing
  for a permission refusal. ⚠️ Those messages are **English prose in a Greek UI** (Q47(b): the
  backend localises nothing). Known, recorded, not solvable here.
- **Editing is gated on `canEdit(PRODUCTS)`.** A `VIEW` role sees the same screen with no edit
  affordances at all — not disabled buttons that produce 403s.

Field by field:

| Field | Control | Notes |
|---|---|---|
| `name` | text | |
| `ean` | text | |
| `sellingPrice` | **`MoneyInput`** | currency from the existing value; never defaulted |
| `unitOfMeasure` | select over `GET /api/units-of-measure` | `PRODUCTS` grant — always available here |
| `vatClass` | select over `GET /api/vat-classes` | needs `TAX_AND_CHARGES`; otherwise read-only id |
| `supplier` | select + supplier SKU text | needs `SUPPLIERS`; redaction-aware (below) |
| `serialTracked` | switch | |

`lastPurchasePrice` is **not editable** — it is derived from purchase invoices.

### Redaction, made load-bearing

The first real consumer of the mechanism the review pass corrected. For each row and for the detail
record, `hiddenInResponse(product, field)` decides whether a value is absent because it was withheld.
A withheld value renders as `—` with "hidden from your role" on hover; an absent-but-permitted value
renders as an empty cell. **They must not look the same**, which is the entire point.

Since V26 no role restricts anything, so the tests create a restricted role at runtime through
`PUT /api/roles/{id}/field-restrictions/{field}` — the same choice the backend's own redaction tests
made, and for the same reason.

---

## 3. Create

`/products/new`, one form, `POST /api/products` with `NewProduct`: `sku`, `name`, `type`,
`unitOfMeasureId`, `defaultVatClassId`, `sellingPrice`, and optional `supplierId` / `supplierSku` /
`serialTracked`.

Creation is the one place a form *does* batch, because the backend does: it is a single request that
either creates a product or does not. On success, straight to `/products/:id` — the operator is
almost always about to look at what they just made.

A role without `TAX_AND_CHARGES` cannot choose a VAT class, and `defaultVatClassId` is required — so
**create is offered only when both grants are held**, rather than presenting a form that cannot be
completed.

---

## 4. What gets tested

Beside the existing 120:

- The list renders money at 2dp and last purchase price at 6dp, in both locales.
- A restricted role sees `—` for a withheld supplier, and an empty cell for a genuinely unset one.
- A `VIEW` role sees no edit affordances; a `FULL` role does.
- A field save sends exactly one PATCH, to the right route, and the response replaces the cached
  record without a refetch.
- A refused save leaves the field in edit state, shows the `detail`, and does not update the cache.
- A role without `TAX_AND_CHARGES` fires **no** VAT class request and renders the id.
- `QuantityInput` refuses a fraction where the unit of measure forbids it — driven from the real
  `unitOfMeasure.fractionalQuantityAllowed`, not a fixture.
- Bundle components render with quantities as strings.

---

## 5. Deliberately not in this pass

- **Bundle editing** (`PUT` / `DELETE /components`) — a different interaction; its own follow-up.
- **Roast Dates** — no API.
- **Stock counts, price history, bulk edit, import** — none exist.
- **Units of measure administration** — it has seven routes and its own Settings item; a select over
  the list is all Products needs.

---

## Addendum — what the build turned up

### ⚠️ Every write in the generated client was a `useQuery`

The first screen to attempt a write found it: **`useProductControllerRename` was a query hook, not a
mutation** — and so were all 66 POST, PUT, PATCH and DELETE routes. Not one `useMutation` existed in
the entire generated client.

A component that merely rendered `useProductControllerRename(id, { name })` would have **sent the
PATCH on mount**, and again on every refetch, invalidation and window focus. Rendering a screen
would have written to the ledger.

The cause was one line in `orval.config.ts` — `query: { useQuery: true }` — which forces *every*
operation into a query rather than letting orval apply its own rule of GET-is-a-query,
everything-else-is-a-mutation. It was written during the foundations pass and nothing caught it,
because nothing consumed a write hook: the foundations built no screen, and the reviews read code
that never called one.

Fixed by removing the line and regenerating; the client now has 20 modules with mutations.
`products.test.tsx` asserts that **rendering the detail screen sends no non-GET request at all**, so
this cannot return quietly.

**The lesson is the one this project keeps relearning** — Q45, step 15, and now this: *a checker
only covers what it is pointed at*. Three reviews and 120 tests passed over a client whose every
write was wired to fire on render, because no test had ever rendered a component that used one.

### Smaller things found while building

- **`Money` and `UnitCost` render with a currency symbol**, so a test looking for `18.50` finds
  nothing — it is `€18.50`, or `18,50 €` in Greek. Assertions now match what is rendered.
- **A row re-renders when its supplier lookup lands**, detaching nodes found before it. The list
  test waits for the resolved supplier name rather than the SKU, then re-queries.
- **Base UI refuses an anchor rendered as a native button** — the "New product" action is a `Link`,
  so it needs `nativeButton={false}` rather than silently shipping broken button semantics.
- **`Select` hands back `null` when cleared**, not `''`; the id parsers accept both.

## 6. To confirm before building

1. **Create as a route (`/products/new`)** rather than a dialog — recommended, for consistency with
   detail-as-a-route.
2. **Active-only by default** in the list, with a toggle — recommended.
3. **Bundles as a tab on `/products`** rather than a separate menu item — matches the kickoff tree.
4. **Where a lookup is not permitted, show the raw id** rather than a blank — recommended, per §0.1.
