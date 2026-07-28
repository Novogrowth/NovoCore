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
            message.attemptStarting(now, sentFrom, replyTo,
                    retryPolicy.delayAfterAttempt(message.getAttempts() + 1));
            repository.save(message);

            // A row that cannot be turned back into a message is failed here and skipped,
            // rather than being allowed to throw.
            //
            // This is not theoretical, and it is not only about bad data. EmailMessage
            // validates what it is given, so a row that got into the table another way — a psql
            // session, a restore from an older schema, a future column this code does not
            // understand — throws on the way out. Letting that escape would abort the whole
            // claim transaction, so nothing in the batch would be sent, and the same batch
            // would be retried on the next cycle and fail identically: one poison row stopping
            // all email in the system, indefinitely, with no message of its own marked failed.
            // Found by a test whose raw-SQL probe left exactly such a row behind.
            try {
                claimed.add(new ClaimedEmail(message.getId(), toMessage(message),
                        message.getAttempts(), message.getMaxAttempts()));
            } catch (RuntimeException e) {
                String error = "This message cannot be sent as stored: "
                        + EmailSenderImpl.describe(e);
                log.error("Email {} is unusable and has been marked FAILED: {}",
                        message.getId(), error);
                message.markAttemptFailed(error, true);
                repository.save(message);
                auditLog.record("email.failed", ENTITY_TYPE, String.valueOf(message.getId()),
                        Map.of("permanent", "true", "error", error));
            }
        }
        return List.copyOf(claimed);
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
