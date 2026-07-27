# NovoCore — Product Brief (Revision 4)

*Internal system of record for Novotrade S.A. (Java Jives). Supersedes revision 3. This revision consolidates a full domain-model design pass — entities, journal mechanics, payment automation, and several new modules. Items marked "draft" or "open" are not yet finalized.*

---

## 1. What NovoCore actually is

Not simply "a Manager.io replacement." The real driver is repetitive manual work, operational gaps, and disconnected systems. The goal is to save time, unify data into one database, generate real reports, and automate time-consuming tasks. Manager.io currently just controls the basics of the business — **replacing it is NovoCore's first real workload, not its purpose.**

**No regulatory/compliance filing responsibility sits with NovoCore.** The external accountant continues handling official filings. Prosvasis Go remains the invoicing system of record until NovoCore takes that over directly (Phase 11).

**Long-term goal: fully replace Prosvasis Go** — considered limited software and a primary motivation for the project. Go's current roles: stock/cost data, purchase order documents, invoice issuing + myDATA transmission + POS terminal triggering. The Go adapter is transitional/bridging, not permanent infrastructure.

---

## 2. Architecture

**Core principle: NovoCore owns its own data model,** never shaped by an external system's schema.

**Field boundary rule:** a field belongs in the core only if the core's ledger, reports, or modules genuinely need it. Purely presentational data stays in its adapter. External system reference IDs (Go ID, Woo ID) never live on core entities — each adapter keeps its own external-ID-to-core-ID mapping table.

**Pattern: ports and adapters.** Adapters translate external systems into the core's model; a black-box swap only touches its adapter. Internally-driven functionality is built as **modules**.

**Governance rules (enforced, not just documented):**
- Adapters/modules call the core only through defined service interfaces — never direct DB access or core-internal imports. Stated in `CLAUDE.md`, enforced by an automated architectural check (e.g. ArchUnit) that fails the build on violation.
- Contract tests per adapter; fail-loud handling — never silently guess or drop data.
- **Performance rule:** core operations complete instantly, never synchronously waiting on an external adapter call. Outbound adapter calls run asynchronously.
- **API failure handling:** verifiable failures get periodic reconciliation jobs; silent/unrecoverable gaps are mitigated by pull-based reconciliation alongside webhooks (never webhook-only), plus logging every call's success/failure and alerting on stale sync timestamps.
- **NEW — code-quality governance beyond structural boundaries:** the architecture protects against cross-component coupling, but not intra-component "patch accumulation" from repeated bug fixes over time. Mitigated by: automated tests for the core (especially financial-correctness logic) built alongside Phase 1; a standing `CLAUDE.md` instruction to prefer root-cause fixes/refactoring over minimal patches when a bug reveals a deeper design issue; periodic deliberate cleanup passes, same spirit as the periodic boundary audits.

---

## 3. Technology stack

Java + Spring Boot, PostgreSQL, self-hosted (Dockerized, HTTPS reverse proxy from day one), web-based multi-user frontend, automated backups to two Google Drive accounts (restore untested). Ruled out: Manager.io as continued ledger, SQLite, cloud hosting for v1, Python/PHP, bank payment initiation, cloning a vendor's feature set.

**Design tooling:** Claude Design for mocking up screens and templates before Claude Code implements them.

---

## 4. Chart of accounts

Simplified, business-driven accounts with optional ΕΛΠ (Ν. 4308/2014) code mapping. **Control account / sub-ledger pattern:** Accounts Receivable, Accounts Payable, Inventory, and Fixed Assets are each a single control account; Customers, Suppliers, Products, and Assets are sub-ledger entities.

**Partner Clearing Accounts:** Skroutz, ACS Courier, POS provider (epay/ePOS) — each reconciled against multiple transaction types (invoices, receipts, payments, debit notes, service/commission invoices — fully enumerated per partner, see §8).

**NEW — Freight / Landed Cost — Unallocated:** an Asset-side account (not Expense/COGS) replacing the old standalone DDP-under-COGS line. Freight/duty costs debit this account, then get allocated proportionally by value into the relevant lots' unit cost, crediting the account back to zero. Behaves like an internal clearing account — should always net to zero once fully allocated; a residual balance is a real discrepancy, not something to leave sitting.

