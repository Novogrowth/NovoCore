# Novocore — Frontend Roadmap & Effort Tracker

**Legend:** 🟢 Done · 🟡 **Current** = in progress · 🔴 Not started

⚠️ **Nothing is 🟡 — no step is half-finished.** **F5 is next**, and it carries more weight than its
row implies (see the note at the bottom).

✅ **Every step through F4 is verified on both legs, including the browser ones.** S1, S2 and F4 each
needed a live browser check against the running stack — the Owner password is deliberately not in
this repo — and **the owner ran all three personally**. Nothing is outstanding on any built step.

⚠️ **F4's contract leg is closed by something stronger than a screen test**, and it is the pattern
F5 onwards should copy: `F4WriteContractIT` sends the screens' literal JSON bodies to a real Boot
server over real HTTP, and **it corrected a premise the step was built on** on its first run.

📌 **One obligation is open and belongs to no step yet:** the database still sorts by bytes under
locale `C` while the browser sorts by `Intl.Collator('el')`. Invisible only because no list pages on
the server. **Whoever adds paging to a list screen owns it** — see `PROGRESS.md`.

**Hours** — `Est.` is a planning estimate where one exists; most rows here don't have one
yet, matching how steps 16a/16b were treated on the backend roadmap (no estimate invented
for work that wasn't originally planned). `Actual` is measured from session transcripts,
not estimated, using the same method documented in `novocore-roadmap.md` — active time
under a 5-minute gap cap, tokens read from the `usage` field. Where a step spans multiple
sessions or is still open, figures are recorded as "at least" per that same convention.

This file covers frontend work only. Backend steps (0–16b and onward) live in
`novocore-roadmap.md`; this file picks up once frontend foundations (Step 16, backend
roadmap) were complete.

---

## Phase A — Stabilize the core screens

Everything in this phase happens before adapters/modules work opens up. Each step is
built and tested individually — automated tests plus a manual browser check — before the
next one starts. This mirrors the backend's own step-by-step discipline (Step 15's
lesson: findings get fixed inside the step that found them, not handed forward with two
candidate causes per symptom).

