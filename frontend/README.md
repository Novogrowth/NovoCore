# NovoCore — frontend

Vite + React 19 + TypeScript + Tailwind v4 + shadcn/ui (Base UI, `base-luma` style, Zinc, Phosphor
icons, Manrope). Talks to the backend in `../backend` through its committed OpenAPI spec.

## Running it

Local development needs **two processes**, and neither starts by itself — not on boot, not when you
open the editor. Starting them is a two-command routine at the beginning of every session, not a
one-time setup step:

```bash
# 1. the backend stack, from ../docker
docker compose -f compose.yml -f compose.dev.yml up --build

# 2. the dev server, from here — a separate process, in its own terminal
npm install          # first time, or after a dependency change
npm run dev          # http://127.0.0.1:5173
```

Both keep running until you stop them, and both are gone after a reboot. `Firefox can't connect to
the server at 127.0.0.1:5173` almost always means step 2 simply isn't running; a page that loads but
fails every request means step 1 isn't.

**Open `http://127.0.0.1:5173`, not `http://localhost:5173`.** This is not a preference:
`docker/Caddyfile` sends `Strict-Transport-Security` for the site address `localhost`, so any
browser that has opened the running stack has an HSTS entry for that hostname. HSTS applies to
**every port**, so `http://localhost:5173` is silently upgraded to HTTPS and the dev server appears
to be broken. `127.0.0.1` is a different host string, has no HSTS entry, and is still a trustworthy
origin — so the backend's `Secure` session cookie is accepted.

The dev server proxies `/api`, `/login` and `/logout` to `https://localhost` (Caddy → the app), so
the browser sees one origin and cookies behave exactly as they do in production. Point it elsewhere
with `VITE_API_TARGET`.

## Commands

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with the API proxy |
| `npm run build` | Typecheck, then production build |
| `npm run typecheck` | `tsc -b` |
| `npm run lint` | ESLint — the only linter; oxlint was removed |
| `npm test` | vitest |
| `npm run knip` | Unused files, exports and dependencies |
| `npm run api:generate` | Regenerate the API client and the paging map from the spec |
| `npm run check:offline` | Fails if the build would fetch from an external origin (run after `build`) |

## Conventions that are not obvious

### Authentication and CSRF

A session cookie — `HttpOnly`, `SameSite=Strict`, `Secure` — set by Spring Security. Spring also
writes a **non-HttpOnly `XSRF-TOKEN` cookie**, which the client reads and echoes as an
**`X-XSRF-TOKEN` header on every non-GET request**. That is the whole convention, and it lives in
one place, `src/api/http.ts`, so no call site can forget it.

The token is present *before* login: `SecurityConfiguration` opts out of deferred CSRF loading, so
the cookie is written on the first response — including the 401 that sends you to the login screen.

`/login` and `/logout` are Spring Security's own endpoints, absent from the OpenAPI spec, and the
only hand-written API calls in the application (`src/api/auth.ts`). Both answer with a status code
rather than a redirect, because `fetch` follows a 302 to an HTML page and reports it as success.

### ⚠️ Every successful write invalidates every query — one place, and a test that it is still there

`createQueryClient()` puts an `onSuccess` on the shared `MutationCache`. It exists because **not one
of this application's thirteen create forms invalidated its list**: every one mutates and navigates,
so a list revisited inside `staleTime` (30 s) was served from cache without the new row.

⚠️ **It reads as intermittent because it heals itself** — after thirty seconds everything looks
right, which is why seven screens shipped with it. It only becomes constant when somebody creates
fifty records in a sitting.

**Two consequences you will meet:**

- **A screen no longer needs to invalidate after a create.** Do not add per-screen invalidation back;
  thirteen copies of a line that must never be forgotten is what caused this.
- ⚠️ **A screen test whose `msw` handler is a static fixture will now show PRE-EDIT data after a
  save**, because the app refetches where it used to trust `setQueryData`. That is the mock being
  unfaithful rather than the app being wrong — make the handler record its writes.
  `products.test.tsx` is the worked example.

`query-client.test.ts` asserts the handler is **present** as well as that it works: with the fix
global, deleting it leaves every screen test in this repository passing.

