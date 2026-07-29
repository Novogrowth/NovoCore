package gr.novotrade.novocore.core.api.email;

import java.util.List;
import java.util.Optional;

/**
 * Sending email — the shared core service {@code CLAUDE.md} names, configured once through
 * Settings and reached only through here.
 *
 * <p>Every feature that needs to send something calls this: Purchase Order PDFs, Reports, the
 * Accountant Monthly Package, Back-in-Stock Reminders. <strong>Nothing else may configure SMTP
 * or construct a mail session.</strong> That is not a convention — an ArchUnit rule confines
 * {@code jakarta.mail} and {@code org.springframework.mail} to this service's own package, so a
 * module growing its own SMTP client is a build failure rather than a discovery.
 *
 * <h2>Sending is asynchronous, and that is required rather than convenient</h2>
 *
 * <p>{@link #send} writes the message to an outbox table in the caller's transaction and
 * returns. It never opens a socket. {@code CLAUDE.md} rule 4 forbids a core operation waiting on
 * an outbound call, and an SMTP conversation is the clearest case of why: {@code mail.novotrade.gr}
 * being slow must not make approving a Purchase Order slow, and it must certainly not roll one
 * back.
 *
 * <p>Because the outbox row is written in the caller's transaction, a message is queued
 * <em>if and only if</em> the operation that queued it committed. A rolled-back Purchase Order
 * sends no PDF, with no compensating logic anywhere — which is the whole reason the outbox is a
 * table rather than an in-memory queue.
 *
 * <h2>An attachment that is already a stored document is referenced, not copied</h2>
 *
 * <p>{@link EmailAttachment#stored(long)} names an {@code AttachmentService} record; the outbox
 * keeps the reference and the document's identity, never a second copy of its bytes. So emailing
 * an invoice PDF that is also attached to the invoice stores that file once. Files that exist
 * nowhere else — a generated Purchase Order, a report — are still carried inline, because there
 * is nothing for them to reference.
 *
 * <p>The distinction is invisible at the point of use. {@link #attachmentsOf} and
 * {@link #downloadAttachment} behave identically for both shapes, so reading a sent message's
 * attachment is one call against the outbox attachment's own id, with no separate lookup and no
 * need to know where the file is kept.
 *
 * <h2>The sender identity is not the caller's to choose</h2>
 *
 * <p>{@link EmailMessage} has no From and no Reply-To. Both are configuration: every message
 * goes out from the configured sender address and carries the configured Reply-To header, which
 * exists because the sending mailbox is unmonitored. A caller that could override either would
 * be able to send replies somewhere nobody reads, which is the failure the setting exists to
 * prevent.
 */
public interface EmailSender {

    /**
     * Queues a message and returns immediately.
     *
     * <p>Named {@code send} rather than {@code queue} because that is what the caller means, and
     * because {@code CLAUDE.md} names the interface this shape. What it guarantees is that the
     * message will be attempted, retried on transient failure, and left visibly
     * {@link EmailStatus#FAILED} rather than dropped if it cannot be delivered — not that a
     * server has accepted it by the time this returns.
     *
     * @return the outbox id, so a caller that wants to can check on it later
     * @throws IllegalArgumentException if the message is malformed — validated by
     *     {@link EmailMessage} itself, so this happens at construction rather than here — or if a
     *     {@link EmailAttachment#stored(long)} attachment names a document that does not exist.
     *     Checked here, in the caller's own transaction, because an id naming nothing is a
     *     mistake in the calling code and refusing it now fails the operation that made it.
     */
    long send(EmailMessage message);

    /** One outbox entry, whatever state it is in. */
    Optional<QueuedEmailView> find(long queuedEmailId);

