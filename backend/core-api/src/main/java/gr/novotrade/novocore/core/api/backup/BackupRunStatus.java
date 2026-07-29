package gr.novotrade.novocore.core.api.backup;

/** How one backup attempt ended. */
public enum BackupRunStatus {

    /** Started and not yet finished. A row stuck here means the process died mid-dump. */
    RUNNING,

    /**
     * The encrypted artefact was written and checksummed.
     *
     * <p>Says nothing about whether it reached Drive — that is per-destination and lives on
     * {@link BackupUploadView}. A backup that dumped perfectly and uploaded nowhere is a
     * {@code SUCCEEDED} run with no off-site copy, and {@link BackupView#isOffsite()} is what
     * distinguishes them. Rolling the two together would let "backup succeeded" mean a file that
     * exists only on the machine the backup protects against losing.
     */
    SUCCEEDED,

    /** Gave up, with a stated reason. Never silent. */
    FAILED
}
