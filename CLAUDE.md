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

### Named anti-pattern: a verification that answers its own request

**Standing practice: when the question is "will the backend accept this", the backend has to answer
it.** A mock, a stub, an intercepted request or a fixture cannot, because it returns whatever it was
told to return. This is not advice about test *coverage* — screen tests over a mock server are
correct and stay. It is about what a given check is *evidence for*.

**It has already produced a confident, wrong entry in `PROGRESS.md`.** The Products create form was
cleared as working by a headless-browser check that **intercepted `POST /api/products` and answered
it with a fabricated `201`** — deliberately, to avoid writing to the development database. The check
captured the payload, the payload read correctly, and the screen navigated to the new product. Every
observation was true. None of it was evidence for the claim, because the backend never saw the
request: **creating a product failed for every user, every time**, and the owner reproduced it in
about a minute by filling the form and pressing the button. The close-out had recorded the opposite,
and had filed the underlying symptom as an unreproduced backend edge case.

**The objection this exists to answer is a real one** — a verification must not litter a curated
database with test rows. It does not need to:

- **Get the refusal from the server.** Submit against a value the domain already refuses — an
  existing SKU answers `422 "already exists"` **only if the body parsed** — which distinguishes a
  parser rejection from a domain one, exercises the whole path, and creates nothing.
- **Then, once, do it for real** and remove the row, saying in the report what residue is left
  (a sequence advanced, an append-only audit entry) rather than implying none.

**What a mock server structurally cannot see**, and therefore what no screen test can be used to
claim: whether a body satisfies the *real* contract. The worked example is exactly that — the spec
declares required fields on 2 of 185 schemas, so every generated request type is fully optional and
none of them means it; the form was written correctly against a contract that was wrong, and the
only thing that could ever have said so was the server.

### Named anti-pattern: a fact established by reading, then built upon

**Reading source code tells you what one file says. It does not tell you what the system does.** The
gap between those is where this one lives, and it is not carelessness — it opens precisely when the
reading is *careful*, because a careful reading feels like a verified fact and gets written down as
one.

**The worked example, from F4.** `NewUnitOfMeasure.fractionalQuantityAllowed` is a primitive
`boolean`. Its compact constructor null-checks `code` and `name` — and nothing else. Read that
constructor and the conclusion is immediate and wrong: *omitting the field must be accepted, and must
silently arrive as `false`, creating a unit that forbids fractional quantities with nobody having
chosen that.* Every step of the reasoning is sound. The constructor really does check only two
fields.

**The server answers `400`.** `FAIL_ON_NULL_FOR_PRIMITIVES` refuses an absent primitive before the
handler runs — the same mechanism that broke product creation through `NewProduct.serialTracked`, and
the reason `OpenApiSchema` now marks primitives required. **The guard exists; it is one layer above
the file that was read.** No amount of further reading *of that file* would have found it.

⚠️ **What it cost, and why it is worth a named entry:** the claim was written into **three files** —
a screen's javadoc, its test, and `frontend/README.md` — before anything executed it. It was
corrected by the first run of a test that asked the real server. Had that test not existed, a false
explanation would now be sitting in the README under a ⚠️, indistinguishable in tone from the ones
that were paid for.

**The tell is grammatical, and it is worth learning to hear:** *"there is no guard"*, *"nothing
validates this"*, *"the backend cannot catch it"*. Every one of those is a claim about **the absence
of behaviour across a whole system**, and no file contains that. A single file can only ever support
*"this file does not do X."*

**The remedy is not more reading.** It is to notice when a conclusion has crossed from *"this code
says"* to *"the system does"*, and make the system say it: a test, a request, a query. This is the
same rule as *"a verification that answers its own request"*, arriving from the other side — that one
is about a check that cannot see the answer, this one is about a conclusion reached without asking.
**Both reduce to: the thing that decides is the thing that must answer.**

### Named anti-pattern: a test environment configured unlike the real one

**A test that runs against a differently-configured dependency is not testing the system; it is
testing a system.** Nothing about this fails loudly — the suite is green, the code is wrong, and the
difference is a line of infrastructure config nobody looks at.

