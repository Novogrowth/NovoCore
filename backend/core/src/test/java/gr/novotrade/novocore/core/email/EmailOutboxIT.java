package gr.novotrade.novocore.core.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailAttachmentSource;
import gr.novotrade.novocore.core.api.email.EmailAttachmentUnavailableException;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailSender;
import gr.novotrade.novocore.core.api.email.EmailStatus;
import gr.novotrade.novocore.core.api.email.QueuedEmailView;
import gr.novotrade.novocore.core.api.email.SentEmailAttachmentView;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The email service end to end: queued in the caller's transaction, sent by the dispatcher
 * against a real SMTP conversation, retried on failure, and left visibly failed when it gives up.
 *
 * <p>GreenMail is an in-process SMTP server, so the message is genuinely composed, transmitted
 * and parsed back. That matters most for the Reply-To header: it is applied by the dispatcher
 * from Settings, a caller has no way to influence it, and reading it off a received message is
 * the only way to know it actually left with one.
 *
 * <p><strong>Nothing here sleeps.</strong> The scheduler is enabled in {@code app}, not in the
 * core's test context, so these tests call {@link EmailDispatcher#dispatchDue()} directly and
 * make a message due again by moving its own due timestamp — which is why the retry and backoff
 * assertions are exact rather than approximate.
 */
class EmailOutboxIT extends AbstractCoreIntegrationTest {

    private static final String SMTP_USER = "erp@novotrade.gr";
    private static final String SMTP_PASSWORD = "greenmail-test-password";
    private static final String REPLY_TO = "kostas@novotrade.gr";

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private RoleService roles;

    @Autowired
    private EmailDispatcher dispatcher;

    @Autowired
    private SettingsService settings;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private AttachmentService attachments;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    /**
     * Every setting this class overwrites, so it can put them back.
     *
     * <p>These integration tests share one database and are deliberately not transactional, so
     * a class that repoints {@code smtp.host} at a throwaway server and walks away leaves the
     * seeded configuration wrong for everything that runs afterwards — including the test that
     * checks what V20 seeds. Settings are global by nature; the usual advice here of "use
     * distinct keys" has no equivalent, so restoring is the alternative.
     */
    private static final List<String> OVERWRITTEN_SETTINGS = List.of(
            SettingKeys.SMTP_HOST,
            SettingKeys.SMTP_PORT,
            SettingKeys.SMTP_USERNAME,
            SettingKeys.SMTP_PASSWORD,
            SettingKeys.SMTP_TRANSPORT_SECURITY,
            SettingKeys.SMTP_FROM_ADDRESS,
            SettingKeys.SMTP_FROM_NAME,
            SettingKeys.SMTP_REPLY_TO,
            SettingKeys.EMAIL_MAX_ATTEMPTS,
            SettingKeys.EMAIL_RETRY_BACKOFF_SECONDS,
            SettingKeys.EMAIL_RETRY_BACKOFF_MAX_SECONDS,
            SettingKeys.EMAIL_DISPATCH_BATCH_SIZE);

    private GreenMail greenMail;
    private Map<String, String> settingsBeforeThisTest;

