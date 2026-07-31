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

**The client is wired correctly** — `src/api/client-shape.test.ts` checks all 174 operations against
the spec: every one of the 82 GETs is a query, every one of the 92 writes is a mutation and none of
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

### Tables page themselves

`DataTable` takes either shape a list endpoint returns. A server-paged response carries a `page`
object; an unpaged one omits it. Which endpoints *accept* `page`/`size` is generated into
`src/api/generated/paging.ts` — 3 of 56 today — so when the backend pages one more, the next
`npm run api:generate` switches every table over it with no component change.

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
