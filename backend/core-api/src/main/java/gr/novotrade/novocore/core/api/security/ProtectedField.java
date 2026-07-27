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
 * <p><strong>The current entries are the concrete case, not placeholders.</strong> Remote/Order
 * Staff has view-only access to Products with the cost and supplier fields hidden, and these are
 * those fields. The three exist because a home-based worker fulfilling orders needs to see what a
 * product is and what it sells for, but has no need to know what it cost us or who supplies it.
 *
 * <p>Products themselves arrive in step 5. When {@code ProductView} is built it must apply these
 * — see {@link RoleView#canSee}. The mechanism is finished; the entity it will guard is not.
 */
public enum ProtectedField {

    /**
     * What the product last cost us to buy. Cost data, hidden from Remote/Order Staff.
     *
     * <p>Distinct from the product's regular selling price, which is <em>not</em> protected: an
     * order picker plainly needs it.
     */
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