    /**
     * One {@code @BeforeEach} rather than three, because the steps are ordered and JUnit does not
     * promise an order between separate lifecycle methods: the settings have to be remembered
     * before {@link #configureSmtp} overwrites them, or the restore puts back this class's own
     * throwaway values.
     *
     * <p>The outbox is emptied because these tests are not transactional (see
     * {@code AbstractCoreIntegrationTest}) and several of them queue a message deliberately
     * without dispatching it. Without this, a later test's batch picks those up and every
     * assertion of the form "this cycle sent exactly one" quietly becomes a statement about the
     * whole class's history — which is how {@code aPoisonRowDoesNotStopEverythingElse} first
     * failed, reporting three sent instead of one. A {@code DELETE} here is not a breach of the
     * codebase's no-delete stance: that governs what the <em>services</em> do to records people
     * rely on, and no other test class touches this table.
     */
    @BeforeEach
    void startWithACleanOutboxAndAMailServer() {
        settingsBeforeThisTest = new HashMap<>();
        OVERWRITTEN_SETTINGS.forEach(
                key -> settingsBeforeThisTest.put(key, settings.find(key).orElse("")));

        jdbc.update("DELETE FROM email_outbox");

        // A dynamic port, so a developer running this with something already bound to 3025 —
        // or two builds at once — does not get a failure that looks like a mail bug.
        greenMail = new GreenMail(
                new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        greenMail.setUser(SMTP_USER, SMTP_USER, SMTP_PASSWORD);
        greenMail.start();

        configureSmtp("127.0.0.1", greenMail.getSmtp().getPort());
    }

    @AfterEach
    void stopMailServerAndRestoreSettings() {
        if (greenMail != null) {
            greenMail.stop();
        }
        settingsBeforeThisTest.forEach(settings::put);
    }

    private void configureSmtp(String host, int port) {
        settings.put(SettingKeys.SMTP_HOST, host);
        settings.put(SettingKeys.SMTP_PORT, String.valueOf(port));
        settings.put(SettingKeys.SMTP_USERNAME, SMTP_USER);
        settings.putSecret(SettingKeys.SMTP_PASSWORD, SMTP_PASSWORD);
        // GreenMail speaks plain SMTP here. The three transport modes are asserted as property
        // mappings in SmtpConfigurationTest, because the difference between them is which
        // properties are set and a test server holding a self-signed certificate would only
        // prove that we told the client to trust anything.
        settings.put(SettingKeys.SMTP_TRANSPORT_SECURITY, "NONE");
        settings.put(SettingKeys.SMTP_FROM_ADDRESS, SMTP_USER);
        settings.put(SettingKeys.SMTP_FROM_NAME, "Java Jives");
        settings.put(SettingKeys.SMTP_REPLY_TO, REPLY_TO);
        settings.put(SettingKeys.EMAIL_MAX_ATTEMPTS, "3");
        settings.put(SettingKeys.EMAIL_RETRY_BACKOFF_SECONDS, "30");
        settings.put(SettingKeys.EMAIL_RETRY_BACKOFF_MAX_SECONDS, "900");
        settings.put(SettingKeys.EMAIL_DISPATCH_BATCH_SIZE, "20");
    }

    // -----------------------------------------------------------------------------------------
    // Queueing
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("send queues and returns without touching the network")
    void sendQueuesWithoutSending() {
        long id = emailSender.send(
                EmailMessage.to("customer@example.com", "Order confirmed", "Thank you."));

        QueuedEmailView queued = emailSender.find(id).orElseThrow();
        assertThat(queued.status()).isEqualTo(EmailStatus.PENDING);
        assertThat(queued.attempts()).isZero();
        assertThat(queued.nextAttemptAtIfAny()).isPresent();
        // The sender is resolved at send time, not at queue time, so nothing is recorded yet.
        assertThat(queued.sentFromIfAny()).isEmpty();
        assertThat(queued.replyToIfAny()).isEmpty();

        assertThat(greenMail.getReceivedMessages())
                .as("CLAUDE.md rule 4: a core operation never waits on an outbound call")
                .isEmpty();
    }

    @Test
    @DisplayName("a rolled-back operation queues no email")
    void queuedInTheCallersTransaction() {
        // The reason the outbox is a table rather than an in-memory queue. A Purchase Order that
        // was not saved must not send its PDF, and this needs no compensating logic anywhere:
        // the outbox row is written in the same transaction and rolls back with it.
        long before = countOutboxRows();

        assertThatIllegalStateException().isThrownBy(() -> transactions.executeWithoutResult(tx -> {
            emailSender.send(EmailMessage.to("customer@example.com", "Should not be sent", "x"));
            throw new IllegalStateException("the operation failed after queueing");
        }));

        assertThat(countOutboxRows()).isEqualTo(before);
    }

    @Test
    @DisplayName("queueing is audited with the recipient and subject, never the body")
    void queueingIsAudited() {
        long id = emailSender.send(EmailMessage.to(
                "customer@example.com", "Statement", "Your outstanding balance is EUR 412.00."));

        List<AuditEntry> entries = auditLog.findForEntity("Email", String.valueOf(id), 10);

        assertThat(entries).isNotEmpty();
        assertThat(entries.getLast().action()).isEqualTo("email.queued");
        assertThat(entries.getLast().detail())
                .containsEntry("to", "customer@example.com")
                .containsEntry("subject", "Statement");
        assertThat(entries)
                .as("an audit log holding the text of every customer email is a second copy of "
                        + "that correspondence in a place chosen for retention, not confidentiality")
                .noneMatch(entry -> entry.detail().values().stream()
                        .anyMatch(value -> value.contains("412.00")));
    }

    // -----------------------------------------------------------------------------------------
    // Sending
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("the dispatcher sends it, and the message carries the configured Reply-To")
    void dispatchSendsWithReplyTo() throws Exception {
        long id = emailSender.send(EmailMessage.to(
                "customer@example.com", "Order confirmed", "Thank you for your order."));

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

        MimeMessage received = greenMail.getReceivedMessages()[0];

        assertThat(received.getSubject()).isEqualTo("Order confirmed");
        assertThat(received.getFrom()[0].toString()).contains(SMTP_USER).contains("Java Jives");
        assertThat(received.getAllRecipients()[0].toString()).isEqualTo("customer@example.com");
        assertThat(received.getContent().toString()).contains("Thank you for your order.");

        // The header this whole step exists to guarantee. erp@novotrade.gr is unmonitored, so a
        // message without this sends every reply somewhere nobody opens.
        assertThat(received.getReplyTo())
                .hasSize(1)
                .allSatisfy(address -> assertThat(address.toString()).isEqualTo(REPLY_TO));

        QueuedEmailView sent = emailSender.find(id).orElseThrow();
        assertThat(sent.status()).isEqualTo(EmailStatus.SENT);
        assertThat(sent.attempts()).isEqualTo(1);
        assertThat(sent.sentAtIfAny()).isPresent();
        assertThat(sent.nextAttemptAtIfAny()).isEmpty();
        assertThat(sent.replyToIfAny()).contains(REPLY_TO);
        assertThat(sent.sentFromIfAny()).contains(SMTP_USER);
        assertThat(sent.lastErrorIfAny()).isEmpty();
    }

    @Test
    @DisplayName("every message gets the Reply-To, not just the plain ones")
    void replyToIsOnEveryMessage() throws Exception {
        emailSender.send(EmailMessage.to("a@example.com", "Plain", "body"));
        emailSender.send(EmailMessage.html("b@example.com", "Html", "<p>body</p>"));
        emailSender.send(EmailMessage.to("c@example.com", "Attached", "body",
                EmailAttachment.pdf("po.pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8))));

        assertThat(dispatcher.dispatchDue()).isEqualTo(3);
        assertThat(greenMail.waitForIncomingEmail(5000, 3)).isTrue();

        assertThat(greenMail.getReceivedMessages()).hasSize(3).allSatisfy(message ->
                assertThat(message.getReplyTo()[0].toString()).isEqualTo(REPLY_TO));
    }

    @Test
    @DisplayName("attachments arrive with their filename and their bytes intact")
    void attachmentsSurvive() throws Exception {
        byte[] pdf = "%PDF-1.7 fake purchase order".getBytes(StandardCharsets.UTF_8);

        emailSender.send(EmailMessage.to(
                "supplier@example.com", "Purchase Order 42", "See attached.",
                EmailAttachment.pdf("po-42.pdf", pdf)));

        dispatcher.dispatchDue();
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

        Multipart multipart = (Multipart) greenMail.getReceivedMessages()[0].getContent();
        assertThat(multipart.getCount()).isEqualTo(2);

        var attachmentPart = multipart.getBodyPart(1);
        assertThat(attachmentPart.getFileName()).isEqualTo("po-42.pdf");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        attachmentPart.getInputStream().transferTo(bytes);
        assertThat(bytes.toByteArray()).isEqualTo(pdf);
    }

    @Test
    @DisplayName("Greek subjects and bodies survive the round trip")
    void greekSurvivesTheRoundTrip() throws Exception {
        // Not incidental. Every customer-facing message this system will send is Greek, and left
        // to the platform default the encoding would be windows-1252 on a Windows development
        // machine and UTF-8 in the container — with nothing failing in either case.
        emailSender.send(EmailMessage.to("customer@example.com",
                "Επιβεβαίωση παραγγελίας", "Ευχαριστούμε για την παραγγελία σας."));

        dispatcher.dispatchDue();
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

        MimeMessage received = greenMail.getReceivedMessages()[0];
        assertThat(received.getSubject()).isEqualTo("Επιβεβαίωση παραγγελίας");
        assertThat(received.getContent().toString())
                .contains("Ευχαριστούμε για την παραγγελία σας.");
    }

    @Test
    @DisplayName("cc and bcc recipients are addressed")
    void copyRecipients() throws Exception {
        emailSender.send(EmailMessage.builder("to@example.com", "Report", "body")
                .cc("cc@example.com")
                .bcc("bcc@example.com")
                .build());

        dispatcher.dispatchDue();
        // GreenMail delivers one copy per recipient.
        assertThat(greenMail.waitForIncomingEmail(5000, 3)).isTrue();
        assertThat(greenMail.getReceivedMessages()).hasSize(3);
    }

    @Test
    @DisplayName("a successful send is audited")
    void sendingIsAudited() {
        long id = emailSender.send(EmailMessage.to("customer@example.com", "Order", "body"));
        dispatcher.dispatchDue();

        assertThat(auditLog.findForEntity("Email", String.valueOf(id), 10))
                .extracting(AuditEntry::action)
                .contains("email.sent");
    }

    // -----------------------------------------------------------------------------------------
    // Referenced attachments: stored once, read transparently, degraded gracefully
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a referenced document is not copied into the outbox, and still arrives intact")
    void referencedDocumentIsStoredOnce() throws Exception {
        byte[] pdf = "%PDF-1.7 the invoice itself".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata document = attachments.attach(
                "PurchaseInvoice", "1001", "invoice-1001.pdf", "application/pdf", pdf);

        long id = emailSender.send(EmailMessage.to(
                "accountant@example.com", "Invoice 1001", "See attached.",
                EmailAttachment.stored(document.id())));

        // The point of the whole change. The bytes exist once, in the table that owns them.
        assertThat(outboxAttachmentBytes(id))
                .as("a referenced document must not be copied into the outbox")
                .isNull();
        assertThat(outboxAttachmentColumn(id, "attachment_id", Long.class))
                .isEqualTo(document.id());
        assertThat(outboxAttachmentColumn(id, "content_source", String.class))
                .isEqualTo("ATTACHMENT");

        // And SMTP transmits real bytes regardless of how we chose to store our own copy.
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

        Multipart multipart = (Multipart) greenMail.getReceivedMessages()[0].getContent();
        var attachmentPart = multipart.getBodyPart(1);
        assertThat(attachmentPart.getFileName()).isEqualTo("invoice-1001.pdf");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        attachmentPart.getInputStream().transferTo(bytes);
        assertThat(bytes.toByteArray())
                .as("the recipient's mail is unaffected by how NovoCore stores its own copy")
                .isEqualTo(pdf);
    }

    @Test
    @DisplayName("viewing a sent attachment is one call, and looks the same for both shapes")
    void readingAnAttachmentIsOneCallForEitherShape() {
        byte[] stored = "%PDF-1.7 stored".getBytes(StandardCharsets.UTF_8);
        byte[] generated = "%PDF-1.7 generated".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata document = attachments.attach(
                "SalesInvoice", "77", "sales-77.pdf", "application/pdf", stored);

        long id = emailSender.send(EmailMessage.builder(
                        "customer@example.com", "Your invoice", "Both attached.")
                .attach(EmailAttachment.stored(document.id()),
                        EmailAttachment.pdf("summary.pdf", generated))
                .build());

        List<SentEmailAttachmentView> views = emailSender.attachmentsOf(id);
        assertThat(views).hasSize(2);

        // Requirement: no separate lookup, and nothing the reader has to know about where the
        // file lives. Both entries answer the same questions and take the same id.
        assertThat(views).allSatisfy(view -> {
            assertThat(view.available()).isTrue();
            assertThat(view.unavailableReasonIfAny()).isEmpty();
            assertThat(view.sizeBytes()).isPositive();
            assertThat(emailSender.downloadAttachment(view.id(), owner()).content()).isNotEmpty();
        });

        SentEmailAttachmentView referenced = views.getFirst();
        assertThat(referenced.source()).isEqualTo(EmailAttachmentSource.ATTACHMENT);
        assertThat(referenced.filename()).isEqualTo("sales-77.pdf");
        assertThat(referenced.storedAttachmentIdIfAny()).contains(document.id());
        assertThat(emailSender.downloadAttachment(referenced.id(), owner()).content()).isEqualTo(stored);

        SentEmailAttachmentView inline = views.getLast();
        assertThat(inline.source()).isEqualTo(EmailAttachmentSource.INLINE);
        assertThat(inline.filename()).isEqualTo("summary.pdf");
        assertThat(inline.storedAttachmentIdIfAny()).isEmpty();
        assertThat(emailSender.downloadAttachment(inline.id(), owner()).content()).isEqualTo(generated);
    }

    @Test
    @DisplayName("deleting the document leaves the history naming the file, not broken")
    void deletedDocumentDegradesGracefully() {
        AttachmentMetadata document = attachments.attach("PurchaseInvoice", "1002", "po-1002.pdf",
                "application/pdf", "%PDF-1.7 order".getBytes(StandardCharsets.UTF_8));

        long id = emailSender.send(EmailMessage.to(
                "supplier@example.com", "Purchase Order 1002", "See attached.",
                EmailAttachment.stored(document.id())));
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        // Deleting a document an old email mentions must be allowed. The alternative is that
        // every message ever sent pins its attachments forever.
        assertThat(attachments.delete(document.id())).isTrue();

        SentEmailAttachmentView view = emailSender.attachmentsOf(id).getFirst();
        assertThat(view.available()).isFalse();
        assertThat(view.unavailableReasonIfAny())
                .hasValueSatisfying(reason -> assertThat(reason).contains("deleted"));
        // Still a complete record of what went out, which is what the history is for.
        assertThat(view.filename()).isEqualTo("po-1002.pdf");
        assertThat(view.sizeBytes()).isPositive();
        assertThat(view.source()).isEqualTo(EmailAttachmentSource.ATTACHMENT);
        assertThat(view.storedAttachmentIdIfAny())
                .as("the foreign key nulls the reference, so availability needs no extra query")
                .isEmpty();

        // The message itself is untouched — it really was sent, with that file on it.
        assertThat(emailSender.find(id).orElseThrow().status()).isEqualTo(EmailStatus.SENT);
        assertThat(emailSender.find(id).orElseThrow().attachmentCount()).isEqualTo(1);

        // Asking for the bytes anyway is distinguishable from asking for an id that never existed.
        assertThatExceptionOfType(EmailAttachmentUnavailableException.class)
                .isThrownBy(() -> emailSender.downloadAttachment(view.id(), owner()))
                .withMessageContaining("po-1002.pdf");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> emailSender.downloadAttachment(-1L, owner()));
    }

    @Test
    @DisplayName("a pruned inline copy reaches the same state, and says so differently")
    void prunedInlineCopyDegradesGracefully() {
        // Nothing prunes anything today — the retention policy is Q43's number to set. What is
        // asserted here is that the state a prune would leave behind is already a state the
        // history renders gracefully, so enabling it later is an UPDATE and not a schema change.
        long id = emailSender.send(EmailMessage.to("customer@example.com", "Report", "Attached.",
                EmailAttachment.pdf("report.pdf", "%PDF-1.7 report".getBytes(StandardCharsets.UTF_8))));
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);

        jdbc.update("""
                UPDATE email_outbox_attachment SET content = NULL
                 WHERE email_outbox_id = ? AND content_source = 'INLINE'
                """, id);

        SentEmailAttachmentView view = emailSender.attachmentsOf(id).getFirst();
        assertThat(view.available()).isFalse();
        assertThat(view.filename()).isEqualTo("report.pdf");
        assertThat(view.sizeBytes()).isPositive();
        assertThat(view.unavailableReasonIfAny())
                .as("the same outcome as a deleted document, reached another way — and the "
                        + "history must be able to say which")
                .hasValueSatisfying(reason -> assertThat(reason).contains("retention"));
    }

    @Test
    @DisplayName("an attachment id that names nothing is refused when the message is queued")
    void unknownDocumentIsRefusedAtQueueTime() {
        // In the caller's own transaction, so the operation that made the mistake fails, rather
        // than a stuck outbox row surfacing hours later.
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> emailSender.send(EmailMessage.to(
                        "customer@example.com", "Invoice", "See attached.",
                        EmailAttachment.stored(999_999L))))
                .withMessageContaining("999999");
    }

    @Test
    @DisplayName("a document deleted before the message goes out fails it visibly, alone")
    void documentDeletedBeforeSendingFailsTheMessage() {
        AttachmentMetadata document = attachments.attach("PurchaseInvoice", "1003", "po-1003.pdf",
                "application/pdf", "%PDF-1.7 order".getBytes(StandardCharsets.UTF_8));

        long doomed = emailSender.send(EmailMessage.to(
                "supplier@example.com", "Purchase Order 1003", "See attached.",
                EmailAttachment.stored(document.id())));
        attachments.delete(document.id());

        long healthy = emailSender.send(
                EmailMessage.to("customer@example.com", "Perfectly fine", "body"));

        assertThat(dispatcher.dispatchDue())
                .as("the healthy message in the same batch must still go out")
                .isEqualTo(1);
        assertThat(emailSender.find(healthy).orElseThrow().status()).isEqualTo(EmailStatus.SENT);

        // Never sent with the attachment quietly missing: that is the one failure a recipient
        // could not possibly detect, so it fails loudly instead (CLAUDE.md rule 8).
        QueuedEmailView failed = emailSender.find(doomed).orElseThrow();
        assertThat(failed.status()).isEqualTo(EmailStatus.FAILED);
        assertThat(failed.lastErrorIfAny())
                .hasValueSatisfying(error -> assertThat(error).contains("po-1003.pdf"));
    }

    @Test
    @DisplayName("the attachment-source CHECK lists exactly the values EmailAttachmentSource has")
    void attachmentSourceCheckMatchesTheEnum() {
        String definition = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                  FROM pg_constraint
                 WHERE conname = 'email_attachment_source_known'
                """, String.class);

        assertThat(definition).isNotNull();
        for (EmailAttachmentSource source : EmailAttachmentSource.values()) {
            assertThat(definition).contains("'" + source.name() + "'");
        }
        assertThat(definition.split("'::character varying").length - 1)
                .as("the CHECK must not permit a source Java does not have")
                .isEqualTo(EmailAttachmentSource.values().length);
    }

    @Test
    @DisplayName("the database refuses an attachment that is both shapes at once")
    void databaseRefusesMixedShapes() {
        long emailId = emailSender.send(EmailMessage.to("customer@example.com", "Subject", "body"));
        AttachmentMetadata document = attachments.attach("PurchaseInvoice", "1004", "raw.pdf",
                "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8));

        // Each of these violates exactly ONE constraint. Written the obvious way, several of them
        // break two at once — and PostgreSQL does not promise which it reports, so a test
        // asserting on the name would pass or fail depending on constraint evaluation order.

        // A reference that also carries a copy: the shape that defeats the point of referencing.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawAttachment(emailId, "ATTACHMENT", document.id(),
                        document.checksumSha256(), true, 91))
                .withMessageContaining("email_attachment_bytes_only_when_inline");

        // An inline row pointing at a document, which would leave one file with two owners.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawAttachment(emailId, "INLINE", document.id(),
                        null, true, 92))
                .withMessageContaining("email_attachment_reference_only_when_referenced");

        // A reference that cannot say which file it was, so the deleted case becomes unanswerable.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawAttachment(emailId, "ATTACHMENT", document.id(),
                        null, false, 93))
                .withMessageContaining("email_attachment_reference_states_its_checksum");

        // And the well-formed reference goes in, so the three above fail for the stated reason
        // rather than because the whole shape of the statement is wrong.
        insertRawAttachment(emailId, "ATTACHMENT", document.id(), document.checksumSha256(),
                false, 94);
    }

    /** A row written straight to the table, to prove the CHECKs and not only the Java. */
    private void insertRawAttachment(long emailId, String source, Long attachmentId,
            String checksum, boolean withContent, int order) {
        jdbc.update("""
                INSERT INTO email_outbox_attachment
                    (email_outbox_id, content_source, attachment_id, filename, content_type,
                     size_bytes, checksum_sha256, content, attachment_order)
                VALUES (?, ?, ?, 'raw.pdf', 'application/pdf', 10, ?, %s, ?)
                """.formatted(withContent ? "'\\x0102'::bytea" : "NULL"),
                emailId, source, attachmentId, checksum, order);
    }

    private byte[] outboxAttachmentBytes(long emailId) {
        return jdbc.queryForObject(
                "SELECT content FROM email_outbox_attachment WHERE email_outbox_id = ?",
                byte[].class, emailId);
    }

    private <T> T outboxAttachmentColumn(long emailId, String column, Class<T> type) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM email_outbox_attachment WHERE email_outbox_id = ?",
                type, emailId);
    }

    // -----------------------------------------------------------------------------------------
    // Failure, retry and giving up
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a failed attempt is retried later, not immediately")
    void failureBacksOff() {
        pointAtADeadServer();
        long id = emailSender.send(EmailMessage.to("customer@example.com", "Order", "body"));

        Instant beforeDispatch = Instant.now();
        assertThat(dispatcher.dispatchDue()).isZero();

        QueuedEmailView afterFirst = emailSender.find(id).orElseThrow();
        assertThat(afterFirst.status()).isEqualTo(EmailStatus.PENDING);
        assertThat(afterFirst.attempts()).isEqualTo(1);
        assertThat(afterFirst.lastErrorIfAny()).isPresent();
        assertThat(afterFirst.nextAttemptAtIfAny())
                .hasValueSatisfying(due -> assertThat(due)
                        .isAfter(beforeDispatch.plusSeconds(25)));

        // The second cycle must claim nothing: the message is not due yet. Without this the
        // backoff would be decoration and a dead server would be hammered every 15 seconds.
        assertThat(dispatcher.dispatchDue()).isZero();
        assertThat(emailSender.find(id).orElseThrow().attempts())
                .as("a message that is not due must not be attempted again")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("after its attempts run out a message is FAILED, kept, and queryable")
    void exhaustedAttemptsFailVisibly() {
        pointAtADeadServer();
        long id = emailSender.send(
                EmailMessage.to("customer@example.com", "Order", "body"));

        // max-attempts is 3 for this class. Each cycle is made due again explicitly rather than
        // by waiting out the real backoff.
        for (int attempt = 1; attempt <= 3; attempt++) {
            makeDueNow(id);
            dispatcher.dispatchDue();
        }

        QueuedEmailView failed = emailSender.find(id).orElseThrow();
        assertThat(failed.status()).isEqualTo(EmailStatus.FAILED);
        assertThat(failed.attempts()).isEqualTo(3);
        assertThat(failed.needsAttention()).isTrue();
        assertThat(failed.nextAttemptAtIfAny()).isEmpty();
        assertThat(failed.lastErrorIfAny())
                .as("CLAUDE.md rule 8: fail loudly, never silently drop")
                .isPresent();

        assertThat(emailSender.failed(50))
                .extracting(QueuedEmailView::id)
                .contains(id);

        // And it stops being attempted, rather than looping.
        makeDueNow(id);
        assertThat(dispatcher.dispatchDue()).isZero();
        assertThat(emailSender.find(id).orElseThrow().attempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("retry puts a failed message back, with a fresh allowance, and it then sends")
    void retryResendsSuccessfully() {
        pointAtADeadServer();
        long id = emailSender.send(EmailMessage.to("customer@example.com", "Order", "body"));
        for (int attempt = 1; attempt <= 3; attempt++) {
            makeDueNow(id);
            dispatcher.dispatchDue();
        }
        assertThat(emailSender.find(id).orElseThrow().status()).isEqualTo(EmailStatus.FAILED);

        // Whatever was wrong has been fixed.
        configureSmtp("127.0.0.1", greenMail.getSmtp().getPort());

        QueuedEmailView requeued = emailSender.retry(id);
        assertThat(requeued.status()).isEqualTo(EmailStatus.PENDING);
        assertThat(requeued.attempts())
                .as("a message that gets one grudging attempt per intervention makes the fix "
                        + "hard to confirm")
                .isZero();

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
        assertThat(emailSender.find(id).orElseThrow().status()).isEqualTo(EmailStatus.SENT);
    }

    @Test
    @DisplayName("nothing re-queues a failed message on its own")
    void retryIsManualOnly() {
        long id = emailSender.send(EmailMessage.to("customer@example.com", "Order", "body"));

        assertThatIllegalStateException()
                .isThrownBy(() -> emailSender.retry(id))
                .withMessageContaining("PENDING");
    }

    @Test
    @DisplayName("retrying a message that does not exist names the id")
    void retryOfUnknownMessage() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> emailSender.retry(-1L))
                .withMessageContaining("-1");
    }

    // -----------------------------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("an unconfigured system consumes no attempts")
    void unconfiguredConsumesNoAttempts() {
        long id = emailSender.send(EmailMessage.to("customer@example.com", "Order", "body"));
        settings.put(SettingKeys.SMTP_HOST, "");

        assertThat(dispatcher.dispatchDue()).isZero();

        QueuedEmailView untouched = emailSender.find(id).orElseThrow();
        assertThat(untouched.status()).isEqualTo(EmailStatus.PENDING);
        assertThat(untouched.attempts())
                .as("a message must not burn its retries against a configuration nobody has "
                        + "filled in yet")
                .isZero();

        // And it goes out the moment the configuration is corrected.
        configureSmtp("127.0.0.1", greenMail.getSmtp().getPort());
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
    }

    @Test
    @DisplayName("verifyConfiguration reaches the real server, or says why it cannot")
    void verifyConfigurationIsHonest() {
        var status = emailSender.verifyConfiguration();
        assertThat(status.configured()).isTrue();
        assertThat(status.isUsable()).isTrue();
        assertThat(status.replyTo()).isEqualTo(REPLY_TO);
        assertThat(status.fromAddress()).isEqualTo(SMTP_USER);
        assertThat(status.problemIfAny()).isEmpty();

        pointAtADeadServer();
        var broken = emailSender.verifyConfiguration();
        assertThat(broken.configured()).isTrue();
        assertThat(broken.reachable())
                .as("a configuration that reads back correctly and cannot connect is the common "
                        + "case; only a real connection tells them apart")
                .isFalse();
        assertThat(broken.problemIfAny()).isPresent();

        settings.put(SettingKeys.SMTP_REPLY_TO, "");
        var unconfigured = emailSender.verifyConfiguration();
        assertThat(unconfigured.configured()).isFalse();
        assertThat(unconfigured.problemIfAny())
                .hasValueSatisfying(problem ->
                        assertThat(problem).contains(SettingKeys.SMTP_REPLY_TO));
    }

    @Test
    @DisplayName("no email setting exposes the password, even redacted")
    void passwordNeverLeaves() {
        assertThat(emailSender.verifyConfiguration().toString())
                .doesNotContain(SMTP_PASSWORD);

        assertThat(settings.listRedacted())
                .filteredOn(view -> view.key().equals(SettingKeys.SMTP_PASSWORD))
                .allSatisfy(view -> {
                    assertThat(view.secret()).isTrue();
                    assertThat(view.value()).isNotEqualTo(SMTP_PASSWORD);
                });
    }

    // -----------------------------------------------------------------------------------------
    // The database refuses impossible rows, not just the service
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a message with no recipient is refused by the database too")
    void databaseRefusesRecipientlessMessage() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update("""
                        INSERT INTO email_outbox
                            (to_addresses, subject, body, body_format, status, max_attempts,
                             next_attempt_at)
                        VALUES (ARRAY[]::text[], 'Subject', 'body', 'PLAIN_TEXT', 'PENDING', 3,
                                now())
                        """))
                .withMessageContaining("email_outbox_has_a_recipient");
    }

    @Test
    @DisplayName("a line break in a recipient or subject is refused by the database too")
    void databaseRefusesHeaderInjection() {
        // The recipient is bound as a parameter and the array built with ARRAY[?], not written
        // as a '{...}' array literal. A first version did the latter and quietly proved nothing:
        // PostgreSQL's array-literal parser treats a backslash as an escape, so the "\n" became
        // a plain 'n', the CHECK had no line break to object to, and the row went in — leaving
        // a message in the outbox that broke every later test in this class.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawOutboxRow(
                        "a@example.com\r\nBcc: b@example.com",
                        "Subject", "PENDING", "now()", null))
                .withMessageContaining("email_outbox_no_header_injection");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawOutboxRow(
                        "a@example.com", "Subject\r\nBcc: b@example.com", "PENDING", "now()", null))
                .withMessageContaining("email_outbox_no_header_injection");
    }

    @Test
    @DisplayName("a failure with no stated reason cannot be stored")
    void databaseRefusesAReasonlessFailure() {
        // The row this table exists to prevent: a silent drop wearing a status.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawOutboxRow(
                        "a@example.com", "Subject", "FAILED", null, null))
                .withMessageContaining("email_outbox_failure_states_a_reason");
    }

    @Test
    @DisplayName("a status that disagrees with its timestamps cannot be stored")
    void databaseRefusesImpossibleStates() {
        // PENDING with no due time — a message that would never be picked up again.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawOutboxRow(
                        "a@example.com", "Subject", "PENDING", null, null))
                .withMessageContaining("email_outbox_status_matches_timestamps");

        // SENT while still due.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawOutboxRow(
                        "a@example.com", "Subject", "SENT", "now()", "an error"))
                .withMessageContaining("email_outbox_status_matches_timestamps");
    }

    @Test
    @DisplayName("an unknown status cannot be stored")
    void databaseRefusesUnknownStatus() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertRawOutboxRow(
                        "a@example.com", "Subject", "QUEUED", "now()", null))
                .withMessageContaining("email_outbox_status_known");
    }

    @Test
    @DisplayName("a row already at its attempt limit fails alone, and does not block the queue")
    void aRowAtItsAttemptLimitDoesNotStopEverythingElse() {
        // The second door into the same batch-wide stall, and the one the original fix missed.
        //
        // This row is valid in every respect the first poison test checks — the address parses,
        // the subject is fine, it rebuilds into a perfectly good EmailMessage. What is wrong is
        // that it sits PENDING with attempts already at max_attempts, which satisfies every
        // CHECK at rest and which the service cannot produce. Claiming it increments attempts
        // past the limit, and that violation lands at flush, after the loop, where no catch can
        // reach it: the whole claim transaction rolls back, taking the healthy message with it,
        // every cycle, forever.
        jdbc.update("""
                INSERT INTO email_outbox
                    (to_addresses, subject, body, body_format, status, attempts, max_attempts,
                     last_attempt_at, next_attempt_at)
                VALUES (ARRAY['stuck@example.com'], 'Already exhausted', 'body', 'PLAIN_TEXT',
                        'PENDING', 3, 3, now(), now())
                """);
        Long stuckId = jdbc.queryForObject(
                "SELECT max(id) FROM email_outbox WHERE subject = 'Already exhausted'", Long.class);

        long healthy = emailSender.send(
                EmailMessage.to("customer@example.com", "Perfectly fine", "body"));

        assertThat(dispatcher.dispatchDue())
                .as("the healthy message in the same batch must still go out")
                .isEqualTo(1);
        assertThat(emailSender.find(healthy).orElseThrow().status()).isEqualTo(EmailStatus.SENT);

        QueuedEmailView stuck = emailSender.find(stuckId).orElseThrow();
        assertThat(stuck.status()).isEqualTo(EmailStatus.FAILED);
        assertThat(stuck.lastErrorIfAny())
                .hasValueSatisfying(error -> assertThat(error).contains("attempt limit"));
        assertThat(stuck.attempts())
                .as("it must not have been incremented past its own limit")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("a stored message that cannot be sent fails alone, and does not block the queue")
    void aPoisonRowDoesNotStopEverythingElse() {
        // Found the hard way: a raw-SQL probe left a row whose recipient EmailMessage refuses,
        // materialising the batch threw inside the claim transaction, and every subsequent
        // dispatch in the class sent nothing at all. One unusable row must fail on its own.
        //
        // Three differently-malformed rows rather than one, because the question worth answering
        // is whether the guard isolates a *class* of bad row or only the case that was
        // reproduced. Each of these fails a different check in rebuilding the message — no '@',
        // no domain suffix, and a bad address in cc rather than to — and each passes every
        // database CHECK, so all three are genuinely storable.
        insertRawPending("no-at-sign", null, "Unsendable A");
        insertRawPending("kostas@novotrade", null, "Unsendable B");
        insertRawPending("fine@example.com", "not-an-address", "Unsendable C");

        List<Long> poisonIds = jdbc.queryForList(
                "SELECT id FROM email_outbox WHERE subject LIKE 'Unsendable %' ORDER BY id",
                Long.class);
        assertThat(poisonIds).hasSize(3);

        long healthy = emailSender.send(
                EmailMessage.to("customer@example.com", "Perfectly fine", "body"));

        assertThat(dispatcher.dispatchDue())
                .as("the healthy message in the same batch must still go out")
                .isEqualTo(1);
        assertThat(emailSender.find(healthy).orElseThrow().status())
                .isEqualTo(EmailStatus.SENT);

        assertThat(poisonIds)
                .allSatisfy(id -> {
                    QueuedEmailView poison = emailSender.find(id).orElseThrow();
                    assertThat(poison.status()).isEqualTo(EmailStatus.FAILED);
                    assertThat(poison.lastErrorIfAny()).isPresent();
                });
    }

    /** A storable PENDING row that the service itself would never have written. */
    private void insertRawPending(String to, String cc, String subject) {
        jdbc.update("""
                INSERT INTO email_outbox
                    (to_addresses, cc_addresses, subject, body, body_format, status,
                     max_attempts, next_attempt_at)
                VALUES (ARRAY[?], CASE WHEN ?::text IS NULL THEN '{}'::text[] ELSE ARRAY[?] END,
                        ?, 'body', 'PLAIN_TEXT', 'PENDING', 3, now())
                """, to, cc, cc, subject);
    }

    @Test
    @DisplayName("the status CHECK lists exactly the values EmailStatus has")
    void statusCheckMatchesTheEnum() {
        // The same guarantee JournalIT makes for journal_source_is_amendable: a value added to
        // one side and not the other cannot hide. Counting the literals is what catches an
        // enum constant that the CHECK does not know about.
        String definition = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                  FROM pg_constraint
                 WHERE conname = 'email_outbox_status_known'
                """, String.class);

        assertThat(definition).isNotNull();
        for (EmailStatus status : EmailStatus.values()) {
            assertThat(definition).contains("'" + status.name() + "'");
        }
        assertThat(definition.split("'::character varying").length - 1)
                .as("the CHECK must not permit a status Java does not have")
                .isEqualTo(EmailStatus.values().length);
    }

