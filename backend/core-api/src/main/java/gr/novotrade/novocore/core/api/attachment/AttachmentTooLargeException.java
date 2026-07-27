package gr.novotrade.novocore.core.api.attachment;

/**
 * An attachment exceeds the configured maximum size.
 *
 * <p>A ceiling exists because content is stored in the database: without one, a folder of
 * high-resolution scans quietly turns every nightly backup into something that no longer
 * finishes. Rejecting loudly at upload is far better than discovering it in a failed backup.
 */
public class AttachmentTooLargeException extends RuntimeException {

    private final long sizeBytes;
    private final long maxSizeBytes;

    public AttachmentTooLargeException(String filename, long sizeBytes, long maxSizeBytes) {
        super("'%s' is %d bytes, which exceeds the maximum of %d. Raise "
                .formatted(filename, sizeBytes, maxSizeBytes)
                + "attachment.max-size-bytes in Settings if this is genuinely needed, bearing in "
                + "mind that attachment content is included in every database backup.");
        this.sizeBytes = sizeBytes;
        this.maxSizeBytes = maxSizeBytes;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }
}
