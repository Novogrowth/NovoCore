# NovoCore — Context Primer for Fresh Chat Sessions

*Paste or attach this at the start of a new chat when you need design clarifications while working with Claude Code, so the conversation doesn't need to be re-explained from scratch. Full exhaustive detail lives in `novocore-product-brief-v4.md` in the repo — this is the condensed version for quick reference and ongoing decisions.*

---

## What NovoCore is

An internally-owned system for Novotrade S.A. (Java Jives) replacing Manager.io, and eventually Prosvasis Go. **Not just "replace Manager"** — the real goal is unifying data across disconnected systems, automating repetitive tasks, and generating real reports. Manager replacement is the first workload, not the purpose. No regulatory/compliance filing responsibility sits with NovoCore — the external accountant handles that; Go remains the invoicing system of record until Phase 11.

## Current build status (as of this primer — 2026-07-28)

**Detailed, always-current status lives in `docs/PROGRESS.md`.** Read that first; this section is
the summary.

- **Setup complete:** git repo at `https://github.com/Novogrowth/NovoCore.git`, working locally at `C:\Novocore` (moved off Google Drive — Drive's virtual filesystem was silently corrupting `node_modules` installs; git/GitHub is the only cross-machine sync mechanism now, never Drive).
- **Frontend foundation built and verified:** Vite + React 19 + TypeScript, Tailwind v4 (CSS-first config), shadcn/ui (style `base-nova`, 13 starter components installed), React Router + a structural app shell (sidebar + main + Outlet, no real pages yet). Two commits pushed to `origin/main`. **Untouched since; the backend has no REST endpoints yet for it to call.**
- **Backend Phase 1: steps 0–5 done (plus inserted steps 3b and 4b). Step 6 is next, blocked on Q7 and Q25.**
  - Step 1 — Maven multi-module skeleton (`core-api` / `core` / `adapters` / `modules` / `app` / `architecture-tests`), ArchUnit guardrails, Docker Compose with Caddy HTTPS, GitHub Actions CI.
  - Step 2 — `Money` / `Quantity` / `SubLedgerRef`, schema conventions, migrations V1–V3, `SettingsService`, `AuditLogService`, `AttachmentService`, audit columns.
  - Step 3 — `AccountGroup` / `Account`, seven types, four kinds, `AccountSystemKey`, `ChartOfAccountsService`, migration V4 seeding **65 accounts across 13 groups**, plus `SchemaConventionsIT`.
  - Step 3b — `VatClass` (9 real Prosvasis Go classes seeded), `VatClassPrecedence`, `VatExemptionReason` (structure only, unseeded), `ChargeType` (structure only, unseeded), migration V5.
  - Step 4 — users, roles, two-layer permissions (migration V6), Spring Security session auth, first-owner bootstrap, `auditorAware` wired to the real user.
  - Step 4b — the first REST endpoint, `GET /api/chart-of-accounts`, read-only, existing to make the `..core.web..` ArchUnit boundary real.
  - **V7 and V8** — the two items queued at the last close-out: `Delivery income` / `COD fee income` accounts with the two `ChargeType` rows seeded against them (Q27), and **the real 29-row AADE VAT exemption reason seed** from Prosvasis Go.
  - Step 5 — `Product`, `Customer`, `Supplier`, `Asset` (migration V9). Q5, Q8, Q9 and Q12 all answered and built. Two new sections (`SUPPLIERS`, `FIXED_ASSETS`), and `PRODUCTS`/`CUSTOMERS` are no longer reserved.
  - **V10** — the VAT rate bound's blind spot closed. V5's 0–100 CHECK claimed to catch a rate written as a fraction and did not (`0.24` is inside 0–100). Now **exactly 0, or 1–100**, since the 0% class is real. Same defect as step 5's depreciation rate; **any future rate column should be checked for it.**
  - **V11** — `UnitOfMeasure` converted from a Java enum to a runtime-editable table (Q34), because myDATA's unit codes are AADE's data and an enum constant cannot own them.
  - **370 tests passing, `mvn clean verify` exit 0.** Compose stack verified healthy over HTTPS.
- **All work is pushed to `origin/main`.** Convention is **one commit per build step**, and **session close-out now always commits *and* pushes** (`CLAUDE.md`) — three consecutive sessions had previously ended with unpushed commits, and the repo has no other cross-machine sync mechanism.
- **🚫 Pre-launch blocker PLB-1: no 2FA.** Decided deliberately (the app is not internet-facing yet) with an explicit condition — it **must be resolved before any external or remote access**, including Remote/Order Staff logging in from outside the local network, which is that role's whole purpose. See `PROGRESS.md`.
- **⚠️ `docker/.env` is gitignored and machine-local** (holds a generated DB password). A fresh clone must copy `.env.example` and set `NOVOCORE_DB_PASSWORD` or nothing starts — there is deliberately no fallback. A fresh machine also needs JDK 25 and a Docker daemon; Maven is not required, as `backend/mvnw` is committed.
- **`CLAUDE.md`** verified against intent, and now carries a **Session close-out** section: on "close the session", commit, update `docs/PROGRESS.md`, update this primer.
- **Toolchain** (per-user, no admin needed): Temurin JDK 25.0.3+9 and Maven 3.9.16 under `C:\Users\kosta\tools\`; Docker Desktop 29.6.2. A committed Maven Wrapper means `./mvnw` works with only a JDK.
- Minor open items: 5 transitive npm vulnerabilities (via react-router-dom, deliberately unfixed for now), old Google Drive copy at `G:\My Drive\Novocore` pending safe removal (rename first, delete later).

## Resolved technical decisions (ADRs in `docs/decisions/`)

- **0001** Java 25 LTS + Maven. Java 25 forces the **Spring Boot 4.x** line (3.5 does not support it), which brings JUnit 6, Testcontainers 2.x and Flyway 12. Accepted knowingly.
- **0002** ArchUnit used as a plain library, not via `archunit-junit5` — no `archunit-junit6` exists and the JUnit 5 variant binds to JUnit Platform 1.x.
- **0003** Multi-module layout so the adapter boundary is a **compile error**, not just a test failure: adapters will depend on `core-api` only, so core entities and repositories are absent from their classpath.
- **0004** **Goods Receipt creates Inventory Lots**, not Purchase Invoice posting, with a **GR/IR clearing account** absorbing the timing gap in either direction. Chosen because brief §6's myDATA-first import means goods routinely arrive before their invoice exists.
- **0005** **EUR-only behaviour, but currency is modelled from day one.** `Money` always carries a currency and every monetary column has one. No rates, no FX gain/loss.
- **Entity ids are `bigint`** (not UUID) — single self-hosted instance, smaller indexes.
- **Money is exactly 2 decimals**, normalised on construction; **unit costs and quantities are 6**. A more precise money value is rejected, never silently rounded. Unit cost deliberately is not `Money` and gets its own type in step 6.
- **Every monetary column carries a `char(3)` currency companion**, named `<column>_currency`, tied by a biconditional CHECK. Settled by the schema's first monetary column, `product.selling_price` in step 5, and enforced across the whole schema by `SchemaConventionsIT`. A JPA field mapping a `char(3)` needs `@JdbcTypeCode(SqlTypes.CHAR)`.
- **Attachment content is stored in PostgreSQL** (`bytea`), so backup and restore stay one atomic artefact.
- **Correction policy, VAT posting, and reversal semantics are NOT yet decided** — see Q13 and Q14 in `PROGRESS.md`. These block the journal engine.

## Architecture (non-negotiable)

- **The core owns its data model** — never shaped by an external system's schema (Go, WooCommerce, etc.).
- **Ports and adapters**: adapters translate external systems into the core's model; a black-box swap only touches its adapter. Internally-driven functionality is a **module** instead.
- **Field boundary rule**: a field belongs in the core only if the core's ledger/reports/modules genuinely need it. External system reference IDs (Go ID, Woo ID) never live on core entities — each adapter keeps its own mapping table.
- **Governance**: adapters/modules call the core only through defined interfaces (enforced by ArchUnit); contract tests per adapter; fail-loud, never silently guess.
- **Performance**: core operations complete instantly, never synchronously wait on an external adapter call.
- **Never silently resolve ambiguity** — this shows up everywhere: rounding, customer matching, bank matching, invoice categorization. Auto-resolve only what's certain; suggest and require confirmation otherwise.
- **Code-quality governance beyond structural boundaries**: automated tests for core logic, prefer root-cause fixes over patches, periodic cleanup passes — architecture alone doesn't prevent patch-accumulation spaghetti.

## Tech stack

**Backend:** Java + Spring Boot, PostgreSQL, Docker, self-hosted (HTTPS reverse proxy from day one). Ruled out: SQLite, Python/PHP backend, cloud hosting for v1, bank payment initiation, cloning a vendor's feature set.

**Frontend:** Vite + React + TypeScript + Tailwind + shadcn/ui, in `/frontend/`, separate from backend. Default shadcn theme until Claude Design sets the real brand look. Ask (don't guess) when a UI element could reasonably use more than one shadcn component.

**Shared core services:** Email sending (SMTP via Settings, one interface, called by any feature — never per-module SMTP config). Document attachments, same principle.

## Domain model summary

**Chart of accounts — built and committed (step 3).** The full account list, decisions and
per-step obligations are in `docs/PROGRESS.md`. Resolved shape:

- **Two levels only:** an `AccountGroup` entity (name + `displayOrder`) with accounts under it. Not a self-referencing Account tree. Groups are an entity rather than flat text because ordering is **manual/drag-and-drop**, which needs somewhere to store a group's position. Alphabetical ordering applies only to sub-ledgers (customers, suppliers); inventory sorts by SKU.
- **Normal balance side is derived from account type, never stored** — there is no `normal_balance_side` column and a test asserts its absence. **Seven types**, because both contra types are genuinely needed: `CONTRA_ASSET` (accumulated depreciation is Asset-classified with a *credit* normal balance; without it fixed assets report at roughly double carrying value) and `CONTRA_INCOME` (sales returns are Income-classified with a *debit* normal balance; typed as `EXPENSE` they would sit below the revenue line and overstate gross revenue).
- **Kinds:** Standard / Bank-Cash / Partner Clearing / Control (with a declared sub-ledger type). `type` and `kind` are independent dimensions — accumulated depreciation is `CONTRA_ASSET` *and* `CONTROL`. Control accounts: Accounts Receivable→Customer, Accounts Payable→Supplier, Inventory→Product-Lot, Fixed Assets→Asset, and **GR/IR clearing→Supplier**. A biconditional CHECK enforces "Control iff sub-ledger" in the database, not just in Java.
- **Bank-Cash:** Cash, Alpha Bank, Piraeus Bank, NBG. **Partner Clearing:** Skroutz, ACS Courier, POS provider, **plus PayPal and Stripe** — the latter two grouped under Cash & Cash Equivalents but treated as clearing accounts, so processor fees post as expense on receipt.
- **Account codes are left blank** and ΕΛΠ mapping is null for now (comes from the accountant later). Because neither is usable as a handle, **`AccountSystemKey`** gives the eleven accounts NovoCore's own posting rules must locate a stable machine identifier. Keyed accounts can be renamed and reordered but never deactivated, and the key is never settable from application code.
- **No account balance is stored anywhere** — a balance is the sum of its journal lines, computed on read from step 7. So step 3 introduced no monetary columns at all.
- **`expectedToClear` flag** rather than a fifth kind, for accounts whose residual balance is a real discrepancy: Freight/Landed Cost — Unallocated, GR/IR clearing, Unclassified — Needs Review.
- **Sales is split by channel:** **Store & Phone**, eCommerce, Skroutz — phone named explicitly rather than left to convention. **Sales returns are contra-revenue, one account per channel**, so credit notes keep return rate visible per channel instead of netting into revenue.
- **New accounts** not mapping from Manager: the three expected-to-clear ones, the three Sales returns, `Rounding differences`, `Inventory write-off / shrinkage`, and `Interest expense` in its own **Finance Costs** group (below EBIT, so EBITDA/EBIT stay meaningful).
- **Inventory write-off sits in the COGS group**, its own account separate from Cost of goods sold — gross margin reflects the loss, sale-driven COGS stays clean. **One account with a reason code**, not three; that reason field is a **step 6 obligation**. Distinct from the Damaged Goods *Location*, which marks stock unsellable but still an asset: **moving a lot there posts nothing**, so **phase 8 Clearing Checks must surface lots aging in it** — the agreed compensating control.
- **No delete, only deactivate**; with no period locking no account is ever safely finished with. A reorder must name every member exactly once rather than leaving the rest in an order nobody chose.
- **Dropped:** Suspense, Inter Account Transfers (Manager had the latter under Equity — the error the brief corrects), and DDP (superseded by Freight/Landed Cost — Unallocated).
- **Known imperfections accepted:** `Interest received` stays in `Income` above EBITDA, so EBITDA is approximate; no current-portion split on the NBG loan; `VAT payable` is a single account pending Q14; `Amortization` is seeded although nothing can post to it.

**VAT — built (step 3b), real data from Prosvasis Go and AADE.**

- **`VatClass` is a runtime-editable entity, not an enum**, because Greek rates change by statute (the 3% and island-reduced 4% classes exist only because of αρ.31 ν.5057/2023). Fields: code, description, ratePercent, active, nullable self-referencing reducedCounterpart.
- **Nine classes seeded, eight distinct percentages** — `0`/`1030`/`1040`/`1041`/`1060`/`1091`/`1131`/`1170`/`1410`. 4% appears twice: `1040` in its own right, `1041` as the island-reduced counterpart of 6%. **So the code is the identity, never the rate** — there is deliberately no `findByRate`, and a test asserts its absence.
- **Island-reduced mappings seeded as data** (24→17, 13→9, 6→4 as `1041`, 4→3), held on the mainland rate pointing at the reduced one. Enforced one level deep, lower-rated, one-to-one, never self-referencing. **No automatic rate switching by shipping destination** — future scope by explicit decision.
- **Rates are percentages** (`24.000000`, not `0.24`) in `numeric(19,6)`, with a CHECK allowing **exactly 0 or 1–100**. The lower bound is the load-bearing half: a plain 0–100 range does not catch `0.24` written for 24%, which was a real defect until V10. Zero stays valid because the zero-rated class is distinct from an exempt line. **Rates are never editable in place** — a rate change is a new class plus deactivation, because editing would retroactively change what already-issued invoices appear to have charged.
- **Precedence rule, stated as code** in `VatClassPrecedence`: **invoice line beats customer beats product**, returning which level won. **No fallback rate** — it throws rather than assuming 24%, since a silent default produces a plausible invoice at a rate nobody chose and an undercharge is unrecoverable after issue.
- **`VatExemptionReason` is a separate entity, not a 0% rate** — zero-rated charges 0% under a real rate; exempt is outside VAT because a named article of the Κώδικας ΦΠΑ says so, and myDATA reports them differently. **Seeded (V8) with the real 29 rows from Prosvasis Go**, in the recodified article numbering (άρθρο 2 και 3, 5, 17, … 58). Gaps at codes 24 and 28 — absent from *Go's* list, so worth confirming with the accountant before phase 7. `mydataCode` is stored verbatim, and that turned out to be load-bearing: codes 12 and 13 name "Πλοία Ανοικτής Θαλάσσης" in their description and not in their myDATA string, so composing the value would have transmitted those two wrong. **`mydataCode` is nullable**, because the OSS/IOSS reasons (29–31) have no myDATA mapping in Go — NULL means "no mapping exists", and phase 7 must refuse to transmit one rather than compose a substitute.
- **`ChargeType`** — extensible lookup for fees charged *to* the customer as revenue: name, default VAT class, income account, active. The service **refuses a non-`INCOME` account**, which is the point of it existing: wiring a delivery fee to `Transportation costs` to net it off would understate revenue and cost together. **Seeded (V7) with `Delivery` and `COD fee` against dedicated `Delivery income` and `COD fee income` accounts** (Q27) — a residual bucket holding most invoices' fee lines would be the largest income line and useless as a residual. Both default to 24%, and **Q33 settled that a fee's VAT rate is independent of the products on the invoice** — a 13% order still carries 24% delivery, and nothing should later be built to derive a fee's rate from the lines around it. Nothing consumes charge types until step 9.
- **`numeric(19,6)` now covers three things** — quantity, unit cost, and *rate*. The distinction that matters is amount (2dp, because that is a cent) vs multiplier (6dp, must not lose precision before the product is rounded once).
- **Still open:** Q14 (where VAT actually posts) is narrowed but not closed — rates and arithmetic exist, the account structure and per-line-vs-per-document rule do not. Q28 (dispatch purpose) is answered as a recommendation only, nothing built.

**Core entities — Product, Customer, Supplier and Asset built (step 5).**

- **Product:** own SKU (unique) and EAN (unique when present), `GOODS`/`SERVICE`, a unit of measure **reference** (a runtime-editable lookup since V11, carrying `allowsFractionalQuantity` and a nullable AADE myDATA code), a **required** default VAT class (there is no fallback rate anywhere, so a product without one could not be invoiced), an optional selling price, one optional supplier plus that supplier's own code. **No Go/Woo ids, no stock column, no last-purchase-price column** — both derived from lots (step 6). **A zero price is refused**; null means "not priced yet", because zero produces an invoice line worth nothing without anyone choosing to give the goods away.
- **Q5 answered: one product, one supplier**, a plain nullable foreign key, no many-to-many. **A supplier SKU without a supplier is refused** in code and by a CHECK — that meaningless state was the whole question.
- **Customer / Supplier:** name, single email, single phone (Q8), VAT number (unique when present), `VatStatus`, and for Customer a nullable VAT class override feeding the precedence rule. **Customer names are not unique; VAT numbers are** — two retail customers can genuinely share a name.
- **Q9 answered: `VatStatus` is shared by both parties** — `DOMESTIC`, `INTRA_EU_B2B`, `NON_EU_EXPORT`, `EXEMPT`, `OTHER`. **Five, not four:** export and intra-EU B2B are both VAT-free under *different* articles and are reported differently, so folding export into "other" would lose what has to be stated. Intra-EU requires a VAT number, exempt requires a `VatExemptionReason`; both definitional and both database constraints. **No VIES validation** — that adapter is phase 7.
- **Matching is split by certainty** (rule 7): `findByVatNumber` is exact and may be applied automatically; `suggestMatches` on name/email/phone returns candidates a human confirms. A blank VAT number matches nothing. **Merge is not built** — brief §5's alias-forward needs the ledger, and half of it would be a merge that appears to work and loses references.
- **Asset — a register, not a valuation.** Name, optional code, acquisition date, an optional later depreciation-start date, `IN_USE`/`DISPOSED` with a disposal date required exactly when disposed, and **a manually set annual straight-line rate (Q12)**. **No monetary field at all**: both fixed-asset control accounts declare `ASSET` as their sub-ledger, so cost and accumulated depreciation are sums of journal lines. No useful life (it is `100/rate`), no salvage value (Greek tax depreciates to zero), no method field (straight-line only).
- **The rate is nullable, and that is the current state**: the statutory rates per category are pending the accountant. Null means "not yet known"; nothing was guessed and no category table was created. `withoutDepreciationRate()` lists the assets still waiting. The rate is bounded **1–100** — the lower bound is the load-bearing half, because a plain 0–100 range cannot catch `0.1` written for 10%.
- **Step 4's field restrictions now bite.** `ProductView.redactedFor(RoleView)` is the single implementation, tested against the real seeded Remote/Order Staff role. Hiding the supplier also hides the supplier's SKU, since that code identifies the supplier indirectly. ⚠️ `ProductService` has unredacted reads for the core's own costing rules and `...For(viewer)` variants that redact — **a named convention, not an enforced one**; the first Products controller must be reviewed for it.
- **Still from the brief, not yet built:** Inventory Lot/Unit (cost, roast date, Location: Inventory/Service/Damaged Goods, optional serial number, FIFO consumption except serialized items which use their own real cost) — step 6.

**Bundles:** a Product with its own SKU, no stock of its own, automatic proportional cost allocation across components; decomposes into component lines for inventory/COGS/invoicing; revenue reporting shows both bundle-level and component-level (linked).

**Journal entries:** two-layer (typed transactions → raw balanced debit/credit entries). Sub-ledger reference required on Control-kind account lines (one line per lot for inventory). Open item matching: computed "open amount" per invoice, Receipt/Payment carries allocations — covers partial payments, installments, bulk remittances, overpayment (unallocated credit), refunds. Rounding: independently computed and compared to source document, residuals <€0.03 (configurable) auto-post to Rounding account, larger flagged. No period locking — reports are dynamic.

**Goods Receipt (new):** physical delivery verification against a Purchase Invoice, separate from posting it. Partial delivery uses the same open-item pattern applied to quantities. Wrong product = supplier-side correction, no special build needed.

**Payment settlement automation:** Cash→Cash account (hard-blocked >€500, Greek law); POS/Skroutz/ACS→their clearing account (open until real bank remittance); Bank deposit→stays open until Bank Aggregator confirms (chosen: best practice over convenience); Credit→stays open against AR. Real-time balances via frequent (not nightly) Bank Aggregator polling.

**Invoice categorization:** 4 categories (Inventory, Fixed Asset, Expense, Prepaid/Deferred). Freight allocates into inventory lines (proportional by value — known approximation vs. weight-based, accepted). Defaults suggested at Product-level then Supplier-level, else lands in "Unclassified — Needs Review."

**AADE-first invoice import:** default rule — pull from myDATA automatically, confirm against Goods Receipt; missing-from-myDATA is itself a supplier compliance flag.

**Permissions and authentication — built (step 4).**

- **Roles are data, what they grant is code.** `Section` and `ProtectedField` are enums (what exists is determined by what was built); `app_role` / `role_section_grant` / `role_field_restriction` are tables, so creating a role is an operation not a migration — brief §7's "multiple custom roles from the start".
- **Access is default-deny**, so "everything else is invisible" needs no enumeration and stays true as sections are added.
- **Owner and Admin use a `full_access` flag, not stored grants**, so a section added later is visible to them at once. Both are **system roles**: unmodifiable, undeletable, so nobody can strip `USERS_AND_ROLES` from the last role holding it.
- **Remote/Order Staff seeded exactly as answered:** FULL on Sales Order Fulfillment / Customers / Back-in-Stock; VIEW on Products; `PRODUCT_LAST_PURCHASE_PRICE`, `PRODUCT_SUPPLIER`, `PRODUCT_SUPPLIER_SKU` hidden; nothing else. Not a system role, so it stays adjustable. **Field restrictions narrow, never widen.**
- ~~**Step 5 obligation**~~ — **done.** `ProductView.redactedFor(RoleView)` applies all three, tested against the seeded role and, as pure logic, against a last purchase price that cannot exist in real data until step 6.
- **Auth: server-side sessions, HttpOnly cookie** (Q22, approved). `SameSite=Strict`, `Secure`, 8h, new session id on login, CSRF on. Login/logout return 204/401 not redirects, and `/api/**` returns 401 not a redirect — a `fetch()` cannot use a 302 to an HTML login page. No login controller was written; Spring Security's own `/login` and `/logout` are used.
- **Password hashes never leave the core.** `UserService.authenticate(username, rawPassword)` verifies internally; a custom `AuthenticationProvider` in `app` calls it, and the session principal's `getPassword()` returns null. Login failures are indistinguishable (unknown user, wrong password, inactive user, inactive role) with the reason recorded only in the audit log.
- **No seeded account, no default password.** The first Owner comes from `NOVOCORE_BOOTSTRAP_OWNER_USERNAME`/`_PASSWORD` and **the app refuses to start** if the user table is empty and they are unset.
- **Password policy settled (Q29):** 12 characters minimum, no composition rules, per NIST SP 800-63B. **2FA decided as no-for-now with a condition (Q30) — now pre-launch blocker PLB-1, above.** Still genuinely open: single-role-per-user reading (Q31 — cheapest to change now), session timeout (Q32).

**REST surface — one endpoint (step 4b).** `GET /api/chart-of-accounts`, read-only. Built specifically to make the `..core.web..` ArchUnit rule load-bearing; its `allowEmptyShould` allowance is removed and the rule was proven to fail against a probe. Authorisation is an explicit typed `requireView(Section...)` call, not a `@PreAuthorize` string that could be misspelled and fail open. **Broader API buildout is deliberately not started.** Note `CoreTestApplication` excludes `..core.web..` from scanning, because the controller needs a `CurrentUser` only `app` implements — a permissive fallback bean in the core was rejected as the thing that later fails open.

**Barcode scanning:** not a module — an input mechanism across Purchase Invoice/Goods Receipt verification, picking, in-store sales, and a new barcode-first entry point into Product Creator (falls back to supplier link, then manual). Once Product Creator exists, NovoCore becomes the point of product creation, syncing outward to Go/Woo — not the reverse.

## Adapters (see brief §8 for full detail)

Prosvasis Go (transitional), WooCommerce, ACS Courier (clearing + voucher generation), Skroutz (clearing + receiving their voucher), POS provider/ePay, POS terminal (deferred), Bank aggregator, File import (kept — clearing reconciliation + migration), AADE myDATA, AADE Provider/Πάροχος, AADE/VIES lookup. Dropped: invoice OCR.

## Modules (see brief §9 for full detail)

Purchase Orders, Sales Order Fulfillment (status list: Processing/On Hold/Completed/Cancelled/Pending Payment/Failed/Draft/Refunded; QZ Tray for printing), Price Tag Printing, AI Analysis (last, read-only), Product Creator, Reports, Clearing Checks, Roast Date Report, Back-in-Stock Reminders, Service/Technician Management, Accountant Monthly Package, Employee Digital Work-Card/Ergani (tentative, needs accountant).

## Roadmap phase order (proposed)

0. Setup (done) → 1. Core → 2a. Dummy data validation → 3. WooCommerce + Go adapters → 2b. Real migration + parallel-run with Manager → 4. Purchase Orders + Sales Order Fulfillment → 5. File import → 6. Bank aggregator → 7. myDATA + VIES → 8. Reports + Clearing Checks → 9. smaller modules → 10. AI Analysis → 11. Core-owned invoice issuing + AADE Provider + POS terminal (retires Go).

## Explicitly still open

**`docs/PROGRESS.md` carries the full numbered list with which build step each one blocks.** The
ones that will stop work soonest:

- ~~**ChargeType income account (Q27)**~~, ~~**the VatExemptionReason seed**~~, ~~**Q5**~~, ~~**Q6**~~, ~~**Q8**~~, ~~**Q9**~~, ~~**Q12**~~ — **all resolved and built** (V7, V8, V9). See the sections above.
- ~~**Q33**~~ (fee VAT independent of the goods) and ~~**Q34**~~ (unit of measure as a table) — **both answered; Q34 built in V11.**
- **Q7 — how stock is exposed** (per location plus a "sellable" figure?), **Q25 — write-off reason as enum or free text**, and **Q11 — bundles in or out**. These block step 6; the user will supply all three at the start of the next session. Nothing about stock was built in step 5 because of Q7.
- **Needs the accountant, all three NULL and fail-loud until then:** statutory **depreciation rates** per asset category plus the category taxonomy (**do not create real assets with real values until confirmed**), the **AADE exemption codes 24 and 28** plus the OSS/IOSS myDATA codes (Q35/Q36), and the **myDATA unit-of-measure codes** (Q38).
- **Q37** — Customer and Supplier have no address fields (not needed while Go issues invoices, needed by phase 11), no human-facing codes, and only one selling price per product.
- **Dispatch purpose / Σκοπός διακίνησης (Q28)** — recommendation: a **core-owned `GoodsDispatch`** (outbound counterpart to Goods Receipt) with a `DispatchPurpose` lookup, in **roadmap phase 4**; not inside the Sales Order Fulfillment module, since supplier returns, inter-location transfers and repairs are dispatches too. Conditional on two unknowns: whether Go already issues Δελτία Αποστολής (if so this is phase 11), and whether the AADE digital delivery note regime applies (accountant question).
- ~~**VAT class list (Q4)**~~ — **resolved and built**, see the VAT section above.
- **VAT posting mechanics (Q14)** — a real design gap, not a clarification. Nothing specifies how input/output VAT posts. Needs a proper conversation. Blocks steps 7–9.
- **Correction/reversal policy (Q13)** — with no period locking, may a posted entry be edited, or is correction reversal-only? Recommendation: immutable once posted. Blocks step 7.
- ~~**Remote Staff restricted fields + auth mechanism (Q21, Q22)**~~ — **both answered and built**, see the permissions section above. Left over from Q22: **2FA (Q30) and password policy (Q29)** are open, and worth deciding before the system faces the internet rather than after.
- **Bundle/Composite products (Q11)** — in brief §5's core entities but absent from the agreed Phase 1 scope. In or out?
- **Landed cost after consumption (Q18)** and the related **provisional lot cost vs purchase price variance** question from ADR 0004 — blocks steps 8 and 10.
- **Write-off reason field (step 3 obligation, + Q25)** — a step 6 build item, not optional: with one write-off account instead of three, the shrinkage/damage/expiry distinction has nowhere else to live. Q25 is whether the reason is a fixed enum (reportable, which is the point) or free text.
- **Damaged Goods aging check (step 3 obligation)** — phase 8 Clearing Checks must surface lots sitting in the Damaged Goods location, since moving a lot there posts nothing and nothing else forces the eventual write-off.
- **Credit note as a typed transaction (Q26)** — now that returns post to per-channel contra-revenue accounts, confirm a credit note is its own transaction type rather than a negative Sales Invoice. Interacts with Q13 and Q16.
- **SMTP credentials** (step 11) and **the two Google Drive backup destinations plus mechanism** (step 12, Q24 — Drive API or `rclone`; retention; encryption at rest).

Also still open from the brief: bank aggregator selection, POS provider (epay vs NBG), invoice/template design mechanism, freight allocation confirmed proportional-by-value, **backup restore test (nothing exists yet)**, physical hosting machine. Needs accountant: AADE Πάροχος scope, Ergani applicability, AADE cash-register/POS interconnection mandate.

## Naming

Product: **NovoCore**. Company name (if commercialized) undecided.

---

*Business context: Novotrade S.A., trading as Java Jives, a coffee equipment retailer in Greece (javajives.gr, WooCommerce). Owner is not a developer and won't read code directly — relies on Claude Code for implementation and this kind of primer for design continuity. Prefers direct, fact-based feedback over agreement, and follows established best practice as the default tie-breaker when genuinely unsure.*