    // -----------------------------------------------------------------------------------------

    private void pointAtADeadServer() {
        // Port 1 on the loopback interface: nothing is listening, and the connection is refused
        // immediately rather than timing out, so the test stays fast.
        settings.put(SettingKeys.SMTP_HOST, "127.0.0.1");
        settings.put(SettingKeys.SMTP_PORT, "1");
    }

    /** Makes a waiting message due immediately, instead of waiting out its real backoff. */
    private void makeDueNow(long id) {
        jdbc.update(
                "UPDATE email_outbox SET next_attempt_at = now() "
                        + "WHERE id = ? AND status = 'PENDING'", id);
    }

    private long countOutboxRows() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM email_outbox", Long.class);
        return count == null ? 0 : count;
    }

    private void insertRawOutboxRow(String recipient, String subject, String status,
            String nextAttemptAtExpression, String lastError) {
        jdbc.update("""
                INSERT INTO email_outbox
                    (to_addresses, subject, body, body_format, status, max_attempts,
                     next_attempt_at, last_error)
                VALUES (ARRAY[?], ?, 'body', 'PLAIN_TEXT', ?, 3, %s, ?)
                """.formatted(nextAttemptAtExpression == null ? "NULL" : nextAttemptAtExpression),
                recipient, subject, status, lastError);
    }

    /**
     * The viewer every download here passes.
     *
     * <p>Q44's access-path check re-reads the section governing the source record before
     * returning bytes, so downloading now needs a role. OWNER has full access, which keeps
     * these tests about the outbox rather than about permissions —
     * {@code EmailAttachmentAccessIT} is where the check itself is proven.
     */
    private RoleView owner() {
        return roles.requireByName("OWNER");
    }

}
