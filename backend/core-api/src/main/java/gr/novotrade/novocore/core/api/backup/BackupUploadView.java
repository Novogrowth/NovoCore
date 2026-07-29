package gr.novotrade.novocore.core.api.backup;

import java.time.Instant;
import java.util.Optional;

/**
 * One copy of one backup at one destination.
 *
 * @param destinationKey the settings prefix this destination is configured under, e.g.
 *     {@code primary} for {@code backup.drive.primary.*}, so a row and its configuration are
 *     findable from each other
 * @param remoteFileId the destination's own identifier for the file — Drive's file id. The only
 *     external identifier anywhere in this feature, and it lives here rather than on the backup
 *     because {@code CLAUDE.md} rule 2 keeps external ids off core entities: this row is the
 *     adapter's mapping table
 */
public record BackupUploadView(
        long id,
        String destinationKey,
        String destinationLabel,
        BackupUploadStatus status,
        String remoteFileId,
        Instant uploadedAt,
        int attempts,
        String error) {

    public Optional<String> remoteFileIdIfAny() {
        return Optional.ofNullable(remoteFileId);
    }

    public Optional<Instant> uploadedAtIfAny() {
        return Optional.ofNullable(uploadedAt);
    }

    public Optional<String> errorIfAny() {
        return Optional.ofNullable(error);
    }

    /** True when a copy of the artefact is currently sitting at this destination. */
    public boolean holdsACopy() {
        return status == BackupUploadStatus.UPLOADED;
    }

    /**
     * True when somebody needs to do something about this destination.
     *
     * <p>Covers {@link BackupUploadStatus#NOT_CONFIGURED} as well as an outright failure, because
     * both mean the off-site copy this destination was supposed to provide does not exist. The
     * remedy differs — supply credentials, or find out why the upload was rejected — but the
     * exposure is identical, and only one of them announces itself.
     */
    public boolean needsAttention() {
        return status == BackupUploadStatus.FAILED || status == BackupUploadStatus.NOT_CONFIGURED;
    }
}
