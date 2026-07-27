package gr.novotrade.novocore.core.api.charge;

import java.util.Objects;

/**
 * A chargeable fee that can appear as a line on a sales invoice — a cash-on-delivery charge, a
 * delivery cost, and whatever is added later.
 *
 * <p><strong>Income, not expense.</strong> These are amounts charged to the customer as revenue.
 * The courier's fee that NovoCore itself incurs is a separate, unrelated expense posting; the two
 * being similar in name is exactly why the income side is modelled explicitly.
 *
 * <p>A lookup table rather than an enum for the same reason VAT classes are: more fee types are
 * expected, and adding one should not be a code change.
 *
 * @param defaultVatClassId the VAT class a line defaults to. Fees are taxable. Overridable per
 *     line by the ordinary precedence rule.
 * @param incomeAccountId the income account the fee posts to. Constrained to an
 *     {@code INCOME}-type account, so a fee cannot be wired to net against an expense.
 */
public record ChargeTypeView(
        long id,
        String name,
        long defaultVatClassId,
        long incomeAccountId,
        boolean active) {

    public ChargeTypeView {
        Objects.requireNonNull(name, "name");
    }
}
