package gr.novotrade.novocore.core.api.attachment;

import java.time.Instant;

/**
 * An attachment without its bytes.
 *
 * <p>Separate from {@link AttachmentContent} on purpose: listing the documents on a record must
 * not drag every PDF out of the database to do it. Queries returning this type never select the
 * content column.
 *
 * @param checksumSha256 lower-case hex, for verifying a restored file against the original
 */
public record AttachmentMetadata(
        long id,
        String entityType,
        String entityId,
        String filename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        Instant createdAt,
        String createdBy) {
}
