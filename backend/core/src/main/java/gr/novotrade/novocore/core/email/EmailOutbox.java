package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outbox's transactional half — everything the dispatcher does to the database, kept apart
 * from everything it does to the network.
 *
 * <p>A separate bean rather than methods on {@link EmailDispatcher}, and not for tidiness: a
 * {@code @Transactional} method called from another method of the same object goes straight to
 * the object and never through the proxy, so the annotations would silently do nothing and the
 * whole claim/send/record sequence would run in one transaction — or in none. Splitting the bean
 * is what makes the boundaries real.
 *
 * <p>The boundary that matters: <strong>no transaction is open while SMTP is talking.</strong>
 * {@link #claimDue} commits before the dispatcher opens a socket, and {@link #recordSent} /
 * {@link #recordFailure} each open their own afterwards. A mail server that accepts a connection
 * and then stops responding therefore holds no database lock and blocks nothing else.
 */
@Component
class EmailOutbox {

    private static final Logger log = LoggerFactory.getLogger(EmailOutbox.class);

    private static final String ENTITY_TYPE = "Email";

    private final QueuedEmailRepository repository;
    private final AuditLogService auditLog;

    EmailOutbox(QueuedEmailRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    /**
     * Takes ownership of up to {@code batchSize} due messages and returns them as plain data.
     *
     * <p>Returns detached values rather than entities on purpose. The attachments are a lazy
     * association, so an entity read here and used after the transaction closes would fail on
     * first access; materialising inside the transaction states that requirement instead of
     * leaving it to be discovered by whoever adds the first attachment.
     */
    @Transactional
    List<ClaimedEmail> claimDue(Instant now, int batchSize, String sentFrom, String replyTo,
            RetryPolicy retryPolicy) {
        List<Long> due = repository.claimDueIds(now, batchSize);
        if (due.isEmpty()) {
            return List.of();
        }

        List<ClaimedEmail> claimed = new ArrayList<>(due.size());
        for (QueuedEmail message : repository.findAllById(due)) {
            // A row that cannot be sent as stored is failed here and skipped, rather than being
            // allowed to throw.
            //
            // This is not theoretical, and it is not only about bad data. EmailMessage validates
            // what it is given, so a row that got into the table another way — a psql session, a
            // restore, a future column this code does not understand — throws on the way out.
            // Letting that escape would abort the whole claim transaction, so nothing in the
            // batch would be sent, and the same batch would be retried on the next cycle and
            // fail identically: one poison row stopping all email in the system, indefinitely,
            // with no message of its own marked failed. Found by a test whose raw-SQL probe left
            // exactly such a row behind.
            //
            // The whole per-message body is inside the try, not just the conversion. An earlier
            // version guarded only toMessage(), which covered the reproduction and a useful
            // family around it — every validation failure in rebuilding the message — but left
            // the claim's own bookkeeping outside, where a throw would still take the batch down.
            try {
                claimed.add(claim(message, now, sentFrom, replyTo, retryPolicy));
            } catch (RuntimeException e) {
                failPermanently(message, e);
            }
        }
        return List.copyOf(claimed);
    }

    private ClaimedEmail claim(QueuedEmail message, Instant now, String sentFrom, String replyTo,
            RetryPolicy retryPolicy) {
        // Checked BEFORE the increment, and this guard is the reason the try above is not
        // enough on its own.
        //
        // attemptStarting increments attempts, and the schema has
        // `CHECK (attempts <= max_attempts)`. A row sitting at PENDING with attempts already
        // equal to max_attempts satisfies every constraint at rest — the service cannot create
        // one, since markAttemptFailed flips to FAILED at the limit and requeue resets to zero,
        // but raw SQL or a restore can. Claiming it would emit an UPDATE that violates the
        // CHECK, and that violation lands at *flush*, when the transaction commits: after the
        // loop, outside the try, and unrecoverable. It would roll back the entire claim
        // transaction including the markAttemptFailed writes the catch block had just made — the
        // same batch-wide stall, reached through a door the try cannot close, and this time not
        // even leaving a record of which row caused it.
        //
        // A failed flush also poisons the persistence context, so there is no per-message
        // recovery available. Prevention is the only reliable fix: never emit the invalid
        // UPDATE in the first place.
        if (message.getAttempts() >= message.getMaxAttempts()) {
            throw new IllegalStateException(
                    "already at its attempt limit (%d of %d) while still PENDING, which the "
                            .formatted(message.getAttempts(), message.getMaxAttempts())
                            + "service cannot produce — it was written directly to the table");
        }

        message.attemptStarting(now, sentFrom, replyTo,
                retryPolicy.delayAfterAttempt(message.getAttempts() + 1));
        repository.save(message);

        return new ClaimedEmail(message.getId(), toMessage(message),
                message.getAttempts(), message.getMaxAttempts());
    }

    private void failPermanently(QueuedEmail message, RuntimeException cause) {
        String error = "This message cannot be sent as stored: "
                + EmailSenderImpl.describe(cause);
        log.error("Email {} is unusable and has been marked FAILED: {}",
                message.getId(), error);
        message.markAttemptFailed(error, true);
        repository.save(message);
        auditLog.record("email.failed", ENTITY_TYPE, String.valueOf(message.getId()),
                Map.of("permanent", "true", "error", error));
    }

    @Transactional
    void recordSent(long id, Instant now) {
        QueuedEmail message = repository.findById(id).orElseThrow();
        message.markSent(now);
        repository.save(message);

        auditLog.record("email.sent", ENTITY_TYPE, String.valueOf(id), Map.of(
                "to", String.join(", ", message.getTo()),
                "subject", message.getSubject(),
                "attempts", String.valueOf(message.getAttempts())));
    }

    /**
     * Records a failed attempt, and says so at a level matching what it means: a message that
     * still has attempts left is a WARN, one that has given up is an ERROR. Both are audited,
     * because a notification that never reached a customer is a business fact and not only an
     * operational one.
     */
    @Transactional
    void recordFailure(long id, String error, boolean permanent) {
        QueuedEmail message = repository.findById(id).orElseThrow();
        message.markAttemptFailed(error, permanent);
        repository.save(message);

        boolean givenUp = message.getStatus() == EmailStatus.FAILED;
        if (givenUp) {
            log.error("Email {} to {} has FAILED after {} attempt(s) and needs attention: {}",
                    id, message.getTo(), message.getAttempts(), error);
        } else {
            log.warn("Email {} to {} failed on attempt {} of {}, will retry: {}",
                    id, message.getTo(), message.getAttempts(), message.getMaxAttempts(), error);
        }

        auditLog.record(givenUp ? "email.failed" : "email.attempt-failed",
                ENTITY_TYPE, String.valueOf(id), Map.of(
                        "to", String.join(", ", message.getTo()),
                        "subject", message.getSubject(),
                        "attempts", String.valueOf(message.getAttempts()),
                        "permanent", String.valueOf(permanent),
                        "error", error));
    }

    @Transactional(readOnly = true)
    long countPending() {
        return repository.countByStatus(EmailStatus.PENDING);
    }

    private static EmailMessage toMessage(QueuedEmail queued) {
        List<EmailAttachment> attachments = queued.getAttachments().stream()
                .map(attachment -> new EmailAttachment(
                        attachment.getFilename(),
                        attachment.getContentType(),
                        attachment.getContent()))
                .toList();

        return new EmailMessage(
                queued.getTo(),
                queued.getCc(),
                queued.getBcc(),
                queued.getSubject(),
                queued.getBody(),
                queued.getBodyFormat(),
                attachments);
    }

    /**
     * One message the dispatcher has taken responsibility for, with the attempt numbers it was
     * claimed under — so a log line can say "attempt 2 of 5" without going back to the database.
     */
    record ClaimedEmail(long id, EmailMessage message, int attempt, int maxAttempts) {
    }
}
