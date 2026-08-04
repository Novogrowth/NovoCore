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

### Named anti-pattern: the extractor answered, and it was not answering the question

**⚠️ Found in R1's Phase 0, 2026-08-03, reading the AADE artefacts.** `pdftotext -layout` is the
obvious way to read a specification PDF, and on the myDATA annexes it **silently drifts the code
column and the description column apart by one or more rows.** Annexes **8.2, 8.7, 8.8 and 8.13** are
all affected. The output is not empty, not truncated, not garbled-looking and not flagged — it is a
clean two-column table of **code/description pairs that are confidently wrong.**

Annex 8.2 extracted as `1 → 24%`, `2 → ΦΠΑ συντελεστής 24% / 13%`, `3 → ΦΠΑ συντελεστής 13% / 6%`.
Every code is real, every description is real, and **every pairing is off by one.** Nothing about the
text says so. A seed written from it would transmit the wrong VAT category to the tax authority under
a code list that had been "read from the official artefact."

**The rule, and it is mechanical rather than a matter of care:**

- **Codes come from the XSD enumerations.** `SimpleTypes-v2.0.1.xsd` carries the same code sets as
  flat `<xs:enumeration value="…"/>` lists. **There is no layout to lose**, so there is nothing for an
  extractor to misalign. This is why the XSDs are in the repository unzipped.
- **Greek descriptions come from a rasterised page, or from a human.** Never from a text dump of the
  annex, and never from the extractor's pairing even when the pairing looks right — *especially* when
  it looks right, because that is the only state it is ever observed in.
- **A code list whose codes and descriptions came from the same text dump has one source, not two.**
  Two sources that agree is evidence; one source read twice is not.

**Why it belongs beside the entries above rather than in a README.** It is the same sentence as *the
thing that answered was not the thing under test* and *a verification that answers its own request*,
arriving from a third direction: **the tool answered, and what it answered was not what was asked.**
Nothing failed. No exception, no exit code, no missing output — which is exactly why it needs a rule
instead of attention. The artefact-specific detail lives in `docs/aade/v2.0.1/README.md`; the general
practice lives here because **the next PDF codification will not be AADE's.**

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

1. **`IllegalArgumentException` for parameter guidance** — seventeen messages across nine controllers, all thrown away. Fixed by `InvalidRequestException`, **renamed `InvalidInputException` and moved to `core-api` in Q1** — see the finding below.
2. **`Objects.requireNonNull` on a request-body field** — sixteen routes answered `500` to a form submitted with something missing. Fixed by `Required.field` in the request record's compact constructor, which is also why `ReversalCommand` fixes six routes in one statement.
3. **`IllegalArgumentException` for an id that names nothing** — four sites in the email slice, answering `400 "Bad request."` where every other route on the surface answers `404 "Not found."`. Fixed by `QueuedEmailNotFoundException` / `EmailAttachmentNotFoundException`.

⚠️ **Those three are DISGUISES, not a total, and conflating the two counts has already made the backend queue contradict itself.** The running count of *instances* is separate and continues past step 15: **instance 4** was the retail customer's own rules thrown as `IllegalArgumentException` (found while reading the Customers API for F2), and **instance 5** was `Objects.requireNonNull` on `NewUser` and `NewRole` — disguise 2 recurring at a new site (found while reading the Users API for F3). ✅ **Both were fixed in Q1 on 2026-08-03.** **When you find another, it is instance 6, whichever disguise it wears.**

**Instance 5 says something about the step-15 sweep that is worth more than the fix**: that sweep was scoped to the **web layer**, and `NewUser`/`NewRole` live in **`core-api`**. The anti-pattern is not a web-layer phenomenon — a request record can sit anywhere, and the guards below cannot see most of them. Check `core-api` too. ⚠️ **And read the Q1 finding below before adding a fourth guard**: the reason `core-api` was never swept is that the vocabulary to sweep it *with* did not exist there.

⚠️ **Instance 4 was not where its own write-up said it was, which is its own small lesson.** The two rules live in **`CustomerView`** — a *response* record in `core-api` — not in the `Customer` domain entity. So the remedy was **not** "swap the exception type there": a view is built only after the service has accepted a change, so a view that cannot satisfy its invariants means *this codebase* assembled an incoherent record, and `IllegalArgumentException` is exactly right for that. **What was missing was any check on the path a caller takes.** The rules are enforced in `CustomerServiceImpl` now, where `deactivate` already checked the same flag; the view's invariants stayed, as the backstop they correctly are. **A response record's invariant and a caller-facing rule look identical in a diff and are opposites in intent.**

