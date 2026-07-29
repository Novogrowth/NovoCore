package gr.novotrade.novocore.core.api.attachment;

import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which {@link Section} governs a document, given the record it is attached to.
 *
 * <h2>Why this exists: Q44's access-path half needs something to check against</h2>
 *
 * <p>ADR 0012 decided that a <em>referenced</em> email attachment must be re-checked against the
 * core record the document belongs to before its bytes are returned — <strong>an email having been
 * sent to someone does not change who may see the source document afterwards</strong>, and the
 * outbox must not become a second, weaker access path. Without that check, a role that cannot open a
 * purchase invoice could read its PDF out of the email that sent it.
 *
 * <p>Implementing it turned up what the decision could not have known: {@link AttachmentService}
 * takes {@code entityType} as free text, so <em>there was nothing to call
 * {@code requireView} with</em>. This is that missing piece — one place mapping an owner type to the
 * section that governs it.
 *
 * <h2>Fail closed, always</h2>
 *
 * <p>An unrecognised owner type is <strong>denied</strong>, not allowed. It is the only defensible
 * default: an owner type this does not know about is one whose visibility rules this cannot reason
 * about, and permitting it would mean the check quietly stops protecting whatever is added next.
 * {@link #sectionFor} therefore returns empty rather than a permissive fallback, and
 * {@link #requireAccess} refuses on empty.
 *
 * <p>The consequence is deliberate and worth stating: <strong>attaching documents to a new kind of
 * record requires adding it here</strong>, or nobody will be able to download those documents out of
 * a sent email. That is the correct direction to fail, and it fails visibly.
 *
 * <h2>Nothing attaches documents yet</h2>
 *
 * <p>No core service calls {@code AttachmentService.attach} today, so this list starts as the set of
 * records that will plausibly carry documents first. That does not make the check optional — it
 * makes it cheap to get right now, which is exactly why {@code EmailSender.downloadAttachment}'s
 * javadoc has been carrying the requirement since step 11 rather than leaving it to be discovered.
 */
public enum AttachmentOwnerType {

    /** A supplier's invoice — its PDF is as sensitive as the purchase prices on it. */
    PURCHASE_INVOICE("PurchaseInvoice", Section.PURCHASING),

    /** A delivery note. Same section as the invoice: two halves of one document flow. */
    GOODS_RECEIPT("GoodsReceipt", Section.PURCHASING),

    SALES_INVOICE("SalesInvoice", Section.SALES),

    CREDIT_NOTE("CreditNote", Section.SALES),

    /** A remittance advice or a bank slip. */
    SETTLEMENT("Settlement", Section.SETTLEMENTS),

    /** A datasheet or an image on a product. */
    PRODUCT("Product", Section.PRODUCTS),

    CUSTOMER("Customer", Section.CUSTOMERS),

    SUPPLIER("Supplier", Section.SUPPLIERS),

    /** A purchase contract or a warranty on a fixed asset. */
    ASSET("Asset", Section.FIXED_ASSETS),

    /** A manual journal entry's supporting document. */
    JOURNAL_ENTRY("JournalEntry", Section.JOURNAL);

    private static final Map<String, AttachmentOwnerType> BY_ENTITY_TYPE = buildIndex();

    private final String entityType;
    private final Section section;

    AttachmentOwnerType(String entityType, Section section) {
        this.entityType = entityType;
        this.section = section;
    }

    /** The string {@code AttachmentService.attach} is called with. */
    public String entityType() {
        return entityType;
    }

    public Section section() {
        return section;
    }

    /**
     * The owner type for an {@code entityType} string, or empty if it is not one this knows.
     *
     * <p>Case-insensitive, because the string is written by hand at every call site and a
     * capitalisation slip should not become a security decision. Empty is the fail-closed answer,
     * not an error to log and continue past.
     */
    public static Optional<AttachmentOwnerType> forEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ENTITY_TYPE.get(normalise(entityType)));
    }

    /** The section governing an {@code entityType}, or empty when it is unrecognised. */
    public static Optional<Section> sectionFor(String entityType) {
        return forEntityType(entityType).map(AttachmentOwnerType::section);
    }

    /**
     * Refuses unless {@code viewer} may view the section governing {@code entityType}.
     *
     * <p>The single implementation of Q44's rule. Callers pass the owner type off the attachment's
     * own metadata, so the check is against the record the document actually belongs to rather than
     * against anything the caller supplied.
     *
     * @throws gr.novotrade.novocore.core.api.security.SectionAccessDeniedException if the role may
     *     not view the governing section, <strong>or if the owner type is unrecognised</strong> —
     *     the two are the same answer on purpose, since an unknown type is one whose rules cannot be
     *     evaluated and the caller must not be able to tell those cases apart
     */
    public static void requireAccess(String entityType, RoleView viewer) {
        java.util.Objects.requireNonNull(viewer, "viewer");
        Section section = sectionFor(entityType).orElseThrow(() ->
                new gr.novotrade.novocore.core.api.security.SectionAccessDeniedException(
                        viewer.name(),
                        // No section can be named, because none is known. Reported as a refusal
                        // against the most restrictive thing there is rather than as a distinct
                        // failure a caller could probe for.
                        Section.JOURNAL,
                        gr.novotrade.novocore.core.api.security.AccessLevel.VIEW));
        viewer.requireView(section);
    }

    private static Map<String, AttachmentOwnerType> buildIndex() {
        java.util.Map<String, AttachmentOwnerType> index = new java.util.HashMap<>();
        for (AttachmentOwnerType type : values()) {
            index.put(normalise(type.entityType), type);
        }
        return Map.copyOf(index);
    }

    private static String normalise(String entityType) {
        return entityType.trim().toLowerCase(Locale.ROOT);
    }
}
