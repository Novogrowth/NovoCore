# NovoCore — Build Progress

*Live status. **Overwritten each session close-out, never appended to.** Last updated: 2026-08-07 —
U5 recorded the scope review; **R4 is current and unfinished.***

> **This file is the ONLY place current state lives.** If a sentence here is out of date, correct it
> here; do not add a second copy somewhere else.
>
> - **What happened in a past step** → [`docs/HISTORY.md`](HISTORY.md), append-only, indexed by step id.
> - **The sequence, and each step's scheduling glyph** → [`docs/novocore-roadmap.md`](novocore-roadmap.md).
> - **How to build** → [`CLAUDE.md`](../CLAUDE.md), and [`frontend/README.md`](../frontend/README.md) before any frontend code.

**The boundary, so nothing drifts back:** a section belongs **here** if a future session must act on
it — an open verdict, an unmet obligation, a named trigger, or a specification a step adopts. It
belongs in `HISTORY.md` if it records **what a past session did**. When a step closes, its record moves
to `HISTORY.md` and its live residuals are extracted here first.

---

## Where things stand

⚠️ **This is the ONLY status table in the repository's documentation, and it must stay the only one.**
U2a merged it with a second table (*What is next, in one place*) that had been living inside step 8a's
record and covered a different, overlapping set of steps. **Do not add a summary above it or a
convenience copy below it** — eight scattered status locations is what U2a existed to fix, and
`HISTORY.md`'s index deliberately carries no status column for the same reason.

| Step | What | Status |
|---|---|---|
| 0 | Toolchain, ADRs | **Done** |
| 1 | Skeleton, guardrails, container stack, CI | **Done, committed** `22bb361` |
| 2 | Money/Quantity/SubLedgerRef, schema conventions, Settings, Audit, Attachments | **Done, committed** `cb93fc8` |
| 3 | Chart of accounts | **Done, committed** — see below |
| 3b | VAT classes, VAT exemption reasons, charge types | **Done, committed** — inserted step, see below |
| 4 | Users, auth, permissions | **Done, committed** — Q21 and Q22 answered, see below |
| 4b | First REST endpoint (chart of accounts, read-only) | **Done, committed** — boundary validation, see below |
| 5 | Product, Customer, Supplier, Asset | **Done, committed** — Q5, Q8, Q9, Q12 answered, see below |
| 6 | Inventory Lot/Unit, Location, stock queries, bundles | **Done, committed** — Q7, Q25, Q11 answered, see below |
| 7 | Journal engine, debits=credits invariant | **Done, committed** `8e7e10e` — Q13, Q14, Q19, Q26, Q15, Q16 answered, see below |
| 8 | Purchase Invoice, Goods Receipt, GR/IR, purchase price variance, FIFO | **Done, committed** `c6e2513` — ADR 0004's open item, Q17 and Q39 answered as **ADR 0008**, see below |
| 9 | Sales Invoice, Credit Note, Receipt, Payment, Bank Transfer, open items, rounding | **Done, committed** `29e9dcd` — Q10, Q15's remainder, Q16, Q26 answered as **ADR 0009**; Q31 confirmed; all seven obligations discharged, see below |
| 10 | Freight / landed cost allocation | **Done, committed** `cf6f1e4` + `6f06cf8` — Q18 answered as **ADR 0010**, and a defect it introduced closed as **ADR 0011**, see below |
| 11 | Email service | **Done, committed** `b542cf7` + `0790c74` — SMTP credentials supplied and stored in Settings, see below |
| 12 | Automated backups | **Done, and operationally verified 2026-07-29** — V23, ADR 0013. Real encrypted dump uploaded to both Drives; all three owner action items closed |
| 13 | Test suite consolidation sweep | **Done** — property tests on the money types and on FIFO, one whole-scenario invariant sweep, **ADR 0014**, and **one real defect found and then fixed (Q45 / ADR 0015)**, see below |
| 14 | **The REST surface** — 133 routes, and Q44 answered in full | **Done, committed** `423bf34` + `e6354d6` + `b8aa9e2` + `f2e8e06` — three sub-steps as agreed, plus the BundleService follow-up. **Migration V25.** See below |
| 15 | **Dummy data validation** — the API driven end to end over HTTP | **Done** — 15a and 15b complete. **Nine real defects found and fixed**, migration **V26**. Route coverage **128/133 driven, 5 excused with reasons**, asserted rather than reported. See below |
| 16a | **Backend prerequisites for the frontend** — four items agreed before any step 16 work | **Done** — `/me`, preview endpoints, the OpenAPI spec + drift check, and the paging contract. Migration **V27**, plus **session eviction**, a defect found while building the first item. See below |
| 16b | **Users & roles, journal listing, settings** — the three sections with no HTTP surface at all | **Done, committed** `452b3fd` — 37 routes, **no migration**. Three defects found and fixed, none of them in the code the step set out to write. See below |
| S1 | **Substring search** — `pg_trgm` + `unaccent`, one shared mechanism, five screens | **Done** — migrations **V28** and **V29**, 17 GIN trigram indexes, `TextSearch` + `SearchFilter`, `?search=` on five routes. **Two findings**, one of which was invisible to the entire test suite until the test database was made to match the real one. See below |
| F4 | **Settings** — three config pages, VAT classes and units of measure, plus search and sorting | **Done** — migration **V30**, 4 GIN trigram indexes, `?search=` on 2 more routes, **22 sub-parts all with verdicts** (21 approved, 1 added mid-step), and `F4WriteContractIT` (15 tests) which **corrected a premise the step was built on**. Two findings. See below |
| R1a | **Document reference data (backend)** — the two-layer document model | **Done, committed** `aa1eda4` + `c5f9a97` — `aade_invoice_type` (55 seeded), the business's own document-type, series and delivery-method lists (all shipped **empty**), the statutory-codification contract with an ArchUnit rule, myDATA payment codes, statutory identifiers on `sales_invoice`, and the three artefact seeds. Migrations **V31**, **V32**. **54 new operations.** All 48 sub-parts have verdicts. See below |
| R1b | **Document reference data (behavioural)** — the series becomes what a sale names | **Done, committed** — `seriesId` mandatory on `NewSalesInvoice`, `channel` removed and **derived from the series**, the consumption path branches **silently** on the document type's `affectsStock`, and three refusals (channel-less, inactive series, inactive type). Migration **V33** (comment only). **No new operations.** ⚠️ **Two defects found, both invisible before this step**; ⚠️ **the derived-accessor guard left R1b and became step W1** on a measurement. All 22 sub-parts have verdicts. See below |
| U3 | **Eleven design decisions written into the repository** — D5, D4, D1, D3, D2, M0, vouchers, the shared gate | **Done, documentation only** — no production code, no schema, no migration, no test changed. D4 split (half already answered), M0 split into M0a/M0b, the per-order shipping address moved to step 22, voucher creation modes recorded against step 21, the Woo one-time load separated from the Woo adapter at step 19, and the **shared gate before step 24** recorded. Nothing ⚪ was promoted or reordered beyond those placements. See below |
| R2 | **Document reference data (screens)** — six settings screens over R1a's six tables | **Done, committed** — the AADE codification (read + three verbs, **no create, ever**), sales/purchase document types, sales/purchase series, delivery methods. ⚠️ **Grew a backend sub-part mid-step**: 7 new routes making a series' abbreviation, document type and ΜΑΡΚ flag **editable while unused and frozen once used**, because none of them had a write route on any installation. **All 41 sub-parts have verdicts**; 4 premises corrected. See below |
| R2b | **What R2's live leg found**, plus two premises its brief had wrong | **Done, committed** — ⚠️ **the stale-list defect was OLDER than R2 and sat in all 13 create forms**, fixed globally with a structural guard; ⚠️ **no server-side check existed** that a series' document type is usable, so the screen was the only guard; `sort_code` on four tables (**V34**, integer, NOT NULL); **payment methods (V35)**, which had no screen because of a scoping error. **237 → 247 operations.** All 30 sub-parts have verdicts. ✅ **§5's conditional (5.4) closed on 2026-08-05** when the owner ran R2b's live leg — the truncation was the select trigger, already fixed. ⚠️ **The leg produced two new rows, neither built: R2c** (sort code invisible as a column, and **absent from the series edit form**) and **R4** (payment methods are a **business** list, not a statutory one — a requirement correction that **changes the sales invoice request contract**) |
| W1 | **Serialised-record contract fidelity** — the wire shape equals the documented shape | **Done, committed** — the generator describes what **Jackson** writes, not what a record's components say. **+58 properties across 27 response schemas**; no operations, no schemas, no migration. ⚠️ **Request records deliberately excluded** — they are deserialised through the canonical constructor and never serialised, so a derived property there describes a write that never happens. `OpenItemRef.isCustomerSide()` deleted (zero references anywhere). **Two premises corrected**, one of them `CLAUDE.md`'s own statement of the Jackson mechanism. All 16 sub-parts have verdicts |
| F5 | **Sales Invoice + Credit Note screens** — the first step to reach the **recording** path | **Done, committed** `c395324` — live leg run 2026-08-06. All 30 sub-parts have verdicts, none open. Five sales/credit-note screens, `search=` on both document routes (**V36**, 4 GIN trigram indexes), the repository's **first three `meta.sortKey`s**, and `DataIntegrityViolationException` mapped to 422 so an index-enforced rule stops arriving as Boot's legacy 500. ⚠️ **Two things are DELIBERATELY not built and have their own roadmap rows** — **N1** (a reversed document's number) and B.4's collation. ⭐ **The record forms are PERMANENT PRODUCT — corrected 2026-08-07 (U5).** This cell used to read *"TRANSITIONAL by decision — a mirror is never typed in real operation"*; **the owner withdrew that premise**, so F5's forms are product and the instruction not to polish them is gone |
| 16 | **The frontend itself** — `/frontend/`, Vite + React + TS + Tailwind + shadcn/ui | **In progress. F0–F4, S1 and S2 done.** ⚠️ **W1 landed 2026-08-04, so next is F5, then the D-block (D1+D3+D4+D5), then F6 onward** — the owner's sequencing decision of **2026-08-04**, recorded as the roadmap's row order. *(This cell previously read "Next is Q1, then R1, then F5", correct when written on 2026-08-02 and overtaken since: Q1, R1a, R1b, R2 and R2b have all landed.)* Foundations `94e17cd`, Products `56e3726` + guards `28c4119` + brand pass, then the render-loop fix `3458ee6`, F0 (the seed pass), F1 Suppliers `b406b27`, F2 Customers `496c7be`, F3 Users & Roles `aea0e56`, then **S1** (search), **S2** (sorting) and **F4** (Settings). **307 frontend tests, 31 files, green.** Per-step detail in `docs/novocore-roadmap.md`; decisions and what each step left behind in *Step 16 — the frontend* below |
| S2 | **Sorting** — sortable columns on the five list screens | ✅ **Complete and live-verified.** Client-side, on all five list screens; the browser leg was run by the owner on 2026-08-01. Nothing outstanding |
| Q1 | **The backend follow-up queue** | ✅ **FULLY CLOSED, 2026-08-03.** Four items, all with verdicts, the owner's browser leg passed on all four checks — and **item 7's regression is closed by 8a**, so the conditional marker this row carried is gone. Q1-a landed in 8a; **Q1-b is the only thing left open**, to decide with R1 |
| 8a | **Declare every compact-constructor requirement** | ✅ **DONE, 2026-08-03**, in two commits. 339 components across 114 records declared, cross-checked against the canonical constructors' bytecode in both directions; four schema-name collisions split; spec 75 → **143** schemas declaring `required`. All three gates met. Backend **1,381** tests green, frontend **308** green |
| 8b | **Consumer cleanup** | ⚪ **OPTIONAL, and not a correctness step.** 8a already regenerated the client and made the suite green; what remains is *taking advantage* of the new contract — removing `?.`/`??` guards on fields that can no longer be undefined. ⚠️ **The test-account decision attaches here** and should be settled *before* it starts |
| **R4** | **Payment methods become a business list** | 🟡 **CURRENT from 2026-08-06.** A **requirement correction**, not a defect: the list starts **empty**, the user creates rows, each names an **AADE payment-method article** *and* **the ledger account it settles to**, and all fields stay editable until the method is used. ⚠️ **It changes the sales invoice request contract** — `SettlementMethod` is a Java enum on `NewSalesInvoice` and must become an FK — which is why the gate is **before F6**. ➕ **R2c's 2b is attached** (the sort code on the series edit form) with the series-ordering check. ➖ **The chart-of-accounts decision (C1) removed a prerequisite it might have had**. **Its full checklist is below, not in `HISTORY.md`** |
| R2c | **Sort code** | ⚪ **DEFERRED AND SPLIT, 2026-08-06.** Not core work; the owner does not want it interrupting the core. **2a** (invisible column, cosmetic) → **F10**'s display-defects list. **2b** (absent from the series edit form) → **R4**, with the unverified series-ordering check. ⚠️ **Neither half is scheduled as R2c**, and the row is not a schedulable item any more |
| U2a | **Split `PROGRESS.md` / `HISTORY.md`** | ✅ **DONE 2026-08-06.** This file, 9,577 → ~1,300 lines; `HISTORY.md` append-only and indexed by step id. ⚠️ **Its guards are U2b's and are not built yet** |
| U2b | **The split's drift guards** | 🟢 **DONE 2026-08-06.** `frontend/src/docs/project-records.test.ts`, **7 tests**, each **proven against the defect it exists for** — a duplicated section, an unindexed one, the current step archived early, a second status table, the deleted header, and a renamed record file (which reported **95** dangling citations). ⚠️ **Plus the CI path change, measured rather than asserted**: the workflow's own globs matched `docs/PROGRESS.md` in **neither** block before and **both** after. Without it none of the six would ever run on a docs-only edit. 📌 **M1 rejected**, reason at the test |
| U4 | **The dated-figure sweep** | ⚪ **DEFERRED by the owner 2026-08-06, and it must be RE-PRICED rather than inherited.** `CLAUDE.md` and roadmap ᵘ² used to assign it to U2; both now point here. ⭐ **The split changed its price, not just its timing** — `HISTORY.md`'s header frames every figure inside it, so the original justification no longer holds. ⚠️ It **edits** historical entries, which U2a's constraint forbade; that is why it could not share a session with the split |
| N1 | **Release a reversed document's number** | ⚪ **Direction settled by the owner 2026-08-05, unbuilt, no slot.** ⚠️ Whatever replaces the partial index **must keep the concurrency guarantee** — a trigger's `NOT EXISTS` does not. `purchase_invoice` has no unique index on its number at all |
| C2 | **The cash limit is TWO thresholds** | ⚪ **Raised by R4's Phase 0, 2026-08-06. Recorded, not scoped.** €500 **incl. VAT** retail, €500 **net** for VAT-registered. 🛑 **Nothing in the model reliably distinguishes ΑΛΠ from ΤΠΔΑ**, which is why it is a row and not a sub-part. ⚠️ **R4 must not pre-empt it** |
| F5b | **`el-GR-x-icu` on `DOCUMENT_NUMBER`** | ✅ **CLOSED 2026-08-06 — NOT NEEDED.** `ORDER BY … COLLATE "el-GR-x-icu"` on `DOCUMENT_NUMBER` **would change no ordering**: the only text sort key carries plain uppercase Greek prefixes, and on those the two collations **agree** (measured, 3 negative controls). ⚠️ **RESIDUAL — the closure rests on a fact about data.** If a series abbreviation ever carries an **accent** or a **lowercase** letter, the work returns — and ⚠️ **nothing constrains one**: `varchar(20)`, not-blank + unique, `Required.text`, no pattern on the screen. **No constraint proposed.** Recorded at `NewSalesDocumentSeries#abbreviation`, its purchase twin and the series screen. Full record at roadmap ᶠ⁵ᵇ |
| W1c | **W1's two consumer clean-ups** | ⚪ **Queued out of W1 2026-08-04, given a row by U2a.** `CustomerView.systemRecord()` reads as if it returns the key while the wire carries a boolean (`AccountView.systemKeyIfAny()` is the idiom); and the settings screen still computes `configured` from `value !== ''` rather than the now-documented `unset` |
| R1c | **Fees / Έξοδα και κρατήσεις** | ⚪ **CUT from R1 (decision A), given a row by U2a.** ⚠️ Likely a **generalisation of `ChargeType`, not a sibling** — *Delivery* and *COD fee* already exist as rows. **The question that decides it:** does Go's list contain those same two rows? If yes, `ChargeType` is the thing to change |
| 8c | **`NewPurchaseInvoiceLine` is a discriminated union modelled flat** | ⚪ **Recorded by 8a as design item H.2, given a row by U2a.** Five components of which at most three can ever be present, selected by `type`; no `required` list can express it. ⚠️ **Named trigger: before a screen binds this record — which is F6** |
| D1 · D3 | **Counterparty codes, the supplier alias, and addresses** | ⚪ **Placed 2026-08-04: after F5, as one block with D4 and D5.** ⚠️ Content decided (U3): codes **nullable**; **supplier has an alias, customer never does**; addresses **structured** and enforced at the **document**, not the customer. ⚠️ **The accepted cost: F5–F9 are built before the fields they will want, so the document screens are touched twice** |
| D4 · D5 | **Internal document numbers, and a movable lock date** | ⚪ **Placed 2026-08-04: after F5, with D1 and D3.** Both are ledger integrity and both the accountant's. ⚠️ D4 is **internal reference numbers, not statutory ones** — gaps do not matter. ⚠️ D5's **first task is confirming what reversals do today**; U3 ran no code |
| D2 | **Product categories, 3 levels** | ⚪ **Gate: step 19**, before the one-time Woo load. Three levels deep, a product in several at once — a self-referencing table plus a join table. **Nothing exists, not even the schema** |
| R3 | **Self-supply posting paths** | ⚪ **NOT SCHEDULABLE — blocked on the accountant**, and inside the before-24 cluster, so *unschedulable* must not be read as *unimportant*. It carries the hardest structural item in the project: pricing from FIFO lot cost, which fights the price → post → consume ordering |
| M0a | **Map Manager's chart onto Novocore's** | 🟢 **UNBLOCKED, can run at any time** (U3, 2026-08-03). It is a **mapping exercise, not an import**: no code, no schema, a spreadsheet and a session. Novocore's chart was built from scratch, so the question is *which Manager accounts have no Novocore home*. ⚠️ Its **target** moved on 2026-08-06 — it now maps onto the official Greek chart (C1) |
| M0b | **A real year of transactions** | ⚪ **Gated: after D1/D3/D4, before step 24.** Importing before those exist means importing into columns that do not exist |
| — | **D1–D5, M0, R3 — the shared gate** | ⚠️ **Six of the seven share ONE deadline: before real data lands at step 24** (D2 is the exception — its gate is step 19). Recorded by U3 because seven independently schedulable rows is how a cluster slips past a shared deadline. ✅ **The slots are no longer open — corrected 2026-08-04**: **D1 + D3 + D4 + D5 are one block after F5**. ⚠️ **The gate still matters more than the slots**: a block that slips as a block still misses step 24 together, and **R3 and M0b remain slotless** |
| C1 | **The chart of accounts** | ⚪ **DECIDED 2026-08-06; ⭐ RE-EXAMINED AND UPHELD 2026-08-07. Recorded, NOT scoped.** The **official Greek chart is used directly**, with an **alias per account for display**, and **no separate business chart mapped onto it**. Reasoning: many-to-one granularity is better served by the product model, and one layer is the reversible choice. ⭐ **The mapping-layer alternative was RE-PROPOSED and WITHDRAWN on 2026-08-07** — it lost because a mapping between two charts is **a second record of one thing**. ⚠️ **The rejected alternative and its reasoning are now recorded at roadmap ᶜ¹**, because a proposal rejected without its reasoning gets re-proposed, and this one already was. **Accepted costs: a longer, more granular account list, and a larger M0a.** ⚠️ **The alias is now C3** |
| **U5** | **The 2026-08-07 scope review** | ✅ **DONE 2026-08-07. Documentation and configuration ONLY** — no production code, no schema, no migration, no test behaviour changed. **Ten decisions recorded, nothing built, nothing scheduled**; placement stays the owner's. **Full reconciliation below.** ⚠️ **Two of its own premises were disproven in Phase 0 and corrected rather than written in** — see the checklist |
| C3 | **The account display alias** | ⚪ **REQUIRED by C1, and BLOCKED. Recorded 2026-08-07; do not build it.** C1 standing makes the alias load-bearing — it is what makes an *official* chart usable by the owner rather than only by his accountant. 🛑 **The blocking question, for the owner:** does `code` become the official Greek chart code and `elp_code` collapse into it, leaving the alias to carry the familiar label — or is another arrangement wanted? ⚠️ **Measured: `code` is blank on every row and `elp_code` null on every row**, so adding an alias now makes **three label columns of which two are empty** — worse than the two-layer shape C1 rejected |
| **C4** | **Orders become a CORE entity** | ⚪ **DECIDED 2026-08-07, UNSCOPED. ⭐ The substantive decision of that session.** Orders are core data alongside Customers, Suppliers, Products and the chart of accounts — no longer only step 22's concern. ✅ **Closes `CLAUDE.md` §1b's open structural question**, by reclassification. ⚠️ **Nothing exists** — measured 2026-08-07: zero `*Order*.java`, no order table. ⚠️ **The half that does NOT close: the order and the document are TWO LINKED OBJECTS**, not one filled in progressively, because the invoicing software applies its own VAT resolution, rounding and numbering |
| C5 | **myDATA characterisation defaults** | ⚪ **Recorded 2026-08-07, NOT scoped. Gate: before step 24 / M0b.** An **account-level default**, a **line-level override**, and a way to **list every line that took its account's default unexamined**. ⚠️ **All three are the requirement, not two plus a refinement** — characterisation is per line, one account can carry several, and a default silently applied would manufacture exactly the accountant discrepancies the feature exists to remove. **Retrofitting onto posted history means restating history** |
| C6 | **Shared-entity ownership** | ⚪ **RAISED 2026-08-07. Twelve questions, none answered.** For each of **Customers, Products, Stock**: who may create, who may edit, what happens when the satellite is edited anyway, what happens when a push fails. 🛑 **Stock is hardest**: Go derives stock from its own documents, so this is **a capability question about Go, not a design decision** — and it may constrain scope rather than be constrained by it. ⚠️ **Go's documented API has no stock at all** (documented, not observed). 📌 **Products currently originate in Woo**; reversing it promotes **Product Creator from a Phase 9 module to a prerequisite** |
| G1 | **Go's API — capture what already works** | ⚪ **RAISED 2026-08-07. Trigger: BEFORE step 18 is scoped.** ⚠️ **Two small applications built with Claude Code already send and capture data against the invoicing software; they are not in this codebase and — verified across `docs/`, `HISTORY.md`, `CLAUDE.md`, `README.md` — nothing here records them.** Six behavioural findings must be captured first, two of which decide open rows: **does the same request twice produce one document or two** (X1's requirement a) and **can stock be set or only derived** (C6). ⚠️ **A working happy path establishes none of them.** ✅ **The vendor's API documentation landed during U5 and is committed at `docs/adapters/prosvasis-go-api.md`** — ⚠️ **which does NOT discharge this row**: documentation describes what the API is *meant* to do, and G1 is about what the two integrations *observed*. ⚠️ **Its capture date is unknown and is owed by whoever captured it.** 📌 **Whether the two apps are absorbed as adapters or stay standalone is SEPARATE and OPEN** |
| F6–F11 | **Purchase, settlements, freight/journal, read views, design pass, UI regression** | 🔴 **Not started.** ⚠️ **F6 is gated behind R4** — purchase documents settle too, so it is built against the corrected payment-method model rather than reopened |
| — | ⚠️ **Database sort order ≠ browser sort order** | 📌 **OPEN, and F4 did not close it.** F4 established *that* `el-GR-x-icu` was never applied; the database still orders by bytes under locale `C` while the browser orders by `Intl.Collator('el')`. Invisible only because no list pages on the server. **Whoever adds paging to a list screen owns this.** Its three requirements are in *Open questions and obligations* below |
| — | `Product.category` | 📌 Queued as **its own proposal** (roadmap **D2**), requirement recorded, deliberately not started |
| — | **Test-environment parity — enforcement** | ⚖️ **STILL HELD, awaiting the owner's decision.** Untouched since S2. Do not act on it in either direction. Full statement in *Open questions and obligations* below |

