package gr.novotrade.novocore.core.api.email;

/**
 * Where a queued message has got to.
 *
 * <p>Three states, and the third one is the point: a message that has exhausted its retries is
 * {@link #FAILED} and stays in the outbox, queryable, rather than being deleted or silently
 * left {@link #PENDING} forever. {@code CLAUDE.md} rule 8's "fail loudly, never silently drop"
 * applies to our own outbound side as much as to an adapter reading someone else's API.
 */
public enum EmailStatus {

    /** Waiting for its next attempt. Carries the instant that attempt becomes due. */
    PENDING,

    /** Accepted by the SMTP server. Says nothing about delivery to the recipient's mailbox. */
    SENT,

    /**
     * Given up on — either the attempt limit was reached, or the failure was one that retrying
     * cannot fix (a malformed recipient address). Requires a human to call
     * {@link EmailSender#retry(long)}; nothing re-queues it automatically, because whatever was
     * wrong is still wrong until somebody changes something.
     */
    FAILED
}
