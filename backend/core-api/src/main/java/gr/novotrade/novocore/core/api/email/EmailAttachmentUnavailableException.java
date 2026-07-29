package gr.novotrade.novocore.core.api.email;

/**
 * Thrown when a sent message's attachment can no longer be produced.
 *
 * <p>Distinct from "no such attachment", which is an {@code IllegalArgumentException}: this one
 * says the row exists, we know what the file was called and how big it was, and the bytes are
 * gone. Conflating the two would make a mistyped id and a deleted document look identical, which
 * is the ambiguity {@code CLAUDE.md} rule 7 exists to refuse.
 *
 * <p>A UI should rarely see this. {@code SentEmailAttachmentView.available()} answers the same
 * question without asking for the bytes, so a history screen greys the entry out and shows
 * {@code unavailableReason} rather than offering a download that fails. This exists for the
 * caller that asks anyway.
 */
public class EmailAttachmentUnavailableException extends RuntimeException {

    private final long emailAttachmentId;
    private final String filename;

    public EmailAttachmentUnavailableException(long emailAttachmentId, String filename,
            String reason) {
        super("'%s' can no longer be produced: %s".formatted(filename, reason));
        this.emailAttachmentId = emailAttachmentId;
        this.filename = filename;
    }

    public long emailAttachmentId() {
        return emailAttachmentId;
    }

    /** What the file was called when it was sent — still known, and still worth reporting. */
    public String filename() {
        return filename;
    }
}
