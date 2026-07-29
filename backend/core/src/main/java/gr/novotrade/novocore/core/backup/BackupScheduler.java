package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupNotConfiguredException;
import gr.novotrade.novocore.core.api.backup.BackupService;
import gr.novotrade.novocore.core.api.backup.BackupView;
import gr.novotrade.novocore.core.api.backup.RestoreCheckView;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the nightly backup and the periodic restore check.
 *
 * <p><strong>Scheduling is enabled in {@code app}</strong>, not here — the same arrangement as
 * {@code EmailDispatcher} and {@code EmailRetention}. So the core's own integration tests hold a
 * fully wired scheduler that never fires on its own and drive it by calling these methods, which
 * is what makes a backup test a matter of assertion rather than of waiting for 02:00.
 *
 * <p>The two jobs are separate schedules and deliberately not chained. Verifying a restore is
 * expensive — it decrypts the whole artefact, creates a database, restores into it and drops it —
 * and doing that after every backup would triple the nightly work to re-prove something that only
 * changes when the schema or the tooling changes. Weekly is enough to catch a regression while it
 * is still recent, and {@code verifyRestore} can be called directly whenever a specific artefact
 * needs proving.
 */
@Component
class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backups;

    BackupScheduler(BackupService backups) {
        this.backups = backups;
    }

    /** The nightly backup. 02:00 by default: after the day's work, before anyone's morning. */
    @Scheduled(cron = "${novocore.backup.cron:0 0 2 * * *}")
    public Optional<BackupView> nightlyBackup() {
        try {
            return Optional.of(backups.runNow());
        } catch (BackupNotConfiguredException e) {
            // Loud, and not fatal. An instance without an encryption key is still a working
            // instance; refusing to serve requests over it would turn a missing setting into an
            // outage. But it must not be quiet either — an unbacked-up system that says nothing is
            // how a year passes before anybody notices.
            log.error("No backup was taken: {}", e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            // A scheduled method that throws is logged by Spring and the schedule continues, but
            // the message would be a stack trace with no context about which job it was.
            log.error("The nightly backup failed unexpectedly: {}",
                    BackupServiceImpl.describe(e), e);
            return Optional.empty();
        }
    }

    /**
     * Proves the most recent backup restores. Weekly, on Sunday, after the nightly run.
     *
     * <p>Verifies the <em>latest</em> artefact rather than a fixed one, so what is proven is the
     * backup that would actually be used in a recovery — not one taken before whatever changed.
     */
    @Scheduled(cron = "${novocore.backup.restore-check-cron:0 0 4 * * SUN}")
    public Optional<RestoreCheckView> weeklyRestoreCheck() {
        Optional<BackupView> latest = backups.latestSuccessful();
        if (latest.isEmpty()) {
            log.error("No restore check ran: this system has never successfully backed up.");
            return Optional.empty();
        }
        BackupView backup = latest.get();
        if (backup.isPruned()) {
            log.warn("No restore check ran: the latest backup {} has already been pruned.",
                    backup.artefactName());
            return Optional.empty();
        }
        try {
            RestoreCheckView check = backups.verifyRestore(backup.id());
            if (!check.passed()) {
                log.error("RESTORE CHECK FAILED for {}: {}. The backups being taken cannot be "
                        + "relied on until this is understood.",
                        backup.artefactName(), check.errorIfAny().orElse("no reason recorded"));
            }
            return Optional.of(check);
        } catch (RuntimeException e) {
            log.error("The weekly restore check could not run: {}",
                    BackupServiceImpl.describe(e), e);
            return Optional.empty();
        }
    }
}
