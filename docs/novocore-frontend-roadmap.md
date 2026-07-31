# Novocore — Frontend Roadmap & Effort Tracker

**Legend:** 🟢 Done · 🟡 **Current** = in progress · 🔴 Not started

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
|   F3 | Users & Roles — real admin screen: create roles, grant sections, manage accounts                                                   |     — |        | 🟡 **Current** |
|   F4 | Settings — general config, Reference Data (VAT classes, UoM), Adapters/Modules toggle grids (read-only placeholders)               |     — |        | 🔴 Not started |
|   F5 | Sales Invoice + Credit Note — first transactional-document screen; decides the create/preview/commit pattern                       |     — |        | 🔴 Not started |
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
- **The SKU filter box is an exact lookup**, because `GET /api/products?sku=` is. Typing `TEST`
  against eight `TEST-PRODUCT-*` SKUs matches nothing. Queued as a backend item; the choice between
  a real search endpoint and clearer labelling is the owner's, and **the frontend should not change
  until it is made.**

**ᶠ¹ F1 — done, all ten sub-parts, with both open questions decided before anything was built.**
The owner chose: VAT status and its exemption reason are **one editor**, with the reason revealed
only when the chosen status requires it; and the **create form is in F1**, which the roadmap line
above now says. Two routes are deliberately excluded — `match-suggestions` belongs to the
never-silently-guess matching flow, `by-vat-number` to the AADE/VIES adapter (step 28). Checklist
with verdicts in `PROGRESS.md`.

**Its hours are blank on the same rule as the bugfix row**: F1 was built inside a session that also
carried that pass and its follow-up, with no commit boundary the measurement method can use.

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
