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

    /** Products. Reserved — the entity arrives in step 5. */
    PRODUCTS(false),

    /** Customers. Reserved — the entity arrives in step 5. */
    CUSTOMERS(false),

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
