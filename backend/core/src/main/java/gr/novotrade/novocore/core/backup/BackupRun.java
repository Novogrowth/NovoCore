package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupRunStatus;
import gr.novotrade.novocore.core.api.backup.BackupView;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One backup attempt.
 *
 * <p>The row is written <em>before</em> the dump starts and updated when it finishes, rather than
 * inserted once at the end. A backup is the one operation whose interesting failure is the process
 * dying halfway through it — and a design that records only completed runs cannot represent that
 * at all: the evidence would be an absence, indistinguishable from a scheduler that never fired.
 * A row stuck at {@code RUNNING} says exactly what happened.
 */
@Entity
@Table(name = "backup_run")
class BackupRun extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artefact_name", nullable = false, length = 200)
    private String artefactName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackupRunStatus status;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "encryption_key_fingerprint", length = 16)
    private String encryptionKeyFingerprint;

    @Column(name = "monthly_archive", nullable = false)
    private boolean monthlyArchive;

    @Column(name = "pruned_at")
    private Instant prunedAt;

    @Column
    private String error;

    @OneToMany(mappedBy = "backupRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("destinationKey ASC")
    private List<BackupUpload> uploads = new ArrayList<>();

    protected BackupRun() {
    }

    BackupRun(String artefactName, Instant startedAt) {
        this.artefactName = artefactName;
        this.startedAt = startedAt;
        this.status = BackupRunStatus.RUNNING;
    }

    void succeeded(Instant finishedAt, long sizeBytes, String checksumSha256,
            String encryptionKeyFingerprint) {
        this.status = BackupRunStatus.SUCCEEDED;
        this.finishedAt = finishedAt;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.encryptionKeyFingerprint = encryptionKeyFingerprint;
        this.error = null;
    }

    void failed(Instant finishedAt, String error) {
        this.status = BackupRunStatus.FAILED;
        this.finishedAt = finishedAt;
        this.error = truncate(error);
    }

    BackupUpload addDestination(String destinationKey, String label) {
        BackupUpload upload = new BackupUpload(this, destinationKey, label);
        uploads.add(upload);
        return upload;
    }

    /**
     * Marks the artefact as gone, without deleting the row.
     *
     * <p>The record of a backup outlives the backup itself on purpose. Deleting the row would make
     * the history a list of surviving files rather than of attempts, and "we have taken a backup
     * every night since March" would become unanswerable the moment retention started deleting
     * things — which is to say, immediately.
     */
    void pruned(Instant when) {
        this.prunedAt = when;
    }

    void setMonthlyArchive(boolean monthlyArchive) {
        this.monthlyArchive = monthlyArchive;
    }

    Long getId() {
        return id;
    }

    String getArtefactName() {
        return artefactName;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    BackupRunStatus getStatus() {
        return status;
    }

    boolean isMonthlyArchive() {
        return monthlyArchive;
    }

    Instant getPrunedAt() {
        return prunedAt;
    }

    String getEncryptionKeyFingerprint() {
        return encryptionKeyFingerprint;
    }

    String getChecksumSha256() {
        return checksumSha256;
    }

    List<BackupUpload> getUploads() {
        return uploads;
    }

    BackupView toView() {
        return new BackupView(
                id,
                artefactName,
                startedAt,
                finishedAt,
                status,
                sizeBytes,
                checksumSha256,
                encryptionKeyFingerprint,
                monthlyArchive,
                prunedAt,
                error,
                uploads.stream().map(BackupUpload::toView).toList());
    }

    /** Keeps a stack-trace-sized failure from becoming the largest thing in the table. */
    private static String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "(no message)";
        }
        return error.length() <= 4000 ? error : error.substring(0, 4000) + "…";
    }
}
