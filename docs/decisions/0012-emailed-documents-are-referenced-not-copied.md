# ADR 0012 — An emailed document is referenced, not copied; a missing file degrades, never breaks

**Date:** 2026-07-29
**Status:** Accepted — implemented as a correction to build step 11 (migration V21)

Revises a decision step 11 made explicitly and got half right. Recorded as its own ADR rather than
edited into the step 11 notes because the original reasoning was written down and defended, and a
later reader deserves to see why it was overturned in one direction and kept in the other.

## What step 11 decided, and what was wrong with it

`EmailAttachment`'s javadoc said, in as many words, that it was "deliberately not
`AttachmentMetadata` plus an id", and gave two reasons:

> A module sending a Purchase Order PDF has just generated the bytes and has no reason to store
> them against a core record first, and a report emailed monthly is not a document anyone wants a
> permanent copy of on the invoice.

Both of those are true, and neither is a reason to make *every* attachment carry its own bytes. The
case they do not cover is the one that matters most for size: a document that is **already** an
`AttachmentService` record and is **also** emailed — a supplier invoice PDF, a report saved against
a period. V20 stored a second copy of that file in `email_outbox_attachment.content`, so the same
bytes sat in two tables, in every `pg_dump`, permanently.

The volume argument is not hypothetical. Reports, the Accountant Monthly Package and Purchase Order
PDFs are all named in the brief as things this service will send, and phase 8 turns that from
occasional into monthly-by-default.

The principle was already established one level up: `EmailSender` exists because email had to be one
door with no duplicate implementations. `AttachmentService` is the same claim about stored
documents. The outbox is not a second store.

## Decision

**Two shapes, one enforced choice.**

- `EmailAttachment.stored(attachmentId)` references an `AttachmentService` document and carries no
  bytes. The outbox row keeps the reference plus the document's identity snapshotted at queue time
  — filename, content type, size, SHA-256.
- `EmailAttachment.of(...)` / `.pdf(...)` carry bytes, for a file that exists nowhere else.

Enforced in the record's constructor and by CHECK constraints, so a row cannot be both shapes or
neither. Same arrangement as a journal line carrying a VAT class or an exemption reason.

**Reference-only was considered and rejected.** It would have meant storing every generated report
PDF through `AttachmentService` first, against a synthetic `Email`/`<outbox id>` entity. That moves
those bytes rather than saving them, and it fills a table whose entire definition is "documents on
core records" with things that are neither. The duplication being removed here is specifically the
*double* storage of one file; a file stored once is already stored once.

### The recipient is unaffected

Worth stating because it is the first thing anyone asks. SMTP transmits real bytes either way — the
dispatcher resolves a reference into content at send time, and `EmailDispatcher.compose` never sees
the distinction. This decision governs only what NovoCore keeps for itself.

### Resolution is late, and invisible at the point of use

The reference is followed at send time, not at queue time, so a single copy of the bytes exists
right up to the moment they are transmitted. On the read side,
`EmailSender.attachmentsOf(emailId)` and `EmailSender.downloadAttachment(emailAttachmentId)` behave
identically for both shapes: same id, same return type, no join to perform and nothing the caller
has to know about where the file is kept. **Viewing what was sent is one action from the sent-email
record**, and stays one if a file that is inline today becomes a stored document tomorrow.

The reference is *validated* at queue time, in the caller's transaction: an attachment id naming
nothing is a mistake in the calling code, and refusing it there fails the operation that made it.
This is deliberately unlike the SMTP configuration, which is **not** checked at queue time — a mail
setting being wrong is an infrastructure state somebody will fix, and the message should wait.

## What happens when the referenced document is deleted

**It is still deletable, and the email history degrades rather than breaking.** The foreign key is
`ON DELETE SET NULL`.

- `CASCADE` would delete the outbox's record that the message ever carried an attachment — history
  rewritten by an unrelated action.
- `RESTRICT` would let an email sent in 2026 pin a document forever. Every document ever attached to
  a message would become undeletable, which is a worse system than one whose history says "no
  longer available".

