package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.backup.BackupRunStatus;
import gr.novotrade.novocore.core.api.backup.RestoreCheckView;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Proves a backup restores, by restoring it.
 *
 * <p>Brief §13 lists "backup restore test" as an outstanding risk, and it is the right thing to
 * have listed: the characteristic failure of a backup regime is not a dump that errors, it is a
 * dump that writes, uploads and looks correct every night for a year and cannot be read back. That
 * failure has no symptom until the day it is discovered, and on that day there is nothing to be
 * done about it.
 *
 * <h2>What is asserted, and why it is more than "the file parses"</h2>
 *
 * <p>A {@code pg_restore} that exits zero proves the archive was well-formed. It does not prove the
 * database means anything. So the restored copy is then queried:
 *
 * <ul>
 *   <li>the migration history came back, at the version the live database is on;
 *   <li>the tables that must never be empty are not — the chart of accounts and the settings;
 *   <li><strong>the restored ledger balances.</strong> Debits equal credits is the invariant
 *       {@code CLAUDE.md} rule 6 makes structural, and it is the one property this system cannot
 *       survive losing. Checking it against the restored copy is what turns "the file restored"
 *       into "the books restored".
 * </ul>
 *
 * <h2>The plaintext dump exists, briefly, and only here</h2>
 *
 * <p>Taking a backup never writes an unencrypted dump to disk. Verifying one must:
 * {@code pg_restore} reads a file, not a pipe, for the custom format's random access. The
 * decrypted copy is written with owner-only permissions into the backup directory — a known
 * location on a volume that is already assumed to hold backups, rather than a system temp
 * directory — and deleted in a {@code finally}. This is a real, stated trade-off rather than an
 * oversight, and it is the reason the verification runs on a schedule rather than continuously.
 */
@Component
class RestoreVerifier {

    private static final Logger log = LoggerFactory.getLogger(RestoreVerifier.class);

    private static final String ENTITY_TYPE = "Backup";

    private final BackupRunRepository runs;
    private final RestoreCheckJournal journal;
    private final PostgresTools postgres;
    private final BackupEncryptionKey encryptionKey;
    private final AuditLogService auditLog;
    private final SettingsService settings;
    private final DatabaseConnectionProvider connections;

    RestoreVerifier(BackupRunRepository runs, RestoreCheckJournal journal, PostgresTools postgres,
            BackupEncryptionKey encryptionKey, AuditLogService auditLog, SettingsService settings,
            DatabaseConnectionProvider connections) {
        this.runs = runs;
        this.journal = journal;
        this.postgres = postgres;
        this.encryptionKey = encryptionKey;
        this.auditLog = auditLog;
        this.settings = settings;
        this.connections = connections;
    }

    RestoreCheckView verify(long backupRunId) {
        BackupRun run = requireVerifiable(backupRunId);
        Path artefact = artefactPath(run);
        SecretKeySpec key = encryptionKey.require();

        requireMatchingKey(run, key);

        long checkId = journal.started(backupRunId);
        List<String> findings = new ArrayList<>();
        Path plaintext = null;
        DatabaseConnection live = connections.current();
        String scratchName = scratchDatabaseName(live);

        try {
            plaintext = decrypt(artefact, key);
            findings.add("Decrypted %s (%,d bytes of dump) with key %s."
                    .formatted(run.getArtefactName(), Files.size(plaintext),
                            run.getEncryptionKeyFingerprint()));

            recreateScratchDatabase(live, scratchName);
            findings.add("Created scratch database " + scratchName + ".");

            DatabaseConnection scratch = live.withDatabase(scratchName);
            postgres.restore(scratch, plaintext);
            findings.add("pg_restore completed.");

            findings.addAll(assertRestoredDatabase(live, scratch));

            RestoreCheckView view = journal.passed(checkId, findings);
            log.info("Restore check for {} PASSED.", run.getArtefactName());
            auditLog.record("backup.restore-verified", ENTITY_TYPE, String.valueOf(backupRunId),
                    Map.of("artefact", run.getArtefactName(), "result", "PASSED"));
            return view;
        } catch (Exception e) {
            String message = BackupServiceImpl.describe(e);
            log.error("Restore check for {} FAILED: {}", run.getArtefactName(), message);
            auditLog.record("backup.restore-verified", ENTITY_TYPE, String.valueOf(backupRunId),
                    Map.of("artefact", run.getArtefactName(), "result", "FAILED",
                            "error", message));
            return journal.failed(checkId, findings, message);
        } finally {
            deleteQuietly(plaintext);
            dropScratchDatabase(live, scratchName);
        }
    }

