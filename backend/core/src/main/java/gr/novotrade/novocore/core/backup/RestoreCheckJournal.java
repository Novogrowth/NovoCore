package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.RestoreCheckView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The restore check's database writes, in their own bean.
 *
 * <p>Separate from {@link RestoreVerifier} for the reason {@link BackupJournal} is separate from
 * {@link BackupServiceImpl}, and it is not a stylistic preference: a {@code @Transactional} method
 * called from another method of the same object goes straight to the object and never through the
 * proxy, so the annotation does nothing at all. Written the obvious way — the verifier annotating
 * its own bookkeeping methods — the whole restore check would have run with no transaction
 * management whatever, and the symptom would have been nothing, until a failure needed to be
 * recorded and was not.
 *
 * <p>Short transactions are also required here rather than merely tidy. A restore check runs
 * {@code pg_restore} over a full database dump; a transaction spanning that would hold a
 * connection open for the duration of the restore, every time.
 */
@Component
class RestoreCheckJournal {

    private final BackupRunRepository runs;
    private final RestoreCheckRepository checks;
    private final Clock clock;

    RestoreCheckJournal(BackupRunRepository runs, RestoreCheckRepository checks, Clock clock) {
        this.runs = runs;
        this.checks = checks;
        this.clock = clock;
    }

    /** Recorded before the restore begins, so a check that dies mid-restore leaves a RUNNING row. */
    @Transactional
    long started(long backupRunId) {
        BackupRun run = runs.findById(backupRunId).orElseThrow(() ->
                new IllegalArgumentException("No backup run with id " + backupRunId));
        return checks.save(new RestoreCheck(run, Instant.now(clock))).getId();
    }

    @Transactional
    RestoreCheckView passed(long checkId, List<String> findings) {
        RestoreCheck check = checks.findById(checkId).orElseThrow();
        check.passed(Instant.now(clock), findings);
        return checks.save(check).toView();
    }

    @Transactional
    RestoreCheckView failed(long checkId, List<String> findings, String error) {
        RestoreCheck check = checks.findById(checkId).orElseThrow();
        check.failed(Instant.now(clock), findings, error);
        return checks.save(check).toView();
    }

    @Transactional(readOnly = true)
    List<RestoreCheckView> checksFor(long backupRunId) {
        return checks.findByBackupRunIdOrderByStartedAtDescIdDesc(backupRunId).stream()
                .map(RestoreCheck::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    Optional<RestoreCheckView> latest() {
        return checks.findByOrderByStartedAtDescIdDesc(Limit.of(1)).stream()
                .findFirst()
                .map(RestoreCheck::toView);
    }
}