**Tests, measured 2026-08-06 (after F5's code): 1,494 passing, 0 failing, 1 skipped, `mvn clean
verify` — ⚠️ **`BUILD SUCCESS` read from Maven's own output, not from a wrapper's exit code**, which
this file has already recorded reporting 0 over a `BUILD FAILURE`. **247 operations, 231 schemas**,
**unchanged**: F5 ships screens, tests and one comment, and **no operation and no schema**. Frontend:
**402 across 41 files**, typecheck / lint / knip clean. F5 added **14** backend tests (1,480 → 1,494)
— 8 in `DocumentNumberReuseIT` and the search extensions, 6 in the new `F5WriteContractIT` — and
**34** frontend tests (368 → 402) across one new file and one extended one. Migration **V36** (4 GIN
trigram indexes).

*(The paragraph below is W1's, kept with its own figures — correct in its step's context.)*

**Tests, measured 2026-08-04 (after W1): 1,480 passing, 0 failing, 1 skipped, `mvn clean verify`
exit 0; 247 operations, 231 schemas, 175 declaring `required`. Frontend: 368 across 39 files,
typecheck/lint/knip/build/check:offline green.** W1 added **3** backend tests (1,477 → 1,480, all
three in the new `SerialisedRecordContractIT`), **no** frontend tests, and **no operations and no
schemas** — its whole spec diff is **58 properties added across 27 response schemas**, which is the
step: the document now describes what Jackson writes. Lint is at its pre-R2 baseline of 3 warnings,
all in files W1 did not touch.

*(The paragraph below is R2b's, kept with its own figures — correct in its step's context.)*

**Tests, measured 2026-08-04 (after R2b): 1,477 passing, 0 failing, 1 skipped, `mvn clean verify` exit 0; 247 operations, 231 schemas, 175 declaring `required`.
Frontend: 368 across 39 files, typecheck/lint/knip/build/check:offline green** — R2b added 8
frontend tests and a whole file (payment methods), plus `query-client.test.tsx`. Backend figures are
in the R2b section; ⚠️ **the first full run went RED and the failure is worth knowing**:
`WebExceptionMappingTest` caught that `PaymentMethodNotFoundException` and
`InvalidPaymentMethodException` were unmapped, so a controller throwing either would have answered
**500 instead of 404/422**. That rule exists for exactly this and found it before any screen did.
⚠️ **The background-task wrapper reported "exit code 0" for that run while Maven reported
`BUILD FAILURE`** — the wrapper's status was the trailing `grep`'s, not the build's, which is the
piped-build rule arriving from a new direction.

*(The paragraph below is R2's, kept with its own figures — correct in its step's context.)*

**Tests, measured 2026-08-04 (after R2): 1,470 passing, 0 failing, 1 skipped, `mvn clean verify`
exit 0; 237 operations, 226 schemas, 170 declaring `required`. Frontend: 358 across 37 files,
typecheck/lint/knip/build/check:offline green.** R2 added **13** backend tests (1,457 → 1,470) — 3 in
the new `DocumentReferenceGraphIT`, 7 in the new `R2ReferenceDataContractIT`, 3 in
`DocumentReferenceDataIT` — **48** frontend tests across **6** new files (310 → 358), and **7
operations** (230 → 237), all of them writes. ⚠️ **The 7 are a deliberate mid-step scope addition**
and not screen work: the *editable-while-unused* correction paths for a series' abbreviation,
document type and ΜΑΡΚ flag, and a delivery method's abbreviation — none of which had a write route
on any installation before. **No migration.** Lint is at its pre-R2 baseline of 3 warnings, all in
files R2 did not touch.

*(The paragraph below is R1b's, kept with its own figures — correct in its step's context.)*

**Tests, measured 2026-08-04 (after R1b): 1,457 passing, 0 failing, 1 skipped, `mvn clean verify`
exit 0; 230 operations, 223 schemas, 167 declaring `required`. Frontend: 310 across 31 files,
typecheck/lint/knip/build green.** R1b added **17** backend tests (1,440 → 1,457) — 9 in
`SalesInvoiceIT`, 8 in the new `R1bWriteContractIT` — and **no operations, no schemas and no change
to the `required` count**: its entire spec diff is **four lines on one schema** (`channel` →
`seriesId` on `NewSalesInvoice`). Migration **V33**, comment only. ⚠️ **That final run did not pass
`-Dnovocore.openapi.write=true`**, so `OpenApiSpecIT`'s drift check ran against the **committed**
spec rather than rewriting it — which is what makes "the spec in the repository matches the code" a
measurement rather than a claim.

*(The paragraph below is R1a's, kept with its own figures — correct in its step's context.)*

**Tests, measured 2026-08-03 (after R1a): 1,440 passing, 0 failing, 1 skipped, `mvn clean verify`
exit 0; 230 operations, 223 schemas, 167 declaring `required`. Frontend: 310 across 31 files,
typecheck/lint/knip/build green.** R1a added 59 backend tests (1,381 → 1,440) and **54 operations**
(176 → 230), across migrations **V31** and **V32**. *(The paragraph below is 8a's, kept with its
own figures — it is correct in its step's context.)*

**Tests, measured 2026-08-03 (after 8a): 1381 passing, 0 failing, 1 skipped, `mvn clean verify`
exit 0; 176 routes, 196 schemas. Frontend: 308 across 31 files, typecheck/lint/knip/build green.**
8a added 4 backend tests (1377 → 1381, all four in `MandatoryDeclarationRulesTest`) and **no routes** —
it changed what the spec *says* about existing operations, not which operations exist.
Counted from a local run on this machine. ⚠️ **Every other figure in this file is a
per-step count** — correct where it stands, wrong lifted out; this line is the current one. **F4 added 16** (1360 → 1376) and **no routes** — the two reference-data
lists gained a *parameter*, not an operation, so the spec diff is **14 lines added and 0 removed**. The PostgreSQL 17 client tools are installed here, so `BackupIT`'s 16
tests and the two backup legs run locally as well as on CI.

⚠️ **The 1 skip is `LiveSeedTest.seedTheLiveDatabase`, and it is deliberate**, not a regression: it
refuses to run without an explicit `-Dnovocore.seed.base-url`, because a seeder with a default target
is how one eventually points at something that matters. Earlier revisions of this file said "0
skipped"; that figure predates F0, which is the step that added this test.

⚠️ **This run is the first under the production database locale.** Until S1, Testcontainers used the
image default (`en_US.utf8`) while the real stack uses `--locale=C`, so every integration test in this
repository was describing a database nobody runs. Both are `C` now, and the whole suite is green under
it — nothing was depending on the permissive one, which is worth knowing because it means the pin
costs nothing to keep.

Step 16a added 36 (1152 → 1188) and four routes (133 → 137): `GET /api/me`,
`PATCH /api/me/language`, `POST /api/sales-invoices/preview`, `POST /api/credit-notes/preview`.
**S1 added 34 (1326 → 1360) and one route (174 → 175)** — the five list routes gained a *parameter* rather than an operation; the one new route is `PATCH /api/products/{id}/brand`. The spec diff is 108 lines of additions and **0 deletions**. **Step 16b added 138 (1188 → 1326) and 37 routes (137 → 174)** — 18 users/roles, 3 journal, 3
settings, 13 lookup administration. The OpenAPI spec was regenerated and the operation sets diffed
directly rather than trusting the line count: **0 removed, 37 added, 174 total.**
---

# ▶ The current step

**R4 is being built. Its checklist lives here, not in `HISTORY.md`, because rows still need verdicts.**
R2c sits with it: R2c is deferred and split, and its half 2b is attached to R4, so the two move to
`HISTORY.md` together at R4's close-out.

### ⚠️ R4's close-out gains a step, recorded now rather than relied on being remembered

**Under the U2a structure a step's close-out is four moves, not three.** CLAUDE.md's six close-out
actions are unchanged; this is what action 2 now means:

1. **Verdict every row in place** — here, where the section is.
2. ⚠️ **Extract the live residuals** to the standing lists below, before anything moves. **R4 has one
   already identified: consequence (b) — the unmapped AADE articles for `ACS_COD`, `PAYPAL` and
   `STRIPE`.** It is the owner's question or his accountant's, and it goes to *Waiting on the
   accountant* below. **Nothing is built on a guess.**
3. **Move the closed section to `HISTORY.md`**, at the top, and add its index row.
4. **Flip its row in the single status table above.**

⭐ **R4's close-out is U2b's M2 positive control, executed for real rather than against a fixture.**
M2 asserts a step id has a `##` section in exactly one file; R4 is the first step to cross that
boundary after the guard exists, so the guard is proven by the operation it guards rather than by a
contrived case.
## ▶ R2c — ⚠️ **DEFERRED AND SPLIT by the owner, 2026-08-06. Neither half is scheduled as R2c**

**The decision, in the owner's terms: R2c is not core work and must not interrupt the core.** The
roadmap row was **demoted 🟡 → ⚪ and moved out of the sequence block**, and the two defects were
re-homed. ⚠️ **The demotion IS the decision being applied, not a side effect of moving the row** —
the same distinction recorded that morning when the row was *promoted*, stated again here because a
record that only ever explains promotions is a record that reads as if demotions are automatic.

| Half | Where it went | Reason, recorded at the destination |
|---|---|---|
| **2a** — the sort code is **not a visible column** on the lists | **F10**, the *display defects deferred from earlier steps' live legs* list | ⚠️ **Cosmetic**, and that is established rather than assumed: the **ordering is correct** (owner-confirmed), so only R2b's 3.6 — *first list column* — did not land. F10's list exists precisely for a defect the owner saw and chose not to stop for |
| **2b** — the sort code is **absent from the series EDIT form** | **R4** | ⭐ **R4 rebuilds payment methods around which fields are editable and until when** — the same question R2b's §3.4 answered for the sort code when it **exempted** the field from the in-use freeze. The code is open and the reasoning is already loaded. **Doing it at F11 would cost somebody re-learning why the freeze has an exemption** |
| 📌 The **unverified half of 2a** — ordering was confirmed on **document types** and never checked on the **series** lists | **R4, alongside 2b** | It is a **behaviour** check, not a display one, and R4's 2b work opens the series screens anyway. **The split from 2a is deliberate and is stated rather than left implied** |

⚠️ **F11 was nearly chosen as 2a's home, and it is the wrong place** — worth recording because the row
title invites it. **F11 is "Whole-system UI regression" and nothing anywhere scopes it**: no brief
section, no footnote, no checklist, no estimate. Parking a known, specific, already-diagnosed defect
on an unscoped batched step is how it stops being tracked.

📌 **The row survives as the single place naming both defects and their destinations.** It is **not a
schedulable item any more** and must not be picked up as one.

*(Everything below is the original 2026-08-05 record, kept because it is the diagnosis both halves
carry with them.)*

**Found by the owner's live leg of 2026-08-05, against R2b's §3.** Recorded here at the moment it was
reported, per `CLAUDE.md` §*A decision reached in a design conversation gets the same close-out
discipline as a build step*. ⚠️ **These are R2b's defects, not F5's, and they must not enter F5's
commit.**

| # | Defect | What is and is not established |
|---|---|---|
| **2a** | **The sort code is not visible as a column on the document type lists** | ⚠️ **Display only.** The **ORDERING is correct** — the owner confirmed rows come back in sort-code order — so R2b's 3.5 (the server's default order) holds and only 3.6 (*first list column*) did not land. 📌 **He confirmed ordering for DOCUMENT TYPES and said nothing about the SERIES lists. Verify those; do not assume they match** — R2b changed four repositories and the leg exercised two screens |
| **2b** | ⚠️ **THE MORE SERIOUS ONE. On sales and purchase SERIES, the sort code appears only on the CREATE form and is ABSENT from the EDIT form** | **Document types allow editing it, which is why L.9 passed** — the passing path and the broken path are different screens, and only one was walked |

