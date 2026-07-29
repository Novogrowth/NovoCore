package gr.novotrade.novocore.core.api.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.catchThrowable;

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
        assertThat(EmailAttachment.of("/tmp/reports/june.pdf", "application/pdf", CONTENT)
                .filename()).isEqualTo("june.pdf");
        assertThat(EmailAttachment.of("C:\\temp\\june.pdf", "application/pdf", CONTENT)
                .filename()).isEqualTo("june.pdf");
    }

    @Test
    @DisplayName("a name that is nothing but separators leaves nothing usable")
    void unusableFilenamesAreRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailAttachment.of("../", "application/pdf", CONTENT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailAttachment.of("  ", "application/pdf", CONTENT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailAttachment.of(null, "application/pdf", CONTENT));
    }

    @Test
    @DisplayName("a line break in a filename is refused, not stripped")
    void filenameHeaderInjectionIsRefused() {
        // It would end the Content-Disposition header. Refused rather than cleaned, because a
        // filename containing one was not typed by accident.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailAttachment.of(
                        "june.pdf\r\nContent-Type: text/html", "application/pdf", CONTENT))
                .withMessageContaining("line break");
    }

    @Test
    @DisplayName("an empty attachment is a failed generation, not a document")
    void emptyContentIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailAttachment.of("june.pdf", "application/pdf", new byte[0]))
                .withMessageContaining("empty");
    }

    @Test
    @DisplayName("a missing content type falls back to the generic one rather than to null")
    void contentTypeDefaults() {
        assertThat(EmailAttachment.of("june.pdf", null, CONTENT).contentType())
                .isEqualTo(EmailAttachment.DEFAULT_CONTENT_TYPE);
        assertThat(EmailAttachment.of("june.pdf", "  ", CONTENT).contentType())
                .isEqualTo(EmailAttachment.DEFAULT_CONTENT_TYPE);
    }

    @Test
    @DisplayName("pdf() is the shape most callers want")
    void pdfFactory() {
        EmailAttachment attachment = EmailAttachment.pdf("po-42.pdf", CONTENT);

        assertThat(attachment.isStored()).isFalse();
        assertThat(attachment.filename()).isEqualTo("po-42.pdf");
        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(attachment.content()).isEqualTo(CONTENT);
    }

    // ---------------------------------------------------------------------------------------
    // The referenced shape
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a stored attachment carries a reference and nothing else")
    void storedCarriesNoBytes() {
        EmailAttachment attachment = EmailAttachment.stored(42L);

        assertThat(attachment.isStored()).isTrue();
        assertThat(attachment.storedAttachmentId()).isEqualTo(42L);
        // The whole point: no second copy of the file, and nothing here that could disagree with
        // the document itself. Name and type are read from it when the message is queued.
        assertThat(attachment.filename()).isNull();
        assertThat(attachment.contentType()).isNull();
    }

    @Test
    @DisplayName("asking a stored attachment for bytes says where to get them")
    void storedContentThrowsRatherThanReturningNull() {
        // Throws rather than returning null, because the alternative is a NullPointerException
        // several frames from the mistake with nothing naming the cause.
        assertThatIllegalStateException()
                .isThrownBy(() -> EmailAttachment.stored(42L).content())
                .withMessageContaining("downloadAttachment");
    }

    @Test
    @DisplayName("asking an inline attachment for its document id says it has none")
    void inlineHasNoStoredId() {
        assertThatIllegalStateException()
                .isThrownBy(() -> EmailAttachment.pdf("po-42.pdf", CONTENT).storedAttachmentId())
                .withMessageContaining("po-42.pdf");
    }

    @Test
    @DisplayName("an attachment is one shape or the other, never both and never neither")
    void exactlyOneShape() {
        // A reference that also carries a copy is the shape that would defeat the point of
        // referencing at all — two versions of the same file, free to disagree.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAttachment(42L, "june.pdf", "application/pdf", CONTENT))
                .withMessageContaining("must not also carry");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAttachment(42L, "june.pdf", null, null))
                .withMessageContaining("must not also carry");

        // And neither shape at all is not an empty attachment, it is an unanswerable one.
        assertThat(catchThrowable(() -> new EmailAttachment(null, "june.pdf", null, null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("an attachment id that could not name a row is refused")
    void nonPositiveIdsAreRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailAttachment.stored(0L))
                .withMessageContaining("positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailAttachment.stored(-1L))
                .withMessageContaining("positive");
    }
}
