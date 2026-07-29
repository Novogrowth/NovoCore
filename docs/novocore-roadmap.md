# NovoCore — Roadmap & Effort Tracker

**Legend:** 🟢 Done · **Bold + Current** = in progress · 🔴 Not started

**Hours** — `Est.` is the original planning estimate, left untouched. `Actual` is measured from the
Claude Code session transcripts, not estimated; see *How the actual figures were derived* below.
**Tokens** are measured from the same source. `Out` is tokens generated; `In` includes cache-creation
and cache-read, which dominate it — read the note before drawing conclusions from that column.

---

## Phase 1 — the core (complete through step 14; step 15 in progress)

| Step | What                                                          |  Est. | Actual |   Out |      In | Status  |
|-----:|---------------------------------------------------------------|------:|-------:|------:|--------:|---------|
|    0 | Toolchain, ADRs                                               |   0.8 |  ᵃ     |    ᵃ  |      ᵃ  | 🟢 Done |
|    1 | Skeleton, guardrails, CI                                      |   1.2 |    1.5 |  377k |   31.5M | 🟢 Done |
|    2 | Money/Quantity/SubLedgerRef, migrations, Settings, Audit, Atts|   1.9 |    1.1 |  333k |   56.9M | 🟢 Done |
|    3 | Chart of accounts                                             |   1.4 |    0.5 |  221k |   15.9M | 🟢 Done |
|   3b | VAT classes, exemption reasons, charge types ᵇ                |     — |    0.4 |  215k |   25.2M | 🟢 Done |
|    4 | Users, auth, permissions (incl. 4b, first REST endpoint) ᶜ    |   1.9 |    0.7 |  341k |   96.9M | 🟢 Done |
|    5 | Product, Customer, Supplier, Asset ᵈ                          |   2.5 |    1.8 |  511k |  125.6M | 🟢 Done |
|    6 | Inventory Lot/Unit, Location, Bundles                         |   2.5 |    0.9 |  462k |   66.9M | 🟢 Done |
|    7 | Journal engine, VAT posting                                   |   2.5 |    1.1 |  517k |   73.1M | 🟢 Done |
|    8 | Purchase Invoice + Goods Receipt + GR/IR + FIFO               |   2.4 |    1.2 |  452k |   99.3M | 🟢 Done |
|    9 | Sales Invoice, Credit Note, Receipt, Payment, Bank Transfer   |   2.7 |    1.6 |  531k |  126.4M | 🟢 Done |
|   10 | Freight / landed cost allocation                              |   1.9 |    1.3 |  442k |   90.1M | 🟢 Done |
|   11 | Email service (incl. the ADR 0012 revision) ᵉ                 |   2.0 |    2.1 |  697k |  127.7M | 🟢 Done |
|   12 | Auto backups (incl. commissioning, CI, self-invocation fixes)ᶠ|   2.7 |    2.6 |  606k |  151.5M | 🟢 Done |
|   13 | Test suite consolidation sweep (incl. Q45 fix) ᵍ              |   2.6 |    1.8 |  490k |  151.9M | 🟢 Done |
|   14 | REST surface — 133 routes, Q44, migration V25 ʲ               |   2.5 |    2.0 |  646k |  151.5M | 🟢 Done |
|   15 | Dummy data validation — **in progress**, see ˡ                |   0.7 |    3.1 | 1,355k|  559.5M | **Current** |
|      | **Subtotal, steps 0–15**                                      |**32.2**|**23.7**|**8.21M**|**1,951M**| |

## Not started

Estimates below are unchanged. Nothing in this section has been measured.

