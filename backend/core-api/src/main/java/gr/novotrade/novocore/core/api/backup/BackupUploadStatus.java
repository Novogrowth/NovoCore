package gr.novotrade.novocore.core.api.backup;

/** What happened to one copy of one backup at one destination. */
public enum BackupUploadStatus {

    /** Queued for this destination, not yet sent. */
    PENDING,

    /** On the destination, with its remote file id recorded. */
    UPLOADED,

    /** Attempted and rejected, with a stated reason. */
    FAILED,

    /**
     * This destination has no usable credentials, so nothing was attempted.
     *
     * <p>Its own status rather than {@link #FAILED}, because the two need different responses: a
     * failure is a thing that went wrong and may work next time, while this is a thing nobody has
     * set up yet and never will until somebody does. Recording it as a failure would bury an
     * unconfigured destination in a list of transient errors — and recording it as nothing at all
     * would let a system quietly believe it has two off-site copies when it has none.
     */
    NOT_CONFIGURED,

    /** Removed from the destination by the retention rule. The row stays as the record that it was there. */
    PRUNED
}
