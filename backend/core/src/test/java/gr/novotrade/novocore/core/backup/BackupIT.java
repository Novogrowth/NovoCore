package gr.novotrade.novocore.core.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.backup.BackupNotConfiguredException;
import gr.novotrade.novocore.core.api.backup.BackupRunStatus;
import gr.novotrade.novocore.core.api.backup.BackupUploadStatus;
import gr.novotrade.novocore.core.api.backup.BackupView;
import gr.novotrade.novocore.core.api.backup.RestoreCheckView;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The backup feature end to end: a real {@code pg_dump} of the Testcontainers database, encrypted,
 * uploaded to a stub Drive, pruned by the retention rule, and — the part brief §13 exists for —
 * actually restored and asserted against.
 *
 * <p>Nothing here is simulated except Google. The dump is produced by the real {@code pg_dump},
 * the artefact is really encrypted, and {@link #theRestoreCheckActuallyRestores()} really creates
 * a database, really restores into it and really queries it. A test that stubbed those would prove
 * only that the orchestration compiles, which is precisely the level of assurance brief §13
 * describes as the risk.
 *
 * <p><strong>Requires the PostgreSQL client tools on the PATH</strong>, at a major version
 * matching the server. The runtime image installs {@code postgresql-client-17}; a developer
 * machine needs them too, and the class is skipped rather than failed where they are absent — a
 * red suite on a machine that is missing a tool teaches people to ignore red suites.
 */
class BackupIT extends AbstractCoreIntegrationTest {

    /**
     * base64 of exactly 32 bytes — {@code novocore-test-backup-key-32byte!}.
     *
     * <p>Fixed rather than random, because the tests assert on the key's fingerprint. The first
     * version of this constant was one byte too long and every test failed with the key guard's
     * own message, which is the guard doing its job: a 33-byte key is refused rather than
     * truncated or stretched into something that would have produced backups that looked
     * encrypted.
     */
    private static final String KEY = "bm92b2NvcmUtdGVzdC1iYWNrdXAta2V5LTMyYnl0ZSE=";

    private static StubDriveServer drive;

    @Autowired
    private gr.novotrade.novocore.core.api.backup.BackupService backups;

    @Autowired
    private SettingsService settings;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private PostgresTools postgres;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private DatabaseConnectionProvider connections;

    @TempDir
    Path backupDirectory;

    private Map<String, String> settingsBeforeThisTest;

    /**
     * Points the Drive client at a stub on localhost, and supplies the encryption key.
     *
     * <p>The stub is started here rather than per test because the base URLs are constructor
     * properties on the client: a per-test server would need the context rebuilt each time, which
     * costs more than it buys. Its failure switches are reset in {@link #resetStub()}.
     */
    @DynamicPropertySource
    static void driveAndKey(DynamicPropertyRegistry registry) throws IOException {
        drive = new StubDriveServer();
        registry.add("novocore.backup.encryption-key", () -> KEY);
        registry.add("novocore.backup.drive.token-endpoint", drive::tokenEndpoint);
        registry.add("novocore.backup.drive.api-base", drive::apiBase);
        registry.add("novocore.backup.drive.upload-base", drive::uploadBase);
    }

    @BeforeEach
    void configureBackups() {
        assumeToolsArePresent();

        settingsBeforeThisTest = new HashMap<>();
        List.of("backup.local-directory", "backup.retention.daily-count",
                        "backup.retention.monthly", "backup.calendar-zone",
                        "backup.drive.primary.folder-id", "backup.drive.primary.client-id",
                        "backup.drive.primary.client-secret", "backup.drive.primary.refresh-token",
                        "backup.drive.secondary.folder-id", "backup.drive.secondary.client-id",
                        "backup.drive.secondary.client-secret",
                        "backup.drive.secondary.refresh-token")
                .forEach(key -> settingsBeforeThisTest.put(key, settings.find(key).orElse("")));

        settings.put("backup.local-directory", backupDirectory.toString());

        // Only the primary is configured. The secondary stays blank on purpose, so every test
        // also asserts that an unconfigured destination is recorded rather than silently skipped.
        settings.put("backup.drive.primary.folder-id", "folder-abc");
        settings.put("backup.drive.primary.client-id", "client-abc");
        settings.putSecret("backup.drive.primary.client-secret", "secret-abc");
        settings.putSecret("backup.drive.primary.refresh-token", "refresh-abc");
    }

    @AfterEach
    void restoreSettingsAndStub() {
        if (settingsBeforeThisTest != null) {
            settingsBeforeThisTest.forEach(settings::put);
        }
        resetStub();
    }

    private void resetStub() {
        drive.refreshTokenRejected = false;
        drive.folderMissing = false;
        drive.uploadRejected = false;
        drive.uploaded().clear();
        drive.deleted().clear();
    }

    private void assumeToolsArePresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                postgres.pgDumpVersion().isPresent(),
                "pg_dump is not on the PATH. Install postgresql-client-17 to run the backup "
                        + "tests; the runtime image already has it.");
    }

    // -------------------------------------------------------------------------------------
    // Taking a backup
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("a backup dumps, encrypts, writes and uploads")
    void backupEndToEnd() throws Exception {
        BackupView backup = backups.runNow();

        assertThat(backup.status()).isEqualTo(BackupRunStatus.SUCCEEDED);
        assertThat(backup.artefactName()).endsWith(".dump.enc");
        assertThat(backup.sizeBytesIfAny()).hasValueSatisfying(size ->
                assertThat(size).isPositive());
        assertThat(backup.checksumSha256()).matches("^[0-9a-f]{64}$");
        assertThat(backup.encryptionKeyFingerprint()).hasSize(16);

        Path artefact = backupDirectory.resolve(backup.artefactName());
        assertThat(artefact).exists();
        assertThat(Files.size(artefact)).isEqualTo(backup.sizeBytesIfAny().orElseThrow());

        // The dump is really encrypted: the artefact must not contain pg_dump's own header.
        byte[] bytes = Files.readAllBytes(artefact);
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1))
                .as("an unencrypted custom-format dump begins with PGDMP")
                .doesNotContain("PGDMP");
        assertThat(new String(bytes, 0, 8, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("NOVOBK01");

        // And what reached the destination is byte-for-byte what is on disk.
        assertThat(drive.uploaded()).containsKey(backup.artefactName());
        assertThat(drive.uploaded().get(backup.artefactName())).isEqualTo(bytes);
    }

    @Test
    @DisplayName("an unconfigured destination is recorded, not silently skipped")
    void unconfiguredDestinationIsVisible() {
        BackupView backup = backups.runNow();

        var secondary = backup.uploads().stream()
                .filter(upload -> upload.destinationKey().equals("secondary"))
                .findFirst()
                .orElseThrow();

        assertThat(secondary.status()).isEqualTo(BackupUploadStatus.NOT_CONFIGURED);
        assertThat(secondary.needsAttention()).isTrue();
        assertThat(secondary.errorIfAny())
                .hasValueSatisfying(problem -> assertThat(problem).contains("refresh-token"));
        assertThat(secondary.attempts())
                .as("never configured is not the same as tried and failed")
                .isZero();

        // The backup itself is still a success, and still off-site via the primary.
        assertThat(backup.succeeded()).isTrue();
        assertThat(backup.isOffsite()).isTrue();
        assertThat(backup.offsiteCopies()).isEqualTo(1);
    }

    @Test
    @DisplayName("an upload failure does not fail the backup, and is recorded against the destination")
    void uploadFailureDoesNotDiscardAGoodBackup() {
        drive.uploadRejected = true;

        BackupView backup = backups.runNow();

        // The artefact is written and safe; recording this as a failed backup would discard a good
        // one over a network error and make "when did we last dump successfully?" unanswerable.
        assertThat(backup.status()).isEqualTo(BackupRunStatus.SUCCEEDED);
        assertThat(backupDirectory.resolve(backup.artefactName())).exists();

        var primary = backup.uploads().stream()
                .filter(upload -> upload.destinationKey().equals("primary"))
                .findFirst().orElseThrow();
        assertThat(primary.status()).isEqualTo(BackupUploadStatus.FAILED);
        assertThat(primary.errorIfAny())
                .hasValueSatisfying(error -> assertThat(error).contains("quota"));

        // And the honest headline: this backup exists only on this machine.
        assertThat(backup.isOffsite()).isFalse();
    }

    @Test
    @DisplayName("an expired refresh token is reported as what it is")
    void expiredRefreshTokenIsNamed() {
        drive.refreshTokenRejected = true;

        BackupView backup = backups.runNow();

        var primary = backup.uploads().getFirst();
        assertThat(primary.status()).isEqualTo(BackupUploadStatus.FAILED);
        assertThat(primary.errorIfAny()).hasValueSatisfying(error -> assertThat(error)
                .as("the remedy is to redo the consent flow, and the message must say so")
                .contains("consent flow"));
    }

    @Test
    @DisplayName("without an encryption key nothing is dumped at all")
    void noKeyMeansNoBackup() {
        BackupEncryptionKey noKey = new BackupEncryptionKey("");

        assertThatExceptionOfType(BackupNotConfiguredException.class)
                .isThrownBy(noKey::require)
                .withMessageContaining("password manager");
    }

    @Test
    @DisplayName("configuration verification connects for real and finds the folder")
    void verifyConfigurationIsHonest() {
        var status = backups.verifyConfiguration();

        assertThat(status.canRun()).isTrue();
        assertThat(status.pgDumpVersion()).contains("17");
        assertThat(status.isOffsiteCapable()).isTrue();
        assertThat(status.usableDestinations()).isEqualTo(1);

        var primary = status.destinations().getFirst();
        assertThat(primary.isUsable()).isTrue();
        assertThat(primary.folderNameIfAny()).contains("NovoCore Backups");

        // A folder that has been deleted reads back as configured and is not usable — which is
        // exactly the distinction a settings read alone could never make.
        drive.folderMissing = true;
        var broken = backups.verifyConfiguration();
        assertThat(broken.isOffsiteCapable()).isFalse();
        assertThat(broken.destinations().getFirst().problemIfAny())
                .hasValueSatisfying(problem -> assertThat(problem).contains("does not exist"));
    }

    @Test
    @DisplayName("a backup is audited")
    void backupIsAudited() {
        BackupView backup = backups.runNow();

        assertThat(auditLog.findForEntity("Backup", String.valueOf(backup.id()), 10))
                .extracting(AuditEntry::action)
                .contains("backup.succeeded");
    }

    // -------------------------------------------------------------------------------------
    // The restore check — brief §13's outstanding risk
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("the restore check really restores, and asserts the ledger still balances")
    void theRestoreCheckActuallyRestores() {
        BackupView backup = backups.runNow();

        RestoreCheckView check = backups.verifyRestore(backup.id());

        assertThat(check.passed())
                .as("findings: %s / error: %s", check.findings(), check.errorIfAny())
                .isTrue();
        assertThat(check.findings())
                .anySatisfy(finding -> assertThat(finding).contains("pg_restore completed"))
                .anySatisfy(finding -> assertThat(finding).contains("Schema version"))
                .anySatisfy(finding -> assertThat(finding).contains("account:"))
                // The assertion the whole feature exists to be able to make.
                .anySatisfy(finding -> assertThat(finding).contains("restored ledger balances"));

        assertThat(backups.latestRestoreCheck()).isPresent();
        assertThat(backups.restoreChecksFor(backup.id())).hasSize(1);
    }

    @Test
    @DisplayName("the scratch database is dropped afterwards, however it went")
    void scratchDatabaseIsCleanedUp() {
        BackupView backup = backups.runNow();
        backups.verifyRestore(backup.id());

        String scratch = settings.find("backup.restore-check.database").orElseThrow();
        // A surviving scratch database would be a full second copy of the production data sitting
        // on the same server, which is a worse outcome than the check not having run.
        assertThat(databaseExists(scratch)).isFalse();
    }

    @Test
    @DisplayName("a backup taken with a different key is refused as a key problem, not corruption")
    void rotatedKeyIsNamedAsSuch() {
        BackupView backup = backups.runNow();

        // Standing in for a key rotation: the artefact on disk is encrypted with the current key,
        // but the row says it was taken with another one. That is exactly the state a rotation
        // leaves behind for every older artefact.
        //
        // Without the recorded fingerprint the failure would surface from the cipher as a GCM tag
        // mismatch — indistinguishable from a damaged file, and therefore the most alarming
        // possible way to report "you need the previous key".
        jdbc.update("UPDATE backup_run SET encryption_key_fingerprint = ? WHERE id = ?",
                "0123456789abcdef", backup.id());

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> backups.verifyRestore(backup.id()))
                .withMessageContaining("key rotation")
                .withMessageContaining("0123456789abcdef");
    }

    @Test
    @DisplayName("a corrupted artefact fails the restore check rather than restoring garbage")
    void corruptedArtefactFailsTheCheck() throws Exception {
        BackupView backup = backups.runNow();
        Path artefact = backupDirectory.resolve(backup.artefactName());

        byte[] bytes = Files.readAllBytes(artefact);
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(artefact, bytes);

        RestoreCheckView check = backups.verifyRestore(backup.id());

        // The whole point of authenticating the artefact: a single flipped bit is caught here,
        // months before somebody needs the file, instead of producing a partial restore.
        assertThat(check.passed()).isFalse();
        assertThat(check.errorIfAny()).isPresent();
        assertThat(backups.restoreChecksFor(backup.id()).getFirst().passed()).isFalse();
    }

    @Test
    @DisplayName("verifying a backup that does not exist, or failed, is refused clearly")
    void unverifiableBackupsAreRefused() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> backups.verifyRestore(-1L))
                .withMessageContaining("-1");
    }

    // -------------------------------------------------------------------------------------
    // Retention
    // -------------------------------------------------------------------------------------

    @Test
    @DisplayName("the newest backups are kept and their artefacts stay on disk")
    void recentBackupsSurviveRetention() {
        BackupView first = backups.runNow();
        BackupView second = backups.runNow();

        assertThat(backupDirectory.resolve(first.artefactName())).exists();
        assertThat(backupDirectory.resolve(second.artefactName())).exists();
        assertThat(backups.find(first.id()).orElseThrow().isPruned()).isFalse();

        // Two backups in the same calendar month: the newer is the month's archive now.
        assertThat(backups.find(second.id()).orElseThrow().monthlyArchive()).isTrue();
    }

    @Test
    @DisplayName("recent() lists attempts newest first, with their destinations")
    void recentListsAttempts() {
        backups.runNow();
        backups.runNow();

        List<BackupView> recent = backups.recent(10);

        assertThat(recent).hasSizeGreaterThanOrEqualTo(2);
        assertThat(recent.getFirst().startedAt())
                .isAfterOrEqualTo(recent.get(1).startedAt());
        assertThat(recent.getFirst().uploads())
                .as("both destinations appear on every run, configured or not")
                .hasSize(2);
    }

    @Test
    @DisplayName("latestSuccessful is what a restore would start from")
    void latestSuccessfulIsTheNewestGoodOne() {
        backups.runNow();
        BackupView newest = backups.runNow();

        assertThat(backups.latestSuccessful()).hasValueSatisfying(latest ->
                assertThat(latest.id()).isEqualTo(newest.id()));
    }

    // -------------------------------------------------------------------------------------

    private boolean databaseExists(String name) {
        String result = postgres.runStatement(connections.current(),
                "SELECT count(*) FROM pg_database WHERE datname = '" + name + "'");
        return !result.strip().equals("0");
    }
}