| Step | What                                                          |  Est. | Status        |
|-----:|---------------------------------------------------------------|------:|---------------|
|   16 | Frontend ⁱ                                                    |   8.0 | 🔴 Not started |
|   17 | Operational monitoring                                        |   1.0 | 🔴 Not started |
|   18 | Prosvasis Go adapter                                          |   4.5 | 🔴 Not started |
|   19 | WooCommerce adapter                                           |   2.0 | 🔴 Not started |
|   20 | Real data migration from Manager.io, parallel-run             |   2.5 | 🔴 Not started |
|   21 | Purchase Orders module                                        |   1.5 | 🔴 Not started |
|   22 | Sales Order Fulfillment module                                |   2.5 | 🔴 Not started |
|   23 | File import adapter                                           |   1.0 | 🔴 Not started |
|   24 | ACS Courier adapter                                           |   1.3 | 🔴 Not started |
|   25 | Skroutz adapter                                               |   1.3 | 🔴 Not started |
|   26 | POS provider adapter                                          |   1.3 | 🔴 Not started |
|   27 | Bank aggregator adapter                                       |   1.3 | 🔴 Not started |
|   28 | AADE myDATA adapter                                           |   2.5 | 🔴 Not started |
|   29 | AADE/VIES lookup adapter                                      |   0.7 | 🔴 Not started |
|   30 | Reports module                                                |   3.0 | 🔴 Not started |
|   31 | Clearing Checks module                                        |   2.0 | 🔴 Not started |
|   32 | Roast Date Report module                                      |   0.5 | 🔴 Not started |
|   33 | Back-in-Stock Reminders module                                |   0.5 | 🔴 Not started |
|   34 | Service/Technician Management module                          |   2.5 | 🔴 Not started |
|   35 | Price Tag Printing module                                     |   0.7 | 🔴 Not started |
|   36 | Accountant Monthly Package module                             |   1.3 | 🔴 Not started |
|   37 | AI Analysis module, incl. bilingual (EN/EL) voice I/O ʲ       |   2.7 | 🔴 Not started |
|  37b | Employee manual + grounded voice assistant (EN/EL) ᵏ          |   1.5 | 🔴 Not started |
|   38 | AADE Πάροχος adapter                                          |   2.0 | 🔴 Not started |
|   39 | Core-owned invoice issuing + POS terminal — retires Go        |   6.5 | 🔴 Not started |
|   40 | Employee Digital Work-Card / Ergani module                    |   1.3 | 🔴 Not started |
|   41 | Migration to a real production server                         |   1.0 | 🔴 Not started |
|   42 | Requirements to go commercial                                 |     — | 🔴 Not started |
|      | **Subtotal, steps 15–41**                                     |**57.6**| |

---

## Notes

**ᵃ Steps 0 and 1 cannot be separated.** `22bb361` carries ADRs 0001–0005 *and* the skeleton, and
both were done in one continuous stretch, so there is no boundary in the record to split on. The
1.5 h and the token figures on row 1 cover **both** rows. Combined estimate was 2.0 h.

**ᵇ Step 3b was missing from this table.** It is a real inserted step (VAT classes, exemption
reasons, charge types — `15627d2`), recorded as such in `PROGRESS.md`. Added here so its measured
0.4 h is attributed rather than silently absorbed into a neighbour. It never had an estimate.

**ᶜ Step 4 includes step 4b.** The two commits are 20 seconds apart (`a1da425`, `91543fa`), so their
time is not separable.

**ᵈ Step 5's window also carries V7, V8, V10 and V11** — the Q27 income accounts and ChargeType seed,
the real 29-row AADE exemption seed, the VAT rate-bound fix, and Q34's units-of-measure table. All
were done in the same stretch as step 5 and none had its own estimate.

**ᵉ Step 11 includes its revision** — ADR 0012, referenced-not-copied attachments (`8af7078`), done
the following day. Build alone was 1.3 h; the revision added 0.8 h.

**ᶠ Step 12 includes three things that arose from it** rather than from a separate plan: the proxy
self-invocation ArchUnit rules and the two real defects they found (`24a3cd7`, `a4ec7db`), the CI
`pg_dump` version fix (`5a6dfa5`), and commissioning against real Google Drive (`e907a9e`). Build
was 1.5 h; commissioning 1.1 h.

