package gr.novotrade.novocore.core.api.security;

/**
 * A part of the application a role can be granted access to — the outer layer of brief §7's
 * two-layer permission model.
 *
 * <p><strong>Sections are code, roles are data.</strong> Which sections exist is determined by
 * what the software has been built to do, so it is an enum. Which sections a given role may see
 * is configuration, so it lives in the database (brief §7: "supports multiple custom roles from
 * the start"). Getting that split the other way round would mean either a migration to add a
 * role, or a database row that has to name a screen that may not exist.
 *
 * <p><strong>Access is default-deny.</strong> A role grants nothing it is not explicitly given,
 * so "everything else is invisible to this role" needs no enumeration and stays true as sections
 * are added. A new section is invisible to every non-full-access role until someone grants it —
 * which is the safe direction to fail.
 */
public enum Section {

    /** The chart of accounts. Built in step 3. */
    CHART_OF_ACCOUNTS(true),

    /** VAT classes, exemption reasons and charge types. Built in step 3b. */
    TAX_AND_CHARGES(true),

    /** Operator-changeable configuration, including SMTP credentials. */
    SETTINGS(true),

    /** The audit log. */
    AUDIT_LOG(true),

    /** User and role administration. */
    USERS_AND_ROLES(true),

    /** Products. Built in step 5, with three field-level restrictions — see {@link ProtectedField}. */
    PRODUCTS(true),

    /** Customers. Built in step 5. */
    CUSTOMERS(true),

    /**
     * Suppliers. Built in step 5.
     *
     * <p>Its own section rather than part of {@link #PRODUCTS}, even though Remote/Order Staff's
     * restrictions hide the supplier <em>of a product</em>. Those are different questions: that
     * restriction narrows a product response, while this governs the supplier list itself, and
     * granting one should not grant the other.
     */
    SUPPLIERS(true),

    /** The fixed asset register. Built in step 5. */
    FIXED_ASSETS(true),

    /**
     * Inventory lots, serialized units and stock movements. Built in step 6.
     *
     * <p><strong>Separate from {@link #PRODUCTS} because of cost.</strong> Stock <em>levels</em> are a
     * product-level read — an order picker granted VIEW on Products needs to know whether there are
     * three left. A <em>lot</em> carries its unit cost, which is exactly what
     * {@link ProtectedField#PRODUCT_LAST_PURCHASE_PRICE} exists to keep away from that role, so
     * granting the ability to see stock must not grant the ability to see what it cost.
     */
    INVENTORY(true),

    /**
     * Journal entries, account balances and the trial balance. Built in step 7.
     *
     * <p>Separate from {@link #CHART_OF_ACCOUNTS} for the reason {@link #INVENTORY} is separate from
     * {@link #PRODUCTS}: seeing the <em>list</em> of accounts is close to harmless, while seeing what has
     * posted to them is every financial figure in the business. Someone maintaining the chart is not
     * necessarily someone who should read the ledger.
     *
     * <p>Granting this is close to granting everything, since an account's lines carry customer,
     * supplier and lot references and therefore the cost of every purchase. No role is granted it by
     * default — access is default-deny — and the two full-access system roles reach it without a grant.
     */
    JOURNAL(true),

    /**
     * Purchase invoices, goods receipts and the GR/IR position between them. Built in step 8.
     *
     * <p>Separate from {@link #SUPPLIERS} for the reason {@link #INVENTORY} is separate from
     * {@link #PRODUCTS}: the supplier list is a directory, while a purchase invoice states what we pay
     * for everything we sell. Whoever adds a supplier's phone number is not necessarily whoever may
     * read the purchase price of every product in the catalogue.
     *
     * <p>Receiving goods and recording an invoice are the same section rather than two, because they
     * are two halves of one document flow: someone verifying a delivery has to see the invoice line it
     * is being received against, or the match cannot be made.
     */
    PURCHASING(true),

    /** Reserved for the Sales Order Fulfillment module (roadmap phase 4). */
    SALES_ORDER_FULFILLMENT(false),

    /** Reserved for the Back-in-Stock Reminders module (roadmap phase 9). */
    BACK_IN_STOCK_REMINDERS(false);

    private final boolean available;

    Section(boolean available) {
        this.available = available;
    }

    /**
     * True when something is actually built behind this section.
     *
     * <p>Reserved sections can be granted and their grants stored, so the permission model is
     * complete before the features are. They are flagged rather than omitted so that a UI can
     * distinguish "you may not see this" from "this does not exist yet" — two states that look
     * identical to a user and have entirely different fixes.
     */
    public boolean isAvailable() {
        return available;
    }
}
