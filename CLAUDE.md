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

## Shared core services (not domain-specific, but still core-owned)

- **Email sending** is a shared core service, configured once via Settings (SMTP credentials, sender identity), exposed through a single interface — e.g. `send(to, subject, body, attachments)`. Any module or feature needing to send email (Purchase Order PDFs, Reports, the Accountant Monthly Package, etc.) calls this service. Never configure SMTP or send email directly from within a module — that recreates the scattered-credentials problem this rule exists to prevent.
- **Document attachments** on core records follow the same shared-service principle — one mechanism, not one per module.

## Code quality — this is not covered by the rules above

The architecture rules above prevent *cross-component* coupling (an adapter reaching into the core). They do **not** prevent *intra-component* decay from repeated small bug fixes over time. So:

- When fixing a bug, don't default to the smallest possible patch. If the bug reveals a deeper design issue, say so and propose the proper fix — don't quietly work around it.
- Write automated tests for core logic, especially anything touching money, journal entries, or FIFO/lot consumption. Tests are what make later refactoring safe; without them, every future fix will be tempted toward "just patch it."
- If asked to review or clean up a module, actually look for accumulated duplication/special-casing, not just the specific thing you were asked about.

### Named anti-pattern: proxy self-invocation

**A class must not call its own `@Transactional` method.** Spring applies it with a proxy, and a call from one method of an object to another method of the *same* object never goes through that proxy — so the annotation silently does nothing. Nothing fails, nothing warns, and the code reads exactly like code that works.