**It has already happened once, and only the live check caught it.** `docker/compose.yml` initialises
PostgreSQL with `--encoding=UTF8 --locale=C` — deliberately, so sort order is deterministic across
machines. Testcontainers took the **image's own default**, `en_US.utf8`. Under locale `C`,
`lower()` folds ASCII **and nothing else**, so the substring-search normalisation function shipped
with a bare `lower()`:

    Πελάτης Λιανικής  →  Πελατησ Λιανικησ      accents stripped, sigma folded, STILL CAPITALISED

and searching for `πελατησ` returned **zero rows on the real server**. The dedicated test asserting
that exact Greek string **passed**, because the database it ran against folded Greek and the real one
does not. No error, no warning, in either place.

**The fix is two things, and the second is the one that matters:**

1. Make the code not depend on the ambient configuration — here, `lower(… COLLATE pg_c_utf8)`, naming
   PostgreSQL 17's builtin provider explicitly instead of inheriting whatever the server was built
   with. ⚠️ Chosen over ICU deliberately: ICU's behaviour tracks the bundled library version, and an
   index expression whose meaning changes on an upgrade is exactly what must not be indexed. (Unicode
   still moves between major versions — **`REINDEX` the trigram indexes after a major upgrade.**)
2. **Pin the test environment to the real one and assert the pin.**
   `PostgresTestContainerConfiguration` now passes the same `POSTGRES_INITDB_ARGS` as
   `compose.yml`, and `TextSearchIT` asserts `datcollate = 'C'` — so removing the pin fails the
   build instead of silently restoring the blind spot.

**The general rule: every knob the real deployment sets, the test environment must set too — and
something must assert it.** Locale and encoding are the ones already known to bite. Timezone,
`DateStyle`, PostgreSQL major version, and Java default locale/charset are the same shape and are
*not* currently pinned or asserted. **When you touch anything whose behaviour depends on collation,
case, accents, ordering or time, check what the container is actually configured with rather than
what you assume.**

