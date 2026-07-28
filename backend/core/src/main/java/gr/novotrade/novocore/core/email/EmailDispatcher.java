package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailBodyFormat;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailNotConfiguredException;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.email.EmailOutbox.ClaimedEmail;
import jakarta.mail.Address;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends what is in the outbox. The one place in NovoCore that talks SMTP.
 *
 * <p>Runs on a schedule rather than being triggered when a message is queued, and the reason is
 * {@code CLAUDE.md} rule 4: a trigger would have to fire either inside the caller's transaction
 * — making the core operation wait on the network after all — or after commit, which needs a
 * second mechanism that then has to handle everything this poller already handles. A poller on a
 * short interval costs one indexed query per cycle and needs no such special case.
 *
 * <p><strong>Scheduling itself is enabled in {@code app}</strong>, not here. So the core's own
 * integration tests hold a fully wired dispatcher that never fires on its own, and drive it by
 * calling {@link #dispatchDue()} — which makes the retry and backoff behaviour testable by
 * assertion rather than by sleeping.
 */
@Component
class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private static final int DEFAULT_BATCH_SIZE = 20;

    private final EmailOutbox outbox;
    private final SettingsService settings;
    private final Clock clock;

    /**
     * The last configuration problem reported, so an unconfigured or broken setup is logged at
     * WARN when it changes and at DEBUG thereafter. Without this, a system waiting for its SMTP
     * password writes a warning every fifteen seconds until somebody supplies it, and the log
     * becomes the thing nobody reads.
     */
    private volatile String lastReportedConfigurationProblem;

    EmailDispatcher(EmailOutbox outbox, SettingsService settings, Clock clock) {
        this.outbox = outbox;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * One dispatch cycle: claim what is due, send it, record what happened.
     *
     * <p>{@code fixedDelay}, not {@code fixedRate} — the next cycle starts after the previous one
     * finishes, so a batch that takes longer than the interval cannot have a second cycle start
     * on top of it and attempt the same messages.
     *
     * @return how many messages were sent successfully, for tests and for a log line
     */
    @Scheduled(
            fixedDelayString = "${novocore.email.dispatch-interval-ms:15000}",
            initialDelayString = "${novocore.email.dispatch-initial-delay-ms:20000}")
    int dispatchDue() {
        SmtpConfiguration configuration;
        try {
            configuration = SmtpConfiguration.readFrom(settings);
        } catch (EmailNotConfiguredException e) {
            reportConfigurationProblem(e.getMessage());
            // Nothing is claimed and no attempt is consumed. A message must not burn its five
            // retries against a configuration that was never filled in — the moment somebody
            // supplies the password, everything waiting goes out.
            return 0;
        }
        lastReportedConfigurationProblem = null;

        Instant now = Instant.now(clock);
        RetryPolicy retryPolicy = RetryPolicy.readFrom(settings);

        List<ClaimedEmail> claimed = outbox.claimDue(
                now, batchSize(), configuration.fromAddress(), configuration.replyTo(),
                retryPolicy);
        if (claimed.isEmpty()) {
            return 0;
        }

        JavaMailSenderImpl sender = configuration.toMailSender();
        int sent = 0;
        for (ClaimedEmail claimedEmail : claimed) {
            if (dispatch(sender, configuration, claimedEmail)) {
                sent++;
            }
        }
        log.info("Email dispatch cycle: {} of {} claimed message(s) sent.", sent, claimed.size());
        return sent;
    }

    private boolean dispatch(JavaMailSenderImpl sender, SmtpConfiguration configuration,
            ClaimedEmail claimed) {
        try {
            sender.send(compose(sender, configuration, claimed.message()));
            outbox.recordSent(claimed.id(), Instant.now(clock));
            return true;
        } catch (Exception e) {
            // Broad on purpose. Anything thrown while sending is a failed attempt, and a
            // dispatcher that let an unanticipated exception escape would abandon the rest of
            // the batch — leaving those messages claimed, their attempt counted, and their
            // failure unrecorded, which is the one outcome this design exists to prevent.
            outbox.recordFailure(claimed.id(), EmailSenderImpl.describe(e), isPermanent(e));
            return false;
        }
    }

    /**
     * Builds the MIME message.
     *
     * <p>From and Reply-To come from configuration and are applied here, to every message, with
     * no way for a caller to supply either — see {@link EmailMessage}. Reply-To is the load-bearing
     * one: {@code erp@novotrade.gr} is an unmonitored send-only mailbox, so without this header
     * every reply from every customer lands somewhere nobody opens.
     */
    private MimeMessage compose(JavaMailSenderImpl sender, SmtpConfiguration configuration,
            EmailMessage message) throws Exception {
        MimeMessage mime = sender.createMimeMessage();
        // multipart only when there is something to attach: a multipart/mixed message with one
        // part is legal but renders oddly in some clients, and this is the common case.
        MimeMessageHelper helper =
                new MimeMessageHelper(mime, message.hasAttachments(), "UTF-8");

        if (configuration.fromName() == null) {
            helper.setFrom(configuration.fromAddress());
        } else {
            helper.setFrom(configuration.fromAddress(), configuration.fromName());
        }
        helper.setReplyTo(configuration.replyTo());

        helper.setTo(message.to().toArray(String[]::new));
        if (!message.cc().isEmpty()) {
            helper.setCc(message.cc().toArray(String[]::new));
        }
        if (!message.bcc().isEmpty()) {
            helper.setBcc(message.bcc().toArray(String[]::new));
        }

        helper.setSubject(message.subject());
        helper.setText(message.body(), message.format() == EmailBodyFormat.HTML);

        for (EmailAttachment attachment : message.attachments()) {
            helper.addAttachment(
                    attachment.filename(),
                    new ByteArrayResource(attachment.content()),
                    attachment.contentType());
        }
        return mime;
    }

    /**
     * Whether retrying this failure is pointless.
     *
     * <p>Only two things qualify: an address the server rejected outright, and a message we could
     * not construct. Everything else — connection refused, timeout, authentication rejected — is
     * treated as transient, because all three are routinely caused by something outside that gets
     * fixed. Authentication in particular: a wrong password is not going to fix itself, but the
     * attempt limit surfaces it within minutes anyway, and treating it as permanent would mark a
     * whole backlog FAILED the moment a password expired, turning a transient outage into
     * dozens of messages needing individual attention.
     *
     * <p>The invalid-address test requires that <em>no</em> valid recipient remains. A message to
     * three people where one address is dead was still delivered to the other two, and giving up
     * on it would report a failure for mail that arrived.
     */
    private static boolean isPermanent(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SendFailedException failure) {
                Address[] invalid = failure.getInvalidAddresses();
                Address[] validUnsent = failure.getValidUnsentAddresses();
                return invalid != null && invalid.length > 0
                        && (validUnsent == null || validUnsent.length == 0);
            }
            if (cause instanceof AddressException
                    || cause instanceof MailParseException
                    || cause instanceof MailPreparationException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private int batchSize() {
        try {
            int configured = settings.requireInt(SettingKeys.EMAIL_DISPATCH_BATCH_SIZE);
            if (configured >= 1) {
                return configured;
            }
        } catch (RuntimeException e) {
            log.debug("Setting '{}' unreadable ({}); using {}.",
                    SettingKeys.EMAIL_DISPATCH_BATCH_SIZE, e.getMessage(), DEFAULT_BATCH_SIZE);
        }
        return DEFAULT_BATCH_SIZE;
    }

    private void reportConfigurationProblem(String problem) {
        if (problem.equals(lastReportedConfigurationProblem)) {
            log.debug("Email still not configured: {}", problem);
            return;
        }
        lastReportedConfigurationProblem = problem;
        long waiting = outbox.countPending();
        log.warn("Email cannot be sent: {} — {} message(s) waiting in the outbox. "
                + "They will go out once this is corrected; no attempts are being consumed.",
                problem, waiting);
    }
}
