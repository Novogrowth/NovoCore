Read CLAUDE.md and /docs/novocore-product-brief-v4.md fully before doing anything else.

We are starting Phase 1 only: the core. Do not build any adapters or modules yet — no Go, no WooCommerce, no Purchase Orders, no Reports, nothing beyond what's listed below. Ask me before scaffolding anything not explicitly in this list.

Phase 1 scope:
1. Project skeleton — Spring Boot (Java), PostgreSQL, Docker Compose for local dev, structured to reflect the ports-and-adapters architecture from day one (a clearly separated `core` package/module, with empty `adapters` and `modules` packages reserved for later phases).
2. Chart of accounts — the Account entity and its "kind" behavior (Standard / Bank-Cash / Partner Clearing / Control with configurable sub-ledger entity type), per the brief section 5.
3. Core entities — Product, Customer, Supplier, Asset, Inventory Lot/Unit, per the brief section 5. Use the field lists as given, but flag anything that seems incomplete or inconsistent rather than guessing.
4. Journal entries — the two-layer typed-transaction-over-raw-entry design, sub-ledger linkage, open item matching, rounding logic, and Goods Receipt, per the brief section 6. This is the most correctness-critical part of the whole system — go slowly here, and write tests as you go, not after.
5. Users, auth, and the permissions model (Owner/Admin + Remote/Order Staff role, section/field-level access) per the brief section 7.
6. Automated backups (to be pointed at two Google Drive destinations — ask me for the actual paths/credentials when you reach this step, don't invent placeholder ones and move on).
7. A basic but real automated test suite for everything above, especially money handling, FIFO lot consumption, and the debits=credits invariant.

When you hit a design decision that isn't answered by CLAUDE.md or the brief, stop and ask rather than making a reasonable-sounding assumption — several parts of the brief are explicitly marked as drafts for exactly this reason.

Start by proposing the project structure and a build order for the items above, and wait for my confirmation before writing code.
