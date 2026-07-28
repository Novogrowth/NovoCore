package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A file attached to a queued message, with its bytes.
 *
 * <p>Stored rather than regenerated at send time. A Purchase Order PDF is generated from data
 * that can change between the order being approved and the mail going out, and a retry three
 * minutes later must send the document that was approved, not a fresh rendering of whatever the
 * order looks like now.
 */
@Entity
@Table(name = "email_outbox_attachment")
class QueuedEmailAttachment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_outbox_id", nullable = false)
    private QueuedEmail message;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "attachment_order", nullable = false)
    private int attachmentOrder;

    protected QueuedEmailAttachment() {
    }

    QueuedEmailAttachment(QueuedEmail message, EmailAttachment attachment, int attachmentOrder) {
        this.message = message;
        // Already sanitised and validated by EmailAttachment's constructor; the schema's CHECK
        // constraints refuse a path or a line break here too, for anything writing directly.
        this.filename = attachment.filename();
        this.contentType = attachment.contentType();
        this.content = attachment.content();
        this.sizeBytes = attachment.content().length;
        this.attachmentOrder = attachmentOrder;
    }

    String getFilename() {
        return filename;
    }

    String getContentType() {
        return contentType;
    }

    byte[] getContent() {
        return content;
    }

    int getAttachmentOrder() {
        return attachmentOrder;
    }
}
