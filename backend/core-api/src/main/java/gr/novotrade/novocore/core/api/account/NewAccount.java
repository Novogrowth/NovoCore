package gr.novotrade.novocore.core.api.account;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.util.Objects;

/**
 * Request to add an account to the chart.
 *
 * <p>A command object rather than a long parameter list, so that adding a field later does not
 * silently change the meaning of an existing call — with six arguments, several of them the same
 * type, a transposed pair compiles fine and produces a wrong account.
 *
 * <p>{@link AccountSystemKey} is not settable here on purpose. The keyed accounts are all seeded,
 * and letting an operator attach a system key to an arbitrary account would let them redirect
 * where rounding differences or FIFO cost postings land.
 *
 * @param groupId the group this account belongs under; must already exist
 * @param subLedgerType required when {@code kind} is {@link AccountKind#CONTROL}, forbidden
 *     otherwise
 * @param expectedToClear true when a residual balance here is a discrepancy rather than a normal
 *     standing balance, which is what lets Clearing Checks find it in phase 8 without a
 *     hardcoded list of accounts to watch
 */
public record NewAccount(
        @Mandatory String name,
        @Mandatory AccountType type,
        @Mandatory AccountKind kind,
        SubLedgerType subLedgerType,
        long groupId,
        @Mandatory Boolean expectedToClear) {

    public NewAccount {
        Required.field(expectedToClear, "expectedToClear");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
    }

    /** A standard account with no sub-ledger and no clearing expectation. */
    public static NewAccount standard(String name, AccountType type, long groupId) {
        return new NewAccount(name, type, AccountKind.STANDARD, null, groupId, false);
    }

    /** A control account behind the given sub-ledger. */
    public static NewAccount control(
            String name, AccountType type, SubLedgerType subLedgerType, long groupId) {
        return new NewAccount(
                name, type, AccountKind.CONTROL, subLedgerType, groupId, false);
    }
}
