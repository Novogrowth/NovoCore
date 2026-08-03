package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What an account's journal lines add up to, as of a date.
 *
 * <p><strong>Nothing here is stored.</strong> Step 3 deliberately gave {@code account} no balance
 * column: a stored running balance is the classic thing that drifts from the entries it claims to
 * summarise. This record is computed from {@code journal_line} on every read, which is why it carries the
 * date it was computed for — a balance with no "as of" is a number nobody can reproduce.
 *
 * <p>The two totals are kept rather than only their difference, because a trial balance needs both
 * columns and because an account with equal, large debits and credits is a different situation from one
 * with no activity, while both net to zero.
 *
 * @param asOf inclusive. Cumulative from the beginning of the ledger — there is no period locking (brief
 *     §6) and therefore no opening-balance concept to net against.
 */
public record AccountBalance(
        long accountId,
        @Mandatory String accountName,
        @Mandatory AccountType accountType,
        @Mandatory Money debits,
        @Mandatory Money credits,
        @Mandatory LocalDate asOf) {

    public AccountBalance {
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(debits, "debits");
        Objects.requireNonNull(credits, "credits");
        Objects.requireNonNull(asOf, "asOf");
        if (debits.isNegative() || credits.isNegative()) {
            throw new IllegalArgumentException(
                    "Account " + accountId + " has debits " + debits + " and credits " + credits
                            + ". Both are sums of positive line amounts, so a negative one means the "
                            + "sides were mixed up rather than that the account is overdrawn.");
        }
    }

    /** Which side this account normally carries its balance on. Derived from the type, never stored. */
    public BalanceSide normalBalance() {
        return accountType.normalBalance();
    }

    /** Debits minus credits. Positive means a debit balance, whatever kind of account this is. */
    public Money net() {
        return debits.minus(credits);
    }

    /**
     * The balance on this account's own normal side — what a statement shows.
     *
     * <p>A negative value here is meaningful rather than an error: it says the account is sitting on the
     * side it does not normally sit on. A credit balance on a bank account is an overdraft; a debit
     * balance on Accounts payable is a supplier who has been overpaid. Both are real, and both are things
     * a report should show as unusual rather than as an absolute value.
     */
    public Money onNormalSide() {
        return normalBalance() == BalanceSide.DEBIT ? net() : credits.minus(debits);
    }

    /** True when the balance is on the side this kind of account normally carries it. */
    public boolean isOnItsNormalSide() {
        return !onNormalSide().isNegative();
    }

    public boolean isZero() {
        return net().isZero();
    }

    /** True when nothing has ever posted here. Distinct from a balance of zero. */
    public boolean hasNoActivity() {
        return debits.isZero() && credits.isZero();
    }
}
