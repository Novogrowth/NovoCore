package gr.novotrade.novocore.core.api.attachment;

/**
 * An attachment together with its bytes, for download.
 *
 * <p>The array is not copied on the way out. Copying a 25 MiB document to guard against a caller
 * mutating it would cost more than it protects, since the only consumer is a response body being
 * streamed. Treat {@link #content} as read-only.
 */
public record AttachmentContent(AttachmentMetadata metadata, byte[] content) {
}
