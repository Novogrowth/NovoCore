package gr.novotrade.novocore.core.attachment;

import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.attachment.AttachmentService}.
 *
 * <p>The metadata queries use constructor expressions so the {@code content} column is never
 * part of the SQL. This is the whole reason they are written out by hand rather than derived:
 * a derived query returning entities would fetch every document's bytes just to list filenames,
 * which on a record with a dozen scanned invoices is tens of megabytes to render a list.
 */
interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("""
            select new gr.novotrade.novocore.core.api.attachment.AttachmentMetadata(
                a.id, a.entityType, a.entityId, a.filename, a.contentType, a.sizeBytes,
                a.checksumSha256, a.createdAt, a.createdBy)
            from Attachment a
            where a.entityType = :entityType and a.entityId = :entityId
            order by a.createdAt desc, a.id desc
            """)
    List<AttachmentMetadata> findMetadataForEntity(
            @Param("entityType") String entityType, @Param("entityId") String entityId);

    @Query("""
            select new gr.novotrade.novocore.core.api.attachment.AttachmentMetadata(
                a.id, a.entityType, a.entityId, a.filename, a.contentType, a.sizeBytes,
                a.checksumSha256, a.createdAt, a.createdBy)
            from Attachment a
            where a.id = :id
            """)
    Optional<AttachmentMetadata> findMetadataById(@Param("id") long id);
}