### ⚠️ Why 2b is not cosmetic, written down because a reader will otherwise treat it as such

**R2b's 3.4 decided the sort code is freely editable and deliberately NOT subject to the
editable-while-unused freeze**, and gave the reason: *"reordering is normal and the code appears on no
document."* **A value that can be set once and never changed is unusable for the purpose that argument
assigns it.** It is not a missing convenience — it is the field failing at the only job it has.

⚠️ **And it is on SERIES**, which is **the picker an employee actually uses when recording a
document** — F5's mandatory series picker is ordered by exactly this column. A business that
reorganises its series has no way to reorder the list it works from every day.

📌 **The shape is one this file already names:** the create path and the edit path disagreed, and the
one that was walked is the one that worked. Same shape as R2's *"the screen was the only guard"* and
as R2b's own §2 — **two paths, one exercised.**

## ▶ R4 — payment methods are a BUSINESS list, not a statutory one. 🟡 **CURRENT from 2026-08-06 — Phase 0 commissioned, NOT BUILT**

⚠️ **The owner's L.10/L.11 results are NOT defects.** The screens did what R2b's rows asked. **What
the rows asked for is the wrong model** — and it is **R1a's correction repeating one entity over**:
payment methods were built as a **seed-only statutory list**, and they are actually a **business list
that REFERENCES an AADE codification**, which is the same two-layer shape `CLAUDE.md` §5 already
records for document types.

**What the owner requires:**

| # | Requirement |
|---|---|
| **R4.1** | **The list starts EMPTY. No seeded rows.** The user creates them freely — full CRUD, not activate/deactivate/describe |
| **R4.2** | Creating one includes **selecting the AADE payment-method article**, which supplies the myDATA code |
| **R4.3** | ⚠️ **CONFIRMED BY THE OWNER: creating one ALSO means choosing THE LEDGER ACCOUNT IT SETTLES TO.** Two POS terminals can share AADE code 7 and land in **different bank accounts**, and **the AADE article cannot tell you which** |
| **R4.4** | **All fields editable for as long as the method HAS NOT BEEN USED** — the editable-while-unused / frozen-once-used pattern R2 already built for a series' abbreviation, `documentTypeId` and `getsMark`. ⭐ **The mechanism exists; this is a third application of it, not a new idea** |

### ⚠️ Four consequences, each of which CONTRADICTS something already decided

**Recorded together because a reader who meets only one of them will re-derive the old answer**, which
is precisely how R1a's two-layer correction had to be made twice.

| # | Consequence |
|---|---|
| **a** | ⚠️ **R2b §4.1 decided NOT to store the myDATA code on the row**, exposing it from the `SettlementMethod` enum instead, on the grounds that storing it would be *a second record of one thing*. **That reasoning held only while the rows were a fixed set.** A **user-created** row cannot inherit a code from an enum it is not a member of. **The code moves onto the row.** 📌 The old argument was not wrong — its premise was withdrawn |
| **b** | ⚠️ **THIS CHANGES THE SALES INVOICE REQUEST CONTRACT.** `SettlementMethod` is a **Java enum** on `NewSalesInvoice`. If payment methods become user-created rows, the invoice's settlement reference **must become an FK to the table**. Every consumer of `newSalesInvoice.settlementMethod` — the generated TypeScript included — changes with it |
| **c** | ⭐ **R2b's own argument AGAINST creation is not refuted — it is the SPECIFICATION of the create form.** §4.7 said adding Cheque or Foreign bank account *"needs an `AccountSystemKey` and two behaviour flags"*, and offered that as the concrete reason there is no create path. **Those are the fields the form must collect.** The argument survives intact and changes sign |
| **d** | 📌 **The eight seeded abbreviations — ΜΕΤΡ, ΚΑΡΤ, ΤΡΑΠ, ΕΠΙΤ, ΑΝΤΙΚ, SKRZ, PPAL, STRP — were INVENTED, not chosen by the owner.** An empty list **removes** that problem rather than needing them made editable. ⭐ This is the same argument that shipped R1a's six document tables empty: *the fabrication is what the empty seed exists to prevent* |

### ⚠️ SEQUENCING — recorded, because the reason is not derivable from the row's position

**R4 runs AFTER F5's close-out and BEFORE F6.** Two reasons, and both are about cost rather than
preference:

- **It changes a contract (consequence b), so the longer it waits the more is built on the enum.**
- **Purchase documents settle too**, so **F6 should be built against the corrected model** rather than
  built against the enum and then reopened.

✅ **It does NOT block finishing F5.** F5's record form is a **test harness by decision**
(`CLAUDE.md` §1b), so revising it later is expected rather than rework. ⚠️ **Do not pre-empt any of
this inside F5** — F5 keeps `SettlementMethod` exactly as it is.

📌 **Where R4 sits relative to N1 and the D-block is NOT decided.** The owner's constraint is
`F5 < R4 < F6`; the roadmap places R4 immediately after F5 to honour the *"the longer it waits"*
argument, and that placement is this session's reading of his reason, **not a fifth requirement he
stated.**

### ⏸️ HANDOVER — **stopped at a clean point 2026-08-06, on branch `r4-payment-methods`. NOT compacted through**

⚠️⚠️ **CORRECTED 2026-08-06, AND THE CORRECTION IS THE POINT. This block used to read: *"Stopped on
the owner's standing instruction — if you approach your context limit, stop at a clean point and
write a handover."* THAT IS NOT WHY THE SESSION STOPPED.**

**The real reason:** the session judged **the remaining work larger than the work already done** —
the `core-api` layer, deleting `SettlementMethod` across 96 sites, spec and client regeneration,
three frontend screens, the test sweep — and chose a clean handover over a partial build. **That is a
good reason to stop.** It is not the owner's trigger, and it was reported as though it were. ⚠️ **The
"roughly 60% of context used" figure behind it was a guess acted on as a measurement**; there is no
counter to read.

**Why correcting it is worth more than being accurate.** ⚠️ **A trigger that fires for the wrong
reason stops carrying information.** The two stops need different responses and the record has to
tell them apart:

| The stop means | What the next session should do |
|---|---|
| **"I ran out of room"** | Nothing about the plan is in doubt — pick it up and continue |
| **"This is bigger than the estimate"** | ⚠️ **The estimate is now evidence.** Re-scope, split the step, or say it is a multi-session job |

**This handover is the second kind.** ⭐ **Read it as evidence about R4's size**, not merely as a
resumption point: the step was scoped at 26 sub-parts and six of them consumed a session.

📌 **It is recorded as S.3's third worked example in `CLAUDE.md`**, and it is the one that broke that
rule's own scoping paragraph — which had exempted *reasons* from needing a checkable referent, two
commits before a reason was the thing that went wrong.

🟢🟢 **2026-08-06 — BACKEND AND FRONTEND BOTH GREEN. C.1 IS DONE. Verdicts quoted:**

```
./mvnw clean verify        BUILD SUCCESS
  core-api 66 · core 806 · app 296 (1 skipped) · architecture 33 · 284 surefire
npx tsc -b --force         exit 0
npm test -- --run          41 files, 407 passed, 0 failed
npm run lint               exit 0 (3 warnings, the pre-existing baseline)
npm run knip               exit 0
npm run build              exit 0
```

**C.1 shipped:** the list has an **Add** control (FULL only), the create form collects abbreviation,
description, a **mandatory** AADE article, a **mandatory** account from
`/api/accounts/payment-method-targets`, and an **optional** sort code that is **omitted when blank**
rather than sent as `0`. The detail screen freezes every field but the sort code once `inUse`, as
`lockedReason` — shown, disabled, with the reason composed on the client because the backend
localises nothing.

⚠️ **Three tests were DELETED rather than adapted**, with what replaced each stated in the file's own
javadoc: the no-create absence test (replaced by its opposite), the seed-only banner test (nothing
replaces it — a list with an Add button needs no explanation for a button it has), and the
*"draws an absent myDATA code as OPEN"* test (**nothing replaces it**: the article is mandatory, so a
method with no code cannot exist). **Frontend 402 → 407.**

*(The backend-only record below is kept for its verdict at the time.)*

🟢 **2026-08-06, EARLIER — THE BACKEND IS GREEN ACROSS EVERY MODULE. Verdicts quoted:**

```
./mvnw clean verify                 (every module, from clean)
[INFO] BUILD SUCCESS
  core-api ....... 66 tests     core ........... 806 tests
  app ............ 296 tests (+15, 1 skipped: the deliberate LiveSeedTest skip)
  architecture ... 33 tests     284 tests in the earlier surefire pass
```

✅ *(RESOLVED — see above.)* 🔴 **THE FRONTEND IS NOT GREEN: 5 failures of 402, all in `payment-methods.test.tsx`**, and they are
**C.1's file** — the screen still tests the seed-only model (`Open` for an absent myDATA code,
*"description and sort code, and nothing else"*, the no-Add convention's affordance assertions).
**C.1 was excluded from the session; those five are exactly its work and nothing else fails.**
`tsc -b --force` is **clean**, and `npm test` is **397 passed / 5 failed / 41 files**.

**Spec 247 → 257 operations, 231 → 237 schemas.** Client regenerated in the same commit.

*(The earlier record below is kept for its verdict at the time.)*

🟡 **2026-08-06, EARLIER — ALL MODULES BUILD AND RUN; THREE SPEC-DRIFT TESTS FAIL BY DESIGN.**

```
./mvnw clean verify          (every module, from clean)
[ERROR] Tests run: 294, Failures: 3, Errors: 0, Skipped: 0     <- app
[INFO]  BUILD FAILURE
```

⚠️ **The three are ALL of them, and all three read the COMMITTED SPEC:**
`OpenApiSpecIT.theSpecMatchesTheSurface`, and both
`SerialisedRecordContractIT.theSpecDocumentsExactlyWhatJacksonWrites` and
`…noRequestSchemaDocumentsADerivedProperty`. **Spec regeneration was excluded from the session that
made the routes change**, so the spec is stale on purpose and these three are the tests whose whole
job is to say so. **`core` is fully green (806 tests); `core-api` and `architecture-tests` are green.**

