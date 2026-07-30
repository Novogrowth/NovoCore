# Step 16 — Frontend foundations

**The foundations pass: nine mechanisms built once and reused by every screen after.** Frontend
only; `/backend/` is untouched. The one file outside `/frontend/` is `.github/workflows/frontend.yml`,
approved explicitly.

Baseline: 174 routes, 1326 tests, `main` clean at `5d5904b`. The existing frontend is the verified
Vite + React 19 + Tailwind v4 + shadcn/ui scaffold from 2026-07-27 (`492ce24`, `531f12a`) — an app
shell with three placeholder pages and no NovoCore functionality.

**No screens are built in this step.** Products, invoices and the rest come after; what is built here
is what they are all built *out of*.

---

## 0. Six things checked against the running system before designing anything

Each of these changed the design. They are recorded first because a foundation built on an assumption
is a foundation that has to be rebuilt.

### 0.1 The OpenAPI spec already carries the permission model, per route

Every one of the 174 operations in [docs/api/openapi.json](api/openapi.json) carries two vendor
extensions written by `OpenApiSpecIT` from the live handler mapping:

```json
"x-novocore-section": "PRODUCTS",
"x-novocore-level": "VIEW"
```

This is the single most useful fact available to this step. The navigation tree has to state which
`Section` each item needs, and **a hand-written statement of that is a second source of truth that
drifts**. So the tree names the endpoint each item is built on, and a test resolves the required
section and level *from the spec* and fails if they disagree. A nav item claiming the wrong grant
becomes a build failure rather than a screen that 403s in front of a user.

Distribution across sections, from the spec:

| Section | Routes | Section | Routes |
|---|---:|---|---:|
| `PRODUCTS` | 27 | `SETTLEMENTS` | 15 |
| `PURCHASING` | 19 | `INVENTORY` | 14 |
| `USERS_AND_ROLES` | 18 | `CHART_OF_ACCOUNTS` | 13 |
| `CUSTOMERS` | 12 | `SUPPLIERS` | 11 |
| `SALES` | 11 | `FIXED_ASSETS` | 11 |
| `TAX_AND_CHARGES` | 10 | `EMAIL_OUTBOX` | 5 |
| `JOURNAL` | 3 | `SETTINGS` | 3 |
| *(authenticated only)* | 2 | | |

`AUDIT_LOG`, `SALES_ORDER_FULFILLMENT` and `BACK_IN_STOCK_REMINDERS` have **zero routes**.

### 0.2 CSRF bootstraps correctly before login — verified, not assumed

The concern was ordering: the login POST needs a CSRF token, and a token that only appears *after*
authentication cannot be sent *with* the authentication. Probed against the live container:

```
$ curl -sk -i https://localhost/api/me
HTTP/1.1 401 Unauthorized
Set-Cookie: XSRF-TOKEN=566eef03-…; Path=/; Secure
Set-Cookie: NOVOCORESESSION=84DC6FAD…; Path=/; Secure; HttpOnly; SameSite=Strict
```

