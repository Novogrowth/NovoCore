package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailAttachmentContent;
import gr.novotrade.novocore.core.api.email.EmailAttachmentSource;
import gr.novotrade.novocore.core.api.email.EmailAttachmentUnavailableException;
import gr.novotrade.novocore.core.api.email.SentEmailAttachmentView;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * A file attached to a queued message — either its bytes, or a reference to the document that
 * already holds them.
 *
 * <p><strong>A referenced document is not copied here.</strong> {@code AttachmentService} owns
 * stored documents, and the outbox is not a second store; emailing an invoice PDF that is also
 * attached to the invoice would otherwise put that file in two tables and in every backup. What
 * this row keeps instead is the reference plus the document's identity as it was at queue time —
 * filename, content type, size, checksum. That snapshot is what lets the history stay meaningful
 * after the document is deleted, and it is why the message says what it <em>sent</em> rather than
 * what the document happens to be called now.
 *
 * <p><strong>An inline attachment is stored rather than regenerated at send time.</strong> A
 * Purchase Order PDF is generated from data that can change between the order being approved and
 * the mail going out, and a retry three minutes later must send the document that was approved,
 * not a fresh rendering of whatever the order looks like now.
 *
 * <p>{@code attachment_id} is a plain column with a database-level foreign key, not a JPA
 * association. The email service reaches {@code AttachmentService} through its interface like any
 * other caller, and importing the attachment entity to get a {@code @ManyToOne} would be reaching
 * around it for no gain — the id is all this row needs, and {@code ON DELETE SET NULL} is applied
 * by PostgreSQL rather than by anything here.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "content_source", nullable = false, length = 20)
    private EmailAttachmentSource contentSource;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column
    private byte[] content;

    @Column(name = "attachment_order", nullable = false)
    private int attachmentOrder;

    protected QueuedEmailAttachment() {
    }

    /**
     * @param storedMetadata the referenced document's metadata, read once at queue time; required
     *     for a stored attachment and forbidden for an inline one
     */
    QueuedEmailAttachment(QueuedEmail message, EmailAttachment attachment,
            AttachmentMetadata storedMetadata, int attachmentOrder) {
        this.message = message;
        this.attachmentOrder = attachmentOrder;

        if (attachment.isStored()) {
            Objects.requireNonNull(storedMetadata,
                    "A stored attachment needs the document's metadata, read at queue time");
            this.contentSource = EmailAttachmentSource.ATTACHMENT;
            this.attachmentId = storedMetadata.id();
            // Copied from the document, not supplied by the caller: EmailAttachment.stored
            // refuses a filename precisely so these cannot disagree with what was sent.
            this.filename = storedMetadata.filename();
            this.contentType = storedMetadata.contentType();
            this.sizeBytes = storedMetadata.sizeBytes();
            this.checksumSha256 = storedMetadata.checksumSha256();
            this.content = null;
        } else {
            this.contentSource = EmailAttachmentSource.INLINE;
            // Already sanitised and validated by EmailAttachment's constructor; the schema's CHECK
            // constraints refuse a path or a line break here too, for anything writing directly.
            this.filename = attachment.filename();
            this.contentType = attachment.contentType();
            this.content = attachment.content();
            this.sizeBytes = attachment.content().length;
        }
    }

    /**
     * The bytes to transmit, resolved from wherever they live.
     *
     * <p>Called at send time rather than at queue time, so a reference is followed as late as
     * possible and the message goes out with the document as it is — which, since a stored
     * document cannot be edited in place, is the document as it was.
     *
     * @throws EmailAttachmentUnavailableException if the file can no longer be produced. The
     *     dispatcher turns this into a visibly failed message: a mail is never sent with an
     *     attachment silently missing, which is {@code CLAUDE.md} rule 8 applied to the one case
     *     where a recipient could not possibly tell.
     */
    EmailAttachmentContent resolveContent(AttachmentService attachments) {
        if (contentSource == EmailAttachmentSource.INLINE) {
            if (content == null) {
                throw unavailable(prunedReason());
            }
            return new EmailAttachmentContent(filename, contentType, content);
        }

        if (attachmentId == null) {
            throw unavailable(deletedReason());
        }
        return attachments.download(attachmentId)
                .map(document -> new EmailAttachmentContent(
                        filename, contentType, document.content()))
                // Belt and braces: the foreign key nulls attachment_id when the document goes, so
                // reaching here means the two disagree. Reported rather than treated as empty.
                .orElseThrow(() -> unavailable(
                        "the document it references (id %d) is not there, although the reference "
                                .formatted(attachmentId) + "still points at it"));
    }

    /**
     * The {@code AttachmentService} document this references, or null.
     *
     * <p>Null for an inline attachment, and also null once a referenced document has been deleted
     * ({@code ON DELETE SET NULL}). Exists for Q44's access-path check, which has to know
     * <em>which</em> core record to re-check the caller against before any bytes are produced —
     * a question {@link #resolveContent} answers too late, because by then it has the bytes.
     */
    Long storedAttachmentId() {
        return attachmentId;
    }

    /** What the sent-email history shows — the same shape whether the bytes are here or not. */
    SentEmailAttachmentView toView() {
        return new SentEmailAttachmentView(
                id,
                attachmentOrder,
                filename,
                contentType,
                sizeBytes,
                contentSource,
                attachmentId,
                unavailableReason());
    }

    /**
     * Null when the file can still be produced, otherwise the sentence explaining why not.
     *
     * <p>No query is needed to answer this. {@code ON DELETE SET NULL} means a non-null
     * {@code attachment_id} is itself the proof that the document is still there, so listing a
     * message's attachments never touches the attachment table.
     */
    private String unavailableReason() {
        if (contentSource == EmailAttachmentSource.INLINE) {
            return content == null ? prunedReason() : null;
        }
        return attachmentId == null ? deletedReason() : null;
    }

    private static String prunedReason() {
        return "the copy kept with this message has been removed under the outbox retention "
                + "policy. The message was sent with it; the file is no longer available here.";
    }

    private static String deletedReason() {
        return "the document it referenced has since been deleted from the record it was "
                + "attached to. The message was sent with it; the file is no longer available.";
    }

    private EmailAttachmentUnavailableException unavailable(String reason) {
        return new EmailAttachmentUnavailableException(id, filename, reason);
    }

    Long getId() {
        return id;
    }

    String getFilename() {
        return filename;
    }

    int getAttachmentOrder() {
        return attachmentOrder;
    }
}
