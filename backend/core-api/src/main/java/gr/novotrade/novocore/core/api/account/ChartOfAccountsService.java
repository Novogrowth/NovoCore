package gr.novotrade.novocore.core.api.account;

import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.util.List;
import java.util.Optional;

/**
 * The chart of accounts: reading it, and the limited changes an operator may make to it.
 *
 * <p>Every account NovoCore posts to is reached through here. The journal engine in step 7 will
 * ask this service which side an account's normal balance is on, whether a line needs a
 * sub-ledger reference, and where a rounding difference goes — none of which any caller decides
 * for itself.
 *
 * <p><strong>There is no delete.</strong> An account that has been posted to cannot be removed
 * without either destroying history or leaving journal lines pointing at nothing, and with no
 * period locking (brief §6) there is no point at which an account is safely finished with.
 * {@link #deactivate} takes it out of circulation instead, leaving its history intact and its
 * balance still reportable.
 */
public interface ChartOfAccountsService {

    // ---------------------------------------------------------------------------------------
    // Reading the chart
    // ---------------------------------------------------------------------------------------

    /**
     * The whole chart: groups in display order, each with its accounts in display order,
     * including inactive ones.
     *
     * <p>This is what the chart-of-accounts screen renders and what a report walks. Inactive
     * accounts are included because they may still hold a balance from before they were
     * deactivated; callers that want only currently-usable accounts should filter with
     * {@link AccountGroupView#activeAccounts()}.
     */
    List<AccountGroupView> chart();

    /** Groups only, in display order, without their accounts. */
    List<AccountGroupView> groups();

    Optional<AccountView> findAccount(long id);

    /** @throws AccountNotFoundException if absent, naming the id. */
    AccountView requireAccount(long id);

    /**
     * The account carrying this system key.
     *
     * <p>How posting rules find the account they need — the rounding account, the GR/IR clearing
     * account, COGS. Throws rather than returning empty: a posting rule with no destination is a
     * broken chart, not a condition to handle.
     *
     * @throws AccountNotFoundException if no account carries the key
     */
    AccountView requireAccount(AccountSystemKey systemKey);

    /** Every account, active and inactive, in group-then-account display order. */
    List<AccountView> allAccounts();

    /** Active accounts only, in group-then-account display order. */
    List<AccountView> activeAccounts();

    /** Active accounts of a given kind — e.g. every bank/cash account for a payment form. */
    List<AccountView> activeAccountsOfKind(AccountKind kind);

    /**
     * Active control accounts behind a given sub-ledger.
     *
     * <p>Returns a list, not a single account, because a sub-ledger can legitimately sit behind
     * more than one: {@link SubLedgerType#ASSET} has both Fixed assets at cost and Fixed assets
     * accumulated depreciation, and {@link SubLedgerType#SUPPLIER} has both Accounts payable and
     * the GR/IR clearing account. Use {@link #requireAccount(AccountSystemKey)} when a specific
     * one is meant.
     */
    List<AccountView> activeControlAccountsFor(SubLedgerType subLedgerType);

    /**
     * Active accounts a Receipt, Payment or Bank Transfer may name as its money side — the
     * bank/cash and partner clearing accounts.
     */
    List<AccountView> activeSettlementTargets();

    /**
     * Active accounts whose residual balance is a real discrepancy rather than a normal standing
     * balance.
     *
     * <p>This is the flag rather than a hardcoded list, so Clearing Checks in phase 8 finds the
     * right accounts without being edited every time one is added.
     */
    List<AccountView> activeAccountsExpectedToClear();

    // ---------------------------------------------------------------------------------------
    // Changing the chart
    // ---------------------------------------------------------------------------------------

    /**
     * Adds an account at the end of its group's display order.
     *
     * @throws AccountGroupNotFoundException if the group does not exist
     * @throws InvalidAccountException if the sub-ledger declaration does not match the kind, or
     *     the name duplicates another account in the same group
     */
    AccountView createAccount(NewAccount request);

    /**
     * Adds a group at the end of the chart's display order.
     *
     * @throws InvalidAccountException if the name duplicates an existing group
     */
    AccountGroupView createGroup(String name);

    /**
     * Renames an account. Permitted on keyed accounts too — the key, not the name, is what
     * posting rules depend on, which is the reason the key exists.
     *
     * @throws InvalidAccountException if the name duplicates another account in the same group
     */
    AccountView renameAccount(long id, String newName);

    /** @throws InvalidAccountException if the name duplicates an existing group */
    AccountGroupView renameGroup(long id, String newName);

    /**
     * Takes an account out of circulation without deleting it. It stops being offered for new
     * postings; its existing history and balance remain.
     *
     * <p>Does not currently check whether the balance is zero. It cannot — there is no ledger
     * until step 7. Once there is one, deactivating a non-zero account should warn rather than
     * refuse, because taking a still-populated account out of use is a legitimate thing to do
     * before a rearrangement.
     *
     * @throws InvalidAccountException if the account carries an {@link AccountSystemKey}, since
     *     the posting rule depending on it has no fallback
     */
    void deactivate(long id);

    void reactivate(long id);

    /**
     * Sets the display order of the accounts within one group, by listing their ids in the
     * intended order. Supports drag-and-drop, which is why ordering is stored rather than
     * derived from the name.
     *
     * @throws InvalidAccountException if the ids are not exactly the accounts in that group —
     *     a partial list would leave the remainder in an order nobody chose
     */
    void reorderAccountsWithinGroup(long groupId, List<Long> accountIdsInOrder);

    /**
     * Sets the display order of the groups themselves.
     *
     * @throws InvalidAccountException if the ids are not exactly the existing groups
     */
    void reorderGroups(List<Long> groupIdsInOrder);
}
