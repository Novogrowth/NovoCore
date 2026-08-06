package gr.novotrade.novocore.core.api.tax;

/**
 * The VAT category a customer or supplier sits in — Q9's classification.
 *
 * <p>One enum shared by both parties rather than two parallel lists. The categories are the same
 * on either side of a transaction (an intra-EU B2B counterparty is an intra-EU B2B counterparty
 * whether we are selling to them or buying from them), and two copies of a tax classification is
 * how one of them ends up missing a case the other has.
 *
 * <p><strong>This is a classification, not a rate and not a posting rule.</strong> Three separate
 * things live alongside each other on a customer and are easy to conflate:
 *
 * <ul>
 *   <li>this status — the legal category the party is in;
 *   <li>a VAT class override — which <em>rate</em> applies, fed into
 *       {@link VatClassPrecedence} (invoice line beats customer beats product);
 *   <li>a {@link VatExemptionReasonView} — the named article of the Κώδικας ΦΠΑ under which a
 *       supply is outside VAT, which is what myDATA is told.
 * </ul>
 *
 * <p>Deliberately no method here says what VAT to charge or where it posts. That is the open VAT
 * posting design (HISTORY.md Q14), and an enum quietly asserting "intra-EU means zero" would be
 * that decision made by accident, in the wrong place.
 *
 * <p><strong>Nothing validates a VAT number against VIES.</strong> The AADE/VIES lookup is its own
 * adapter in roadmap phase 7. Until then a VAT number is whatever was typed, and
 * {@link #requiresVatNumber()} asserts only that one is <em>present</em> — never that it is real.
 */
public enum VatStatus {

    /**
     * A Greek party, taxed under the ordinary domestic regime. The default for the overwhelming
     * majority of both customers and suppliers.
     */
    DOMESTIC(false, false),

    /**
     * A VAT-registered business in another EU member state, where the supply is reverse-charged
     * to them.
     *
     * <p>A VAT number is required, and that is definitional rather than a policy choice: without
     * a counterparty VAT number there is no reverse charge to apply and the supply is not
     * intra-EU B2B, it is a distance sale to a consumer. Treating one as the other is a real VAT
     * error, so the status refuses to exist without the number that makes it true.
     */
    INTRA_EU_B2B(true, false),

    /**
     * A party outside the EU, where a supply is an export.
     *
     * <p>Split out from "other" deliberately. An export and an intra-EU B2B supply are both
     * VAT-free but under <em>different articles</em>, so they are reported to myDATA differently;
     * collapsing them into one bucket would lose exactly the distinction that has to be stated on
     * the document. A VAT number is not required — most non-EU customers have nothing that
     * resembles one.
     */
    NON_EU_EXPORT(false, false),

    /**
     * Outside VAT because a named article of the Κώδικας ΦΠΑ says so.
     *
     * <p>Requires a {@link VatExemptionReasonView}. "Exempt" with no article named is an
     * assertion rather than a status — it cannot be reported, cannot be defended in an audit, and
     * cannot be told apart from a mistake. The reason is what makes it a fact.
     */
    EXEMPT(false, true),

    /**
     * A case none of the above describes.
     *
     * <p>Present so that an unusual party can be recorded truthfully instead of being forced into
     * a category that is wrong, which is the failure mode of a closed list with no escape hatch.
     * It is deliberately <em>not</em> the default: nothing may fall into it by omission, because a
     * bucket that fills up by accident tells you nothing about what is in it.
     */
    OTHER(false, false);

    private final boolean requiresVatNumber;
    private final boolean requiresExemptionReason;

    VatStatus(boolean requiresVatNumber, boolean requiresExemptionReason) {
        this.requiresVatNumber = requiresVatNumber;
        this.requiresExemptionReason = requiresExemptionReason;
    }

    /** True when this status is not meaningful without a counterparty VAT number. */
    public boolean requiresVatNumber() {
        return requiresVatNumber;
    }

    /** True when this status is not meaningful without a named exemption reason. */
    public boolean requiresExemptionReason() {
        return requiresExemptionReason;
    }
}
