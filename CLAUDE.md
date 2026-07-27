# NovoCore — Instructions for Claude Code

NovoCore is Novotrade S.A. (Java Jives)'s internally-owned financial and operational system, replacing Manager.io and eventually Prosvasis Go. Full domain design lives in `/docs/novocore-product-brief-v4.md` in this repo — read it for *what* to build. This file governs *how* — read it every session, it is not optional context.

## Non-negotiable architecture rules

1. **The core owns its data model.** Chart of accounts, journal entries, and core entities (Product, Customer, Supplier, Asset, Account, Inventory Lot) are defined by NovoCore's own needs, never by an external system's schema. Never add a field to a core entity just because Go or WooCommerce happens to have it — see the brief's "field boundary rule."
2. **External system reference IDs never live on core entities.** Each adapter keeps its own external-ID-to-core-ID mapping table (e.g. Go product ID, Woo product ID). The core only ever knows its own SKU/ID.
3. **Adapters and modules call the core ONLY through its defined service interfaces.** Never direct database access from an adapter or module. Never import a core-internal class from outside the core package. This is enforced by an ArchUnit test that must pass on every build — if you're about to write code that violates this, stop and add the missing interface method to the core instead of reaching around it.
4. **Core operations must complete instantly.** Never make a core operation (saving an invoice, a product, a journal entry) synchronously wait on an external adapter call. Outbound adapter calls (pushing to Go, notifying Woo) run asynchronously, in the background.
5. **Money is always `BigDecimal`, never `double` or `float`.** No exceptions, anywhere, ever — including in tests, in DTOs, in reports. This is the single most important rule in this codebase.
6. **Debits must equal credits, structurally.** A journal entry that doesn't balance cannot be saved. This is enforced at the persistence layer, not just validated in a UI form.
7. **Never silently guess or auto-resolve ambiguity.** Rounding differences, customer matches, bank reconciliation matches — all follow the same pattern: auto-resolve only what's genuinely certain (an exact match against a strong identifier), suggest and require one-click confirmation for everything else. See the brief for the specific thresholds and rules per case.
8. **Adapters have contract tests.** Every adapter must have an automated test confirming the external API still returns the shape it expects. If an external API call returns something unexpected, fail loudly and flag it — never silently drop or guess at malformed data.

## Code quality — this is not covered by the rules above

The architecture rules above prevent *cross-component* coupling (an adapter reaching into the core). They do **not** prevent *intra-component* decay from repeated small bug fixes over time. So:

- When fixing a bug, don't default to the smallest possible patch. If the bug reveals a deeper design issue, say so and propose the proper fix — don't quietly work around it.
- Write automated tests for core logic, especially anything touching money, journal entries, or FIFO/lot consumption. Tests are what make later refactoring safe; without them, every future fix will be tempted toward "just patch it."
- If asked to review or clean up a module, actually look for accumulated duplication/special-casing, not just the specific thing you were asked about.

## Stack

Java + Spring Boot, PostgreSQL, Docker, self-hosted with an HTTPS reverse proxy from the start. Web-based multi-user frontend. No SQLite, no Python/PHP backend — these were deliberately ruled out, don't reintroduce them for "quick" tooling either.

## Scope discipline

NovoCore is built in phases (see the brief's roadmap). **Only build what the current phase asks for.** Do not pre-build adapters or modules for later phases "while you're in there" — this project deliberately avoids scope creep. If a task seems to need something from a later phase, say so and ask, rather than building it speculatively.

## When something in the brief is marked "draft" or "open"

Ask before implementing. Several entity field lists and mechanisms in the brief are explicitly marked as not finalized — building against them as if they were final risks real rework.
