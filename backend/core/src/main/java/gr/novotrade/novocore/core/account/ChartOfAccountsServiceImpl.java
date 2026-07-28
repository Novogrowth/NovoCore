package gr.novotrade.novocore.core.account;

import gr.novotrade.novocore.core.api.account.AccountGroupNotFoundException;
import gr.novotrade.novocore.core.api.account.AccountGroupView;
import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountNotFoundException;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.account.InvalidAccountException;
import gr.novotrade.novocore.core.api.account.NewAccount;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ChartOfAccountsServiceImpl implements ChartOfAccountsService {

    private static final String ACCOUNT_ENTITY = "Account";
    private static final String GROUP_ENTITY = "AccountGroup";

    private final AccountRepository accounts;
    private final AccountGroupRepository groups;
    private final AuditLogService auditLog;

    /**
     * Resolved at call time rather than injected, and that is not a style choice.
     * {@code JournalServiceImpl} depends on <em>this</em> service to validate the accounts a line
     * posts to, so a constructor dependency the other way would be a bean cycle. The only thing this
     * service asks the ledger is what an account is carrying at the moment somebody retires it, which
     * is a question with an answer long after startup — the same reasoning that put the ledger's
     * sub-ledger existence check into a database trigger rather than into Java.
     */
    private final org.springframework.beans.factory.ObjectProvider<
            gr.novotrade.novocore.core.api.ledger.JournalService> journal;

    ChartOfAccountsServiceImpl(AccountRepository accounts, AccountGroupRepository groups,
            AuditLogService auditLog,
            org.springframework.beans.factory.ObjectProvider<
                    gr.novotrade.novocore.core.api.ledger.JournalService> journal) {
        this.accounts = accounts;
        this.groups = groups;
        this.auditLog = auditLog;
        this.journal = journal;
    }

    // ---------------------------------------------------------------------------------------
    // Reading the chart
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<AccountGroupView> chart() {
        // Two queries regardless of chart size: the groups, and every account with its group
        // join-fetched. Assembling in memory rather than navigating a mapped collection is what
        // keeps this from becoming a query per group.
        Map<Long, List<AccountView>> byGroup = new LinkedHashMap<>();
        for (Account account : accounts.findAllInChartOrder()) {
            byGroup.computeIfAbsent(account.getGroup().getId(), key -> new ArrayList<>())
                    .add(toView(account));
        }

        return groups.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(group -> new AccountGroupView(
                        group.getId(),
                        group.getName(),
                        group.getDisplayOrder(),
                        byGroup.getOrDefault(group.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountGroupView> groups() {
        return groups.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(group -> new AccountGroupView(
                        group.getId(), group.getName(), group.getDisplayOrder(), List.of()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountView> findAccount(long id) {
        return accounts.findById(id).map(ChartOfAccountsServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView requireAccount(long id) {
        return findAccount(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView requireAccount(AccountSystemKey systemKey) {
        Objects.requireNonNull(systemKey, "systemKey");
        return accounts.findBySystemKey(systemKey)
                .map(ChartOfAccountsServiceImpl::toView)
                .orElseThrow(() -> new AccountNotFoundException(systemKey));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountView> allAccounts() {
        return toViews(accounts.findAllInChartOrder());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountView> activeAccounts() {
        return toViews(accounts.findActiveInChartOrder());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountView> activeAccountsOfKind(AccountKind kind) {
        Objects.requireNonNull(kind, "kind");
        return toViews(accounts.findActiveByKindInChartOrder(kind));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountView> activeControlAccountsFor(SubLedgerType subLedgerType) {
        Objects.requireNonNull(subLedgerType, "subLedgerType");
        return toViews(accounts.findActiveControlAccountsFor(subLedgerType));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountView> activeSettlementTargets() {
        return activeAccounts().stream().filter(AccountView::isSettlementTarget).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountView> activeAccountsExpectedToClear() {
        return toViews(accounts.findActiveExpectedToClearInChartOrder());
    }

    // ---------------------------------------------------------------------------------------
    // Changing the chart
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public AccountView createAccount(NewAccount request) {
        Objects.requireNonNull(request, "request");
        String name = requireText(request.name(), "Account name");

        AccountGroup group = groups.findById(request.groupId())
                .orElseThrow(() -> new AccountGroupNotFoundException(request.groupId()));

        // Checked here as well as by the database constraint. The constraint is the guarantee;
        // this is what turns it into a message naming the account instead of a driver-level
        // integrity violation.
        if (request.kind() == AccountKind.CONTROL && request.subLedgerType() == null) {
            throw new InvalidAccountException(
                    "Control account '" + name + "' must declare a sub-ledger type: a control "
                            + "account with no sub-ledger has nothing to reconcile against.");
        }
        if (request.kind() != AccountKind.CONTROL && request.subLedgerType() != null) {
            throw new InvalidAccountException(
                    "Account '" + name + "' is " + request.kind() + " but declares sub-ledger "
                            + request.subLedgerType() + ". Only CONTROL accounts have one.");
        }
        if (accounts.existsByGroupIdAndNameIgnoreCase(request.groupId(), name)) {
            throw new InvalidAccountException(
                    "Group '" + group.getName() + "' already has an account named '" + name + "'.");
        }

        Account account = new Account(
                name,
                request.type(),
                request.kind(),
                request.subLedgerType(),
                group,
                nextDisplayOrderIn(request.groupId()),
                request.expectedToClear());
        Account saved = accounts.save(account);

        auditLog.record("account.created", ACCOUNT_ENTITY, String.valueOf(saved.getId()), Map.of(
                "name", name,
                "type", request.type().name(),
                "kind", request.kind().name(),
                "group", group.getName()));

        return toView(saved);
    }

    @Override
    @Transactional
    public AccountGroupView createGroup(String name) {
        String groupName = requireText(name, "Group name");
        if (groups.existsByNameIgnoreCase(groupName)) {
            throw new InvalidAccountException("A group named '" + groupName + "' already exists.");
        }

        int displayOrder = groups.findMaxDisplayOrder().map(max -> max + 1).orElse(0);
        AccountGroup saved = groups.save(new AccountGroup(groupName, displayOrder));

        auditLog.record("account-group.created", GROUP_ENTITY, String.valueOf(saved.getId()),
                Map.of("name", groupName));

        return new AccountGroupView(
                saved.getId(), saved.getName(), saved.getDisplayOrder(), List.of());
    }

    @Override
    @Transactional
    public AccountView renameAccount(long id, String newName) {
        String name = requireText(newName, "Account name");
        Account account = accounts.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        long groupId = account.getGroup().getId();
        if (!account.getName().equalsIgnoreCase(name)
                && accounts.existsByGroupIdAndNameIgnoreCase(groupId, name)) {
            throw new InvalidAccountException(
                    "Group '" + account.getGroup().getName() + "' already has an account named '"
                            + name + "'.");
        }

        String previousName = account.getName();
        account.rename(name);

        // Renaming a keyed account is allowed on purpose: posting rules depend on the key, not
        // the name. Recording the old name keeps the ledger's history readable afterwards.
        auditLog.record("account.renamed", ACCOUNT_ENTITY, String.valueOf(id),
                Map.of("from", previousName, "to", name));

        return toView(account);
    }

    @Override
    @Transactional
    public AccountGroupView renameGroup(long id, String newName) {
        String name = requireText(newName, "Group name");
        AccountGroup group = groups.findById(id)
                .orElseThrow(() -> new AccountGroupNotFoundException(id));

        if (!group.getName().equalsIgnoreCase(name) && groups.existsByNameIgnoreCase(name)) {
            throw new InvalidAccountException("A group named '" + name + "' already exists.");
        }

        String previousName = group.getName();
        group.rename(name);

        auditLog.record("account-group.renamed", GROUP_ENTITY, String.valueOf(id),
                Map.of("from", previousName, "to", name));

        return new AccountGroupView(
                group.getId(), group.getName(), group.getDisplayOrder(), List.of());
    }

    @Override
    @Transactional
    public Optional<gr.novotrade.novocore.core.api.shared.Money> deactivate(long id) {
        Account account = accounts.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        // A keyed account is one a posting rule resolves at runtime with no fallback. Rounding
        // differences with nowhere to go would stop a sales invoice from posting at all, so this
        // is refused rather than warned about.
        if (account.isSystemAccount()) {
            throw new InvalidAccountException(
                    "Account '" + account.getName() + "' carries the system key "
                            + account.getSystemKey() + " and cannot be deactivated: a posting "
                            + "rule resolves it at runtime and has no fallback.");
        }
        if (!account.isActive()) {
            return Optional.empty();
        }

        // Warned about, not refused — the step 3 decision, now that there is a ledger to ask. The
        // balance does not vanish when an account is retired; it stops being maintained, which is a
        // different and quieter problem.
        Optional<gr.novotrade.novocore.core.api.shared.Money> outstanding = balanceLeftBehind(id);

        account.setActive(false);
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("name", account.getName());
        outstanding.ifPresent(balance -> detail.put("balanceLeftBehind", balance.toString()));
        auditLog.record("account.deactivated", ACCOUNT_ENTITY, String.valueOf(id), detail);

        return outstanding;
    }

    /**
     * What this account is carrying right now, or empty when it is carrying nothing.
     *
     * <p>Empty for an account with no activity as well as one that nets to zero: neither is a warning
     * anybody needs, and {@code AccountBalance.hasNoActivity()} is what tells them apart if a caller
     * ever cares.
     */
    private Optional<gr.novotrade.novocore.core.api.shared.Money> balanceLeftBehind(long id) {
        gr.novotrade.novocore.core.api.ledger.JournalService ledger = journal.getIfAvailable();
        if (ledger == null) {
            return Optional.empty();
        }
        gr.novotrade.novocore.core.api.ledger.AccountBalance balance =
                ledger.balanceOf(id, java.time.LocalDate.now());
        // onNormalSide(), so the warning reads the way the account does: an expense left carrying 40
        // says 40, not −40, and an account sitting on the wrong side reads negative, which is itself
        // worth seeing on the way out.
        return balance.isZero() ? Optional.empty() : Optional.of(balance.onNormalSide());
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        Account account = accounts.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (account.isActive()) {
            return;
        }

        account.setActive(true);
        auditLog.record("account.reactivated", ACCOUNT_ENTITY, String.valueOf(id),
                Map.of("name", account.getName()));
    }

    @Override
    @Transactional
    public void reorderAccountsWithinGroup(long groupId, List<Long> accountIdsInOrder) {
        Objects.requireNonNull(accountIdsInOrder, "accountIdsInOrder");
        if (!groups.existsById(groupId)) {
            throw new AccountGroupNotFoundException(groupId);
        }

        List<Account> existing = accounts.findByGroupIdOrderByDisplayOrderAscIdAsc(groupId);
        requireExactlyTheSameIds(
                existing.stream().map(Account::getId).toList(),
                accountIdsInOrder,
                "accounts in group " + groupId);

        Map<Long, Account> byId = new LinkedHashMap<>();
        existing.forEach(account -> byId.put(account.getId(), account));

        // Rewritten contiguously from zero. The seed uses sparse hand-assigned values, but once
        // a human has dragged a row there is no meaning left in the gaps.
        for (int position = 0; position < accountIdsInOrder.size(); position++) {
            byId.get(accountIdsInOrder.get(position)).moveTo(position);
        }

        auditLog.record("account-group.accounts-reordered", GROUP_ENTITY, String.valueOf(groupId),
                Map.of("order", accountIdsInOrder.toString()));
    }

    @Override
    @Transactional
    public void reorderGroups(List<Long> groupIdsInOrder) {
        Objects.requireNonNull(groupIdsInOrder, "groupIdsInOrder");

        List<AccountGroup> existing = groups.findAllByOrderByDisplayOrderAscIdAsc();
        requireExactlyTheSameIds(
                existing.stream().map(AccountGroup::getId).toList(),
                groupIdsInOrder,
                "account groups");

        Map<Long, AccountGroup> byId = new LinkedHashMap<>();
        existing.forEach(group -> byId.put(group.getId(), group));

        for (int position = 0; position < groupIdsInOrder.size(); position++) {
            byId.get(groupIdsInOrder.get(position)).moveTo(position);
        }

        auditLog.recordSystemAction("account-group.reordered",
                Map.of("order", groupIdsInOrder.toString()));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private int nextDisplayOrderIn(long groupId) {
        return accounts.findMaxDisplayOrderInGroup(groupId).map(max -> max + 1).orElse(0);
    }

    /**
     * A reorder must name every member exactly once. A partial list would leave the remainder in
     * whatever order they happened to have, which is not an order anybody chose — so it is
     * refused rather than being completed with a guess ({@code CLAUDE.md} rule 7).
     */
    private static void requireExactlyTheSameIds(
            List<Long> existingIds, List<Long> requestedIds, String what) {
        Set<Long> existing = new HashSet<>(existingIds);
        Set<Long> requested = new HashSet<>(requestedIds);

        if (requestedIds.size() != requested.size()) {
            throw new InvalidAccountException(
                    "Reorder of " + what + " lists the same id more than once.");
        }
        if (!existing.equals(requested)) {
            throw new InvalidAccountException(
                    "Reorder of " + what + " must list every one of them exactly once. Expected "
                            + existing.size() + " ids " + existingIds + " but got " + requestedIds
                            + ".");
        }
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidAccountException(what + " must not be blank.");
        }
        return value.trim();
    }

    private static List<AccountView> toViews(List<Account> accounts) {
        return accounts.stream().map(ChartOfAccountsServiceImpl::toView).toList();
    }

    private static AccountView toView(Account account) {
        AccountGroup group = account.getGroup();
        return new AccountView(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getKind(),
                account.getSubLedgerType(),
                account.getSystemKey(),
                group.getId(),
                group.getName(),
                account.getDisplayOrder(),
                account.isActive(),
                account.isExpectedToClear(),
                account.getElpCode());
    }
}
