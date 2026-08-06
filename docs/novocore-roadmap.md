# Novocore — Unified Roadmap & Effort Tracker

**This file replaces the former backend and frontend roadmaps.** `novocore-frontend-roadmap.md` was
deleted on 2026-08-02; neither file should be recreated. Backend and frontend are one sequence
because they no longer proceed independently — several steps below span both.

**Legend:** 🟢 Done · 🟡 **Current** · 🔴 Not started · ⚪ Placement proposed, not decided, or optional

**Step IDs are deliberately not renumbered.** `0`–`16b` (backend), `F0`–`F11` (frontend) and `S1`/`S2`
keep the identifiers used in `HISTORY.md`, commit messages and every ADR. New work takes new prefixes
(`Q`, `R`, `D`, `X`, `M`, `U`) rather than displacing anything.

**`U` = a session that changes documentation and governance and produces no production code.** U1 is
the roadmap unification and documentation reconciliation of 2026-08-02. **Future documentation and
governance sessions take `U2`, `U3` and so on rather than entering the `F`/`Q`/`R` sequence** — a
doc-only session given a build-step letter makes the build sequence read as further along than it is,
which is the same misreading the attribution note under ᵘ¹ exists to prevent.

**Hours** — `Est.` is the original planning estimate, never overwritten. `Actual` is **measured** from
Claude Code session transcripts, never estimated; method in *How the actual figures were derived*. A
figure that cannot be measured is left blank with a reason, never filled with a plausible number.
`Out` is tokens generated; `In` is input + cache-creation + cache-read — **read the warning under
*How the actual figures were derived* before drawing any conclusion from that column.**

