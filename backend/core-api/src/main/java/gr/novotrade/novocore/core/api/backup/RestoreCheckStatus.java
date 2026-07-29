package gr.novotrade.novocore.core.api.backup;

/** How one restore verification ended. */
public enum RestoreCheckStatus {

    RUNNING,

    /** The artefact decrypted, restored into a scratch database, and passed every assertion. */
    PASSED,

    /**
     * The artefact could not be restored, or the restored database failed an assertion.
     *
     * <p>This is the status the whole step exists to be able to produce. Brief §13 lists "backup
     * restore test" as an outstanding risk precisely because an untested backup fails silently:
     * it writes, it uploads, it looks right, and it cannot be read back.
     */
    FAILED
}
