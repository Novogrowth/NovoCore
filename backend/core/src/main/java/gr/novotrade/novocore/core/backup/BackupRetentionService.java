package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupRunStatus;
import gr.novotrade.novocore.core.api.backup.BackupUploadStatus;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the backups the retention rule no longer keeps — from local disk and from every
 * destination that holds a copy.
 *
 * <p>The rule itself is {@link BackupRetentionRule}, deliberately separate and pure. This class is
 * the part with side effects, and it is written so that the irreversible step is the last one and
 * the smallest: work out what goes, delete the remote copies, delete the local file, then record
 * it. An error anywhere in that sequence leaves an artefact that should have gone, which the next
 * pass will remove — the opposite ordering leaves a row saying an artefact was pruned while the
 * file is still there, or worse, a file deleted that the rule wanted kept.
 *
 * <p><strong>Applied identically to local disk and to Drive.</strong> Retention that ran on one
 * and not the other would make "is this backup still available?" have two different answers, and
 * the wrong one would be believed.
 */
@Component
class BackupRetentionService {

    private static final Logger log = LoggerFactory.getLogger(BackupRetentionService.class);

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Athens");

    private final BackupRunRepository runs;
    private final BackupJournal journal;
    private final GoogleDriveClient drive;
    private final SettingsService settings;
    private final Clock clock;

    BackupRetentionService(BackupRunRepository runs, BackupJournal journal, GoogleDriveClient drive,
            SettingsService settings, Clock clock) {
        this.runs = runs;
        this.journal = journal;
        this.drive = drive;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * One retention pass over every surviving successful backup.
     *
     * @return how many artefacts were removed
     */
    int apply() {
        BackupRetentionRule rule = rule();
        List<BackupRetentionRule.Candidate> candidates = candidates();
        if (candidates.isEmpty()) {
            return 0;
        }

        Path directory = Path.of(settings.find("backup.local-directory").orElse(""));
        int removed = 0;

        for (BackupRetentionRule.Decision decision : rule.decide(candidates)) {
            if (decision.retained()) {
                // Recomputed and rewritten every pass rather than set once at creation, so that
                // changing the rule re-evaluates history instead of grandfathering whatever the
                // old rule happened to mark.
                journal.markMonthlyArchive(decision.backupRunId(), decision.monthlyArchive());
                continue;
            }
            if (removeArtefact(decision, directory)) {
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Backup retention removed {} artefact(s). Keeping the most recent {} plus "
                    + "every calendar month's last backup.", removed, dailyCount());
        }
        return removed;
    }

    /** The zone that decides which calendar month a backup belongs to. */
    ZoneId calendarZone() {
        String configured = settings.find("backup.calendar-zone").orElse("").trim();
        if (configured.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(configured);
        } catch (DateTimeException e) {
            // Falls back rather than refusing, and this is the one place in this feature that
            // does. Refusing would stop backups over a typo in a display concern; the cost of the
            // fallback is that a month boundary may be off by an hour or two, which changes which
            // of two adjacent nights is archived and never whether one is.
            log.warn("Setting 'backup.calendar-zone' is '{}', which is not a known zone; using {}.",
                    configured, DEFAULT_ZONE);
            return DEFAULT_ZONE;
        }
    }

    private boolean removeArtefact(BackupRetentionRule.Decision decision, Path directory) {
        Optional<BackupRun> found = runs.findById(decision.backupRunId());
        if (found.isEmpty()) {
            return false;
        }
        BackupRun run = found.get();

        // Remote first. If this fails the local file stays too, so the next pass retries the whole
        // removal — whereas deleting locally first and failing here would leave a copy on Drive
        // that nothing knows how to find again.
        for (BackupUpload upload : run.getUploads()) {
            if (upload.getStatus() != BackupUploadStatus.UPLOADED
                    || upload.getRemoteFileId() == null) {
                continue;
            }
            DriveDestination.Configured configured =
                    DriveDestination.from(settings, upload.getDestinationKey());
            if (!configured.isConfigured()) {
                log.warn("Cannot remove the expired backup {} from '{}': {}",
                        run.getArtefactName(), upload.getDestinationKey(), configured.problem());
                return false;
            }
            DriveDestination destination = configured.destination().orElseThrow();
            try {
                drive.delete(destination, drive.accessToken(destination),
                        upload.getRemoteFileId());
            } catch (RuntimeException e) {
                log.error("Could not remove the expired backup {} from {}: {}",
                        run.getArtefactName(), destination.label(),
                        BackupServiceImpl.describe(e));
                return false;
            }
        }

        try {
            Files.deleteIfExists(directory.resolve(run.getArtefactName()));
        } catch (IOException e) {
            log.error("Could not delete the expired artefact {}: {}",
                    run.getArtefactName(), e.getMessage());
            return false;
        }

        journal.pruned(decision.backupRunId(), Instant.now(clock), decision.monthlyArchive());
        log.info("Pruned backup {} — {}.", run.getArtefactName(), decision.reason());
        return true;
    }

    @Transactional(readOnly = true)
    List<BackupRetentionRule.Candidate> candidates() {
        return runs.findByStatusAndPrunedAtIsNull(BackupRunStatus.SUCCEEDED).stream()
                .map(run -> new BackupRetentionRule.Candidate(run.getId(), run.getStartedAt()))
                .toList();
    }

    private BackupRetentionRule rule() {
        return new BackupRetentionRule(dailyCount(), monthlyCount(), calendarZone());
    }

    private int dailyCount() {
        int configured = readPositiveInt("backup.retention.daily-count", 7);
        return Math.max(configured, 1);
    }

    /** Empty for {@code FOREVER}, which is what the answered policy says. */
    private Optional<Integer> monthlyCount() {
        String value = settings.find("backup.retention.monthly").orElse("").trim();
        if (value.isBlank() || value.equalsIgnoreCase(SettingKeys.RETENTION_FOREVER)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Math.max(Integer.parseInt(value), 1));
        } catch (NumberFormatException e) {
            // Keeping too much is recoverable by editing a setting; deleting a monthly archive
            // because a value was mistyped is not. So an unreadable value means forever.
            log.warn("Setting 'backup.retention.monthly' is '{}', which is neither a number nor {};"
                    + " keeping every monthly archive.", value, SettingKeys.RETENTION_FOREVER);
            return Optional.empty();
        }
    }

    private int readPositiveInt(String key, int fallback) {
        try {
            int value = settings.requireInt(key);
            return value >= 1 ? value : fallback;
        } catch (RuntimeException e) {
            log.warn("Setting '{}' is missing or unreadable ({}); using {}.",
                    key, e.getMessage(), fallback);
            return fallback;
        }
    }

    /** For the scheduler's log line, and for tests. */
    Map<String, String> describePolicy() {
        return Map.of(
                "daily", String.valueOf(dailyCount()),
                "monthly", monthlyCount().map(String::valueOf)
                        .orElse(SettingKeys.RETENTION_FOREVER),
                "zone", calendarZone().getId());
    }
}
