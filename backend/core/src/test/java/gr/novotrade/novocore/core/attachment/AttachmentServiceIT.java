package gr.novotrade.novocore.core.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.attachment.AttachmentContent;
import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.attachment.AttachmentTooLargeException;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class AttachmentServiceIT extends AbstractCoreIntegrationTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7 pretend invoice".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private AttachmentService attachments;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private SettingsService settings;

    @Test
    @DisplayName("stores and retrieves a document byte-for-byte")
    void storesAndRetrievesExactly() {
        AttachmentMetadata stored = attachments.attach(
                "PurchaseInvoice", "inv-1", "supplier-invoice.pdf", "application/pdf", PDF_BYTES);

        AttachmentContent downloaded = attachments.download(stored.id()).orElseThrow();

        assertThat(downloaded.content()).isEqualTo(PDF_BYTES);
        assertThat(downloaded.metadata().filename()).isEqualTo("supplier-invoice.pdf");
        assertThat(downloaded.metadata().contentType()).isEqualTo("application/pdf");
        assertThat(downloaded.metadata().sizeBytes()).isEqualTo(PDF_BYTES.length);
    }

    @Test
    @DisplayName("records a SHA-256 checksum, so a restored file can be verified")
    void recordsChecksum() {
        AttachmentMetadata stored = attachments.attach(
                "PurchaseInvoice", "inv-checksum", "doc.pdf", "application/pdf", PDF_BYTES);

        // Compared against an independently computed digest, so this checks the stored value is
        // actually SHA-256 of the content rather than merely that the service agrees with itself.
        assertThat(stored.checksumSha256())
                .matches("[0-9a-f]{64}")
                .isEqualTo(sha256Hex(PDF_BYTES));
    }

    @Test
    @DisplayName("lists documents on a record newest first, without reading their content")
    void listsForEntityNewestFirst() {
        attachments.attach("Supplier", "sup-1", "first.pdf", "application/pdf", PDF_BYTES);
        attachments.attach("Supplier", "sup-1", "second.pdf", "application/pdf", PDF_BYTES);

        List<AttachmentMetadata> listed = attachments.listFor("Supplier", "sup-1");

        assertThat(listed).extracting(AttachmentMetadata::filename)
                .containsExactly("second.pdf", "first.pdf");
    }

    @Test
    @DisplayName("documents are scoped to their own record")
    void scopedToTheirRecord() {
        attachments.attach("Supplier", "sup-scope-a", "a.pdf", "application/pdf", PDF_BYTES);

        assertThat(attachments.listFor("Supplier", "sup-scope-b")).isEmpty();
        assertThat(attachments.listFor("Customer", "sup-scope-a")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "../../etc/passwd",
        "..\\..\\windows\\system32\\config",
        "/absolute/path/invoice.pdf",
        "C:\\Users\\kosta\\invoice.pdf"
    })
    @DisplayName("a filename is reduced to a bare name, so it cannot escape its record")
    void filenameIsSanitised(String dangerousFilename) {
        // Both separators are stripped regardless of host platform. A Linux server would not
        // treat backslashes as separators, so "..\..\secret" would survive as a "safe" name that
        // a Windows client then interprets on save.
        AttachmentMetadata stored = attachments.attach(
                "PurchaseInvoice", "inv-path", dangerousFilename, "application/pdf", PDF_BYTES);

        assertThat(stored.filename()).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
    }

    @Test
    @DisplayName("a filename with nothing usable left is rejected")
    void unusableFilenameRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> attachments.attach(
                "PurchaseInvoice", "inv-bad-name", "../", "application/pdf", PDF_BYTES));
    }

    @Test
    @DisplayName("an empty upload is rejected rather than stored as a document")
    void emptyUploadRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> attachments.attach(
                        "PurchaseInvoice", "inv-empty", "empty.pdf", "application/pdf",
                        new byte[0]))
                .withMessageContaining("failed upload");
    }

    @Test
    @DisplayName("a missing content type falls back rather than storing blank")
    void missingContentTypeFallsBack() {
        AttachmentMetadata stored = attachments.attach(
                "PurchaseInvoice", "inv-no-type", "unknown.bin", null, PDF_BYTES);

        assertThat(stored.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("an oversized document is refused, with the configured limit in the message")
    void oversizedDocumentRefused() {
        settings.put("attachment.max-size-bytes", "64");

        try {
            byte[] tooBig = new byte[65];
            assertThatExceptionOfType(AttachmentTooLargeException.class)
                    .isThrownBy(() -> attachments.attach(
                            "PurchaseInvoice", "inv-big", "big.pdf", "application/pdf", tooBig))
                    .withMessageContaining("65")
                    .withMessageContaining("64");
        } finally {
            settings.put("attachment.max-size-bytes", "26214400");
        }
    }

    @Test
    @DisplayName("adding a document is audited against the record it belongs to")
    void additionIsAudited() {
        AttachmentMetadata stored = attachments.attach(
                "PurchaseInvoice", "inv-audited", "audited.pdf", "application/pdf", PDF_BYTES);

        List<AuditEntry> entries =
                auditLog.findForEntity("PurchaseInvoice", "inv-audited", 10);

        assertThat(entries).isNotEmpty();
        assertThat(entries.getFirst().action()).isEqualTo("attachment.added");
        assertThat(entries.getFirst().detail())
                .containsEntry("filename", "audited.pdf")
                .containsEntry("checksumSha256", stored.checksumSha256());
    }

    @Test
    @DisplayName("deletion removes the document and leaves a traceable audit entry")
    void deletionIsAudited() {
        AttachmentMetadata stored = attachments.attach(
                "PurchaseInvoice", "inv-deleted", "gone.pdf", "application/pdf", PDF_BYTES);

        assertThat(attachments.delete(stored.id())).isTrue();
        assertThat(attachments.download(stored.id())).isEmpty();
        assertThat(attachments.listFor("PurchaseInvoice", "inv-deleted")).isEmpty();

        // The entry has to stay meaningful once the bytes are gone, so it carries the filename
        // and checksum rather than just an id.
        assertThat(auditLog.findForEntity("PurchaseInvoice", "inv-deleted", 10))
                .anySatisfy(entry -> {
                    assertThat(entry.action()).isEqualTo("attachment.deleted");
                    assertThat(entry.detail()).containsEntry("filename", "gone.pdf");
                    assertThat(entry.detail())
                            .containsEntry("checksumSha256", stored.checksumSha256());
                });
    }

    @Test
    @DisplayName("deleting something absent reports false rather than throwing")
    void deletingAbsentReportsFalse() {
        assertThat(attachments.delete(999_999L)).isFalse();
    }

    @Test
    @DisplayName("metadata can be read without pulling the content")
    void metadataWithoutContent() {
        AttachmentMetadata stored = attachments.attach(
                "Asset", "asset-1", "warranty.pdf", "application/pdf", PDF_BYTES);

        assertThat(attachments.findMetadata(stored.id()))
                .get()
                .satisfies(metadata -> assertThat(metadata.filename()).isEqualTo("warranty.pdf"));
        assertThat(attachments.findMetadata(999_999L)).isEmpty();
    }

    private static String sha256Hex(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