**Current state, measured 2026-08-06 (after F5):** **1,494 backend tests** (0 failures, 0 errors,
1 skipped — the deliberate `LiveSeedTest` skip; ⚠️ **`BUILD SUCCESS` read from Maven's own output,
not a wrapper's exit code**), **402 frontend tests across 41 files**, **247 API operations and 231
schemas** — ⚠️ **F5 changed neither count**: it ships five screens, a migration of four GIN trigram
indexes (**V36**) and one new integration test, and **no operation and no schema**. Its live leg ran
on 2026-08-06 and passed 22 of 23 rows, the 23rd never applicable.

*(The paragraph below is W1's, kept with its own figures — correct in its step's context.)*

**Current state, measured 2026-08-04 (after W1):** **1,480 backend tests** (0 failures, 0 errors,
1 skipped, `mvn clean verify` exit 0), **368 frontend tests across 39 files**, **247 API operations
and 231 schemas, 175 of which declare `required`** — ⚠️ **W1 changed no operation and no schema
count either**; it added **58 properties across 27 response schemas**, which is the whole point of
it: the document now says what Jackson writes.

*(The line below is R1b's, kept with its own figures — correct in its step's context.)*
**Measured 2026-08-04 (after R1b):** 1,457 backend tests, 310 frontend tests across 31 files, 230
operations and 223 schemas, 167 declaring `required`.

---

## Contents

1. [Phase 1 — the core](#phase-1--the-core-complete)
2. [Phase 2 — document model & core screens](#phase-2--document-model--core-screens-current)
3. [Phase 3 — adapters & modules](#phase-3--adapters--modules)
4. [Cross-cutting obligations](#cross-cutting-obligations--not-scheduled)
5. [Open decisions](#open-decisions--owners-call)
6. [Notes](#notes)
7. [How the actual figures were derived](#how-the-actual-figures-were-derived)

---

## Phase 1 — the core (complete)

| Step | What                                    |  Est. | Actual |     Out |       In | Status  |
|-----:|-----------------------------------------|------:|-------:|--------:|---------:|---------|
|    0 | Toolchain, ADRs                         |   0.8 |   ᵃ    |    ᵃ    |     ᵃ    | 🟢 Done |
|    1 | Skeleton, guardrails, CI                |   1.2 |    1.5 |    377k |    31.5M | 🟢 Done |
|    2 | Money/Quantity, migrations, audit       |   1.9 |    1.1 |    333k |    56.9M | 🟢 Done |
|    3 | Chart of accounts                       |   1.4 |    0.5 |    221k |    15.9M | 🟢 Done |
|   3b | VAT classes, exemptions, charges ᵇ      |     — |    0.4 |    215k |    25.2M | 🟢 Done |
|    4 | Users, auth, permissions ᶜ              |   1.9 |    0.7 |    341k |    96.9M | 🟢 Done |
|    5 | Product, Customer, Supplier, Asset ᵈ    |   2.5 |    1.8 |    511k |   125.6M | 🟢 Done |
|    6 | Inventory lots, locations, bundles      |   2.5 |    0.9 |    462k |    66.9M | 🟢 Done |
|    7 | Journal engine, VAT posting             |   2.5 |    1.1 |    517k |    73.1M | 🟢 Done |
|    8 | Purchase invoice, GR/IR, FIFO           |   2.4 |    1.2 |    452k |    99.3M | 🟢 Done |
|    9 | Sales invoice, receipts, payments       |   2.7 |    1.6 |    531k |   126.4M | 🟢 Done |
|   10 | Freight / landed cost allocation        |   1.9 |    1.3 |    442k |    90.1M | 🟢 Done |
|   11 | Email service ᵉ                         |   2.0 |    2.1 |    697k |   127.7M | 🟢 Done |
|   12 | Auto backups (incl. commissioning) ᶠ    |   2.7 |    2.6 |    606k |   151.5M | 🟢 Done |
|   13 | Test consolidation sweep ᵍ              |   2.6 |    1.8 |    490k |   151.9M | 🟢 Done |
|   14 | REST surface — 133 routes ʰ             |   2.5 |    2.0 |    646k |   151.5M | 🟢 Done |
|   15 | Dummy data validation — 9 defects ˡ     |   0.7 |    4.5 |  1,729k |   669.4M | 🟢 Done |
|  16a | Backend prerequisites for frontend ᵐ    |     — |    2.4 |    472k |   168.1M | 🟢 Done |
|  16b | Users/roles, journal, settings API ⁿ    |     — |    2.0 |    526k |   184.3M | 🟢 Done |
|      | **Subtotal, steps 0–16b**               |**32.2**|**29.5**|**9.58M**|**2,413M**|         |

⚠️ **"The core is built" is true of the ledger, not of the document model.** Phase 2 below carries
core schema work that Phase 1 did not cover: document types, series, categories, entity codes,
addresses, self-supply posting paths. Reading Phase 1 as "core complete, only screens remain" is the
misreading this note exists to prevent.

---

## Phase 2 — document model & core screens (current)

**This phase is backend roadmap step `16` in the old numbering.** Everything here is core and
frontend work that must land before any adapter is built.

| Step | What                                    |  Est. | Actual |   Out | Status         |
|-----:|-----------------------------------------|------:|-------:|------:|----------------|
|  FND | Frontend foundations ᶦ                  |     — |    0.7 |  183k | 🟢 Done         |
|      | Products screens ᵖ                      |     — |        |       | 🟢 Done         |
|      | Products bugfix pass ᵐᶠ                 |     — |        |       | 🟢 Done         |
|      | Brand pass + the session before it ᵛᵛ   |     — |   0.73 |  232k | 🟢 Done         |
|   F0 | Restore dev seed data ᶠ⁰                |     — |    0.9 |  221k | 🟢 Done         |
|   F1 | Suppliers screens                       |     — |        |       | 🟢 Done         |
|   F2 | Customers screens                       |     — |        |       | 🟢 Done         |
|   F3 | Users & Roles screens                   |     — |    0.9 |  259k | 🟢 Done         |
|   S1 | Substring search, 5 screens             |     — |    1.8 |  359k | 🟢 Done         |
|   S2 | Column sorting, 5 screens               |     — |    0.7 |  216k | 🟢 Done         |
|   F4 | Settings, VAT classes, UoM              |     — |    1.0 |  605k | 🟢 Done         |
|   U1 | Roadmap unification + doc reconcile ᵘ¹  |     — |    1.0 |  253k | 🟢 Done         |
|   Q1 | Backend queue: 4+6, 5, 1, 7 ᵘ           |     — |    1.5 |  208k | 🟢 Done         |
|   8a | `@Mandatory`, schema names, bytecode rule ᵈᵉᶜ | — |  1.3 |  314k | 🟢 Done         |
|  R1a | Document reference data — additive ʳ    |     — |    1.9 |  529k | 🟢 Done         |
|   U3 | Eleven design decisions recorded ᵘ³     |     — |    0.2 |   90k | 🟢 Done         |
|  R1b | Document reference data — behavioural ʳᵇ |     — |    1.5 |  286k | 🟢 Done         |
|   R2 | Document reference data (screens) ʳ²    |     — |    1.9 |  447k | 🟢 Done         |
|  R2b | R2 live-leg fixes + sort code + payment methods ʳ²ᵇ | — |    1.3 |  309k | 🟢 Done         |
|      | **▼ THE DECIDED SEQUENCE — the row order below IS the decision** ˢᵉᑫ | | | | |
|   W1 | Serialised-record contract fidelity ʷ¹  |     — |    1.5 |  356k | 🟢 Done         |
|   F5 | Sales Invoice + Credit Note ʷ           |     — |    3.7 |  766k | 🟢 Done         |
|   R4 | Payment methods become a business list ʳ⁴ |   — |        |       | 🟡 **Current** ʳ⁴ |
|   N1 | Release a reversed document's number ⁿ¹ |     — |        |       | ⚪ Direction settled, unbuilt |
|   D1 | Supplier/customer codes + alias ᵈ¹      |     — |        |       | ⚪ After F5, with D3 ˢᵉᑫ |
|   D3 | Customer/supplier addresses ᵈ³          |     — |        |       | ⚪ After F5, with D1 ˢᵉᑫ |
|   D4 | Internal document numbers ᵈ⁴            |     — |        |       | ⚪ After F5, with D5 ˢᵉᑫ |
|   D5 | Period locking — a movable lock date ᵛ  |     — |        |       | ⚪ After F5, with D4 ˢᵉᑫ |
|   F6 | Purchase Invoice + Goods Receipt ᶠ⁶     |     — |        |       | 🔴 Not started  |
|   F7 | Receipts, Payments, Transfers           |     — |        |       | 🔴 Not started  |
|   F8 | Freight, Journal, Write-offs            |     — |        |       | 🔴 Not started  |
|   F9 | Operational read views                  |     — |        |       | 🔴 Not started  |
|  F10 | Design pass, brand look + version badge ᵇᵃᵈᵍᵉ | — |      |       | 🔴 Not started  |
|  F11 | Whole-system UI regression              |     — |        |       | 🔴 Not started  |
|      | **▼ OUTSIDE THE SEQUENCE — each has its own gate, none is "next"** ˢᵉᑫ | | | | |
|  R2c | Sort code: invisible column, unsettable on series ʳ²ᶜ | — |  |       | ⚪ **DEFERRED and SPLIT** — 2a → F10, 2b → R4 ʳ²ᶜ |
|   C1 | Official Greek chart adopted directly, + display alias ᶜ¹ | — |    |  | ⚪ Decided 2026-08-06, **not scoped** ᶜ¹ |
|   C2 | Cash limit is TWO thresholds — needs a retail/B2B distinction ᶜ² | — |  |  | ⚪ Raised by R4, 2026-08-06 ᶜ² |
|   D2 | Product categories, 3 levels ᵗ          |     — |        |       | ⚪ Before the Woo load (19) |
|   R3 | Self-supply posting paths ˢ             |     — |        |       | ⚪ Not schedulable — accountant |
|  U2a | Split `PROGRESS.md` / `HISTORY.md` ᵘ²   |     — |        |       | 🟢 Done |
|  U2b | The split's drift guards ᵘ²ᵇ            |     — |        |       | 🔴 **Next after R4** — not optional |
|   U4 | The dated-figure sweep ᵘ⁴               |     — |        |       | ⚪ Deferred, **re-price before scheduling** |
|  F5b | `el-GR-x-icu` on `DOCUMENT_NUMBER` ᶠ⁵ᵇ  |     — |        |       | ⚪ Conditional on an owner check |
|  W1c | W1's two consumer clean-ups ʷ¹ᶜ         |     — |        |       | ⚪ Queued |
|  R1c | Fees / *Έξοδα και κρατήσεις* ʳ¹ᶜ        |     — |        |       | ⚪ Cut from R1, unscheduled |
|   8c | `NewPurchaseInvoiceLine`'s flat union ⁸ᶜ |    — |        |       | ⚪ **Trigger: before F6 binds it** |
|  M0a | Manager chart mapping — no code ᵐ⁰      |     — |        |       | ⚪ Unblocked, any time |
|  M0b | Trial import, one real year ᵐ⁰          |     — |        |       | ⚪ After D1/D3/D4, before 24 |
|   8b | Consumer cleanup — optional ᵈᵉᶜ         |     — |        |       | ⚪ Optional      |
|      | **Subtotal, F-rows (step 16 estimate)** |**8.0**|        |       |                |
|      | **Subtotal, Phase 2**                   |  **—**|        |       |                |

⚠️ **The phase subtotal is `—` on purpose, and the row above it is why.** **8.0 h was step 16's
estimate and it covers the F-rows only.** Q1, U2, R1–R3, D1–D5 and M0 were **never estimated**, and
several of them are backend schema work rather than screens. Adding 8.0 across this table would
present an estimate for the frontend as an estimate for the phase — so the F-row subtotal is stated
separately instead, where a reader scanning a column of dashes will actually meet it.

### ˢᵉᑫ The sequence was decided on 2026-08-04, and the row order above is where it is recorded

**The owner's decided sequence, in his own terms:**

    W1  →  F5  →  D1 + D3 + D4 + D5  →  F6 onward
    …amended earlier on 2026-08-06, after R2b's and F5's live legs:
    W1  →  F5  →  R2c  →  R4  →  D1 + D3 + D4 + D5  →  F6 onward
    …and amended AGAIN the same day, when the owner deferred R2c:
    W1  →  F5  →  R4  →  D1 + D3 + D4 + D5  →  F6 onward
    D2   before step 19 (the Woo one-time load)
    R3   when the accountant answers — not schedulable
    U2   whenever a session has slack
    R2c  deferred out of the sequence entirely, and split — see ʳ²ᶜ
    C1   recorded, not scoped — see ᶜ¹

**The rows were moved to match**, per `CLAUDE.md` §*A sequencing decision changes the roadmap's ORDER,
not a paragraph beside it* — a rule written the same day and for this. **The order is the statement;
this paragraph is the reasoning, not the record.**

**Why the four D-rows are ONE block rather than four slots**, recorded here and at each row:

- **D1 and D3 are the same two entities and the same two screens** — supplier and customer codes, the
  supplier alias, and both parties' addresses all land on the Customers and Suppliers screens. Doing
  them apart means **reopening those two screens twice**; doing them together means reopening them
  once. That is the whole argument, and it is about screen churn rather than about schema.
- **D4 and D5 are both ledger integrity and both the accountant's concerns** — internal reference
  numbers for the documents Novocore itself creates, and the movable lock date that closes a filed
  period. Neither has a screen in common with D1/D3; both are read by the same person for the same
  reason.

**And why they land AFTER F5**, which resolves the trade U3 recorded and deliberately left open: F5 is
the first step in a long while that produces something to look at, and Q1, 8a, R1a, R1b, R2 and R2b
have all been foundation. ⚠️ **The counter-argument has not gone away and is not deleted** — F5–F9 now
get built before the counterparty fields they will eventually want, so the document screens are
touched twice. **The owner made that trade knowingly; it is recorded as a cost, not as a non-issue.**

⚠️ **THREE STATUS MISMATCHES ARE OPEN, and nothing was promoted to close them.** Position and status
are different claims — *"this comes next"* is not *"this is scheduled"* — so the glyphs are untouched
and the mismatches are stated instead:

| Row | The mismatch | Outcome |
|---|---|---|
| **W1** | It was **first in the sequence** and being worked on 2026-08-04, while its status still read ⚪ **Unscheduled** | ✅ **Resolved by completion, not by promotion.** W1 landed the same day and the row is 🟢 **Done**. The proposal was never applied — the work overtook it, which is the honest way for a position/status mismatch to close |
| **F5** | It read 🟡 **Current** while a step sat ahead of it | ✅ **Resolved.** W1 is done, so F5 is genuinely current now and the glyph was always going to be right; it was briefly early rather than wrong |
| **D1 / D3 / D4 / D5** | Their status cells said **`Placement TBD`**, which stopped being true the moment the sequence was decided | ⚠️ **STILL ⚪, deliberately.** The false half was corrected in place — they read *After F5* — and the glyph **stays**: placed is not scheduled |

⚠️ **`Placement TBD` was corrected rather than left**, and that is a different act from promoting a
glyph. It was a **claim that had become false**: four rows went on saying *nobody has decided where
this goes* after somebody had. The ⚪ is a claim about scheduling and is still true.

#### ⚠️ 2026-08-06 — R2c DEMOTED, R4 PROMOTED. Both are the decision being applied, not row movement

**Two glyph changes on one day, in opposite directions, and this paragraph exists because that file's
warning cuts both ways.** `CLAUDE.md` §*a sequencing decision changes the roadmap's ORDER* forbids a
status changing **as a side effect** of a row moving. Neither of these is that:

| Row | Change | Why it is a decision |
|---|---|---|
| **R2c** | 🟡 **Current** → ⚪, and **out of the sequence block entirely** | ⚠️ **The owner deferred it: it is not core work and must not interrupt the core.** The demotion IS the decision. Earlier the same day the *promotion* was likewise the decision, and that footnote says so — this is the symmetric act, recorded to the same standard, so a reader does not read it as R4 displacing R2c |
| **R4** | ⚪ *After R2c, before F6* → 🟡 **Current** | **The owner commissioned R4's Phase 0 in the same instruction.** A step whose Phase 0 has been asked for is being worked on; the glyph follows that, not the vacated position. Its binding gate is unchanged and still **before F6** |

⚠️ **R2c did not merely move — it was SPLIT, and neither half is scheduled as R2c.** 2a (the invisible
column, cosmetic) is now an entry in F10's display-defects list; 2b (sort code absent from the series
**edit** form) is attached to **R4**. The row survives only as the single place naming both defects and
their destinations — see ʳ²ᶜ. **It is not a schedulable item any more and must not be picked up as one.**

**Q1, 8a, R1a, R1b, R2 and R2b are all 🟢 Done (R1b, R2 and R2b on 2026-08-04).** The running
order is above, in the table. ⚠️ **`PROGRESS.md`'s *What is next, in one place* no longer exists** —
U2a merged it into that file's **single status table**, because two status tables covering
overlapping sets of steps is exactly the drift this file's own rules exist to prevent. **This table
remains the record of the SEQUENCE; `PROGRESS.md` carries the STATUS.** ⚠️ **R1 split
into R1a and R1b on 2026-08-03**, and the boundary was test-facing: R1a could not change what any
existing test asserts, and R1b changed what *every* sales-invoice test constructs — so a red build
in R1a was a failure in new code, and in R1b it could be either. **That split paid for itself:** every
one of R1b's 54 construction sites compiled or failed as a construction problem, and the two genuine
defects it surfaced were both immediately distinguishable from fixture noise.

⚠️ **W1 is new, added 2026-08-04, and it is R1b's Phase 0 refusing to be folded in.** The
derived-accessor guard was scoped as one of R1b's three lines; measuring it first showed **32
committed schemas would fail it**, so it left R1b entirely rather than being landed with a baseline.
See ʷ¹ — the measurement is the valuable part and it is recorded there. ⚠️ **8a and 8b were
not new work invented here**: they are backend queue item 8, lifted out of Q1 and given their own rows
so the placement decision is visible in the sequence rather than buried in a queue — see ᵈᵉᶜ.

⚠️ **8b dropped from 🔴 Not started to ⚪ Optional on 2026-08-03, and the reason was measured rather
than judged** — see ᵈᵉᶜ. It is no longer on the critical path, so **R1 follows 8a directly.**

### ⚠️ The ⚪ rows share a deadline, and that is the decision (U3, 2026-08-03)

**Six of the seven ⚪ items above have the SAME gate: they must land before real data does, at step
24.** They are not seven independently schedulable rows, and treating them as such is exactly how a
**cluster** slips past a **shared** deadline — each row moves a little, no row looks late, and the
thing they have in common is written down nowhere.

| Item | Why it is inside the gate |
|---|---|
| **D5** | Before anything is filed from Novocore |
| **D4** | Before the accountant works in it |
| **D1** | Manager and Go data both carry codes and aliases — migrating without them means importing into columns that do not exist |
| **D3** | The same, for addresses |
| **M0** (both halves) | Its whole purpose is to *precede* the real migration |
| **R3** | ΣΑΥΤ and ΠΣΑΥΤ are issued routinely, so it must work before go-live |

**D2 is the exception — its gate is step 19**, because the Woo adapter syncs categories and the
one-time load runs before it.

⚠️ **The gate is still the gate. The SLOTS ARE NO LONGER OPEN — corrected 2026-08-04.** This section
used to end *"the individual slots are NOT [decided]"* and *"whether D1 and D3 land before or after F5
is an open decision below"*. **Both were answered by the owner's sequencing decision on 2026-08-04**:
**D1 + D3 + D4 + D5 are one block, after F5 and before F6** — see ˢᵉᑫ above and the rows themselves.
U3's four placements stand unchanged (M0a unblocked; M0b after D1/D3/D4; D2 before the Woo load; two
requirements moved into steps 21 and 22). Full reasoning in `HISTORY.md` under *U3*.

⚠️ **What the sequencing decision did NOT change: the gate is still what matters.** Four of the six
now have slots, and a slot is a weaker guarantee than a deadline — **a block that slips as a block
still misses step 24 together**, which is the exact failure this section was written to name. **R3 and
M0b remain without slots** and are the two most likely to be forgotten.

✅ **Every step through Q1 is verified on both legs, with one stated exception.** ⚠️ **Q1's live
browser leg has not been run** — its four items are proved by the contract tests and by a probe
against the real server, which is what has actually bitten, but the browser half of item 5's screen
change is outstanding and is the owner's to run. Everything below describes the position through F4.

✅ **Every step through F4 is verified on both legs.** Each carries its own automated tests, and S1,
S2 and F4 each also needed a live browser check against the running stack — **the owner ran all three
personally on 2026-08-01**, since the Owner password is deliberately not in this repository. Nothing
is outstanding on any built step. ⚠️ The one exception below the step level is **F2a, the customer VAT
class override deferred out of F2** — a deliberate deferral rather than an unfinished step. **It no
longer has a row in this phase**: it moved to step 18 on 2026-08-02, because it is adapter-dependent
work rather than a leftover screen task. See ᶠ²ᵃ under step 18.

**Testing is continuous per step, not batched at the end.** **F10 (design) and F11 (regression) are
the only genuinely batched steps** — visual polish and a holistic proof only make sense once several
real screens exist to be consistent against. Every other step carries its own automated tests plus a
manual check before the next one starts, the same discipline used throughout the backend.

**— Stable checkpoint —** Everything above built, individually tested, and proven together end to
end. This is the gate before adapters and modules open up.

---

## Phase 3 — adapters & modules

| Step | What                                    |  Est. | Status         |
|-----:|-----------------------------------------|------:|----------------|
|   X1 | General integration outbox ˣ            |     — | ⚪ Proposed, before 18 |
|   17 | Operational monitoring                  |   1.0 | 🔴 Not started  |
|   18 | Prosvasis Go adapter                    |   4.5 | 🔴 Not started  |
|      | *(sub-item of 18)* Customer VAT class override ᶠ²ᵃ | — | 🔴 Not started |
|  18b | Dispatch document + transport data ʸ    |     — | 🔴 Not started  |
|      | *(before 19)* One-time Woo → Novocore product load ʷᵒᵒ | — | 🔴 Not started |
|   19 | WooCommerce adapter ʷᵒᵒ                 |   2.0 | 🔴 Not started  |
|   20 | Skroutz adapter                         |   1.3 | 🔴 Not started  |
|   21 | ACS Courier adapter ᵃᶜˢ                 |   1.3 | 🔴 Not started  |
|   22 | Sales Order Fulfillment module ˢᵒᶠ       |   2.5 | 🔴 Not started  |
|   23 | File import adapter                     |   1.0 | 🔴 Not started  |
|   24 | Manager.io migration, parallel run ᵐᵍ ˢᵉʳ |   2.5 | 🔴 Not started  |
|   25 | Clearing Checks module ᶻ                |   2.0 | 🔴 Not started  |
|   26 | Price Tag Printing module               |   0.7 | 🔴 Not started  |
|   27 | Purchase Orders module                  |   1.5 | 🔴 Not started  |
|   28 | AADE/VIES lookup adapter ᵃᵈ             |   0.7 | 🔴 Not started  |
|   29 | AADE myDATA adapter ᵃᵃ                  |   2.5 | 🔴 Not started  |
|   30 | Bank aggregator adapter                 |   1.3 | 🔴 Not started  |
|   31 | Roast Date Report module                |   0.5 | 🔴 Not started  |
|   32 | Back-in-Stock Reminders module          |   0.5 | 🔴 Not started  |
|   33 | Service/Technician module               |   2.5 | 🔴 Not started  |
|   34 | Reports module                          |   3.0 | 🔴 Not started  |
|   35 | Accountant Monthly Package              |   1.3 | 🔴 Not started  |
|   36 | AI Analysis + voice I/O ʲ               |   2.7 | 🔴 Not started  |
|   37 | Employee manual + assistant ᵏ           |   1.5 | 🔴 Not started  |
|   38 | AADE Πάροχος adapter                    |   2.0 | 🔴 Not started  |
|   39 | POS provider adapter                    |   1.3 | 🔴 Not started  |
|   40 | Core-composed invoicing, retires Go ᵇᵇ  |   6.5 | 🔴 Not started  |
|   41 | Ergani work-card module                 |   1.3 | 🔴 Not started  |
|   42 | Production server migration             |   1.0 | 🔴 Not started  |
|   43 | Commercialization requirements ᶜᶜ       |     — | 🔴 Not started  |
|      | **Subtotal, steps 17–43**               |**48.9**|                |

⚠️ **These estimates are not credible and are deliberately left unrescaled.** Step 15 — the only step
whose work was contact with reality rather than clean-room build — came in at **6.4× estimate**. Every
adapter, the migration, and F11 are that same kind of work. The ratio is recorded so the decision to
rescale is the owner's and evidence-based. Do not treat the subtotal as a schedule.

Each Phase 3 step carries its own frontend counterpart — a new screen or the activation of an existing
nav placeholder — built after its backend half lands. Those are not pre-listed here; they get real rows
as each step is scoped, rather than thirty speculative ones now.

---

## Cross-cutting obligations — not scheduled

Real work with no step number. Each has an owner condition rather than a date.

| Obligation | Trigger / owner |
|---|---|
| **`el-GR-x-icu` never applied** — DB sorts by bytes under locale `C`, browser by `Intl.Collator('el')`. Confirmed live 2026-08-02: `datcollate=C`, 0 user collations, 0 non-default column collations, 0 indexes containing `COLLATE` | Invisible only because no list pages on the server. **Whoever adds paging to a list screen owns it** |
| **No `sortKey` on any of the *seven* list column files** — no backend constant names one. ⚠️ **Seven, not five**: F4 shipped VAT classes and units of measure with sorting too | The day a list gains paging, its sort controls disappear until keys exist. Safe and loud, still an obligation |
| **Paging missing on five services** — purchase invoices, goods receipts, settlements, lots/consumptions, email outbox. Confirmed 2026-08-02: exactly 3 of 175 operations accept `page`/`size` | Contract settled and proven on sales invoices. Mechanical. Fires together with the two rows above and with real data volume |
| **Test-environment parity has no teeth** — timezone, `DateStyle`, PostgreSQL major version, Java default locale/charset are unpinned | Owner's open decision — see below |
| **2FA + recovery codes (PLB-1)** — deliberately absent because the app is not internet-facing | ⚠️ **Must be resolved before any external or remote access**, including a Remote/Order Staff login, which is that role's whole purpose |
| **No change-password screen, and no recovery path** — rotating the owner password is a one-off programmatic run of `UserService.changePassword` against the live database | Owner holds the current password (they ran the S1/S2/F4 browser checks with it). The gap is the missing screen and the absent reset path, not a lost credential |
| **Series gap detection** — a document issued through Go that never reached Novocore | Belongs with step 25, Clearing Checks. Schema must support it from R1 onward |
| **XSD / annex diff check** — AADE publishes no live codification API; lists change between spec versions | Belongs with step 29. Must alert a human, never auto-apply |
| **Customer / supplier merge** — no mechanism exists (`V17` says so explicitly) | Leaning "alias forward, never rewrite history" |
| **Frontend dependency advisories — 4 as of 2026-08-02** (`npm audit`): 2 high via `react-router`/`react-router-dom`, 2 moderate via `@hono/node-server`/`@modelcontextprotocol/sdk`. ⚠️ Point-in-time figure; re-run rather than trust it | Deliberately unfixed |
| **Old repository copy in a Google Drive folder** | Pending safe removal |
| **One build script — make the safe path the easy path** ᵇˢ. ⚪ **Recommended by U3 (2026-08-03), unscheduled, deliberately not built.** Sets `pipefail`, always builds with `-am`, never truncates output, and is what `CLAUDE.md` tells sessions to invoke | ⚠️ **Nothing in this repository can guard a session's shell habits** — no rule, no test, no CI job sees how a command was typed. The only lever is making the correct invocation the default one. **Trigger: the fifth member of the stale-artefact family**, or whoever next writes a script that builds |

---

## Open decisions — owner's call

Nothing here is solved. Each is recorded so its absence reads as a decision rather than an omission.

**Sequencing and scope**

- **Should the Prosvasis Go adapter come before F5?** Novocore never obtains a ΜΑΡΚ itself — it
  packages data, the adapter hands it to Go, and the document comes back with its ΜΑΡΚ. So F5 as a
  data-entry screen is work that largely disappears once step 18 lands. The "F5 sets the pattern for
  F6–F8" argument is weaker than it looked. ⚠️ **The 2026-08-04 sequencing decision did NOT settle
  this** — it placed F5 relative to W1 and the D-block, and step 18 was not in the list it ordered. **A
  decision to bring step 18 forward would displace F5 in the sequence above**, so it is still open and
  is still the one that would cost the most to answer late.
- ✅ **~~Should backend queue item 8 be promoted to first on severity?~~ DECIDED 2026-08-03, and the
  answer was neither.** It was **lifted out of Q1 entirely** and given its own numbered step, split
  into **8a** (annotation, generator line, bidirectional bytecode cross-check, spec) and **8b**
  (client regeneration, fixture reconciliation), **placed after Q1 and before R1**. ⚠️ Both halves of
  that description were later corrected by measurement: the cross-check is **ASM + reflection, not
  ArchUnit**, and the 8a/8b boundary moved because it left `main` red — see ᵈᵉᶜ. Two reasons, both
  pointing the same way: **R1 adds eight tables' worth of new records**, which should be written with
  the enforcement already in place rather than retrofitted; and **every screen built afterwards
  multiplies the fixture reconciliation**, so it is cheapest now and only gets worse. Never
  concurrent with a frontend step. See ᵈᵉᶜ.
- ✅ **~~Do D1 and D3 land before or after F5?~~ DECIDED 2026-08-04 by the owner: AFTER.** And D4 and
  D5 with them, as **one block** — see ˢᵉᑫ above, where the reasoning sits beside the rows themselves.
  ⚠️ **The losing side of the trade is kept rather than deleted, because it is a cost that was
  accepted and not an argument that was refuted:** F5–F9 are now built before the counterparty code,
  alias and address fields those screens will want, so **the document screens get touched twice**.
  What won was that **Q1, 8a, R1a, R1b, R2 and R2b have all been foundation with nothing visible**,
  and F5 is the first step in a long while that produces something to look at.
- ✅ **~~Where D4 and D5 sit exactly.~~ DECIDED 2026-08-04 — in the same block, after F5.** They are
  grouped with each other rather than with D1/D3 because **both are ledger integrity and both are the
  accountant's concerns**; D1 and D3 are grouped because they are **the same two entities and the same
  two screens**, so doing them apart reopens Customers and Suppliers twice.
- ✅ **~~Where M0 sits.~~ DECIDED 2026-08-03 (U3), by splitting it.** **M0a — the chart mapping — is
  unblocked and can run at any time**, because it is a mapping exercise rather than an import and
  needs no code. **M0b — a real year of transactions — waits on D1, D3 and D4**, or it imports into a
  model already known to be incomplete. ⚠️ **Not after F11, and the reasoning is kept because that was
  the initial instinct:** M0 exists to find gaps while fixing them is still free; run it after eleven
  screens exist and every finding costs screens too. **It also does not need F11 — it is an import,
  not data entry.**
- **R3 is not schedulable at all.** Blocked on the accountant, and it carries **the hardest structural
  item in the project** — pricing from FIFO lot cost, which fights the price → post → consume
  ordering. ⚠️ **When the answer arrives, size it as a step rather than slotting it in as a
  sub-part.**
- **Analysis dimensions on journal lines.** ⚠️ **Channel is not the open part** — `SalesChannel` is an
  enum, `sales_invoice.channel` is `NOT NULL` with a CHECK, and step 3 split the Sales *and*
  Sales-returns accounts per channel, so per-channel revenue and return rate are **already visible in
  the ledger**. **Account-splitting works for channel but does not scale**: three channels is six
  accounts, and adding shop or product line multiplies rather than adds. The open question is whether a
  **generic analysis-dimension mechanism on journal lines** is wanted *before* a second dimension is
  needed, since retrofitting it means restating history. **Separately: should `SalesChannel` be
  promoted from an enum to a table**, given that adding a channel today means a migration *plus* new
  accounts?
- **Row double-click on a list screen.** Reported as broken during the Products bugfix pass; it had
  **never been built**, and was deliberately left unbuilt. Whether a table row should have a default
  action at all — and whether that action is "open detail" when the SKU cell is already a link to
  exactly that — is a decision affecting **every** list screen, not a Products fix.
- **Reserve a company dimension?** Not building multi-company — deciding whether to reserve the column.
  A second legal entity is the one change that turns this system into a rewrite.
- ⚖️ **A dedicated non-owner test account, so a live browser leg does not need the owner.** Raised
  2026-08-03 and **recorded without being acted on.** Credentials would live in a **gitignored local
  env file**, never in the repository. **The trade-off, stated rather than buried:** a working
  credential on disk, against a hard rule that it exists **only** on a development stack and never
  anywhere else — and the moment that rule is bent it is a real account on a real system. **Not
  needed for 8a**, which has no browser leg at all. ⚠️ **8b is the first step that might want one**:
  it regenerates the client and reconciles fixtures across the whole suite, which is exactly the
  shape of change whose breakage shows up in a browser rather than in a test. Owner's call.

**Awaiting the external accountant**

- **Precedence between the product's VAT class, the island reduced-rate mapping and the customer VAT
  class override.** Three inputs, no stated priority, and it is a statutory question rather than a UI
  one. ⚠️ **Needed for the island rates regardless of whether the override is ever built** — the
  mapping is seeded since `V5` and is in real use, so two of the three inputs already exist. See ᶠ²ᵃ.
- Statutory depreciation rates per asset category, and the category taxonomy. ⚠️ **Do not create real
  assets with real values until confirmed.**
- Two AADE VAT exemption codes (24, 28) absent from Go's list.
- myDATA codes for the OSS and IOSS exemption reasons — seeded null deliberately.
- myDATA unit-of-measure codes — column exists, every row null.
- **Self-supply posting accounts** — which chart-of-accounts rows carry the revenue and expense legs.
  VAT treatment *is* settled: deductible, and not capitalised into the asset cost.
- **Whether self-supply VAT deductibility is the same for both uses** — capitalising to fixed assets is
  business use; internal consumption may or may not be. One flag per type may be insufficient.
- Whether the AADE digital delivery-note regime applies, and whether Go already issues the legal
  dispatch note.
- **Q1-b — does `VatExemptionReasonService.create` keep a reason to exist?** ⚠️ **Decide with R1, not
  before.** Confirmed 2026-08-03: it has **no production caller** — seeding is Flyway SQL (`V5`, `V8`)
  and `/api/vat-exemption-reasons` is GET-only; its only callers are 12 sites in
  `VatExemptionReasonIT`. **Deliberately not deleted**: exemption reasons are the *seed-only* model,
  and two AADE codes (24, 28) plus the OSS and IOSS myDATA codes are still open with the accountant,
  so a create path may yet be wanted. R1 is the step that settles the seed-only pattern for document
  types, and this is the same question one entity earlier.

**Product and vendor**

- Bank aggregator selection and Greek coverage; POS terminal provider (epay vs NBG); ACS settlement
  myDATA code; invoice/document template design mechanism; physical hosting machine; company name if
  commercialized; the 8-hour session timeout, never confirmed as the right value.

---

## Notes

**ᵃ Steps 0 and 1 cannot be separated.** `22bb361` carries ADRs 0001–0005 *and* the skeleton. Row 1's
figures cover both. Combined estimate 2.0 h.

**ᵇ Step 3b is a real inserted step** (`15627d2`), never estimated, recorded so its 0.4 h is attributed
rather than absorbed into a neighbour.

**ᶜ Step 4 includes 4b.** Commits `a1da425` and `91543fa` are 20 seconds apart; time not separable.

**ᵈ Step 5's window also carries V7, V8, V10, V11.**

**ᵉ Step 11 includes ADR 0012's revision** (`8af7078`). Build 1.3 h, revision 0.8 h.

**ᶠ Step 12 includes the proxy self-invocation ArchUnit rules, the two defects they found
(`24a3cd7`, `a4ec7db`), the CI `pg_dump` fix (`5a6dfa5`), and live commissioning against Google Drive
(`e907a9e`).** Build 1.5 h, commissioning 1.1 h.

**ᵍ Step 13 includes the Q45 ledger fix** (ADR 0015, V24 — `951929f`). Sweep 1.0 h, defect 0.8 h.

**ʰ Step 14 — four commits** (`423bf34`, `e6354d6`, `b8aa9e2`, `f2e8e06`), and **the first estimate
this project came close to on a pure-build step**: 2.5 estimated, 2.0 measured. It also produced the
one migration the plan said would not be needed (**V25**) — the sort of thing an estimate cannot price
either way, and the reason step 16b checked its CHECK constraints rather than assuming.

**ˡ Step 15 is the one large estimate miss — 0.7 h against 4.5 h, a ratio of 6.4.** Three causes: the
estimate priced "insert some rows" rather than six classes of check over 133 routes; the step's purpose
is finding defects and it found nine, each needing a decision, a root-cause fix and tests; two of the
nine were recurrences of one pattern, which meant naming an anti-pattern and building three guards.
**The single most useful calibration point in this file: an estimate for "validate what we built" is
really an estimate for finding nothing.** Steps 24 and 25 have the same character. Full detail in
`HISTORY.md`.

**ᵐ Step 16a — four backend prerequisites, plus session eviction** (`fad0d11`), a defect latent since
step 4. The four: `GET /api/me`; `PATCH /api/me/language` (**V27**); preview endpoints for sales
invoices and credit notes, as an *extraction* of the existing pricing phase rather than a second
implementation; the committed OpenAPI spec with its CI drift check; and the paging contract, settled
and proven on sales invoices. One planning session that day (1.52 h) is deliberately excluded from the
row.

**ⁿ Step 16b — 37 routes, 137 → 174, no migration.** Three defects found inside the row, none in the
code the step set out to write: narrowing a role ended nobody's session; user administration was a
route to unlimited access; an unparseable enum answered a bare "Bad request." And one correction to a
belief rather than to code — the `HHH000104` justification was Hibernate 5 behaviour, wrong on 7.

**ᶦ Frontend foundations were built before step 1** (`492ce24`, `531f12a`, 2026-07-27) — **0.7 h,
183k out, 14.2M in** — attributed to no numbered row at the time. Recorded so the foundation is not
paid for twice. ⚠️ **This figure covers those two commits and nothing else.** It is not a figure for
Products, which is the next row.

**ᵖ Products screens — Actual deliberately blank.** The step-16 foundations commit, the first real
screen and its two guards (`94e17cd`, `56e3726`, `28c4119`, 2026-07-30/31) landed **without a
close-out recording figures**, and reconstructing them now would mean assigning transcripts to commits
after the fact — exactly the guesswork the `Actual` column exists to keep out. The transcripts survive
in `~/.claude/projects/c--Novocore/`; a future session can measure them against the commit boundaries.
**This note exists so nobody reads the silence as zero.**

**ᵐᶠ The Products bugfix pass is what "built and unit-tested" cost** (`3458ee6`). A re-render loop that
wedged the browser tab whenever a list filter changed, latent since the table component was written,
plus a select displaying a raw id. Both invisible against a one-row fixture. ⚠️ **Anything reused from
that period has been proven correct on one row, which is not the same as proven correct.** Its hours
are blank because it spans a diagnosis session and a fix session with no commit boundary between them
the measurement method can use.

**ᵛᵛ The brand pass and the session before it** (`d0ec9d9a` + `f4e4d84c`, 2026-07-31) — **0.73 h
active, 0.97 h wall, 232k out, 23.6M in**, over a window running from `28c4119`. The window rule
includes the earlier session, which spent 0.45 h diagnosing a frontend/backend version mismatch; the
brand pass itself was 0.20 h and 96k out. Given its own row so a measured figure is not lost when a
per-session table is condensed away.

**ᶠ⁰ F0 — the seed pass had never been written.** Step 15's proposal scheduled it as commit 15c, the
step was agreed at full scope, 15a and 15b landed and 15c did not, and `HISTORY.md` never mentioned it
in either direction. This is why an approved proposal is written down as a checklist, one line per part,
at the moment of approval. Built as `521a601`. **Re-seeding is `docker/reset-trading-data.sql`, never
`docker compose down -v`** — on this stack `down -v` also destroys the commissioned Google Drive refresh
tokens and the Owner account, neither reproducible from `docker/.env`. The fixture's exact contents,
measured off the seeded database, are in `HISTORY.md`.

**ᶠ²ᵃ The customer VAT class override — deferred out of F2, and moved to step 18 on 2026-08-02.**
`vatClassOverrideId` and `PATCH …/vat-class-override` exist and are customer-only; *"this customer is
always taxed at this class regardless of the product"* carries real accounting weight. **Two tests hold
it absent** — one that nothing matching `/VAT class/i` renders on the customer detail screen, one that
the create body carries no `vatClassOverrideId` key (both in `customers.test.tsx`) — so adding it later
is a deliberate act with tests to update rather than something that drifts in with a copied screen.
**Both stay and must not be weakened while this is unbuilt.**

⚠️ **It sat under F2 as a sub-row until 2026-08-02, which read as a small leftover screen task somebody
could pick up on a quiet afternoon. It is not.** It is **adapter-dependent work**, so it is attached to
step 18 as a named sub-item. **Three reasons, and the recorded one is no longer the main one:**

- **(a) Permission gating — the original reason, still open.** A control that changes what VAT a
  customer is charged needs its section and level worked through (`TAX_AND_CHARGES` was the candidate).
- **(b) Precedence, which got harder after the deferral.** There are now **three** inputs with no
  stated priority: the **product's own VAT class**, the **island reduced-rate mapping** (seeded since
  `V5`, and confirmed in use — Java Jives ships to reduced-VAT islands), and the **customer override**.
  Which wins when a customer holding an override buys a product with its own class and ships to an
  island is a **statutory question, not a UI decision**. Recorded under *Open decisions → awaiting the
  external accountant*.
- **(c) The decisive one — the rule must live in Go too.** Go prices and issues the documents;
  Novocore records them and **recomputes net/VAT/total independently from the line items to compare
  against the source document**. An override set only in Novocore would therefore make that comparison
  disagree with **every invoice for that customer** — not by a rounding residual but by a **whole VAT
  class**, which the mechanism flags as a probable data-entry error. The result is a control that
  **manufactures false alarms**. If Go carries the equivalent setting instead, the same business rule
  lives in two systems with no sync, which is the disconnected-data problem Novocore exists to end.
  **Either way the adapter is where one system can own the rule.**

🎯 **Verification item for whoever builds step 18 — to be answered against the running system, NOT by
reading one file:** *"Does recording a sales invoice recompute VAT from the customer and the product,
or store what the source document states?"* The answer decides whether this is a small screen or
adapter-dependent work. ⚠️ **The reasoning in (c) was derived from the design record, not from the
code** — which is `CLAUDE.md` §*a fact established by reading, then built upon* in its live form, and
is why this is an item to execute rather than a conclusion to build on.

⚠️ **This row was dropped from the first draft of this unified file, and the drop is itself the
failure `CLAUDE.md` §"An approved proposal is a checklist" exists to prevent** — a deferred sub-part
losing the only row that tracked it, in a file whose own notes explain why that must not happen. It was
restored for that reason as much as for the work, and moving it to step 18 does not undo the
restoration: it still has exactly one row, and that row now sits where the work is.

**ᵘ¹ U1 — roadmap unification and documentation reconciliation** (2026-08-02). Not a build step and it
never had an estimate: two roadmap files became this one, fourteen design decisions that existed only
in chat were written into the repository, ADR 0016 was added, and **the credit-note rename landed —
U1's, not Q1's**. Its findings are in `HISTORY.md` under *Roadmap unification*. U1 is what the `U`
prefix defined at the top of this file means.

✅ **U1 carries the credit-note rename.** `CreditNoteService.issue(NewCreditNote)` → `record(...)`,
matching `SalesInvoiceService.record` which was already correct; the controller method → `recordNote`
(not `record`, which would have collided with `SalesController_record` and created a *second*
duplicate `operationId`, backend queue item 1's exact defect); `operationId` `SalesController_issue` →
`SalesController_recordNote`; spec regenerated (**one-line diff**) and the generated client
regenerated. It was done in the same session rather than queued because **a naming rule with a known
standing violation is a rule people stop believing.**

⚠️ **It was attributed to Q1 until 2026-08-02, and the attribution was wrong in a way that showed.**
It came from **finding C1** — a naming-rule violation found on the committed surface while reconciling
the roadmap — not from the backend follow-up queue, which is a queue of defects raised by frontend
work. Filing it under Q1 made a cold read of these documents report *"Q1 current, one of six landed"*,
which **overstates progress on a queue that is untouched.** Correcting the attribution removes that
misreading at its source rather than annotating around it.

**Measured, per the method below** — window `0450c9f` (F4's close-out) to this session's commits.
**490 events, 1.01 h active against 17.99 h wall clock, 253k out, 49.0M in. Recorded as 1.0.** The
wall figure is large because the window spans a night; the 5-minute cap is what stops that counting,
and the split shows it working: **0.70 h in this session** (359 events), **0.14 h in a short session
that morning** (128 events) and 0.01 h of a previous session's tail (6 events), the remainder being
capped inter-session gaps. As with every row it **excludes its own close-out**, so read it as "at
least".

**The follow-up corrections of 2026-08-02 are inside U1, not a new row.** Six corrections from the
close-out review — the `U` prefix definition, this reattribution, the F-row subtotal, the figures rule
in `CLAUDE.md`, U2's placement and F2a's move to step 18. **The 1.0 h above does not include them**,
for the same reason it excludes the rest of the close-out: the transcript does not yet contain the
work when the figure is computed. **No number is written in for them rather than a plausible one.**

⚠️ **This row's `Out` is low relative to its `In`** — 253k against 49.0M — and that is the shape of a
reconciliation rather than a build: most of the cost was reading `PROGRESS.md`, two roadmaps, a
primer and a README repeatedly, plus a full `mvn clean verify` and a live database session, to produce
comparatively little text. Recorded without adjustment; it is data about what this kind of step costs.

**ᶜᵒⁿᵈ ✅ RESOLVED, 2026-08-03 — this footnote's marker is gone from Q1's row.** It read: *"Q1 is done
and NOT fully closed, and the distinction is deliberate … what remains open is item 7's regression,
and 8a is what closes it."* **8a closed it the following day**, so the row carries a plain 🟢.
⚠️ **Kept rather than deleted, because the device worked.** Stating a residual in the *status marker*
rather than in a paragraph underneath is what made the next session treat it as an acceptance
criterion instead of as background. Reuse it; do not let a step with something outstanding read as a
plain 🟢.

**ᵘ Q1 — the backend follow-up queue. 🟢 Done, 2026-08-03.** Of nine numbered items, two were done
(2 and 9) and one closed as stale (3) before Q1 existed as a step. **Item 8 left the queue and became
its own step** (see ᵈᵉᶜ), so **Q1 is four items: 4+6, 5, 1, 7 — all four landed.**

- **4+6** — one anti-pattern, fixed together. `Required` and `InvalidRequestException` moved from
  `core.web` into `core-api`, the exception renamed **`InvalidInputException`**; the two retail-customer
  rules enforced in `CustomerServiceImpl` where a caller reaches them; `NewUser`/`NewRole` guarded with
  `Required.field`. Plus **item 4's part 2**, a sweep case for a *well-formed body a domain rule
  refuses* — **proven by running it against the defect and watching it fail** before the fix landed.
- **5** — `PATCH /api/roles/{id}/description`. `Role.description` had **no setter**, so it was
  structurally unwritable rather than merely unrouted. Both frontend "there is no route" notes came
  out with it, as that item said they would.
- **1** — `InventoryController.writeOff` → `createWriteOff`, and `OpenApiSpecIT` now **refuses** to
  write a duplicate `operationId` rather than emitting an invalid document. The `orval.config.ts`
  workaround and the assertion pinning it were **deleted**.
- **7** — the seven boolean primitives boxed with `Required.field`, **plus a latent eighth**
  (`NewVatExemptionReason.inputVatDeductible`). ✅ **The regression it carried was closed by 8a the
  next day** — see ᵈᵉᶜ. ⚠️ Only **seven** could be confirmed in the spec; the eighth has no schema.

✅ **The live browser leg passed on 2026-08-03**, run personally by the owner after the app image
was rebuilt (see ᵗʰᶦⁿ). Four checks: the description **saved** on role 3; **cleared** to its unset
placeholder; role 1 (OWNER) showed the Description editor **disabled with the system-role reason**,
exactly as Name does; and **product creation still worked**, which is item 7's boxed `serialTracked`
proved from a form rather than from a test.

⚠️ **The credit-note rename is not in this queue.** It belongs to **U1** — see ᵘ¹ for why the
attribution was corrected.

**ᵗʰᶦⁿ The app image serves no frontend, and that is why a live leg needs a rebuild.** Confirmed
2026-08-03: the deployed jar contains **zero** static assets. The browser loads from the **Vite dev
server**, which proxies `/api` through Caddy to the app container — so the frontend recompiles from
disk on every save and the backend changes only when an image is rebuilt. Q1's first browser attempt
answered `404 "No static resource api/roles/3/description"` against an image built **26 hours before
the commit**, whose compiled `RoleController` carried eight route templates and not the ninth.
**Rebuilding the app image is now an unconditional precondition of handing a live leg to the owner**
(`CLAUDE.md`), and it is `build` + `up -d app` — ⚠️ **never `down -v`**, which also destroys the
commissioned Drive tokens and the Owner account.

**ᵈᵉᶜ Step 8 — declare every compact-constructor requirement.** Lifted out of Q1 on 2026-08-03 and
given its own step **after Q1, before R1**, replacing the open decision *"should item 8 be promoted
within Q1?"* The answer was neither promote nor leave last.

✅ **8a is DONE (2026-08-03). Its boundary with 8b moved during Phase 0, and the cause was CI.**

**The approved split was *8a = annotation + generator + rule + spec + schema names; 8b = client
regeneration + fixture reconciliation*, and that boundary could not exist without a red `main`.**
`.github/workflows/frontend.yml` triggers on `docs/api/openapi.json` — the one file 8a exists to
change — and that workflow both runs `spec-hygiene.test.ts`, which pinned three assertions to the
pre-8a state, and regenerates the client and diffs it against the committed one. Deferring the spec
instead is not an escape: `OpenApiSpecIT` fails the build on spec drift. So the regeneration and its
two fixes moved into 8a.

⚠️ **This was established by simulating the whole of 8b in an isolated `git worktree`, not by
predicting it.** The guard-derived `required` lists were applied to a spec copy, `npm run
api:generate` was run, then `tsc -b --force` and the full vitest suite. **420 generated files
changed, 1 TypeScript error, 1 failing test, 307 of 308 tests still passing** — so the second half
was never a session's work, and item 9's claim that the fixture backlog is *measured at zero* held.
The working tree was never touched.

- **8a, as built** — `@Mandatory` and `@ConditionallyMandatory(reason)` in
  `…core.api.shared` (the only module every request record can see); one line in
  `OpenApiSchema.recordSchema` reading the first; the bidirectional cross-check; **339 components
  declared across 114 records and 105 files**; Q1-a's four collisions split; the spec regenerated
  (75 → **143** schemas declaring `required`); the client regenerated; `spec-hygiene.test.ts`
  rewritten and one `RoleView` fixture completed.
- **8b — now ⚪ optional, and not a correctness step.** What remains is *taking advantage* of the new
  contract: removing `?.`/`??` guards on view fields that can no longer be undefined, and similar
  consumer tidying. **Trigger: whenever a screen touching those fields is next opened** — there is no
  reason to do it as a standalone pass. ⚠️ **The dedicated non-owner test-account decision attaches
  here and should be settled BEFORE 8b starts, not during it**: 8b is the first change whose breakage
  would show in a browser rather than in a test.

⚠️ **The mechanism was NOT ArchUnit, and this footnote said it was.** ArchUnit supplies class
discovery and the failure idiom; `org.springframework.asm` plus reflection does the attribution.
Two measured reasons: `JavaCodeUnit.getMethodCallsFromSelf()` carries **no argument information**, so
it can say a constructor guards something and never *which component*; and it **mis-attributes
lambda-body calls to the enclosing constructor** — it reports **342** guard calls where the
constructors' bytecode contains **340**, the two extra being `requireNonNull`s inside
`StockLevels`'s `byLocation.forEach(…)`, which constrain map entries rather than components. That is
the blind spot `CLAUDE.md` already names under proxy self-invocation. No new dependency was needed.

⚠️ **The cross-check was not optional and remains the load-bearing half.** Without it the annotation
is 339 hand-applied assertions that nothing verifies — *a fact established by reading, then built
upon*, at the scale of a whole API surface. All three rules were **proven by running them against
probes and watching them fail** before being trusted.

➕ **Q1-a landed here, and its recorded justification was WRONG and has been replaced.** The reason
given was economics — *schema naming is a generator concern and 8a already regenerates the spec, so
scheduling it separately pays that regeneration twice*. **The real reason is correctness.**
`OpenApiSchema` registered a component under the record's simple name, and Q1 recorded seven
`NameRequest` records as "structurally identical today, so the document is accidentally correct".
Measured on 2026-08-03: there were **four** collisions rather than one — `NameRequest` ×7 serving
**nine** operations, plus `ContactDetailsRequest`, `VatNumberRequest` and `VatStatusRequest` ×2 each,
so 13 records collapsing into 4 schemas over 15 operations. And **"identical today" stopped being
true the moment 8a ran**: two of the seven guard `name` with `Required.text` and five do not, so the
single merged schema would have declared `name` required for nine operations of which five do not
require it, or optional for two that do. **8a did not coincide with the defect; 8a would have created
it.** An economics argument invites someone to re-litigate the bundling on cost grounds later; a
correctness one does not. All four were split, including the three still identical — a schema that
is correct by coincidence is one nobody notices becoming wrong. `OpenApiSchema.claim` now refuses a
collision, **scoped to what reaches the spec**: `CreditNoteServiceImpl.Computation` /
`SalesInvoiceServiceImpl.Computation` and the matching `Rounding` pair collide too and are
**known and deliberately left alone**, because failing a build on a name that describes no contract
forces a rename for nothing, which is how a rule earns the reputation that gets it deleted.

**Measured, per the method below** — window `3044139` (Q1's close-out, 2026-08-03 13:33) to this
session's commit. **542 events, 1.33 h active against 1.36 h wall clock, 314k out, 81.9M in.
Recorded as 1.3.** Wall and active are nearly equal here, which is what a single uninterrupted
session looks like — unlike Q1's row, where the 5-minute cap was doing real work across a night. As
with every row it **excludes its own close-out**, so read it as "at least".

⚠️ **This row's `Out` is the highest of any step so far (314k) and its `In` the second highest**, and
that is the shape of a step whose cost is *review* rather than construction: 105 files read by
category, four probes run and reverted, one worktree simulation, and two full `mvn clean verify`
runs. The code written is small. Recorded without adjustment.

✅ **Item 7's regression is CLOSED.** Boxing the booleans (Q1 item 7) improved the *message* —
`"serialTracked" is required and was not supplied.` instead of a field-less `Cannot map null into
type boolean` — and removed the *declaration*, because `OpenApiSchema` marks a component required
when it `isPrimitive()` and a boxed `Boolean` is not: schemas declaring `required` went **78 → 75**
on 2026-08-03, and **75 → 143** when 8a landed the same day. ⚠️ **Seven of the eight, not eight.**
`NewVatExemptionReason.inputVatDeductible` has **no schema at all** — `/api/vat-exemption-reasons` is
GET-only, which is Q1-b's finding arriving from the other side — so it is confirmed annotated and
guarded in the backend instead. The seven that do have schemas are asserted **by name** in
`spec-hygiene.test.ts`, because a count cannot say which field came back. Documents saying "the
eight" were corrected.

**Measured, per the method below** — window `f143215` (U1's follow-up close-out, 2026-08-02 15:10)
to this session's commit. **558 events, 1.49 h active against 21.43 h wall clock, 208k out, 76.0M
in. Recorded as 1.5.** The wall figure spans a night; the 5-minute cap is what stops that counting,
and the split shows it working — **1.40 h of the 1.49 is this session** (`c4003270`), the remainder
being a previous session's tail and capped inter-session gaps. As with every row it **excludes its
own close-out**, so read it as "at least".

⚠️ **This row's `Out` is low for the amount of code changed** — 208k against 76.0M in — and that is
the shape of a step whose expensive part was *reading to decide*: a 26-request probe, a full
`mvn clean verify` run four times, and two rounds of measuring the guard population. The four fixes
themselves are small diffs.

**Item 3 is closed as stale.** It asked for an owner decision between a real search endpoint and
relabelling the products filter box. **That decision was made and the work delivered by S1** on
2026-08-01 — `?search=` exists on seven routes, the box sends it, and `sku=`/`ean=` stay exact for
scanners. `HISTORY.md` still listed it as *"needs an owner decision first"*; the two records
disagreed and `HISTORY.md` was the stale one.

**ᵘ² U2a — split `PROGRESS.md` / `HISTORY.md`. 🟢 DONE 2026-08-06.** `PROGRESS.md` was **9,577 lines,
811 KB, ~200k tokens**, and the first file every session read. It is now **1,396 lines**;
`HISTORY.md` is **8,339**, append-only, indexed by step id, with a header stating it is **not
authoritative for current state**.

⭐ **The size was not the defect, and the boundary is not chronological.** Phase 0 found **live status
in eight places inside one file**, two opposite orderings with no marked seam, and a section headed
*"Next action — read this first"* sitting at **line 8305 of 9,577** whose own subheading was stale by
nine steps. **The boundary is what a sentence CLAIMS, not when it was written:** a section is live if
a future session must act on it — an open verdict, an unmet obligation, a named trigger, or a
specification a step adopts — and historical if it records what a past session did. All 56 `##`
sections were verdicted against that rule and approved individually before anything moved.

⚠️ **Three things it deliberately did NOT do**, each with a row of its own rather than a note:
**U2b** carries the drift guards; **U4** carries the dated-figure sweep; and the **four buried
obligations it surfaced** became F5b, W1c, R1c and 8c.

📌 **`git log --follow docs/HISTORY.md` reaches all 97 pre-split commits** — the rename was committed
on its own, with no content change, because git computes rename detection from a delete/add pair and
a rename-plus-edit is not one. **The accepted cost: `--follow` on `PROGRESS.md` starts at U2a.**
Exactly one of the two files can inherit the history.

**ᵘ²ᵇ U2b — the split's drift guards. 🔴 Next after R4's close-out, and NOT optional.** Three tests
plus a CI change: **(1)** a step id has a `##` section in **exactly one** of the two files;
**(2)** every `PROGRESS.md`/`HISTORY.md` reference names a file that exists; **(3)** `HISTORY.md`'s
*not authoritative* header is present; **(4)** **exactly one** table matches `| Step | … | Status |`.

⚠️ **(5) is the one that makes the others worth having: neither CI workflow triggers on `docs/*.md`.**
`frontend.yml` watches `frontend/**` and `docs/api/openapi.json`; `backend.yml` watches `backend/**`.
**A docs-only edit — precisely the change these guards exist for — would not run them.** Without the
path change the first four are decoration.

⭐ **R4's close-out is guard (1)'s positive control, executed for real rather than against a fixture** —
R4 is the first step to cross the PROGRESS → HISTORY boundary after the guard exists.

**ᵘ⁴ U4 — the dated-figure sweep. ⚪ Deferred by the owner 2026-08-06. RE-PRICE IT; DO NOT INHERIT ITS
JUSTIFICATION.** Per-step route and test counts inside `HISTORY.md` are correct in their step's
context and wrong lifted out; U1 date-stamped the headline ones and left the rest.

⚠️ **`CLAUDE.md` and this footnote both used to assign this to U2, and U2's own checklist never
contained it** — §*An approved proposal is a checklist* failing one level up. Both now point here.

⭐ **The split changed the sweep's PRICE, not merely its timing.** `HISTORY.md`'s header states the
file is not authoritative for current state, so **every figure inside it is already framed by its
container** — which is most of what the sweep was for. And the sweep **edits** historical entries,
which is why it could not share a session with a split whose safety property was that it changed no
content.

**ᶠ⁵ᵇ F5b — `ORDER BY … COLLATE "el-GR-x-icu"` on `DOCUMENT_NUMBER`.** ⚪ **Deferred out of F5 as its
sub-part B.4; given a row by U2a, 2026-08-06.** Conditional on **whether a real Prosvasis Go document
number carries Greek letters — the owner's check, still outstanding.** The two collations agree on
Latin document numbers, which is the whole basis of the deferral. ⚠️ **A Spring Data `Sort` cannot
express `COLLATE`**, so applying it means leaving the `Pageable`-driven path for that one property;
the note saying where is at `SalesInvoiceServiceImpl.SORTABLE`.

⚠️ **It had NO row until U2a, and the primer asserted that it did** — see ʷ below. A second record
describing a row that does not exist is worse than silence, because it ends the search.

**ʷ¹ᶜ W1c — W1's two consumer clean-ups.** ⚪ **Marked *Queued out of W1* on 2026-08-04 and tracked
nowhere; given a row by U2a.** `CustomerView.systemRecord():Optional<CustomerSystemKey>` reads as if
it returns the key while the wire carries a boolean from `isSystemRecord()` — `AccountView.systemKeyIfAny()`
is this codebase's own idiom for the fix. And the settings screen still computes `configured` from
`value !== ''` rather than the now-documented `unset`.

**ʳ¹ᶜ R1c — Fees / *Έξοδα και κρατήσεις*.** ⚪ **Cut from R1 as decision A — *"unscheduled, not
forgotten"*, with no row to be unforgotten in; given one by U2a.** ⚠️ Likely a **generalisation of
`ChargeType` rather than a sibling**: *Delivery* and *COD fee* already exist as `ChargeType` rows, and
a second table would be two records of one thing. **The question that decides it:** does Go's
*Έξοδα και κρατήσεις* list contain those same two rows? If yes, `ChargeType` is what changes.

**⁸ᶜ 8c — `NewPurchaseInvoiceLine` is a discriminated union modelled as a flat record.** ⚪ **Recorded
by 8a as design item H.2; given a row by U2a.** Five components of which **at most three can ever be
present**, selected by `type`. **No `required` list can express that** — OpenAPI needs `oneOf` with a
discriminator, and the generated TypeScript would then be two types rather than one with five optional
fields. `@ConditionallyMandatory` keeps the contract incomplete rather than self-contradictory, which
is correct *for now*. ⚠️ **Named trigger: before a screen binds this record — which is F6.**

**ᵘ³ U3 — eleven design decisions written into the repository** (2026-08-03). **Documentation and
governance only: no production code, no schema, no migration, no test changed.** Eleven decisions had
been settled in a design conversation and existed **nowhere in this repository** — the failure
`CLAUDE.md` §*A decision reached in a design conversation gets the same close-out discipline as a
build step* was added to prevent, and the same shape that left *"F5 is next"* standing in four
documents after the owner had decided otherwise.

**What it changed here:** D4 rewritten to its remaining half (ᵈ⁴); D1 and D3 given their content
(ᵈ¹, ᵈ³); D5 given its model (ᵛ); M0 split into M0a and M0b (ᵐ⁰); D2 gated to step 19 (ᵗ); the Woo
one-time load separated from the Woo adapter and given its own row (ʷᵒᵒ); voucher creation modes
recorded at step 21 (ᵃᶜˢ); the per-order shipping address moved to step 22 (ˢᵒᶠ); **the shared
before-24 gate recorded as a decision in its own right**; and the build-script recommendation filed as
a cross-cutting obligation (ᵇˢ).

⚠️ **Nothing ⚪ was promoted or reordered beyond the four decided placements**, and the
D1/D3-versus-F5 question is in *Open decisions* stated as a trade rather than resolved. Full
reasoning, the four prompt-versus-repository discrepancies it reported, and the two things the
repository already said that sharpened D4 and D5 are in `HISTORY.md` under *U3*.

**Measured, per the method below** — window `fadcddd` (R1a's close-out, 2026-08-03 20:44) to this
session's commit. **127 events, 0.242 h active against 0.263 h wall clock, 90k out, 13.0M in.
Recorded as 0.2.** Wall and active are nearly equal, which is what a single uninterrupted sitting
looks like; 6 of the 127 events are the previous session's tail. As with every row it **excludes its
own close-out**, so read it as "at least" — and for a doc-only session that exclusion is a larger
share of the total than usual, because the close-out *is* most of the writing.

⚠️ **`In` is 13.0M against 90k `Out`, the most lopsided ratio of any row here**, and that is the shape
of a session that read four long governance documents and several `PROGRESS.md` sections repeatedly to
produce comparatively little text. Recorded without adjustment; it is data about what a decision-
recording session costs.

**ᵇˢ One build script.** ⚪ **A recommendation recorded by U3, unscheduled and deliberately not
built.** The stale-artefact family has four members, all in `CLAUDE.md`: a container serving an old
jar; annotations reverted with the build error piped away; `mvn -pl app` without `-am`; an aborted
`install` answering from stale jars. All four reduce to *the thing that answered was not the thing
under test* — **and it keeps happening because the rule is a convention.** The proposal is to make the
safe path the easy path: one script that sets `pipefail`, always builds with `-am`, never truncates
output, and is what `CLAUDE.md` tells sessions to invoke, so the mistake requires **deliberately not
using the provided tool.** ⚠️ **The reasoning is the durable part: nothing in this repository can
guard a session's shell habits** — no ArchUnit rule, no test and no CI job sees how a command was
typed — **so the only lever is the default invocation.**

**ʳ R1 — document reference data, backend.** Scheduled before F5 because F5's document model depends on
it. Covers: sales and purchase document types (two tables, seeded from the official AADE list, users may
not author rows or behaviour flags); sales and purchase document series; delivery methods; the myDATA
payment-method code as an attribute of the **existing** settlement-method concept (`SettlementMethod`
already exists), not a parallel entity; fees extending charge types; the myDATA issuer branch number;
and a stored marker for which spec version the seeded codifications correspond to.

⚠️ **R1 references the existing sales channel; it must not create one.** `SalesChannel` is an enum
(`STORE_AND_PHONE`, `ECOMMERCE`, `SKROUTZ`) and `sales_invoice.channel` is a `NOT NULL` column with a
CHECK, both since step 9. A series names a channel; it does not define the concept.

⚠️ **Novocore never obtains a ΜΑΡΚ itself.** Legal issuance always runs through an external
transmission path — Prosvasis Go today, a certified Πάροχος at step 40 — and **that does not change in
any phase**. What changes at step 40 is narrower: Novocore begins allocating the series number and
composing the document itself, transmitting via the Πάροχος adapter instead of handing the job to Go.
**Numbers are recorded, never generated, UNTIL step 40.** Sequence and gap-prevention machinery belongs
there and nowhere earlier. **Terminology rule: no operation, class, method or route may be named
"issue" or "issuance"** — see `CLAUDE.md`; the wrong name has already caused one design
misunderstanding and left one standing violation, now fixed.

⚠️ **ΜΑΡΚ, UID, QR URL and transmission status are core fields, not adapter data** (ADR 0016). This
looks like a violation of "external reference IDs never live on core records" and is not one: Go's
internal document id is adapter data, but the ΜΑΡΚ is a statutory identifier that legally identifies
Novocore's own document and survives Go being replaced.

⚠️ **Document behaviour varies by myDATA type.** ΑΛΠ and ΤΠΔΑ combine sale and transport, so **stock
moves**; a plain Τιμολόγιο is purely sales and **does not reduce stock**. This business issues both
routinely. Until 18b exists, such a document is recorded, the ledger posts, stock is left untouched,
and the document sits in a visible, **queryable** "stock not yet moved" state. **Stock figures are
therefore incomplete for a routine share of real sales.** Loud, measurable, and not a rounding concern.

⚠️ **CORRECTED 2026-08-03 (R1a): document types are NOT seed-only. There are two layers.** This
paragraph used to say they were, on the `VatExemptionReason` model. **The owner's real Prosvasis Go
configuration disproved it** — Go's type numbers are Go's internal ids (adapter data, rule 2), six of
his nineteen types have **no AADE invoice type at all**, and he has stated that types and series must
be user-creatable because more will be needed.

- **`aade_invoice_type`** — all 55 XSD `InvoiceType` values, group from annex 8.1 as a column.
  **Seed-only; the statutory-codification contract applies here and only here.**
- **`sales_document_type` / `purchase_document_type`** — the business's own lists, **user-creatable,
  full CRUD**, with a **nullable** FK to `aade_invoice_type`. **R1a ships them EMPTY**; the owner
  creates his own through R2's screens, choosing each AADE type himself rather than having one
  inferred.

Reasoning in full in `HISTORY.md` under *Why the model changed*; the governing statement is
`CLAUDE.md` §*The document model*, item 5.

**ʳᵇ R1b — document reference data, behavioural. ✅ DONE 2026-08-04.** ⚠️ **The approved checklist
said "`documentType` becomes mandatory on `NewSalesInvoice`" and that could not be built literally** —
`sales_invoice` has `series_id` and **no `document_type_id` column**, and a series carries a
`NOT NULL` document type. Two independently settable references could disagree about what kind of
document a row is, which is the same defect the channel rule exists to prevent. **So there is ONE new
mandatory component, `seriesId`, and the document type is mandatory THROUGH it.**

What landed: **1.** `seriesId` mandatory on `NewSalesInvoice` (`Required.field`), `channel` removed
from it entirely. **2.** `SalesInvoiceServiceImpl` branches on the series' document type's
`affectsStock` before `consumeStock` — ⚠️ **silently**: a non-moving type creates **no
`stock_consumption` row at all**, no marker, no warning, and the source CHECK was not widened.
**3.** Channel derived from the series; **F5 therefore has no channel field**. **4.** Three refusals
in `compute(...)` — a channel-less series, an inactive series, and an inactive document type — so a
*preview* refuses what a *record* would. **5.** `reverse()` carries the series. **6.** `V33`, comment
only, writing at the column why `series_id` stays nullable while the service requires it.

⚠️ **`sales_invoice.channel` was NOT relaxed and `series_id` was NOT made `NOT NULL`.** The first is a
refusal that holds R3's question open; the second would mean backfilling a series nobody authored, and
**whether migrated history carries a series is step 24's question** — see ᵐ⁰ and step 24.

⭐ **Two defects found, and both were invisible before this step.** A test written to *document* the
new behaviour found that the service's duplicate-number check was still **global** while `V32` had
made the database key **per-series** — the two agreed only because every row's series was null, so
R1a's C.6 key would have been unreachable, enforced by nothing. And the negative control for the stock
branch **reported PASS while running nothing**, because a `Class#a+b` failsafe selector matched no
tests and `failIfNoSpecifiedTests=false` turned that into a green build. Both are written up in
`CLAUDE.md`.

**ʷ¹ W1 — a serialised record's wire shape must equal its documented shape.** ⚠️ **Scoped out of R1b
on 2026-08-04 after measuring it, and the measurement is the point.** The rule: **the properties
Jackson writes must equal the properties `OpenApiSchema` documents** — two honest ways to comply,
delete the accessor or document it.

📊 **Measured 2026-08-04 by a throwaway probe over 203 records: 46 serialise beyond their components,
and 32 of those are schemas on the committed API surface, shipping 66 properties the spec does not
document.** Confirmed behaviourally on `SalesDocumentTypeView`, whose real serialisation emits
`"draft":false`. **None is a live defect** — nothing sets `additionalProperties: false`, the generated
TypeScript simply lacks the fields — **but the contract lies about 32 schemas**, which is the silent
half `CLAUDE.md` predicted nothing would ever report. A rule 32 committed schemas fail cannot land
green, so it is its own step rather than a baseline.

⚠️ **THE MECHANISM IS JACKSON, NOT ASM.** 8a needed ASM for *argument attribution*; here the question
is *what would Jackson call this*, and the only correct oracle is Jackson —
`ObjectMapper.acceptJsonFormatVisitor` against `Class.getRecordComponents()`. **The proof:** a control
shaped like R1a's defect showed Jackson does **not** publish `issuedByUs` — it strips the `is` prefix
and publishes **`suedByUs`**. Nobody derives that by reading. ⭐ It also needs **no exemption list**:
`equals`/`hashCode`/`toString`, static factories, the compact constructor and the `…IfAny()` idiom are
all invisible to Jackson by construction, which independently confirms `CLAUDE.md`'s claim that
`…IfAny()` is safe — and for a better reason than the one recorded.

⭐ **EVALUATE THE GENERATOR ROUTE FIRST.** Teaching `OpenApiSchema` to describe what Jackson serialises
documents all 66 in **one change** instead of editing 66 records, and the rule then verifies the
generator rather than policing records. ⚠️ Not free: 8a's rule makes primitives `required`, so 66 new
required booleans means fixture reconciliation across 32 schemas. **Weigh it at scoping.** The rule
belongs in the **app module against the real Boot-configured mapper bean**.

**✅ W1 IS DONE — 2026-08-04. The generator route was taken, and it cost 14 fixture edits.** Spec
**+58 properties across 27 response schemas**, no operation and no schema-count change; backend
**1,480** tests (`BUILD SUCCESS`), frontend **368** green, and every one of the 14 `tsc` errors was a
**test fixture** rather than application source.

⚠️ **The step corrected two premises, and the second is worth more than the fix.**
**1.** The 32/66 figure did **not** predate R1a/R1b — it was measured *in* R1b's Phase 0, so what the
re-measure establishes is that **R2 and R2b added nothing** (⭐ R2's X.6 discipline held; there is no
67th). **2.** `CLAUDE.md` said *"Jackson serialises a record's no-arg public accessors"*, and that is
**false**: Jackson publishes **bean getters**. Of **222** non-component accessors on this surface,
**79** are `is*` and Jackson publishes **66** — **153 are invisible to it**, including every
`…IfAny()`, which is a far better reason for that idiom's safety than the one recorded.

⚠️ **REQUEST records are deliberately NOT given derived properties, and it is one rule rather than
two behaviours.** A request record is **deserialised through the canonical constructor**, which sees
exactly the components, and **is never serialised at all** — so a derived property there describes a
write that never happens. `OpenItemRef.isCustomerSide()` was **deleted** (zero references anywhere in
compiled code) and the both-directions case is refused at build time, with the both-directions set
**pinned non-empty as a positive control**.

⭐ **Building it found what reading could not:** a name-based type lookup made the generator
**non-deterministic**, because `CustomerView` has both `isSystemRecord():boolean` and
`systemRecord():Optional<CustomerSystemKey>`. **The type comes from Jackson's visitor.** Measured at
**1.5 h active / 356k output**, covering Part 1 and Part 2 — short, as every close-out figure is,
because the close-out is not yet in the transcript that measures it.

**ʳ²ᵇ R2b — what R2's live leg found, 2026-08-04. 🟢 DONE.** Five sections, two of which started
from a wrong premise. ⚠️ **The stale-list defect was OLDER than R2 and sat in all THIRTEEN create
forms** — R2 copied the pattern faithfully, including the defect — and it **heals itself in 30
seconds**, which is why seven screens shipped with it. Fixed globally with a structural guard.
⚠️ **There was NO server-side check that a series' document type is usable**: the create screen's
picker was the only guard, the edit screen had none, and an adapter or a direct call had none at
all — a path where **the screen was load-bearing and nothing behind it was**. Now refused on create
*and* change, both sides, with **draft tested before inactive** because a draft is always inactive.

✅ **`sort_code` on four tables (`V34`), INTEGER and NOT NULL.** ⚠️ The owner overruled a nullable
proposal, and the reason does not transfer elsewhere: `sales_invoice.series_id` stayed nullable
because backfilling would **invent a series nobody authored**, which is a false statement about a
legal document — whereas **a sort code has no truth value**, so an initial backfill fabricates
nothing. Integer because a text sort puts `1000` before `900`.

⭐ **Payment methods had no screen because of a SCOPING ERROR rather than an implementation gap**
(`V35`). *"`SettlementMethod` is an enum, so nothing to edit"* was carried into R2's scope, while
delivery methods — a near-identical row in the same specification — got full CRUD. ⚠️ **The brief's
premise was also wrong**: the myDATA codes have been on the enum since it was written, so they are
read from it and **not stored**, and a drift test holds table and enum together in both directions.

Spec **237 → 247 operations**. ⚠️ **Measured at 1.3 h active / 309k output — this session's total
minus R2's own 1.9 h / 447k, so it also covers the live-leg recording commit `bf3f950`** — and, like
every close-out figure, short because the close-out is not yet in the transcript that measures it.

**ʳ² R2 — document reference data, screens. 🟢 DONE 2026-08-04, and it grew a backend sub-part.**
⚠️ **Seven new routes (230 → 237 operations) that the step was not scoped for**: a series'
`abbreviation`, `documentTypeId` and `getsMark`, and a delivery method's `abbreviation`, are now
**editable while the row is unused and frozen once it is used**. Before R2 none of them had a write
route on any installation, so a typo in a hand-authored Greek series name had no correction path —
deactivate-and-recreate burns the abbreviation permanently, because `…_abbreviation_unique` is not
partial. ⚠️ **On the purchase series and delivery methods the freeze CANNOT FIRE** (nothing in the
schema references them until F6 and 18b); `DocumentReferenceGraphIT` makes that a red build rather
than a silent gap. **S.4 closed as done-by-correction — no seed mechanism was built.** Four premises
were corrected in Phase 0; the AADE picker is **34 options, not 55**, and
`transformableIntoSeries` is **singular**. Measured at **1.9 h active, 447k output** — short,
because the close-out is not yet in the transcript that measures it.

*(The paragraph below is R2's original scoping note, kept for the reasoning it records.)*

**ʳ² R2 — document reference data, screens.** ⚠️ **FULL CRUD, not the read-plus-activate shape F4
built for VAT classes.** The owner authors these rows — he creates his 15 sales and 4 purchase
document types and their series here, **choosing each AADE invoice type himself** rather than having
one inferred. The tables ship empty from R1a precisely so that he does. ⚠️ **Needs a live browser
leg**, and therefore an app-image rebuild before it is handed over. **R2 is also where a dev seed of
a few types and series belongs** — R1a deferred it (S.4) because nothing without screens needs one.
 🎯 Like every step that adds a search box, R2 **adopts
its row from the 16-row search target list in `PROGRESS.md`** rather than re-deriving a narrower one —
see `CLAUDE.md`, *"reconcile against the fullest list"*. The same applies to **F5–F9**, whose rows are
already written there, including entities that do not exist yet. ⚠️ Those rows share a trap: a
document's search fields include the *counterparty's* name, VAT, code and alias, which live on another
table, and `TextSearch`'s dotted path produces an **inner join** — so a document with no counterparty
would drop out of its own list.

**ˢ R3 — self-supply posting paths.** 📍 **Confirmed 2026-08-04 as NOT SCHEDULABLE, and therefore
outside the sequence: it starts when the accountant answers, and no earlier.** ⚠️ **That is the one
row above whose gate nothing in this repository can move**, and it is inside the before-step-24
cluster — so *unschedulable* must not be read as *unimportant*. `Στοιχείο Αυτοπαράδοσης`, used for internal consumption and for
moving an item from inventory into fixed assets. The customer is the issuer, so it needs a protected
self-customer record on the pattern of the retail walk-in — **excluded from customer sales, revenue and
margin reporting**, since revenue is recognised at cost. The line price derives from **FIFO lot cost,
not the price list**, which couples pricing to lot selection — true nowhere else in the system. VAT is
deductible and not capitalised. The accounts are an accountant question and must be refused rather than
guessed.

**ᵗ D2 — product categories.** ⚠️ **Not two flat columns and not an enum.** Three levels deep, with a
product belonging to several categories at once — a self-referencing category table plus a join table.
Recorded so the requirement is not re-derived narrower. **Nothing exists, not even the schema**;
confirmed live 2026-08-02, and `V29` carries a header saying so explicitly.

⚠️ **Its gate is step 19, not step 24** (U3, 2026-08-03) — the Woo adapter syncs categories, and the
one-time load runs before that. **Woo's own category structure is hierarchical and multi-membership,
which is the same shape D2 already requires**, so the load does not need a reshaping step. **The
owner confirms Woo's categories are exactly the ones wanted: they import AS-IS, with no curation
during the load.** See ʷᵒᵒ for why the load is not the adapter.

**ᵈ¹ D1 — supplier/customer codes, and the alias.** 📍 **Placed 2026-08-04: after F5, in one block with
D3, D4 and D5.** ⚠️ **D1 and D3 are paired because they are the same two entities and the same two
screens** — codes, the supplier alias and both parties' addresses all land on Customers and Suppliers,
so doing them apart reopens those two screens twice. Content decided 2026-08-03 (U3):

- **Codes are for the business's own reference and are NULLABLE.** The id remains the handle; nothing
  in the system depends on a code existing.
- **Supplier has an alias. Customer never does.** ⚠️ **Recorded as a decision, not an oversight** —
  an asymmetry with no argument behind it is the shape S1's reconciliation caught with
  `supplier.vat_number`, so this one has its argument written down.

⚠️ **"Alias" already means something else in this repository and D1 does not resolve that.** Brief
§5's *"alias forward, never rewrite history"* is the **customer merge** mechanism; a supplier alias
here is a **short trading name**. Customer-never-has-one *narrows* the collision — the two senses no
longer land on the same entity — but the word still carries both meanings, and customer merge is
still an open cross-cutting obligation above. **Do not conflate them.** The three fields
(`Supplier.code`, `Supplier.alias`, `Customer.code`) and what each needs beyond a column are itemised
in `PROGRESS.md` under *Queued out of S1*.

**ᵈ³ D3 — addresses. Structured, conditional, and smaller than the row suggests.** 📍 **Placed
2026-08-04: after F5, in one block with D1, D4 and D5 — and specifically PAIRED WITH D1**, because both
land on the Customers and Suppliers screens and splitting them means reopening those screens twice.
Content decided 2026-08-03 (U3).

**Structured, not free text** — street, number, postcode, city, country as separate fields. Three
concrete reasons: **myDATA requires the counterparty address elements separately** on transmitted
documents; **ACS needs the same** for shipping labels; and **the data already exists structured in
both Woo and Go**. A free-text field means parsing it back apart later, by hand, on every record.

**Who needs one:** suppliers **always**; customers **who purchase with VAT**. ⚠️ **Retail customer
addresses may be NULL** — Skroutz frequently sends orders with no phone, address or email at all.

⚠️ **Enforced at the DOCUMENT, not at the CUSTOMER**, and the reasoning is recorded because the
opposite looks more natural. When a customer has a VAT number the address is **sourced from AADE or
VIES rather than typed** — and that lookup is **step 28**, far after this work. So a customer-level
constraint would **block record creation for a long stretch** with no way to satisfy it except manual
entry, and would **fail any adapter processing a B2B order that arrives without one**. Document-level
enforcement works whether the address was typed now or fetched later, **the legal requirement is on
the transmitted document anyway**, and it is the shape **`@ConditionallyMandatory` already exists
for** (8a).

⚠️ **D3 shrinks.** Billing and shipping are separate, shipping defaulting to billing — but the
**shipping address is registered at the ORDER**, not on the customer, and affects only the courier
voucher. **The customer entity holds ONE (billing) address**; per-order shipping **moved to step 22**
— see ˢᵒᶠ.

**ᵈ⁴ D4 — internal document numbers. It splits in two, and one half needs nothing built.** 📍 **Placed
2026-08-04: after F5, in one block with D1, D3 and D5 — and specifically PAIRED WITH D5**, because both
are ledger integrity and both are the external accountant's concerns; neither shares a screen with
D1/D3. Content decided 2026-08-03 (U3).

**Half one is already answered and is not D4's.** Sales document numbers are **captured** from Go —
or from a certified Πάροχος in future — after the document has been issued and transmitted, exactly
like the ΜΑΡΚ, UID and QR code. Purchase document numbers are **whatever the supplier issued**:
through myDATA for domestic suppliers once step 29 exists, and the supplier's own reference number
for foreign ones. That is `CLAUDE.md` §*The document model* item 2 already in force.

**Half two is what D4 actually named, and it is all that remains in this row.** Documents **Novocore
itself creates and no external party issues** — manual journal entries, goods receipts, freight
allocations, write-offs. They have no supplier and no Go, so **without a Novocore number they have no
human-facing identifier at all.** *"What is entry 412"* is a question about a manual journal entry,
and today the only answer is a database id.

⚠️ **The distinction that makes this cheap, and it is what stops a reader refusing to build it:**
these are **internal reference numbers, not statutory document numbers.** No legal sequence, no
unbroken requirement, **gaps do not matter.** Simple per-type counters. **None of step 40's machinery
and no conflict with "numbers are recorded, never generated"** — that rule is about documents an
external party issues, and `CLAUDE.md` now carries the carve-out explicitly.

⭐ **Step 7 filed this question already, and it names D4's two open decisions.** `HISTORY.md`,
journal engine: *"**No entry number.** The id is the handle. A human-facing sequential number is a
real thing an accountant asks for and carries a format decision (per-year reset? prefix per source?)
nobody has been asked."* **Those two are D4's to answer.**

**ᵛ D5 — period locking, by a movable LOCK DATE.** 📍 **Placed 2026-08-04: after F5, in one block with
D1, D3 and D4 — and specifically PAIRED WITH D4**, because both are ledger integrity and both are the
external accountant's concerns. Confirmed absent: `V15` states *"NO PERIOD LOCKING
and therefore no lock table (brief §6, explicit)"* and no lock table exists. Without it, entries dated
into a filed period can keep arriving, so the ledger silently drifts away from what the accountant
filed and nothing reports that it happened. Small to build now; awkward to retrofit onto live data.

**The model, decided 2026-08-03 (U3): a single movable lock date, NOT a fiscal-year flag.**
Everything dated on or before it is closed; everything after it is open, and the owner moves it
forward as periods are filed. Two reasons, both load-bearing:

- **The owner will not accept blanket locking** — past records sometimes genuinely need altering, and
  a lock date leaves open exactly what must stay open.
- **It is finer grained than a fiscal year, and Greek VAT is why that matters.** VAT is filed monthly
  or quarterly, so a year-granularity toggle leaves a filed February editable for eleven more months
  — the very drift the lock exists to prevent.

⚠️ **Two properties without which it is decoration:** **only the owner may move it** (a lock anyone
can slide backwards is a suggestion), and **every change is audited** — who, from what date, to what
date, when.

**It blocks two different operations and both are in scope:** editing an existing entry in a closed
period, **and** posting a new entry dated into one.

⚠️ **Consequence, and it is a requirement for whoever builds this rather than an established fact:
reversal dating stops being optional.** If a closed period cannot receive entries, a correction to a
document in one must carry **the correction date**, not the original document's date. **U3 did not
confirm this against the code — it ran none.** ⚠️ **Confirming what reversals do today is D5's first
task.**

⚠️ **Two statements elsewhere in `HISTORY.md` are built on this feature's absence and change meaning
the day it exists.** Step 3: *"There is no delete, only `deactivate`. **With no period locking** there
is no point at which an account is safely finished with."* Step 7: `entry_date` *"has a floor of
2000-01-01 and **no upper bound**, because a forward-dated accrual is legitimate and **there is no
period locking**."* Revisit both rather than rediscovering them — and note the second is only half
answered by a lock date, which bounds postings from **below**.

**ᵐ⁰ M0 — it splits, and the first half is not an import.** Decided 2026-08-03 (U3).

**M0a — a mapping exercise. No code, and unblocked now.** ⭐ **Novocore's chart of accounts was built
from scratch, not copied from Manager** — 65 accounts across 13 groups, designed from the brief, with
`AccountSystemKey` on the eleven the posting rules must locate (step 3's record). So **the real test
is not an import**: does every account in Manager map to a Novocore account, and **which do not?**
That is a spreadsheet and a session, and **it tests the most load-bearing part of the model.**

⚠️ **Its TARGET changed on 2026-08-06 and the row did not.** The owner decided the **official Greek
chart is used directly** with a display alias, and no business chart on top — see ᶜ¹. So M0a maps
Manager's accounts onto **the official chart**, not onto a chart of our own design. **Neither
scheduled nor blocked by that decision; only the answer side of the mapping moved.**

**M0b — a real year of transactions.** Real chart plus one real year, imported into the current core
to find out whether the model fits data nobody has examined yet. ⚠️ **Waits until D1, D3 and D4
exist**, or it imports into a model already known to be incomplete.

Migration is where data models die, and step 24 is twenty steps away. This is the cheapest,
highest-information test available and it gets cheaper the sooner it runs. ⚠️ **Why NOT after F11,
kept because it was the owner's initial instinct:** M0 exists to find gaps **while fixing them is
still free** — run it after eleven screens exist and every finding costs screens too. **And it does
not need F11**: it is an import, not data entry.

**ᶜ¹ C1 — the official Greek chart is used DIRECTLY, with a display alias. Decided by the owner
2026-08-06. ⚪ Recorded, NOT scoped, and deliberately not built.**

**The decision, in one sentence: Novocore uses the official Greek chart of accounts as its chart,
with an ALIAS on each account for display, and there is NO separate business chart mapped onto the
official one.** One layer, not two.

**The owner's reasoning, recorded because it is the part no reading of the code supplies:**

- **The only thing a second, business-owned chart genuinely buys is many-to-one granularity** —
  several business lines rolling up to one statutory account. **That need is better served by the
  product model** — product categories and lines that name products — **than by multiplying the
  chart.**
- ⚠️ **One layer is also the more REVERSIBLE choice, and this is the load-bearing half.** Adding a
  second layer later is **additive**. Collapsing two into one is a **merge**, and a merge loses
  history. When the two options are not symmetric, take the one that can still be undone.

**What exists today, measured 2026-08-06 rather than remembered:**

| Fact | Evidence |
|---|---|
| ⚠️ **There is NO alias field on an account** | `Account` has `code`, `name`, `account_type`, `account_kind`, `sub_ledger_type`, `system_key`, `group_id`, `display_order`, `active`, `expected_to_clear`, `elp_code` — **and nothing else**. `AccountView` carries the same set. **Not built, per the owner's instruction** |
| **The chart is Novocore's own 65 accounts across 13 groups**, built from scratch at step 3 | `V4__chart_of_accounts.sql`; recorded under *Step 3* in `HISTORY.md` |
| **`code` is deliberately blank** and **`elp_code` is null on every row** | Step 3's decision — both were to come from the accountant. `AccountSystemKey` exists precisely because neither is usable as a handle |

⚠️ **An observation this raises, flagged for the owner and NOT acted on:** the chart today carries
**two** code columns — `code` (blank) and `elp_code` (null) — which is itself the two-layer shape this
decision rejects, one field down. If the official chart becomes *the* chart, whether those two collapse
into one is a real question and it is **not answered here**. Recorded rather than decided.

⚠️ **M0a's target changes with this, and that is stated at ᵐ⁰ too.** M0a asks *"does every Manager
account map to a Novocore account?"* — under this decision the answer side of that mapping is **the
official Greek chart**, not a chart of our own design. **M0a is not thereby scheduled, blocked or
cancelled**; only its target moved.

**ᶜ² C2 — the cash limit is TWO thresholds, and the discriminator is not in the model. Raised by R4's
Phase 0, 2026-08-06. ⚪ Recorded, NOT scoped, and deliberately not built inside R4.**

**The owner's correction:** the Greek legal cash limit is **€500 INCLUDING VAT for retail sales** and
**€500 NET plus VAT for VAT-registered customers.** Two thresholds, selected by who the counterparty
is.

**The authoritative signal is the DOCUMENT TYPE, decided by the owner** — retail goes out as **ΑΛΠ**,
B2B as **ΤΠΔΑ**. ⚠️ **NOT the presence of a customer VAT number**, and the alternative was considered
and rejected: **two signals for one rule is the shape A.6 exists to remove**, and a customer with a
VAT number can still be sold to at retail.

**What Phase 0 measured, so C2 does not re-derive it:**

- ✅ **The shipped guard compares GROSS.** `requireWithinCashLimit(method, receivable)` where
  `receivable = Σ line.gross() + rounding` and `gross() = net.plus(vat)`, refusing at `>=` against
  `CASH_PAYMENT_LIMIT`. **So it is CORRECT FOR RETAIL and merely absent for B2B** — B2B is
  *over*-guarded at €500 gross where the law allows €500 net (€620 at 24%). Refusing legal sales is
  the safe direction and is still wrong.
- ✅ **The guard can reach the document type.** `compute(...)` resolves the series first and
  `SeriesContext` carries `SalesDocumentTypeView`. The type is in scope; the guard does not take it.
- 🛑 **NOTHING IN THE MODEL RELIABLY DISTINGUISHES ΑΛΠ FROM ΤΠΔΑ, WHICH IS WHY THIS IS A ROW AND NOT
  A SUB-PART.** The nearest candidate is `AadeInvoiceGroup` — ΑΛΠ is `ISSUER_UNMATCHED`, ΤΠΔΑ is
  `ISSUER_MATCHED`, seeded on all 55 types — and it fails three ways: the FK from
  `sales_document_type` is **nullable**; `SalesDocumentTypeView` **does not expose the group**; and
  ⚠️ **matched/unmatched is a myDATA REPORTING distinction being borrowed for a STATUTORY CASH-LAW
  purpose, which nobody has confirmed is the same line.** ⚠️ **No field was added to make it work.**
- ❌ **No test exercises the limit against a B2B document**, and there is nothing to exercise: one
  test touches the rule at all (`SalesInvoiceIT`, `"legal cash limit"`).

⚠️ **What C2 must decide before it can build anything:** whether the retail/B2B line is the AADE
group, an explicit flag on `sales_document_type`, or something else — and **whether that is a
statutory question for the accountant** rather than a modelling one. **R4 must not pre-empt it.**

**ᶠ⁶ F6 — Purchase Invoice + Goods Receipt. ⚠️ Two things R1b left it, recorded 2026-08-04 so they
are not rediscovered.**

- **`purchase_document_type` becomes mandatory HERE, not in R1b.** R1b was sales-only, deliberately:
  the purchase side already separates the document from the stock movement through Goods Receipt and
  GR/IR, `NewPurchaseInvoice` has no series, type or channel, and including it would have roughly
  doubled R1b's blast radius for no behaviour F6 does not need anyway. **Confirmed against the code
  in R1b's Phase 0, not assumed.**
- ⚠️ **The one inconsistency this leaves, stated plainly:** after R1b, **`sales_document_type.affects_stock`
  is READ and `purchase_document_type.affects_stock` is NOT** — while `V31` lines 314–321 carry the
  strongest justification that column has anywhere (the `2062` ΤΔΑΑ / `2041` Δελτίο Παραλαβής pair, a
  purchase document bringing stock in with no payable behind it). **A reader arriving at F6 will find
  a column documented as load-bearing that nothing loads. It is waiting for F6, not forgotten.**
- 📌 **`NewPurchaseInvoice` uses `Objects.requireNonNull` and `IllegalArgumentException` for caller
  mistakes**, where the codebase now prescribes `Required.field`. **Not a live defect** — Jackson wraps
  it and `WebExceptionHandler.unreadableBody` answers a 4xx naming the field, so no guard fires — but
  it is a different message from the `Required.field` route on the same kind of record. **Recorded, not
  fixed:** touching it in R1b would have breached the sales-only boundary for a tidy. On the backend
  queue.

**ˢᵉʳ Step 24 also owns a question R1b deliberately did not answer.** `sales_invoice.series_id` is
**nullable**, and the service — not the column — is what requires it. Making it `NOT NULL` would mean
backfilling every pre-R1b invoice with a series **nobody authored**, which is exactly the fabrication
the empty seed exists to prevent. **Whether migrated history carries a series is this step's decision**
(with M0b), and if it answers it, tightening the column becomes possible then. The reason is written at
the column in `V33` as a deliberate departure from A.7.

**ʷ F5 — Sales Invoice + Credit Note.** ⚠️ **F5 HAS NO CHANNEL FIELD, and that is settled rather than
an omission (R1b, 2026-08-04).** A sales invoice's channel comes from its **series** — ΑΛΠW is the web
series, so an invoice in it is a web sale by definition rather than by someone remembering to tick a
box. `NewSalesInvoice` has no `channel` component, so there is nothing for a form to bind. What F5
**does** need is a **series picker**, and it is mandatory: the series supplies the channel *and* the
document type, and the document type decides whether recording the sale moves stock at all. ⚠️ A
channel-less series (self-supply) is **refused** by the backend with a message pointing at R3 — a
screen should not offer one, but must render the refusal if it does.

Decides the document interaction pattern F6–F8 reuse. ⚠️ **But
see the open decision above**: since documents arrive already issued, F5 before step 18 is a
data-entry screen for documents created elsewhere. Also carries the **transformation requirement** — an
employee correcting a mistake must transform a document into the correct series or a return document in
one action, with series, products and customer auto-filled, **never re-keyed**; same flow for a returned
or cancelled order. **That behaviour needs the Go adapter**; R1 stores only which series a series may
transform into.

**ʳ⁴ R4 — payment methods are a BUSINESS list that references an AADE codification, not a statutory
list.** ⚠️ **This is a REQUIREMENT CORRECTION, not a defect** — the screens R2b built do exactly what
R2b's rows asked; the rows asked for the wrong model. Found by the owner's live leg of **2026-08-05**
(L.10, L.11), and it is **R1a's two-layer correction repeating one entity over**: what `CLAUDE.md` §5
says about `aade_invoice_type` versus `sales_document_type` is the same sentence about
`payment_method`.

**What the owner requires:** the list **starts empty** with no seeded rows and the user creates them
freely; creating one **selects the AADE payment-method article**, which supplies the myDATA code;
⚠️ creating one **also chooses the ledger account it settles to** — *two POS terminals can share AADE
code 7 and land in different bank accounts, and the AADE article cannot tell you which*; and **all
fields stay editable until the method has been used**, on the freeze pattern R2 already built for a
series' abbreviation, document type and ΜΑΡΚ flag.

⚠️ **Three things it contradicts, recorded at the row because meeting only one of them re-derives the
old answer:** R2b §4.1 decided *not* to store the myDATA code, exposing it from the `SettlementMethod`
enum — **a user-created row cannot inherit a code from an enum it is not in**, so the code moves onto
the row; `SettlementMethod` is a **Java enum on `NewSalesInvoice`**, so **this changes the sales
invoice request contract** to an FK; and R2b §4.7's argument *against* creation — *"it needs an
`AccountSystemKey` and two behaviour flags"* — **is not refuted, it is the specification of the create
form.** 📌 The eight seeded abbreviations (ΜΕΤΡ, ΚΑΡΤ, ΤΡΑΠ, ΕΠΙΤ, ΑΝΤΙΚ, SKRZ, PPAL, STRP) were
**invented, not chosen by the owner** — an empty list removes that rather than making them editable.

**Why the row sits here: `F5 < R4 < F6`, and both halves are cost.** It changes a contract, so the
longer it waits the more is built on the enum; and **purchase documents settle too**, so F6 should be
built against the corrected model rather than reopened. ✅ **It does not block finishing F5** — F5's
record form is a **test harness by decision** (`CLAUDE.md` §1b), so revising it later is expected.
⚠️ **F5 must not pre-empt any of it.** 📌 Its position relative to **N1 and the D-block was not
specified by the owner**; immediately-after-F5 is this file's reading of the *"the longer it waits"*
argument, not a fifth requirement.

🟡 **CURRENT as of 2026-08-06.** R2c was deferred out of the sequence and R4's Phase 0 was
commissioned in the same instruction, so it is now first. **The gate did not change** — before F6 is
still the binding constraint, and it was binding before the promotion.

**➕ R2c's 2b is attached here, and the reason is not convenience.** The sort code is **absent from
the sales and purchase SERIES edit form** while document types allow editing it. ⭐ **R4 rebuilds
payment methods around exactly one question — which fields are editable, and until when** — which is
the same question R2b's §3.4 answered for the sort code when it exempted the field from the in-use
freeze. **The code is open and the reasoning is loaded.** At F11 it would cost somebody re-learning
why the freeze has an exemption. 📌 **The unverified series-ordering check rides with it** — the owner
confirmed sort-code ordering on the **document type** lists and said nothing about the **series**
lists.

**➖ A prerequisite R4 might have had is gone, decided 2026-08-06.** R4.3 requires choosing **the
ledger account a payment method settles to**, which raises *which chart is being picked from*. The
owner has settled it: **the official Greek chart is used directly, with a display alias per account,
and there is no separate business chart mapped onto it** — see ᶜ¹. **So R4's account picker offers
accounts from the one chart that exists**, and nothing in R4 waits on a second layer that will not be
built. ⚠️ **The alias column does NOT exist today** (measured 2026-08-06 against `Account` and
`AccountView` — see ᶜ¹) and **R4 must not add it**: an alias is a chart-of-accounts field, not a
payment-method one.

**ʳ²ᶜ R2c — the sort code is invisible on the lists and unsettable on a series.** Two defects from the
owner's live leg of **2026-08-05**, against R2b's §3. **They are R2b's, not F5's.**

**2a — display only.** The sort code **is not a visible column** on the document type lists, while the
**ordering is correct** (owner-confirmed). So R2b's 3.5 held and only 3.6 — *first list column* — did
not land. 📌 **He confirmed ordering for DOCUMENT TYPES and said nothing about the SERIES lists;
verify those rather than assuming they match.**

⚠️ **2b is the more serious one, and it is not cosmetic.** On **sales and purchase SERIES** the sort
code appears **only on the create form** and is **absent from the edit form**. Document types allow
editing it, **which is why L.9 passed** — the passing path and the broken path are different screens.
**R2b's 3.4 deliberately exempted this field from the in-use freeze** on the stated grounds that
*"reordering is normal"* — so **a value settable only once is unusable for the purpose that argument
assigns it.** And it is on **series**, the picker an employee uses when recording a document, ordered
by exactly this column.

### ⚠️ R2c IS DEFERRED AND SPLIT — the owner, 2026-08-06, later the same day

**It is not core work and the owner does not want it interrupting the core.** The row was therefore
**demoted 🟡 → ⚪ and moved out of the sequence block**, per `CLAUDE.md` §*a sequencing decision
changes the roadmap's ORDER, not a paragraph beside it*. ⚠️ **The demotion IS the decision being
applied, not a side effect of moving the row** — the same distinction this footnote flagged that
morning **in the other direction**, and it is worth stating both times or the record only ever
explains promotions.

**And it was split in the same instruction, so neither half is scheduled as R2c:**

| Half | Where it went | Reason recorded at the destination |
|---|---|---|
| **2a** — the sort code is not a visible column on the lists | **F10**, the display-defects-from-live-legs list under ᵇᵃᵈᵍᵉ | ⚠️ **Cosmetic.** The **ordering is correct**; only the column is missing. F10's list exists for exactly this — a defect the owner saw and chose not to stop for |
| **2b** — the sort code is absent from the **series EDIT form** | **R4** | ⭐ **R4 rebuilds payment methods around WHICH FIELDS ARE EDITABLE AND UNTIL WHEN** — the same question R2b's freeze exemption answered for the sort code. The code is open and the reasoning is loaded. Doing it at F11 would cost somebody re-learning why the freeze has an exemption |
| 📌 The **unverified half of 2a** — the owner confirmed sort-code ordering for **document types** and said nothing about the **series** lists | **R4, with 2b** | It is a *behaviour* check rather than a display one, and R4's 2b work opens the series screens anyway. Splitting it from 2a is deliberate and is stated rather than left implied |

*(This footnote read "No slot is decided" when first written on 2026-08-06, then "A slot is decided —
R2c is next", and now this. All three were true when written; the churn is recorded rather than
overwritten so the sequence of decisions is legible.)*

⚠️ **F11 was nearly used as 2a's home and is the wrong place**, which is worth recording because the
row title invites it: **F11 is "Whole-system UI regression" and nothing anywhere scopes it** — no
brief section, no footnote, no checklist. Parking a known specific defect on an unscoped batched step
is how it stops being tracked.

**ⁿ¹ N1 — a reversed document's number becomes available again.** ⚠️ **The DIRECTION is settled (owner,
2026-08-05); the BUILD is deliberately not F5's.** F5 found three enforcements of document-number
uniqueness disagreeing and measured the consequence over HTTP: reverse an invoice, re-record it under
its own number in the same series, and the server answered **`500`** in Boot's legacy body shape. The
same defect exists twice — `sales_invoice_number_idx` and `credit_note_number_idx`.

**The owner's reasoning, which is the part no reading of the code could supply:** a reversal in
Novocore undoes **Novocore's own mis-recording** of a document Go issued. It is **not** a cancellation
of an issued document — Greek law has no such thing; an error in an issued document is corrected by a
credit invoice. Go's document still exists under its number and must be recorded again correctly,
**so the number has to become available.** The partial unique index is therefore the enforcement that
is wrong; the trigger and the service message are right.

**Why it is its own row rather than a line in F5:** a partial index cannot express *"not reversed"* by
itself, and the fix carries a design question that must not be answered by accident — ⚠️ **a trigger's
`NOT EXISTS` is not a substitute for a unique index under concurrent inserts.** Measured on the live
database with a positive control: session A held an uncommitted duplicate, session B inserted the same
number for the same supplier and was **accepted**. `purchase_invoice` has **no unique index on its
number at all** and is exposed to exactly that today, which is a second thing for this row to settle.
**Whatever replaces the index must keep the concurrency guarantee.**

**Nothing dangerous is left open in the meantime.** F5's A.1b makes the violation answer `422` with a
readable detail, and `DocumentNumberReuseIT` asserts only what holds either way — never a 5xx, always
an RFC 7807 detail — so the day N1 lands, those assertions fail in the safe direction.

📌 **And N1 is itself transitional in the longer run:** once the adapter exists, the correction for a
wrong mirror is a **re-fetch from the source**, not a reversal. Reversal exists because humans
currently type the mirror by hand — see `CLAUDE.md` §1b.

**ˣ X1 — general integration outbox.** The fourth architectural non-negotiable — *a core operation never
waits on an external call* — **has no implementation.** Confirmed 2026-08-02: every `outbox` reference
in the backend is email, backup or attachment, and `backend/adapters` and `backend/modules` contain
**zero Java files**. Step 11 delivered an email outbox: transactional write, background dispatcher,
exponential retry. That is exactly what ten adapters need, and it is email-specific. There is **no**
general integration-event outbox, **no** idempotency keys, **no** replay log, **no** ordering guarantee
— and ten adapters need all four. Under the project's own "one shared service per cross-cutting
concern" rule this should be built once, before the first adapter. For myDATA in particular,
"transmitted twice" is not a theoretical failure mode. **A real gap, not a future nicety.**

⚠️ **Adapter ID-mapping tables have no designed lifecycle either**, and it belongs with X1: external ID
reuse, deleted-then-recreated external records, and two adapters disagreeing about which core record an
external ID resolves to are all unanswered. An adapter-time design item.

### ⭐ X1's FIRST CONCRETE REQUIREMENTS — settled by the owner 2026-08-05. **Record only; do not build**

⚠️ **This also answers a question that was previously left open: X1 was thought unspecifiable before a
real adapter exists. It is not.** Three requirements are fully specified now, and they are **CORE, not
adapter** — they must be settled *before* the adapter is built rather than invented by it.

**The failure they exist to prevent, in the owner's own framing, and it is not a latency problem — a
loading screen does not cover it:**

> Novocore submits an order. Go issues the document. AADE returns a ΜΑΡΚ. **The response to Novocore is
> then lost.** The document **legally exists** and Novocore does not know it. The operator retries, and
> there are now **two legally issued documents for one sale** — correctable only by a credit note.

| # | Requirement |
|---|---|
| **a** | **An idempotency key on the order submission**, so a retry cannot produce a second document |
| **b** | **A persisted *submitted, outcome unknown* state**, so a restarted Novocore can **ask what happened** rather than guess. This is the pull-based reconciliation the architecture rules already require |
| **c** | **A user-visible unresolved state — never an error that invites a retry.** An error message beside a button is an instruction to do the one thing that doubles the damage |

📌 **Requirement (c) is a UI obligation as much as a backend one**, and the screen that will need it —
the sales document list — is being built in F5, before the adapter exists. It is recorded here rather
than built there, because inventing an unresolved state with nothing to produce it is how a marker
nobody maintains gets added (the same argument that removed R1b's *stock not yet moved* indicator).

**ʸ 18b — dispatch document.** Placed with the Go adapter because Go already issues the δελτία, so the
dispatch document is most likely another **received** document rather than a Novocore-authored one —
much smaller than designing one from scratch. Vehicles (`Μεταφορικά μέσα`) and transport purposes
(`Σκοποί διακίνησης`) were dropped from R1 and return here, where the document that uses them exists.

**ʷᵒᵒ 19 — the WooCommerce adapter, and the one-time load that is NOT it.** Decided 2026-08-03 (U3).

**Direction first: Novocore is the centre of the ecosystem.** Categories, brands, products and
everything product-related are **created in Novocore**; **WooCommerce receives from Novocore, never
the reverse.**

⚠️ **Conflating the adapter with the initial load means building bidirectional sync that is never
needed again.** They are two different things with two different lifetimes:

- **The adapter syncs Novocore → Woo, forever.** That is step 19.
- **The initial load runs Woo → Novocore ONCE and is then deleted.** It has a migration's property —
  **one clean shot** — so it is **throwaway code with careful verification**, and it has its own row
  above rather than being absorbed into 19.

**Three decisions recorded with it:**

- **Categories import AS-IS.** The owner confirms Woo's categories are exactly the ones wanted, so
  there is **no curation during the load**. Woo's hierarchical, multi-membership structure matches
  D2's three-level many-to-many — see ᵗ. **D2 must therefore exist before the load.**
- ⚠️ **STOCK MUST NOT COME FROM WOO.** Woo's stock numbers are **a projection with no cost attached**,
  and Novocore needs opening **lots** — quantity *and* cost. Those come from Go, or from a physical
  count valued against purchase invoices. **Product data from Woo; stock from elsewhere.** Recorded
  as **a separate and probably harder migration question**, not as part of the product load.
- **After cutover Woo is READ-ONLY for product data**, and any change made there is overridden by
  Novocore. ⚠️ **Scoped, and the scope is the part that matters:** Novocore owns **the fields it
  manages** and overwrites them without asking; fields it does **not** manage — SEO text, image
  galleries, plugin data — are **left untouched**. ⚠️ **That list must be explicit and written down
  at step 19. It does not exist yet.** The alternative is discovering it when a product's images
  vanish.

**ᵃᶜˢ 21 — the ACS Courier adapter has TWO MODES, and the step name hides one of them.** Decided
2026-08-03 (U3), **before the step is scoped**, because *"ACS adapter"* naturally reads as one thing
and a step scoped from the name alone would build half of it.

- **Receive an existing voucher.** **Skroutz vouchers are created by Skroutz** and arrive at Novocore
  ready.
- **Create one.** Novocore creates the voucher for **WooCommerce and phone (manual) orders**.

**ˢᵒᶠ 22 — Sales Order Fulfilment also owns the per-order SHIPPING address.** Moved here from D3 on
2026-08-03 (U3). Billing and shipping are separate and shipping defaults to billing, but **the
shipping address is registered at the ORDER, not on the customer**, and it affects only the courier
voucher — so **the customer entity holds one (billing) address** and this is where the other lives.
⚠️ **There is no order entity anywhere in this system today**; step 22 is where one *will* exist,
which is precisely why the requirement is recorded here rather than left in D3 waiting on a table
that does not exist. See ᵈ³.

### ⚠️ AN OPEN STRUCTURAL QUESTION, recorded 2026-08-05. **Deliberately NOT resolved**

**The order is the thing that gets issued, and it is scheduled four phases after the screens that
depend on it.** Both paths in `CLAUDE.md` §1b run through an **order** — customer, lines, prices,
channel — which is submitted to the invoicing software for issuance and later receives a document
back. **Novocore has no order entity. It is this step, in Phase 4, while F5–F9 build the document
screens in Phase 2.**

⚠️ **And the order and the document are TWO LINKED OBJECTS, not one object filled in progressively.**
The issued document may not match the order: **Go applies its own VAT resolution, its own rounding and
its own numbering.** A design that treats the document as the order with extra fields would have no
way to represent the ordinary case where they differ — which is also the case a reconciliation check
exists to find.

**This is recorded as an open question and nothing is decided from it.** It is stated here, and
against F5 in `HISTORY.md`, so that whoever reaches step 22 knows the question predates them and
whoever extends a document screen knows not to answer it by accident.

**ᵐᵍ 24 — the migration is the shared deadline for six ⚪ rows.** D5, D4, D1, D3, M0 and R3 all have
to land before real data does — see *The ⚪ rows share a deadline* under Phase 2 for the per-item
reasons. **M0 exists to precede this step**, and M0a can run immediately.

**ᶻ 25 — Clearing Checks** also owns series gap detection: because Novocore records numbers rather than
generating them, a jump in a series means a document was issued through Go and never arrived. That check
is only possible because numbering is observed.

**ᵃᵈ 28 — the AADE services that genuinely are adapter-shaped**, as opposed to the codification lists
under ᵃᵃ: the **Basic Business Registry ΑΦΜ lookup** and **VIES**. Both are live request/response
services against a remote system, which is what an adapter is for.

**ᵃᵃ 29 — AADE myDATA adapter.** ⚠️ **AADE publishes no live API for codifications.** The REST API only
moves documents. Code lists live in the Annex tables of versioned PDFs, the XSD schema files, and an
Excel of permitted classification combinations — and they do change between versions. **Source: a chat
design session, where it was checked against AADE's published REST API method list on 2026-08-02. It
has not been verified from inside this repository, and is recorded as stated rather than as confirmed.**
The approach is: seed as versioned core data, record the spec version, and add a periodic diff check
that **alerts a human and never auto-applies** — because a code list that updates itself would silently
change what already-transmitted documents claim.

**ᵇᵇ 40 — core-composed invoicing.** The one step where Novocore begins allocating the **series number**
and composing the document itself, transmitting through the Πάροχος adapter instead of handing the job
to Go. **It still does not obtain the ΜΑΡΚ itself** — that comes back from the transmission path, as it
always has. Everything R1 deliberately did not build — sequence allocation, gap prevention — belongs
here and not before.

**ᶜᶜ 43 — commercialization.** Per-customer instance with its own database is architecturally
compatible: no company dimension, no multi-tenancy, EUR-only holds. What it actually requires is
elsewhere — migration tooling that runs against databases nobody has seen, provisioning and onboarding,
import from whatever each customer uses today, an internet-facing security posture, and the hosting
decision (who runs the server) that determines which of three different products this becomes.

**ʲ Step 36 includes bilingual EN/EL two-way voice I/O** layered on AI Analysis's read-only
deterministic-query design. An adapter to an external speech provider, not a change to the core. +0.7 h
on the original 2.0 h.

**ᵏ Step 37 pairs a searchable employee manual with an assistant that answers strictly by citing the
manual's own content** and says when a case isn't covered. The 1.5 h covers the mechanism only —
**writing the manual's content is not a development-hours item.** Placed after the modules most likely
to generate real procedures worth documenting.

**ᵇᵃᵈᵍᵉ F10 — the build-SHA badge, with a named trigger rather than "if it recurs".** ⚠️ **It will
recur; that is what the 2026-08-03 finding establishes** — a current screen calling a stale API is
this stack's default state after any backend commit, because the frontend is served by a dev server
that recompiles from disk and the backend by an image that changes only when rebuilt. The unconditional
rebuild rule in `CLAUDE.md` removes the *cause*; this makes the *condition* visible when the rule is
somehow not followed.

The shape: record the git SHA into the jar (`spring-boot:build-info` plus the commit id), expose it on
an authenticated route, and have the app shell show a badge when the frontend's SHA and the backend's
disagree. **Attached to F10 because F10 touches the app shell anyway**, so the placement costs almost
nothing there and would be a whole detour anywhere else.

⚠️ **Step 43 needs it regardless of F10.** Once anyone other than this business runs NovoCore,
*"which version is that customer on?"* stops being a convenience and becomes a support precondition —
so if F10 slips past 43 for any reason, this moves rather than waits.

#### 📌 Display defects deferred to F10 from earlier steps' live legs

**A running list, so a defect the owner saw but chose not to stop for is neither fixed on the spot nor
forgotten.** Each entry names the step whose live leg found it.

- 🆕 **The sort code is not a visible column on the document-type or series lists** — **R2b's live leg
  of 2026-08-05, arriving here 2026-08-06 as R2c's half 2a.** ⚠️ **Display only, and that is
  established rather than assumed:** the owner confirmed the **ordering is correct**, so R2b's 3.5
  (the server's default order) landed and only 3.6 (*first list column*) did not. The fix is one
  column definition per list. 📌 **The behaviour half of the same defect did NOT come here** — the
  ordering was confirmed for document types and **never checked on the series lists**, and that check
  went to R4 with 2b, because it is behaviour and R4 opens those screens anyway.

- ✅ ~~The AADE invoice-type picker cell is too small and cuts its text~~ — **PULLED BACK OUT OF F10
  AND FIXED IN R2b, 2026-08-04.** `OptionSelect` now passes `w-full`, so the trigger uses its column
  instead of shrinking to content. A width change only; F10 keeps the styling sweep.

  ⚠️⚠️ **AND THE REASON RECORDED HERE FOR PULLING IT WAS WRONG.** This entry used to say *"the
  disambiguating half of the label is the part being cut"* — that the `code —` prefix was lost.
  **`line-clamp-1` truncates the END**, so the prefix was structurally safe and the **group suffix**
  was what disappeared. The claim was never checked before being written into two documents, and it
  was then **amplified**: it was the stated justification for pulling the item out of F10 at all.

  **Pulling it may still have been right** — the picker is used nineteen times and its options are
  the longest in the application — but that is a different argument from the one that was made, and
  the record should not pretend otherwise. It is `CLAUDE.md`'s *fact established by reading, then
  built upon*, in a place where the reading was of a CSS class rather than a Java file.

  📌 **One thing is still open**: the owner said "cell", which may mean the select trigger (fixed) or
  a **column in the AADE invoice types list**, which is a different element and a different fix. He
  has not answered, and it is recorded rather than guessed.

**Steps 36 and 37 have not had a Claude Code pass against the real codebase** the way the rest of this
file has. Treat their estimates as rougher than the others.

---

## How the actual figures were derived

**Source: the Claude Code session transcripts** in `~/.claude/projects/`. These are local files, not an
estimate or a reconstruction.

**Hours.** A step's window runs from the previous step's last commit to its own last commit, including
its close-out. Active time is the sum of gaps between consecutive events, **each gap capped at 5
minutes** — so thinking and tool time count, and idling does not. Every row **excludes its own
close-out**, which is not in the transcript when the figure is computed, so every figure reads as "at
least".

**The cap is doing what it should, and that is measurable.** Across steps 0–14 the total was **18.6 h
of active time against ≈22.6 h of session wall-clock**. Step 15's two sessions add 4.5 h active against
4.9 h wall-clock, a tighter ratio because both were single long sittings.

**Tokens.** Read from the `usage` field of every assistant message over the same windows.

> ⚠️ **The `In` column is dominated by cache reads and is not comparable to `Out`.** It is
> `input + cache_creation + cache_read`. This is a 1M-context Opus session, so every request re-reads
> a large cached context — hundreds of requests per step, each re-reading a context measured in
> hundreds of thousands of tokens, is how a 1.1 h step reaches 73M. Those tokens are genuinely
> consumed and genuinely billed, at a tenth of the input rate, but **`Out` is the better measure of
> work produced.**

⚠️ **F5's 3.7 h / 766k covers THREE sessions and is SHORT, deliberately recorded rather than
withheld.** The windows are 2026-08-05 (the bulk of the build) and two on 2026-08-06 (the owner's
live-leg report, and the session that finished C.9, D, F.1 and this close-out). **The close-out
itself is not yet in the transcript when the figure is computed**, so the true total is a little
higher — that is the standing caveat in this file, not a defect in this row. ⭐ **It is the largest
`Out` figure in the table**, which is consistent with the step: two document domains, five screens, a
migration and a contract IT.

**Nothing before 2026-07-27 is measured.** The initial commit is 2026-07-24 and predates any Claude Code
session. No figure is offered rather than a guessed one. **One frontend window is unmeasured and blank
on purpose** — commits `94e17cd`, `56e3726`, `28c4119` landed without a close-out recording figures, and
reconstructing them now would be the guesswork this column exists to keep out. The transcripts survive;
a future session can measure them properly. **This note exists so nobody reads the silence as zero.**

**The calibration data, both directions.** Measured effort came in consistently **below** estimate for
every build step: **20.6 h actual against 31.5 h estimated for steps 0–14**, a ratio of about **0.65**,
and the actual figure includes step 3b, which had no estimate at all. Only three build steps landed near
or over their estimate — **11 (2.1 vs 2.0)**, **12 (2.6 vs 2.7)** and **14 (2.0 vs 2.5)**. The first two
are the ones with real-world commissioning in them (live SMTP, live Google Drive) rather than pure build;
the third is the largest single piece of code the project has produced in one step, which is the more
ordinary reason an estimate holds. **Step 15 is the sole exception and it is a large one** — 4.5 h
against 0.7 h — which is why it is excluded from that ratio rather than folded into it: it is a
validation step, not a build step, and footnote ˡ sets out why the two do not price alike.

**Estimates are never overwritten by actuals.** They sit in their own column, because the
estimate-versus-actual comparison is the only calibration data this project has. **And the not-started
estimates are deliberately not rescaled by the 0.65 ratio**: it was measured on core-domain build work
with a settled architecture, and adapters against third-party APIs, a frontend, and a real data
migration are different work with different failure modes. The ratio is recorded so the decision to
rescale is the owner's and evidence-based.
