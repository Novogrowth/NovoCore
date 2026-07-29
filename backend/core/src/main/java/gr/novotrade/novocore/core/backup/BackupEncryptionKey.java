package gr.novotrade.novocore.core.backup;

import gr.novotrade.novocore.core.api.backup.BackupNotConfiguredException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The key every backup artefact is encrypted with, read from the environment.
 *
 * <h2>Why this is not a setting</h2>
 *
 * <p>Step 11 put the SMTP password in {@code Settings} and argued for it. This is the opposite
 * decision and it is not a reversal — the two differ on one fact that decides everything: the
 * {@code setting} table is <strong>inside the dump</strong>. A backup key kept there would be
 * encrypted inside the artefact it exists to decrypt, so reading it would require the backup it
 * is needed to read. There is no ordering of those steps that terminates.
 *
 * <p>It is therefore an environment variable, and never written to the database, the audit log,
 * or any log line. {@link #fingerprint()} is what gets recorded instead.
 *
 * <h2>The obligation this creates</h2>
 *
 * <p><strong>The key must be recorded outside this system.</strong> It lives in
 * {@code docker/.env}, which is gitignored and machine-local — so if it exists nowhere else, then
 * losing the host loses both the database and the only means of reading its backups, on the one
 * day both were supposed to help. A password manager entry is the whole remedy and takes a
 * minute. The application logs this obligation on every start where a key is present, because a
 * warning nobody reads is still better than an assumption nobody stated.
 *
 * <h2>Absent rather than generated</h2>
 *
 * <p>A missing key refuses to back up. Generating one on first run would be worse than useless:
 * it would be stored somewhere to survive a restart — and the only place available is the
 * database, which is the arrangement ruled out above.
 */
@Component
public class BackupEncryptionKey {

    /** AES-256. 32 bytes, base64-encoded in the environment. */
    static final int KEY_LENGTH_BYTES = 32;

    private final String configured;

    BackupEncryptionKey(
            @Value("${novocore.backup.encryption-key:${NOVOCORE_BACKUP_ENCRYPTION_KEY:}}")
            String configured) {
        this.configured = configured == null ? "" : configured.trim();
    }

    boolean isPresent() {
        return !configured.isBlank();
    }

    /**
     * @throws BackupNotConfiguredException if unset or unusable. Named loudly, because the
     *     alternative failure — backing up without encryption "just this once" — would upload the
     *     entire ledger, every password hash and every stored document to two consumer Google
     *     accounts in the clear.
     */
    SecretKeySpec require() {
        return new SecretKeySpec(requireBytes(), "AES");
    }

    /**
     * First 16 hex characters of SHA-256 over the key: enough to tell two keys apart, useless for
     * recovering one.
     *
     * <p>Recorded on every artefact so that restoring with a rotated key can say "this was
     * encrypted with a different key" instead of surfacing as a GCM authentication failure, which
     * reads as "your backup is corrupt" — the most alarming possible way to report a solvable
     * problem.
     */
    Optional<String> fingerprint() {
        if (!isPresent()) {
            return Optional.empty();
        }
        return Optional.of(fingerprintOf(requireBytes()));
    }

    static String fingerprintOf(byte[] key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", e);
        }
    }

    private byte[] requireBytes() {
        if (!isPresent()) {
            throw new BackupNotConfiguredException(
                    "NOVOCORE_BACKUP_ENCRYPTION_KEY is not set, so no backup can be taken. It is "
                            + "a base64-encoded 32-byte key, and it is deliberately not a setting: "
                            + "the settings table is inside the dump, so a key stored there would "
                            + "be encrypted inside the artefact it decrypts. Generate one with "
                            + "`openssl rand -base64 32`, put it in docker/.env, and record it in "
                            + "a password manager — if it exists only on this host, losing the "
                            + "host makes every backup unreadable.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured);
        } catch (IllegalArgumentException e) {
            throw new BackupNotConfiguredException(
                    "NOVOCORE_BACKUP_ENCRYPTION_KEY is not valid base64. Generate one with "
                            + "`openssl rand -base64 32`.");
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            // Refused rather than stretched with a KDF. Accepting a short key by hashing it would
            // silently turn a typo'd 4-character value into a "valid" 256-bit key and produce
            // backups that look encrypted and are trivially breakable.
            throw new BackupNotConfiguredException(
                    "NOVOCORE_BACKUP_ENCRYPTION_KEY decodes to %d bytes; AES-256 needs exactly %d. "
                            .formatted(decoded.length, KEY_LENGTH_BYTES)
                            + "Generate one with `openssl rand -base64 32`.");
        }
        return decoded;
    }
}
