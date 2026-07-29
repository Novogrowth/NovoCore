package gr.novotrade.novocore.core.api.security;

/**
 * A specific field that can be hidden from a role even inside a section the role can see — the
 * inner layer of brief §7's two-layer permission model.
 *
 * <p>These are named, enumerated fields rather than free-text column names. A restriction stored
 * as the string {@code "lastPurchasePrice"} would silently stop protecting anything the day that
 * field was renamed, and nothing would fail — the field would simply become visible. As an enum,
 * a rename is a compile error.
 *
 * <p><strong>⚠️ Nothing is restricted today, and that is a decision.</strong> V6 seeded all three of
 * these against Remote/Order Staff, on the reasoning that an order picker has no need to know what a
 * product cost or who supplies it. <strong>V26 removed those rows:</strong> the business has no
 * confidentiality need around purchase price — a bank balance might reasonably stay hidden from a
 * home-based worker, what a bag of beans cost does not.
 *
 * <p>These were the only restrictions in the system and these values are the only fields the
 * mechanism knows about, so <strong>no role has any field restriction at present</strong>. The
 * values are kept rather than deleted because the change was to the policy and not to the model:
 * restricting one again is an {@code INSERT}, not a rebuild, and a plausible future case was named
 * when this was decided — a bank or partner-clearing balance.
 *
 * <p>{@link RoleView#canSee} and {@code ProductView.redactedFor} are unchanged and still correct.
 * They have nothing to hide today, which is a different thing from being wrong. The tests that prove
 * they work therefore create a role and restrict a field at runtime rather than relying on the seed
 * — with no restriction anywhere, a change that stopped the redacting reads consulting the role
 * would otherwise pass everything while removing the guarantee.
 */
public enum ProtectedField {

    /** What the product last cost us to buy. Restrictable, and not restricted (see above). */
    PRODUCT_LAST_PURCHASE_PRICE(Section.PRODUCTS),

    /** Which supplier a product comes from. */
    PRODUCT_SUPPLIER(Section.PRODUCTS),

    /** The supplier's own code for the product, which identifies the supplier indirectly. */
    PRODUCT_SUPPLIER_SKU(Section.PRODUCTS);

    private final Section section;

    ProtectedField(Section section) {
        this.section = section;
    }

    /**
     * The section this field belongs to.
     *
     * <p>Field-level restriction only ever narrows section access; it cannot widen it. A role
     * that cannot view {@link Section#PRODUCTS} does not see these fields regardless of whether
     * they are individually restricted, which {@link RoleView#canSee} enforces.
     */
    public Section section() {
        return section;
    }
}
