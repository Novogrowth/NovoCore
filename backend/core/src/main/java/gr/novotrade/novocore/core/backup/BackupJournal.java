package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.backup.BackupRunStatus;
import gr.novotrade.novocore.core.api.backup.BackupUploadStatus;
import gr.novotrade.novocore.core.api.backup.BackupView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything a backup does to the database, kept apart from everything it does to the disk and the
 * network.
 *
 * <p>A separate bean rather than {@code @Transactional} methods on {@link BackupServiceImpl}, for
 * the reason {@code EmailOutbox} is separate from {@code EmailDispatcher}: a self-invocation never
 * goes through the proxy, so the annotations would silently do nothing and the whole backup would
 * run in one transaction — or in none.
 *
 * <p>The boundary matters more here than it does for email. <strong>No transaction may be open
 * while {@code pg_dump} runs</strong>, which on a real database is minutes. Holding one would keep
 * a connection and a snapshot pinned for the duration of the backup every night, which is exactly
 * the kind of thing that is invisible until the table it blocks is the one somebody is trying to
 * use.
 */
@Component
class BackupJournal {

    private static final String ENTITY_TYPE = "Backup";

    private final BackupRunRepository runs;
    private final AuditLogService auditLog;

    BackupJournal(BackupRunRepository runs, AuditLogService auditLog) {
        this.runs = runs;
        this.auditLog = auditLog;
    }

    /**
     * Records that a backup has started, and commits before the dump begins.
     *
     * <p>Written first rather than at the end, so a process killed mid-dump leaves a row stuck at
     * {@code RUNNING}. Recording only completed runs would make that failure an absence in the
     * table, indistinguishable from a scheduler that never fired — and those two have completely
     * different remedies.
     */
    @Transactional
    long started(String artefactName, Instant now, List<DriveDestination.Configured> destinations) {
        BackupRun run = new BackupRun(artefactName, now);
        for (DriveDestination.Configured destination : destinations) {
            BackupUpload upload = run.addDestination(destination.key(), destination.label());
            if (!destination.isConfigured()) {
                // Recorded now rather than skipped, so an unconfigured destination is visible on
                // every single run instead of being absent and therefore unnoticed.
                upload.notConfigured(destination.problem());
            }
        }
        return runs.save(run).getId();
    }

    @Transactional
    void succeeded(long runId, Instant finishedAt, long sizeBytes, String checksum,
            String keyFingerprint) {
        BackupRun run = require(runId);
        run.succeeded(finishedAt, sizeBytes, checksum, keyFingerprint);
        runs.save(run);
        auditLog.record("backup.succeeded", ENTITY_TYPE, String.valueOf(runId), Map.of(
                "artefact", run.getArtefactName(),
                "sizeBytes", String.valueOf(sizeBytes),
                "checksumSha256", checksum));
    }

    @Transactional
    void failed(long runId, Instant finishedAt, String error) {
        BackupRun run = require(runId);
        run.failed(finishedAt, error);
        runs.save(run);
        auditLog.record("backup.failed", ENTITY_TYPE, String.valueOf(runId), Map.of(
                "artefact", run.getArtefactName(),
                "error", error == null ? "(none)" : error));
    }

    @Transactional
    void uploaded(long runId, String destinationKey, String remoteFileId, Instant when) {
        BackupRun run = require(runId);
        uploadFor(run, destinationKey).uploaded(remoteFileId, when);
        runs.save(run);
    }

    @Transactional
    void uploadFailed(long runId, String destinationKey, String error) {
        BackupRun run = require(runId);
        uploadFor(run, destinationKey).failed(error);
        runs.save(run);
        auditLog.record("backup.upload-failed", ENTITY_TYPE, String.valueOf(runId), Map.of(
                "destination", destinationKey,
                "error", error == null ? "(none)" : error));
    }

    @Transactional
    void pruned(long runId, Instant when, boolean monthlyArchive) {
        BackupRun run = require(runId);
        run.pruned(when);
        run.setMonthlyArchive(monthlyArchive);
        run.getUploads().forEach(BackupUpload::pruned);
        runs.save(run);
    }

    @Transactional
    void markMonthlyArchive(long runId, boolean monthlyArchive) {
        BackupRun run = require(runId);
        run.setMonthlyArchive(monthlyArchive);
        runs.save(run);
    }

    /** Whether an artefact of this name already exists — see {@code BackupServiceImpl}'s naming. */
    @Transactional(readOnly = true)
    boolean artefactNameTaken(String artefactName) {
        return runs.findByArtefactName(artefactName).isPresent();
    }

    /**
     * Every successful backup whose artefact still exists — the retention rule's input.
     *
     * <p>Lives here rather than on {@code BackupRetentionService} because that class's
     * {@code apply()} is deliberately <em>not</em> transactional (it deletes files and calls
     * Drive), and a transactional method called from it would be a self-invocation: the proxy
     * bypassed, the annotation doing nothing.
     */
    @Transactional(readOnly = true)
    List<BackupRetentionRule.Candidate> retentionCandidates() {
        return runs.findByStatusAndPrunedAtIsNull(BackupRunStatus.SUCCEEDED).stream()
                .map(run -> new BackupRetentionRule.Candidate(run.getId(), run.getStartedAt()))
                .toList();
    }

    /**
     * Everything needed to delete one backup's copies, materialised as plain data.
     *
     * <p>Detached values rather than the entity, for the reason {@code EmailOutbox.claimDue} gives:
     * {@code uploads} is a lazy association, and the retention pass reads it <em>after</em> any
     * transaction has closed — so returning the entity would throw on first access. This was a
     * real defect on an untested path: retention only reaches it once there are more than seven
     * backups, which no test had produced.
     */
    @Transactional(readOnly = true)
    Optional<ExpiredArtefact> artefactToRemove(long runId) {
        return runs.findById(runId).map(run -> new ExpiredArtefact(
                run.getArtefactName(),
                run.getUploads().stream()
                        .filter(upload -> upload.getStatus() == BackupUploadStatus.UPLOADED)
                        .filter(upload -> upload.getRemoteFileId() != null)
                        .map(upload -> new RemoteCopy(
                                upload.getDestinationKey(), upload.getRemoteFileId()))
                        .toList()));
    }

    /** One expired backup's artefact name and the remote copies still holding it. */
    record ExpiredArtefact(String artefactName, List<RemoteCopy> copies) {
    }

    record RemoteCopy(String destinationKey, String remoteFileId) {
    }

    @Transactional(readOnly = true)
    Optional<BackupView> find(long runId) {
        return runs.findById(runId).map(BackupRun::toView);
    }

    @Transactional(readOnly = true)
    List<BackupView> recent(int limit) {
        return runs.findByOrderByStartedAtDescIdDesc(Limit.of(limit)).stream()
                .map(BackupRun::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    Optional<BackupView> latestSuccessful() {
        return runs.findFirstByStatusOrderByStartedAtDescIdDesc(BackupRunStatus.SUCCEEDED)
                .map(BackupRun::toView);
    }

    private BackupRun require(long runId) {
        return runs.findById(runId).orElseThrow(() -> new IllegalStateException(
                "Backup run %d has disappeared from the table while it was running.".formatted(runId)));
    }

    private static BackupUpload uploadFor(BackupRun run, String destinationKey) {
        return run.getUploads().stream()
                .filter(upload -> upload.getDestinationKey().equals(destinationKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Backup run %d has no destination '%s'."
                                .formatted(run.getId(), destinationKey)));
    }
}