**ᵍ Step 13 includes the Q45 fix** (ADR 0015, migration V24 — `951929f`). The sweep itself was 1.0 h;
finding, deciding and fixing the ledger defect it turned up added 0.8 h.

**ʲ Step 14 is done** — the REST surface, in four commits (`423bf34`, `e6354d6`, `b8aa9e2`,
`f2e8e06`). Its figures cover the whole of it up to the moment this file was written, so they
**exclude the close-out commit and push that follow** — a few minutes and a few thousand tokens
that land after the measurement, as they do for every step measured this way.

Worth recording next to the number: **the estimate was 2.5 h and the actual was 2.0 h**, the first
estimate this project has come close to on a pure-build step. It also produced the one migration
the plan said would not be needed (V25), which is the sort of thing the estimate could not have
priced either way.

**ˡ Step 15 is NOT finished, and its row is measured anyway.** 15a (the harness) is complete and
15b (the narrative) is partly complete; the refusal matrix, the permission sweep, the read-back and
date-boundary checks, restore and `assertEveryRouteCoveredExcept` are still to come. The figures
cover one session, measured the same way as every other row and by the same method at the bottom of
this file — 1,171 timestamped events, 3.25 h wall clock, 3.14 h active under the 5-minute cap, seven
gaps capped. They exclude the close-out that follows them, as every row does.

Recorded mid-step rather than left blank because a partial measurement is a fact and a blank is not,
and the calibration point is worth having early: **the estimate was 0.7 h and the step is at 3.1 h
with work remaining.** That is the largest overrun in this table by a wide margin — every other step
came in at or under estimate. Two things account for it, and neither is the narrative itself:

1. **0.7 h priced the wrong thing.** It reads like an estimate for "write a script that inserts some
   rows". What was agreed and built is six classes of check over 133 routes, a shared invariant
   component, a route-coverage ledger and a permission sweep. The proposal said so before any code
   was written and put the full version at 2.0–2.5 h.
2. **The step's whole purpose is finding defects, and it found six** — each needing a decision, a
   fix, tests and a commit, which is work an estimate for a validation harness cannot contain by
   construction. A validation step that finds nothing is cheap; one that earns its keep is not.

**The other estimates are deliberately not rescaled**, for the reason already stated below: this
figure comes from validating an existing API and does not transfer to a frontend or an adapter.

**ⁱ Step 16's estimate is untouched, but the frontend is not at zero.** A verified Vite + React 19 +
Tailwind v4 + shadcn/ui foundation with an app shell was built on 2026-07-27 (`492ce24`, `531f12a`)
and has been untouched since — **0.7 h, 183k out, 14.2M in**, spent before step 1 and attributed to
no row in this table. The 8.0 h estimate stands as written; this is recorded so the foundation is not
paid for twice.

**ʲ Step 37's estimate now includes bilingual (English/Greek) two-way voice I/O** — speech-to-text and
text-to-speech layered on top of AI Analysis's existing read-only, deterministic-query design, so a
question can be spoken and the answer heard rather than only typed and read. This is an adapter to an
external speech provider (candidates discussed: Speechmatics for Greek accuracy, or Azure/Google for
broader ecosystem fit), not a change to the core. +0.7 h added to the original 2.0 h estimate for the
provider integration; this does not include ongoing per-minute usage cost, which is metered separately
and not a development-hours item.

**ᵏ Step 37b is a new step, not in the original roadmap.** It pairs a searchable employee manual with
a voice-and-text assistant that answers strictly by retrieving and citing the manual's own content —
never improvising a procedure — and explicitly says when a case isn't covered, the same
never-silently-resolve-ambiguity discipline used throughout the ledger itself. The 1.5 h estimate
covers the retrieval-and-voice mechanism only. **Writing the manual's actual procedural content is not
a development-hours item** — it's domain knowledge that has to come from the business, not something
Claude Code can generate on its own, and its true cost isn't captured by this column. Placed after the
modules most likely to generate real procedures worth documenting (Sales Order Fulfillment, Service/
Technician Management), since a manual describing unstable or not-yet-built workflows would need
rewriting.

