package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupUploadStatus;
import gr.novotrade.novocore.core.api.backup.BackupUploadView;
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
import java.time.Instant;

/**
 * One copy of one backup at one destination.
 *
 * <p>A row per destination rather than a pair of columns on {@link BackupRun}, because "two Drive
 * accounts" is configuration and not schema. A third destination, or an entirely different kind of
 * one, is a settings block and a row here.
 *
 * <p>{@code remoteFileId} is Drive's own identifier and is the only external system id anywhere in
 * this feature. It lives here rather than on the backup because {@code CLAUDE.md} rule 2 keeps
 * external ids off core entities — and this row <em>is</em> the per-destination mapping table that
 * rule asks for.
 */
@Entity
@Table(name = "backup_upload")
class BackupUpload extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "backup_run_id", nullable = false)
    private BackupRun backupRun;

    @Column(name = "destination_key", nullable = false, length = 40)
    private String destinationKey;

    @Column(name = "destination_label", length = 200)
    private String destinationLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackupUploadStatus status;

    @Column(name = "remote_file_id", length = 200)
    private String remoteFileId;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(nullable = false)
    private int attempts;

    @Column
    private String error;

    protected BackupUpload() {
    }

    BackupUpload(BackupRun backupRun, String destinationKey, String destinationLabel) {
        this.backupRun = backupRun;
        this.destinationKey = destinationKey;
        this.destinationLabel = destinationLabel;
        this.status = BackupUploadStatus.PENDING;
    }

    void uploaded(String remoteFileId, Instant when) {
        this.attempts++;
        this.status = BackupUploadStatus.UPLOADED;
        this.remoteFileId = remoteFileId;
        this.uploadedAt = when;
        this.error = null;
    }

    void failed(String error) {
        this.attempts++;
        this.status = BackupUploadStatus.FAILED;
        this.error = truncate(error);
    }

    /**
     * No credentials, so nothing was attempted.
     *
     * <p>Does not count as an attempt. Incrementing here would make an unconfigured destination
     * look like one that has been tried and rejected many times, which is the wrong problem and
     * points at the wrong remedy.
     */
    void notConfigured(String why) {
        this.status = BackupUploadStatus.NOT_CONFIGURED;
        this.error = truncate(why);
    }

    void pruned() {
        this.status = BackupUploadStatus.PRUNED;
        this.remoteFileId = null;
        this.uploadedAt = null;
    }

    String getDestinationKey() {
        return destinationKey;
    }

    BackupUploadStatus getStatus() {
        return status;
    }

    String getRemoteFileId() {
        return remoteFileId;
    }

    BackupUploadView toView() {
        return new BackupUploadView(
                id, destinationKey, destinationLabel, status, remoteFileId, uploadedAt, attempts,
                error);
    }

    private static String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "(no message)";
        }
        return error.length() <= 2000 ? error : error.substring(0, 2000) + "…";
    }
}
