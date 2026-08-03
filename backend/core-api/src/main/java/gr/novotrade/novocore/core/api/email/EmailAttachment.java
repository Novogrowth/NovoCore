package gr.novotrade.novocore.core.api.email;

import gr.novotrade.novocore.core.api.shared.ConditionallyMandatory;
import java.util.Objects;

/**
 * A file to attach to an outgoing message, in one of two shapes.
 *
 * <h2>Referenced, when the file is already a stored document</h2>
 *
 * <p>{@link #stored(long)} names an {@code AttachmentService} record and carries no bytes. The
 * outbox keeps the reference plus enough of the document's identity — filename, content type,
 * size and checksum, snapshotted at queue time — to describe what was sent; the bytes stay in the
 * one place that owns them. Emailing an invoice PDF that is also attached to the invoice
 * therefore stores that file once, not twice, and a year of sent-email history costs rows rather
 * than megabytes.
 *
 * <p>This is the same principle as {@link EmailSender} itself: one door, no duplicate
 * implementations. A stored document has an owner already, and the outbox is not it.
 *
 * <h2>Inline, when the file exists nowhere else</h2>
 *
 * <p>{@link #pdf} and {@link #of} carry bytes. A Purchase Order PDF generated at approval time,
 * or a monthly report, is not a document anyone wants a permanent copy of on a core record —
 * there is nothing to reference, and forcing one into the attachment table would move those bytes
 * rather than save them, while filling a table of "documents on core records" with things that
 * are neither.
 *
 * <p>Stored inline rather than regenerated at send time, deliberately: a Purchase Order PDF is
 * generated from data that can change between the order being approved and the mail going out,
 * and a retry three minutes later must send the document that was approved, not a fresh rendering
 * of whatever the order looks like now.
 *
 * <p>Inline bytes are consequently the only part of the outbox that grows without bound, which is
 * what makes a retention policy for them a separate question from how long outbox <em>rows</em>
 * are kept. See {@code SentEmailAttachmentView} for the state a pruned copy leaves behind.
 *
 * <h2>Exactly one shape</h2>
 *
 * <p>Enforced in the constructor and, for anything writing to the table directly, by CHECK
 * constraints — the same arrangement as a journal line carrying a VAT class or an exemption
 * reason but never both and never neither.
 *
 * <p>The array is not copied, matching {@code AttachmentContent}. Treat the content as read-only.
 */
public record EmailAttachment(
        Long attachmentId,
        String filename,
        String contentType,
        @ConditionallyMandatory("required only when attachmentId is absent; forbidden when it is "
                + "present, because a stored attachment must not also carry a copy")
        byte[] content) {

    /** What an attachment with no stated type is sent as. */
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public EmailAttachment {
        if (attachmentId != null) {
            if (attachmentId <= 0) {
                throw new IllegalArgumentException(
                        "An attachment id must be positive, not " + attachmentId);
            }
            if (filename != null || contentType != null || content != null) {
                // The shape that would defeat the point: a reference that also carries a copy.
                throw new IllegalArgumentException(
                        "A stored attachment references document %d and must not also carry its "
                                .formatted(attachmentId)
                                + "own filename, content type or bytes — those are read from the "
                                + "document itself, so a second copy here could disagree with it.");
            }
        } else {
            Objects.requireNonNull(content,
                    "An attachment must either reference a stored document or carry its content");
            filename = sanitiseFilename(filename);
            contentType = contentType == null || contentType.isBlank()
                    ? DEFAULT_CONTENT_TYPE
                    : contentType.trim();

            if (content.length == 0) {
                // Same stance as AttachmentService: an empty file is a failed generation, not an
                // intent. Attaching it produces a mail whose recipient opens nothing and has no
                // way to tell that anything went wrong.
                throw new IllegalArgumentException(
                        "Attachment '%s' is empty. An empty attachment is a failed generation, not a "
                                .formatted(filename) + "document.");
            }
        }
    }

    /**
     * Attaches a document already held by {@code AttachmentService}, by its id.
     *
     * <p>The reference is checked when the message is queued, not when it is sent: an id that
     * names nothing is a mistake in the calling code, and refusing it there fails the operation
     * that made it rather than surfacing hours later as an outbox row nobody is watching.
     *
     * <p>If the document is deleted <em>after</em> queueing but before the message goes out, the
     * message fails visibly and says so — a mail is never sent with an attachment silently
     * missing. Once it has been sent, deleting the document leaves the history entry naming the
     * file and reporting it as no longer available.
     */
    public static EmailAttachment stored(long attachmentId) {
        return new EmailAttachment(attachmentId, null, null, null);
    }

    /** A file that exists only for this message, with its bytes. */
    public static EmailAttachment of(String filename, String contentType, byte[] content) {
        return new EmailAttachment(null, filename, contentType, content);
    }

    /** An inline attachment named and typed as a PDF, which is what most callers here are sending. */
    public static EmailAttachment pdf(String filename, byte[] content) {
        return of(filename, "application/pdf", content);
    }

    /** True when this names a stored document rather than carrying its own bytes. */
    public boolean isStored() {
        return attachmentId != null;
    }

    /**
     * The stored document's id.
     *
     * @throws IllegalStateException if this attachment carries its own bytes instead
     */
    public long storedAttachmentId() {
        if (attachmentId == null) {
            throw new IllegalStateException(
                    "'%s' carries its own bytes and references no stored document"
                            .formatted(filename));
        }
        return attachmentId;
    }

    /**
     * The bytes to transmit.
     *
     * <p>Throws rather than returning null on a stored attachment, because the alternative is a
     * {@code NullPointerException} several frames away from the mistake. Nothing outside this
     * service needs to resolve a reference by hand: the dispatcher does it at send time and
     * {@code EmailSender.downloadAttachment} does it for the sent-email history, so both shapes
     * behave identically at the point of use.
     *
     * @throws IllegalStateException if this attachment references a stored document
     */
    @Override
    public byte[] content() {
        if (attachmentId != null) {
            throw new IllegalStateException(
                    ("This attachment references stored document %d and holds no bytes of its "
                            + "own. Read it through EmailSender.downloadAttachment, which resolves "
                            + "both shapes the same way.").formatted(attachmentId));
        }
        return content;
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