### The API client is generated

`npm run api:generate` runs orval over `../docs/api/openapi.json`, then the paging-map generator.
The output in `src/api/generated/` is committed and **never edited by hand**; CI regenerates it and
fails on a diff.

Two things the config does that are worth knowing:

- The spec has no tags, so a transformer derives one per controller **in orval's in-memory copy**.
  The committed spec belongs to the backend and is never rewritten by a frontend build.
- `InventoryController_writeOff` is used by two operations, which OpenAPI forbids. The config
  suffixes the HTTP verb to de-duplicate, and `src/api/spec-hygiene.test.ts` fails if that set of
  collisions changes — including when the backend fixes it, at which point the workaround should be
  deleted.

### Reads are queries, writes are mutations — and every screen test proves it renders without writing

Two layers, because they answer different questions.

**The client is wired correctly** — `src/api/client-shape.test.ts` checks all 175 operations against
the spec: every one of the 82 GETs is a query, every one of the 93 writes is a mutation and none of
them is also a query. This exists because the opposite was once true of **all 92 writes at once**: a
single `query: { useQuery: true }` in `orval.config.ts` made every POST, PATCH, PUT and DELETE a
query hook, so rendering a component that used one would have sent the request on mount, and again
on every refetch and window focus. The test was proven to fail against that exact config.

**Each screen uses it correctly** — a screen can still fire a mutation from an effect or a
render-time branch and produce the same outcome from correct machinery. So **every screen test
includes a "rendering sends no write" assertion**, using `trackRequests` from `src/test/requests.ts`:

```ts
const requests = trackRequests(server)
afterEach(() => requests.reset())

it('sends no write merely by rendering', async () => {
  renderScreen()
  await screen.findByRole('heading', { name: '…' })
  requests.expectNoWrites()
})
```

It listens to the mock server rather than to individual handlers, so it sees requests to routes the
test never set up — which is the case a per-handler counter structurally cannot catch.
`src/test/requests.test.ts` proves the guard itself goes red on a POST, PATCH and DELETE.

This is the standing pattern for **every** screen from Products on, not a Products-specific check.

### A `Me` fixture comes from `src/test/fixtures.ts` — but only its invariant half

`aUser({ role, sections })` fills `id`, `username`, `active` and `restrictedFields`. **`role` and
`sections` are required parameters and must stay at the call site**, because which sections a role
holds and whether it is full-access are the *content* of a test — a reader has to see them without
opening another file.

It exists because nineteen hand-authored `Me` literals across seven files **all omitted `active`**,
and nothing noticed until the spec started declaring primitives required and `tsc` reported them at
once. They had been describing a `/api/me` the server never sends. Fixing one field meant editing
eleven sites.

⚠️ **It does not close the drift class, and should not be relied on as if it did.** A reference-typed
field that is mandatory in fact is still optional in the generated types, so a fixture can still omit
one. Only the backend declaring those (`PROGRESS.md` item 8) lets `tsc` catch it — and no test in
this repository can catch it honestly, because every other candidate source of truth about the wire
is hand-authored here.

`app.test.tsx` and `session.test.tsx` deliberately do not use it: they build a raw JSON body and
assert on `displayName`, so their identity fields are content too.

### A list screen's filter state must never allocate

`useListState`'s setters return the **same state object** when nothing changed, and `unwrapList`
returns the **same empty array** every time. Both look like micro-optimisations and neither is: with
either one gone, changing a filter puts the tab into an unterminating render loop.

The cycle is worth knowing, because any table built on `useReactTable` can re-enter it. A filter
change is a query-key change, so the query holds no data while it refetches; a fresh `[]` for that
state is a new `data` identity every render; `useReactTable` memoises its core row model on that
identity, and rebuilding it calls `_autoResetPageIndex()`, which reaches `setPage(0)` on a table
already on page 0. React bails out only on `Object.is`, so a setter that spreads into a new object
re-renders regardless — and that render allocates the next `[]`. **React flushes it in a microtask,
so the page does not get slow, it stops**: the response that would have ended the loop can never be
delivered.

