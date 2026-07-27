package gr.novotrade.novocore.core.attachment;

import gr.novotrade.novocore.core.api.attachment.AttachmentContent;
import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.attachment.AttachmentTooLargeException;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AttachmentServiceImpl implements AttachmentService {

    // Audit entries are recorded against the record the document is attached to, not against
    // "Attachment": the useful question is "what happened to this purchase invoice", so the
    // trail belongs on the invoice.
    private static final String MAX_SIZE_SETTING = "attachment.max-size-bytes";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final AttachmentRepository repository;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    AttachmentServiceImpl(AttachmentRepository repository, SettingsService settings,
            AuditLogService auditLog) {
        this.repository = repository;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional
    public AttachmentMetadata attach(String entityType, String entityId, String filename,
            String contentType, byte[] content) {
        requireText(entityType, "entityType");
        requireText(entityId, "entityId");
        requireText(filename, "filename");
        Objects.requireNonNull(content, "content");

        String safeFilename = sanitiseFilename(filename);

        if (content.length == 0) {
            // An empty attachment is almost always a failed upload rather than an intent, and
            // storing it would leave a record that looks like a document and is not one.
            throw new IllegalArgumentException(
                    "'%s' is empty. An empty attachment is a failed upload, not a document."
                            .formatted(safeFilename));
        }

        long maxSizeBytes = maxSizeBytes();
        if (content.length > maxSizeBytes) {
            throw new AttachmentTooLargeException(safeFilename, content.length, maxSizeBytes);
        }

        Attachment saved = repository.save(new Attachment(
                entityType,
                entityId,
                safeFilename,
                contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType,
                content.length,
                sha256Hex(content),
                content));

        auditLog.record("attachment.added", entityType, entityId, Map.of(
                "attachmentId", String.valueOf(saved.getId()),
                "filename", saved.getFilename(),
                "sizeBytes", String.valueOf(saved.getSizeBytes()),
                "checksumSha256", saved.getChecksumSha256()));

        return toMetadata(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentMetadata> listFor(String entityType, String entityId) {
        requireText(entityType, "entityType");
        requireText(entityId, "entityId");
        return repository.findMetadataForEntity(entityType, entityId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttachmentMetadata> findMetadata(long attachmentId) {
        return repository.findMetadataById(attachmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttachmentContent> download(long attachmentId) {
        return repository.findById(attachmentId)
                .map(attachment -> new AttachmentContent(
                        toMetadata(attachment), attachment.getContent()));
    }

    @Override
    @Transactional
    public boolean delete(long attachmentId) {
        Optional<Attachment> found = repository.findById(attachmentId);
        if (found.isEmpty()) {
            return false;
        }
        Attachment attachment = found.get();

        // Recorded before the row goes, and with enough detail that the deletion stays
        // meaningful once the bytes no longer exist to inspect.
        auditLog.record("attachment.deleted", attachment.getEntityType(),
                attachment.getEntityId(), Map.of(
                        "attachmentId", String.valueOf(attachmentId),
                        "filename", attachment.getFilename(),
                        "sizeBytes", String.valueOf(attachment.getSizeBytes()),
                        "checksumSha256", attachment.getChecksumSha256()));

        repository.delete(attachment);
        return true;
    }

    private long maxSizeBytes() {
        return settings.findDecimal(MAX_SIZE_SETTING)
                .map(java.math.BigDecimal::longValueExact)
                .orElseThrow(() -> new IllegalStateException(
                        "Setting '%s' is missing. It is seeded by migration; a database that "
                                .formatted(MAX_SIZE_SETTING)
                                + "lacks it has not been migrated."));
    }

    /**
     * Reduces a supplied filename to a bare name.
     *
     * <p>Both separators are handled regardless of host platform: an upload arriving from a
     * Windows client carries backslashes, and a server on Linux would not treat those as path
     * separators — leaving {@code ..\..\secret} intact as a "safe" name that a Windows client
     * then interprets on save. There is a matching CHECK constraint in the schema.
     */
    private static String sanitiseFilename(String filename) {
        String name = filename.trim();
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            name = name.substring(lastSeparator + 1);
        }
        // A name that was nothing but separators or dots leaves nothing usable behind.
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException(
                    "'%s' does not contain a usable filename.".formatted(filename));
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java platform, so this cannot happen on a working JVM.
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", e);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static AttachmentMetadata toMetadata(Attachment attachment) {
        return new AttachmentMetadata(
                attachment.getId(),
                attachment.getEntityType(),
                attachment.getEntityId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getChecksumSha256(),
                attachment.getCreatedAt(),
                attachment.getCreatedBy());
    }
}
