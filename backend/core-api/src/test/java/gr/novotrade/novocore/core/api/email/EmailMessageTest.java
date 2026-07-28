package gr.novotrade.novocore.core.api.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailMessageTest {

    @Test
    @DisplayName("the simple case is one call")
    void singleRecipient() {
        EmailMessage message = EmailMessage.to("kostas@novotrade.gr", "Subject", "Body");

        assertThat(message.to()).containsExactly("kostas@novotrade.gr");
        assertThat(message.cc()).isEmpty();
        assertThat(message.bcc()).isEmpty();
        assertThat(message.format()).isEqualTo(EmailBodyFormat.PLAIN_TEXT);
        assertThat(message.hasAttachments()).isFalse();
    }

    @Test
    @DisplayName("there is no way to set a From or a Reply-To on a message")
    void senderIdentityIsNotOnTheMessage() {
        // The whole point of the shared service. If a caller could name its own sender, one
        // module would eventually send as something else and replies would go to a mailbox
        // nobody reads — which is precisely what the configured Reply-To exists to prevent.
        assertThat(EmailMessage.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("from", "fromAddress", "replyTo", "sender");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "no-at-sign",
        "@novotrade.gr",
        "kostas@",
        "two@at@novotrade.gr",
        "kostas @novotrade.gr",
        "kostas@novotrade",
    })
    @DisplayName("addresses that would fail hours later as a bounce are refused now")
    void malformedAddressesAreRefused(String address) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailMessage.to(address, "Subject", "Body"));
    }

    @Test
    @DisplayName("a line break in an address is refused — it would end the header")
    void addressHeaderInjectionIsRefused() {
        // An address containing CRLF lets everything after it be read as further headers, which
        // is how a recipient list quietly becomes a Bcc to somebody else.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailMessage.to(
                        "kostas@novotrade.gr\r\nBcc: elsewhere@example.com",
                        "Subject", "Body"))
                .withMessageContaining("line break");
    }

    @Test
    @DisplayName("a line break in the subject is refused for the same reason")
    void subjectHeaderInjectionIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailMessage.to("kostas@novotrade.gr",
                        "Order confirmed\r\nBcc: elsewhere@example.com", "Body"))
                .withMessageContaining("line break");
    }

    @Test
    @DisplayName("a blank subject is refused rather than defaulted")
    void blankSubjectIsRefused() {
        // How a template that failed to interpolate presents itself.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailMessage.to("kostas@novotrade.gr", "   ", "Body"))
                .withMessageContaining("subject");
    }

    @Test
    @DisplayName("an empty body is allowed; a null one is not")
    void bodyMayBeEmptyButNotNull() {
        assertThat(EmailMessage.to("kostas@novotrade.gr", "Subject", "").body()).isEmpty();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailMessage.to("kostas@novotrade.gr", "Subject", null));
    }

    @Test
    @DisplayName("a message with no recipient is refused")
    void atLeastOneRecipient() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailMessage(List.of(), List.of(), List.of(),
                        "Subject", "Body", EmailBodyFormat.PLAIN_TEXT, List.of()))
                .withMessageContaining("at least one");
    }

    @Test
    @DisplayName("a repeated address is collapsed, so nobody gets the same mail twice")
    void duplicatesAreCollapsed() {
        EmailMessage message = EmailMessage.builder("kostas@novotrade.gr", "Subject", "Body")
                .to("kostas@novotrade.gr", "  kostas@novotrade.gr  ")
                .build();

        assertThat(message.to()).containsExactly("kostas@novotrade.gr");
    }

    @Test
    @DisplayName("addresses are trimmed, because a copy-pasted one carries whitespace")
    void addressesAreTrimmed() {
        assertThat(EmailMessage.to("  kostas@novotrade.gr  ", "Subject", "Body").to())
                .containsExactly("kostas@novotrade.gr");
    }

    @Test
    @DisplayName("cc and bcc are validated the same way as to")
    void copyRecipientsAreValidatedToo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailMessage.builder("kostas@novotrade.gr", "S", "B")
                        .cc("not-an-address")
                        .build());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailMessage.builder("kostas@novotrade.gr", "S", "B")
                        .bcc("also-not-an-address")
                        .build());
    }

    @Test
    @DisplayName("allRecipients covers all three fields")
    void allRecipientsSpansEveryField() {
        EmailMessage message = EmailMessage.builder("a@novotrade.gr", "S", "B")
                .cc("b@novotrade.gr")
                .bcc("c@novotrade.gr")
                .build();

        assertThat(message.allRecipients())
                .containsExactly("a@novotrade.gr", "b@novotrade.gr", "c@novotrade.gr");
    }

    @Test
    @DisplayName("attachments come back in the order they were given")
    void attachmentOrderIsPreserved() {
        EmailMessage message = EmailMessage.to("kostas@novotrade.gr", "Order", "See attached",
                EmailAttachment.pdf("po-1.pdf", bytes("one")),
                EmailAttachment.pdf("po-2.pdf", bytes("two")));

        assertThat(message.attachments())
                .extracting(EmailAttachment::filename)
                .containsExactly("po-1.pdf", "po-2.pdf");
        assertThat(message.hasAttachments()).isTrue();
    }

    @Test
    @DisplayName("an over-long subject is refused rather than silently truncated")
    void overLongSubjectIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmailMessage.to(
                        "kostas@novotrade.gr", "x".repeat(501), "Body"))
                .withMessageContaining("501");
    }

    @Test
    @DisplayName("html() marks the body, rather than the body being sniffed for angle brackets")
    void htmlIsDeclaredNotGuessed() {
        assertThat(EmailMessage.html("kostas@novotrade.gr", "S", "<p>Γειά</p>").format())
                .isEqualTo(EmailBodyFormat.HTML);
        // A plain-text body containing a comparison must not be reclassified as HTML.
        assertThat(EmailMessage.to("kostas@novotrade.gr", "S", "3 < 5").format())
                .isEqualTo(EmailBodyFormat.PLAIN_TEXT);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