`data-table-loop.test.tsx` guards it with a render budget. **Its 50 ms response delay is
load-bearing** — answered instantly, `msw` resolves inside the same microtask checkpoint the reset
is queued on, and the defect measures 3 renders instead of 84.

### "Not yours to edit" and "fixed on this record" are different, and look different

Two states that read alike and must not be collapsed. `FieldEditor` takes both:

| | Means | Renders |
|---|---|---|
| `editable: false` | a VIEW grant — **not yours to edit** | **no affordance at all** |
| `lockedReason` | **editable in general, fixed on _this_ record** | shown, **disabled**, with the reason |

A disabled button for a VIEW role invites somebody to keep trying at something their role will never
allow. A *hidden* control on the one record where a setting is fixed leaves an operator hunting for
something every other record of that kind has. `editable: false` wins when both apply: why a record
is special is not information a read-only role needs in place of an edit it cannot do anyway.

**This is a standing pattern, not a Customers solution.** The category is "generally editable, fixed
by rule on this instance", and it recurs: the shared retail customer (`systemKey` — VAT treatment
fixed by CHECK, cannot be deactivated) is the first, and posted Sales and Purchase Invoices will be
the next and much larger one. Reach for `lockedReason` there rather than inventing a second way.

⚠️ **Where the reason text comes from matters.** Prefer the backend's own words. The retail record's
locks are currently *mirrored* on the client because two of those routes throw
`IllegalArgumentException` and the message is correctly discarded, leaving a bare `400` — a backend
defect queued as item 4. A mirrored reason is a stopgap and should be labelled as one at the call
site, because it can drift from the rule it describes.

### One choice out of a few is a `SegmentedControl`, and an unavailable option says why

`components/segmented-control.tsx`. Built for the role grant grid — a row per section, `NONE` /
`VIEW` / `FULL` across — and it does two things `ToggleGroup` on its own does not.

**It cannot be emptied.** Base UI lets the pressed item be pressed again to deselect, which would
leave a row answering "none of the three" — a state `PUT …/grants/{section}` cannot express. An empty
change is ignored.

**An option can carry a `disabledReason`**, which is the `lockedReason` distinction one level down:
a caller holding `VIEW` on Sales may confer `NONE` and `VIEW` there and not `FULL`, so `FULL` is
shown, disabled, with the reason — hiding it would leave an administrator hunting for a level that
exists on every other row. **`NONE` is never locked**: revoking is always allowed, and must not
require the access being taken away.

⚠️ **Every `disabledReason` is a mirror of a backend rule.** They exist to stop the screen firing a
request whose refusal is already certain — not to replace the refusal. Each of those guards answers
`422` with a fuller sentence than the mirror, and `Refusal` shows it whenever a request is sent.

### A grid of permissions is drawn from the catalogue, never from the record

`GET /api/sections` is the row list, not `RoleView.sectionGrants`. A section a role has never been
granted has no key in the map, so a grid built from the role draws only the rows somebody already
touched — and the missing rows are the ones an administrator is looking for.

⚠️ **And `sectionGrants` is empty for a full-access role.** Owner and Admin hold everything through
the `fullAccess` flag and carry no grant rows at all, so reading the map alone renders seventeen rows
of `NONE` for the two most privileged roles in the system. Check the flag first. A test holds it.

### A password is generated, shown once, acknowledged, and gone

`components/password/password-handoff.tsx` is the only place this application displays a credential,
and it is used by both the reset on a user's page and the first password on the create form.

- **Generated, never typed** (`lib/generated-password.ts`, `crypto.getRandomValues`, rejection-sampled
  over an alphabet with no `0`/`O` or `1`/`l`/`I`). An administrator inventing a password on somebody
  else's behalf is how an office ends up sharing a pattern.
- **No confirm-field.** There is nothing to confirm: the operator did not choose the value and cannot
  mistype it. What can actually go wrong is closing the dialog without having taken it — so the close
  is gated on an explicit acknowledgment, and Escape and outside clicks do not dismiss it.
- **Not retrievable.** The value lives in one component's state; it is not a query key, not written to
  the cache, not in a URL. `UserView` has no password field and no route returns one, so "show it
  again" is not a feature that was left out — it cannot exist.