    /**
     * The assertions that make this a restore <em>check</em> rather than a restore attempt.
     *
     * <p>Compared against the live database rather than against fixed numbers, so the check keeps
     * working as the data grows and still catches the failure that matters — a restore that
     * silently brings back less than it should.
     */
    private List<String> assertRestoredDatabase(DatabaseConnection live,
            DatabaseConnection scratch) {
        List<String> findings = new ArrayList<>();

        String liveVersion = queryOne(live, "SELECT max(version) FROM flyway_schema_history");
        String restoredVersion = queryOne(scratch, "SELECT max(version) FROM flyway_schema_history");
        if (!liveVersion.equals(restoredVersion)) {
            throw new IllegalStateException(
                    "The restored database is at schema version %s; the live one is at %s."
                            .formatted(restoredVersion, liveVersion));
        }
        findings.add("Schema version " + restoredVersion + ", matching the live database.");

        for (String table : List.of("account", "setting", "journal_entry", "journal_line")) {
            long liveRows = Long.parseLong(queryOne(live, "SELECT count(*) FROM " + table));
            long restoredRows = Long.parseLong(queryOne(scratch, "SELECT count(*) FROM " + table));
            if (restoredRows != liveRows) {
                throw new IllegalStateException(
                        "Table %s restored %d row(s); the live database has %d."
                                .formatted(table, restoredRows, liveRows));
            }
            findings.add("%s: %,d rows.".formatted(table, restoredRows));
        }

        // The assertion this whole feature is for. CLAUDE.md rule 6 makes debits = credits
        // structural in the live database; a backup that restores a ledger which no longer
        // balances has restored a file and lost the books.
        String residual = queryOne(scratch, """
                SELECT coalesce(sum(CASE WHEN side = 'DEBIT' THEN amount ELSE -amount END), 0)
                  FROM journal_line
                """);
        if (new java.math.BigDecimal(residual).signum() != 0) {
            throw new IllegalStateException(
                    "The restored ledger does not balance: debits minus credits is " + residual);
        }
        findings.add("The restored ledger balances: debits minus credits is 0.");

        return findings;
    }

    private BackupRun requireVerifiable(long backupRunId) {
        BackupRun run = runs.findById(backupRunId).orElseThrow(() ->
                new IllegalArgumentException("No backup run with id " + backupRunId));
        if (run.getStatus() != BackupRunStatus.SUCCEEDED) {
            throw new IllegalArgumentException(
                    "Backup %d is %s; only a successful backup has an artefact to restore."
                            .formatted(backupRunId, run.getStatus()));
        }
        if (run.getPrunedAt() != null) {
            throw new IllegalArgumentException(
                    "Backup %d (%s) has been pruned by the retention rule; its artefact is gone."
                            .formatted(backupRunId, run.getArtefactName()));
        }
        return run;
    }

    /**
     * Refuses early when the configured key is not the one that encrypted this artefact.
     *
     * <p>Without this the decryption fails on the GCM tag, which is indistinguishable from
     * corruption and reads as "your backup is damaged" — the most alarming possible way to report
     * "the key has been rotated since this was taken".
     */
    private void requireMatchingKey(BackupRun run, SecretKeySpec key) {
        String current = BackupEncryptionKey.fingerprintOf(key.getEncoded());
        String recorded = run.getEncryptionKeyFingerprint();
        if (recorded != null && !recorded.equals(current)) {
            throw new IllegalArgumentException(
                    ("Backup %s was encrypted with key %s and the configured key is %s. This is a "
                            + "key rotation, not a damaged backup: restoring it needs the key it "
                            + "was taken with.")
                            .formatted(run.getArtefactName(), recorded, current));
        }
    }

