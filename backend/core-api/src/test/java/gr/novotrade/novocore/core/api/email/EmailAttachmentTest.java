package gr.novotrade.novocore.core.api.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailAttachmentTest {

    private static final byte[] CONTENT = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("a directory component is stripped, whichever separator was used")
    void directoryComponentsAreStripped() {
        // Both separators regardless of host platform, for the reason AttachmentService gives:
        // a name generated on Windows carries backslashes, and a server on Linux would leave
        // "..\..\secret" intact as a "safe" name that the recipient's Windows client then acts on.
        assertThat(new EmailAttachment("/tmp/reports/june.pdf", "application/pdf", CONTENT)
                .filename()).isEqualTo("june.pdf");
        assertThat(new EmailAttachment("C:\\temp\\june.pdf", "application/pdf", CONTENT)
                .filename()).isEqualTo("june.pdf");
    }

    @Test
    @DisplayName("a name that is nothing but separators leaves nothing usable")
    void unusableFilenamesAreRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAttachment("../", "application/pdf", CONTENT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAttachment("  ", "application/pdf", CONTENT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAttachment(null, "application/pdf", CONTENT));
    }

    @Test
    @DisplayName("a line break in a filename is refused, not stripped")
    void filenameHeaderInjectionIsRefused() {
        // It would end the Content-Disposition header. Refused rather than cleaned, because a
        // filename containing one was not typed by accident.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAttachment(
                        "june.pdf\r\nContent-Type: text/html", "application/pdf", CONTENT))
                .withMessageContaining("line break");
    }

    @Test
    @DisplayName("an empty attachment is a failed generation, not a document")
    void emptyContentIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAttachment("june.pdf", "application/pdf", new byte[0]))
                .withMessageContaining("empty");
    }

    @Test
    @DisplayName("a missing content type falls back to the generic one rather than to null")
    void contentTypeDefaults() {
        assertThat(new EmailAttachment("june.pdf", null, CONTENT).contentType())
                .isEqualTo(EmailAttachment.DEFAULT_CONTENT_TYPE);
        assertThat(new EmailAttachment("june.pdf", "  ", CONTENT).contentType())
                .isEqualTo(EmailAttachment.DEFAULT_CONTENT_TYPE);
    }

    @Test
    @DisplayName("pdf() is the shape most callers want")
    void pdfFactory() {
        EmailAttachment attachment = EmailAttachment.pdf("po-42.pdf", CONTENT);

        assertThat(attachment.filename()).isEqualTo("po-42.pdf");
        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(attachment.content()).isEqualTo(CONTENT);
    }
}