- **It takes no `open` prop**, deliberately: the caller renders it only while a hand-off is in
  progress, so the acknowledgment cannot survive into the next one.

### Refusals are shown by one component

`<Refusal error={mutation.error} />`. Never `error.detail` at a call site: a `403` carries no
`detail` by design — a permission refusal deliberately says nothing — and an unreachable server
throws a `TypeError` that is not an `ApiError` at all, so reading `detail` directly renders **an
empty alert** for both. `Refusal` renders nothing when there is no error, so no call site needs a
conditional of its own.

Every mutation a screen fires needs one. Deactivating a product shipped with an `onSuccess` and
nothing else, so a `422` explaining exactly what was wrong produced no visible change and the button
read as dead.

### Selects are built from an option list, never from children alone

Use `OptionSelect` with `options`, and build them from reference data with `idOptions` from
`src/api/lookups.ts`. The primitives in `components/ui/select.tsx` remain for anything that needs
custom item rendering — **and anything reaching for them owes `items` to `Select.Root`.**

⚠️ **Base UI's `Select.Value` resolves its label by looking the value up in the root's `items`, and
silently renders `String(value)` when there are none.** Nothing warns, and the popup — built from the
same list — still shows the right words, so it is only wrong on the closed trigger. It shipped that
way everywhere: `Unit: 4`, `Type: GOODS`, and `en` for the language. `OptionSelect` takes the list
once and renders both the `items` prop and the items from it, so the two cannot disagree and the
trap cannot be reset by the next screen.

### The spec declares primitives required — and nothing else, which is still not the whole truth

**This section used to say the opposite.** Until 2026-08-01 `required` appeared on two schemas out of
185, so every generated request type was fully optional and none of them meant it — a field the types
called optional could be mandatory, and you found out as a `400` naming no field. That broke product
creation for every user (`NewProduct.serialTracked`) and would have broken account creation the same
way (`NewUser.roleId`).

**What changed:** `OpenApiSchema.recordSchema` now marks a record's **primitive** components
required, so 78 schemas declare one. A primitive cannot be null, so on a request it is mandatory —
`FAIL_ON_NULL_FOR_PRIMITIVES` refuses an absent one before any handler runs — and on a response it is
always present, so one rule is accurate in both directions. **`tsc` now refuses a create form that
omits one.**

⚠️ **What is still not declared, and you will meet it:** a *reference-typed* field that a compact
constructor requires (`Required.field` / `requireNonNull`) is mandatory in fact and invisible to the
generator, because reflection cannot see inside a constructor body. **28 schemas have one.**
`NewRole` is the readable example — it declares no `required` list at all, and `POST /api/roles` with
`{}` is still refused. So a field the types call optional *can* still be mandatory; the class that
silently broke a screen is closed, the rest is queued.

`spec-hygiene.test.ts` pins the count and fails in both directions — including asserting `NewRole`
stays undeclared, so the day the guarded half lands, somebody comes back here.

**A screen test still cannot tell you a write works.** A mock server answers whatever it is given;
that has not changed and is why the create forms are proved against the real backend.

### Money is a string, everywhere

`Money`, `UnitCost`, `Quantity` and `Rate` are strings on the wire. `<input type="number">` is
**banned by an ESLint rule**; every decimal field uses `MoneyInput`, `UnitCostInput`,
`QuantityInput` or `RateInput` from `src/components/decimal/`, which are text inputs backed by
`decimal.js`. `parseFloat` and `parseInt` are banned too.

Inputs accept both `.` and `,`, never show grouping separators while editing, and refuse to
*interpret* an ambiguous value such as `1.234,56`: it stays on screen, marked invalid, rather than
being quietly reshaped into `1.23456`.

### Redacted fields are absent, not null

The backend serialises with `non_null` inclusion, so a field a role may not see is simply not in the
response. That is why nearly every generated response type is optional, and why
`usePermissions().isFieldHidden(field)` exists — a missing value means "not shown to you", not "not
set".

### Navigation is data

`src/nav/tree.ts` is the only statement of what the menu contains. Each node names the grant it
needs and the endpoint it is built on, and `src/nav/tree.test.ts` checks that grant **against the
spec's own `x-novocore-section` / `x-novocore-level`**. Reordering the menu is an edit to that
array. The sidebar and the router both filter it through `visibleNav`, so a page hidden from a role
cannot be reached by typing its URL.

