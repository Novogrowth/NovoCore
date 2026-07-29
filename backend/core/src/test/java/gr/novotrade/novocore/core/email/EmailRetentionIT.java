package gr.novotrade.novocore.core.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailAttachmentUnavailableException;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailSender;
import gr.novotrade.novocore.core.api.email.EmailStatus;
import gr.novotrade.novocore.core.api.email.SentEmailAttachmentView;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Q43 as built: rows forever, inline copies of generated attachments for 90 days.
 *
 * <p>The interesting assertions are the two <em>restrictions</em>, not the deletion. A prune that
 * removes too much is the failure mode that matters, and there are three ways to get it wrong: take
 * a referenced document's bytes (which belong to another service), take a PENDING message's bytes
 * (which it still needs), or take a FAILED message's bytes (which is the whole reason it was kept).
 * Each has a test.
 *
 * <p>Nothing here sleeps or waits 90 days. Ages are made by moving {@code sent_at} backwards, so
 * the boundary assertions are exact.
 */
class EmailRetentionIT extends AbstractCoreIntegrationTest {

    private static final List<String> OVERWRITTEN_SETTINGS = List.of(
            SettingKeys.EMAIL_RETENTION_MESSAGE_DAYS,
            SettingKeys.EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS);

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private EmailRetention retention;

    @Autowired
    private AttachmentService attachments;

    @Autowired
    private SettingsService settings;

    @Autowired
    private JdbcTemplate jdbc;

    private Map<String, String> settingsBeforeThisTest;

    @BeforeEach
    void rememberSettingsAndEmptyTheOutbox() {
        settingsBeforeThisTest = new HashMap<>();
        OVERWRITTEN_SETTINGS.forEach(
                key -> settingsBeforeThisTest.put(key, settings.find(key).orElse("")));
        jdbc.update("DELETE FROM email_outbox");
    }

    @AfterEach
    void restoreSettings() {
        settingsBeforeThisTest.forEach(settings::put);
    }

    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("V22 seeds the answers Q43 gave")
    void seededDefaultsAreTheAnswer() {
        assertThat(settings.find(SettingKeys.EMAIL_RETENTION_MESSAGE_DAYS))
                .as("a sent-email history is a business record, and since V21 it is cheap")
                .contains(SettingKeys.RETENTION_FOREVER);
        assertThat(settings.find(SettingKeys.EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS))
                .contains("90");
    }

    @Test
    @DisplayName("an inline copy older than 90 days is dropped; the history entry survives it")
    void inlineCopyIsDroppedAndTheEntryRemains() {
        long id = sentMessageWith(EmailAttachment.pdf(
                "po-2001.pdf", "%PDF-1.7 order".getBytes(StandardCharsets.UTF_8)));
        ageSentAtByDays(id, 91);

        assertThat(retention.pruneNow().inlineAttachmentsDropped()).isEqualTo(1);

        // Exactly the state V21 built and this test now reaches by policy rather than by hand.
        SentEmailAttachmentView view = emailSender.attachmentsOf(id).getFirst();
        assertThat(view.available()).isFalse();
        assertThat(view.unavailableReasonIfAny())
                .hasValueSatisfying(reason -> assertThat(reason).contains("retention"));
        assertThat(view.filename()).isEqualTo("po-2001.pdf");
        assertThat(view.sizeBytes()).isPositive();

        // The message itself is untouched: it was sent, with that file on it, and says so.
        assertThat(emailSender.find(id).orElseThrow().status()).isEqualTo(EmailStatus.SENT);
        assertThat(emailSender.find(id).orElseThrow().attachmentCount()).isEqualTo(1);

        assertThatExceptionOfType(EmailAttachmentUnavailableException.class)
                .isThrownBy(() -> emailSender.downloadAttachment(view.id()));
    }

    @Test
    @DisplayName("a referenced document is never pruned — its bytes are not the outbox's to delete")
    void referencedDocumentSurvivesThePrune() {
        // The restriction that matters most. Widening the statement to "all attachments" would
        // make one service delete another's documents, and the symptom would be a purchase
        // invoice's PDF disappearing off the invoice because an email mentioned it 91 days ago.
        byte[] pdf = "%PDF-1.7 the invoice".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata document = attachments.attach(
                "PurchaseInvoice", "2002", "invoice-2002.pdf", "application/pdf", pdf);

        long id = sentMessageWith(
                EmailAttachment.stored(document.id()),
                EmailAttachment.pdf("cover.pdf", "%PDF-1.7 cover".getBytes(StandardCharsets.UTF_8)));
        ageSentAtByDays(id, 400);

        assertThat(retention.pruneNow().inlineAttachmentsDropped())
                .as("only the inline one, however old the message is")
                .isEqualTo(1);

        List<SentEmailAttachmentView> views = emailSender.attachmentsOf(id);
        assertThat(views.getFirst().available())
                .as("the referenced document is still there and still readable")
                .isTrue();
        assertThat(emailSender.downloadAttachment(views.getFirst().id()).content()).isEqualTo(pdf);
        assertThat(views.getLast().available()).isFalse();

        // And the document itself is untouched in the table that owns it.
        assertThat(attachments.download(document.id())).isPresent();
    }

