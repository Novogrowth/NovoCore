package gr.novotrade.novocore.core.api.email;

/**
 * An attachment from the sent-email history, with its bytes.
 *
 * <p>Its own type rather than {@code AttachmentContent}, because half of these have no
 * {@code AttachmentMetadata} behind them: an inline attachment belongs to no core record and has
 * no entity type, entity id or stored checksum. What both shapes genuinely share is a filename, a
 * content type and bytes, and that is what this carries.
 *
 * <p>The filename and content type are the ones the message was <em>sent</em> with, snapshotted at
 * queue time, not whatever the document is called now.
 *
 * <p>The array is not copied on the way out, matching {@code AttachmentContent}. Treat
 * {@link #content} as read-only.
 */
public record EmailAttachmentContent(String filename, String contentType, byte[] content) {
}