This has now bitten this codebase three times: `EmailOutbox` (step 11, caught while designing), `RestoreVerifier` (step 12, caught in review), and `AuditLogServiceImpl` (pre-existing, caught by the rule below — audit entries were joining the caller's transaction and **rolling back with the very operation they recorded**, which is exactly what its `REQUIRES_NEW` existed to prevent).

**Two ArchUnit rules now enforce it** (`SelfInvocationRulesTest`), and they are deliberately narrow so they stay believable:

1. a **non-transactional** method may not call its own class's `@Transactional` method — the proxy is bypassed and there is no transaction at all;
2. **nothing** may self-invoke a method declaring non-default propagation — a self-called `REQUIRES_NEW` silently joins the caller instead of starting its own.

A `@Transactional` method calling another on the same class with default propagation is **allowed**: the inner call joins the outer transaction, which is what the code means. Forbidding it produced 44 findings, nearly all harmless — and a rule that cries wolf is one someone deletes.

**The remedy is always the same:** move the transactional methods into their own bean. `EmailOutbox`/`EmailDispatcher`, `RestoreCheckJournal`/`RestoreVerifier` and `BackupJournal`/`BackupRetentionService` are the worked examples.

**What the rules cannot see, so watch for it in review:** `@Async`, `@Cacheable`, `@PreAuthorize` and `@Retryable` fail identically and are not covered; nor is a call reached through a lambda or method reference captured elsewhere. The general principle is the thing to hold on to — **an annotation that Spring applies with a proxy does nothing when the call originates inside the same object.**

A related trap with the same shape: a method that is *not* transactional returning JPA entities with lazy associations, which then blow up on first access at the caller. Materialise plain data inside the transaction instead — `EmailOutbox.claimDue` and `BackupJournal.artefactToRemove` both state this requirement rather than leaving it to be discovered.

## Stack

**Backend:** Java + Spring Boot, PostgreSQL, Docker, self-hosted with an HTTPS reverse proxy from the start. No SQLite, no Python/PHP backend — these were deliberately ruled out, don't reintroduce them for "quick" tooling either.

**Frontend:** lives in `/frontend/`, a separate directory from the backend. Vite + React + TypeScript + Tailwind CSS + shadcn/ui. Use shadcn's default theme until Claude Design defines the real brand look — don't invent a color palette or visual style yourself. When more than one shadcn component could reasonably fit a given UI element, or there's no written component-mapping guidance covering it, stop and ask which one to use rather than picking one yourself; for anything a component-mapping reference (once one exists in this repo) already covers clearly, use it directly without asking.

## Environment note

This repo lives on local disk on every machine it's developed from — never inside a cloud-sync folder (Google Drive, OneDrive, Dropbox). Cross-machine access happens exclusively via git/GitHub (`https://github.com/Novogrowth/NovoCore.git`). Do not assume or recreate a cloud-sync dependency for this repo; if a task seems to need one, ask first.

## Scope discipline

NovoCore is built in phases (see the brief's roadmap). **Only build what the current phase asks for.** Do not pre-build adapters or modules for later phases "while you're in there" — this project deliberately avoids scope creep. If a task seems to need something from a later phase, say so and ask, rather than building it speculatively.

## When something in the brief is marked "draft" or "open"

Ask before implementing. Several entity field lists and mechanisms in the brief are explicitly marked as not finalized — building against them as if they were final risks real rework.

## Session close-out

When the user says "close the session" (or clearly equivalent phrasing like "let's stop here" or "end session"), perform these five actions **in this order**, regardless of what step or task is in progress:

1. **Update `docs/PROGRESS.md`.** Record: which step(s) were worked on, what's now done and verified, what's still open or blocked (including any question numbers from the product brief), and the concrete next action for the following session. Overwrite stale status, don't just append.
2. **Update `docs/novocore-context-primer.md`.** Reflect any changes to build status, resolved decisions, or open items so the primer stays accurate for a fresh chat session. Don't let it drift out of sync with what actually happened.
3. **Update `docs/novocore-roadmap.md`.** Move any step that finished to 🟢 Done, mark the next one **Current**, and fill in the `Actual` hours and token columns for the work this session covered. **Measure, never estimate** — see below.
4. **Commit, once, covering everything.** Stage and commit all outstanding changes — the session's work *and* the three documents above — in a single commit whose message summarizes what was done this session. If the work is incomplete or known-broken, say so explicitly in the message rather than implying it's finished.
5. **Push to `origin`.** Always, without being asked. Then verify it landed (`git log --oneline origin/main -1` after a fetch) and confirm local and remote agree.

Committing before pushing, and documenting before committing, is deliberate: the documentation updates are themselves changes, so committing first would leave them uncommitted and immediately stale — the exact drift step 2 exists to prevent.

Do all five before ending the session — don't ask for confirmation on whether to do them, only flag anything unusual you find while doing so (e.g., uncommitted changes you didn't expect, tests that were failing when you started).

**On the roadmap (step 3).** That file's `Actual` columns are worth something only because every figure in them was measured. The method is documented at the bottom of the file itself and must be followed rather than reinvented: session transcripts in `~/.claude/projects/`, windows bounded by each step's last commit, active time summed from inter-event gaps each capped at 5 minutes, tokens read from the `usage` field on assistant messages.

- **A figure that cannot be measured is left blank with a note saying why.** Never write a plausible number into a column whose entire value is that it contains no plausible numbers.
- **Never overwrite the `Est.` column.** The estimate-versus-actual comparison is the only calibration data this project has, and it is destroyed the first time an estimate is replaced rather than kept alongside.
- **Expect the current session's figures to be short**, because the close-out itself is not yet in the transcript when they are computed. Record them anyway and say so, rather than waiting for a completeness that never arrives.
- **Do not rescale the not-started estimates** to match the observed ratio. That is a decision for the user, made on evidence, not an automatic correction — the measured ratio comes from core-domain build work and does not transfer to adapters, a frontend, or a real data migration.

**On pushing (step 5).** This is a standing instruction, not a per-session decision. Three consecutive sessions ended with unpushed commits because pushing waited on an explicit request, and the repo has no other cross-machine sync mechanism — unpushed work is work that exists on exactly one laptop. So:

- Push at every close-out. Never wait to be asked.
- Because of this, **`PROGRESS.md` must not maintain a list of "unpushed commits."** That list was itself a source of drift; it went out of date and was wrong. State instead that close-out always pushes, so local and `origin/main` agree at the end of every session, and record the commit each step landed in.
- If the push **fails** (no network, rejected, diverged), say so loudly in the session summary and correct `PROGRESS.md` so it does not claim the work is pushed when it isn't. A doc claiming a push that never happened is worse than no note at all.
- If the remote has diverged, do not force-push. Stop and report.

One thing this ordering does **not** override:

- **One commit per build step.** If a build step finished during the session, commit it on its own first, then let the close-out commit carry the documentation and any partial work. The single-commit rule above applies to the close-out, not to collapsing completed steps together. The push at the end covers all of them.