    /**
     * The attachments on one message, in the order they were sent.
     *
     * <p>The same answer for a referenced document and an inline file: name, type, size, and
     * whether the bytes can still be produced. An entry whose file has since been deleted or
     * pruned reports {@code available() == false} with a reason, rather than disappearing from
     * the list or failing the call — the message really did go out with that file on it, and the
     * history should keep saying so.
     *
     * @throws IllegalArgumentException if no such message exists. An empty list means the message
     *     had no attachments, which is a different fact.
     */
    List<SentEmailAttachmentView> attachmentsOf(long queuedEmailId);

    /**
     * Opens one attachment from a sent message, by the id {@link #attachmentsOf} gives.
     *
     * <p>One call, for either shape — a referenced document is resolved through
     * {@code AttachmentService} here rather than by the caller. Viewing what was sent is
     * therefore a single action from the sent-email record, and stays one if a file that is
     * inline today becomes a stored document tomorrow.
     *
     * <h2>⚠️ Whoever wires this to HTTP must add the permission check first</h2>
     *
     * <p><strong>Decided, not yet built</strong> (Q44, ADR 0012), because there is no route to the
     * outbox at all today and a permission guarding nothing is a half-built feature. It is written
     * here so it is a requirement being implemented rather than a gap being discovered:
     *
     * <p>For a <em>referenced</em> attachment this must re-check the caller's permission against
     * the core record the document belongs to — {@code RoleView.requireView(Section...)} and
     * {@code RoleView.canSee(ProtectedField)}, the primitives
     * {@code ProductView.redactedFor(RoleView)} already composes. <strong>An email having been
     * sent to someone does not change who may see the source document afterward</strong>, and the
     * outbox must not become a second, weaker access path to restricted data: without the check, a
     * role that cannot open a purchase invoice could read its PDF out of the email that sent it.
     *
     * <p>The obligation is a direct consequence of referencing. While the outbox held its own copy
     * of the bytes, the attachment was arguably the message's own business; now it is a pointer
     * into a document with its own visibility rules. An <em>inline</em> attachment has no core
     * record behind it and so no record-level permission to consult — it is governed by whatever
     * {@code Section} the outbox itself is eventually given.
     *
     * @throws IllegalArgumentException if no such attachment exists
     * @throws EmailAttachmentUnavailableException if it exists but its bytes are gone — the
     *     referenced document was deleted, or an inline copy was pruned. Deliberately not the
     *     same exception as an unknown id, and deliberately not an empty {@link Optional}: those
     *     would make a mistyped id and a deleted document indistinguishable.
     */
    EmailAttachmentContent downloadAttachment(long emailAttachmentId);

    /**
     * Messages that have given up, newest first — the list somebody actually has to look at.
     *
     * <p>This is a query over the outbox, not a second copy of it. Step 9 rejected a review
     * queue for rounding differences on the grounds that a queue is a second copy of state that
     * goes stale; the distinction here is that the outbox row <em>is</em> the state, and this
     * method is a filter over it.
     */
    List<QueuedEmailView> failed(int limit);

    /** Messages waiting for an attempt, oldest due first. */
    List<QueuedEmailView> pending(int limit);

    /**
     * Puts a failed message back in the queue for one more round of attempts.
     *
     * <p>Deliberately manual. Nothing re-queues a failed message on its own, because a message
     * that exhausted its retries failed for a reason that is still true — a wrong password, a
     * dead host, an address that does not exist. Automatic re-queueing would turn that into a
     * loop that hides the problem instead of surfacing it.
     *
     * @return the message's new state
     * @throws IllegalStateException if it is not {@link EmailStatus#FAILED}
     * @throws IllegalArgumentException if no such message exists
     */
    QueuedEmailView retry(long queuedEmailId);

    /**
     * Reports the current configuration and, if it is complete, opens a connection to the SMTP
     * server and authenticates — without sending anything.
     *
     * <p>For a Settings screen, and for answering "is email working?" honestly. A configuration
     * that reads back correctly and cannot log in is the common case, and only a real connection
     * distinguishes the two.
     */
    EmailConfigurationStatus verifyConfiguration();
}