    private Path decrypt(Path artefact, SecretKeySpec key) throws Exception {
        Path target = artefact.resolveSibling(artefact.getFileName() + ".restore-check.tmp");
        try (InputStream in = Files.newInputStream(artefact);
                OutputStream out = Files.newOutputStream(target)) {
            BackupCipher.decrypt(in, out, key);
        }
        restrictToOwner(target);
        return target;
    }

    /**
     * Owner-only, on the platforms that have such a thing.
     *
     * <p>Best effort by design: this is a defence in depth on a file that exists for seconds, and
     * failing the whole restore check because the filesystem does not carry POSIX permissions —
     * which is the case on the Windows development machines this is written on — would trade a
     * real guarantee for a theoretical one.
     */
    private void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException e) {
            log.debug("Could not restrict permissions on {}: {}", path, e.getMessage());
        }
    }

    private void recreateScratchDatabase(DatabaseConnection live, String scratchName) {
        // Connected to the live database only to issue the statement; nothing is written to it.
        postgres.runStatement(live, "DROP DATABASE IF EXISTS \"%s\"".formatted(scratchName));
        postgres.runStatement(live, "CREATE DATABASE \"%s\"".formatted(scratchName));
    }

    private void dropScratchDatabase(DatabaseConnection live, String scratchName) {
        try {
            postgres.runStatement(live, "DROP DATABASE IF EXISTS \"%s\"".formatted(scratchName));
        } catch (RuntimeException e) {
            // Left behind rather than retried into a loop, and said out loud: a scratch database
            // that survives is a full copy of the production data sitting on the same server.
            log.error("The restore-check database '{}' could not be dropped and is still present, "
                    + "holding a full copy of the data: {}", scratchName, e.getMessage());
        }
    }

    private String scratchDatabaseName(DatabaseConnection live) {
        String configured = settings.find("backup.restore-check.database")
                .filter(value -> !value.isBlank())
                .orElse("novocore_restore_check")
                .trim();
        if (configured.equalsIgnoreCase(live.database())) {
            // The single most destructive misconfiguration available in this feature: the check
            // begins by dropping this database.
            throw new IllegalStateException(
                    ("Setting 'backup.restore-check.database' is '%s', which is the LIVE database. "
                            + "The restore check drops and recreates it, so this is refused.")
                            .formatted(configured));
        }
        if (!configured.matches("[a-z0-9_]+")) {
            // It is interpolated into DROP DATABASE. Quoted, but a whitelist is what makes that
            // safe rather than merely careful.
            throw new IllegalStateException(
                    "Setting 'backup.restore-check.database' is '%s'; only lower-case letters, "
                            .formatted(configured) + "digits and underscores are allowed.");
        }
        return configured;
    }

    private String queryOne(DatabaseConnection connection, String sql) {
        return postgres.runStatement(connection, sql).strip();
    }

    private Path artefactPath(BackupRun run) {
        Path directory = Path.of(settings.find("backup.local-directory").orElse(""));
        Path artefact = directory.resolve(run.getArtefactName());
        if (!Files.isReadable(artefact)) {
            throw new IllegalArgumentException(
                    "The artefact for backup %d is not on this machine at %s. Restoring an "
                            .formatted(run.getId(), artefact)
                            + "off-site copy means downloading it first, which this does not do.");
        }
        return artefact;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("The decrypted dump {} could not be deleted and is still on disk: {}",
                    path, e.getMessage());
        }
    }

    List<RestoreCheckView> checksFor(long backupRunId) {
        return journal.checksFor(backupRunId);
    }

    Optional<RestoreCheckView> latest() {
        return journal.latest();
    }
}
