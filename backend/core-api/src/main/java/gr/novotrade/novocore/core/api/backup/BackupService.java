package gr.novotrade.novocore.core.api.backup;

import java.util.List;
import java.util.Optional;

/**
 * Automated backups — brief §3's "automated backups to two Google Drive accounts", and brief
 * §13's outstanding "restore untested" risk closed in the same step.
 *
 * <p>A scheduled {@code pg_dump}, encrypted with AES-256-GCM before anything leaves the host,
 * copied to every configured destination, pruned on a stated retention rule, and periodically
 * restored into a scratch database to prove it can be.
 *
 * <h2>An untested backup is a belief, not a backup</h2>
 *
 * <p>{@link #verifyRestore} exists because the usual way a backup regime fails is silent: dumps
 * that write, upload and look correct for months, and cannot be read back. It is part of this
 * step rather than a later intention for exactly that reason, and it asserts more than "the file
 * parses" — the restored database must report the expected schema version, carry the expected
 * rows, and <strong>still balance</strong>, which is the one property this system cannot survive
 * losing.
 *
 * <h2>The encryption key is not in the database, and cannot be</h2>
 *
 * <p>It comes from {@code NOVOCORE_BACKUP_ENCRYPTION_KEY}. This is the opposite of where step 11
 * put the SMTP password, and not a change of heart: the {@code setting} table is <em>inside</em>
 * the dump. A key kept there would be encrypted inside the artefact it exists to decrypt.
 *
 * <p>The operational consequence has teeth and is stated rather than assumed: <strong>that key
 * must be recorded outside this system</strong>, in a password manager, or every backup ever
 * taken becomes unreadable the day the host is lost — which is the day they were for.
 *
 * <h2>Off-site is reported separately from success</h2>
 *
 * <p>{@link BackupRunStatus#SUCCEEDED} means the artefact was written and checksummed. Whether a
 * copy reached Drive is per-destination, on {@link BackupUploadView}, and summarised by
 * {@link BackupView#isOffsite()}. A backup that exists only on the machine it protects is not a
 * failure and is not a success either, and this interface refuses to collapse the two.
 */
public interface BackupService {

    /**
     * Takes a backup now: dump, encrypt, checksum, write locally, upload to every configured
     * destination, then apply retention.
     *
     * <p>Synchronous and slow by nature, which is why nothing in a request path calls it — the
     * scheduler does. {@code CLAUDE.md} rule 4 is not in tension with that: this is not a core
     * operation waiting on an adapter, it is a background job whose whole purpose is the adapter
     * call.
     *
     * <p><strong>An upload failure does not fail the backup.</strong> The artefact is already
     * written and safe on disk; recording the run as failed because Google was unreachable would
     * discard a good backup over a network error, and would make the run history unusable for
     * answering "when did we last successfully dump?". The destination records its own failure.
     *
     * @throws BackupNotConfiguredException if backups cannot run at all — no encryption key, no
     *     {@code pg_dump}, an unwritable directory. Not thrown for an unconfigured destination.
     */
    BackupView runNow();

    /** Backup attempts, newest first, each with its destinations. */
    List<BackupView> recent(int limit);

    Optional<BackupView> find(long backupRunId);

    /**
     * The most recent successful backup, which is the one a restore would start from.
     *
     * <p>Empty is a real and alarming answer, not an error: it means this system has never
     * successfully backed up.
     */
    Optional<BackupView> latestSuccessful();

    /**
     * Restores a backup into a scratch database, asserts against it, and drops it.
     *
     * <p>Runs against the artefact on local disk. Verifying a <em>downloaded</em> copy would
     * additionally prove the upload, and is worth doing later; this proves the artefact and the
     * encryption round trip, which is where the silent failures actually are.
     *
     * @param backupRunId which backup to verify
     * @throws IllegalArgumentException if no such backup exists, or it did not succeed, or its
     *     artefact has already been pruned
     */
    RestoreCheckView verifyRestore(long backupRunId);

    /** Verification history for one backup, newest first. */
    List<RestoreCheckView> restoreChecksFor(long backupRunId);

    /** The most recent verification of any backup — "when did we last prove a restore works?". */
    Optional<RestoreCheckView> latestRestoreCheck();

    /**
     * Reports what is configured and, for each destination, actually connects and looks for the
     * target folder without uploading anything.
     *
     * <p>A refresh token that reads back correctly and no longer works is the common case — Google
     * expires them on password change, on consent revocation, and after six months of disuse —
     * and only a real call distinguishes it from a working one.
     */
    BackupConfigurationStatus verifyConfiguration();
}
