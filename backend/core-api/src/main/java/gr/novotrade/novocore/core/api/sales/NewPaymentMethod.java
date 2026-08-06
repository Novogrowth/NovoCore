package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

/**
 * Request to create a payment method — <strong>the form V35 argued could not exist.</strong>
 *
 * <p>R2b's §4.7 gave the concrete reason there was no create path: adding a method <em>"needs an
 * {@code AccountSystemKey} and two behaviour flags, and no form can supply those."</em> ⭐ That
 * argument was never refuted — <strong>it turned out to be this record's specification.</strong> The
 * account is supplied (as a real account rather than a system key, because a user-created row cannot
 * inherit a stable handle it is not a member of), and both flags are derived rather than collected.
 *
 * <h2>⚠️ Both references are mandatory, and neither is conditional</h2>
 *
 * <p><strong>A payment method cannot be created without both an AADE article and a reconciliation
 * account.</strong> Confirmed against the weaker "at least one of the two" reading.
 *
 * <p>⚠️ <strong>{@code @ConditionallyMandatory} on {@code accountId} was considered and rejected —
 * do not add it.</strong> An earlier draft made the account required <em>iff the method settles
 * immediately</em>; since settling immediately <em>meant</em> having an account, that condition tested
 * the field against itself and could never fire. There is now no condition at all, which
 * {@link Mandatory} says plainly.
 *
 * @param aadePaymentMethodId annex 8.12's article. Supplies the myDATA code, which is therefore
 *     <strong>not</strong> stored on the row — resolved through this reference instead, so there is
 *     nothing that can disagree.
 * @param accountId the account this method reconciles to: a bank account, the cash box, a partner
 *     clearing account, or <strong>Accounts receivable</strong> for a method that leaves the invoice
 *     open. ⚠️ Validated against {@code ChartOfAccountsService.activePaymentMethodTargets()}, which is
 *     a <em>wider</em> set than a Receipt's settlement targets and deliberately a separate question —
 *     see {@code ChartOfAccountsService}.
 * @param sortCode ordering only; unique. A text sort puts {@code 1000} before {@code 900}, hence an
 *     {@code int}.
 */
public record NewPaymentMethod(
        @Mandatory String abbreviation,
        @Mandatory String description,
        @Mandatory Long aadePaymentMethodId,
        @Mandatory Long accountId,
        int sortCode) {

    public NewPaymentMethod {
        abbreviation = Required.text(abbreviation, "abbreviation");
        description = Required.text(description, "description");
        Required.field(aadePaymentMethodId, "aadePaymentMethodId");
        Required.field(accountId, "accountId");
    }
}
