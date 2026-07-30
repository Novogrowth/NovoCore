package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentOwnerType;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailAttachmentContent;
import gr.novotrade.novocore.core.api.email.EmailAttachmentNotFoundException;
import gr.novotrade.novocore.core.api.email.EmailConfigurationStatus;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailNotConfiguredException;
import gr.novotrade.novocore.core.api.email.EmailSender;
import gr.novotrade.novocore.core.api.email.EmailStatus;
import gr.novotrade.novocore.core.api.email.QueuedEmailNotFoundException;
import gr.novotrade.novocore.core.api.email.QueuedEmailView;
import gr.novotrade.novocore.core.api.email.SentEmailAttachmentView;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EmailSenderImpl implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderImpl.class);

    private static final String ENTITY_TYPE = "Email";

    /**
     * Used when {@code email.max-attempts} is missing or unreadable.
     *
     * <p>The one place in this service that falls back rather than refusing, and the reason is
     * specific: queueing happens inside the caller's transaction, so throwing here would fail the
     * Purchase Order rather than the email. A business operation must not be lost because a retry
     * count was deleted from a settings table. The fallback is logged at WARN each time, so it is
     * loud without being fatal.
     */
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final QueuedEmailRepository repository;
    private final QueuedEmailAttachmentRepository attachmentRepository;
    private final SettingsService settings;
    private final AuditLogService auditLog;
    private final AttachmentService documents;
    private final Clock clock;

    EmailSenderImpl(QueuedEmailRepository repository,
            QueuedEmailAttachmentRepository attachmentRepository, SettingsService settings,
            AuditLogService auditLog, AttachmentService documents, Clock clock) {
        this.repository = repository;
        this.attachmentRepository = attachmentRepository;
        this.settings = settings;
        this.auditLog = auditLog;
        this.documents = documents;
        this.clock = clock;
    }

    @Override
    @Transactional
    public long send(EmailMessage message) {
        Objects.requireNonNull(message, "message");

        // Deliberately does NOT check that SMTP is configured. Queueing an operation's
        // notification must not fail because a mail setting is wrong — the message waits and
        // goes out when the configuration is fixed, which is strictly better than the operation
        // being rolled back and the notification never existing at all.
        //
        // A referenced document IS checked here, and the difference is worth stating: an SMTP
        // setting being wrong is an infrastructure state that somebody will fix, while an
        // attachment id naming nothing is a mistake in the code that just called this. Refusing
        // it now fails the operation that made the mistake, at the point it was made.
        QueuedEmail queued = repository.save(new QueuedEmail(
                message, maxAttempts(), Instant.now(clock), storedDocumentsIn(message)));

        // Recipients and subject, never the body. An audit log holding the text of every
        // customer email is a second copy of that correspondence in a place chosen for
        // retention, not for confidentiality.
        auditLog.record("email.queued", ENTITY_TYPE, String.valueOf(queued.getId()), Map.of(
                "to", String.join(", ", message.to()),
                "subject", message.subject(),
                "attachments", String.valueOf(message.attachments().size())));

        return queued.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QueuedEmailView> find(long queuedEmailId) {
        return repository.findById(queuedEmailId).map(QueuedEmail::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueuedEmailView> failed(int limit) {
        return repository
                .findByStatusOrderByCreatedAtDesc(EmailStatus.FAILED, Limit.of(limit))
                .stream()
                .map(QueuedEmail::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueuedEmailView> pending(int limit) {
        return repository
                .findByStatusOrderByNextAttemptAtAscIdAsc(EmailStatus.PENDING, Limit.of(limit))
                .stream()
                .map(QueuedEmail::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SentEmailAttachmentView> attachmentsOf(long queuedEmailId) {
        return repository.findById(queuedEmailId)
                .map(QueuedEmail::attachmentViews)
                // Not an empty list: that would say "this message had no attachments", which is a
                // different and perfectly ordinary fact.
                .orElseThrow(() -> new QueuedEmailNotFoundException(queuedEmailId));
    }

    @Override
    @Transactional(readOnly = true)
    public EmailAttachmentContent downloadAttachment(long emailAttachmentId, RoleView viewer) {
        Objects.requireNonNull(viewer, "viewer");

        QueuedEmailAttachment attachment = attachmentRepository.findById(emailAttachmentId)
                .orElseThrow(() -> new EmailAttachmentNotFoundException(emailAttachmentId));

        requireAccessToTheSourceRecord(attachment, viewer);

        // One call, one id, either shape. The reference is followed here rather than by the
        // caller, which is what keeps "view what was sent" a single action from the sent-email
        // record — and what stops a file that moves from inline to stored later from changing
        // how anything reads it.
        return attachment.resolveContent(documents);
    }

    /**
     * Q44's access-path check, applied before any bytes are produced.
     *
     * <p>An email having been sent to someone does not change who may see the source document
     * afterwards. So a <em>referenced</em> attachment is re-checked against the section governing
     * the record it belongs to — the outbox's own section, which the caller already holds, is not
     * enough on its own, or the outbox would be a second and weaker way in.
     *
     * <p>Two cases pass through without a record check, both correctly. An <em>inline</em>
     * attachment has no core record behind it; the outbox section is all there is to check. And a
     * referenced document that has since been <em>deleted</em> has no record either — there is
     * nothing to check against and no bytes to leak, so {@code resolveContent} reports it
     * unavailable, which is what the history should say.
     */
    private void requireAccessToTheSourceRecord(QueuedEmailAttachment attachment, RoleView viewer) {
        Long documentId = attachment.storedAttachmentId();
        if (documentId == null) {
            return;
        }
        documents.findMetadata(documentId).ifPresent(metadata ->
                AttachmentOwnerType.requireAccess(metadata.entityType(), viewer));
    }

    @Override
    @Transactional
    public QueuedEmailView retry(long queuedEmailId) {
        QueuedEmail message = repository.findById(queuedEmailId)
                .orElseThrow(() -> new QueuedEmailNotFoundException(queuedEmailId));

        message.requeue(Instant.now(clock), maxAttempts());
        repository.save(message);

        auditLog.record("email.retried", ENTITY_TYPE, String.valueOf(queuedEmailId), Map.of(
                "subject", message.getSubject(),
                "to", String.join(", ", message.getTo())));

        return message.toView();
    }

    @Override
    public EmailConfigurationStatus verifyConfiguration() {
        SmtpConfiguration configuration;
        try {
            configuration = SmtpConfiguration.readFrom(settings);
        } catch (EmailNotConfiguredException e) {
            return EmailConfigurationStatus.notConfigured(e.getMessage());
        }

        String problem = null;
        boolean reachable = false;
        try {
            configuration.toMailSender().testConnection();
            reachable = true;
        } catch (Exception e) {
            // Deliberately broad. testConnection throws MessagingException, but a wrong
            // transport-security setting surfaces as a socket or TLS failure instead, and the
            // point of this method is to answer "does email work?" rather than to classify.
            problem = describe(e);
        }

        return new EmailConfigurationStatus(
                true,
                reachable,
                configuration.host(),
                configuration.port(),
                configuration.transportSecurity(),
                configuration.username(),
                configuration.fromAddress(),
                configuration.replyTo(),
                problem);
    }

    /**
     * Reads the metadata for every referenced document in the message, once, before anything is
     * written — so a message naming a document that does not exist is refused whole rather than
     * queued with one bad attachment.
     *
     * <p>Metadata, never content: this must not pull a 20 MB PDF out of the database to establish
     * that it is there. The bytes are read once, by the dispatcher, at send time.
     */
    private Map<Long, AttachmentMetadata> storedDocumentsIn(EmailMessage message) {
        Map<Long, AttachmentMetadata> found = new HashMap<>();
        for (EmailAttachment attachment : message.attachments()) {
            if (!attachment.isStored()) {
                continue;
            }
            long documentId = attachment.storedAttachmentId();
            found.computeIfAbsent(documentId, id -> documents.findMetadata(id)
                    .orElseThrow(() -> new IllegalArgumentException(
                            ("Cannot attach document %d to this message: no such attachment. An "
                                    + "attachment id is resolved when the message is queued, so a "
                                    + "reference that names nothing fails here rather than hours "
                                    + "later in the outbox.").formatted(id))));
        }
        return found;
    }

    private int maxAttempts() {
        try {
            int configured = settings.requireInt(SettingKeys.EMAIL_MAX_ATTEMPTS);
            if (configured >= 1) {
                return configured;
            }
            log.warn("Setting '{}' is {}, which is not a usable attempt count; using {}.",
                    SettingKeys.EMAIL_MAX_ATTEMPTS, configured, DEFAULT_MAX_ATTEMPTS);
        } catch (RuntimeException e) {
            log.warn("Setting '{}' is missing or unreadable ({}); using {}.",
                    SettingKeys.EMAIL_MAX_ATTEMPTS, e.getMessage(), DEFAULT_MAX_ATTEMPTS);
        }
        return DEFAULT_MAX_ATTEMPTS;
    }

    /**
     * One line naming the exception type and its message, with the cause appended when the
     * outer message is the uninformative half — which, for mail failures, it usually is.
     */
    static String describe(Throwable error) {
        StringBuilder description = new StringBuilder()
                .append(error.getClass().getSimpleName())
                .append(": ")
                .append(error.getMessage());
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            description.append(" (caused by ")
                    .append(cause.getClass().getSimpleName())
                    .append(": ")
                    .append(cause.getMessage())
                    .append(')');
        }
        return description.toString();
    }
}
