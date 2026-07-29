package gr.novotrade.novocore.core.api.backup;

/**
 * Thrown when backups cannot run at all because something they require is missing.
 *
 * <p>Reserved for what stops the <em>whole</em> feature: no encryption key, an unwritable
 * directory, no {@code pg_dump} on the path. A Drive destination with no credentials is
 * <strong>not</strong> this — the dump still runs, still encrypts, still lands on disk, and that
 * destination records {@link BackupUploadStatus#NOT_CONFIGURED}. Refusing to back up at all
 * because the off-site half is unconfigured would trade a partial backup for none.
 */
public class BackupNotConfiguredException extends RuntimeException {

    public BackupNotConfiguredException(String message) {
        super(message);
    }
}
