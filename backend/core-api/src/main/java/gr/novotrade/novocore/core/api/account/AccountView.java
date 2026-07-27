package gr.novotrade.novocore.core.api.account;

import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.util.Objects;
import java.util.Optional;

/**
 * One account, as everything outside the core sees it.
 *
 * <p>{@link #normalBalance()} and {@link #statementSection()} are computed from {@link #type()}
 * rather than being fields, so this DTO cannot carry a combination the domain forbids.
 *
 * @param code the account code. Blank for now by decision — ΕΛΠ mapping and codes come from the
 *     accountant later. Never use it as an identifier; see {@link AccountSystemKey}.
 * @param elpCode ΕΛΠ (Ν. 4308/2014) mapping, null until the accountant supplies it
 * @param subLedgerType present only when {@link #kind()} is {@link AccountKind#CONTROL}
 * @param systemKey present only for the few accounts NovoCore's own posting rules must locate
 * @param expectedToClear true when a residual balance on this account is a real discrepancy
 *     rather than a normal standing balance
 */
public record AccountView(
        long id,
        String code,
        String name,
        AccountType type,
        AccountKind kind,
        SubLedgerType subLedgerType,
        AccountSystemKey systemKey,
        long groupId,
        String groupName,
        int displayOrder,
        boolean active,
        boolean expectedToClear,
        String elpCode) {

    public AccountView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(groupName, "groupName");
        // The invariant that makes a Control account's balance reconcilable at all: a control
        // account without a declared sub-ledger has nothing to reconcile against, and a
        // sub-ledger declared on a non-control account would never be enforced on its lines.
        if (kind == AccountKind.CONTROL && subLedgerType == null) {
            throw new IllegalArgumentException(
                    "Control account '" + name + "' must declare a sub-ledger type.");
        }
        if (kind != AccountKind.CONTROL && subLedgerType != null) {
            throw new IllegalArgumentException(
                    "Account '" + name + "' is " + kind + " but declares sub-ledger "
                            + subLedgerType + ". Only CONTROL accounts have a sub-ledger.");
        }
    }

    /** Derived from {@link #type()}, never stored. */
    public BalanceSide normalBalance() {
        return type.normalBalance();
    }

    /** Derived from {@link #type()}, never stored. */
    public StatementSection statementSection() {
        return type.statementSection();
    }

    /** True when this account reduces the class it sits under rather than adding to it. */
    public boolean isContra() {
        return type.isContra();
    }

    public Optional<SubLedgerType> subLedger() {
        return Optional.ofNullable(subLedgerType);
    }

    public Optional<AccountSystemKey> systemKeyIfAny() {
        return Optional.ofNullable(systemKey);
    }

    /** True when journal lines on this account must carry a sub-ledger reference. */
    public boolean requiresSubLedgerReference() {
        return kind.requiresSubLedgerReference();
    }

    /** True when a Receipt, Payment or Bank Transfer may name this account as its money side. */
    public boolean isSettlementTarget() {
        return kind.isSettlementTarget();
    }
}
