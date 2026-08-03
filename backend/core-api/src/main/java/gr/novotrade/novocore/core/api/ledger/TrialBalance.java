package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Every account with activity, in chart order, with its debit and credit totals.
 *
 * <p><strong>This is the payoff of enforcing {@code CLAUDE.md} rule 6 per entry.</strong> If every entry
 * balances, the whole ledger balances, and {@link #isBalanced()} is that statement made about real posted
 * data rather than about the code that wrote it. A trial balance that does not balance in a system with a
 * deferred constraint trigger on every entry would mean the trigger had been dropped — which is exactly
 * the failure a test should be able to detect.
 *
 * <p>Accounts with no activity are omitted. Including 67 zero rows would bury the ones that matter, and
 * "which accounts have never been posted to" is a different question with its own answer in the chart.
 *
 * @param asOf inclusive, cumulative from the beginning. There is no period locking (brief §6).
 */
public record TrialBalance(
        @Mandatory LocalDate asOf,
        @Mandatory Currency currency,
        @Mandatory List<AccountBalance> balances) {

    public TrialBalance {
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(balances, "balances");
        balances = List.copyOf(balances);
    }

    public Money totalDebits() {
        return balances.stream().map(AccountBalance::debits)
                .reduce(Money.zero(currency), Money::plus);
    }

    public Money totalCredits() {
        return balances.stream().map(AccountBalance::credits)
                .reduce(Money.zero(currency), Money::plus);
    }

    /** True when total debits equal total credits, which they must. See the class comment. */
    public boolean isBalanced() {
        return totalDebits().equals(totalCredits());
    }

    /** The out-of-balance amount, which is zero unless something is structurally wrong. */
    public Money difference() {
        return totalDebits().minus(totalCredits());
    }

    public boolean isEmpty() {
        return balances.isEmpty();
    }
}