### An "Edit" button's accessible name says which field

`FieldEditor` renders the word **Edit**; its `aria-label` is **"Edit <the field's label>"**. A detail
screen renders five or more of them, and without this they are five controls all called "Edit" —
indistinguishable to a screen reader, and indistinguishable to a test.

That second half is not hypothetical: several screen tests reached the field they wanted with
`getAllByRole('button', { name: 'Edit' })[3]`, and **every one of them broke the day a field was
inserted above it** (Brand, on Products). Positional indexing into identical controls is the smell;
naming them is the fix. Tests now say `getByRole('button', { name: 'Edit VAT status' })`, which also
makes them readable, and the count/absence assertions use `/^Edit /`.

### A search box is `SearchFilter`, and it sends `search=`, never an exact-lookup parameter

`components/data-table/search-filter.tsx`. One component for all five list screens, because the
things they would drift on are the things that matter: the debounce interval, and whether a cleared
box sends `search=` or omits it.

- **It reports on a trailing debounce**, 250 ms. The visible value is not debounced, so the box never
  feels laggy. This is not a micro-optimisation: every one of these lists is client-paged today, so a
  filter change is a query-key change — the exact input to the render loop above. That loop is
  defended against in `useListState` and `unwrapList` and stays defended, but there is no reason to
  walk into it ten times a word.
- **A cleared box reports `undefined`, never `''`.** Both mean "no filter" to the backend, but only
  `undefined` keeps the parameter out of the query key, so clearing returns to the query the screen
  started on rather than to a second identical one cached separately.
- **The callback is held in a ref**, deliberately. Every call site passes an inline arrow, so naming
  it as an effect dependency would restart the timer on every parent render and the debounce would
  never elapse on a busy screen. A test holds it.

⚠️ **`search=` is not the same parameter as `sku=` or `ean=`, and the Products box used to send the
wrong one.** Those two are exact lookups and stay exact — they are what a barcode scanner and an
integration call use, and a scan matching a *substring* of a barcode would put the wrong product on
an invoice. The filter box sent `sku=` until the search endpoint existed, so typing `TEST` against
eight `TEST-PRODUCT-*` SKUs matched nothing. The same distinction holds for customers'
`by-vat-number`, which is the authoritative auto-link and must not match approximately.

### Tables page themselves

`DataTable` takes either shape a list endpoint returns. A server-paged response carries a `page`
object; an unpaged one omits it. Which endpoints *accept* `page`/`size` is generated into
`src/api/generated/paging.ts` — 3 of 56 today — so when the backend pages one more, the next
`npm run api:generate` switches every table over it with no component change.

### Text is ordered by one collator, and the database will agree with it

`src/lib/collation.ts`. Never `a.localeCompare(b)` at a call site and never a bare `[].sort()`.

**The default is wrong in a way that looks like data being wrong.** `[].sort()` compares UTF-16 code
units and PostgreSQL under this deployment's `--locale=C` compares bytes — the same wrongness. Every
uppercase word before every lowercase one, accented words after all unaccented ones, and **every
Greek name after every Latin one**, with `Ωμέγα` ahead of `αθήνα` because capitals hold the lower
code points. On the live seed that put `Πελάτης Λιανικής` below all five `TEST-CUSTOMER-*` rows.

⚠️ **`pg_c_utf8` does not fix ordering, and it is the obvious thing to reach for.** S1 introduced
`lower(… COLLATE pg_c_utf8)` so Greek capitals fold; it changes **case mapping**, not **sort
order**, and its `ORDER BY` output is character-for-character identical to `C`'s. Measured.

**The order is `Intl.Collator('el')` — Greek block first, then Latin, fixed.** Not the account's
language: a list whose row order changes when somebody switches UI language is worse than one that
does not. And it is deliberately **the same order the database will produce** when these endpoints
start sorting on the server — PostgreSQL's `el-GR-x-icu` and this return byte-identical results, and
`collation.test.ts` pins PostgreSQL's actual output as the expectation so the two cannot drift.

