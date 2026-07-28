package gr.novotrade.novocore.core.api.email;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One outgoing message, as the sender describes it.
 *
 * <p><strong>The sender identity is deliberately absent.</strong> There is no {@code from} and
 * no {@code replyTo} field, because a caller must not be able to choose either: the From address
 * and the Reply-To header are configuration, resolved from Settings at send time and applied to
 * every message identically. Letting a module pass its own would recreate exactly the scattered
 * configuration {@code CLAUDE.md}'s shared-service rule exists to prevent — one module quietly
 * sending as something else, discovered when a customer replies into a mailbox nobody reads.
 *
 * <p>Addresses and the subject are validated here rather than at send time, so a malformed one
 * is refused by the caller's own operation instead of surfacing hours later as a failed outbox
 * row nobody is watching.
 */
public record EmailMessage(
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String body,
        EmailBodyFormat format,
        List<EmailAttachment> attachments) {

    /** Subjects longer than this are refused; RFC 5322 recommends far shorter lines still. */
    private static final int MAX_SUBJECT_LENGTH = 500;

    public EmailMessage {
        to = normaliseAddresses(to, "to");
        cc = normaliseAddresses(cc, "cc");
        bcc = normaliseAddresses(bcc, "bcc");
        Objects.requireNonNull(format, "format");
        attachments = attachments == null ? List.of() : List.copyOf(attachments);

        if (to.isEmpty()) {
            throw new IllegalArgumentException("An email must have at least one 'to' recipient");
        }
        if (subject == null || subject.isBlank()) {
            // A blank subject is how a template that failed to interpolate presents itself, and
            // it is also what most spam filters weight against. Refused rather than defaulted.
            throw new IllegalArgumentException("An email must have a subject");
        }
        subject = subject.trim();
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException(
                    "Subject is %d characters; the limit is %d"
                            .formatted(subject.length(), MAX_SUBJECT_LENGTH));
        }
        requireNoLineBreak(subject, "subject");

        if (body == null) {
            throw new IllegalArgumentException("An email must have a body (use \"\" for none)");
        }
    }

    /** A plain-text message to one recipient, which is most of them. */
    public static EmailMessage to(String recipient, String subject, String body) {
        return builder(recipient, subject, body).build();
    }

    /** A plain-text message to one recipient with attachments — the Purchase Order PDF case. */
    public static EmailMessage to(String recipient, String subject, String body,
            EmailAttachment... attachments) {
        return builder(recipient, subject, body).attach(attachments).build();
    }

    /** An HTML message to one recipient. */
    public static EmailMessage html(String recipient, String subject, String htmlBody) {
        return builder(recipient, subject, htmlBody).format(EmailBodyFormat.HTML).build();
    }

    public static Builder builder(String recipient, String subject, String body) {
        return new Builder(recipient, subject, body);
    }

    /** Every address the message is addressed to, across all three fields. */
    public List<String> allRecipients() {
        List<String> all = new ArrayList<>(to);
        all.addAll(cc);
        all.addAll(bcc);
        return List.copyOf(all);
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    /**
     * Checks an address well enough to catch the mistakes that actually happen, and no further.
     *
     * <p>Full RFC 5322 validation is famously not worth attempting — the grammar admits
     * addresses no mail server would accept and rejects some it would. What is checked here is
     * what a wrong value looks like in practice: something with no {@code @}, something with
     * whitespace in it, an empty local or domain part, and a line break. That last one is not
     * cosmetic: an address containing {@code \r\n} would end the header and let everything after
     * it be read as further headers, which is how a recipient list becomes a Bcc to somebody
     * else. Anything subtler than these is left to the SMTP server, which is the only thing that
     * genuinely knows.
     */
    private static String validateAddress(String address, String field) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("A '%s' address must not be blank".formatted(field));
        }
        String trimmed = address.trim();
        requireNoLineBreak(trimmed, field + " address");

        int at = trimmed.indexOf('@');
        if (at <= 0 || at != trimmed.lastIndexOf('@') || at == trimmed.length() - 1) {
            throw new IllegalArgumentException(
                    "'%s' is not a usable email address (%s)".formatted(trimmed, field));
        }
        if (trimmed.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "'%s' contains whitespace and is not a usable email address (%s)"
                            .formatted(trimmed, field));
        }
        if (!trimmed.substring(at + 1).contains(".")) {
            // Catches the local-hostname typo ("kostas@novotrade") that would be delivered
            // nowhere and reported as a hard bounce hours later.
            throw new IllegalArgumentException(
                    "'%s' has no domain suffix and is not a usable email address (%s)"
                            .formatted(trimmed, field));
        }
        return trimmed;
    }

    private static List<String> normaliseAddresses(List<String> addresses, String field) {
        if (addresses == null) {
            return List.of();
        }
        return addresses.stream()
                .map(address -> validateAddress(address, field))
                .distinct()
                .toList();
    }

    private static void requireNoLineBreak(String value, String field) {
        if (value.chars().anyMatch(c -> c == '\r' || c == '\n')) {
            throw new IllegalArgumentException(
                    "The %s must not contain a line break — it is written into a mail header, "
                            .formatted(field)
                            + "where one would let the rest of the value be read as further "
                            + "headers.");
        }
    }

    /** Assembles a message; every field except the three constructor arguments is optional. */
    public static final class Builder {

        private final List<String> to = new ArrayList<>();
        private final List<String> cc = new ArrayList<>();
        private final List<String> bcc = new ArrayList<>();
        private final List<EmailAttachment> attachments = new ArrayList<>();
        private final String subject;
        private final String body;
        private EmailBodyFormat format = EmailBodyFormat.PLAIN_TEXT;

        private Builder(String recipient, String subject, String body) {
            this.to.add(recipient);
            this.subject = subject;
            this.body = body;
        }

        public Builder to(String... recipients) {
            this.to.addAll(List.of(recipients));
            return this;
        }

        public Builder cc(String... recipients) {
            this.cc.addAll(List.of(recipients));
            return this;
        }

        public Builder bcc(String... recipients) {
            this.bcc.addAll(List.of(recipients));
            return this;
        }

        public Builder attach(EmailAttachment... files) {
            this.attachments.addAll(List.of(files));
            return this;
        }

        public Builder format(EmailBodyFormat format) {
            this.format = format;
            return this;
        }

        public EmailMessage build() {
            return new EmailMessage(to, cc, bcc, subject, body, format, attachments);
        }
    }
}