**The remedy is always to name the failure:** `InvalidInputException` (the caller's input is wrong, and say how), `Required.field` (a body field is missing), or the core's own `...NotFoundException` (the id names nothing). If a new one is needed, add it to `core-api` — `WebExceptionMappingTest` then *forces* it to be mapped, which is the point.

### ⚠️ Five instances were not one confusion. They were two — and only one was a mechanism gap

**This is the finding from Q1 (2026-08-03), and it is worth more than the two fixes it produced.**
Five instances of one named anti-pattern, three guards that all missed the last two, and the obvious
conclusion — *add a fourth guard* — is wrong. Tracing **why the remedy was unused** rather than
counting instances splits them cleanly:

- **Group A — the wrong exception for a domain refusal or an unknown id** (instances 1, 3, 4). The
  remedy existed, was in reach, and was *already used correctly in the neighbouring method*:
  `CustomerServiceImpl.deactivate` throws `InvalidCustomerException` and answers 422 with its reason,
  three methods from the two rules that answered a bare 400. **These are local slips.** No mechanism
  was missing.
- **Group B — a missing body field** (instances 2, 5). ⚠️ **`Required` and `InvalidRequestException`
  lived in `core.web`. `NewUser` and `NewRole` live in `core-api`, which has zero production
  dependencies by design.** The prescribed remedy was not merely unused at instance 5's site — **it
  was structurally unreachable from it.** Instance 5 was not a lapse of attention. **It was the only
  thing its author could have written.**

**No guard could have found this**, and that is the point. `WebAuthorizationRulesTest` is scoped to
`..core.web..`; the record is in `core-api`. A rule with a wider scope would not have helped either,
because the fault was *a missing remedy, not a misused one* — there was nothing wrong to detect, only
something absent.

**The fix was placement, and the objection to it was a naming problem.** `Required` and the exception
moved to `gr.novotrade.novocore.core.api.shared`, and the exception lost "Request" →
**`InvalidInputException`**. *"The caller supplied a structurally incomplete command"* is not
HTTP-specific — an adapter calling `UserService.create` with a null username has made exactly the
mistake an HTTP client makes by omitting the key. **Only the word "request" and the 400 mapping were
ever web-shaped**, and the mapping stayed in `WebExceptionHandler` where the argument for
400-over-422 is written out. `WebExceptionMappingTest` now *forces* that mapping to exist, which is
free enforcement the old placement did not get.

⚠️ **The cheap alternative was considered and rejected**: having those records throw the
`InvalidUserException` / `InvalidRoleException` that already sit beside them. Zero new dependencies,
zero new concepts — and it would have answered **422 in `core-api` and 400 in `core.web` for the same
mistake, decided by nothing but which module the record happens to live in.** An inconsistency with
no argument behind it is the shape S1's reconciliation caught with `supplier.vat_number`.

**The general rule: when one confusion recurs, ask whether the remedy was reachable from where the
mistake was made. If it was not, the instance count is measuring the wrong thing.**

### ⚠️ A contract must not promise what nothing refuses — and the check must be able to see which field

**This is 8a's finding (2026-08-03), and the part worth keeping is not the annotation.**

`@Mandatory` declares that a record component is never absent, because **reflection cannot see inside
a constructor body** and so `OpenApiSchema` could only ever infer it for primitives. 339 components
across 114 records were mandatory in fact and described as optional, and the generated TypeScript
made every one of them `T | undefined`. That much is ordinary. Three things about it are not:

**1. The declaration is worthless without a cross-check, and the obvious tool cannot do it.**
Several hundred hand-applied assertions that nothing verifies is *a fact established by reading, then
built upon* at the scale of an API surface — and the second direction is the dangerous one: a field
wrongly declared mandatory lets every consumer dereference it without a check, and **nothing anywhere
refuses the body that omits it.** ⚠️ **ArchUnit cannot express this rule.**
`JavaCodeUnit.getMethodCallsFromSelf()` carries **no argument information** — it can say a
constructor guards *something* and never *which component* — and it **attributes lambda-body calls to
the enclosing code unit**, reporting 342 guard calls where the constructors' bytecode contains 340.
That second half is the blind spot this file already names under proxy self-invocation, met from a
new direction. `MandatoryDeclarationRulesTest` uses `org.springframework.asm` plus reflection, with
ArchUnit reduced to class discovery. **When a rule needs to know what a call was applied to, ArchUnit
is the wrong instrument; check before designing around it.**

**2. A declared set derived from guards is a LOWER BOUND, and saying so is load-bearing.** Only
`Required.field`, `Required.text` and `Objects.requireNonNull` are visible; a component made
mandatory by an inline `if (x == null) throw` is not. **"Every `@Mandatory` component is guarded"
must never be read as "every mandatory component is annotated."** An incomplete `required` list is
still true; a wrong one is worse than none. The same sentence covers the conditional case:
`NewPurchaseInvoiceLine` has five components of which at most three can ever be present, selected by
`type`, and **no `required` list can express that** — so they carry `@ConditionallyMandatory` with a
reason **at the field**, not an exemption list inside the test. A future reader who finds a guarded
component without `@Mandatory` must be able to see why without reconstructing it.

**3. A probe of this rule reported PASS while measuring nothing**, which is where the two
requirements under *the throwaway probe* came from — **a negative control, and never piping a build.**
Stated once, there, rather than twice.

### ⚠️ Named anti-pattern: the screen was the only guard, and nothing behind it was

**Found by R2's live leg, 2026-08-04, and it is the reason a browser pass is not a formality.**

`SalesDocumentSeriesServiceImpl.create` resolved a document type and **never read `isActive()`**. The
*create screen* filtered its picker to active types; the *edit screen* did not filter at all. So the
rule "a series may not point at a retired document type" was enforced by one screen, on one of the
two paths, and by nothing at all for an adapter or a direct API call.

⚠️ **The data proved it rather than an argument**: both series the owner created point at an inactive
type — one deactivated, one a **draft whose stock question was never answered**. Nothing refused
either.

**The tell, and it is worth learning to hear:** *"the picker only offers active ones."* That is a
statement about a **control**, and it is being used to answer a question about a **rule**. The two
are different the moment a second screen, an adapter or a `curl` exists — and R2 shipped the second
screen in the same step, which is how the inconsistency became visible at all.

**The remedy is not "filter the other picker too."** It is to ask what refuses this when the screen is
not there, and if the answer is nothing, build it in the service — then fix both pickers, which are
now stopping a request whose refusal is certain rather than being the refusal.

⚠️ **Two things R2b's fix records that are easy to get wrong:**

- **SETTING is refused; HOLDING is not.** Deactivating a document type must not break the series
  already pointing at it, or deactivation becomes destructive and nobody will use it. The guard runs
  on create and on change — the two places a type is *set* — and nowhere else. The same shape as the
  `active` flag on a payment method: recording a *new* invoice against a retired method is refused,
  and invoices already settled by it are untouched.
- ⚠️ **Test the DRAFT case before the INACTIVE case.** A draft is *always* inactive — the CHECK forces
  it — so testing `!isActive()` first catches every draft and gives it the milder message, and the
  specific reason becomes unreachable. They are genuinely different failures: a draft's stock
  behaviour is undecided, so a document in a series pointing at it **could not post correctly**;
  deactivated merely means *not for new documents*.

### ⚠️ Named anti-pattern: a defect copied faithfully thirteen times, and self-healing

**Also found by R2's live leg, and it had been in the repository since the first screen.**

**Not one of this application's thirteen create forms invalidated its list.** Every one mutates and
then navigates to the new record — products, customers, suppliers, users, roles, VAT classes, units
of measure, and all six of R2's. R2 did not diverge from the pattern; **it copied the pattern
faithfully, including the defect.**

⚠️ **The reason it survived seven screens is that it heals itself.** `staleTime` is 30 seconds, so a
list revisited inside that window is served from cache without the new row, and a list revisited
after it looks perfectly fine. **A bug that fixes itself in half a minute reads as "the browser being
slow", not as a bug** — and nobody adding one supplier a week ever hits it. It becomes constant only
when somebody creates fifty rows in a sitting, which is exactly what R2's screens exist for.

**The fix is global — one `MutationCache.onSuccess` on the shared `QueryClient` — and the argument is
the defect itself:** thirteen copies of a line that must never be forgotten is the shape that
produced this, and a fourteenth create form would copy it again.

⚠️ **A global fix needs a STRUCTURAL guard, and this is the part that is easy to skip.** With the fix
in the shared client, deleting it leaves **every screen test in the repository passing** — no screen
contains it, so no screen test can see it. `query-client.test.ts` therefore asserts *both*: that the
handler is present (cheap, and what makes removal a red build) and that it works end to end through
a real `createQueryClient()` with its real 30-second `staleTime`. A test client with caching turned
off would pass against the defect and prove nothing.

📌 **One consequence worth knowing before it surprises somebody:** a screen test whose `msw` handler
is a static fixture will now show *pre-edit* data after a save, because the app refetches where it
used to trust `setQueryData`. That is the mock being unfaithful, not the app being wrong — the
remedy is to make the handler record its writes, as `products.test.tsx` now does.

### Named practice: the throwaway probe

**When the question is behavioural — *what does the system actually answer?* — boot the real
application over real HTTP against a real database in a throwaway integration test, print every
status and body, read them, then delete it.** Reading the code answers what it *says*. The probe
answers what it *does*. This is `CLAUDE.md`'s two reading-related anti-patterns met from the practical
side, and it costs about ten minutes.

**It earned its entry in Q1's Phase 0 by correcting two premises that careful reading had produced
and would have been built on:**

- Backend queue item 4 recorded that setting `EXEMPT` on the retail customer answers a bare 400. **It
  answers 422**, from a *generic* rule that applies to every customer. The retail rule is reachable
  only when the body **also** carries `vatExemptionReasonId` — so the sweep case written against the
  item's own description would have passed against the defect it existed for.
- Item 6 recorded `POST /api/users {}` as evidence about its `requireNonNull` guards. **That body
  never reaches them** — the primitive `roleId` fails first. `POST /api/roles {}` is the clean case.

**Both were found by a probe that printed 26 responses, and neither was findable by reading.**

#### ⚠️ Two requirements. A probe that does not meet them is not evidence

**These are preconditions of trusting a probe's result, not advice about writing better ones.** A
probe exists to be believed — that is its whole purpose — so the question *"could this have reported
what it reported while measuring nothing?"* has to be answerable before the result is used.

**1. Every probe carries a negative control — a case designed to FAIL.**

**A probe with only positive cases cannot distinguish *the thing works* from *the thing was never
there*.** Both produce the same output. Add at least one case that must fail, and treat its failing
as the evidence that the probe is measuring anything at all: if the negative control passes, **every
other result in that run is void**, including the ones that looked right.

⚠️ **This is not hypothetical and the margin was one case.** In 8a, four conditions lined up at
once — an uncommitted change reverted by a `git checkout`, a file that then did not compile, a build
error swallowed by a pipe (see requirement 2), and `architecture-tests` answering from a
**previously installed jar** — and the probe reported **PASS**. Every individual observation was
true. It was caught **only** because that particular case was designed to fail and reported success,
which is loud. **Reverse the polarity and the identical mistake ships in silence**, because nothing
ever prompts a second look at a pass. It is *the thing that answered was not the thing under test*,
at the scale of one shell command.

**The worked examples are already in this file, and they are this requirement rather than three
separate habits:** the item 4 sweep case was run against the defect first, watched to fail, and only
then run against the fix; `WebAuthorizationRulesTest.clientMistakesAreNotProgrammingErrors` and
`PermissionSweepIT.noDomainRuleRefusesAWellFormedBodyWithoutSayingWhy` were each proven the same way.
⚠️ **Those three have the new *guard* as the subject — does it catch the defect. This requirement has
the *probe* as the subject — is the apparatus alive.** The second is the more general and the easier
to skip, because a green run feels like an answer.

⚠️ **It happened again in R1b (2026-08-04), the mechanism was new — and it is now CLOSED BY
CONFIGURATION rather than by this paragraph.**

The stock branch's negative control was run against deliberately-broken code with
`mvn -pl core -am verify -Dit.test='SalesInvoiceIT#aNonStockMovingTypeConsumesNothing+aStockMovingTypeStillConsumes'`,
and it reported **`BUILD SUCCESS`**. The branch really had been removed; the source on disk said so.
**Failsafe never matched the `Class#a+b` selector, ran nothing, and `-Dfailsafe.failIfNoSpecifiedTests=false`
turned "measured nothing" into a green build.**

⚠️ **This is the reason the fix is a pom change and not another sentence here.** The rule *"if the
negative control passes, every other result in that run is void"* already existed, in this file, above
this line — and it cannot help, because **the run reported success**. There was nothing for the rule to
fire on. **Four earlier members of this family were also already covered by written rules.** A defence
that depends on the session noticing is the thing that just failed; the only lever left is the tool.

**✅ THE FIX, 2026-08-04: `failIfNoSpecifiedTests` is pinned `true` in the `<configuration>` of BOTH
surefire and failsafe in `backend/pom.xml`.** Neither plugin carried the setting before — both were
simply on their default, which is already `true` — so **nothing in the repository was ever wrong; the
`false` only ever came from a command line.** Pinning it in `<configuration>` is what makes that
command line inert: **an explicit plugin `<configuration>` value beats the user property a `-D` flag
feeds**, so `-Dfailsafe.failIfNoSpecifiedTests=false` now does nothing. Proven by running the exact
command shape that silently passed and watching it fail.

⚠️ **The cost is real, is not hidden, and is the reason to read before "fixing" a build that refuses a
selector: `-am` together with `-Dtest`/`-Dit.test` no longer works.** A reactor build visits modules
that legitimately do not contain the named test — the aggregator has no tests at all — and each of
those now fails. **The replacement is to run the module's whole suite** (`mvn -pl core -am verify`),
which is what a negative control should use anyway, since it is the invocation that cannot report
success while measuring nothing. A build with **no** selector is completely unaffected; the flag only
engages when one is given.

⚠️ **Do not re-open it by adding `-Dfailsafe.failIfNoSpecifiedTests=false` back.** It will not work,
and the error message unhelpfully suggests exactly that flag — that suggestion comes from the plugin
and predates the pin.

##### ⚠️ The replacement invocation — and the trap you will fall into if you improvise one

**The obvious workaround for "the selector fails with `-am`" is to drop `-am`. Do not.** `-pl X`
without `-am` is **already a named member of the stale-artefact family above**, and it is what
produced two mis-diagnoses inside twenty minutes in R1a. **A fix that makes a neighbouring failure
mode more attractive has to name the replacement, or the replacement gets invented under time
pressure by somebody who just wants one test to run.**

⚠️ **This is not theoretical, and it was measured on 2026-08-04 rather than argued.** The jars in
`~/.m2` were from the previous day. `./mvnw -pl app clean test-compile` compiled `TradingQuarter`
against that stale `core-api` and failed with `long cannot be converted to SalesChannel` — R1b's own
change, invisible to the module being built. `dependency:build-classpath` confirmed it: the classpath
named `~/.m2/…/novocore-core-api-0.1.0-SNAPSHOT.jar`, not the reactor.

📌 **And the first attempt to demonstrate it reported `BUILD SUCCESS` — because `-pl app test-compile`
without `clean` said `Nothing to compile - all classes are up to date` and compiled nothing.** The
same family, one layer down: a green build that measured nothing. **Use `clean` when the question is
"what does this compile against".**

**✅ THE REPLACEMENT — a two-step, and the `&&` is load-bearing:**

```
cd backend
./mvnw -pl <module> -am install -DskipTests   && ./mvnw -pl <module> verify -Dit.test=<TestClass>
```

**Step 1 rebuilds every dependency from source and installs it**, so step 2's `-pl` resolves fresh
artefacts instead of whatever `~/.m2` happens to hold. **Step 2 has no `-am`, so no module without
the named test is visited, and the pin does its job in the module that has it.**

⚠️ **`&&`, never `;`.** If step 1 fails part-way — the R1a bite where `install` aborted at a test, so
`core-api` reinstalled and `core` did not — a `;` would run step 2 against a half-updated set and let
it explain the old code to you in the vocabulary of the new. `-DskipTests` makes an abort much less
likely; the `&&` is what makes it *impossible* to proceed from one.

**Verified end to end on 2026-08-04:** step 1 exit 0 → step 2 compiles clean, R1b's `core-api` change
picked up; and a name matching nothing still fails (`No tests matching pattern "NoSuchTestAnywhere"`),
so the two-step does not quietly reopen the hole the pin closed.

**The always-safe option, and the right one for a negative control:** run the module's whole suite,
`./mvnw -pl <module> -am verify`. No selector, so nothing to mis-match, and no stale artefact — it is
the invocation that cannot report success while measuring nothing.

**2. A piped build hides its own failure. Do not pipe one, or set `-o pipefail`.**

**The mechanism, because the instruction on its own is forgettable and the mechanism is not:** a
shell pipeline's exit status is the exit status of its **last** command. `mvn … | tail -3` therefore
returns `tail`'s status — `0`, essentially always — and the build's own non-zero status is discarded
before anything can test it. ⚠️ **And the filter destroys the evidence in the same stroke:** the
compiler error that would have been read instead is exactly what `tail -3` cut off. So the failure is
invisible to a script *and* to a human reading the output, which is why it survives both.

**Either `set -o pipefail` in any script that pipes a build, or do not pipe the build.** When a probe
depends on a fresh artefact — and one that rebuilds a module before testing it always does —
**confirm the build succeeded on its own terms before believing anything the test then says.**

**3. ⚠️ A prove-against-the-defect script must RESTORE with something that cannot silently restore
too much. `git checkout --` is not that thing.**

**W1 met this on 2026-08-04 and it is the third occurrence in this file.** A script broke the code
four ways in turn, ran the guard against each, and restored with `git checkout -- <path>` between
rounds. **All four proofs fired correctly and named the right property.** The restores did not:

- On a **tracked** file, `git checkout --` restores it to **`HEAD`** — which reverted the step's own
  uncommitted work. `OpenItemRef`'s deletion and its entire rationale comment vanished, and so did a
  regenerated `openapi.json`. **Silently, and reported as success.**
- On the **untracked** new test file it failed loudly (`did not match any file(s) known to git`) and
  **left the injected defect in place**, so every later round in that run was measuring broken code.

⚠️ **Note which half was dangerous.** The loud failure cost nothing — it was investigated
immediately. The silent one reverted real work while the script printed nothing, and would have been
committed had the tree not been read afterwards. This is *the thing that answered was not the thing
under test* one more time: the later rounds ran against a tree nobody had looked at.

**The remedy, in order of preference: copy the file aside and copy it back; or make the injection a
patch you reverse; or — simplest and what should have been done — inject and restore each defect by
hand, one at a time, reading `git status` between rounds.** A batch script is a false economy here,
because the whole point of the exercise is that you are deliberately holding a broken tree.

📌 **And always `git status` after any prove-against-the-defect run**, before believing the green
that follows it. W1's script ended by re-running the guard and reporting failure — which is the only
reason the damage was found in the same minute rather than in the commit.

📌 **A recommendation is on file to make this unnecessary, and it is deliberately not built** —
roadmap footnote ᵇˢ, recorded by U3 on 2026-08-03. **One build script** that sets `pipefail`, always
builds with `-am` and never truncates output, which this file would then tell sessions to invoke — so
the mistake requires **deliberately not using the provided tool**. ⚠️ **The argument for it is that
nothing in this repository can guard a session's shell habits**: no ArchUnit rule, no test and no CI
job sees how a command was typed, so **the only available lever is making the correct invocation the
default one.** The stale-artefact family has **four** members and this rule is a convention — which is
why it was written in one session (8a) and the family gained two more the same day (R1a). ⚠️ Those two
did not involve a pipe; they were an **unread build exit status**, which is the same rule and is why
they are recorded together.

⚠️ **Delete it.** A probe is evidence for a decision, not a test — it asserts nothing, so leaving it
behind adds runtime and implies coverage it does not provide. What survives is the *assertion* it
justified, written into a real test.

**Three layers guard it, and each catches what the others cannot:**

- `WebAuthorizationRulesTest.clientMistakesAreNotProgrammingErrors` — no class in `..core.web..` may **construct** `IllegalArgumentException`. Build-time and precise; proven to fail against a probe. Blind to anything thrown below the web layer.
- `PermissionSweepIT.noRouteRefusesWithoutSayingWhy` — every route, reads with no parameters and writes with no body, must not answer a bare `"Bad request."`. **This is what found instance 3**, in the service layer where the ArchUnit rule structurally cannot look.
- `PermissionSweepIT.noRouteFailsOnAnEmptyBody` — no route may answer `5xx` to a missing field. Catches instance 2 whatever raised it: a `requireNonNull`, an unboxed null, an `orElseThrow` with the wrong supplier.
- ✅ **`PermissionSweepIT.noDomainRuleRefusesAWellFormedBodyWithoutSayingWhy`** (added by Q1, 2026-08-03) — a curated table of bodies that **parse cleanly and are wrong only in the domain's terms**, each of which must be refused with its reason rather than a bare `"Bad request."`, a `5xx`, a `"Malformed request body"` — or an acceptance. **This is not a fourth guard of the same kind**, which is why it was worth adding: the three above all probe *absent* input, and this probes *present-and-wrong* input, the residual named in the next paragraph. It is what finally caught instance 4, and **it was proven by running it against the defect first and watching it fail.** ⚠️ **Every case creates nothing** — each acts on the seeded retail customer or on a record the class already makes, and each is refused. **When a rule is written that a caller can trip with a well-formed body, add a line to that table.**

**What none of them can see, so watch for it in review:** a *wrong but non-empty* value whose route is **not in that table** — an unparseable enum, an id of the right shape naming another party's record, a date range running backwards. Those reach the handler and are only as good as the message written for them. And **`Objects.requireNonNull` is not banned anywhere**, deliberately: it is correct on our own arguments (`ListResponse` uses it properly, and so do the response records) and no rule can tell a caller's omission from a programmer's, which is exactly the judgement a reviewer has to make.

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

⚠️ **This rule is about documents an EXTERNAL PARTY issues, and the carve-out is written here because
without it a future session would correctly refuse to build D4.** Novocore also creates documents
**nobody else issues** — manual journal entries, goods receipts, freight allocations, write-offs.
Those have no supplier and no Go, so **without a Novocore number they have no human-facing identifier
at all**; *"what is entry 412"* is a question about a manual journal entry, and today the only answer
is a database id.

**Those are INTERNAL REFERENCE NUMBERS, not statutory document numbers**, and the distinction is what
makes them cheap: **no legal sequence, no unbroken requirement, gaps do not matter.** Simple per-type
counters, **none of step 40's machinery**. Decided 2026-08-03 (U3); scope and the two open format
questions are in the roadmap under ᵈ⁴. **A sales or purchase document number is still captured, never
generated** — that half of D4 was already answered by this rule and needs nothing built.

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
routinely, so this is not an edge case.

⚠️ **CORRECTED 2026-08-03 (R1a). This paragraph used to say document types are seeded from the AADE
list and users may never author a row. That was wrong, and the owner's real Prosvasis Go
configuration is what disproved it.** There are **TWO LAYERS**, and collapsing them is the mistake:

- **`aade_invoice_type` — the statutory codification.** All 55 `InvoiceType` values from
  `SimpleTypes-v2.0.1.xsd`, with annex 8.1's group as a column. **Seed-only: activate, deactivate,
  describe; no `create`; Flyway owns row authorship.** The `VatExemptionReason` model applies **here
  and only here**.
- **`sales_document_type` / `purchase_document_type` — the business's own lists.** **USER-CREATABLE,
  full CRUD**, with a **nullable** FK to `aade_invoice_type`.

**The two facts that force the split:** Go's type numbers (`7001`, `2062`, …) are **Go's internal
ids**, so under rule 2 above they belong in the adapter's mapping table; and **six of the owner's
nineteen types have no AADE invoice type at all** — Προσφορά, Δελτίο Αποστολής, Παραγγελία and the
rest are **operational documents, not tax documents**. A model in which the AADE code *is* the row
can represent neither. **A type with no AADE code carries a null FK — never a sentinel row, never an
`"N/A"` code**, because inventing an AADE code is exactly what the seeding rule forbids.

⚠️ **The general lesson, which outlives the two tables:** *"seeded from the official list"* was a true
statement about **one** thing (the codification) generalised into a false one about **another** (the
business's document list). **When a rule says a list is not ours to author, check that the list it
names is the same list the code is about.**

**6. Known limitation, and it must stay visible.** Until a dispatch document exists (18b), **stock
figures are incomplete for every non-stock-moving sales document**, which is a routine share of real
sales. The document is recorded, the ledger posts, and stock is left untouched.

⚠️ **CORRECTED 2026-08-04 (R1b). This paragraph used to end "…and the document must sit in a
queryable *stock not yet moved* state so the gap is measurable rather than merely known." That is NOT
what was built, and the difference is a decision rather than an omission.** The behaviour is
**SILENT**: a document type whose `affectsStock` is false creates **no `stock_consumption` row at
all** — no pending row, no marker, no flag, no warning, nothing queryable. `stock_consumption`'s
source CHECK was deliberately **not** widened, because there is nothing new to record.

**The owner's decision, taken as a decision.** An indicator nobody acts on is a second thing to keep
true, and an earlier scope carried one which was removed on purpose. **Do not add one back on the
grounds that it would be helpful.** The limitation is real and stays recorded here; what changed is
that it is recorded *here* rather than represented in the data.

**6b. Channel comes from the SERIES. ✅ BUILT IN R1b, 2026-08-04.**
A sales invoice's channel is **not independently settable**. ΑΛΠW is the web series, so an invoice in
it is a web sale **by definition** rather than by someone remembering to tick a box — which means
**F5 has no channel field**. `NewSalesInvoice` has no `channel` component; it has a **mandatory
`seriesId`**, and the channel is read off the series.

⚠️ **The document type is mandatory THROUGH the series, and there is deliberately no
`documentTypeId`.** `sales_invoice` has `series_id` and **no `document_type_id` column**;
`sales_document_series.document_type_id` is `NOT NULL`, so naming a series names a type. Two
independently settable references could disagree about what kind of document a row is — the same
defect the channel rule exists to prevent. **A future reader looking for a document-type column will
not find one, and that is the design.**

⚠️ **`sales_invoice.series_id` is NULLABLE and the service is what requires it.** A deliberate
departure from A.7's *"a constraint the database holds cannot be bypassed"*: `NOT NULL` would mean
backfilling every pre-R1b invoice with a series nobody authored, which is the fabrication the empty
seed exists to prevent. **Whether migrated history carries a series is step 24's question** and is
not pre-empted. The reason is written at the column, in `V33`.

- ⚠️ `sales_document_series.channel` is **nullable, and null means "this series is not a sales
  channel"** — which the self-supply series genuinely are not, since the customer is the issuer.
- ⚠️ **There is NO channel column on the purchase series table at all.** Channel is where a *sale*
  came from and never applies to a purchase. A nullable column that could only ever be null invites
  someone to fill it, and a purchase series carrying `ECOMMERCE` would be storable, meaningless and
  indistinguishable from data. **Its absence is the decision, so a test asserts the absence** — "there
  is no route" and "the route silently does nothing" look identical to a caller.
- ✅ **`sales_invoice.channel` is `NOT NULL`, it was NOT relaxed, and R1b refuses instead.**
  Recording against a channel-less series raises `InvalidSalesInvoiceException` — 422 with a message
  naming self-supply, saying that the revenue leg has no candidate account, that which accounts carry
  each leg is an accountant's question, and that **R3** answers it. The refusal lives in
  `compute(...)`, which `record` and `preview` share, so an entry screen learns before the operator
  submits. **The constraint is what holds the question open; do not widen it.** R3 resolves both.
- ✅ **An inactive series, or an active series whose document type is inactive, is also refused** —
  the same shape as the existing "Product … is inactive" and "VAT class … is inactive" rules. This is
  the guard R1a's A.8 deliberately left unbuilt, on the grounds that nothing referenced a document
  type until something did.

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

### ⚠️ A live-leg checklist is DERIVED from the screens a step ships, never composed freehand

**R2's was composed freehand, and the reconciliation is what showed the cost.** The approved block
had ten rows; the checklist actually handed over had twelve items. **Four items had no row** — the
coherence rule, the seed-only convention, the purchase-series channel absence, and delivery methods
— **and three rows had no item**, for three unrelated reasons. One row was mapped onto the wrong
path entirely.

None of that was a gap in what was *built*. It was a gap in what the leg was asked to *look at*, and
the block was called "the live leg" while covering roughly two thirds of it.

**The practice: enumerate the screens and routes the step ships, and derive one row per thing a
browser can answer that a test cannot.** Then reconcile both directions afterwards — items with no
row, and rows with no item — because those are different failures and neither is visible from the
other side.

### Named convention: a seed-only screen states its own emptiness, and a test pins it

**Established in R2, 2026-08-04, on `/settings/aade-invoice-types` — the first screen over a
`StatutoryCodification`.** Such a list has read plus activate/deactivate/describe and **no create
path, on any installation, ever**: AADE authors the rows, Flyway writes them, and
`StatutoryCodificationRulesTest` already makes a `create` method a build failure on the backend.

**Three things, and the second is the one that gets left out:**

1. **No Add control.**
2. **A permanent line on the screen saying who authors the rows.** ⚠️ A list with no Add button and
   no explanation reads as a screen somebody has not finished — so the absence has to be *legible as
   a decision* rather than merely performed.
3. **An absence test asserting no create control renders**, for a **FULL-access** role, with the
   reason in the test's own name.

⚠️ **Do not confuse this with `frontend/README.md`'s fourth field state.** That one — *"not built
yet"* — also owes an absence test, and the two are **opposites**: one is a deferral somebody will
come back and build, this is a permanent prohibition. They look identical in a diff, which is exactly
why each says which it is in words.

📌 **The next instance is already known and is not built: `/api/vat-exemption-reasons`.** R1a gave it
the same three write operations and it has **no screen at all**, with nothing anywhere recording that
absence. When somebody builds it, it is the AADE screen's twin — copy all three points above.

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

### Novocore is the centre — and an initial load is not an adapter

**Decided 2026-08-03 (U3).** Product-related data — categories, brands, products — is **created in
Novocore**. **WooCommerce receives from Novocore, never the reverse.**

⚠️ **The trap is conflating the adapter with the one-time load, and doing so means building
bidirectional sync that is never needed again.** They have different lifetimes:

- **The adapter syncs Novocore → Woo, forever** (step 19).
- **The initial load runs Woo → Novocore ONCE and is then deleted.** It is a migration and has a
  migration's property — **one clean shot** — so it is throwaway code with careful verification, and
  it has its own roadmap row rather than living inside step 19.

⚠️ **Stock must not come from Woo.** Woo's stock numbers are a **projection with no cost attached**,
and Novocore needs opening **lots** — quantity *and* cost. Product data from Woo; **stock from Go or
from a physical count valued against purchase invoices**, which is a separate and probably harder
migration question.

⚠️ **After cutover Woo is read-only for product data, SCOPED.** Novocore owns the fields it manages
and overwrites them without asking; fields it does not manage — SEO text, image galleries, plugin
data — are left untouched. **That list must be explicit and written down at step 19; it does not
exist yet.** The alternative is discovering it when a product's images vanish.

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

### A sequencing decision changes the roadmap's ORDER, not a paragraph beside it

**When a sequencing decision is made, the roadmap's ORDER is changed to match it. Recording the
decision in prose beside a list still in the old order is not enough — the order IS the statement, and
a reader follows the list, not the footnote. This is the same failure as two records disagreeing: the
roadmap and the decision become two records of the sequence.**

**Why this rule exists, in two occurrences that were already paid for:**

- **`F5 is next` survived in four documents after the owner had decided otherwise.** The backend queue
  had been prioritised ahead of it in a design conversation, and every document a fresh session would
  read still said F5. That is already the worked example under *a decision reached in a design
  conversation*, and it is the same failure arriving through the sequence rather than through the
  decision: **the prose was eventually corrected and the list was not.**
- **The D-rows sat under `⚪ Placement TBD` while three of the same items carried product-brief
  question numbers elsewhere.** A row whose status field says *the placement is undecided* is a
  statement, and it went on being made after the placement was decided.

**The mechanical form, because "update the roadmap" is too vague to be followed:**

1. **Move the rows.** A table whose row order no longer means anything is worse than no order, because
   nothing announces that it stopped meaning something.
2. **Write each row's reasoning AT the row** — a footnote or a status cell — not only in a decisions
   list. A reader scanning for what is next does not read the decisions list.
3. ⚠️ **Do not promote a status as a side effect of moving a row.** Position and status are different
   claims: *"this comes next"* and *"this is scheduled"* are not the same sentence, and a ⚪ row that
   silently becomes 🟡 because it moved up is a decision nobody made. **Say the mismatch and propose
   it.**

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

### ⚠️ Rebuilding the app image is an unconditional precondition of handing a live leg to the owner

**Before asking the owner to open a browser against the running stack — at close-out or at any other
point — rebuild the app image. Always. Not "if it looks stale".**

```
cd docker && docker compose -f compose.yml -f compose.dev.yml build app \
                          && docker compose -f compose.yml -f compose.dev.yml up -d app
```

⚠️ **`build` and `up -d app` ONLY. Never `down -v`** — on this stack `-v` also destroys the
commissioned Google Drive refresh tokens and the Owner account, neither reproducible from
`docker/.env`. The command is already in `docker/README.md`; what was missing was the trigger.

**Why unconditional, and not the timestamp comparison it is tempting to write instead.** The obvious
cheaper check is *"is the image older than `HEAD`?"* — and it was the first thing proposed. It is
wrong for two reasons, both worth keeping:

- **It is a heuristic, not a fact.** An image created *after* `HEAD` was not necessarily built *from*
  `HEAD`. That holds for one developer on one branch and stops holding quietly the moment it does
  not — a rebuild from a dirty tree, a branch switch, a second machine. A check that is right until
  it silently is not is worse than no check, because it is trusted.
- **This occurrence produced a false FAILURE, and the same condition produces a false PASS.** A
  stale image made a working route look missing, which is loud and gets investigated. Reverse it: a
  new commit breaks something the old image did correctly, the browser leg passes against the old
  image, and **nothing ever prompts a second look at a pass.** That is the direction that ships.

**One command, no judgement call, no output to interpret.** That is the whole argument for making it
unconditional rather than conditional.

### Named anti-pattern, sibling: the thing that answered was not the thing under test

**⚠️ Found on 2026-08-03, the day after Q1 was committed.** The browser leg on Q1's new role
description route answered `404 "No static resource api/roles/3/description"` — Spring's message when
no handler matches and the request falls through to static resource resolution.

**Nothing was wrong with the code.** Registration, generated client and committed spec all agreed on
`PATCH /api/roles/{id}/description`; the only thing that disagreed was the **deployed artefact**. The
app image had been built 26 hours before the commit, and reading the compiled `RoleController` out of
that jar showed eight route templates and no description route — plus `core/web/Required`, the
*pre-Q1* placement, and `InventoryController.writeOff` with no `createWriteOff`. It contained none of
Q1.

**The structural cause, which is the part worth recording — it is not "somebody forgot".** The app
image serves **no frontend at all** (zero static assets in the jar). The browser loads from the
**Vite dev server**, which proxies `/api` through Caddy to the app container. So the two halves have
categorically different staleness behaviour: **the frontend recompiles from disk on every save; the
backend changes only when somebody rebuilds an image.** **A current screen calling a stale API is the
DEFAULT state of this stack after any backend commit**, not an unlucky one.

**This is a sibling of *a verification that answers its own request*, not a new species.** That one is
a check whose subject was **stubbed**; this is a check whose subject was **a different build**. Both
reduce to the same sentence — **the thing that answered was not the thing under test** — and in both
cases every individual observation was true. What neither makes visible on its own is the *identity*
of the thing answering.

⚠️ **An anonymous probe cannot tell the two apart on this surface.** `PATCH` to the real route and to
`/api/roles/3/definitely-not-a-route` **both answer 401**: Spring Security refuses before dispatch.
That is the same fact already recorded about `/v3/api-docs`, and it means "just curl it" is not a
substitute for the rebuild.

#### ⚠️ It recurred twice in R1a, and neither time involved a container. A Maven reactor is enough

**Found 2026-08-03, twenty minutes apart, while fixing one defect.** A serialisation bug was
correctly diagnosed and correctly fixed, and the rerun reported **the identical failure** — so the
obvious conclusion was that the diagnosis had been wrong. It had not been:

1. **`mvn -pl app` without `-am` answers from the previously installed jar.** The fix was in
   `core-api`, which was never rebuilt.
2. Then `mvn install` **aborted at a test** that still referenced the removed method — so `core-api`
   reinstalled and **`core` did not**, and the app ran the new API against the old implementation and
   answered `500` from a `NoSuchMethodError` that looked exactly like the original bug.

**Both times the answer was already on screen and in the build's own exit status**, not in the test
result: `INSTALL=1`. ⚠️ **This is why the piped-build rule and this one are the same rule.** A build
whose failure you did not look at will hand you a stale artefact and let the next command explain it
to you in the vocabulary of the thing you were investigating.

**The practical form: when a fix "does not work", check that the fix was BUILT before rethinking the
fix.** `-pl X` without `-am`, and any `install` that did not reach every module, are the two ways
this repository produces it.

#### ⚠️ And a fifth way, found in R1b: `failsafe:verify` reads reports it did not write

**2026-08-04.** After a deliberate defect run left one failing test, the next command —
`mvn -pl app -am verify -Dit.test='TradingQuarterOverHttpIT'` — reported **`There are test failures`
in `novocore-core`**. No core test had run: the selector matches nothing there, so failsafe wrote no
reports. **`failsafe:verify` is a separate goal from `failsafe:integration-test`, and it fails on
whatever is sitting in `target/failsafe-reports` — including the previous run's.**

**So a targeted rerun after any failing run reports the OLD failure**, attributed to the module you
did not touch, in a command that never executed it. It is the stale-artefact family's shape exactly:
the thing that answered was not the thing under test.

**The mechanism, measured 2026-08-04:** failsafe writes `target/failsafe-reports/failsafe-summary.xml`,
and `verify` reads it. When `integration-test` matched nothing it **did not rewrite** the summary — so
`verify` read the previous run's, which recorded a failure.

⚠️ **There is no configuration parameter for this — none exists that makes `verify` distrust a summary
it did not write.** But **the `failIfNoSpecifiedTests` pin above closes the path that produced it**,
and that was verified rather than assumed: with a failing summary deliberately planted in `core`, the
same command now fails at **`integration-test`, naming the real problem** ("No tests matching
pattern"), before `verify` ever reads the stale file. The phantom *"There are test failures in
novocore-core"* no longer appears.

📌 **The residual, stated because it is what is left:** invoking `mvn failsafe:verify` as a **bare
goal** still reads a stale summary and still reports the old failure. That needs somebody to run the
goal on its own, which nothing here does. **For that case only, `clean` first** — it is discipline,
not configuration, and it is a much smaller surface than what the pin removed.

### ⚠️ A record that goes on the wire is asked for every BEAN GETTER — and the contract must say so

**Found in R1a, 2026-08-03, and the 500 it produced was luck. Generalised and CORRECTED by W1,
2026-08-04.**

`AadeInvoiceTypeView` — a response record — carried a one-line derived accessor `issuedByUs()`
delegating to an enum method that **throws** for the six codes that are neither issued nor received.
The exception is correct: asking a payroll adjusting entry which party issued it *is* a programming
error. **Putting a caller for it on a serialised record is what was wrong.**

`GET /api/aade-invoice-types` answered **`500 "Failed to write request"`** for the whole
codification. Every service-layer test passed — one of them asserted the throw and called it correct.

⚠️ **The louder half is what the 500 concealed by being loud.** `OpenApiSchema` described **record
components**, so the committed spec documented five properties while Jackson wrote six.
**A derived accessor that merely returns a value ships an undocumented field on every response**,
absent from the generated TypeScript, with nothing anywhere to say so. The throw is the only reason
the disagreement was visible at all — and W1 measured the silent version: **32 schemas on the
committed surface were writing 66 properties the document did not mention.**

#### ⚠️ This entry USED TO SAY "Jackson serialises a record's no-arg public accessors". That is false

**Measured 2026-08-04 (W1 Phase 0), by asking Jackson rather than by reading it.** Jackson publishes
**bean getters** — `isXxx()` returning `boolean`, `getXxx()` — **plus record components.** A plain
`normalBalance()`, `hasVariance()`, `netExactly()`, `totalDebits()` or `bornSettled()` is published by
**nothing**. On this surface: **222** non-component public no-arg accessors exist, **79** are named
`is*`, and Jackson publishes **66**. **153 are invisible to it.**

**The proof is a name nobody would derive by reading:** `issuedByUs()` is published as
**`suedByUs`** — the `is` prefix is *stripped*, because Jackson sees `is` + `suedByUs`. A control
record carrying both `issuedByUs()` and `label()` published the first and not the second.

⚠️ **`…IfAny()` helpers were recorded here as safe "for the same reason this was not: they cannot
throw." That reason is wrong, and the right one is much stronger and much more general:** they return
`Optional`, so **Jackson never publishes them at all** — and neither does it publish any of the other
149 non-bean-getter accessors. The old wording told a reader that a safe pattern was dangerous.

**The 79 − 66 = 13 residual is fully attributed rather than waved at:** **11** live on `Money`,
`Quantity`, `Rate` and `UnitCost`, whose serialisers `NovoCoreJsonModule` replaces so Jackson never
bean-introspects them; **2** are `ProductView.isSerialTracked()` and `isBundle()`, whose published
names are *already record components* and which delegate to them, so they cannot disagree.

#### ✅ The rule, built in W1: what Jackson writes must equal what the contract documents

**Two honest ways to comply — delete the accessor, or document it.** `SerialisedRecordContractIT`
(app module, against the **real Boot-configured mapper bean**) enforces it, reading the **committed**
`openapi.json` on one side and asking **Jackson** on the other, so a generator bug cannot make both
sides agree.

⚠️ **A REQUEST record is treated differently, and this is ONE rule rather than two behaviours. Do not
collapse it.** The rule is *describe what Jackson actually does with this record*, and Jackson does
two different things: it **serialises** a response, asking every bean getter; it **deserialises** a
request through the canonical constructor, which sees exactly the record components — **a request
record is never serialised at all.** So a derived property on `NewSalesInvoiceLine` would describe a
write that never happens, and a generated client would be told to compute and send a value the server
discards. A future reader who notices only that `SalesInvoiceLineView` documents `exempt` while
`NewSalesInvoiceLine` does not, and "simplifies" the two together, is documenting a serialisation
that does not occur.

⚠️ **A record reached from BOTH directions cannot be described either way**, so the rule refuses it —
and **pins the both-directions set non-empty as a positive control**, because "no offenders" and
"this test measured nothing" produce the same output otherwise. Same shape as
`DocumentReferenceGraphIT`.

⚠️ **The type comes from Jackson's visitor, never from a reflective lookup by name.** That was the
first implementation and it made the generator **non-deterministic**: `CustomerView` has both
`isSystemRecord():boolean` and `systemRecord():Optional<CustomerSystemKey>`, which map to one
published name, and a name-based lookup picks between them in `Class.getMethods()` order — which the
JVM does not specify. Two runs produced two documents; the spec drift check caught it.

#### ⚠️ The general rule does NOT subsume `AadeInvoiceTypeIT.theViewHasNoDerivedAccessorThatCanThrow`

**Keep both, and this is the reason.** That test is narrower *and stricter*: it forbids **any**
non-component no-arg public accessor on one view. The general rule only requires that whatever
Jackson publishes is documented — so under W1, **a throwing bean getter is now a documented property
and still answers `500` on every row.** Documenting a field is not the same as the field working.
The general rule can see a contract mismatch; it cannot see an exception. **A test that looks
redundant against a newer, wider rule is worth checking against this case before deleting it.**

📌 **One consequence, so nobody keeps paying a price that no longer exists.** R2's X.6 chose a
*component* (`inUse`) over a derived accessor specifically to avoid becoming the 67th undocumented
property. **That reason expired on 2026-08-04.** A derived accessor on a response record is now an
ordinary, documented part of its schema. Choose a component or an accessor on the merits.

### ⚠️ Two enforcements of one rule that agree by construction will diverge the day the construction changes

**Found in R1b, 2026-08-04, by a test written to document the new behaviour rather than to hunt a
bug — which is the only reason it was found at all.**

Document-number uniqueness is enforced **twice, deliberately**: by a database trigger and partial
unique index, and by `SalesInvoiceRepository.existsStandingInvoice` in the service, so the refusal
explains itself instead of arriving as a constraint name. That duplication is correct and is written
down as such.

**R1a changed one of them and not the other, and nothing could have noticed.** `V32` made the
database key `(COALESCE(series_id, -1), upper(document_number))` — because ΑΛΠ-1 and ΤΠΔΑ-1 are two
different documents that both legitimately carry the number 1 — while the service query kept checking
the number **globally**. ⭐ **The two still agreed perfectly**, because every row's `series_id` was
null, so every row sat in one group. R1a's own migration says exactly this and treats it as the proof
the change was safe: *"With every row's series NULL — which is every row today — the index is EXACTLY
today's global index."* **That sentence is true, and it is also the reason the divergence was
invisible.**

**The moment R1b gave an invoice a series, the database allowed the second document and the service
refused it.** The per-series key R1a paid a whole sub-part (C.6) to get right would have been
**unreachable** — enforced by nothing, contradicted by the layer above, and with a green suite.

**The general rule: when a schema change is justified by "this is byte-for-byte the current behaviour
because of what the data happens to look like today", that justification is also a list of the places
that will silently disagree once the data stops looking like that.** Write the list down at the time.
The tell is any argument of the form *"identical for every existing row"* — it is a statement about
data, not about code, and code is what will be wrong later.

⚠️ **It is not enough to fix the query.** The fix has to encode the *same* null semantics the database
uses: `COALESCE(series_id, -1)` and the trigger's `IS NOT DISTINCT FROM` both make two nulls collide,
and a naïve `existing.seriesId = :seriesId` would make them **not** collide — silently dropping the
guarantee for every invoice recorded before R1b, which is precisely the trap C.6 documents for the
index and which repeats one layer up.

#### ✅ The remedy, applied deliberately in R2 — write the list down *as a test*, not as a paragraph

**R2 met this shape again, knowingly, and it is the worked example of what the rule above asks for.**
The step added an *editable-while-unused, frozen-once-used* rule to three records. On the sales series
the predicate is real (`EXISTS (SELECT 1 FROM sales_invoice WHERE series_id = :id)`). On the other
two it is **`false` by construction**, and measured rather than assumed on 2026-08-04: the only
foreign key referencing `purchase_document_series` is its own transformation target, and **nothing
whatsoever references `delivery_method`**. So two of the three guards cannot fire, and would start
being able to — silently — the day F6 gives a purchase document a series and 18b gives a dispatch
document a delivery method.

**`DocumentReferenceGraphIT` is the list, in a form that fails a build.** It reads `pg_constraint`
after every migration has run and pins the referencing set of all three tables. When F6 adds the
column, that test goes red and its failure message names the field, the method and what to do.

**Three things about it are worth copying, and the third is the one that is easy to skip:**

- **It asks the catalogue, not the migrations.** A grep over `V*.sql` answers what the migrations
  *say*; this asks what the database *has* — the distinction under *a fact established by reading*.
- **The failure message is the handover note**, not `expected [] to equal [...]`. It says which
  method still returns a hard-coded `false` and which test to extend.
- ⚠️ **It carries a POSITIVE control**: `sales_document_series` must come back with exactly two
  references. Without it, both "nothing references this" assertions would pass just as happily
  against a typo in the SQL, a wrong catalogue column, or a connection to an empty schema — an empty
  result would mean *"this test measures nothing"* and would read as *"nothing references it"*. That
  is the negative-control requirement, arriving from the direction where the expected answer is
  emptiness.

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
