package gr.novotrade.novocore.core.attachment;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A document stored against a core record.
 *
 * <p>Note the absence of {@code @Lob} on {@link #content}. A plain {@code byte[]} maps to
 * {@code bytea}, which is what we want; {@code @Lob} would map to a PostgreSQL large object,
 * stored outside the table and needing explicit unlinking to avoid orphans on delete.
 *
 * <p>Loading this entity loads the bytes. Anything that only needs metadata uses the projection
 * query on the repository instead, so listing the documents on a record does not pull every PDF
 * out of the database.
 */
@Entity
@Table(name = "attachment")
class Attachment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false, length = 120)
    private String entityId;

    @Column(name = "filename", nullable = false, updatable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, updatable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, updatable = false, length = 64)
    private String checksumSha256;

    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    /** For JPA only. */
    protected Attachment() {
    }

    Attachment(String entityType, String entityId, String filename, String contentType,
            long sizeBytes, String checksumSha256, byte[] content) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.content = content;
    }

    Long getId() {
        return id;
    }

    String getEntityType() {
        return entityType;
    }

    String getEntityId() {
        return entityId;
    }

    String getFilename() {
        return filename;
    }

    String getContentType() {
        return contentType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getChecksumSha256() {
        return checksumSha256;
    }

    byte[] getContent() {
        return content;
    }
}
