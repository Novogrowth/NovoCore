package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupConfigurationStatus;
import gr.novotrade.novocore.core.api.backup.BackupDestinationStatus;
import gr.novotrade.novocore.core.api.backup.BackupNotConfiguredException;
import gr.novotrade.novocore.core.api.backup.BackupService;
import gr.novotrade.novocore.core.api.backup.BackupView;
import gr.novotrade.novocore.core.api.backup.RestoreCheckView;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class BackupServiceImpl implements BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupServiceImpl.class);

    private static final DateTimeFormatter ARTEFACT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    /** The extension says both things a later reader needs: a pg_dump, and encrypted. */
    private static final String ARTEFACT_SUFFIX = ".dump.enc";

    private final BackupJournal journal;
    private final BackupRetentionService retention;
    private final RestoreVerifier restoreVerifier;
    private final PostgresTools postgres;
    private final GoogleDriveClient drive;
    private final BackupEncryptionKey encryptionKey;
    private final SettingsService settings;
    private final DatabaseConnectionProvider connections;
    private final Clock clock;

    BackupServiceImpl(BackupJournal journal, BackupRetentionService retention,
            RestoreVerifier restoreVerifier, PostgresTools postgres, GoogleDriveClient drive,
            BackupEncryptionKey encryptionKey, SettingsService settings,
            DatabaseConnectionProvider connections, Clock clock) {
        this.journal = journal;
        this.retention = retention;
        this.restoreVerifier = restoreVerifier;
        this.postgres = postgres;
        this.drive = drive;
        this.encryptionKey = encryptionKey;
        this.settings = settings;
        this.connections = connections;
        this.clock = clock;
    }

    @Override
    public BackupView runNow() {
        // Checked before a row is written. A run that could never have worked should leave no
        // record suggesting it was attempted and failed for some incidental reason.
        postgres.requireAvailable();
        SecretKeySpec key = encryptionKey.require();
        Path directory = localDirectory();

        DatabaseConnection connection = connection();
        Instant startedAt = Instant.now(clock);
        String artefactName = artefactName(startedAt, connection.database());
        List<DriveDestination.Configured> destinations = DriveDestination.all(settings);

        long runId = journal.started(artefactName, startedAt, destinations);
        Path artefact = directory.resolve(artefactName);

        BackupCipher.Written written;
        try {
            written = dumpAndEncrypt(connection, artefact, key);
        } catch (Exception e) {
            // The half-written artefact goes. Leaving it would put a file in the directory that
            // looks like a backup, sorts newest, and cannot be restored — which is worse than no
            // file at all, because retention would then count it as one of the seven.
            deleteQuietly(artefact);
            String message = describe(e);
            log.error("Backup {} failed: {}", artefactName, message);
            journal.failed(runId, Instant.now(clock), message);
            return journal.find(runId).orElseThrow();
        }

        journal.succeeded(runId, Instant.now(clock), written.sizeBytes(),
                written.checksumSha256(), BackupEncryptionKey.fingerprintOf(key.getEncoded()));
        log.info("Backup {} written: {} bytes.", artefactName, written.sizeBytes());

        uploadEverywhere(runId, artefact, artefactName, destinations);

        // Retention last, so a failure here cannot cost the backup that was just taken.
        try {
            retention.apply();
        } catch (RuntimeException e) {
            log.error("Backup {} was taken, but the retention pass failed: {}",
                    artefactName, describe(e));
        }

        BackupView view = journal.find(runId).orElseThrow();
        if (!view.isOffsite()) {
            // The state worth shouting about: backups are running, they are correct, and every
            // copy would be lost with the host they exist to protect against losing.
            log.error("Backup {} exists only on this machine — no destination holds a copy. {}",
                    artefactName,
                    view.destinationsNeedingAttention().stream()
                            .map(upload -> upload.destinationKey() + ": "
                                    + upload.errorIfAny().orElse(upload.status().name()))
                            .toList());
        }
        return view;
    }

    /**
     * Runs {@code pg_dump} and encrypts its output straight into the artefact file.
     *
     * <p>One pipeline, no intermediate file: the unencrypted database never exists on disk, not
     * even for the seconds an encrypt-afterwards design would need, and not in a temp directory a
     * crash could leave it in.
     */
    private BackupCipher.Written dumpAndEncrypt(DatabaseConnection connection, Path artefact,
            SecretKeySpec key) throws Exception {
        PostgresTools.RunningDump dump = postgres.startDump(connection);
        BackupCipher.Written written;
        try (InputStream plaintext = dump.output();
                OutputStream target = Files.newOutputStream(artefact)) {
            written = BackupCipher.encrypt(plaintext, target, key);
        }
        // Checked after the streams close, and this order is load-bearing: pg_dump can write a
        // perfectly well-formed partial dump and then fail, so a non-zero exit is the only thing
        // that distinguishes a complete backup from a truncated one that encrypts just as happily.
        dump.awaitSuccess();
        return written;
    }

    private void uploadEverywhere(long runId, Path artefact, String artefactName,
            List<DriveDestination.Configured> destinations) {
        for (DriveDestination.Configured configured : destinations) {
            if (!configured.isConfigured()) {
                log.warn("Backup destination '{}' is not configured, so {} has no copy there. {}",
                        configured.key(), artefactName, configured.problem());
                continue;
            }
            DriveDestination destination = configured.destination().orElseThrow();
            try {
                String token = drive.accessToken(destination);
                String fileId = drive.upload(destination, token, artefact, artefactName);
                journal.uploaded(runId, configured.key(), fileId, Instant.now(clock));
            } catch (RuntimeException e) {
                // One destination failing must not stop the other. Two accounts exist precisely so
                // that one of them being unreachable is survivable, and a loop that abandoned the
                // rest on the first error would throw that away.
                String message = describe(e);
                log.error("Backup {} could not be uploaded to {}: {}",
                        artefactName, destination.label(), message);
                journal.uploadFailed(runId, configured.key(), message);
            }
        }
    }

    @Override
    public List<BackupView> recent(int limit) {
        return journal.recent(limit);
    }

    @Override
    public Optional<BackupView> find(long backupRunId) {
        return journal.find(backupRunId);
    }

    @Override
    public Optional<BackupView> latestSuccessful() {
        return journal.latestSuccessful();
    }

    @Override
    public RestoreCheckView verifyRestore(long backupRunId) {
        return restoreVerifier.verify(backupRunId);
    }

    @Override
    public List<RestoreCheckView> restoreChecksFor(long backupRunId) {
        return restoreVerifier.checksFor(backupRunId);
    }

    @Override
    public Optional<RestoreCheckView> latestRestoreCheck() {
        return restoreVerifier.latest();
    }

    @Override
    public BackupConfigurationStatus verifyConfiguration() {
        Optional<String> pgDumpVersion = postgres.pgDumpVersion();
        String problem = null;
        boolean canRun = true;

        if (pgDumpVersion.isEmpty()) {
            problem = "pg_dump is not runnable in this environment.";
            canRun = false;
        } else if (!encryptionKey.isPresent()) {
            problem = "NOVOCORE_BACKUP_ENCRYPTION_KEY is not set.";
            canRun = false;
        } else {
            try {
                localDirectory();
            } catch (BackupNotConfiguredException e) {
                problem = e.getMessage();
                canRun = false;
            }
        }

        List<BackupDestinationStatus> destinations = new ArrayList<>();
        for (DriveDestination.Configured configured : DriveDestination.all(settings)) {
            if (!configured.isConfigured()) {
                destinations.add(BackupDestinationStatus.notConfigured(
                        configured.key(), configured.label(), configured.problem()));
                continue;
            }
            DriveDestination destination = configured.destination().orElseThrow();
            try {
                // A real call, not a settings read. A refresh token that reads back perfectly and
                // no longer works is the common failure here, and nothing short of using it tells
                // the two apart.
                String folderName = drive.verifyFolder(
                        destination, drive.accessToken(destination));
                destinations.add(BackupDestinationStatus.reachable(
                        configured.key(), configured.label(), destination.folderId(), folderName));
            } catch (RuntimeException e) {
                destinations.add(BackupDestinationStatus.unreachable(
                        configured.key(), configured.label(), destination.folderId(), describe(e)));
            }
        }

        return new BackupConfigurationStatus(
                canRun,
                pgDumpVersion.orElse(null),
                settings.find("backup.local-directory").orElse(null),
                encryptionKey.fingerprint().orElse(null),
                destinations,
                problem);
    }

    private DatabaseConnection connection() {
        return connections.current();
    }

    /**
     * The directory artefacts are written to, created if absent and checked for writability.
     *
     * <p>Checked every run rather than once at startup: a bind mount that disappeared on a
     * container restart is the ordinary way this breaks, and a check performed only at boot would
     * have passed months earlier.
     */
    private Path localDirectory() {
        String configured = settings.find("backup.local-directory")
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new BackupNotConfiguredException(
                        "Setting 'backup.local-directory' is blank, so there is nowhere to write "
                                + "a backup."));
        Path directory = Path.of(configured);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new BackupNotConfiguredException(
                    "Backup directory '%s' cannot be created: %s".formatted(configured,
                            e.getMessage()));
        }
        if (!Files.isWritable(directory)) {
            throw new BackupNotConfiguredException(
                    ("Backup directory '%s' is not writable by this process. In the container it "
                            + "is a mounted volume, which must be writable by uid 10001.")
                            .formatted(configured));
        }
        return directory;
    }

    /**
     * A name that sorts chronologically and states what it is.
     *
     * <p>In the configured calendar zone rather than UTC, so the name of a backup and the month
     * retention files it under agree — a 01:30 Athens backup on the 1st named with a UTC date
     * would read as the previous month while being archived as this one.
     *
     * <p><strong>Two backups in the same second get {@code -2}, {@code -3} rather than colliding.</strong>
     * Timestamped to the second because that is what a person reading a Drive folder wants; the
     * suffix handles the case that produces, which is not hypothetical — somebody taking a manual
     * backup in the same second the scheduler fires, and, as it turned out, a test suite taking
     * two in a row. The alternative of putting milliseconds in every name would make every
     * artefact less readable to accommodate a case that almost never happens.
     *
     * <p>The unique constraint remains the backstop. This loop closes the gap that produces a
     * failed backup for a reason nobody could act on; it does not pretend to be atomic against a
     * second process, which this deployment does not have.
     */
    private String artefactName(Instant startedAt, String database) {
        ZoneId zone = retention.calendarZone();
        String stem = "novocore-%s-%s".formatted(
                ARTEFACT_TIMESTAMP.format(startedAt.atZone(zone)), database);

        String candidate = stem + ARTEFACT_SUFFIX;
        for (int attempt = 2; attempt <= 100 && journal.artefactNameTaken(candidate); attempt++) {
            candidate = "%s-%d%s".formatted(stem, attempt, ARTEFACT_SUFFIX);
        }
        return candidate;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove the incomplete artefact {}: {}", path, e.getMessage());
        }
    }

    static String describe(Throwable error) {
        StringBuilder description = new StringBuilder()
                .append(error.getClass().getSimpleName())
                .append(": ")
                .append(error.getMessage());
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            description.append(" (caused by ")
                    .append(cause.getClass().getSimpleName())
                    .append(": ")
                    .append(cause.getMessage())
                    .append(')');
        }
        return description.toString();
    }
}