Two things that file also settles: **numeric ordering is off** (`TEST-PRODUCT-10` before
`TEST-PRODUCT-2`) because stock `el-GR-x-icu` does not do it and a nicer browser order is not worth
the two halves disagreeing; and **a wire decimal is never compared as text** — `"9.00"` sorts above
`"1234.56"` under any text comparator, so money uses `compareMoney`, which groups by currency before
comparing amounts rather than stating a conversion nobody performed.

⚠️ **`collation.test.ts` asserts the resolved locale is `el`.** On a small-icu runtime
`Intl.Collator('el')` silently falls back to the root locale, every comparison stays locale-aware,
and only the Greek-first reordering — the actual decision — disappears. That is the same shape as
the test database configured unlike the real one, so the pin is asserted rather than assumed.

### A sortable column, and the one case where a header must not be one

`sortableHeader(label)` in `components/data-table/sortable-header.tsx`, plus `meta.sortKey` when the
endpoint has a matching backend constant. Ascending → descending → **unsorted**; the third state is
not a courtesy, because every list has a natural order the backend chose and without it there is no
way back to the one the screen opened in.

**A column that cannot sort renders as plain text, not as a disabled button** — the `FieldEditor`
distinction above, one control along. Both states come out of the same helper, so a column cannot
end up with an affordance that does nothing.

⚠️ **The case that matters: a server-paged list must not sort in the browser.** Sorting the
twenty-five rows in hand and presenting them as the order of four thousand produces a table that
looks entirely convincing and answers a different question. So on a server-paged endpoint a column
is sortable **only** if its `meta.sortKey` is one the endpoint declares, and it sorts through the
request. `canSortColumn` states this once. Today every list takes the other branch — all five
endpoints return their rows whole, confirmed against the running container's own bytecode — but the
branch is built and tested because the day it starts applying is the day the backend adds paging,
and nothing on this side would change to mark the occasion. **No column file carries a `sortKey`
yet**, so when one of them gains paging its sort controls disappear until somebody adds them. Safe,
loud, and a real obligation.

⚠️ **The size of that obligation, measured 2026-08-04 after R2b: 12 column FILES covering 14 LIST
SCREENS** — R2b added payment methods. *(It was 11 / 13 after R2, and 7 after F4.)* S2 shipped sorting against five screens, F4 took it to seven, and R2 added six screens.
**The two numbers differ and the larger one is the obligation**, because
`document-reference/document-type-columns.tsx` and `document-reference/series-columns.tsx` are each
shared by a sales screen and a purchase screen — one file, two lists, two endpoints that could gain
paging independently.

**Count, do not trust the prose** — `ls src/pages/*/*-columns.tsx` for the files, and remember the
two shared ones count twice. This number was wrong in three documents until 2026-08-02, which is why
it now carries a date and why the file/screen distinction is spelled out rather than left to be
rediscovered.

⚠️ **R2 added no `sortKey` and owed none**: all six of its endpoints are `{paged: false, sorts: []}`
in the generated `paging.ts`, so every one of its columns sorts client-side over the whole list,
correctly. R2 inherits the obligation; it does not discharge or worsen it.

Two defaults live on `DataTable` rather than on each column, because forgetting one on a single
column is a table that is quietly wrong in one place: **`sortUndefined: 'last'`** in both directions
(a descending sort opening on a screen of blanks reads as broken), and **`sortDescFirst: false`**.
That second one is a fix, not a preference — TanStack otherwise picks a column's first direction
from **the value in row zero**, so the direction of a user's first click depended on which record
happened to be at the top, and the header's accessible label followed it.

**Sort by what the cell shows.** Enum columns order by their translated label, not the constant;
lookup columns by the name, not the id. A consequence worth knowing rather than discovering: those
columns reorder when the language changes, which is correct — an alphabetical list is alphabetical
in the alphabet being read.

**A column whose value the role cannot see does not sort.** `enableSorting: notHidden(field)` on
Products' price and supplier columns. Client-side the values are absent so nothing could leak today,
and the control would merely shuffle nothing — but it is S1's disclosure finding one control along
(ordering compares against every other row at once), and that half starts applying the moment these
lists sort on the server.

