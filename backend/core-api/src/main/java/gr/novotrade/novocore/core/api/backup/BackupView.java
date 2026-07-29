package gr.novotrade.novocore.core.api.backup;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One backup attempt and where its copies went.
 *
 * @param artefactName the file's name, identical on disk and at every destination — so retention
 *     can compare the three places by name without downloading anything
 * @param sizeBytes size of the <em>encrypted</em> artefact, which is what was actually written
 *     and uploaded
 * @param checksumSha256 over the encrypted artefact too, so a file fetched back from Drive can be
 *     verified without decrypting it and therefore without the key
 * @param encryptionKeyFingerprint first 16 hex characters of SHA-256 over the encryption key.
 *     Enough to tell two keys apart and useless for recovering either. It exists so that
 *     restoring with a rotated key reports "wrong key" rather than a GCM tag mismatch, which
 *     reads as "your backup is corrupt" — the most alarming possible way to say the wrong thing
 * @param monthlyArchive whether the retention rule is keeping this one forever as its calendar
 *     month's archive
 */
public record BackupView(
        long id,
        String artefactName,
        Instant startedAt,
        Instant finishedAt,
        BackupRunStatus status,
        Long sizeBytes,
        String checksumSha256,
        String encryptionKeyFingerprint,
        boolean monthlyArchive,
        Instant prunedAt,
        String error,
        List<BackupUploadView> uploads) {

    public BackupView {
        uploads = uploads == null ? List.of() : List.copyOf(uploads);
    }

    /**
     * Whether a copy of this backup exists anywhere other than the machine it was taken on.
     *
     * <p>The question worth asking, and deliberately not the same as {@link #succeeded()}. A dump
     * that wrote perfectly to local disk and reached no destination has protected against a
     * dropped table and against nothing else — not a failed disk, not a lost machine, not a
     * ransomed host. Treating that as an unqualified success is how a backup regime discovers its
     * gap on the day it matters.
     */
    public boolean isOffsite() {
        return uploads.stream().anyMatch(BackupUploadView::holdsACopy);
    }

    /** How many destinations currently hold a copy. */
    public long offsiteCopies() {
        return uploads.stream().filter(BackupUploadView::holdsACopy).count();
    }

    public boolean succeeded() {
        return status == BackupRunStatus.SUCCEEDED;
    }

    /** True when the artefact has been removed by the retention rule; the record of it remains. */
    public boolean isPruned() {
        return prunedAt != null;
    }

    public Optional<Duration> duration() {
        return finishedAt == null ? Optional.empty()
                : Optional.of(Duration.between(startedAt, finishedAt));
    }

    public Optional<String> errorIfAny() {
        return Optional.ofNullable(error);
    }

    public Optional<Long> sizeBytesIfAny() {
        return Optional.ofNullable(sizeBytes);
    }

    /** Destinations that failed or were never configured — see {@link BackupUploadView#needsAttention()}. */
    public List<BackupUploadView> destinationsNeedingAttention() {
        return uploads.stream().filter(BackupUploadView::needsAttention).toList();
    }
}