⚠️ **What this cost, and why it is worth a named entry:** the suite was green before and after, so
nothing in this repository could have reported it. It was found by running the migration against the
live stack — which is why the standing practice above ("when the question is *will the backend accept
this*, the backend has to answer it") extends to configuration and not only to request bodies.

### Named anti-pattern: a client's mistake raised as a programming error

**An exception type that means "our code is wrong" must never be used to tell a caller that *their* request is wrong.** The two are handled differently on purpose — `WebExceptionHandler` returns a validation message because an operator who cannot see why a document was refused cannot fix it, and *withholds* the message from a programming error because it describes internal state. Signal a client mistake with the wrong type and the message is correctly discarded: the caller gets `400 "Bad request."`, or a `500` in Boot's legacy body shape, and in both cases a response that looks deliberate.

**This bit three times inside step 15 alone**, in three different disguises:

1. **`IllegalArgumentException` for parameter guidance** — seventeen messages across nine controllers, all thrown away. Fixed by `InvalidRequestException`.
2. **`Objects.requireNonNull` on a request-body field** — sixteen routes answered `500` to a form submitted with something missing. Fixed by `Required.field` in the request record's compact constructor, which is also why `ReversalCommand` fixes six routes in one statement.
3. **`IllegalArgumentException` for an id that names nothing** — four sites in the email slice, answering `400 "Bad request."` where every other route on the surface answers `404 "Not found."`. Fixed by `QueuedEmailNotFoundException` / `EmailAttachmentNotFoundException`.

⚠️ **Those three are DISGUISES, not a total, and conflating the two counts has already made the backend queue contradict itself.** The running count of *instances* is separate and continues past step 15: **instance 4** is the retail customer's own rules thrown as `IllegalArgumentException` from the domain (found while reading the Customers API for F2), and **instance 5** is `Objects.requireNonNull` on `NewUser` and `NewRole` — disguise 2 recurring at a new site (found while reading the Users API for F3). Both are open, as items 4 and 6 in `PROGRESS.md`. **When you find another, it is instance 6, whichever disguise it wears.**

**Instance 5 says something about the step-15 sweep that is worth more than the fix**: that sweep was scoped to the **web layer**, and `NewUser`/`NewRole` live in **`core-api`**. The anti-pattern is not a web-layer phenomenon — a request record can sit anywhere, and the guards below cannot see most of them. Check `core-api` too.

**The remedy is always to name the failure:** `InvalidRequestException` (the request is wrong, and say how), `Required.field` (a body field is missing), or the core's own `...NotFoundException` (the id names nothing). If a new one is needed, add it to `core-api` — `WebExceptionMappingTest` then *forces* it to be mapped, which is the point.

**Three layers guard it, and each catches what the others cannot:**

- `WebAuthorizationRulesTest.clientMistakesAreNotProgrammingErrors` — no class in `..core.web..` may **construct** `IllegalArgumentException`. Build-time and precise; proven to fail against a probe. Blind to anything thrown below the web layer.
- `PermissionSweepIT.noRouteRefusesWithoutSayingWhy` — every route, reads with no parameters and writes with no body, must not answer a bare `"Bad request."`. **This is what found instance 3**, in the service layer where the ArchUnit rule structurally cannot look.
- `PermissionSweepIT.noRouteFailsOnAnEmptyBody` — no route may answer `5xx` to a missing field. Catches instance 2 whatever raised it: a `requireNonNull`, an unboxed null, an `orElseThrow` with the wrong supplier.

**What none of them can see, so watch for it in review:** a *wrong but non-empty* value — an unparseable enum, an id of the right shape naming another party's record, a date range running backwards. Those reach the handler and are only as good as the message written for them. And **`Objects.requireNonNull` is not banned anywhere**, deliberately: it is correct on our own arguments (`ListResponse` uses it properly) and no rule can tell a caller's omission from a programmer's, which is exactly the judgement a reviewer has to make.

## The document model — Novocore does not issue, it records

**This section governs every document screen, adapter and schema decision from R1 onward.** It was
settled in a design session on 2026-08-02 and is not open.

**1. Novocore never obtains a ΜΑΡΚ itself.** Greek law requires transmission to AADE at issuance, and
the document receives a ΜΑΡΚ and a QR code at that moment. **Legal issuance always runs through an
external transmission path — Prosvasis Go today, a certified Πάροχος at step 40 — and that does not
change in any phase.** A sales document appears in Novocore only *after* it legally exists.

**2. Numbers are recorded, never generated — until step 40.** No sequence, no counter, no
allocation-at-commit. What changes at step 40 is narrower than it sounds: Novocore begins allocating
the **series number** and composing the document itself, transmitting via the Πάροχος adapter instead
of handing the job to Go. It still does not obtain the ΜΑΡΚ. **Sequence and gap-prevention machinery
belongs at step 40 and nowhere earlier.**

**3. Naming rule: no operation, class, method or route may be named `issue`, `issueInvoice` or
`issuance`.** Use `requestIssuance`, `submitForIssuance`, `recordIssuedDocument` — or, for the
ordinary case of learning about a document that already exists, `record`, which is what
`SalesInvoiceService.record` has always been called.

- **Prose describing the legal act is not a violation.** "The document was issued by Go and reached
  AADE" is accurate and should stay. The rule is about **identifiers**, because an identifier is what
  a reader infers behaviour from.
- ⚠️ **The rule was written after the misunderstanding, not before it.** `CreditNoteService.issue`
  existed until 2026-08-02, and its `operationId` `SalesController_issue` sat in the committed spec
  and the generated TypeScript client. It was renamed to `record` / `recordNote` in the same session
  the rule was written, **deliberately rather than being queued**: a naming rule with a known standing
  violation is a rule people stop believing. The controller method is `recordNote` and not `record`
  because `SalesController_record` already exists for sales invoices, and a second duplicate
  `operationId` is exactly the defect backend queue item 1 is open against.

**4. ΜΑΡΚ, UID, QR URL and transmission status are CORE fields on the sales invoice** (ADR 0016), not
adapter data. Go's own internal document id stays in the adapter mapping table. This is not a
violation of rule 2 in *Non-negotiable architecture rules* above — see the ADR for why a statutory
identifier of Novocore's own document is categorically different from a vendor's instance identifier.

**5. Document behaviour varies by myDATA type.** ΑΛΠ and ΤΠΔΑ combine sale and transport, so **stock
moves**. A plain Τιμολόγιο is purely sales and **does not reduce stock**. This business issues both
routinely, so this is not an edge case. **Document types are SEEDED from the official AADE list** —
users may activate, deactivate and edit a description, and may **never** author a row or its behaviour
flags. ⚠️ **The model for that is `VatExemptionReason`, NOT `VatClass`.** VAT classes are seeded *and*
extensible — `POST /api/vat-classes` exists and a user can author one and set its `reduced-counterpart`
link. Reaching for VAT classes as the analogy produces the wrong thing.

**6. Known limitation, and it must stay visible.** Until a dispatch document exists (18b), **stock
figures are incomplete for every non-stock-moving sales document**, which is a routine share of real
sales. The document is recorded, the ledger posts, stock is left untouched, and the document must sit
in a **queryable** "stock not yet moved" state so the gap is measurable rather than merely known.

**7. `Στοιχείο Αυτοπαράδοσης` (self-supply)** covers internal consumption and moving inventory into
fixed assets. The customer is the issuer, so it needs a **protected self-customer record** on the
retail-walk-in pattern, **excluded from customer sales, revenue and margin reporting** since revenue is
recognised at cost. **The line price derives from FIFO lot cost, not the price list** — which couples
pricing to lot selection, true nowhere else in the system. VAT is deductible and **not** capitalised
into the asset cost. **Which accounts carry each leg is an accountant question — refuse rather than
guess.**

**8. Document transformation.** An employee correcting a mistake must transform a document into the
correct series or a return document **in one action**, with series, products and customer auto-filled,
**never re-keyed**. Same flow for a returned or cancelled order. This needs the Go adapter; only the
allowed-target reference is stored earlier (R1).

### AADE reference data

**AADE publishes no live API for codifications.** The myDATA REST API only moves documents. Code lists
live in the Annex tables of versioned PDFs, the XSD schema files, and an Excel of permitted
classification combinations — and they **do** change between versions. *(Source: the 2026-08-02 design
session, checked against AADE's published REST API method list. Not verified from inside this
repository.)*

**The approach:** seed as versioned core data, store which spec version the seed corresponds to, and
add a periodic diff check that **alerts a human and never auto-applies**. ⚠️ **Auto-apply is
forbidden** because a code list that updates itself would silently change what already-transmitted
documents claim.

**The live AADE services that genuinely are adapter-shaped** are a different thing and belong at step
28: the **Basic Business Registry ΑΦΜ lookup** and **VIES**.

### The integration outbox does not exist

⚠️ **Architecture rule 4 — *a core operation never waits on an external call* — has no
implementation.** Step 11 built an **email** outbox and nothing else: verified 2026-08-02, every
`outbox` reference in the backend is email, backup or attachment, and `backend/adapters` and
`backend/modules` contain zero Java files. There is **no** general integration-event outbox, **no**
idempotency keys, **no** replay log and **no** ordering guarantee. **Ten adapters need all four.**
Under this file's own "one shared service per cross-cutting concern" rule it should be built once,
before the first adapter — roadmap step X1. **Record it as a real gap, not a future nicety.** For
myDATA in particular, "transmitted twice" is not a theoretical failure mode.

⚠️ **Adapter ID-mapping tables have no designed lifecycle either**, and it is the same design item:
external ID reuse, deleted-then-recreated external records, and two adapters disagreeing about which
core record an external ID resolves to are all unanswered.

### Product categories are not two columns

⚠️ **Three levels deep, and a product belongs to several categories at once** — a self-referencing
category table plus a join table. **Not two flat columns, and not an enum.** Nothing exists, not even
the schema; `V29` carries a header saying so, because the brief's one-line *"Category (main/sub)"*
understates it and building from that line would produce the wrong thing.

### Channel already reaches the ledger

`SalesChannel` is an enum, `sales_invoice.channel` is `NOT NULL` with a CHECK, and **step 3 split the
Sales *and* Sales-returns accounts per channel** — so per-channel revenue and return rate are already
visible. **R1 references channel; it must not create it.** The open question is not channel: it is
whether a **generic analysis-dimension mechanism on journal lines** is wanted before a second dimension
(shop, product line, campaign) is needed, since account-splitting multiplies rather than adds and
retrofitting a dimension means restating history. Recorded as an open decision in the roadmap.

## Stack

**Backend:** Java + Spring Boot, PostgreSQL, Docker, self-hosted with an HTTPS reverse proxy from the start. No SQLite, no Python/PHP backend — these were deliberately ruled out, don't reintroduce them for "quick" tooling either.

**Frontend:** lives in `/frontend/`, a separate directory from the backend. **Read `frontend/README.md` before writing frontend code — it is not a getting-started file.** Every convention in it was earned: the write-hooks defect, the render loop that wedged the tab, the select that showed raw ids, the two ways a control can be unavailable, and the one place a credential is ever displayed. Vite + React + TypeScript + Tailwind CSS + shadcn/ui. Use shadcn's default theme until Claude Design defines the real brand look — don't invent a color palette or visual style yourself. When more than one shadcn component could reasonably fit a given UI element, or there's no written component-mapping guidance covering it, stop and ask which one to use rather than picking one yourself; for anything a component-mapping reference (once one exists in this repo) already covers clearly, use it directly without asking.

## Environment note

This repo lives on local disk on every machine it's developed from — never inside a cloud-sync folder (Google Drive, OneDrive, Dropbox). Cross-machine access happens exclusively via git/GitHub (`https://github.com/Novogrowth/NovoCore.git`). Do not assume or recreate a cloud-sync dependency for this repo; if a task seems to need one, ask first.

## Scope discipline

NovoCore is built in phases (see the brief's roadmap). **Only build what the current phase asks for.** Do not pre-build adapters or modules for later phases "while you're in there" — this project deliberately avoids scope creep. If a task seems to need something from a later phase, say so and ask, rather than building it speculatively.

## When something in the brief is marked "draft" or "open"

Ask before implementing. Several entity field lists and mechanisms in the brief are explicitly marked as not finalized — building against them as if they were final risks real rework.

## An approved proposal is a checklist, not a paragraph

**When a proposal with more than one sub-part is approved, write the approval into `docs/PROGRESS.md` as an explicit checklist — one line per sub-part — at the moment it is approved.** Not as prose describing what was agreed. Prose is where a sub-part goes to be forgotten, because a summary of what *was* built reads as complete no matter what is missing from it.

**This rule exists because it has already cost something.** Step 15's proposal had three commits in it — 15a, 15b and **15c, the seed pass that populates the live Compose database** — and the step was agreed at *full* scope, which included 15c. 15a and 15b landed. 15c did not. `PROGRESS.md` recorded "15a — the harness", "15b, completed", and **never mentioned 15c in either direction** — not delivered, not deferred, not cut. Nothing was wrong with any sentence in that close-out; it was accurate about everything it described. The gap was that it summarised what was built instead of reconciling against what was approved.

The cost surfaced two steps later, in the frontend: the development database held nothing but Flyway's own seed, the first real screen correctly showed an empty table, and a session was spent proving the data had never existed rather than building anything. The seam 15c needed (`HttpTransport`) had been sitting in the repository the whole time with a javadoc naming the driver that was never written.

### And reconcile against the *fullest* list, not the one the step was scoped from

**A second, cheaper version of the same failure**, from S1 (substring search). The step was scoped in conversation against five entities that already had screens. Reconciling it afterwards against the **complete** per-screen field list found two gaps, and **the suite was green before and after** — because a test only ever checks the fields somebody pointed it at:

- **`Product.brand` had never been built at all**, despite being in brief §5's Product list from the beginning. Absent from the schema, therefore absent from every test, therefore invisible.
- **`supplier.vat_number` existed and simply was not searched**, while `customer.vat_number` was — an inconsistency with no argument behind it, visible only by reading the two lists side by side.

**The remedy is to write the full target list down once, in `PROGRESS.md`, and have each step adopt its row** rather than re-derive a narrower one from memory of a conversation. The search target list is there now and is the worked example. ⚠️ **A field named in the brief is not evidence that it was built** — check the schema. Several of the brief's entity field lists are marked *(draft)* and were built partially; `Supplier.code`, `Supplier.alias` and `Customer.code` are still not columns.

### A decision reached in a design conversation gets the same close-out discipline as a build step

**A decision is not recorded because it was reached. It is recorded because somebody wrote it into a
repository document.** Chat is not a repository. If a design conversation settles something — a
model, a constraint, a naming rule, a placement — it gets written into `PROGRESS.md` (and wherever
else governs it) **in the same session**, as a checklist line with a verdict, exactly as an approved
build proposal does.

**This rule exists because it had already cost something by the time it was written.** As of
2026-08-02, **four core-model decisions existed only in chat and were absent from every document in
this repository**: document types, document series, the legal basis for VAT exemptions, and myDATA
payment-method codes. All four are prerequisites for the document model — R1 — and none of them was
findable by a fresh session reading the project record. Worse, the record was **actively misleading
in a way nobody would have questioned**: it listed **F5 as the next step** long after the backend
queue had been prioritised ahead of it in conversation. A reader following the documents would have
started the wrong work, with nothing anywhere to contradict them.

**This is §"An approved proposal is a checklist, not a paragraph" occurring one level up.** That rule
catches a sub-part lost inside an approved proposal. This one catches a whole *decision* lost because
the conversation that produced it was never treated as producing an artefact. The failure mode is
identical and so is the remedy: **prose in a chat window is where a decision goes to be forgotten,
because a conversation that felt conclusive reads as recorded.**

**Two tells that a conversation has produced something that must be written down now:**

- You find yourself about to say *"as we agreed"* about something you cannot cite a file for.
- A document you are reading contradicts what you believe, and your instinct is that the document is
  out of date rather than that you are wrong. **Check which — and then fix the document either way.**

### Every figure written into a document carries a date or a step reference

**A bare number — a route count, a test count, a total — is correct in the paragraph that wrote it and
wrong the moment somebody lifts it out. This is the same failure as two records disagreeing, one
document further along.** So write *"175 operations as of 2026-08-02"* or *"133 routes at step 14"*,
never *"175 operations"*.

The failure needs no carelessness to happen: the number was true, the sentence around it was true, and
a later reader — or a later paragraph in the same file — quotes it as current. Two records then
disagree, exactly as they did over backend queue item 3, except that here **both records were written
by someone who checked.**

⚠️ **`PROGRESS.md` is known to contain unswept instances of this** — per-step route and test counts
that are correct in their step's context and wrong lifted out. The headline ones were date-stamped
during U1; the rest were deliberately not swept, and that is stated rather than claimed as done. The
sweep belongs with **U2**, the `PROGRESS.md`/`HISTORY.md` split.

## Session close-out

When the user says "close the session" (or clearly equivalent phrasing like "let's stop here" or "end session"), perform these six actions **in this order**, regardless of what step or task is in progress:

1. **Reconcile every approved checklist this session touched.** For each sub-part of every proposal in play, state which of exactly three things it is: **done** (and how it was verified), **explicitly deferred** (with the reason, and where it is now recorded so it can be picked up), or **still open**. Reconcile against the *approved* list, not against memory of what was worked on — the failure mode this exists to catch is a sub-part nobody has thought about since it was approved, and a summary written from what was built cannot see it. **A sub-part with no verdict is a finding**, and says so in the close-out. If a proposal was approved this session and never written down as a checklist, write it down now, then reconcile against it.
2. **Update `docs/PROGRESS.md`.** Record: which step(s) were worked on, what's now done and verified, what's still open or blocked (including any question numbers from the product brief), and the concrete next action for the following session. Overwrite stale status, don't just append. The reconciliation from step 1 goes here, as the checklist with its verdicts — not as a paragraph summarising them.
3. **Update `docs/novocore-context-primer.md`.** Reflect any changes to build status, resolved decisions, or open items so the primer stays accurate for a fresh chat session. Don't let it drift out of sync with what actually happened.
4. **Update `docs/novocore-roadmap.md`.** Move any step that finished to 🟢 Done, mark the next one **Current**, and fill in the `Actual` hours and token columns for the work this session covered. **Measure, never estimate** — see below. **There is one roadmap.** `docs/novocore-frontend-roadmap.md` was deleted on 2026-08-02 and merged into it; backend and frontend are one sequence because several steps span both. Do not recreate a second roadmap file — a second record of the same thing is what let the backend queue and the frontend roadmap disagree about item 3 for a week.
5. **Commit, once, covering everything.** Stage and commit all outstanding changes — the session's work *and* the three documents above — in a single commit whose message summarizes what was done this session. If the work is incomplete or known-broken, say so explicitly in the message rather than implying it's finished.
6. **Push to `origin`.** Always, without being asked. Then verify it landed (`git log --oneline origin/main -1` after a fetch) and confirm local and remote agree.

Reconciling before documenting, committing before pushing, and documenting before committing, is deliberate: the reconciliation is what the documentation has to record, and the documentation updates are themselves changes, so committing first would leave them uncommitted and immediately stale — the exact drift step 3 exists to prevent.

Do all six before ending the session — don't ask for confirmation on whether to do them, only flag anything unusual you find while doing so (e.g., uncommitted changes you didn't expect, tests that were failing when you started).

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