⚠️ **THE OBJECTIVE AND THE EXCLUSION WERE IN CONFLICT AND THE EXCLUSION WON.** *"Green across all
modules"* is unreachable while `-Dnovocore.openapi.write=true` is off the table: the surface gained
routes, `OpenApiSpecIT` exists to fail when it does, and it names the fix in its own message. **The
build is not green and is not claimed to be.**

📌 **The single next action is the regen** — `./mvnw verify -Dnovocore.openapi.write=true`, then the
client, then a re-run. Nothing else is known to be failing.

### ✅ THE SORT-CODE ALLOCATOR IS CLOSED — one allocator, in production. *(The open record follows.)*

**`NewPaymentMethod.sortCode` is `Integer` and nullable; null means *append at the end*, and
`PaymentMethodServiceImpl` resolves it to `max + 10`. All four test-side allocators are deleted.**
⚠️ **The UNIQUE constraint stays and was not dropped quietly:** the column is what a picker is ordered
by, so two rows sharing a code makes the order between them arbitrary and the list shuffles between
requests. Defaulting fabricates nothing — `V34` already records that *a sort code has no truth value*
until somebody chooses one, so supplying *at the end* declines to make the caller invent an answer
rather than inventing one.

### ⚠️ *(SUPERSEDED)* THE SORT-CODE COLLISION IS NOT CLOSED. It was FOUR allocators

**Reported as a strategy question rather than as four fixed failures, because fixing the failures did
not fix the cause.** Sort codes are `UNIQUE` on `payment_method`, and the allocators are:

| Where | Strategy |
|---|---|
| `PaymentMethodFixture` (core) | `max + 10` |
| `PaymentMethodIT` (core) | `max + 10` — **the same strategy, a second implementation** |
| `TradingQuarter` (app) | a shared `AtomicInteger` counter |
| `PaymentMethods` (app) | derived from a discriminator's hash, offset into 200 000+ |

⭐ **The two core-side ones now agree, which is what stopped the four failures.** They agree *by
having been edited to*, not by construction — **there is still no single allocator**, and the next
test class to author a payment method will pick whichever it copies.

**Why the app-side two have not collided:** the contract ITs run against their own database and
`TradingQuarter`'s counter is namespaced within one run. ⚠️ **That is an argument from the current
arrangement, not a guarantee** — it is the same shape as the *"identical for every existing row"*
justification `CLAUDE.md` warns about under R1b.

📌 **What it would take to close it:** one allocator all four call, or a non-unique sort code. **Not
done, and stated as open rather than left to be rediscovered by the next collision.**

*(The green record below was true of `core` alone and is kept for its verdict.)*

🟢 **RESOLVED 2026-08-06 — THE BRANCH IS GREEN. Verdict quoted, not summarised:**

```
./mvnw -pl core verify
[INFO] BUILD SUCCESS
[INFO] Tests run: 806, Failures: 0, Errors: 0, Skipped: 0
```

✅ **A.10's negative control RAN and passed.** With `requireActivePaymentMethod` removed:
`BUILD FAILURE`, **9 tests ran** in the nested class, **1 failed — exactly
`aDeactivatedPaymentMethodIsRefused` and nothing else.** Run in a throwaway worktree; the main tree
was verified clean afterwards. ⚠️ **The count is quoted with the verdict deliberately** — a targeted
run in this repository reported `BUILD SUCCESS` on zero tests earlier the same day.