### A field can be unavailable in *four* ways, and only two of them are `FieldEditor` states

The table under *"Not yours to edit" and "fixed on this record"* above is half the story. F4 needed
the other half, because a settings screen and a reference-data screen are mostly made of fields
nobody can change:

| | Means | Renders |
|---|---|---|
| `editable: false` | a VIEW grant — **not yours to edit** | no affordance |
| `lockedReason` | editable in general, **fixed on _this_ record** | shown, **disabled**, with the reason |
| **no route exists** | **nobody can change it, on any installation** | **plain text with the reason — not a `FieldEditor` at all** |
| **not built yet** | the route exists and the screen does not use it | plain text, and **a test asserting the absence** |

⚠️ **The third is the one that gets built wrong**, and reaching for `editable: false` is the tempting
mistake: in this application that string means *"your role may not"*, so using it for a VAT class's
rate tells an administrator holding `TAX_AND_CHARGES:FULL` something false. A **disabled** control is
worse again — it invites a hunt for the permission that unlocks it, and there is none. `RoleDetail`
made this call first for a role's description; `VatClassDetail` (rate, code), `UnitDetail` (code) and
`cash.payment.limit` all follow it.

The fourth is a *deferral*, not a property of the data, and it owes a test. `reduced-counterpart` on
VAT classes is the worked example: `PUT`/`DELETE` exist and F4 deliberately does not use them, so a
test asserts no such control is rendered — which makes building it later a deliberate act with a test
to update, rather than something that drifts in with a copied screen.

### The Settings screens read one endpoint, and the key in the URL is not the key on the screen

`GET /api/settings` returns the whole catalogue in one response, and the three pages each render a
slice of it — one query, not three, or a save on one page leaves the other two stale.

⚠️ **`PUT /api/settings/{key}` binds `{key}` to the ENUM CONSTANT** — `LEDGER_ROUNDING_THRESHOLD` —
while the response body's own `key` field carries the **dotted** spelling, `ledger.rounding.threshold`.
No converter is registered, so the dotted form is refused by Spring before any of our code runs. Both
spellings live side by side in `pages/settings/settings-catalogue.ts` for exactly this reason.

That file is a hand-written mirror of a backend enum, so `settings-catalogue.test.ts` asserts it
covers `SettingsCatalog` **exactly** — a nineteenth key added on the backend otherwise lands on no
page at all, unreachable, with nothing broken anywhere.

⚠️ **`SettingsCatalog` is an allowlist, not a view of the table.** 33 rows exist, 18 are reachable;
the whole `backup.*` namespace has no route. A screen expecting to see everything in the database
would be wrong about what exists. And **there is no General page** — the 18 keys distribute 4/12/2
across Documents & Rounding, Email/SMTP and Retention with nothing left over, so the nav item was
dropped rather than shipped empty.

### When a value's permitted set is not in the spec, read the enum — not the prose about it

A setting's value is an opaque `string` in the OpenAPI document, so `smtp.transport-security` has no
generated enum and `enum-labels.test.ts` cannot see it. The list has to be mirrored by hand in
`settings-catalogue.ts`.

⚠️ **`SettingType`'s javadoc named the accepted values as `NONE`, `STARTTLS` or `TLS`, and there is
no `TLS` constant** — it is `IMPLICIT_TLS`, which is what the live stack runs on port 465. A select
built from that sentence offers an option every save refuses. Corrected in the backend during F4, and
now pinned from both ends: `settings-catalogue.test.ts` asserts the list, and
`F4WriteContractIT.transportSecurityAcceptsOnlyRealConstants` makes **the real server** say which
spelling is real.

### A mock server still cannot tell you a write works — `F4WriteContractIT` is how F4 answered that

Every F4 write is sent as **the literal JSON the screen builds** to a real Spring Boot server over
real HTTP against real PostgreSQL, in `backend/app/src/test/…/F4WriteContractIT.java`. The bodies are
written out as strings rather than constructed from the request records, because building one from
`NewUnitOfMeasure` asks Jackson to agree with itself.

