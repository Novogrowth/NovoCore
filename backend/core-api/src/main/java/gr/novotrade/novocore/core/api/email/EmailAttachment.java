package gr.novotrade.novocore.core.api.email;

import java.util.Objects;

/**
 * A file to attach to an outgoing message.
 *
 * <p>Deliberately not {@code AttachmentMetadata} plus an id. A module sending a Purchase Order
 * PDF has just generated the bytes and has no reason to store them against a core record first,
 * and a report emailed monthly is not a document anyone wants a permanent copy of on the
 * invoice. Where a caller <em>does</em> want to send something already stored, it reads it back
 * through {@code AttachmentService.download} and wraps it here — one line, and it keeps the two
 * shared services independent of each other.
 *
 * <p>The array is not copied, matching {@code AttachmentContent}. Treat {@link #content} as
 * read-only.
 */
public record EmailAttachment(String filename, String contentType, byte[] content) {

    /** What an attachment with no stated type is sent as. */
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public EmailAttachment {
        Objects.requireNonNull(content, "content");
        filename = sanitiseFilename(filename);
        contentType = contentType == null || contentType.isBlank()
                ? DEFAULT_CONTENT_TYPE
                : contentType.trim();

        if (content.length == 0) {
            // Same stance as AttachmentService: an empty file is a failed generation, not an
            // intent. Attaching it produces a mail whose recipient opens nothing and has no way
            // to tell that anything went wrong.
            throw new IllegalArgumentException(
                    "Attachment '%s' is empty. An empty attachment is a failed generation, not a "
                            .formatted(filename) + "document.");
        }
    }

    /** An attachment named and typed as a PDF, which is what most callers here are sending. */
    public static EmailAttachment pdf(String filename, byte[] content) {
        return new EmailAttachment(filename, "application/pdf", content);
    }

    /**
     * Reduces a supplied filename to a bare name, handling both separators regardless of host
     * platform — the same rule and the same reasoning as {@code AttachmentService}. A filename
     * is copied verbatim into a MIME header and then used by the recipient's mail client to
     * name a file on their disk, so a directory component in it is worth removing on the way
     * out as well as on the way in.
     */
    private static String sanitiseFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("An attachment must have a filename");
        }
        // Checked against the raw input, BEFORE the directory component is stripped, and the
        // order is load-bearing. A test caught it the other way round: given
        // "june.pdf\r\nContent-Type: text/html", the strip runs to the last '/' — which is the
        // one inside the injected header — and leaves "html", a name with no line break in it
        // and nothing left to refuse. Sanitising first can therefore destroy the evidence that
        // the value should have been rejected outright.
        if (filename.chars().anyMatch(c -> c == '\r' || c == '\n')) {
            throw new IllegalArgumentException(
                    "An attachment filename must not contain a line break — it is written into a "
                            + "Content-Disposition header, where one would let the rest of the "
                            + "name be read as further headers.");
        }

        String name = filename.trim();
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            name = name.substring(lastSeparator + 1);
        }
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException(
                    "'%s' does not contain a usable filename.".formatted(filename));
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