| Step | What                                                                                                                               |  Est. | Actual | Status        |
|-----:|------------------------------------------------------------------------------------------------------------------------------------|------:|-------:|---------------|
|   F0 | Restore dev data — build and run step 15c, the seed pass that was approved and never written ᶠ⁰                                    |     — |    0.9 | 🟢 Done        |
|      | *(not a step)* Products bugfix pass — the render loop, and two defects it hid ᵇᶠ                                                   |     — |        | 🟢 Done        |
|   F1 | Suppliers — list, detail, create, per-field PATCH (Products' pattern, reused) ᶠ¹                                                   |     — |        | 🟢 Done        |
|   F2 | Customers — same pattern, plus the protected retail-customer record ᶠ²                                                            |     — |        | 🟢 Done        |
|      | *(deferred out of F2)* Customer VAT class override — its own follow-up ᶠ²ᵃ                                                          |     — |        | 🔴 Not started |
|   F3 | Users & Roles — real admin screen: create roles, grant sections, manage accounts ᶠ³                                                |     — |    0.9 | 🟢 Done        |
|   S1 | *(standalone, not folded into F4)* Substring search — `pg_trgm` + `unaccent`, one shared mechanism, wired to all five built screens ˢ¹ |     — |    1.8 | 🟢 Done        |
|   S2 | *(standalone)* **Sorting** — sortable columns on all five list screens, one collator, client-side ˢ²                                |     — |    0.7 | 🟢 Done        |
|   F4 | Settings — three config pages, Reference Data (VAT classes, UoM), Adapters/Modules grids (already read-only) ᶠ⁴                     |     — |    1.0 | 🟢 Done        |
|   F5 | Sales Invoice + Credit Note — first transactional-document screen; decides the create/preview/commit pattern                       |     — |        | 🔴 **Next**    |
|   F6 | Purchase Invoice + Goods Receipt — same document pattern; no preview endpoint yet, decide whether to add one                       |     — |        | 🔴 Not started |
|   F7 | Receipts, Payments, Bank Transfers — editable-in-place variant of the document pattern, plus settlement/allocation UI              |     — |        | 🔴 Not started |
|   F8 | Freight Allocation, Journal (read-only), Write-offs — rounds out Finance on patterns already proven                                |     — |        | 🔴 Not started |
|   F9 | Stock by Location, Lots & Serialized Units, Email Outbox — operational read views                                                  |     — |        | 🔴 Not started |
|  F10 | Design optimization pass — one batched review across every built screen; Claude Design's real brand look                           |     — |        | 🔴 Not started |
|  F11 | Whole-system regression — a realistic end-to-end scenario driven through the actual UI; the frontend equivalent of backend Step 15 |     — |        | 🔴 Not started |
|      | **Subtotal, F0–F11**                                                                                                               | **—** |        |               |

**— Stable checkpoint —** Everything above built, individually tested, polished, and
proven together end to end. This is the gate before adapters/modules work opens up.

## Phase B — Adapters & modules (frontend side)

Each remaining backend roadmap step (17–43 in `novocore-roadmap.md`) gets its frontend
counterpart here once its backend work lands — either a new screen or activating an
existing nav placeholder. Order follows the backend roadmap's own sequencing, not
repeated here to avoid the two files drifting out of sync with each other.

| Step | What                                                                                                                               |  Est. | Actual | Status        |
|-----:|------------------------------------------------------------------------------------------------------------------------------------|------:|-------:|---------------|
| F12+ | Per adapter/module frontend work, backend-then-frontend, in backend roadmap order                                                  |     — |        | 🔴 Not started |

---

## Notes

**Already built, not tracked as its own row above.** Frontend foundations (nav-as-data,
permission gate, typed API client, decimal handling, table abstraction, i18n, CSRF/proxy
setup, the write-mutation-safety guard) and the Products screen (list, detail, create,
deactivate/reactivate, inline per-field editing) are complete — see `novocore-roadmap.md`
rows for Step 16/16a/16b-adjacent frontend work and its own measured figures. This file
starts fresh from the next unbuilt piece of work.

⚠️ **"Complete" there meant "built and unit-tested", and the bugfix row above is what that
distinction cost.** Every one of those foundations was exercised only against an empty table and a
one-row fixture, and both defects that survived — a table that re-rendered itself whenever a filter
changed, and a select that displayed the raw id — are invisible at that size. **F1 onwards should
assume the same is true of anything it reuses**: the pattern being reused has been proven correct on
one row, which is not the same as proven correct.

**ᶠ⁰ F0 — done, and the answer was the less comfortable of the two.** The database was
not wiped; the seed pass **had never been written**. Step 15's proposal scheduled it as
commit 15c, the step was agreed at *full* scope which included it, 15a and 15b landed and
15c did not — and `PROGRESS.md` never mentioned it in either direction, so nothing recorded
that it was missing. The seam it needed (`HttpTransport`) had been in the repository since
15a with a javadoc naming the driver nobody wrote. See `PROGRESS.md`'s F0 section for the
four independent proofs that nothing had ever been deleted, the decisive one being that
`pg_sequences.last_value` was still NULL — never called — for `product`, `supplier`,
`journal_entry` and four more.

**What the fixture actually contains**, measured off the seeded database rather than
carried forward from this note's first draft, which claimed *"15 products, 12 customers,
~120 journal entries"* and was wrong on all three:

| | | | |
|---|---:|---|---:|
| Products | 8 | Sales invoices | 10 |
| Customers | 5 *(incl. the seeded retail walk-in)* | Purchase invoices | 7 |
| Suppliers | 3 | Goods receipts | 5 |
| Fixed assets | 2 | Credit notes | 4 |
| Journal entries | 48 *(131 lines)* | Inventory lots | 6 |

Debits equal credits at €20,372.46, no entry is unbalanced, every entry falls inside
2026-01-05 to 2026-03-31, and the entries come from **nine** distinct sources — so the
data has breadth rather than one path repeated. Every party and product carries a
`TEST-` prefix, per step 15's §11 Q3.

**Re-seeding does not mean `docker compose down -v`.** Use `docker/reset-trading-data.sql`.
On this stack `down -v` also destroys the commissioned Google Drive refresh tokens and the
Owner account, neither of which is reproducible from `docker/.env` — a hazard that was
found while doing F0 and is written up in the script itself.

**Measured, per the method in `novocore-roadmap.md`:** 353 events in the window from
`507864f` to F0's close-out, **0.93 h active** against 2.58 h wall clock, 221k out, 33.5M
in. Recorded as **0.9**. Two caveats, both of which make it an "at least": the window rule
includes 0.24 h of the *preceding* session's tail, which ran on after its own commit, and —
as with every row in either roadmap — the close-out that follows is not yet in the
transcript when the figure is computed. This session's own share was 0.69 h and 154k out.

**ᵇᶠ The Products bugfix pass (2026-07-31, `3458ee6`) is deliberately not numbered.** It is not a
roadmap step and it did not start F1 — **F1 is still the next piece of work.** It is given a row so
the sequence of what actually happened can be read off this table, and its hours are **left blank**
rather than estimated, on the same rule as every other figure here: it spans a diagnosis session and
a fix session with no commit boundary between them that the measurement method can use, and no
figure that cannot be measured gets a plausible one written into this column.

What it was: a re-render loop in `DataTable` — a freshly allocated `[]` while a query held no data,
feeding `useReactTable`'s auto-reset, feeding a `useListState` setter that allocated a new state
object for a no-op change. Any change to a list screen's filter wedged the tab permanently, which is
why three unrelated-looking interactions all appeared dead at once. **It was latent since `DataTable`
was written and is unrelated to F0** — an empty response wedges identically — but F0 is what gave
anyone a reason to touch a filter. Full write-up, including why the regression test needed a
response delay to fail at all, is in `PROGRESS.md` under *Products — the wedge*.

**Two things it leaves open, both decisions rather than work:**

- **Row double-click.** It was reported as broken; it had never been built. Whether a table row
  should have a default action at all — and whether that action is "open detail", when the SKU cell
  is already a link to exactly that — is a design decision for every list screen from F1 on, not a
  Products fix. **Deliberately left unbuilt pending that decision**, so F1 does not copy an answer
  nobody made.
- ~~**The SKU filter box is an exact lookup**, because `GET /api/products?sku=` is. Typing `TEST`
  against eight `TEST-PRODUCT-*` SKUs matches nothing. Queued as a backend item; the choice between
  a real search endpoint and clearer labelling is the owner's, and **the frontend should not change
  until it is made.**~~ **Decided and closed by S1 (2026-08-01): a real search endpoint.** The box
  now sends `search=`; `sku=` and `ean=` stay exact and are what a scanner uses.

**ᶠ¹ F1 — done, all ten sub-parts, with both open questions decided before anything was built.**
The owner chose: VAT status and its exemption reason are **one editor**, with the reason revealed
only when the chosen status requires it; and the **create form is in F1**, which the roadmap line
above now says. Two routes are deliberately excluded — `match-suggestions` belongs to the
never-silently-guess matching flow, `by-vat-number` to the AADE/VIES adapter (step 28). Checklist
with verdicts in `PROGRESS.md`.

**Its hours are blank on the same rule as the bugfix row**: F1 was built inside a session that also
carried that pass and its follow-up, with no commit boundary the measurement method can use.

**Re-examined at F3's close-out, and still blank — for a reason worth stating precisely, because the
obvious reading is wrong.** A window *does* exist for both F1 (`0a957d1`→`b406b27`, 0.36 h) and F2
(`b406b27`→`496c7be`, 0.51 h). The blanks were never "no commit exists". They are that the bounding
commits — `297cf9e`, `0a957d1`, `0cfb130` — are docs-and-practice commits made **part-way through the
same sitting**, so each window slices the session rather than bounding the step, and the figure would
under-count by an unknown amount. **A figure that cannot be measured is left blank rather than given a
plausible value**, and a measurable-looking number that silently excludes part of its step is exactly
the plausible value that rule exists to keep out. F3 differs because its window runs commit-to-commit
across a session boundary, not through one.

**Two things F1 establishes that F2 inherits.** First, `VatStatus`'s two flags — `INTRA_EU_B2B`
needs a VAT number, `EXEMPT` needs an exemption reason — are **not on the wire**, so
`vat-status-rules.ts` mirrors them from `VatStatus.java` and a test pins that every value is
accounted for. **F2 must move that file up rather than copy it**: customers have the same VAT fields
and the same route shape, and two copies is how the two screens come to disagree about what `EXEMPT`
requires. Second, creation was proved **against the real backend in both browsers**, twice — once
against a name the domain already holds, which returns `422` only if the body parsed and writes
nothing, and once for real. That is now the standing rule in `CLAUDE.md`, and it is what the
products create form was cleared by a stub without.

**ᶠ² F2 — done.** The shared extraction went first, as it had to: `lib/vat-status.ts` and
`components/vat/vat-status-field.tsx` came out of `pages/suppliers/` **before** any Customers code
existed, and Suppliers' 18 tests passed unchanged on them — which is the proof, rather than a claim
that the move was equivalent.

**The protected retail record shows its locked controls disabled with the reason, never hidden.**
That needed `FieldEditor` to grow a state it did not have, and the distinction is worth keeping:
`editable: false` (a VIEW grant — *not yours to edit*) gets **no affordance at all**, because a
disabled button tells somebody to keep trying; `lockedReason` (*editable in general, fixed on this
record*) gets a **disabled control with the reason**, because hiding it would leave an operator
hunting for a setting every other customer has. A test holds both directions.

Reading the API fresh rather than adjusting Suppliers' shape was worth it three times:

- **The retail record's rules are only partly refused well.** Deactivation and the `INTRA_EU_B2B`
  rule answer `422` with full reasons; `EXEMPT` and setting a VAT number answer a bare `400`,
  because those are thrown as `IllegalArgumentException` from the domain. Reading one rule would
  have suggested they all worked. Backend item 4; the screen mirrors the reasons meanwhile, and says
  it is a mirror.
- **`CustomerView` returns `systemRecord` and `mergeable`, and the spec declares neither.** The
  screen uses `systemKey !== undefined`, which *is* in the spec.
- **Customers do not reject duplicate names; suppliers do.** F1's trick for exercising create
  without writing — submit an existing name, get `422` only if the body parsed — answered `201` here
  and created a row. Found by trying it. F2's creation proof is therefore a real create in each
  browser with the rows removed after.

**ᶠ²ᵃ The customer VAT class override is deferred, deliberately and with a test holding it.**
`vatClassOverrideId` and `PATCH …/vat-class-override` exist and are customer-only; *"this customer is
always taxed at this class regardless of the product"* carries real accounting weight and needs the
`TAX_AND_CHARGES` gating worked through. A test asserts the field is **absent** from the detail
screen, so adding it later is a deliberate act with a test to update rather than something that
drifts in with a copied screen. Not scheduled against a step — it is the owner's to place.

**ᶠ³ F3 — done, all fifteen sub-parts, with both decisions taken before anything was built.** The
grant grid is a **segmented three-state toggle per section**, unavailable levels disabled with their
reason; setting somebody else's password is **generate, show once, force an acknowledgment, never
again — no confirm-field**. Checklist with verdicts in `PROGRESS.md`.

**Two things F3 establishes that later steps inherit.** `SegmentedControl` and `PasswordHandoff` are
both written as shared components rather than screen parts, and both are documented in
`frontend/README.md` — the password one because it is the only place this application ever displays a
credential, and the segmented one because the "unavailable option, shown with its reason" shape is the
`lockedReason` rule one level down.

**And one trap worth carrying forward:** a **full-access role holds everything with no grant rows at
all**, so a grid built from `RoleView.sectionGrants` alone renders every section as `NONE` for Owner
and Admin — the screen stating the exact opposite of the truth about the two most privileged roles.
The catalogue (`GET /api/sections`) is the row list; the flag is checked first. A test holds both.

**Measured, per the method in `novocore-roadmap.md`** — and this row is no longer blank, because the
thing that blocked it has gone. The earlier note here said F3's hours could not be measured as "F3
was built in a session with no commit boundary the measurement method can use before its own
close-out." Its close-out is this one, so both boundaries now exist: `496c7be` (F2's last commit) to
`aea0e56`. **569 events, 0.87 h active against 16.74 h wall clock, 259k out, 86.6M in. Recorded as
0.9.**

The wall-clock figure is large because the window spans a night; the 5-minute gap cap is exactly
what stops that counting, and the split shows it doing so: **0.66 h in the F3 build session**
(2026-08-01, 519 events) plus **0.12 h of the preceding session's tail** (50 events, which carried
the groundwork commit `96bed1c` seven minutes after F2's), the remaining 0.09 h being the single
capped inter-session gap between them. As with every row here it excludes its own close-out, which is
not in the transcript when the figure is computed, so read it as "at least".

⚠️ **The post-F3 work in the same day is deliberately not in this figure.** The four commits from
`50f7055` to `32305a9` — backend item 2's primitive half, item 9, item 8's rewrite and the docs
cold-read — are queue work approved *after* F3 landed, not part of the step. Measured separately for
the record: **1.55 h active, 236k out** over `aea0e56`→`32305a9`. They have no roadmap row of their
own; `PROGRESS.md` carries them as the post-F3 approvals table.

⚠️ **F3's live probe corrected a backend item rather than just passing.** `NewUser.roleId` is a
primitive `long`, so omitting it answers `400` naming no field — the same defect as `serialTracked`,
which `PROGRESS.md` had recorded as one of *exactly two* on the surface. That count came from a grep
for primitive `boolean`; **at least 22 request records carry a primitive**, and **0 of the 50
request-body schemas on the surface declare a `required` list**. F5 onwards is where those start
being sent, one at a time.

**ˢ¹ S1 — done, all twelve sub-parts.** Standalone, spanning backend and frontend: `pg_trgm` +
`unaccent` (migration **V28**), one `IMMUTABLE` normalisation function, 15 GIN trigram indexes, one
shared `TextSearch` specification, `?search=` on the five list routes, and one `SearchFilter`
component on all five screens. It closes the open decision recorded in the bugfix note above.

**Two findings, and the first is the more important one.** The **test database was not configured
like the real one** — `compose.yml` uses `--locale=C` (deliberately, for deterministic Greek sort
order) where Testcontainers took the image default `en_US.utf8`. Under locale `C`, `lower()` folds
ASCII only, so the normalisation function shipped with a bare `lower()`, **every test passed**, and
searching for a Greek name on the real server returned nothing with no error anywhere. Caught by the
live check, not by the suite. The function now names `pg_c_utf8`, and — the real fix — the test
container is pinned to the same locale as production, with an assertion so the pin cannot be quietly
removed. The second: a **restricted column must leave the query, not just the response**, or a role
can confirm a hidden supplier code one character at a time. Full write-ups in `PROGRESS.md`.

🎯 **The authoritative search target list is the 16-row table in `PROGRESS.md`, not this row and not
the five screens S1 shipped against.** It names the fields for every screen that will ever have a
search box, including ones whose entity does not exist yet. **A step that adds search adopts its row
from that table** — the point of writing it down is that nobody re-derives a narrower version later.
It also flags the trap those rows share: a document's search fields include the *counterparty's*
name, VAT, code and alias, which live on another table, and `TextSearch`'s dotted path produces an
**inner join** — so a document with no counterparty would drop out of its own list.

**The reconciliation against that full list found two gaps a green build could not**, and both were
closed in **V29**: `Product.brand` had never been built at all (named in brief §5 since the
beginning, absent from the schema, therefore absent from every test), and `supplier.vat_number`
existed but was simply not searched while `customer.vat_number` was. A test only checks the fields
somebody pointed it at, which is the argument for writing the list down once.

**Still deliberately not built:** `Supplier.code`, `Supplier.alias`, `Customer.code` — named in the
brief's *(draft)* field lists, built by neither entity, and a schema-plus-routes-plus-forms item
rather than a search one. That single item blocks part of **six** rows of the target list, which is
the argument for clearing it before F5.

**`Product.category` is its own proposal and was not started in any form**, not even the schema. The
requirement is recorded in `PROGRESS.md`: **three levels deep** and **a product belongs to several
categories at once**, which means a self-referencing category table plus a join table — not two flat
columns, and not an enum. Written down because the brief's one-line *"Category (main/sub)"*
understates it, and building from that line would produce the wrong thing.

**✅ Closed out 2026-08-01, and live-verified by the owner.** All fifteen sub-parts have verdicts in
`PROGRESS.md` — twelve approved up front, three added mid-step (Brand, the supplier VAT gap, and the
`aria-label` fix a broken test forced). None is "still open". The owner ran the two live HTTP checks
personally on the running stack and both returned correct results, so the one partial verdict the
step carried is now closed.

**Measured, per the method in `novocore-roadmap.md`** — and this row is *not* blank, because for once
the window is clean: `d27d9bc` (F3's close-out) to `3ea8782`, commit-to-commit **inside a single
session** rather than slicing through one. **821 events, 1.81 h active against 3.66 h wall clock,
359k out, 120.2M in. Recorded as 1.8.** 815 of the 821 events are this session; the remaining 6 are
the previous session's tail, which the window rule includes. As with every row here it **excludes its
own close-out**, which is not in the transcript when the figure is computed — read it as "at least".

**ˢ² S2, sorting — done.** Sortable columns on Products, Suppliers, Customers, Users and Roles, on
the existing `DataTable`. **Client-side**, and that is a finding rather than a shortcut: the running
container's own bytecode says those five controllers accept `active search` and nothing else, so
every row is already in the browser and a client sort sorts the *list*, not a page.

**The collation question was settled before any sorting code was written, from the live database.**
`ORDER BY` under `--locale=C` is **byte order** — `Zebra` before `apple`, `Ácme` after `zebra`, and
every Greek name after every Latin one with `Ωμέγα` ahead of `αθήνα`. ⚠️ **That is live behaviour,
not something sorting introduced**: all five endpoints already order in the database. ⚠️ **And
`pg_c_utf8` does not fix it** — S1's collation changes case *mapping*, not sort *order*, and its
`ORDER BY` output is character-for-character identical to `C`'s. The answer is `el-GR-x-icu`, Greek
block first, fixed rather than following the account language; `Intl.Collator('el')` matches it
byte-for-byte and `collation.test.ts` pins PostgreSQL's literal output so the two cannot drift.

**One real defect found by a test:** TanStack picked a column's first sort direction from the value
in **row zero**, so the direction of a user's first click depended on which record happened to be on
top — and the header's accessible label followed it. Fixed with `sortDescFirst: false`.

**269 frontend tests, 27 files** (from 238/26); lint 0 errors, build and offline check clean.

**Measured, per the method in `novocore-roadmap.md`** — window `d7708da` (S1's close-out) to this
session's commit. **397 events, 0.69 h active against 0.78 h wall clock, 216k out, 35.1M in.
Recorded as 0.7.** 392 of the 397 are this session; the remaining 5 are the previous session's tail,
which the window rule includes. As with every row here it **excludes its own close-out**, so read it
as "at least".

✅ **S2's open browser leg is closed** — the owner ran it personally on 2026-08-01, together with
F4's. Nothing about S2 is outstanding.

⚠️ **What S2 leaves for whoever adds server-side sorting.** `DataTable` now refuses to client-sort a
server-paged list — sorting one page of many and presenting it as the order of the whole table is
convincing and wrong — so a column there is sortable only if its `meta.sortKey` is one the endpoint
declares. **None of the five column files carries a `sortKey` yet**, because no backend enum exists
to name one. The day one of these gains paging, its sort controls *disappear* until somebody adds
them. That is the safe failure and it is loud, but it is a real obligation.

**ᶠ⁴ F4, Settings — done, all 22 sub-parts** (21 approved up front, one added mid-step: the `SettingType` javadoc fix, given a row rather than a paragraph per `CLAUDE.md`). Three settings pages over one endpoint, VAT classes
and units of measure as full list/detail/create screens, plus search (target list rows 6 and 7,
migration **V30**) and sorting adopted from S1 and S2. **307 frontend tests, 31 files** (from
269/27); **backend 1376, +16**, one new migration, one new IT.

**Four decisions were taken before any code, and three of them were corrections to the preconditions
the step was scoped from.** ⚠️ **VAT classes were never "add and deactivate only"** — seven routes
exist, including `PATCH …/description` and the `reduced-counterpart` pair; the *rate* and the *code*
genuinely have none and never will. ⚠️ **The island reduced rates were already seeded**, since V5,
with the counterpart chain already populated — `PROGRESS.md` said the opposite, and the owner has
since confirmed applicability is decided rather than open: **Java Jives ships to reduced-VAT
islands**. ⚠️ **And "General" had no keys to put on it**, so the nav item was dropped rather than
shipped empty.

**Two findings, and the first is the one worth carrying forward.** `F4WriteContractIT` — the screens'
literal JSON bodies, sent to a real Boot server over real HTTP — **corrected a premise the step was
built on, on its first run**. The belief was that omitting the primitive `fractionalQuantityAllowed`
arrives silently as `false`; the server answers `400`, because `FAIL_ON_NULL_FOR_PRIMITIVES` refuses
an absent primitive and the guard simply is not in the constructor that had been read. The claim had
already been written into three files before anything executed it. **Reading is not running.** The
second: `SettingType`'s javadoc named a transport-security constant, `TLS`, that does not exist — and
because a setting's value is an opaque string in the spec, nothing in either repository could have
caught it.

**F4 also adds a fourth way a field can be unavailable**, now written up in `frontend/README.md`:
*no route exists on any installation* is neither `editable: false` (which means "not yours") nor a
disabled control (which invites a hunt for the permission that unlocks it). Plain text with the
reason. The rate and code on a VAT class, the code on a unit, and `cash.payment.limit` all use it.

✅ **Both verification legs are closed.** The contract leg by `F4WriteContractIT`; the **browser leg
by the owner personally, on 2026-08-01**, against the running stack — as for S1 and S2. Nothing about
F4 is outstanding.

📌 **What F4 did NOT close, and must not be read as having closed:** `el-GR-x-icu` is still not
applied to the database. F4 established *that* — measured five ways — and recording it was the
sub-part. **The divergence is open**: the browser orders by `Intl.Collator('el')`, the database by
bytes under locale `C`, and it is invisible only because no list pages on the server. **Whoever adds
paging to a list screen owns it.** Full statement in `PROGRESS.md`.

**Measured, per the method in `novocore-roadmap.md`** — window `a4324db` (S2's commit) to `c89c1c9`,
commit-to-commit inside a single session, which is the clean case. **1200 events, 0.95 h active
against 1.06 h wall clock, 605k out, 134.5M in. Recorded as 1.0.** 1188 of the 1200 events are this
session; the remaining 12 are the previous session's tail, which the window rule includes. As with
every row here it **excludes its own close-out**, which is not in the transcript when the figure is
computed — read it as "at least".

⚠️ **F4 is the largest `Out` figure of any frontend step so far** — 605k against S1's 359k and F3's
259k — and the step was not proportionally larger. Most of the difference is the two documentation
passes the findings earned and the contract IT, not screen code. Recorded without adjustment; it is
data about what this kind of step costs, which is the only reason this column exists.

**F5 carries more weight than its position implies.** It's not just the next screen — it
decides the entire document-creation interaction pattern (multi-line entry, running
totals, preview-then-commit) that F6–F8 all reuse. Getting it right cheaply informs the
rest of Phase A; getting it wrong means unwinding a pattern from three screens instead of
one. Worth disproportionate manual scrutiny relative to its single row here.

**Testing is continuous per step, not batched at the end.** F10 (design) and F11
(regression) are the only steps that are genuinely batched across everything built so
far — deliberately, since visual polish and a holistic proof only make sense once
several real screens exist to be consistent against. Every other step (F0–F9) carries
its own automated tests plus a manual check before the next step starts, the same
discipline used throughout the backend.

**Phase B is intentionally underspecified here.** Its actual shape depends entirely on
what order backend Steps 17–43 land in, which isn't fixed yet. This file will grow real
rows for Phase B as each backend step's frontend counterpart is actually scoped, rather
than pre-listing all ~30 of them speculatively now.