    @Test
    @DisplayName("a message that has not been sent keeps its bytes, however old the row is")
    void pendingAndFailedMessagesKeepTheirAttachments() {
        // A system waiting on a broken SMTP password for months must not have its attachments
        // removed from under it; and a FAILED message keeps them because retrying it is the
        // entire reason it was kept.
        long pending = emailSender.send(EmailMessage.to("a@example.com", "Waiting", "body",
                EmailAttachment.pdf("waiting.pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8))));
        long failed = emailSender.send(EmailMessage.to("b@example.com", "Gave up", "body",
                EmailAttachment.pdf("gaveup.pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8))));
        jdbc.update("""
                UPDATE email_outbox
                   SET status = 'FAILED', next_attempt_at = NULL, last_attempt_at = now(),
                       attempts = 1, last_error = 'no route to host', created_at = now() - interval '400 days'
                 WHERE id = ?
                """, failed);
        jdbc.update(
                "UPDATE email_outbox SET created_at = now() - interval '400 days' WHERE id = ?",
                pending);

        assertThat(retention.pruneNow().inlineAttachmentsDropped()).isZero();

        assertThat(emailSender.attachmentsOf(pending).getFirst().available()).isTrue();
        assertThat(emailSender.attachmentsOf(failed).getFirst().available())
                .as("a retry that cannot re-send the attachment is not a retry")
                .isTrue();
    }

    @Test
    @DisplayName("the 90-day boundary is a boundary, and a second pass is idempotent")
    void boundaryAndIdempotence() {
        long justInside = sentMessageWith(EmailAttachment.pdf(
                "fresh.pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)));
        ageSentAtByDays(justInside, 89);

        assertThat(retention.pruneNow().inlineAttachmentsDropped()).isZero();
        assertThat(emailSender.attachmentsOf(justInside).getFirst().available()).isTrue();

        ageSentAtByDays(justInside, 91);
        assertThat(retention.pruneNow().inlineAttachmentsDropped()).isEqualTo(1);

        // The second pass must report nothing rather than re-writing rows it already cleared,
        // otherwise every daily run rewrites the whole table and the audit log fills with noise.
        assertThat(retention.pruneNow().isNothing()).isTrue();
    }

    @Test
    @DisplayName("rows are kept forever, and the setting is real rather than decorative")
    void messageRowsAreKeptForeverUnlessConfiguredOtherwise() {
        long id = sentMessageWith(EmailAttachment.pdf(
                "old.pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)));
        ageSentAtByDays(id, 4000);

        assertThat(retention.pruneNow().messagesRemoved())
                .as("Q43: a sent-email history is a business record")
                .isZero();
        assertThat(emailSender.find(id)).isPresent();

        // But the mechanism honours a number, or the setting would be a lie.
        settings.put(SettingKeys.EMAIL_RETENTION_MESSAGE_DAYS, "365");
        assertThat(retention.pruneNow().messagesRemoved()).isEqualTo(1);
        assertThat(emailSender.find(id)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM email_outbox_attachment WHERE email_outbox_id = ?",
                Long.class, id))
                .as("attachment rows follow their message by ON DELETE CASCADE")
                .isZero();
    }

    @Test
    @DisplayName("an unreadable retention setting stops the prune loudly, and deletes nothing")
    void anUnparseableSettingDeletesNothing() {
        // The one setting in this service with no safe default: guessing "0 days" would delete
        // everything and no amount of logging would undo it. Loud and inert is the pair.
        long id = sentMessageWith(EmailAttachment.pdf(
                "safe.pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)));
        ageSentAtByDays(id, 400);

        settings.put(SettingKeys.EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS, "ninety");
        assertThat(retention.pruneNow().isNothing()).isTrue();
        assertThat(emailSender.attachmentsOf(id).getFirst().available()).isTrue();

        settings.put(SettingKeys.EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS, "0");
        assertThat(retention.pruneNow().isNothing()).isTrue();
        assertThat(emailSender.attachmentsOf(id).getFirst().available()).isTrue();
    }

    // -----------------------------------------------------------------------------------------

    /** A message that has been through the outbox and is recorded as sent, with attachments. */
    private long sentMessageWith(EmailAttachment... files) {
        long id = emailSender.send(EmailMessage.builder(
                        "customer@example.com", "Subject", "body")
                .attach(files)
                .build());
        // Marked sent directly rather than dispatched: this class is about retention, and
        // standing up an SMTP server to reach the same row state would only add a way to fail.
        jdbc.update("""
                UPDATE email_outbox
                   SET status = 'SENT', sent_at = now(), next_attempt_at = NULL,
                       last_attempt_at = now(), attempts = 1
                 WHERE id = ?
                """, id);
        return id;
    }

    private void ageSentAtByDays(long id, int days) {
        jdbc.update("UPDATE email_outbox SET sent_at = now() - make_interval(days => ?) WHERE id = ?",
                days, id);
    }
}
