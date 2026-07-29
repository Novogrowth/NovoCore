package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies Q43: outbox rows are kept forever, the inline copies of generated attachments for 90
 * days.
 *
 * <p>The state this produces was already built and tested in V21 — an inline attachment whose
 * {@code content} is null reports itself as no longer available, naming the file and saying why.
 * What was missing was the thing that produces it on a schedule, with the guards that decide
 * <em>which</em> rows are safe to touch. Those guards are the substance here; the {@code UPDATE}
 * itself is one statement.
 *
 * <p><strong>Scheduling is enabled in {@code app}</strong>, not here, exactly as
 * {@link EmailDispatcher} is: the core's own tests hold a fully wired bean that never fires on its
 * own and drive it by calling {@link #pruneNow()}, so retention is asserted rather than waited for.
 *
 * <p>Runs daily rather than hourly. Nothing here is urgent — a file kept 90 days and 14 hours has
 * cost nothing — and a bulk update over the largest table in the schema is not something to do on a
 * short timer.
 */
@Component
class EmailRetention {

    private static final Logger log = LoggerFactory.getLogger(EmailRetention.class);

    private static final String ENTITY_TYPE = "Email";

    private final QueuedEmailRepository repository;
    private final SettingsService settings;
    private final AuditLogService auditLog;
    private final Clock clock;

    EmailRetention(QueuedEmailRepository repository, SettingsService settings,
            AuditLogService auditLog, Clock clock) {
        this.repository = repository;
        this.settings = settings;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * One retention pass.
     *
     * <p>Attachments first, then rows, and the order matters when both are configured: pruning
     * rows first would delete messages whose inline copies the attachment pass would then not
     * count, making the reported figures disagree with what actually happened. Under Q43's answer
     * only the first half ever runs.
     *
     * @return what it removed, for a log line and for tests
     */
    @Scheduled(cron = "${novocore.email.retention-cron:0 30 3 * * *}")
    @Transactional
    public Pruned pruneNow() {
        EmailRetentionPolicy policy;
        try {
            policy = EmailRetentionPolicy.readFrom(settings);
        } catch (RuntimeException e) {
            // Deliberately refuses rather than falling back. Every other setting in this service
            // has a safe default; this one does not, because the failure mode of guessing is
            // deleting data nobody can get back. Loud and inert is the right combination.
            log.error("Email retention did not run: {}", e.getMessage());
            return Pruned.nothing();
        }

        Instant now = Instant.now(clock);

        int attachments = policy.inlineAttachmentCutoff(now)
                .map(repository::pruneInlineAttachmentsSentBefore)
                .orElse(0);
        int messages = policy.messageCutoff(now)
                .map(repository::deleteSentBefore)
                .orElse(0);

        Pruned pruned = new Pruned(attachments, messages);
        if (!pruned.isNothing()) {
            log.info("Email retention: dropped {} inline attachment copy/copies and removed {} "
                    + "sent message row(s).", attachments, messages);
            // Audited against the service rather than a single message, because that is what
            // happened — one policy decision affecting many rows. A per-row entry would be a
            // second copy of the outbox in the audit log, which is the shape step 11 already
            // rejected for message bodies.
            auditLog.record("email.pruned", ENTITY_TYPE, "retention", Map.of(
                    "inlineAttachmentsDropped", String.valueOf(attachments),
                    "messagesRemoved", String.valueOf(messages),
                    "inlineAttachmentRetention", describe(policy.inlineAttachmentAge()),
                    "messageRetention", describe(policy.messageAge())));
        }
        return pruned;
    }

    private static String describe(Optional<java.time.Duration> age) {
        return age.map(duration -> duration.toDays() + " days").orElse("FOREVER");
    }

    /**
     * @param inlineAttachmentsDropped copies whose bytes were removed; the history entries remain
     * @param messagesRemoved rows deleted outright, always 0 under Q43's answer
     */
    record Pruned(int inlineAttachmentsDropped, int messagesRemoved) {

        static Pruned nothing() {
            return new Pruned(0, 0);
        }

        boolean isNothing() {
            return inlineAttachmentsDropped == 0 && messagesRemoved == 0;
        }
    }
}