*(The red record below is kept, because it is the worked example S.3's third instance rests on.)*

🔴🔴 **CORRECTED 2026-08-06 — THE BRANCH WAS RED, AND THE HANDOVER SAID IT WAS NOT.**

**It read *"the tree compiles and nothing is half-built."* The first clause is true and was checked.
The second is false and was not.** `V37` drops and recreates `payment_method` **without the `method`
column the `PaymentMethod` JPA entity still maps**, so Hibernate's schema validation fails at context
startup and **every integration test in `core` errors before running**:

```
SchemaManagementException: Schema validation: missing column [method] in table [payment_method]
```

⚠️ **The branch has been red since `56b06e7` and nothing knew, because the suite was never run after
the migration landed.** A schema change is **not verifiable by compilation** — Flyway runs at context
startup — so *"it compiles"* was a true statement doing the work of a false one. **Recorded in
`CLAUDE.md` under the stale-artefact family.**

✅ **The fix is not a puzzle; it is the work already scheduled.** The entity, repository, service and
controller must catch up with `V37`. **Until they do, no `core` integration test can run at all** —
which is also why R4's A.10 could not be proven this session (below).

| Commit | What |
|---|---|
| `8f1947d` | Part 1 — R2c deferred and split, R4 promoted, the chart-of-accounts decision (C1) |
| `5fffa4a` | The approved checklist written **at the moment of approval**, plus `CLAUDE.md`'s new anti-pattern (S.3) |
| `56b06e7` | **`V37`** — the whole migration, plus the AADE README's note 5 (**G.1**, **G.2**) |

**Sub-parts with a verdict so far: A.1 ✅ · A.2 ✅ · A.5 ✅ · G.1 ✅ · G.2 ✅ · S.3 ✅ (schema and
documentation halves).** ⚠️ **Every other row is untouched and still needs one.** A.6's *decision* is
encoded in `V37`; its **Java** half is not written.

#### ⭐ What the next session must not re-derive — the two findings that cost this one the most

1. ⚠️ **Annex 8.12's codes are NOT in any XSD.** `paymentMethods-v2.0.1.xsd` defines no code list;
   the type is a **range** (`xs:int`, 1–8) in `InvoicesDoc-v2.0.1.xsd`. `CLAUDE.md`'s *"codes come
   from the XSD enumerations"* **has no safe side here** — both halves come from the annex.
   ✅ **Already read and seeded: code `3` is `Μετρητά`.** Full note in `docs/aade/v2.0.1/README.md`
   §5. 📌 **`pdftoppm` and ImageMagick are absent on this machine and the Read tool's PDF rendering
   therefore fails; PyMuPDF (`import fitz`) is installed and works.**
2. ⚠️ **The inactive-payment-method guard has NO automated test**, and `PROGRESS.md` says it is
   verified in `R2ReferenceDataContractIT`, which has no payment-method case at all. That is **A.10**
   and **S.1**, and it is one of `CLAUDE.md`'s two new worked examples.

#### ⚠️ A.10 — WRITTEN, COMMITTED, AND **NOT PROVEN**. Its negative control is still owed

**The test exists** (`SalesInvoiceIT.aDeactivatedPaymentMethodIsRefused`, in `@Nested
TheSeriesDecides`): it records a sale, deactivates `ON_ACCOUNT`, asserts **both `record` and
`preview`** refuse with *"is inactive" / "not for new documents"*, asserts the **already-recorded**
invoice still reads and still names the retired method, and restores in a `finally`.

🔴 **It has never executed.** The branch is red (above), so the context cannot start. ⚠️ **Do not
read its presence as coverage** — the whole point of A.10 was that a guard without a test had been
recorded as tested. **The first thing to do once the entity catches up is run it, then prove it red
against the removed guard.**

⚠️ **And the proof attempt found a NEW hole in the build, worth more than the attempt:**
`-Dit.test='SalesInvoiceIT#aDeactivatedPaymentMethodIsRefused'` reported **`BUILD SUCCESS` while
running zero tests** — the method is in a `@Nested` class, so the pattern's **class half matched** and
`failIfNoSpecifiedTests` did not fire, because **it asserts the pattern matched, not that anything
ran.** Use `-Dit.test='SalesInvoiceIT'` or the whole suite, and **always grep the output for the class
name.** Recorded in `CLAUDE.md`.

#### The next action, in order — nothing here is a decision, all of it is execution

1. **`core-api`** — `AadePaymentMethodView` + service on the `StatutoryCodification<V>` contract;
   `PaymentMethodView` rewritten (surrogate `id`, `accountId`, `aadePaymentMethodId`, `inUse`,
   ⚠️ **`settlesImmediately()` derived from `accountId != null`**, no `subjectToCashLimit` field);
   `NewPaymentMethod`; `NewSalesInvoice.settlementMethod` → `@Mandatory Long paymentMethodId`.
2. ⚠️ **G.1's Java half is the one thing still needing a judgement, and the input is now in hand.**
   The cash limit derives from the method's AADE article being **code 3**, as a **named constant with
   the rasterised-read citation** — deliberately **not** a column on the codification, because the
   €500 limit is *Greek law as Novocore applies it*, not an attribute AADE published, and the
   codification must stay a faithful copy. 📌 **State the residual rather than closing it: a method
   with a NULL article gets no limit** (G.2 makes that reachable), so `subjectToCashLimit` is
   `article == 3`, and a cash-like method created with no article is a hole. **Report it to the
   owner; do not invent a second signal to plug it.**
3. **`core`** — entity, repository, `PaymentMethodServiceImpl` (+`create`), controller (+create and
   the four edit routes), `SalesInvoiceServiceImpl` (`requireActivePaymentMethod`, the posting branch
   now reading `account_id` instead of `AccountSystemKey`), `CreditNoteServiceImpl`.
4. **Delete `SettlementMethod` and `SettlementMethodMydataCodeTest`** (B.2) — ⚠️ **96
   `SettlementMethod.<CONST>` sites across 8 backend test files**, measured; `SalesInvoiceIT` alone
   has 38. Expect a compile-driven sweep exactly like R1b's 54 construction sites.
5. **A.9, A.10** — extend `DocumentReferenceGraphIT` with `payment_method`; write the missing guard
   test **and prove it red against the removed guard first**.
6. **Spec + client regen (B.3)**, then the frontend (C.1–C.4, D.1, D.2).
7. **A.3's reset** — `docker/reset-trading-data.sql` must be **re-runnable** and must leave R4's
   live-leg preconditions re-establishable. ⚠️ **L.0 has to say what the owner clicks**, or **L.9 and
   L.10 cannot run**.

📌 **`V37` is proven but NOT YET APPLIED to the dev database** — it was validated in a rolled-back
transaction. The live schema is still pre-R4, and the app image is current as of `8f1947d`.

### 📋 THE APPROVED BUILD CHECKLIST — **written at the moment of approval, 2026-08-06, before any code**

**Per `CLAUDE.md` §*An approved proposal is a checklist, not a paragraph*.** Every row gets a verdict
at close-out: **done** (and how it was verified), **explicitly deferred** (with the reason and where
it is recorded), or **still open**. ⚠️ **A sub-part with no verdict is a finding.**

#### ⭐ The three decisions the owner answered at approval — recorded before the rows that depend on them

| # | Decision | The reasoning, recorded because it is not derivable from the code |
|---|---|---|
| **A.6** | ⭐ **`settlesImmediately` is DERIVED. It is `account_id` being present, and is not a field** | **An account on the method means the money is already there; that is what immediate settlement *is*.** `SettlementMethod`'s own javadoc already said so — *"a method settles immediately **because** there is an account the money is already in"* — so two fields would be **one fact said twice**, and two fields that can contradict each other |
| **C.4** | ⭐ **ONE description, in Greek. The 16 `SettlementMethod.*` i18n entries go and NOTHING replaces them** | ⚠️ **Recorded as a GENERAL RULE, not a payment-method decision:** **nothing else the business authors is bilingual** — customer names, product names, series abbreviations and descriptions are all a single Greek string. **The i18n layer is for Novocore's own vocabulary** — labels, statuses, refusal messages — **not for the business's own data.** Written where a future reader meets it *before* asking the same question about `SalesChannel` or write-off reasons |
| **A.3** | ⭐ **RESET the trading data. Do not backfill** | **Backfilling would invent payment methods nobody authored**, which is the thing R4 exists to prevent — the same argument that shipped R1a's six tables empty |

#### ⚠️ A.6 KILLS A.5, and the checklist may not hold both

**A.5 originally proposed `account_id` be `@ConditionallyMandatory` — required iff the method settles
immediately.** Once *"settles immediately"* **means** *"has an account"*, **that condition tests the
field against itself and can never fail.** A guard that cannot fire is worse than no guard: it reads
as enforcement and is decoration.

⚠️ **`@ConditionallyMandatory` is therefore CONSIDERED AND REJECTED, and this line exists so nobody
adds it back.** `account_id` is simply **optional**, and **its presence carries the meaning**.

#### ⚠️ 2026-08-06 — A.5, A.6 AND G.2 ARE REVERSED BY THE OWNER. Recorded before building

**The rule: A PAYMENT METHOD CANNOT BE CREATED WITHOUT BOTH AN AADE ARTICLE AND A RECONCILIATION
ACCOUNT.** Both mandatory — confirmed explicitly against the weaker *"at least one of the two"*
reading. ⚠️ **Both reversals are also written at the columns in `V37`**, because that is where the old
reasoning was and a reader who meets only the old argument will re-derive it.

| Was | Is now | Why the new one is BETTER, not merely different |
|---|---|---|
| **A.5** — `account_id` **optional**, `@ConditionallyMandatory` rejected | **MANDATORY** | ⭐ Under A.5, Επί πιστώσει carried a **null**, and the posting code read that null as *"debit Accounts receivable"* **by convention**. The account was real; it was unnamed, and the interpretation lived in Java. **Now Επί πιστώσει NAMES Accounts receivable explicitly. No null means anything, and the posting rule reads the account the method names — in every case, with no branch.** ⚠️ **`@ConditionallyMandatory` is still rejected, for a better reason: there is no condition left.** It was previously rejected because *"required iff it settles immediately"* tested the field against itself; now the field is unconditionally required, which `@Mandatory` says plainly |
| **A.6** — `settlesImmediately` derives from `account_id` **being present** | **Derives from the ACCOUNT'S KIND** | Presence no longer discriminates anything: **every** method has an account. `BANK_CASH` or `PARTNER_CLEARING` ⇒ the money is already somewhere ⇒ **born settled**; the Accounts receivable **CONTROL** account ⇒ it is not ⇒ **an open item** until a Receipt allocates. ⭐ **Still one fact, still derived, still no stored flag — derived from a different place** |
| **G.2** — the AADE article **nullable**, on R1a's precedent | **MANDATORY** | ⚠️ **The R1a analogy was considered and does not hold.** A **document type** with no AADE code is **a real thing the business issues** — Προσφορά, Δελτίο Αποστολής, Παραγγελία are operational documents that genuinely are not tax documents. A **payment method** with no article **is not a real kind of thing; it is an incompletely specified row.** The null would not record *"this has none"* but *"nobody has said yet"* — the two meanings a nullable column cannot tell apart |

⭐ **And G.1's residual is now CLOSED, by making the model complete rather than by adding a second
signal.** The cash limit derives from the article being code 3; while an article could be absent, a
cash-like method created without one would silently escape the statutory limit. **That hole is
unreachable now.** It was the outcome this session declined to fake with an "or the account is the
cash account" fallback, and requiring the article is what actually removed it.

##### ⚠️ Consequence (a) — the account picker widens, and the answer is a SECOND ROUTE

`GET /api/accounts/settlement-targets` returns `BANK_CASH` and `PARTNER_CLEARING` only
(`AccountKind.isSettlementTarget()`). **Accounts receivable must now be selectable.** Three ways were
available and two are wrong:

| Option | Verdict |
|---|---|
| **Change `AccountKind.isSettlementTarget()`** | ❌ **REJECTED, and it would be a real defect.** That predicate means *"a Receipt, Payment or Bank Transfer may name this as its money side"* and `NewSettlement.accountId` is validated against it. Adding AR would let **a Receipt settle into Accounts receivable** — allocating a receivable against itself. **One predicate, two questions, and only one of them is asking about payment methods** |
| **Filter at the screen** | ❌ **REJECTED — it is literally *the screen was the only guard*.** An adapter or a `curl` would face no rule at all |
| ✅ **A second route + a service-side guard** | **CHOSEN.** *"Which account may a payment method reconcile to"* is a **different question** from *"which account may a Receipt name"*, so it gets its own answer: `activePaymentMethodTargets()` = active settlement targets **plus** the `ACCOUNTS_RECEIVABLE`-keyed account, exposed as `GET /api/accounts/payment-method-targets`, with `PaymentMethodServiceImpl` refusing anything outside that set on create **and** change |

⚠️ **Live-leg row L.5 is REWRITTEN.** It read *"the picker offers only bank, cash and
partner-clearing accounts — no Accounts Receivable"*. **That assertion is now exactly backwards.**
It becomes: *the picker offers bank, cash and partner-clearing accounts **and Accounts receivable**,
and offers nothing else — no Sales, no Inventory, no Output VAT.*

##### 🛑 Consequence (b) — HELD. The three unmapped methods need an article, and it is not mine to invent

**Annex 8.12's eight articles, from the rasterised read:** **1** Επαγ. Λογαριασμός Πληρωμών Ημεδαπής ·
**2** Επαγ. Λογαριασμός Πληρωμών Αλλοδαπής · **3** Μετρητά · **4** Επιταγή · **5** Επί Πιστώσει ·
**6** Web Banking · **7** POS / e-POS · **8** Άμεσες Πληρωμές IRIS.

**What each of the three would plausibly need, and the question each turns on — reported, NOT decided:**

| Method | Candidate articles | The question that decides it |
|---|---|---|
| **ACS cash on delivery** | **3** (Μετρητά) or **1** (Ημεδαπής) | ⚠️ **Does the article describe what the CUSTOMER did, or how WE received the money?** The customer pays the courier in cash; ACS remits to our bank. Both readings are defensible and they give different codes |
| **PayPal** | **2** (Αλλοδαπής), **1**, or **7** | Does a PayPal balance count as an *επαγγελματικός λογαριασμός πληρωμών* at all — and if so, PayPal Europe is Luxembourg-established, which points at **2** rather than **1** |
| **Stripe** | **2**, or **1** | Same shape; Stripe Payments Europe is Irish |

⚠️ **This is the owner's question or his accountant's, and NOTHING IS BUILT ON A GUESS.** The
codification seed and the create form do not depend on it — **only the owner's own first rows do**,
and he authors those. 📌 **It also has a statutory edge this session cannot judge:** a wrong article
is a misdeclared filing that looks successful, which is exactly what `requireMydataPaymentCode()`
already refuses to paper over.

#### 🅟 PHASE 0 — the cash limit is TWO thresholds, and the discriminator is NOT in the model

**Measured 2026-08-06 against the code, not read from a summary.**

| # | Question | What was measured |
|---|---|---|
| **a** | What does `requireWithinCashLimit` compare, against what? | ⭐ **GROSS — VAT-INCLUSIVE.** It takes `receivable`, which is `computedGross + rounding`, and `computedGross` is `Σ line.gross()` where `PricedLine.gross()` is literally `net.plus(vat)`. The constant is `SettingKeys.CASH_PAYMENT_LIMIT` (€500) and it refuses at **`>=`**. ⭐ **So the shipped guard is CORRECT FOR RETAIL and merely ABSENT FOR B2B — not wrong for both.** F5's live leg refusing exactly 500.00 and recording 499.99 was proving the **retail** rule, and it proved it correctly. ⚠️ **B2B is not unguarded, it is OVER-guarded**: the retail ceiling is applied to a B2B sale whose legal ceiling is €500 **net** + VAT — €620 at 24%. **Refusing legal sales is the safe direction to be wrong, and it is still wrong** |
| **b** | Can the guard reach the document type where it runs? | ✅ **YES — the owner's expectation is confirmed.** `compute(...)` resolves `SeriesContext series = resolveSeries(...)` as its **first** act, and `SeriesContext` carries `SalesDocumentTypeView documentType`. `requireWithinCashLimit` is called ~45 lines later. **The type is in scope; the guard simply does not take it** |
| **c** | What distinguishes ΑΛΠ from ΤΠΔΑ **in the model**? | 🛑 **NOTHING RELIABLE — and this is where I stop, per the instruction.** The nearest thing is `AadeInvoiceGroup`: ΑΛΠ is *Μη Αντικριζόμενα* (`ISSUER_UNMATCHED`), ΤΠΔΑ is *Αντικριζόμενα* (`ISSUER_MATCHED`), and the group is seeded on all 55 AADE types. **Three reasons it will not carry this rule:** (i) `sales_document_type.aade_invoice_type_id` is **NULLABLE**, so a business type may have no group at all; (ii) `SalesDocumentTypeView` exposes `aadeInvoiceTypeId` and `aadeInvoiceTypeCode` and **NOT the group**, so it is not reachable from `SeriesContext` today; (iii) ⚠️ **matched/unmatched is a myDATA REPORTING distinction being borrowed for a STATUTORY CASH-LAW purpose, and nobody has confirmed the two coincide.** ⚠️ **No field was added to make it work** |
| **d** | Any test against a B2B document? | ❌ **No — and there is nothing to test against.** Exactly one test touches the rule (`SalesInvoiceIT:473`, asserting `"legal cash limit"`), and with no retail/B2B distinction in the model it cannot be B2B-specific |

##### ➗ The split — and it matches the owner's expectation

- ✅ **R4 CARRIES the derivation and the constant.** R4 deletes the enum that held
  `subjectToCashLimit`, so the flag must land somewhere; **G.1's cited constant (article code 3) is
  R4's**, because R4 is what moves it. The guard keeps comparing **gross** against the existing
  setting, which is correct for retail.
- 🔴 **A NEW ROW carries the two-threshold rule** — roadmap **C2**. It needs a retail/B2B distinction
  that **does not exist in the model**, and inventing one inside a payment-method step is precisely
  what the instruction forbade. ⚠️ **R4 must not pre-empt it.**

#### The rows

| # | Sub-part | Verdict |
|---|---|---|
| **A.1** | `V37`: `payment_method` gains a surrogate `id`, an **optional** AADE payment-article reference and an **optional** `account_id` FK → `account`; `method` stops being the primary key. ⚠️ **Ships EMPTY** — the eight seeded rows go | |
| **A.2** | ⚠️ **Drop `sales_invoice_settlement_method_known`** and convert `sales_invoice.settlement_method` → `payment_method_id` FK. **The migration says at the constraint why a CHECK listing eight literal names cannot survive a user-created row** | |
| **A.3** | ⚠️ **Trading-data RESET, not a backfill.** ⚠️ **Two owner conditions:** it must be **re-runnable**, and after it R4's own live-leg preconditions must be **re-establishable** — a recordable series against an active document type, or **L.9 and L.10 cannot run**. 📌 **L.0 states what the owner must click** | |
| **A.4** | The **AADE payment-method codification** as a statutory layer, on the `aade_invoice_type` model: seed-only, the `StatutoryCodification` contract, **no create, ever** | |
| **A.5** | ⚠️ **REWRITTEN AT APPROVAL — `account_id` is simply OPTIONAL and its presence carries the meaning.** `@ConditionallyMandatory` considered and **rejected**: see the box above, because with A.6 the condition tests the field against itself and can never fire | |
| **A.6** | ⭐ **`settlesImmediately()` derives from `account_id != null`.** No column, no form field, no flag. The reasoning is written **at the code**, not only here | |
| **A.7** | `PaymentMethodService` gains `create`; abbreviation, description, AADE article, account and sort code all editable **while unused** | |
| **A.8** | The freeze: `inUse` on `PaymentMethodView` plus a `requireNothingRecorded`-shaped **server** guard answering 422 and naming the field. Copies the series semantics exactly — ⚠️ **a reversed invoice counts** ("recorded" is not "standing"), and the flag is a `boolean` because **the screen renders the reason** (Q47(b)) | |
| **A.9** | ⚠️ Extend **`DocumentReferenceGraphIT`** to pin `payment_method`'s referencing set, with the **failure message as the handover note** | |
| **A.10** | ⚠️ **The missing test** — recording an invoice settled by a **deactivated** method is refused with its reason. ⚠️ **Proven RED against the removed guard first**, and the run's own verdict read from Maven | |
| **B.1** | `NewSalesInvoice.settlementMethod` → `paymentMethodId` (`@Mandatory`); `SalesInvoiceView` / `CreditNoteView` carry the id and the description; ⚠️ **`bornSettled` comes off the row, not off an enum** | |
| **B.2** | `SettlementMethod` **deleted**, and `SettlementMethodMydataCodeTest` with it — its subject moves to the codification | |
| **B.3** | Spec and generated client regenerated; **every drifted fixture named**, as R2b's S.1 did | |
| **C.1** | Payment-methods screen: create form (abbreviation, description, AADE article picker, account picker, sort code); ⚠️ **the no-Add convention line and its absence test DELETED**; the freeze rendered as `lockedReason` | |
| **C.2** | ⚠️ The account picker reads **`GET /api/accounts/settlement-targets`** — **it already exists**; do not build a second one | |
| **C.3** | ⭐ **THE OWNER'S CALL, MADE: the invoice record form offers ACTIVE payment methods ONLY.** **On a form creating a NEW document there is no "current one" to preserve**, which is the whole thing R2b's active-plus-current-labelled pattern exists for — and **offering an option solely to refuse it afterwards is worse than not offering it.** ⚠️ **F5's contrary decision is SUPERSEDED, and it is said AT THE SITE OF F5's COMMENT** so a reader does not find two live arguments. ⚠️ **The server guard stays regardless — the picker is not the guard** | |
| **C.4** | ⭐ **One Greek description; the 16 i18n entries go.** The **general rule** written where a future reader meets it first | |
| **D.1** | ➕ **R2c 2b** — the sort code is settable on the sales **and** purchase series **EDIT** forms | |
| **D.2** | ➕ **R2c 2a's behaviour half** — verify the **series** lists are ordered by sort code. Confirmed only for document types; R2b changed four repositories and the leg walked two screens | |
| **G.1** | ⚠️ **`subjectToCashLimit` had DISAPPEARED from the checklist** — A.4 named three things read off the enum and only two were handled. It drives the statutory €500 refusal the owner's live leg exercised at exactly that value. **Establish where it goes.** Expectation to be **corrected if wrong**: it derives from the AADE article, not from a user-settable field — *a statutory limit nobody should be able to untick*. ⚠️ **Confirm which article denotes cash before building on it; do not assume a code number** | |
| **G.2** | ⚠️ **The AADE article reference MUST BE NULLABLE**, and the checklist did not say so. **Three of the eight have no myDATA code** (`ACS_COD`, `PAYPAL`, `STRIPE`) — **R1a's finding again**, where six of nineteen document types carry no AADE code. A user-created method may legitimately have no statutory article: the reference is optional, the create form may leave it empty, and **the consequence for transmission is recorded against step 29 rather than resolved here** | |
| **G.3** | ⭐ **R4 is establishing a PATTERN, not just fixing payment methods.** Record against **`SalesChannel`'s existing open decision** that R4's shape is the template for promoting an enum-that-carries-accounts into a table: **surrogate id, optional statutory codification reference, direct FK to `account`, freeze-once-used.** ⚠️ **Report only — do not touch `SalesChannel`** | |
| **S.1** | `CLAUDE.md` §5's payment-method paragraph, `PROGRESS.md`, the primer and the roadmap — R4 closed, and ⚠️ **the A.3 evidence claim corrected** (`PROGRESS.md` says the inactive-method guard is verified in `R2ReferenceDataContractIT`; it is not) | |
| **S.2** | 📌 `SalesChannel` recorded as the same shape against its open decision. **Report, do not fix** | |
| **S.3** | ⭐ **A new named anti-pattern in `CLAUDE.md`: a claim recorded at close-out is a CLAIM.** A claim about a file or a test is proved by **grepping it**, not by remembering writing it. **Two worked examples, both from the last two steps** — F5's B.4 note and R4's Phase 0 finding about `R2ReferenceDataContractIT` | |

### 🟡 2026-08-06 — R4 IS CURRENT, and two things arrived with the promotion

**The owner deferred R2c out of the sequence and commissioned R4's Phase 0 in the same instruction.**
⚠️ **The promotion ⚪ → 🟡 is that decision being applied, not a consequence of R2c vacating the
slot** — position and status are different claims, and a step whose Phase 0 has been asked for is a
step being worked on. **The gate is unchanged and was always the binding one: before F6.**

**➕ R2c's 2b is now R4's, and the reason is recorded rather than assumed.**

| Item | What |
|---|---|
| **R4.5** | **The sort code must be settable on the sales and purchase SERIES EDIT form**, where today it appears only on the create form. ⭐ **It attaches here because R4 rebuilds payment methods around exactly one question — which fields are editable and until when** — which is the same question R2b's §3.4 answered for the sort code when it **exempted** that field from the in-use freeze. **The code is open and the reasoning is loaded.** At F11 it would cost somebody re-learning why the freeze has an exemption |
| **R4.6** | 📌 **Verify that the SERIES lists are ordered by sort code.** The owner confirmed ordering on the **document type** lists and said nothing about series. R2b changed four repositories and the leg exercised two screens. **It rides with R4.5 because it is behaviour, not display, and 2b opens those screens anyway** |

⚠️ **These two are RECORDED, not approved.** They are R2c's content re-homed by the owner's
instruction; they still need a verdict line in R4's approved checklist when R4 is scoped.

**➖ A prerequisite R4 might have had is GONE — the chart of accounts is decided (2026-08-06).**

**R4.3 requires choosing the ledger account a payment method settles to**, which raises the question
*which chart is being picked from*. The owner has settled it: **Novocore uses the official Greek chart
directly, with a display alias per account, and there is NO separate business chart mapped onto the
official one** — recorded under *Step 3* below and as roadmap row **C1**.

- ✅ **So R4's account picker offers accounts from the one chart that exists**, and nothing in R4 waits
  on a second layer that will not be built.
- ⚠️ **The alias field does NOT exist on an account today** — measured 2026-08-06 against `Account` and
  `AccountView`, both of which carry `code`, `name`, `type`, `kind`, `subLedgerType`, `systemKey`,
  `group`, `displayOrder`, `active`, `expectedToClear`, `elpCode` and nothing else. **Not built, per
  the owner's instruction**, and recorded against C1, the row that will need it.
- ⚠️ **R4 must not add it.** An alias is a chart-of-accounts field, not a payment-method one, and
  building it inside R4 would put a chart decision in a payment-method migration.

---

## 🚫 Pre-launch blockers

Not open questions — **decided, and deliberately unresolved for now, with a condition attached.**
These must be closed before the stated trigger, not merely before Phase 1 ends.

### PLB-1 — No 2FA. Blocks any external or remote access. (Q30)

**Decision: no 2FA for now**, because the application is not internet-facing. That is a deliberate
choice with an explicit condition, not a deferral by default.

**This must be revisited and resolved before *any* external or remote access is enabled**,
including:

- exposing NovoCore to the public internet (Caddy already obtains a publicly trusted certificate
  automatically once `NOVOCORE_SITE_ADDRESS` is a real hostname, so this is one environment
  variable away from being live);
- **Remote/Order Staff logging in from outside the local network** — which is the whole point of
  that role, so this trigger is likely to arrive sooner than a general public launch;
- any VPN-less remote access for an owner or admin.

Why it matters here specifically: a full-access role can reach every financial record in the
system, and the only thing standing in front of it is one password. Session cookies are hardened
(`HttpOnly`, `Secure`, `SameSite=Strict`), which addresses cookie theft but does nothing about a
stolen or reused password.

Scope when resolved: TOTP is the obvious candidate, and the decision needs to cover whether it is
mandatory for full-access roles only or for everyone, plus recovery codes — a second factor with
no recovery path locks the owner out of their own financial system.

---

## ⚠️ To be aware of immediately

1. **`docker/.env` is gitignored and machine-local, and holds exactly three variables**:
   `NOVOCORE_DB_PASSWORD`, `NOVOCORE_SITE_ADDRESS` and `NOVOCORE_BACKUP_ENCRYPTION_KEY`. Every
   one-time bootstrap variable has been removed once consumed — the three from step 11 (see item 3)
   and the four Drive OAuth secrets, which now live in Settings. A fresh clone must run
   `cp docker/.env.example docker/.env` and set `NOVOCORE_DB_PASSWORD`, or nothing starts. This is
   deliberate — there is no fallback password anywhere.

   **The encryption key is the one variable that is not a hand-off and never goes away.** It is read
   on every backup and every restore, and it cannot move into Settings because the `setting` table is
   inside the dump. It **is** recorded in a password manager as of 2026-07-29, which is what makes
   the backups meaningful rather than decorative.

   ⚠️ **The `.env` copy is 43 characters, not the 44 it was generated as** — the trailing `=` base64
   padding was dropped in transit. This is harmless and was checked rather than assumed: both forms
   decode to **byte-identical** 32-byte key material, and Java's `Base64.getDecoder()` accepts
   unpadded input. So a restore works with either copy. Recorded because a future reader comparing
   the password-manager entry against `.env` will notice the difference and should not conclude the
   key was corrupted.
2. **A fresh machine also needs the toolchain**: JDK 25 and a Docker daemon. Maven is not
   required — `backend/mvnw` is committed. `mvn verify` needs Docker for the `*IT` tests;
   `mvn test` does not.
3. **The first Owner account exists: `kostas`, and its password has been rotated once.** Created
   2026-07-28 by `InitialOwnerBootstrap`, then changed the same day through
   `UserService.changePassword` — the real service, so the password policy, the delegating encoder
   and the `user.password-changed` audit entry all applied, exactly as they would if a screen
   existed. **The owner holds it** — they signed in with it to run the S1, S2 and F4 browser checks
   personally on 2026-08-01. It is deliberately **not** in this repository, which is why those checks
   are the owner's to run rather than a session's.

   ⚠️ **Corrected 2026-08-02.** This entry previously read *"the current password lives only in the
   chat session that generated it"*, which was both alarming and wrong, and it had been carried
   forward into the roadmap as an obligation about a lost credential. **The real gap is narrower and
   is still real: there is no change-password screen and no recovery path.** Rotating it means the
   same one-off route — `UserService.changePassword` against the live database — and there is nothing
   an owner who forgot it could do from the application. That, not a missing copy, is what has to be
   built before anyone else depends on this account.
4. **All three one-time bootstrap variables have been removed from `.env`**, having served their
   purpose: `NOVOCORE_SMTP_PASSWORD` (consumed into the `smtp.password` setting) and
   `NOVOCORE_BOOTSTRAP_OWNER_USERNAME` / `_PASSWORD` (consumed into the user table). The app was
   recreated without them and starts clean. **The SMTP password now lives only in the database** —
   changing it means changing the setting, not the environment.
5. **⚠️ `docker/.env` has a UTF-8 BOM and CRLF line endings.** This cost real time: a
   `grep '^NOVOCORE_DB_PASSWORD='` silently matched nothing, because the BOM sits between the start
   of the file and the first key, so the value came back empty and the failure looked like a
   password problem. Docker Compose copes with both. **Anything else reading this file must strip
   the BOM and the CRs** — `sed '1s/^\xEF\xBB\xBF//' docker/.env | tr -d '\r'`.
6. **⚠️ A test cannot be pointed at a non-Testcontainers database just by setting
   `spring.datasource.*`.** `PostgresTestContainerConfiguration` lives in
   `..core.testsupport..`, which is inside the package `CoreTestApplication` component-scans — and
   because that `@ComponentScan` is declared explicitly, it does **not** carry Boot's
   `TypeExcludeFilter`, which is what normally keeps `@TestConfiguration` classes out of a scan.
   So the container bean is registered by scanning, and its `@ServiceConnection` **overrides any
   datasource properties the test sets**.

   Two consequences. First, `AbstractCoreIntegrationTest`'s `@Import` of that configuration is
   **redundant** — the container would be there regardless. Second, and the reason this is a
   warning rather than a curiosity: a test that sets a datasource URL gets a container anyway and
   **reports the URL it asked for while talking to somewhere else**. It cost real time during the
   owner password rotation, where the symptom was an empty user table on a database that
   demonstrably had a user in it. The diagnostic that settles it in one line is
   `SELECT current_database()` — a Testcontainers PostgreSQL answers `test`.

   To genuinely reach another database, declare a boot configuration that excludes
   `..core.testsupport..` from the scan, and put it in the `gr.novotrade.novocoretest` sibling
   package for the reason `CoreTestApplication` documents at length.

---

## Verified working

- `mvn clean verify` green, Java 25 enforced by maven-enforcer.
- Docker Compose stack (`docker compose -f compose.yml -f compose.dev.yml up --build`): all
  three containers healthy, PostgreSQL gating the app's start via its healthcheck, Flyway
  applying migrations.
- HTTPS through Caddy at `https://localhost` with HSTS and an HTTP→HTTPS 308 redirect.
- `/actuator*` blocked at the proxy (empty-body 404 from Caddy) while reachable internally —
  checked by comparing response bodies, not just status codes.
- ArchUnit rules proven to actually fail: a probe class with a `double` field tripped all three
  money rules before being deleted.
- **`SchemaConventionsIT` proven to actually fail**, same method: a temporary migration adding
  `double precision`, `real`, `money`, `numeric(10,4)` and unbounded `numeric` columns tripped
  both rules and named all five, while correctly ignoring the `numeric(19,2)` and
  `numeric(19,6)` columns alongside them. Probe deleted.
- **The chart-of-accounts invariants are enforced by the database, not only by Java** — proven
  by raw-SQL probes in `ChartOfAccountsIT` that bypass the service and are rejected by CHECK
  constraints. Same for the VAT class rules in `VatClassIT`.
- **The currency-companion rule proven to actually fail**, same method: a temporary migration
  adding a `probe_money` table with a `numeric(19,2)` column and no `_currency` companion tripped
  `SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency`, naming the offending column, while
  correctly ignoring a properly paired column in the same table. Probe deleted.
- **Hibernate's `ddl-auto: validate` caught a real mismatch during step 5** — the entity mapped
  `selling_price_currency` as a `varchar` against a `char(3)` column and the context refused to
  start. Fixed with `@JdbcTypeCode(SqlTypes.CHAR)` rather than by widening the column. Worth
  recording because it is the first time that setting has earned its keep.
- **The `..core.web..` boundary rule proven to actually fail**, same method: a probe class in
  `..core.web..` referencing a public core-internal class tripped it, naming both the offending
  field and constructor parameter. Probe deleted. Its `allowEmptyShould` allowance is gone, so the
  rule can no longer pass vacuously.
- **Authentication end to end over real HTTP**: 401 unauthenticated, 403 for Remote/Order Staff,
  200 for the Owner, logout invalidating the session, CSRF enforced, and the session cookie's
  `HttpOnly` / `Secure` / `SameSite=Strict` asserted against the real `Set-Cookie` header.
- **The startup refusal when no user exists and no initial owner is supplied** — unit-tested,
  including the partial-credentials case.
- **The widened monetary-currency rule proven to actually fail**, same method again: a temporary
  `probe_cost` table with an unpaired `landed_cost numeric(19,6)` tripped
  `SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency`, naming exactly that column while
  correctly ignoring a properly paired `unit_cost`, a rate and a quantity in the same table. Probe
  deleted.
- **`CLAUDE.md` rule 6 is proven to be a database guarantee, not a service check.** Raw-SQL probes
  in `JournalIT` write straight to the tables and are refused: an unbalanced entry, an entry with no
  lines at all, a one-line entry, an entry spanning two currencies, a zero line amount, a deleted
  entry, a changed `source`, a dangling sub-ledger reference, and a VAT class on an account that is
  not a VAT account. **The balance probes use a `DO` block**, because the constraint is *deferred*
  and under autocommit each separate statement would be its own transaction.
- **Q13's correction policy is proven to be enforced in the database too** — the `UPDATE` and
  `DELETE` halves of `immutableSourcesAreRefused` bypass the service entirely — and **the two
  statements of the policy are proven to agree**: a test calls
  `journal_source_is_amendable(varchar)` for every value of the Java `JournalSource` enum and
  compares against `isAmendable()`, and a second test checks the `journal_entry_source_known` CHECK
  lists exactly the enum's values and no others.
- **The two-shapes-of-lot invariant is enforced by the database**, proven by raw-SQL probes in
  `InventoryIT` that bypass the service: a quantity with no location, a location with no quantity, a
  lot with more remaining than received, a negative remaining, a negative unit cost, a bundle that is
  also serial-tracked, a serialized service product, a self-referencing bundle component and a
  duplicate component are all refused by CHECK or UNIQUE constraints, each named in the assertion.
- **Step 10's landed-cost invariants**, by raw-SQL probes in `FreightAllocationIT`: a lot whose two
  cost halves name different currencies, a lot carried below what was paid for it, two allocation
  lines for one lot, and a second reversal of one allocation are all refused by the database.
- **The Inventory control account agrees with what the lots carry**, asserted directly rather than
  inferred — `inventoryLedgerPositionOf` sums the Inventory-side lines for one lot's sub-ledger
  reference and is compared against `remainingValue()` across every shape ADR 0011 examines. This is
  the assertion that found the defect ADR 0011 fixes, and the one that proves it fixed.
- **Step 8's purchasing invariants likewise**, by raw-SQL probes in `PurchaseInvoiceIT`: a duplicate
  supplier invoice number (case-insensitively, by trigger), an invoice line that is neither an
  inventory nor an expense shape, a line stating no VAT treatment, a second GR/IR match for the same
  pair, and a second lot claiming one delivery line. **And the consumption-source CHECK is held to
  `JournalSource.mayConsumeStock()`** the same way `journal_source_is_amendable` is held to
  `isAmendable()` — per value, and by counting the constraint's literals so a value added to the
  database alone cannot hide.
- **The whole system, played as one trading year and then swept** (step 13, `WholeScenarioIT`).
  After buying before and after invoicing, allocating freight onto partly-sold stock, decomposing a
  bundle, overselling, crediting stock back, settling both ways, reversing and writing off: **no
  entry in the database is unbalanced**, asked in raw SQL; the trial balance balances; every control
  account equals the sum of its own sub-ledger; GR/IR, both variance accounts, AR, AP and Inventory
  each agree with the documents behind them; and the whole thing restores into a fresh database and
  still balances there.
- **The value types and FIFO, over generated input rather than chosen examples** (step 13). The
  properties are listed in the step 13 section; the ones worth knowing exist are that `compareTo`
  agrees with `equals` on `Money`, that the currency guard holds on every operation, that
  `ProportionalAllocation` agrees with an independently written largest-remainder split, and that
  FIFO's allocation agrees with an independently written FIFO over twenty random histories.
- **The property runner itself is proven to fail** (`PropertyTest`), the same way the ArchUnit rules
  and `SchemaConventionsIT` are — a checker that is silently broken is worse than no checker.

- **A lot's carrying value and the Inventory control account agree by construction** (ADR 0015),
  at every point in a lot's life and for every unit cost, not only the whole-cent ones — and a fully
  consumed lot leaves exactly zero behind. Asserted both as worked examples with the measured
  numbers (`LotCarryingValueIT`, **proven to fail against the old formula**) and as properties over
  twenty generated histories per run (`FifoPropertiesIT`).

## Not yet verified

- ~~**Backup restore.**~~ **Closed, in code and in operation.** Proven in the suite and on CI
  (`5a6dfa5`) — a real `pg_dump`, a real `pg_restore` into a real scratch database, and an assertion
  that the restored ledger *balances* — and since 2026-07-29 the **whole regime runs for real**: an
  encrypted artefact produced by the deployed container and uploaded to both Google Drive accounts.
  Brief §13's long-standing "restore untested" risk is **closed**. The one part still not
  independently exercised is the **weekly restore check running against a downloaded off-site
  artefact** — the check has only ever restored from the local copy, which is the same file, so this
  is a thin residual rather than a gap.
- ~~**The REST surface is one read-only endpoint.**~~ **Closed — step 14 built 133 routes.**
  ~~**And nothing has driven them as a sequence.**~~ **Closed by step 15**: a trading quarter now runs
  entirely over HTTP and the ledger it produces satisfies every universal invariant. **128 of the 133
  routes are driven, and the remaining 5 are excused in writing** — `assertEveryRouteCoveredExcept`
  makes that an assertion, and it also fails on an excuse for a route that no longer exists or that
  was in fact covered, so the list cannot outlive what it describes.

  What is *still* unverified is narrower than it was, and worth stating precisely: **no human has used
  a browser.** The frontend has no login screen and calls none of this. Every route that runs, runs
  because a test asked it to.
- ~~**Nobody has logged in.**~~ **Done, against the running Compose stack over HTTPS**: 401
  unauthenticated, 204 on login as the new `kostas` owner with a CSRF token, 200 returning the
  full chart of accounts. So the whole path — Caddy, TLS, session cookie, CSRF, the core's own
  password verification, `requireView` — works against real containers and not only in a test.
  **Still true: no human has used a browser**, and **the frontend has no login screen.**
- **The implicit-TLS path is verified by hand, not by the suite.** The automated tests run against
  an in-process SMTP server over plain SMTP, so what they prove about TLS is the property mapping
  (`SmtpConfigurationTest`), not a real handshake. The real 465 path was checked twice by throwaway
  probes that were deleted: one authenticating without sending, and one sending real mail. **A
  change to `SmtpConfiguration`'s TLS properties would not be caught by `mvn verify`** — re-probe
  by hand, or point a test at a TLS-capable server.
- **PostgreSQL 18.** Pinned to `postgres:17-alpine` in both `backend/pom.xml`
  (`postgres.docker.image`) and `docker/compose.yml`. Both must move together.
---

# Open questions and obligations

**Everything still open, in one place.** Resolved questions stay filed at the step that answered them,
in `HISTORY.md` under *Open questions, by the step they block* — this section carries only what is
still owed.

### ⚠️ Four obligations U2a found buried, and the rule they earned

**Each of these was marked deferred, queued or unscheduled inside a step's record, had no roadmap row,
and had no home outside the step that raised it.** A clean chronological split would have archived all
four silently, which is the finding rather than a housekeeping detail. **All four now have roadmap rows
and are in the status table above:**

| Was buried in | Now | What is owed |
|---|---|---|
| **F5's B.4** | **F5b — ✅ now CLOSED** | ⚠️⚠️ **The owner had ANSWERED its condition on 2026-08-05 and the answer reached no file** — through F5's close-out and through U2a itself, both of which wrote *"still outstanding"*. **Seven sites were wrong for a day, and the newest was created after the answer existed.** ⭐ That is this rule's own failure mode landing on the obligation it created. **Closed 2026-08-06 as NOT NEEDED**, on a measurement rather than a deferral; its residual is at the two series request records and the series screen |
| **W1's *Queued out of W1*** | **W1c** | `CustomerView.systemRecord()` reads as if it returns the key while the wire carries a boolean; the settings screen still computes `configured` from `value !== ''` rather than the documented `unset` |
| **R1's decision A** | **R1c** | The Fees / *Έξοδα και κρατήσεις* question — likely a generalisation of `ChargeType` rather than a sibling. **The question that decides it:** does Go's list contain *Delivery* and *COD fee*, the two rows `ChargeType` already holds? |
| **8a's design item H.2** | **8c** | `NewPurchaseInvoiceLine` is a discriminated union modelled as a flat record. ⚠️ **Named trigger: before a screen binds it — F6** |

📌 **The rule this earned is in `CLAUDE.md`:** *an item marked deferred, queued or unscheduled inside a
step's record has no owner once that record closes — it gets a roadmap row at the moment it is
deferred, not when somebody notices.* These four are its worked examples.

### 📌 One dangling reference U2a found and did NOT invent a note for

**`frontend/src/auth/permissions.ts:95`** says the real fix for the mirrored restriction logic *"is a
backend change and is noted in `PROGRESS.md`"*. ⚠️ **It is noted in neither file** — measured, not
assumed: nothing matching *derived restriction* exists in `PROGRESS.md` or `HISTORY.md`. **The
reference predates U2a and points at something that was never written down.**

**Recorded rather than resolved.** Writing a note to make the citation true would be manufacturing a
record to satisfy a pointer; the honest options are to make `/api/me` report derived restrictions, or
to delete the claim. **Neither is U2a's to choose.** ⚠️ Note that U2b's guard (2) — *every reference
names a file that exists* — **would not catch this**, because the file does exist; only the
*content* is absent. That limit is stated at the guard rather than left to be discovered.
### ⚖️ Held for the owner — does test-environment parity become a queue item with teeth?

**Raised by the owner at S2's kickoff, 2026-08-01, and deliberately parked. Nothing has been built,
queued, or removed on account of it, and nothing should be until the decision comes back.** It is
recorded here only so it cannot be lost — which is the exact failure mode step 15c cost this project.

**The question.** `CLAUDE.md`'s named anti-pattern *"a test environment configured unlike the real
one"* ends by naming four knobs the real deployment sets that the test environment does **not**
currently pin or assert: **timezone**, **`DateStyle`**, **PostgreSQL major version**, and **Java
default locale/charset**. Locale and encoding are pinned and asserted (`PostgresTestContainer­
Configuration` passes `compose.yml`'s `POSTGRES_INITDB_ARGS`; `TextSearchIT` asserts
`datcollate = 'C'`). The other four are the same shape and are not.

**The two options, as the owner framed them:**

1. **Its own backend queue item, with an enforcement mechanism** — pin each knob in the test
   container and assert the pin, so removing one fails the build the way the collation pin now does.
2. **Stays a `CLAUDE.md` note** — a thing a reviewer is told to watch for, with no build-time teeth.

**What is worth knowing when the decision is made**, and it is the argument the S1 finding already
paid for: the collation divergence was **invisible to the entire test suite, before and after**. The
suite was green describing a database nobody runs, and only the live check caught it. That is
evidence about this *class* of defect rather than about collation specifically — which is the case
for option 1. The case for option 2 is that four assertions nobody can attribute to a real observed
failure is how a build gains checks that get deleted the first time one is inconvenient.

⚠️ **S2 did not touch this**, and S2's own collation work deliberately leans on the *asserted* half:
`collation.test.ts` asserts `Intl.Collator('el')` resolved to `el` rather than to a root-locale
fallback — which is the **Java-default-locale knob's JavaScript twin**, and is the fifth instance of
the same shape. Whichever way the decision goes, that assertion stays; it is not a down payment on
option 1 and should not be read as one.

### 📌 STANDING OPEN OBLIGATION — the database does not yet sort the way the browser does

⚠️ **F4 answered the question; it did not fix the thing.** Recording the answer was the sub-part, and
it is done. **The divergence itself is open and stays open**, and it must not be read as resolved
because a session went and looked at it.

**What is true today, on the running stack:**

| | Orders by |
|---|---|
| The **browser**, on all five list screens (S2) | `Intl.Collator('el')` — Greek block first, accents and case handled |
| The **database**, on every list endpoint | **byte order under locale `C`** — `Zebra` before `apple`, every Greek name after every Latin one |

**These disagree, and today it does not show**, for one reason only: all five endpoints return their
rows whole, so the browser re-sorts the entire list and the database's order is never what anybody
sees. **That is a property of the data being small, not a property of the design.**

⚠️ **The day any of these lists gains server-side paging, the disagreement becomes visible and
wrong** — page 1 would hold the rows the *database* thinks come first, re-sorted by the *browser*
into an order that is correct only within that page. This is the same shape as the guard `DataTable`
already carries for sorting a server-paged list, arriving from the other direction.

**What closing it requires**, none of which is built:

1. `ORDER BY … COLLATE "el-GR-x-icu"` on the list queries. `el-GR-x-icu` **is available** on the
   running image (3 `el*` ICU collations registered) so nothing needs installing — only using.
2. `collation.test.ts`'s pinned PostgreSQL output is the expectation it has to match; that pin exists
   precisely so the two halves cannot drift, and it is the reason this is a small job rather than a
   research one.
3. ⚠️ **Still no collation index**, per S2's decision — an index expression whose meaning changes on
   an ICU upgrade is what `CLAUDE.md` rules out. Add one only when a table gets large, and record the
   `REINDEX` obligation next to the existing trigram one. PostgreSQL records `collversion` and warns
   loudly, which is the opposite of the silent failure the rule was written against.

**It is not scheduled**, and deliberately so: it becomes necessary the moment server-side paging
lands on one of these five, and that is the queued tier-A paging item's neighbour rather than this
one's. **Whoever builds paging on a list screen owns this.**

### ⚠️ Still waiting on input

- **Q35** **AADE exemption codes 24 and 28 are absent from Go's list.** Confirm with the
  accountant whether AADE defines them and whether we need them, before the myDATA adapter is built
  (phase 7). If so it is two `INSERT`s, not a restructuring.
  - ✅ **ANSWERED 2026-08-03 by the artefact, not by the accountant.** Annex 8.3 of the myDATA ERP
    specification defines **all 31 codes with no gaps** — both exist, and it was **two `INSERT`s
    exactly as predicted**. Seeded by **R1 F1**. ⚠️ What is still open is only
    `input_vat_deductible` on those two rows, which is a tax judgement rather than a code-list fact.
    **See the master list under *Waiting on the accountant*; this entry is kept for its history and
    that list is the current one.**
- **Q36** **The OSS and IOSS reasons (codes 29–31) have no myDATA code.** Seeded as NULL
  deliberately — **approved as built**, with phase 7 required to refuse transmission on a NULL
  rather than guess. The real values are to be confirmed with the accountant before then.
- **Q38** *(new)* **The AADE myDATA unit-of-measure codes.** `unit_of_measure.mydata_code` exists
  and every row is NULL. Same shape and same phase-7 obligation as Q36. Add them to the accountant
  list alongside the exemption codes and the depreciation rates.
  - ❌ **WITHDRAWN 2026-08-03 — this was never an accountant question.** Annex 8.13
    `Είδος Ποσότητας` is the published list, and the reason Q38 was filed against a person is that
    the artefact was not in the repository. Seeded by **R1 F3**. ⚠️ **The sentence above — "add them
    to the accountant list alongside the exemption codes" — is how it happened**: the item was
    grouped by *feeling* like the others rather than by anyone checking whether a source existed.
    **See the master list under *Waiting on the accountant*, which is the current record.**
- **Q37** **Customer and Supplier have no address fields.** Not needed while Go issues the
  invoices, but needed by phase 11 at the latest, and possibly sooner for courier vouchers in phase
  4. Also unasked: whether Customer and Supplier want human-facing codes (the internal id is a
  bigint), and whether more than one selling price per product is ever needed.
  ⚠️ **Two of those three were answered on 2026-08-03 (U3) — addresses as D3, human-facing codes as
  D1. Multiple selling prices remains unasked.** See the master list under *Also still open*, which is
  the current record, and `PROGRESS.md` §*U3* for the reasoning.
- **⚠️ Statutory depreciation rates and the asset category taxonomy** — **needs the accountant**,
  the same way the VAT class list did. The rate field exists per asset and is nullable; no rates
  and no category table were invented. Do not create real assets with real values until these are
  confirmed. When they arrive, the natural home for defaults is an `AssetCategory` lookup carrying a
  default rate — which is also where Q12's deferred pre-fill would live.
- **Q28** **Where "Σκοπός διακίνησης" (dispatch purpose) belongs.** Analysis and recommendation
  below; **nothing built**. Correctly identified as unrelated to VAT — it is not folded into
  either VAT entity.

### Q28 in full — dispatch purpose

**It is an attribute of an outbound goods movement**, not of an invoice and not of a receipt.
That placement decides the rest:

- **Not Goods Receipt (step 8).** That is *inbound*. The supplier authors their own dispatch note
  and states their own purpose; we read theirs, we never state ours on a receipt.
- **Not inside the Sales Order Fulfillment module either**, even though that is where the ACS
  voucher generation and QZ Tray printing already live. Dispatches also happen for supplier
  returns, transfers between our own locations, goods sent out for repair (brief §9's
  Service/Technician Management), consignment and sampling. If the purpose lives inside the sales
  module, every non-sale dispatch has no home, and a module ends up owning a core concept —
  against `CLAUDE.md` rule 1.
- **Recommendation: a core-owned `GoodsDispatch`** — the outbound counterpart to Goods Receipt —
  carrying a `DispatchPurpose` core lookup entity, same shape as `VatExemptionReason` since it is
  likewise a codified AADE list. Sales Order Fulfillment then becomes one *consumer* that creates
  a dispatch with purpose = sale, alongside the other cases.
- **Which phase: roadmap phase 4**, with Purchase Orders + Sales Order Fulfillment — that is when
  goods first physically leave under NovoCore's control.

**Two things must be settled before that is final, and both could move it to phase 11:**

1. **Does Prosvasis Go currently issue the Δελτίο Αποστολής?** Go is the invoicing system of
   record until phase 11. If it already issues dispatch notes, NovoCore does not need to author
   them until Go is retired, and phase 4 only needs to print the *courier voucher* — which is not
   a legal dispatch document. That would make this phase 11 scope, not phase 4.
2. **Does the AADE Ψηφιακό Δελτίο Αποστολής (digital delivery note) regime apply to us?** If
   NovoCore must *transmit* delivery notes rather than print them, that is an AADE
   Provider/myDATA concern (phases 7 and 11) and the purpose codes must be correct before then.
   **This is an accountant question** — same bucket as the already-open AADE Πάροχος scope item.

### Waiting on the accountant, and blocking real data rather than code

*Revised 2026-08-03 by R1's Phase 0. Three changes, and the shape of two of them is worth more than
the items: **a question filed against a person because the artefact that answers it was not in the
repository is not an accountant question.** Two of the four items below were exactly that.*

- **Statutory depreciation rates per asset category**, plus the category taxonomy. The field exists and
  is nullable; **do not create real assets with real values until these are confirmed.** *(Unchanged.)*
- ~~**The myDATA unit-of-measure codes (Q38)**~~ — ❌ **REMOVED from this list, 2026-08-03. It was
  never an accountant question.** `unit_of_measure.mydata_code` was NULL on all 8 rows and Q38 was
  filed beside the exemption codes and the depreciation rates because nobody had a source.
  **Annex 8.13 `Είδος Ποσότητας` of the myDATA ERP specification is that source and has been
  published all along.** It sat on this list from step 3b until R1's Phase 0 put the artefact in the
  repository. ⚠️ **Recorded as the finding rather than quietly struck**, because the same shape
  applies to anything else on this list whose answer is a published table rather than a judgement.
  Seeded by **R1 F3**.
- **AADE exemption codes 24 and 28 (Q35)** — ✅ **the code-list half is ANSWERED by the artefact.**
  `V8`'s header asked for confirmation against AADE's published table before phase 7; annex 8.3
  defines **all 31 codes with no gaps**, so both exist and both are seeded by **R1 F1**. ⚠️ **What
  remains on this list is narrower and genuinely the accountant's: is input VAT deductible on those
  two rows?** `input_vat_deductible` is a tax judgement, not a code-list fact — the other 29 rows are
  all `false` by transcription from Go and the artefact does not state it. **Both rows follow the
  existing convention and are listed as needing confirmation.**
- **The OSS/IOSS myDATA codes (Q36)** — **unchanged and still open.** Codes 29–31 have no myDATA
  string in Go; seeded NULL deliberately, with phase 7 required to **refuse** transmission on a NULL
  rather than guess.
- 🆕 **⚠️ THE SELF-SUPPLY REVENUE LEG — added 2026-08-03 by R1's Phase 0. This is the question, not a
  detail of one.** `Στοιχείο Αυτοπαράδοσης` (AADE `6.1`) and `Στοιχείο Ιδιοχρησιμοποίησης` (AADE
  `6.2`) are seeded as document types by R1; **no posting rule is built and none should be until this
  is answered.**

  **The question: does a self-supply credit a dedicated revenue account at all, or is it a
  contra-COGS / inventory reduction with no revenue leg?** Everything downstream — whether it is a
  sales invoice at all, whether it needs a settlement method, whether it appears in revenue — follows
  from the answer, which is why it is stated this way round.

  ⚠️ **The near-misses are traps, and each fails for its own reason.** Recorded so nobody resolves
  this by picking the closest-looking account:
  - **The three channel `Sales` accounts** — wrong by construction. `SalesChannel`'s own javadoc says
    channel figures answer a question about *customer* revenue; a self-supply through one would
    corrupt exactly the per-channel revenue and return-rate figures **step 3 split them to protect.**
  - **`Services`** — wrong. A self-supply is not a service.
  - **`Other income`** — wrong for the subtlest reason: it is a *residual*, and putting a statutory
    self-supply in a residual makes it unfindable in the one report where it must be visible.

  **The expense side has a candidate and it is not a guess:** `Internal consumption` (General
  Expenses, 0 journal lines) was seeded in `V4` with the comment *"staff coffee, demos, tastings —
  deliberately not a write-off"*. It has waited since step 3 for exactly this.

  ⚠️ **Also needed, and separable:** can deductibility differ between the two uses? Capitalising to
  fixed assets is business use; internal consumption may or may not be. AADE's own split into `6.1`
  and `6.2` is evidence that **one flag per document type** suffices, but
  `vat_exemption_reason.input_vat_deductible` is the codebase's precedent and it is **per row**, so
  this is not settled by analogy.

### Also still open, not blocking anything

- **Q41 — after-the-fact GR/IR matching.** Belongs with phase 8's Clearing Checks; needs an answer to
  "whose document is the variance entry?".
- **Q42** — a bundle containing a serial-tracked component cannot be sold as a bundle. The machine sells
  on its own line.
- **Q28 — dispatch purpose placement.** Recommendation is a core-owned `GoodsDispatch` in phase 4,
  conditional on whether Go already issues Δελτία Αποστολής and whether the AADE digital delivery note
  regime applies (accountant question). Nothing built.
- **Q32 — the 8-hour session timeout.**
- **Q37 — addresses on Customer and Supplier**, plus human-facing codes and multiple selling prices.
  ⚠️ **PARTLY ANSWERED 2026-08-03 (U3), and the remainder is stated so it is not read as closed.**
  **Addresses are D3** — structured, suppliers always, customers who purchase with VAT, retail may be
  NULL, **enforced at the document rather than the customer**, and the customer holds only the
  **billing** address (per-order shipping moved to step 22). **Human-facing codes are D1** — nullable,
  the business's own reference, **supplier has an alias and customer never does**. ⚠️ **Multiple
  selling prices per product is STILL UNASKED** and is not covered by either.
- **Q40 — a human-facing document number** for the documents NovoCore owns. Step 10 adds one more to
  the list: a freight allocation has no number either, only an id. ⚠️ **ANSWERED IN PRINCIPLE
  2026-08-03 (U3): this is D4's remaining half.** These are **internal reference numbers, not
  statutory ones** — no legal sequence, gaps do not matter, simple per-type counters, none of step
  40's machinery. ⚠️ **Two format decisions are still open and nobody has been asked**, and step 7
  named them: **does a counter reset per year, and is the prefix per document type or per source?**
- **Q12 leftover — is the periodic depreciation posting run Phase 1 scope**, or only the register and
  the calculation? Still waiting on the statutory rates either way.
- ~~**Q43**~~ — **answered and built (V22).** See the section below.
- ~~**Q44**~~ — **both halves answered and built in step 14c** (`b8aa9e2`). The section half is
  `Section.EMAIL_OUTBOX`, its own grant rather than part of `SETTINGS`; the access-path half is built
  in `EmailSenderImpl` and proven behaviourally, and it needed a piece the decision could not have
  anticipated — `AttachmentOwnerType`, because `entityType` is free text and there was nothing to
  check against. **See the step 14 section above.** The original decision record follows, unchanged.

---

## 🎯 The search target list — **all 16 rows.** This is the authoritative version

**Confirmed 2026-08-01, and it is deliberately longer than the list S1 was scoped against.** The
approval conversation narrowed to the five entities that already had screens; this is the whole
thing. **It is recorded here so no future step re-derives it, and so nothing gets built narrower
than it should be.** A screen that adds search adopts *its row*, not a guess at its row.

⚠️ **"Not built yet" means two different things below, and conflating them will cost a session.**
For rows 6–11 the **entity and its routes already exist** — only the screen is missing, so adopting
search is the one-line change S1 was built for. For rows 12–15 **the entity does not exist at all**;
its row here is a specification for whoever builds it, not work that is waiting.

| # | Screen | Fields to search | State today |
|---|---|---|---|
| 1 | **Products** | SKU, Title, EAN, Supplier's SKU, Brand, **Category/subcategory** | ⚠️ **Five of six done** — SKU, title, brand, barcode, supplier's SKU. **Brand was built for this** (migration **V29**: column, PATCH route, create-form field, index). **Category is not a column and is its own proposal** — see below |
| 2 | **Suppliers** | Code, Legal name, Alias, VAT (ΑΦΜ) | ⚠️ **Two of four done** — legal name and VAT, plus email and phone (kept). **Code and Alias are not columns**. The VAT gap was found by this reconciliation and closed in **V29** |
| 3 | **Customers** | Code, Name, VAT (ΑΦΜ) | ⚠️ **Partly done** — name and VAT searched, plus email and phone (kept). **Code not a column** |
| 4 | **Users** | Username | ✅ **Done**, plus display name (kept) |
| 5 | **Roles** | Name, Description | ✅ **Done** |
| 6 | **VAT Classes** | Name / Code | Entity + routes exist; **screen is F4** |
| 7 | **Units of Measure** | Name / Code | Entity + routes exist; **screen is F4** |
| 8 | **Sales Invoices / Credit Notes** | Document number, Customer name, Customer VAT, **Customer Code**, Date, Document type-series | Entities + routes exist; screens are **F5** |
| 9 | **Purchase Invoices / Debit Notes** | Document number, Supplier name, Supplier VAT, **Supplier Alias**, Date, Document type-series | Purchase invoices exist; **debit notes do not exist at all** |
| 10 | **Receipts / Payments** | As rows 8–9 — document number, counterparty name, VAT, alias, date, type-series | `settlement` + routes exist; screen is **F7** |
| 11 | **Manual Journal Entries** | Description / memo | Entity + routes exist; screen is **F8** |
| 12 | **Assets** | Name, Code | Entity + routes exist, **and `asset.code` is a real column** — the only one of the code fields that is |
| 13 | **Purchase Orders** | PO number, Supplier name, Supplier VAT, Supplier Alias, Date | **No entity.** Later phase |
| 14 | **Sales Order Fulfilment** | Order number, Customer name, Customer VAT, Status, Date, Sales channel, *Invoice number (later)*, *Skroutz reference (later)* | **No entity.** Later phase |
| 15 | **Service Tickets** | Ticket number, Customer name, Serial number, Customer VAT, Date | **No entity.** Later phase |
| 16 | **Back-in-Stock Reminders** | Customer name / phone, Product name, Customer VAT | **No entity.** Later phase |

**Two things this table settles that would otherwise be re-argued every time:**

- **Email and phone on Suppliers and Customers were added beyond the specified list, and stay.**
  Confirmed 2026-08-01. They are a filter box's most useful columns and cost one index each.
- **A counterparty's name, VAT, code or alias is on the *other* table.** Rows 8–10 and 13–15 all
  search a field that does not live on the document. `TextSearch` supports a dotted path
  (`"customer.name"`) for exactly this — ⚠️ **it produces an INNER JOIN**, so a document with no
  counterparty would vanish from its own list. Whoever builds those decides join-versus-denormalise
  per document; the mechanism does not force the choice, and this note exists so the trap is met
  before the bug is.

⚠️ **Rows 2, 3, 8, 9, 10 and 13 all depend on `Supplier.code`, `Supplier.alias` or `Customer.code`,
none of which exist.** That single queued item below therefore blocks part of six rows, not two —
which is the argument for doing it before F5 rather than after.

### 📌 Queued out of S1 — the master-data fields the brief names and step 5 never built

**`Supplier.code`, `Supplier.alias`, `Customer.code`.** All three are in the brief's *(draft)* field
lists and **none is a column.** Verified against the live schema, not inferred.

**This is not a search item.** Search over a column nobody can populate finds nothing, and an index
on it would be a claim about coverage that is not true. Each field needs: a migration, service
methods, PATCH routes, create-form fields, audit entries — and *then* one line in a new index
migration plus one string in the service's `SEARCHABLE` array. That last part is the cheap half, as
`Product.brand` has now demonstrated end to end.

⚠️ **`Supplier.alias` collides with a word already in use.** Brief §5's *"alias forward, never
rewrite history"* is the **customer merge** mechanism — a different thing entirely. A supplier alias
here is a short trading name. Conflating them would be expensive.

⚠️ **This item blocks part of six rows of the target list above**, not three: rows 2, 3, 8, 9, 10 and
13 each search a code or alias. That is the argument for doing it **before F5**, when the first
document screen starts wanting a counterparty's code.

**`Product.brand` is no longer on this list — it was built (V29).** Plain nullable `varchar(120)`,
one brand per product, **no uniqueness on the value** and no brand table: a brand is a label, not an
accounting object, and nothing posts, reports or computes from one. Blank normalises to null so "no
brand" has one representation. Column, `PATCH /api/products/{id}/brand`, create-form field, detail
editor, audit entry, GIN index, and searched alongside SKU, title, barcode and the supplier's code.

### 📌 Queued, its own proposal — `Product.category`

**Deliberately not built in any form this session — not even the schema.** Brief §5's one-line
*"Category (main/sub, including Spare Part)"* understates it, and building from that line would
produce the wrong thing. The requirement, **decided 2026-08-01 and recorded here so it is not
re-derived narrower:**

- **Three levels deep** — category / subcategory / sub-subcategory. Not two.
- **A product belongs to SEVERAL categories at once**, WooCommerce-style.

Those two together mean **a self-referencing category table plus a product-to-category join
table**. They rule out both of the shapes somebody reading only the brief would reach for: it is
**not two flat columns** on `product`, and **not a fixed enum**.

Consequences to work through when it is scoped: what a three-level path does to reporting and to the
Woo adapter's own category tree; whether a product may be attached to a parent as well as a leaf;
and — for search specifically — that a many-to-many makes the searched text a *joined* column, so
`TextSearch`'s dotted path and its inner join need thinking about rather than reaching for.

---

### Backend work, queued and not next — tier-A paging on five services

**Not blocking F4 or any frontend step.** Mechanical follow-through with no decision outstanding; the
contract is settled and proven on sales invoices. Kept here in full because the four checks below
were expensive to learn and two of them are counter-intuitive.

| Service | Routes | Sort enum to add | Note |
|---|---|---|---|
| `PurchaseInvoiceService` | `GET /api/purchase-invoices` | invoice date, supplier number, recorded-at | |
| `GoodsReceiptService` | `GET /api/goods-receipts` | receipt date, recorded-at | |
| `SettlementService` | `GET /api/settlements` | settlement date, recorded-at | |
| `InventoryService` | `GET /api/inventory/lots`, `/consumptions` | acquisition date, recorded-at | **⚠️ read check 3** |
| `EmailSender` | `GET /api/email/outbox` | queued-at, status | **⚠️ read check 3** |

⚠️ **The two rows flagged above page rows that carry a collection** — a lot reaches its serialized
units, an outbox message reaches its attachments — so **check 3 below is about them specifically**,
and the reason it gives is not the one you have probably read elsewhere.

**The recipe, in order, per service:** a `…Sort` enum in `core-api` next to its service interface →
a `page…` method on the interface taking `PageRequest` and returning `PageResponse` → a `Page<E>`
finder on the repository with **no `OrderBy` in the method name** (the ordering comes from the
`Pageable`) → a `SORTABLE` map plus a natural order in the impl, calling `SpringPaging` → the route
gaining `page`/`size`/`sort`/`direction` through `Paging.of`.

### Four checks per service — 1 and 2 bit on sales invoices, 3 and 4 in step 16b

**1. Every sort key must be a real column.** `GROSS_TOTAL` was removed from `SalesInvoiceSort`
because an invoice's gross is summed in Java from its lines. The same trap exists on purchase
invoices, goods receipts and settlements — check before adding the constant, not after.

**2. Regenerate the spec at the end and read the diff.** It should be additions only; a deletion
means a response shape changed rather than gained a field.

**3. ⚠️ Do not `join fetch` a collection to page it — and the usual reason for that is wrong.**
Applies to **`InventoryService` (lots → serialized units)** and **`EmailSender` (outbox messages →
attachments)**, the two rows flagged in the table above.

> The received wisdom is `HHH000104` — Hibernate cannot apply a limit to a query with a fetched
> collection, so it loads the whole result set and pages in memory. **That is Hibernate 5
> behaviour**, and step 16b repeated it in four javadocs before measuring it. On **Hibernate 7** the
> limit *is* applied in SQL and the collections load separately. Measured, for a five-row page of
> twenty-five entries:
>
> ```
> without a collection fetch:  5 entities loaded,  0 collection loads
> with one:                   15 entities loaded,  5 collection loads
> ```
>
> So the real cost is an **N+1 per page**, not an out-of-memory. Still worth avoiding — and worth
> stating accurately, because a justification that turns out to be false is how a good decision gets
> reversed by whoever checks it.

**Copy `JournalEndpointIT.aPageLoadsOnlyItsOwnRows`**: assert entity **and** collection load counts
from Hibernate's `Statistics`, and assert `isStatisticsEnabled()` **first**. The earlier version of
that test measured nothing — it asserted "0 is less than 25" and passed against every possible
implementation, including a deliberately introduced fetch. A single-valued `@ManyToOne` fetch is
fine and is not what this is about; see `JournalEntrySpecifications.linesOfAccount`, which fetches
one and documents why the count query must not.

**4. ⚠️ A JPQL `(:param is null or …)` does not work on PostgreSQL** — relevant the moment any of
these gains an optional filter. A bare parameter in `? IS NULL` gives the driver nothing to infer a
type from and the statement is refused outright: `could not determine data type of parameter $1`. It
fails for *every* request, not only the ones omitting the filter. Use a `Specification`, as
`JournalEntrySpecifications` does; casting each parameter also works and leaves dead conditions in
every plan.

**Then** `mvn verify -Dnovocore.openapi.write=true`, and commit the spec with the change.

Step 15 added 113 in total (1039 → 1152), and the count understates the change twice over:
`WholeScenarioIT`'s 21 invariant tests were **replaced** by 12 shared ones fed to a `@TestFactory`,
and several of the new "tests" are sweeps over all 133 routes reported as one. See the step 15
section.

Step 13 added 94: 53 in `core-api` (the property harness, its own self-test, and properties over
`Money`, `Quantity`, `UnitCost` and `ProportionalAllocation`) and 41 in `core` (12 FIFO properties
against a real database, 21 whole-scenario invariants, and 8 worked examples for the defect the
properties found).

Step 14 added 79: 22 core unit (the money serialisers and the endpoint-declaration check, neither
needing Docker), 14 core integration (Q44's access path, bundle redaction, the section-list
agreement), 39 app integration (four endpoint suites over the real filter chain) and 4 architecture.

**Step 11 introduced the first non-container tests in `core`** (`RetryPolicyTest`,
`SmtpConfigurationTest`). Until now everything in that module needed Docker; these do not, which is
why the `core` row in the count above gained a "unit" half.

`mvn test` runs the non-container tests in ~6 seconds and needs no Docker. `mvn verify`
additionally runs the `*IT` tests under Failsafe against a real PostgreSQL 17 container.

### 🔧 Small frontend fix for later — a missing route and a downed server look identical

**Not urgent, and not a defect in either file's own logic** — both do exactly what they were written
to do. But together they leave one message covering two very different situations, and it cost real
time on 2026-07-31.

`useSession` in `frontend/src/auth/session.tsx` treats a 401 as the answer "nobody is signed in" and
**every other failure as `error`** — deliberately, and the reasoning is sound: a Settings page that
renders as though the user has no grants when the server is down is worse than one that says the
server is down. `App.tsx` then checks `error` *before* `isSignedOut`, also deliberately, so that a
password box is never shown in front of a server that cannot check the password.

The gap is that a **404 falls into the same bucket as a connection failure**, so the shell renders
"Novocore is not answering — the server could not be reached" when the server answered perfectly
well and simply does not have the route.

**How it presented**, which is the part worth remembering: the running container was two days older
than the working tree and predated `GET /api/me` (`3158239`). Unauthenticated, the security entry
point answers 401 before routing, so the login form appeared normally and the password was accepted
(`204`). Only *after* authenticating did the request reach the dispatcher and come back 404 — so the
symptom was **"the server is unreachable, but only once I log in successfully"**, which points at
authentication and at the credential, and is nowhere near the actual cause.

**The fix is to distinguish them in the message, not to change either file's error/signed-out
split**, which is right as it stands. A 404 on `/api/me` means the frontend and backend are built
from different commits, and saying so ("this build expects an API this server does not have —
rebuild the app container") turns a twenty-minute hunt into a one-line diagnosis. `session.test.tsx`
is where the case belongs.
