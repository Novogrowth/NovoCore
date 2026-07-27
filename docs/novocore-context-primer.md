# NovoCore — Context Primer for Fresh Chat Sessions

*Paste or attach this at the start of a new chat when you need design clarifications while working with Claude Code, so the conversation doesn't need to be re-explained from scratch. Full exhaustive detail lives in `novocore-product-brief-v4.md` in the repo — this is the condensed version for quick reference and ongoing decisions.*

---

## What NovoCore is

An internally-owned system for Novotrade S.A. (Java Jives) replacing Manager.io, and eventually Prosvasis Go. **Not just "replace Manager"** — the real goal is unifying data across disconnected systems, automating repetitive tasks, and generating real reports. Manager replacement is the first workload, not the purpose. No regulatory/compliance filing responsibility sits with NovoCore — the external accountant handles that; Go remains the invoicing system of record until Phase 11.

## Current build status (as of this primer — 2026-07-27)

**Detailed, always-current status lives in `docs/PROGRESS.md`.** Read that first; this section is
the summary.

- **Setup complete:** git repo at `https://github.com/Novogrowth/NovoCore.git`, working locally at `C:\Novocore` (moved off Google Drive — Drive's virtual filesystem was silently corrupting `node_modules` installs; git/GitHub is the only cross-machine sync mechanism now, never Drive).
- **Frontend foundation built and verified:** Vite + React 19 + TypeScript, Tailwind v4 (CSS-first config), shadcn/ui (style `base-nova`, 13 starter components installed), React Router + a structural app shell (sidebar + main + Outlet, no real pages yet). Two commits pushed to `origin/main`. **Untouched since; the backend has no REST endpoints yet for it to call.**
- **Backend Phase 1: steps 0–2 done, step 3 (chart of accounts) not started.**
  - Step 1 — Maven multi-module skeleton (`core-api` / `core` / `adapters` / `modules` / `app` / `architecture-tests`), ArchUnit guardrails, Docker Compose with Caddy HTTPS, GitHub Actions CI.
  - Step 2 — `Money` / `Quantity` / `SubLedgerRef`, schema conventions, migrations V1–V3, `SettingsService`, `AuditLogService`, `AttachmentService`, audit columns.
  - **99 tests passing, `mvn verify` exit 0.** Compose stack verified healthy over HTTPS.
- **⚠️ Nothing is pushed.** Both backend commits (`22bb361`, `cb93fc8`) are on local branch `phase-1/core-skeleton`; `origin` has only `main` at `f96b826`. Given git is the sole cross-machine mechanism, this work exists on one machine only.
- **⚠️ `docker/.env` is gitignored and machine-local** (holds a generated DB password). A fresh clone must copy `.env.example` and set `NOVOCORE_DB_PASSWORD` or nothing starts — there is deliberately no fallback.
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

**Chart of accounts — now fully specified from the real Manager.io export.** The complete
account list, kinds, groups and seed content is in `docs/PROGRESS.md`; the entities and seed
migration are **not yet written**. Resolved shape:

- **Two levels only:** an `AccountGroup` entity (name + `displayOrder`) with accounts under it. Not a self-referencing Account tree. Groups are an entity rather than flat text because ordering is **manual/drag-and-drop**, which needs somewhere to store a group's position. Alphabetical ordering applies only to sub-ledgers (customers, suppliers); inventory sorts by SKU.
- **Normal balance side is derived from account type, never stored.** Six types, because `CONTRA_ASSET` is genuinely needed: accumulated depreciation is an Asset-type account with a *credit* normal balance, and without it fixed assets report at roughly double their carrying value.
- **Kinds:** Standard / Bank-Cash / Partner Clearing / Control (with a declared sub-ledger type). Control accounts: Accounts Receivable→Customer, Accounts Payable→Supplier, Inventory→Product-Lot, Fixed Assets→Asset, and **GR/IR clearing→Supplier**.
- **Bank-Cash:** Cash, Alpha Bank, Piraeus Bank, NBG. **Partner Clearing:** Skroutz, ACS Courier, POS provider, **plus PayPal and Stripe** — the latter two grouped under Cash & Cash Equivalents but treated as clearing accounts, so processor fees post as expense on receipt.
- **Account codes are left blank** and ΕΛΠ mapping is null for now (comes from the accountant later).
- **`expectedToClear` flag** rather than a fifth kind, for accounts whose residual balance is a real discrepancy: Freight/Landed Cost — Unallocated, GR/IR clearing, Unclassified — Needs Review.
- **Sales is split by channel:** Store, eCommerce, Skroutz.
- **New accounts** not mapping from Manager: the three above plus Rounding, Inventory write-off / shrinkage, and Interest expense in its own **Finance Costs** group (below EBIT, so EBITDA/EBIT stay meaningful).
- **Dropped:** Suspense, Inter Account Transfers (Manager had the latter under Equity — the error the brief corrects), and DDP (superseded by Freight/Landed Cost — Unallocated).
- **Known imperfections accepted:** `Interest received` stays in `Income` above EBITDA, so EBITDA is approximate; no current-portion split on the NBG loan; `VAT payable` is a single account pending Q14.

**Core entities (draft field lists — see brief for full lists):** Account (kind: Standard/Bank-Cash/Partner Clearing/Control), Product (SKU, EAN, Type/Status, Bundle flag, computed Stock — no Go/Woo IDs), Inventory Lot/Unit (cost, roast date, Location: Inventory/Service/Damaged Goods, optional serial number, FIFO consumption except serialized items which use their own real cost), Customer (own internal ID; VAT authoritative, phone/email suggestive-only matching; merge = alias forward, never rewrite history), Supplier (parallel to Customer), Asset (straight-line depreciation only).

**Bundles:** a Product with its own SKU, no stock of its own, automatic proportional cost allocation across components; decomposes into component lines for inventory/COGS/invoicing; revenue reporting shows both bundle-level and component-level (linked).

**Journal entries:** two-layer (typed transactions → raw balanced debit/credit entries). Sub-ledger reference required on Control-kind account lines (one line per lot for inventory). Open item matching: computed "open amount" per invoice, Receipt/Payment carries allocations — covers partial payments, installments, bulk remittances, overpayment (unallocated credit), refunds. Rounding: independently computed and compared to source document, residuals <€0.03 (configurable) auto-post to Rounding account, larger flagged. No period locking — reports are dynamic.

**Goods Receipt (new):** physical delivery verification against a Purchase Invoice, separate from posting it. Partial delivery uses the same open-item pattern applied to quantities. Wrong product = supplier-side correction, no special build needed.

**Payment settlement automation:** Cash→Cash account (hard-blocked >€500, Greek law); POS/Skroutz/ACS→their clearing account (open until real bank remittance); Bank deposit→stays open until Bank Aggregator confirms (chosen: best practice over convenience); Credit→stays open against AR. Real-time balances via frequent (not nightly) Bank Aggregator polling.

**Invoice categorization:** 4 categories (Inventory, Fixed Asset, Expense, Prepaid/Deferred). Freight allocates into inventory lines (proportional by value — known approximation vs. weight-based, accepted). Defaults suggested at Product-level then Supplier-level, else lands in "Unclassified — Needs Review."

**AADE-first invoice import:** default rule — pull from myDATA automatically, confirm against Goods Receipt; missing-from-myDATA is itself a supplier compliance flag.

**Permissions (Phase 1, not deferred):** two-layer (section visibility + field-level restriction). Owner/Admin full access. "Remote/Order Staff" role defined for home-based workers.

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

- **VAT class list (Q4)** — Product has a "VAT Class" and Supplier a "VAT status", but no rates appear anywhere in the brief. Hard blocker for step 5.
- **Product↔Supplier link (Q5)** — Product has "Supplier's SKU" but no supplier reference, which makes the field meaningless. Hard blocker for step 5.
- **VAT posting mechanics (Q14)** — a real design gap, not a clarification. Nothing specifies how input/output VAT posts. Needs a proper conversation. Blocks steps 7–9.
- **Correction/reversal policy (Q13)** — with no period locking, may a posted entry be edited, or is correction reversal-only? Recommendation: immutable once posted. Blocks step 7.
- **Remote Staff restricted fields + auth mechanism (Q21, Q22)** — blocks step 4.
- **Bundle/Composite products (Q11)** — in brief §5's core entities but absent from the agreed Phase 1 scope. In or out?
- **Landed cost after consumption (Q18)** and the related **provisional lot cost vs purchase price variance** question from ADR 0004 — blocks steps 8 and 10.
- **SMTP credentials** (step 11) and **the two Google Drive backup destinations plus mechanism** (step 12, Q24 — Drive API or `rclone`; retention; encryption at rest).

Also still open from the brief: final Product/Customer/Supplier/Asset field lists, bank aggregator selection, POS provider (epay vs NBG), invoice/template design mechanism, freight allocation confirmed proportional-by-value, **backup restore test (nothing exists yet)**, physical hosting machine. Needs accountant: AADE Πάροχος scope, Ergani applicability, AADE cash-register/POS interconnection mandate.

## Naming

Product: **NovoCore**. Company name (if commercialized) undecided.

---

*Business context: Novotrade S.A., trading as Java Jives, a coffee equipment retailer in Greece (javajives.gr, WooCommerce). Owner is not a developer and won't read code directly — relies on Claude Code for implementation and this kind of primer for design continuity. Prefers direct, fact-based feedback over agreement, and follows established best practice as the default tie-breaker when genuinely unsure.*
