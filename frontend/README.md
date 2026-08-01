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
and nothing on this side would change to mark the occasion. **None of the five column files carries
a `sortKey` yet**, so when one of them gains paging its sort controls disappear until somebody adds
them. Safe, loud, and a real obligation.

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