What remains is a history entry that still names the file, states its size and its checksum, and
reports it as unavailable with the reason. `SentEmailAttachmentView.available()` answers that
without a query — a non-null `attachment_id` is itself the proof the document is still there — so
listing a message's attachments never touches the attachment table. Asking for the bytes anyway
throws `EmailAttachmentUnavailableException`, which is deliberately **not** the same as the
`IllegalArgumentException` for an id that never existed: conflating a mistyped id with a deleted
document is exactly the ambiguity `CLAUDE.md` rule 7 refuses.

The checksum snapshot earns its keep here. `AttachmentServiceImpl.delete` already writes the
filename and checksum into the audit log before removing the row, so a deletion recorded there and a
history entry here can still be tied to each other after the bytes are gone.

**A document deleted between queueing and sending fails the message, visibly and alone.** It is
genuinely unsendable, so it is marked `FAILED` naming the file, through the same per-message guard
that isolates a poison row — a mail is never sent with an attachment silently missing, which is the
one failure mode a recipient could not possibly detect.

## Consequence for retention (Q43), which this splits in two

Before this change, "how long do we keep sent emails?" was one question with one answer covering
both cheap rows and expensive bytes. After it, the outbox's unbounded growth is **exactly the inline
attachments and nothing else**, so the two halves can take different numbers:

- **Outbox rows** — recipients, subject, status, error. Cheap; worth years.
- **Inline attachment bytes** — generated PDFs and reports, the files that were never meant to be
  permanent. Expensive; the real subject of a retention policy.

**Answered and built in V22, the day after this ADR:** rows `FOREVER`, inline copies **90 days**, both
as Settings. The prediction above held — it needed no schema change. `EmailRetention` runs the
`UPDATE` daily, restricted to `content_source = 'INLINE'` (a referenced document's bytes are not the
outbox's to delete) and to `status = 'SENT'` (a PENDING message still needs its bytes; a FAILED one
keeps them because retrying it is why it was kept). The state it leaves is the one this ADR already
built and tested — "no longer available", distinguished from a deleted document by `content_source`
so the history can say which happened.

`content_source` is stored rather than inferred from which columns are populated, for the reason
step 9 stores `vat_class_source`: once the bytes are gone, both shapes are a row with nothing in it.

## Consequence for Q44 — decided 2026-07-29, built with the outbox screen

`downloadAttachment` returns document bytes, so the outbox is a second route to a file whose core
record may itself be restricted. **Decided rather than deferred**, even though nothing needs building
until the frontend phase, so it arrives as a requirement being implemented and not a gap being found:

> **`downloadAttachment` must re-check the caller's permission against the underlying core record
> before returning bytes for a referenced attachment**, using `RoleView.requireView(Section...)` and
> `RoleView.canSee(ProtectedField)` — the primitives `ProductView.redactedFor(RoleView)` already
> composes. **An email having been sent to someone does not change who is allowed to see the source
> document afterward.** The outbox must not become a second, weaker access path to restricted data.

The obligation is created *by this ADR*. While the outbox held its own copy of the bytes, the
attachment was arguably the message's own business; a reference is a pointer into a document that
belongs to a core record with its own visibility rules. Removing the duplicated storage removed the
excuse for a duplicated access rule along with it — the same trade this ADR makes everywhere else.

Scope, so it is neither over- nor under-applied:

- **Referenced attachments only.** An inline generated PDF has no core record and therefore no
  record-level permission to consult; it falls under whatever `Section` the outbox itself gets.
- **Checked against the document's `entity_type` / `entity_id`**, reached through
  `AttachmentService.findMetadata`.
- **A deleted reference needs no check** — nothing remains to authorise, and the entry already
  reports itself unavailable.
- **`attachmentsOf` returns no bytes**; whether a filename is itself restricted is the `Section`
  question, which is the half of Q44 still open.

Recorded in three places on purpose: here, in `HISTORY.md`, and in `EmailSender.downloadAttachment`'s
own javadoc — the last being where whoever wires the screen will actually be looking.

## Status of the step 11 reasoning that was kept

The other half of `EmailAttachment`'s original javadoc stands unchanged and should not be
"cleaned up" later: an inline attachment is **stored rather than regenerated at send time**, because
a Purchase Order PDF is generated from data that can change between approval and dispatch, and a
retry three minutes later must send the document that was approved.
