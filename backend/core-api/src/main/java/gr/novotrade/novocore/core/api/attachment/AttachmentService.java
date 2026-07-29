package gr.novotrade.novocore.core.api.attachment;

import java.util.List;
import java.util.Optional;

/**
 * Documents attached to core records — the one mechanism for it, per {@code CLAUDE.md}'s shared
 * core services rule. A module needing to attach a file calls this rather than growing its own
 * storage.
 *
 * <p>Content is held in PostgreSQL so that a backup and a restore are a single atomic artefact;
 * see the attachments migration for the reasoning and its cost.
 */
public interface AttachmentService {

    /**
     * Stores a document against a core record.
     *
     * @param filename as supplied by the uploader; any directory component is stripped before
     *     storage, so a name like {@code ../../etc/passwd} cannot escape its record on download
     * @throws AttachmentTooLargeException if it exceeds the configured maximum
     * @throws IllegalArgumentException if the content is empty or a required field is blank
     */
    AttachmentMetadata attach(String entityType, String entityId, String filename,
            String contentType, byte[] content);

    /** Documents on one record, newest first. Does not read content out of the database. */
    List<AttachmentMetadata> listFor(String entityType, String entityId);

    /** Metadata for one document, without its content. */
    Optional<AttachmentMetadata> findMetadata(long attachmentId);

    /** One document including its bytes. */
    Optional<AttachmentContent> download(long attachmentId);

    /**
     * Removes a document. Recorded in the audit log, including its filename and checksum, so
     * that a deletion remains traceable after the bytes are gone.
     *
     * <p><strong>A document that has been emailed is still deletable, and deleting it does not
     * disturb the mail's history.</strong> {@code EmailSender} references stored documents rather
     * than copying them, so a sent message points at this row; the reference is nulled here and
     * the history entry goes on naming the file, its size and its checksum, reporting it as no
     * longer available. Refusing the deletion instead would let an email sent years ago pin a
     * document forever, and cascading it would delete the record that the message ever carried an
     * attachment. The checksum recorded in the audit log is the same one the outbox snapshotted,
     * so the two can still be tied together afterwards.
     *
     * @return true if something was removed
     */
    boolean delete(long attachmentId);
}
