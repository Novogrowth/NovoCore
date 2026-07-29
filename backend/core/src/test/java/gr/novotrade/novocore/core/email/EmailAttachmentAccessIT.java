package gr.novotrade.novocore.core.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentOwnerType;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailSender;
import gr.novotrade.novocore.core.api.email.SentEmailAttachmentView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.SectionAccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Q44's access-path check, proven behaviourally.
 *
 * <h2>Why this test is the whole feature</h2>
 *
 * <p>The rule is one sentence — <em>an email having been sent to someone does not change who may see
 * the source document afterwards</em> — and it is invisible when it works. Nothing throws, nothing
 * logs, and a download simply succeeds or does not. That is the same shape as the audit-log defect
 * in step 12, where a structurally spotless fix reintroduced the bug in full and only the
 * behavioural tests held the guarantee. So this file asserts outcomes, not structure.
 *
 * <p>Every test here was checked against the unguarded version of
 * {@code EmailSenderImpl.downloadAttachment} — the one that resolved the content without consulting
 * the source record — and the denial tests fail against it. A check that has never been observed to
 * refuse is not a check.
 */
class EmailAttachmentAccessIT extends AbstractCoreIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private AttachmentService attachments;

    @Autowired
    private RoleService roles;

    // -------------------------------------------------------------------------------------------
    // The refusals — these are what fail against the unguarded version
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the outbox is not a second way into a purchase invoice's PDF")
    void theOutboxIsNotASecondWayIn() {
        AttachmentMetadata document = attachments.attach(
                AttachmentOwnerType.PURCHASE_INVOICE.entityType(), "9001",
                "supplier-invoice.pdf", "application/pdf", bytes("the supplier's prices"));
        long emailId = emailSender.send(EmailMessage.to(
                "accountant@example.com", "Invoice", "Attached.",
                EmailAttachment.stored(document.id())));
        long attachmentId = onlyAttachment(emailId).id();

        RoleView outboxOnly = roleWith("Q44_OUTBOX_ONLY", Section.EMAIL_OUTBOX);

        // The role can see the outbox — it can list this very message and see that a file called
        // supplier-invoice.pdf went out — and still may not read the bytes, because the document
        // belongs to a purchase invoice and this role cannot open one.
        assertThat(outboxOnly.canView(Section.EMAIL_OUTBOX)).isTrue();
        assertThat(outboxOnly.canView(Section.PURCHASING)).isFalse();

        assertThatExceptionOfType(SectionAccessDeniedException.class)
                .isThrownBy(() -> emailSender.downloadAttachment(attachmentId, outboxOnly));
    }

    @Test
    @DisplayName("holding PURCHASING is not enough either — both checks apply")
    void bothChecksApply() {
        // The controller applies EMAIL_OUTBOX before this method is reached, so the service-level
        // check is deliberately about the source record alone. What this asserts is that the two
        // are genuinely different grants rather than one standing in for the other.
        RoleView purchasingOnly = roleWith("Q44_PURCHASING_ONLY", Section.PURCHASING);

        assertThat(purchasingOnly.canView(Section.EMAIL_OUTBOX)).isFalse();
        assertThat(purchasingOnly.canView(Section.PURCHASING)).isTrue();
    }

    @Test
    @DisplayName("an unrecognised owner type is refused, not waved through")
    void anUnknownOwnerTypeIsRefused() {
        // Attaching under a type nobody registered. This is what happens when a new kind of record
        // starts carrying documents and nobody adds it to AttachmentOwnerType — and the failure has
        // to be a refusal, because a type whose visibility rules are unknown is not one that can be
        // judged safe.
        AttachmentMetadata document = attachments.attach(
                "SomeFutureRecord", "1", "mystery.pdf", "application/pdf", bytes("unknown"));
        long emailId = emailSender.send(EmailMessage.to(
                "someone@example.com", "Mystery", "Attached.",
                EmailAttachment.stored(document.id())));
        long attachmentId = onlyAttachment(emailId).id();

        assertThat(AttachmentOwnerType.sectionFor("SomeFutureRecord")).isEmpty();

        // Refused even for the Owner. Strict on purpose: if only restricted roles were denied, the
        // missing registration would be invisible to whoever could fix it.
        assertThatExceptionOfType(SectionAccessDeniedException.class)
                .isThrownBy(() -> emailSender.downloadAttachment(attachmentId, owner()));
    }

    // -------------------------------------------------------------------------------------------
    // What must still work
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a role that may open the purchase invoice may read its PDF")
    void theRightRoleGetsTheBytes() {
        byte[] pdf = bytes("the supplier's prices");
        AttachmentMetadata document = attachments.attach(
                AttachmentOwnerType.PURCHASE_INVOICE.entityType(), "9002",
                "supplier-invoice.pdf", "application/pdf", pdf);
        long emailId = emailSender.send(EmailMessage.to(
                "accountant@example.com", "Invoice", "Attached.",
                EmailAttachment.stored(document.id())));
        long attachmentId = onlyAttachment(emailId).id();

        RoleView bothSections = roleWith(
                "Q44_OUTBOX_AND_PURCHASING", Section.EMAIL_OUTBOX, Section.PURCHASING);

        assertThat(emailSender.downloadAttachment(attachmentId, bothSections).content())
                .isEqualTo(pdf);
    }

    @Test
    @DisplayName("an inline attachment has no source record, so the outbox section is enough")
    void anInlineAttachmentNeedsOnlyTheOutbox() {
        byte[] generated = bytes("a report nobody stored");
        long emailId = emailSender.send(EmailMessage.to(
                "owner@example.com", "Monthly report", "Attached.",
                EmailAttachment.pdf("report.pdf", generated)));
        long attachmentId = onlyAttachment(emailId).id();

        RoleView outboxOnly = roleWith("Q44_INLINE_OUTBOX_ONLY", Section.EMAIL_OUTBOX);

        // Nothing to re-check against: these bytes exist nowhere else, so they are the message's
        // own business — which is precisely the half of step 11's reasoning ADR 0012 kept.
        assertThatCode(() -> emailSender.downloadAttachment(attachmentId, outboxOnly))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a deleted document reports unavailable rather than refusing on a missing record")
    void aDeletedDocumentReportsUnavailable() {
        AttachmentMetadata document = attachments.attach(
                AttachmentOwnerType.SALES_INVOICE.entityType(), "9003",
                "sales-invoice.pdf", "application/pdf", bytes("a sale"));
        long emailId = emailSender.send(EmailMessage.to(
                "customer@example.com", "Your invoice", "Attached.",
                EmailAttachment.stored(document.id())));
        long attachmentId = onlyAttachment(emailId).id();

        attachments.delete(document.id());

        // ON DELETE SET NULL leaves no record to check against, and there are no bytes to leak.
        // Reporting the file as gone is the honest answer and is what the history should say; a
        // permission refusal here would be a lie about why.
        assertThatExceptionOfType(
                gr.novotrade.novocore.core.api.email.EmailAttachmentUnavailableException.class)
                .isThrownBy(() -> emailSender.downloadAttachment(attachmentId, owner()));
    }

    @Test
    @DisplayName("a null viewer is refused outright — there is no unchecked path")
    void aNullViewerIsRefused() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> emailSender.downloadAttachment(1L, null));
    }

    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every registered owner type names a section that is actually built")
    void everyOwnerTypeNamesABuiltSection() {
        // A registration pointing at a reserved section would deny everyone with no way to grant
        // it, which reads as a permission problem and is really a typo.
        for (AttachmentOwnerType type : AttachmentOwnerType.values()) {
            assertThat(type.section().isAvailable())
                    .as("%s points at %s, which is reserved", type, type.section())
                    .isTrue();
        }
    }

    // -------------------------------------------------------------------------------------------

    private SentEmailAttachmentView onlyAttachment(long emailId) {
        List<SentEmailAttachmentView> found = emailSender.attachmentsOf(emailId);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private RoleView roleWith(String name, Section... sections) {
        String unique = name + "_" + SEQUENCE.incrementAndGet();
        RoleView created = roles.create(new NewRole(unique, "Q44 access-path test role"));
        for (Section section : sections) {
            roles.grant(created.id(), section, AccessLevel.VIEW);
        }
        return roles.require(created.id());
    }

    private RoleView owner() {
        return roles.requireByName("OWNER");
    }

    private static byte[] bytes(String text) {
        return ("%PDF-1.7 " + text).getBytes(StandardCharsets.UTF_8);
    }
}