**It immediately corrected a claim this README nearly shipped.** `NewUnitOfMeasure.fractionalQuantity­
Allowed` is a primitive `boolean`, and the belief going in was that omitting it silently arrives as
`false` — a unit that cannot be sold by the half with nobody having chosen that. **The server answers
`400`**: `FAIL_ON_NULL_FOR_PRIMITIVES` refuses an *absent* primitive, not only an explicit null. The
form's design did not change — the choice is still required rather than a checkbox — but the *reason*
did, and it is a better one: an unticked checkbox does not omit the field, it sends `false`, which is
**accepted**. The server can refuse an omission; only the screen can refuse a default nobody chose.

**The browser leg is a separate question, and it is answered separately.** Driving these forms
against the running Compose stack needs the Owner password, which is deliberately not in this
repository — so the owner runs it. For S1, S2 and F4 they did, on 2026-08-01. **Neither leg
substitutes for the other**: the IT cannot tell you a control is reachable, and a browser pass cannot
tell you which of 22 sub-parts was checked.

### A document screen records; it never issues

**This governs F5 onward and it is not a wording preference.** Novocore never obtains a ΜΑΡΚ. Greek law
requires transmission to AADE at issuance, the document gets its ΜΑΡΚ and QR code there, and it appears
in Novocore only *afterwards* — through Prosvasis Go today, through a certified Πάροχος at step 40.
Neither phase changes that. Full statement in `CLAUDE.md`, *The document model*.

What this means on screen:

- **No button says "Issue".** A sales-document form records something that already exists elsewhere;
  the verb is *record*, or *save*. A screen offering to issue an invoice describes a system this is
  not, and would set an operator's expectation that a ΜΑΡΚ appears when they press it.
- **The document number is an input, not an output.** It arrives with the document. There is no
  sequence, no counter and no "next number" preview to render — **until step 40**, and even there the
  ΜΑΡΚ still comes back from the transmission path.
- **ΜΑΡΚ, UID, QR URL and transmission status are ordinary core fields** on the record (ADR 0016), so
  they read off `SalesInvoiceView` like anything else once R1 lands. They are **not** adapter data and
  are not fetched from somewhere special.
- ⚠️ **Stock does not always move.** ΑΛΠ and ΤΠΔΑ combine sale and transport, so stock moves; a plain
  Τιμολόγιο is purely sales and **does not** reduce stock, and this business issues both routinely.
  ⚠️⚠️ **CORRECTED 2026-08-04 (R2). This bullet used to say such a document must be "visibly and
  queryably" stock-not-yet-moved. IT IS SILENT, BY DECISION** — a document type whose `affectsStock`
  is false creates **no `stock_consumption` row at all**: no pending row, no marker, no flag, no
  warning, nothing queryable. **Do not build an indicator on the strength of this file.** The
  decision, the reasoning and the standing instruction not to add one back are in `CLAUDE.md`, *The
  document model* §6 — deliberately in one place, because this bullet saying it a second time is how
  the two came to disagree.

⚠️ **The generated client changed on 2026-08-02 and the old name is gone.** `POST /api/credit-notes`
was `salesControllerIssue`; it is now **`salesControllerRecordNote`**, and
`SalesControllerIssue4xx` is now `SalesControllerRecordNote4xx`. Nothing in `src/` consumed it yet —
F5 is where it starts being used — but a stale snippet from an older session will not compile.

### knip's entry list

`src/auth`, `src/nav`, `src/components/decimal`, `src/components/data-table`, `src/i18n` and
`src/lib/decimal.ts` are declared as knip entry points. They are the foundations this step
delivered, and their consumers are the screens built after it; without this, knip reports every one
of them as an unused export, and a tool that reports 35 findings nobody can act on is a tool
somebody stops running. Unused *files* and *dependencies* are still reported everywhere.

`src/api/wire-format.assert.ts` is imported by nothing on purpose: `tsc` is its assertion.

### Language

English and Greek. The language is stored on the account (`PATCH /api/me/language`), not in the
browser. Enum labels are frontend-owned, and `src/i18n/enum-labels.test.ts` fails if any value of
any enum in the spec lacks a label in either language.

The backend localises nothing (Q47(b)), so validation messages arrive as English prose. They are
shown as they arrive; translating them would need error codes on the backend.
