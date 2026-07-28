package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.email.EmailConfigurationStatus;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailNotConfiguredException;
import gr.novotrade.novocore.core.api.email.EmailSender;
import gr.novotrade.novocore.core.api.email.EmailStatus;
import gr.novotrade.novocore.core.api.email.QueuedEmailView;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
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
    private final SettingsService settings;
    private final AuditLogService auditLog;
    private final Clock clock;

    EmailSenderImpl(QueuedEmailRepository repository, SettingsService settings,
            AuditLogService auditLog, Clock clock) {
        this.repository = repository;
        this.settings = settings;
        this.auditLog = auditLog;
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
        QueuedEmail queued = repository.save(
                new QueuedEmail(message, maxAttempts(), Instant.now(clock)));

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
    @Transactional
    public QueuedEmailView retry(long queuedEmailId) {
        QueuedEmail message = repository.findById(queuedEmailId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No queued email with id " + queuedEmailId));

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
