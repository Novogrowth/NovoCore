package gr.novotrade.novocore.core.api.email;

/**
 * Where a sent message's attachment keeps its bytes.
 *
 * <p>Recorded on the outbox row rather than inferred from which columns are populated, for the
 * same reason step 9 stores {@code vat_class_source}: the question being asked later is "why does
 * this history entry look like this?", and once a document has been deleted or an inline copy
 * pruned, both shapes are a row with no bytes in it. Without this column the two would be
 * indistinguishable, and the history could not say which happened.
 */
public enum EmailAttachmentSource {

    /**
     * The bytes live in the outbox, because the file existed nowhere else — a generated Purchase
     * Order PDF, a report. These are the only part of the outbox that grows without bound, and
     * the only part a retention policy would prune.
     */
    INLINE,

    /**
     * The bytes live in {@code AttachmentService}, which owns them, and the outbox holds a
     * reference plus the document's identity as it was at queue time. Emailing a stored document
     * costs a row, not a second copy of the file.
     */
    ATTACHMENT
}
