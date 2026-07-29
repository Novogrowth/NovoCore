package gr.novotrade.novocore.core.api.email;

import java.util.Optional;

/**
 * One attachment on a message in the outbox, as the sent-email history shows it.
 *
 * <p><strong>Identical for both shapes.</strong> Whether the bytes live in the outbox or in
 * {@code AttachmentService}, this says the same things about them — name, type, size — and
 * {@code EmailSender.downloadAttachment} takes the same {@link #id} either way. A reader of the
 * sent-email record never has to know where the file is kept, and never has to go and find it
 * somewhere else. That transparency is the point of referencing rather than copying: it must cost
 * the caller nothing.
 *
 * @param id the outbox attachment's own id — what {@code EmailSender.downloadAttachment} takes,
 *     for either shape
 * @param filename as it was sent, snapshotted at queue time
 * @param sizeBytes as it was sent, so the history states the size even once the bytes are gone
 * @param storedAttachmentId the {@code AttachmentService} document this references, absent for an
 *     inline attachment and also absent once a referenced document has been deleted
 * @param unavailableReason why the bytes can no longer be produced, or null when they can
 */
public record SentEmailAttachmentView(
        long id,
        int attachmentOrder,
        String filename,
        String contentType,
        long sizeBytes,
        EmailAttachmentSource source,
        Long storedAttachmentId,
        String unavailableReason) {

    /**
     * Whether the file can still be produced.
     *
     * <p>False is a normal state, not a broken one, and it is reached two ways: a referenced
     * document was deleted from the record it was attached to, or an inline copy was pruned by a
     * retention policy. Both leave a history entry that still names the file, states its size and
     * says what happened to it — which is what a sent-email record is for. The alternative,
     * making either deletion impossible, would mean an email sent in 2026 pins a document
     * forever.
     */
    public boolean available() {
        return unavailableReason == null;
    }

    /** Present exactly when {@link #available()} is false; the sentence to show a reader. */
    public Optional<String> unavailableReasonIfAny() {
        return Optional.ofNullable(unavailableReason);
    }

    /** The stored document, for a caller that wants to open it on the record it belongs to. */
    public Optional<Long> storedAttachmentIdIfAny() {
        return Optional.ofNullable(storedAttachmentId);
    }
}
