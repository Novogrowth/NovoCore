# NovoCore — Build Progress

*Live status. Overwritten each session close-out, not appended to. Last updated: 2026-08-06 — R2c
deferred and split, R4 promoted to current, and the chart-of-accounts decision recorded (C1).*

*Close-out now also pushes to `origin` automatically (`CLAUDE.md`), so this file no longer tracks
unpushed commits.*

Phase 1 (the core) is in progress. Build order and step numbering are as agreed at Phase 1
kickoff; they differ slightly from the brief's roadmap in that permissions were moved earlier
(step 4, before the ledger) and a Settings service was added (step 2).

---

## Where things stand

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
| F5 | **Sales Invoice + Credit Note screens** — the first step to reach the **recording** path | **Done, committed** `c395324` — live leg run 2026-08-06. All 30 sub-parts have verdicts, none open. Five sales/credit-note screens, `search=` on both document routes (**V36**, 4 GIN trigram indexes), the repository's **first three `meta.sortKey`s**, and `DataIntegrityViolationException` mapped to 422 so an index-enforced rule stops arriving as Boot's legacy 500. ⚠️ **Two things are DELIBERATELY not built and have their own roadmap rows** — **N1** (a reversed document's number) and B.4's collation. ⚠️ **The record forms are TRANSITIONAL by decision** — a mirror is never typed in real operation |
| 16 | **The frontend itself** — `/frontend/`, Vite + React + TS + Tailwind + shadcn/ui | **In progress. F0–F4, S1 and S2 done.** ⚠️ **W1 landed 2026-08-04, so next is F5, then the D-block (D1+D3+D4+D5), then F6 onward** — the owner's sequencing decision of **2026-08-04**, recorded as the roadmap's row order. *(This cell previously read "Next is Q1, then R1, then F5", correct when written on 2026-08-02 and overtaken since: Q1, R1a, R1b, R2 and R2b have all landed.)* Foundations `94e17cd`, Products `56e3726` + guards `28c4119` + brand pass, then the render-loop fix `3458ee6`, F0 (the seed pass), F1 Suppliers `b406b27`, F2 Customers `496c7be`, F3 Users & Roles `aea0e56`, then **S1** (search), **S2** (sorting) and **F4** (Settings). **307 frontend tests, 31 files, green.** Per-step detail in `docs/novocore-roadmap.md`; decisions and what each step left behind in *Step 16 — the frontend* below |

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

## ▶ F5 — Sales Invoice + Credit Note screens. **DONE 2026-08-06, live leg run, merged to `main`**

**Written here at the moment of approval, per `CLAUDE.md` §*An approved proposal is a checklist, not
a paragraph*.** Every row below gets a verdict at close-out: **done** (and how it was verified),
**explicitly deferred** (with the reason and where it is recorded), or **still open**. A row with no
verdict is a finding.

F5 decides the document interaction pattern F6–F8 reuse, and it is the first step to reach the
**recording** path — where several of R1b's and R2b's constraints have never met data.

### ⭐ OWNER DECISIONS OF 2026-08-05 — recorded before the remaining code, and they RESHAPE the step

⚠️ **Read these before touching anything in F5.** They were settled in a design conversation and are
written here, in `CLAUDE.md` and in the roadmap in the same session, per the rule that a design
conversation gets the same close-out discipline as a build step.

**1. A sales document in Novocore is a MIRROR, and the record form is a TEST HARNESS.**
The invoicing software is the only issuer; Novocore sends an **order**, that software issues, and
Novocore **fetches the issued document back**. "Issuing" from a Novocore screen means sending the
order again. ⚠️ **A sales invoice will never be recorded by hand in real operation** — the core works
standalone **for testing purposes only**. Full statement in `CLAUDE.md` §1b. **F5's deliverables
therefore split three ways:**

| Part | Status |
|---|---|
| List and detail screens | **PERMANENT PRODUCT** — people read mirrored documents daily |
| The recording **path** (service, refusals, posting, stock) | **PERMANENT** — a document issued at the counter in Go still arrives through it |
| The record **form** | ⚠️ **TRANSITIONAL, a test harness.** Correct, not polished |

**2. D is scoped DOWN.** Nobody will ever type a credit note. **D.1 list and D.2 detail stay full
product quality; D.3/D.4 become minimal** — enough to exercise the recording path, its derived fields
and its refusals, with preview and rounding acceptance only so far as the path needs exercising. **Do
not build the polished version.** D.5 stays, and see decision 4 for why reversal is itself temporary.

**3. X1 gained three concrete requirements** — idempotency key, a persisted *submitted, outcome
unknown* state, and a **user-visible unresolved state that never invites a retry**. They are **core,
not adapter**. ⭐ This also closes a question previously left open: X1 was thought unspecifiable
before a real adapter exists, and it is not. **Recorded in the roadmap under ˣ; not built.**

**4. A.1c's direction is SETTLED — the number IS released — and is deliberately NOT built in F5.**
It has its own roadmap row, **N1**. The reasoning: a reversal undoes **Novocore's own mis-recording**
of a document Go issued, not a cancellation of an issued document — Greek law has no such thing. So
the partial unique index is the wrong enforcement. ⚠️ **It is not built here because a partial index
cannot express *"not reversed"* and the fix must not silently drop the concurrency guarantee.** A.1b
already prevents the raw 500. `DocumentNumberReuseIT` stays exactly as it is.

**5. An open structural question, recorded and NOT resolved:** both paths run through an **order**,
Novocore has no order entity (step 22, Phase 4) and the screens that depend on it are Phase 2 — and
**the order and the document are two linked objects, not one filled in progressively**, because Go
applies its own VAT resolution, rounding and numbering. Recorded against step 22 and here.

### 🅟 Phase 0 — the premises, measured rather than read

**Method:** a throwaway `F5Phase0ProbeIT` in the app module (real Boot server, real HTTP, real
PostgreSQL, ~45 printed responses, negative control *"a well-formed sale must be 201"*), **deleted
afterwards**; plus direct SQL against the running stack in rolled-back transactions, with its own
negative control. App image rebuilt first, unconditionally.

| # | Finding, measured 2026-08-05 |
|---|---|
| **P.1** | 🐛 ⚠️ **"A reversed invoice releases its number" is stated in three places and enforced by none.** The service message and the DB trigger both say it and the trigger implements it; the partial unique index `sales_invoice_number_idx … WHERE reversal_of_id IS NULL` excludes the *reversing* row and **not the reversed one**. Measured over HTTP: record → reverse → re-record the same number in the same series answers **`500`** in Boot's legacy body, from `DataIntegrityViolationException`. Confirmed independently in SQL on the live database with a live negative control. **It predates R1b — `V17` had the same predicate** — and was unreachable until F5 |
| **P.2** | 🐛 **`documentNumber` is mandatory in fact, undeclared in the spec.** Guarded by an inline `if (… isBlank()) throw` on both `NewSalesInvoice` and `NewCreditNote` — exactly 8a's stated blind spot — so neither carries `@Mandatory` and neither appears in `required`. The generated TypeScript says `documentNumber?: string`. Its refusal is labelled `"Malformed request body:"` for a body that parsed, while `seriesId` beside it answers `"\"seriesId\" is required and was not supplied."` |
| **P.3** | ⚠️ **PREMISE CORRECTED — a line states *neither* a VAT class nor an exemption reason in the ordinary case.** "Never both" is right; "never neither" is the opposite of the design. `VatClassPrecedence` is line → customer → product, and a null `vatClassId` is how a line says *"whatever this customer or this product says"*. Measured `201` |
| **P.4** | ⚠️ **PREMISE CORRECTED — the backend DOES declare sort keys.** `SalesInvoiceSort` is a bound enum (`INVOICE_DATE`, `DOCUMENT_NUMBER`, `RECORDED_AT`), mapped in `SalesInvoiceServiceImpl.SORTABLE`, present in the spec and in `paging.ts`. What is missing is only the **frontend** half: no column file in the repository carries a `meta.sortKey`. The server-sort machinery (`canSortColumn`, `manualSorting`, `useListState.setSort`) is already built and tested |
| **P.5** | ⚠️ **PREMISE CORRECTED — the cash limit is `>=`, not "above".** Measured: **exactly €500.00 is refused**, €499.99 records. A screen mirroring this with `>` lets through the one value the law cares most about |
| **P.6** | ⚠️ **The draft-document-type refusal on the recording path is UNREACHABLE by construction.** R2b's own guard refuses creating a series against a draft type, and `PUT …/stock-behaviour` refuses null flags — so a series can never come to point at a draft. `resolveSeries`'s draft branch is defensive only, like the `affectsStock == null` branch beside it |
| **P.7** | ⚠️ **`GET /api/sales-invoices` has no "everything" mode.** With no parameters it answers `400 "a date range needs 'from' and 'to', or name a customerId instead"`. The list screen cannot open unfiltered; the default range is a decision F5 has to make and record |
| **P.8** | ⚠️ **Neither document endpoint accepts `search=`, and an unknown parameter is silently ignored** — measured `200` with the full list. A `SearchFilter` wired without the backend half would look like it works and filter nothing |
| **P.9** | ⚠️ **The counterparty trap does not bite row 8, and the real obstacle is bigger.** `SalesInvoice.customerId` is a **scalar `Long`, not a JPA association**, so `TextSearch`'s dotted path cannot be used at all. And `customer_id` is `NOT NULL`, so no sales document could vanish from its own list. The join-versus-denormalise choice is real; the drop-out risk is not |
| **P.10** | **Rounding is two optional fields on the same request body — no separate route, no review queue.** Measured: preview reports `roundingDifference` / `roundingThreshold` / `roundingNeedsAcceptance` / `receivable`; record refuses `422`; the same body plus `roundingAcceptedBy` records `201` and stores `roundingNeededReview`, `roundingAcceptedBy`, a server `roundingAcceptedAt` and the note. Threshold boundary measured: 0.03 posts automatically, 0.04 refuses. ⚠️ **`roundingAcceptedBy` is silently dropped when the difference is small**, so the control must appear only when the preview asks for it |
| **P.11** | **Per-series uniqueness works, and this is the first time it could be exercised.** The same number in two different series → both `201`; the same number in one series → `422` on `record` **and** on `preview`. All ten live `sales_invoice` rows still have `series_id IS NULL` |
| **P.12** | **ΜΑΡΚ / UID / QR URL / transmission status have NO write route anywhere**, at record time or after. Measured on a fresh record: the three are absent from the body entirely (`non_null`) and `transmissionStatus` is `"UNKNOWN"`. This is `frontend/README.md`'s **third** field state (*no route exists on any installation*), not the fourth |
| **P.13** | **Immutability confirmed at the wire.** `PATCH …/{id}/description` → `404` (no route registered), `DELETE …/{id}` → `405`. The whole sales surface is five routes and none is an edit. Correction is reversal or credit note, and the backend refuses mixing them |
| **P.14** | **W1's derived properties are on the wire and usable**: `inForce`, `reversal`, `reversed` on both views; `bundle`, `exempt` on `SalesInvoiceLineView` |
| **P.15** | ⚠️ **Live-leg precondition nobody had recorded:** the owner's two real series are `TEST99` (channel NULL, draft type) and `TEST2` (`ECOMMERCE`, deactivated type), so **neither can record an invoice today**. Reactivating type 1 makes `TEST2` the only recordable series. ⚠️ **RE-MEASURED 2026-08-06 AND OUT OF DATE — see L.0 below.** There are now **four** series and **three** types, and **every type is inactive**, so nothing can record. The remedy is no longer one reactivation |
| **P.16** | **Collation measured on the live stack** (`datcollate = C`, PG 17.10, `el-GR-x-icu` available). The two orders differ only where text carries Greek letters or mixed case; on Latin document numbers they agree, numeric ordering off in both. ⚠️ **A Spring Data `Sort` cannot express `COLLATE`**, so applying it means leaving the `Pageable`-driven path for that property |
| **P.17** | ⚠️ **My own first `install` was piped to `tail` and reported `INSTALL_EXIT=0` over a compilation failure** — `CLAUDE.md`'s piped-build trap, caught by reading the output. Every later build used `set -o pipefail` and the documented two-step |

### 🅘 The three investigations, and what each established

**A.1a — the provenance search. The claim is a real decision, and the SALES side contradicts it.**

`git log -S "releases its number"` returns three commits. The **origin is step 8, `c6e2513`,
migration `V16` — purchase invoices, not sales.** Its comment argues the design out loud:

> *"**A TRIGGER RATHER THAN A UNIQUE INDEX**, because the rule is not row-local and a partial index
> cannot express it. […] once an invoice has been reversed, re-entering it correctly under the same
> supplier number is the ordinary thing to want, **because the commonest reason to reverse one is
> that it was typed wrong**. […] The alternative — a `superseded` flag maintained alongside
> `reversal_of_id` — would be a second copy of a fact, free to disagree with the first."*

⭐ **And `V16` backs it correctly:** `CREATE INDEX purchase_invoice_number_idx` — **non-unique**.
Verified on the live database: `purchase_invoice` has exactly two unique constraints,
`journal_entry_id` and `reversal_of_id`. **The purchase side is coherent.**

⚠️ **Step 9 (`29e9dcd`, `V17`) copied the rule to sales, repeated the same comment —** *"a trigger
rather than a partial unique index"* **— and then created that index 25 lines later.** `V32` rebuilt
it per-series in R1a, faithfully preserving the contradiction. **So the defect is not the rule; it is
that the sales side contradicted its own written decision.** The owner has since settled the
direction — see decision 4 — and the argument `V16` recorded was about *Novocore's* data entry, which
is not the same claim as one about Go's numbering.

**A.1d — the family is TWO INSTANCES OF ONE DEFECT, not a broad class.**

⚠️ **There is no `DataIntegrityViolationException` handler in `WebExceptionHandler` at all**, so any
constraint a service does not pre-check escapes to Boot's default. One probe posted 13
caller-reachable unique constraints twice each:

| Result | Constraints |
|---|---|
| **Refused cleanly** (422 with a written reason) | product SKU, product EAN, customer VAT, username, role name, asset code, sales-document-type description and sort code, delivery-method abbreviation, sales-series abbreviation and sort code |
| 🐛 **Escaped as a bare `500`** | `sales_invoice_number_idx`, `credit_note_number_idx` |

Both escapees are **the same defect twice** — a partial unique index on `… WHERE reversal_of_id IS
NULL` sitting under a service check and a trigger that both release the number.

⚠️ **Three candidates first answered `400` because my bodies were wrong** (`vatStatus` missing,
`legalName` vs `name`, `rawPassword`), so they never reached their constraint — W1's P.9 shape
exactly. **They were fixed and re-run rather than reported as a clean sweep**, which is the only
reason the sweep means anything.

⚠️ **`supplier` has no unique index on `vat_number` at all** — noticed while reading the catalogue.
Nothing to escape, so not a defect; recorded because `customer_vat_number_unique` exists and the
asymmetry is the kind that reads as an oversight later.

**The concurrency measurement, which reshaped N1.** A `BEFORE INSERT` trigger's `NOT EXISTS` cannot
see uncommitted rows and takes no lock. Measured on the live database with a **positive control**
(same transaction → the trigger refuses, so the apparatus is alive): session A held an uncommitted
`RACE-PROBE-1`, session B inserted the same number for the same supplier and was **`INSERT 0 1` —
accepted**. Both rolled back; **0 rows left**. So "make the index agree with the message" would trade
away a real guarantee, and `purchase_invoice` is exposed to this today.

**The `upper(document_number)` Greek case measurement (C.3's basis).**
Live stack, `datcollate = C`, PostgreSQL 17.10, `el-GR-x-icu` available:

```
byte order (locale C, today):  Alpha-1 | zeta-1 | ΑΛΠ-10 | ΑΛΠ-2 | ΑΛΦΑ-1 | ΩΜΕΓΑ-1 | άλφα-1
el-GR-x-icu (the app's order): ΑΛΠ-10 | ΑΛΠ-2 | ΑΛΦΑ-1 | άλφα-1 | ΩΜΕΓΑ-1 | Alpha-1 | zeta-1
```

On **realistic** document numbers the two agree unless the text carries Greek letters or mixed case:

```
C  : ALP-1 | TEST-SI-2026-0001 | TEST-SI-2026-0002 | TEST-SI-2026-0010 | alp-2 | ΑΛΠ 1 | ΤΠΔΑ 1
ICU: ΑΛΠ 1 | ΤΠΔΑ 1 | ALP-1 | alp-2 | TEST-SI-2026-0001 | TEST-SI-2026-0002 | TEST-SI-2026-0010
```

Numeric ordering is off in **both** (`-0010` before `-0002`), consistent with `collation.test.ts`.
⚠️ **And `SpringPaging` builds a Spring Data `Sort`, which cannot express `COLLATE`** — so applying it
means leaving the `Pageable`-driven path for that one property. **That is why B.4 is deferred rather
than cheap**, and why the deferral is conditional on what a real Go document number looks like.

**B.6 — the island reduced rate is NOT implemented, and there is no place for it to sit.**

Three facts, measured. **(1)** The mapping exists as **data only** — `vat_class.reduced_counterpart_id`,
seeded in `V5` with the real chains (24→17, 13→9, 6→4→1041), with admin routes. **(2)** Nothing
outside `..core.tax..` reads it; `SalesInvoiceServiceImpl.price` resolves through `VatClassPrecedence`
and takes `vatClass.ratePercent()` directly. **It is a lookup table waiting for a rule.** **(3)** No
input could feed such a rule anyway — `Customer` has no address, postcode or region, and structured
addresses are **D3**, scheduled *after* F5.

⚠️ **The part recorded at `VatClassPrecedence` for whoever gets the accountant's answer:** an island
rate is a fact about a **destination**, so it may not merely *reorder* line → customer → product — it
plausibly enters as a **fourth input none of the three carries**, and **per-document rather than
per-customer**, which is the shape D3 already records for addresses. **F5's line form is built
directly on `VatClassPrecedence.resolve` and is downstream of that answer.**

### 🅐 Finding 1 — split at approval, because the message is the least authoritative of the three

⚠️ **The owner rejected the obvious sub-part.** The Phase 0 proposal was *"make the index match the
message"*, and **nobody established that the message is correct**. Novocore does not issue these
numbers — Prosvasis Go prints them and Novocore records what was printed — so *"a reversed invoice
releases its number"* is **a claim about Go's behaviour**, not about Novocore's policy. It is
therefore established before it is implemented, in either direction.

| # | Sub-part | Verdict |
|---|---|---|
| **A.1a** | **ESTABLISH, do not decide:** is the release rule a real requirement, or an unverified sentence that propagated from the message into the trigger? Search the repository for its origin — which commit, which step, and whether any recorded evidence supports it. **Report; do not choose.** *(The owner is separately checking Go's actual behaviour.)* | |
| **A.1b** | **UNCONDITIONAL, whichever way A.1a goes:** a `DataIntegrityViolationException` must not escape as Boot's legacy `500` body. It answers **422 with a full detail**, like every other refusal on this surface | |
| **A.1c** | **Then, and only then:** make the three enforcements agree, in the direction A.1a and the owner's answer establish. ⚠️ If the number is **not** released, the change is to **delete the claim** from the trigger and from the operator-facing message — much smaller than a `reversed_at` column, and it removes a promise Novocore cannot keep. If it **is** released, propose the schema change **with its concurrency argument stated**: a trigger's `NOT EXISTS` is not a substitute for a unique index under concurrent inserts, and that guarantee must not be traded away silently | |
| **A.1d** | ⚠️ **Generalise once, one probe:** is there a handler for `DataIntegrityViolationException` at all, and can any **other** index-enforced rule escape as a bare `500` the same way? **If finding 1 is one instance of a family, the family is named now** — not one member fixed | |
| **A.2** | `documentNumber` on `NewSalesInvoice` and `NewCreditNote`: `Required.text` + `@Mandatory`, so the refusal names the field and `tsc` refuses a form that omits it | |
| **A.3** | A regression test pairing **reversal with re-recording**, which no existing test does | |
| **A.4** | Record the `resolveSeries` **draft branch as unreachable by construction**, beside the `affectsStock == null` branch that already says so | |

### 🅑 Backend — what the screens need that does not exist

| # | Sub-part | Verdict |
|---|---|---|
| **B.1** | `?search=` on `GET /api/sales-invoices` and `GET /api/credit-notes`, adopting **target-list row 8**, with the counterparty-reach decision (association / denormalise / subquery) written down **at the code** — `customerId` is a scalar, so `TextSearch`'s dotted path is not available | |
| **B.2** | A GIN trigram index for every column added, and `SearchIndexIT` extended so a missing one is a build failure | |
| **B.3** | ✅ **DECIDED AT APPROVAL — `CUSTOMER_NAME` is NOT added to `SalesInvoiceSort`.** The endpoint already accepts a `customerId` filter — it is *why* an unfiltered call is refused — so grouping one customer's documents is a **filter** concern, not a **sort** concern. Sorting by customer name across a paged multi-customer list is a weaker case than it looked. If it is wanted later **it arrives with its own collation work**, which is where the obligation belongs | ✅ **Decided** |
| **B.4** | ✅ **DEFERRED AT APPROVAL, with the reason recorded rather than left silent.** The only shipping text sort key is `DOCUMENT_NUMBER`, and the two collations **agree on Latin document numbers**. The obligation stays with **whoever adds the first Greek-bearing server sort key**, exactly as the roadmap assigns it. ⚠️ **The owner is checking whether a real Prosvasis Go document number carries Greek letters; if it does, B.4 returns to scope for `DOCUMENT_NUMBER` immediately.** So: **note at the code where the `Pageable`-driven sort would have to be left**, so adding `ORDER BY … COLLATE "el-GR-x-icu"` later is a change to one ordering path and not a rewrite | ⏸️ **Deferred** |
| **B.5** | 📌 **Report only — do not fix here.** An unknown `customerId` and an unknown `seriesId` both answer a bare `404 "Not found."`, so a form cannot say which id was wrong | |
| **B.6** | ⭐ **NEW AT APPROVAL — the VAT precedence coupling.** `VatClassPrecedence` is line → customer → product, and **the island reduced rate does not appear in that chain.** ESTABLISH before building the line form: is the island mapping implemented anywhere, and if so where does it sit relative to those three? **If it is not implemented, say so plainly.** ⚠️ **Do not build the island rule** — it is an open question with the owner's accountant and the answer may **reorder the chain**. Record the coupling **at the code and here**, so whoever reads the accountant's answer knows F5's line form is downstream of it | |

### 🅒 Frontend — sales invoices

| # | Sub-part | Verdict |
|---|---|---|
| **C.1** | List screen over the **server-paged** endpoint. ✅ **Default range decided at approval: the current calendar year, 1 January to today.** Reason recorded: every accounting question this business asks is scoped to a fiscal year, the period lock (**D5**) is year-shaped, and the list is server-paged so row count is not the constraint. **The range is visible and changeable** | |
| **C.2** | `meta.sortKey` for `INVOICE_DATE`, `DOCUMENT_NUMBER`, `RECORDED_AT` — **the first sort keys in this repository** — and every other column plain text, not a disabled control | |
| **C.3** | Detail screen: read-only throughout; ΜΑΡΚ / UID / QR / transmission as **plain text with the no-route reason**; `inForce` / `reversal` / `reversed` read off the view | |
| **C.4** | Record form: **mandatory series picker**, no channel field and no document-type field, lines, settlement method | |
| **C.5** | ⚠️ **Preview before submit**, because the rounding acceptance control cannot be rendered from anything the form knows on its own | |
| **C.6** | The acceptance control, shown **only** when the preview reports `roundingNeedsAcceptance` — the value is silently dropped otherwise | |
| **C.7** | `<Refusal>` on every mutation; every reachable refusal rendered | |
| **C.8** | Reversal action and its four refusals | |
| **C.9** | **No `MutationCache` invalidation of its own** (the global handler covers it — a fourteenth copy is what the global fix exists to prevent), and **stateful `msw` handlers** so a save does not show pre-edit data | |

### 🅓 Frontend — credit notes

| # | Sub-part | Verdict |
|---|---|---|
| **D.1** | List screen — **not** server-paged, client-sorted, **no `sortKey` owed**, and the contrast with C.2 stated | |
| **D.2** | Detail screen, read-only | |
| **D.3** | Record form driven **from an invoice**: pick the invoice, then its lines; quantity, unit price and `stockReturned` per line; **everything else derived and visibly so** | |
| **D.4** | Its own preview and rounding acceptance | |
| **D.5** | Reverse action and its refusals | |

### 🅔 Explicitly out of scope, each with its reason

| # | Item | Verdict |
|---|---|---|
| **E.1** | **Document transformation** — needs the Go adapter; R1 stores only the allowed-target reference. **No partial version** | ⛔ **Out** |
| **E.2** | **R3 / self-supply posting** — a channel-less series is **refused** and the refusal rendered. **No accounts guessed** | ⛔ **Out** |
| **E.3** | Any ΜΑΡΚ / UID / QR write path | ⛔ **Out** |
| **E.4** | A *stock-not-moved* indicator — removed by decision in R1b. **Do not add one back** | ⛔ **Out** |
| **E.5** | ✅ **DECIDED AT APPROVAL — `GET /api/sales-invoices/rounding-differences` gets NO screen at F5.** It is a review queue for a workflow that **has never once run**, and there is no real data to review. It gets a screen when there are actual rounding differences to look at. **It keeps its endpoint and stays without a nav node** | ⛔ **Out, with a trigger** |

### 🅕 Contract test

| # | Sub-part | Verdict |
|---|---|---|
| **F.1** | `F5WriteContractIT` — the **literal JSON the screens build**, sent to a real server over real HTTP, on the `F4WriteContractIT` pattern. A screen test over `msw` proves wiring, never contract | |

### 🅡 RECONCILIATION as of 2026-08-06 — **COMPLETE. Every sub-part has a verdict and the live leg ran**

✅ **F5 landed on `main` in `c395324`** (a `--no-ff` merge of `f5-sales-invoice-credit-note`, so the
step has one identifiable landing commit while its thirteen individual messages survive). Frontend
**402 tests across 41 files**
as of 2026-08-06, typecheck / lint / knip clean; backend figures in the close-out paragraph at the
top of this file. **Spec unchanged at 247 operations / 231 schemas** — F5 adds screens, tests and one
comment, and **no operation and no schema**.

*(The paragraph below is this section's earlier state, kept because its figures were correct when
written: as of 2026-08-05, head `fef8497`, backend 1,488 tests and frontend 379 across 40 files.)*

⚠️ **Numbering note, because the approval conversation and this checklist use the same letters
differently in one place.** The owner's message referred to *"B.3/B.4"* for the sort-key and
collation decisions and *"B.5"* for the report-only item — **those map exactly onto B.3, B.4 and B.5
below.** **B.6 is the VAT precedence coupling**, which was added *at* approval as a new sub-part and
has no earlier number. There is no gap and no renumbering.

| # | Verdict |
|---|---|
| **A.1a** | ✅ **Done** — provenance traced to step 8 `V16`; the sales side contradicts its own comment. See 🅘 |
| **A.1b** | ✅ **Done** — `DataIntegrityViolationException` → 422 with a readable detail. **Proven against the defect** in a throwaway worktree: 2 failures naming the exact `500` body, positive control unaffected |
| **A.1c** | ⏸️ **Direction settled, DEFERRED to its own roadmap row N1** — owner decision 4. Not built, deliberately |
| **A.1d** | ✅ **Done** — the family is two instances of one defect. See 🅘 |
| **A.2** | ✅ **Done** — `Required.text` + `@Mandatory` on both `documentNumber`s. Spec diff 2 lines; generated type `documentNumber?: string` → `documentNumber: string` |
| **A.3** | ✅ **Done** — `DocumentNumberReuseIT`, 3 tests, asserting only what holds either way. **Stays as it is** per decision 4 |
| **A.4** | ✅ **Done** — draft branch recorded unreachable by construction at `resolveSeries` |
| **B.1** | ✅ **Done** — `search=` on both routes; `TextSearch.matchingRelated` + `CustomerSearch` + `SalesDocumentSeriesSearch`. ⭐ **The dotted path S1 built for exactly this case cannot serve it** — it needs a mapped association and every cross-aggregate reference here is a scalar id |
| **B.2** | ✅ **Done** — migration **V36**, 4 GIN trigram indexes, `TextSearchIT` extended. 5 new search tests, including one that plants a null series with SQL and proves the invoice is **not** dropped — the property the `IN`-versus-join choice exists for |
| **B.3** | ✅ **Decided at approval** — `CUSTOMER_NAME` **not** added to `SalesInvoiceSort`. Reason recorded at the columns file |
| **B.4** | ⏸️ **Deferred at approval, reason recorded — and NOT BUILT: there is no `el-GR-x-icu` collation on `DOCUMENT_NUMBER` anywhere.** ⚠️ Conditional on the owner's check of whether a real Go document number carries Greek letters; the two orders **agree on Latin document numbers**, which is the whole basis of the deferral. ⚠️ **CORRECTED 2026-08-06: this row previously read *"The `Pageable`/`COLLATE` note is at the code"* and that was HALF FALSE.** The note existed only in **`sales-invoice-columns.tsx`**, recording B.3's reason (why `CUSTOMER_NAME` is not offered). **B.4 asked for a note at the BACKEND place the `Pageable`-driven sort would have to be left, and there was none** — `grep` for `COLLATE` across `backend/` returned nothing. ✅ **Now written, at `SalesInvoiceServiceImpl.SORTABLE`**, saying that a Spring Data `Sort` cannot carry `COLLATE` so the change is one of **mechanism**, not expression |
| **B.5** | ✅ **Reported, not fixed** — an unknown `customerId` and an unknown `seriesId` both answer a bare `404 "Not found."` |
| **B.6** | ✅ **Done** — island rate absent; coupling recorded at `VatClassPrecedence` and in 🅘 |
| **C.1** | ✅ **Done** — list opens on 1 Jan → today, range visible and changeable |
| **C.2** | ✅ **Done** — the repository's **first three `meta.sortKey`s**, taken from the generated enum |
| **C.3** | ✅ **Done** — detail screen read-only throughout; statutory block is plain text with its reason |
| **C.4–C.6** | ✅ **Done** — record form, mandatory series picker, no channel/document-type field, preview-before-submit, conditional acceptance control. ⚠️ **TRANSITIONAL per decision 1** |
| **C.7** | ✅ **Done** — `<Refusal>` on every mutation |
| **C.8** | ✅ **Done** — reversal action with its refusals |
| **C.9** | ✅ **DONE 2026-08-06.** `sales.test.tsx` is now **19 tests**: 11 over the list and detail (which ⭐ **found a real date defect in the screen they were written against** — `toISOString` on a calendar date, fixed in `lib/calendar-date.ts`) and **8 over the record form**, centred on the preview-then-accept sequence the handover singled out. Handlers record their writes; the two captured bodies are asserted **equal**, which is the only thing on this side that can notice if preview and record stop being built by one function. **Proven against three injected defects**, each firing alone |
| **D.1–D.5** | ✅ **DONE 2026-08-06, at the scope decision 2 set.** List and detail at full product quality; the record form **thin**, with the *"test harness for a workflow with no production caller"* reason at the top of the file. **15 tests.** ⭐ The list's own assertion is that **no `sort=` reaches the server** — the contrast with C.2, which is the point of shipping both |
| **E.1–E.5** | ⛔ **Out of scope**, unchanged |
| **F.1** | ✅ **DONE 2026-08-06** — `F5WriteContractIT`, **6 tests**, real Boot server over real HTTP against real PostgreSQL. Scoped to what `R1bWriteContractIT` does **not** already drive: the credit-note body (never sent by anything before F5), the preview → refusal → accept sequence, `documentNumber`'s named refusal on both routes, and the list parameters — including that `search=` genuinely **narrows**, which a mock structurally cannot answer |

⭐ **Two things the guardrails caught in F5's own new code, both worth keeping:**

- **`tsc` refused the reversal form** because `ReversalCommand.reason` is mandatory (`Required.text`),
  and the record argues why — *"a reversal that says nothing about why leaves the ledger internally
  consistent and unexplainable."* It had been written as optional. **That is 8a's and A.2's contract
  work paying for itself on this step's own code, the same hour A.2 extended it.**
- The ESLint money rule caught `Number(id)` — correct to disable with a reason, and it forced the
  single-currency decision to be stated at the code instead of `'EUR'` scattered through the form.

### ✅ F5 IS CLOSED — **all 30 sub-parts have verdicts, the live leg ran, and it is on `main`**

**Nothing about F5 is open.** The step's 30 sub-parts each carry a verdict in 🅡 above; the live leg
ran on 2026-08-06 and **22 of 23 rows passed, with the 23rd never applicable** (L.23, conditional on
the deferred B.4). ⚠️ **R2b's live leg is separately closed** — it ran on 2026-08-05 and its two open
outcomes became roadmap rows **R2c** and **R4**, both recorded and **neither built**.

**⚠️ Three things F5 deliberately did NOT build, each with its own row, so none of them is a loose
end:**

| What | Where it lives now |
|---|---|
| **A reversed document's number becoming available again** | Roadmap **N1**. Direction settled by the owner; the fix must keep the concurrency guarantee, which a trigger's `NOT EXISTS` does not. **L.15's 422 is the expected behaviour until it lands** |
| **`ORDER BY … COLLATE "el-GR-x-icu"` on `DOCUMENT_NUMBER`** | **B.4, deferred**, conditional on whether a real Prosvasis Go document number carries Greek letters. The note naming where the `Pageable` path would have to be left is at `SalesInvoiceServiceImpl.SORTABLE` |
| **The island reduced rate in `VatClassPrecedence`** | **B.6**, an open question with the owner's accountant. ⚠️ The answer may **reorder the chain**, and F5's line form is downstream of it |

📌 **Three questions are with the owner and block nothing:** the two above (B.4's Greek letters and
B.6's precedence), and **where R2c sits in the sequence**, which nobody has decided — its roadmap row
sits outside the sequence for exactly that reason.

⚠️ **The next step is R2c or R4, and R4 is the one with a deadline attached**: it changes the sales
invoice request contract and **F6 should be built against the corrected model**, so it runs before
F6. R2c has no slot.

### 🅛 The live leg — **DERIVED from the screens and routes F5 ships**
### ✅ **RAN BY THE OWNER, 2026-08-06. EVERY APPLICABLE ROW PASSED**

**Result, one line: 22 of 23 rows passed and the 23rd was never applicable.** Verdicts are in the
`Result` column of the table below, one per row, per `CLAUDE.md` §*a live-leg block is DERIVED from
the screens a step ships*.

⭐ **L.15 is the row worth reading, and its verdict is a PASS for a refusal.** Reversing an invoice
and re-recording the same number in the same series is **refused with a readable 422**, not the
`500` in Boot's legacy body that F5's Phase 0 measured on 2026-08-05. ⚠️ **That is the EXPECTED
result while N1 remains unbuilt, and it is not a defect** — the row was deliberately written not to
assume the re-record succeeds, because **the direction was settled and the fix was not built**: a
partial index cannot express *"not reversed"*, and whatever replaces it must keep the concurrency
guarantee (`CLAUDE.md` §2b, roadmap **N1**). **A.1b is what turned the 500 into this refusal**, and
this row is the acceptance test for that half. ⚠️ **When N1 lands, this row's expected result
changes to a successful re-record — update it then**, and `DocumentNumberReuseIT` will fail in the
safe direction on the same day.

⚠️ **Two rows' conditions resolved before the leg ran, and they resolved oppositely:**

- **L.22 became UNCONDITIONAL** — B.1 shipped, so both lists have a search box and the *"confirm its
  absence is legible"* branch never applied. It passed as a search test.
- **L.23 was NEVER APPLICABLE** — it fires *only if B.4 returns to scope*, and B.4 is **deferred**
  pending whether a real Prosvasis Go document number carries Greek letters. 📌 **It is recorded as
  not applicable rather than as passed**, because a row nobody could run is not evidence of
  anything, and "23 of 23" would be a claim about a check that did not happen.

### ⚠️ L.0 — the preconditions, **re-measured 2026-08-06 and CHANGED. Read this before starting**

✅ **The app image was rebuilt and restarted on 2026-08-06**, unconditionally, per `CLAUDE.md`. That
half is done and needs nothing from the owner.

⚠️ **The rest of L.0 said "reactivate sales document type 1", and that is no longer the remedy.** The
live database was read on 2026-08-06 and has moved since P.15 was written — **four series and three
types, and every type is inactive**, so **no series can record anything**:

| Series | Type | State of that type | Channel |
|---|---|---|---|
| `TEST99` | 2 | ⚠️ **draft** — both stock flags NULL | **none** |
| `TEST2` | 1 | decided, **inactive** | `ECOMMERCE` |
| `TEST00` | 3 | decided, **inactive** | `STORE_AND_PHONE` |
| `TESTttt` | 3 | decided, **inactive** | `SKROUTZ` |

⚠️ **And L.8 is NOT reachable with this data, which is the part that would have been discovered
mid-leg.** `TEST99` is the only channel-less series, and its type is a **draft** — and on the
recording path the **type** check runs *before* the channel check, so it would be refused with the
draft-type message and the R3 sentence L.8 exists to read would never appear. *(Read from
`resolveSeries`; the browser is what settles it, which is exactly what L.8 is for.)*

**So the leg needs three owner actions first, all of them one-click screen work:**

1. **Reactivate sales document type 3** (`Test81`) → `TEST00` and `TESTttt` become recordable, which
   is what L.7 and L.11–L.15 need.
2. **Leave type 1 inactive** → recording against `TEST2` is L.9's refusal, already set up.
3. **Create one series with NO sales channel against type 3** → makes L.8's R3 refusal reachable.

⚠️ **Why this session did not perform them, stated rather than left as a gap:** driving the running
stack needs the **Owner password**, which is deliberately not in this repository — the same reason
`frontend/README.md` records for every browser leg since S1. They could have been done with direct
SQL, and were not: that reaches around the service, writes no audit entry, and is the shape this
codebase's first architecture rule exists to prevent.

| # | Derives from | Check | Result, 2026-08-06 |
|---|---|---|---|
| **L.1** | invoice list | Opens on **1 January of the current year to today**. The range is visible and changeable — the endpoint refuses without one | ✅ **Passed** |
| **L.2** | invoice list | Page 2 works; the `page` block drives the control and the row count is the server's total | ✅ **Passed** — server paging, on the first screen in this application to have it |
| **L.3** | invoice list | Only Date / Document number / Recorded are clickable headers; **every other header is plain text, not a dead button** | ✅ **Passed** — the three sortable, the rest plain text |
| **L.4** | invoice list | Sorting by Document number changes the **server's** order — page 1 changes, not just the rows in hand | ✅ **Passed** — the SERVER reordered, which is the half a browser had to answer |
| **L.5** | invoice detail | **Nothing is editable.** No Edit control anywhere | ✅ **Passed** — no Edit control anywhere, for a FULL-access role |
| **L.6** | invoice detail | ΜΑΡΚ, UID, QR and transmission read as **plain text saying Novocore never obtains one** — not blank, not disabled | ✅ **Passed** — plain text with the reason, not blank and not disabled |
| **L.7** | record form | The **series picker is mandatory**, and there is **no channel field and no document-type field**. Recording shows the channel the series supplied | ✅ **Passed** — mandatory series, no channel field, no document-type field |
| **L.8** | record form | Recording against the channel-less series is refused and **the message naming R3 is readable on screen** | ✅ **Passed** — reachable only because L.0's third owner action created a channel-less series against an ACTIVE type; the R3 sentence read on screen |
| **L.9** | record form | Recording against a series whose type was deactivated is refused with the *retired* wording | ✅ **Passed** — the *retired* wording, distinct from the draft wording |
| **L.10** | record form | ⭐ **Deactivate a payment method in Settings, then try to settle a new invoice with it** — refused. **R2b's carried item, first reachable here** | ✅ **Passed.** ⭐ **R2b's carried item, closed at its first reachable moment** — it needed a recorded invoice, so it waited for F5 |
| **L.11** | record form | A cash sale of **exactly €500.00** is refused; €499.99 records. The one refusal with no confirmation path | ✅ **Passed** — €500.00 refused, €499.99 recorded. ⚠️ The `>=` boundary P.5 corrected, confirmed at the one value the law cares most about |
| **L.12** | record form | A stated total differing by more than €0.03 → the preview **shows the difference and the threshold** and offers the acceptance; submitting without it is refused; with it, it records | ✅ **Passed** — difference and threshold both shown, refused without acceptance, recorded with it |
| **L.13** | record form | A difference **under** the threshold offers **no** acceptance control and posts silently | ✅ **Passed** — no acceptance control, posts silently. The negative half of C.6 |
| **L.14** | record form | The same document number in **two different series** both save; twice in one series is refused | ✅ **Passed** — R1a's C.6 per-series key, exercised by a human for the first time |
| **L.15** | ⚠️ invoice detail | **Reverse an invoice, then re-record the same number in the same series.** ⚠️ **The expected result is whatever A.1c establishes — this row does NOT assume the re-record succeeds.** It is the acceptance test for A.1, and today the behaviour is a `500` | ✅ **PASSED — and the pass IS the refusal.** A readable **422**, not the `500` Phase 0 measured. ⚠️ **Expected while N1 is unbuilt; this row changes when N1 lands** |
| **L.16** | series detail *(R2 L.8 carried)* | Once a series has recorded a document, its abbreviation / document type / ΜΑΡΚ flag are **shown, disabled, with the reason** — never hidden | ✅ **Passed** — shown, disabled, with the reason. ⭐ **R2's L.8 carried, and unreachable until a series had recorded a document** |
| **L.17** | series detail *(R2 L.5 carried)* | That frozen field reads as *frozen*, not as *not yours* and not as *broken* — **the one reachable `lockedReason` instance nobody has ever been asked to look at** | ✅ **Passed** — reads as *frozen*, not as *not yours* and not as broken. ⭐ **R2's L.5 carried — the one reachable `lockedReason` instance nobody had ever been asked to look at** |
| **L.18** | credit-note form | Picking an invoice fills its lines; **customer, channel, settlement method and VAT are shown as derived**, not as empty fields | ✅ **Passed** — the invoice's lines arrive as drafts, and the four derived facts read as derived |
| **L.19** | credit-note form | Crediting more than was sold is refused; a second note against the same line is cumulative | ✅ **Passed** — refused, and cumulative across notes |
| **L.20** | credit-note list | Sorting works **in the browser** — this list is not server-paged, and **the contrast with L.4 is the point** | ✅ **Passed** — browser sorting. ⭐ **The contrast with L.4 is the point, and both were seen in one sitting** |
| **L.21** | any two lists | Create an invoice, then a credit note, then revisit both lists **within 30 seconds** — the new rows are there. The global invalidation, on new screens | ✅ **Passed** — the global invalidation, on two screens that carry no copy of it. ⚠️ **Inside 30 seconds, which is the only window in which the defect is visible** |
| **L.22** | *(conditional)* | If B.1 ships: searching by customer name finds documents. If it does not, **there is no search box** — confirm its absence is legible | ✅ **Passed, UNCONDITIONALLY** — B.1 shipped, so this was a search test rather than an absence test |
| **L.23** | *(conditional)* | Only if B.4 returns to scope: a Greek document number sorts before a Latin one | ⛔ **NOT APPLICABLE, not passed.** It fires only if B.4 returns to scope, and B.4 is deferred. **Recorded as unrun, because a row nobody could run is not evidence** |

**Reconciled both directions at approval.** *Rows with no screen:* none — L.16/L.17 sit on R2's series
screen, which F5 makes **reachable** rather than ships, and that is stated. *Screens with no row:* the
`rounding-differences` report, deliberately (E.5). *Refusals with no row:* mixed currency, zero-total
invoice, inactive customer/product/VAT class, serial mismatch, bundle-with-serial-component — all
`422` through the same `<Refusal>` as L.8–L.11, so a browser adds nothing once one is confirmed
legible. ⚠️ **The draft-type refusal has no row because it is unreachable by construction (P.6)** —
a correction, not an omission.

### ✅ CARRIED, SEPARATE — R2b's live leg **RAN 2026-08-05** and its 13 rows are closed

**They were never merged into F5's block, and must not be** — they are R2b's, not F5's. Results are
in *R2b's live leg* above, row by row. In one line: **eleven of thirteen passed**, **L.13 closed §5's
open question** (the truncation was the select trigger, already fixed — the AADE list is clean), and
the two that did not pass produced **two new roadmap rows rather than fixes inside F5**:

- **R2c** — the sort code is not visible as a column (L.8), and is **absent from the series edit
  form** (found beside L.9). ⚠️ **Not built. Its own row.**
- **R4** — the payment-method **model** is wrong (L.10/L.11). ⚠️ **A requirement correction, not a
  defect. Not built. Its own row, and it runs after F5 and before F6.**

⚠️ **Neither enters F5's commit.** They are R2b's consequences; F5's scope is unchanged by them.

---

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

## ▶ R2b — what R2's live leg found, plus two things its brief had wrong. **DONE 2026-08-04**

**Five sections. §5 of the task (recording the live leg) landed separately in `bf3f950`; §1–§4 and
§5's fix are this.** Two of the five started from a premise that turned out to be wrong, and both
corrections are recorded against the premise rather than only against the outcome.

| # | Sub-part | Verdict |
|---|---|---|
| **1.1** | Establish whether the stale-list defect is R2's or older | ⚠️ **OLDER, and measured rather than argued.** **Not one of the thirteen create forms** in the application invalidates its list — products, customers, suppliers, users, roles, VAT classes, units of measure and all six of R2's. **R2 copied the pattern faithfully, including the defect** |
| **1.2** | The self-healing mechanism, recorded | ✅ **Done.** `staleTime: 30_000`. A list revisited inside 30 s is served from cache without the new row; after it, everything looks fine. **The bug fixes itself in half a minute**, which is why seven screens shipped with it and why it reads as "the browser being slow" |
| **1.3** | Fix it — **globally, one mechanism** | ✅ **Done.** One `MutationCache.onSuccess` on the shared `QueryClient`. The argument is the defect: thirteen copies of a line that must never be forgotten is what produced it, and a fourteenth form would copy it again |
| **1.4** | ⚠️ **A STRUCTURAL guard**, because a per-screen test proves nothing | ✅ **Done, and this is the important half.** With the fix global, **deleting it leaves every screen test passing** — no screen contains it. `query-client.test.ts` asserts the handler is present *and* that it works end to end through a real `createQueryClient()` with its real 30-second `staleTime`; a test client with caching off would pass against the defect |
| **1.5** | Proven red first | ✅ **Done.** Both tests run against the reverted fix: the structural one failed on the missing handler, the end-to-end one on the absent row. Then green once restored |
| **2.1** | ⚠️ Record it as what it is: **the screen was the only guard** | ✅ **Done**, and it is now a named anti-pattern in `CLAUDE.md`. `SalesDocumentSeriesServiceImpl.create` never read `isActive()`; the create screen filtered its picker and the edit screen did not filter at all. An adapter or a direct call was never constrained |
| **2.2** | Refuse a **draft** or **inactive** type on **create and change**, **both sides** | ✅ **Done.** `requireUsableDocumentType` in both series services, on both paths. 422 with the reason |
| **2.3** | ⚠️ **Draft tested FIRST**, with the reason at the site | ✅ **Done.** A draft is *always* inactive — the CHECK forces it — so testing `!isActive()` first would give every draft the milder message and make the specific reason unreachable. Commented at both sites and asserted by a test that rejects the wrong wording |
| **2.4** | ⚠️ **Setting refused, holding not** — deactivation must not be destructive | ✅ **Done**, and asserted directly: a series pointed at a type *before* it is retired still reads, still edits, still keeps its type. Without that, nobody would dare deactivate anything |
| **2.5** | Fix both pickers; the **current** value stays visible when inactive | ✅ **Done.** Active types **plus the currently-selected one if inactive**, labelled. ⚠️ Filtering it out would hit the `Select.Value` → `String(value)` trap `frontend/README.md` documents and render a raw id; the call site names that document |
| **2.6** | Proven against the defect first | ✅ **Done.** With both guards replaced by `if (false)`, **exactly the two guard tests failed and nothing else**; green once restored |
| **3.1** | `sort_code` on the four document tables | ✅ **Done.** `V34`. Named `sort_code`, not `code` — a field called `code` on a document type attracts identifier rules within a year |
| **3.2** | ⚠️ **INTEGER, not text** | ✅ **Done.** A text sort puts `1000` before `900`, which for a column whose purpose is ordering is the column failing at its job. The owner's own scheme is numeric with deliberate gaps (1xxx/2xxx/3xxx/4xxx/5xxx), so 1500 inserts between 1000 and 2000 without renumbering |
| **3.3** | ⚠️ **NOT NULL, backfilled** — the owner overruled the nullable proposal | ✅ **Done, and the reason is recorded at the column.** `series_id` stayed nullable because backfilling would **invent a series nobody authored**, which is a false statement about a legal document. **A sort code has no truth value** — order is arbitrary until someone chooses it — so an initial backfill (`id * 10`, ten-spaced) fabricates nothing. No NULLS-LAST decision to carry, no service-requires-but-column-permits divergence from A.7 |
| **3.4** | Unique per table; freely editable via `PATCH …/sort-code` | ✅ **Done.** Four routes. ⚠️ **Deliberately NOT the editable-while-unused freeze** — reordering is normal and the code appears on no document |
| **3.5** | Becomes the default order everywhere | ✅ **Done.** All four repositories' `findAll…` / `findByActiveTrue…` / `findByDocumentTypeId…` and the drafts query now order by it, so every list and every picker built on them follows — including the series picker F5 will use |
| **3.6** | First list column, and never derived from Go | ✅ **Done.** First, because it is what the list is ordered by. Go's numbers stay adapter data |
| **3.7** | The sortKey obligation, stated not implied | ✅ **Done.** All these endpoints are `{paged:false, sorts:[]}`, so client-side ordering over the whole list is correct today and **the obligation stays at 13 undischarged**. What changed is the default order the server returns |
| **4.1** | ⚠️ **The brief's premise was wrong: do NOT store the myDATA code** | ⭐ **Confirmed and recorded against the premise.** The codes have been on `SettlementMethod` since it was written — `CASH→3, BANK_DEPOSIT→1, CARD_POS→7, ON_ACCOUNT→5, SKROUTZ→5`, three open. *"None of these fields exists on an enum"* was true of abbreviation, description and active and **false of the code**. The view resolves it from the enum, so **there is nothing that can disagree** |
| **4.2** | `payment_method`, one row per enum value, no create | ✅ **Done.** `V35`. The enum constant **is** the primary key — no surrogate id, because a second identifier is a second thing to keep in step |
| **4.3** | ⚠️ **A drift test failing in BOTH directions** | ✅ **Done.** `PaymentMethodIT` — every enum value has exactly one row *and* every row an enum value. One direction alone passes while a new constant has no row, or while a stale row survives a rename. SQL cannot see a Java enum, so `V35`'s `DO` block only counts; this compares the names |
| **4.4** | The screen, on the AADE pattern, with the no-Add convention | ✅ **Done** — and it is that convention's **second instance**, which is the first evidence it is a convention rather than a description of one screen |
| **4.5** | ⚖️ **The `active` guard — build only if it belongs on the recording path** | ✅ **Built, and the condition was checked rather than assumed.** ⚠️ **It is NOT in the same service method §2 touches** — that is the series service; this is `SalesInvoiceServiceImpl.compute(...)`. But the substance held: `SettlementMethod` has exactly **one** caller-settable site, `NewSalesInvoice.settlementMethod`, read in `compute` beside `requireWithinCashLimit`, on the same path as R1b's refusals, shared by `record` and `preview`, and contract-testable today. **A credit note takes its method from the invoice it refunds — holding, not setting — so it is deliberately unguarded** |
| **4.6** | A sort code here too | ✅ **Done.** Same argument: eight options in enum-declaration order is not a sensible list |
| **4.7** | 📌 Cheque / Foreign bank account recorded | ✅ **Done**, in `V35` and the controller. They are in the owner's Go list and are **not** values of this enum; adding either needs an `AccountSystemKey` and two behaviour flags — **the no-create argument stated concretely rather than in the abstract** |
| **5.1** | ⚠️ **Correct the false claim first, with its mechanism** | ⭐ **Done — and it was wrong.** `line-clamp-1` truncates the **END**, so the `code —` prefix was **structurally safe** and the **group suffix** was what disappeared. Corrected below |
| **5.2** | ⚠️ Note that the claim was **amplified** | ✅ **Done.** It was the stated reason for pulling the item out of F10, so **the reason for pulling it was wrong** even though pulling it may still be right — the picker is used nineteen times and the label is long |
| **5.3** | The narrow width fix | ✅ **Done.** `OptionSelect` passes `w-full` unless a caller overrides, so the trigger uses the column instead of shrinking to content. **A width change only**; F10 keeps the styling sweep |
| **5.4** | ⚠️ Conditional on which element the owner meant | ✅ **CLOSED 2026-08-05 by the live leg — the element was the SELECT TRIGGER.** L.13: the AADE invoice types **list shows no truncation**. L.12: the picker's option text now reads in full. So 5.3's `w-full` fix addressed the element the owner actually saw, and **there is no second element to fix**. ⚠️ **This verdict is CLOSED, not "closed if"** — the condition was evaluated, not assumed away |
| **S.1** | Client regenerated, **every drifted fixture named** | ✅ **Done.** Spec **237 → 247 operations**, **226 → 231 schemas**, **170 → 175 declaring `required`**. ⭐ **Eleven fixtures drifted and `tsc` named every one** — 8a's `required` declaration doing exactly what it was built for. By file: `query-client.test.tsx` ×2, `purchase-document-series.test.tsx` ×2, `purchase-document-types.test.tsx` ×1, `sales-document-series.test.tsx` ×4, `sales-document-types.test.tsx` ×2. Five pinned counts updated with dated reasons |
| **S.2** | ⭐ **Record the live-leg lesson while it is fresh** | ✅ **Done**, in `CLAUDE.md`: **a live-leg block is DERIVED from the screens a step ships, never composed freehand.** R2's had ten rows against twelve items; four items had no row and three rows had no item. **F5's is built that way** |
| **S.3** | `CLAUDE.md`, `PROGRESS.md`, primer, roadmap | ✅ **Done** — all four |

### 🅛 R2b's live leg — ⭐ **DERIVED from the screens it touches, not composed freehand**
### ✅ **RAN BY THE OWNER, 2026-08-05. Results recorded below**

**This is S.2's lesson applied immediately rather than filed.** Every row below names the screen or
route it came from, and the two directions are reconciled by construction: there is one row per thing
a browser can answer that a test cannot, and nothing else.

| # | Screen / route it derives from | Check | Result, 2026-08-05 |
|---|---|---|---|
| **L.1** | §1 — sales document types, create | Create a type. **It appears in the list immediately**, with no manual refresh | ✅ **Passed** | ✅ **Passed** |
| **L.2** | §1 — **an OLDER screen**, e.g. suppliers or products | Create one there too. ⭐ **This is the row that proves the fix is global** rather than applied to R2's screens only — the defect was in all thirteen | ✅ **Passed, and it is the row that mattered** — the owner proved the global refresh on **both** a new screen and an older one, which is exactly what a per-screen fix could not have produced | ✅ **Passed** — server paging, on the first screen in this application to have it |
| **L.3** | §2 — sales series, create | Create a **draft** type (leave a stock flag unset), then try to create a series against it → **refused, naming the undecided stock behaviour** | ✅ **Passed** | ✅ **Passed** — the three sortable, the rest plain text |
| **L.4** | §2 — sales series, create | Deactivate a decided type, then try to create a **new** series against it → **refused with a DIFFERENT reason** (*not for new documents*), never the draft wording | ✅ **Passed, with the two wordings distinct** — which is the half 2.3 exists for: a draft is always inactive, so the milder message swallowing the specific one is the failure mode, and it did not happen | ✅ **Passed** — the SERVER reordered, which is the half a browser had to answer |
| **L.5** | §2 — sales series, detail | ⭐ **A series that already points at a type you then deactivate still opens, still edits, and keeps its type.** Setting is refused; **holding is not** | ✅ **Passed** | ✅ **Passed** — no Edit control anywhere, for a FULL-access role |
| **L.6** | §2 — sales series, detail | The document-type picker offers active types **plus the current one if it is now inactive**, marked — never a raw id and never an empty select | ✅ **Passed** | ✅ **Passed** — plain text with the reason, not blank and not disabled |
| **L.7** | §3 — all four document screens, create | The **Sort code** field is required, digits only, and the row saves with it | ✅ **Passed** | ✅ **Passed** — mandatory series, no channel field, no document-type field |
| **L.8** | §3 — all four document lists | The list is **ordered by sort code**, and the sort code is the first column | ⚠️ **HALF FAILED — see R2c §2a.** The **ordering is correct** (owner confirmed rows come back in sort-code order, on the document-type lists). The **column is not visible**. Display only | ✅ **Passed** — reachable only because L.0's third owner action created a channel-less series against an ACTIVE type; the R3 sentence read on screen |
| **L.9** | §3 — any document type or series, detail | Change a sort code → **it saves** (freely editable, no in-use freeze). Reuse one another row holds → **refused** | ✅ **Passed on DOCUMENT TYPES** — which is the path the owner exercised. 🐛 **And that is why it passed: the SERIES edit form has no sort-code field at all** — R2c §2b, found beside this row rather than by it | ✅ **Passed** — the *retired* wording, distinct from the draft wording |
| **L.10** | §4 — payment methods, list | Eight methods, ordered. **No Add control**, and the banner says why. `Open` where the myDATA code is genuinely unestablished | ⭐ **NOT A DEFECT — A REQUIREMENT CORRECTION. See R4.** The screen did what this row asked. **What the row asked for is the wrong model**: payment methods are a *business* list referencing an AADE codification, not a seed-only statutory list | ✅ **Passed.** ⭐ **R2b's carried item, closed at its first reachable moment** — it needed a recorded invoice, so it waited for F5 |
| **L.11** | §4 — payment methods, detail | Description and sort code editable; **myDATA code and behaviour are plain text with their reason**; deactivate and reactivate work | ⭐ **NOT A DEFECT — A REQUIREMENT CORRECTION. See R4.** Same: the fields are frozen because the row is seeded, and the rows should not be seeded | ✅ **Passed** — €500.00 refused, €499.99 recorded. ⚠️ The `>=` boundary P.5 corrected, confirmed at the one value the law cares most about |
| **L.12** | §5 — the AADE picker on a document type | Open it. **The option text is no longer cut** — `code — description · group` reads in full | ✅ **Passed** | ✅ **Passed** — difference and threshold both shown, refused without acceptance, recorded with it |
| **L.13** | §5 — ⚠️ **the open question** | If the truncation you saw was in a **column of the AADE invoice types LIST** rather than the picker, say so — that is a different element and is **not** fixed | ✅ **CLOSED, and the answer is the picker.** The AADE invoice types **list shows no truncation**. With L.12 passing, what the owner originally reported was the **select trigger**, and the `w-full` fix (5.3) resolved it. **§5's conditional 5.4 is closed, not conditional** | ✅ **Passed** — no acceptance control, posts silently. The negative half of C.6 |

⚠️ **One reconciliation gap, recorded rather than rounded away.** The owner reported **eleven of
thirteen passed** and named nine (L.1–L.7, L.9, L.12); L.13 closes as a tenth. The eleventh is not
individually attributable from the report — most likely L.10 or L.11, each of which **literally
passed the check as written** while the requirement behind it turned out to be wrong. **Nothing in
the results depends on which**: L.8 is a defect either way, L.10/L.11 are superseded by R4 either
way, and no row is left without a verdict. It is recorded because a count that does not reconcile is
the shape a missing verdict hides in.

⚠️ **What only a contract test can answer, and is therefore NOT on this list:** the payment-method
`active` guard. It refuses recording an invoice settled by a deactivated method, and **recording an
invoice needs F5**, which does not exist. It is verified in `R2ReferenceDataContractIT` over real
HTTP and is unreachable from any screen — the same shape as L.8 in R2's own leg. **Carried to F5.**

### ⚠️ One regression the global invalidation caused, and why the fix was the mock

`products.test.tsx` went red on *"saves one field and shows the server's answer"*. **The app is
correct**: after a PATCH every query is invalidated, the detail refetches, and a real server returns
the updated product. **The mock returned a static fixture**, so the screen appeared to revert to the
pre-edit name.

**The handler was made stateful rather than the assertion relaxed** — a mock that forgets a write the
app just made is describing a server nobody runs. 📌 **Any screen test with a static fixture can now
show pre-edit data after a save**; that is the mock being unfaithful, and this is the worked example.

## ▶ W1 — Part 2: a serialised record's wire shape equals its documented shape. **DONE 2026-08-04**

**Approved 2026-08-04 with four conditions, written here as a checklist at the moment of approval**,
per `CLAUDE.md` §*An approved proposal is a checklist, not a paragraph*.

### 🅟 Phase 0 — EVIDENCE, not decisions

**Everything below was established by asking the running system, never by reading source and
concluding.** The probe was a `@SpringBootTest` in the **app module** over the **real
Boot-configured `ObjectMapper` bean**, comparing `acceptJsonFormatVisitor` against
`Class.getRecordComponents()`; it carried a negative control and was deleted afterwards.

| # | Finding, measured 2026-08-04 |
|---|---|
| **P.1** | **197** record schemas on the committed surface; **32** serialise beyond their components, shipping **66** undocumented properties. Committed spec at the time: **247 operations, 214 paths, 231 schemas, 175 declaring `required`** |
| **P.2** | ⚠️ **PREMISE CORRECTED — the 32/66 figure did NOT predate R1a and R1b.** The roadmap's own ʷ¹ footnote dates it **2026-08-04, in R1b's Phase 0**, and the offender list contains R1a's `SalesDocumentTypeView`. What the re-measure actually establishes is narrower: **R2 and R2b added nothing** |
| **P.3** | ⭐ **R2's discipline held; there is no 67th.** Its three series/delivery views are on the surface with `inUse` as a documented **component**, `PaymentMethodView` and `AadeInvoiceTypeView` are clean, and none of the five is among the 32 |
| **P.4** | ⚠️⚠️ **PREMISE CORRECTED — `CLAUDE.md` stated the mechanism wrongly.** Jackson publishes **bean getters**, not "a record's no-arg public accessors". **222** non-component accessors exist, **79** are `is*`, Jackson publishes **66**, and **153 are invisible to it**. Proof: `issuedByUs()` → **`suedByUs`**; a control's `label()` was not published at all |
| **P.5** | The **79 − 66 = 13** residual is **fully attributed**: 11 on `Money`/`Quantity`/`Rate`/`UnitCost` (custom serialisers, never bean-introspected), 2 on `ProductView` whose published names are already components |
| **P.6** | **The generator has ONE path**, not two — `recordSchema` has no direction parameter, and `OpenItemRef` (reached from both directions) produced one schema |
| **P.7** | **No derived property appeared anywhere in the committed spec**, in either direction |
| **P.8** | **Deserialisation ACCEPTS a derived property.** `FAIL_ON_UNKNOWN_PROPERTIES = false`; the real mapper accepted a `NewSalesInvoiceLine` body carrying `exempt`/`product`/`serialized` and produced a record identical to the one without them. Baseline accepted, negative control (`productId` non-numeric) refused. ⚠️ **Mapper-level, not HTTP** — it was supporting evidence for the option *not* chosen |
| **P.9** | ⚠️ **My first deserialisation case answered the wrong question** — it omitted the mandatory `lineType`, so the refusal was about a missing field, not about the extras. Caught only by reading the output |
| **P.10** | **No exemption list is needed**, but a **scoping sentence** is: `Money`/`UnitCost`/`Quantity`/`Rate` are matched in `classSchema` first and carry hand-written schemas. ⭐ **The rule's first run proved this rather than my prose** — it reported `Money.amount` and `Money.currency` as promises nothing keeps |
| **P.11** | **Caller counts, from bytecode attributed by receiver type** (a type-blind grep was tried and discarded — it credited `StockLevels.empty` with 84 callers, which are `List.isEmpty()`). ⚠️ **PREMISE CORRECTED: the brief's "every one … with real callers" is wrong.** **15 of 66** have a production caller; **51** have none; **10** have **zero references anywhere** in compiled backend code |
| **P.12** | ⭐ **`SettingView.unset` is the concrete cost of the contract's lie.** `setting-row.tsx` computes `const configured = value !== ''` and `settings.test.tsx` called that "the only signal" — the backend has always computed `isUnset()` and it never reached the wire |
| **P.13** | **Generator route measured in an isolated worktree**: spec **+58 properties across 27 schemas**, **14 type errors in 9 files — every one a test fixture, none in application source** — **368/368** frontend tests pass, backend `BUILD SUCCESS` with **1,477** tests |
| **P.14** | ⭐ **Building it found a defect reading could not.** A name-based type lookup made the generator **non-deterministic**: `CustomerView` has both `isSystemRecord():boolean` and `systemRecord():Optional<CustomerSystemKey>`, and `Class.getMethods()` order is unspecified. The drift check caught it. **The type must come from Jackson's visitor** |
| **P.15** | ⚠️ **Two of my own diagnostics reported failure while measuring nothing** — a run whose `EXIT=1` was a *compile* error, and an AssertJ-truncated message that made a bogus 10,984-line "diff" look real. And **the background wrapper reported "exit code 0" while Maven reported `BUILD FAILURE`**, exactly as the brief warned |

### 🅐 The build, as approved — option B, four conditions

| # | Sub-part | Verdict |
|---|---|---|
| **A.1** | **Option B — document derived properties on RESPONSE schemas only** | ✅ **Done.** Spec **+58 properties across 27 schemas**; the four request records and `OpenItemRef` gained nothing |
| **A.2** | ⚠️ **The reason recorded as the owner framed it — SERIALISATION, not contract meaning** | ✅ **Done**, in `OpenApiSchema.recordSchema`'s javadoc, in `SerialisedRecordContractIT`'s failure message, and in `CLAUDE.md`: a request record is **deserialised through the canonical constructor, which sees exactly the components, and is never serialised at all**, so the seven request-side properties describe **a write that never happens**. ⭐ **B is therefore one rule — *describe what Jackson actually does with this record* — applied to two genuinely different mechanisms**, which is what stops a future session collapsing B back into A |
| **A.3** | **Condition 1 — the both-directions guard carries a POSITIVE CONTROL** | ✅ **Done.** `RECORDS_REACHED_FROM_BOTH_DIRECTIONS` is pinned **non-empty** (`OpenItemRef`) on `DocumentReferenceGraphIT`'s pattern, so an empty result cannot read as compliance. **Proven by forcing the set empty and watching the control fail** while the derived-property assertion still passed — which is the whole point |
| **A.4** | **Condition 2 — the deletion's reason recorded correctly** | ✅ **Done**, at the site. `OpenItemRef.isCustomerSide()` is deleted because it had **zero references anywhere in compiled backend code**; the comment states in as many words that simplifying B is **a consequence, not the justification**, and says what will happen to a reader who restores it believing otherwise |
| **A.5** | **Condition 3 — `AadeInvoiceTypeIT.theViewHasNoDerivedAccessorThatCanThrow` KEPT, reason restated** | ✅ **Done.** Untouched. `CLAUDE.md` and the new IT both state explicitly that the general rule **does not subsume it**: under W1 a **throwing** bean getter is now a *documented* property and still answers **500 on every row**. Documenting a field is not the field working |
| **A.6** | **Condition 4 — keep/delete for the ten zero-reference properties** | ✅ **Reported before regeneration; verdict KEEP on nine, DELETE on one.** Table below. Only `OpenItemRef.isCustomerSide()` was deleted |
| **A.7** | **The rule lives in the app module against the real mapper bean** | ✅ **Done.** `SerialisedRecordContractIT`, `@Autowired ObjectMapper`. ⭐ **Two sources, not one read twice**: the documented side is the **committed** `openapi.json`, the written side is **Jackson** — so an `OpenApiSchema` bug cannot make both agree |
| **A.8** | **Scoped to records built by `recordSchema`, with the reason written down** | ✅ **Done**, and expressed as `OpenApiSchema.builtByRecordSchema()` — something the generator knows — rather than a list a test hard-codes |
| **A.9** | **The type comes from Jackson's visitor, never a reflective lookup** | ✅ **Done**, with the `CustomerView.systemRecord` non-determinism recorded at the method as the reason |
| **A.10** | **Spec regenerated, client regenerated, every drifted fixture named** | ✅ **Done. 14 fixtures in 9 files, all named**: `query-client.test.tsx` ×2, `customers.test.tsx` ×2, `products.test.tsx` ×1, `purchase-document-series` ×1, `purchase-document-types` ×1, `sales-document-series` ×1, `sales-document-types` ×2, `settings.test.tsx` ×2, `vat-classes.test.tsx` ×2. **All were test fixtures; none was application source** |
| **A.11** | **Proven against the defect first** | ✅ **Done, four separate defects, each naming its own property** — a new derived accessor (`VatClassView.deliberatelyUndocumented`), a derived property documented on a request schema (`NewSalesInvoiceLine.exempt`), a restored `OpenItemRef.isCustomerSide()` (`OpenItemRef.customerSide`), and the positive control forced empty |
| **A.12** | ⚠️ **The proof script's RESTORE step reverted W1's own work** | ⭐ **Found, repaired, and written into `CLAUDE.md` as a third requirement of the throwaway probe.** `git checkout --` restored tracked files to **`HEAD`**, silently deleting the `OpenItemRef` change and the regenerated spec, and **failed loudly on the untracked new test**, leaving a defect in place. **All four proofs were still valid**; the restores were not. The loud half cost nothing, the silent half nearly shipped |
| **A.13** | ⭐ **The consequence for future steps, recorded** | ✅ **Done**, in `CLAUDE.md`: **R2's X.6 reason for choosing a component over a derived accessor has expired.** A derived accessor on a response record is now an ordinary documented part of its schema |
| **A.14** | **ONE commit, for 8a's CI reason** | ✅ **Done.** `frontend.yml` triggers on `docs/api/openapi.json` and `OpenApiSpecIT` fails on drift, so generator + spec + client + fixtures cannot be split without leaving `main` red |
| **A.15** | `.claude/settings.json` gitignored, not committed | ✅ **Done**, with the reason at the entry |
| **A.16** | `CLAUDE.md`, `PROGRESS.md`, primer, roadmap | ✅ **Done** — all four |

### 🅑 Condition 4 — the ten zero-reference properties, one line each

⚠️ **Zero callers in Java is not evidence a property is useless on a wire no client has ever been
given it on.** Nine are kept; the frontend could not have called any of them, because none was in the
generated client.

| Property | Verdict |
|---|---|
| `GoodsReceiptLineView.serialized` | **KEEP** — whether to show serial numbers on a receipt line; F6 needs it |
| `JournalEntrySummaryView.amendable` | **KEEP** — whether a journal list may offer Edit; F8 needs it |
| `OpenItemRef.customerSide` | 🗑️ **DELETE** — zero references, and `OpenItemType.isCustomerSide()` is where the question belongs |
| `PurchaseDocumentTypeView.draft` | **KEEP** — its sales twin *has* production callers; deleting one of a pair is the `supplier.vat_number` asymmetry |
| `PurchaseInvoiceLineView.inventory` | **KEEP** — the inventory/expense discriminator (8a's H.2); a purchase screen must render them differently |
| `SalesInvoiceLineView.exempt` | **KEEP** — an exempt line must display differently; F5 needs it |
| `SettingView.unset` | **KEEP, emphatically** — the screen already reconstructs it from `value !== ''`; documenting it removes a workaround |
| `SettlementView.receipt` | **KEEP** — receipt vs payment; F7 needs it |
| `StockConsumptionView.inForce` | **KEEP** — consistent with its `reversal`/`reversed` siblings, which are documented |
| `StockWriteOffView.reversed` | **KEEP** — same family |

### 📌 Queued out of W1, deliberately not done here

| Item | Where it goes |
|---|---|
| **The app image carries no commit label** — nothing can ask it what it was built from, which is what the R1a stale-artefact incident was diagnosed by reading a jar to answer | **F10**, attached to the build-SHA badge row (owner's placement) |
| **`CustomerView.systemRecord():Optional<CustomerSystemKey>` reads as if it returns the key, while the wire carries a boolean** from `isSystemRecord()` | Queued. `AccountView.systemKeyIfAny()` is this codebase's own idiom for the fix |
| **The settings screen still computes `configured` from `value !== ''`** rather than using the now-documented `unset` | Queued; noted in `settings.test.tsx` |

## ▶ W1 — Part 1: the roadmap re-sequenced, and a standing rule. **DONE 2026-08-04**

**Written as a checklist at the moment of approval**, per `CLAUDE.md` §*An approved proposal is a
checklist, not a paragraph*. Part 1 is documentation and governance only — **no production code, no
schema, no migration, no test changed.**

| # | Sub-part | Verdict |
|---|---|---|
| **1.1** | Add the sequencing rule to `CLAUDE.md`'s documentation-discipline cluster | ✅ **Done.** §*A sequencing decision changes the roadmap's ORDER, not a paragraph beside it*, placed between the design-conversation rule and the dated-figures rule — the two it sits between are the same failure one level up and one level down |
| **1.1b** | Record **why** the rule exists, with its two occurrences | ✅ **Done.** *"F5 is next"* survived in **four documents** after the owner had decided otherwise; and the **D-rows sat under `⚪ Placement TBD`** while three of the same items carried product-brief question numbers elsewhere. ⭐ **A third element was added that the brief did not ask for**: a *mechanical form* — move the rows, write the reasoning at the row, and **do not promote a status as a side effect of moving a row** — because *"update the roadmap"* is too vague to be followed and the promotion trap is the one this session actually met |
| **1.2a** | Reorder the roadmap rows to **W1 → F5 → D1+D3+D4+D5 → F6 onward** | ✅ **Done.** Phase 2's table now carries a labelled **▼ THE DECIDED SEQUENCE** block in exactly that order |
| **1.2b** | **D2 before step 19**, **R3 not schedulable**, **U2 on slack** | ✅ **Done**, and ⚠️ **as a judgement call worth naming**: they were moved into a labelled **▼ OUTSIDE THE SEQUENCE** group rather than left interleaved. **Leaving them in place would have stated that R3 comes between R2b and D1**, which is false the moment row order means order. `M0a`, `M0b` and `8b` are in the same group for the same reason. **No placement claim was changed** — only where the rows render. Reversible if the owner prefers them interleaved |
| **1.2c** | D1+D3 as **one** reopening, not two | ✅ **Done**, at the rows: **the same two entities and the same two screens** — codes, the supplier alias and both parties' addresses all land on Customers and Suppliers, so doing them apart reopens those two screens twice. Recorded in the ˢᵉᑫ note **and** in footnotes ᵈ¹ and ᵈ³ |
| **1.2d** | D4+D5 as one block — both ledger integrity, both the accountant's | ✅ **Done**, at the rows and in footnotes ᵈ⁴ and ᵛ. Neither shares a screen with D1/D3; both are read by the same person for the same reason |
| **1.2e** | ⚠️ **Nothing else moves; report status mismatches, do not promote** | ✅ **Done, and three are open.** **W1** is first and still ⚪ Unscheduled; **F5** is second and still 🟡 Current; the **four D-rows** keep ⚪. Each mismatch is tabulated in the roadmap under ˢᵉᑫ with a proposal that was **not applied** |
| **1.2f** | ⚠️ The `Placement TBD` text on D1/D3/D4/D5 | ✅ **Corrected in place, and the distinction is stated.** `Placement TBD` was **a claim that had become false** — four rows going on saying *nobody has decided where this goes* after somebody had. **Correcting a false claim is not promoting a glyph**: the ⚪ stays, because *placed* is not *scheduled* |
| **1.2g** | The two roadmap questions the decision **answered** | ✅ **Closed with the losing argument kept.** *"Do D1 and D3 land before or after F5?"* and *"Where do D4 and D5 sit exactly?"* are now ✅ under *Open decisions*, and §*The ⚪ rows share a deadline* no longer says the slots are open. ⚠️ **The cost is recorded, not argued away**: F5–F9 are built before the counterparty fields they will want, so **the document screens get touched twice** |
| **1.2h** | The **other three records** of the sequence, brought into line | ✅ **Done** — `PROGRESS.md`'s *What is next, in one place* (which now **follows** the roadmap rather than restating it), its step-16 row (*"Next is Q1, then R1, then F5"*, correct on 2026-08-02 and overtaken), the D1 row, the shared-gate row, and the primer's *"The next step is F5"*. ⚠️ **This is the rule's own failure mode**: a rule about two records disagreeing, added while four records disagreed |
| **1.3** | ⚠️ **One question the decision did NOT settle, flagged rather than absorbed** | 📌 **Open, at its row.** *"Should the Prosvasis Go adapter (18) come before F5?"* — step 18 was **not in the list the owner ordered**, so the sequence does not answer it, and **a decision to bring 18 forward would displace F5**. Left open with that consequence written down |

## ▶ R2 — document reference data, screens. **Phase 0 reported and approved 2026-08-04**

**Written at the moment of approval, one line per sub-part, per `CLAUDE.md` §*An approved proposal
is a checklist, not a paragraph*.** Phase 0 corrected four premises and found two documentation
defects before a line was written; each is recorded against the line it changed.

⚠️ **R2 grew a backend sub-part mid-approval (block X), and that is stated rather than absorbed.**
The step was scoped as screens. Phase 0's 0.7 found that a series' `abbreviation`, `documentTypeId`
and `getsMark`, and a delivery method's `abbreviation`, have **no write route on any installation** —
so the owner is about to hand-author nineteen Greek document types and their series with **no
correction path at all**, and deactivate-and-recreate burns the abbreviation permanently because
`…_abbreviation_unique` is not partial. The owner declined to defer it. It is new routes, new view
components, a spec change and a client regeneration inside a frontend step.

### 🅐 The six screens

| # | Sub-part | Verdict |
|---|---|---|
| **A.1** | **AADE invoice types** — list + detail. `TAX_AND_CHARGES`, nav under Settings | ✅ **Done.** List + detail, `TAX_AND_CHARGES`, under Settings. 55 rows, no create path |
| **A.2** | ⚠️ **No create control, ever** — `StatutoryCodification`. Row authorship is Flyway's | ✅ **Done.** No Add control anywhere, and **no `/new` route either** — a create form somebody added later would be unreachable |
| **A.3** | A permanent explanatory line in the units-of-measure banner style, saying AADE authors these 55 codes | ✅ **Done.** Permanent banner in the units-of-measure style: *"AADE authors these 55 codes… nobody can add one, on any installation."* |
| **A.4** | ⚠️ **An absence test** asserting no create control renders, naming *why* the omission is permanent rather than "not yet". **The convention's first instance** — the next seed-only screen copies it | ✅ **Done.** *"offers NO create control, permanently"*, asserted for a **FULL-access owner** — a role that could add one anywhere would add one here. ⭐ **Recorded in `CLAUDE.md` as a named convention**, with its next instance (`/api/vat-exemption-reasons`) named and the contrast against README state 4 ("not built yet") spelled out |
| **A.5** | Detail: `description` editable (`PATCH`), activate/deactivate, **`code` as `ReadOnlyField`** — no route on any installation | ✅ **Done.** Description editable; **code and group are `ReadOnlyField`** — plain text with the reason, never a disabled control. A test asserts no Edit button exists for either |
| **B.1** | **Sales document types** — list + detail + create. `SALES` | ✅ **Done.** List + detail + create, `SALES` |
| **B.2** | ⚠️ **Three-state stock control on CREATE** — `null` ≠ `false`. `SegmentedControl`, which already refuses to be emptied | ✅ **Done.** `SegmentedControl` with a third `Not decided` option; the create form **starts there** and **omits** the field rather than sending `false` |
| **B.3** | ⚠️ **Two-state on DETAIL**, because `StockBehaviourRequest` boxes both as `@Mandatory`: once decided a type **can never return to undecided**. Measured, not assumed | ✅ **Done, and it corrected the scoped design.** `StockBehaviourRequest` boxes both components `@Mandatory`, so once answered the question **cannot be unanswered**. The detail control shows `Not decided` **disabled with the reason** rather than removing it — an option that vanished between the two screens would leave somebody hunting |
| **B.4** | Activate control **shown, disabled, with the reason** while a stock flag is unset — `customer-detail.tsx:117–137`'s precedent, mirroring the backend's 422 | ✅ **Done.** Activate rendered **disabled with the reason beneath it**, on `customer-detail.tsx:117–137`’s precedent. A test asserts both the disabled control and the visible reason |
| **B.5** | AADE picker: `side=ISSUED` (**34**, not 55), grouped by annex 8.1 group, options rendered **`code — description`** | ✅ **Done.** `side=ISSUED` → **34**, not 55. Options render **`code — description`**, grouped by annex 8.1 group, rows unsorted within a group (dotted codes text-sort wrongly) |
| **B.6** | `requiresMydataTransmission`, description, and the AADE mapping's `PUT`/`DELETE` pair (clear = DELETE, never a null body) | ✅ **Done.** myDATA is a required choice with no default; the AADE mapping uses `PUT` to set and **`DELETE` to clear** |
| **C.1** | **Purchase document types** — the same, `PURCHASING`, AADE picker `side=RECEIVED` (**15**) | ✅ **Done.** Same screens, `PURCHASING`, `side=RECEIVED` → **15**. A test asserts the request carries `RECEIVED` and **never** `ISSUED` |
| **C.2** | ⚠️ **Grouping is kept on the purchase picker although it has one group** — uniform component, no branch | ✅ **Done.** One component, no branch on how many groups a side happens to span |
| **D.1** | **Sales document series** — list + detail + create. `SALES` | ✅ **Done.** List + detail + create, `SALES` |
| **D.2** | ⚠️ **Channel offers "not a sales channel" as an explicit option, never a blank** — six of the owner's series are channel-less. `PUT …/channel` is `@Mandatory`, so that option routes to **`DELETE …/channel`** | ✅ **Done.** *"Not a sales channel"* is a named option; choosing it sends **`DELETE …/channel`**, never a `PUT` of null. The create form **starts** on it |
| **D.3** | On-screen text: **a sales invoice's channel comes FROM its series** and is not settable on the invoice | ✅ **Done.** On the detail screen and the create form, with a test asserting the sentence renders |
| **D.4** | `transformableIntoSeriesId` — ⚠️ **singular, an `OptionSelect` + explicit "none"**, not a multi-select. The row being edited is omitted from its own options | ⚠️ **Done, PREMISE CORRECTED — it is SINGULAR.** `transformableIntoSeriesId` is one nullable id with `PUT`/`DELETE …/transformation-target`, so an `OptionSelect` plus an explicit "none", **not** a multi-select. Materially less work than scoped. The row is omitted from its own options (belt); the server refuses a self-reference with a 422 and a CHECK sits under that (braces) |
| **E.1** | **Purchase document series** — the same, `PURCHASING`. ⚠️ **No channel field and no channel column** — its absence is the decision | ✅ **Done.** No channel field, no channel column, **and three absence tests** — list column, detail control, create field. `onChannel` is not even passed, so a caller cannot wire one up |
| **F.1** | **Delivery methods** — list + detail + create. `SALES` | ✅ **Done.** List + detail + create, `SALES` |
| **G.1** | Six nav entries in `src/nav/tree.ts` with the right grant, checked against the spec by `tree.test.ts` | ✅ **Done.** Six nodes, **five different grants and none of them `SETTINGS`**; `tree.test.ts` checks each against the spec’s own `x-novocore-section` |
| **G.2** | Six `.test.tsx` files, each with `requests.expectNoWrites()` on render | ✅ **Done.** Six files, **48 new tests**; every screen asserts `expectNoWrites()` on render |
| **G.3** | i18n en + el for every new string; `enum-labels.test.ts` green | ✅ **Done.** 208 keys across four files, with **en and el key sets asserted equal** by the merge itself; `enum-labels.test.ts` green |

### 🅧 ⚠️ SCOPE ADDITION — editable while unused, frozen once used. **Backend work in a screens step**

| # | Sub-part | Verdict |
|---|---|---|
| **X.0** | **Report the exact "used" predicate per field before building it.** If any is expensive or ambiguous, **STOP and report** rather than guess | ✅ **Done, and reported before a line was built.** Sales series: `EXISTS (SELECT 1 FROM sales_invoice WHERE series_id = :id)` — index-only on `sales_invoice_series_idx`, and **one batched query per list** rather than an N+1. ⚠️ A **reversed** invoice counts (it was recorded, it is in the journal). A **transformation-target** reference does **not** (it is by id and survives any of the three changing). Purchase series and delivery methods: **`false` by construction** — see X.9. Neither expensive nor ambiguous, so no stop |
| **X.1** | `PATCH /api/sales-document-series/{id}/abbreviation` — refuses 422 with the reason once used | ✅ **Done.** `PATCH /api/sales-document-series/{id}/abbreviation` |
| **X.2** | `PUT /api/sales-document-series/{id}/document-type` — same | ✅ **Done.** `PUT /api/sales-document-series/{id}/document-type` |
| **X.3** | `PUT /api/sales-document-series/{id}/gets-mark` — same | ✅ **Done.** `PUT /api/sales-document-series/{id}/gets-mark` |
| **X.4** | The three purchase-series equivalents | ✅ **Done.** All three purchase equivalents. ⚠️ Their refusal is unreachable until F6 — built anyway, because a purchase abbreviation is typed by hand exactly as a sales one is, and an inconsistency with no argument behind it is the shape S1 caught with `supplier.vat_number` |
| **X.5** | `PATCH /api/delivery-methods/{id}/abbreviation` | ✅ **Done.** `PATCH /api/delivery-methods/{id}/abbreviation` |
| **X.6** | Views gain **one component** saying the row is in use — ⚠️ **a component, not a derived accessor**: W1 measured 32 schemas shipping 66 undocumented properties and R2 must not add a 67th | ✅ **Done.** One new **component** `inUse` on all three views — a component and **not** a derived accessor, so W1’s 66 undocumented properties do not become 67 |
| **X.7** | ⚠️ **The reason is composed on the screen, not sent by the server** — every existing `lockedReason` call site is a client i18n string, and Q47(b) says the backend localises nothing, so a server sentence would be English on a Greek UI | ✅ **Done.** The screen composes the reason. Every existing `lockedReason` call site is a client i18n string, and the backend localises nothing (Q47(b)) — a server sentence would render as English prose beside Greek labels |
| **X.8** | The screens use **`lockedReason`** (shown, disabled, reason) instead of the plain-text no-route treatment. That is what the addition buys | ✅ **Done.** The three fields are `lockedReason` — shown, **disabled**, with the reason — instead of the plain-text no-route treatment they had. That is what the addition buys |
| **X.9** | ⚠️ **A forward-looking test pinning the reference graph**, because *a purchase series is referenced by nothing today* so its freeze is unreachable and would silently become reachable at F6. Same shape as R1b's per-series key that agreed only because every row was null | ✅ **Done.** `DocumentReferenceGraphIT` reads `pg_constraint` **after every migration has run** and pins all three tables’ referencing sets, with failure messages that name the method still returning a hard-coded `false`. ⭐ **It carries a POSITIVE control** on the sales series, so an empty result cannot silently mean *"this test measures nothing"*. Written up in `CLAUDE.md` under the R1b entry it applies |
| **X.10** | `PermissionSweepIT`'s table, `RouteCoverage`, `WebExceptionMappingTest` | ✅ **Done.** `PermissionSweepIT`’s table is prefix-based, so the seven inherit their section automatically; `TradingQuarterOverHttpIT` excuses them as a **separate block with R2’s own argument** — these are *refused* once a series is used, which is the state the quarter spends ten invoices creating, so R1a’s "reference data a trading narrative does not touch" would have been the wrong reason |

### 🅢 Cross-cutting

| # | Sub-part | Verdict |
|---|---|---|
| **S.1** | Spec regenerated, client regenerated, **every drifted fixture reported BY NAME** | ✅ **Done.** Spec **230 → 237 operations**, **223 → 226 schemas**, **167 → 170 declaring `required`** (all measured 2026-08-04). Client regenerated. ⭐ **ZERO fixtures drifted, and that is measured rather than an omission**: `inUse` is required on three response types, so `tsc` would refuse any hand-authored fixture of them — there are none, because R2 is the first step to consume those records. **Four pinned counts updated** with dated reasons (`client-shape.test.ts` ×2, `spec-hygiene.test.ts` ×2); each went red first, which is what a pin is for |
| **S.2** | A contract IT against the **real running server** for every new write route (block X), on `F4WriteContractIT`'s pattern — literal JSON, not serialised from the record | ✅ **Done.** `R2ReferenceDataContractIT` — **7 tests over real HTTP**, JSON written as literals. ⭐ Both halves of the rule on **one series either side of one invoice**, so neither half can pass vacuously. ⭐ **Proven against the defect**: with the guard replaced by `if (false)` the refusal test failed naming the exact assertion, then passed again once restored |
| **S.3** | `CLAUDE.md`, `PROGRESS.md`, the primer and the roadmap updated | ✅ **Done** — `CLAUDE.md`, `PROGRESS.md`, the primer and the roadmap, in this close-out |
| **S.4** | ⚠️ **The dev seed, deferred from R1a — closes as done-by-correction.** No seed mechanism: `TradingQuarter` gains one `TEST-` purchase type, one `TEST-` purchase series and two `TEST-` delivery methods, created **over HTTP** like everything else it builds. The tables ship empty precisely so the owner authors them | ✅ **Done as a correction, and NO seed mechanism was built.** `TradingQuarter` gains one `TEST-` purchase type, one purchase series and two delivery methods, created **over HTTP** like everything else it builds — so one `LiveSeedTest` run now populates all five business tables. **No migration, no seed SQL:** the tables ship empty precisely so the owner authors them, and a seed would put rows in front of him he did not create and could not have refused. ⚠️ Three excuses in `TradingQuarterOverHttpIT` became redundant and were removed — the coverage assertion would have failed on them |
| **S.5** | ⚠️ **`frontend/README.md:518–521` corrected** — it is the only document left telling a future session to build the "stock not yet moved" indicator R1b deliberately removed, and `CLAUDE.md` §6 says not to add one back. Point at `CLAUDE.md`; do not restate the decision twice | ✅ **Done.** `frontend/README.md:518–521` corrected, and **pointed at `CLAUDE.md` §6 rather than restating the decision** — it saying so a second time is how the two came to disagree in the first place |
| **S.6** | `frontend/README.md`'s column-file count restated **13, dated** — that number has been wrong in three documents before | ⚠️ **Done, and the answer is TWO numbers.** **11 column FILES covering 13 LIST SCREENS**, dated in `frontend/README.md`. They differ because `document-type-columns.tsx` and `series-columns.tsx` are each shared by a sales and a purchase screen — one file, two lists, two endpoints that can gain paging independently. **The obligation is 13.** R2 owed no `sortKey`: all six endpoints are `{paged:false, sorts:[]}` in the generated `paging.ts`, so it inherits the obligation without discharging or worsening it |
| **S.7** | **NO SEARCH BOX**, recorded as a decision so nobody adopts a target-list row by reflex: no R2 entity is on the 16-row list, no R2 endpoint accepts `search=`, the largest list is 55 rows and all six are client-paged | ✅ **Done.** Recorded as a decision in each list screen’s javadoc and here: no R2 entity is on the 16-row search target list, no R2 endpoint accepts `search=`, and the largest of these lists is 55 rows returned whole. Adopting a row by reflex would have meant a migration, an index and a route parameter with nothing behind them |
| **S.8** | 📌 **`/api/vat-exemption-reasons` recorded, NOT built** — three write routes from R1a, no screen, and no record anywhere that it has no screen. **It is the AADE screen's twin** when someone builds it | ✅ **Done — recorded, NOT built.** `/api/vat-exemption-reasons` gained three write routes in R1a, has **no screen**, and nothing anywhere recorded that absence. Now in `CLAUDE.md` as the AADE screen’s twin, with the three points to copy. Not built: R2 was already carrying backend work it was not scoped for |

### 🅛 The live leg — the point of the step

| # | Sub-part | Verdict |
|---|---|---|
| **L.0** | ⚠️ **App image rebuilt** before hand-over. Unconditional | ✅ **Done.** Image rebuilt from `52c56ab` at `2026-08-04T13:23:46Z`, 19 s after the commit, and the startup line reported **237 handlers** — 230 before R2, so the seven new routes were provably in the deployed artefact rather than only in the repository |
| **L.1** | Create a type, leave a stock flag unset, try to activate — control **disabled with the reason visible** | ⚠️ **SPLIT — one half passed, one failed, and the split is the finding.** This row conflates **two** hand-over items. **Item 2 (the activate control) PASSED**: a draft refuses activation, disabled, with the reason visible. **Item 1 (creating the draft) FAILED** — symptom in §1 of the R2-live-leg task, **which is not in the message this session received**. ⭐ **The database proves the WRITE half worked**: `sales_document_type` id 2 (`Test 2`) has `affects_stock` and `transfers_stock` both **NULL** and `active = false` — exactly the draft the design specifies, so the service, the CHECK and the omit-rather-than-send-false form all behaved. The failure is on the reporting side, not the write side | ✅ **Passed** |
| **L.2** | Set both flags, activate, succeeds | ✅ **PASSED.** Hand-over item 3. Both flags set, activation succeeds — confirmed in the browser and consistent with the data (`Test` carries `affects_stock=false, transfers_stock=false`, i.e. it was decided and activated before item 7 deactivated it again) | ✅ **Passed** — server paging, on the first screen in this application to have it |
| **L.3** | Create a sales series with a channel and one without — both save | ✅ **PASSED.** Hand-over item 5. Both saved, and ⭐ **the channel-less one renders as a NAMED choice rather than a blank** — the point of the row. Confirmed in the data: `TEST99` has `channel IS NULL` and `TEST2` has `ECOMMERCE` | ✅ **Passed** — the three sortable, the rest plain text |
| **L.4** | An inactive document type is refused with a usable **4xx naming the reason**, never a 5xx or a bare "Bad request." | ❌ **FAILED — hand-over item 7 — AND ⚠️ THIS ROW WAS MAPPED ONTO THE WRONG PATH.** ⚠️ **The row's own subject is unreachable**: the "inactive document type refused with a 4xx" guard lives in `SalesInvoiceServiceImpl` line 230, on the **recording** path, so it needs **F5** exactly as L.8 does. What item 7 actually exercised was the **series-creation** path — and ⭐ **`SalesDocumentSeriesServiceImpl.create` has NO active check at all** (verified in the source, 2026-08-04: it calls `documentTypes.findById` and never reads `isActive()`), so **the screen's active-only filter is the ONLY guard on that path** and it did not hold. **Proven in the data, not merely reported:** `sales_document_series` id 2 (`TEST2`) points at document type 1, which is `active = false`; and id 1 (`TEST99`) points at type 2, which is not merely inactive but **a draft whose stock question has never been answered**. **Both series in the database name an inactive type.** Fix in §2 of the live-leg task (not in this session's message) | ✅ **Passed** — the SERVER reordered, which is the half a browser had to answer |
| **L.5** | A frozen field renders **shown-and-disabled with its reason** — not hidden, not silently read-only | ⚠️ **NOT VERIFIED, and no hand-over item covered its subject** — a reconciliation finding rather than a result. This row is about the **`lockedReason`** state (shown, **disabled**, with the reason). The nearest thing the browser confirmed is hand-over item 8 — the AADE code and group rendering as **plain text with the reason** — which is a *different* state (`frontend/README.md`'s **third**: no route on any installation). ⚠️ **R2 does ship one REACHABLE `lockedReason` instance and nobody was asked to look at it**: the `Not decided` option on a decided type's stock control, disabled because `StockBehaviourRequest` boxes both components `@Mandatory`. Every other instance is the series freeze, which needs F5. **Carry to F5's live leg** | ✅ **Passed** — no Edit control anywhere, for a FULL-access role |
| **L.6** | The AADE picker is usable at 34 Greek options, **including the two that read `Για Μελλοντική Χρήση`** | ✅ **PASSED.** Hand-over items 9 and 10. The picker is usable at 34 Greek options, and codes **4** and **12** both read `Για Μελλοντική Χρήση` with the note explaining that annex 8.1's cell is empty — so the `code — description` rendering is what makes them distinguishable, as designed. ⚠️ **One display defect**: the picker cell cut its text. Deferred to F10 at first, then **pulled back and fixed in R2b** (`w-full`). ⚠️⚠️ **The reason given for deferring-then-pulling was WRONG** — `line-clamp-1` truncates the END, so the `code —` prefix was never at risk and the group suffix was what was lost. See R2b §5 | ✅ **Passed** — plain text with the reason, not blank and not disabled |
| **L.7** | ⚠️ *(scope addition)* Create a series, **correct its abbreviation**, it saves | ✅ **PASSED.** Hand-over item 6. ⭐ **And it covered more than this row names**: the owner corrected the **abbreviation, the document type AND the ΜΑΡΚ flag** on a fresh series, so all three of block X's sales-side routes are browser-confirmed, not just the one this row lists. **This is the sub-part R2 grew for**, and it is the first evidence that the correction path an owner authoring nineteen Greek series actually needs is reachable from a screen | ✅ **Passed** — mandatory series, no channel field, no document-type field |
| **L.8** | ⚠️ *(scope addition)* The same field is **shown-disabled-with-reason** once the series has recorded a document — **if that state is reachable without F5** | ⛔ **NOT REACHABLE — recorded as unreachable, NOT as untested.** The frozen-once-used state needs a **recorded document**, and recording one needs **F5**, which does not exist. The row was written conditionally (*"if that state is reachable without F5"*) and the condition is false. ✅ **Its contract half IS verified** — `R2ReferenceDataContractIT` drives the freeze over real HTTP either side of one invoice, and was proven against the defect. **Carry the browser half to F5's live leg**, together with L.5 | ✅ **Passed** — reachable only because L.0's third owner action created a channel-less series against an ACTIVE type; the R3 sentence read on screen |
| **L.9** | State plainly which items are verified at the **contract level** and which **only the browser** can answer | ✅ **Done — see *What only the browser could answer* below.** Ten of twelve hand-over items passed, two failed, one row was unreachable and one had no item at all | ✅ **Passed** — the *retired* wording, distinct from the draft wording |

### ⭐ R2's findings — four premises corrected, and what R1's constraints did on meeting data

**1. ⚠️ `transformableIntoSeries` is SINGULAR.** Phase 0 was scoped believing it was a multi-select
over the same table. It is one nullable `bigint` with a self-FK and a `PUT`/`DELETE` pair. An
`OptionSelect` plus an explicit "none" — materially less work than scoped.

**2. ⚠️ The AADE picker is 34 options, not 55.** `?side=ISSUED` returns 34 and `RECEIVED` 15; the six
`ENTITY_ADJUSTING` codes are in neither. And the backend *enforces* the split — a sales type naming a
received code is refused 422 — so a 55-option picker would have been mostly certain refusals.

**3. ⚠️ Codes 4 and 12 are NOT blank, and the real problem is the opposite one.** V31 seeded both with
the group heading `Για Μελλοντική Χρήση`, and `…_description_not_blank` makes an empty one impossible.
So the danger was never a blank option — it is that **two different statutory codes carry
character-for-character identical descriptions**, and a picker showing the description alone offers
two options a human cannot tell apart. Hence `code — description` on every option, which also rescues
the three-character descriptions in `ISSUER_UNMATCHED`.

**4. ⚠️ Nothing freezes conditionally today — the fields had NO route at all.** Phase 0 was scoped
believing a series' abbreviation froze once it had recorded a document. It never froze, because it
was never editable: no route changed a series' `abbreviation`, `documentTypeId` or `getsMark`, or a
delivery method's `abbreviation`, on any installation. **That is what block X was added to fix**, and
it is why those fields are `lockedReason` now rather than the plain-text no-route treatment.

**5. 📌 Two documentation defects, found by reading rather than by a test.**
`frontend/README.md:518–521` was the **only document left telling a future session to build the
"stock not yet moved" indicator R1b deliberately removed** — a live contradiction pointing the wrong
way. And `/api/vat-exemption-reasons` has three write routes from R1a, no screen, and nothing
anywhere recording that absence.

#### ⭐ What R1's constraints did on meeting data — ⚠️ **CORRECTED after the live leg ran**

⚠️ **This section said "they still have not". That was true when it was written, before the owner ran
the live leg on 2026-08-04, and it is now WRONG — corrected rather than left standing.** Measured on
the live database **after** the owner's session:

| | Before the live leg | After |
|---|---|---|
| `aade_invoice_type` | 55 | **55** |
| `sales_document_type` | 0 | **2** — one decided-then-deactivated, one still a draft |
| `sales_document_series` | 0 | **2** — one with `ECOMMERCE`, one with `channel IS NULL` |
| `delivery_method` | 0 | **1** |
| `purchase_document_type` / `purchase_document_series` | 0 / 0 | **0 / 0** — not exercised |
| `sales_invoice` | 10, all `series_id IS NULL` | **10, all `series_id IS NULL`** |

**⭐ NINE constraints fired correctly on first contact with data rather than a fixture**, and each is
visible in the rows above rather than only in a report:

- **`…_active_has_stock_behaviour`** — `Test 2` carries both stock flags **NULL** with `active =
  false`. The draft state exists in the data exactly as designed, and the screen refused to activate
  it with the reason shown.
- **The nullable stock flags are genuinely three-state** — a NULL in the database, not a `false`.
  This is the constraint R1b's silent-consumption branch reads, and it survived a real form.
- **`sales_document_series_channel_known`** with a **NULL** channel — `TEST99` is channel-less and
  saved, which is the self-supply shape, and the screen offered it as a named choice.
- **The coherence rule** (`transfersStock` without `affectsStock`) refused before a request was sent.
- Plus the abbreviation, document-type and ΜΑΡΚ correction paths, all three exercised.

⚠️ **What did NOT get exercised, and the distinction matters:**

- **The per-series uniqueness key is STILL one group.** All 10 invoices still have `series_id IS
  NULL`, because nothing recorded an invoice — that needs **F5**. R1a's C.6 key remains enforced by
  nothing in practice, exactly as R1b left it.
- **The channel-less, inactive-series and inactive-type refusals are still unreachable**, for the
  same reason: all three live on the **recording** path.
- **`LiveSeedTest` still has not been re-run**, so the `TEST-` residue is still absent. The rows above
  are the **owner's own**, typed through the screens.

⚠️⚠️ **And the tenth constraint is the one that did not fire — item 7.** **Both** series in the
database point at an **inactive** document type: `TEST2` → type 1 (deactivated), and `TEST99` → type
2 (a draft that was never activated at all). Nothing refused either, because
**`SalesDocumentSeriesServiceImpl.create` has no active check** and the screen's filter was the only
guard. **This is the single most valuable result of the live leg**: it found a path where the screen
is load-bearing and nothing behind it is.

⭐ **One thing was confirmed rather than assumed while looking:** `sales_invoice` rows 8 and 9 share
the document number `TEST-SI-2026-0007`, which looks like the uniqueness key failing. It is not —
`sales_invoice_number_idx` is partial, `WHERE reversal_of_id IS NULL`, and one of the two is the
other's reversal. The constraint is intact.

⚠️ **And R2 met the same vacuous-constraint shape again, knowingly.** Two of its three `inUse`
predicates are `false` **by construction**: the only FK referencing `purchase_document_series` is its
own transformation target, and **nothing at all** references `delivery_method`. Those guards cannot
fire and would start being able to, silently, at F6 and 18b. `DocumentReferenceGraphIT` is the list
written down as a test — see `CLAUDE.md`, where it is recorded as the worked example of the remedy
the R1b entry prescribes.

### 🅛 The live leg — ran 2026-08-04. ⚠️ **TWELVE hand-over items against TEN checklist rows**

**The two lists do not map one to one, and reconciling them silently is how a sub-part goes
missing.** The `🅛` block above was written at approval time; the hand-over checklist was written for
the owner's browser and is a different shape. Both directions are reconciled here.

| Hand-over item | → row | Result |
|---|---|---|
| 1 — create a type with both stock questions unanswered | **L.1** (first half) | ❌ **FAILED**, §1 |
| 2 — Activate disabled with the reason | **L.1** (second half) | ✅ PASSED |
| 3 — set both flags, activate | **L.2** | ✅ PASSED |
| 4 — `affectsStock=No` + `transfersStock=Yes` refused before sending | ⚠️ **no row** | ✅ PASSED |
| 5 — a series with a channel and one without | **L.3** | ✅ PASSED |
| 6 — correct abbreviation, document type and ΜΑΡΚ | **L.7** (names only the abbreviation) | ✅ PASSED |
| 7 — deactivate a type, create a series against it | **L.4** ⚠️ *wrong path* | ❌ **FAILED**, §2 |
| 8 — AADE: no Add, permanent line, code/group plain text | ⚠️ **no row** | ✅ PASSED |
| 9 — codes 4 and 12 both `Για Μελλοντική Χρήση` | **L.6** | ✅ PASSED |
| 10 — the picker usable at 34 Greek options | **L.6** | ✅ PASSED *(display defect → F10)* |
| 11 — purchase series: no channel anywhere | ⚠️ **no row** | ✅ PASSED |
| 12 — delivery methods create + abbreviation correctable | ⚠️ **no row** | ✅ PASSED |

**⚠️ Four hand-over items had NO row in the approved checklist** — 4, 8, 11 and 12. None is a gap in
what was *built*: each is browser evidence for a sub-part verified elsewhere (**B.2/B.3** the
coherence rule, **A.2/A.3/A.5** the seed-only convention, **E.1** the channel absence, **F.1**
delivery methods). But the `🅛` block was written as *"the live leg"* and did not cover a third of
what the live leg actually needed to ask. **A live-leg block should be derived from the screens the
step ships, not composed freehand.**

**⚠️ Three rows had no item, for three different reasons** — and they are not interchangeable:

- **L.0** is a precondition *this session* performs, not something the owner does. Done.
- **L.5** — nothing covered its subject. A **reconciliation finding**, not a result.
- **L.8** — **unreachable**, and correctly written as conditional at approval time.

**⚠️ L.4 is the one that was mapped onto the wrong path**, and the mapping error was mine at
hand-over time rather than a defect in either checklist. The row means *the recording path refuses an
inactive type*; the item asked *does the series form offer an inactive type*. Both are worth testing,
they are different guards, and **only one of them exists** — see L.4's verdict.

### ⚠️ What only the browser could answer, now that it has — L.9

**Ten of twelve passed on first contact.** Recording them as evidence rather than ticks, because
several of these could not have been answered any other way:

- **A control being *reachable*.** `R2ReferenceDataContractIT` proves the server accepts and refuses
  the right things; it cannot say an operator can get to the control. Items 2, 6, 8, 11 and 12 are
  reachability results and nothing else could have produced them.
- **Whether a picker is *usable*.** Item 10 is a judgement, not an assertion — 34 Greek options with
  the `code — description` rendering. The owner's verdict is what settled it, and it came with a
  display defect no test would have reported.
- **Whether a refusal is *legible*.** Item 2 confirms not just that Activate is disabled but that the
  reason is visible beside it — the difference between a control that reads as broken and one that
  reads as answered.
- **Two failures that only a browser could surface.** Item 7 is a screen-side guard failing where
  **no backend guard exists at all**, and item 1 is a reporting-side failure over a write the
  database shows was correct. **Neither is visible to any test in this repository**, which is the
  standing argument for the live leg restated with fresh evidence.

⚠️ **Two rows are carried forward to F5's live leg** — **L.5** (the one reachable `lockedReason`
instance nobody was asked to look at) and **L.8** (the series freeze, which needs a recorded
document). Both have their contract half verified already.

### 📌 Explicitly NOT in R2, each with its reason

| Item | Why not |
|---|---|
| The owner's **real** nineteen types and series | He authors them after R2 lands, choosing each AADE type himself. The tables ship empty for exactly this reason |
| **Fees / Έξοδα και κρατήσεις** | Cut from R1, still unscheduled |
| **Settlement-method myDATA codes** | They live on an enum; there is nothing to edit. Not assumed into scope |
| **`company.branch-number`, the spec-version marker** | Report whether they need a screen at all — propose, do not build |
| **A `/api/vat-exemption-reasons` screen** | R2 is already carrying backend work it was not scoped for. Recorded (S.8), not built |
| **W1, 8b, R3, F5** | Other steps |
| **Purchase document types' behaviour** | F6's |
| **`SalesChannel`** | Not to be changed |

---

## ▶ R1b — document reference data, behavioural. **Phase 0 reported and approved 2026-08-04**

**Written at the moment of approval, one line per sub-part, per `CLAUDE.md` §*An approved proposal
is a checklist, not a paragraph*.** Phase 0 corrected four premises before a line was written; each
correction is recorded against the line it changed rather than summarised.

### 📋 The checklist

| # | Sub-part | Verdict |
|---|---|---|
| **B.0** | Environment: Docker daemon, Compose stack, **app-image rebuild**, Vite dev server | ✅ **Done.** ⚠️ **The rebuild was not insurance — the image was genuinely stale.** The new container applied **V31 and V32** on boot (`Current version of schema "public": 30`), so the image that had been running **predated R1a entirely**. Startup reports **230 handlers**, matching the committed spec. Vite on `http://127.0.0.1:5173/`; ⚠️ `https://localhost/` is **not** the frontend — Caddy proxies everything to the app, which serves no static assets and answers 401 |
| **R1b-1a** | `seriesId` becomes **mandatory** on `NewSalesInvoice`, guarded with `Required.field` | ✅ **Done**, `@Mandatory Long seriesId`. ⚠️ **NOT `documentTypeId`** — see premise correction 1 |
| **R1b-1b** | `SalesInvoiceServiceImpl` branches on the series' document type's `affectsStock` before `consumeStock` | ✅ **Done.** `resolveSeries(...)` resolves series → type → channel once and carries it in `SeriesContext`; `record` calls `consumeStock` only when `affectsStock` is true |
| **R1b-1c** | ⚠️ **SILENT** — no pending state, no marker, no warning | ✅ **Done, and asserted as an ABSENCE.** `R1bWriteContractIT` checks the wire body carries no property naming a stock gap, because "we deliberately report nothing" and "somebody forgot the field" are indistinguishable otherwise |
| **R1b-1d** | `stock_consumption`'s source CHECK is **not** widened | ✅ **Done** — untouched. A non-moving type creates no row, so there is nothing new to permit |
| **R1b-1e** | Tests for the new branch **with a negative control** | ⭐ **Done, and the control earned its keep TWICE.** `aStockMovingTypeStillConsumes` is the paired positive case; then the branch was **removed and the suite re-run**, and `aNonStockMovingTypeConsumesNothing` failed exactly as required. ⚠️ **The first attempt at that defect run reported PASS while running nothing** — see the findings below |
| **R1b-2a** | **Channel derived from the series** and removed from `NewSalesInvoice` | ✅ **Done.** Spec diff is 4 lines: `channel` → `seriesId` on one schema, and nothing else |
| **R1b-2b** | **REFUSE** a channel-less series, with a message naming what R3 is waiting on | ✅ **Done** in `compute(...)`, so **preview refuses what record refuses**. 422 with the reason; asserted over real HTTP |
| **R1b-2c** | `sales_invoice.channel` stays `NOT NULL` — **not relaxed** | ✅ **Done, and asserted.** `SalesInvoiceIT` queries `information_schema` for `is_nullable = 'NO'`, so relaxing it later fails the build rather than silently working |
| **R1b-2d** | **F5 has no channel field** — recorded where F5 will see it | ✅ **Done** — roadmap footnote ʷ (F5's own row), `SalesInvoiceService.record`'s javadoc, `NewSalesInvoice`'s javadoc, `CLAUDE.md` §6b, and the primer. ⚠️ F5 needs a **series picker** instead, and it is mandatory |
| **R1b-3** | **Refuse recording against an inactive series or document type** | ✅ **Done.** Both refusals name what is inactive; the draft case says so specifically. Proven over HTTP by recording successfully *first*, so the refusal is about the deactivation and not the fixture |
| **R1b-4** | `series_id` **stays NULLABLE**; reason **at the column**; a line for **step 24** | ✅ **Done.** Migration **V33**, comment only — no column change, no data change. Step 24 gains roadmap footnote ˢᵉʳ |
| **R1b-5** | `reverse()` copies `series_id` onto the reversal row | ✅ **Done**, and asserted (`aReversalCarriesTheSeries`) |
| **R1b-6** | Record that **`documentType` becomes mandatory THROUGH the series** | ✅ **Done** — `NewSalesInvoice`'s javadoc, `V33`'s header, `CLAUDE.md` §6b, the roadmap and the primer all say a reader will find no `document_type_id` column and that this is the design |
| **S.1** | Spec regenerated; client regenerated; **every drifted fixture reported BY NAME** | ✅ **Done. Exactly ONE fixture drifted:** `frontend/src/api/generated/model/newSalesInvoice.ts`. ⭐ **And four did NOT, which is worth stating because R1a had five:** `client-shape.test.ts` (230 operations / 134 writes — R1b adds no operation), `spec-hygiene.test.ts` (230 / 167 declaring `required` — the schema still declares five, a different fifth), `enums.json` (`SalesChannel` is untouched and still on the response side), and **no page fixtures, because F5 does not exist** |
| **S.2** | Contract ITs against the **real running server** for the changed write routes | ✅ **Done.** `R1bWriteContractIT` — 8 tests over real HTTP, JSON written as literals rather than serialised from the record, driving `POST /api/sales-invoices` and `/preview` |
| **S.3** | `CLAUDE.md`, `PROGRESS.md`, the primer and the roadmap updated | ✅ **Done** — all four, in this close-out |
| **S.4** | ⚠️ **NO DEV SEED.** Fixtures self-create; S.4 stays **fully** deferred to R2 | ✅ **Done as a correction, and nothing was built.** `TradingQuarter` creates its types and series **over HTTP** exactly as it creates customers and products; core ITs use `SalesDocumentFixture`. **No migration, no seed.** S.4 remains R2's |
| **N.1** | Record the **LiveSeedTest residue** | ✅ **Done** — primer and this file. `TEST-` document types and series will be the **first rows those tables hold** when R2's screens open; consistent with the residue already left for customers and products, and flagged so it is not a surprise |
| **N.2** | Record the **F6 inconsistency** | ✅ **Done** — new roadmap footnote ᶠ⁶: after R1b, sales `affects_stock` is read and purchase `affects_stock` is not, while `V31` lines 314–321 carry the column's strongest justification |
| **N.3** | Record `NewPurchaseInvoice`'s `requireNonNull` / `IllegalArgumentException` — record, do **not** fix | ✅ **Done, and NOT fixed.** In footnote ᶠ⁶ with its measured shape: a 4xx that names the field, so no guard fires and it is **not a live defect** — only a different message from the `Required.field` route on the same kind of record |
| **N.4** | Record the **derived-accessor guard as its own step** | ✅ **Done.** New roadmap row **W1** with footnote ʷ¹, carrying the mechanism, the `suedByUs` proof, the 32/66 measurement and the generator-route recommendation |

### ⚠️ Four premises Phase 0 corrected, before anything was built

**1. "documentType becomes mandatory on `NewSalesInvoice`" was written before the schema was on the
table, and could not be built literally.** `sales_invoice` carries **`series_id` and no
`document_type_id`** (`V32`), and `sales_document_series.document_type_id` is `NOT NULL`. So the
series supplies **both** the channel and the document type. Two independently settable fields could
disagree — which is exactly what R1b-2's own argument rejects for channel (*one fact, one place*).
**One new mandatory component: `seriesId`. The document type becomes mandatory THROUGH it.**

**2. No dev seed is needed, and S.4 is not partially undone.** The Phase 0 prompt assumed a small
seed addition would be required. It is not: **every fixture already creates its own reference data**
— `TradingQuarter` builds suppliers, customers, products, accounts and assets **over HTTP** and can
create a type and a series the same way (R1a shipped 22 type routes and 18 series routes);
`DocumentReferenceDataIT` already does it in-process. **Zero new seed, zero new migration**, and
**S.4 stays fully deferred to R2**, whose row already says so.

**3. The blast-radius list was missing two files, in both directions.** Measured by counting
construction sites, 2026-08-04: **54 sites of `NewSalesInvoice` across six files** — `SalesInvoiceIT`
(32), `TradingQuarter` (9), `CreditNoteIT` (5), `WholeScenarioIT` (4), `SettlementIT` (2),
`RefusalMatrix` (2). ⚠️ **`SettlementIT` was absent from the expected list**, including its shared
`saleOf(...)` fixture that every settlement test runs through. ⚠️ **`ReadBackChecks` was absent and
is an ASSERTION site** — it asserts `channel == "ECOMMERCE"` on the wire body, which makes it the
**canary**: the one place a wrong fixture surfaces as a wire-level failure rather than a compile
error. And **`PermissionSweepIT` constructs nothing** (its bodies are literal `{}`); **`LiveSeedTest`
constructs nothing** — both are affected only transitively.

**4. ⚠️ The derived-accessor guard is not a sub-part. It is its own step, and the survey is why.**
See *The derived-accessor guard — its own step* below.

### 📋 Every existing test R1b changed, and whether it was construction or assertion

**54 construction sites of `NewSalesInvoice` across six files, plus three files that needed something
other than a constructor argument. ⚠️ Exactly ONE change touched anything other than construction, and
it is the fourth row — read its reason.**

| File | What changed | Kind |
|---|---|---|
| `SalesInvoiceIT` | 32 sites: `SalesChannel.X` → `series(SalesChannel.X)`. Two `@Autowired` beans and a `SalesDocumentFixture` helper added | **Construction** |
| `TradingQuarter` | 9 sites → `id("series:web"/"series:store"/"series:skroutz")`; the three series and one document type now **created over HTTP** in the catalogue phase | **Construction** |
| `CreditNoteIT` | 5 sites. ⚠️ The shared `sale(…, SalesChannel channel)` helper **keeps its signature** and maps channel→series in one place, so 18 call sites read exactly as before | **Construction** |
| `WholeScenarioIT` | 4 sites | **Construction** |
| `SettlementIT` | 2 sites, one of them the shared `openSale(...)` fixture every settlement test runs through | **Construction** |
| `RefusalMatrix` | 2 sites; the now-unused `SalesChannel` import removed | **Construction** |
| `SalesInvoiceIT.duplicateNumberIsRefusedByTheDatabase` | ⚠️ The **raw SQL `INSERT` gained `series_id`**. Its assertion is untouched | **Construction — see below** |
| `TradingQuarterOverHttpIT` | Two **excuses deleted** — `POST /api/sales-document-types` and `POST /api/sales-document-series` are now genuinely driven | **Construction** |

⚠️ **NO ASSERTION WAS CHANGED IN ANY TEST.** The four the step was told to watch all passed unmodified:
`SalesInvoiceIT`'s `SALES_STORE_AND_PHONE` account assertions, `CreditNoteIT`'s `SALES_SKROUTZ` one,
`CreditNoteIT`'s `assertThat(note.channel()).isEqualTo(SalesChannel.SKROUTZ)` — which is now the
strongest evidence the derivation works, since **nothing in the request says SKROUTZ any more** — and
`ReadBackChecks`' wire assertion `channel == "ECOMMERCE"`, the canary, which needed no edit at all.

**Why the raw INSERT had to change, and why it is construction rather than assertion.** That test
bypasses the service to prove the *database* refuses a duplicate. Its INSERT omitted `series_id`, so
under `(COALESCE(series_id, -1), upper(document_number))` it landed in a different group from the
service-recorded invoice — **a legitimately different document, correctly not refused.** The test was
no longer building a duplicate. It builds one again now; what it asserts is unchanged. ⭐ A companion
test was **added** for the complementary fact (`theSameNumberInAnotherSeriesIsAllowed`), which was
unobservable before R1b because every row's series was null.

📌 **One near-miss worth recording.** A first attempt rewrote `CreditNoteIT` with a regex, which also
rewrote `assertThat(note.channel()).isEqualTo(SalesChannel.SKROUTZ)` — **an assertion** — and changed a
helper's parameter type. It was reverted and redone by hand. **A bulk edit across 54 sites cannot tell
a constructor argument from an assertion**, and this step's whole rule is that the difference matters.

### ⭐ R1b's findings — two defects, both invisible before this step

#### 1. ⚠️ Two enforcements of one rule, agreeing only because of what the data happened to look like

**Found by a test written to DOCUMENT the new behaviour, not to hunt a bug — which is the only reason
it was found.**

Document-number uniqueness is enforced twice on purpose: by the database trigger and partial unique
index, and by `SalesInvoiceRepository.existsStandingInvoice`, so the refusal explains itself instead
of arriving as a constraint name. **R1a changed one of them and not the other.** `V32` made the key
`(COALESCE(series_id, -1), upper(document_number))` because ΑΛΠ-1 and ΤΠΔΑ-1 are two different
documents; the service query kept checking the number **globally**.

⭐ **The two still agreed perfectly, and R1a's own migration says why** — *"With every row's series
NULL, which is every row today, the index is EXACTLY today's global index."* **That sentence is true,
and it is also exactly why the divergence was invisible.** The moment R1b gave an invoice a series,
the database allowed the second document and the service refused it: **the per-series key C.6 spent a
whole sub-part getting right would have been unreachable, enforced by nothing, with a green suite.**

**Fixed** — the query is series-scoped and reproduces the database's null semantics explicitly
(`existing.seriesId IS NULL AND :seriesId IS NULL`), because a naïve `=` would make two nulls **not**
collide and silently drop the guarantee for every pre-R1b invoice. Both directions are now asserted,
in `SalesInvoiceIT` and over HTTP. **The general lesson is in `CLAUDE.md`:** an argument of the form
*"identical for every existing row"* is a statement about data, not code, and it is also a list of
the places that will diverge once the data changes.

#### 2. ⚠️ A negative control reported PASS while running nothing

**The stock branch's control was run against deliberately-broken code** —
`-Dit.test='SalesInvoiceIT#aNonStockMovingTypeConsumesNothing+aStockMovingTypeStillConsumes'` — and
reported **`BUILD SUCCESS`**. The branch really had been removed. **Failsafe never matched the
`Class#a+b` selector, ran nothing, and `-Dfailsafe.failIfNoSpecifiedTests=false` turned "measured
nothing" into green.** Re-run with the plain class selector it failed correctly, on exactly one test.

✅ **CLOSED BY CONFIGURATION, 2026-08-04, not by a rule.** ⚠️ **The rule it violated already existed**
— *"if the negative control passes, every other result in that run is void"* — and could not fire,
because the run reported **success**. Four earlier members of this family were also already covered by
written rules. So the lever had to be the tool.

⭐ **Neither pom carried `failIfNoSpecifiedTests` at all**, for surefire or failsafe: both were on
their default, which is **already `true`**. **Nothing in the repository was ever wrong — the `false`
only ever came from a command line.** The fix is therefore to **pin `true` in the `<configuration>`
of both plugins**, because an explicit `<configuration>` value beats the user property a `-D` feeds.
`-Dfailsafe.failIfNoSpecifiedTests=false` is now inert. **Proven by running the exact command shape
that silently passed and watching it fail**, and by confirming a selector-free build is unaffected.

⚠️ **The cost, stated rather than hidden: `-am` together with `-Dtest`/`-Dit.test` no longer works** —
a reactor visits modules that legitimately lack the named test, and each now fails.

⚠️ **And that cost has a hazard in it, which is why the replacement is written down rather than left
to be improvised.** The obvious workaround is to drop `-am` — and `-pl X` without `-am` is **already a
named member of the stale-artefact family**, the one that produced two mis-diagnoses inside twenty
minutes in R1a. ⭐ **Measured 2026-08-04, not argued:** the jars in `~/.m2` were from the previous day,
and `./mvnw -pl app clean test-compile` compiled `TradingQuarter` against that stale `core-api`,
failing with `long cannot be converted to SalesChannel` — R1b's own change. `dependency:build-classpath`
confirmed the classpath named `~/.m2/…/novocore-core-api-0.1.0-SNAPSHOT.jar`. 📌 **And the first
attempt to demonstrate it reported `BUILD SUCCESS`, because without `clean` it said *"Nothing to
compile"* and compiled nothing** — the same family one layer down.

**✅ The replacement, verified end to end:**
`./mvnw -pl <module> -am install -DskipTests && ./mvnw -pl <module> verify -Dit.test=<Class>`.
Step 1 rebuilds every dependency from source; step 2 has no `-am`, so no module without the test is
visited. ⚠️ **`&&`, never `;`** — a part-way `install` is the R1a bite, and `&&` makes proceeding from
one impossible. Proven: step 1 exit 0 → step 2 compiles clean with R1b's change picked up, and a name
matching nothing **still fails**, so the two-step does not reopen the hole. **The always-safe option,
and the right one for a negative control, remains the module's whole suite** (`./mvnw -pl core -am
verify`).

⚠️ **One documented command was broken by this and is fixed: `LiveSeedTest`'s javadoc**, which said
`mvn -pl app test -Dtest=LiveSeedTest -Dsurefire.failIfNoSpecifiedTests=false …`. Two faults: the flag
is now **inert**, and `-pl app` without `-am` is the trap above — **on a seeder that writes to a live
database**, where an old request shape would be transmitted to a real server. It now uses the two-step.
**CI is unaffected** — `.github/workflows/backend.yml` runs `./mvnw verify` with no selector, and a
selector-free build never engages the flag.

📌 **The third, smaller one is closed by the same pin.** After that red run, a targeted `verify` on a
*different* module reported *"There are test failures in novocore-core"* — no core test had run.
**The mechanism:** failsafe writes `failsafe-summary.xml` and `verify` reads it; when
`integration-test` matched nothing it **did not rewrite** the summary, so `verify` read the previous
run's. ⚠️ **No configuration parameter exists for that** — but with a failing summary deliberately
planted, the same command now fails at `integration-test` naming the real problem, **before** `verify`
reads the stale file. **Residual:** `mvn failsafe:verify` as a *bare goal* still reads a stale summary;
that one is `clean`-and-discipline, and nothing in this repository invokes it that way.

### ⭐ The derived-accessor guard — its own step, with a measured scope

**It was folded into R1b assuming it was small. Phase 0 measured it and it is not, and that is the
finding rather than an inconvenience.**

**The rule, framed as CONTRACT FIDELITY:** the properties **Jackson writes** must equal the
properties **`OpenApiSchema` documents**. That gives every record two honest ways to comply —
delete the accessor, or document it — instead of one style prohibition.

⚠️ **THE MECHANISM IS JACKSON, NOT ASM, and the probe proved why.** 8a needed ASM because it needed
*argument attribution*; here the question is *"what would Jackson call this"*, and the only correct
oracle is Jackson: `ObjectMapper.acceptJsonFormatVisitor(...)` — which builds the real serializer —
compared against `Class.getRecordComponents()`. **The proof:** a control record shaped exactly like
R1a's defect showed that Jackson **did not publish `issuedByUs`** — it stripped the `is` prefix and
published **`suedByUs`**. Nobody would derive that by reading, and reimplementing Jackson's naming
rules in ASM would be a second implementation that agrees until it does not.

⭐ **It needs no exemption list.** `equals`/`hashCode`/`toString` are not accessors, static factories
are static, the compact constructor is not a method, and **`…IfAny()` is invisible to Jackson** —
all four fall out of the mechanism. ✅ `CLAUDE.md`'s claim that `…IfAny()` is the safe exception is
**confirmed, and for a better reason than the one recorded**: not merely that it cannot throw, but
that Jackson never sees it.

**📊 The measured scope, 2026-08-04.** Probe scanned **203 records** with `NovoCoreJsonModule`
registered (so `Money`, `Quantity`, `UnitCost` and `Rate` correctly drop out — they have custom
serialisers). **46 records serialise beyond their components; 32 of them are schemas on the
committed API surface, shipping 66 properties the spec does not document.** Confirmed behaviourally
on at least one: a real `writeValueAsString` of a real `SalesDocumentTypeView` emits
`"draft":false`, which is in no schema.

```
AccountView[contra, settlementTarget]        CreditNoteView[inForce, reversal, reversed]
CustomerCreditView[exhausted, untouched]     CustomerView[mergeable, systemRecord]
FreightAllocationView[reversal, reversed]    GoodsReceiptLineView[awaitingInvoice, serialized]
GoodsReceiptView[inForce, reversal, ...]     GrIrMatchView[unfavourable]
InventoryLotView[open]                       JournalEntrySummaryView[amendable, reversal, reversed]
JournalEntryView[amendable, balanced, ...]   JournalLineView[debit]
NewGoodsReceiptLine[serialized]              NewPurchaseInvoiceLine[exempt, inventory]
NewSalesInvoiceLine[exempt, product, ...]    NewStockWriteOff[serialized]
OpenItem[fullySettled, untouched]            OpenItemRef[customerSide]
ProductView[redacted, stocked]               PurchaseDocumentTypeView[draft]
PurchaseInvoiceLineView[awaitingDelivery,…]  PurchaseInvoiceView[inForce, reversal, reversed]
SalesDocumentTypeView[draft]                 SalesInvoiceLineView[bundle, exempt]
SalesInvoiceView[inForce, reversal, ...]     SerializedUnitView[onHand, sellable]
SettingView[unset]                           SettlementView[fullyAllocated, receipt]
StockConsumptionView[inForce, return, ...]   StockLevels[empty, oversold]
StockWriteOffView[reversal, reversed, ...]   VatClassView[zeroRated]
```

**None is a live defect.** Nothing sets `additionalProperties: false` (2 of 223 schemas do, neither
of these), the generated TypeScript simply lacks the fields, and no client breaks. **What is wrong
is that the contract lies about 32 schemas** — the silent half `CLAUDE.md` predicted and said
nothing would ever report. **It is the first time this project has had a real number on that
question.**

⭐ **EVALUATE THE GENERATOR ROUTE FIRST when that step is scoped.** Teaching `OpenApiSchema` to
describe what Jackson serialises would document all 66 in **one change** instead of editing 66
records, and the rule would then verify the generator rather than police records. ⚠️ **It is not
free:** 8a's rule makes primitives `required`, so 66 new required booleans means fixture
reconciliation across 32 schemas. **Weigh it there, not here.**

**Placement: the `app` module, against the real Boot-configured mapper bean** — the probe used a
`JsonMapper` built in a test, and the thing that decides must be the thing that answers.

⚠️ **R1b must not ADD a violation.** Review discipline rather than a rule, since the rule does not
exist yet: the records R1b writes carry no derived accessors.

---

## ▶ U3 — eleven design decisions written into the repository, 2026-08-03

**Documentation and governance only.** No production code, no schema, no migration, no test was
changed. `U` is the prefix for exactly this (see the roadmap's ID-convention note): a session that
changes documentation and governance and produces none of the above.

**Why this session existed, stated plainly because it is the point.** Eleven decisions had been
settled in a design conversation and existed **nowhere in this repository**. That is the failure
`CLAUDE.md` §*A decision reached in a design conversation gets the same close-out discipline as a
build step* was added to prevent — the same shape that left *"F5 is next"* standing in four documents
long after the owner had decided otherwise. **This session is that rule being applied rather than
described.**

### 📋 The nine sections of the approved scope, one line each, with verdicts

| § | Decision | Verdict |
|---|---|---|
| 1 | **D5 — period locking by a movable lock date**, owner-only, every move audited, blocking both edits *and* new postings into a closed period | ✅ **Recorded.** Roadmap footnote ᵛ rewritten; the reversal-dating consequence recorded as a **requirement on whoever builds D5** and as something to confirm against the code, deliberately **not** confirmed here |
| 2 | **D4 splits in two.** Half one (sales and purchase document numbers) is **already answered and needs nothing built**; half two (Novocore's own internal documents) is what D4 keeps | ✅ **Recorded.** Roadmap row rewritten to half two only, new footnote ᵈ⁴, and half one recorded as answered. ⭐ **Step 7 had already recorded the question** — see *What the repository already said* below |
| 3 | **D1 — codes are nullable and for the business's own reference; supplier has an alias, customer never does** | ✅ **Recorded.** New roadmap footnote ᵈ¹; the asymmetry is recorded **as a decision, not an oversight**, which is the whole reason it needed writing down |
| 4 | **D3 — addresses are structured, conditional at the document, and the row shrinks**; per-order shipping moves to step 22 | ✅ **Recorded.** New footnote ᵈ³, and step 22 gains footnote ˢᵒᶠ so the moved requirement is read where the work is |
| 5 | **Vouchers — the courier adapter has two modes**, receive an existing Skroutz voucher or create one for Woo and phone orders | ✅ **Recorded** against step 21, footnote ᵃᶜˢ, **before that step is scoped** |
| 6 | **M0 splits.** M0a is a **mapping exercise, not an import**, and is unblocked now; M0b is the real year and waits on D1/D3/D4 | ✅ **Recorded.** Roadmap row split into two, footnote ᵐ⁰ rewritten, and **why not after F11** kept because it was the owner's initial instinct |
| 7 | **D2 — direction, and the one-time load is not the adapter.** Categories import as-is; **stock must not come from Woo**; Woo is read-only for product data after cutover, **scoped** to the fields Novocore manages | ✅ **Recorded.** Footnote ᵗ extended, step 19 gains footnote ʷᵒᵒ, and the field-ownership list is recorded as **owed at step 19** rather than described as existing |
| 8 | **Re-sequencing — the shared gate is the decision; the individual slots are not** | ✅ **Recorded.** The gate is a note under the Phase 2 table; the four decided placements are applied; **nothing else was promoted or reordered**, and the F5-versus-D1/D3 trade is in *Open decisions* stated as a trade rather than resolved |
| 9 | **One build script — make the safe path the easy path.** Recorded as unscheduled, **not built** | ✅ **Recorded** as a cross-cutting obligation with a trigger, plus a pointer in `CLAUDE.md` beside the piped-build rule it exists to make unnecessary |

**Out of scope and deliberately untouched:** the derived-accessor architecture rule (a rule over
*every* response record, the general version of the two one-record guards R1a built). **It is code,
it belongs to R1b**, and this session wrote no pointer to it beyond this sentence.

### ⭐ What the repository already said, and it sharpens two of the decisions

**Neither of these was in the prompt. Both were found by reading the record before writing to it.**

- **D4 half two was already an open question, filed at step 7, and it names the format decisions.**
  The journal-entry section records: *"**No entry number.** The id is the handle. A human-facing
  sequential number is a real thing an accountant asks for and carries a format decision (per-year
  reset? prefix per source?) nobody has been asked."* So D4 half two is **not a new requirement** —
  it is a question this project has been carrying since step 7, and **those two format questions are
  D4's to answer**: does a counter reset per year, and is the prefix per document type or per source?
- ⭐ **Three of these decisions already had product-brief QUESTION NUMBERS, and nothing connected
  them.** **Q40** — *"a human-facing document number for the documents NovoCore owns"*, with step 10
  adding freight allocations to its list — **is D4's remaining half, exactly.** **Q37** — *"addresses
  on Customer and Supplier, plus human-facing codes"* — **is D3 and D1 together.** Both were sitting
  under *Also still open, not blocking anything* while the D-rows sat under *Placement TBD* in the
  roadmap, **two records of the same open items with no cross-reference between them.** Both question
  entries are now annotated with what U3 answered and, more importantly, **what it did not**: Q37's
  *multiple selling prices per product* is untouched by D1 or D3 and remains unasked.
- **D5 has two standing statements built on its absence**, and whoever builds it must revisit both
  rather than discover them. Step 3: *"There is no delete, only `deactivate`. **With no period
  locking** there is no point at which an account is safely finished with."* Step 7: `entry_date`
  *"has a floor of 2000-01-01 and **no upper bound**, because a forward-dated accrual is legitimate
  and **there is no period locking**."* ⚠️ Both are correct today and **both change meaning the day a
  lock date exists** — the second one especially, since a lock date is a *lower* bound on new
  postings and the absent upper bound is a separate question it does not answer.

### 1️⃣ D5 — a movable lock date, not a fiscal-year flag

**A single movable lock date. Everything dated on or before it is closed; everything after is open.**
The owner moves it forward as periods are filed.

**Why not a fiscal-year flag, and both halves of the reasoning are load-bearing:**

- **The owner will not accept blanket locking** — past records sometimes genuinely need altering. A
  lock date gives exactly that: whatever must stay open is simply left after the line.
- **It is finer grained than a fiscal year, and Greek VAT is why that matters.** VAT is filed monthly
  or quarterly here, so a year-granularity toggle leaves a filed February editable for eleven more
  months — which is precisely the drift the lock exists to prevent.

**Two properties without which it is decoration**, and they are requirements rather than nice-to-haves:

- ⚠️ **Only the owner may move it.** A lock anyone can slide backwards is a suggestion.
- ⚠️ **Every change is audited** — who, from what date, to what date, when.

**It blocks two different operations and both are in scope:** editing an existing entry in a closed
period, **and** posting a new entry dated into one.

⚠️ **Consequence, and it is a requirement for whoever builds D5 rather than a finding of this
session: reversal dating stops being optional.** If a closed period cannot receive entries, a
correction to a document in one must be dated to **the correction date**, not to the original
document's date. **This was deliberately NOT confirmed against the code here** — this session ran no
code and read no service to check what reversals do today. **Confirming it is D5's first task**, and
the roadmap footnote says so. (The existing footnote already carried a weaker version of this —
*"confirm that a reversing entry carries the correction date"* — which is the same item arriving
before the lock date made it unavoidable.)

### 2️⃣ D4 — it splits in two, and one half needs nothing built

**Half one, already answered, nothing to build.** Sales document numbers are **captured** from Go —
or from a certified Πάροχος in future — after the document has been issued and transmitted, exactly
like the ΜΑΡΚ, UID and QR code. Purchase document numbers are **whatever the supplier issued**:
captured through myDATA for domestic suppliers once that adapter exists (step 29), and taken as the
supplier's own reference number for foreign suppliers. **This half is `CLAUDE.md` §*The document
model* item 2 already in force; D4 never needed to decide it.**

**Half two, which is what D4 actually named.** Documents **Novocore itself creates and no external
party issues** — manual journal entries, goods receipts, freight allocations, write-offs. These have
no supplier and no Go, so **if Novocore does not number them they have no human-facing identifier at
all.** *"What is entry 412"* is a question about a manual journal entry, and today the answer is a
database id.

⚠️ **The distinction that makes this cheap, and it is what stops a reader refusing to build it:**
these are **internal reference numbers, not statutory document numbers.** No legal sequence, no
unbroken requirement, **gaps do not matter.** Simple per-type counters. **None of step 40's
machinery**, and **no conflict with "Novocore records numbers, never generates them"** — that rule is
about documents an external party issues. Written into `CLAUDE.md` §*The document model* as an
explicit carve-out, because a future session reading rule 2 alone would correctly refuse to build
this.

**D4's own two open questions, inherited from step 7:** does the counter reset per year, and is the
prefix per document type or per source? Nobody has been asked.

### 3️⃣ D1 — codes and alias

- **Codes are for the business's own reference and are NULLABLE.** They are not identifiers the
  system depends on; the id remains the handle.
- **Supplier has an alias. Customer never does.** ⚠️ **Recorded as a decision, not an oversight** —
  which is the entire reason it is written here, because an asymmetry with no argument behind it is
  the shape S1's reconciliation caught with `supplier.vat_number`.

⚠️ **The word "alias" is already in use in this repository for something else, and D1 does not
resolve that.** Brief §5's *"alias forward, never rewrite history"* is the **customer merge**
mechanism; a supplier alias here is a **short trading name**. The customer-never-has-one decision
*narrows* the collision — the two senses no longer land on the same entity — but the word still means
two things, and the customer-merge obligation is still open in the roadmap's cross-cutting table.
**Do not conflate them.**

### 4️⃣ D3 — addresses: structured, conditional, and smaller than the row suggests

**Structured, not free text** — street, number, postcode, city, country as separate fields. Three
reasons, all concrete: myDATA requires the counterparty address elements **separately** on
transmitted documents; ACS needs the same for shipping labels; and **the data already exists
structured in both Woo and Go.** A free-text field means parsing it back apart later, by hand, on
every record.

**Who needs one:** suppliers **always**; customers **who purchase with VAT**. Retail customer
addresses may be **NULL** — Skroutz frequently sends orders with no phone, address or email at all.

⚠️ **Enforced at the DOCUMENT, not at the CUSTOMER**, and the reasoning is recorded because the
opposite looks more natural:

- When a customer has a VAT number the address is **sourced from AADE or VIES rather than typed** —
  and that lookup is **step 28**, far after this work.
- So a customer-level constraint would **block record creation for a long stretch** with no way to
  satisfy it except manual entry, and would **fail any adapter processing a B2B order that arrives
  without one.**
- Document-level enforcement works whether the address was typed now or fetched later, and **the
  legal requirement is on the transmitted document anyway.**
- It is also the shape **`@ConditionallyMandatory` already exists for** (8a).

⚠️ **D3 shrinks.** Billing and shipping are separate, shipping defaulting to billing — but the
**shipping address is registered at the ORDER, not on the customer**, and affects only the courier
voucher. So **the customer entity holds ONE (billing) address**, and per-order shipping **moves to
step 22, Sales Order Fulfilment.** ⚠️ **There is no order entity anywhere in this system today** —
step 22 is where one would be created, which is exactly why the requirement is recorded there rather
than left in D3 waiting for a table that does not exist.

### 5️⃣ Vouchers — the courier adapter has two modes (step 21)

**Skroutz vouchers are already created by Skroutz and arrive at Novocore ready.** Novocore only needs
to **create** a voucher for **WooCommerce and phone (manual) orders**.

So the ACS adapter has **two modes — receive an existing voucher, or create one.** ⚠️ Recorded
against step 21 **before that step is scoped**, because *"ACS adapter"* naturally reads as one thing
and a step scoped from the name alone would build half of it.

### 6️⃣ M0 splits, and the first half is not an import

- **M0a — a mapping exercise.** ⭐ **Novocore's chart of accounts was built from scratch, not copied
  from Manager** — confirmed against this file's own step-3 record: 65 accounts across 13 groups,
  designed from the brief, with `AccountSystemKey` on the eleven the posting rules must locate. So
  **the real test is not an import**: does every account in Manager map to a Novocore account, and
  **which do not?** That is a spreadsheet and a session, **it needs no code**, and it tests the most
  load-bearing part of the model. **It can run almost immediately.**
- **M0b — a real year of transactions.** Waits until **D1, D3 and D4 exist**, or it imports into a
  model already known to be incomplete.

⚠️ **Why NOT after F11, recorded because it was the owner's initial instinct and the reasoning should
outlive the conversation:** M0's purpose is to **find gaps while fixing them is still free.** Run it
after eleven screens exist and every finding costs screens too. **It also does not need F11** — it is
an import, not data entry.

### 7️⃣ D2 — categories, and the one-time load that is not the adapter

**Direction: Novocore is the centre of the ecosystem.** Categories, brands, products and everything
product-related are **created in Novocore**; WooCommerce **receives** from Novocore, never the
reverse.

⚠️ **The initial load is NOT the adapter, and conflating them means building bidirectional sync that
is never needed again.**

- **The Woo adapter syncs Novocore → Woo, forever.**
- **The initial load runs Woo → Novocore ONCE and is then deleted.** It is a migration with a
  migration's property: **one clean shot.** Treat it as throwaway code with careful verification.

**Three decisions recorded with it:**

- **Categories import as-is.** The owner confirms Woo's categories are exactly the ones wanted; **no
  curation during the load.** Woo's structure is hierarchical and multi-membership, which **matches
  D2's three-level many-to-many** — the shape is already right.
- ⚠️ **Stock must NOT come from Woo.** Woo's stock numbers are **a projection with no cost attached**,
  and Novocore needs opening **lots** — quantity *and* cost. Those come from Go, or from a physical
  count valued against purchase invoices. **Product data from Woo, stock from elsewhere.** Recorded
  as a **separate and probably harder migration question**, not as part of the product load.
- **After cutover Woo is READ-ONLY for product data**, and any change made there is overridden by
  Novocore. ⚠️ **Scoped:** Novocore owns the fields it manages and overwrites them without asking;
  fields it does **not** manage — SEO text, image galleries, plugin data — are left untouched.
  ⚠️ **That list must be explicit and written down at step 19** — not discovered when a product's
  images vanish. **It does not exist yet, and the roadmap now says so rather than implying it does.**

### 8️⃣ Re-sequencing — the shared gate is the decision

⚠️ **Six of the seven ⚪ TBD items have the SAME deadline: before real data lands at step 24.**

| Item | Why it is inside the gate |
|---|---|
| **D5** | Before anything is filed from Novocore |
| **D4** | Before the accountant works in it |
| **D1** | Manager and Go data carry codes and aliases — migrating without them means importing into columns that do not exist |
| **D3** | Same, for addresses |
| **M0** | Its whole purpose is to precede the real migration |
| **R3** | ΣΑΥΤ and ΠΣΑΥΤ are issued routinely, so it must work before go-live |

**D2 is the exception: its gate is step 19**, because the Woo adapter syncs categories.

⚠️ **Recording the gate is the point of this section.** Treating these as seven independently
schedulable rows is exactly what lets a **cluster** slip past a **shared** deadline — each row moves a
little, no row looks late, and the deadline they all share is nowhere in the file.

**Decided placements, applied:**

| Placement | Applied where |
|---|---|
| **M0a — unblocked, can run at any time** | Roadmap row split; M0a is no longer ⚪ Placement TBD |
| **M0b — before step 24, after D1/D3/D4** | Roadmap row, gated |
| **D2 — before the one-time load, which is before step 19** | Roadmap row + footnote ʷᵒᵒ at step 19 |
| **Per-order shipping address → step 22** | Footnote ˢᵒᶠ |
| **Voucher creation modes → step 21** | Footnote ᵃᶜˢ |

⚠️ **STILL ⚪, and deliberately NOT promoted or ordered:**

- **Whether D1 and D3 land before or after F5** is the owner's open call, and it is a **real trade**,
  recorded in *Open decisions* in those terms: doing them first means **F5–F9 are built once**, but
  **Q1, 8a, R1a, R1b and R2 have all been foundation with nothing visible to show**, and **F5 is the
  first step in a long while that produces something to look at.**
- **D4 and D5's exact slots.** The gate is decided; the position is not.
- **R3 is not schedulable at all** — blocked on the accountant, and it carries **the hardest
  structural item in the project** (pricing from FIFO lot cost, which fights the price → post →
  consume ordering). ⚠️ **When the answer arrives it should be sized as a step, not slotted in as a
  sub-part.**

### 9️⃣ One build script — recorded as a recommendation, not built

**The stale-artefact family now has four members**, all four already in `CLAUDE.md`:

1. a container serving an old jar (Q1's browser leg);
2. annotations reverted with the build error **piped away** (8a's third probe);
3. `mvn -pl app` without `-am` (R1a);
4. an aborted `install` answering from stale jars (R1a).

All four reduce to **the thing that answered was not the thing under test**, which `CLAUDE.md`
already names — **and it keeps happening because the rule is a convention.**

**Recommendation, recorded as unscheduled and NOT built here: make the safe path the easy path.** One
build script that **sets `pipefail`, always builds with `-am`, never truncates output**, and is what
`CLAUDE.md` tells sessions to invoke. Then the mistake requires **deliberately not using the provided
tool.** ⚠️ **The reasoning, which is the part worth keeping: nothing in this repository can guard a
session's shell habits.** No ArchUnit rule, no test, no CI job sees how a command was typed. **The
only lever is making the correct invocation the default one.**

### 📌 What did not match the repository, reported rather than manufactured

**Per the session's own instruction: where the prompt referred to repository text, it was treated as
conditional.** Four items.

1. ⚠️ **"The pipefail rule … written in one session, broken in the next" is right in substance and
   imprecise in mechanism.** The rule was written in **8a** and the two recurrences were in **R1a** the
   same day — but **neither R1a recurrence involved a pipe.** They were an **unread build exit
   status**: `INSTALL=1` was on screen both times. `CLAUDE.md` already states these are *"the same
   rule"*, so the four-member family stands; the sentence is recorded here with the mechanism
   corrected rather than repeated.
2. ⚠️ **Step 22 has no order entity to attach a shipping address to.** The placement is still correct
   — step 22 is where an order would be created — but the roadmap footnote says *"where the order
   entity will exist"*, not *"does"*.
3. ✅ **The M0a premise checked out.** *"Novocore's chart of accounts was built from scratch, not
   copied from Manager"* is confirmed by this file's step-3 record, so the mapping-exercise argument
   rests on something verified rather than assumed.
4. ✅ **"Seven ⚪ TBD items" is exactly right** — R3, D1, D2, D3, D4, D5, M0 carried ⚪ Placement TBD in
   the Phase 2 table before this session. **U2 and 8b are also ⚪ but are *Unscheduled* and *Optional*
   respectively**, not *Placement TBD*, and are correctly outside the gate.

**Nothing in this session was verified against running code**, and nothing in it needed to be: it
records decisions. ⚠️ **The one claim that would need a live check before being built on** is D5's
reversal-dating consequence, and it is written down as **D5's first task** rather than as a fact.

---

## ▶ Step 8a — `@Mandatory`. **Phase 0 reported and approved 2026-08-03; built the same day**

⚠️ **Q1 finished on 2026-08-03 as FOUR items, not five.** Item 8 was lifted out of the queue and made
its own numbered step — **8a** and **8b** — **placed after Q1 and before R1**. That decision
closed the standing open question *"should item 8 be promoted within Q1?"*, and the answer was neither
promote nor leave last. Reasons in the roadmap under ᵈᵉᶜ.

⚠️ **The 8a/8b boundary moved on 2026-08-03, at the end of Phase 0, and the reason is CI.** The
approved split was *8a = annotation + generator + rule + spec + schema names; 8b = client
regeneration + fixture reconciliation*. **That boundary cannot exist without a red `main`:**
`.github/workflows/frontend.yml` triggers on `docs/api/openapi.json`, which 8a exists to change, and
that workflow both runs `spec-hygiene.test.ts` (three assertions pinned to the pre-8a state) and
regenerates the client and diffs it against the committed one. Deferring the spec instead is not an
escape — `OpenApiSpecIT` fails the build on spec drift. **See the checklist below for the revised
split, and *What Phase 0 measured* for how it was established.**

✅ **Q1's live browser leg passed on 2026-08-03**, run by the owner **after the app image was
rebuilt** — the first attempt hit a stale container, which is recorded below as a process finding and
is explicitly not a defect.

✅ **8a was not optional housekeeping, and it closed the regression Q1 shipped deliberately.**
Item 7 boxed the boolean primitives, which improved the refusal message and removed the `required`
declaration from the spec (78 → 75 schemas declaring `required`, measured 2026-08-03). 8a put it back
and went much further: **75 → 143, measured 2026-08-03 after 8a.** Full detail in the 8a checklist
above, the Q1 section below, and `spec-hygiene.test.ts`.

**Read, in this order:** `CLAUDE.md` — including its new **document model** section, which governs
everything from R1 onward → **`frontend/README.md`** (every frontend convention lives there, and
several of them were earned expensively) → `docs/novocore-roadmap.md`, **now the single unified
roadmap**, for the step order → the *Step 16, the frontend* section below for F1–F4's decisions and
what they left behind.

*Last updated: 2026-08-03, step 8a.*

### 📋 8a — Phase 0's seven questions, each with its verdict (2026-08-03)

**Phase 0 was report-and-stop. Every question below was answered by measurement, and four of them
corrected a premise this step was written on.** The approval of 2026-08-03 accepted all seven answers
and added decisions A–H, which are reconciled in their own table further down.

| # | Question | Verdict |
|---|---|---|
| 0.1 | Settle the count, with an explicit basis | ✅ **Answered, and it supersedes both recorded figures.** See *The three counts* below |
| 0.2 | Is a `requireNonNull` on a response record the same claim as `required`, and what does the client do with it? | ✅ **Answered by running the real generator, not by reading its output.** Yes, and orval renders a required field **non-optional**: `name?: string` → `name: string`. `tsc` then refuses an incomplete fixture (`TS2741`) — which closes the fixture-drift class. **The 46 response-side records earn MORE than the 48 request-side ones** (204 of the 339 components), so the split did not need revisiting on this account |
| 0.3 | Prove the bidirectional cross-check is buildable | ✅ **Proven in both directions**, and it corrected the mechanism — see decision B. Zero unattributable guard calls across all 186 records |
| 0.4 | A review strategy by category | ✅ **Six categories, each closed by a mechanical signal rather than by sampling.** See the build checklist below, where each is its own line |
| 0.5 | Item 7's regression as an acceptance criterion | ✅ **Answered, and it corrected the gate itself** — only **seven** of the eight can be confirmed in the spec. See decision G |
| 0.6 | Q1-a's full collision list, and whether any collision is between non-identical records | ✅ **Answered, and it is larger and sharper than recorded** — four collisions, not one; and `NameRequest`'s seven records differ in exactly the property 8a publishes. See decision F |
| 0.7 | Confirm or revise the split | ✅ **Revised, on a measurement.** See decision A |

### 📊 What Phase 0 measured, and how

**Nothing here was reasoned from source.** Two throwaway probes in `architecture-tests` (both deleted,
per `CLAUDE.md`'s *named practice: the throwaway probe*), one orval run against a modified spec copy,
and **one full simulation of 8b in an isolated `git worktree`** — the working tree was never touched
and `git status` was clean afterwards.

| What | Measured 2026-08-03 |
|---|---:|
| Records on the `architecture-tests` class graph | **186** |
| Records with ≥1 guarded component | **114** |
| **Guarded components** | **339** |
| Guard calls in the canonical constructors' bytecode | 340 |
| Guard calls ArchUnit attributes to those constructors | 342 |
| Distinct source files an annotation touches | **105** |
| Conditional guards (reached after a branch) | **6**, across 2 records |
| Unattributable guard calls | **0** |

**The 8b simulation, which is what moved the boundary:** the guard-derived `required` lists were
applied to the spec in the worktree, `npm run api:generate` was run, then `tsc -b --force` and the
full vitest suite. **420 generated files changed; 1 TypeScript error; 1 failing test; 307 of 308
tests still passing.** Item 9's claim that the fixture backlog is *measured at zero* holds — and is
now measured rather than read. **8b as scoped was not a session.**

### 📊 The three counts, all kept — decision D of Q1, honoured

⚠️ **Nothing is overwritten. The 2026-08-03 exact count supersedes the other two as the figure to
use; both prior figures stay visible with their bases, because the differences are counting bases
rather than disagreements.**

| | Recorded before Q1 | Heuristic scan, 2026-08-03 | **Exact, 2026-08-03 (8a Phase 0)** |
|---|---:|---:|---:|
| Records guarding a reference-typed field in a compact constructor | 90 | 94 | **114** |
| …of which request-reachable | 28 | 48 | **48** |
| Guarded components in total | — | 289 | **339** |
| …`Objects.requireNonNull` | — | 269 | **305** |
| …`Required.field` / `Required.text` | — | 20 | **27 / 8** |

**Basis of the exact count, stated because that is the whole point of keeping three:**

- **Universe** — every `record` on the `architecture-tests` class graph, i.e. the *main* artifacts of
  `core-api`, `core` and `app`. `app`'s test sources (where `OpenApiSchema` lives) are not on that
  graph, and no request record can live there.
- **"Guarded"** — the record's **canonical constructor's own bytecode** contains an `INVOKESTATIC` to
  `Objects.requireNonNull`, `Required.field` or `Required.text` whose first argument is a **direct
  load of a canonical-constructor parameter slot**.
- **The four value types are IN** (6 components) because they are records with guarded components the
  rule cannot exempt without an argument — but they are **spec-neutral**: `Money`/`UnitCost` schemas
  are hand-written in `OpenApiSchema` and already declare `required`, and `Quantity`/`Rate` are bare
  strings with no object schema.
- **Nested line records are IN**, and counted as request-reachable, because reachability was computed
  by `$ref` closure from `requestBody` — which is exactly the path a client meets them by.
- **Deliberately NOT counted:** 2 calls inside a lambda (`StockLevels`) and 1 on a loop variable
  (`NewFreightAllocation.lotId`). Neither constrains a component.

**Split by what the spec does with the record:** request-reachable **48 records / 89 components**
(47 plus `OpenItemRef`, which is both); response-only **46 / 204**; value types **4 / 6**; not on the
HTTP surface at all **16 / 40**.

### 📋 8a — the approved build scope, one line per sub-part (approved 2026-08-03)

⚠️ **The 333 annotations are deliberately NOT one line.** They are decomposed by the six review
categories, so a category that gets skipped is visible rather than absorbed into a total.

| # | Sub-part | Verdict |
|---|---|---|
| 1 | `@Mandatory` in `gr.novotrade.novocore.core.api.shared` | ✅ **Done.** `@Target(RECORD_COMPONENT)`, `RUNTIME` retention — narrow on purpose, so it cannot be applied anywhere it would do nothing |
| 2 | `@ConditionallyMandatory(reason)` in the same package — **reason string required**, per decision C | ✅ **Done.** `value()` has no default, so the reason cannot be omitted, and a blank one fails the build |
| 3 | The bidirectional rule in `architecture-tests`: **ASM + reflection**, not ArchUnit attribution (decision B) | ✅ **Done.** `MandatoryDeclarationRulesTest`, 4 tests. **All three rules proven against probes**, not assumed — see *What was proven by failing first* |
| 4 | **Category A** — the 6 conditional guards, read individually, exempted not annotated | ✅ **Done.** All 6 read: `NewPurchaseInvoiceLine` ×5, `EmailAttachment.content` ×1. Each carries its reason at the field |
| 5 | **Category B** — 46 response views, 204 components | ✅ **Done.** Every guard read. The withholding-mechanism sweep was the part that mattered and it **passed**: `ProductView.redactedFor` nulls only `supplierId`, `supplierSku`, `lastPurchasePrice` — none guarded — and `SettingView` substitutes a masked **non-null** string. Both go through the canonical constructor, so nulling a guarded component would throw rather than shorten a body |
| 6 | **Category C** — 48 request bodies, 89 components | ✅ **Done.** Every compact constructor read; all 89 guards unconditional |
| 7 | **Category D** — the 4 value types, 6 components (spec-neutral; verify the diff shows nothing) | ✅ **Done and confirmed spec-neutral.** `Money`/`UnitCost` schemas are hand-written in `OpenApiSchema` and already declared `required`; `Quantity`/`Rate` are bare strings with no object schema. The spec diff shows nothing for any of the four |
| 8 | **Category E** — 16 off-surface records, 40 components (one decision taken once, then mechanical) | ✅ **Done, in scope.** A record that later reaches the surface arrives already correct. ⚠️ Two of the 16 (`ListResponse`, `PageResponse`) turned out **not** to be off-surface: they are generic envelopes reaching the spec as `ListResponse_ProductView` and so on, so `items` is now required on **34** generated list schemas |
| 9 | **Category F** — the `NameRequest` family, reviewed *with* the rename rather than separately | ✅ **Done**, and it is where the finding came from — see sub-part 11 |
| 10 | Commit 1, verified green **standing alone** before splitting (decision E) | ✅ **Done.** `68946f6`. `mvn clean verify` exit 0, 1381 tests, spec byte-identical, so `frontend.yml` does not trigger on it |
| 11 | Q1-a — split all four collisions, **including the three identical today** (decision F) | ✅ **Done.** 13 records renamed with an entity prefix. ⚠️ **`NameRequest` was never merely latent** — 2 of its 7 records guard `name` and 5 do not, so the merged schema would have been wrong for at least two of its **nine** operations the moment 8a ran |
| 12 | `OpenApiSpecIT` refuses a schema-name collision, **scoped to the spec, not to all records** (decision D) | ✅ **Done**, in `OpenApiSchema.claim`, which is where a name is claimed. **Proven by renaming `SupplierNameRequest` back onto `CustomerNameRequest` and watching the build fail.** `Computation` and `Rounding` are recorded as known and left alone |
| 13 | One line in `OpenApiSchema.recordSchema` reading `@Mandatory` | ✅ **Done** — `component.getType().isPrimitive() \|\| component.isAnnotationPresent(Mandatory.class)` |
| 14 | Spec regenerated | ✅ **Done.** 262 insertions, 115 deletions; **196 schemas, 176 operations** (2026-08-03) |
| 15 | `npm run api:generate` — the client committed in step with the spec | ✅ **Done**, and **proven idempotent** — regenerating twice produces identical bytes, so the CI drift check passes |
| 16 | `spec-hygiene.test.ts` — the three pinned assertions rewritten from pinning the defect to pinning the guarantee | ✅ **Done**, and it is now v3 of that test. The `NewProduct` assertion became a **by-name table of all seven** boxed flags; `NewRole.required` flipped from `toBeUndefined()` to `toContain('name')`; the count went 75 → **143**; and four new assertions pin the collision split |
| 17 | The one `RoleView` fixture in `users.test.tsx` | ✅ **Done.** `sectionGrants: {}` and `restrictedFields: []`, with a note saying they are empty because a full-access role holds FULL **by the flag rather than by grant rows** |
| 18 | **Gate 1** — the rule passes both directions, 0 unattributable | ✅ **MET.** 186 records scanned, 339 components attributed, **0 unattributable** |
| 19 | **Gate 2** — schemas declaring `required` ≥ 78 | ✅ **MET, 143** (was 75; 78 before Q1) |
| 20 | **Gate 3** — the **seven** spec-visible booleans by name, plus the eighth confirmed annotated and guarded in the backend (decision G) | ✅ **MET.** All seven asserted **by name** in `spec-hygiene.test.ts`. The eighth, `NewVatExemptionReason.inputVatDeductible`, is `@Mandatory` + `Required.field` and enforced by the bytecode rule |
| 21 | Record H.1 — `EmailMessage.subject`/`body`, and the general lower-bound limit | ✅ **Done.** Neither is spec-visible (`EmailMessage` is a service-interface type, not on the HTTP surface), so per decision H they were **left alone** and the gap recorded. The lower-bound limit is written into `Mandatory`, `MandatoryDeclarationRulesTest`, `OpenApiSchema` and `spec-hygiene.test.ts` |
| 22 | Record H.2 — `NewPurchaseInvoiceLine` is a discriminated union modelled flat; a design item, **not 8a's to fix** | ✅ **Recorded**, in `ConditionallyMandatory`'s javadoc and as a design item below |
| 23 | 8b becomes a ⚪ optional roadmap row with its trigger stated, and the test-account decision attached | ✅ **Done** in the roadmap |

### 🔬 What 8a proved by failing first, rather than by passing

**Every guard added this step was run against the defect it exists for and watched to fail**, per the
practice Q1 established. Four probes, all reverted:

| Probe | What fired |
|---|---|
| Removed `@Mandatory` from `CustomerView.name` | `everyGuardedComponentIsDeclared` — *"refused when absent … but the contract describes it as optional"* |
| Added `@Mandatory` to `CustomerView.email`, which nothing guards | `everyDeclaredComponentIsGuarded` — *"the contract promises what nothing enforces"* |
| Marked the unguarded `CustomerView.phone` `@ConditionallyMandatory("")` | `everyExemptionIsRealAndExplained` — **both** messages: nothing to be exempt from, and no reason |
| Renamed `SupplierNameRequest` back to `CustomerNameRequest` | `OpenApiSchema.claim` — the build refused to write the spec |

⚠️ **The third probe was run twice, and the first run was worthless.** It reported PASS. The
`git checkout` that reverted the previous probe had also reverted the *uncommitted* annotations, the
resulting file did not compile, the `-q` build's error was swallowed by a `| tail -3`, and
`architecture-tests` answered from the **previously installed jar**. Every observation was true and
none of it was evidence. This is `CLAUDE.md`'s *the thing that answered was not the thing under
test*, at the scale of one command — caught only because that case was designed to fail, and a case
designed to fail reporting success is loud.

➡️ **This is the occurrence; the rules it produced are in `CLAUDE.md` under *the throwaway probe*,
and they are stated there only** — **every probe carries a negative control**, and **a piped build
hides its own failure**. Recorded here as the evidence rather than restated as a rule, because two
records of one rule is the drift that let backend item 3 contradict itself for a week.

### 📌 Two design items 8a recorded and deliberately did not fix (decision H, 2026-08-03)

**Both are real, both were found by measurement, and neither is 8a's to close.**

**H.1 — the declared set is a LOWER BOUND, and one place already proves it.** The bytecode
cross-check can see three guard forms: `Required.field`, `Required.text` and
`Objects.requireNonNull`. A component made mandatory by an inline `if (x == null) throw` is invisible
to it, and **`EmailMessage.subject` and `EmailMessage.body` are exactly that** — refused when absent,
not annotated, and correctly so, because the rule's second direction would otherwise have to accept
a declaration it cannot verify. Checked per decision H: **neither is spec-visible.**
`EmailMessage` is a service-interface type and does not appear in the document, so normalising them
to `Required.text` would change no contract and was not done. ⚠️ **The general rule matters more than
the instance: "every `@Mandatory` component is guarded" must never be read as "every mandatory
component is annotated."** An incomplete `required` list is still true; a wrong one is worse than
none. Written into `Mandatory`, `MandatoryDeclarationRulesTest`, `OpenApiSchema` and
`spec-hygiene.test.ts` so it cannot be inferred away.

**H.2 — `NewPurchaseInvoiceLine` is a discriminated union modelled as a flat record.** Five
components of which **at most three can ever be present**, selected by `type`: an INVENTORY line
requires `productId`, `quantity` and `unitPrice` and *forbids* `expenseAccountId` and `amount`; an
EXPENSE line is the mirror. **No `required` list can express that** — OpenAPI needs `oneOf` with a
discriminator, and the generated TypeScript would then be two types rather than one with five
optional fields. `@ConditionallyMandatory` is the correct treatment *for now*: it keeps the contract
incomplete rather than self-contradictory, and it puts the reason at the field. **Recorded as a
design item, unscheduled.** It would be worth doing before a screen binds this record — which is
F6, purchasing.

### ▶▶ What is next, in one place

⚠️ **THE SEQUENCE IS THE ROADMAP'S ROW ORDER, and this table follows it rather than restating it.**
Decided by the owner on 2026-08-04, amended twice on 2026-08-06:

    W1  →  F5  →  R4  →  D1 + D3 + D4 + D5  →  F6 onward
    D2   before step 19 (the Woo one-time load)
    R3   when the accountant answers — not schedulable
    U2   whenever a session has slack
    R2c  DEFERRED out of the sequence and SPLIT — 2a → F10, 2b → R4
    N1   direction settled, unbuilt, no slot
    C1   the chart of accounts decision — recorded 2026-08-06, not scoped

⚠️ **Two glyphs moved on 2026-08-06, in opposite directions, and BOTH are the decision being applied
rather than a side effect of a row moving.** **R2c 🟡 → ⚪** because the owner deferred it — it is not
core work and must not interrupt the core. **R4 ⚪ → 🟡** because the owner commissioned its Phase 0 in
the same instruction. R4's gate did not change and was always the binding one: **before F6**.

**Per `CLAUDE.md` §*A sequencing decision changes the roadmap's ORDER*, `docs/novocore-roadmap.md` was
re-ordered to match, with each row's reasoning written at the row.** ⚠️ **Three status glyphs were
deliberately NOT promoted** — W1 is first and still ⚪, F5 is second and still 🟡, and the four D-rows
keep ⚪ while the `Placement TBD` text they carried (false the moment the sequence was decided) was
corrected to *After F5*. The proposals are in the roadmap under ˢᵉᑫ; **position and status are
different claims and a glyph nobody decided to move must not move.**

| | State |
|---|---|
| **Substring search (S1)** | ✅ **Complete and live-verified.** Nothing outstanding |
| **Sorting (S2)** | ✅ **Complete and live-verified.** Client-side, on all five list screens; the browser leg was run by the owner on 2026-08-01. Nothing outstanding |
| **F4 — Settings** | ✅ **COMPLETE.** All 22 sub-parts have verdicts, none is "still open". Contract verified by the real backend; **browser leg run personally by the owner on 2026-08-01**. Nothing outstanding |
| **Q1 — the backend follow-up queue** | ✅ **FULLY CLOSED, 2026-08-03.** Four items, all with verdicts, the owner's browser leg passed on all four checks — and **item 7's regression is closed by 8a**, so the conditional marker this row carried is gone. Q1-a landed in 8a; **Q1-b is the only thing left open**, to decide with R1 |
| **8a — declare every compact-constructor requirement** | ✅ **DONE, 2026-08-03**, in two commits. 339 components across 114 records declared, cross-checked against the canonical constructors' bytecode in both directions; four schema-name collisions split; spec 75 → **143** schemas declaring `required`. All three gates met. Backend **1,381** tests green, frontend **308** green |
| **8b — consumer cleanup** | ⚪ **OPTIONAL, and not a correctness step.** 8a already regenerated the client and made the suite green; what remains is *taking advantage* of the new contract — removing `?.`/`??` guards on fields that can no longer be undefined. ⚠️ **The test-account decision attaches here** and should be settled *before* it starts |
| **R1a — document reference data, additive** | ✅ **DONE, 2026-08-03**, commits `aa1eda4` + `c5f9a97`. Six new tables, 54 new routes, a new architecture rule, one deletion. **All 48 sub-parts have a verdict**; 46 done, S.4 deferred to R2, E.3 a finding. **Four premise corrections and one defect only the real server could find** — see R1a's findings. No live leg: R1a ships no screens |
| **U3 — eleven design decisions recorded** | ✅ **DONE, 2026-08-03. Documentation only** — no code, no schema, no migration, no test. It does **not** change what is next. Four placements applied (M0a unblocked, M0b gated, D2 before step 19, and two requirements moved into steps 21 and 22); **the shared before-24 gate recorded as a decision**; nothing else promoted or reordered. **D1/D3-versus-F5 is stated as a trade in the roadmap, not resolved** |
| **R1b — document reference data, behavioural** | ✅ **DONE, 2026-08-04.** All 22 sub-parts have verdicts, none is "still open". ⚠️ **`seriesId`, not `documentTypeId`** — the document type is mandatory *through* the series, because `sales_invoice` has `series_id` and no `document_type_id`. Channel derived from the series; a channel-less series **refused**, not accommodated. **Nothing outstanding** |
| **W1 — a serialised record's wire shape must equal its documented shape** | ✅ **DONE, 2026-08-04. All 16 sub-parts have verdicts, none is "still open".** Nothing outstanding. *(Originally:)* ⚠️ **32 committed schemas ship 66 undocumented properties** — measured, not estimated. None is a live defect; the contract lies about all 32. ⭐ **Evaluate the generator route first** — one change to `OpenApiSchema` documents all 66. Detail in the roadmap under ʷ¹ |
| **R2 — document reference data, screens** | 🔴 After R1b. ⚠️ **Full CRUD**, not the read-plus-activate shape F4 built — the owner authors these rows. He creates his 19 document types and their series here, **choosing each AADE type himself**. ⚠️ Needs a **live browser leg**, and therefore an app-image rebuild |
| **R4 — payment methods become a business list** | 🟡 **CURRENT from 2026-08-06.** A **requirement correction**, not a defect: the list starts **empty**, the user creates rows, each names an **AADE payment-method article** *and* **the ledger account it settles to**, and all fields stay editable until the method is used. ⚠️ **It changes the sales invoice request contract** — `SettlementMethod` is a Java enum on `NewSalesInvoice` and must become an FK — which is why the gate is **before F6**. ➕ **R2c's 2b is attached** (the sort code on the series edit form) with the series-ordering check. ➖ **The chart-of-accounts decision (C1) removed a prerequisite it might have had** |
| **R2c — sort code** | ⚪ **DEFERRED AND SPLIT, 2026-08-06.** Not core work; the owner does not want it interrupting the core. **2a** (invisible column, cosmetic) → **F10**'s display-defects list. **2b** (absent from the series edit form) → **R4**, with the unverified series-ordering check. ⚠️ **Neither half is scheduled as R2c**, and the row is not a schedulable item any more |
| **C1 — the chart of accounts** | ⚪ **DECIDED 2026-08-06, recorded, NOT scoped.** The **official Greek chart is used directly**, with an **alias per account for display**, and **no separate business chart mapped onto it**. Reasoning: many-to-one granularity is better served by the product model, and one layer is the reversible choice (adding a layer is additive; collapsing two is a merge that loses history). ⚠️ **No alias field exists on an account today** — measured, not built |
| **F5 — Sales Invoice + Credit Note** | 🟢 **DONE 2026-08-06.** *(This row read "🟡 NEXT" until then; kept below as written.)* 🟡 **NEXT. W1 landed on 2026-08-04, so F5 is now current** — after it come D1 + D3 + D4 + D5 as one block, then F6 onward. ⚠️ It decides the create/preview/commit pattern F6–F8 all reuse, so it is worth disproportionate scrutiny — but ⚠️ **see the open decision in the roadmap**: since documents arrive already issued, F5 before step 18 is a data-entry screen for documents created elsewhere, and much of it disappears when the Go adapter lands |
| ~~⚠️ **Backend queue item 8 — promote to first?**~~ | ✅ **DECIDED 2026-08-03, and the answer was neither.** Not promoted within Q1 and not left last: **lifted out of the queue** into its own step, split 8a/8b, placed after Q1 and before R1. The open decision is removed from the roadmap and replaced by the step |
| **M0a — map Manager's chart onto Novocore's** | 🟢 **UNBLOCKED, can run at any time** (U3, 2026-08-03). It is a **mapping exercise, not an import**: no code, no schema, a spreadsheet and a session. Novocore's chart was built from scratch, so the question is *which Manager accounts have no Novocore home* |
| **M0b — a real year of transactions** | ⚪ **Gated: after D1/D3/D4, before step 24.** Importing before those exist means importing into columns that do not exist |
| **D1–D5, M0, R3 — the shared gate** | ⚠️ **Six of the seven share ONE deadline: before real data lands at step 24** (D2 is the exception — its gate is step 19). Recorded by U3 because seven independently schedulable rows is how a cluster slips past a shared deadline. ✅ **The slots are no longer open — corrected 2026-08-04**: **D1 + D3 + D4 + D5 are one block after F5**. ⚠️ **The gate still matters more than the slots**: a block that slips as a block still misses step 24 together, and **R3 and M0b remain slotless** |
| ⚠️ `Supplier.code` / `Supplier.alias` / `Customer.code` | 📌 **This is D1, and its content is now decided** (U3, 2026-08-03): codes are **nullable** and for the business's own reference; **supplier has an alias, customer never does** — an asymmetry recorded as a decision rather than an oversight. ✅ **The placement is DECIDED — 2026-08-04, AFTER F5, in one block with D3, D4 and D5.** ⚠️ **The argument for doing it BEFORE F5 was not refuted, it was outweighed, and it is kept as the accepted cost**: it blocks part of six rows of the search target list, rows 8–10 are exactly the document screens F5–F7 build, and **those screens will therefore be touched twice**. What won was that F5 is the first visible step after six foundation steps. **The glyph stays ⚪** — placed is not scheduled |
| ⚠️ **Database sort order ≠ browser sort order** | 📌 **OPEN, and F4 did not close it.** F4 established *that* `el-GR-x-icu` was never applied; the database still orders by bytes under locale `C` while the browser orders by `Intl.Collator('el')`. Invisible only because no list pages on the server. **Whoever adds paging to a list screen owns this.** See the standing item below |
| `Product.category` | 📌 Queued as **its own proposal**, requirement recorded, deliberately not started |
| **Test-environment parity — enforcement** | ⚖️ **STILL HELD, awaiting the owner's decision.** Untouched by F4. Do not act on it in either direction |

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

## ▶ R1 Phase 0 — what is TRUE of the system and the artefacts, 2026-08-03

**This section is EVIDENCE, not decisions.** Every statement below was established by querying the
running stack, parsing the committed spec, or reading the AADE artefacts — none of it by reading
source and concluding. The decisions R1 took *in response* are in the R1 scope section; keeping them
apart is deliberate, because a finding stays true when a decision is revisited.

**How it was established.** Live PostgreSQL **17.10** in `novocore-postgres-1` (up 30h, healthy at
the time of the queries) interrogated directly with `psql`; `docs/api/openapi.json` parsed; the
`v2.0.1` XSD enumerations read as text; the ERP PDF's §8 annex map read and cross-checked. Phase 0
was approved on 2026-08-03 with **all eleven premise corrections accepted**.

### 📋 The eleven corrected premises, and what proved each

| # | The premise as written | What is actually true | Proved by |
|---|---|---|---|
| 1 | Settlement methods are an entity with rows | **`SettlementMethod` is a Java `enum` in `core-api`.** No table, no row, no id, no `active` | `\d`-level sweep of all 47 live tables; `sales_invoice.settlement_method` is `varchar(30)` under a CHECK |
| 2 | One settlement method (ACS) lacks a myDATA code | **Three do** — `ACS_COD`, `PAYPAL`, `STRIPE`. PayPal and Stripe were not mentioned at all | The enum's eight constants read against annex 8.12's eight statutory values |
| 3 | `VatExemptionReasonService.create` is the only unreachable seed-only write path | **`ChargeTypeService` has six**, and `/api/charge-types` is `GET`-only | The committed spec (176 operations); `TaxLookupController` exposes GET only; no production caller |
| 4 | Annex 8.7 `Κατηγορία Τελών` is the Fees list | **It is 22 statutory levies** — mobile telephony, plastic bags, subscription TV, hotel stays, restaurant/casino gross receipts. No delivery, no COD | Annex 8.7 read directly |
| 5 | AADE codes 24 and 28 may be retired | **Both exist.** Annex 8.3 defines **31 codes with no gaps** | Annex 8.3 read directly — see below |
| 6 | Self-supply is one concept | **AADE defines two document types**: `6.1 Στοιχείο Αυτοπαράδοσης` and `6.2 Στοιχείο Ιδιοχρησιμοποίησης` | `SimpleTypes-v2.0.1.xsd` `InvoiceType`; annex 8.1 |
| 7 | Line prices are assumed to come from the product | **Nothing reads `product.selling_price` on the sales path.** `unitPrice` is caller-supplied and `@Mandatory` | Non-test reader sweep of `sellingPrice`; `SalesInvoiceServiceImpl.price(…)` reads the product only for `active` and `defaultVatClassId` |
| 8 | Excluding a self-customer from reporting would touch reports | **No revenue, sales-by-customer or margin report exists anywhere** | `SalesInvoiceService` has listings only — `ofCustomer`, `between`, two paged variants, `withAcceptedRoundingDifference`, `totalRoundingBetween` |
| 9 | The AADE artefact filenames were normalised | **They never were.** The README listed five short names, **none of which exist on disk** | `ls` of `docs/aade/v2.0.1/` |
| 10 | The XSD splits sales and purchase document types | **One enumeration of 55 values covering both.** The split is Novocore's, from annex 8.1's group headings | `SimpleTypes-v2.0.1.xsd` `InvoiceType` |
| 11 | `pdftotext` can read the annex tables | **It silently drifts code and description apart** in 8.2, 8.7, 8.8 and 8.13, producing plausible wrong pairs | Annex 8.2 extracted as `1 → 24%`, `2 → 24%/13%`, `3 → 13%/6%` — every pairing off by one. ⚠️ **Now a named anti-pattern in `CLAUDE.md`** |

### 📊 `SettlementMethod` — the eight values as they stand, 2026-08-03

**An enum, deliberately.** Its own javadoc: *"every value here has behaviour only NovoCore can
supply — the cash limit, whether an invoice is born settled — so a row an operator added at runtime
would be storable and unhandled."*

| Value | `AccountSystemKey` | `settlesImmediately` | `subjectToCashLimit` | myDATA code (annex 8.12) |
|---|---|:---:|:---:|---|
| `CASH` | `CASH` | ✅ | ✅ | **3** Μετρητά |
| `CARD_POS` | `PARTNER_CLEARING_POS` | ✅ | — | **7** POS / e-POS |
| `SKROUTZ` | `PARTNER_CLEARING_SKROUTZ` | ✅ | — | **5** Επί Πιστώσει |
| `ACS_COD` | `PARTNER_CLEARING_ACS` | ✅ | — | ⚠️ **none — open** |
| `PAYPAL` | `PAYPAL` | ✅ | — | ⚠️ **none — open** |
| `STRIPE` | `STRIPE` | ✅ | — | ⚠️ **none — open** |
| `BANK_DEPOSIT` | *(none)* | — | — | **1** Επαγ. Λογαριασμός Ημεδαπής |
| `ON_ACCOUNT` | *(none)* | — | — | **5** Επί Πιστώσει |

⚠️ **PayPal and Stripe map to nothing obvious in an eight-value statutory list** — they are neither a
domestic professional payment account, nor cash, cheque, credit, web banking, POS, nor IRIS. That is
a finding about the list, not a gap in our knowledge of it.

### 📊 `ChargeType` — shape, and why its income-only constraint is load-bearing

`charge_type`, structure `V5`, seeded `V7`. Live rows on 2026-08-03: **`COD fee`** and **`Delivery`**,
both VAT class 9 (24%), each pointing at its own income account.

| Column | |
|---|---|
| `id` | bigserial |
| `name` | varchar(120) NOT NULL **UNIQUE — this is the identity. There is no `abbreviation` and no `code`** |
| `default_vat_class_id` | bigint NOT NULL → `vat_class` |
| `income_account_id` | bigint NOT NULL → `account`, **constrained to `INCOME` in the service** |
| `active` | boolean |

⚠️ **`V5`'s argument for the constraint, quoted because it is the reason an expense side cannot
simply be bolted on:** *"The courier fee we ourselves incur is an unrelated expense posting against
Transportation costs, and the two being near-homonyms is exactly why the revenue side is modelled
explicitly: netting one against the other understates revenue and cost together and leaves a gross
margin that looks plausible while being wrong."* The service **refuses** a non-`INCOME` account, and
`CONTRA_INCOME` too.

⚠️ **`/api/charge-types` is `GET`-only.** `ChargeTypeService` declares `create`, `rename`,
`changeDefaultVatClass`, `changeIncomeAccount`, `deactivate`, `reactivate` — **none has an HTTP route
and none has a production caller.** `SalesInvoiceServiceImpl` and `TaxLookupController` inject the
service and use only `require` / `all` / `active`.

### 📊 `vat_exemption_reason` — 29 rows, and what the artefact says about them

Live: **29 rows, 26 with a `mydata_code`, 3 NULL** (OSS/IOSS codes 29–31), `input_vat_deductible`
**false on every row**.

- ✅ **Every seeded description matches AADE's ν.5144/2024 column exactly.** The recodified article
  numbering `V8` transcribed from Prosvasis Go's screen is now confirmed against the authority. **The
  rows were right; the provenance was not.**
- ⚠️ **Codes 24 and 28 exist and Novocore does not have them.** Annex 8.3 defines all 31 with no
  gaps: `24 = Χωρίς ΦΠΑ - άρθρο 8 του Κώδικα ΦΠΑ`, `28 = Χωρίς ΦΠΑ – άρθρο 29 περ. β' παρ.1 του
  Κώδικα ΦΠΑ, (Tax Free)`. **`V8`'s own header predicted exactly this**, asking for confirmation
  against AADE's published table before the myDATA adapter — the artefact answers it, and it is two
  `INSERT`s as `V8` said.
- **Provenance was Go's screen, not the artefact**, and no spec version was recorded anywhere.

### 📊 `sales_invoice` — 21 columns, and the whole-database sweep

Read from `information_schema` on the live database. The 21: `id`, `customer_id`, `channel`,
`settlement_method`, `document_number`, `invoice_date`, `description`, `stated_total`,
`stated_total_currency`, `rounding_amount`, `rounding_amount_currency`, `rounding_needed_review`,
`rounding_accepted_by`, `rounding_accepted_at`, `rounding_note`, `journal_entry_id`,
`reversal_of_id`, and the four audit columns.

| | |
|---|---|
| Human-facing document number | ✅ `document_number varchar(60) NOT NULL` — *"the number the issuing system printed"* |
| Series | ❌ **nothing** |
| ΜΑΡΚ / UID / QR URL / transmission status | ❌ **nothing** |

⚠️ **The sweep, because its result is stronger than any single column check.** A
`column_name ~* 'mark|uid|qr|series|branch|establishment|transmission|mydata|aade'` query across
**all 47 tables** in the live database returns **exactly two columns**:
`unit_of_measure.mydata_code` and `vat_exemption_reason.mydata_code`. That is the complete myDATA
footprint of the schema as of 2026-08-03.

Uniqueness on the number today is a **BEFORE INSERT OR UPDATE trigger** plus a partial unique index
on `upper(document_number)` where `reversal_of_id IS NULL` — not a plain constraint, because whether
a row is superseded depends on whether *another* row points at it.

### 🔬 Stock: the evidence in full, with the negative control

**Ten recorded sales invoices → ten `stock_consumption` rows → ten `stock_consumption_line` rows.**
Every consumption's lot lines sum **exactly** to `quantity_filled`, checked row by row.

| Consumption | Product | `quantity_filled` | Lot lines | Agrees |
|---:|---:|---:|---|:---:|
| 1 | 1 | 20.000000 | `1@9.000000` | ✅ |
| 2 | 2 | 2.000000 | `3@70.000000` | ✅ |
| 3 | 3 | 10.000000 | `4@1.000000` | ✅ |
| 4 | 3 | 2.000000 | `4@1.000000` | ✅ |
| 5 | 2 | 1.000000 | `3@70.000000` | ✅ |
| 6 | 4 | 1.000000 | `5@900.000000` | ✅ |
| 7 | 1 | 6.000000 | `1@10.149000` | ✅ |
| 8 | 1 | 10.000000 | `1@10.149000` | ✅ |
| 9 | 1 | 4.000000 | `1@9.000000` | ✅ |
| 10 | 3 | 88.000000 | `4@1.000000` | ✅ |

- **Consumption is unconditional for stocked goods.** `stock_consumption.source` is CHECK-constrained
  to **`'SALES_INVOICE'` and nothing else**, and **`NewSalesInvoice` has no field that could suppress
  consumption** — twelve components, none of them a stock flag.
- **The three invoice lines with no consumption are explained, not anomalous:** one `CHARGE` line,
  one `SERVICE` product (`TEST-PRODUCT-INSTALL-01`), and one `BUNDLE` (`TEST-PRODUCT-KIT-01`, whose
  components consume separately through `sales_invoice_line_component`).
- **Consumption 10 is Q17's negative stock, visible in real data:** 500 requested, **88 filled** —
  allowed, flagged, never silent, exactly as ADR 0008 says.
- ⭐ **The step-10 landed-cost re-costing is visible on lot 1.** Consumptions 1 and 9 took it out at
  `9.000000`; consumptions 7 and 8 took it out at `10.149000`. **A lot's carrying cost moved between
  them**, which is precisely why `stock_consumption_line` stores its own `unit_cost` rather than
  recomputing from the lot. The data demonstrates the reason for the design.
- The FIFO index is `inventory_lot (product_id, acquisition_date, id)`.

⚠️ **The negative control was run and it fired.** The invariant checker was fed the ten real rows
**plus one synthetic row** asserting `quantity_filled = 5.000000` against lot lines summing to
`3.000000`. It reported **exactly that row and nothing else** (`id = -1`, `FLAGGED`). Without this,
ten passes would be indistinguishable from a checker that was never evaluating anything —
`CLAUDE.md`'s *every probe carries a negative control*, honoured rather than cited.

### 📊 Settings — there is no company identity at all

**33 live keys**: 16 backup, 8 email, 5 SMTP, 2 ledger rounding, 1 cash limit, 1 attachment size.
**No ΑΦΜ, no company name, no address, no branch, no establishment.** The gap is wider than the
question asked — the myDATA issuer branch number is one field of a company-identity block that does
not exist.

✅ **`setting_key`'s CHECK is `^[a-z0-9]+(\.[a-z0-9-]+)+$`**, so `company.branch-number` is a valid
key shape **with no migration to the constraint**.

### 📊 Stock movement outside a sales invoice

**`stock_write_off` is the only non-sale stock-out.** Live: **3 rows, 3 journal lines** against
`INVENTORY_WRITE_OFF`. It names a *lot* directly rather than a product, has no customer, no VAT, no
document and no myDATA anything, and `journal_entry_id` is nullable because a zero-cost lot
derecognises nothing.

⚠️ **`WriteOffReason` has no internal-use value** — `SHRINKAGE | DAMAGE | EXPIRY | OTHER` — and
**`V4`'s comment on the `Internal consumption` account says so deliberately:** *"Stock consumed by
the business itself — staff coffee, demos, tastings. **Deliberately not a write-off: it is a real
cost of operating, not a loss.**"* Reusing the write-off path for internal consumption would
contaminate the shrinkage figure the single account exists to isolate.

There is **no dispatch document, no transfer and no internal-issue document.**

### 📊 Fixed assets — no cost path of any kind

`Asset` carries `code`, `name`, `acquisition_date`, `depreciation_rate_percent`,
`depreciation_start_date`, `status`, `disposal_date` — **no monetary field.** `AssetService` has
thirteen methods and **none takes an amount.**

⚠️ **Three account system keys are declared, seeded, and have never been posted to. Live journal-line
counts, 2026-08-03:**

| Account | System key | Journal lines |
|---|---|---:|
| Fixed assets at cost | `FIXED_ASSETS_AT_COST` | **0** |
| Fixed assets accumulated depreciation | `FIXED_ASSETS_ACCUMULATED_DEPRECIATION` | **0** |
| Depreciation | `DEPRECIATION_EXPENSE` | **0** |
| Internal consumption | *(none)* | **0** |
| Cost of service sold | *(none)* | **0** |
| Cost of goods sold | `COST_OF_GOODS_SOLD` | 10 |
| Inventory write-off / shrinkage | `INVENTORY_WRITE_OFF` | 3 |

`FIXED_ASSETS_AT_COST` has **no production reference anywhere in the codebase** — it is declared in
`AccountSystemKey` and nothing resolves it. **There is no inventory→asset path because there is no
asset-cost posting path at all.**

### 📊 Where a sales line's price comes from

⚠️ **Caller-supplied.** `NewSalesInvoiceLine.unitPrice` is `@Mandatory`; `SalesInvoiceServiceImpl`
reads the product only for `active` and `defaultVatClassId`. `product.selling_price` exists and its
only non-test readers are `Product`, `ProductServiceImpl`, `ProductController`, `NewProduct` and
`ProductView` — **the sales path is not among them.**

⚠️ **The real obstacle is ordering, not a missing lookup.** `SalesInvoiceServiceImpl` runs
**price → post → `consumeStock`**. FIFO lot selection happens *last*, so at pricing time the lot cost
is not yet known. Anything that must price a line *from* lot cost is blocked on that sequence.

### 📊 What a fourth sales channel costs today — measured, as evidence for the enum-vs-table question

| | |
|---|---|
| Java | 1 constant in `SalesChannel`; **2** in `AccountSystemKey` |
| Migration | 2 `account` rows (INCOME + CONTRA_INCOME); drop+recreate `account_system_key_known` (**29 → 31** values); drop+recreate `sales_invoice_channel_known` (3 → 4); plus `V17`'s `UPDATE … WHERE name = …` and verification `DO` block |
| Spec / client | regeneration → `salesChannel.ts`, `accountSystemKey.ts`, `newSalesInvoice.ts`, `salesInvoiceView.ts`, `creditNoteView.ts` |
| i18n | `enums.json` × 2 (EN, EL) |
| **Total** | **7 production files + 1 migration + 2 locale files.** Additive, no data migration, no restatement |

⚠️ **The cost is not the argument for a table**, and this measurement is recorded so the open decision
is not taken on the wrong grounds. An operator-added channel needs two accounts to exist and
`AccountSystemKey` to resolve them; a table relocates that problem rather than solving it. **If a
table is ever right, the argument will be that channel stopped being one dimension** — which is the
analysis-dimension question the roadmap already holds open.

### 📌 The AADE artefacts — one record, in the README

**The §8 annex map, the 21 XSD files, and which table carries which codification live in
[`docs/aade/v2.0.1/README.md`](aade/v2.0.1/README.md)** and are deliberately **not** duplicated here.
Two records of one table map is the drift this file has already paid for twice.

What belongs here is what the artefacts *changed* about the project record: the four items under
*"Four things this folder settled"* in that README — exemption codes 24 and 28 exist; the seeded
descriptions are confirmed against ν.5144/2024; annex 8.7 is not the Fees list; and **Q38, the
myDATA unit-of-measure codes, was never an accountant question — annex 8.13 is the published list it
was waiting for.**

⚠️ **`vat_class.code` (`'1410'`, `'1030'`, …) is Prosvasis Go's rate code, NOT AADE's VAT category
from annex 8.2.** There is no myDATA code on a VAT class today in either direction. Recorded because
the two are easy to conflate and nothing in the schema says otherwise.

## ▶ R1a — ✅ **DONE 2026-08-03.** Commits `aa1eda4` (the rewritten checklist) and `c5f9a97` (the build)

**All 48 R1a sub-parts have a verdict. 46 are done, 1 is explicitly deferred (S.4) and 1 is a
finding rather than a task (E.3).** Nothing is without a verdict. R1b's two lines are untouched and
stay `⬜`.

**Measured 2026-08-03, after R1a:** backend `mvn clean verify` exit 0, **1,440 tests, 0 failures,
1 skipped** (`LiveSeedTest`, as always). Frontend **310 tests across 31 files**; typecheck, lint,
knip and build green. Spec **230 operations and 223 schemas, 167 declaring `required`**. Migrations
**V31** and **V32**.

⚠️ **No live browser leg, and that is correct rather than skipped.** R1a ships **no screens** — R2
does — so there is nothing to open a browser against. The app image was therefore **not** rebuilt;
the unconditional rebuild rule is a precondition of *handing a live leg to the owner*, and no leg is
being handed over. **R2 is the step that needs one.**

---

## ▶ R1 — scope approved 2026-08-03. ⚠️ **Checklist REWRITTEN 2026-08-03 against the two-layer model**

**Approved as: Part 1 (land the findings, ✅ done, commit `e8ee709`), Part 2 (six decisions), Part 3
(the build).** This checklist is written **at the moment of approval**, per `CLAUDE.md`'s *an approved
proposal is a checklist, not a paragraph* — not after the build, when a summary of what was built
cannot see what was not.

⚠️ **THE CHECKLIST COMMITTED IN `746d7dd` IS SUPERSEDED.** It was written against a model the owner's
real Prosvasis Go document-type configuration has since disproved. **Part 3 below replaces it in
full**, and *Why the model changed* states the reasoning. Do not build against `746d7dd`'s version.

⚠️ **Every line below is `⬜ NOT STARTED` except where marked.** That is the honest state.

### 📋 Part 2 — the six decisions, recorded

| # | Decision | Verdict |
|---|---|---|
| A | **Fees is CUT from R1**, unscheduled, not forgotten | ✅ **Recorded** — see *Fees, and why it was cut* below |
| B | **Two codification families**, contract for one of them | ⬜ **Not started** — see the family split below |
| C | **Keep AADE's original filenames** | ✅ **Done**, commit `e8ee709`. README rewritten; *"never edited"* now extends to names |
| D | `SettlementMethod` **stays an enum**; `company.branch-number` **not** `mydata.*` | ✅ **Recorded**; the build half is scope D and F |
| E | **Stock behaviour is driven by document type** — the flags are READ, not merely recorded | ⬜ **Not started** — see *the withdrawn instruction* below |
| F | **Three artefact additions** — exemption codes 24/28, provenance, unit-of-measure codes | ⬜ **Not started** — F1, F2, F3 below |

### ⚠️ Decision E — the withdrawn instruction, recorded so nobody reinstates it

**An earlier instruction said to seed `affectsStock` and `transfersStock` as classification only and
leave them unread. It is WITHDRAWN.** The owner's own correction, recorded as such rather than as a
defect anyone found.

**The argument for leaving them unread** was that wiring them would leave stock incomplete for
Τιμολόγια until dispatch documents arrive at 18b. **It does not survive checking the sequence:** no
real data exists yet (ten dev invoices, all `TEST-`), real invoices arrive at the **step-24
migration**, and **18b lands well before it.** So the gap is dev-only and temporary.

**Against that**, ⚠️ **a flag seeded in the database that nothing reads is a recorded fact
contradicting the code** — the failure class this project has spent several sessions catching, and
the same shape as *a fact established by reading, then built upon*.

⭐ **The symmetry that confirms the design: the PURCHASE side already works this way.** Stock arrives
on the **Goods Receipt**, not on the purchase invoice — document and stock movement are already
separate entities, reconciled by GR/IR. **The sales side has no equivalent, and the dispatch document
at 18b is exactly that missing half.** Document-type-driven stock is not a new idea being introduced
here; it is the sales side catching up with the purchase side.

### 📋 Decision B — TWO codification families, not one. The distinction is who authors a row

⚠️ **An earlier framing collapsed these. They are different things and only one gets the contract.**

⚠️ **MEMBERSHIP CORRECTED 2026-08-03 (R1a).** The row below originally listed *sales document types*
and *purchase document types* as statutory codifications. **They are not, and the owner's real Go
configuration is what proved it** — see *Why the model changed* below. The statutory codification is
**`aade_invoice_type`**, the 55-value XSD enumeration; the two document-type tables are **business
lists the owner authors**. The family split itself was right; only its membership was wrong.

| | **Statutory codifications** | **Business reference lists** |
|---|---|---|
| Who authors a row | **AADE, and nobody else** | **The business** |
| Members | `VatExemptionReason`, **`AadeInvoiceType`** | `ChargeType`, **sales document types, purchase document types, both series tables, delivery methods** |
| Row authorship | **Flyway alone** | The application |
| Contract in `core-api` | `activate`, `deactivate`, `describe` — **no `create`** | *(none — an ordinary CRUD service)* |
| Enforcement | ⬜ **An architecture rule.** The absence of `create` must be **assertable, not conventional** | — |

⚠️ **`ChargeType`'s six write methods are unreachable because its ROUTES WERE NEVER BUILT, not
because it is seed-only.** Adding "Gift wrapping" is a business decision. **Freezing it under the
statutory contract would be wrong**, and the fact that it *looks* like `VatExemptionReason` from the
outside is exactly the trap this split exists to avoid. 📌 **Recorded as needing write routes,
unscheduled.**

✅ **This answers Q1-b as a consequence rather than as a judgement call:**
**`VatExemptionReasonService.create` is REMOVED**, with its tests, because the contract says Flyway
owns row authorship. If AADE adds a code, that is a migration, not an API call.

⚠️ **The counter-argument is recorded and was accepted as the reason for defining a contract rather
than copying a shape:** three copies of an unexercised decision is not a pattern. Deleting `create`
without stating the contract would have left the next entity to rediscover the argument.

### ⭐ Annex 8.1's group map — read from RASTERISED pages, and it cross-checks exactly

⚠️ **Established 2026-08-03 by rendering pages 88–92 at 170 dpi with PyMuPDF and reading them as
images.** Not from `pdftotext` — that extractor scrambles this table, which is why it is now a named
anti-pattern in `CLAUDE.md`. **Every group heading below was read visually.**

| Annex 8.1 group | Meaning | Codes | Count |
|---|---|---|---:|
| **Αντικριζόμενα Παραστατικά Εκδότη** ημεδαπής/αλλοδαπής | matched, **we issue** | `1.1–1.6`, `2.1–2.4`, `3.1`, `3.2`, `4`, `5.1`, `5.2`, `6.1`, `6.2`, `7.1`, `8.1`, `8.2`, `8.4`, `8.5`, `8.6`, `9.1–9.3`, `10.1`, `10.2` | 28 |
| **Μη Αντικριζόμενα Παραστατικά Εκδότη** ημεδαπής/αλλοδαπής → Παραστατικά Λιανικής | unmatched, **we issue** (retail) | `11.1–11.5`, `12` | 6 |
| **Μη Αντικριζόμενα Παραστατικά Λήπτη** ημεδαπής/αλλοδαπής → Λήψη Παραστατικών Λιανικής | unmatched, **we receive** | `13.1–13.4`, `13.30`, `13.31` | 6 |
| **Αντικριζόμενα Παραστατικά Λήπτη** ημεδαπής/αλλοδαπής | matched, **we receive** | `14.1–14.5`, `14.30`, `14.31`, `15.1`, `16.1` | 9 |
| **Εγγραφές Τακτοποίησης Εσόδων-Εξόδων** → Εγγραφές Οντότητας | **the entity's own adjusting entries** | `17.1–17.6` | 6 |

✅ **28 + 6 + 6 + 9 + 6 = 55, which is exactly the XSD `InvoiceType` enumeration size.** Every code in
the machine-readable list is accounted for by a group, none is left over and none was invented. **That
equality is the cross-check**, and it is why the map can be trusted rather than merely believed.

**The split is decided by Εκδότη (issuer) versus Λήπτη (recipient):**

- **SALES — 34 codes:** `1.x`, `2.x`, `3.x`, `4`, `5.x`, `6.x`, `7.1`, `8.x`, `9.x`, `10.x`, `11.x`, `12`
- **PURCHASE — 15 codes:** `13.x`, `14.x`, `15.1`, `16.1`

⚠️ **AND A THIRD GROUP THAT FITS NEITHER TABLE — a finding, not an ambiguity.** `17.1` Μισθοδοσία,
`17.2` Αποσβέσεις and `17.3–17.6` (adjusting entries, accounting and tax basis) are **the entity's own
journal entries**, not documents issued to or received from a counterparty. **Scope A says two tables;
these six codes belong to neither.** This is the "if any row's group cannot be read with confidence,
stop and list it" case arriving from the other side — they *can* be read with confidence, and the
confident reading says they do not fit. ⚖️ **Needs a decision before scope A is built:** omit them, or
add a third table, or carry a nullable group discriminator. **Not guessed.**

### ✅ …AND THE BLOCKER DISSOLVED, 2026-08-03 — by correcting the model, not by choosing an option

**A.5 is CLOSED, and none of its three options was taken.** The six `17.x` codes were homeless only
because **the 55 statutory values had been forced into two business tables**. Under the two-layer
model there is **one** codification table, `aade_invoice_type`, and the group is a **column** — so
`17.1`–`17.6` are simply six rows carrying `ENTITY_ADJUSTING`. No third table, no omission, no
discriminator to invent. **`28 + 6 + 6 + 9 + 6 = 55` still holds**, and it is now the seed's own
cross-check rather than an argument about where to put a leftover.

⚠️ **Worth keeping as a shape, because it will recur:** the blocker was real and its three options
were exhaustive *given the model it was asked inside*. What made it dissolve was noticing that the
model was wrong — a statutory list had been given the job of a business list. **When a decision has
only bad options, check whether the question is the wrong shape before choosing the least bad one.**

⭐ **Also confirmed visually, and it matters to R3:** `6.1 Στοιχείο Αυτοπαράδοσης` and
`6.2 Στοιχείο Ιδιοχρησιμοποίησης` sit under one sub-heading, `Στοιχείο Αυτοπαράδοσης -
Ιδιοχρησιμοποίησης`, as **two distinct codes**. Both are in the **issuer** group, so both are sales
document types.

### 📌 Fees, and why it was cut — decision A, with the open question stated precisely

**Not built, no schema proposed, unscheduled.** The reasoning, because "deferred" without it is how a
thing gets rebuilt wrong later:

**The likely reading** is that Go's *Έξοδα και κρατήσεις* is its list of **non-product line items**,
carrying **both** classification pairs because one row serves both directions — *Delivery* is income
when charged to a customer and expense when the courier bills us. That makes its AADE grounding
**8.8–8.11**, not 8.7. (Annex 8.7 `Κατηγορία Τελών` is 22 statutory levies and contains neither.)

⚠️ **But under that reading it is a GENERALISATION of `ChargeType`, not a sibling.** *Delivery* and
*COD fee* **already exist as `ChargeType` rows** — live, ids 1 and 2. A second table would be **two
records of the same thing**, which is the drift that let backend item 3 contradict itself for a week.
**Whether `ChargeType` grows an expense side or is subsumed is a design decision, not a settings
table**, and nothing in F5 needs it.

**The open question, stated so it can actually be answered:** ⚖️ **does Go's *Έξοδα και κρατήσεις*
list contain *Delivery* and *COD fee* — the same rows `ChargeType` already holds?** If yes it is a
generalisation and `ChargeType` is the thing to change. If no, it is a different concept and the
question is what it actually is.

## ⚠️ WHY THE MODEL CHANGED — the checklist committed in `746d7dd` was wrong, and this replaces it

**Rewritten 2026-08-03, at the start of R1a, BEFORE anything was built against it.** The previous
Part 3 checklist (commit `746d7dd`) is superseded in full. It is not preserved line by line, because
a superseded checklist sitting beside its replacement is the second record that drifts — what is
preserved is *why it was wrong*, which is the part with any future value.

**The correction is the owner's, not a defect anyone found.** He supplied Prosvasis Go's actual
document-type configuration — **15 sales types and 4 purchase types, each with its own series** — and
two facts in it break the previous design:

1. ⚠️ **Go's type numbers (`7001`, `7071`, `2062`, …) are GO'S INTERNAL IDS, not AADE codes.** Under
   *Non-negotiable architecture rule 2* they belong in the **Go adapter's mapping table** and nowhere
   near a core entity. The previous model, in which "the AADE code IS the row", had no place to put
   them and no way to notice that they were not AADE's.
2. ⚠️ **SIX of the nineteen types have NO AADE invoice type at all** — Προσφορά, Δελτίο Αποστολής,
   Δελτίο ποσοτικής παραλαβής, Παραγγελία, Δελτίο Παραλαβής, ΔΑ Αποστολής Σε Προμηθευτή. They are
   **operational documents, not tax documents.** A model in which the AADE code is the row's identity
   cannot represent a document that has none.

**And a stated requirement the previous model could not meet.** The owner: *"in the future we may
need to use more document types and/or series. This means that the build must be complete."*
**Document types and series must be USER-CREATABLE.**

⚠️ **The original specification always had "aade code" as ONE FIELD AMONG SIX.** That reading was
correct and was overridden — the seed-only framing came from `CLAUDE.md`'s document-model section
generalising *"document types are seeded from the AADE list"* into *"a document type IS an AADE
code"*. **`CLAUDE.md` §5 has been corrected in the same commit as this checklist**, because a
governing statement that contradicts the approved model is exactly what the next session builds
against.

### 📐 The corrected model — TWO LAYERS

| | **Layer 1 — `aade_invoice_type`** | **Layer 2 — `sales_document_type` / `purchase_document_type`** |
|---|---|---|
| What it is | **The statutory codification.** All 55 XSD `InvoiceType` values, one table | **The business's own document lists**, exactly as the owner specified them |
| Rows | **55, seeded by Flyway**, group from annex 8.1 as a **column** | ⚠️ **SHIPS EMPTY.** The owner creates his own through R2's screens |
| Who authors a row | **AADE, and nobody else** | **The business** |
| Contract | **Statutory-codification contract** — `activate`, `deactivate`, `describe`, **no `create`**. ⚠️ **This contract applies HERE AND ONLY HERE** | **Full CRUD.** Ordinary write routes |
| Columns | id, code, description, group, active | id, description, `affectsStock`, `transfersStock`, `requiresMyDataTransmission`, active, **nullable FK → `aade_invoice_type`** |

⚠️ **The FK is NULLABLE because six of the owner's nineteen types have no AADE type.** That is
modelled as **absence**, never as a sentinel row and never as an `"N/A"` code — an `N/A` row in a
statutory codification is an invented AADE code, which the seeding rule forbids outright.

⚠️ **The owner's 19 types are deliberately NOT seeded, and their AADE mappings are deliberately NOT
inferred.** He will create them through R2's screens, choosing each AADE type himself: he knows which
is which better than an inference does, and it exercises the CRUD mechanism the moment it exists.
**An inferred Go→AADE mapping would be a guess written into a statutory field** — refuse rather than
guess.

### ⚠️ The A.3/A.4 dependency that was never written down — another instance of the named shape

**A.3 and A.4 read *"Seed the 34 sales codes … active only where in use"* and *"the 15 purchase
codes, same treatment"*.** ⚠️ **"In use" is not a fact about the AADE artefacts. It is a fact about
this business, and the only source for it is the owner's Go configuration** — which was not in the
repository, was not named as a prerequisite in the checklist, and existed only in the chat
transcript. **A.3 and A.4 as committed could not have been executed by anyone reading the
repository**, and nothing in them said so.

📌 **Recorded as another instance of `CLAUDE.md` §*A decision reached in a design conversation gets
the same close-out discipline as a build step*.** The shape is identical to the four core-model
decisions that were absent from every repository document on 2026-08-02: the *decision* was written
down and its *precondition* was not, so the checklist read as actionable while being blocked on
something nobody had recorded. **A sub-part whose input lives only in a conversation is not a
checklist line; it is a checklist line plus a missing one.**

⭐ **Under the two-layer model the dependency dissolves rather than being satisfied.** Layer 1 seeds
**all 55 rows active** — activation no longer encodes "this business issues it", because that is what
layer 2 is for. Nothing in R1a needs to know which types the business uses.

---

### 📋 Part 3, REWRITTEN — one line per sub-part. **R1a and R1b marked**

**Legend:** 🅐 = R1a, this session. 🅑 = R1b, explicitly not this session.

#### Layer 1 — the statutory codification

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **L.1** | 🅐 | `aade_invoice_type` — id, code, description, `group`, active. **One table for all 55 values** | ✅ **Done.** `V31`. The column is `invoice_group` and not `group`, which is reserved in SQL | ✅ **Passed** |
| **L.2** | 🅐 | Codes seeded from **`SimpleTypes-v2.0.1.xsd`** `InvoiceType` — 55 flat enumerations, **never a text dump** | ✅ **Done.** All 55, in the XSD's own enumeration order — which is also annex 8.1's reading order, and therefore the order rows are returned in (by `id`, never by `code`: a text sort puts `10.1` before `2.1` and `13.31` before `13.4`) | ✅ **Passed** — server paging, on the first screen in this application to have it |
| **L.3** | 🅐 | Greek descriptions from **rasterised annex 8.1 pages, read visually**. Never from `pdftotext` | ✅ **Done.** Pages 89–93 rendered at 170 dpi with PyMuPDF and read as images. ⚠️ **Codes `4` and `12` have an EMPTY description cell** — the only text AADE gives them is the group label `Για Μελλοντική Χρήση`, which is what they carry, read from the artefact rather than invented | ✅ **Passed** — the three sortable, the rest plain text |
| **L.4** | 🅐 | `group` column from annex 8.1's rasterised headings — the five groups of the map above | ✅ **Done.** Five values, CHECK-constrained, named for what they mean (`ISSUER_MATCHED` …) rather than transliterated | ✅ **Passed** — the SERVER reordered, which is the half a browser had to answer |
| **L.5** | 🅐 | The **`28 + 6 + 6 + 9 + 6 = 55`** cross-check asserted **in a test**, not merely stated in a comment | ⭐ **Done, TWICE and independently.** A `DO` block in `V31` refuses to seed a list that does not reconcile, and `AadeInvoiceTypeIT` **parses `SimpleTypes-v2.0.1.xsd` itself** so the database is compared against **the artefact** rather than a list typed into a test file twice — with a negative control that fails if the XSD was not read | ✅ **Passed** — no Edit control anywhere, for a FULL-access role |
| **L.6** | 🅐 | Adopts the statutory-codification contract: `activate`, `deactivate`, `describe`, **no `create`** | ✅ **Done.** `AadeInvoiceTypeService extends StatutoryCodification<AadeInvoiceTypeView>` | ✅ **Passed** — plain text with the reason, not blank and not disabled |
| **L.7** | 🅐 | Read + `activate`/`deactivate`/`describe` routes | ✅ **Done.** 5 routes. ⚠️ With `side=ISSUED|RECEIVED` and `group=` filters, because offering all 55 on a *sales* document-type form would put “Ενοίκιο Έξοδο” in the picker | ✅ **Passed** — mandatory series, no channel field, no document-type field |
| **A.5** | 🅐 | ✅ **CLOSED — the six `17.x` codes are rows carrying `ENTITY_ADJUSTING`.** No third table, no omission, no discriminator | ✅ **Resolved by correcting the model**, 2026-08-03 |

#### B — the codification contract itself

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **B.1** | 🅐 | Statutory-codification contract in `core-api` — `activate`, `deactivate`, `describe`, **no `create`** | ✅ **Done.** `gr.novotrade.novocore.core.api.codification.StatutoryCodification` |
| **B.2** | 🅐 | Architecture rule asserting the absence of `create` on the contract's implementors | ✅ **Done.** `StatutoryCodificationRulesTest`, 4 tests. Forbids `create`/`add`/`register`/`insert` on any implementor, **carries its own negative control** (no implementors ⇒ fail, not a vacuous pass), and asserts the rule **in both directions** — the two codifications are under the contract and the six business lists are deliberately not |
| **B.3** | 🅐 | `VatExemptionReason` adopts the contract | ✅ **Done**, and it gained `describe` |
| **B.4** | 🅐 | `VatExemptionReasonService.create` **removed**, with its tests (Q1-b, closed by consequence) | ✅ **Done. Q1-b is CLOSED.** `create` and `NewVatExemptionReason` deleted; `VatExemptionReasonIT` rewritten — it was built entirely on `create`, and it now asserts against the shipped seed, which is the honest fixture for a Flyway-owned list |
| **B.5** | 🅐 | `ChargeType` recorded as a **business reference list** needing write routes; **does not** adopt the contract | ✅ **Done** — recorded in `StatutoryCodification`'s javadoc as the list that *looks* like a codification and is not. Its write routes stay unbuilt and unscheduled |

#### A — the business document-type lists. ⚠️ SHIPPED EMPTY

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **A.1** | 🅐 | `sales_document_type` — id, description, `affects_stock`, `transfers_stock`, `requires_mydata_transmission`, active, **nullable FK → `aade_invoice_type`** | ✅ **Done.** `V31` |
| **A.2** | 🅐 | `purchase_document_type` — same columns. ⚠️ **`affects_stock` IS meaningful here — do not remove it** | ✅ **Done**, and `affects_stock` is documented at the column, the entity, the view and the service with the `2041` example |
| **A.3** | 🅐 | ⚠️ **Ship both EMPTY.** No seed of the owner's 19 types, **no inferred Go→AADE mapping** | ✅ **Done, and asserted over HTTP** — `theBusinessListsShipEmpty` checks all five tables, because “deliberately seeded nothing” and “the seed silently failed” look identical from a screen |
| **A.4** | 🅐 | Full **write routes** for both — create, describe, change flags, set/clear AADE type, activate/deactivate | ✅ **Done.** 22 routes across the two lists |
| **A.6** | 🅐 | `affects_stock` / `transfers_stock` **NULLABLE**, null = *nobody has decided*. ⚠️ A `false` is a guess wearing a value's clothes | ✅ **Done.** Boxed `Boolean` on the entity, the view and the request record |
| **A.7** | 🅐 | CHECK `active = false OR (affects_stock IS NOT NULL AND transfers_stock IS NOT NULL)`, **checked on CREATION only** — deactivating later must never invalidate historical documents | ✅ **Done**, as a table CHECK — a constraint the database holds cannot be bypassed by a second write path. ⚠️ **A type created without the flags is an inactive DRAFT**, not a refusal: refusing would make it impossible to save a type before the stock question is answered, and defaulting to `false` would record a decision nobody took. `GET .../drafts` lists them and `reactivate` refuses one by name |
| **A.8** | 🅐 | Report what existing `active`-flag entities do, and **follow that pattern** rather than inventing one | ✅ **Done — reported below** under *What the existing `active`-flag entities do*. The pattern was followed, not invented |
| **A.9** | 🅐 | Record that the sales/purchase split is **ours**, from annex 8.1's headings — the XSD has one enumeration | ✅ **Done** — in `V31`'s header, `AadeInvoiceGroup`'s javadoc, and the two side guards that enforce it. ⚠️ Nothing in AADE's artefacts stops a sales type naming a purchase code; **this codebase is the only place that split exists** |
| **A.10** | 🅐 | Record that **Go's type numbers are adapter data** (rule 2), and where they will live | ✅ **Done** — `V31`'s header and `CLAUDE.md` §*The document model* item 5. They belong in the Go adapter's mapping table under architecture rule 2 |
| **A.11** | 🅐 | Record **R2's consequence: full CRUD screens**, where R2 will see it | ✅ **Done** — the roadmap's R2 row and this file. R2 needs **full CRUD** screens, not the read-plus-activate shape F4 built for VAT classes |
| **A.12** | 🅐 | Record the **`2062` ΤΔΑΑ / `2041` Δελτίο Παραλαβής** example — a purchase document bringing stock IN with no payable behind it. ⭐ The clearest justification `affects_stock` has on the purchase side | ✅ **Done** — `V31`, `PurchaseDocumentTypeView`, `PurchaseDocumentTypeService` and `DocumentReferenceDataIT` |

#### C — the series tables. ⚠️ SHIPPED EMPTY

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **C.1** | 🅐 | `sales_document_series` — id, abbreviation, description, active, documentType FK, **channel (NULLABLE)**, `gets_mark`, `transformable_into_series` | ✅ **Done.** `V31` |
| **C.2** | 🅐 | `purchase_document_series` — same, **with NO channel column at all** | ✅ **Done.** ⚠️ No column, no accessor, no service method and no route — and `DocumentReferenceDataEndpointIT` asserts the **route** is absent, because “there is no route” and “the route silently does nothing” are indistinguishable to a caller |
| **C.3** | 🅐 | ⚠️ Ship both **EMPTY** — the owner creates his own | ✅ **Done**, asserted with A.3 |
| **C.4** | 🅐 | Full **write routes** for both | ✅ **Done.** 18 routes across the two. `PUT` to set and `DELETE` to clear for channel and transformation target, on `VatClassController`'s reduced-counterpart precedent |
| **C.5** | 🅐 | Record **both channel decisions** with their reasoning: null on sales = *not a sales channel*; **absent** on purchase because a column that can only ever be null invites someone to fill it | ✅ **Done** — `V31`, both view records, both controllers and `DocumentReferenceDataIT` |
| **C.6** | 🅐 | Uniqueness on **(series, number)**. ⚠️ **Must reconcile with the existing trigger + partial unique index on `upper(document_number)`** | ⭐ **Done, and the reconciliation was the whole difficulty.** The key became `(COALESCE(series_id, -1), upper(document_number))`. ⚠️ A plain `UNIQUE (series_id, …)` would have **silently lost** today's guarantee, because two NULLs never collide in a unique index — global uniqueness would have become no uniqueness at all, on precisely the rows nobody would think to test. The trigger gained `IS NOT DISTINCT FROM` for the same reason. With every row's series NULL, the index is byte-for-byte today's behaviour |
| **C.7** | 🅐 | **No sequence, no counter, no allocation.** "Integers from 1, continuous" recorded as an **expectation**, not enforced | ✅ **Done**, and asserted as an **absence**: `DocumentReferenceDataIT` checks that neither series service has any method whose name contains `next`, `allocate` or `number` |

#### D–H — the remaining R1a scope

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **D.1** | 🅐 | `delivery_method` — id, abbreviation, description, active, plus routes | ✅ **Done.** `V31`, 6 routes, ships empty |
| **E.1** | 🅐 | myDATA payment code as **one constructor argument on `SettlementMethod`, no migration**: CASH→3, BANK_DEPOSIT→1, CARD_POS→7, ON_ACCOUNT→5, SKROUTZ→5 | ✅ **Done.** One constructor argument, no migration |
| **E.2** | 🅐 | `ACS_COD`, `PAYPAL`, `STRIPE` **null and listed open — three, not one** | ✅ **Done**, and asserted **by name** rather than by count — a count says “three of something” and cannot say which three came back (8a's gate-3 lesson). A fourth test makes every value either mapped or explicitly listed open, so a ninth settlement method fails the build |
| **E.3** | 🅐 | Strike *"settable once, then frozen"* from the immutability section, with the reason | ⚠️ **NOTHING TO STRIKE — a finding, not a completed line.** The wording *“settable once, then frozen”* **does not exist anywhere in this repository** in the sense this line means. Searched: the only occurrences are about `unit_of_measure.mydataCode` (F4 row 13 below, and the primer), which is a different subject and is **still true** — `recordMydataCode` refuses a second write. A settlement method's myDATA code is a **constructor argument on an enum**, so there is nothing settable at runtime to freeze. The sentence this line was written against was in the proposal and never reached a repository document. **Recorded rather than silently ticked** |
| **F.1** | 🅐 | `company.branch-number` setting. Head office is `0`. Never a constant in code | ✅ **Done.** `V32` seeds `0`; `SettingKeys.COMPANY_BRANCH_NUMBER`; catalogued `READ_WRITE` and on the Documents settings page. ⚠️ **The gap is wider and is recorded, not filled:** there is no ΑΦΜ, no company name and no address — this is one field of a company-identity block that does not exist |
| **G.1** | 🅐 | ΜΑΡΚ, UID, QR URL, transmission status on `sales_invoice` — nullable, **schema + validation only** | ✅ **Done.** `V32`. `mark` is `bigint` because AADE's own `response-v2.0.1.xsd` types `invoiceMark` as `xs:long`. ⚠️ **No setter and no route** — nothing in R1a is entitled to supply one. A CHECK keeps `TRANSMITTED` and the presence of a ΜΑΡΚ from ever disagreeing. Three status values, not four: a `FAILED` nothing can produce reads as coverage and is not |
| **G.2** | 🅐 | Series reference on `sales_invoice`. ⚠️ **Keep `document_number`, do not duplicate it** | ✅ **Done.** `document_number` untouched; `series_id` is a nullable FK. See C.6 for the uniqueness reconciliation |
| **G.3** | 🅐 | ADR note — why statutory identifiers are core fields and Go's document id is not | ✅ **Done** — `V32`'s header and `SalesInvoice`'s field block carry the argument: the test that separates them is whether the value survives the vendor being replaced |
| **H.1** | 🅐 | Spec-version marker recording `v2.0.1`, pointing at `docs/aade/v2.0.1/` | ✅ **Done.** `aade.spec-version = v2.0.1`, catalogued **`READ_ONLY`** — and as a *second* read-only reason, `derived`, not `statutory`. Editing it would not change a row; it would only make the marker lie about the rows that are there |

#### The three artefact additions

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **F1** | 🅐 | Seed exemption codes **24 and 28** with verbatim wire strings from annex 8.3 | ⚠️ **Done, WITH ITS PREMISE CORRECTED.** Both codes seeded with annex 8.3's ν.5144/2024 text, read visually. **But `mydataCode` is NULL on both, not a “verbatim wire string”.** ⭐ **Annex 8.3 contains no wire strings** — it gives the reason text under two article numberings. The `N-description` form on the other 26 rows is *Prosvasis Go's* rendering, transcribed verbatim precisely because composing one is a bet, and **codes 12 and 13 are the standing proof that the bet loses.** Go has no row for 24 or 28, so there is nothing verbatim to copy. NULL is the OSS/IOSS stance: *no mapping exists* |
| **F1b** | — | Both rows listed as **needing the accountant** for `input_vat_deductible` | ✅ **Done** — on the accountant list, commit `e8ee709` |
| **F2** | 🅐 | Provenance on the existing 29 rows — confirmed against ν.5144/2024, brought under the version marker | ✅ **Done** — table and column comments on `vat_exemption_reason` now cite annex 8.3 and point at `aade.spec-version`. ⚠️ The `code` column's comment records **how V8's open question was closed**: `VatExemptionType` is `xs:int` restricted to `1..31` **with no gaps**, so 24 and 28 were absent from Go rather than retired by AADE |
| **F3** | 🅐 | Seed the unit-of-measure myDATA codes from **annex 8.13** | ⚠️ **Done, WITH ITS PREMISE CORRECTED — and Q38 is SHARPENED, not closed.** ⭐ **Annex 8.13 has SEVEN codes, not eight**, confirmed against `QuantityType` (`xs:int`, `1..7`); the eight was our own unit *rows*. **Four map with certainty** (PIECE→1, KILOGRAM→2, LITRE→3, METRE→4). **Four are left NULL and listed open, and each is a different kind of gap:** GRAM and MILLILITRE have **no AADE code at all** — the list has no sub-units, and mapping GRAM to Κιλά would transmit a quantity wrong by a factor of a thousand; SET and PACK are a genuine judgement between `1` and `7 Τεμάχια_Λοιπές Περιπτώσεις`, and the choice changes what is transmitted |

#### The two recording items

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **N.1** | 🅐 | **PyMuPDF is a dev-environment prerequisite.** `pdftoppm` is absent on this machine, so the Read tool's PDF path fails without it | ✅ **Done** — recorded below under *Two things this session had to know about its own tooling*. `pdftoppm` is absent on this machine, so the Read tool's PDF path fails; PyMuPDF is what rasterises the annexes |
| **N.2** | 🅐 | **The Q38 shape as a named failure mode** — a question parked on a person when the answer is in a document nobody has | ✅ **Done** — recorded below, and **sharpened by F3**: the artefact answered most of Q38 and the residue is genuinely a human decision, which is a more useful lesson than “the question was never the accountant's” |

#### Cross-cutting — R1a is not backend-only

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **S.1** | 🅐 | Spec regenerated; `npm run api:generate`; **every drifted fixture reported BY NAME** | ✅ **Done.** Spec **176 → 230 operations**, **196 → 223 schemas**, **143 → 167 declaring `required`** (all measured 2026-08-03). Client regenerated and **proven byte-identical on a second run**. **Five drifted fixtures, every one named below** |
| **S.2** | 🅐 | Contract ITs against the **real running server** for every new write route | ✅ **Done.** `DocumentReferenceDataEndpointIT` — 17 tests over real HTTP against the real server, driving **all 54 new routes**. ⭐ **It found a defect nothing else could**, written up below |
| **S.3** | 🅐 | `CLAUDE.md`, `PROGRESS.md`, the primer and the roadmap updated | ✅ **Done** — all four, in this close-out |
| **S.4** | 🅐 | Dev fixtures may need a few types and series to exist — **DEV seed only, marked as dev data, never in a production migration** | ⚠️ **EXPLICITLY DEFERRED to R2. Nothing was built.** No dev seed of document types or series exists. **Nothing in R1a needs one:** R1a ships no screens, and every test that needs a type or a series creates its own. Seeding dev fixtures now would put rows in front of the owner that he did not author — the exact thing A.3 exists to prevent. **R2 is the step that will want them**, and its roadmap row says so |

#### 🅑 R1b — explicitly NOT this session

| # | Phase | Sub-part | Verdict |
|---|:---:|---|---|
| **R1b-1** | 🅑 | `documentType` becomes **MANDATORY** on `NewSalesInvoice`; `SalesInvoiceServiceImpl` branches on `affectsStock` before `consumeStock`. ⚠️ **SILENT** — no pending state, no marker, no warning; a non-moving type creates no consumption row. `stock_consumption`'s source CHECK is **not widened**. Tests for the new branch **with a negative control** | ⬜ **R1b** |
| **R1b-2** | 🅑 | **CHANNEL BECOMES AUTHORITATIVE** — an invoice's channel comes **from its series** and is not independently settable. **F5 therefore has no channel field.** ⚠️ `sales_invoice.channel` is `NOT NULL` and self-supply series have no channel: **do NOT relax the constraint** — **REFUSE** to record against a channel-less series instead, so the constraint holds the open question open rather than papering over it. **R3 resolves both together** | ⬜ **R1b** |

### ⚖️ The R1a / R1b split — approved, and the boundary is a test-facing one

**The boundary is not arbitrary and did not move with the model correction.**

| | **R1a — additive** | **R1b — behavioural** |
|---|---|---|
| Sub-parts | L.1–L.7, B.1–B.5, A.1–A.12, C.1–C.7, D.1, E.1–E.3, F.1, G.1–G.3, H.1, F1, F2, F3, N.1, N.2, S.1–S.4 | R1b-1, R1b-2 |
| Risk shape | **Nothing existing changes behaviour.** New tables, one seed, new nullable columns, one deletion of an uncalled method | ⚠️ **Adds a mandatory field to `NewSalesInvoice`** and branches the consumption path |
| What it can break | Almost nothing. The 1,381 tests (2026-08-03) do not touch any of it | ⚠️ **Every construction site of `NewSalesInvoice`** — `TradingQuarter`, `WholeScenarioIT`, `SalesInvoiceIT`, `CreditNoteIT`, `PermissionSweepIT`, `RefusalMatrix`, `LiveSeedTest`, plus frontend fixtures |

**The argument for the boundary, in one sentence:** **R1a cannot change what any existing test
asserts**, and R1b changes what **every sales-invoice test constructs** — so a failure in R1a is a
failure in new code, and a failure in R1b could be either. Keeping them apart is what makes a red
build diagnosable.

⚠️ **The cost, stated honestly because it is the same cost that moved 8a's boundary:** both halves
change `docs/api/openapi.json`, so **both need their own spec + client regeneration.** That is two
regenerations instead of one, and `frontend.yml` triggers on each. It is a real price and it is
smaller than the alternative — 8a's own lesson was that a boundary which leaves `main` red is not a
boundary, and this one leaves it green on both sides.

**R1b depends on R1a** (`documentType` cannot become mandatory before the document types exist), so
the order is fixed rather than a preference.

### ⭐ R1a's findings — four premises corrected, and one defect only the real server could find

#### 1. ⚠️ A derived accessor on a serialised record answered `500` for the whole codification

**`AadeInvoiceTypeView` shipped with a one-line `issuedByUs()`** delegating to
`AadeInvoiceGroup.issuedByUs()`, which **throws** for the six `ENTITY_ADJUSTING` codes — because a
payroll adjusting entry genuinely has no issuing party, and asking is a programming error. The
exception was right. The delegate was not.

**Every service-layer test passed.** `AadeInvoiceTypeIT` had eleven of them, one of which asserted
the throw *and called it correct*. `GET /api/aade-invoice-types` answered
**`500 "Failed to write request"`** for all 55 rows, because **Jackson serialises a record's no-arg
public accessors as properties** and called it on every one.

⚠️ **The 500 was luck, and that is the part worth keeping.** `OpenApiSchema` describes **record
components**, so the committed spec documented five properties while Jackson would have written six.
**A derived accessor that merely returned a value would have added an undocumented field to every
response, the generated TypeScript would not have had it, and nothing anywhere would have said so.**
The throw is what made the disagreement loud.

**Two guards now, at the two layers that can see it:** `AadeInvoiceTypeIT` refuses any no-arg public
accessor on the view that is not a record component, and `DocumentReferenceDataEndpointIT` asserts
the **wire body** carries exactly the five documented properties.

📌 **This is `CLAUDE.md`'s standing practice paying for itself again** — *when the question is “will
the backend accept this”, the backend has to answer it.* Nothing below the HTTP boundary could have
seen it.

#### 2. ⚠️ …and it was diagnosed wrong twice, by a stale jar, in the same session

**The first fix looked like it had not worked.** Removing `issuedByUs()` and re-running
`mvn -pl app` reported the identical 500 — so the obvious conclusion was that the diagnosis was
wrong. **It was not: `-pl app` without `-am` answers from the previously installed jar.** The second
occurrence was the same trap with a different mechanism: `install` aborted at a *test* still
referencing the removed method, so `core-api` reinstalled and **`core` did not**, and the app then
ran new API against old implementation and answered `500` from a `NoSuchMethodError`.

⭐ **This is `CLAUDE.md`'s *the thing that answered was not the thing under test*, twice, inside
twenty minutes** — and neither occurrence involved a container or a deployment. **A Maven reactor is
enough.** The habit that caught it both times was checking the *build's own* exit status rather than
the test result: `INSTALL=1` was on screen both times and was the whole answer.

#### 3. ⚠️ Annex 8.3 contains no wire strings — so codes 24 and 28 are seeded NULL

The checklist said *“seed exemption codes 24 and 28 with verbatim wire strings from annex 8.3”*. The
annex gives the **reason text** under two article numberings and nothing else. The `N-description`
form the other 26 rows carry is **Prosvasis Go's** rendering, and `V8` stored it verbatim
*specifically because composing one is a bet* — with codes 12 and 13 as the standing proof that the
bet loses (their description names `Πλοία Ανοικτής Θαλάσσης` and their myDATA string does not).

**Go has no row for 24 or 28**, so there is nothing verbatim to copy, and a composed value would be
a fabricated string transmitted to the tax authority. **NULL, with both listed open** — the stance
already taken for OSS/IOSS.

#### 4. ⚠️ Annex 8.13 has SEVEN codes, and the eight was our own row count

`QuantityType` in `SimpleTypes-v2.0.1.xsd` is `xs:int` restricted to `1..7`. The checklist's
*“the 8 unit-of-measure myDATA codes”* conflated AADE's list size with NovoCore's **unit rows**, and
the two do not line up. Four map with certainty; four do not, for two different reasons — see F3's
verdict. **`GET /api/units-of-measure/without-mydata-code` now lists four rather than eight**, which
is the honest shape of a question that was partly answerable from a document and partly is not.

#### 5. ⚠️ Codes `4` and `12` have an empty description cell in annex 8.1

Both are legal `InvoiceType` values. AADE gives them **no description at all** — the only text is
the left-column group label `Για Μελλοντική Χρήση`. That label is what they carry, read from the
artefact rather than invented, and asserted in two tests so nobody later “fixes” what looks like a
placeholder.

### 📊 What the existing `active`-flag entities do — A.8's report

**Asked for so the new tables would follow the established pattern rather than invent one.** Read
from `VatClass`, `UnitOfMeasure`, `ChargeType`, `VatExemptionReason`, `Product`, `Customer`,
`Supplier` on 2026-08-03:

| The pattern | And R1a's tables |
|---|---|
| `active boolean NOT NULL DEFAULT true` | ✅ Same, on all five |
| Paired `deactivate` / `reactivate` — never `activate`, never a boolean setter | ✅ Same |
| Both are `POST /{id}/deactivate` and `/reactivate`, answering `204` | ✅ Same |
| Both are **idempotent** — already-inactive `deactivate` returns rather than failing | ✅ Same |
| **Nothing is ever deleted**; an inactive row stays readable so issued documents stay explicable | ✅ Same |
| A list route takes `?active=true`; the unfiltered list is everything | ✅ Same |
| An audit entry per transition, carrying the row's human identifier | ✅ Same |
| ⚠️ **Deactivation is refused where a live reference exists** — `UnitOfMeasureService` names the products still using a unit | ⚠️ **Not adopted, deliberately.** Nothing references a document type yet: `sales_invoice.series_id` is nullable and unused, and R1b is what makes a type reachable from a document. Adding a refusal now would guard a reference that cannot exist. **R1b owns it** |

⚠️ **One thing R1a's tables do that none of the others does, and it is new rather than inconsistent:**
`reactivate` can be **refused**, when a document type's stock behaviour is still undecided. No
existing entity has a state where reactivation is invalid, because none has a nullable behaviour
flag. The refusal names the flag.

### 📋 The five drifted fixtures, by name — S.1

**Every one is listed, including the two that were a consequence of a decision rather than a count.**

| # | Fixture | What drifted, and what it now says |
|---|---|---|
| 1 | `frontend/src/api/client-shape.test.ts` | Operation count **176 → 230** and write count **94 → 134**, with the per-group breakdown of the 54 written into the comment so the next reader does not have to re-derive it |
| 2 | `frontend/src/api/spec-hygiene.test.ts` | Operation count **176 → 230**; schemas declaring `required` **143 → 167**. ⚠️ The comment now says the **direction** matters more than the number — a *drop* means a record stopped guarding something |
| 3 | `frontend/src/i18n/locales/en/enums.json` and `.../el/enums.json` | **12 new labels each.** The Greek uses AADE's own annex 8.1 vocabulary (`Αντικριζόμενα Εκδότη`) rather than a translation of our enum names, because that is what the operator sees in myDATA and in Go |
| 4 | `frontend/src/pages/settings/settings-catalogue.ts` + `.test.ts` | The two new keys. ⚠️ **The test caught a real conflation** — see below |
| 5 | `frontend/src/pages/settings/settings.test.tsx` | Two new fixture rows, and the never-writable assertion now covers **both** read-only keys with their **different** reasons |

⭐ **Fixture 4 is worth more than a line.** `aade.spec-version` was first added carrying
`readOnlyReason: 'statutory'` — reusing the flag that existed — and
`settings-catalogue.test.ts` **failed**, which is exactly what its own comment predicted: *“a second
key acquiring `readOnlyReason` would mean somebody used it as a convenient way to say ‘not built
yet’”*. A specification version is not set by law; it is **derived** from what a migration seeded,
and editing it would not change a row — only make the marker lie about the rows that are there. So
there are two reasons now, the screen shows a different explanation for each, and **the assertion
was rewritten to compare key→reason PAIRS**, because a list of keys would have gone green the moment
the wrong reason was attached to the right key.

📌 **A sixth gap was found while fixing the fifth, and it was silent.** `SettingRow` resolves its
label with `t('settings.key.<dotted key>', { defaultValue: key })`, so a key with no translation
renders as `company.branch-number` on screen and in the “Edit …” button — no error, no warning,
looking exactly like a row somebody forgot to finish. It shipped that way for one commit and was
caught only because a screen test happened to assert a button by name. **A new test now asserts
every `ALL_SETTINGS` key has a label in both locales.** ⚠️ It is deliberately not part of
`enum-labels.test.ts`: those are a *different* set of strings used in a different place, and both
existed for these two keys — only one of them was the one the row reads.

### 📌 Two things this session had to know about its own tooling — N.1 and N.2

**N.1 — PyMuPDF is a dev-environment prerequisite, and `pdftoppm` is not present.** The Read tool's
PDF path shells out to `pdftoppm`, which is absent on this machine (`pdftotext` **is** present,
which is worse than neither: the one that works is the one that must not be used). Rasterising an
annex page is:

```
python -c "import fitz; fitz.open(pdf)[page-1].get_pixmap(dpi=170).save(out)"
```

⚠️ **Anyone reading an AADE annex needs this.** Without it the only working extractor is the one
`CLAUDE.md` names as an anti-pattern.

**N.2 — the Q38 shape, named: *a question parked on a person when the answer is in a document
nobody has*.** `unit_of_measure.mydata_code` sat on the waiting-on-the-accountant list from step 3b
because no source had been supplied. Annex 8.13 had been published throughout. ⚠️ **And the sharper
version is what F3 found:** the artefact answered **four of eight** rows, and the other four are
genuinely a human decision — two because AADE's list has no sub-unit at all, two because the choice
between `1` and `7` changes what is transmitted. **The failure was not “it was never the
accountant's question”. It was filing the WHOLE question against a person because the document that
answers most of it was not in the repository** — which left nobody able to see which part was
actually theirs.

### ➡️ What R1b inherits, beyond its own two lines

- **`sales_invoice.series_id` exists and is nullable and unused.** R1b makes it the source of the
  invoice's channel; nothing populates it today.
- **Document-type deactivation is unguarded**, deliberately — see A.8's report. R1b is what makes a
  type reachable from a document, and therefore what owns the refusal.
- **The uniqueness key is already `(COALESCE(series_id, -1), upper(document_number))`**, so R1b does
  not have to touch it. With every row's series NULL it behaves exactly as it did before R1a.
- **`TransmissionStatus` has three values and no `FAILED`.** Whichever step first has something that
  can *put* a document into that state adds it.

**R1b depends on R1a** (E1 cannot make `documentType` mandatory before A creates the document types),
so the order is fixed rather than a preference.

## Q1 — the backend follow-up queue — 2026-08-03. Reconciled against the approved scope

**Approved as decisions A–I plus a four-item work order. Every part below has a verdict.**
✅ **Everything below has a verdict, and the one thing left open on 2026-08-03 — item 7's regression
— was closed by 8a the same day.** Only **Q1-b** remains, and it is a decision to take with R1 rather
than a task. Figures in this section are *as Q1 landed*: backend `mvn clean verify` exit 0, **1,377 tests, 0 failures, 1 skipped**
(`LiveSeedTest`, as always). Frontend **308 tests across 31 files**, lint, build and knip green.
**176 API operations** (was 175). All figures measured 2026-08-03.

### 📋 The decisions, one line each

| # | Decision | Verdict |
|---|---|---|
| A | Move `Required` + the exception into `core-api`, rename to drop "Request" | ✅ **Done.** Both now in `gr.novotrade.novocore.core.api.shared`; **`InvalidInputException`**. Name kept as proposed — it matches the 22 existing `Invalid…Exception` types in `core-api`. `InvalidCommandException` was considered and rejected: "command" is used in this codebase only for `ReversalCommand` and would read as applying to command-shaped records only. 26 files repointed. `WebExceptionHandler` keeps the mapping and now carries the 400-over-422 argument explicitly |
| A | Record the *finding* in `CLAUDE.md`, next to the anti-pattern | ✅ **Done.** New subsection *"Five instances were not one confusion. They were two."* Group A (1, 3, 4) local slips with the remedy in reach; Group B (2, 5) remedy **structurally unreachable** from `core-api`. States that no guard could have found it and that the instance count was measuring the wrong thing |
| B | Item 4 part 2 — a sweep case carrying a valid body a domain rule refuses | ✅ **Done.** `PermissionSweepIT.noDomainRuleRefusesAWellFormedBodyWithoutSayingWhy`, 8 cases, none of which creates anything. **Proven against the defect**: run with the fix disabled it reported both bare-400 sites; restored, green. Why it is not a fourth guard of the same kind is written into its javadoc and into `CLAUDE.md` — the other three probe *absent* input, this probes *present-and-wrong* |
| C | Item 8 leaves Q1, becomes its own step 8a/8b, after Q1 and before R1 | ✅ **Done.** Roadmap rows added, footnote ᵈᵉᶜ carries both reasons, the open decision it replaced is struck through with the outcome. Q1 restated as four items in the roadmap, this file and the primer |
| C | The bidirectional cross-check is part of 8a's design, not optional | ✅ **Done, and built.** ⚠️ **It is NOT an ArchUnit cross-check** — 8a's Phase 0 measured that ArchUnit carries no argument information and mis-attributes lambda-body calls (342 reported vs 340 actual). ASM + reflection does the attribution. The reason it is not optional stands: without it the annotation is 339 hand-applied assertions nothing verifies |
| D | Record BOTH figure sets, do not overwrite | ✅ **Done.** See *The two counts* below. The 90 / 28 is kept with its basis; the 2026-08-03 scan is recorded beside it with its own basis and method |
| E | The probe technique becomes a named practice in `CLAUDE.md` | ✅ **Done.** *"Named practice: the throwaway probe"*, including the two premise errors it caught this session and the instruction to **delete it afterwards** — a probe is evidence for a decision, not a test |
| F | Correct item 4's imprecise description | ✅ **Done**, in the item 4 verdict below and in `CLAUDE.md` |
| G | Correct item 6's two overstatements | ✅ **Done**, in the item 6 verdict below; also written into `NewRole`'s javadoc, which is where the next reader of that guard will be |
| H | Item 5 is stronger than recorded — no setter | ✅ **Done.** Stated in `Role.describe`'s javadoc, in `RoleService.describe`, in the verdict below and in the IT |
| I | Box the latent eighth, and first answer whether its create path is dead | ✅ **Done, and the answer is below under Q1-b.** Boxed |

### 📋 The four work items

| # | Item | Verdict |
|---|---|---|
| **4+6** | one anti-pattern, fixed together | ✅ **Done.** Item 4: both system-record rules now enforced in `CustomerServiceImpl`, answering **422 with the full reason** where they answered `400 "Bad request."`. Item 6: `NewUser` and `NewRole` use `Required.field`. Item 4's part 2 landed with them |
| **4+6** | check whether anything else in `core-api` carries the same shape | ✅ **Done, and it found nothing more.** `SupplierView` and `AssetView` carry the same *kind* of invariant, but `SupplierServiceImpl.requireCoherentVatStatus` and `AssetServiceImpl.dispose` already refuse a caller with `InvalidSupplierException` / `InvalidAssetException` before the view is built — so those invariants are backstops, which is what `CustomerView`'s were supposed to be. **No instance 6** |
| **5** | change a role's description, narrowly | ✅ **Done, both legs.** `PATCH /api/roles/{id}/description`, `RoleService.describe`, `Role.describe`, an audit entry, and `editableRole` so a system role is still refused. Frontend: an ordinary `FieldEditor`, **both "there is no route" notes removed** along with the two now-false i18n strings in EN and EL. Driven end to end by `UserRoleEndpointIT.roleLifecycle` against the real server, including the clear-by-blank case and the system-role refusal |
| **1** | duplicate `operationId`, then delete the workaround | ✅ **Done, all three parts.** `InventoryController.writeOff` (POST) → `createWriteOff`; **`OpenApiSpecIT` now refuses** to write a duplicate rather than emitting an invalid document; `orval.config.ts` lines 59–83, 34 and 53–56 **deleted**, and `spec-hygiene.test.ts`'s duplicate assertion rewritten from pinning the defect to pinning the guarantee |
| **7** | box the seven booleans plus the eighth | ✅ **Done, and its regression is CLOSED by 8a (2026-08-03).** It carried a deliberate, measured regression for two days; the verdict below is kept in full because the *coupling it exposed* is the lasting part |
| **8** | — | ✅ **Explicitly out of Q1.** Design approved, build not scheduled this session; **nothing of 8a or 8b was written.** See decision C |

### ✅ Item 7's verdict in full — the regression is CLOSED, and the coupling it exposed is the lasting part

⚠️ **Status, 2026-08-03: closed by 8a on the same day it was opened's successor.** Schemas declaring
`required` went 78 → 75 when item 7 landed, and **75 → 143** when 8a did; all seven spec-visible
flags are asserted **by name** in `spec-hygiene.test.ts`, and the eighth
(`NewVatExemptionReason.inputVatDeductible`, which has no schema) is enforced in the backend by
`MandatoryDeclarationRulesTest`. **The compile-time catch is back and is now stronger than before**:
it was previously an accident of the field being primitive, and it is now a declaration that a
build-time rule keeps honest. The account below is kept as written because the *coupling* it
exposed outlives the fix.

**Measured, not reasoned.** Boxing a primitive changes what `OpenApiSchema` can see: it marks a
component `required` when `isPrimitive()`, and a boxed `Boolean` is not one. So the same edit that
turned `400 "Cannot map null into type boolean"` into `400 "serialTracked" is required and was not
supplied.` **removed the field from the contract's `required` list.** Schemas declaring `required`
went **78 → 75 (2026-08-03)**.

- **What is lost:** the compile-time catch. `tsc` no longer refuses a TypeScript caller that omits one
  of these eight flags.
- **What is not lost:** the refusal. The server still refuses an omitted flag, and now says which.
- **What closes it:** **8a**, which is why it is scheduled immediately after Q1 and before R1.
- **Why the exposure is acceptable:** the order is Q1 → 8a/8b → R1a → R1b → R2 → F5, and **F5–F8 are the
  steps that would send these bodies** — so no screen is written inside the window.
  `product-create.tsx` still sends `serialTracked` explicitly, and its comment was corrected: it had
  claimed omission was a compile error, which was true from 2026-08-01 until this change and is not
  true now.
- **Pinned in both directions** by `spec-hygiene.test.ts`, which now asserts `NewProduct.required`
  does **not** contain `serialTracked`, and tells whoever sees that change to delete the note and the
  explicit send.

⚠️ **This is the trade the queue's own reasoning did not see.** Item 7 was recorded as "not urgent —
`tsc` now refuses a TypeScript caller that omits one", which was true *because the field was
primitive*. Boxing it is what removes that property. **Items 7 and 8 were coupled and the queue
listed them as independent.**

### 📋 What Q1 raised — two new items, and where each went (settled 2026-08-03)

The queue is empty of its original numbered rows. **Neither of these becomes a free-floating queue
row**, because a queue of two items that both belong to scheduled steps is a third record of the same
work — which is the drift `PROGRESS.md` has already paid for twice.

| # | Where it went |
|---|---|
| **Q1-a** | ➕ **Folded into 8a.** Schema naming is a **generator** concern and **8a already regenerates the spec**, so scheduling it separately pays that regeneration twice. The generator should refuse a schema-name collision the way it now refuses a duplicate `operationId`. **Q1-a's client fallout lands in 8b** — renaming schemas renames generated TypeScript types, so it arrives in the same regeneration as the `@Mandatory` fixture work rather than in a second one |
| **Q1-b** | ⚖️ **Stays open, to decide with R1**, and is recorded as an open decision in the roadmap rather than as a task. R1 settles the seed-only pattern for document types; this is the same question one entity earlier |

**Both as originally raised:**

| # | Item | Raised |
|---|---|---|
| Q1-a | **Component schema names collide the same way `operationId` did, and nothing checks it.** `OpenApiSchema` registers a record under its **simple name**, so **seven** distinct `NameRequest` records across seven controllers resolve to **one** schema. They are structurally identical today, so the document is accidentally correct; the day one gains a field, six routes are described by the wrong shape with no warning. Closing it means renaming records or qualifying schema names across the surface, which regenerates the whole client — so it was **not** bolted onto item 1. ⚠️ **A new request record must not add an eighth**: `RoleController.RoleDescriptionRequest` is named apart for exactly this reason, and says so in its javadoc | 2026-08-03, while building item 5 |
| Q1-b | **`VatExemptionReasonService.create` has no production caller.** Answering decision I: the seed is **Flyway SQL** (`V5`, `V8`), not this method, and `/api/vat-exemption-reasons` is **GET-only**. Its only callers are 12 sites in `VatExemptionReasonIT`. So it is exercised but unreachable from outside the core. ⚠️ **Not deleted** — the document-model decisions make exemption reasons the seed-only model, and two AADE codes (24, 28) plus the OSS/IOSS myDATA codes are still open with the accountant, so a create path may yet be wanted. **Decide it with R1**, which settles the seed-only pattern for document types | 2026-08-03, answering decision I |

### 📊 The two counts, both kept — decision D

⚠️ **Neither overwrites the other, and the difference is a counting basis rather than a disagreement.**

| | Recorded before Q1 | Measured 2026-08-03 |
|---|---:|---:|
| Records guarding a reference-typed field in a compact constructor | **90** | **94** |
| …of which request bodies | **28** | **48** |
| Guarded components in total | — | **289** |
| …guarded by `Objects.requireNonNull` | — | **269** |
| …guarded by `Required.field` / `Required.text` | — | **20** |

**Basis of the 2026-08-03 figures:** a heuristic source scan of every record in `core-api`, `core` and
`app`, cross-referenced against the committed spec; it **includes** the four value types (`Money`,
`UnitCost`, `Quantity`, `Rate`) and **counts nested line records** (`NewCreditNoteLine`,
`NewPurchaseInvoiceLine`, …) as request-reachable. The earlier figures appear to count top-level
request bodies only. **Settle with an exact count when 8a is scoped** — and that count should come
from the generator's own reflection rather than from a regex, which is the same argument 8a's
ArchUnit cross-check rests on.

**The `requireNonNull` : `Required.*` ratio of 269 : 20 is the number worth carrying into 8a.** Most
of those 269 are on **response** records and are correct there — they assert *we always set this*,
which is our own invariant rather than a caller's obligation. They declare the same thing in a schema
and mean something different in the code, which is exactly the judgement 8a cannot automate away.

### ✅ The live browser leg — passed 2026-08-03, run by the owner

**After the app image was rebuilt.** The first attempt failed against a stale container; that is the
process finding below, not a defect. Four checks, all passed:

| # | Check | Result |
|---|---|---|
| 1 | Edit the description on role 3 and save | ✅ **Saved** — `200`, the new text rendered |
| 2 | Clear it to empty and save | ✅ **Cleared** to its unset placeholder, which is the blank-clears-it rule from the service |
| 3 | Role 1 (OWNER), a system role | ✅ Description editor **disabled with the system-role reason**, exactly as Name is — `editableRole` reaching the screen |
| 4 | Create a product | ✅ **Still works** — item 7's boxed `serialTracked` proved from a form rather than from a test |

⚠️ **Check 4 is the one worth keeping in view.** `serialTracked` is the field whose primitive form
broke product creation for every user, and item 7 changed its type. A contract test proves the server
accepts the body; only the form proves the form still sends it.

### ✅ Q1 is now FULLY CLOSED — the one thing it deliberately left open was shut by 8a

**Q1 was recorded as "built and live-verified, and deliberately not closed" for one day**, because
item 7's regression was outstanding and every document said so rather than showing a plain ✅. **8a
closed it on 2026-08-03** and the qualifier is removed everywhere.

⚠️ **The device worked and is worth reusing.** A step that reads as finished is a step nobody returns
to; stating the residual in the status marker itself — rather than in a paragraph underneath —
is what made the next session pick it up as an acceptance criterion instead of as background. **The
only thing still open from Q1 is Q1-b**, and it is deliberately a decision rather than a task: to be
settled with R1.

### 🔍 Process finding — a live leg was run against a container that did not contain the code

⚠️ **This is a PROCESS finding and explicitly NOT a defect. Nothing in the code was wrong.** It is
recorded because the workflow made it invisible, and because it will happen again unless the workflow
changes.

**What was observed.** The browser answered `404 "No static resource api/roles/3/description"` —
Spring's message when no handler matches and the request falls through to static resource resolution.
Not a validation failure and not a permission failure: *this path does not exist*.

**The evidence, gathered rather than assumed:**

| What | Finding |
|---|---|
| App image build time | **2026-08-02 07:50:40 UTC**. `/app/novocore.jar` dated the same |
| Q1's commit `2bc19ab` | **2026-08-03 12:57:56 +0300** — the image predated it by 26 hours, and predated `f143215` too, so the container was **two commits behind** |
| The old jar's compiled `RoleController` | **Eight** route templates, **no** `/api/roles/{id}/description`. Also `core/web/Required` and `core/web/InvalidRequestException` — the **pre-Q1** placement — and `InventoryController.writeOff` with no `createWriteOff`. **It contained none of Q1** |
| Handler enumeration at `2bc19ab` | A booted Spring context, `RequestMappingHandlerMapping` enumerated rather than the source read: **176 `/api/**` handlers**, including `PATCH /api/roles/{id}/description → RoleController.describe` |
| The generated client | `role.ts:368` — `{url: `/api/roles/${id}/description`, method: 'PATCH'}` |
| The committed spec | `PATCH /api/roles/{id}/description`, `operationId` `RoleController_describe`, `USERS_AND_ROLES`/`FULL` |

**The reconciliation: registration, client and spec all agreed. The lone disagreement was the
deployed artefact.** Not a method mismatch, not a path mismatch, not a spec-versus-implementation
gap.

**After a clean rebuild** (`build` + `up -d app`, ⚠️ **never `down -v`**): the startup line reads
**"176 handlers under /api/**"** where the old build would have said 175; the new jar's
`RoleController` carries **nine** templates; `core/web/Required` is **gone** and
`core.api.shared.Required` / `InvalidInputException` are **present**; `createWriteOff` exists.

#### The structural cause — and it is not "somebody forgot"

**The app image serves no frontend at all.** Zero static assets in the jar; Caddy proxies everything
to `app:8080`. The browser loads from the **Vite dev server**, which proxies `/api` through Caddy to
the app container. So the two halves have categorically different staleness behaviour:

> **The frontend recompiles from disk on every save. The backend changes only when somebody rebuilds
> an image. A current screen calling a stale API is the DEFAULT state of this stack after any backend
> commit** — not an unlucky one.

**The framing, now in `CLAUDE.md`: it is a sibling of *a verification that answers its own request*,
not a new species.** That one is a check whose subject was **stubbed**; this is a check whose subject
was **a different build**. Both reduce to one sentence — **the thing that answered was not the thing
under test** — and in both cases every individual observation was true. Neither makes the *identity*
of the answering thing visible on its own.

⚠️ **An anonymous probe cannot separate them here.** `PATCH` to the real route and to
`/api/roles/3/definitely-not-a-route` **both answer 401**, because Spring Security refuses before
dispatch — the same fact already recorded about `/v3/api-docs`. So "just curl it" is not a substitute.

#### The rule: REBUILD, unconditionally. Not a timestamp check

⚠️ **This overrides the recommendation made when the finding was reported**, which was a one-line
comparison of the image's creation time against `HEAD`. Two reasons, both recorded in `CLAUDE.md`:

- **The comparison is a heuristic, not a fact.** An image created *after* `HEAD` was not necessarily
  built *from* `HEAD`. It holds for one developer on one branch and **stops holding quietly** when
  that changes — a rebuild from a dirty tree, a branch switch, a second machine. A check that is
  right until it silently is not is worse than none, because it is trusted.
- **This occurrence produced a false FAILURE; the same condition produces a false PASS.** A stale
  image made a working route look missing — loud, and investigated within a day. Reverse it: a new
  commit breaks something the old image did correctly, the browser leg passes against the old image,
  and **nothing ever prompts a second look at a pass.** That is the direction that ships.

**One command, already in `docker/README.md`, no judgement call and no output to interpret:**

```
cd docker && docker compose -f compose.yml -f compose.dev.yml build app \
                          && docker compose -f compose.yml -f compose.dev.yml up -d app
```

⚠️ **`build` and `up -d app` ONLY — never `down -v`**, which also destroys the commissioned Google
Drive refresh tokens and the Owner account, neither reproducible from `docker/.env`.

#### 📌 The build-SHA badge — a future item with a NAMED trigger

⚠️ **Not "if it recurs". It will recur** — that is exactly what the structural cause establishes.
The unconditional rebuild removes the *cause*; the badge makes the *condition* visible when the rule
is somehow not followed.

**Attached to F10**, which touches the app shell anyway: record the git SHA into the jar
(`spring-boot:build-info` plus the commit id), expose it on an authenticated route, and show a badge
when the frontend's SHA and the backend's disagree. ⚠️ **Step 43 needs it regardless of F10** — once
anyone other than this business runs NovoCore, *"which version is that customer on?"* stops being a
convenience and becomes a support precondition. If F10 slips past 43, this moves rather than waits.

### 📋 What was proven against a real server rather than read

Per the newly named practice. A throwaway `Q1ProbeIT` booted the real application over real HTTP
against a real PostgreSQL, made **26 requests**, printed every status and body, and **was deleted**.

- **It corrected item 4's description.** A bare `EXEMPT` on the retail customer answers **422** from a
  *generic* rule applying to every customer; the retail rule is reached only when the body also
  carries `vatExemptionReasonId`. ⚠️ **A sweep case written from the item's own wording would have
  passed against the defect it existed for** — and the first draft of the new sweep case did exactly
  that, until the probe's finding was applied to it.
- **It corrected item 6's evidence.** `POST /api/users {}` fails on the primitive `roleId` before
  reaching `requireNonNull`, so `noRouteFailsOnAnEmptyBody` was weaker evidence for item 6 than the
  item claimed. `POST /api/roles {}` is the clean case, and `NewRole`'s javadoc now says so.
- **It corrected item 6's severity.** The `requireNonNull` messages were **not** discarded — they
  surfaced as `"Malformed request body: username"`. Wrong shape and wrong status class, and
  actionable all the same.
- **It corrected item 4's location.** The two rules are in **`CustomerView`**, a *response* record in
  `core-api`, not in the `Customer` domain entity — so the approved remedy ("swap the exception type")
  would have been wrong. A view is built only after the service accepted the change, so an incoherent
  view really is our bug and `IllegalArgumentException` is right for it. **What was missing was any
  check on the caller's path.** The invariants stayed; the rules are enforced in the service now.
- **It caught two order-dependent cases in the new sweep**, written against a shared probe customer
  that already carried a VAT number. They use a customer the test makes itself now. **A case that can
  pass for a reason it is not about is worse than no case.**

✅ **The live browser leg was the owner's, as always, and it passed on 2026-08-03** — see *The live
browser leg* above for the four checks. The Owner password is deliberately not in this repository.
⚠️ **The first attempt ran against a stale container**, which is the process finding above.

📌 **Recorded, not acted on: a dedicated non-owner test account** with credentials in a **gitignored
local env file**, so a live leg does not need the owner. **The trade-off is real and is why this is a
decision rather than a task:** a working credential on disk, against a hard rule that it exists
**only** on a development stack — and the moment that rule is bent it is a real account on a real
system. **It was not needed for 8a**, which had no browser leg and was verified by tests and `tsc`
alone. ⚠️ **8b is still the first step that might want
one**: it regenerates the client and reconciles fixtures across the whole suite, which is exactly the
shape of change whose breakage shows up in a browser rather than in a test. **Owner's call**, recorded
in the roadmap's open decisions.

## U1 — roadmap unification & documentation reconciliation — 2026-08-02. Reconciled against the approved scope

**`U1` is this session's step ID, and `U` is now a defined prefix**: *a session that changes
documentation and governance and produces no production code.* Future documentation and governance
sessions take **U2, U3 …** rather than entering the F/Q/R sequence — a doc-only session given a build
letter makes the build sequence read as further along than it is.

**Not a build step.** A unified roadmap written outside the repository was reconciled against the real
codebase and the running stack, then applied along with fourteen design decisions that existed only in
chat. **Every part below has a verdict; none is "still open".**

### 📋 Phase 0 — reconcile the supplied file (report, then stop)

| # | Sub-part | Verdict |
|---|---|---|
| 0.1 | Every 🟢 Done row against the repository; every Est./Actual/Out figure against the old roadmaps | **Done.** All 25 referenced commits resolve on the dates claimed. Phase 1 reproduces the old file exactly and all three subtotals add up. **Six discrepancies found**, all reported before any change and all corrected — see the table below |
| 0.2 | Every factual claim in Notes, confirmed or refuted | **Done.** Eight confirmed (four of them **live**, against the running database), one confirmed-and-understated, two refuted, one refused as unverifiable — see below |
| 0.3 | Anything real lost relative to the two files it replaces | **Done.** 15 items enumerated: **5 judged unacceptable and restored**, 4 carried as one-liners, 6 accepted because verified present elsewhere |
| 0.4 | Anything in the repository contradicting a decision in the new file | **Done.** Eight found, all reported |

### 📋 Phase 0's findings, and what was done with each

| Finding | Verdict |
|---|---|
| **D1** — `FND \| Frontend foundations + Products \| 0.7` attached a **measured** figure to work the old file says is **unmeasured** (`56e3726` is in the blank window) | **Fixed.** Row split: *Frontend foundations* keeps 0.7/183k; *Products screens* gets its own row with a blank Actual and footnote ᵖ giving the reason |
| **D2** — a measured **0.73 h / 232k out** (the brand pass, `d0ec9d9a`+`f4e4d84c`) had no home | **Fixed.** Its own Phase 2 row, footnote ᵛᵛ |
| **D3** — Phase 2's Est. subtotal read **8.0** while every row read `—` | **Fixed.** Subtotal is `—`, with a note that 8.0 was step 16's estimate and covers the F-rows only |
| **D4** — Phase 2 had no `Out` column, so six frontend figures were dropped | **Fixed.** Column added and populated: 183k / 221k / 259k / 359k / 216k / 605k |
| **D5** — the file claimed the `In` column and per-step token detail "live in `PROGRESS.md`". **They do not.** This file contains **zero** input-token figures and one per-step `Out` figure | **Fixed.** The full `In` column, the **2,413M** subtotal and the cache-reads warning box are carried into the unified file. ⚠️ **This one would have destroyed measured data on deletion** |
| **D6** — header said it replaces `novocore-roadmap.md`, the name it would be saved as | **Fixed** |
| **F1** — "five list column files" | **Fixed to seven**, here, in the primer and in `frontend/README.md`. F4 shipped VAT classes and units of measure with sorting; S2's own row stays historically accurate at five |
| **F2** — "five transitive vulnerabilities in the routing library" | **Refuted and replaced.** `npm audit` reports **4**: 2 high via `react-router`(`-dom`), 2 moderate via `@hono/node-server`/`@modelcontextprotocol/sdk`. Marked point-in-time with its date |
| **F3** — Q1's order (`1, 4+6, 5, 7, 8`) contradicted this file's `4+6, 5, 1, 7, 3, 8`, and "seven open" contradicted its own next sentence | **This file's order stands.** Corrected to **six open** and **4+6, 5, 1, 7, 8**. Item 8 **not** promoted — recorded as an open decision instead |
| **F4** — "AADE publishes no live API for codifications" | **Refused as unverifiable from here.** Recorded as stated, sourced to the design session and its 2026-08-02 check against AADE's published REST method list, explicitly **not** presented as confirmed |
| **F5** — "the current password lives only in the chat session that generated it" | **This file was wrong, not the supplied one.** The owner demonstrably holds it — they ran the S1/S2/F4 browser checks. Corrected here; the roadmap now records the real gap, which is **no change-password screen and no recovery path** |
| **C1** — `CreditNoteService.issue` / `SalesController_issue` violated the new naming rule | **Renamed, in this session.** ⚠️ **The rename is U1's work, not Q1's** — it came from this finding, on the committed surface, not from the backend follow-up queue. Filed under Q1 until 2026-08-02; see *U1 follow-up corrections* below |
| **C2** — "never issues, in any phase" contradicted step 40 | **Requalified**, in `CLAUDE.md`, the roadmap and the primer: Novocore never obtains a **ΜΑΡΚ**; step 40 changes only that it allocates the **series number** and composes the document |
| **C3** — "same treatment as VAT classes" | **Repointed at `VatExemptionReason`.** `POST /api/vat-classes` exists, so a user genuinely can author a VAT class — seeded **and** extensible, the wrong model for a seed-only list |
| **C4** — channel presented as new reference data that stops at the document | **Restated.** `SalesChannel` is an enum, `sales_invoice.channel` is `NOT NULL` with a CHECK, and step 3 split Sales *and* Sales-returns per channel — so channel **already reaches the ledger**. R1 references it, never creates it. The open decision was rewritten around scaling and the enum-vs-table question |
| **C5** — three documents said F5 was next after the queue had been reprioritised in chat | **Fixed** in this file (two places), the primer and the roadmap. It is the worked example for the new `CLAUDE.md` rule |
| **C7** — `docs/PROJECT_STATE_SUMMARY.md`, untracked | **Deleted, not repointed.** A snapshot duplicating this file and the roadmap; a second record that drifts is the failure that produced the item-3 disagreement. Regenerate on demand |
| **C8** — this file's search target list said "all 15 rows" over a 16-row table | **Fixed to 16**, matching the other two documents |

### 📋 What was verified live rather than read

Against the running Compose stack on 2026-08-02, because each is a claim about **the absence of
behaviour across the system** and no file can support one (`CLAUDE.md`, *a fact established by
reading*):

- **`el-GR-x-icu` never applied** — `datcollate=C`, `datctype=C`, `datlocprovider=c`; **0** user
  collations; **0** columns with a non-default collation; **0** indexes containing `COLLATE`; server
  17.10.
- **No product categories** — no category table, no category column on `product`.
- **No `code`/`alias`** on `supplier` or `customer`.
- **No document-number allocation** — **zero** non-identity sequences in the whole schema.
- **No document type or series tables**; R1 is genuinely unbuilt.
- **Paging** — exactly **3 of 175** operations accept `page`/`size`, and none of the five named
  services is among them.
- **No general integration outbox** — `backend/adapters` and `backend/modules` hold **zero** Java
  files; every `outbox` reference is email, backup or attachment.
- **Test counts** — a full `mvn clean verify` was run rather than trusting a recorded figure:
  **1,376 tests, 0 failures, 0 errors, 1 skipped, exit 0**. Frontend: **307 across 31 files**.

### 📋 Phases A–E, the applied changes

| # | Sub-part | Verdict |
|---|---|---|
| A | Replace `docs/novocore-roadmap.md` with the corrected unified file | **Done** |
| B | Delete `docs/novocore-frontend-roadmap.md`, no stub; repoint every reference | **Done.** Deleted. Repointed in `CLAUDE.md`, `docs/novocore-context-primer.md`, this file (6 sites), `frontend/src/pages/customers/customer-create.tsx`, `frontend/src/pages/customers/customer-detail.tsx`. Two mentions remain **deliberately** — the deletion notices in `CLAUDE.md` and the roadmap header |
| C1–C2 | Document model: never issues; records, never generates until step 40 | **Done** — `CLAUDE.md` (*The document model*), roadmap ʳ/ᵇᵇ, primer |
| C3 | Naming rule in `CLAUDE.md` | **Done**, and the one standing violation fixed in the same session rather than queued |
| C4 | ΜΑΡΚ/UID/QR/status are core fields | **Done — ADR 0016**, a new ADR rather than an amendment, because the reasoning (*would it survive the vendor being replaced?*) is worth stating once and citing |
| C5 | Behaviour varies by myDATA type; types are seed-only | **Done**, with the analogy corrected to `VatExemptionReason` |
| C6 | Known limitation: stock incomplete until a dispatch document exists, and queryable | **Done** — `CLAUDE.md`, roadmap, primer, `frontend/README.md` |
| C7 | `Στοιχείο Αυτοπαράδοσης` | **Done.** Accounts explicitly refused rather than guessed |
| C8 | Document transformation in one action | **Done** |
| C9 | AADE publishes no live codification API; alert, never auto-apply | **Done**, sourced rather than asserted |
| C10 | Live AADE services that are adapter-shaped | **Done** — roadmap ᵃᵈ, step 28 |
| C11 | The fourth non-negotiable has no implementation | **Done** — recorded as a real gap in `CLAUDE.md`, roadmap X1 and the primer, with the live evidence |
| C12 | Adapter ID-mapping lifecycle | **Done**, alongside C11 as the same design item |
| C13 | Product categories: 3 levels, multi-membership | **Done** |
| C14 | Channel is a real field, not propagated to journal lines | **Done, but restated** — see C4 in the findings table |
| D | `CLAUDE.md`: a design conversation gets close-out discipline | **Done**, with the four missing core-model decisions and the stale "F5 is next" recorded as what it cost |
| E | Backend queue item 3 reconciliation | **Done.** Closed as stale, in the summary table, the order list and its own section |

### ⚠️ The finding that is worth more than the fixes

**The F2a row — the deferred customer VAT class override — had been dropped from the unified file, and
that drop is itself an instance of the failure `CLAUDE.md` §"An approved proposal is a checklist"
exists to prevent.** A deliberately deferred sub-part, with a test asserting the field's absence and no
owner placement, lost the only row in the project that tracked it — **in a file whose own notes explain
why that must not happen.** Nothing would have failed. It was found by enumerating what the new file
lost against the two it replaced, which is the same reconciliation move that found 15c.

**The general shape, now stated in `CLAUDE.md`:** condensing is a rewrite, and a rewrite loses whatever
nobody explicitly checks for. **A summary of what a file contains cannot see what it dropped** — only a
comparison against the file it replaced can.

## U1 follow-up corrections — 2026-08-02. Six items from the close-out review, all with verdicts

**Documentation and governance only; no production code was written or changed.** The list was
approved as six items and is reconciled here one line per item, per `CLAUDE.md` §*An approved proposal
is a checklist, not a paragraph*.

| # | Correction | Verdict |
|---|---|---|
| 1 | **`U` approved as a step-ID prefix, with a definition** — *a session that changes documentation and governance and produces no production code*; future doc/governance sessions take U2, U3 … rather than entering the F/Q/R sequence | ✅ **Done.** Recorded in the roadmap's ID-convention note and at the head of the U1 section above |
| 2 | **Reattribute the credit-note rename from Q1 to U1**, everywhere both are mentioned; Q1 returns to 🔴 Not started, five items, working order 4+6, 5, 1, 7, 8; remove any wording implying the queue has partial progress | ✅ **Done.** Roadmap ᵘ¹/ᵘ + the Q1 row + the note under the Phase 2 table; this file's *What is next* table, finding C1, the queue header, the order list, and the paragraph that used to carry the rename. Sites checked by grep in both directions, not by memory |
| 3 | **Second subtotal row on Phase 2** — *Subtotal, F-rows (step 16 estimate) · 8.0* above the phase subtotal, which stays `—` | ✅ **Done.** The existing note was correct and stayed; the row exists because a reader scanning a column of dashes never reaches a note below the table |
| 4 | **Rule into `CLAUDE.md`** — every figure carries a date or a step reference; a bare number is correct in the paragraph that wrote it and wrong the moment somebody lifts it out | ✅ **Done**, as its own subsection under the documentation-discipline cluster. ⚠️ **`PROGRESS.md` was deliberately NOT swept for existing violations** — that is U2's, and the rule says so rather than implying the sweep happened. **One line was fixed rather than swept:** this file's own headline count now reads *"Tests, measured 2026-08-02"*, because the U2 note asserts the headline figures are date-stamped and it was the one that was not |
| 5 | **Record U2 as a scheduled future task, do not start it** | ✅ **Done.** ⚪ Unscheduled row in the roadmap with footnote ᵘ², and the section below. **Not started, and nothing was moved out of this file** |
| 6 | **Move F2a out of Phase 2 and attach it to step 18**, keeping 🔴 and the test asserting the field's absence; record the three-part reasoning, the step-18 verification item, and the precedence open decision | ✅ **Done.** Row removed from Phase 2, sub-item added under step 18, footnote ᶠ²ᵃ rewritten, verification item and open decision recorded. **No production code touched; the test is untouched** |

**Constraints, each confirmed rather than assumed:** no `Est.` figure changed, no `Est.` overwritten by
an `Actual`, no blank `Actual` filled; no ⚪ item promoted or reordered; **U2 added as ⚪ is a new row,
not a promotion**; no production code in the diff.

### 📌 U2 — split `PROGRESS.md`. ⚪ Unscheduled, recorded and deliberately not started

**This file is ~6,000 lines, append-only, and the first file every session reads.** It contains
per-step route counts and test counts that are correct in their own context and wrong lifted out;
**the headline ones were date-stamped during U1, the rest were not swept — and that is stated here
rather than claimed as done.**

**The shape.** `PROGRESS.md` becomes **short and always-current** — state, next step, open items.
Everything historical moves to **`HISTORY.md`**, append-only and **explicitly not authoritative for
current state**.

**The reason is not length.** A document that is only ever appended to **cannot stay true**, and the
backend-queue-item-3 disagreement already cost a session: two records of one fact disagreed, and the
one a fresh session reads first was the stale one.

### 📌 F2a — the customer VAT class override is adapter-dependent work, and now sits under step 18

**It sat as a sub-row under F2 until 2026-08-02, which read as a small leftover screen task somebody
could pick up on a quiet afternoon.** It is not. **The deferral reason recorded to date — permission
gating — is no longer the main one.** Three reasons, in the order they matter:

- **(a) Permission gating — the original, still open.** A control that changes what VAT a customer is
  charged needs its section and level worked through (`TAX_AND_CHARGES` was the candidate).
- **(b) Precedence, which got harder after the deferral.** Three inputs, no stated priority: the
  **product's own VAT class**, the **island reduced-rate mapping** (seeded since `V5`, and confirmed
  in use — Java Jives ships to reduced-VAT islands), and the **customer override**. Which wins when a
  customer holding an override buys a product with its own class and ships to an island is a
  **statutory question, not a UI decision.**
- **(c) The decisive one — the rule must live in Go too.** Go prices and issues the documents;
  Novocore records them and **recomputes net/VAT/total independently from the line items to compare
  against the source document**. An override set only in Novocore would make that comparison disagree
  with **every invoice for that customer** — not by a rounding residual but by a **whole VAT class**,
  which the mechanism flags as a probable data-entry error. That is a control that **manufactures
  false alarms**. If Go carries the equivalent setting instead, the same business rule lives in two
  systems with no sync — the disconnected-data problem Novocore exists to end. **Either way the
  adapter is where one system can own the rule.**

🎯 **Verification item for whoever builds step 18 — answer it against the running system, NOT by
reading one file:**

> **Does recording a sales invoice recompute VAT from the customer and the product, or store what the
> source document states?** The answer decides whether the customer VAT class override is a small
> screen or adapter-dependent work.

⚠️ **This was reasoned from the design record, not from the code** — which is `CLAUDE.md` §*a fact
established by reading, then built upon* in its live form. The conclusion in (c) is a claim about what
the system does, and no file supports one; hence an item to execute rather than a fact to build on.

**Not built, and the tests holding it are untouched.** There are **two**, both in
`frontend/src/pages/customers/customers.test.tsx`, and they were read rather than assumed:
*"does not offer the VAT class override, which is deferred"* asserts nothing matching `/VAT class/i`
renders on the **detail screen**, and *"sends what was filled in, and no VAT class override"* asserts
the **create body** has no `vatClassOverrideId` key. **Both stay and must not be weakened while this is
unbuilt** — they are what make adding the field a deliberate act with tests to update.

**Also recorded as an open decision** (roadmap, *awaiting the external accountant*): precedence between
product VAT class, island reduced-rate mapping and customer VAT class override. ⚠️ **Needed for the
island rates regardless of whether the override is ever built** — two of the three inputs already
exist and are in use.

## Step F4 — **Settings**. Approved 2026-08-01, checklist written at approval

**The approved checklist**, written down at the moment of approval per `CLAUDE.md` and reconciled
against at close-out. **Four decisions were taken before any code**, and three of them corrected a
precondition this step was scoped from — recorded below the table.

| # | Sub-part | Verdict |
|---|---|---|
| **Nav** | | |
| 1 | **Drop `settings.general` from the nav tree** — no catalogue key lands on it | ✅ **Done** — removed from `nav/tree.ts` with the reason in place, and both locale files |
| **Settings block — `SETTINGS` grant, 18 keys** | | |
| 2 | One shared **typed** settings-field mechanism, per catalogue type, not three copies | ✅ **Done** — `settings-catalogue.ts` + `setting-row.tsx`, built on `FieldEditor`. One request per field, no batching |
| 3 | `/settings/documents` — **Documents & Rounding**, 4 keys | ✅ **Done** |
| 4 | `/settings/email` — **Email / SMTP**, 12 keys (8 `smtp.*` + 4 dispatch) | ✅ **Done** |
| 5 | `/settings/retention` — **Retention**, 2 keys | ✅ **Done.** ⚠️ Nothing here governs **backup** retention — that is `backup.retention.*`, which has no route |
| 6 | `cash.payment.limit` renders with **no edit affordance at all**, with the statutory reason | ✅ **Done**, and asserted from both ends — the screen renders no button, and `F4WriteContractIT` has the real server refuse the `PUT` |
| 7 | `smtp.password` is **write-only**: configured/never-configured, set-new-value, never a value | ✅ **Done** — the two states are told apart by an empty value with no timestamps. A test asserts the redaction marker never reaches the screen either |
| 8 | The block survives a **403** — `SETTINGS` is default-deny and no role holds it by grant | ✅ **Done** — `Refusal` on the query, tested |
| **Reference data** | | |
| 9 | `/settings/vat-classes` — list, add, deactivate, reactivate, **edit description** | ✅ **Done** — list, detail, create |
| 10 | Rate and code render **read-only with no affordance** — not disabled | ✅ **Done.** A fourth unavailability state, now written up in `frontend/README.md` — *no route exists* is not `editable: false` and must not be a disabled control |
| 11 | **Reduced-counterpart management (`PUT`/`DELETE`) deliberately deferred**, with a test asserting its absence | ✅ **Deferred as approved**, and the test exists. The mapping is *shown* read-only, as the counterpart's code rather than its id |
| 12 | `/settings/units-of-measure` — list, add, deactivate, reactivate, rename, fractional toggle, myDATA code | ✅ **Done.** ⚠️ There is **no `GET /api/units-of-measure/{id}`**, so the detail page finds its row in the unfiltered list |
| 13 | `mydataCode` is **settable once then frozen** → `lockedReason`, shown disabled with the reason | ✅ **Done** — the first `lockedReason` that is not about one special record, and the server's refusal of a second write is asserted |
| 14 | The create form **always sends `fractionalQuantityAllowed`** — ~~a primitive with no backend guard~~ | ✅ **Done, and the premise was wrong.** See the correction below — the backend *does* refuse an omission. The required-choice design stands on a different and better argument |
| 15 | `GET /api/units-of-measure/without-mydata-code` surfaced as a **standing to-do**, not a debug panel | ✅ **Done** — a banner above the list. ⚠️ **All 8 seeded units were in it at F4; R1a made it 4** — annex 8.13 supplied the four certain mappings and left GRAM, MILLILITRE, SET and PACK genuinely open. The banner is shorter and still correct |
| **Inherited habits — S1 and S2** | | |
| 16 | **Search**, adopting target-list rows 6 and 7: `?search=` on both routes, migration **V30**, `SearchFilter` on both screens | ✅ **Done** — V30 with 4 GIN trigram indexes, `search()` on both services, `?search=` on both routes, `SearchFilter` on both screens. Live-verified: V30 is applied on the running stack |
| 17 | **Sorting** — sortable columns on both reference lists, S2's collator | ✅ **Done** — every column except the composite flags ones. The rate sorts with `compareDecimal`, not as text |
| 18 | Every screen test carries **"rendering sends no write"**; every mutation carries `<Refusal>` | ✅ **Done** — 6 such assertions across the three new test files |
| **Verification** | | |
| 19 | **Every write confirmed against the real running backend**, not the mock server | ✅ **Done, both legs.** *Contract:* `F4WriteContractIT` — 15 tests, real Boot server, real PostgreSQL, sending the screens' literal JSON — and **it found a wrong premise on its first run** (sub-part 14). *Browser:* **the owner ran it personally on 2026-08-01** against the running stack, as for S1 and S2. Nothing about F4's verification is outstanding |
| **Corrections found before building** | | |
| 20 | Correct this file's **"no island class is seeded"** claim, and record applicability as **decided** | ✅ **Done** — recorded above at F4's kickoff. Java Jives ships to reduced-VAT islands, so the seeded chain is applicable data |
| 21 | Record the **`el-GR-x-icu` collation answer** — never applied as a Postgres collation | ✅ **Done** — measured five ways against the running stack, written up above. ⚠️ **Recording it is what was in scope; it is NOT resolved.** It stays an open obligation — see the standing item below |
| **Added during the step** — per `CLAUDE.md`, a finding gets a row rather than a paragraph | | |
| 22 | Fix **`SettingType`'s javadoc naming a transport-security constant that does not exist** | ✅ **Done** — the javadoc said `TLS`; `EmailTransportSecurity` has `IMPLICIT_TLS`. Corrected in `core-api`, and pinned from both ends: `settings-catalogue.test.ts` asserts `TLS` is not offered, and `F4WriteContractIT` has the real server accept `IMPLICIT_TLS` and refuse `TLS` |

### ⚠️ F4's first finding — the real backend corrected a premise the step was built on

**Found on the first run of `F4WriteContractIT`, and it is the whole argument for that file
existing.** The step was scoped believing that `NewUnitOfMeasure.fractionalQuantityAllowed` — a
primitive `boolean` — could be **omitted silently**: that Jackson would deserialise the absent field
to `false`, creating a unit that forbids fractional quantities without anybody having chosen that,
with no guard anywhere. That belief came from reading the record's compact constructor, which
null-checks `code` and `name` and nothing else. It was reasonable and it was **wrong**.

**The server answers `400`.** An absent creator property reaches the canonical constructor as `null`
and `FAIL_ON_NULL_FOR_PRIMITIVES` — on in this application — refuses the body before any handler
runs. It is the same mechanism that broke product creation through `NewProduct.serialTracked`, and
the reason step 15's item-2 sweep now marks primitives required in the spec. **The guard exists; it
is just not in the constructor.**

⚠️ **The correction makes the screen's design better-argued rather than wrong.** The create form
still makes the fractional choice **required** rather than offering a checkbox — but for a reason the
server cannot enforce and only a screen can:

| | Sent | Server |
|---|---|---|
| field omitted | — | **`400`**, naming no field |
| unticked checkbox | `false` | **`201`** — a decision nobody made |

**The second row is the one that needed a design answer**, and it is the row the original premise
obscured. Both are now asserted against the real server (`omittingThePrimitiveIsRefused`,
`anExplicitFalseIsAccepted`), and the wrong claim has been removed from three places it had already
been written into: the create form's javadoc, its screen test, and `frontend/README.md`.

**What this says about method, which is worth more than the fix:** the claim was carried from a
careful reading of the source into three files before anything executed it. Reading is not running.

### ⚠️ F4's second finding — a javadoc named a constant that does not exist

`SettingType.TRANSPORT_SECURITY`'s javadoc listed the accepted values as *"`NONE`, `STARTTLS` or
`TLS`"*. **There is no `TLS` constant** — `EmailTransportSecurity` has `IMPLICIT_TLS`, `STARTTLS` and
`NONE`, and `IMPLICIT_TLS` is what the live stack runs on port 465.

**Nothing in either repository could have caught it.** A setting's value is an opaque `string` in the
OpenAPI document, so no enum is generated, `enum-labels.test.ts` cannot see it, and the frontend has
to mirror the list by hand — from the prose, if nobody checks. The refusal message is built from
`values()` and was always correct; only the sentence describing it was wrong. A select built from
that sentence offers an option every save refuses.

Fixed in `SettingType`, and now pinned from both ends: `settings-catalogue.test.ts` asserts the list
and explicitly that `TLS` is not in it, and `F4WriteContractIT` has **the real server** accept
`IMPLICIT_TLS` and refuse `TLS`.

### The four decisions taken at F4's kickoff, and why three of them were corrections

**1. `settings.general` is dropped.** The nav declared four settings pages; the 18 catalogued keys
distribute 4 + 12 + 2 across the other three, leaving General with **zero**. A menu item with nothing
on it is exactly the permanent placeholder step 16b existed to remove.

**2. VAT Classes are not "add and deactivate only" — that precondition was wrong.** Seven routes
exist: create, get, deactivate, **reactivate**, **`PATCH …/description`**, and **`PUT`/`DELETE
…/reduced-counterpart`**. ⚠️ **The rate and the code genuinely have no route** and never will —
confirmed three ways (controller javadoc, service interface, entity). **Decided: F4 builds add /
deactivate / reactivate / edit description.** Reduced-counterpart management is deferred — it carries
statutory weight and is its own decision. **There is no ΕΛΠ/myDATA field on a VAT class at all**; the
`code` *is* the Prosvasis Go code and is the identity. myDATA codes live on `unit_of_measure` and
`vat_exemption_reason`, which are different tables.

**3. ⚠️ The island rates were already seeded, and this file said they were not.** `V5` seeds **nine**
VAT classes carrying the ΑΑΔΕ codes — `0`, `1030`, `1040`, `1041`, `1060`, `1091`, `1131`, `1170`,
`1410` — and the reduced-counterpart chain is **already populated**: 24→17, 13→9, 6→4 *(to `1041`,
the art.31 variant, not `1040`)*, 4→3. The claim recorded here that "no island class is seeded
pending the applicability decision" was **factually wrong about the live database**, and was found by
reading the table rather than the doc.

**Applicability is now decided, not open: Java Jives ships to reduced-VAT islands, so these rates are
genuinely in use.** The mapping is intentional and applicable data, not incidental seed. ⚠️ **That
does not un-defer sub-part 11** — the rates being applicable is a different question from whether a
screen should manage the mapping, and the second is still the owner's to place.

⚠️ **Never key a VAT class off its rate.** Nine rows, **eight distinct percentages** — 4% appears
twice, as `1040` and as `1041` under a different legal basis. `VatClassService` deliberately offers no
`findByRate`, and a rate-based picker would be correct most of the time, which is the worst outcome
available.

**4. F3's acceptance pass came back passed**, so F4 starts clean. All seven checks confirmed by the
owner on 2026-08-01. The gate is closed.

### ✅ The collation question, answered before building — `el-GR-x-icu` was never applied

**Asked at F4's kickoff because S2's write-up could be read either way, and it needed a real answer on
record rather than staying ambiguous. It is not a defect — S2 recorded the decision correctly — but
the wording made it easy to conclude the database had been changed. It has not been.**

**Measured against the running stack**, five ways, every one negative:

| Check | Result |
|---|---|
| Database default collation | **`C`**, `datlocprovider = c` — libc, unchanged |
| User-created collations in `public` | **none** — no `CREATE COLLATION` was ever run |
| Columns with a non-default collation | **none** |
| Indexes whose definition contains `COLLATE` | **none** |
| `el-GR-x-icu` in any migration or Java source | **zero occurrences** — it appears only in docs, in `collation.ts` comments, and in `collation.test.ts` as a *pinned expectation string* |

And the ordering half is unchanged in code: every list endpoint is still a plain
`findAllByOrderByNameAsc()` / `…SkuAsc()` with no `COLLATE` clause, so **live `ORDER BY` is still byte
order under `C`** — exactly what S2 measured and reported.

**So `el-GR-x-icu` was used for exactly two things, and neither is a schema change:** as an *ad-hoc
`psql` measurement* establishing what output the client collator must match, and as the *documented
target* for whenever server-side sorting lands. `Intl.Collator('el')` is the only collator running in
production today. This is consistent with S2's own **"Decision: no collation index now"** — the
answer was always recorded, just not in a form that survived being re-read.

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

---

## Step S2 — **sortable columns** on the five list screens. Approved 2026-08-01

**The approved checklist**, written down at approval per `CLAUDE.md` and reconciled against here.

| # | Sub-part | Verdict |
|---|---|---|
| 1 | **Settle the collation question first, before any sorting code** | ✅ **Done, and answered from the live database rather than from reasoning.** See below |
| 2 | One shared ordering mechanism, not per-screen comparators | ✅ **Done** — `src/lib/collation.ts`: `compareText`, `compareDecimal`, `compareMoney`. Nothing else in the app compares text |
| 3 | Sortable columns on Products, Suppliers, Customers, Users, Roles | ✅ **Done** — every column except the composite Flags/Grants ones, which say in code why they cannot be ordered |
| 4 | Use the existing `DataTable` abstraction and the tier-A paging groundwork | ✅ **Done** — `DataTable` + `useListState`; the handle gained `serverSorts`, nothing else changed shape |
| 5 | State which side sorts, and why | ✅ **Client-side.** All five endpoints return their rows whole, so a browser sort sorts the *list*, not a page. Backend sorting for these five is not built and is not the queued tier-A item either — that item covers five *other* services |
| 6 | Confirm against the real running backend | ✅ **Done, all four legs** — the fourth (a browser against the live stack) was run by the owner on 2026-08-01. See below |

### S2's first question, answered — what `ORDER BY` does today, and what fixes it

Measured on the running stack (PostgreSQL **17.10**, `datcollate = C`, `datlocprovider = c`), not
inferred. **This is live behaviour, not a risk sorting would have introduced**: all five list
endpoints already order in the database — `findAllByOrderByNameAsc()` on Customers, Suppliers and
Roles, `findAllByOrderBySkuAsc()` on Products, and `Sort.by(asc("name"))` in S1's search paths.

**`ORDER BY name` is byte order.** Latin: every uppercase ASCII word before every lowercase one
(`Zebra` < `apple`), and accented Latin above all of ASCII so it lands *after* everything
(`Ácme` after `zebra`). Greek: the whole block sits above Latin-1, so **every Greek name sorts after
every Latin name**; inside Greek, capitals precede lowercase (`Ωμέγα` < `αθήνα`), and precomposed
accented capitals sit *below* their plain forms (`Ά` U+0386 < `Α` U+0391, so `Άλφα` < `Αθήνα`).

⚠️ **`pg_c_utf8` does NOT fix ordering, and it is the obvious thing to reach for.** S1 introduced
`lower(… COLLATE pg_c_utf8)` so Greek capitals fold, which makes it very easy to conclude the
collation question is settled. It is not: `pg_c_utf8` changes **case mapping**, not **sort order**,
and its `ORDER BY` output is character-for-character identical to `C`'s. Verified side by side.

**The fix is ICU**, which is present and complete on `postgres:17-alpine` — `icu-libs 78.1` with
`icu-data-full`, 908 collations registered. **Decided 2026-08-01: `el-GR-x-icu`, Greek block first,
fixed.** Not `und-x-icu` (Latin first) and **not** following the account's language — a list whose
row order changes when somebody switches UI language is worse than one that does not. These are a
Greek company's records read by Greek operators.

**The two halves were checked against each other rather than assumed.** PostgreSQL 17.10 / ICU 78.1
under `el-GR-x-icu` and Node 24 / ICU 78.3 under `Intl.Collator('el')` return **byte-identical**
orderings of a 16-string mixed sample. That sample and PostgreSQL's exact output are pinned in
`collation.test.ts`, so the frontend carries a record of what the database will do and a change that
looks harmless fails loudly.

⚠️ **On `CLAUDE.md`'s rejection of ICU** — that was about an *indexed expression*
(`novocore_searchable`, `IMMUTABLE`, 17 GIN indexes on it), where a meaning that shifts on upgrade
would be silently wrong. **`ORDER BY … COLLATE` with no index carries none of that**: the sort is
computed per query, and an ICU bump reorders edge cases cosmetically. The risk returns only with a
btree index on `(name COLLATE "el-GR-x-icu")` — and even then PostgreSQL records `collversion`
(`153.136.48` here) and **warns loudly**, the opposite of the silent failure the rule was written
against. **Decision: no collation index now.** Add one when a table gets large, and note the
`REINDEX` obligation next to the existing trigram one.

⚠️ **Numeric ordering is deliberately off.** `{ numeric: true }` would put `TEST-PRODUCT-2` before
`TEST-PRODUCT-10`, which is what a person wants — but stock `el-GR-x-icu` does not do it, so
enabling it would buy niceness at the cost of the two halves disagreeing. Matching it server-side
needs `CREATE COLLATION … locale = 'el-GR-u-kn-true'`, a backend decision and a migration.

### What S2 verified against the running stack — all four legs, the last by the owner

1. ✅ **The collation behaviour above** — every claim measured against the live PostgreSQL.
2. ✅ **The premise that these five do not sort on the server** — answered by the **running
   container's own bytecode**, not by the committed spec, because the image was built seven minutes
   *before* the last backend commit and PROGRESS.md already records what a stale container costs.
   The constant pools of the deployed controllers read: Customer/Product/Supplier/User/Role →
   `active search`; Journal/Sales → `page size sort direction`. **The check discriminates**, which
   is what makes it evidence.
3. ✅ **The real rows, through the shipped comparator** — the live customer list moves
   `Πελάτης Λιανικής` from last to first, which is the whole point.
4. ✅ **A browser clicking a header against the live stack — DONE.** It needed the Owner password,
   which is deliberately not in this repo, so **the owner ran it personally on 2026-08-01**, together
   with F4's. Frontend tests cover the wiring; none of them is evidence that the screen works in a
   browser, per the standing rule — this is that evidence. **Nothing about S2 is outstanding.**

### ⚠️ S2's finding — the first sort direction depended on which row was at the top

Found by a test, and it is not cosmetic. Left alone, TanStack chooses a column's first sort
direction with `getAutoSortDir()`, which reads **the value in row zero**: a string starts ascending,
anything else starts descending. So the direction of a user's first click depended on which record
happened to be first at that moment, and a column whose first row was empty would flip direction
when the data changed — with no rule anybody could infer from the screen. The header's accessible
label follows the direction, so the control would announce a different action from one load to the
next. Fixed with a table-level `sortDescFirst: false`; a test names all three columns.

### What already existed, and still does — for whoever adds *server-side* sorting

- **A server-side sorting contract exists and is proven on sales invoices**: a `…Sort` enum in
  `core-api`, `PageRequest`/`PageResponse`, `SpringPaging.pageableFor` mapping a *logical* name to an
  entity property, and `Paging.of` taking the enum at the route boundary so **no caller-supplied
  string ever reaches a query**. ⚠️ **Every ordering ends with the id** — a sort on a non-unique
  column leaves rows tied and PostgreSQL may return ties in a different order per query, so
  successive pages could show one row twice and skip another.
- **The frontend reads it**: `useListState` has `setSort` and now `serverSorts`, both from the
  generated capability map — so a screen starts sorting on the server the moment the backend
  declares it, with no component change.
- ⚠️ **`DataTable` now refuses to client-sort a server-paged list**, and this is the guard to know
  about: sorting one page of many and presenting it as the order of the whole table is convincing
  and wrong. A column on a server-paged endpoint is sortable **only** if it carries a `meta.sortKey`
  the endpoint declares; otherwise its header renders as plain text. **None of the *seven* column
  files carries a `sortKey` yet**, because no backend enum exists to name — so the day one of these
  gains paging, its sort controls *disappear* until somebody adds the keys. That is the safe failure
  and it is loud, but it is a real obligation and it is written here rather than left to be discovered.
  ⚠️ **Seven, not five, as of 2026-08-02**: S2 shipped against five screens and that row stays
  historically accurate, but **F4 then shipped VAT classes and units of measure with sorting too**, so
  the obligation is two files larger than the step that created it. Counted from
  `frontend/src/pages/*/*-columns.tsx`.
- **The queued tier-A paging item is adjacent work, not this work.** Five *different* services
  (purchase invoices, goods receipts, settlements, inventory, email outbox) are listed further down
  with their sort enums and **four checks that were expensive to learn**.

**Where things stand, for F4:**

- **F0–F4, S1 and S2 are done.** Products, Suppliers, Customers, Users & Roles, then substring search
  across all five, then S2 (sorting) and F4 (Settings). **307 frontend tests, 31 files; 1376 backend
  tests, `mvn clean verify` exit 0.**
- ✅ **S1 (search) is COMPLETE and LIVE-VERIFIED — nothing about it is outstanding.** The database
  half was proven directly during the step; the HTTP half needed the Owner password, and **the owner
  ran it personally on 2026-08-01**, confirming `/api/products?search=kit` and
  `/api/customers?search=πελατησ` both return correct results on the running stack. There is no
  pending check, no partial verdict and nothing to re-test.
- ⚠️ **F4 inherits S1's habit, not just its component.** Any new list screen gets `SearchFilter` and
  a `?search=` parameter built the same way; the backend side is one line in the service plus one
  index in a new migration. `TextSearch` was written against exactly that criterion, and the
  **16-row search target list below is where a screen reads its own fields from** rather than
  re-deriving a narrower set.
- ⏳ **First, check whether the owner's manual acceptance pass on F3 came back.** Seven checks were
  agreed at F3's close-out and are listed in the F3 section below. **Anything they turn up is an F3
  defect and is fixed inside F3 before F4 work starts** — the pass is the gate, not a formality
  running alongside.
- **F4 is Settings** — general config, Reference Data (VAT classes, units of measure), and the
  Adapters/Modules grids, which are already built as read-only placeholders.
- **Nothing in the backend queue blocks it.** Item 2 is done, so the contract now declares which
  body fields are mandatory; F4 touches **exactly one** primitive body — `POST /api/units-of-measure`
  (`fractionalQuantityAllowed`) — and `tsc` will now refuse a form that omits it.
- ⚠️ **`SettingsCatalog` is an allowlist, not a view of the table**, and the whole `backup.*`
  namespace has no route at all. A settings screen that expects to list everything will be wrong
  about what exists. **`cash.payment.limit` is readable and deliberately never writable** —
  statutory, and the catalogue entry says why, so its missing write route is not an oversight to
  "fix".
- ⚠️ **There is deliberately no route to change a VAT rate**, and its absence is asserted across
  three plausible paths: editing one would retroactively change what every invoice already issued
  under that class appears to have charged. A rate change is a **new class plus a deactivation**.
- 📌 **VAT Classes — how F4 sources them. Decided 2026-08-01, recorded here before the screen
  exists so it is not re-opened while building it.**
  - **No Go-adapter dependency.** The classes are **seeded directly** with the current Greek
    statutory rates — **24% / 13% / 6%** — rather than imported from Prosvasis Go. F4 does not wait
    on, and must not be designed around, an adapter that is phases away.
  - ~~⏳ **Island-rate applicability is NOT settled**… Until that is answered, no island class is
    seeded.~~ ✅ **BOTH HALVES OF THIS WERE WRONG, and F4 corrected them by reading the table.**
    - **The island classes were already seeded**, and had been since **V5** — `1170` (17%), `1091`
      (9%), `1041` (4%, αρ.31) and `1030` (3%) — with the reduced-counterpart chain **already
      populated**: 24→17, 13→9, 6→4 *(to `1041`, not `1040`)*, 4→3.
    - **Applicability is decided, not open.** Confirmed by the owner 2026-08-01: **Java Jives ships
      to reduced-VAT islands**, so these rates are genuinely in use and the mapping is intentional
      data rather than incidental seed.
    - ⚠️ This does **not** un-defer the mapping-management UI. Applicability being resolved is a
      different question from whether a screen should edit the mapping, and the second is still the
      owner's to place — see F4 sub-part 11.
  - **Adding a class is always available.** The seed is a starting point, not a closed list.
  - **An existing class's rate can never be modified after creation** — the bullet above is the
    mechanism, this is the policy it enforces, and they are the same decision stated from both ends.
    A rate change is **a new class plus a deactivation of the old one**, because editing a rate in
    place would retroactively change what already-issued invoices appear to have charged. The screen
    must therefore offer *"add a class"* and *"deactivate a class"* and must **not** offer a rate
    field on an existing one — not even disabled, since a disabled field invites somebody to look
    for the permission that unlocks it.
- **The standing rule applies to F4's create forms**: a screen test over the mock server cannot tell
  you a write works. Prove creation against the real backend — see F3's write-up for the pattern,
  including how to get a refusal from the server without writing anything.

---

## Step S1 — **substring search** (`pg_trgm` + `unaccent`). Approved 2026-08-01, standalone, not folded into F4

**✅ CLOSED OUT 2026-08-01. Every sub-part has a verdict, and none is "still open."**

**The approved checklist**, written down at the moment of approval per `CLAUDE.md`, and reconciled
against here rather than against memory of what was built. Rows 13–15 were **added during the step**
— 13 and 14 by reconciling against the complete field list, 15 by a test that broke — and they are in
the same table rather than in prose, because prose is where a sub-part goes to be forgotten.

| # | Sub-part | Verdict |
|---|---|---|
| 1 | Migration enabling `pg_trgm` and `unaccent` | **Done** — `V28__substring_search.sql`. Both installed on the live stack, asserted by `TextSearchIT.extensionsInstalled` |
| 2 | An `IMMUTABLE` normalisation function — lowercase, unaccent, **final sigma ς → σ** | **Done** — `novocore_searchable(text)`. ⚠️ **Shipped wrong once and was fixed by the live check** — see the locale finding below |
| 3 | GIN trigram indexes on every searched column | **Done** — 17 of them (15 in V28, 2 in V29), all present on the live database, and `TextSearchIT.everySearchedColumnIsIndexed` fails the build if a searched column loses one |
| 4 | **One shared, reusable query mechanism** — not per-entity duplicated logic | **Done** — `core/support/TextSearch.java` returning a `Specification<E>`, plus `Specifications.activeOnly`. Five services call it; none has search logic of its own |
| 5 | Products — SKU, name, EAN, supplier's SKU, **brand** | **Done**, and it grew a guard nobody asked for — see the disclosure finding below. **Brand was added in V29** when the full target list was reconciled |
| 6 | Suppliers — name, **VAT number**, email, phone | **Done**. VAT was **missing until the reconciliation** and was closed in V29. Code and Alias: **not built, queued** (they are not columns) |
| 7 | Customers — name, VAT number, email, phone | **Done**. Code: **not built, queued** |
| 8 | Users — username, display name | **Done** |
| 9 | Roles — name, description | **Done** |
| 10 | `?search=` on the five list routes; spec regenerated | **Done** — spec diff is **additions only**: 35 lines, 5 parameters, 0 deletions. Generated client diff is 5 lines |
| 11 | Frontend — search box on all five screens, Products' exact-SKU box replaced | **Done** — `components/data-table/search-filter.tsx`, one component, debounced |
| 12 | Confirmed against the **real running backend**: indexes exist, "Cof" matches prefix *and* mid-string, existing exact-match filtering does not regress | ✅ **Done, and now complete on both legs.** The database leg was proven here (see below) and **found the locale defect**. The HTTP leg needed the Owner password, which is deliberately not in this repo — **the owner ran it personally on 2026-08-01 and confirmed both `/api/products?search=kit` and `/api/customers?search=πελατησ` return correct results on the live stack.** Nothing about S1 is now unverified |
| 13 | *(added mid-step)* **`Product.brand`** — column, route, form, index, searched | ✅ **Done** — migration **V29**. It was **never built at all**, despite being in brief §5's Product list from the beginning; found by reconciling against the full field list, not by a test |
| 14 | *(added mid-step)* **Supplier VAT number searchable** | ✅ **Done** — **V29**. The column existed since V9 and simply was not searched, while `customer.vat_number` was. An inconsistency with no argument behind it, invisible to a green build |
| 15 | *(added mid-step)* **`FieldEditor`'s Edit button carries its field name** | ✅ **Done** — an unasked-for change, and the reason is recorded: adding Brand broke four tests that reached their field by *position* among identically-named "Edit" buttons. Five controls all called "Edit" are indistinguishable to a screen reader too. 18 selectors across 5 files now name their field |

### ⚠️ S1's third finding — the reconciliation itself found two gaps a green build could not

**Recorded because it is an argument for the practice, not just an outcome.** S1 was scoped against a
five-entity list agreed in conversation. Reconciling it afterwards against the **complete** per-screen
field list — the 16-row table above — found two things, and **the whole suite was green both before
and after**, because a test can only check the fields somebody pointed it at:

1. **`Product.brand` was never built at all.** Named in brief §5's Product list since the beginning,
   absent from the schema, and therefore absent from every test. Built in **V29**.
2. **`supplier.vat_number` existed and was simply not searched**, while `customer.vat_number` was —
   an inconsistency with no argument behind it, invisible to anything except reading the two lists
   side by side. Closed in **V29**.

**The list is now written down (above) precisely so this is a one-time cost.** A step that adds
search adopts its row; it does not re-derive one from memory of a conversation.

### ⚠️ S1's finding — the test database was not configured like the real one, and it hid a defect

**This is the reason the live check exists, and the first time it has caught something the whole
test suite could not.**

`docker/compose.yml` initialises PostgreSQL with `--encoding=UTF8 --locale=C`, deliberately: it
makes sort order deterministic across machines. **Under locale `C`, `lower()` folds ASCII and
nothing else.** Greek capitals pass straight through.

So the normalisation function, written with a plain `lower()`, did this on the real server:

    Πελάτης Λιανικής  →  Πελατησ Λιανικησ      accents stripped, sigma folded, still capitalised

and a search for `πελατησ` returned **zero rows**. No error. The index built, the query ran, the
answer was empty and looked like "no such customer".

**Every integration test passed against it**, because `PostgresTestContainerConfiguration` took the
image's own default locale — `en_US.utf8` — where a bare `lower()` folds Greek correctly. The tests
were describing a database nobody runs.

Two fixes, and the second matters more than the first:

1. **The function names its collation**: `lower(… COLLATE pg_c_utf8)`. PostgreSQL 17's builtin
   provider does full Unicode case mapping and is platform-independent. Chosen over ICU
   (`und-x-icu`) because ICU's behaviour tracks the bundled ICU version, and an index expression
   whose meaning changes on a library upgrade is precisely what must not be indexed. ⚠️ **`REINDEX`
   the fifteen indexes after a PostgreSQL major upgrade** — Unicode itself moves, which is the same
   caveat the `unaccent` rules carry, and it is the price of the function being `IMMUTABLE` at all.
2. **The test container now passes the same `POSTGRES_INITDB_ARGS` as `compose.yml`.** This is the
   real fix: the divergence, not the collation, was the defect. `TextSearchIT` now asserts
   `datcollate = 'C'`, so removing the pin fails the build rather than quietly restoring the blind
   spot. **The whole suite was re-run under the production locale and is green** — nothing else was
   depending on the permissive one.

### ⚠️ S1's other finding — a hidden column must not be a searchable one

Raised while writing `ProductService.search`, not by a test. `PRODUCT_SUPPLIER_SKU` is a restricted
field *and* one of the columns worth searching. Redaction blanks it in the response — and does
nothing about the row still being **findable** by matching it, which discloses the value one
character at a time, every step confirmed by a result the role is entitled to see.

So `searchFor` **removes the column from the query** for a viewer who may not see it, rather than
only redacting the answer. Both restricted fields are checked, mirroring `ProductView.redactedFor`,
which blanks the SKU when either is restricted. The consequence to know: **the same term can return
fewer rows for a restricted role.** Two tests hold it, one at the service and one over HTTP.

⚠️ The HTTP one uses a **purpose-built role, not Remote/Order Staff** — V26 removed every seeded
restriction, so written against a seeded role it would have asserted a restriction that is not
configured and passed only if the guard were broken the other way. The first draft did exactly that
and failed.

### What sub-part 12 proved — and it now covers both legs

Proved against the **live stack** (`docker compose`, real seeded data, migrated v27 → v28 in place —
not a fresh database):

- both extensions installed; **all 15 indexes present**;
- `Πελάτης Λιανικής` found by all six spellings an operator could type — `ΠΕΛΑΤΗΣ`, `Πελάτης`,
  `πελατης`, `πελατησ`, `ελατη`, `λιανικ`;
- **the "Cof" case**: a name *starting* with it and a name *containing* it mid-string both returned,
  a third row containing neither excluded — run inside a transaction and **rolled back**, so no rows
  remain (residue: the `product` id sequence advanced, which a rollback does not undo);
- **the index is genuinely used** — at 50,000 rows the planner chooses
  `Bitmap Index Scan on product_name_search`, measured rather than assumed, also rolled back;
- **exact matching did not regress**: on the `sku`, `ean` and `vat_number` exact paths a *prefix*
  still matches nothing, and full values still match.

✅ **And the HTTP leg is now proved too — by the owner, on 2026-08-01.** Driving it needs the Owner
password, which is deliberately not in `docker/.env` and comes from the operator's environment, so it
could not be done from inside the session. **The owner signed in and ran both checks on the real
running stack:**

    /api/products?search=kit          → correct results ✅
    /api/customers?search=πελατησ     → correct results ✅

The second is the one that matters most: it is the exact query that returned **zero rows** before the
locale defect was found, and a green test suite said nothing was wrong. It is also independent
confirmation of the whole chain — Caddy, TLS, the session cookie, the permission interceptor, the
service, `TextSearch`, the function and the index — which no test in this repository exercises
together against *this* database.

The automated cover remains: `MasterDataEndpointIT.Search` and `UserRoleEndpointIT` drive `?search=`
over real HTTP through the real Spring Security chain against a real PostgreSQL.

**Three decisions taken at approval, each closing something that was genuinely open:**

- **`Supplier.code`, `Supplier.alias` and `Customer.code` do not exist as columns**, and are *not*
  added here. They come from the brief's Supplier and Customer field lists, **both marked
  *(draft)***, and step 5 built neither. Search covers the columns that exist; the three fields are
  **queued as their own item** (see below) because adding them is a schema plus routes plus forms
  plus a uniqueness decision, not a search feature.
- **Frontend is in scope**, all five screens.
- **Greek final sigma is folded** — `unaccent` maps ά→α but leaves ς and σ distinct, so a term
  ending in a sigma would match only if the operator typed the same form the data uses. On Greek
  party names, which is most of this data, that is a real miss.

⚠️ **This closes the open decision the frontend roadmap recorded and refused to guess at.** Its
bugfix note says the SKU filter box is an exact lookup, that typing `TEST` against eight
`TEST-PRODUCT-*` SKUs matches nothing, and that *"the choice between a real search endpoint and
clearer labelling is the owner's, and the frontend should not change until it is made."* It is now
made: a real search endpoint.

**Explicitly out of scope, and why:** VAT classes, units of measure and every transactional document
get no search here. Sub-part 4 exists so that wiring search in when their screens are built is one
line at the call site plus one index — which is the criterion this step is judged against, not an
aspiration.

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

### CI — green, and `BackupIT` now runs for real on it (`5a6dfa5`, 2026-07-29)

The backend workflow was **failing on `mvn verify`** at the start of this session, on
`BackupIT.verifyConfigurationIsHonest`: `pg_dump` reported `16.14` where the test requires 17.

**The cause was a config assumption, not a broken install.** `.github/workflows/backend.yml`
installed `postgresql-client-17` from PGDG and then trusted the bare name `pg_dump` to mean it. It
does not: `ubuntu-latest` already carries PostgreSQL 16, and `/usr/bin/pg_dump` is
`postgresql-common`'s `pg_wrapper`, which selects a version from the local cluster rather than the
newest one installed. So a green install step was followed by a 16 binary.

The fix is to **name the versioned directory** — `/usr/lib/postgresql/17/bin` appended to
`$GITHUB_PATH` — rather than rely on any resolution rule, plus a **separate step that asserts all
three binaries report 17** and fails naming the offender. Separate because `$GITHUB_PATH` only
applies from the next step, and because a recurrence should fail at the install with the cause named
rather than in an integration test two minutes later. **No application code changed**, and the
runner's own PostgreSQL 16 packages were left installed — removing them would fix this build by
breaking anything else on the runner that expects them.

**Verified against the actual environment, not by reasoning.** Run
[30446419236](https://github.com/Novogrowth/NovoCore/actions/runs/30446419236) is `BUILD SUCCESS`,
866 tests, 0 failures, 0 errors, **0 skipped**, and its log reads:

    pg_dump resolves to: /usr/lib/postgresql/17/bin/pg_dump
    which -a pg_dump  →  /usr/lib/postgresql/17/bin/pg_dump
                         /usr/bin/pg_dump          ← the 16 wrapper, still there, now behind
    pg_dump    (PostgreSQL) 17.10 (Ubuntu 17.10-1.pgdg24.04+1)
    pg_restore (PostgreSQL) 17.10 (Ubuntu 17.10-1.pgdg24.04+1)
    psql       (PostgreSQL) 17.10 (Ubuntu 17.10-1.pgdg24.04+1)

**What this buys beyond a green tick: `BackupIT` ran, 16 tests, 0 skipped.** Step 12's restore check
genuinely dumped, created a database, restored into it and asserted the restored ledger balances —
**on CI, with a real PostgreSQL 17 client against a real PostgreSQL 17 server.** The outcome the
workflow's own comment names as the worst available (a green build that silently stopped covering
brief §13's outstanding risk) did not happen, and is now guarded against by the version assertion
rather than by the test alone.

**⚠️ This does not discharge the `docker compose up --build` action item.** What CI proves is that a
17 client dumps and restores a 17 server correctly. The **runtime** image is a different artefact:
`docker/Dockerfile`'s `postgresql-client-17` line is still written and never executed. The risk is
lower — the client/server pairing is no longer an untested assumption — but the Dockerfile edit
itself remains unexercised, and only a real build proves it.

---

## Step 16a — done (the four backend prerequisites)

Four items raised before any frontend foundation work, proposed in
`docs/step-16-backend-prerequisites-proposal.md` and approved item by item. **None touched
`/frontend/`.** All four are complete.

**1. `GET /api/me` + `PATCH /api/me/language` (V27).** Identity, role, **every** `Section` with its
level *and* `isAvailable()` — so a UI can tell "you may not see this" from "this is not built yet",
which `Section` already modelled and nothing exposed. Q47(a): language is a column on `app_user`, not
a Setting, because a preference belongs to a person and must follow them between devices. Nullable
with no default — "has not chosen" is a real answer. Q47(b): **the backend localises nothing**, so
the CHECK constrains the tag's *shape* and names no language; the frontend decides what it offers.
The trigger to revisit is written into V27.

**`@AuthenticatedOnly` rather than a hole in three checks.** `/api/me` is the route that tells a
caller which sections they hold, so gating it behind one is circular. Instead of "unless the path is
/api/me" inside the interceptor, the startup check and the ArchUnit rule, the rule became: **every
`/api/**` handler carries exactly one of `@Requires` or `@AuthenticatedOnly`**, and both is refused
at startup. The set of controllers allowed to use it is asserted **in both directions** — a new one
fails, and a listed one that stops using it also fails — both proven against probes.

**2. Preview endpoints — an extraction, never a second implementation.** `POST
/api/sales-invoices/preview` and `POST /api/credit-notes/preview`. Everything `record`/`issue` worked
out before writing became `compute()`, returning one value both paths consume, so they *cannot*
disagree — asserted by driving one request through both and comparing every line's net, VAT, gross,
class and precedence level. **Proven to fail** against a preview that adds a cent.

Not implemented as post-then-rollback, deliberately: that would burn document numbers, fight the
deferred debits=credits trigger, and **leave real audit entries behind**, since those are written
`REQUIRES_NEW` and are proven to survive a rolled-back caller.

One refusal is *reported* rather than raised — a rounding difference above the threshold with nobody
accepting it. `record` still refuses; preview returns `roundingNeedsAcceptance` with the difference
and the threshold used, because an entry screen has to show the operator the difference and offer the
acceptance **before** they submit, and cannot if asking what the difference is refuses to answer.

The credit note earns its own preview: it credits back **the VAT the sale actually charged**, read
off the invoice line, not what the precedence rule resolves today. A test moves the customer's
override between the sale and the credit and asserts the VAT still matches what was taken.

**3. The OpenAPI spec — springdoc tried, rejected on evidence, generator written.** springdoc 3.0.3
resolves and the app starts, but it pulls **Jackson 2** alongside our Jackson 3 and swagger-core
introspects with the Jackson 2 mapper — so it cannot see `NovoCoreJsonModule` and reflects Java
accessors. It produced `Money.amount` as a **number**, a whole `java.util.Currency` object for the
currency, and `Quantity` as `{value: number, zero, negative, positive}` against a real wire format of
a bare `"3.000000"`. **The probe's output is preserved verbatim in `OpenApiSchema`'s javadoc**,
because the evidence is worth more than the conclusion.

Built instead: `OpenApiSpecIT` walks the same `RequestMappingHandlerMapping` that `RouteCoverage`
already reads. **137 operations, 150 schemas**, in `docs/api/openapi.json`. **An unknown type fails
the build** rather than being guessed — a bare `BigDecimal` is refused by name, pointing at the
`Rate` type. Every operation carries `x-novocore-section` / `x-novocore-level` from its own
`@Requires`, so the permission model ships with the contract. The spec is committed and the build
fails on drift, **proven** by tampering with one `operationId`. `moneyIsAlwaysAString` is a
*separate* test from the drift check, deliberately: drift says the spec matches the code, that says
the spec is not lying about the thing that matters most — a generator can pass the first and fail the
second, which is exactly what springdoc did.

⚠️ **A portability bug found while building it:** Jackson's pretty-printer indents with the *system*
line separator, so the file was CRLF on Windows and would have been LF on the CI runner — the drift
check would then have failed on every build that ran somewhere other than where the file was last
written, reporting a contract change that had not happened. Normalised at generation, plus a
`.gitattributes` rule.

**4. Paging — the contract, and sales invoices as the worked example.** `PageRequest` /
`PageResponse` / `SortDirection` in `core-api`, **not Spring Data's** (ADR 0003); `SpringPaging` is
the one place the framework's paging meets ours. Offset paging **with a total, not a cursor**,
because an accounting table needs "page 7 of 34" and a row count. `ListResponse` gains an *optional*
`page`, absent on an unpaged list — the regenerated spec was **152 insertions and zero deletions**,
which is that backward-compatibility claim checked rather than asserted.

**The ordering is total, and that is the subtle part.** A sort on invoice date leaves rows tied and
PostgreSQL may return tied rows differently per query, so successive pages could show one row twice
and never show another — plausible on screen, wrong. `SpringPaging` appends the id to every ordering
in the sort's own direction, and a test walks a 12-row list four at a time **with all twelve tied on
the sort column**, asserting each was seen exactly once.

Sort keys are a per-endpoint enum, so an unknown value is refused by Spring before our code runs and
the accepted values land in the spec; the service *also* maps names to properties explicitly, which
is the guard that holds if a service is called from elsewhere. `Paging.of` translates
`PageRequest`'s `IllegalArgumentException` into `InvalidRequestException` — `PageRequest` is right to
throw it, and `WebExceptionHandler` is right to discard it, which is precisely the named
anti-pattern; one helper keeps it from being got right in most routes.

⚠️ **`GROSS_TOTAL` had to be removed from the sort enum.** An invoice's gross is not a column —
`grossTotal()` sums its lines in Java. Ordering by it needs a correlated subquery per page or a
stored total that could disagree with the lines. **The same trap exists on purchase invoices, goods
receipts and settlements** — check before adding a constant, not after.

---

## Step 16b — done (users & roles, journal listing, settings) — `452b3fd`

**The three sections with no HTTP surface at all.** `USERS_AND_ROLES` and `JOURNAL` had zero routes;
Settings had neither UI nor API. All three were administered by direct SQL, and all three would have
become permanent frontend placeholders. **1188 → 1326 tests, 137 → 174 routes, no migration** —
checked against the `role_section_grant`, `setting_key_format` and `journal_entry_source_known`
CHECKs rather than assumed, after step 14's plan said "no migrations expected" and was wrong.

Proposed in `docs/step-16b-users-journal-settings-proposal.md` and approved area by area.

### Users & roles — 18 routes

Real CRUD, per-section grants, field restrictions, plus `GET /api/sections` (the catalogue a role
editor renders its grid from) and `GET /api/roles/{id}/users` (because role deactivation is refused
while anybody holds it, and a refusal naming a count and not the people is a dead end).

**Governed by `Section.USERS_AND_ROLES`, not hard-coded to Owner/Admin.** In practice that *is*
Owner-and-Admin-only today — access is default-deny and nothing else is granted the section — but it
stays configuration, which is what brief §7's "multiple custom roles from the start" requires of the
section that administers roles. Hard-coding it would have made the section dead code and broken
`PermissionSweepIT`'s granted-everywhere role, which asserts every read is admitted by *stored
grants* rather than by the `fullAccess` flag.

### 🛡️ Narrowing a role now ends its holders' sessions, and did not before

`UserSessions` was wired into `UserService.deactivate` and `changeRole` only. **`RoleServiceImpl` had
no reference to it at all.** Latent and harmless while role editing was direct SQL — an `UPDATE` to
`role_section_grant` never went through Java — and not harmless the moment
`PUT /api/roles/{id}/grants/{section}` exists: revoking a section would have left every holder of the
role using it for up to a full session lifetime. Exactly the failure `UserSessions` was built to
prevent, arriving through the door this step opened.

| Operation | Ends sessions |
|---|---|
| revoke a section (`FULL`→`NONE`) | ✅ |
| downgrade (`FULL`→`VIEW`) | ✅ |
| restrict a field | ✅ |
| reset a password | ✅ **new** |
| widen a grant | ❌ correct |
| re-grant the same level | ❌ correct |
| re-restrict an already-restricted field | ❌ correct |
| rename / change language | ❌ correct |

**Both directions are asserted**, because a control that evicted on every change would pass a test
checking only the revoking half while making the system unusable. **Each eviction assertion was
confirmed to fail with its call removed**, then restored — the step-12 audit-log lesson: a structural
test ("`RoleServiceImpl` depends on `UserSessions`") would pass against code that injects it and never
calls it.

**`AccessLevel.isNarrowerThan` uses an explicit `rank()` switch, not `ordinal()`.** Same answer today;
silently different the day somebody reorders the constants, and the thing reading it decides whether
a revoked user stays logged in.

**Role deactivation deliberately does not evict** — it is refused while anybody holds the role, so
there is provably nobody to evict. That reasoning is now a test, so weakening the refusal fails
loudly instead of quietly leaving deactivation unable to log anyone out.

### 🛡️ User administration was a route to unlimited access — closed by two rules

Neither closes it alone:

- **Per-section, on `RoleService.grant`** — you may only confer a level you already hold on that
  section. Read through `RoleView.accessTo`, never off the grant rows, so a full-access actor holds
  `FULL` everywhere by construction (Owner and Admin have no grant rows at all, and a check reading
  them directly would conclude the Owner may grant nothing). Revoking is always allowed: `NONE` is
  narrower than everything, and containment must not require the access being contained.
- **By the `fullAccess` flag, on `UserService.create`/`changeRole`** — you may not put anybody into a
  full-access role without holding one. This is the precedent `RoleService.create` already set (no
  custom role can *become* full access), which was otherwise trivially sidestepped by creating an
  account in one of the two that already exist.

Plus a refusal to edit the permissions of your own role, or to change your own role.

**`PrivilegeEscalationIT` walks the compound path** — a `USERS_AND_ROLES`-only role creates a second
role, grants it `JOURNAL:FULL`, puts an account in it — and asserts it is **cut at the grant, not
downstream**, with the target role verified to carry no `JOURNAL` key afterwards. Cutting it only at
account creation would leave a role carrying `JOURNAL:FULL` in the database, one mistake from
somebody being moved into it.

**Confirmed to fail against the flag-only version.** The sequence never touches a full-access role at
any point, which is precisely why a flag-only guard has nothing to fire on;
`theFlagAloneWouldNotHaveCaughtTheCompoundPath` asserts that fact directly so the reasoning stays
checkable once the probe is gone.

### Journal — 3 routes

`GET /api/journal-entries` (paged), `GET /api/journal-entries/{id}`, `GET /api/accounts/{id}/ledger`
(paged). Filters: date range, account, source, sub-ledger — **all optional**, unlike
`/api/sales-invoices` which demands a range, because a ledger screen's landing view is "everything,
newest first" and the page already bounds the query.

- **`source` accepts all ten `JournalSource` values**, not the six brief §6 names as the typed
  transactions. Goods receipts, credit notes, freight allocations and inventory write-offs post too.
- **The account filter is an `EXISTS` subquery, not a join** — an entry with three lines on the
  account must appear once, and `distinct` would fix the duplicates while leaving `count(*)`, and
  therefore "page 7 of 34", wrong.
- **`/api/accounts/{id}/ledger` is governed by `JOURNAL`, not `CHART_OF_ACCOUNTS`**, despite the path.
  Asserted behaviourally (a role with the chart and not the journal reads the account and is refused
  its ledger) and carried as an explicit exception in `PermissionSweepIT`'s rule table, ordered above
  the chart prefix.
- **No write routes.** `postManualEntry`, `amend` and `reverse` have services and no routes: a
  manual-entry screen is a design conversation — line editor, account picker, balance-as-you-type —
  not something to add behind a listing.
- **Q40 (a human-facing entry number) stays open.** The kickoff brief said it had been decided; this
  file said otherwise and was right. Deferred: the format is genuinely unguessable, it needs a
  migration plus a backfill plus a gap-free allocation strategy, and it is broader than the journal.

⚠️ **`JournalEntrySummaryView` exists because `JournalEntryView` refuses to exist without its lines**
— and **not** for the reason four javadocs originally gave. The received wisdom that a fetched
collection forces in-memory paging (`HHH000104`) is **Hibernate 5 behaviour**. Measured on 7:

```
without a collection fetch:  5 entities loaded,  0 collection loads
with one:                   15 entities loaded,  5 collection loads
```

So the cost is an **N+1 per page**, not an out-of-memory. **The test meant to protect this passed
against a deliberately introduced fetch** until it was rewritten against the measured numbers and
given an `isStatisticsEnabled()` check — the earlier version was asserting "0 is less than 25" and
would have passed against any implementation. Recorded because a false justification is how a good
decision gets reversed by whoever checks it next.

**Filtering uses `Specification`s, not one JPQL string with optional predicates.** PostgreSQL cannot
infer a type for a bare parameter in `? IS NULL` and refuses the statement outright. Casting each
parameter works and keeps five dead conditions in every plan.

### Settings — 3 routes, and the lookups — 13

**An allowlist, not a view of the table.** `SettingsCatalog` (18 entries) binds to `{key}`, so an
uncatalogued key **has no route** — Spring refuses it before any of our code runs, and the exposed
set appears in the OpenAPI document.

**The whole `backup.*` namespace is excluded, and per-key exclusion would not have been enough:**
`backup.drive.*.folder-id` and `.client-id` are **not** flagged secret in the table and would arrive
in the clear from any `listRedacted()`-based listing — and would be missed again by whoever adds a
third destination. Asserted against the raw response bytes, not the parsed items. The published spec
contains **zero occurrences of `backup.`**.

The **backup encryption key needs no exclusion**: it has no row in the table at all, because the
table is inside the dump it decrypts (ADR 0013). A test asserts that absence rather than trusting it.

**Values are validated before they are stored**, so a refused write leaves the previous one intact.
Today `SettingsService.put` accepts `"0,03"` with a comma and the failure surfaces on the next invoice
recorded, naming a key nobody was thinking about.

**`cash.payment.limit` is readable and never writable.** Statutory (€500, N. 5301/2026), not
technically immutable — the catalogue entry records *why*, so the missing write route does not read
as an oversight. The read serves **the administrator reviewing configuration**, not the operator who
hit the refusal: they already get the limit interpolated into the 422 *and* into the invoice preview,
with no `SETTINGS` grant. That is asserted by a test, so the placement decision does not quietly
become wrong the day somebody stops interpolating it.

**Six guards, three proven to fire against probes:** the `backup.` namespace rule; secrets-are-
write-only; and an **ArchUnit rule keeping `..core.web..` off the untyped `SettingsService`**. Plus:
every catalogued key exists in the table (except `smtp.password`, which no migration seeds); the
encryption key has no row; and every seeded value satisfies its declared type.

**VAT classes and units of measure got write routes under their existing sections**
(`TAX_AND_CHARGES`, `PRODUCTS`), not `SETTINGS` — asserted behaviourally, since a role with
`SETTINGS:FULL` reaches neither. ⚠️ **There is deliberately no route to change a rate, and its absence
is asserted across three plausible paths**: editing one would retroactively change what every invoice
already issued under that class appears to have charged, so a rate change is a new class plus a
deactivation. `GET /api/units-of-measure/without-mydata-code` makes **Q38's outstanding list visible
for the first time** — it was answerable only from `psql`, which is how a question waiting on the
accountant stays unasked.

### 🐛 A defect that was not this step's, fixed for the whole surface

`MethodArgumentTypeMismatchException` **is an `IllegalArgumentException`**, so an unparseable enum
fell through to the generic handler as a bare `400 "Bad request."` That is `CLAUDE.md`'s named
residual — *a wrong but non-empty value, specifically an unparseable enum* — the case none of the
three guards can see: it is not a 5xx, so `noRouteFailsOnAnEmptyBody` is blind to it, and nothing in
`..core.web..` constructs the exception, so the ArchUnit rule is too.

Found by **`PermissionSweepIT.noRouteRefusesWithoutSayingWhy`** when this step added the first
enum-typed **path** variables — and **latent on every enum query parameter since 16a**, so a mistyped
`?sort=` on any paged list had been answering `"Bad request."` all along. Fixed in
`WebExceptionHandler`: the refusal now names the parameter and, for an enum, the accepted constants.

### Coverage and the sweeps

- **`TradingQuarterOverHttpIT` drives the three journal routes rather than excusing them** — it has
  hundreds of real entries and runs as the Owner, so it cross-checks each summary's `total` and
  `lineCount` against the full entry's own lines, over reversals, credit notes, freight allocations
  and write-offs. A draft excuse claiming the quarter's operator lacks `JOURNAL` was **false** and
  was caught before it went in.
- Users/roles, settings and lookup administration are **excused with written reasons**, each naming
  the test that covers it. `EXCUSED_ROUTES` moved from `Map.of` to `Map.ofEntries` (10-pair cap).
- `PermissionSweepIT` gained four prefixes and one ordered exception.
- The spec was regenerated and **the operation sets diffed directly**: 0 removed, 37 added, 174
  total. The raw diff showed ~300 deleted lines, which was the paths block re-sorting alphabetically
  — worth checking rather than trusting on an additive change.

### One process note

The proposal's section headers twice stated a route count its own tables contradicted — "17
users/roles" against 9+9, and "11 lookup routes" against 6+7. **The tables were right both times.**
Worth knowing for future proposals: count from the table, not the prose.

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

## ✅ Closed — a permission change now takes effect immediately (2026-07-30)

**Found while building `/api/me`, fixed the same day by session eviction.** Kept in full below
because the defect is more instructive than the fix, and because the reasoning decides what happens
if NovoCore is ever run as more than one instance.

**Decision: eviction, not a short-lived cache.** A time-boxed refresh of the principal shrinks the
window without closing it, and a window is exactly what must not exist when an account is being cut
off deliberately — "revoked but still working for another minute" is not meaningfully better than
"for another hour" for a departing employee or a compromised account.

**Built:** `UserSessions` in `core-api` (the same seam as `CurrentUser`), implemented by
`NovoCoreSessionRegistry` in `app`. `UserService.deactivate` and `changeRole` both call
`endAllFor(userId)` **inside their own transaction**, so a rolled-back revocation cannot log
somebody out of an account that is still active, and a committed one cannot leave them logged in.
The number of sessions ended is recorded on the audit entry.

**Three design points worth keeping:**

- **Our own registry, not Spring Security's `SessionRegistry`.** The framework's keys its map by the
  *principal object*, so lookup depends on `NovoCorePrincipal` equality — and our principal wraps a
  `UserView` carrying display name, language and the whole resolved role. Any of those changing
  would change the key and orphan the sessions registered under the old one: **eviction would report
  success while ending nothing.** Keying by user id, a long that never changes, removes the question.
- **`UserSessions` is a required constructor argument, not an `ObjectProvider`.** A no-op fallback
  would let the application start with eviction silently doing nothing. It fails to start instead —
  the same stance as the initial-owner bootstrap. The core's *test* context declares a no-op
  explicitly, where the claim that there are no sessions to end is simply true.
- **`HttpSessionEventPublisher` is not optional plumbing.** Without it the registry never learns a
  session ended and its map grows for the life of the process.

**Proven by `SessionEvictionIT`, over real HTTP on sessions that really logged in** — the service
layer and the session disagreeing *was* the defect, so a service-level test would have passed
against the broken version. Five tests: deactivation ends the session on the next request; a role
change ends it so the old grants cannot outlive the move; **every** session of that user ends, not
just one; other users are unaffected; and deactivating somebody who was never logged in is
uneventful. **Four of the five were confirmed to fail** with the eviction call removed.

**⚠️ Residual, stated rather than discovered later: the registry is per-process and in memory.** One
JVM, one self-hosted instance — the assumption the rest of the deployment already makes. Running
NovoCore as more than one instance makes this insufficient and the sessions have to move somewhere
shared.

### The defect as it was

**Pre-existing since step 4, not introduced by the `/api/me` work.**

`CoreAuthenticationProvider` builds a `NovoCorePrincipal` at login holding a **`UserView` snapshot**,
which Spring Security stores in the session. `SecurityContextCurrentUser.find()` returns that
snapshot, and `SectionAccessInterceptor` checks permissions against it. So for the life of a session
— **up to 8 hours** — the following have no effect on a user who is already logged in:

- revoking a section grant, or lowering it from `FULL` to `VIEW`;
- moving a user to a different role;
- deactivating the role, or **deactivating the user**;
- restricting a `ProtectedField` (the mechanism V26 emptied but left intact).

The last two are the ones worth stating plainly: **deactivating an account does not log that account
out**, and the operator doing it has no indication of that. `UserService.deactivate` refuses to
remove the last full-access user, so the system cannot be locked out — but a departing employee's
live session keeps working until it expires.

**How it was found**, which is worth keeping: `GET /api/me` returned the language a `PATCH` had just
set, and then the *next* `GET /api/me` did not. The read-back was coming from the session, not the
database. That is the same failure the whole permission model has, surfacing on the one route where
it is immediately visible.

**`/api/me` was fixed first, on its own.** `MeController.me()` reads the user record fresh
(`users.require(currentUser.require().id())`), because a route whose entire job is reporting current
identity and grants must not report yesterday's. `MeIT.grantsAreReadFreshRatherThanFromTheSession`
asserts it and was **proven to fail** against the snapshot-reading version. That fix stands
independently of the eviction above and is still worth having: eviction ends a session on a
*revocation*, while this keeps `/api/me` honest about every other change — a rename, a language, a
grant that was *widened* rather than removed, none of which end a session.

**What is still true after the fix.** Eviction closes the revocation cases, which are the ones with
a security consequence. It does not make the session's snapshot live: a user whose display name or
grants change in a way that does *not* end their session still carries the old snapshot in
`SectionAccessInterceptor` until they log in again. That is now a correctness wrinkle rather than a
security hole — widening a grant mid-session takes effect at next login — and it is left alone
deliberately, because closing it means a database read on every request and the case for paying that
has not been made.

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

## Git state

**Close-out now always pushes** (`CLAUDE.md`, session close-out step 4), so local `main` and
`origin/main` agree at the end of every session. This section therefore records *which commit each
step landed in* and no longer tracks what is unpushed — that list was itself a source of drift, and
at the start of this session it was wrong: it claimed `a09428e` and `920044c` were local when both
were already on `origin`.

| Commit | Step |
|---|---|
| `22bb361` | Step 1 — skeleton, guardrails, container stack |
| `cb93fc8` | Step 2 — primitives, migrations V1–V3, Settings, Audit, Attachments |
| `f2ed289` | Step 3 — chart of accounts, migration V4 |
| `15627d2` | Step 3b — VAT classes, exemption reasons, charge types, migration V5 |
| `a1da425` | Step 4 — users, roles, permissions, session auth, migration V6 |
| `91543fa` | Step 4b — first REST endpoint, web boundary rule made real |
| `efe897e` | Q27 — `Delivery income` / `COD fee income` accounts, ChargeType seed, migration V7 |
| `09ea0d5` | The real AADE VAT exemption reason seed, migration V8 |
| `ae7c31f` | Step 5 — Product, Customer, Supplier, Asset, migration V9 |
| `9c25993` | VAT rate bound's blind spot closed, migration V10 |
| `2dce5df` | Q34 — units of measure as a runtime-editable table, migration V11 |
| `7182831` | Step 6 — inventory lots, serialized units, locations, bundles, migrations V12–V13 |
| `8e7e10e` | Step 7 — journal engine, VAT accounts, stock write-offs, migrations V14–V15 |
| `c6e2513` | Step 8 — purchase invoices, goods receipts, GR/IR clearing, purchase price variance, FIFO consumption, migration V16 |
| `29e9dcd` | Step 9 — sales invoices, credit notes, settlements, bank transfers, open-item matching, rounding, migration V17 |
| `cf6f1e4` | Step 10 — freight / landed cost allocation, the lot's two cost figures, migration V18 (**ADR 0010**) |
| `6f06cf8` | Step 10 (cont.) — stock returning into a re-costed lot, migration V19 (**ADR 0011**) |
| `b542cf7` | Step 11 — the shared email service, outbox, dispatcher, retry, migration V20 |
| `0790c74` | Step 11 (cont.) — the second route into the batch-wide stall, and the credential cleanup |
| `8af7078` | Step 11 (rev.) — an emailed document is referenced, not copied; Q43 answered, Q44's access path decided, migrations V21–V22 (**ADR 0012**) |
| `855643b` | Step 12 — automated backups, encrypted, off-site and proven restorable, migration V23 (**ADR 0013**) |
| `24a3cd7` | Proxy self-invocation made a build failure — `SelfInvocationRulesTest`, and the two real defects it found |
| `a4ec7db` | The audit-log fix proven behaviourally rather than structurally |
| `5a6dfa5` | CI — `pg_dump` made to actually mean 17 on the runner (workflow only, no application code) |
| `e907a9e` | Step 12 commissioned — backups running for real, off-site and proven |
| `9c7ed41` | Step 13 — property-based tests, the whole-scenario invariant sweep, **ADR 0014**, and **Q45** raised |
| `951929f` | Q45 fixed — a lot's movements post the change in its carrying value, migration V24 (**ADR 0015**) |
| `423bf34` | Step 14a — REST foundations (`@Requires`, money as strings, the full error mapping, three architecture rules) and the master-data surface |
| `e6354d6` | Step 14b — purchasing and inventory endpoints |
| `b8aa9e2` | Step 14c — sales, settlements, the outbox, **Q44 in full**, migration V25 |
| `f2e8e06` | Step 14c (cont.) — `BundleService`'s `For` variants, closing the last redaction asymmetry |
| `7c4c2c4` | **`Rate`** — a percentage is a type, not a bare `BigDecimal` (found by step 15a's JSON sweep) |
| `908b226` | **Step 15a** — the validation harness: `LedgerInvariants`, `HttpTransport`, `JsonNumberSweep`, `RouteCoverage` |
| `5bf069c` | Freight `basis` off the wire — a derived accessor, not a record component (found by 15b) |
| `fc217ea` | AR and the open items agree again — three separate causes (found by 15b) |
| `d8c9e77` | **Step 15b (part)** — the trading-quarter narrative, driven entirely over HTTP |
| `6d85c89` | Open items include customer credits, and the invariant now sums signed by type |
| `1421dfb` | Step 15b — the quarter-end review, and the two error-reporting defects it found |
| `b65f7b2` | **Q21 revised** — no field restricted from any role, migration **V26** |
| `1a4b294` | **Step 15b complete** — the refusal matrix, the permission sweep, read-back and date boundaries, restore, `assertEveryRouteCoveredExcept`, and **defects 7, 8 and 9** with the guard the recurrence earned |
| `3158239` | **Step 16a (1)** — `GET /api/me`, `PATCH /api/me/language`, `@AuthenticatedOnly`, migration **V27** |
| `0df73c3` | **Step 16a (2a)** — `POST /api/sales-invoices/preview` |
| `bc0c088` | **Step 16a (2b)** — `POST /api/credit-notes/preview` |
| `fad0d11` | **Session eviction** — revoking access ends the session that holds it (defect found by item 1) |
| `416ca82` | **Step 16a (3)** — the OpenAPI spec, generated from our own serialisers, with the CI drift check |
| `2d37a68` | The generated API contract is LF in the working copy too |
| `8c23e0b` | **Step 16a (4)** — the paging contract, and sales invoices as the worked tier-A example |
| `452b3fd` | **Step 16b** — users & roles, the journal listing and settings: 37 routes, eviction on narrowing, the two anti-escalation rules, and the enum-refusal fix. No migration |
| | **— the frontend, `/frontend/` — one row per step from here —** |
| `94e17cd` | **Frontend foundations** — nav-as-data, permission gate, typed client, decimals, table abstraction, i18n, CSRF/proxy, the write-mutation guard |
| `56e3726` + `28c4119` | **Products** — list, detail, create, per-field PATCH, plus the guards |
| `3458ee6` | **The Products bugfix pass** — the `DataTable` render loop, and the two defects it hid. Not a numbered step |
| `b406b27` | **F1** — Suppliers |
| `496c7be` | **F2** — Customers, and the `editable: false` / `lockedReason` distinction |
| `aea0e56` | **F3** — Users & Roles: the grant grid, and a password shown exactly once |
| `3ea8782` | **S1** — substring search, `pg_trgm` + `unaccent`, migrations **V28** and **V29**. Two findings, one invisible to the whole suite |
| `a4324db` | **S2** — sortable columns on five screens, one collator. The collation question settled from the live database |
| `c89c1c9` | **F4** — Settings: three config pages, VAT classes, units of measure, search and sorting. Migration **V30**. `F4WriteContractIT` **corrected a premise the step was built on** |

Interleaved with these are small docs-only commits (`e25fcee`, `a09428e`, `920044c`, `de16e58`,
`b065901`, `8c27cb4`, `2c3fa8a`, `21b2231`, `d1111d0`, `610f785`, `836a4eb`) and this session's
close-out commit.

**Step 10 is two commits, deliberately.** The convention is one per build step, and `cf6f1e4` is a
complete, green step on its own; `6f06cf8` is a distinct decision found by *reviewing* it, with its
own ADR and its own migration. Folding it in would have buried that story inside another commit's
message, which is the opposite of what the convention is for.

**Step 11 is two commits for the same reason.** `b542cf7` is a complete, green step; `0790c74`
corrects a claim that step made — the poison-pill guard was narrower than its commit message said —
and carries the credential cleanup with it. **This has now happened on two consecutive steps, and
both times the second commit came from reviewing the first rather than from testing it.** Worth
treating as a habit rather than a coincidence: the review pass after a step looks green is earning
its keep.

Local branch `phase-1/core-skeleton` still exists and is fully merged; safe to delete.
Convention going forward is **one commit per build step**, so history stays checkpoint-able.

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

## Step 3 — done

### ⚠️ 2026-08-06 — THE CHART OF ACCOUNTS IS DECIDED, and it supersedes part of what follows

**Recorded here, at the description of the thing it governs, per `CLAUDE.md` §*A decision reached in a
design conversation gets the same close-out discipline as a build step*.** Roadmap row **C1**;
⚪ **recorded, not scoped, and deliberately not built.**

> **Novocore uses the OFFICIAL GREEK CHART OF ACCOUNTS DIRECTLY, with an ALIAS on each account for
> display. There is NO separate business chart of accounts mapped onto the official one.**

**The owner's reasoning, which is the part no reading of the code supplies:**

- **The only thing a second, business-owned chart genuinely buys is many-to-one granularity** —
  several business lines rolling up to one statutory account. **That need is better served by the
  product model** — product categories and lines that name products — **than by multiplying the
  chart.**
- ⚠️ **One layer is also the more REVERSIBLE choice, and this is the load-bearing half.** Adding a
  second layer later is **additive**. Collapsing two into one is a **merge**, and a merge loses
  history. The two options are not symmetric, so take the one that can still be undone.

**What is true today, measured 2026-08-06 rather than remembered:**

| # | Fact | Evidence |
|---|---|---|
| **1** | ⚠️ **There is NO alias field on an account** | `Account` carries `code`, `name`, `account_type`, `account_kind`, `sub_ledger_type`, `system_key`, `group_id`, `display_order`, `active`, `expected_to_clear`, `elp_code` — **and nothing else**. `AccountView` carries the same set. ⚠️ **Deliberately NOT built**; its absence is recorded against **C1**, the row that will need it |
| **2** | **The chart is Novocore's own 65 accounts across 13 groups**, designed from the brief, **not copied from anywhere** | `V4__chart_of_accounts.sql`, and the rest of this section |
| **3** | **`code` is blank on every row and `elp_code` is null on every row** | Step 3's decision — both were to come from the accountant. `AccountSystemKey` exists *because* neither is usable as a handle |

⚠️ **An observation this raises, flagged and deliberately NOT acted on:** the account carries **two**
code columns — `code` (blank) and `elp_code` (null) — which is the two-layer shape this decision
rejects, one field down. **If the official chart becomes *the* chart, whether those two collapse into
one is a real question**, and it is not answered here. Recorded rather than decided.

⚠️ **Two consequences elsewhere, both recorded and neither built:**

- **M0a's target moved.** *"Does every Manager account map to a Novocore account?"* now maps onto **the
  official Greek chart** rather than a chart of our own design. **M0a is not thereby scheduled,
  blocked or cancelled** — only the answer side of the mapping changed.
- **R4 loses a prerequisite it might have had.** Its account picker offers accounts from the one chart
  that exists; nothing in it waits on a second layer that will not be built. **R4 must not add the
  alias column** — that is a chart field, not a payment-method one.

⚠️ **Everything below this box describes the chart AS BUILT at step 3 and is still accurate as a
record of what is in the database.** The decision above changes what the chart will *become*; it does
not retroactively describe what shipped.

`AccountGroup` and `Account` entities, seven types, four kinds, `expectedToClear`,
`displayOrder`, migration `V4__chart_of_accounts.sql` with the full seed (**65 accounts across
13 groups**), `ChartOfAccountsService` on `core-api`, 29 integration tests, and the
schema-convention test.

### What was built beyond the original step-3 spec

Four things that were not in the spec as written last session. All four were flagged and
approved (or are consequences of an approved decision) rather than added quietly.

1. **A seventh account type, `CONTRA_INCOME`.** Forced by the decision to add contra-revenue
   accounts. Sales returns are income-classified with a *debit* normal balance; since the
   normal balance side is derived from the type and never stored, there is no way to represent
   that with six types. Typing returns as `EXPENSE` would put them below the revenue line,
   overstating gross revenue and putting something that is not a cost into expenses. This is
   exactly the argument that produced `CONTRA_ASSET` for accumulated depreciation, one
   statement down.
2. **`AccountSystemKey`** — a stable machine identifier on the eleven accounts NovoCore's own
   posting rules must locate (Rounding, GR/IR clearing, Unclassified, Freight — Unallocated,
   Inventory write-off, the four control accounts, accumulated depreciation, COGS). Needed
   because account codes are deliberately blank and names are operator-editable, so a posting
   rule looking either up would break the first time someone renamed an account. A keyed
   account can be renamed and reordered freely; it cannot be deactivated, and its key is never
   settable from application code. Extending the list is deliberately a migration.
3. **`spring.flyway.encoding: UTF-8` stated explicitly** rather than relying on the default.
   The seed already contains em-dashes and Greek arrives in step 5; resolved from a platform
   default on Windows this would apply mojibake and then diverge Flyway's checksum between
   environments.
4. **Three contra-revenue accounts rather than one.** The approved decision named a single
   `Sales returns & allowances` account, but the stated reason for it was per-channel return
   rate — and channel exists nowhere in the model except in which Sales account gets credited,
   so one shared account would collapse exactly that visibility. Seeded one per channel,
   mirroring the Sales split. Collapsing to one later is a seed-only change.

### Decisions applied this session

- Channel split kept as Store / eCommerce / Skroutz, with **"Sales — Store & Phone"** named
  explicitly so the account states what it contains instead of relying on an undocumented
  convention about where phone orders go.
- **`Sales returns` added as contra-revenue** (three accounts, above). Credit notes debit these
  rather than netting into the channel Sales accounts.
- **Inventory write-off is one account with a reason code**, not three accounts — which of
  shrinkage / damage / expiry a write-off was belongs on the transaction, not in the chart.
- **Inventory write-off moved from General Expenses to the COGS group**, kept as its own
  account separate from `Cost of goods sold`, so gross margin reflects the loss honestly while
  sale-driven COGS stays uncontaminated.
- **`Rounding differences` added** — `STANDARD`, `EXPENSE`, in General Expenses, able to carry
  either balance. It was missing from the seed spec entirely, while V2 had already seeded
  `ledger.rounding.threshold` describing automatic posting to "the Rounding account" and brief
  §7 requires it. Step 9 would have had nowhere to post.
- **Damaged Goods → write-off stays posting-free** (see the obligation below).

### ⚠️ Obligations this step created for later steps

Both are recorded here deliberately so they are not rediscovered cold.

- **Step 6 — a reason field on the inventory write-off transaction.** Because there is one
  write-off account rather than three, the shrinkage / damage / expiry distinction has nowhere
  to live except on the transaction. This is **not optional and not out of scope**: without it,
  the single account is strictly less informative than three would have been, and the reason
  the single account was chosen disappears.
- **Phase 8 — Clearing Checks must surface lots aging in the Damaged Goods location.** Moving
  a lot to the Damaged Goods `Location` posts nothing: the stock is unsellable but still an
  asset at cost, and only the write-off derecognises it. Nothing forces that second step, so
  without a check the balance sheet carries worthless stock at full cost indefinitely. Keeping
  the move posting-free was the explicit decision (impairment-on-move contradicts the brief's
  plain Location model); this check is the agreed compensating control.

### Design notes worth keeping

- **Normal balance side is derived from the type and never stored.** There is no
  `normal_balance_side` column, and a test asserts its absence. Two columns that must agree are
  two columns that can disagree.
- **`type` and `kind` are independent dimensions.** Accumulated depreciation is `CONTRA_ASSET`
  *and* `CONTROL`, which is why they are not one enum.
- **No account balance is stored anywhere.** A balance is the sum of an account's journal lines,
  computed on read from step 7. Consequently **step 3 introduced no monetary columns at all** —
  the assumption last session that it would was wrong. The schema-convention test's scale rule
  was dormant until step 7, which brought the first monetary columns; its no-floating-point rule
  was live from the start.
- **`account_control_iff_sub_ledger` is a biconditional CHECK.** A Control account without a
  sub-ledger has nothing to reconcile against; a sub-ledger on a non-Control account is a rule
  never enforced on its lines. Both directions are refused.
- **A blank account code is refused** (`code IS NULL OR btrim(code) <> ''`), so "no code" has
  exactly one representation and two accounts cannot collide on the unique index by both
  carrying `''`.
- **Account names are unique within a group, not globally.**
- **There is no delete**, only `deactivate`. With no period locking there is no point at which
  an account is safely finished with.
- **A reorder must name every member exactly once** — a partial list is refused rather than
  leaving the remainder in an order nobody chose (`CLAUDE.md` rule 7).
- `Cost of goods sold` is `STANDARD`, not `CONTROL`, but its lines still carry Inventory-Lot
  sub-ledger references: Control-ness governs whether a reference is *required*, not whether one
  may be present.

### Accepted imperfections in the seed

- `Interest received` stays under `Income`, above EBITDA, so **EBITDA is approximate**. Left as
  Manager has it; reversible.
- **No current-portion split** on the NBG loan. Proper practice splits the next 12 months into
  Current Liabilities; not requested, and would need the repayment schedule.
- `VAT payable` is seeded as a single account. Almost certainly insufficient — see Q14.
- **PayPal/Stripe as Partner Clearing under Cash & Cash Equivalents** was an explicit decision
  after the alternative was flagged. Consequence: processor fees post as expense on receipt.
  The accountant may prefer processor balances presented as receivables rather than cash
  equivalents; that is presentation and reversible.
- **`Amortization` is seeded although nothing can post to it** — the Asset entity has no
  intangibles concept. Present so the statement layout is right if that changes.

### Deliberately excluded

- **Inter Account Transfers** — dropped per brief §4. A transfer between own bank accounts is
  two Asset-account entries. Manager had it under Equity, which is the error the brief corrects.
  If it carries a balance in Manager, phase 2b migration needs a destination for it.
- **DDP** — superseded by Freight / Landed Cost — Unallocated (brief §4).
- **Suspense** — replaced by Unclassified — Needs Review.
- **EBITDA, EBIT, Net profit (loss)** — computed subtotals, not ledger accounts.

---

## Step 3b — done

An inserted step, not in the original Phase 1 numbering. Real VAT data arrived from Prosvasis Go
and the AADE/myDATA documentation, which resolved Q4 and brought one new piece of scope
(charge types) that depends on VAT classes existing. Migration `V5`.

### VatClass — a real entity, not an enum

Runtime-editable lookup: `code`, `description`, `ratePercent`, `active`, and a nullable
self-referencing `reducedCounterpart`. **Seeded with the nine real Prosvasis Go classes** —
`0`, `1030`, `1040`, `1041`, `1060`, `1091`, `1131`, `1170`, `1410`.

- **Nine rows, eight distinct percentages.** 4% appears twice: `1040` as a rate in its own right
  and `1041` as the island-reduced counterpart of 6% under αρ.31 ν.5057/2023. Same percentage,
  different legal basis, different code.
- **The code is the identity, never the rate.** Because of the above, a lookup by rate is
  ambiguous by construction, so `VatClassService` deliberately has no `findByRate` — a test
  asserts its absence. A method that is right most of the time is worse than one that does not
  exist.
- **Island-reduced mappings seeded** mainland → reduced: 24→17, 13→9, 6→4 (`1041`, not `1040`),
  4→3. The 0% class has no counterpart. Enforced one level deep, lower-rated, one-to-one, and
  never self-referencing — in the service with named messages, and by `CHECK`/`UNIQUE`
  constraints in the database, proven by raw-SQL probes.
- **Recorded as data only.** Nothing chooses a rate by shipping destination; that is future
  scope, as instructed.
- **All eight reduced/mainland rates seeded, not just the mainland four**, because we do ship to
  islands under the reduced regime.
- **The rate is not editable in place** — there is no mutator and a test asserts none exists.
  Editing would retroactively change what every invoice already issued under that class appears
  to have charged. A rate change is a new class plus deactivation of the old one.
- **Rate stored as a percentage** (`24.000000`, not `0.24`) in `numeric(19,6)`, with a `CHECK`
  refusing anything outside 0–100 so a fraction fails loudly rather than undercharging by 100×.

### The VAT precedence rule, stated as code

`VatClassPrecedence` in `core-api` implements **invoice line beats customer beats product**,
returning both the winning class and a `VatClassSource` saying which level supplied it — so
"why is this line at 13%?" is answerable about a real invoice.

**There is deliberately no fallback rate.** If no level specifies a class, it throws
`VatClassNotDeterminableException` rather than assuming 24%. A silent default produces a
plausible invoice at a rate nobody chose, and an undercharge is not recoverable from the customer
after issue. Tested exhaustively over all eight present/absent combinations, because the rule is
three null checks whose *ordering* carries the entire meaning.

It takes ids rather than objects so it can be applied before Product, Customer and Sales Invoice
exist. **This creates a step 5 obligation:** Product needs a default VAT class, and Customer
needs a *nullable* VAT class override — which overlaps Q9 (Customer has no VAT status field
although Supplier does).

### VatExemptionReason — structure built, deliberately unseeded

`code` (integer), `description`, `mydataCode`, `inputVatDeductible`, `active`. **No seed data** —
the ~29 verified rows are still to come.

- **A separate entity from VatClass, not a 0% rate.** Zero-rated charges 0% under a rate that
  exists; exempt is outside VAT because a named article of the Κώδικας ΦΠΑ says so. Reported
  differently to myDATA.
- **`mydataCode` is stored verbatim**, not composed from `code + "-" + description` at use time.
  It is what goes on the wire, and reproducing AADE's exact punctuation by concatenation is a bet
  worth not taking. `VatExemptionReasonView.mydataCodeMatchesDescription()` exists so a test can
  check whether the composition actually holds **once the real rows land** — worth running then.
- **`code` is an integer, not text.** myDATA's own field is numeric, and text would sort "10"
  before "2" in a picker of ~29 entries. **If any real row's code is not a plain integer, say so
  and it becomes a `varchar` migration.**
- **`inputVatDeductible` is uniformly "Όχι" in everything seen so far.** Kept because it is a
  genuine per-reason distinction in AADE's table; a test proves the column can carry `true` so it
  is not a constant waiting to be optimised away.
- Neither code nor myDATA string is editable — a retired reason is deactivated, not corrected.
- Tests use codes in the 9000s so they cannot collide with AADE's real 1–31 range.

### ChargeType — new scope, structure built, unseeded

`name`, `defaultVatClassId`, `incomeAccountId`, `active`.

- **The income-side guard is the reason this service exists** rather than a bare repository: the
  account must be `INCOME`-type. `EXPENSE` is refused (wiring a delivery fee to
  `Transportation costs` to "net it off" understates revenue and cost together and leaves a gross
  margin that looks plausible and is wrong), and `CONTRA_INCOME` is refused too (that side is for
  sales returns).
- **Unseeded pending Q27** — see below. Seeding against the wrong income account would mean
  migrating posted history later.
- **Nothing consumes it yet.** Sales Invoice line items are step 9.

### Design note: the slice boundary holds inside the core

`ChargeType` holds plain `Long` ids for its VAT class and account, not JPA associations, because
`VatClass` and `Account` are package-private within their own slices. That is not a style choice
— it is the only option available, so ADR 0003's boundary holds *between slices of the core*, not
just between the core and its adapters, without needing another ArchUnit rule. The ids are
validated through `VatClassService` and `ChartOfAccountsService`, the same published interfaces
an adapter would use, and the FK constraints still exist in the database.

### Design note: a third meaning for `numeric(19,6)`

`vat_class.rate_percent` is the schema's **first `numeric` column**, and it is a *rate* — neither
of the two shapes V1 named. The convention now reads: `numeric(19,2)` for a posted **amount**
(two decimals because that is what a cent is), `numeric(19,6)` for a **multiplier** — quantity,
unit cost, or rate — which must not itself lose precision before the product is rounded once.
`SchemaConventionsIT` was updated to say so. Its scale rule became live here; the `numeric(19,2)`
half waited until step 5's `product.selling_price` and now covers the journal's own amounts.

---

## Step 4 — done (users, auth, permissions)

Q21 and Q22 both answered, so this was built as specified rather than against a placeholder.

### Q22 — server-side sessions with an HttpOnly cookie (approved)

- **Spring Security with form login**, session-based. `NOVOCORESESSION` cookie is `HttpOnly`,
  `SameSite=Strict`, `Secure`, 8-hour timeout, with a new session id issued on login so a fixated
  identifier cannot become an authenticated one. All three attributes are asserted against the
  real `Set-Cookie` header over HTTP.
- **CSRF is on**, with the token in a JavaScript-readable cookie so a frontend can echo it back.
  Non-negotiable given cookie auth: without it any site the user visits while logged in can make
  their browser send an authenticated request. Deferred token loading is switched off so the
  cookie exists on the first response.
- **Login and logout return status codes, not redirects** (204/401). A `fetch()` cannot do anything
  useful with a 302 to a login page — it follows it and gets HTML with status 200, which looks
  like success. `/api/**` likewise returns 401 rather than redirecting.
- **No login controller was written.** Authentication uses Spring Security's own `/login` and
  `/logout`, so this step added no hand-written API surface.
- **Password hashes never leave the core.** `UserService.authenticate(username, rawPassword)` takes
  the plain password and returns a user or nothing, so hashing and comparison both happen inside
  the core. The conventional `UserDetailsService` arrangement hands the hash to the framework,
  putting it on the boundary and into every stack trace on the authentication path. A custom
  `AuthenticationProvider` in `app` calls the core instead, and `NovoCorePrincipal.getPassword()`
  returns null — it is what lives in the session.
- **Login failures are indistinguishable.** Unknown username, wrong password, deactivated user and
  deactivated role all return empty, and the unknown-username path still runs a hash comparison so
  it does not return measurably faster. The reason is recorded in the audit log, where the
  distinction legitimately belongs; both success and failure are logged.
- **Hashes are algorithm-prefixed** (`{bcrypt}$2a$...`) via a delegating encoder, so a future move
  to a stronger algorithm does not invalidate existing passwords.
- **⚠️ Password policy is a stated default, not a decision.** Twelve characters minimum, no
  composition rules (NIST SP 800-63B: composition rules push people to predictable substitutions).
  **2FA is not implemented.** Q22 approved the session mechanism and left both open — see below.

### Q21 — Remote/Order Staff, built as the concrete case

Brief §7 also requires **multiple custom roles from the start**, so roles are **data** while the
things being granted are **code**:

- `Section` and `ProtectedField` are enums — which parts of the application exist is determined by
  what has been built, not by configuration.
- `app_role` + `role_section_grant` + `role_field_restriction` are tables, so creating a role is an
  operation rather than a migration.
- **Access is default-deny.** "Everything else is invisible" needs no enumeration and stays true as
  sections are added; a new section is invisible until granted.
- **Owner and Admin use a `full_access` flag, not stored grants per section.** With stored grants a
  section added in a later release would be invisible to the owner of the system until someone
  inserted a row. Both are **system roles**: unmodifiable and undeletable, so removing
  `USERS_AND_ROLES` from the last role that has it cannot lock everyone out.
- **Remote/Order Staff is seeded exactly as answered** — `FULL` on Sales Order Fulfillment,
  Customers and Back-in-Stock Reminders; `VIEW` on Products; `PRODUCT_LAST_PURCHASE_PRICE`,
  `PRODUCT_SUPPLIER` and `PRODUCT_SUPPLIER_SKU` hidden; nothing else. Deliberately **not** a system
  role, so it stays adjustable at runtime.
- **Field restrictions narrow, never widen.** A role that cannot view Products does not see a
  product's cost even with no restriction recorded against the field.
- **An inactive role or user grants nothing**, independently of the other.
- The permission decision lives on `RoleView` as pure logic, so it is exhaustively tested with no
  database — including a sweep over *every* section, which means a section added later is covered
  by the test the day it appears.

### ⚠️ Step 5 obligation: the field mechanism has nothing to guard yet

`ProtectedField`'s three entries are live configuration, not placeholders — the grants and
restrictions are seeded and enforced. But **Products do not exist until step 5**, so no response is
currently redacted by them. When `ProductView` is built it **must** consult
`RoleView.canSee(ProtectedField)` for each of the three. That is the one piece of Q21 that could
still silently not happen.

### First-login bootstrap

**No user account is seeded and there is no default password** — the same stance as the database
credential. The first Owner comes from `NOVOCORE_BOOTSTRAP_OWNER_USERNAME` /
`NOVOCORE_BOOTSTRAP_OWNER_PASSWORD`, and **the application refuses to start** if the user table is
empty and those are unset, naming both variables. Once a user exists the variables are ignored and
should be removed. `docker/.env.example` and `compose.yml` carry them.

### `auditorAware` now records the real user

Step 2 left this returning `system` unconditionally with a note that step 4 would replace it. It
now reads the authenticated user via a `CurrentUser` interface in `core-api`, implemented in `app`
against the security context — the seam that keeps the core unaware Spring Security exists.
Unattended work (Flyway seed, future backup and depreciation runs) still records `system`, which is
honest rather than attributing it to whoever logged in last. Resolved through an `ObjectProvider`
so the core's own tests, which have no web layer, still work.

---

## Step 4b — done (the first REST endpoint)

One endpoint: `GET /api/chart-of-accounts`, read-only, returning `List<AccountGroupView>` through
`ChartOfAccountsService`. **Scoped deliberately narrow — this is boundary validation, not the start
of the frontend API.** No other endpoint was added.

**It did its job.** The `..core.web..` ArchUnit rule previously carried `allowEmptyShould(true)` and
passed while checking nothing. That allowance is now removed, and the rule was **proven to fail**: a
temporary probe class in `..core.web..` referencing a public core-internal class tripped it, naming
both the field and the constructor parameter. Probe deleted.

The controller has no repository, no entity, no `@Transactional` and no mapping code — a service
interface and a permission check. Authorisation is an explicit `requireView(Section.CHART_OF_ACCOUNTS)`
rather than a `@PreAuthorize` string, because a typed enum cannot be misspelled and a misspelled
expression that fails open is the worst available outcome. With many controllers this should become
a shared interceptor.

Proven end to end over real HTTP: 401 unauthenticated, **403 for Remote/Order Staff**, 200 with the
chart for the Owner, session invalidated by logout, CSRF enforced, and the refusal body leaking
neither the contents nor the permission model.

### Consequence: the core's test context excludes the web layer

`CoreTestApplication` now excludes `..core.web..` from component scanning. The controller depends on
`CurrentUser`, which only `app` implements, so scanning it in the core's own tests failed the whole
context. Excluding it is the honest answer — those tests exercise services against a real database,
and the endpoint is tested in `app` where the full wiring exists. **A permissive fallback
`CurrentUser` bean in the core was considered and rejected**: a security component that substitutes
a default when its real implementation is missing is precisely what later fails open.

### Notes for whoever adds the second controller

- Response types are core-api DTOs, not separate web records. Right for a read-only projection
  already shaped for the outside world; wrong the first time a response needs a shape the core has
  no reason to have.
- `WebExceptionHandler` maps the core's permission exceptions to 401/403. It exists because those
  exceptions live in `core-api`, which may not have a Spring dependency, so `@ResponseStatus` on
  them is not an option.

---

## V7 and V8 — the two queued items, done

**V7 (`efe897e`) closes Q27.** `Delivery income` and `COD fee income` added to the Income group
(65 → 67 accounts), and the two `ChargeType` rows seeded against them, so `charge_type` is no
longer empty. V7 opens a display-order gap at 6 and 7 **by position rather than by name**, so the
sales-related lines read together and `Other income` stays last — a residual bucket in the middle
of a list invites postings that should have gone somewhere specific. No channel split, and no
`AccountSystemKey`: these accounts are located through `charge_type.incomeAccountId`, which is an
operator decision per fee rather than a rule compiled into the software.

**✅ Q33 settled, and confirmed with the accountant: a fee's VAT rate is independent of the products
on the invoice.** Both charge types default to 24%, and that is the operative rate rather than a
placeholder — a 13% order still carries 24% delivery. It was raised here as a possible defect, on
the general principle that an ancillary charge follows the main supply's rate; **the accountant
confirmed the treatment as built**, so this is a settled decision rather than a recommendation that
was overruled. Consequently **nothing should later be built to derive a fee's rate from the lines
around it.** The per-line override still exists for a deliberate exception; what is deliberately
absent is anything automatic. V7's comment and `ChargeTypeIT` both state the decision rather than
the former limitation.

**V8 (`09ea0d5`) seeds the real AADE VAT exemption reasons** — 29 rows from Prosvasis Go's
"Διατάξεις απαλλαγής Φ.Π.Α." screen, in the **recodified** Κώδικας ΦΠΑ article numbering (άρθρο 2
και 3, 5, 17, … 58) rather than the older numbering most documentation still uses. Three findings:

1. **Codes 24 and 28 are absent from Go's list.** Gaps were anticipated, but these are missing from
   *Go* rather than known to be retired by AADE. See Q35.
2. **Codes 29, 30 and 31 — the OSS and IOSS reasons — have no myDATA code in Go**, so
   `mydata_code` is now **nullable**. NULL means "no mapping exists", not "not filled in yet".
   Composing a string would fabricate a value that later gets transmitted, and omitting the three
   rows would leave an exempt OSS/IOSS sale with no reason to select. **Phase 7 obligation:
   transmission must refuse a NULL**, which is what `VatExemptionReasonView.requireMydataCode()`
   exists to do. See Q36.
3. **Storing the myDATA string verbatim was load-bearing**, which step 3b said a test should check
   once the real rows landed. It was: codes 12 and 13 name "Πλοία Ανοικτής Θαλάσσης" in their
   description and **not** in their myDATA string. Composing the value would have transmitted those
   two wrong. A test asserts exactly which rows break the pattern.

Descriptions drop Go's numeric prefix (`"1 - Χωρίς ΦΠΑ …"`), since the code is its own column here
— keeping it would render as "1 - 1 - …". That also sidesteps a source quirk: Go's row for code 9
appears to carry the prefix `"8 - "`.

---

## Step 5 — done (Product, Customer, Supplier, Asset)

Migration `V9`, four entities, four services, and all four blocking questions answered.

### The four answers, as built

- **Q5 — one product, one supplier.** A plain nullable foreign key, no many-to-many. The supplier
  SKU is **refused without a supplier**, in the service and by a CHECK constraint: that meaningless
  state is the whole content of the question. A test asserts no join table exists.
- **Q8 — a single email and a single phone**, on Customer and Supplier alike.
- **Q9 — `VatStatus`, shared by both parties** so the two lists cannot diverge. **Five values, not
  four:** `NON_EU_EXPORT` is split out of `OTHER`, because an export and an intra-EU B2B supply are
  both VAT-free **under different articles** and are reported differently — "other" would lose
  exactly what has to be stated on the document. `INTRA_EU_B2B` requires a VAT number and `EXEMPT`
  requires an exemption reason; both are definitional rather than policy, and both are CHECK
  constraints as well as service checks. `OTHER` exists so an unusual party can be recorded
  truthfully, and **nothing defaults to it**. No VIES validation, as instructed.
- **Q12 — a manually set depreciation rate on Asset, nullable.** Null means "the statutory rate is
  not known yet", which is the register's actual state. `AssetService.withoutDepreciationRate()`
  exists so that stops being forgettable, and `AssetView.canDepreciate()` is what a run must check
  instead of substituting a default. **No rate was invented and no category table was created** —
  both wait on the accountant, the way the VAT class list did.

### Deliberately omitted from Asset, with reasons

- **Useful life** — for straight-line it is `100 / rate`, so storing both invites them to disagree.
  Same argument that keeps `normal_balance_side` out of the chart of accounts.
- **Salvage value** — Greek tax depreciation writes down to zero, and it would be the one monetary
  field on an otherwise ledger-derived record.
- **A depreciation method field** — straight-line only (brief §5). A single-valued column is dead
  weight; a second method arriving is a migration with a decision attached.
- **Any monetary field at all.** Both fixed-asset control accounts declare `ASSET` as their
  sub-ledger, so every posting names its asset and cost and accumulated depreciation are **sums of
  journal lines**. Consequence, stated plainly at the time: **until step 7 this was a register, not
  a valuation** — the same shape as a product having no stock until lots exist. The ledger exists
  now, so an asset's carrying value is `subLedgerBalanceOf` on its `ASSET` reference; nothing posts
  to it yet, because the depreciation run is still open.

### The depreciation rate is bounded 1–100, and the lower bound is the point

A plain 0–100 range **cannot** catch `0.1` written for 10%: it sits comfortably inside, and the
charge would be a hundred times too small every year with nothing complaining. 1% is a hundred-year
life, which no statutory category has. **A test caught this**: the first version of the validation
claimed in its message to reject fractions and did not. Worth knowing that
`vat_class_rate_is_a_percentage` has the same blind spot — its 0–100 bound does not catch `0.24`
written for 24% either.

### Step 4's field-restriction obligation is discharged

`ProductView.redactedFor(RoleView)` is the **single** implementation, delegating every decision to
`RoleView.canSee`. Tested against the real seeded `REMOTE_ORDER_STAFF` role loaded from the
database, and — as pure logic in `core-api` — against a last purchase price that cannot exist in
real data until step 6, so all three restricted fields are covered now rather than two of them.

**One rule beyond the stored restrictions: hiding the supplier hides the supplier's SKU too**,
since a supplier code identifies the supplier indirectly. Narrowing only, which is the safe
direction and the direction field restrictions are allowed to move in.

> ⚠️ **A named convention, not an enforced one.** `ProductService` has plain read methods
> (unredacted, for the core's own costing and posting rules) and `...For(viewer)` variants that
> redact. **Anything answering a request from a person must use the `...For` variants.** Making
> redaction mandatory would mean inventing a pretend-role for the posting rules to pass, and a
> "system role that sees everything" is precisely the thing a controller later reuses. The `For`
> suffix is in the name so its absence is visible at the call site — but the first Products
> controller must be reviewed for this specifically.

### `product.selling_price` is the schema's first monetary column

Which settles what V1 left open. The convention is now stated and enforced:

    <name>            numeric(19,2)   the amount
    <name>_currency   char(3)         its ISO 4217 code, present exactly when the amount is

Tied by a biconditional CHECK, and `SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency`
enforces the pairing **across the whole schema**, so step 7's monetary columns inherit the rule
rather than re-deciding it once there are dozens. Proven to fail against a probe table.

A **zero price is refused**; null is how "not priced yet" is said. Zero and unset look identical on
a screen, and zero produces an invoice line worth nothing without anyone choosing to give the goods
away. Null is permitted because a product imported from an external catalogue or created
barcode-first may genuinely not have a price — the refusal belongs at invoicing, not at creation.

### Other decisions worth keeping

- **`ProductType`** is `GOODS` / `SERVICE`, and it decides real behaviour: a service has no lots,
  credits `Services` rather than a channel `Sales` account, and costs against `Cost of service
  sold` — three accounts the seeded chart already distinguishes.
- **Matching is split by certainty** (`CLAUDE.md` rule 7). `findByVatNumber` is an exact match on
  an authority-issued identifier and may be applied automatically; `suggestMatches` returns
  candidates a human confirms. A blank VAT number matches **nothing** rather than the first party
  without one — that would be an automatic match on the absence of the identifier that makes
  automatic matching safe.
- **Customer names are not unique; VAT numbers are.** Two unrelated retail customers genuinely can
  share a name, and refusing the second would push whoever is serving them into inventing a suffix.
- **Phone numbers are compared as stored.** Nothing normalises `+30` / `0030` / bare local yet, so a
  differently formatted number will not match. That belongs with the adapters that import contact
  data, where the source format is known.
- **`suggestMatches` runs one derived query per supplied criterion and merges in Java.** The
  compact single-JPQL-query version does not work: a named parameter appearing only inside
  `:x IS NOT NULL` gives Hibernate nothing to infer a type from, it binds as `bytea`, and
  PostgreSQL rejects `lower(bytea)` at runtime. A test caught it.
- **Cross-slice references are plain ids**, validated through the published services — the same
  pattern `ChargeType` established, and the only route available since each slice's entities are
  package-private.
- **Sections `PRODUCTS` and `CUSTOMERS` are now available; `SUPPLIERS` and `FIXED_ASSETS` are new.**
  No grants were seeded: access is default-deny, so the two new sections are invisible to
  Remote/Order Staff without saying so, and visible to Owner and Admin at once via `full_access`.
- **The migration README was corrected** — it still said there were no migrations yet, and told
  writers to use PostgreSQL DOMAINs that V1 explicitly rejected.

### Not built, deliberately — each asserted absent by a test

So they read as decisions rather than oversights, and so a later step cannot quietly assume one
exists:

- **No bundle flag** (Q11 still open). A flag nothing honours reads as a half-built feature.
- **No customer merge.** Brief §5's alias-forward needs an alias table and a decision about
  postings already made under the retired id; neither exists until the ledger does. Half of it is
  worse than none — a merge that appears to work and loses references.
- **No generic retail customer** (Q10 unanswered). A seeded catch-all is the row that quietly
  absorbs every unmatched sale and then cannot be untangled.
- **No address fields** on Customer or Supplier. Go issues the invoices until phase 11, so nothing
  needs to print one yet. See Q37.
- **No stock in any form**, and no `last_purchase_price` column (Q6 answered by implementation:
  computed, like stock). `ProductView.lastPurchasePriceIfAny()` exists and is always empty until
  step 6.

### ⚠️ Obligations this step created

- **Step 6 — `ProductType.SERVICE` must not answer "zero" when asked for stock.** A service has no
  lots. Zero and "not applicable" look identical on a screen and would produce a back-in-stock
  reminder for a service.
- **Step 6 — `UnitOfMeasure.allowsFractionalQuantity()` exists and nothing enforces it.** Three of
  a product sold by the piece is three; 2.5 pieces is a data-entry error worth catching. The rule
  is stated on the unit so step 6 reads it off there rather than re-deriving its own list.
- ~~**Step 7 — the `Depreciation` expense account has no `AccountSystemKey`**~~ — **done in step 7.**
  `DEPRECIATION_EXPENSE` was added in V14, so the expense side of a depreciation posting has a stable
  handle instead of being found by an editable name. **Nothing posts to it**: whether the periodic
  run is Phase 1 scope is still open, and the statutory rates are still with the accountant.
- **The first Products controller must use the `...For` variants.** See the warning above.

---

## V10 and V11 — the two follow-ups from step 5's review

### V10 (`9c25993`) — the VAT rate bound had the same blind spot as the depreciation rate

Flagged by the user after step 5's rate bug, and worth recording as a pattern rather than an
incident. **V5's 0–100 CHECK did not do what its comment claimed.** It said a rate entered as a
fraction would "fail loudly instead of undercharging 100×"; in fact `0.24` sits comfortably inside
0–100, so it was accepted as a quarter of one percent and produced exactly that undercharge.
`VatClassViewTest` even asserted the behaviour and documented the gap as a known limit instead of
closing it.

**The rule is now "exactly 0, or between 1 and 100"** — not a flat minimum, because the `'0'` class
(Μηδενικός Συντελεστής ΦΠΑ) is real, seeded, and legally distinct from an exempt line, so `>= 1`
would have refused real data. Nothing charges a fraction of a percent, so the whole interval (0, 1)
is unreachable by legitimate data and is available as a trap. Stated once as
`VatClassView.isAcceptableRate`, applied by the service, and enforced by the database, so all three
agree by construction. Every seeded rate already satisfied it.

**Worth generalising:** a range check whose bounds both sit outside the plausible-typo range catches
nothing. Both times the mistake was the same — the *upper* bound was chosen carefully and the lower
one was left at "not negative". Any future rate column should be checked against this.

### V11 (`2dce5df`) — units of measure became a table (Q34)

Step 5 built `UnitOfMeasure` as an enum; the user approved converting it. The decisive argument is
the one that made `VatExemptionReason` a table: **myDATA has its own unit codes, which are AADE's
data, and an enum constant cannot own them.** Prosvasis Go also holds "Μονάδες μέτρησης" as an
editable list, so an operator already expects to add one without a deployment. Converted now rather
than after step 6, because lots carry quantities and the reference gets harder to move with every
table pointing at it.

- **`mydata_code` is nullable and every seeded row has NULL** — same stance as the OSS/IOSS
  exemption reasons. `UnitOfMeasureView.requireMydataCode()` makes phase 7 fail naming the unit
  rather than transmitting a composed code, and `withoutMydataCode()` answers "which units cannot be
  transmitted?" before AADE asks it.
- **A myDATA code is write-once.** One that has been transmitted describes documents already filed
  under it, so a wrong mapping means deactivate-and-replace, not edit.
- **`allowsFractionalQuantity` moved from enum constant to column**, since it is a judgement about
  how the business sells rather than a physical fact. Still unenforced until quantities exist.
- **`UnitOfMeasure` is a real `@ManyToOne` from `Product`**, unlike the VAT class and supplier which
  are plain ids. Not an inconsistency: it lives in the same package, so it is the same slice of the
  core rather than another aggregate reached through a published service.
- **Deactivating a unit a product still uses is refused**, not cascaded — a product whose unit was
  retired carries a quantity that no longer states what it counts.
- The column conversion is **add / backfill / constrain / drop**. The product table is expected to be
  empty everywhere, but a migration that silently needs an empty table is one that fails on exactly
  the machine where someone has been working.

> ⚠️ **New step 6 obligation:** `ProductService.changeUnitOfMeasure` must refuse a change on a
> product that has stock. Reinterpreting 12 pieces as 12 kilograms is not a units change, it is a
> different quantity. There is nothing to guard yet; the obligation is stated in the interface and
> the implementation so it is findable.

> ⚠️ **Still needed from the accountant, now with a home to go in:** the verified AADE unit codes
> (Q34 follow-on), alongside the exemption codes in Q35/Q36 and the depreciation rates.

---

## Step 6 — done (inventory lots, serialized units, locations, bundles)

Commit `7182831`, migrations `V12` and `V13`. All three blocking questions were answered at the
start of the session, so this was built to the answers rather than around them.

### The three answers, as built

- **Q7 — stock per location, plus a computed sellable figure; sellable is the Inventory location
  only, excluding Damaged Goods and Service.** `InventoryService.stockOf` returns `StockLevels`,
  which carries **every** location with zero where there is none, and derives `sellable()` from
  `StockLocation.sellableLocations()` rather than reading `INVENTORY` directly — so a second
  sellable location is one edit on the enum instead of a search for hardcoded values. This is why
  V9 was right to refuse a stock column: nine on hand and three sellable is the ordinary case, and
  a single number has to pick one of them to be wrong about.
- **Q25 — a fixed enum.** `WriteOffReason`: `SHRINKAGE` / `DAMAGE` / `EXPIRY` / `OTHER`. Free text
  would give "damaged", "Damaged", "broken in transit" and "ΦΘΟΡΑ" as four categories, which is the
  same as having none, and reportability is the entire reason the single write-off account was
  chosen over three. `OTHER` exists so nobody is forced to pick the nearest-looking value and
  corrupt the four that matter.
- **Q11 — bundles built now, to brief §5 in full.** We currently sell bundled products, so this was
  not speculative scope. All five requirements: own SKU, no stock of its own, proportional
  allocation, decomposition into component lines, and the link between the two revenue levels.

### `StockLocation` is an enum, not a runtime-editable table

The opposite call from `VatClass` and `UnitOfMeasure`, and deliberately so. Those became tables
because the authoritative list belongs to AADE or to Prosvasis Go. These three values are
NovoCore's own, and **every one of them has behaviour attached that only NovoCore can supply**:
`INVENTORY` is what sellability is computed from, and `DAMAGED_GOODS` is what phase 8's Clearing
Checks must single out. A row an operator added at runtime would be storable and unhandled.

Named `StockLocation` rather than `Location`, because a bare `Location` reads as an address and
Customer and Supplier will need one of those (Q37). **Not a warehouse** — several physical
warehouses are a different concept, absent from the brief, and would arrive alongside this rather
than as extra values in it.

### Two shapes of lot, and the nullable columns are the mechanism

The load-bearing design decision of this step. A lot is one of exactly two things:

| | quantity columns | `location` | `serialized_unit` rows |
|---|---|---|---|
| **Pooled** | on the lot | on the lot | none |
| **Serial-tracked** | **absent** | **absent** | one per unit, each with its own location |

The rule across both: **location lives wherever the quantity does.** A serial-tracked lot stores no
quantity because the quantity *is* the count of its units — storing it as well would be two numbers
that must agree and are therefore free to disagree after the first sale, the same argument that
keeps `normal_balance_side` off `account`, a useful life off `asset`, and a cost off `asset`
entirely. `InventoryLotView` still exposes concrete quantities for either shape, computed by
counting, so a caller does not have to know which kind it is asking about.

One CHECK (`inventory_lot_pooled_columns_go_together`) refuses any third shape, proven by raw-SQL
probes. The consequences are symmetric and each fails loudly: **`moveLot` refuses a serial-tracked
lot** (move its units — one machine going out for repair does not move the others) and **`moveUnit`
refuses anything not on hand**.

### `UnitCost` — the type `Money` has been pointing at since step 2

Six decimals, its own type, in `core-api/shared`. `Money` is exactly two decimals and *rejects*
anything more precise; a unit cost cannot live inside that, because brief §4's proportional
landed-cost allocation produces repeating decimals per unit and rounding them before they are
multiplied back out overstates the lot for no reason anyone can later find. Two euros of freight
over three units is `0.666667`, and `extend()` is the only route back to `Money` — so every place
that gives up precision does it in a method that names its rounding mode.

**Zero is allowed, negative is not.** A supplier's free sample is a real lot, and unlike a zero
*selling price* it gives nothing away by being recorded. A negative unit cost is not a fact about
any lot: a purchase credit reduces the quantity or reverses the receipt.

`inventory_lot.unit_cost` is therefore **the schema's first monetary `numeric(19,6)` column** — the
others at that scale are a VAT rate, a depreciation rate and a quantity, none of which have a
currency. See the schema-convention note below.

### Bundles, and the allocation arithmetic

`BundleAllocation.proportionally(total, weights)` is **exact integer arithmetic in cents** with
largest-remainder distribution. The obvious implementation — divide, multiply, round each line —
loses a cent or two on almost every split, and those are exactly the residuals brief §6 then has to
reconcile. Instead each part's numerator is a whole number, floored by integer division, and the
leftover cents go to the parts whose exact share was cut by the most, with ties broken by position
so the answer is reproducible rather than dependent on iteration order.

Two consequences worth keeping:

- **It never needs a rounding mode and never produces a rounding difference.** `Rounding
  differences` is for reconciling against an *external* document, not for absorbing our own
  arithmetic.
- **`BundleDecomposition` enforces in its constructor that the component lines sum to the bundle
  line.** That is what makes brief §5's "linked, not duplicated" a property of the data rather than
  a hope about whoever writes the phase 8 report: either level gives the same revenue, and adding
  them together is visibly double-counting.

The rest of the bundle rules:

- **Components are one level deep** — a component may not itself be a bundle. Same rule and same
  reasoning as V5's island-reduced VAT counterpart: it makes a cycle impossible by construction
  rather than by a recursive check that has to be got right, and it keeps allocation single-pass.
  Enforced in the service, because a CHECK cannot read the other row's flag; the self-reference half
  *is* a CHECK.
- **`define` replaces the whole component list**, never merges. A partial change leaves the rest in
  a state nobody chose — the argument that makes a chart-of-accounts reorder name every member. It
  also means the flag and the components are set in one transaction, so **a bundle never exists
  empty**.
- **A bundle has no stock of its own.** It cannot receive a lot, and a product that already has lots
  cannot become one — either way the same goods would be counted twice. `stockOf` on a bundle
  computes how many could be assembled per location, limited by whichever component runs out first,
  by **integer** division: half a component is not half a bundle.
- **A bundle may be GOODS or SERVICE and its components may be either.** A machine sold with its
  installation is a real bundle; the installation takes allocated revenue and nothing off a shelf,
  so only *stocked* components constrain availability. A bundle with no stocked components refuses
  to report stock rather than answering zero, which would say the opposite of what is true.
- **An unpriced component refuses decomposition** rather than weighing zero — a zero weight would
  push the whole bundle's revenue onto the priced components and report the unpriced one as pure
  margin. Same stance as having no fallback VAT rate. `bundlesWithUnpricedComponents()` exists so
  that is found before a sale rather than during one, mirroring
  `AssetService.withoutDepreciationRate()`.

### Serial numbers are unique across all stock

Strictly a stronger claim than the world supports — two manufacturers could issue the same string —
and still the right constraint. Within one business's stock, the same serial appearing twice is
overwhelmingly a duplicate scan or a unit received twice, and catching that is worth more than
accommodating a collision nobody has seen. A real one becomes a per-product uniqueness rule as a
deliberate migration, rather than being discovered as a silent overwrite of a warranty record.

### `Section.INVENTORY` is new, and separate from `PRODUCTS` because of cost

Stock **levels** are a product-level read: Remote/Order Staff has VIEW on Products and genuinely
needs to know whether there are three left. A **lot** carries its unit cost, which is exactly what
`PRODUCT_LAST_PURCHASE_PRICE` exists to keep from that role. So granting the ability to see stock
must not grant the ability to see what it cost. No grants were seeded — access is default-deny.

As in step 4b, the section each `InventoryService` method belongs to is **stated in its Javadoc and
not enforced by the service**; the check belongs at the controller, and there is no controller yet.

### Obligations discharged this step

- ~~**Step 3 — a reason field on the inventory write-off**~~ — the enum exists (see the caveat
  below about it having no consumer yet).
- ~~**Step 5 — `SERVICE` must not answer "zero" for stock**~~ — `StockNotApplicableException`,
  which says "not applicable" instead. Zero is indistinguishable from sold out on a screen and would
  put a repair service into a back-in-stock reminder.
- ~~**Step 5 / V11 — enforce `allowsFractionalQuantity`**~~ — enforced on lot receipts and on bundle
  component quantities, read off the unit rather than from a list kept elsewhere.
- ~~**V11 — `changeUnitOfMeasure` must refuse a product with stock**~~ — done, and
  `changeSerialTracking` carries the same guard for the same reason, plus a stronger one: a pooled
  quantity of five has no serial numbers to recover.
- ~~**Q6 — last purchase price computed rather than stored**~~ — now populated from the most recent
  lot's unit cost, by acquisition date rather than insertion order (a backdated receipt must not
  win). Batched into one `DISTINCT ON` query for list reads rather than one per row.

### ⚠️ Obligations this step created

- ~~**Step 7 — the write-off transaction must carry `WriteOffReason`**~~ — **done in step 7.**
  `stock_write_off` carries it and reduces the lot (or the named unit) *and* posts in one
  transaction. Brief §5's exception is honoured **by construction**: a write-off always names its
  lot, so nothing picks one for the caller and no FIFO logic can creep in.
- **Step 8 — a lot needs its source document reference.** Brief §5 lists one and ADR 0004 already
  settles that the Goods Receipt is what creates a lot; neither exists yet, so no nullable column
  was added early. Also step 8's: **FIFO consumption must use the order `lotsOf` already defines**
  (acquisition date, then id) rather than inventing its own, and **Q17** — whether aggregate stock
  may go negative — belongs with that consumption. A single lot already cannot go below zero, by
  CHECK.
- ~~**Step 9 — the serialized unit's sale link**~~ — **done in step 9.** `SerializedUnitStatus.SOLD` is declared and
  unreachable: brief §5 wants the customer/invoice link recorded on the unit once sold, and a
  nullable customer id added now would let a unit be marked sold to somebody with no document behind
  it. The stock count is already written against the status column, so it will be right the day a
  unit is sold without anyone revisiting the query. Also step 9's: **`BundleService.dissolve` on a
  bundle that has been sold** would strand decomposed component lines pointing at something that is
  no longer a bundle — brief §5's "alias forward, never rewrite history" is the shape of the answer
  and it needs the ledger.
- ~~**Step 10 — last purchase price must stop coming from the lot**~~ — **done in step 10.** It now
  returns the lot's **received** cost, which ADR 0010 froze for the life of a lot precisely so this
  question has an answer inside the inventory slice. The residual — that the received cost is the last
  price we *believed*, not necessarily the last a supplier *invoiced* (ADR 0008) — is recorded in the
  step 10 section and was deliberately not built around.
- **Phase 8 — the Damaged Goods aging check now has its query.** `InventoryService.lotsAt` and
  `unitsAt` are what it reads, and `lotsAt` deliberately covers serial-tracked lots (via any on-hand
  unit at that location) and deliberately excludes exhausted lots, which are history rather than
  stock aging at cost.

### Two defects the tests caught, both fixed at the root

Recorded because the pattern matters more than the incidents.

1. **Multiplying two quantities overflowed the scale.** `quantityPerBundle.value().multiply(
   bundleQuantity.value())` is 6dp × 6dp = 12dp, and `Quantity` allows six — so *one* bundle
   containing *one* grinder failed on its own trailing zeros. The patch would have been a
   `setScale` at the call site. The fix is `Quantity.times(Quantity)`, stated once: it strips
   trailing zeros, and **throws rather than rounding** if the product genuinely needs more than six
   places, because a quantity is a physical count and an inexpressible one is a modelling error, not
   a rounding question. Steps 8–10 will multiply quantities constantly and now cannot re-make this
   mistake.
2. **`InventoryLot.getUnits()` returned two different orders.** `@OrderBy` sorts the list Hibernate
   *loads*, and does nothing for a lot built in memory a moment earlier — so a freshly received lot
   came back in scan order and the same lot re-read came back sorted. One projection returning two
   orders is precisely the difference a test written against the second case never sees. Now sorted
   in the getter, so both agree.

### The schema-convention rule was widened, and proven to fail

`SchemaConventionsIT.everyMonetaryColumnCarriesItsCurrency` previously covered `numeric(19,2)` only.
`unit_cost` is monetary at six decimals, so the rule had to grow — but **scale alone cannot be the
discriminator**, because a VAT rate and a quantity share it and are not money. The discriminator is
the **name**: a monetary multiplier is named `..._cost`, and the rule requires a currency companion
for any `numeric(19,6)` column whose name ends that way.

**Proven to actually fail**, the same method this repo has used throughout: a temporary probe
migration added a `probe_cost` table with an unpaired `landed_cost`, a correctly paired `unit_cost`,
a rate and a quantity. The rule failed naming exactly `probe_cost.landed_cost` and ignored the other
three. Probe deleted. A future monetary six-decimal column that is genuinely not a cost means
widening the rule, not naming the column around it — which the migration README now says.

---

## Step 7 — done (the journal engine)

Commit `8e7e10e`, migrations `V14` and `V15`, plus **ADR 0006** (correction policy) and **ADR 0007**
(VAT posting). Six questions were answered before the step started — Q13, Q14, Q19, Q26, Q15 and
Q16 — so this was built to the answers rather than around them.

### Rule 6, and what "structurally" turned out to mean

**Debits equal credits is a `DEFERRABLE INITIALLY DEFERRED` constraint trigger**, checked at commit.
A CHECK cannot express it — it spans rows — and deferral is load-bearing rather than a nicety: an
entry is legitimately unbalanced between its first line and its last, so a per-statement check would
make writing one impossible.

**Two triggers, not one.** A trigger on `journal_line` never fires for an entry that has *no* lines,
so without a second one on `journal_entry` an empty entry would be perfectly storable. The
entry-level trigger also enforces the minimum of two lines and refuses an entry spanning two
currencies.

**Stored `total_debits` / `total_credits` columns with a single-row CHECK were considered and
rejected.** They would have made the invariant far easier to express, and they would be a second
copy of what the lines already say — the argument that keeps `normal_balance_side` off `account` and
a quantity off a serial-tracked lot.

**A named side plus a strictly positive amount, never a signed one.** Signed makes the invariant a
question of whether every producer of a line remembered to negate; the named side makes it a sum per
side, which no accidental sign can satisfy. Zero is refused as firmly as negative — a zero line
balances while stating nothing, so an entry padded with them would satisfy rule 6 and mean nothing.
A `debit_amount`/`credit_amount` pair was also rejected, on a schema ground: ADR 0005 needs a
currency companion per monetary column, and two amount columns give either two currencies that must
agree or one that breaks the naming convention `SchemaConventionsIT` enforces.

### Q13, answered — ADR 0006

Invoices and credit notes are **immutable once posted**; receipts, payments, bank transfers and
manual journal entries are **editable in place**, with the previous date, description and lines
written to the audit log *before* being overwritten. The line drawn is whether the record exists
outside NovoCore: an invoice has been issued to somebody else and will be transmitted to AADE, so
editing it makes NovoCore disagree with what the counterparty holds.

- **The policy is stated once, in SQL.** `journal_source_is_amendable(varchar)` is what both triggers
  call, and a test calls it for every value of the Java enum and compares. "Immutable" that holds
  only for callers who came through the service is not immutability.
- **⚠️ Immutability was extended to the inventory write-off** — not covered by Q13's wording, flagged
  as an addition rather than folded in quietly, and **explicitly approved**. The reason is stronger
  than the invoice's: editing the entry would change the loss recognised without changing the lot
  quantity it came out of.
- **Nothing is ever deleted**, from either table, whatever the source.
- **A reversal is verified to be the exact mirror** of its original — same accounts, amounts and
  references, opposite sides. That is what makes `post(..., reversalOfEntryId)` a safe path for a
  service reversing its own document rather than a second and weaker write path. Line *descriptions*
  are excluded from the comparison, since a reversal legitimately re-words them.
- **`reverse` refuses a source that owns state the ledger cannot see**, naming the service to use
  instead. Reversing a receipt's money without releasing its allocations would leave invoices
  reported as settled by a receipt that no longer exists.
- Reversing a **reversal** is permitted and needs no special rule; the "reversed at most once"
  UNIQUE constraint already stops the real mistake.

### Q14, answered — ADR 0007

**Separate `Output VAT` (liability) and `Input VAT` (asset), never netted.** `V14` repurposes V4's
single `VAT payable` into the output side and adds the input side; repurposing is safe **only**
because nothing had posted anywhere yet, and an account that has been posted to is never repurposed.
Neither is `expected_to_clear` — a balance between filings is the ordinary state of affairs, and
flagging them would put a permanent false positive into phase 8's Clearing Checks.

**The load-bearing consequence: a journal line carries `vat_class_id` and `taxable_base`.** Q14 says
VAT is computed per line and summed by rate — and "summed by rate" is only meaningful if the sum can
be told apart afterwards. Without the class on the line, two Output VAT lines at different rates are
two indistinguishable amounts against one account, and one could have posted a single total and lost
nothing. It also cannot live only on the invoice, because a Manual Journal Entry can post to a VAT
account directly and a figure assembled from documents alone would omit it. The **class** is stored,
never the rate, because `1040` and `1041` both charge 4%.

- **Permitted, not required, on the two VAT accounts; forbidden everywhere else**, by trigger.
  Required would break the periodic settlement, which moves money at no rate at all.
- **Reverse charge needed no new structure** — two lines in one ordinary entry, one to each VAT
  account, same class and base. Both figures stay separately reportable while netting to zero in
  cash, which is exactly what netting the accounts would have destroyed.
- **An exempt line posts no VAT line at all**; its exemption reason belongs on the invoice line,
  because there is no VAT line to hang it on.
- **`vatTotals(from, to)`** reads the dimension back, netted per direction so a credit note reduces
  output VAT rather than appearing as a second figure. It is here rather than with the reports
  because a column nothing reads is indistinguishable from a column nobody thought about. There is
  deliberately **no third "VAT payable to authorities" account**: settlement is debit Output, credit
  Input, net to the bank, and NovoCore never accrues a return it has no duty to file.

### The write-off obligation, discharged

`stock_write_off` carries the `WriteOffReason` the single write-off account was chosen against three
for. It reduces the lot (or the named unit) **and posts, in one transaction** — either alone is
worse than neither. `SerializedUnitStatus.WRITTEN_OFF` is reachable at last.

- **Brief §5's serialized exception is honoured by construction**: a write-off always names its lot,
  so nothing picks one for the caller and no FIFO logic can creep in. A test posts two lots at
  different costs and proves the named unit's own cost is used.
- **A lot carried at zero cost posts nothing, and the stock still leaves.** A free sample
  derecognises nothing because nothing was carried, and the ledger rightly refuses a zero-amount
  line, so `journalEntryId` is nullable and `derecognisedNothing()` is the honest reading. This is a
  real case, not a defensive one — `UnitCost` explicitly allows zero.
- **No amount column on the write-off.** What was posted is in the entry, and recomputing it from the
  lot's unit cost would give a different answer once step 10 allocates freight.
- **Reversal restores the quantity or the unit status and posts the mirror together**, refuses a
  second reversal, refuses reversing a reversal, and refuses restoring more than the lot ever
  received (reachable, if something consumed the lot in between).
- The **location of a written-off unit is deliberately left alone** — the machine is often still
  physically in Damaged Goods, and the stock count excludes it by status rather than by place.
- `writeOffsOf` and `writeOffsBetween` **include reversals**: netting them out is the reader's
  decision, not the query's.

### Other decisions worth keeping

- **`GOODS_RECEIPT` is deliberately absent from `JournalSource`.** ADR 0004 settles that a Goods
  Receipt posts, so it will need a value — but whether it is amendable has not been asked, and
  adding one is deliberately a migration so the question gets asked. **Step 8 obligation.**
- **No `source_id` on an entry.** `source` says what *kind* of transaction produced it, because Q13's
  policy is keyed on that and needs it now. There is nothing to point at until steps 8 and 9 — the
  same stance V12 took on a lot's source document. **Step 8 obligation.**
- **No entry number.** The id is the handle. A human-facing sequential number is a real thing an
  accountant asks for and carries a format decision (per-year reset? prefix per source?) nobody has
  been asked. Recorded as an open question rather than guessed at.
- **`entry_date` has a floor of 2000-01-01 and no upper bound.** The floor catches the transposed or
  two-digit year, which a plain "is a date" check cannot — the same lesson V10 recorded about the VAT
  rate, that a bound only earns its keep if it sits inside the plausible-typo range. No upper bound,
  because a forward-dated accrual is legitimate and there is no period locking.
- **A dangling sub-ledger reference is refused by trigger**, using dynamic SQL, because the reference
  is polymorphic and cannot be a foreign key. Doing it in Java would have made the ledger depend on
  the inventory service, which already depends on the ledger to post a write-off — a bean cycle for a
  check the database makes directly.
- **`journal_line_number_unique_in_entry` is DEFERRABLE.** An amendment replaces the whole line list,
  so line number 0 is inserted while the old one is still present. Relying on Hibernate ordering
  orphan removals before inserts would be relying on a library's implementation detail to keep a
  schema constraint satisfiable.
- **Balances are computed on every read** and carry the date they were computed for.
  `AccountBalance` keeps both totals rather than only their difference, because an account with
  equal, large debits and credits is a different situation from one with no activity while both net
  to zero. A balance on the *wrong* side reads negative rather than being made positive — a credit
  balance on a bank account is an overdraft and a debit balance on Accounts payable is an overpaid
  supplier, and an absolute value would hide both.
- **`subLedgerBalanceOf` is debit-positive**, not presented on a normal side: one sub-ledger
  reference legitimately appears on accounts with opposite normal sides — an asset's cost and its
  accumulated depreciation both carry the same `ASSET` reference — so flipping per account first
  would make their net the sum of the two rather than the carrying value.
- **`linesOf` returns lines, not a ledger type with a running balance.** A running balance depends on
  where the reader started and is meaningless against a filtered list; that is presentation, and it
  belongs to phase 8's report.
- **`Section.JOURNAL` is separate from `CHART_OF_ACCOUNTS`**, for the reason `INVENTORY` is separate
  from `PRODUCTS`: seeing the list of accounts is close to harmless, while seeing what has posted to
  them is every financial figure in the business. No grants seeded — default-deny.
- **`AccountSystemKey` gained three values.** `OUTPUT_VAT` and `INPUT_VAT` are Q14's;
  **`DEPRECIATION_EXPENSE` discharges the step 5 obligation** — it is the handle a depreciation run
  will need, and creating it is not building the run.

### ⚠️ Obligations this step created

- **Step 8 — `GOODS_RECEIPT` needs a `JournalSource` value and an amendability answer**, and an entry
  needs its `source_id` once there is a document table to point at. Both above.
- ~~**Step 9 — Q13's second half**~~ — **done in step 9** (`SettlementService.amend` releases most-recent-first). Originally: Editing a Receipt or Payment below its
  already-allocated total must reduce allocations **starting with the most recently applied one,
  working backward**. Nothing enforces that yet because allocations do not exist. This is the item
  most easily forgotten, in the same way the write-off reason was.
- ~~**Step 9 — the invoice postings must supply the VAT dimension**~~ — **done in step 9.** It is *optional* at the ledger
  (the settlement entry legitimately has none), so nothing forces a sales invoice to carry it, and a
  VAT return assembled without it would silently understate. Exempt lines must carry their
  `VatExemptionReason` on the invoice line, which makes exempt turnover by reason a document-level
  report rather than a ledger-level one.
- **The first ledger controller must expose `postManualEntry`, not `post`.** `post` takes a source
  and is the entry point every typed transaction uses from inside the core; `postManualEntry` takes
  no source and is the shape a request from a person can be turned into. Same class of caution as the
  `ProductService` `...For(viewer)` convention.
- **`deactivate` on the chart of accounts still does not check the balance.** Its Javadoc said it
  could not, because there was no ledger; there is one now. The intended behaviour was stated then —
  *warn* rather than refuse, since taking a populated account out of use before a rearrangement is
  legitimate — and nothing implements it yet.

---

## Step 8 — done (purchase invoices, goods receipts, GR/IR, variance, FIFO)

Commit `c6e2513`, migration `V16`, and **ADR 0008**, which answers all three blocking questions
together because they share one principle: **a posting that reflects a physically verified event does
not change after other things have come to depend on it.**

### The three answers, as built

- **ADR 0004's open item — a purchase price variance account, not retroactive lot re-costing.** The
  lot keeps the unit cost it was received at; when the invoice lands at a different price the
  difference posts to `Purchase price variance`. Re-costing a lot that FIFO has already consumed into
  posted COGS is the same problem as editing a posted entry, expressed as a number instead of a
  document. The account is in the **COGS group** so gross margin reflects it, is **not**
  `expected_to_clear` (a variance balance is a result, not a discrepancy), and carries either sign —
  an invoice *below* the expected price is a credit variance, and forcing it positive would hide the
  good news with the bad.
- **Q17 — aggregate stock may go negative, flagged not blocked.** A single lot still cannot, by the
  V12 CHECK. What FIFO cannot fill is the difference between `quantity_requested` and
  `quantity_filled` on the consumption, `stockOf` subtracts outstanding shortfalls so a product
  genuinely reads **−2** rather than 0, and `consumptionsWithShortfall()` is the query phase 8's
  Clearing Checks reads. **No COGS is posted for the shortfall** — there is no lot to take a cost
  from, and reaching for the last purchase price would be the silent guess rule 7 forbids — so COGS
  is understated while a shortfall stands and the flag is what says so.
- **Q39 — a Goods Receipt is immutable, corrected by reversal.** `GOODS_RECEIPT` is deliberately
  absent from `journal_source_is_amendable`, so the existing enum↔function test proves the two agree
  without the function changing at all. `GoodsReceiptService.reverse` un-receives the lots and posts
  the mirror together, and **refuses outright** once anything has happened to them.

### The variance can only ever arise in one direction, and that is the design

A receipt line matched to an invoice line that already exists **takes its unit cost from that
invoice** — `NewGoodsReceiptLine` refuses to state one, and the refusal says why. So invoice-first
clears GR/IR exactly and produces no variance at all; goods-first is the case ADR 0008 exists for.
That asymmetry is worth remembering, because it is what makes "the lot keeps its cost" a rule rather
than a compromise: the lot's cost is only ever provisional when nothing better existed at the time.

### GR/IR matching happens at document creation, and there is deliberately no later matching

Whichever document is created second names what it settles: a Goods Receipt names the invoice line it
delivers against, or a Purchase Invoice names the receipt lines it pays for. There is **no separate
"match these two later" operation** — it would need its own journal entry belonging to no document,
and an unmatched GR/IR balance is already exactly what ADR 0004 says it is. Both halves are queryable
(`linesAwaitingDelivery` / `linesAwaitingInvoice`) and that is what phase 8 reads. **⚠️ Adding
after-the-fact matching is a real feature with a real decision attached (whose document is the
variance entry?); it belongs with Clearing Checks, not with a later step reaching for it casually.**

`gr_ir_match` is a table rather than a nullable foreign key either way, because brief §6 handles
partial delivery across several days: one invoice line is routinely settled by several receipts, and
a supplier billing in instalments splits one receipt across two invoices. **It carries no money** —
the variance is stored per invoice *line*, computed as the residual that makes that line's debits sum
exactly to what the supplier charged, so an unbalanced invoice is impossible by construction.

### A rounding residual that is accepted rather than engineered away

Every posted amount is rounded once at its own line. So when one receipt line is matched by two
invoices, the two rounded portions can sum to a cent more or less than the single rounded amount the
receipt credited, and **that cent stays in GR/IR clearing**. Accepted deliberately: GR/IR is
`expected_to_clear` and `ledger.rounding.threshold` already exists to say what residual is noise,
which is the mechanism brief §6 defines for exactly this. The alternative — a running-total clearing
scheme with per-line cleared amounts — adds a column that must agree with the postings and buys a
cent.

### The step 7 obligation about `source_id`, discharged by deciding against it

**There is no `source_id` on `journal_entry`, and that is the answer rather than an omission.** Two
reasons: an entry from an immutable source cannot be `UPDATE`d after posting, so the id would have to
be known before the document existed; and storing the link on both sides would be two copies of one
fact, free to disagree — the argument that keeps `normal_balance_side` off `account`. The link lives
on the **document** (`journal_entry_id`, UNIQUE), one direction only, exactly as V15 stores one
direction of the reversal link and queries the other. `source` still says what *kind* of document
produced an entry, which is what Q13's policy is keyed on, and each document service answers
`findByJournalEntry`.

### A fourth serialized-unit status, and a freed serial number

Reversing a delivery of serial-tracked machines had no truthful status available: `IN_STOCK` is
false, `SOLD` is false, and `WRITTEN_OFF` would put a loss that never happened into the shrinkage
report the single write-off account was chosen over three *for*. So **`UNRECEIVED`**, and it is the
one status that **does not hold its serial number**: the commonest reason to reverse a delivery is
that it was entered wrong, and re-entering the same machines correctly must not be blocked by the
mistake. The V12 UNIQUE constraint became a **partial unique index**, and
`SerializedUnitStatus.holdsItsSerialNumber()` is the single statement of the rule that the Java check
and the index both read.

### The duplicate-invoice-number rule is a trigger, and the reason generalises

A partial unique index cannot express it. Two documents legitimately share a supplier's number — the
reversing document carries the original's, and once an invoice has been reversed, re-entering it
correctly under the same number is the ordinary thing to want. **Whether a row is superseded depends
on whether another row points at it**, which no index over this row's own columns can see, and a
`superseded` flag maintained beside `reversal_of_id` would be the second copy of a fact this schema
keeps refusing to create. Recorded in the migration README as a rule, not an incident.

### What was built beyond the four things the step named, and why

Both were flagged as judgement calls rather than added quietly:

1. **Expense lines on a purchase invoice.** Without them a coffee retailer cannot record electricity,
   rent or an accountant's fee at all, and step 9 is the sales side. The line **names its account**;
   nothing is inferred. **Brief §7's automatic categorisation is deliberately not built** — the
   suggest-from-product-then-supplier-then-`Unclassified` rule is about *choosing* a destination for
   an invoice nobody typed, and it belongs with the myDATA import that creates those. Nothing lands in
   `Unclassified — Needs Review` by accident here, which is the failure mode a half-built version
   would have.
2. **Reverse charge on the purchase side.** A Greek retailer importing from the EU is ordinary, and
   posting input VAT the supplier never charged would reclaim tax nobody paid. Q14 already settled
   that it needs no new structure. **It is a flag on the line and is never inferred**, but it must
   agree with the supplier's `VatStatus` — required for `INTRA_EU_B2B`, refused for anything else,
   and a disagreement is refused in both directions rather than resolved.

Every invoice line states **either** a VAT class **or** a VAT exemption reason, never both and never
neither: a line with no treatment cannot be filed from, and one with both states two legal positions
about the same money. A purchase from outside the EU, where import VAT settles at customs, is an
exempt line carrying the article that actually applies.

### Obligations discharged this step

- ~~**Step 8 — a lot needs its source document reference**~~ — `inventory_lot.goods_receipt_line_id`,
  nullable and UNIQUE. Null means no NovoCore delivery created it, which is phase 2b's opening stock
  and nothing else once that is done. **One direction only**: `goods_receipt_line` has no `lot_id`.
- ~~**Step 8 — FIFO must use `lotsOf`'s order**~~ — acquisition date then id, read off the same
  repository method, narrowed to lots at a *sellable* location. Selling out of Damaged Goods is not a
  decision a costing rule gets to make quietly, so a product with five damaged units still records a
  shortfall.
- ~~**Step 8 — `GOODS_RECEIPT` needs a `JournalSource` value and an amendability answer**~~ — both,
  above.
- ~~**Step 8 — an entry needs its `source_id`**~~ — **decided against, with reasons.** See above.
- ~~**`deactivate` does not check the balance**~~ — it does now, and **warns rather than refuses**,
  which is what step 3 said it should do once a ledger existed. The warning is the *return value*, so
  a caller cannot fail to receive it, and it is also written to the audit log. `JournalService` is
  resolved through an `ObjectProvider` because `JournalServiceImpl` depends on
  `ChartOfAccountsService` — a constructor dependency the other way would be a bean cycle.

### ⚠️ Obligations this step created

- ~~**Step 9 — a sales invoice produces two entries, not one**~~ — **as built.** `InventoryService.consume` posts
  its own COGS entry, because reducing lots without posting is the "half is worse than neither"
  problem the write-off settled. So a sale posts revenue in one entry and cost in another, linked by
  the consumption record. That is an ordinary arrangement and it is stated here so step 9 does not
  discover it as a surprise.
- ~~**Step 9 — serialized consumption**~~ — **done in step 9**, with no FIFO and no shortfall. `consume` refuses a serial-tracked product outright, naming
  step 9: selling an identified unit means marking it `SOLD`, and brief §5 requires the customer and
  invoice on it at that point. `SerializedUnitStatus.SOLD` is still unreachable.
- ~~**Step 10 — Q18 is now constrained**~~ — **answered in step 10 as ADR 0010**, within the shape
  this constraint set: allocation is computed against the lot's received cost, and the share belonging
  to stock already costed out goes to `Landed cost variance` rather than back into a posted COGS.
- **Phase 8 — two new checks have their queries.** `consumptionsWithShortfall()` for Q17's flag, and
  `linesAwaitingDelivery()` / `linesAwaitingInvoice()` for the two halves of a non-zero GR/IR
  balance. The checks themselves are still phase 8's to write.
- **The first purchasing controller** must expose the document services and not
  `InventoryService.receive`/`unreceive`, which are the lower layer — the same class of caution as
  `postManualEntry` versus `post` and the `ProductService` `...For(viewer)` convention.

---

## Step 9 — done (sales invoices, credit notes, settlements, open items, rounding)

Commit `29e9dcd`, migration `V17`, and **ADR 0009**. Step 9 was the first step blocked on no
question; what it carried instead was a list of seven obligations recorded across steps 3 to 8.
**All seven are discharged**, and four questions were answered on the way in — Q10, Q15's
remainder, Q16 and Q26 — plus Q31 confirmed with no change needed.

### The one idea the rest of the step leans on

**Open item matching is a layer over Accounts receivable and Accounts payable, not a second ledger
beside them. Documents post; allocations post nothing.** A sales invoice posts, a receipt posts,
and saying which one paid the other would be an entry debiting and crediting the same control
account for the same amount.

Everything unusual about the settlement slice follows from that one line:

- **No document stores an open amount and nothing stores a "paid" flag.** An open amount is gross
  less what has been allocated, computed on every read — the stance that keeps a balance off
  `account` and a stock figure off `product`.
- **An allocation can be reduced or released freely**, which is exactly what makes **Q13's second
  half implementable at all**. Had an allocation posted, every release would need a reversal and a
  corrected receipt would produce a cascade of entries describing bookkeeping rather than money.
- **`release` deletes a row** — the one place in this schema that happens, and right here for the
  reason the rest refuses it: an allocation is not a record of an event, it is a statement about
  the current relationship between two documents. The audit log records it.

### The seven obligations, discharged

1. ~~**Q13's second half**~~ — `SettlementService.amend` releases allocations **most-recent-first**,
   reducing the last one partially when that is enough, every release audit-logged with the
   allocation it touched. Most-recent-first because the earlier allocations are the ones somebody
   deliberately matched. `allocation_order` exists because `created_at` cannot answer "which was
   last" when several are created in one transaction.
2. ~~**The invoice postings must supply the VAT dimension**~~ — every Output VAT line carries its
   class and taxable base, per line and summed by class. Proven by a test that puts two rates on
   one invoice and reads them back separately through `vatTotals`.
3. ~~**A sale produces two entries**~~ — revenue from `SalesInvoiceService`, cost of goods sold from
   `InventoryService.consume`, linked by the consumption the line points at.
4. ~~**Serialized consumption**~~ — a line for a serial-tracked product names its machines. Each is
   costed at **its own lot's cost with no FIFO** (brief §5's explicit exception), marked `SOLD`, and
   carries the buyer and the invoice line. Biconditional by CHECK, so the status cannot be reached
   without the document and the document cannot be recorded on a unit that was not sold.
   **No shortfall on this path**: aggregate stock may go negative because "how many are there" can
   be wrong, but "is machine 1234 on the shelf" cannot be, and nothing a later delivery brings could
   back it. So it refuses.
5. ~~**`BundleService.dissolve` on a sold bundle**~~ — discharged by making it **safe** rather than
   by refusing it. The decomposition is **materialised** on the invoice at the moment of sale, so a
   recorded invoice does not depend on the definition still existing. Brief §5's "alias forward,
   never rewrite history" holds with no alias table, because there is no history to rewrite.
   ⚠️ **The obligation this creates in exchange:** a dual-level revenue report must read
   `SalesInvoiceLineView.components()` and **never** `BundleService.componentsOf`.
6. ~~**Q15's remainder**~~ — answered properly, see below.
7. ~~**Q16 and Q26**~~ — both built, see below.

### Q15's remainder, answered: confirm at entry, record the confirmation, no queue

| difference against the document's stated total | what happens |
|---|---|
| zero | nothing |
| at or below `ledger.rounding.threshold` | posts to `Rounding differences` automatically |
| above the threshold | **the document is refused**, naming both totals, until the caller says who accepts it and why |

Three reasons a queue lost:

1. **A queue is a second copy of state** — created when the condition arises, removed when it is
   resolved, and wrong the day those fall out of step. The same argument this schema has made
   against `normal_balance_side`, stored balances, and a `superseded` flag.
2. **The person who can explain the difference is holding the document**, not whoever opens a queue
   next week. Confirming at entry is rule 7 applied where it works best.
3. **A bare flag loses the resolution**, so the confirmation is stored (`rounding_accepted_by`,
   `_at`, `_note`) and "somebody looked at this" becomes a fact in the data.

`rounding_needed_review` is **stored, not derived**, because the threshold is operator-changeable
and a later change must not retroactively alter which invoices somebody had to agree to.

**The difference always posts**, so Accounts receivable agrees with the document the customer
holds — the one outcome open-item matching cannot survive is disagreeing with it.

**The rule this generalises to:** where the ambiguity is visible at entry, confirm at entry and
record the confirmation; where it is a consequence the operator cannot fix at entry (Q17's
shortfall), flag it on the record and provide a query. **Neither is a queue.**

### Q10, answered: the retail customer is seeded and structural

`customer.system_key`, one value, `RETAIL_WALK_IN`, seeded as "Πελάτης Λιανικής". Step 5's fear was
right and the answer is not "no catch-all" — it is that the alternative is a person creating one by
hand on day one, which produces exactly that row with nothing able to tell which one it is.

- **Findable by machine.** The column is `insertable = false, updatable = false` on the entity, so
  no service path can create a second keyed row — `AccountSystemKey`'s stance.
- **Protected.** Undeactivatable by CHECK (not merely by the service, so it holds against `psql`),
  and **refused on both sides of a merge**. Brief §5's alias-forward is about two records of one
  real party; this is the *absence* of a party, so aliasing it into somebody would attribute every
  anonymous till sale to one named person. Merge is still not built; the rule is recorded on
  `CustomerSystemKey` so whoever builds it consults rather than rediscovers it.
- **VAT treatment fixed** at `DOMESTIC`, no VAT number, no exemption reason — three CHECKs.
- **Not a default.** Nothing falls back to it.

### The sales invoice

- **Recorded, not issued.** Go is the invoicing system of record until phase 11, so
  `document_number` is Go's and `stated_total` is what Go's document says. Immutable (Q13), and
  doubly right here since the document exists outside NovoCore.
- **`SalesChannel` picks the revenue account** — the only place channel exists in the model, which
  is why step 3 split Sales and Sales returns three ways. A `SERVICE` product credits `Services`
  instead; `ProductType` deciding real behaviour, for the third time.
- **`SettlementMethod` picks the debit account** — brief §6's settlement automation. Cash and the
  partner clearing methods debit their own account and the invoice is **born fully settled**;
  `BANK_DEPOSIT` and `ON_ACCOUNT` debit AR and it is an open item. Bank deposit deliberately does
  not settle on entry: a customer saying they transferred is not the money arriving.
- **The cash limit is hard-blocked**, not flagged — the one place in this design where a check
  refuses instead of asking for confirmation, because the confirmation nobody can give is the
  legality of the transaction (N. 5301/2026, penalties to double the cash amount).
- **`vat_class_source` is stored**, so "why is this line at 13%?" stays answerable a year later,
  after the customer's override has changed and the product's default with it.

### The credit note (Q26)

Its own transaction type, referencing the invoice it corrects and each line referencing the invoice
line it credits — which is what supplies the rate, the product and the channel. It debits the
channel's `Sales returns` account, **always credits Accounts receivable** even against a cash sale
(the money is owed back until it is refunded; crediting the till takes out money nobody handed
over), and is immutable once issued.

**The VAT credited is the rate the sale charged**, never re-resolved. A credit note issued after
the customer's override changed would otherwise return VAT at 13% against output collected at 24%.

### A stock return is not a reversal

New in step 9 and worth stating as a distinction rather than a mechanism:

| | reversal | return |
|---|---|---|
| says | this consumption should not have happened | the sale was real and the goods came back |
| quantity | the whole of it | any part |
| how often | at most once, by UNIQUE | as often as goods come back |
| posts | an exact ledger mirror | an ordinary entry, debit Inventory credit COGS |

Both are rows in `stock_consumption`, told apart by which of `reversal_of_id` and
`returns_consumption_id` is set; a CHECK refuses both, and a trigger holds the total returned within
what was taken. Stock returns **at the cost it left at**, read off the consumption's own stored
lines — step 10 will move a lot's unit cost, and returning at a later cost would revalue stock
through a credit note. `reverseConsumption` now refuses a return row outright, because restoring
quantities is the opposite of what un-making a return would need.

**A credit note that restored stock is not reversible** — ADR 0008's principle in the other
direction. The goods are on a shelf, in a lot FIFO may have sold from again. A price-only credit
note reverses normally.

### Receipts and Payments share one table, deliberately

Structurally one thing: money moving between one of our accounts and one counterparty's sub-ledger.
`SettlementDirection` decides the side of the entry and which `JournalSource` it carries, so **Q13's
per-source policy is untouched and the ledger cannot tell**. `PartyType` is a separate dimension
because all four combinations are real — receipt from a customer, payment to a supplier, refund *to*
a customer against a credit note, refund *from* a supplier. Folding them into one enum makes the
last two unrepresentable, which is how a refund gets recorded as a negative receipt.

**When to split them:** the first column that belongs to one and not the other. There is none today.

### Q16, answered: unallocated credit is a standalone document that posts nothing

The money is already in Accounts receivable — the receipt that overpaid put it there.
`customer_credit` says whose it is and that it is available, which is the open-item layer's job.

**It is created only when the caller says so.** A receipt whose allocations come to less than its
amount means either "the customer overpaid" or "nobody has finished matching this remittance", and
guessing between them is what rule 7 forbids. The unmatched case stays queryable
(`withUnallocatedAmount()`), which is brief §6's "unmatched lines flagged for Clearing Checks".

### Thirteen new account system keys, and why a "deliberately small" set grew by that much

The criterion has not moved — a key exists when NovoCore's own posting rules must locate a specific
account at runtime. Step 9 is simply the first step where the software chooses a revenue account
and a settlement account without a person naming one: three channel Sales, three Sales returns,
`Services`, and the six accounts `SettlementMethod` resolves (Cash, three partner clearing, PayPal,
Stripe). **No key for `Cost of service sold`** — nothing computes a service's cost, so no rule
resolves it, and a key is a promise that code which does not exist can reach the account.

### Two new sections

`SALES` and `SETTLEMENTS`, separate from `CUSTOMERS` for the reason `PURCHASING` is separate from
`SUPPLIERS`, and separate from each other because a settlement reaches both sub-ledgers at once. No
grants seeded — default-deny.

### Q31, confirmed: single role per user stays

No change needed to what step 4 built. Confirmed against the current team shape (Owner, Admin, one
operational Remote/Order Staff role); no foreseeable need for anyone to hold two roles. It stops
being the cheap-now/expensive-later item it has been since step 5.

### ⚠️ Obligations this step created

- **A dual-level revenue report reads the stored components, never `componentsOf`.** See obligation
  5 above. This is phase 8's, and it is the one thing that makes materialised decomposition safe.
- **Phase 6 — the Bank Aggregator feeds this open-item layer.** `BANK_DEPOSIT` invoices stay open
  by design until the adapter confirms the matching incoming transaction; `withUnallocatedAmount()`
  is what auto-matching will read and reduce.
- **Phase 8 — three more checks have their queries.** `withAcceptedRoundingDifference` (Q15's
  flag), `withUnallocatedAmount` (unmatched remittances), and `openItemsFor` (aged debtors).
- **The first sales controller** must expose the document services and not
  `InventoryService.consume`/`returnConsumed`, which are the lower layer — the same class of caution
  as `postManualEntry` versus `post`.

### Two defects the tests caught, both fixed at the root

Recorded because the pattern matters more than the incidents, and both were found by tests written
against the intended behaviour rather than against the code.

1. **A return into a serial-tracked lot tried to restore a quantity.** A serial-tracked lot stores
   none — the quantity *is* the count of its on-hand units (V12) — so `restore` threw. The fix is
   the rule stated where it belongs: for a serial-tracked lot the stock comes back by the units
   changing status, and restoring a number there would be a second copy of what the units say.
2. **A credit note's open amount ignored what it had been spent on.** A credit note is the one open
   item settleable from *either* side — a target when a refund pays it out, a source when it is set
   against an invoice — and counting only allocations *against* it would have let the same credit be
   both refunded in cash and applied to an invoice, each half looking correct alone.

---

## Step 10 — done (freight / landed cost allocation)

Commits `cf6f1e4` and `6f06cf8`, migrations `V18` and `V19`, and **ADR 0010** and **ADR 0011**.
Q18 — the only question that had been blocking a numbered step — is answered, and a defect the answer
introduced was found by review and closed in the same session.

### Q18, answered: a lot's share is split by what is still in the lot

Brief §4 allocates freight and duty out of `Freight / Landed Cost — Unallocated` into the lots they
delivered, proportionally by value. V4 created that account and flagged it `expected_to_clear`; step 8
gave a carrier's invoice somewhere to land. **Nothing had ever cleared it.** This is what does.

| the part of a lot's share belonging to | goes |
|---|---|
| stock **still on hand** | onto that lot's unit cost, normally |
| stock **already gone** | to a new `Landed cost variance` account |

The second row is the whole of Q18. That share cannot raise a unit cost, because the units it is about
are not in the lot; and it cannot be added to the cost of goods sold that took them out, because that
entry is immutable (ADR 0006) and re-costing consumption already costed out is what ADR 0008 refuses.
So it posts openly, to the exact counterpart of `Purchase price variance`, one position along in the
same group and not `expected_to_clear` for the same reason.

**A fully-sold lot may still be named**, and all of its share goes to variance. That is the case Q18
exists for; refusing it would leave real freight sitting in an expected-to-clear account forever.

### A lot's cost is now two figures, and the received half is frozen

`inventory_lot.unit_cost` became `received_unit_cost` and **stops changing**;
`allocated_landed_unit_cost` accumulates beside it; the carrying cost is their sum, computed on read
and deliberately not a third column.

The freezing is load-bearing rather than tidy. If allocation were computed against the *carrying* cost,
the first freight invoice would move the proportions the second one divides by, so two invoices
covering one shipment would split differently depending on the order they were entered in — and nobody
would ever see that happen, they would simply have costs that could not be reproduced. It also makes
each allocation's basis recomputable from frozen inputs, which is why `freight_allocation_line` stores
no basis column.

`goods_receipt_line.unit_cost` is untouched and is **not** the same fact: that is what a delivery
document said and one half of every GR/IR variance, while this is what a lot was opened at. They
coincide for every lot a delivery created and cannot for phase 2b's opening stock — which is exactly
the case a rule reading the delivery document instead would have no answer for.

### What an allocation is allocated out of

A **purchase invoice expense line pointed at `Freight / Landed Cost — Unallocated`**, not a bare
amount. Naming the source is what makes "how much of this freight is unallocated" answerable and what
stops the account being credited below what was debited into it — the GR/IR match's shape. One source
line per allocation, so a multi-line freight invoice keeps a remainder per line; many lots across many
purchase invoices, which is the consolidated-shipment case; and the lots are always **named**, never
inferred from suppliers and dates (rule 7).

Refused rather than resolved: a line booked to another account, an invoice that has been reversed, more
than the line charged, a lot received at zero cost (proportional-by-value gives it no share), a lot from
a reversed Goods Receipt, and any currency mismatch.

### ⚠️ ADR 0011 — the asymmetry between returns and reversals

**Found by review, measured rather than reasoned about.** The review question was whether reversing an
allocation on a lot with intervening sale-then-return activity could produce a wrong figure. It cannot,
and the reason matters: a sale stores the cost it took stock out at, a return reads that same figure
back (ADR 0009), so the pair nets to zero in both Inventory and COGS and there is no freight left inside
a posted COGS to be inconsistent with. Reversal is also value-neutral on the relationship, because its
guard forces `remaining` to equal `remainingAtAllocation` and the mirror credit equals the `capitalised`
computed from that same figure.

**But probing it found a real defect next door, needing no reversal at all.** Three operations put stock
back into a lot, all at the cost it left at; if an allocation landed while the stock was out, the lot
carries those units higher than what was debited back. Measured on a lot of 10 at €10 with 2 sold and
€100 allocated: lot valuation €200, Inventory €180, on all three paths, never clearing.

The fix is deliberately **not uniform**, and the asymmetry is the part to remember:

- **A return catches up.** It says the sale was real, so the allocation's split was right at the time
  and only the returning units' share is owed. Debit Inventory, credit `Landed cost variance`, in the
  same entry, computed from the consumption line's stored cost against the lot's frozen received cost —
  so nothing new is stored and it is zero whenever the lot has not moved. COGS is still credited exactly
  what was debited, so ADR 0009 stands. Refusing was available and rejected: a return is driven by a
  credit note at the till, and making it wait on a freight document ends in returns going unrecorded.
- **A reversal refuses.** It says the movement never happened — which would mean the allocation computed
  the wrong *split*, not merely posted its counterpart to the wrong account. `reverseConsumption` and
  `reverseWriteOff` refuse once the lot has been re-costed, naming the remedy: reverse the allocation
  (permitted, the quantity has not moved), reverse the movement, allocate again. **A test walks that
  sequence and checks the end state is exactly right**, because a refusal whose named remedy has never
  been tried is a refusal that might not have one.

**V19 exists because of this:** `stock_write_off` now stores its unit cost. V15 deliberately stored no
amount, on the argument that the entry is the honest source — true, and it stopped being sufficient the
day a lot's carrying cost could move, because the entry gives the rounded amount and not the six-decimal
cost behind it. `stock_consumption_line` has stored its own since step 8 for exactly this reason. The
posted **amount** is still not stored: a historical input has to be kept once it stops being
recoverable, a historical output was always in the entry.

### Obligations discharged this step

- ~~**Step 6 — last purchase price must stop coming from the lot's unit cost**~~ — `lastPurchaseCostOf`
  returns the **received** cost, so a product no longer reads as dearer because its last delivery came
  by air. **One residual difference is recorded rather than hidden:** where a delivery preceded its
  invoice and the invoice disagreed, ADR 0008 keeps the lot at the received cost, so this is the last
  price we *believed* rather than necessarily the last a supplier *invoiced*. Making it the latter needs
  a purchasing-side query — the inventory slice cannot read purchasing, which depends on it — and that
  was **not built**.
- ~~**`Freight / Landed Cost — Unallocated` has a key and nothing clears it**~~ — cleared now, and
  `linesAwaitingAllocation()` is the query phase 8 reads against its balance.

### Code-quality work folded in, both flagged rather than done quietly

1. **`BundleAllocation` became `ProportionalAllocation` in `core-api/shared`.** Freight allocation is
   the same arithmetic, and a second copy would have been a second set of rounding behaviour — the kind
   of difference that surfaces later as a report a cent out with nothing to say which half is wrong. One
   production call site and one test moved with it.
2. **`PurchaseInvoiceLineViews` extracted**, because a second service in the slice now projects invoice
   lines and two private copies of one projection diverge the first time a field is added to either.

### ⚠️ Obligations this step created

- **Phase 8 — one more check has its query.** `linesAwaitingAllocation()` against the
  `Freight / Landed Cost — Unallocated` balance, the same shape as the two GR/IR halves.
  `InventoryService.lotsWithAllocatedLandedCost()` answers "which stock is carrying freight" when a
  valuation comes out above the invoices behind it.
- **The first purchasing controller** must expose `FreightAllocationService` and **not**
  `InventoryService.applyLandedCost` / `removeLandedCost`, which are the lower layer — the same class of
  caution as `postManualEntry` versus `post`. Those two move a lot's cost with no entry behind them.
- **A stock valuation report should reconcile against the Inventory control account**, which is now a
  real check rather than a tautology: ADR 0011 made them agree, and the test that asserts it is the one
  that found the defect.

---

## Step 11 — done (the shared email service)

Commit `b542cf7`, migration `V20`. Credentials and both configuration decisions were supplied at
the start of the session, so this was built to the answers rather than around them.

### The two decisions, as built

- **All email configuration lives in Settings**, including the password — decided deliberately
  against the environment-variable alternative the last close-out raised. The exposure argument
  for the environment was that Settings sits inside the backup and step 12 copies backups to
  Google Drive; that does not apply, because access to that Drive is scoped to one person.
- **The sending address is `erp@novotrade.gr` and is unmonitored**, so **`smtp.reply-to` is
  `kostas@novotrade.gr` and applied to every outgoing message**. This is the whole point of the
  step, and it is why the setting is **required rather than optional**: treating it as optional
  would mean a missing value quietly routing every customer reply into a mailbox nobody opens — a
  failure with no symptom.

### The interface, and what it deliberately cannot do

`EmailSender` is the single door. `EmailMessage` **has no `from` and no `replyTo` field**, so a
caller cannot choose either; both are resolved from Settings at send time and applied identically.
A caller that could override them would be able to send as something else and route replies
somewhere unread, which is exactly the scattered-configuration failure `CLAUDE.md`'s shared-service
rule exists to prevent. A test asserts those record components do not exist.

**An ArchUnit rule confines `jakarta.mail` and `org.springframework.mail` to
`gr.novotrade.novocore.core.email`**, so "never configure SMTP or send email directly from within a
module" is a build failure rather than a convention. **Proven to fail**, the same way every other
guardrail here was: a probe class in `..core.settings..` holding a `JavaMailSenderImpl` tripped it,
naming the field, the constructor call and the return type. Probe deleted.

### Asynchronous by an outbox table, per rule 4

`send` writes a row **in the caller's transaction** and returns; it never opens a socket. A
scheduled dispatcher does the SMTP conversation with **no transaction open across the network**.

The transaction detail is the reason it is a table and not an in-memory queue: a message is queued
**if and only if** the operation that queued it committed. An approved Purchase Order always sends
its PDF; a rolled-back one never does, with no compensating logic anywhere. An in-memory queue gets
both wrong in opposite directions. A test rolls back a transaction around `send` and asserts no row
survives.

- **Three transactions per message, never one**: claim (commits before any socket opens), send,
  record. `EmailOutbox` is a separate bean from `EmailDispatcher` for a concrete reason, not
  tidiness — a `@Transactional` method called from another method of the same object bypasses the
  proxy entirely, so the annotations would have done nothing.
- **The claim uses `FOR UPDATE SKIP LOCKED`.** Currently belt and braces: one instance, one
  scheduler thread, `fixedDelay`. It costs a clause and prevents sending the same email twice the
  first time somebody runs two instances during a migration.
- **A crash between the server accepting a message and the row being marked sent produces a
  duplicate on retry.** Stated rather than hidden. It is the right direction to fail — a
  confirmation arriving twice is a nuisance, never arriving is a lost order — and avoiding it would
  mean holding a transaction open across a network conversation.
- **Scheduling is enabled in `app`, not in the core.** So the core's tests hold a fully wired
  dispatcher that never fires on its own and is driven by calling it. Nothing in the email tests
  sleeps, and the retry assertions are exact rather than approximate.

### Retry, and giving up in public

Exponential backoff doubling from 30s to a 15-minute ceiling, 5 attempts — roughly eight minutes,
which covers a mail server restart without leaving a genuinely undeliverable message retrying all
day. `max_attempts` is **copied onto the row at queue time**, so lowering the limit cannot
retroactively fail messages already waiting.

- **A message that runs out of attempts is `FAILED`, kept and queryable**, never deleted and never
  left `PENDING` forever (rule 8). A CHECK refuses a `FAILED` row with no stated reason — the row
  this table exists to prevent is a silent drop wearing a status.
- **Re-queueing is manual.** Nothing retries a failed message automatically, because it failed for
  a reason that is still true; automatic re-queueing turns that into a loop that hides the problem.
  `retry` resets the attempt count rather than granting one more, so a fix can actually be
  confirmed.
- **Only two failures are treated as permanent**: an address the server rejected outright, and a
  message that could not be constructed. Authentication rejection is *not* — a wrong password would
  otherwise mark a whole backlog `FAILED` the moment a password expired, turning one transient
  problem into dozens needing individual attention. The attempt limit surfaces it within minutes
  anyway.
- **An unconfigured system consumes no attempts at all.** Nothing is claimed, so the moment the
  configuration is corrected everything waiting goes out. The dispatcher logs the problem at WARN
  when it changes and DEBUG thereafter, so a system waiting for its password does not write a
  warning every fifteen seconds until the log becomes the thing nobody reads.

### `smtp.start-tls` replaced by `smtp.transport-security`

Step 2 declared a boolean `smtp.start-tls` and nothing ever wrote a value under it, so renaming
cost nothing. **A boolean has two states and there are three**, and the two encrypted ones are not
interchangeable: our server is implicit TLS on 465, and a STARTTLS client pointed at that port
**hangs rather than failing**, so the symptom is a timeout minutes later rather than a refusal.
That is why the property mapping has its own test.

Also stated explicitly rather than relied upon: hostname verification on, TLS 1.2/1.3 only,
STARTTLS **required** and not merely enabled (with only `enable`, a server that declines the
upgrade receives the password in the clear and the send still reports success), and **finite
network timeouts in every mode** — Jakarta Mail's own defaults are infinite, so one hung server
would otherwise block the dispatcher thread permanently with the outbox showing nothing wrong.

### The password is not in git, and that is not the same as not being in Settings

V20 seeds host, port, transport security, username, sender address, sender name, Reply-To and the
four retry settings. **It does not seed the password.** A migration is a file in git; a credential
in git is in git permanently, readable by anyone who ever clones the repository, present in every
CI checkout, and not removable by editing the file.

So the password reaches Settings once from `NOVOCORE_SMTP_PASSWORD` — the same route
`NOVOCORE_BOOTSTRAP_OWNER_PASSWORD` takes — and the variable can then be removed. **The decision
that it lives in Settings is unaffected: the environment is how it arrives, not where it is kept.**
`SmtpPasswordBootstrap` differs from `InitialOwnerBootstrap` in two deliberate ways: a missing
value **does not stop the application** (an instance that cannot send email is entirely usable,
unlike one nobody can log into), and a value that is set but ignored **is logged**, because
somebody editing `.env` to change a password and finding authentication still failing needs to be
told where the value actually lives.

A test asserts that **no migration file anywhere inserts `smtp.password`** — checked against the
files rather than the table, because "is it seeded?" is a question about what is committed, and the
live table is written to by the email service's own tests.

### Two defects found by tests, not by reasoning

Both are recorded because both were invisible to inspection and are the kind that come back.

1. **`EmailAttachment` sanitised the filename before checking for a line break.** Given
   `june.pdf\r\nContent-Type: text/html`, the directory strip ran to the last `/` — the one *inside
   the injected header* — leaving `html`, a name with no line break in it and nothing left to
   refuse. **Sanitising first can destroy the evidence that a value should have been rejected
   outright.** The check now runs on the raw input.
2. **One unusable outbox row stopped all email indefinitely.** Materialising the claimed batch threw
   inside the claim transaction, so the whole transaction aborted, nothing in the batch was sent,
   and the next cycle claimed the same batch and failed identically — with no message of its own
   ever marked failed. Found because a raw-SQL probe left exactly such a row behind and nine
   unrelated tests went red.

   **The first fix was narrower than its commit message claimed, and a review found a second door
   into the same stall.** Recorded in full below, because "we fixed the poison pill" is exactly the
   kind of half-true note that stops the next person looking.

### The batch-wide stall, and what actually closes it

Worth stating precisely, because the failure is severe (all email stops, silently, forever) and
reachable by more than one route.

**What the guard covers.** Everything thrown while rebuilding a stored row into an `EmailMessage`
— which is a *class* of failure, not the one case that was reproduced: any invalid `to`, `cc` or
`bcc` address, a blank or over-long subject, a null body, a bad attachment filename, an empty
attachment. A test now stores three differently-malformed rows in one batch (no `@`, no domain
suffix, and a bad address in `cc` rather than `to`), each of which passes every database CHECK, and
asserts all three fail individually while a healthy message in the same batch still goes out.

**The second door, found by review rather than by a test going red.** `attemptStarting` increments
`attempts`, and the schema has `CHECK (attempts <= max_attempts)`. A row sitting `PENDING` with
`attempts` already equal to `max_attempts` **satisfies every constraint at rest** — the service
cannot produce one, since `markAttemptFailed` flips to `FAILED` at the limit and `requeue` resets to
zero, but raw SQL or a restore can. Claiming such a row emits an `UPDATE` that violates the CHECK,
and **that violation lands at flush, when the transaction commits** — after the loop, outside any
`try`, and unrecoverable. It rolls back the entire claim transaction *including the
`markAttemptFailed` writes the catch block had just made: the same batch-wide stall, reached through
a door a `try` around the conversion cannot close, and this time not even leaving a record of which
row caused it.*

**Why prevention rather than catching.** A failed flush poisons the persistence context, so there is
no per-message recovery available once the invalid `UPDATE` has been queued. The fix is a guard
*before* the increment, so the invalid statement is never emitted. Its own test stores exactly such a
row and asserts the healthy message in the batch still sends.

**Known residual, not defended.** A row that cannot be *loaded* as an entity at all still aborts the
batch, because `findAllById` runs before anything can be guarded. In practice that needs a `status`
or `body_format` value the deployed Java does not know — the CHECK constraints make that impossible
within one version, so the realistic route is **deploying a version that adds an enum value, writing
rows with it, then rolling back to the older jar**. Defending it would mean claiming through a native
projection instead of the entity, which is a real cost against a narrow risk. Recorded so the choice
is visible; **if an enum value is ever added to `EmailStatus` or `EmailBodyFormat`, a downgrade is
not safe.**

### Test hygiene worth keeping

- The email tests **empty the outbox** before each test and **restore every setting they
  overwrite** afterwards. These integration tests share one database and are deliberately not
  transactional, and settings are global by nature — the suite's usual "use distinct keys" advice
  has no equivalent. Without the first, a later test's batch picks up messages earlier tests queued
  on purpose and every "this cycle sent exactly one" assertion silently becomes a statement about
  the whole class's history. That is how one test first failed, reporting three sent instead of one.
- **A raw-SQL probe written as a `'{...}'` array literal proved nothing.** PostgreSQL's array-literal
  parser treats a backslash as an escape, so the intended `\n` became a plain `n`, the CHECK had
  nothing to object to, and the row went in. Bind the value as a parameter and build the array with
  `ARRAY[?]`.
- **A test that sets a datasource URL still gets a Testcontainers database.** See item 6 of "To be
  aware of immediately" — the container configuration is picked up by component scanning, not by
  the `@Import` that appears to control it, and `@ServiceConnection` then overrides the properties.
  Worth knowing before writing any test meant to run against something other than a throwaway
  container.

### Verified by hand, beyond the suite

- **V20 applies on the Compose stack**, and `SmtpPasswordBootstrap` was observed in **both**
  branches: storing the password on the first start, then reporting the variable as ignored on the
  next.
- **The real credentials authenticate against `mail.novotrade.gr:465`** over implicit TLS with
  hostname verification on.
- **Two real emails were sent to `kostas@novotrade.gr`** through the full production path — queue,
  dispatcher, real SMTP — carrying Greek text and an attachment. Two rather than one because
  `-Dtest=` made Surefire run the throwaway probe as well as Failsafe. Both probes were deleted.

### Not built, deliberately

- **No HTTP route.** The outbox, the failure list and `verifyConfiguration` all have services and no
  controller, consistent with everything since step 4b. `verifyConfiguration` exists specifically so
  a Settings screen can answer "is email working?" honestly, and is waiting for that screen.
- **No templates, no HTML layout, no localisation.** `EmailMessage` carries a subject and a body,
  and whichever module sends something composes it. The first module with a real template is where
  that decision belongs.
- **No retention policy on sent messages.** Rows accumulate. Worth revisiting alongside step 12.
  **Narrowed by V21** — see below: the growth is now only the inline attachment bytes, and pruning
  them needs no schema change.
- **No `Section` for the outbox.** Nothing reads it over HTTP yet, and a permission guarding
  nothing is a half-built feature.

---

## Step 11, revisited — an emailed document is referenced, not copied (V21, ADR 0012)

Raised as a design question after step 11 landed: does `EmailAttachment` store its own copy of the
bytes, or reference an existing `AttachmentService` record?

**It duplicated.** `email_outbox_attachment.content` was `bytea NOT NULL`, with no link to
`attachment`. V20 said so deliberately and gave reasons that were half right — a generated Purchase
Order PDF and a monthly report genuinely have nothing to reference. What that reasoning missed is the
case that costs the most: a document that is **also** an `AttachmentService` record, where the same
file then sat in two tables and in every `pg_dump`, permanently. **Full reasoning in ADR 0012.**

- **Two shapes, exactly one per row.** `EmailAttachment.stored(id)` references a document and carries
  no bytes; `.pdf(...)` / `.of(...)` carry bytes for a file that exists nowhere else. Enforced by the
  record's constructor **and** by CHECK constraints, so raw SQL cannot write a row that is both or
  neither. Reference-only was considered and rejected: it would move report bytes rather than save
  them, and fill a "documents on core records" table with things that are neither.
- **The recipient is unaffected.** SMTP transmits real bytes either way; the dispatcher resolves a
  reference at send time and `compose` never sees the distinction.
- **Viewing is one action, identical for both shapes.** `attachmentsOf(emailId)` then
  `downloadAttachment(attachmentId)` — same id, same return type, no join for the caller and nothing
  to know about where the file lives. It stays one action if a file that is inline today becomes a
  stored document tomorrow.
- **A deleted document degrades, it does not break.** `ON DELETE SET NULL` — not CASCADE (which would
  delete the record that the message ever had an attachment) and not RESTRICT (which would let a mail
  from 2026 pin a document forever). The history entry still names the file, its size and its
  checksum, and reports it unavailable **with the reason**. Availability needs no extra query: a
  non-null `attachment_id` is itself the proof. Asking for the bytes anyway throws
  `EmailAttachmentUnavailableException`, deliberately distinct from the `IllegalArgumentException` for
  an id that never existed.
- **A document deleted *before* the message goes out fails it visibly and alone**, through the same
  per-message guard that isolates a poison row. A mail is never sent with an attachment silently
  missing — the one failure a recipient could not detect.
- **Validated at queue time, in the caller's transaction.** An attachment id naming nothing is a
  mistake in the calling code, so it fails the operation that made it. Deliberately unlike the SMTP
  configuration, which is *not* checked at queue time.
- **`content_source` is stored, not inferred.** Once the bytes are gone both shapes are a row with
  nothing in it; without the column the history could not say whether a document was deleted or an
  inline copy pruned. Same reasoning as step 9 storing `vat_class_source`.
- **One defect found by reviewing the tests rather than the code:** two of the raw-SQL CHECK probes
  violated *two* constraints at once, and PostgreSQL does not promise which it reports — the
  assertions would have passed or failed on constraint evaluation order. Each probe now breaks
  exactly one, with a well-formed row inserted afterwards to prove the statement shape itself is good.

Docker Desktop was not running at the start of this session — every IT failed with "Could not find a
valid Docker environment", which is worth recognising quickly since it looks like a mass failure of
the code under test.

---

## Q43 — answered and built (V22): rows forever, generated attachments 90 days

One question until V21, two afterwards. While the outbox copied every attachment, "how long do we keep
sent emails?" covered cheap metadata and expensive duplicated bytes together and could not have one
right answer. Referencing separated them, and the two halves took different numbers.

| | Setting | Answer |
|---|---|---|
| Message rows (recipients, subject, status, error, attachment metadata) | `email.retention.message-days` | `FOREVER` |
| Inline copies of **generated** attachments (PO PDFs, reports) | `email.retention.inline-attachment-days` | `90` |

Both live in **Settings**, changeable without a redeploy — the same argument that put SMTP there.

- **`EmailRetention` runs daily** (`@Scheduled` cron, default 03:30). Scheduling is enabled in `app`,
  not the core, so the core's tests drive it by calling `pruneNow()` and nothing sleeps — the same
  arrangement as `EmailDispatcher`.
- **The `UPDATE` is one statement; the guards are the substance.** A prune that removes too much is the
  failure that matters, and there are three ways to get it wrong. Each is a restriction with a test:
  - **`content_source = 'INLINE'`** — a referenced document's bytes belong to `AttachmentService` and
    are **never** pruned here. Widening this would make one service delete another's documents, with
    the symptom being a purchase invoice's PDF vanishing off the invoice because an email mentioned it
    91 days ago. Tested directly: a referenced attachment survives a prune that drops an inline one
    beside it, and the document is still readable afterwards.
  - **`status = 'SENT'`** — a **PENDING** message still needs its bytes (a system waiting months on a
    broken SMTP password must not have its attachments removed from under it), and a **FAILED** one
    keeps them because retrying it is the entire reason it was kept. A retry that cannot re-send the
    attachment is not a retry.
  - **`content IS NOT NULL`** — keeps it idempotent, so the daily run reports zero instead of
    rewriting rows it already cleared and filling the audit log with noise.
- **The state it produces was already built in V21** and needed no schema change, exactly as predicted:
  the history entry keeps the filename and size and reports the file unavailable, distinguished from a
  deleted document by `content_source`.
- **An unreadable setting stops the prune and deletes nothing**, loudly. The only setting in this
  service with no safe default — guessing "0 days" would delete everything and no logging would undo
  it. `FOREVER` is spelled out rather than encoded as blank or `0`, because a blank setting is
  indistinguishable from one deleted by accident.
- **Row deletion is built although it never runs** under `FOREVER`, so the setting is real rather than
  decorative. Attachment rows follow by `ON DELETE CASCADE`. This is a legitimate deletion:
  `CLAUDE.md`'s no-delete stance governs records people rely on, and a retention policy set
  deliberately is the opposite of an accidental loss.

**822 tests passing, `mvn clean verify` exit 0** (up from 802 at step 11's close: +13 for V21, +7 for
V22 retention).

---

## Step 12 — done (automated backups), V23, ADR 0013

A scheduled encrypted `pg_dump`, copied to two Google Drive accounts, pruned on the stated retention
rule, and **proved restorable** — which closes brief §13's "backup restore test" risk rather than
deferring it again. **Full reasoning in ADR 0013.**

### The encryption key is an environment variable, and cannot be anything else

`NOVOCORE_BACKUP_ENCRYPTION_KEY`, AES-256-GCM, applied before a single byte leaves the host. This is
the **opposite** of step 11's decision for the SMTP password and is not a reversal: **the `setting`
table is inside the dump**, so a key kept there would be encrypted inside the artefact it exists to
decrypt. There is no ordering of those steps that terminates.

**⚠️ The obligation this creates:** that key must be recorded **outside this system** — a password
manager. `docker/.env` is gitignored and machine-local, so if it exists only there, losing the host
loses the database *and* every backup of it at once. The app logs this on every start.

A 16-hex-character **fingerprint** is recorded per artefact, so restoring with a rotated key reports
"this is a key rotation" instead of a GCM tag failure, which reads as "your backup is corrupt".

### Off-site is reported separately from success — the headline that matters

`SUCCEEDED` means the artefact was written and checksummed. Whether a copy reached Drive is
per-destination (`backup_upload`), summarised by `BackupView.isOffsite()`. A dump that wrote to local
disk and reached nowhere protects against a dropped table and against nothing else; the service logs
an **error** for exactly that state.

- **An upload failure does not fail the backup** — the artefact is already safe, and discarding a
  good backup over a network error would also make "when did we last dump successfully?"
  unanswerable.
- **`NOT_CONFIGURED` is its own status.** Never set up needs a different response from tried and
  rejected, and every run records a row per destination so a missing off-site copy is visible rather
  than absent.
- **One destination failing never stops the other.**

### Retention, exactly as specified

**7 most recent successful backups, rolling, plus the last successful backup of each calendar month,
forever, uncapped.** Stated positively it needs no month-end logic: *a backup is its month's archive
iff no later successful backup exists in the same calendar month.* A month whose 31st failed archives
the 30th's; a month with no successful backup designates nothing.

- **The calendar zone (`Europe/Athens`) is load-bearing** — 01:30 on the 1st in Athens is the
  previous month in UTC, which would archive the wrong artefact twelve times a year, silently.
- **Only successful runs are candidates** — letting failures fill the rolling seven would evict the
  last good backups during the one week you would most want them.
- **Applied identically to local disk and both destinations.**
- **Pure logic in `BackupRetentionRule`**, unit-tested against explicit dates. Every other component
  can be fixed and re-run; this one's mistakes are already made.
- **`backup_run` rows outlive their artefacts**, so the history is a list of attempts rather than of
  surviving files.

### The restore check asserts the books, not the file

Creates a scratch database, `pg_restore`s into it, then asserts: schema version matches live, row
counts match live for `account` / `setting` / `journal_entry` / `journal_line`, and — the one that
matters — **the restored ledger balances**. Findings are kept on a passing check too, because a green
flag with nothing behind it is the unverified claim brief §13 already objected to. Scratch database
name whitelisted and **refused if it equals the live database**, since the check begins by dropping
it. Weekly by default; the nightly backup is separate.

### Notable implementation decisions

- **Plain `HttpClient`, not `google-api-services-drive`.** The failures that matter are protocol-level
  (expired refresh token, deleted folder, 403 quota) and `StubDriveServer` produces all of them over
  a real socket with no credentials — asserting the uploaded bytes equal the artefact on disk.
- **The plaintext dump never touches disk** — `pg_dump`'s stdout is piped straight through the
  cipher. The one exception is the restore check, owner-only and deleted in a `finally`, stated as a
  trade-off.
- **`CipherInputStream` is deliberately not used**: it swallows the GCM tag failure and would let a
  truncated backup decrypt to a short plaintext with no error. Both tampering and truncation have
  tests.
- **`postgresql-client-17` is in the runtime image and in CI.** The major version **must** match the
  server — `pg_dump` refuses to dump a newer one — so upgrading the postgres image means changing
  the Dockerfile in the same commit.
- **A new ArchUnit rule** confines `javax.crypto` and `ProcessBuilder` to `..core.backup..`.

### Defects found by running it, not by reasoning about it

1. **`RestoreVerifier` called its own `@Transactional` methods** — a self-invocation bypasses the
   proxy, so they would have done nothing, silently. Split into `RestoreCheckJournal`. The same
   lesson step 11 recorded for `EmailOutbox`, rediscovered by writing it the obvious way.
2. **Artefact names collided within one second** — dismissed while designing as pathological, hit by
   the test suite immediately, and reachable in production by a manual backup during the scheduled
   one. Fixed in production code with a `-2`/`-3` suffix.
3. **Avoided rather than fixed:** reading `spring.datasource.url` fails the whole context under
   Testcontainers' `@ServiceConnection`, and is the general case of a dump driven by a property that
   could drift from the pool — faithfully backing up the wrong database while looking healthy.
   `DatabaseConnectionProvider` reads the pool.
4. **The retention rule tests failed four ways at first and all four were the fixture**, which
   restarted backup ids at 1 per month. Worth recording because every one of them looked like a bug
   in the rule.

### Verified, and not verified

- **858 tests passing, `mvn clean verify` exit 0** (up from 822). `BackupIT` runs the real
  `pg_dump`, really encrypts, really restores into a real scratch database and really asserts the
  ledger balances. **(Now 866 after the self-invocation work; see the CI section at the top —
  `BackupIT`'s 16 tests run on CI too, 0 skipped, on a real 17 client/server pair.)**
- ~~**⚠️ Never run against real Google Drive.**~~ **Run for real on 2026-07-29** — both destinations
  `UPLOADED`. See "Step 12, commissioned" below.
- ~~**⚠️ The container image has not been rebuilt**~~ **Rebuilt 2026-07-29**, installing
  `postgresql-client-17 (17.10-1.pgdg26.04+1)`, applying V21–V23 to the live database and taking a
  real dump through it.
- **PostgreSQL 17 client tools were installed on this machine** at
  `C:\Users\kosta\tools\pg17\pgsql\bin` and added to the user PATH. Without them `BackupIT` **skips**
  rather than fails — deliberately, so a missing tool does not teach people to ignore red suites,
  but it does mean a silent loss of coverage worth knowing about.

---

## Proxy self-invocation — now a build failure, and it found two real defects

Raised at step 12's close: the `RestoreVerifier` self-invocation was the **second** time that exact
pattern had bitten the codebase, after step 11's `EmailOutbox`. Rather than rely on it being
rediscovered a third time, it is now enforced.

**`SelfInvocationRulesTest`** — two ArchUnit rules over the production class graph:

1. **A non-transactional method may not call its own class's `@Transactional` method.** The proxy is
   bypassed, so there is no transaction at all. This is the shape that bit us twice.
2. **Nothing may self-invoke a method declaring non-default propagation.** A self-called
   `REQUIRES_NEW` silently joins the caller's transaction instead of starting its own — wrong even
   when the caller *is* transactional, which is the case rule 1 has to permit.

Each rule has a **probe fixture proving it fails**, plus a fixture proving the recommended remedy
does not trip it. Rules nobody has watched reject something are indistinguishable from rules that
match nothing — the lesson from step 4b's vacuous `..core.web..` rule.

**Deliberately allowed:** a `@Transactional` method calling another on the same class with default
propagation. The inner call joins the outer transaction, which is what the code means. The first
draft forbade it and reported **44 violations**, essentially all harmless; a rule that cries wolf 44
times is one somebody deletes. Narrowing to the two shapes above turned that into 6 findings across
3 classes — and **two of them were real defects.**

### 🐛 `AuditLogServiceImpl` — pre-existing, and the serious one

`record(action, entityType, entityId)` and `recordSystemAction(...)` were **unannotated** and
self-invoked the four-argument `record(...)`, which is `@Transactional(propagation = REQUIRES_NEW)`.
That `REQUIRES_NEW` exists — and is documented in that class — so that *an audit entry survives the
rollback of the operation it describes*. Through a self-call it was never applied, so every entry
written via those two overloads **joined the caller's transaction and was rolled back with the very
operation it was recording.** A rejected journal entry or a refused permission is exactly what you
most want recorded, and exactly what was being lost. In the audit log, which is the record of last
resort.

**Fixed:** all three public overloads now carry the annotation and delegate to a private, unannotated
`write(...)`. No self-invocation of an annotated method remains.

**Verified behaviourally, not just structurally — and this distinction is the whole lesson.**
`AuditLogIT` had seven tests and **not one of them involved a rollback**, which is exactly why the
defect survived from the day the class was written. Two new tests now assert the property the
`REQUIRES_NEW` exists for:

- `entriesSurviveTheRollbackOfTheirOperation` — writes through **all three overloads** inside a
  transaction that then throws, and asserts all three entries are still there afterwards.
- `theEntryIsVisibleWhileTheCallerIsStillOpen` — reads through a **separate JDBC connection** while
  the caller's transaction is still open, proving the entry was genuinely committed by its own
  transaction rather than merely happening not to be rolled back. A `JdbcTemplate` would have
  joined the caller's transaction and passed either way.

**Both were confirmed to fail against the reintroduced bug** before being accepted — the defect was
temporarily restored, the tests failed, and the fix was put back. A regression test that has never
been seen to fail is a regression test nobody has verified.

**⚠️ The ArchUnit rules cannot protect this property.** Deleting the annotation from an overload and
calling the private `write(...)` directly is structurally spotless and reintroduces the defect in
full. Only these two tests would notice. Structural rules catch the *shape*; only a behavioural test
holds the *guarantee*.

### 🐛 `BackupRetentionService` — step 12, plus a latent lazy-loading failure

`apply()` is deliberately not transactional (it deletes files and calls Drive) and self-invoked a
`@Transactional` read. Moved to `BackupJournal.retentionCandidates()`.

Fixing it surfaced a second, worse defect on the same path: `removeArtefact` loaded a `BackupRun`
and read its **lazy `uploads` association outside any transaction**. It would have thrown on the
first real prune — which only happens once there are more than `daily-count` backups, a state no
test had ever produced. Now `BackupJournal.artefactToRemove` materialises plain data inside the
transaction, the way `EmailOutbox.claimDue` already documents, and **a new test drives a real prune
end to end** (artefact deleted from disk *and* from the destination, row surviving its artefact).

### `SettlementServiceImpl` — not a defect, restructured anyway

Three findings where private helpers called the public `@Transactional openAmountOf`. Harmless in
effect — every public entry point reaching those helpers is transactional, so the read joins that
transaction — but indistinguishable in bytecode from the shape that is *not* harmless. Split into a
private `openAmount(...)` computation with the public method as the thin transactional wrapper,
which says which is the entry point and keeps the rule sharp enough to be worth having.

### Recorded in `CLAUDE.md` as well

The rules cannot cover `@Async`, `@Cacheable`, `@PreAuthorize` or `@Retryable`, which fail
identically, nor a call reached through a captured lambda. `CLAUDE.md` names the general
anti-pattern, the remedy, and the related trap of returning lazily-associated entities from a
non-transactional method.

**866 tests passing, `mvn clean verify` exit 0** (up from 858; ArchUnit 13 → 18, plus the two
behavioural audit-log tests).

---

## Step 13 — done (the test suite consolidation sweep), ADR 0014

Three things, in the order they were built: a property-based testing harness and the properties it
exists for, property tests over FIFO against a real database, and one whole-scenario test that plays
a trading year and then sweeps every invariant the system has over the resulting database.

**952 tests at this point, `mvn clean verify` exit 0** — 960 once Q45 was fixed, below.

### jqwik could not be used, for the third time in a row, and for the same reason (ADR 0014)

jqwik is a JUnit Platform **test engine**, and `net.jqwik:jqwik-engine:1.10.1` — the newest release
— declares `junit-platform-engine:1.14.4`. Spring Boot 4.1 brings JUnit 6, whose platform artefacts
are `6.x`. There is no jqwik 2. **Verified against Maven Central rather than assumed.**

This is exactly the situation ADR 0002 resolved for `archunit-junit5` and step 11 resolved for
`greenmail-junit5`, and it is resolved the same way: *take the idea, not the artifact.* The
difference is that jqwik has no plain-library form — the engine **is** the product — so the harness
was written: `Gen`, `Property` and `ValueGenerators` in `..core.api.testsupport..`, about 500 lines,
published from `core-api` as a test-jar so `core` uses the same generators and the same shrinking.

**The seed is fixed by default.** 500 cases, the same ones on every machine and every CI run, so a
red build always means a defect rather than today's dice — `CLAUDE.md`'s "a check that cries wolf is
one somebody deletes", applied to a runner rather than to a rule. `-Dnovocore.property.seed=<n>`
explores further, deliberately, and **anything a new seed finds belongs in a named example test**
rather than being left to luck. The breadth a fixed seed costs is bought back in the generators:
roughly a third of every sample comes from a hand-written edge list — zero, one cent, the scale
limit, a rounding midpoint — because that is where these types break, and a uniformly random
twelve-digit decimal never lands there.

**The harness is proven to fail** (`PropertyTest`), for the reason the `..core.web..` ArchUnit rule
taught: a checker that is itself broken produces a green suite that proves nothing. It proves a
false property is reported, that a non-assertion exception counts as a failure, that the value
reported is the *shrunk* one, and that generation is reproducible. **Writing it immediately caught a
weakness in the shrinker**: the first version offered "half" and "one unit closer to zero" and
nothing between, so a property failing above 1000 shrank 12345 to 1543 and then crawled down by 0.01
until the round limit stopped it. A halving ladder replaced it and converges in tens of rounds.

### What the properties actually claim

Over `Money`, `Quantity`, `UnitCost` and `ProportionalAllocation` — the laws, not the examples. That
equality is numeric equality (the whole reason the scale is fixed on construction); that `compareTo`
agrees with `equals`, which nothing in the suite had ever said although `Money` is `Comparable` and
gets sorted; that the currency guard holds on **every** binary operation rather than the two an
example test happened to cover; that rounding never moves a value by a whole cent in any mode; that
`Quantity.times` refuses **exactly** when the product needs more than six decimals, not merely that
it can refuse.

`ProportionalAllocation` gains the most. Its two callers — a bundle's price pushed onto its
components, a freight invoice split across lots — are both wrong in ways nobody notices if a single
cent goes astray. The properties assert that the parts sum exactly to the whole, that **no part is
ever more than a cent from its exact share** (which is what distinguishes largest-remainder from
"floor everything and dump the residual on the last part" — both sum correctly, only one is an
allocation), that a weightless part takes nothing, and that negating the total negates every part.
It is also checked against **an independently written largest-remainder split** that shares no code
with the implementation. One tempting property is deliberately absent and says so: permuting the
weights does not permute the result, because ties are broken by position — asserting the stronger
claim would be asserting a bug.

### FIFO, over generated histories rather than chosen ones

`FifoPropertiesIT` generates a whole history — several deliveries at different dates, costs and
locations, then several sales, some of which oversell — and replays it against the real services,
twenty histories per property. It asserts conservation (`filled + shortfall == requested`, lines sum
to filled), that no lot leaves its own bounds, that aggregate stock reconciles to the lots less the
shortfalls, that the entry balances and every line names its lot, and that a shortfall is never
costed. FIFO's allocation is compared against **an independently written FIFO** computed from the
lots' captured before-state, which subsumes ordering, exhaustion and never-touching-Damaged-Goods —
those three are still asserted separately, because "the allocation differs" is a worse bug report
than "it sold out of Damaged Goods".

**The first thing it found was a mistake in its own fixture, and the finding is worth keeping.**
Building lots through `InventoryService.receive` creates stock with **no ledger entry behind it** —
ADR 0004 puts the Inventory debit on the Goods Receipt, which is the document that knows the
supplier the GR/IR clearing is against. The interface says so explicitly ("nothing outside the core
should be calling this"). `StockConsumptionIT` uses the same shortcut and is right to, because it
asserts nothing about the ledger; anything that does assert about the ledger must go through a
Goods Receipt.

**The second thing it found is Q45**, above: the Inventory rounding residue. That is a real defect
in posted money, and it is written up rather than fixed.

### One trading year, then every invariant at once

`WholeScenarioIT` builds a year — purchases arriving before and after their invoices, freight
allocated onto stock partly sold, a bundle decomposed, a sale nobody had the stock for, a credit
note that brings stock back, settlements both ways, a reversal, a write-off, a bank transfer — and
then asserts, as separate ordered tests so a break names itself:

- **No entry anywhere in the database is unbalanced, empty, one-sided or multi-currency**, asked in
  raw SQL straight against the tables. It bypasses every service, view and Java check, which is what
  makes it a statement about the *data*. It is the one assertion here that would still be worth
  keeping if everything else in the file were deleted.
- No journal line is zero or negative.
- **The ledger is not trivial** — over 15 entries and 60 lines. Guards the failure mode every
  whole-system test has: passing because it did nothing.
- The trial balance balances.
- **Every control account equals the sum of its own sub-ledger**, swept over the whole chart rather
  than over named accounts, and every Control-account line carries a reference.
- Inventory equals what every lot says it is carrying; GR/IR holds exactly the timing gap and is
  zero when both sides are clear; both variance accounts equal what the documents recorded.
- VAT precedence resolved at all three levels on one document, with each line recording *which*
  level won — the beans line is `CUSTOMER` even though the customer's rate and the product's rate
  are both 13%, because recording only the number would make "why is this line at 13%?"
  unanswerable.
- Output and input VAT are separate figures and each equals its own account.
- Open items equal AR and AP, which ADR 0009 requires by construction since allocations post
  nothing.
- The oversold product reads negative and is findable; the bundle is stored decomposed and the
  components sum to the line; a reversal is an exact mirror; the write-off both reduced stock and
  posted.
- **And the whole year backs up, restores into a fresh database, and still balances there.** This is
  worth more here than in `BackupIT`, where the restore check asserts that a nearly-empty ledger
  balances. (Skipped on a machine without `pg_dump`; it runs on CI.)

**It found a real gap in its own reasoning too**, which is the kind of thing only a whole-scenario
test can: `Landed cost variance` has **two** contributors, not one. The allocations put the share
belonging to already-sold stock into it (ADR 0010) and ADR 0011's catch-up takes some back out when
returned stock re-enters a re-costed lot. Comparing the account against the allocations alone was
wrong — 18.38 against 22.98, the 4.60 being exactly the credit note's four returned units. The test
now reads both contributions off the ledger by source and asserts they add up.

### Why this class gets its own database, and what that buys

`WholeScenarioIT` declares a `@DynamicPropertySource` for the backup leg, so Spring gives it its own
context and therefore its own container. That is a feature: the sweeps cover exactly the scenario
this class built and nothing else, so "Inventory equals the sum of what the lots carry" is an
equality rather than a delta against whatever the rest of the suite left behind.

---

## Step 13, part two — Q45 fixed (ADR 0015, migration V24)

Approved as recommended and built the same day. **960 tests, `mvn clean verify` exit 0.**

### What the rule now is

A lot's **carrying value** is its remaining quantity extended at its unit cost, rounded exactly once
— one definition, `LotValuation`, one rounding mode. **Every posting that moves a lot's stock puts
the change in that figure on the Inventory line.** Not the quantity moved extended at the cost and
rounded, which is a different number and was the whole of Q45.

So `InventoryLotView.remainingValue()` and the Inventory account's position for that lot are equal
at every moment by construction, and a fully consumed lot leaves exactly nothing behind.

### Four things worth knowing beyond the ADR

1. **The freight allocation had to change too**, and it was not in the approved list. It is what
   creates six-decimal unit costs in the first place (ADR 0010: €2.00 over three units is 0.666667)
   and it debits Inventory, so leaving it alone would have left the invariant false at the one place
   it matters most. Its capitalised half is no longer a proportional estimate of what the stock on
   hand should absorb — it is exactly how much the allocation raises the lot's carrying value.
   ADR 0010's decision is untouched; only its arithmetic is stated exactly rather than approximately.
2. **Migration V24**, which drops exactly one CHECK. With the capitalised half stated exactly, the
   variance half is the remainder — and the remainder can be one cent **negative**, because a
   six-decimal per-unit cost cannot always express a total. `Landed cost variance` is credited in
   that case, which needs nothing new: ADR 0011's return catch-up already credits it.
3. **The rounding mode is fixed at `HALF_UP` and deliberately does not follow
   `ledger.rounding.mode`.** A lot's value at two moments must be measured the same way, and a
   setting somebody can change cannot promise that — a change mid-life would leave exactly the cent
   this fix removes. `ledger.rounding.mode` still governs document rounding, which is what brief §6
   asks it for. Nothing about today's numbers changes; what changes is that nothing can change them
   tomorrow. Consequence: **`InventoryServiceImpl` no longer reads settings at all.**
4. **Reversal is the one place the two rules pull apart, and it is guarded rather than fudged.** A
   reversal must post the exact mirror (Q13, ADR 0006), and the mirror is only the right amount if
   the lot has not moved since. `reverseConsumption` and `reverseWriteOff` compare the two and
   **refuse if and only if they differ**, naming the remedy. It cannot fire for a whole-cent cost, it
   does not fire when reversing the most recent movement, and it fires only when reversing *behind* a
   later movement on a sub-cent-cost lot.

### Two consequences a reader should not be surprised by

- **Two identical units out of one lot can post different costs** — 12.50 then 12.51. They must, if
  the lot is to end at zero: 22 × 12.505 is 275.11 and no repeated cent figure divides it.
- **Cost is now path-independent.** Twenty-two single sales and one sale of twenty-two both cost
  €275.11. Before, they cost €275.22 and €275.11 and both claimed to be the cost of the same lot.

### Verification, and it is the point

- **`LotCarryingValueIT` is proven to actually fail.** The old formula was reinstated and five of
  its eight tests went red, one at €275.22 against €275.11 — the reported drift, to the cent. The
  three that stayed green are supposed to, and the file says which and why.
- **`FifoPropertiesIT`'s whole-cent restriction is removed.** The ledger-agreement and
  self-liquidation properties now run over 0.333333, 10.666667, 12.505 and 99.999999 across twenty
  generated histories each. That is the fix being checked against the class of input that found the
  bug, which is what was asked for.
- **`WholeScenarioIT` is unchanged and still green**, which is what says the fix moved nothing it
  should not have. No other test in the suite needed changing either — a fact worth recording,
  because it means every existing example test used whole-cent costs, which is why the defect
  survived twelve build steps.

---

## Open questions, by the step they block

Numbering follows the original Phase 1 question list so references stay stable.
**Resolved:** Q1–Q3 (chart of accounts), Q20 (money scale), **Q4 (VAT classes — real rate list
supplied and seeded, built as a runtime-editable entity; precedence rule stated as code)**.

### ✅ Resolved and built this session

- ~~**Q27**~~ dedicated `Delivery income` / `COD fee income` accounts — **built** in V7.
- ~~**The VatExemptionReason seed**~~ — **built** in V8, 29 real rows.
- ~~**Q5**~~ Product↔Supplier — one supplier, plain foreign key, no many-to-many.
- ~~**Q8**~~ single email and single phone per customer.
- ~~**Q9**~~ `VatStatus` classification plus a VAT number field; VIES deferred to phase 7.
- ~~**Q12**~~ a manually set depreciation rate on Asset; automatic pre-fill deferred.
- ~~**Q6**~~ last purchase price is **computed, not stored**, for consistency with stock.

### ✅ Also resolved this session

- ~~**Q33**~~ — **a fee's VAT rate is independent of the products purchased, confirmed with the
  accountant.** Nothing to build; the seeded 24% default is the answer. See the V7 section.
- ~~**Q34**~~ — **converted to a table** (V11). See above.
- ~~**The VAT rate bound**~~ — **fixed** (V10). Not a question, a defect the user flagged.

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

### Resolved in step 4
- ~~**Q21** field-level restriction list~~ — **answered and built.** Remote/Order Staff's exact
  grants and the three hidden Product fields are seeded and enforced.
- ~~**Q22** auth mechanism~~ — **approved and built.** Server-side sessions, HttpOnly cookie.
- ~~**Q23** reserved section keys~~ — **done.** `SALES_ORDER_FULFILLMENT` and
  `BACK_IN_STOCK_REMINDERS` exist as `Section` values flagged unavailable, so they can be granted
  before the modules exist and a UI can tell "you may not see this" from "this isn't built yet".

### Left over from Q22
- ~~**Q29** password policy~~ — **approved as defaulted.** 12 characters minimum, no composition
  rules, per NIST SP 800-63B. Settled; no further work.
- **Q30** **2FA — decided (no, for now) with a condition. Escalated to a pre-launch blocker: see
  PLB-1 at the top of this file.** Not an open question and not a deferral by default; it has a
  named trigger that must close it.
- ~~**Q31**~~ — **confirmed in step 9: single role per user stays, no change needed.** Brief §7's
  "multiple custom roles from the start" reads as many role *definitions*, not many roles per
  person, which is what step 4 built. Confirmed against the current team shape (Owner, Admin, one
  operational Remote/Order Staff role) with no foreseeable need for anyone to hold two. It is no
  longer the cheap-now/expensive-later item it had been since step 5.
- **Q32** *(still open)* Session timeout is 8 hours. Reasonable for a working day; confirm or
  change.

### Resolved in step 5 — core entities
- ~~**Q4** VAT class list~~ — resolved and built in step 3b.
- ~~**Step-3b obligation**~~ — **done.** `Product.defaultVatClassId` is required (there is no
  fallback rate, so a product without one could not be invoiced), and
  `Customer.vatClassOverrideId` is nullable. `VatClassPrecedence` now has real stored levels.
- ~~**Step-4 obligation**~~ — **done.** `ProductView.redactedFor(RoleView)`, tested against the
  real seeded role. See the warning in the step 5 section about the `...For` naming convention.
- ~~**Q5**~~, ~~**Q6**~~, ~~**Q8**~~, ~~**Q9**~~, ~~**Q12**~~ — **answered and built.** See above.
- ~~**Q7**~~ — **answered and built in step 6.** Stock per location plus a computed sellable figure;
  sellable is the Inventory location only.
- ~~**Q11**~~ — **answered and built in step 6.** Bundles, to brief §5 in full.
- ~~**Q10**~~ — **answered and built in step 9 (ADR 0009): seeded, and structural.** "Πελάτης
  Λιανικής" carries `CustomerSystemKey.RETAIL_WALK_IN`; its VAT treatment is fixed at `DOMESTIC` by
  CHECK, it cannot be deactivated by CHECK, and it is refused on both sides of a merge. Not a
  default — nothing falls back to it.
- **Q12 leftover** *(still open)* Is the periodic depreciation **posting run** in Phase 1, or only
  the register and the calculation? The register is built; nothing posts.

### Resolved in step 6 — inventory
- ~~**Step-3 obligation:** the write-off reason field~~ — **fully discharged in step 7.** The enum
  landed in step 6 with no consumer, which was recorded then as the item most likely to be silently
  forgotten; `stock_write_off` is now the transaction that carries it. See the step 7 section.
- ~~**Q25**~~ — **a fixed enum**: `SHRINKAGE` / `DAMAGE` / `EXPIRY` / `OTHER`. Reportability is the
  whole point, and free text would give four spellings of "damaged" as four categories.

### Resolved in step 7 — the ledger
- ~~**Q13**~~ — **answered and built. ADR 0006.** Invoices and credit notes immutable once posted,
  corrected by reversal; receipts, payments, transfers and manual entries editable in place with the
  previous state written to the audit log. Enforced in the database, not only in the service.
  **Approved extension: the inventory write-off is immutable too.** ⚠️ Its second half — editing a
  Receipt/Payment below its allocated total reduces allocations most-recent-first — is a **step 9
  obligation** and is not implemented, because allocations do not exist yet.
- ~~**Q14**~~ — **answered and built. ADR 0007.** Separate Output VAT (liability) and Input VAT
  (asset), never netted; per-line computation summed by rate, which is why a journal line carries its
  VAT class and taxable base; reverse charge as its own path, needing no new structure; exempt lines
  posting no VAT line at all. ⚠️ **Step 9 obligation:** the invoice postings must actually supply the
  VAT dimension, which is optional at the ledger.
- ~~**Q19**~~ — **confirmed.** All six typed transactions are Phase 1. `JournalSource` declares all of
  them plus the credit note and the write-off; six have no producer until steps 8 and 9.
- ~~**Q26**~~ — **answered.** A credit note is its own transaction type, not a negative Sales
  Invoice: it references the original, posts against the existing per-channel `Sales returns`
  account, and is immutable once issued — the same policy as the invoice it corrects.
  `JournalSource.CREDIT_NOTE` exists and carries that policy; the transaction itself is step 9.
- ~~**Q15**~~ — **answered: per document**, as the brief already stated. The recomputation is
  compared against the source document total, not line by line. Nothing built — the comparison
  belongs with the invoice transactions in step 9, and the destination account already exists.
  ⚠️ Still unanswered within Q15: **where a flagged-for-review item lives** — a review queue, or a
  flag on the record.
- ~~**Q16**~~ — **answered: a standalone credit document**, not a bare AR balance adjustment,
  consistent with treating every financial event as a trackable document with its own lifecycle.
  Nothing built; it belongs with open-item matching in step 9.

### Resolved in step 8 — purchasing and FIFO
- ~~**ADR 0004's open item**~~ — **answered and built. ADR 0008.** A purchase price variance account;
  the lot keeps the cost it was received at.
- ~~**Q17**~~ — **answered and built. ADR 0008.** Aggregate stock may go negative; the shortfall is
  recorded, subtracted from the sellable figure, and queryable. No COGS is posted for it and a later
  receipt does not retro-cost it.
- ~~**Q39**~~ — **answered and built. ADR 0008.** A Goods Receipt is immutable; correction is
  `GoodsReceiptService.reverse`, which un-receives the lots and posts the mirror together and refuses
  once they have been touched.

### Resolved in step 9 — sales, credit notes and the open-item layer
- ~~**Q10**~~ — **the shared retail customer, seeded and structural.** See above and ADR 0009.
- ~~**Q15's remainder**~~ — **answered: confirm at entry, record the confirmation, no review
  queue.** A rounding difference above `ledger.rounding.threshold` refuses the document until
  somebody accepts it; below it, it posts automatically; the difference always posts, so Accounts
  receivable agrees with what the customer holds. The rule generalises: visible-at-entry is
  confirmed at entry, a consequence the operator cannot fix (Q17's shortfall) is flagged and
  queried, and neither is a queue.
- ~~**Q16**~~ — **built: unallocated credit is a standalone `customer_credit` document that posts
  nothing**, created only when the caller states the remainder is credit rather than an unmatched
  remittance.
- ~~**Q26**~~ — **built: the credit note is its own transaction type**, referencing the invoice it
  corrects, debiting the per-channel `Sales returns` account, immutable once issued, crediting the
  VAT the sale actually charged.
- ~~**Q31**~~ — **confirmed, nothing to change.** See above.

### Resolved in step 10 — landed costs
- ~~**Q18**~~ — **answered and built. ADR 0010.** A lot's share of an allocated freight cost splits by
  what is still in the lot: the part belonging to stock on hand raises its unit cost, the part belonging
  to stock already gone posts to `Landed cost variance`. Nothing reaches back into posted COGS, which is
  the constraint ADR 0008 placed on the answer. The allocation basis is the lot's **received** cost,
  which is why that figure is now frozen for the life of a lot.
- ~~**Step 6's last-purchase-price obligation**~~ — **discharged**, with one residual difference
  recorded. See the step 10 section.
- **ADR 0011** *(new, not a question)* — **returns catch the freight up, reversals refuse.** A defect
  ADR 0010 introduced, found by reviewing step 10 rather than by a failing test, measured at €20 on a
  ten-unit lot. Recorded here because the asymmetry is the kind of thing a later reader would otherwise
  try to "make consistent".

### ✅ Q45 — answered and fixed (ADR 0015)

- **Q45** *(raised and closed on 2026-07-29)* — **a lot whose unit cost was not a whole number of
  cents left a permanent residue in the Inventory control account when it was fully consumed.**
  Found by the FIFO property tests, reproduced by a throwaway probe, then **fixed as ADR 0015**:
  every posting that moves a lot now puts *the change in the lot's carrying value* on the Inventory
  line, so `remainingValue()` and the account agree by construction and an emptied lot leaves
  nothing behind. The description below is kept as it was written, because the measurements are the
  reason the fix looks the way it does — see "Step 13, part two" above for what was actually built.

  **What happens.** A Goods Receipt debits Inventory with the whole delivery rounded once
  (`quantity × unitCost`, one rounding). Each consumption credits Inventory with *its own* line
  rounded once. Those two roundings are at different granularities, so they do not add up:

      22 units @ 12.505000, sold one at a time
        Goods Receipt debited Inventory        275.11
        each sale credited                      12.51   (12.505 rounded, HALF_UP)
        22 sales credited                      275.22
        >>> lot empty, Inventory residue        -0.11

      3 units @ 10.666667 (a landed-cost-allocated lot), sold one at a time
        >>> lot empty, Inventory residue        -0.01

  **Why it matters, and why it is not cosmetic.** The residue is real journal lines on a real
  account. It does not net out across lots — `HALF_UP` rounds away from zero, so the drift is
  systematically negative — and there is no document behind it, nothing to reconcile it against and
  no report that would explain it. The Inventory line on the balance sheet and COGS are both wrong
  by the accumulated amount, permanently. It also makes `InventoryLotView.remainingValue()` and the
  Inventory account disagree part-way through a lot's life, which is the invariant ADR 0011 exists
  to protect.

  **It is reachable by design, not by accident.** `UnitCost` carries six decimals precisely so
  ADR 0010's landed-cost allocation can divide freight across lots without losing precision —
  €2.00 over three units is 0.666667. Every re-costed lot is a candidate.

  **Recommended fix, for the decision rather than as a fait accompli:** post the *change in the
  lot's carrying value* rather than `quantity × unitCost` rounded. That is, a movement's amount is
  `round(remainingBefore × cost) − round(remainingAfter × cost)`. It makes the ADR 0011 invariant
  true by construction at every point, guarantees a lot self-liquidates to exactly zero, and needs
  no new account and no migration. The cost is that COGS per unit varies by a cent within a lot,
  which is the honest answer — the lot cost what it cost and all of it has to leave. Rejected
  alternatives: posting the residue to `Rounding differences` (that account is for reconciling
  against an *external* document, never for absorbing our own arithmetic — the same stance
  `ProportionalAllocation` takes) and accepting it with a tolerance (which is how a wrong number
  becomes permanent).

  ~~**Not fixed in step 13, deliberately.**~~ **Approved and fixed the same day.** The
  recommendation above was accepted as written, and ADR 0015 is it: `consume`, `writeOff`,
  `returnConsumed`, both reversals **and the freight allocation** now post the change in the lot's
  carrying value. The freight allocation was not in the original list and had to be: it is what
  creates sub-cent unit costs in the first place, and it posts to Inventory, so leaving it would
  have left the invariant false at the one place it matters most.

  ~~**What the suite says in the meantime.**~~ `FifoPropertiesIT`'s restriction to whole-cent costs
  **is gone**: the ledger-agreement and self-liquidation properties now run over every cost shape,
  which is what verifies the fix against the class of input that found the bug.

---

## Step 14 — done (the REST surface, and Q44 in full)

Four commits: `423bf34` (14a), `e6354d6` (14b), `b8aa9e2` (14c), `f2e8e06` (14c cont.). **133
routes** across 11 controller packages, one migration (**V25**), and 79 new tests.

Split into three sub-steps deliberately — one commit of that size would have been unreviewable, and
each of the three is green on its own. The full proposal, with the endpoint tables and the reasoning
behind every decision, is `docs/step-14-rest-surface-proposal.md`.

### The foundations, which are the part that will outlive the endpoints

- **`@Requires(section, level)` plus an interceptor** replaces step 4b's inline `requireView` call.
  4b's own javadoc said this should happen "with many controllers"; the risk it introduces is the
  opposite of the one 4b avoided — an annotation that is *forgotten* fails open, silently, and looks
  exactly like working code. **Three layers say otherwise**: an ArchUnit rule fails the build, a
  `ContextRefreshedEvent` check refuses to let the application start, and the interceptor refuses the
  request. All three were proven to fire.
- **Money crosses the wire as a string** — `{"amount": "12.50", "currency": "EUR"}`, quantities as
  bare strings. JSON has no decimal type and a number literal becomes an IEEE-754 double in a
  browser, which is `CLAUDE.md` rule 5 broken at the one layer facing outward. **A JSON number is
  refused, not accepted and rounded**: a client that sent one has already lost the value. Held to
  **12.505** specifically — the unit cost behind Q45 — including an assertion of exactly what a
  double does to it.
- **The error mapping went from two cases to the full set**: 404 absent, 422 refused, 409 immutable,
  410 pruned, 413, 503, 400. **Permission refusals stay generic** (they would describe the permission
  model); **validation refusals carry the core's own message**, because an operator who cannot see
  why a document was refused cannot fix it. `WebExceptionMappingTest` enumerates `core-api`'s
  exceptions and fails if one is unmapped, so the explicit list cannot fall behind.
- **Lists are wrapped** in `{"items": [...]}`. Step 14 ships **unpaged**, deliberately — no service
  method takes a limit — and the envelope is what makes adding paging later something other than a
  breaking change. **`GET /api/products` returns every product**, which is fine at hundreds and not
  at tens of thousands.
- **Commands, not CRUD.** One route per named service operation, because a whole-object `PUT` would
  have to diff and dispatch inside a controller and would turn an absent field into a null. 201 for
  creations, 204 for void commands.

### Three architecture rules, each closing something that fails silently

Every route declares a section; nothing in `..core.web..` calls an unredacted product read; nothing
in `..core.web..` calls the lower inventory layer. **Each was proven to fail against a probe, and the
probes deleted.**

The third is the one worth restating. **`receive`, `unreceive`, `consume`, `reverseConsumption`,
`applyLandedCost` and `removeLandedCost` have no HTTP route and cannot be called from one.** Each
moves stock and posts nothing on its own, because the document service that calls them posts the
entry in the same transaction. A route to `receive` would create a lot with no document; a route to
`consume` would take stock out with no sale. Either leaves the Inventory control account disagreeing
with what the lots carry — the invariant ADR 0015 restored. **Stock moves through documents.**

### Two placements that are decisions, not details

- **`GET /api/products/{id}/stock` is under `PRODUCTS`, not `INVENTORY`.** `StockLevels` carries
  quantities and no cost. An order picker with VIEW on Products needs to know there are three left; a
  *lot* is what says what those three cost. Putting it behind INVENTORY would either stop
  Remote/Order Staff doing its job or force a grant that hands over cost data with it.
- **An asset's carrying value gets no route at all.** It is `subLedgerBalanceOf`, which is
  `Section.JOURNAL` — every posting against the asset. Exposing it on the asset route would be a
  second, weaker path to ledger data, which is the exact failure Q44 exists to prevent elsewhere.

### ✅ Q44 — both halves, answered and built

**The section half.** `Section.EMAIL_OUTBOX` is new and deliberately **not** folded into `SETTINGS`:
changing the SMTP password and reading who was emailed about what are different grants — the
argument that separates `JOURNAL` from `CHART_OF_ACCOUNTS` and `INVENTORY` from `PRODUCTS`. Bodies
are already absent from `QueuedEmailView`, so what it governs is recipients, subjects, delivery state
and attachments.

**The access-path half.** `EmailSender.downloadAttachment` now takes the viewer as a **required
parameter with no unchecked overload beside it** — an unchecked path left available is the path that
eventually gets called — and re-checks a *referenced* attachment against the section governing the
record it belongs to.

**⚠️ Implementing it turned up what the decision could not have known: there was nothing to check
against.** `AttachmentService.entityType` is free text, so no mapping from a document to a section
existed. **`AttachmentOwnerType`** is that missing piece: one typed registry, **fail-closed on an
unrecognised type, denying even the Owner**. The consequence is deliberate — attaching documents to a
new kind of record means adding it there, or nobody can download them out of a sent email. That fails
visibly; the alternative fails silently. Denying the Owner too is the strict reading, chosen because
if only restricted roles were refused the missing registration would be invisible to whoever could
fix it.

**Proven behaviourally, not structurally.** `EmailAttachmentAccessIT` (8 tests) and
`OutboxEndpointIT` (6) assert outcomes — a clerk holding `EMAIL_OUTBOX` and not `PURCHASING` sees the
attachment's *name* and gets **403** for its bytes, while the Owner gets the file — and **both denial
tests were confirmed to fail against the check removed**, then it was restored. That is the step-12
audit-log lesson applied on purpose: a structurally spotless change can reintroduce the defect in
full, and only a behavioural test holds the guarantee.

### 🐛 "No migrations expected" was wrong, and how it was found matters

The plan said step 14 would need no migration, reasoning that a `Section` is a Java enum and grants
are default-deny. **`role_section_grant` carries a CHECK listing every known section by name**, so
`EMAIL_OUTBOX` existing only in Java **could not be granted at all** — every insert refused by the
database. Three tests failed; the reasoning had not.

The constraint is doing exactly what it was built for — the same pattern as
`journal_entry_source_known`: the database states the value list independently, so neither side can
drift unnoticed, and the price is that adding a section is a migration. **V25 pays it.**

**The durable fix is the guard, not the migration.** `SecurityIT` now holds the CHECK to the enum in
**both** directions and adds a third test that a grant can really be *stored* for every section,
reserved ones included — structural agreement not being the same as it working. The next person
adding a section finds out from a named test rather than from three unrelated failures.

### Three more defects and gaps the work turned up

1. **`ProductService.allFor(viewer)` returned `active()`** — contradicting its own name and leaving
   `all()` with no redacted counterpart at all. Corrected, and `activeFor`, `findBySkuFor`,
   `findByEanFor` and `bySupplierFor` added, so **every plain read now has a `...For` variant**.
   Without them a controller wanting active products or a SKU lookup would have had to filter a
   redacted list itself — domain logic in the web layer, filtering on a field that may have just been
   blanked.
2. **`EndpointDeclarationCheck` died at startup on `NoUniqueBeanDefinitionException`.** A real
   context has **two** `RequestMappingHandlerMapping` beans, Spring MVC's and the actuator's. It now
   checks every mapping, which is also the stricter answer — choosing one by name would leave the
   others unexamined without saying so. Covered by a test that fails against the old shape.
3. **`BundleService.allBundles()` and `bundlesWithUnpricedComponents()` returned unredacted
   `ProductView`s.** The behaviour was correct (the controller redacted by hand) and the *guarantee*
   was conventional, because the architecture rule was written against `ProductService` alone. Closed
   in `f2e8e06` with `allBundlesFor` / `bundlesWithUnpricedComponentsFor` and the rule extended,
   proven to fail against a probe.

### Deliberately not built in step 14

Users and roles, settings, the audit log, backup administration, **journal writing** (manual entries)
and VAT-class/exemption-reason *administration*. Each has a service and can get a route later; none
is needed to drive a trading workflow, and adding them would have tripled the review surface. The
journal has **read** routes nowhere either — `Section.JOURNAL` is close to granting everything, and
nothing in step 14's workflows needs it.

### Not blocking anything, but unanswered
- **Q40** **Does a journal entry need a human-facing entry number?** The id is the handle today. An
  accountant asking "what is entry 412" is a real request, and it carries a format decision nobody
  has been asked — per-year reset? a prefix per source? Nothing was guessed. The same question now
  applies to the purchase invoice and goods receipt, which likewise have no NovoCore-facing number:
  an invoice at least carries the *supplier's*, and a delivery may carry their note reference.
- **Q41** **After-the-fact GR/IR matching.** A match is made by whichever document is created
  second; nothing matches an existing invoice to an existing delivery later. That leaves a real
  balance sitting in GR/IR — which is exactly what ADR 0004 says a residual means, and phase 8's
  Clearing Checks is what surfaces it. Building it needs an answer to "whose document is the variance
  entry?", so it belongs with those checks rather than with a later step reaching for it casually.
- **Q42** *(new)* **A bundle containing a serial-tracked component cannot be sold as a bundle.**
  Step 9 refuses it, naming the reason: which machine left the shelf is a fact somebody scanned, and
  a bundle line names no serial numbers, so a bundle definition would be choosing units. The machine
  can be sold on its own line today. The real answer — if this ever comes up — is serial numbers per
  *component* on a bundle line, which is a request shape nobody has asked for. **Not blocking
  anything**; recorded so it is a decision rather than a surprise at the till.
- **A credit note that restored stock cannot be reversed.** Not a question — a decision (ADR 0009),
  applying ADR 0008's principle in the other direction: the goods are physically on a shelf, in a
  lot FIFO may have sold from again. A price-only credit note reverses normally. Recorded here
  because it is the kind of limitation that looks like an omission from the outside.

### Blocking phase 8 — Clearing Checks
- **Step-3 obligation:** surface lots aging in the Damaged Goods location. **Step 6 built the query
  it needs** — `InventoryService.lotsAt(DAMAGED_GOODS)` and `unitsAt(DAMAGED_GOODS)`, covering both
  shapes of lot and excluding exhausted ones. The *check* is still phase 8's to write.

### ~~Blocking step 12 — backups~~ — Q24 answered and built
- ~~**Q24**~~ — **answered 2026-07-29 and built.** Google **Drive API** (not `rclone`), **OAuth
  refresh token per account** (a service account has no Drive quota of its own, and Shared Drives
  need Workspace, which `novotrade.gr` is not). Dumps **are encrypted at rest**, AES-256-GCM, before
  anything leaves the host. Retention: **7 rolling + every calendar month's last, forever.** See the
  step 12 section below and ADR 0013.
- ~~**⚠️ Still outstanding:** the two Drive destinations' folder ids and OAuth credentials.~~
  **Supplied and verified 2026-07-29.** Both destinations are fully configured and both have
  uploaded a real artefact. The OAuth consent screen is published **In production** — deliberately,
  because a consent screen left in *Testing* expires refresh tokens after **7 days**, which would
  have produced a backup regime that worked for a week and then failed quietly. `drive.file` is a
  non-sensitive scope, so publishing needed no Google verification review. **Nothing about the
  credentials is in the repo**: the client id and folder ids are Settings rows, the client secret and
  refresh tokens are `secret` Settings rows, and the environment variables that carried them have
  been removed from `.env`.

### ✅ Step 12, commissioned — all three owner action items closed (2026-07-29)

**Step 12 is no longer "code-complete but unverified". It runs.** The caveat that stood in this file
since the step was built is removed rather than softened, because the thing it warned about has
happened for real.

| Item | State |
|---|---|
| 1. Encryption key into a password manager | **Done.** Generated with `openssl rand -base64 32`, in `docker/.env`, and recorded in a password manager. |
| 2. OAuth consent for both Drive accounts | **Done.** Both consented, both destinations `UPLOADED`. |
| 3. `docker compose up --build` proving the image | **Done.** `postgresql-client-17 (17.10-1.pgdg26.04+1)` installed, V21–V23 applied, real dump taken through it. |

**The evidence, from the database rather than from the logs:** `backup_run` id 8,
`novocore-20260729T160100-novocore.dump.enc`, 309,820 bytes, `SUCCEEDED`, with **both**
`backup_upload` rows reading `UPLOADED` and no error. Retention pruned run 1 under its own stated
policy ("outside the most recent 7, and a later backup exists in 2026-07").

**The key was never generated until this session.** Worth stating plainly because this file
previously claimed otherwise: item 1 used to read *"In progress. Until then the only copy is
`docker/.env`."* That was wrong — there was no copy anywhere, the variable was absent from `.env`
entirely, and therefore **no backup had ever been attempted, let alone failed.** The running stack
was still at schema V20 on an image built before step 12 existed: no `backup_run` table, no
`pg_dump` in the container, and not one log line mentioning backups. A doc that describes a
half-finished state is more dangerous than one that says nothing, because it stops anyone looking.

### 🐛 The commissioning bug: four secrets stored wrapped in literal angle brackets

Both Drive uploads failed identically with `HTTP 401: The provided client secret is invalid`, on a
run whose dump and encryption had both succeeded.

**Cause: two inconsistent placeholder styles in the instructions.** The `.env` block used
`<client secret>` and the SQL block used `PASTE_CLIENT_ID`. The angle-bracketed placeholders were
pasted *over* and the brackets came with them; the bare-word ones were replaced cleanly. So all four
secret values — both client secrets and both refresh tokens — were stored as `<GOCSPX-…>` and
`<1//0…>`, while the four non-secret values (client ids, folder ids) were clean.

**Diagnosed by structure, not by guessing.** The stored secret was 37 characters where a `GOCSPX-`
secret is 35. Rather than speculate about corruption or a regenerated secret in the Google console,
the stored values were inspected at their byte boundaries — `ascii(left(value,1)) = 60` and
`ascii(right(value,1)) = 62`, i.e. `<` and `>` — which named the exact defect in one query and
proved it applied to all four values and only those four. **No trip to the Google console was needed
and no re-consent was required**: the secret material inside the brackets was correct all along.

The fix strips exactly one character from each end, guarded by those same two `ascii` conditions so
it is safe to run twice:

```sql
UPDATE setting SET value = substring(value from 2 for length(value) - 2), ...
WHERE setting_key ~ '^backup\.drive\.(primary|secondary)\.(client-secret|refresh-token)$'
  AND ascii(left(value,1)) = 60 AND ascii(right(value,1)) = 62;
```

**⚠️ The lesson is about the verification step, not the paste.** A check *was* run over `.env`
before the rebuild, and it printed the length of every value — including the wrong one. It did not
catch the bug **because it never stated the expected length to compare against**. A 37-character
`GOCSPX-` secret was visibly wrong at that moment and nothing said so. Generalising: **a verification
that prints a value's shape without asserting what the shape should be is not a verification, it is
a display.** Every check written from here should carry its expectation — which is what the final
form of the settings query does (`value ~ '^GOCSPX-[A-Za-z0-9_-]{28}$'`, and 8-of-8 non-blank,
and *client ids identical / folder ids different / refresh tokens different*).

That last triple is worth keeping for its own sake: **two consents against the same Google account
produce two valid tokens, two working uploads, and two copies in one Drive** — an off-site regime
that looks correct and has a single point of failure. Nothing downstream would ever notice, so the
check has to exist at configuration time.

### ✅ Closed incident: a refresh token was pasted into a chat session

During commissioning the **secondary** account's refresh token was pasted into the assistant chat.
It was **revoked at `myaccount.google.com/permissions` and re-consented**, and — the part that is
easy to miss — **a fresh destination folder was created under the new grant.** That second step is
not optional: `drive.file` grants access per file to the app under a specific authorisation, so
revoking drops those grants and re-consenting does not reliably restore access to a folder created
under the old one. Reusing the original folder id would have produced a permissions failure at 02:00
rather than at configuration time. The current `backup.drive.secondary.folder-id` is the new folder;
the original is unused and may be deleted from Drive. **Closed, with no residual.**

The general rule this leaves: **an authorisation code is short-lived and single-use and barely worth
worrying about; a refresh token is durable and must be treated as compromised the moment it is
copied anywhere it was not meant to go.**

### Residuals — small, and stated rather than tidied away

- **Seven local-only artefacts from the failed runs** (`backup_run` 2–7 plus their successors) still
  sit in the backup volume with `FAILED` uploads. Harmless: retention's rolling window will age them
  out, and it already pruned run 1 correctly.
- **The throwaway commissioning container** ran with `NOVOCORE_BACKUP_CRON='0 * * * * *'`, which is
  why there are eight runs one minute apart. It was stopped; nothing persists it, and the scheduled
  cron remains the default 02:00.
- **Artefact names are stamped in `backup.calendar-zone` (Europe/Athens) while `started_at` is
  stored UTC**, so `…T160100…` corresponds to `13:01:00` in the table. Not a defect — worth knowing
  before someone reads it as a three-hour discrepancy.
- **The four settings were written by raw SQL**, because there is still no HTTP route to Settings.
  So **no audit-log entry exists for them**, and `updated_by` reads `system`, which is the honest
  option available rather than claiming a session that did not happen. Worth revisiting when the
  Settings screen lands.

---

## Step 15 — in progress (dummy data validation, over HTTP)

The full proposal, with the six classes of check and the exit criterion, is
`docs/step-15-validation-proposal.md`. Agreed at **full scope**, not the reduced version.

**What it is for, restated because it is easy to mistake for a second domain test suite.**
`WholeScenarioIT` proves the domain is right when driven through its *services*. Step 15 proves the
**REST surface is a faithful and usable route to it** — a different question, and one nothing had
asked: when this started, roughly **half of the 133 routes had never received an HTTP request**, and
the untested half was the half that moves money.

### Done

- **15a — the harness** (`908b226`). `LedgerInvariants`, extracted from `WholeScenarioIT` so the same
  invariants can be asked of an HTTP-built database; `HttpTransport`, the seam that lets one scenario
  run under Failsafe *or* against live Compose; `JsonNumberSweep` over every response; and
  `RouteCoverage`, whose denominator comes from Spring's own handler mapping. **All four proven to
  fail, not merely to pass.** One new invariant: `everySubLedgerReferenceIsLive` — the old check asked
  whether a reference was *present*, this asks whether it points at anything, which no trigger can
  guarantee after a row is deleted.
- **15b, part** (`d8c9e77`, `1421dfb`). A trading quarter driven entirely over HTTP: GR/IR both ways,
  a purchase price variance, freight split across a partly-sold lot, serial-tracked machines under
  reverse charge, a bundle, all three sales channels, an intra-EU exempt sale, a customer credit, a
  stock-restoring credit note into a re-costed lot, a write-off, an oversell, a reversal, a settlement
  amendment that cascades, then a quarter-end review and corrections. **All twelve universal
  invariants pass on a database that only ever received HTTP requests.**
- **Route coverage: 90/133 (68%)**, reported by the ledger rather than asserted, because
  `assertEveryRouteCoveredExcept` is still to come.

### ✅ 15b, completed 2026-07-30 — the five remaining items, in the recorded order

1. **The refusal matrix** (`RefusalMatrix`, 18 entries, one `DynamicTest` each). Every entry asserts
   three things and the third is the one with teeth: the status; that the body is
   **`application/problem+json`** with a `status` agreeing with the HTTP status and a non-blank
   `detail`; and that the detail names the reason **or deliberately does not**. Nothing in the
   repository asserted the media type before — this makes defect 6's fix permanent, on Spring's own
   refusals as well as ours. `mustNotSay` is the part worth keeping: a withholding policy tested only
   in the generous direction quietly becomes a leak. 409 and 410 are stated as unreachable from a
   trading narrative rather than quietly omitted.
2. **The permission sweep** (`PermissionSweepIT`), and the design decision is the point: **the
   expectation is stated independently**, in a route-prefix→`Section` table, rather than read back off
   the handler's own `@Requires`. A sweep that derives its expectation from the declaration proves only
   that the interceptor applies it, and would pass happily against an inventory route declared
   `PRODUCTS`. Three sweeps, each catching what the others structurally cannot:
   - **Remote/Order Staff over all 133** — reaches exactly **20**, refused on **113**, and every
     refusal is checked to leak neither section nor role nor level.
   - **A view-everywhere role** — every state-changing route must refuse it. Catches a mutating
     handler declared `level = VIEW`, which staff cannot detect outside Products.
   - **A granted-everywhere role**, by stored grants and *not* the `fullAccess` flag — every read must
     reach its handler. The only direction that catches a route guarded by a section no grant can
     satisfy.

   The table is asserted **exhaustive and free of dead rules**, so a new route family cannot arrive
   unclassified. Plus the one field-layer pair nothing asserted: an order picker sees
   `/api/products/{id}/stock` and is refused `/api/inventory/lots`, which is the entire argument for
   `INVENTORY` being a section separate from `PRODUCTS`. **V26's byte-level redaction is deliberately
   not duplicated here** — `MasterDataEndpointIT.Redaction` owns it, on both the single read and the
   list.
3. **Read-back and date boundaries** (`ReadBackChecks`). Eleven documents re-fetched and compared
   against the literals sent; nine date-filtered listings asked the three boundary questions against a
   document **on the boundary**, with the anchor date read from the response rather than assumed; and
   filters asserted to exclude as well as include, which is the half that catches a listing ignoring
   its filter entirely. **No off-by-one was found** — but it was asserted nowhere, and the quarter's
   own reads all span the whole period and so could never have told an inclusive bound from an
   exclusive one. It also pins a contract nothing else stated: `default-property-inclusion: non_null`
   means **an unset field is absent, not null**, so a client must read "missing" as "not set".
4. **Restore.** The HTTP-built quarter dumps, restores into a real scratch database, the restored
   ledger balances and matches row-for-row, and the twelve invariants are then re-run on the **live**
   database to show the cycle left the source untouched. Stated plainly in the test: the twelve are
   **not** re-run *inside* the scratch database, because the verifier owns and drops that connection
   and prising it open would mean changing step 12's production code to suit a test.
5. **`assertEveryRouteCoveredExcept`** — **128/133 driven, 5 excused.** The excuse list started at 43,
   and working through it was the useful part: almost every entry was not unreachable, merely
   unwritten. That became `TradingQuarter.quarterEndHousekeeping` — chart-of-accounts maintenance
   (**which nothing had ever written to over HTTP**), six reversals each on a document created for the
   purpose, a settlement allocated *afterwards* and one allocation released, master-data corrections,
   an asset lifecycle, stock moved. The 5 excused are all the email outbox, for one reason: **no route
   sends mail**, so a narrative that touches no service cannot put a message there — and
   `OutboxEndpointIT` drives all five.

### 🐛 Three more defects, found by the work above

| | What | Found by |
|---|---|---|
| 7 | **Sixteen routes answered `500` to a missing required field** — every reversal route among them — in Boot's legacy body shape, the one response on the surface that is not RFC 7807. A missing field arrived as null and met an `Objects.requireNonNull` written to catch a *programming* error. **The same root pattern as defect 5, one exception class along.** Fixed at the root by `Required`, declared in the request record's compact constructor, so `ReversalCommand` fixes six routes in one statement and a seventh reversible document gets it free; `WebExceptionHandler` now unwraps that cause and reports it as itself rather than as "malformed request body" | `PermissionSweepIT.noRouteFailsOnAnEmptyBody` |
| 8 | **A reversed freight allocation reported `0.00`** for the amount it took back out, because a reversal keeps no lines of its own. The ledger was right and the wire was not: any period report summing freight variance overstated by every reversal in it — a plausible number, wrong, with no document to point at. The view now negates the original's figures while keeping the lines-free structure | the `landedCostVarianceDecomposes` invariant, once the narrative reversed an allocation |
| 9 | **The whole email slice answered `400 "Bad request."` for an id that names nothing**, where every other route on the surface answers `404 "Not found."` — four sites, so on those routes alone a client could not tell a malformed request from a missing record. **The third occurrence of defect 5's pattern.** Fixed by `QueuedEmailNotFoundException` and `EmailAttachmentNotFoundException`, which also preserves ADR 0012's deliberate distinction from the 410 `EmailAttachmentUnavailableException` | one site by the new ArchUnit rule, **three by the behavioural sweep** — see below |

### 🛡️ The recurrence earned a guard: a client's mistake raised as a programming error

**Three occurrences inside one step** (defects 5, 7 and 9) of one root pattern: *an exception type
meaning "our code is wrong" used to tell a caller that their request is wrong.* The message is then
correctly discarded and the caller gets `400 "Bad request."` or a `500` — a response that looks
deliberate. Same shape as proxy self-invocation, which earned its ArchUnit rules after biting across
steps 11–12, and the remedy is likewise one sentence each time.

**Now named in `CLAUDE.md` and guarded three ways, each catching what the others cannot:**

- **`WebAuthorizationRulesTest.clientMistakesAreNotProgrammingErrors`** — no class in `..core.web..`
  may *construct* `IllegalArgumentException`. Build-time and precise, **proven to fail against a
  probe** that named the exact method and line. Blind to anything thrown below the web layer.
- **`PermissionSweepIT.noRouteRefusesWithoutSayingWhy`** — every route, reads with no parameters and
  writes with no body, must not answer a bare `"Bad request."`. **This found three of defect 9's four
  sites**, in the service layer where the ArchUnit rule structurally cannot look. That split is the
  argument for having both.
- **`PermissionSweepIT.noRouteFailsOnAnEmptyBody`** — no route may answer `5xx` to a missing field,
  whatever raised it: a `requireNonNull`, an unboxed null, an `orElseThrow` with the wrong supplier.

**Deliberately *not* a rule: `Objects.requireNonNull` is not banned anywhere.** It is correct on our
own arguments — `ListResponse` uses it properly — and no static rule can tell a caller's omission from
a programmer's. Forbidding it would produce exactly the cries-wolf rule the self-invocation work was
careful to avoid. What it cannot see is recorded in `CLAUDE.md` for review instead: a *wrong but
non-empty* value — an unparseable enum, an id naming another party's record, a date range running
backwards — reaches the handler and is only as good as the message written for it.

### 🐛 Six defects found earlier in the step, each with its own commit

The point of the step, and none of them was reachable from the service layer.

| | What | Commit |
|---|---|---|
| 1 | **Every VAT rate crossed the wire as a JSON number.** Step 14 applied the money-as-strings rule to `Money`/`UnitCost`/`Quantity` and not to the raw `BigDecimal` rates beside them. Found in a route four tests already called. Fixed by a **`Rate` value type**, which also gave the factor-of-100 bound one home instead of two. **No migration** — and that is tested per value, not assumed | `7c4c2c4` |
| 2 | **`FreightAllocationLineView.basis`** — an exact 12-decimal product crossing as `540.0`. Now a derived accessor rather than a record component, so it is off the wire and unchanged for Java callers. Zero production consumers; the one test that reads it passes **unmodified**, which is the evidence it carried nothing | `5bf069c` |
| 3 | **AR and the open items disagreed** — impossible by construction per ADR 0009. **Three separate causes**, isolated by measurement: a credit note against a born-settled invoice moved AR when its invoice never had; reducing a settlement stranded the customer credit it left; and the invariant itself was not counting unallocated credits | `fc217ea` |
| 4 | **`GET /api/open-items` omitted customer credits**, under-reporting a customer's position. Fixed before step 16 builds screens against it. It also exposed the invariant summing open amounts **unsigned** — right until now only because every credit note in every scenario happened to be fully allocated | `6d85c89` |
| 5 | **Seventeen parameter messages across nine controllers were discarded.** The controllers signalled a *client* mistake with `IllegalArgumentException`, which step 14 had already decided means a *programming* error: logged, caller gets `"Bad request."` `InvalidRequestException` now carries them | `1421dfb` |
| 6 | **Spring's own refusals returned a different body shape entirely** — Boot's legacy `{"timestamp","status","error","path"}`, no `detail`, so a client cannot read errors uniformly. Fixed with `spring.mvc.problemdetails.enabled` | `1421dfb` |

**⚠️ Defect 6's fix had a cost, and finding it is the argument for the whole step.** Turning
problemdetails on registers a second advice over the same framework exceptions, and Boot's won:
`HttpMessageNotReadableException` started answering `"Failed to read request"`, **replacing step 14's
most load-bearing message** — the one telling a client that an amount must be a JSON string and not a
number. The test asserting that rule went red immediately. `WebExceptionHandler` is now
`@Order(HIGHEST_PRECEDENCE)`. A fix that quietly broke the money contract is exactly what a
validation step exists to catch.

### The API corrected the scenario seven times, which counts as much as the defects

Each of these was the system being right and the narrative being wrong:

- **The €500 legal cash limit** (brief §6, N. 5301/2026) is a **hard block with no override**,
  enforced in three services. A 700.00 cash receipt was refused; a real operator banks it.
- **One customer's credit cannot settle another's invoice.**
- **A product's unit of measure cannot be changed once it has lots** — "reinterpreting a recorded
  quantity in a different unit is not a correction, it is a different quantity."
- **The consumption and write-off listings require a date range or an id**, and now say so.
- **A card or cash sale is born fully settled and never has an open amount**, so it cannot take a
  later allocation. `ON_ACCOUNT` is the only method that leaves a receivable — which is the whole
  point of it, and the housekeeping chapter now says so where it chose an invoice to allocate against.
- **A line already credited in full cannot be credited again**: "crediting more than was sold would
  reclaim output VAT that was never charged."
- **Stock that was never there cannot be lost** — a write-off was aimed at the filter lot, which
  March's oversell had emptied.

### ✅ Q21 revised — no field is restricted from any role (V26)

**Decision, not an omission.** V6 hid a product's cost, supplier and supplier SKU from Remote/Order
Staff; **V26 removes all three.** The business has no confidentiality need behind them — a bank
balance might reasonably stay hidden from a home-based worker, what a bag of beans cost does not.

**The consequence is wider than three rows.** `ProtectedField`'s three values are the only fields the
mechanism knows about and that role held the only restrictions, so **no role has any field restriction
and the inner layer of brief §7's two-layer model is now unused.**

**Expressed as deleting data, not code.** `ProtectedField` keeps all three values, the CHECK keeps
listing them, and `RoleView.canSee`, `ProductView.redactedFor`, the supplier-implies-supplier-SKU
narrowing and the three ArchUnit rules are untouched. Restricting one again is an `INSERT`. A future
case was named when this was decided: **a bank or partner-clearing balance.**

**⚠️ The trap this creates, and what closes it.** With no restriction in real data, a change that
stopped `ProductService`'s `...For` reads consulting the role would pass every test while removing the
guarantee — the shape of the audit-log defect step 12 found. So **every test that proved redaction via
the seeded role now creates a role and restricts a field at runtime** (`RoleService.restrictField`;
roles are data, which is what makes it possible without a migration): `ProductIT`,
`BundleIT` (both `allBundlesFor` cases, which is what step 14c's `f2e8e06` existed to guarantee),
`SecurityIT`, and `MasterDataEndpointIT`, which asserts against the bytes that staff and owner now
receive the **same** product on both the single read and the list — separate code paths, so "both show
everything" is as much a claim as "both hide it" was.

One thing learned in the doing: the first version of `SecurityIT`'s sweep asserted that *no role
anywhere* restricts anything and **failed on execution order** — the throwaway restricted roles the
other tests create share this database. The claim is about what V6 seeds, so it is scoped to that. A
test that passes or fails on ordering is worse than a narrower one.

### Two things worth carrying forward

- **`mvn compile` without `clean` reported `BUILD SUCCESS` against stale classes** after a type change
  in an upstream module, three separate times — once producing a class file that threw
  `"Unresolved compilation problems"` at runtime because the IDE had written it. **After changing a
  signature in `core-api`, build with `clean`** or the result means nothing.
- **`post(String, String)` and `post(String, Object)` overload silently.** A misplaced bracket sent ten
  request descriptions as request *bodies* and compiled cleanly. The narrative uses a single-shape
  helper now; the overload stays because raw-JSON requests are what the refusal matrix needs.

---
## Step 16 — the frontend (in progress)

**`/frontend/` is no longer a scaffold.** Four commits have landed. The first three are summarised
here from their own commit messages rather than re-derived; the fourth is this session.

| Commit | What |
|---|---|
| `94e17cd` | **Foundations** — the nine mechanisms every screen after it is built out of |
| `56e3726` | **Products, the first real screen** — list with a Bundles tab, detail at its own route, create, deactivate/reactivate, inline per-field PATCH. Found the write-hooks defect below |
| `28c4119` | **Two guards for that defect**, plus a correction: 92 writes, not 66 |
| *this session* | **Brand pass** — product name casing, logo and favicon |

**The one defect worth carrying forward, because it nearly wrote to the ledger by rendering.**
`query: { useQuery: true }` in `orval.config.ts` forced *every* generated operation into a
`useQuery` — all 92 non-GET routes included. A component that merely rendered a write hook would
have sent the PATCH on mount, and again on every refetch, invalidation and window focus. Nothing
caught it because nothing had yet *consumed* a write hook: the foundations built no screen, and two
reviews read code that never called one. **Same lesson as Q45 and step 15 — a checker only covers
what it is pointed at.** Now guarded in both layers: `src/api/client-shape.test.ts` checks all 174
operations against the spec, and `src/test/requests.ts` (`expectNoWrites()`) is the standing
assertion in every screen test from Products on. Both guards are themselves proven to go red.

### ✅ This session — the brand pass (2026-07-31)

Small and self-contained, at the owner's direction: correct the product name and wire in the real
logo. **No behaviour changed.**

**The name is `Novocore`** — capital N, rest lowercase — not `NovoCore`. Seven user-facing strings
corrected across four files: the `<title>`, and `app.name` / `app.unreachable.title` /
`registry.adaptersBody` in both the `en` and `el` locales. The logo art itself is set all-lowercase
("novocore"), which independently corroborates the casing.

**Deliberately *not* renamed**, and this is the part to hold on to: the Java package
`gr.novotrade.novocore`, the repo, every internal identifier, the `NOVOCORE_SITE_ADDRESS` env var,
the `x-novocore-*` spec extensions — and the **414 files under `src/api/generated/`** that carry the
Orval header comment `* NovoCore API`. That string comes from the backend OpenAPI spec's title and
is reverted by the next `npm run api:generate`; editing it would be churn that undoes itself. Seven
source-code comments and four developer-facing strings (`README.md`, `eslint.config.js`,
`scripts/check-no-cdn.js`) were also left, by decision — this was a display-text fix, not a rename.

**Assets.** `public/Logo-Novocore.svg` is the wordmark, in the sidebar header (`h-8`) and on the
login card (`h-10`), replacing the `app.name` text that sat in both. `app.name` survives as the
`alt` text, so the accessible name is unchanged. `public/Favicon-Novocore.png` is the favicon,
replacing the purple Vite scaffold mark (`public/favicon.svg`, deleted).

**⚠️ The supplied "SVGs" contain no vector data.** Both are a base64 PNG inside an `<svg>` wrapper —
zero `<path>` elements, one `<image>`/`<use>` pair. So: not scalable, not smaller (the favicon SVG
was 20KB against the PNG's 14KB), and **not recolourable by CSS**. The wordmark still ships as the
SVG because the wrapper composites its two raster pieces at the right offsets and carries the aspect
ratio; the favicon ships as the PNG because there the wrapper buys nothing and costs 6KB. The two
unused variants were not committed. **If a true vector wordmark is ever produced, it replaces
`Logo-Novocore.svg` at the same path and nothing else changes.**

**Contrast was measured, not assumed.** Ink is exactly `#000000` and `#333333` on transparent.
Against the sidebar's `--sidebar: oklch(0.985 0 0)` that is **20.1:1** and **12.1:1**; against the
login card's white, 21:1 and 12.6:1. Both are far above the 3:1 threshold and were also checked by
compositing the logo onto those exact colours at the exact rendered sizes and looking at it.

**Three things flagged and deliberately left alone**, all confirmed by the owner:

- **Dark mode would make the logo invisible.** `.dark` tokens exist in `src/index.css` but nothing
  ever applies the class, so it is unreachable today. If it is ever switched on, the sidebar becomes
  `oklch(0.21 …)` and the logo falls to **1.44:1 / 1.16:1**. Because the asset is raster,
  `currentColor` cannot rescue it — it needs `dark:invert` or a light-ink variant. **Whoever enables
  dark mode owns this.**
- **There is no collapsed sidebar.** `components/ui/sidebar.tsx` defaults to `collapsible="offcanvas"`,
  which slides the whole panel off rather than narrowing to an icon rail, so there is no tight-space
  slot for the mark. Switching to `collapsible="icon"` is a navigation change, not a brand fix.
- **The favicon is soft at 16px.** The mark's three horizontal bands blur together at 1× DPR; at
  24/32px, and therefore on any 2× display, it is crisp. Acceptable as-is.

**Verified:** typecheck clean, lint 0 errors (2 pre-existing warnings), **144/144 tests pass**,
build clean, `check:offline` passes, `knip` clean.

### 📋 Housekeeping note for the next frontend session

**This file had drifted.** Three frontend commits (`94e17cd`, `56e3726`, `28c4119`) landed without
the status table or the "Next action" section being updated, so it still claimed the frontend had no
login screen. Corrected above. The summaries of those three commits are drawn from the commit
messages, which are detailed — but they are second-hand, and the sessions that wrote them would have
recorded more. **Two pre-existing unreferenced scaffold assets remain**: `frontend/public/icons.svg`
and `frontend/src/assets/vite.svg`. Neither is imported anywhere. Left alone as out of scope.

---
## F0 — done (2026-07-31). The dev database, and the approved commit nobody missed

**The frontend was being built against a database that held nothing but Flyway's own seed.** Found
during the icon investigation — zero rows in Products — and picked up here as F0 on the frontend
roadmap. The roadmap offered two hypotheses: the seed pass never ran, or a volume reset wiped it.
**Neither was quite right. The seed pass had never been written.**

### What the record actually says

Step 15's proposal (`docs/step-15-validation-proposal.md`) put the seeder in **D1 as option (c),
recommended**, and scheduled it as its own commit, **15c**, explicitly cuttable. §10 lists the
seeder among the things a *reduced* step 15 would cut, and this file records the step as **"Agreed
at full scope, not the reduced version"** — so 15c was in.

`908b226` is 15a. `d8c9e77`, `1421dfb` and `1a4b294` are 15b. **There is no 15c commit**, and the
step-15 section above lists 15a and 15b as done and **never mentions 15c in either direction** —
not delivered, not deferred, not cut. Nothing in that close-out is inaccurate about anything it
describes. It summarised what was built instead of reconciling against what was approved, and a
summary cannot see an absence.

The seam is still sitting where 15a left it: `HttpTransport`'s javadoc has said since then that it
exists so the scenario can run *"for the seed pass that populates the live Compose database"*, and
it had exactly **one** implementation. `RouteCoverage:47` anticipates the same driver. This is the
cost of prose over a checklist, and it is why `CLAUDE.md` now opens the close-out with a
reconciliation step.

### It was never wiped — four independent proofs

| Evidence | What it rules out |
|---|---|
| `PG_VERSION` mtime **2026-07-27 16:13:22Z**, identical to the volume's `CreatedAt` | the data directory has never been re-initialised, so **no `docker compose down -v` has ever run on this stack** |
| `flyway_schema_history` continuous in one volume — V1–V20 on 07-28 17:21, V21–V23 on 07-29 12:32, V24–V27 on 07-31 01:06 | a reset would have re-run V1 at the later date |
| `audit_log`, append-only by trigger, unbroken ids 1–56 | nothing was deleted, and not one entry is a trading action |
| **`pg_sequences.last_value` NULL — never called — for `product`, `supplier`, `journal_entry`, `sales_invoice`, `purchase_invoice`, `inventory_lot`, `asset`** | decisive: a high-water mark survives `DELETE`, `TRUNCATE` and rollback. **Not one such row had ever existed** |

### What was built (`521a601`)

- **`LiveHttpTransport`** — the second `HttpTransport`, real HTTPS at a base URL supplied from
  outside. Caddy's internal CA is accepted (the concession `vite.config.ts` already makes on the
  same hop); **hostname verification stays on**; errors are returned rather than thrown, so the
  shared code above it does not change behaviour with the transport.
- **`LiveSeedTest`** — the driver. No Spring context, no Testcontainers, disabled unless
  `-Dnovocore.seed.base-url` is given. Credentials come from `NOVOCORE_SEED_USERNAME` /
  `NOVOCORE_SEED_PASSWORD` in the environment, so no password reaches a command line, a build log
  or a process list. Verifies with `ReadBackChecks.documents()` and `assertFiltersActuallyFilter()`,
  and `JsonNumberSweep` runs over every response — **against this build of the application rather
  than a test container**, which is the one thing this driver checks that the Failsafe one cannot.
  It deliberately does **not** run `TradingQuarterOverHttpIT`'s backup check, which writes stub
  Drive settings and would overwrite the commissioned credentials.
- **`TradingQuarter.happens()`** — the fourteen-call sequence, extracted so the two drivers share it
  instead of keeping a copy each.
- **`docker/reset-trading-data.sql`** — a targeted reset. Keeps Settings, Users, Roles, the Flyway
  lookups, the backup history and the audit log. **The audit log's absence from the `TRUNCATE` list
  is load-bearing**: `TRUNCATE` does not fire row triggers, so naming it there would silently do the
  one thing its append-only trigger forbids. **No `RESTART IDENTITY`**, because `audit_log` stores
  entity ids as text and outlives the rows — recycling ids would make it quietly wrong.

### ⚠️ `docker compose down -v` is expensive on this stack, and that is not obvious

`docker/.env` holds **three** keys: the DB password, the site address, the backup encryption key.
The **Google Drive client secrets and refresh tokens are not there** — they live in the `setting`
table, put there once during commissioning, and `NOVOCORE_BOOTSTRAP_OWNER_*` are blank. So `down -v`
destroys the commissioned Drive credentials *and* the Owner account, neither reproducible from
`.env`, and the consent flow has to be re-run — including fresh destination folders, per the closed
incident in the step 12 section. **Use `docker/reset-trading-data.sql`.**

### Verified, and how

The run was done by the owner in their own terminal so no credential entered this session. Then,
against the database directly:

| | |
|---|---:|
| Products / customers / suppliers / fixed assets | 8 / 5 / 3 / 2 |
| Journal entries, lines | 48, 131 |
| Sales invoices, purchase invoices, goods receipts, credit notes | 10 / 7 / 5 / 4 |
| Inventory lots, serialised units, settlements, write-offs, freight allocations | 6 / 3 / 3 / 3 / 3 |
| **Unbalanced entries** | **0** |
| Total debits = total credits | **€20,372.46** |
| Entry dates | 2026-01-05 → 2026-03-31, the quarter's own bounds |
| Distinct posting sources | **9** — breadth, not one path repeated |

Every party and product carries a `TEST-` prefix (step 15 §11 Q3). Account 71 is the one the
narrative added, `created_by = 'kostas'` — which independently confirms the reset script's
`created_by <> 'system'` discriminator picks out exactly the right row.

**The API side was verified by the seed pass itself**, which is stronger than a `curl` would have
been: eleven documents re-fetched over HTTP and compared against the literals sent, the filter
checks run, and every response swept for a decimal that arrived as a JSON number.

**All three refusals are now proven, not merely written.** No base URL → skipped, with its reason.
No credentials → fails with its own message before any network call. **Already populated → refused
on the owner's second run, naming the counts and the reset script** — the guard that could only be
exercised once, taken while the taking was free. And a deliberately wrong password reached the live
server and came back `401`, which is TLS, the CSRF bootstrap and the form post all working.
`TradingQuarterOverHttpIT` is green on the extraction, 68 tests.

### 📋 Reconciliation against what was approved

The first application of `CLAUDE.md`'s new close-out step 1. Every sub-part agreed in this session,
with a verdict — including the three that were **not** in the original approval and were added
inside the step.

| # | Sub-part | Verdict |
|---|---|---|
| 1 | Confirm whether the seed pass ever ran against the live Compose DB | **Done** — it was never written; no 15c commit, and the close-out never mentioned it |
| 2 | Check whether a volume reset wiped it | **Done** — ruled out four ways; the sequences had never been called |
| 3 | Run the seed pass; confirm real data is visible | **Done** — run by the owner, verified above against the database |
| 4 | `LiveHttpTransport` | **Done** — `521a601` |
| 5 | `LiveSeedTest` | **Done** — `521a601`, all three refusals proven |
| 6 | `docker/reset-trading-data.sql` | **Done** — `521a601`. *Not yet executed*: nothing has needed a reset. Its `created_by <> 'system'` discriminator is confirmed correct against the live row |
| 7 | Correct the roadmap's fixture counts | **Done** — `novocore-roadmap.md`; the old "15 products, 12 customers, ~120 journal entries" was wrong on all three |
| 8 | `CLAUDE.md` reconciliation step | **Done** — a new "an approved proposal is a checklist" section, and reconciliation is now close-out **step 1 of six** |
| 9 | *(added)* `TradingQuarter.happens()` extraction | **Done** — the alternative was the same fourteen-call sequence in two drivers, which `CLAUDE.md` names as the shape that decays |
| 10 | *(added)* `seed.ps1` runner | **Done, and deleted by the owner after use as designed.** Never committed |
| 11 | *(added)* `/seed.ps1` in `.gitignore` | **Done** — it was **not** ignored, so the file holding the Owner password in plain text sat in the repository root where `git add -A` would have found it. The entry stays after the file's deletion, because the next person to need one will put it back |

**Nothing from this session is deferred or open.** Two things are inherited rather than raised here:
the `InventoryController_writeOff` duplicate `operationId` (still queued for a backend session, see
below), and `frontend/README.md`'s two-process run instructions, which were written in an earlier
session and left uncommitted — carried into this close-out rather than left to drift further.

---
## Products — the wedge (2026-07-31, `3458ee6`). Not a roadmap step

**A bugfix pass between F0 and F1, on the screen F0 had just given data to. F1 has not started.**

Reported as two things: Firefox's *"this page is slowing down"* warning recurring, and three
interactions on Products dead — the *new product* button, deactivate/reactivate, and double-clicking
a row. The reasonable guess, stated in the request, was a re-render loop tied to row data, since
everything appeared the moment there were rows.

**The guess was wrong in its cause and right in its instinct.** There is a re-render loop. It has
nothing to do with rows: with `GET /api/products` rewritten to `{"items":[]}` — the exact response
every check before F0 saw — it wedges identically. It has been latent since `DataTable` was written.
What F0 changed is that somebody finally had a reason to use a filter.

### The loop, in the order it happens

1. Changing a filter changes the query key, so the query holds **no data** while it refetches.
2. `unwrapList` answered that with a **freshly allocated `[]`** — a new identity every render.
3. `useReactTable` memoises its core row model on `[table.options.data]`, so it rebuilt every render,
   and rebuilding calls `_autoResetPageIndex()` (`table-core` `index.esm.js:2973`).
4. That queues `resetPageIndex()` → `setPageIndex(0)` → `DataTable`'s `onPaginationChange`.
5. Which calls `list.setPage(0)` on a table **already on page 0** — and `setState((s) => ({ ...s,
   page }))` returns a new object regardless, so React cannot bail out.
6. Re-render, back to 2.

**React flushes that cycle in a microtask, so the tab does not get slow — it stops.** The event loop
never runs again, so the in-flight response that would have ended it can never be delivered, and
every click and keystroke after it is discarded. **That is the whole explanation for the three dead
interactions**: the page was already wedged when they were tried.

### How it was established, since two of the three symptoms turned out to be something else

Reproduced in headless Chrome **and** Firefox against the running stack, on the seeded data.

- **Nothing is wrong at rest.** 10 s idle on `/products`: 0 API requests, 0 long tasks, 0 DOM
  mutations, 601 animation frames, 18,843 of 18,848 CPU samples `(idle)`. So: not row rendering.
- **Bisected by interaction**, asking the page to evaluate `1` after each. Hover — fine. Click a
  blank area — fine. **Untick "active only" — no answer in 8 s**, and every mouse and key event
  after that times out at the protocol layer. Typing in the SKU filter does it too.
- **Profiled** by first interrupting with `Runtime.terminateExecution`, which was *accepted* — so the
  main thread was executing script, not blocked on I/O. `processRootScheduleInMicrotask →
  performSyncWorkOnRoot → commitRoot`, repeating, with `data-table.tsx:87` — the
  `onPaginationChange` closure — among the application frames.
- **Each half proven separately** by patching one line at a time against the live dev server: either
  a stable empty array *or* a setter that returns the same object stops it.

### What the three reported interactions actually were

| Reported | Verdict |
|---|---|
| *"New product" button/route* | ⚠️ **Half wrong when first written — see the correction below.** The button and the route are fine; the tab being wedged is what made them read as dead. **But submitting was broken**, and this table originally said it worked |
| *Deactivate / reactivate* | **Genuinely broken, separately.** The backend refuses deactivating a bundle component with `422` and a complete message; the screen rendered **nothing** |
| *Double-clicking a row* | **Never implemented.** No `onDoubleClick` anywhere in `frontend/src`; only the SKU cell is a link |

### ⚠️ Correction, same day: creating a product was broken, and the check that cleared it verified nothing

The row above originally read *"Not broken… submitting posts a correct payload and lands on the new
product."* **The owner disproved it in about a minute** by filling the form and pressing the button:
`400 Malformed request body: Cannot map null into type boolean`.

**The payload was correct. The verification was not.** The browser probe **intercepted the POST and
answered it with a fabricated `201`** — deliberately, to avoid writing to the development database —
so it inspected the body it had built, saw it read correctly, and watched the screen navigate to a
stub of its own making. The backend never saw the request. Everything the check reported was true;
none of it was evidence for the claim it was used to support.

**The rule this earns, and it generalises past this bug: when the question is "will the backend
accept this", the backend has to answer it.** Where a write must not persist, get the refusal from
the *server* — an existing SKU answers `422 "already exists"` only if the body parsed, which
separates "rejected by the parser" from "rejected by the domain" and creates nothing. That is how it
was finally established, and it costs no more than stubbing did.

The defect itself is a backend/spec one and is written up in full as item 2 under *Next action*:
`serialTracked` is a primitive `boolean` on a record, Jackson passes an absent creator property as
`null`, and the spec declares no required fields on any of 71 request bodies — so the generated type
said optional and the form was written correctly against a contract that was wrong. **The frontend
now sends the field explicitly** (`product-create.tsx`), pinned by `spec-hygiene.test.ts`, and a
screen test asserts it is on the wire — proven to fail against the code that shipped.

### 📋 Reconciliation against what was approved

| # | Sub-part | Verdict |
|---|---|---|
| 1 | Render loop — the stable-array half | **Done** — one module-level `NO_ROWS` in `list-response.ts`, returned by both empty paths |
| 2 | Render loop — the no-op-when-equal setter half | **Done** — `setPage`, `setSize` **and** `setSort` in `use-list-state.ts`; all three, because `resetPageSize` reaches `setSize` by the same route |
| 3 | A regression test that toggles a filter and fails on a render-count explosion | **Done** — `data-table-loop.test.tsx`, and see the note below on what it took to make it fail |
| 4 | Surface the swallowed deactivate refusal | **Done** — shared `Refusal` component, used by the detail screen and `FieldEditor` alike |
| 5 | Select labels — show the resolved label, not the raw value/id | **Done** — `OptionSelect`, all eight call sites |
| 6 | *(added)* The create form's own swallowed errors | **Done** — it read `error.detail` directly, so a `403` (no detail, by design) and an unreachable server rendered nothing. Approved after the fact |
| 7 | *(added)* The language select showed `en`, not `English` | **Done** — same root cause, app-wide rather than Products-only. Approved after the fact |
| 8 | Row double-click | **Explicitly deferred** — an open design decision, not a fix: whether a row should have a default action at all, and whether it is "open detail" when the SKU link already does that. Recorded in `novocore-roadmap.md` |
| 9 | The `Cannot map null into type boolean` log line | ⚠️ **Deferred, then reopened and half-fixed the same day.** It was not an unexplained edge case: it is `POST /api/products` failing for every user because `serialTracked` is absent. **Frontend workaround shipped**; the backend/spec half stays queued as item 2 |
| 10 | *(found, not fixed)* The SKU filter is an exact lookup | **Explicitly deferred** — queued as backend item 3; the product decision is the owner's |

### ⚠️ The regression test needed a delay, and this is the part worth remembering

**The first version of the test passed against the fully reverted code.** Answered instantly, `msw`
resolves inside the same microtask checkpoint the auto-reset is queued on, so the query holds no
data for exactly **one** render and the cycle never closes — measured at **3 renders with the defect
entirely present.** With `delay(50)` — which a real network makes unavoidable — the same code
renders **84 times against 4 when fixed.**

Two more properties of that test are deliberate and should survive editing:

- **The counter throws.** A test that merely counted would **hang**, not fail: the loop starves the
  timers `waitFor` runs on. Throwing unwinds to an error boundary and gives the event loop back.
- **The screen under test is a stand-in, not Products.** Every list screen is built from
  `useListState` + `DataTable`, so the guard belongs to the pieces.

**Proven four ways**, each by reverting and re-running: both fixes in place → 15 pass; stable array
reverted → 1 fails; setters reverted → 3 fail; **both reverted → the render-budget test fails**,
*"the table re-rendered itself in a loop: rendered 28 times for one filter change"*. The behavioural
test only fails when **both** are gone, because either alone breaks the cycle — which is exactly why
each invariant is also stated on its own where it can fail by itself.

### Verification

12 checks in **both** Chrome and Firefox against the seeded data — unticking the filter, re-ticking
it and typing in the SKU box all responsive in 1–3 ms where each previously wedged the tab
permanently; the refusal now on screen naming the bundle; `Goods`, `Kilogram` and `English` where
`GOODS`, `4` and `en` used to be. **162 tests, 18 files**, typecheck, lint (0 errors, the same 2
pre-existing warnings as `HEAD`), knip and the production build all clean.

**No database change.** A temporary full-access account was created to drive the browser and
deleted; one product deactivated during the first run was reactivated through the API; the database
was verified back to 8 products, all active, one user.

---
## Next action — read this first

### ✅ The follow-up queue — all 9 numbered items are closed

Each was raised by frontend work and none of them was frontend work to fix.

> ### ✅ Q1 is COMPLETE — 2026-08-03. Four items: 4+6, 5, 1, 7.
>
> Items 2 and 9 were done, and item 3 closed as stale, **before Q1 existed as a step** — they were
> never Q1 progress, and the **credit-note rename is U1's**. **Item 8 left the queue on 2026-08-03**
> and became its own numbered step (8a / 8b), placed after Q1 and before R1, which is what closed the
> standing open decision about promoting it.
>
> ⚠️ **The detail below is each item AS RAISED.** The verdicts, the corrections to two of these
> write-ups, and the two new items Q1 raised are in *Q1 — the backend follow-up queue* above, which is
> the current record. **Where the two disagree, the Q1 section is right** — items 4 and 6 in
> particular are described imprecisely below, and both were corrected by a probe against the running
> server.

> ### ✅ Item 3 is closed as stale — reconciled 2026-08-02
>
> Item 3 asked for an **owner decision** between adding a real search endpoint and relabelling the
> Products filter box, and said *"do not change the frontend until that is decided."* **That decision
> was made and the work was delivered by S1 on 2026-08-01.** `?search=` exists on seven routes, the
> filter box sends it, and `sku=`/`ean=` stay exact because that is what a barcode scanner uses.
>
> **This queue said the opposite for a full week**, while the frontend roadmap and
> `frontend/README.md` both recorded the decision as made. **Two records of one fact disagreed, and
> the one a fresh session would read first was the wrong one** — a step could have been scheduled for
> work already shipped, or the owner asked again for a decision they had already given. It was found
> by reconciling this file against the repository rather than by anything failing.
>
> ⚠️ **The lesson is the one now in `CLAUDE.md`**: a decision reached in conversation is not recorded
> until somebody writes it into the document that governs it — *and closes the item that was waiting
> on it*. S1 wrote the decision into two files and left the third contradicting them. It is also part
> of why there is now **one** roadmap file instead of two.

> **Item 2 was the priority and it is now done** (2026-08-01), so the paragraph that used to stand
> here — *"take item 2 first… `required` is declared on 2 schemas out of 185"* — has been removed
> rather than left to mislead: that figure is now **78**, and the instruction has been carried out.
> The reasoning behind it is preserved under item 2 itself, including the costed comparison, because
> the estimate-versus-outcome is the only calibration data this decision produced.
>
> **What replaced the priority question:** items **7** and **8** carry what item 2 deliberately did
> not close, and they are separate on purpose. Item 7 is a message-quality fix on 7 known fields;
> item 8 needs a design decision before any code and is the one that closes fixture drift.

| # | Item | Priority | Raised |
|---|---|---|---|
| 2 | ✅ **DONE (primitive half), 2026-08-01** — the spec now declares every primitive component required, so a mandatory field is knowable from the contract. Items 7 and 8 carry what it deliberately did not close | — | 2026-07-31 |
| 4 | ✅ **DONE 2026-08-03.** ⚠️ Corrected while doing it: the rules live in **`CustomerView`**, a response record, not in the domain entity — so the fix was to enforce them in `CustomerServiceImpl` where a caller reaches them, not to change the type there. Both routes answer **422 with the reason** now | — | 2026-07-31 |
| 1 | ✅ **DONE 2026-08-03.** POST handler → `createWriteOff`; `OpenApiSpecIT` now **refuses** to write a duplicate; the `orval.config.ts` workaround and the assertion pinning it were deleted | — | 2026-07-30 |
| 3 | ✅ **CLOSED AS STALE, 2026-08-02** — no SKU search endpoint. The decision it waited on was made and the work delivered by **S1** on 2026-08-01; this row said otherwise for a week | — | 2026-07-31 |
| 5 | ✅ **DONE 2026-08-03**, both legs. `PATCH /api/roles/{id}/description`. ⚠️ Stronger than recorded: `Role.description` had **no setter**, so it was structurally unwritable, not merely unrouted. Both frontend notes and their two i18n strings came out with it | — | 2026-08-01 |
| 6 | ✅ **DONE 2026-08-03**, with 4. Both use `Required.field`. ⚠️ Two corrections: the messages were **not** discarded (they surfaced as `"Malformed request body: username"`), and `POST /api/users {}` never reached these guards — the primitive `roleId` fails first, so `POST /api/roles {}` is the clean case | — | 2026-08-01 |
| 7 | ✅ **DONE 2026-08-03, plus a latent eighth** (`NewVatExemptionReason.inputVatDeductible`). It carried a regression for one day — a boxed `Boolean` is not primitive, so the same edit removed the `required` declaration, 78 → 75 schemas — and **8a closed it, 75 → 143**. ⚠️ The eighth has no schema at all, so only **seven** could be confirmed in the spec. The "not urgent, `tsc` refuses one" note was true *because* the field was primitive; boxing is what removed that | — | 2026-08-01 |
| 8 | ✅ **LEFT THIS QUEUE 2026-08-03** and became roadmap step **8a**, which is now **done**. 90 records / 28 request bodies as recorded; a heuristic scan counted 94 / 48; **the exact count is 114 records / 339 components / 48 request-reachable**, and it supersedes both — see *The three counts* above | — | 2026-08-01 |
| 9 | ✅ **DONE 2026-08-01, frontend.** Shared `Me` fixture in `src/test/fixtures.ts` — invariant fields only; `role` and `sections` stay at the call site | — | 2026-08-01 |

**The order it was worked in — ✅ all four landed 2026-08-03. Item 8 was lifted out of the queue into
its own step (8a / 8b) rather than being worked as item 5 of this list.**

1. **4 and 6 together** — one anti-pattern, and 4's part 2 (give the sweep a case carrying a valid
   body a domain rule refuses) is what stops a sixth instance arriving the way these two did.
2. **5** — smallest of the lot, and it sits in the same files as 6, so doing it alongside costs
   almost nothing.
3. **1** — `operationId` collision; mechanical, and deleting the frontend workaround is part of it.
4. **7** — box the 7 booleans. Not urgent: `tsc` now refuses a TypeScript caller that omits one.
5. ~~**8**~~ — ✅ **removed from this list on 2026-08-03.** It is now roadmap steps **8a** and **8b**,
   scheduled after Q1 and before R1. See *Q1 — the backend follow-up queue* above, decision C.

*(Item 3 was fifth in an earlier version of this list. It is closed as stale — see above.)*

⚠️ **Step 8 is the most severe open thing in the project, and Q1's item 7 made it load-bearing.**
Boxing the seven booleans improved the refusal message and removed the `required` declaration from
the spec (78 → 75 schemas, measured 2026-08-03). **8a restores it.** The full trade is written out
under *Item 7's verdict in full* above.

**The credit-note rename is NOT part of this queue** — it was listed here until 2026-08-02 and has
been reattributed to **U1**, where it belongs. It came from finding **C1**, a naming-rule violation
found on the committed surface while reconciling the roadmap, not from frontend work raising a backend
defect, which is what every numbered row above is. Full detail under *U1 follow-up corrections*.

**It still shares one edge with item 1**, worth keeping visible when item 1 is worked: the controller
method became **`recordNote`**, not `record`, because `SalesController_record` already exists for
sales invoices and a **second** duplicate `operationId` is precisely item 1's defect.

⚠️ **Item 2's costed comparison is kept rather than deleted now that it is done.** It measured both
options against all 71 request-bodied operations, and the estimate-versus-outcome is the only
calibration data this decision produced: the sweep was predicted to touch no production code and to
show a small frontend blast radius, and it touched none and surfaced 19 errors, **all in fixtures**.
Worth reading before items 7 and 8 are scheduled.

*(Numbering is kept as originally assigned so existing references still resolve; the order of the
rows is the order to work in.)*

---

#### 1. **`InventoryController_writeOff` is the `operationId` of two operations, which OpenAPI forbids.**
`POST /api/inventory/write-offs` and `GET /api/inventory/write-offs/{id}` are two Java methods of
the same name, and `OpenApiSpecIT` derives the id as `Controller_method`, so it emitted an invalid
spec without complaining. Found by step 16 generating a TypeScript client from it: the output did
not compile, twenty duplicate-identifier errors in one file.

**Two parts, and the second is the one that matters.** Fix the collision — rename a controller
method, or disambiguate in the generator. Then make `OpenApiSpecIT` **refuse** a duplicate
`operationId` rather than writing one out: the generator produced a spec that no conforming
consumer can read, and said nothing, which is the failure mode step 16a's drift check exists to
prevent.

**Do not fold this into frontend work.** The frontend carries a documented workaround
(`frontend/orval.config.ts` suffixes the HTTP verb) pinned by `frontend/src/api/spec-hygiene.test.ts`,
which is written to fail **in both directions** — including the moment this is fixed. So the
sequence after the backend fix is: regenerate the spec, watch that test fail, delete the
de-duplication block in `orval.config.ts` and the assertion that pinned it, regenerate the client,
confirm green.

---

#### 2. **A primitive on a request-body record is mandatory, the spec said it was optional, and omitting it is a `400`. This broke product creation for every user.** — ✅ **primitive half DONE 2026-08-01**

> ### ✅ The primitive half is fixed. Approved and done before F4, exactly as scoped.
>
> `OpenApiSchema.recordSchema` marks a record's **primitive** components required. **One rule, both
> directions**: a primitive cannot be null, so on a request it is mandatory and on a response it is
> always present.
>
> | | |
> |---|---:|
> | Schemas declaring `required` — was 2 (`Money`, `UnitCost`) | **78** |
> | Spec diff | **+76 lines, −0**, 174 operations unchanged |
> | Generated client files changed | 76 (+176 −176) |
> | **Production code changed** | **none** — the generator is `src/test` |
> | Type errors it surfaced | **19, every one in a test fixture** |
> | Backend | `BUILD SUCCESS`, **1327 run**, 1 skipped (`LiveSeedTest`, disabled without a base URL) |
> | Frontend | 228 tests, lint, build, knip all green |
>
> **The 19 errors are the interesting part, and they were not busywork.** Not one was in production
> code — the whole blast radius was **fixtures claiming a wire shape the server never sends**:
> `Me.active` (11 sites), `Role.id` (4), `UnitOfMeasureView.active`, `RoleView.active`. Every screen
> test in the application was rendering against a `/api/me` with no `active` field, which the live
> probe shows is always present. The spec fix found that; nothing else had.
>
> **`product-create.tsx`'s workaround is no longer a workaround.** `serialTracked` is now
> `serialTracked: boolean` in the generated type, so **omitting it is a compile error** rather than a
> runtime `400`. Its comment says so. `spec-hygiene.test.ts` was rewritten from pinning the defect to
> pinning the guarantee — and still fails in both directions, including asserting that `NewRole`
> stays *undeclared*, so the day the guarded half lands somebody comes back to it.
>
> **What this did NOT close is items 7 and 8 below.** They are separate on purpose, not bundled.

##### The three things checked before it was approved

1. **One spec artefact, one producer, no second path.** `docs/api/openapi.json` is the only spec file
   in the repo; `OpenApiSpecIT` is its only producer; its consumers are orval, the paging-map
   generator, and CI's drift check. **There is no live `/v3/api-docs`**: springdoc is not a
   dependency, `unzip -l` finds **zero** springdoc/swagger entries in the deployed jar, and the `401`
   that `/v3/api-docs` returns is simply what Spring Security answers for *any* unmapped path —
   `/definitely-not-a-route-xyz` returns the same. So there is no second document to keep accurate.
   (springdoc was tried and rejected in step 16a: it introspects with Jackson **2** while
   `NovoCoreJsonModule` is Jackson **3**, so it could not see one of our serialisers and described
   `Money.amount` as a JSON number.)
2. **The redaction check now covers every response record, not just `ProductView`.** **53** response-
   side records carry a primitive; all were swept. **No exception.** No `@JsonInclude` override exists
   anywhere in the codebase, no construction site blanks a primitive with a literal default, and the
   only two mechanisms that withhold data both leave primitives alone: `ProductView.redactedFor`
   nulls three **reference-typed** fields (`Long supplierId` — boxed precisely because it is nullable
   — `String supplierSku`, `UnitCost lastPurchasePrice`), and `SettingView` substitutes a masked
   `String` for a secret's value. There is also direct wire evidence from F3's probe: `RoleView`
   returned `"fullAccess":false,"systemRole":false` — **`false` values present on the wire**, which
   is exactly what a `NON_DEFAULT` inclusion would have dropped.
3. **The anti-pattern numbering was inconsistent, and is fixed below** — see item 6.

#### 2 (continued). The original write-up

> ⚠️ **This item was rewritten on 2026-07-31 after the owner reproduced it live.** The previous
> version described it as an unreproduced edge case involving an *explicit* `null`, and recorded
> that "the current frontend sends `null` for neither field". **That was wrong, and the way it was
> got wrong is the lesson.** The browser check that cleared the create form **intercepted the POST
> and answered it with a fake `201`.** It captured the payload, the payload read correctly, and the
> screen navigated — to a stub. The real backend never saw that request. A verification that
> answers its own request verifies nothing, and it produced a confident, wrong sentence in this
> file. The rule it earns: **when the question is "does the backend accept this", the backend has
> to answer.**

**What actually happens**, measured against the running stack with `curl`, three bodies differing in
one field, all using an existing SKU so nothing could be created either way:

| Body | Answer |
|---|---|
| `serialTracked` **absent** — exactly what the create form sent | **`400` "Malformed request body: Cannot map `null` into type `boolean`"** |
| `"serialTracked": false` | `422` "A product with SKU … already exists" — i.e. it **parsed** and reached the domain |
| `"serialTracked": null` | the same `400` |

**An absent field and an explicit null are the same thing here**, which is the part that makes this
more than an edge case. `NewProduct` is a Java **record**, so Jackson deserialises through the
canonical constructor and hands an absent creator property in as `null`;
`FAIL_ON_NULL_FOR_PRIMITIVES` — on, in this application — then refuses it. Nothing reaches a
handler, so nothing in the domain can produce a better message, and the caller is told only that
its body was malformed, with **no field named.**

**And the spec says the field is optional.** `NewProduct` declares no `required` list — nor does any
other request body. Of 185 schemas, **exactly two declare a required field: `Money` and
`UnitCost`**, across 71 operations that take a body. So the generated TypeScript is
`serialTracked?: boolean`, `product-create.tsx` was written correctly against the published
contract, and **product creation failed for every user, every time.**

~~**The whole surface has exactly two such bodies**, established by grep over all 174 routes, and both
are the same field:~~

- `NewProduct.serialTracked` — `POST /api/products`
- `ProductController.SerialTrackingRequest.serialTracked` — `PATCH /api/products/{id}/serial-tracking`

Both were confirmed to answer `400` to `{}`. The PATCH is not hit in practice only because the
detail screen's editor always sends the field.

> ⚠️ **"Exactly two" was wrong, corrected 2026-08-01 while building F3.** That grep searched for a
> primitive **`boolean`**. A primitive **`long`** fails in exactly the same way and none were counted.
> `POST /api/users` with `roleId` omitted answers `400 "Malformed request body: Cannot map null into
> type long"` — proved live against the running stack, naming no field, on a route whose published
> contract calls the field optional.
>
> **At least 22 request records carry a primitive component**, including `NewAccount`,
> `NewBankTransfer`, `NewSalesInvoice`, `NewSalesInvoiceLine`, `NewPurchaseInvoice`,
> `NewPurchaseInvoiceLine`, `NewCreditNote`, `NewCreditNoteLine`, `NewGoodsReceipt`,
> `NewGoodsReceiptLine`, `NewSettlement`, `NewFreightAllocation`, `NewStockWriteOff`,
> `NewJournalLine`, `NewInventoryLot`, `NewStockConsumption`, `NewBundleComponent`, `NewChargeType`,
> `NewUnitOfMeasure`, `NewVatExemptionReason`, `NewProduct` and `NewUser`. **Every one is a route
> where an omitted id or flag is a `400` naming nothing.** They have not bitten only because the
> screens that would send those bodies do not exist yet — F5 onwards is precisely when they will,
> one at a time, exactly as Products did.
>
> This raises the priority of part 2 rather than changing it: **50 request-body schemas are reachable
> on the surface and zero declare a `required` list.** (The "2 of 185" above counts every schema,
> responses included; 0 of 50 is the figure a client is affected by.)

**⚠️ This codebase already documented the defect and already applies the fix — on one route.**
Found while probing F3's surface, and it changes what item 2 costs. `RoleController` declares:

```java
/**
 * @param restricted boxed, not a primitive. An omitted field on a primitive boolean arrives as
 *     false and would silently REMOVE a restriction the caller never mentioned — a wrong answer
 *     that looks like a successful request, which is worse than the 400 this produces instead.
 */
record FieldRestrictionRequest(Boolean restricted) {
    FieldRestrictionRequest { Required.field(restricted, "restricted"); }
}
```

Probed against the running stack: `PUT /api/roles/{id}/field-restrictions/{field}` with `{}` answers
**`400 "restricted" is required and was not supplied.`** — a plain 400 naming the field, in the
ordinary refusal shape. That is exactly what `POST /api/products` should have said.

So this is not a design question. **`serialTracked` is the same field type on the same surface with
the pattern not applied**, in the two places named above. The javadoc even states the second failure
mode, which is worse than the one we hit: had `FAIL_ON_NULL_FOR_PRIMITIVES` been off,
`PATCH …/serial-tracking` with the field omitted would have silently turned serial tracking **off**
and answered `200`. The 400 is the lucky outcome.

**Three parts, and the second is the one that matters.**

1. **Decide what a missing primitive should mean.** Almost certainly `Required.field`'s treatment —
   name the field — or a boxed `Boolean` with a stated default, which is a domain decision rather
   than a serialisation one.
2. **Make the spec say what a body requires.** This is the general defect and the one that will bite
   again: a client cannot distinguish mandatory from optional on any of 71 bodies. Until it does,
   every generated request type is a suggestion.
3. **Extend the sweep.** `PermissionSweepIT.noRouteFailsOnAnEmptyBody` sends `{}` and asserts only
   that the answer is not `5xx` — a `400` satisfies it. It should send `{}` **and** an explicit
   `null` per field, and require the answer to name what was missing.

##### 📊 The two ways to close this, costed against the surface rather than argued (2026-08-01)

**Measured first, so the choice is made on figures.** Every request body on the surface was scripted
against the Java record behind it, transitively — the top-level schema *and* everything reachable
from it, because a sales invoice's lines are nested and the earlier grep could not see them:

| | |
|---|---:|
| Request-body schemas on the surface | **50** |
| …declaring a `required` list | **0** |
| Schemas carrying a primitive component (17 top-level + 6 nested) | **23** |
| **Operations that send a primitive somewhere in the body** | **22 of 71** |
| Of those, **boolean flags** — the dangerous subset, see below | **7** |
| Operations the frontend calls **today** | **5** |

**The 7 booleans are the whole risk, and the ids mostly are not.** `NewProduct.serialTracked`,
`SerialTrackingRequest.serialTracked`, `NewUnitOfMeasure.fractionalQuantityAllowed`,
`NewAccount.expectedToClear`, `NewSettlement.remainderBecomesCustomerCredit`,
`NewCreditNoteLine.stockReturned`, `NewPurchaseInvoiceLine.reverseCharge`. **A form always sends an
id** — it comes from a select the operator had to choose — so a `long` is unlikely to be omitted in
practice. **A false flag is exactly what a form omits**, which is what `serialTracked` was.

⚠️ **And that is why a live probe cannot be relied on to find these.** A probe fills the form in and
submits; the failure appears when a user **leaves the optional-looking checkbox alone**. The standing
"the backend has to answer" rule lowers the cost of every other kind of contract defect, and is close
to blind to this one. F3 is an honest example in the other direction: `roleId` was caught only
because the probe deliberately sent a body *without* it.

**(a) Keep discovering them one screen at a time.** 17 operations remain, and they are not spread
evenly — they are concentrated in **F5 (sales invoices + credit notes, 5 operations including both
nested-line flags), F6 (purchase invoices + goods receipts, 3), F7 (settlements + bank transfers, 5)
and F8 (freight, write-offs, 2)**. F4 hits **exactly one**: `POST /api/units-of-measure`, and it is
one of the seven booleans. Per instance the cost is small now — a one-line workaround, a test pinning
the body, a queue entry — but it is paid **17 more times**, each on a screen whose defect surfaces
as *"creating this fails for everyone"* rather than as a compile error, and each leaving a workaround
that has to be found and removed when the backend is eventually fixed. **Three such workarounds
already exist and are pinned by tests written to fail when the fix lands** — that mechanism works,
and it is also 17 more places to unwind later.

**(b) One sweep, and it is much cheaper than "71 operations at once" suggests.** The spec generator
is a **single reflection loop over `RecordComponent`s** (`OpenApiSchema.recordSchema`), so the whole
change is *mark a component required when `component.getType().isPrimitive()`*:

- **It touches no production code at all.** `OpenApiSchema` and `OpenApiSpecIT` live in
  `backend/app/src/**test**/java`. No runtime behaviour changes, no migration, no new refusal — the
  application answers exactly as it does today. The only artefacts that change are a published
  document and the generated TypeScript.
- **The rule is accurate in both directions.** A primitive is never null, so it is always present in
  a response *and* always mandatory in a request. Checked against the one thing that could have made
  it a lie: redaction. `ProductView`'s three redactable fields are `Long supplierId` (boxed
  deliberately, because it is nullable), `String supplierSku` and `UnitCost lastPurchasePrice` — all
  reference types. **No primitive is ever blanked**, so no schema would claim a field that can go
  missing.
- **The frontend blast radius today is 5 call sites**, all of which already send every field, so
  `tsc` should report nothing. The change announces itself through an existing tripwire:
  `spec-hygiene.test.ts` pins the set of schemas declaring `required` and **fails in both
  directions** — it is written to go red the moment the backend starts describing its bodies.
- **The generator's javadoc is currently wrong and would be corrected by the same edit.** It
  justifies "nothing is marked required" with `default-property-inclusion: non_null`, which is a
  *response* argument applied to a method that also builds every request schema.

**What (b) does not close, and should not be sold as closing.** 28 schemas guard a **non-primitive**
field with `Required.*`/`requireNonNull` — mandatory in fact, invisible to reflection, because the
guard is inside a compact constructor. Declaring those needs an annotation on the record components
(or a marker interface) applied across ~28 records, which is the genuinely larger piece and carries
real judgement per field. **It is separable and should be separated**: the primitive half is
mechanical and closes the class of bug that has actually bitten twice; the guarded half is a
consistency improvement that has bitten nobody.

**Recommendation, and it is a recommendation rather than a decision.** Do the primitive half **before
F4**, not in parallel with it: it is small, it is test-scope-only, and running it *during* a frontend
step means regenerating the client mid-step for no reason. F4 then gets `fractionalQuantityAllowed`
declared rather than discovered, and F5 — which is the heaviest hit, and the step the roadmap already
singles out as deciding the whole document pattern — starts with an honest contract. Then, as a
follow-on rather than a blocker, **box the 7 booleans with `Required.field`**, which upgrades the
message from `"Cannot map null into type boolean"` to one naming the field. Leave the `long` ids
primitive; the spec fix is enough for them. The guarded-field half stays queued.

**What it costs if it is wrong:** a spec regeneration and a client regeneration, both reversible in
one commit, with no production code touched. That asymmetry is the strongest argument for doing it
now rather than after F5.

**The frontend carries a workaround in the meantime** (`3458ee6`'s follow-up): `product-create.tsx`
sends `serialTracked: false` explicitly, with the reason at the call site, pinned by
`spec-hygiene.test.ts`, which asserts that only `Money` and `UnitCost` declare required fields and
**fails the moment that changes in either direction** — so whoever fixes the spec is sent back to the
workaround rather than leaving it to rot.

**How it was found, and why it was nearly missed twice.** It first appeared as an unexplained log
line while diagnosing the Products wedge, and the browser check that was supposed to clear the
create form **stubbed the POST with a fake `201`** — so it verified the payload against nobody and
reported the form as working. The owner reproduced it in about a minute by filling the form and
pressing the button. `CLAUDE.md`'s named anti-pattern — *a client's mistake raised as a programming
error* — lists three guards and then states the residual in as many words: *"a wrong but non-empty
value… only as good as the message written for them."* **This was not even that. It is a body with a
field simply left out, refused by the parser, on a route whose own spec said the request was valid.**

---

#### 4. **The retail customer's own rules are thrown as `IllegalArgumentException`, so the caller is told nothing.**

`Customer` refuses two edits to the `RETAIL_WALK_IN` record with messages that are complete,
specific and exactly what an operator needs:

```
Customer 'Πελάτης Λιανικής' carries system key RETAIL_WALK_IN and is EXEMPT. A system record's
VAT treatment is fixed at DOMESTIC: it is not one identifiable party, so it cannot make a claim
about a party's status.

Customer 'Πελάτης Λιανικής' carries system key RETAIL_WALK_IN and a VAT number. A shared
anonymous record cannot hold one party's ΑΦΜ.
```

Both are `IllegalArgumentException`, so `WebExceptionHandler` correctly discards them — that type
means *our* code is wrong — and the caller gets `400 "Bad request."`. Measured against the running
stack: `PATCH /api/customers/1/vat-status` with `EXEMPT` and `PATCH /api/customers/1/vat-number`.

**Two neighbouring behaviours in the same class are already right**, which is what makes this a slip
rather than a design: `deactivate` throws `InvalidCustomerException` and answers `422` with its
reason, and so does the `INTRA_EU_B2B`-without-a-VAT-number rule. The remedy is to use the same type.

**Why no existing guard catches it, and this is the interesting part.** `CLAUDE.md` lists three
layers and states the residual; this sits exactly in it.

- `WebAuthorizationRulesTest` is scoped to `..core.web..`. `Customer` is in the **domain**, so the
  rule structurally cannot look there.
- `PermissionSweepIT.noRouteRefusesWithoutSayingWhy` sends **no body**. This needs a body that is
  well-formed and wrong only in the domain's terms.
- `noRouteFailsOnAnEmptyBody` looks for `5xx`. This is a `400`.

So the fix has two parts, and the second is the one that matters: change the type, then **give the
sweep a case that carries a valid body a domain rule refuses** — otherwise the fifth instance is
found the same way this one was, by someone poking at it by hand.

**Found while reading the Customers API for F2**, by probing the server rather than reading the
service — the `INTRA_EU_B2B` path answers correctly, so reading one rule would have suggested they
all did.

---

#### 3. ~~**There is no SKU search — `GET /api/products?sku=` is an exact lookup.**~~ — ✅ **CLOSED AS STALE, 2026-08-02**

**As raised (2026-07-31):** `ProductController.products` routed `sku` to `findBySkuFor`, which returns
nought or one product. The Products filter box was wired to it, so typing `TEST` against eight
`TEST-PRODUCT-*` SKUs matched nothing. The item asked the owner to choose between adding a real search
and relabelling the control, and said **"do not change the frontend until that is decided."**

**What actually happened:** the owner chose a real search endpoint, and **S1 delivered it on
2026-08-01** — `pg_trgm` + `unaccent` (V28/V29), one `IMMUTABLE` normalisation function, one shared
`TextSearch` specification, and `?search=` on the list routes (**seven of them today**, after F4 added
VAT classes and units of measure). The box sends `search=`. `sku=` and `ean=` deliberately stay exact,
because that is what a barcode scanner and an integration call use, and a scan matching a *substring*
of a barcode would put the wrong product on an invoice.

⚠️ **This row went on saying "needs an owner decision first" for a week after the decision was made
and the work shipped**, while `frontend/README.md` and the frontend roadmap both recorded it as
closed. Nothing failed; nothing could. It was found by reconciling this file against the repository.
**Two records of one fact, and the one a fresh session reads first was the stale one** — the same
shape as the 15c gap, and part of why there is now one roadmap file instead of two. The general rule
is in `CLAUDE.md`: *a decision reached in a design conversation gets the same close-out discipline as
a build step* — which includes **closing the item that was waiting on it**.

---

#### 5. **A role's `description` can be set once and never changed — there is no route.**

`NewRole` takes `name` and `description`, and `RoleService` exposes `rename` and nothing else. There
is **no `PATCH /api/roles/{id}/description`** anywhere on the surface, so a description typed into
the create form is permanent: the only way to correct a typo in one today is to create a second role,
move every holder across, and deactivate the first.

**The asymmetry is almost certainly an oversight rather than a decision**, because nothing in
`RoleServiceImpl`, `RoleController` or the step 16b proposal gives a reason for it, and the parallel
field on every other entity is editable. A description is not a permission — changing it confers
nothing — so none of the escalation guards that make role editing careful apply to it.

**The fix is a `PATCH …/description` mirroring `rename`**: `@Requires(USERS_AND_ROLES, FULL)`,
`editableRole` (so system roles stay untouchable, consistent with every other role write), an audit
entry, and the updated `RoleView` returned. `DescriptionRequest` already exists in
`TaxLookupController` and would need either reuse or a sibling.

**The frontend renders it read-only in the meantime, and deliberately not through `FieldEditor`.**
`editable: false` in this application means *"not yours to edit"* — a VIEW grant — and would tell a
full-access administrator something false. `role-detail.tsx` draws a plain row with the reason
beside it, and `role-create.tsx` says on the form that this is the only chance to set it. **Both
notes come out when this lands.**

---

#### 6. **`NewUser` and `NewRole` guard request-body fields with `Objects.requireNonNull` — the fifth confirmed instance of "a client's mistake raised as a programming error".**

##### ⚠️ The numbering, stated once so the queue stops disagreeing with itself

**Three anti-patterns are named in `CLAUDE.md`, and items 4 and 6 belong to the same one:**

| Named anti-pattern | What it is | Items here |
|---|---|---|
| **Proxy self-invocation** | a class calling its own `@Transactional` method, so Spring's proxy is bypassed and the annotation does nothing | none outstanding |
| **A verification that answers its own request** | a check whose subject is stubbed, so it can only confirm what it was told | none outstanding — it is now the standing rule |
| **A client's mistake raised as a programming error** | an exception type meaning *our code is wrong* used to tell a caller *their request is wrong*, so `WebExceptionHandler` correctly discards the message | **items 4 and 6** |

**Two counts were being conflated.** `CLAUDE.md` numbers **three disguises** found inside step 15 —
(1) `IllegalArgumentException` for parameter guidance, (2) `Objects.requireNonNull` on a request-body
field, (3) `IllegalArgumentException` for an id that names nothing. Item 4 counts **instances across
the project** and its own text says the next one would be *"the fifth instance"*.

**So, current and authoritative:** item 4 is the **fourth** confirmed instance (disguise 1/3's shape,
found in F2). **Item 6 is the fifth** — disguise **2** recurring at a new site. Calling it "instance
2" in the first draft mixed the disguise number with the instance count; that is corrected here.

**Item 4 predicted exactly this.** It says the fifth would be *"found the same way this one was, by
someone poking at it by hand"* — and it was, by reading `NewUser` while building F3. The prediction
holding is itself the argument for its part 2: give the sweep a case carrying a **valid body that a
domain rule refuses**, or the sixth arrives the same way.

**Why no guard catches item 6 either, and it is the same blind spot as item 4:**
`WebAuthorizationRulesTest` is scoped to `..core.web..` and these records are in **`core-api`**;
`noRouteRefusesWithoutSayingWhy` sends no body; `noRouteFailsOnAnEmptyBody` looks for `5xx` and this
is a `400`.

---

Still present in two records:

```java
public record NewUser(String username, String displayName, String rawPassword, long roleId) {
    public NewUser {
        Objects.requireNonNull(username, "username");       // ← a caller's omission,
        Objects.requireNonNull(displayName, "displayName"); //   raised as our bug
        Objects.requireNonNull(rawPassword, "rawPassword");
    }
}

public record NewRole(String name, String description) {
    public NewRole { Objects.requireNonNull(name, "name"); }
}
```

**The remedy is `Required.text` / `Required.field`**, exactly as `ReversalCommand` and the step 15
sweep applied everywhere else — one statement per field, and the caller is told which field is
missing instead of being handed a message that describes our internals.

**⚠️ Its severity is genuinely lower than it looks, and the claim is backed by a test rather than by
reasoning** — worth recording so nobody re-raises it as urgent. `UserServiceImpl.create` and
`RoleServiceImpl.create` **re-check every one of these fields properly** — `requireUsername`,
`requireText`, `PasswordPolicy.check` — and throw `InvalidUserException` / `InvalidRoleException`,
which map to a `422` with a real message. The `requireNonNull` guards fire **only** when a field is
absent from the JSON.

**The evidence that it is a `400` and not a `500`:** `PermissionSweepIT.noRouteFailsOnAnEmptyBody`
sends literally `"{}"` to **every** non-GET/DELETE route with no exclusions and asserts the answer is
not `5xx`. The suite is green, so `POST /api/users` with `{}` — the exact body that trips
`requireNonNull(username)` — is already proven not to explode, on every build. That is stronger than
a one-off probe would have been, and it is why this sits below item 2 rather than beside it.

**Not reachable from the F3 screens**, which always send every field and are tested on the exact
body. Fix it with item 2 — same family, same files, and item 2's audit has to open both records
anyway.

---

#### 7. **Box the 7 boolean primitives with `Required.field`, so the refusal names the field.**

**Split out of item 2 deliberately, and approved as a separate later follow-on rather than bundled.**
Item 2's primitive half made these fields *knowable from the contract*; this makes the refusal
*readable* when somebody sends the body anyway — a raw HTTP client, an adapter, a future screen built
against a stale client.

The seven, and they are the whole set:

| Record | Field | Route |
|---|---|---|
| `NewProduct` | `serialTracked` | `POST /api/products` |
| `ProductController.SerialTrackingRequest` | `serialTracked` | `PATCH /api/products/{id}/serial-tracking` |
| `NewUnitOfMeasure` | `fractionalQuantityAllowed` | `POST /api/units-of-measure` |
| `NewAccount` | `expectedToClear` | `POST /api/accounts` |
| `NewSettlement` | `remainderBecomesCustomerCredit` | `POST /api/settlements` |
| `NewCreditNoteLine` | `stockReturned` | `POST /api/credit-notes` (nested) |
| `NewPurchaseInvoiceLine` | `reverseCharge` | `POST /api/purchase-invoices` (nested) |

**Booleans and not the `long` ids, and the distinction is the reasoning.** A form always sends an
id — it came from a select the operator had to choose. **A false flag is exactly what a form omits.**
`serialTracked` was precisely that, and the ids have never been the failure.

**The pattern already exists one file away**: `RoleController.FieldRestrictionRequest(Boolean
restricted)` with `Required.field(restricted, "restricted")` answers `400 "restricted" is required
and was not supplied.` against `POST /api/products`'s `400 "Cannot map null into type boolean"`.
Both were measured in the same probe run.

⚠️ **Its javadoc names a second failure mode that is worse than the one we hit**, and it applies to
all seven: had `FAIL_ON_NULL_FOR_PRIMITIVES` been off, an omitted flag would arrive as `false` and
**silently turn the setting off**, answering `200`. The `400` is the lucky outcome. Boxing removes
the luck.

**Not urgent, and the reason is now in the contract:** with item 2 done, `tsc` refuses a frontend
call that omits one of these, so the only callers that can still hit it are non-TypeScript ones.

---

#### 8. **Declare every compact-constructor requirement — requests *and* responses. The half reflection cannot see.**

> ✅ **Scope widened 2026-08-01, approved.** It was written for request bodies only. It now covers
> **response records too**, because the same annotation read by the same generator closes the last
> fixture-drift gap **for free** — see the fixture note below — rather than needing a second
> mechanism beside it. **90 records** across the surface guard at least one reference-typed field in
> a compact constructor, of which **28 are request bodies**.

**Its own item, not bundled into item 2, and deliberately the last of the three.** A reference-typed
component required by a compact constructor (`Required.field` / `requireNonNull`) is mandatory in
fact and **invisible to `OpenApiSchema`**, because reflection cannot look inside a constructor body.

`NewRole` is the readable example and is pinned by a frontend test: it declares no `required` list at
all, `POST /api/roles` with `{}` is still refused, and `spec-hygiene.test.ts` asserts that
`NewRole.required` stays **undefined** — so the day this lands, somebody is sent back to the
frontend to decide what is newly knowable.

**It needs a decision before it needs code.** Reflection cannot infer it, so the requirement has to
be *declared*: an annotation on the record component (`@Mandatory`, read by the generator), a marker
the `Required` helper itself records, or a hand-maintained table in the generator. The first is the
obvious candidate and the one with a real cost — it is ~28 records and it puts an annotation in
`core-api`, which is the module the architecture rules keep deliberately thin.

**On the request side this class has bitten nobody**, and that is worth saying so it is not
re-raised as urgent: every one of those fields is re-checked by the service with a message that names
it, so the observed failure is an ordinary refusal an operator can act on — not the silent,
field-less `400` that primitives produced.

**The response side is where the value is, and it is why the scope was widened.** `CustomerView` and
`SupplierView` guard 2 reference-typed fields each, `UserView` 3, `RoleView` 6. Those fields are
**always present on the wire and optional in the generated TypeScript**, which is exactly the hole
that let 19 test fixtures drift undetected until item 2's primitive half exposed them. Declaring them
lets `tsc` enforce fixture completeness — the one thing no test in this repository can do honestly,
because every other candidate source of truth about the wire is hand-authored.

**So the sequencing is: this closes the fixture-drift class properly, and item 9 only reduces the
duplication that made the last occurrence expensive to fix.** They are not alternatives.

---

#### 9. ✅ **Shared `Me` test fixture — DONE 2026-08-01, narrowly scoped as approved.**

**`frontend/src/test/fixtures.ts`**: `aUser(...)`, `everySectionAt(level)` and `OWNER_ROLE`.

**The scope was the whole point of the approval, so it is recorded here rather than left to the
diff.** Only the **invariant** fields moved — `id`, `username`, `active`, `restrictedFields` — which
is exactly the set no test reads and every fixture has to get right anyway, and exactly the set that
drifted. **`role` and `sections` are required parameters, not defaults**, because which sections a
role holds and whether it is full-access are the *content* of these tests: a reader has to see them
at the call site or the test stops being evidence and becomes a pointer to a shared file.

| | |
|---|---:|
| `Me` literals converted | **15**, across 6 files |
| Left alone | 2 — both `{ ...owner, id: n }` spreads, which already inherit correctly |
| Local copies of `everySectionAt` removed | 5 |
| Not converted, deliberately | `app.test.tsx`, `session.test.tsx` — they build a raw JSON body and **assert on `displayName`**, so their identity fields are content too |
| Tests | 228, unchanged and all passing |

⚠️ **A `CUSTOM_ROLE` constant was written and then deleted.** Every viewer fixture names its own role
(`VIEWER`, `PROBE-ADMIN`, `BOOKKEEPER`) and that name is the contrast the test is drawing — sharing
one would have taken content out of the call site, which is what the approval ruled out. knip caught
it as an unused export, which is the tool doing exactly its job.

**This does not close the fixture-drift class** — item 8 does. See the note below for why nothing
more should be built here.

---

#### 📐 The reasoning behind item 9, and the argument against building more than it

**The question this answers:** item 2's sweep caught 19 test fixtures that had drifted from the real
wire shape. Is anything catching that class going forward, or does it only resurface the next time a
field becomes required?

**What is now permanently caught, established by probing the type system rather than assuming:**

| Drift shape | Caught? |
|---|---|
| A fixture omits a **primitive** (`Me.active`, `Role.id`) | ✅ **yes, on every typecheck** — this is what found the 19 |
| A fixture carries a **field that does not exist** | ✅ yes — excess-property checking on object literals |
| A fixture omits a **reference-typed field that is mandatory in fact** (`CustomerView.name`) | ❌ **no** |

**So one hole remains — and it is measured at zero.** Every non-spread fixture in the suite already
carries every reference-typed field its record's compact constructor requires; the three spread-built
fixtures inherit from complete bases. **The 19 were not the visible half of a larger mess: they were
the whole of it.** (First count of this said 18 more were hiding. That count was wrong — it read
guard names from sibling code and treated spread-inherited keys as missing. Recorded because the
corrected figure is what changed the recommendation.)

**Which argues against most of what could be built here:**

- **A frontend "drift test" should not be built.** It would need a source of truth for what the
  server actually sends, and the frontend has none: fixtures, mock handlers and expectations are all
  hand-authored, so a test comparing them to each other is the named anti-pattern — *a verification
  that answers its own request*. The only honest source is the real server, which the frontend suite
  cannot reach in CI.
- **A backend "is the spec's optionality honest?" report would cry wolf.** It could compare declared-
  optional response fields against what real routes emit — but a genuinely nullable field that simply
  happens to be set in the fixture data (`Product.ean`) reads identically to a mandatory one. It
  needs an allowlist, allowlists decay, and `CLAUDE.md` is explicit that a rule which cries wolf is
  one somebody deletes.
- **Generating fixtures from the spec is over-engineering** against a backlog of zero.

**What is worth doing, and it is a duplication argument rather than a correctness one.** There are
**19 hand-authored `Me` literals across 7 test files**, most of them a near-identical `owner` and
`viewer` pair. That duplication is *why* one field being wrong meant editing 11 sites. A small
`src/test/fixtures.ts` — `anOwner()`, `aViewerOf(section)`, and the `RoleView`/`UserView`
equivalents — makes the next such fix one line, and makes a new screen test start from a complete
shape instead of a plausible one.

⚠️ **The tradeoff, stated because it is real:** this codebase values tests that read as evidence on
their own, and an extracted fixture is an indirection away from that. The differences between these
fixtures — which sections, at which level — are the *content* of each test and must stay at the call
site. Only the invariant part (`id`, `username`, `active`, `restrictedFields`) should move.

**The genuine closure is free if item 8 is built with response records in scope** — the annotation
makes `name`, `sku`, `hiddenFields` and the rest required, and `tsc` then enforces the third row of
the table above. That is a reason to widen item 8, not to add a mechanism beside it.

---

**Steps 0–16b are complete, committed and pushed. `mvn verify` is green at 1327 tests across 174
routes, with exactly one skip — `LiveSeedTest`, which is disabled unless `-Dnovocore.seed.base-url`
is given and has been since F0 built it. Step 16, the frontend, is in progress** — see *Step 16 — the
frontend* above for what has landed.

**There is no known correctness defect in the ledger**, and step 15 is the strongest evidence this
project has for that claim: a full trading quarter, built by nothing but HTTP requests, satisfies all
twelve universal invariants — and still does after being dumped, restored into a fresh database and
swept again.

### ➡️ Step 16, the frontend — under way. **F0–F4, S1 and S2 are done; Q1 is next, then R1, then F5.** *(This heading said "F4 is next" until 2026-08-02 — stale by two steps. Current status is at the top of this file.)*

#### ✅ F3, Users & Roles — done (2026-08-01). Both decisions taken before building

**Not a party record, and not the master-data pattern stretched to fit.** 18 routes over two
entities that reference each other, one of which (`Role`) is a *permission* document: a name plus a
map of `Section → AccessLevel` plus a set of restricted fields. There is no `active`-filtered list
of things with a VAT number here, and reaching for `supplier-detail.tsx` as a template would produce
the wrong screen.

| | |
|---|---|
| Roles | `GET/POST /api/roles`, `GET /api/roles/{id}`, `/name`, `/deactivate`, `/reactivate`, `/users`, **`PUT /grants/{section}`**, **`PUT /field-restrictions/{field}`** |
| Users | `GET/POST /api/users`, `GET /api/users/{id}`, `/display-name`, **`/password`**, **`/role`**, `/deactivate`, `/reactivate` |
| Reference | `GET /api/sections` — every section with an `available` flag |

**Shapes worth noting before anything is drawn.** `RoleView.sectionGrants` is a **map**, not a list,
so the natural control is a grid of sections × levels rather than a form of fields — and `GET
/api/sections` exists precisely to enumerate the rows. `NewRole` takes only `name` and
`description`: **a role cannot be created with grants, or with `fullAccess`**, so creating one and
granting it are necessarily two steps and the screen must not pretend otherwise. `NewUser` carries a
`rawPassword`, which is the first password field in this frontend and the first place a value must
never be echoed back, logged, or put in a query key.

##### ✅ The Step 16b escalation guards, exercised as a real limited-privilege account

Not read from the service and not asserted through a mock. A role holding **`USERS_AND_ROLES:FULL`
and nothing else** was created, an account put in it, and the compound path walked as that account:

| # | Attempt | Answer |
|---|---|---|
| 1 | Widen its **own** role (`PRODUCTS:FULL`) | **`422`** — *"You cannot change the permissions of 'PROBE-ADMIN', which is your own role… Ask another administrator."* |
| 2a | Create a **second** role | `201` — legitimate on its own, which is the point |
| 2b | Grant that role **`JOURNAL:FULL`**, which the actor does not hold | **`422`** — *"…because your own role has NONE there. Access can only be passed on, never invented."* |
| 3 | Grant that role `USERS_AND_ROLES:VIEW`, which the actor **does** hold | `200` — the guard is "no wider than you hold", not "nothing at all" |
| 4 | Create an account in the **OWNER** role | **`422`** — *"…Otherwise administering users would be a route to unlimited access: create an account in a full-access role, then log in as it."* |
| 5 | Move **itself** into the Owner role | **`422`** — *"You cannot change your own role…"* |

**All four guards hold, and every refusal is a `422` carrying its own reason.** That is a direct
input to the screen rather than a box ticked: F3 can surface these with the shared `Refusal` and say
nothing itself — unlike the retail customer, where two rules answer a bare `400` and the client had
to mirror the text. **Where a guard explains itself, the screen must not restate it**, or the two
drift.

Row 3 is the one worth keeping in view: it is what stops the screen implementing "an administrator
may not grant anything", which would be wrong and would make the section unusable.

##### ⚠️ The grid trap: a full-access role holds everything with **no grant rows at all**

**The single most important thing F3 leaves behind, and it is a design constraint rather than a
defect that was found in running code.** `RoleView.sectionGrants` is **empty** for Owner and Admin:
their access is the `fullAccess` flag, and they carry no `role_section_grant` rows whatsoever. So a
grid built from the map alone renders **seventeen rows of `NONE` for the two most privileged roles in
the system** — the screen stating the exact opposite of the truth about precisely the roles where
being wrong matters most.

**It never shipped that way.** The shape was known before the grid was written, from the same fact
`RoleServiceImpl.refuseIfCallerCannotConferIt` already depends on (it reads through
`RoleView.accessTo` rather than off the grant rows, "because a check reading them directly would
conclude the Owner may grant nothing"). `role-grants.tsx` checks `fullAccess` first and renders every
row as `FULL`; the rows themselves come from `GET /api/sections`, never from the record.

**A test holds it in the failing direction** — it asserts every row reads `Full` for a full-access
role, so removing the flag check goes red rather than silently redrawing Owner as having no access.
Also written up in `frontend/README.md` under *"A grid of permissions is drawn from the catalogue,
never from the record"*, which is the copy a future screen will find.

⚠️ **The same shape will recur wherever a permission is displayed**, because the flag-versus-rows
asymmetry is in the data model rather than in this screen.

##### Two things the fresh read caught

- **`PUT …/field-restrictions/{field}` handles the missing-primitive case correctly**, and its
  javadoc explains why boxed-plus-`Required.field` is the right shape. That is the fix backend
  item 2 needs, already written down and already working one file away — recorded there, because it
  turns that item from a design question into an application of an existing pattern.
- **`GET /api/sections` reports `available` per section**, which is what lets a grant grid show
  "granted but not built yet" honestly rather than offering a permission that leads nowhere.

##### ✅ Both open questions, decided by the owner on 2026-08-01 — before anything was built

1. **The grant grid is a segmented three-state toggle per row** — NONE / VIEW / FULL — with every
   level the caller cannot confer rendered **disabled with its `lockedReason`**, matching the pattern
   Customers established.
2. **Setting somebody else's password is generate / display once / acknowledge.** The value is
   generated, shown once with a copy affordance, and the dialog **cannot be closed until the
   administrator explicitly acknowledges having taken it**. Never shown again, never retrievable.
   **No confirm-field** — the same shape as every credential hand-off already used in this project.

##### 📋 Approvals given after F3 landed, and their verdicts

Reconciled here rather than left in a session's history, per the checklist rule.

| Approved | Scope as approved | Verdict |
|---|---|---|
| **Item 2, primitive half** — sweep the spec generator before F4, not parallel to it | primitives only; boxing and the guarded half kept out | **Done** — `dee71ba`. 2 → 78 schemas, no production code touched, 19 fixtures corrected |
| **Item 8, widened** | cover **response** records as well as request bodies, same annotation mechanism | **Done as a scope change** — item 8 rewritten; it is queued work, not built. It is now the thing that closes fixture drift |
| **Item 9, narrowly** | only the invariant `Me` shape moves; anything a test asserts on stays at the call site | **Done** — `src/test/fixtures.ts`; 15 literals converted, `role` and `sections` required parameters, `CUSTOM_ROLE` written and deleted for taking content out of a call site |
| **Item 7** | box the 7 booleans, later and separate | **Explicitly deferred**, queued as its own item with the full list of seven |
| Build nothing else for fixture drift | — | **Held.** The argument against a frontend drift test and a backend optionality report is written up under the item 9 note |

##### 📋 F3's checklist, reconciled against what was approved

| # | Sub-part | Verdict |
|---|---|---|
| 1 | `SegmentedControl` — one exclusive choice, per-option `disabledReason`, built on shadcn `toggle-group` | **Done** — 3 tests, including that pressing the pressed option sends nothing |
| 2 | Grant grid: `GET /api/sections` × {NONE, VIEW, FULL}, `PUT …/grants/{section}` per cell | **Done** — driven live in both browsers |
| 3 | Field restrictions: the three `ProtectedField`s, `PUT …/field-restrictions/{field}` | **Done** — the boxed `restricted` always stated, asserted |
| 4 | Roles list — name, description, what it grants, system / full-access / inactive flags | **Done** |
| 5 | Role detail — rename, grants, restrictions, deactivate / reactivate, and its holders | **Done** |
| 6 | Role create — `name` + `description` only, saying that grants are the next step | **Done, proved against the real backend from the form** |
| 7 | Holders (`GET /api/roles/{id}/users`), because deactivation is refused while anybody holds it | **Done** |
| 8 | Users list — username, display name, role, inactive flag | **Done** |
| 9 | User detail — display name, role, deactivate / reactivate | **Done** |
| 10 | Set password — generate, show once, copy, forced acknowledgment | **Done** — and the value it displayed was proved to sign that account in |
| 11 | User create — username, display name, role, and the same hand-off for the first password | **Done, proved against the real backend from the form** |
| 12 | Nav + routes: `/roles`, `/roles/new`, `/roles/:id`, `/users/new`, `/users/:id` | **Done** — `users` and `roles` are **two** menu items now, and the `users` label changed from "Users & Roles" to "Users" |
| 13 | EN + EL strings | **Done** — 76 keys each |
| 14 | Tests, including the standing "rendering sends no write" guard on every screen | **Done** — 34 new; 194 → 228 |
| 15 | Proved against the **real** backend, per the standing rule — not against the mock server | **Done** — see below, including what it found |

##### What the live probe was, and what it left behind

The owner chose the probe account be created directly in the database rather than exchange a
credential. So: a role `TEST-PROBE-F3-ADMIN` holding **`USERS_AND_ROLES:FULL` and nothing else**, and
an account in it, inserted with a bcrypt hash generated for the purpose. Everything after that went
through the real application over HTTPS — **nothing intercepted, nothing stubbed.**

**Eight bodies proved without writing anything**, each answered by a refusal only reachable if the
body *parsed*: `NewRole` and `NewUser` against a name the domain already holds (`422`), and
`GrantRequest`, `FieldRestrictionRequest`, `PasswordRequest`, `RoleRequest`, `DisplayNameRequest` and
`NameRequest` against ids that name nothing (`404`).

**Then once, for real, in Chrome and Firefox both**, through the screens: a role created *from the
form*, granted, narrowed and widened again, a field restricted, renamed; an account created *from the
form*, given a password through the hand-off dialog, renamed, moved between roles. **The password the
dialog displayed was then used to sign that account in** — which is the only check that proves the
value on screen is the value that was set.

The confer guard fired from the browser exactly as the groundwork found it, with its own sentence on
screen. And the grid's disabled cell was confirmed **unclickable**, not merely styled.

**Residue, stated rather than implied.** All eleven rows created were deleted; the fixture is back to
**1 user, 3 roles, 4 grants, 0 field restrictions**. What remains and cannot be removed:
`app_user_id_seq` is at 13 (the fixture uses 1) and `app_role_id_seq` at 9 (the fixture uses 1–3),
and **20 audit entries** under the probe's username — append-only by trigger, which is correct.
⚠️ The four rows inserted by SQL have **no** audit entries, because they bypassed the service layer;
that is a property of the method the owner chose, and is worth knowing when reading the log.

##### 🐛 What the probe found — and it corrects a claim in backend item 2

**`NewUser.roleId` is a primitive `long`**, so `POST /api/users` with the field omitted answers
`400 "Malformed request body: Cannot map null into type long"` — **naming no field**, while the spec
calls it optional. Proved live. That is the *same defect as `serialTracked`*, which item 2 says is one
of exactly two on the surface.

**That claim is wrong, and the way it is wrong is the useful part.** The grep behind it searched for a
primitive **`boolean`**. Every primitive **`long`** fails identically and none of them were counted.
**At least 22 request records carry a primitive field** — `NewAccount`, `NewBankTransfer`,
`NewSalesInvoice`, `NewPurchaseInvoice`, `NewCreditNote`, `NewGoodsReceipt`, `NewSettlement`,
`NewFreightAllocation` and the rest — and every one of them is a route where an omitted id is a `400`
that names nothing. Nothing has hit them because no screen exists for those routes yet. **F5 onwards
will hit them one at a time**, exactly as Products did.

Measured directly off the published spec while checking this: **50 request-body schemas are reachable
on the surface and _zero_ of them declare a `required` list.** (Item 2's "2 of 185" counts all
schemas, including responses; 0 of 50 is the number that matters to a client.)

**The contrast that settles the design question** was in the same probe run, one route apart:

| Body | Answer |
|---|---|
| `POST /api/users` with `roleId` omitted — a primitive `long` | `400 "Cannot map null into type long"`, **no field named** |
| `PUT …/field-restrictions/{field}` with `restricted` omitted — a boxed `Boolean` + `Required.field` | `400 **"restricted" is required and was not supplied.**` |

**F3 itself is safe from it**: `user-create.tsx` always sends `roleId` and cannot submit without a
role chosen, and a test asserts the exact body. The defect is queued, not carried.

**Two smaller backend items this raised**, both new:

- **A role's `description` can be set and never changed.** `NewRole` takes one; there is no
  `PATCH …/description` anywhere on the surface. The screen renders it as plain text with the reason
  beside it rather than through `FieldEditor`, because `editable: false` in this application means
  *"not yours to edit"* and would say something false here.
- **`NewUser` and `NewRole` use `Objects.requireNonNull` on request-body fields**, which is
  `CLAUDE.md`'s named anti-pattern instance 2 in a record that predates `Required.field`. Not
  reachable from the F3 forms, which always send every field.

##### 📋 F3's close-out (2026-08-01) — and the one thing it leaves genuinely open

**All fifteen sub-parts have a verdict above, and all fifteen are done.** The five approvals given
after F3 landed have verdicts too — four done, one (**item 7**, boxing the seven booleans)
**explicitly deferred** and queued with its full list. **No sub-part is without a verdict**, which is
the finding this reconciliation exists to produce and this time there isn't one.

**Hours are now measured and the roadmap row is no longer blank** — `496c7be`→`aea0e56`, **0.87 h
active, 259k out**, recorded as **0.9**. The earlier note said the figure could not be measured
because F3 had no commit boundary before its own close-out; this *is* that close-out, so the boundary
now exists. Full split and caveats in `novocore-roadmap.md` under ᶠ³.

⚠️ **F1 and F2 remain blank deliberately, and were re-examined rather than assumed.** A window does
exist for each (`0a957d1`→`b406b27` yields 0.36 h, `b406b27`→`496c7be` yields 0.51 h), **but the
reason those rows are blank was never "no commit exists"** — it was that the bounding commits are
docs-and-practice commits made *part-way through* the same sitting, so the window slices the session
instead of bounding the step, and the figure would under-count by an unknown amount. That objection
still holds, so **no number was written in**. Recorded here so the next session does not re-derive it
and reach the opposite conclusion.

**⏳ Open: the owner's manual acceptance pass on F3, before F4 starts.** Everything above was proved
by automated tests plus a live browser probe driven by Claude; this is the owner driving the same
screens independently. Seven checks, agreed at close-out:

| # | Check | What it is really testing |
|---|---|---|
| 1 | Owner and Admin role detail pages show **full access in every section**, never `NONE` | The grid trap above — the one defect most likely to be reintroduced by the next screen that displays a permission |
| 2 | Deactivating a role with an active holder **names the holders** | That the `422` reaches the screen through `Refusal` and is not a dead end |
| 3 | Every level the operator cannot confer is **disabled with the real reason** | `SegmentedControl`'s `disabledReason`, and that `NONE` is never locked |
| 4 | Create a user, then **sign in as that user with the exact password the dialog showed** | The only check that proves the displayed value is the value that was set |
| 5 | **No confirm-field anywhere**, and Escape / outside-click do not dismiss before acknowledgment | The hand-off's whole design — the failure mode is closing the dialog without having taken the value |
| 6 | The nav shows **"Users" and "Roles" as two separate items** | Sub-part 12, including the label change away from "Users & Roles" |
| 7 | A role's **description is genuinely uneditable** | Item 5 — there is no `PATCH …/description` route, and the screen must say so rather than offer an edit that cannot work |

**If any of these fails it is an F3 defect and is fixed inside F3**, not carried into F4 — the step-15
discipline this project already follows. Until the pass is done, F3 is *built and verified by test and
probe*, which is not the same as *accepted*.

---

#### 📋 F2, Customers — done (2026-07-31, `b406b27`’s successor). Both decisions taken before building

**The shared extraction happened first**, before anything Customers-specific: `lib/vat-status.ts`
and `components/vat/vat-status-field.tsx` came out of `pages/suppliers/`, Suppliers was rewired onto
them, and **its 18 tests passed unchanged** — which is what proves the move rather than a claim that
it is equivalent.

**The two open questions, decided by the owner and then built as decided:**

1. **The protected retail record shows its locked controls disabled, with the domain's real
   explanation — never hidden, never left to produce a bare `400`.**
2. **The VAT class override is deferred** to its own follow-up, with its own scrutiny once the
   `TAX_AND_CHARGES` gating and the accounting implications are worked through. F2 is
   name / contact / VAT status / deactivate. ⚠️ **Since 2026-08-02 it is a sub-item of step 18**, and
   permission gating is no longer the main reason — see *U1 follow-up corrections*.

| # | Sub-part | Verdict |
|---|---|---|
| 1 | Extract `lib/vat-status.ts` and `components/vat/vat-status-field.tsx` first | **Done** — Suppliers' tests pass unchanged on them |
| 2 | `FieldEditor` grows a **locked** state | **Done** — and see the distinction below, which is the part worth keeping |
| 3 | Customers list, with the structural record marked | **Done** — a badge, so it is known before it is opened |
| 4 | Customer detail — name, VAT number, contact, VAT status | **Done** |
| 5 | Deactivate / reactivate with `Refusal` | **Done** |
| 6 | The retail record's three locks | **Done** — verified in Chrome **and** Firefox against the live record |
| 7 | Create form, no VAT class override | **Done, proved against the real backend** |
| 8 | Routes, EN + EL strings | **Done** — 22 keys each |
| 9 | Tests | **Done** — 12 new; 194 total |
| 10 | VAT class override | **Explicitly deferred**, and since 2026-08-02 **attached to step 18 (Prosvasis Go adapter) as a named sub-item** — it is adapter-dependent work, not a leftover screen task. A test asserts the field is **absent**, so adding it is a deliberate act with a test to update rather than something that drifts in with a copied screen; **that test stays and is not to be weakened.** Reasoning in full under *U1 follow-up corrections* and roadmap ᶠ²ᵃ |

##### The distinction `FieldEditor` now draws, which is the reusable part

`editable: false` and `lockedReason` are **not** the same thing and must not be collapsed:

- **`editable: false`** — "not yours to edit", from a VIEW grant. **No affordance at all.** A
  disabled button here tells somebody to keep trying at something their role will never allow.
- **`lockedReason`** — "editable in general, fixed on *this record*". **Shown, disabled, with the
  reason.** Hiding it would leave an operator hunting for a setting that exists on every other
  customer.

`editable: false` still wins, and a test asserts it: a VIEW role sees no buttons **and** none of the
lock explanations, because why this record is special is not information a read-only role needs in
place of the edit it cannot do anyway.

##### Two things reading the API fresh caught that copy-and-adjust would not

- **The retail record's own rules are only *partly* refused well.** Deactivation and the
  `INTRA_EU_B2B` rule answer `422` with full reasons; setting `EXEMPT` or a VAT number answers
  `400 "Bad request."` and nothing, because those are thrown as `IllegalArgumentException` from the
  domain. **Reading one rule would have suggested they all worked.** This is why the screen carries
  the explanations itself — a mirror, recorded as one, to be reconsidered when backend item 4 lands.
- **Customers do not reject duplicate names; suppliers do.** F1's clean trick — create against an
  existing name, get `422` if the body parsed, write nothing — **does not work here**: it answered
  `201` and created a row. Found by trying it, and the two rows it created were deleted. So F2's
  creation proof is a real create in each browser, with the rows removed after.

**Residue, stated rather than implied:** `customer_id_seq` is at 9 where the fixture uses 1–5, and
the audit log holds the create and delete entries plus two renames of the retail record (probed and
reverted through the API), being append-only by trigger. The fixture is back to 5 customers, one
user.

---

---

#### F1, Suppliers — done

#### 📋 F1 scope, read off the API surface (2026-07-31). **Two items need an owner decision before they are built — marked ❓.**

The `SUPPLIERS` section has 11 routes. Seven are screen work; two are deliberately **not** in F1.

**Both ❓ were decided by the owner on 2026-07-31, before any of it was built:** VAT status and its
exemption reason are **one editor with the reason revealed only when the chosen status requires it**,
and **the create form is in F1**.

| # | Sub-part | Routes | Verdict |
|---|---|---|---|
| 1 | `useVatExemptionReasons` lookup, gated on `TAX_AND_CHARGES` exactly as `useVatClasses` is | `GET /api/vat-exemption-reasons` | **Done** — `api/lookups.ts`; a role without the grant is proven not to request it |
| 2 | Suppliers list — columns, active-only filter, `DataTable` | `GET /api/suppliers` | **Done** — 3 seeded suppliers render in both browsers |
| 3 | Supplier detail — name, VAT number | `PATCH …/name`, `…/vat-number` | **Done** |
| 4 | Contact details — **one editor, two fields**, because the route takes `email` and `phone` together | `PATCH …/contact-details` | **Done** — one request asserted |
| 5 | VAT status **and** exemption reason — decided: one editor, reason revealed on demand | `PATCH …/vat-status` | **Done** — see the rules note below |
| 6 | Deactivate / reactivate, with `Refusal` | `POST …/deactivate`, `…/reactivate` | **Done** — refusal surfaced from the start, not added after |
| 7 | Create form — decided: in F1 | `POST /api/suppliers` | **Done, and proved against the real backend** |
| 8 | Wire `/suppliers`, `/suppliers/new` and `/suppliers/:id` into `routes.tsx` | — | **Done** — the nav node already existed |
| 9 | Tests, including the three standing guards this pass added | — | **Done** — 18 new tests; 182 total |
| 10 | EN + EL strings | — | **Done** — 21 keys each |
| — | `GET /api/suppliers/match-suggestions` | — | **Out of scope.** It serves the never-silently-guess matching flow (brief rule 7), which belongs to the import/reconciliation work, not to a master-data screen |
| — | `GET /api/suppliers/by-vat-number/{vatNumber}` | — | **Out of scope.** A lookup for the AADE/VIES adapter (step 28), not a screen |

**What the API decides for us, so it is not re-litigated per field.** `VatStatus` carries two flags
and they are not symmetrical: `INTRA_EU_B2B` **requires a VAT number**; `EXEMPT` **requires an
exemption reason**; `DOMESTIC`, `NON_EU_EXPORT` and `OTHER` require neither.
`SupplierServiceImpl` enforces both and refuses with a message, so the screen's job is to avoid
offering a combination that will be refused — not to re-implement the rule.

⚠️ **Those two flags are not on the wire, so `vat-status-rules.ts` mirrors them and can drift.**
`VatStatus` is serialised as a bare string enum: a client is told the five values and nothing about
what each requires. `vat-status-rules.test.ts` pins what *can* be pinned — every value is accounted
for, so a **sixth status added on the backend fails a test** here rather than silently defaulting to
"requires nothing". What it cannot catch is a change to what an **existing** value requires; that
needs the flags in the spec, and is worth folding into backend item 2, which is already about the
spec not saying what it means.

**A note for F2, which is Customers and has the same VAT fields.** `PATCH /api/customers/{id}/vat-status`
exists with the same shape, so `vat-status-rules.ts` and the coupled editor should be **moved up out
of `pages/suppliers/` rather than copied** the moment F2 needs them. Copying is how the two screens
end up disagreeing about what `EXEMPT` requires.

**One thing checked rather than assumed, given what F0's follow-up just cost:** `NewSupplier` is a
record of `String`s, an enum and a `Long` — **no primitives**, so it does not carry the
absent-field-is-null trap that broke product creation. Its compact constructor requires `name` and
`vatStatus`.

**And then checked again against the server, because that is now the rule.** Reading the record was
not treated as evidence. Creating a supplier was driven in **Chrome and Firefox, with nothing
intercepted**, twice and in this order:

1. **Against a name the domain already holds** — `422 "A supplier named 'TEST-SUPPLIER-01 Roaster'
   already exists."`, rendered on screen. A `422` is only reachable if the body **parsed**, so this
   proves the whole path and writes nothing. Had `NewSupplier` carried the primitive trap, this
   would have been the `400` that products gave.
2. **Once for real** — body `{"name":"TEST-PROBE-SUPPLIER-01","vatStatus":"DOMESTIC"}`, `201`, and
   the screen landed on the new supplier.

Both probe rows were then deleted. **Residue, stated rather than implied:** `supplier_id_seq` is at
5 where the fixture uses 1–3, and the audit log holds the create and delete entries, which is
append-only by trigger and correctly cannot be removed. The fixture is back to 3 suppliers.

**A defect in the verification itself was caught by the same rule.** The first version of the probe
checked "the body parsed" as `status !== 400` — which **passed when no request had been made at
all**, because submit was disabled and `status` was `undefined`. That is the identical failure the
`CLAUDE.md` rule was written about, one turn later, in the tool written to enforce it. The check now
asserts a request was made before asserting anything about its answer.

Frontend work is tracked step by step in **`docs/novocore-roadmap.md`**, which is the file
to read for what comes next. **F0 is done** (see its section above): the development database now
holds a real trading quarter — 8 products, 5 customers, 3 suppliers, 48 balanced journal entries —
so **F1 onwards is the first frontend work in this project being built against data that exists.**
Every screen before it was built against empty tables.

**A bugfix pass on Products came between F0 and F1 (2026-07-31, `3458ee6`). It is not a roadmap
step and F1 has not started.** It is written up under *Products — the wedge* below. Read the short
version: a render loop in `DataTable` made the tab unresponsive the instant any list filter changed,
which is why three unrelated-looking interactions all appeared dead at once. It had been latent
since `DataTable` was written and is unrelated to F0 — an empty response wedges identically — but
F0 is what gave anyone a reason to use a filter.

**Foundations, Products, a brand pass and an icon fix have landed** (`94e17cd`, `56e3726`,
`28c4119`, `92976fc`, `507864f`). The paragraphs below were written before any of that and are kept
because the reasoning still holds — with one correction: **the frontend now has a login screen, and
a human has used a browser.** What has *not* happened is a human driving the ledger screens end to
end; every frontend test still runs because a test asked it to.

**Step 15 earned its place ahead of step 16 nine times over.** Every one of the nine
defects it found is one step 16 would otherwise have hit through a second layer, with two candidate
causes for every symptom. The three found last — a `500` on sixteen routes given a form with a field
missing, a reversal document reporting `0.00`, and a whole slice answering `400` where the rest of the
surface answers `404` — are exactly the things a form, a report and an error toast collide with on day
one.

**What step 16 can now rely on, which it could not before:** every route has been driven or excused in
writing; every refusal is RFC 7807 with a status a client can branch on; every role's access to every
route is asserted rather than assumed; no route answers a bare "Bad request." or a 500 to a bad form;
and `?from=`/`?to=` are proven inclusive at both ends.

**One thing it must still largely not rely on: almost nothing has been driven by hand.** Every
route that runs, runs because a test asked it to. The login screen and the Products screen now
exist; the rest of the surface has still never been rendered.

**PLB-1 (2FA) is the only pre-launch blocker outstanding** — deferred, unchanged, and still blocking
any external or remote access.

**PLB-1 (2FA) stays deferred**, because its trigger condition has not arrived: it must be resolved
before *any* external or remote access, and there is none. That is a condition, not a date — see
the pre-launch blockers section above.

⚠️ **Two things to carry into whatever comes next.**

- Q45 survived twelve build steps because every example test in the suite used whole-cent costs. The
  generated tests found it in their first run. **Step 15 is the same lesson from a different
  direction**: the `Rate` defect, the `basis` defect and the AR discrepancy all survived because no
  test had ever driven those routes over HTTP, or driven that combination of documents at all. The
  argument is not "write more property tests" specifically — it is that **a checker only covers what
  it is actually pointed at**, and both steps found their defects by pointing an existing kind of
  check somewhere new.
- **A decision recorded with its reasoning can be reversed on evidence; one recorded as a bare rule
  cannot.** Two deliberate decisions were overturned this step — the credit note always crediting AR,
  and Q21's product redaction — and in both cases the *stated reason* was what made it possible to
  tell whether the reversal was sound. Both old reasons are kept in the javadoc rather than deleted.

### Credential housekeeping — done, nothing outstanding

Both items raised at the end of step 11 are closed. The `kostas` password was rotated through
`UserService.changePassword` against the live database and verified by logging in over HTTPS (the
old password now returns 401), and all three consumed bootstrap variables were removed from
`docker/.env`, after which the app was recreated and starts clean. See "To be aware of immediately"
above for the current state.

### ~~Step 12 needs Q24~~ — answered and built

Q24 was answered 2026-07-29 (Google Drive API, OAuth, encrypted at rest) and step 12 is built on it.
What remains is operational, not a decision: the two destinations' folder ids and OAuth credentials.

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

#### ✅ Q44's access-path half — decided 2026-07-29, **built in step 14c**

**`EmailSender.downloadAttachment` must re-check the caller's permission against the underlying core
record before returning bytes for a *referenced* (stored) attachment**, using the authorization
already in place — `RoleView.requireView(Section…)` for the section and `RoleView.canSee(ProtectedField)`
for field-level restrictions, the same primitives `ProductView.redactedFor(RoleView)` composes.

**The principle:** *an email having been sent to someone does not change who is allowed to see the
source document afterward.* The outbox must not become a second, weaker access path to restricted
data. Without this check, a role that cannot open a purchase invoice could read that invoice's PDF out
of the email that sent it — the permission model intact on one route and bypassed on the other.

This is a **direct consequence of V21** and did not exist before it. While the outbox held its own copy
of the bytes, the mail's attachment was arguably the mail's own business; now it is a pointer into
`attachment`, which belongs to a core record with its own visibility rules. Referencing removed the
duplicated storage and, with it, the excuse for a duplicated access rule.

**Scope of the check, so it is not over- or under-applied when built:**

- **Referenced attachments only.** An inline generated PDF has no core record behind it, therefore no
  record-level permission to consult; it is governed by whatever `Section` the outbox itself gets.
- **The check is on the referenced document's `entity_type` / `entity_id`**, which
  `email_outbox_attachment.attachment_id` reaches via `AttachmentService.findMetadata`.
- **A deleted reference needs no check** — there is nothing left to authorise, and the entry already
  reports itself unavailable.
- **`attachmentsOf` returns metadata only** (filename, size, availability), no bytes. Whether a
  *filename* is itself restricted is a `Section` question, not this one.

**Nothing is built yet, and nothing is exposed**: there is still no HTTP route to the outbox at all, so
this is not a live vulnerability today. It is recorded here, in `EmailSender.downloadAttachment`'s
javadoc, and in ADR 0012 precisely so it is a requirement being implemented rather than a gap being
discovered.

### Standing note

The REST surface is deliberately still one endpoint — **the ledger, inventory, purchasing, sales,
landed costs and now the email outbox all have no HTTP route at all.** Building out the rest of the API
needs its own scoping conversation, not incremental drift. When it happens, these lower-layer methods
must **not** be what a controller exposes: `JournalService.post` (use `postManualEntry`),
`InventoryService.receive` / `unreceive` (use `GoodsReceiptService`), `InventoryService.consume` /
`returnConsumed` (use `SalesInvoiceService` and `CreditNoteService`), **`InventoryService.applyLandedCost`
/ `removeLandedCost` (use `FreightAllocationService`)**, and `ProductService`'s unredacted reads (use the
`...For(viewer)` variants). **PLB-1 (2FA) must be closed before any remote access is enabled** —
including Remote/Order Staff logging in from outside the local network, which is that role's entire
purpose.

**Step 11 adds one to that list, in the other direction.** Any feature that needs to send something
calls `EmailSender.send` and composes an `EmailMessage`. It **cannot** set a From or a Reply-To,
cannot configure SMTP, and cannot construct a mail session — an ArchUnit rule confines
`jakarta.mail` and `org.springframework.mail` to `..core.email..`. If `EmailSender` cannot express
what a module needs, **add to that interface rather than around it**, exactly as rule 3 says for
adapters.
