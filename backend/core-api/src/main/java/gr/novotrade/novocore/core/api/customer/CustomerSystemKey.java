package gr.novotrade.novocore.core.api.customer;

/**
 * Stable machine identifiers for customer records NovoCore's own logic has to locate.
 *
 * <p>The same idea as {@code AccountSystemKey}, for the same reason: a customer's name is
 * operator-editable and their id is an implementation detail, so neither is a safe handle for code
 * that has to find one specific row. There is exactly one such row.
 *
 * <p><strong>Q10, answered.</strong> Step 5 deliberately seeded no generic retail customer, on the
 * grounds that a catch-all absorbs every unmatched sale and then cannot be untangled. The answer is
 * to seed it and make it <em>structural</em> — because the alternative is a person creating it by
 * hand on the first day of trading, which produces exactly the row step 5 feared, only with nothing
 * in the software able to tell which row it is.
 *
 * <p>A keyed customer is not a default. Nothing falls back to it: a sale names it because whoever
 * rang it up chose "retail, no details", which is a real answer to who bought it.
 */
public enum CustomerSystemKey {

    /**
     * The shared "Πελάτης Λιανικής" record — brief §5's generic retail customer for walk-in and
     * phone sales where no identifiable party was recorded.
     *
     * <p><strong>Its VAT treatment is fixed, not editable.</strong> {@code DOMESTIC}, no VAT number,
     * no exemption reason, enforced by CHECK constraints as well as by the service. It cannot
     * sensibly carry a VAT number because it is not one identifiable party, and it cannot be
     * {@code INTRA_EU_B2B} or {@code EXEMPT} because both are claims about a specific counterparty.
     *
     * <p><strong>Protected from deactivation and from merge.</strong> Deactivation is refused by a
     * CHECK, not merely by the service, so it holds against a {@code psql} session too. Merge is not
     * built (brief §5's alias-forward still needs the ledger decisions step 5 named), and the rule is
     * recorded here rather than discovered then: <em>this record is refused on both sides of a
     * merge</em>. Brief §5's "alias forward, never rewrite history" is about two records of one real
     * party; this is the absence of a party, so aliasing it into somebody would attribute every
     * anonymous till sale ever made to one named person, and aliasing somebody into it would erase a
     * real customer's history into an anonymous bucket.
     */
    RETAIL_WALK_IN;

    /**
     * Whether a customer carrying this key may be deactivated. Never — stated as a method rather than
     * as a bare {@code false} so the reason has somewhere to live and a caller reads the rule rather
     * than the constant.
     */
    public boolean isDeactivatable() {
        return false;
    }

    /**
     * Whether a customer carrying this key may take part in a merge, on either side.
     *
     * <p>Never. Merge does not exist yet; when it is built it must consult this rather than
     * rediscovering the argument. See the note on {@link #RETAIL_WALK_IN}.
     */
    public boolean isMergeable() {
        return false;
    }
}