**Dropped:** "Suspense" and "Inter Account Transfers." Suspense replaced by "Unclassified — Needs Review." A transfer between own bank accounts is two Asset-account entries, never Equity/P&L.

**Sales income split by channel.**

---

## 5. Core entities

### Account
Code, name, type, normal balance side, parent category, ΕΛΠ mapping, active/inactive. **Kind:** Standard / Bank-Cash / Partner Clearing / **Control** (with a configurable linked sub-ledger entity type — Customer for AR, Supplier for AP, Product/Lot for Inventory, Asset for Fixed Assets).

### Product *(draft)*
Title, Photo (core — inline on invoices, staff browsing), Category (main/sub, including **Spare Part**), Brand, Regular Price incl. VAT (default/list price, distinct from actual per-transaction price), VAT Class, SKU, EAN, Stock (computed from lots, never stored), Supplier's SKU, Type/Status (Sellable/Test/On-Demand-Unlisted), Pcs/Carton, Bundle/Composite flag, last purchase price, Unit of measure, Item type (Product vs Service), Active/inactive. Go/Woo IDs excluded — live in adapter mapping tables.

**Sellability** is never category-based — governed uniformly by (a) stock at a sellable location and (b) active Woo listing status.

### Inventory Lot / Unit
Every purchase creates a lot: product, quantity, unit cost (includes allocated landed costs), source invoice, acquisition date, optional roast date, current **Location** (Inventory / Service / Damaged Goods — moves over time). Serialized products (e.g. coffee machines) get individual units within a lot — serial number, status, customer/invoice link once sold.

COGS and non-serialized adjustments always use **FIFO**. **Exception:** serialized-item count corrections/write-offs refer to the specific unit and its real actual cost directly — no FIFO logic, since it isn't pooled stock.

### Supplier *(draft, new formal entity)*
Code, Legal name, Alias, VAT, Phone, Email(s), VAT status (normal/exempt), Bank accounts, Active/inactive, Address, Contact person(s). Mixed inventory/expense/asset suppliers need no special modeling.

### Customer & identity model
Fields (draft): Name, Code, VAT, Address, Contact, Active/inactive.

**Identity resolved:** NovoCore's own internal ID is the storage key; adapters keep their own mapping tables. VAT is authoritative (auto-links); phone/email are suggestive only (flagged for human confirmation, never auto-merged).

