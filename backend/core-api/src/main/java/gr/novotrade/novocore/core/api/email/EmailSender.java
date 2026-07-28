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
     *     {@link EmailMessage} itself, so this happens at construction rather than here
     */
    long send(EmailMessage message);

    /** One outbox entry, whatever state it is in. */
    Optional<QueuedEmailView> find(long queuedEmailId);

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