---

## How the actual figures were derived

**Source: the Claude Code session transcripts** in `~/.claude/projects/` — 14 sessions across
`c--Novocore` and `g--My-Drive-Novocore` (the pre-move location), 8,563 timestamped events spanning
2026-07-27 13:53Z to 2026-07-29 15:32Z. These are local files, not an estimate or a reconstruction.

**Hours.** A step's window runs from the previous step's last commit to its own last commit
(including its close-out commit, so the documentation written for a step counts towards it). Active
time is the sum of the gaps between consecutive events in that window, **each gap capped at 5
minutes** — so thinking and tool time count, and lunch, overnight and between-session idling do not.
Measured total is **18.6 h of active time against ≈22.6 h of session wall-clock**, which is the sanity
check that the cap is doing what it should.

**Tokens.** Read from the `usage` field of every assistant message in those transcripts and summed
over the same windows.

> ⚠️ **The `In` column is dominated by cache reads and is not comparable to `Out`.** It is
> `input + cache_creation + cache_read`. This is a 1M-context Opus session, so every request re-reads
> a large cached context — hundreds of requests per step, each re-reading a context measured in
> hundreds of thousands of tokens, is how a 1.1 h step reaches 73M. Those tokens are genuinely
> consumed and genuinely billed, at a tenth of the input rate, but **`Out` is the better measure of
> work produced.**

**Nothing before 2026-07-27 is measured.** The initial commit is 2026-07-24 and predates any Claude
Code session in this project — the brief and the early design work happened elsewhere. No figure is
offered for it rather than a guessed one.

**One observation, offered without acting on it.** Measured effort came in consistently below
estimate: **20.6 h actual against 31.5 h estimated** for steps 0–14, a ratio of about 0.65, and the
actual figure includes step 3b, which had no estimate at all. Only three steps landed near or over
their estimate — **11 (2.1 vs 2.0)**, **12 (2.6 vs 2.7)** and **14 (2.0 vs 2.5)**. The first two are
the ones with real-world commissioning in them (live SMTP, live Google Drive) rather than pure build;
the third is the largest single piece of code the project has produced in one step, which is the more
ordinary reason an estimate holds. **The estimates for steps 15–41 have deliberately not been
adjusted by this ratio**: it was measured on core-domain build work with a settled architecture, and
adapters against third-party APIs, a frontend, and a real data migration are different work with
different failure modes. The ratio is recorded so the decision to rescale is yours and evidence-based
rather than mine and automatic.

---

## Original instructions for Claude Code

*Retained verbatim, moved here from the top of the file.*

> For each step marked 🟢 Done, replace the estimated `Hours` value with actual pure development time
> if it can be reconstructed (e.g. from commit timestamps, session logs, or your own record of the
> work) — otherwise leave the estimate and note that it's still an estimate. Populate the `Tokens`
> column with actual token consumption for that step if this can be determined (e.g. from usage logs,
> transcript length, or any available accounting); otherwise mark it `n/a`. For steps marked 🔴 Not
> started, leave `Hours` as the current rough estimate unless you have a more informed basis to
> refine it, and leave `Tokens` blank. Keep the legend and status convention below intact when
> updating.

**How this was answered:** actual hours and actual tokens were both available from the session
transcripts and are populated for every done step, so nothing is marked `n/a` and no estimate was
left standing in the `Actual` column. The original `Hours` estimates were kept in their own column
rather than overwritten, so the estimate-versus-actual comparison survives. Not-started rows are
unchanged. Steps 0 and 1 are the one place where the record genuinely cannot separate two rows, and
that is marked rather than split by guesswork.

**Steps 37 and 37b were added after this file was last updated by Claude Code**, from a chat
discussion about bilingual voice I/O and an employee manual with a grounded voice assistant. Both are
brainstorm-stage additions, not yet reviewed or scoped by Claude Code against the real codebase the
way the rest of this file was — treat their estimates as rougher than the others until they've had
that pass.