**Per-channel matching, finalized:** Walk-in/phone — business via VAT, retail via one shared generic "Πελάτης Λιανικής" record or phone (suggestive). Website — business via VAT, retail via email. Skroutz — VAT checked per-order (not per-account, so mixed personal/business accounts split naturally); retail uses the Skroutz customer ID 1:1, no name-mismatch logic built (doesn't affect financial correctness).

**Universal rule:** any Customer creation, manual or automatic, checks VAT/phone/email against existing records and prompts on a possible match.

**Merge mechanics CONFIRMED:** "alias forward, never rewrite history" — old journal lines keep their original reference; the merge marks the old ID as aliased; reports become alias-aware.

### Asset
Name, Code, Acquisition date/cost, Depreciation method (straight-line only) and rate, Accumulated depreciation (calculated), Status. Must exist at the moment a Fixed Assets Purchase Invoice posts.

### Bundle/Composite products
A Bundle is a Product with its own SKU, no stock of its own (computed from components), and a list of components (SKU + quantity + automatic proportional price/cost allocation). One core-level rule decomposes a bundle sale into component lines for inventory/COGS/invoicing, replacing three separate ad hoc implementations across Woo/Skroutz/Go today. Revenue reporting shows both bundle-level and decomposed component-level lines (linked, not duplicated).

---

## 6. Journal entries & core mechanics

**Two-layer design:** typed transactions (Purchase Invoice, Sales Invoice, Receipt, Payment, Bank Transfer, Manual Journal Entry) generate raw balanced debit/credit entries. Debits = credits, enforced structurally.

**Rounding:** always independently computed and compared against the source document; residuals below €0.03 (configurable) auto-post to Rounding; larger differences flagged for review.

**No formal period locking.** Reports generated dynamically. **Depreciation:** straight-line only. **Document attachments** on any core record.

**Sub-ledger linkage:** every journal line has an optional sub-ledger reference (entity type + ID), required on Control-kind account lines. Inventory/COGS lines reference specific lots — one line per lot consumed.

**Open item matching:** each invoice has a computed "open amount." A Receipt/Payment carries one or more allocations — covers simple payments, installments, bulk remittances (unmatched lines flagged for Clearing Checks), overpayment (unallocated customer credit), refunds (credit note = negative-open-amount document).

**NEW — Goods Receipt (Purchase Invoice confirmation):** a physical delivery-verification step, distinct from simply posting the invoice. What's scanned in on arrival is compared against the invoice's expected lines; discrepancies (short-shipped, wrong item, extra) are flagged rather than silently trusted. **Partial delivery** (common on imports, couriers delivering across multiple days) is handled by the same open-item pattern applied to quantities: each line has a computed "open receiving amount," decremented by multiple Goods Receipt events until it reaches zero or is manually closed. A wrong product delivered needs no special build — it's a supplier-side correction (δελτίο αποστολής), already surfaced as a discrepancy and resolved via the normal credit note flow.

**NEW — AADE-first purchase invoice import (default rule):** automatically import invoices from myDATA, confirm against Goods Receipt. An invoice legally expected but missing from myDATA is itself flagged as a supplier compliance issue. Manual entry is the fallback only when genuinely absent from myDATA. Caveats: myDATA may only carry invoice-level totals rather than full line detail depending on the supplier's transmission classification (attach the actual PDF in that case); myDATA timing may lag physical delivery.

**NEW — Invoice line categorization:** four categories — Inventory/Product, Fixed Asset, Expense, and **Prepaid/Deferred expense** (spread across periods). Freight/landed cost is a special case allocating into inventory lines (proportional by value), not a standalone category. **Mechanism (standard practice):** defaults suggested at Product level (a catalog Product suggests Inventory but is always reclassifiable, e.g. to Asset for a showroom unit) and Supplier level (for lines with no Product reference); no default → lands in "Unclassified — Needs Review" rather than blocking entry. Same suggest-never-auto-commit principle used throughout.

**NEW — Payment method settlement automation:**
- Cash → Cash account directly, invoice born fully settled. **Hard-blocked above €500** (Greek legal cash limit, actively enforced under Ν. 5301/2026, penalties up to double the cash amount).
- POS/ePOS/Skroutz/ACS → respective Partner Clearing account, open until the partner's real bank remittance clears it.
- Bank account deposit → the specific bank account. **Resolved:** stays open until the Bank Aggregator adapter confirms the matching incoming transaction (not marked paid on entry) — chosen by default to established best practice.
- Credit → stays open against AR until a later Payment.
- Real-time balances via **frequent** (not nightly-batch) Bank Aggregator polling, feeding the existing open item matching mechanism. POS/ePOS auto-matches on amount alone; Skroutz/ACS need the partner's own remittance report (API/file import) to know which orders were included. Commission arrives as its own Service Invoice, posting against the clearing account — monitored by Clearing Checks.

---

## 7. Permissions / roles (Phase 1, not deferred)

Two-layer model: section/module visibility plus field-level restriction within visible sections. Owner/Admin has full access. "Remote/Order Staff" role defined for home-based workers (Sales Order Fulfillment, Customers, Products view-only, Back-in-Stock — everything else invisible). System supports multiple custom roles from the start.

---

## 8. Adapters

| Adapter | Purpose | Notes |
|---|---|---|
| **Prosvasis Go** | Stock/cost, PO documents, invoice issuing + myDATA + POS triggering (today) | Transitional |
| **WooCommerce** | Store adapter | Absorbs fragile plugin-based order/stock/voucher sync |
| **ACS Courier** | Clearing (Sales invoices, Payments, Receipts, Service invoices, Debit notes for damaged goods) + operational voucher/label generation | Two roles |
| **Skroutz** | Clearing (Sales invoices, Receipts, Purchase invoices, Payments, Debit notes, Service invoices) + receiving/printing Skroutz's own voucher | |
| **POS provider (ePay, Piraeus)** | Clearing (Sales invoices, Receipts at bank, Payments, Service invoices with commission analysis) | Possible switch to NBG |
| **POS terminal** *(deferred)* | Triggers card payment on the physical terminal | Only needed at Phase 11; build after bank/provider decision settles; touches a separate AADE cash-register interconnection mandate to confirm with the accountant |
| **Bank aggregator** | Read-only balances via PSD2 aggregator | No payment initiation; selection + coverage still open |
| **File import** | Excel/CSV | Kept — clearing reconciliation files, Manager migration |
| **AADE myDATA** | Cross-check + structured invoice source | See §6 |
| **AADE Provider (Πάροχος)** | Alternative myDATA transmission | Confirm with accountant |
| **AADE/VIES lookup** | Auto-fill customer/supplier data from VAT number | |

**Dropped:** invoice OCR — ~99% of invoices retrievable via myDATA.

---

## 9. Modules

| Module | Purpose |
|---|---|
| **Purchase Orders** | Supplier PO automation, built inside the core, pushes to Go |
| **Sales Order Fulfillment** | Pending/completed/cancelled order screen; status list: Processing, On Hold, Completed, Cancelled, Pending Payment, Failed, Draft, Refunded. Channel-dependent voucher handling (none/ACS-generated/Skroutz-received). Reuses QZ Tray for silent printing (required — browsers can't do silent printer access) |
| **Product Creator** | Create Woo products from a supplier link **or** a scanned barcode (NEW — barcode lookup first, falling back to link, falling back to manual); Claude-generated content, manual confirmation always required. **NEW — confirmed directional decision:** once built, NovoCore becomes the point of product creation, syncing outward to Go/Woo, not the reverse |
| **Price Tag Printing** | Existing standalone tool, folded in |
| **AI Analysis** | Read-only Q&A via Claude API, deterministic queries only, sequenced last |
| **Reports** | On-demand reporting |
| **Clearing Checks** | Reconciles invoices against each partner's own records; built on open item matching |
| **Roast Date Report** | Sorts coffee stock by roast date |
| **Back-in-Stock Reminders** | Manual call queue |
| **Service/Technician Management** | Repair department, ties to serial numbers |
| **Accountant Monthly Package** | Generates the accountant's monthly document set — the project's original motivating example |
| **Employee Digital Work-Card (Ergani)** *(tentative)* | Real legal obligation (Law 4808/2021) — confirm with accountant |
| **NEW — Barcode scanning** | Not a standalone module — an input mechanism across Purchase Invoice/Goods Receipt verification, Sales Order picking, and in-store sales, using existing EAN and serial number data |

---

## 10. Roadmap (proposed)

0. Setup — vault, git, `CLAUDE.md`, ArchUnit check **(DONE)**. 1. Core (ledger, chart of accounts, journal entries, Goods Receipt, users/auth/permissions, backups, tests). 2a. Populate with dummy data to validate functionality/backups/restores. 3. WooCommerce + Prosvasis Go adapters. 2b. Real migration (now expected to carry real sub-ledger detail sourced from Go, exportable to Excel) + parallel-running. 4. Purchase Orders + Sales Order Fulfillment. 5. File import adapter. 6. Bank aggregator adapter. 7. AADE myDATA + AADE/VIES lookup. 8. Reports + Clearing Checks. 9. Product Creator, Roast Date Report, Back-in-Stock, Service/Technician, Price Tag Printing, Accountant Monthly Package. 10. AI Analysis. 11. Core-owned invoice issuing + AADE Provider + POS terminal adapters — retires Go.

---

## 11. Tooling & setup status

Obsidian vault (inside Google Drive sync) + git, pushed to **https://github.com/Novogrowth/NovoCore.git** — setup complete. Claude Code drives file edits, commits, and application code; Claude (chat/Project) for design discussion; Claude Design for visual mockups.

---

## 12. Naming

Product: **NovoCore**. Company name (if commercialized) undecided.

---

## 13. Explicitly open / not yet decided

- Final Product, Customer, Supplier, Asset field lists (drafts above)
- Bank aggregator selection + Greek coverage; POS terminal provider (epay vs NBG)
- Final build order
- Invoice/document template design mechanism (deferred to Purchase Orders/Sales Order Fulfillment)
- Freight/landed cost allocation confirmed as proportional-by-value (a known approximation vs. weight-based — acceptable given no weight data tracked)
- Backup restore test; physical hosting machine; company name
- Needs accountant: AADE Πάροχος scope, Ergani applicability, AADE cash-register/POS interconnection mandate

---

*This brief reflects an extended scoping process. Detailed field-level and API-level design for each adapter/module is deferred to the start of that piece's own build phase.*