The token cookie is written **on the 401**, before anyone is authenticated. This works because
[SecurityConfiguration:61-63](../backend/app/src/main/java/gr/novotrade/novocore/app/security/SecurityConfiguration.java#L61-L63)
deliberately opts out of deferred CSRF loading, for exactly this reason, and says so.

**The convention, settled:** read the non-HttpOnly `XSRF-TOKEN` cookie, echo it as an
`X-XSRF-TOKEN` header on every non-GET request. Nothing else. It is applied in one place — the API
client's mutator — so no call site can forget it.

### 0.3 Login and logout are not in the spec and never will be

`SecurityConfiguration` uses Spring Security's own `/login` and `/logout`, and
[WebConfiguration:52-58](../backend/core/src/main/java/gr/novotrade/novocore/core/web/WebConfiguration.java#L52-L58)
scopes the section interceptor to `/api/**` precisely because those two are the framework's, not
ours. `OpenApiSpecIT` generates from the handler mapping, so they are absent from `openapi.json` and
orval will never produce a hook for them.

**Authentication is therefore the one piece of hand-written API client code**, in `src/api/auth.ts`:
form-encoded `POST /login` (204 on success, 401 on failure — no redirects, by design) and
`POST /logout`.

### 0.4 Geist has no Greek. Manrope does.

The scaffold bundles `@fontsource-variable/geist`, whose package ships `latin`, `latin-ext`,
`cyrillic` and `vietnamese` — **no Greek subset at all**. Half of this application's text is Greek,
so Geist cannot be the font, and no amount of theme configuration fixes it.

`@fontsource-variable/manrope@5.3.0` declares `["cyrillic","cyrillic-ext","greek","latin","latin-ext","vietnamese"]`.
Checked by reading the package's own metadata, not by looking at a specimen page.

**Decision: Manrope, hardcoded.** Not a setting. This is the resolution of a problem the first draft
of this proposal raised and could not solve: a font *setting* has nowhere to live — `SettingsCatalog`
is a fixed enum with no font key, and the only per-user field the backend stores is `language` — so
it would have had to be browser-local state masquerading as configuration. Hardcoding removes the
question. When Claude Design defines the real brand look, changing one CSS variable is the whole
change.

**Enforced:** a test reads the metadata of every font package the app imports and fails if `greek` is
absent from its subsets. So swapping the font to one that cannot render Greek fails the build rather
than shipping tofu to half the UI.

### 0.5 `http://localhost:5173` cannot work as the dev server

[docker/Caddyfile:20-22](../docker/Caddyfile#L20-L22) sends
`Strict-Transport-Security: max-age=31536000; includeSubDomains`, and `NOVOCORE_SITE_ADDRESS` is
`localhost`. The running stack has already served that header to the development browser. HSTS is
recorded **per hostname and applies to every port**, so a browser that has visited `https://localhost`
will silently upgrade `http://localhost:5173` to HTTPS and fail to reach a plain-HTTP Vite server. The
Caddyfile's own comment warns about exactly this.

**Decision: the dev server binds `127.0.0.1`,** which is a different host string, carries no HSTS
entry, and is still a "potentially trustworthy origin" — so the `Secure` cookies the backend sets are
accepted. Vite proxies `/api`, `/login` and `/logout` to `https://localhost` with `changeOrigin: true`
(Caddy routes by Host) and `secure: false` (Caddy's internal CA is not in Node's trust store).

Alternatives rejected: publishing `app:8080` to the host (changes `compose.dev.yml`, and drops TLS
from the development path so cookie behaviour stops resembling production); running Vite over HTTPS
with a generated certificate (a certificate-management problem in exchange for nothing).

### 0.6 Only 3 of 56 list endpoints are server-paged

`GET /api/sales-invoices`, `GET /api/journal-entries` and `GET /api/accounts/{id}/ledger` take
`page`/`size`/`sort`/`direction`. The other 53 return everything. This is the state step 16a's paging
contract left deliberately, with tier-A paging (inventory lots, outbox) still to come.

The table abstraction is therefore designed around *the transition*, not around either end of it —
see §6.

---

## 1. Navigation structure

**One file of data, `src/nav/tree.ts`.** Reordering or regrouping is an edit to that array. No
component anywhere else decides what appears in the menu.

```ts
type Requirement = { section: Section; level: AccessLevel }

type NavNode = {
  id: string                  // stable; the i18n key is `nav.${id}`
  path?: string               // absent on grouping nodes
  requires: Requirement[]     // ALL must hold; [] means authenticated-only
  status: 'BUILT' | 'NOT_BUILT'
  endpoint?: string           // 'GET /api/products' — resolved against the spec by a test
  adapter?: AdapterKey        // placeholder behind an adapter toggle
  module?: ModuleKey          // placeholder behind a module toggle
  icon?: Icon
  dividerAfter?: boolean      // the one divider, inside Accounting
  children?: NavNode[]
}
```

**Labels are not in the tree.** Each node carries an `id`; `nav.<id>` resolves it in English and
Greek. A tree holding English strings is a tree that has to be restructured to translate.

### Visibility

One recursive function, `visibleNav(tree, me)`, used by **both** the sidebar and the router guard —
so a hidden page reached by typing its URL says "not available" rather than rendering an empty shell
against a 403.

- `NOT_BUILT` → visible only when `me.role.fullAccess`, rendered disabled. That flag is exactly
  OWNER and ADMIN, both seeded `full_access = true` in
  [V6__users_roles_permissions.sql:169-176](../backend/core/src/main/resources/db/migration/V6__users_roles_permissions.sql#L169-L176).
  Reading the flag rather than matching role names means a future full-access role behaves correctly
  without a code change.
- `BUILT` → visible when **every** entry in `requires` is satisfied. An array rather than a single
  section because some pages genuinely read two sections, and a design that can only express one
  would push the second check into the component — which is where per-screen permission logic starts.
- A group renders when **any descendant** is visible, computed bottom-up. Never from the first child:
  a role granted Settlements but not Sales must still see the Finance heading.

### What the tree contains

The full tree as specified in the kickoff, unchanged in structure. Two placements worth recording:

- **Reference Data splits in two.** VAT classes are `TAX_AND_CHARGES`; **units of measure are
  `PRODUCTS`** — checked in the spec, not assumed. A single "Reference Data" page would show half its
  content to a role holding one grant and look broken. Two items, each declaring its real section,
  which is consistent with the backend having kept both out of `SETTINGS` in the first place.
- **`AUDIT_LOG` gets no item.** It is a grantable section with zero routes. It is not the Journal —
  the Journal is the ledger, the audit log is who-changed-what — and inventing a screen for an API
  that does not exist would produce exactly the permanent placeholder step 16b existed to eliminate.

### The adapter and module registries

`src/nav/registry.ts` holds the ten adapters and eleven modules as typed keys with their status. The
grids in Settings render from it; nav placeholders reference it by key.

**Two of the module keys are also backend `Section`s** — `SALES_ORDER_FULFILLMENT` and
`BACK_IN_STOCK_REMINDERS`, both declared `available(false)` in
[Section.java:135-139](../backend/core-api/src/main/java/gr/novotrade/novocore/core/api/security/Section.java#L135-L139).
For those two the built/not-built fact belongs to the backend and arrives on `/api/me` as
`SectionAccess.available`. The other nineteen have no backend fact, so the registry is their only
source and says so.

**No test can compare the registry to the backend's `available` flag**, and the first draft of this
document claimed one did. That flag arrives at runtime and is absent from the spec, so there is
nothing at build time to compare against. What protects the case instead is `visibility.ts`: the
backend's answer is authoritative and a stale frontend claim is **downgraded**, never upgraded — an
item this tree calls BUILT renders as a placeholder if the backend says nothing is behind it, and an
item this tree calls NOT_BUILT stays a placeholder whatever the backend says. Both directions are
asserted in `tree.test.ts`. What `tree.test.ts` does check about the registry is that it and the
tree tell the same story: a module the registry calls not-built has no item claiming otherwise.

`Section.isAvailable`'s own Javadoc names why this distinction is worth carrying: *"so that a UI can
distinguish 'you may not see this' from 'this does not exist yet' — two states that look identical to
a user and have entirely different fixes."* This step is that UI.

### What enforces it

Four tests, in `src/nav/tree.test.ts`:

1. Every `id` is unique.
2. Every `id` has an `en` and an `el` label.
3. **Every node with an `endpoint` matches the spec**: its `requires` equals that operation's
   `x-novocore-section` / `x-novocore-level`.
4. Every `BUILT` node has an `endpoint`; every `NOT_BUILT` node has none. A "built" screen with no
   API behind it is the mistake this catches.

---

## 2. Permission gate

**One hook, `useMe()`**, over TanStack Query: key `['me']`, no retry, `staleTime` 5 minutes. A 401
from anywhere clears it and routes to `/login`; a successful login invalidates it.

`usePermissions()` derives everything else, and it is the only place `AccessLevel` is compared:

| Function | Answers |
|---|---|
| `canView(section)` | grant is `VIEW` or `FULL` |
| `canEdit(section)` | grant is `FULL` |
| `sectionAvailable(section)` | is anything built behind it (`SectionAccess.available`) |
| `isFullAccess` | OWNER/ADMIN — governs placeholder visibility |
| `isFieldHidden(field)` | the role restricts this `ProtectedField` |

`<RequireSection section level>` wraps routes with the same predicate the sidebar uses.

**On redaction.** `Me.restrictedFields` and `ProductView.hiddenFields` both exist, and Jackson's
`non_null` inclusion means a redacted field is genuinely *absent* rather than null. Since V26 no role
restricts anything, so this mechanism is currently unexercised by real data — which is precisely why
it gets helpers and tests now rather than being discovered later.

**Two questions, two answers, because they are not the same question.** `isFieldHidden(field)` is
role-level — *should this column exist at all* — and it applies the one implication the backend
derives without reporting (`ProductView.redactedFor` hides a supplier's SKU whenever it hides the
supplier). `hiddenInResponse(view, field)` is per-record and reads the response's own
`hiddenFields`, which cannot drift because it is part of what was sent. How a hidden field *renders*
is a screen's decision and belongs to the first screen that has one, not here.

---

## 3. API client

**orval, generating TanStack Query hooks from the committed spec.** `npm run api:generate` regenerates;
output is committed; CI fails on drift, mirroring how the backend guards the spec itself.

- **Tagging.** The spec has no tags (operations are named `ChartOfAccountsController_groups`), so
  everything would land in one file. An orval `input.override.transformer` derives a tag from the
  operationId prefix **in memory only** — the committed spec is never rewritten by the frontend
  build. 174 hooks become ~15 readable modules.
- **Mutator.** A hand-written fetch mutator, not axios: `credentials: 'same-origin'`, the
  `X-XSRF-TOKEN` header on non-GET, RFC 7807 `detail` extracted into a typed `ApiError`, 401 routed to
  login. One place, so no call site can get it wrong.
- **Optionality is correct, not a nuisance.** `default-property-inclusion: non_null` means response
  fields are almost all optional in the spec and therefore in TypeScript. That is the truth — a
  redacted or absent field is absent — and the components are written for it rather than casting it
  away.

### Confirming the wire format survives generation

Step 16a's acceptance test guarantees the *spec* types money as a string. This step confirms **orval's
TypeScript output does not lose that**, two ways:

1. `src/api/wire-format.assert.ts`, compiled by `tsc` in CI: assignments proving `Quantity`, `Rate`
   and `Money['amount']` accept `'12.50'`, each paired with a `@ts-expect-error` line proving they
   reject `12.50`. If a regeneration ever widens one to `number`, the `@ts-expect-error` stops
   erroring and the build fails.
2. A test that walks the generated model files and fails on any property named `amount`, `quantity`,
   `rate` or `unitCost` declared `number`.

The second catches what the first cannot: a *new* endpoint whose money field nobody wrote an
assertion for.

---

## 4. Visual tokens

`components.json` → `style: "luma"`, `baseColor: "zinc"`, `iconLibrary: "phosphor"`. All three are
values the installed shadcn CLI supports; the Luma preset's own defaults (lucide, Inter) are overridden
deliberately. The seventeen `ui/` components generated under `base-nova` are regenerated; `lucide-react`
and the Geist font package are removed with them.

Manrope is imported from `@fontsource-variable/manrope`, **latin and greek subsets only**, and bound to
`--font-sans` in `src/index.css`. Nothing is fetched at runtime: no CDN, no Google Fonts, no external
stylesheet — this runs on the shop's own network. Enforced by a test that scans the production build
output for external `http(s)` URLs.

**No screen-level polish beyond applying these tokens.** Claude Design defines the real look later.

---

## 5. Decimal handling

`<input type="number">` is prohibited outright — it discards precision, localises inconsistently, and
scrolls values on a mouse wheel. One core component, `DecimalInput`, with three wrappers:

| Component | Scale | Notes |
|---|---:|---|
| `MoneyInput` | 2 | pairs with a currency; emits `Money` |
| `UnitCostInput` | 6 | pairs with a currency; emits `UnitCost` |
| `QuantityInput` | 6 | `allowFractions` from the unit of measure's `fractionalQuantity` |
| `RateInput` | 6 | percentage |

Behaviour, identical across all four:

- `type="text"`, `inputMode="decimal"` — a numeric keypad on a phone, a text field everywhere else.
- Accepts `.` and `,` as the decimal separator; Greek keyboards produce the comma.
- **No grouping separators while editing.** A field that reformats as you type is a field that
  corrupts a paste, and `1.234` is genuinely ambiguous between locales.
- Canonicalises on blur to the wire scale; emits the canonical `"1234.56"` string on submit.
- Display formatting is locale-aware (`1.234,56` under `el-GR`) and produced from the `Decimal`,
  never from a `Number`.

decimal.js does every conversion. `Number()`, `parseFloat` and arithmetic operators never touch a
value that came off the wire.

**Enforced:** an ESLint `no-restricted-syntax` rule fails the build on `type="number"` in any JSX
input, plus a unit-test corpus covering the cases that break naive implementations — `0.1 + 0.2`,
comma input, a trailing separator, a second separator, negatives, and six-decimal truncation.

---

## 6. Table abstraction

`DataTable` over TanStack Table (headless) rendered with the shadcn `Table` primitives. It accepts
either shape and switches on one fact:

> **A server-paged response carries a `page` object; an unpaged one omits it** — because Jackson drops
> nulls, so `ListResponse.page` is simply absent when the endpoint did not page.

That handles rendering. The other half — *sending* `page`/`size`/`sort` only to endpoints that accept
them — is answered by generating `src/api/paging.generated.ts` from the spec alongside orval: which
operations take paging parameters, and which sort enum each accepts. So when tier-A paging lands on
inventory lots, `npm run api:generate` moves that endpoint from client-side to server-side paging and
**no component changes**. That is the requirement, mechanised rather than promised.

Client-side paging is the fallback for the other 53 endpoints, using TanStack's own pagination row
model over the full array.

---

## 7. i18n

react-i18next, scaffolded now so every screen after is bilingual from birth. Namespaces:
`common`, `nav`, `enums` — three, not the five this document first listed; `errors` and `fields`
were named before there was anything to put in them, and an empty namespace is a place for strings
nobody reads to accumulate.

Language is per-user: read from `me.language`, written with `PATCH /api/me/language`, falling back to
the browser then `en`. The backend validates the *shape* of a language tag and deliberately does not
maintain a list of supported languages —
[LanguageTag.java:14-20](../backend/core/src/main/java/gr/novotrade/novocore/core/security/LanguageTag.java#L14-L20)
states that which languages are offered is the frontend's decision. This step makes that decision: `en`
and `el`.

**Enum labels are frontend-owned**, and their coverage is enforced: a test walks **every enum schema
in `openapi.json`** and fails if any value lacks an `en` and an `el` label. That is what stops a new
backend enum value reaching a user as `SHRINKAGE`.

**Pass-through, untranslated, always Greek:** VAT exemption reason text and myDATA labels. They arrive
as data, not as enum values, and translating a statutory phrase would be inventing one.

**One limitation, stated rather than papered over.** Q47(b) settled that the backend localises nothing,
so `ProblemDetail.detail` is English prose with no code to key from. Validation refusals will read in
English inside a Greek UI. Fixing that properly means error codes on the backend — a real change, not
this step's, and not worth guessing at now.

---

## 8. Dev environment

```
http://127.0.0.1:5173   →  Vite dev server
  /api, /login, /logout →  https://localhost  (Caddy → app:8080)
```

`changeOrigin: true` so Caddy sees the Host it routes by; `secure: false` for Caddy's internal CA.
One origin from the browser's point of view, so `SameSite=Strict` and `HttpOnly` behave exactly as
they do in production. The target is overridable by `VITE_API_TARGET` for anyone running the backend
some other way.

Why `127.0.0.1` rather than `localhost`: §0.5. It is written into `vite.config.ts`, the frontend
README and the primer, because the failure it avoids looks like a broken dev server rather than a
cached HSTS entry.

---

## 9. Code-quality standing rules

Applied from this point forward, and mechanised where a mechanism exists:

| Rule | Mechanism |
|---|---|
| TypeScript strict | `strict: true` + `noUncheckedIndexedAccess` (both currently **off**) |
| Unused imports/variables | ESLint `unused-imports`, plus `noUnusedLocals`/`noUnusedParameters` |
| Type-aware linting | typescript-eslint with project service |
| `<input type="number">` banned | ESLint `no-restricted-syntax` |
| Dead exports | `knip`, `npm run knip` |
| Everything on every push | `.github/workflows/frontend.yml` |

**oxlint is removed.** It arrived with the Vite template; keeping it alongside ESLint would mean two
config files, two rule sets, and two places a rule can be silently disabled.

`frontend.yml` mirrors `backend.yml`'s discipline — path-filtered, concurrency-grouped, least
privilege — and runs lint, typecheck, test, build, knip and the orval drift check.

The judgement-based rules stay judgement-based, and are recorded so they are actually applied:
diff size as a smell test; ~300 lines as a file-length smell test, **with generated orval output
explicitly exempt**; a fresh-session review pass after each real chunk of work; and a fresh-session
pass checking new code against the wire-format rules, the redaction convention and the
`type="number"` prohibition.

---

## Build order

Each stage is verified against the running stack before the next begins.

1. **Tokens and tooling** — Luma/Zinc/Phosphor, Manrope, strict TS, ESLint, knip, vitest, CI.
2. **API client** — orval, mutator, CSRF, wire-format assertions.
3. **Permission gate** — `useMe`, login/logout, `usePermissions`, `RequireSection`.
4. **Navigation** — tree, registries, visibility, sidebar, router, the four drift tests.
5. **Decimal inputs** — `DecimalInput` and its four wrappers.
6. **Table** — `DataTable` and the generated paging capability map.
7. **i18n** — namespaces, enum-label coverage, language wiring.

---

## Addendum — what actually happened

Written after the build. The plan above stands; these are the places it met reality.

### The style name is `base-luma`, not `luma`

`https://ui.shadcn.com/r/styles/luma/…` is a 404. This project uses Base UI (the scaffold was
`base-nova`), and the registry serves the Base UI variants under a `base-` prefix. Confirmed by
fetching `base-luma/button.json` before changing anything, rather than by running the CLI and
seeing what happened.

### A defect in the committed spec, found by generating from it

**`InventoryController_writeOff` is the operationId of two operations** —
`POST /api/inventory/write-offs` and `GET /api/inventory/write-offs/{id}` — because two Java methods
share a name and `OpenApiSpecIT` derives the id as `Controller_method`. OpenAPI requires it to be
unique. The generated TypeScript did not compile: duplicate identifiers, twenty errors in one file.

Worked around in `orval.config.ts` by suffixing the HTTP verb, and pinned by
`spec-hygiene.test.ts`, which fails if the set of collisions changes **in either direction** — a new
one appearing, or this one being fixed and the workaround becoming dead code. **The real fix is on
the backend** and is not this step's to make.

### The decimal input's first design corrupted a pasted amount

Its own test caught it. Rejecting the second separator keystroke-by-keystroke, then accepting the
digits after it, turned `1.234,56` into **`1.23456`** — silently, and looking entirely normal. That
is precisely the class of failure `<input type="number">` is banned to prevent, reintroduced by the
replacement.

**Changed to refuse to interpret rather than to reshape**: characters that can never be part of a
decimal are still refused, but an ambiguous value stays on screen exactly as typed, marked invalid,
holding no value — and it survives blur rather than being wiped, so the mistake is visible and
correctable. The same rule now covers a fraction typed into a unit of measure that cannot be
divided: shown, refused, never rounded to something nobody asked for.

### Six dependencies removed rather than kept for later

knip found `react-hook-form`, `@hookform/resolvers`, `zod`, `sonner`, `next-themes` and `radix-ui`
unused, along with the three shadcn components that pulled them in. All are one `npm install` away
when the first form or toast lands, and keeping them would have meant the first knip run of every
future session reporting findings nobody intends to act on.

### `check:offline` is a script and a CI step, not a test

The no-CDN rule reads what the **build** emitted, and `npm test` runs before `npm run build`. As a
test it would have had to skip when `dist/` was absent — a check that quietly examines nothing and
reports green. It is a script that **fails on a missing or empty `dist/`**, and CI runs it after the
build. Its first version matched any URL-shaped text and reported five false positives from
documentation links inside React, React Router, i18next, Base UI and Tailwind error messages; it now
matches only fetching contexts, and was **proven to fail against a probe** — a Google Fonts
`<link>` injected into the built HTML.

### Verified against the running stack

- `GET /api/me` through the dev proxy at `127.0.0.1:5173` → **401 with the `XSRF-TOKEN` cookie
  set**, so the token really is available before login.
- `POST /login` through the proxy → **401 with no `Location` header**: a status, not a redirect.
- The SPA loads through the dev server, and the production build ships **only the Latin and Greek
  Manrope subsets** (24.8 kB + 9.4 kB) and no other font.

**Not verified: a successful authenticated round trip.** `NOVOCORE_BOOTSTRAP_OWNER_USERNAME` and
`_PASSWORD` are blank in `docker/.env`, as they should be once the owner exists, so there are no
credentials available to this session. Everything up to the password check is proven; signing in and
seeing the navigation filtered by real grants needs one manual login.

### Also worth recording

- **The `Appearance` settings item is gone**, following from the decision to hardcode Manrope.
- **The production bundle is 579 kB** (183 kB gzipped) in one chunk. Fine for a shop's own network;
  code-splitting is a decision for when there are screens to split.
- **Two lint warnings remain**, both upstream: TanStack Table's `useReactTable` cannot be memoised
  by the React Compiler, and one shadcn file exports a hook beside a component.

---

## Addendum 2 — the fresh-session review pass

Two independent reviews of the committed code, one targeted at the wire-format rules / the redaction
convention / the `type="number"` prohibition, one general. Neither wrote the code. Every finding
below was re-verified before acting on it, and two were verified and **rejected**.

### What was wrong, and is now fixed

**The redaction helper under-reported, on the one field the backend hides indirectly.**
`ProductView.redactedFor` derives `hideSupplierSku = hideSupplier || !canSee(PRODUCT_SUPPLIER_SKU)`
— a supplier code identifies the supplier in a different column — but `/api/me` reports stored
restrictions with no derivation. A role restricting only `PRODUCT_SUPPLIER` therefore received
products with **both** fields blanked while `restrictedFields` named one, and `isFieldHidden` said
`false` about a value that had been withheld: "not set" and "not shown to you" collapsed into one,
which is the exact failure this convention exists to prevent. Fixed with the implication mirrored
(and flagged as a mirror), plus `hiddenInResponse` reading the backend's own per-record report.

**The session ended badly when it expired.** `useSessionExpiryHandler` wrote `null` to `['me']` and
then called `queryClient.clear()`, which removed the query it had just written — the second line
undid the first. Proven by probe: reverting to that pair fails three of the five new session tests.

**A server outage presented as a sign-out.** `useSession` kept only `ApiError`s, and `fetch` rejects
with a `TypeError` when the server is unreachable, so a network failure produced neither an error
nor a user and the app rendered the login form — inviting somebody to type a password at a server
that cannot check it. Proven by probe: restoring the old filter fails the new test.

**Two hand-written API paths the documentation said did not exist.** `session.tsx` and
`useLanguage.ts` wrote `/api/me` and `/api/me/language` by hand while orval had generated both. A
URL literal outside the generated client is a path a regeneration cannot update. Both now call the
generated functions.

**Tests that could not fail.** The registry-versus-spec check asserted something TypeScript already
guaranteed; `useListState` — the hook the whole paging design rests on — had no tests at all; two
test names promised more than their bodies checked. All three addressed, and §1 and §7 of this
document corrected where they described guarantees the code did not provide.

**The float ban was a spelling check.** It caught bare `parseFloat`/`parseInt` only —
`Number.parseFloat(x)`, `Number(x)` and `+x` all passed, and `Number(x)` is the one somebody would
actually write. Widened, with a documented per-line escape for genuine counts.

Also: a dead pager control, a `formatMoney` default that rounded unit costs to two decimals, a
`-0,00`, an unreachable sidebar branch, a translation key that existed in neither locale, 45
duplicated lines across two input wrappers, five orphan strings, and three dead exports.

### What was reported and did not survive checking

**Two of the three claimed session defects do not reproduce.** The reviewer reported that
`queryClient.clear()` in `useLogin` and `useLogout` left the shell rendering the previous user and
made a correct password appear to do nothing. Probed both directly — restore the old line, run the
tests — and **neither fails**: a mounted observer whose query is removed re-subscribes and refetches,
so the user-visible outcome was correct either way. The new code is still preferred, because it says
what it means rather than depending on that behaviour, but the old code was not broken and this
document does not claim it was.

**A test bug the reviews did not find, which the fixes exposed.** The first run of the new session
tests failed for a reason that had nothing to do with the code: jsdom's default origin is
`http://localhost:3000`, so the relative `/api/me` the generated client uses resolved somewhere no
mock handler matched. Every assertion was passing or failing on an unhandled request. Pinned the
test origin to `http://localhost`, which is also where the real backend is.

### Final state

174 routes generated into 21 modules, **120 frontend tests**, lint clean, typecheck clean, knip
clean, build clean, no external origins.

---

## What this step does not do

- No screens. No Products page, no invoice form.
- No backend changes. Tier-A paging for inventory lots and the outbox stays a backend item.
- No brand look. Tokens are applied; visual design is Claude Design's, later.
- No adapter or module toggles that persist. The grids render read-only from the registry, every row
  disabled and marked not-built, **nothing stored** — because there is no settings key and no API
  behind them, and a toggle that silently forgets is worse than a toggle that says it does nothing.
