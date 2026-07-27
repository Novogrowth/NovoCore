package gr.novotrade.novocore.core.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountGroupNotFoundException;
import gr.novotrade.novocore.core.api.account.AccountGroupView;
import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountNotFoundException;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.account.InvalidAccountException;
import gr.novotrade.novocore.core.api.account.NewAccount;
import gr.novotrade.novocore.core.api.account.StatementSection;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The chart of accounts, against a real PostgreSQL with the real seed migration applied.
 *
 * <p><strong>On counting.</strong> These tests share one database and are deliberately not
 * transactional (see {@link AbstractCoreIntegrationTest}), so tests that add accounts would
 * corrupt any assertion about a global total. Every count here is therefore scoped to the
 * {@link #SEEDED_GROUPS} the migration creates, and every test that mutates does so inside a
 * group it makes for itself. Absolute totals over the whole table are avoided on purpose rather
 * than being made to work with test ordering, which would be a guarantee about JUnit rather than
 * about the chart.
 */
class ChartOfAccountsIT extends AbstractCoreIntegrationTest {

    /** The thirteen groups V4 seeds, in the order it seeds them. */
    private static final List<String> SEEDED_GROUPS = List.of(
            "Cash & Cash Equivalents",
            "Current Assets",
            "Non-Current Assets",
            "Current Liabilities",
            "Non-Current Liabilities",
            "Equity",
            "Income",
            "COGS",
            "Selling Expenses",
            "General Expenses",
            "Administrative Expenses",
            "Depreciation & Amortization",
            "Finance Costs");

    @Autowired
    private ChartOfAccountsService chart;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------------------
    // The seed
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the seeded groups are present in the intended order, Finance Costs last")
    void seededGroupsAreInOrder() {
        List<String> seeded = chart.groups().stream()
                .map(AccountGroupView::name)
                .filter(SEEDED_GROUPS::contains)
                .toList();

        // Finance Costs sitting below Depreciation & Amortization is the whole reason it is its
        // own group: interest above EBIT would make both EBITDA and EBIT wrong by definition.
        assertThat(seeded).containsExactlyElementsOf(SEEDED_GROUPS);
    }

    @Test
    @DisplayName("each seeded group holds the number of accounts the seed specifies")
    void seededGroupSizes() {
        assertThat(accountCountsBySeededGroup())
                .containsEntry("Cash & Cash Equivalents", 6)
                .containsEntry("Current Assets", 7)
                .containsEntry("Non-Current Assets", 3)
                .containsEntry("Current Liabilities", 8)
                .containsEntry("Non-Current Liabilities", 1)
                .containsEntry("Equity", 2)
                .containsEntry("Income", 10)
                .containsEntry("COGS", 3)
                .containsEntry("Selling Expenses", 7)
                .containsEntry("General Expenses", 10)
                .containsEntry("Administrative Expenses", 5)
                .containsEntry("Depreciation & Amortization", 2)
                .containsEntry("Finance Costs", 1);
    }

    @Test
    @DisplayName("every system key resolves to exactly one seeded account")
    void everySystemKeyResolves() {
        // The test that matters most in this class. AccountSystemKey, the CHECK constraint in V4
        // and the seed rows are three separate lists that have to agree; a key added to the enum
        // but not seeded would otherwise surface as a posting failure in step 7 or later, at
        // which point the cause is a long way from the symptom.
        for (AccountSystemKey key : AccountSystemKey.values()) {
            AccountView account = chart.requireAccount(key);
            assertThat(account.systemKey())
                    .as("account resolved for %s", key)
                    .isEqualTo(key);
        }

        assertThat(chart.allAccounts())
                .filteredOn(account -> account.systemKey() != null)
                .as("a system key is unique, so no two accounts may claim the same one")
                .hasSize(AccountSystemKey.values().length);
    }

    @Test
    @DisplayName("account codes and ΕΛΠ mappings are seeded blank, as decided")
    void codesAndElpMappingsAreBlank() {
        // Deliberate: both come from the accountant later. Asserted so that a well-meaning
        // future seed cannot start inventing codes that something then keys off.
        assertThat(seededAccounts())
                .allSatisfy(account -> {
                    assertThat(account.code()).isNull();
                    assertThat(account.elpCode()).isNull();
                });
    }

    // ---------------------------------------------------------------------------------------
    // Normal balance derivation — the reason the contra types exist
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("accumulated depreciation is asset-classified but credit-normal")
    void accumulatedDepreciationIsCreditNormal() {
        AccountView accumulated =
                chart.requireAccount(AccountSystemKey.FIXED_ASSETS_ACCUMULATED_DEPRECIATION);

        assertThat(accumulated.type()).isEqualTo(AccountType.CONTRA_ASSET);
        assertThat(accumulated.normalBalance())
                .as("typed as a plain ASSET this would derive debit-normal, flipping every "
                        + "depreciation posting and reporting fixed assets at roughly double "
                        + "their carrying value")
                .isEqualTo(BalanceSide.CREDIT);
        assertThat(accumulated.statementSection()).isEqualTo(StatementSection.BALANCE_SHEET);
        assertThat(accumulated.isContra()).isTrue();
        assertThat(accumulated.type().presentationClass())
                .as("presents within assets, as a deduction from the asset it depreciates")
                .isEqualTo(AccountType.ASSET);

        AccountView atCost = chart.requireAccount(AccountSystemKey.FIXED_ASSETS_AT_COST);
        assertThat(atCost.normalBalance()).isEqualTo(BalanceSide.DEBIT);
    }

    @Test
    @DisplayName("sales returns are income-classified but debit-normal")
    void salesReturnsAreDebitNormal() {
        List<AccountView> returns = seededAccounts().stream()
                .filter(account -> account.type() == AccountType.CONTRA_INCOME)
                .toList();

        assertThat(returns)
                .as("one contra-revenue account per sales channel, so return rate stays visible "
                        + "per channel instead of being netted into revenue")
                .extracting(AccountView::name)
                .containsExactly(
                        "Sales returns — Store & Phone",
                        "Sales returns — eCommerce",
                        "Sales returns — Skroutz");

        assertThat(returns).allSatisfy(account -> {
            assertThat(account.normalBalance()).isEqualTo(BalanceSide.DEBIT);
            assertThat(account.statementSection()).isEqualTo(StatementSection.PROFIT_AND_LOSS);
            assertThat(account.isContra()).isTrue();
            // Typed as EXPENSE these would sit below the revenue line, overstating gross revenue
            // and putting something that is not a cost into expenses.
            assertThat(account.type().presentationClass()).isEqualTo(AccountType.INCOME);
        });
    }

    @Test
    @DisplayName("normal balance is derived for every seeded account and never stored")
    void normalBalanceIsDerivedEverywhere() {
        // No column to check against, which is the point: the schema has no normal_balance_side,
        // so type and side cannot disagree.
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'account'
                """, String.class))
                .doesNotContain("normal_balance_side", "normal_balance", "balance");

        assertThat(seededAccounts()).allSatisfy(account ->
                assertThat(account.normalBalance()).isEqualTo(account.type().normalBalance()));
    }

    @Test
    @DisplayName("the sales channel split is three income accounts, phone named explicitly")
    void salesIsSplitByChannel() {
        assertThat(seededAccounts())
                .filteredOn(account -> account.name().startsWith("Sales — "))
                .extracting(AccountView::name)
                .containsExactly("Sales — Store & Phone", "Sales — eCommerce", "Sales — Skroutz");
    }

    // ---------------------------------------------------------------------------------------
    // Kinds
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("control accounts each declare a sub-ledger, and the sub-ledger finds them")
    void controlAccountsDeclareTheirSubLedger() {
        assertThat(seededAccounts())
                .filteredOn(account -> account.kind() == AccountKind.CONTROL)
                .allSatisfy(account -> {
                    assertThat(account.subLedger()).isPresent();
                    assertThat(account.requiresSubLedgerReference()).isTrue();
                });

        assertThat(inSeededGroups(chart.activeControlAccountsFor(SubLedgerType.CUSTOMER)))
                .extracting(AccountView::name)
                .containsExactly("Accounts receivable");

        // Two, and the reason activeControlAccountsFor returns a list: ADR 0004's GR/IR clearing
        // account sits behind the same sub-ledger as Accounts payable.
        assertThat(inSeededGroups(chart.activeControlAccountsFor(SubLedgerType.SUPPLIER)))
                .extracting(AccountView::name)
                .containsExactly(
                        "Accounts payable", "Goods Received / Invoice Received clearing");

        assertThat(inSeededGroups(chart.activeControlAccountsFor(SubLedgerType.INVENTORY_LOT)))
                .extracting(AccountView::name)
                .containsExactly("Inventory");

        assertThat(inSeededGroups(chart.activeControlAccountsFor(SubLedgerType.ASSET)))
                .extracting(AccountView::name)
                .containsExactly(
                        "Fixed assets at cost", "Fixed assets accumulated depreciation");
    }

    @Test
    @DisplayName("bank/cash and partner clearing accounts are the settlement targets")
    void settlementTargets() {
        assertThat(inSeededGroups(chart.activeAccountsOfKind(AccountKind.BANK_CASH)))
                .extracting(AccountView::name)
                .containsExactly("Cash", "Alpha Bank", "Piraeus Bank", "National Bank of Greece");

        assertThat(inSeededGroups(chart.activeAccountsOfKind(AccountKind.PARTNER_CLEARING)))
                .extracting(AccountView::name)
                // PayPal and Stripe are grouped under Cash & Cash Equivalents but are clearing
                // accounts, not bank accounts: the balance is held by a processor and clears on
                // their remittance, not against a bank statement line.
                .containsExactly(
                        "PayPal",
                        "Stripe",
                        "Partner Clearing — Skroutz",
                        "Partner Clearing — ACS Courier",
                        "Partner Clearing — POS provider");

        assertThat(inSeededGroups(chart.activeSettlementTargets())).hasSize(9);
    }

    @Test
    @DisplayName("exactly the three intended accounts are flagged expected-to-clear")
    void expectedToClearAccounts() {
        // The flag rather than a hardcoded list is what lets Clearing Checks in phase 8 find
        // these without being edited each time one is added.
        assertThat(inSeededGroups(chart.activeAccountsExpectedToClear()))
                .extracting(AccountView::name)
                .containsExactly(
                        "Freight / Landed Cost — Unallocated",
                        "Unclassified — Needs Review",
                        "Goods Received / Invoice Received clearing");
    }

    // ---------------------------------------------------------------------------------------
    // The two decisions that moved accounts
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("inventory write-off sits in COGS, separate from cost of goods sold")
    void inventoryWriteOffIsInCogsButNotCogs() {
        AccountView writeOff = chart.requireAccount(AccountSystemKey.INVENTORY_WRITE_OFF);

        // In the COGS group so gross margin reflects shrinkage honestly...
        assertThat(writeOff.groupName()).isEqualTo("COGS");
        // ...but its own account, so sale-driven COGS stays uncontaminated.
        assertThat(writeOff.id())
                .isNotEqualTo(chart.requireAccount(AccountSystemKey.COST_OF_GOODS_SOLD).id());
        assertThat(writeOff.type()).isEqualTo(AccountType.EXPENSE);

        // One account, not three. Which of shrinkage/damage/expiry a write-off was belongs on the
        // transaction as a reason code — a step 6 obligation, not a chart-of-accounts axis.
        assertThat(seededAccounts())
                .filteredOn(account -> account.name().toLowerCase().contains("write-off")
                        || account.name().toLowerCase().contains("shrinkage"))
                .hasSize(1);

        // Internal consumption is deliberately not a write-off: staff coffee and demo stock is a
        // real cost of operating, not a loss.
        assertThat(seededAccounts())
                .filteredOn(account -> account.name().equals("Internal consumption"))
                .singleElement()
                .satisfies(account -> assertThat(account.groupName())
                        .isEqualTo("General Expenses"));
    }

    @Test
    @DisplayName("the rounding account V2's threshold setting refers to actually exists")
    void roundingAccountExists() {
        // V2 seeded ledger.rounding.threshold describing automatic posting to "the Rounding
        // account", which until V4 did not exist anywhere.
        AccountView rounding = chart.requireAccount(AccountSystemKey.ROUNDING_DIFFERENCES);

        assertThat(rounding.name()).isEqualTo("Rounding differences");
        assertThat(rounding.groupName()).isEqualTo("General Expenses");
        // An expense account because it legitimately carries a balance on either side, depending
        // on which way the residuals happened to fall.
        assertThat(rounding.type()).isEqualTo(AccountType.EXPENSE);
        assertThat(rounding.kind()).isEqualTo(AccountKind.STANDARD);
    }

    // ---------------------------------------------------------------------------------------
    // Structural guarantees, enforced by the database rather than only by Java
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("the database refuses a control account with no sub-ledger")
    void databaseRefusesControlAccountWithoutSubLedger() {
        // Asserted against raw SQL on purpose. The service checks this too, but a check that only
        // exists in Java is not a guarantee — it says nothing about a psql session or a future
        // migration.
        long groupId = anySeededGroupId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account (group_id, display_order, name, account_type, account_kind,
                                     sub_ledger_type)
                VALUES (?, 900, 'Probe: control without sub-ledger', 'ASSET', 'CONTROL', NULL)
                """, groupId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account_control_iff_sub_ledger");
    }

    @Test
    @DisplayName("the database refuses a sub-ledger on a non-control account")
    void databaseRefusesSubLedgerOnNonControlAccount() {
        long groupId = anySeededGroupId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account (group_id, display_order, name, account_type, account_kind,
                                     sub_ledger_type)
                VALUES (?, 901, 'Probe: standard with sub-ledger', 'ASSET', 'STANDARD', 'CUSTOMER')
                """, groupId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account_control_iff_sub_ledger");
    }

    @Test
    @DisplayName("the database refuses an unknown account type")
    void databaseRefusesUnknownAccountType() {
        long groupId = anySeededGroupId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account (group_id, display_order, name, account_type, account_kind)
                VALUES (?, 902, 'Probe: nonsense type', 'REVENUE', 'STANDARD')
                """, groupId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account_type_known");
    }

    @Test
    @DisplayName("a blank code is refused, so \"no code\" has exactly one representation")
    void databaseRefusesBlankCode() {
        // Without this, two accounts could both carry '' and collide on the unique index for a
        // reason nobody would guess from the error.
        long groupId = anySeededGroupId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO account (group_id, display_order, name, account_type, account_kind, code)
                VALUES (?, 903, 'Probe: blank code', 'ASSET', 'STANDARD', '   ')
                """, groupId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("account_code_not_blank");
    }

    // ---------------------------------------------------------------------------------------
    // Changing the chart
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a new account is appended to its group and recorded in the audit log")
    void createAccountAppendsAndAudits() {
        AccountGroupView group = chart.createGroup("Test — create");

        AccountView first = chart.createAccount(
                NewAccount.standard("Test expense one", AccountType.EXPENSE, group.id()));
        AccountView second = chart.createAccount(
                NewAccount.standard("Test expense two", AccountType.EXPENSE, group.id()));

        assertThat(first.displayOrder()).isZero();
        assertThat(second.displayOrder()).isEqualTo(1);
        assertThat(second.active()).isTrue();
        assertThat(second.systemKey()).isNull();
        assertThat(second.groupName()).isEqualTo("Test — create");

        List<AuditEntry> entries =
                auditLog.findForEntity("Account", String.valueOf(first.id()), 10);
        assertThat(entries).isNotEmpty();
        assertThat(entries.getFirst().action()).isEqualTo("account.created");
        assertThat(entries.getFirst().detail()).containsEntry("name", "Test expense one");
    }

    @Test
    @DisplayName("a control account created without a sub-ledger is refused with a clear message")
    void createControlAccountWithoutSubLedgerIsRefused() {
        AccountGroupView group = chart.createGroup("Test — control validation");

        assertThatExceptionOfType(InvalidAccountException.class)
                .isThrownBy(() -> chart.createAccount(new NewAccount(
                        "Test control", AccountType.ASSET, AccountKind.CONTROL,
                        null, group.id(), false)))
                .withMessageContaining("must declare a sub-ledger type");

        assertThatExceptionOfType(InvalidAccountException.class)
                .isThrownBy(() -> chart.createAccount(new NewAccount(
                        "Test standard", AccountType.ASSET, AccountKind.STANDARD,
                        SubLedgerType.CUSTOMER, group.id(), false)))
                .withMessageContaining("Only CONTROL accounts");
    }

    @Test
    @DisplayName("a duplicate name within a group is refused, the same name elsewhere is not")
    void duplicateNameWithinGroupIsRefused() {
        AccountGroupView first = chart.createGroup("Test — duplicates A");
        AccountGroupView second = chart.createGroup("Test — duplicates B");

        chart.createAccount(NewAccount.standard("Shared name", AccountType.EXPENSE, first.id()));

        assertThatExceptionOfType(InvalidAccountException.class)
                .isThrownBy(() -> chart.createAccount(
                        NewAccount.standard("Shared name", AccountType.EXPENSE, first.id())))
                .withMessageContaining("already has an account named");

        // Unique within a group, not globally — see the migration's reasoning.
        assertThat(chart.createAccount(
                NewAccount.standard("Shared name", AccountType.EXPENSE, second.id())).name())
                .isEqualTo("Shared name");
    }

    @Test
    @DisplayName("creating an account in a group that does not exist names the missing group")
    void createAccountInMissingGroup() {
        assertThatExceptionOfType(AccountGroupNotFoundException.class)
                .isThrownBy(() -> chart.createAccount(
                        NewAccount.standard("Orphan", AccountType.EXPENSE, 999_999L)))
                .withMessageContaining("999999");
    }

    @Test
    @DisplayName("an account can be deactivated and reactivated, and both are audited")
    void deactivateAndReactivate() {
        AccountGroupView group = chart.createGroup("Test — deactivation");
        AccountView account = chart.createAccount(
                NewAccount.standard("Test deactivatable", AccountType.EXPENSE, group.id()));

        chart.deactivate(account.id());
        assertThat(chart.requireAccount(account.id()).active()).isFalse();
        // Still readable, and still in the full chart: it may hold a balance from before.
        assertThat(chart.allAccounts()).extracting(AccountView::id).contains(account.id());
        assertThat(chart.activeAccounts()).extracting(AccountView::id)
                .doesNotContain(account.id());

        // Idempotent rather than throwing — deactivating something already inactive is not an
        // error worth interrupting a caller for.
        chart.deactivate(account.id());

        chart.reactivate(account.id());
        assertThat(chart.requireAccount(account.id()).active()).isTrue();

        assertThat(auditLog.findForEntity("Account", String.valueOf(account.id()), 10))
                .extracting(AuditEntry::action)
                .contains("account.deactivated", "account.reactivated");
    }

    @Test
    @DisplayName("an account a posting rule depends on cannot be deactivated")
    void systemAccountsCannotBeDeactivated() {
        AccountView rounding = chart.requireAccount(AccountSystemKey.ROUNDING_DIFFERENCES);

        assertThatExceptionOfType(InvalidAccountException.class)
                .isThrownBy(() -> chart.deactivate(rounding.id()))
                .withMessageContaining("ROUNDING_DIFFERENCES")
                .withMessageContaining("no fallback");

        assertThat(chart.requireAccount(rounding.id()).active()).isTrue();
    }

    @Test
    @DisplayName("a keyed account may be renamed — the key is what posting rules depend on")
    void keyedAccountCanBeRenamed() {
        AccountView original = chart.requireAccount(AccountSystemKey.UNCLASSIFIED_NEEDS_REVIEW);

        chart.renameAccount(original.id(), "Unclassified — Needs Review (renamed)");
        try {
            AccountView renamed =
                    chart.requireAccount(AccountSystemKey.UNCLASSIFIED_NEEDS_REVIEW);
            assertThat(renamed.name()).isEqualTo("Unclassified — Needs Review (renamed)");
            assertThat(renamed.id())
                    .as("the key still resolves after a rename, which is why it exists")
                    .isEqualTo(original.id());
            assertThat(auditLog.findForEntity("Account", String.valueOf(original.id()), 10))
                    .anySatisfy(entry -> {
                        assertThat(entry.action()).isEqualTo("account.renamed");
                        assertThat(entry.detail())
                                .containsEntry("from", "Unclassified — Needs Review");
                    });
        } finally {
            // Restored so the seed-content assertions elsewhere in this class stay independent
            // of test ordering.
            chart.renameAccount(original.id(), original.name());
        }
    }

    @Test
    @DisplayName("reordering accounts within a group rewrites positions contiguously")
    void reorderAccountsWithinGroup() {
        AccountGroupView group = chart.createGroup("Test — reorder");
        AccountView a = chart.createAccount(
                NewAccount.standard("Reorder A", AccountType.EXPENSE, group.id()));
        AccountView b = chart.createAccount(
                NewAccount.standard("Reorder B", AccountType.EXPENSE, group.id()));
        AccountView c = chart.createAccount(
                NewAccount.standard("Reorder C", AccountType.EXPENSE, group.id()));

        chart.reorderAccountsWithinGroup(group.id(), List.of(c.id(), a.id(), b.id()));

        assertThat(accountsIn(group.id()))
                .extracting(AccountView::name)
                .containsExactly("Reorder C", "Reorder A", "Reorder B");
        assertThat(accountsIn(group.id()))
                .extracting(AccountView::displayOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("a partial or duplicated reorder is refused rather than completed by guessing")
    void incompleteReorderIsRefused() {
        AccountGroupView group = chart.createGroup("Test — partial reorder");
        AccountView a = chart.createAccount(
                NewAccount.standard("Partial A", AccountType.EXPENSE, group.id()));
        AccountView b = chart.createAccount(
                NewAccount.standard("Partial B", AccountType.EXPENSE, group.id()));

        // CLAUDE.md rule 7: the remainder would be left in an order nobody chose.
        assertThatExceptionOfType(InvalidAccountException.class)
                .isThrownBy(() -> chart.reorderAccountsWithinGroup(group.id(), List.of(a.id())))
                .withMessageContaining("exactly once");

        assertThatExceptionOfType(InvalidAccountException.class)
                .isThrownBy(() -> chart.reorderAccountsWithinGroup(
                        group.id(), List.of(a.id(), a.id())))
                .withMessageContaining("more than once");

        // Unchanged by the refused calls.
        assertThat(accountsIn(group.id()))
                .extracting(AccountView::name)
                .containsExactly("Partial A", "Partial B");
        assertThat(b.displayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("a missing account names the id it wanted")
    void missingAccountNamesTheId() {
        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> chart.requireAccount(999_999L))
                .withMessageContaining("999999");

        assertThat(chart.findAccount(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("the assembled chart holds every account exactly once")
    void chartIsCompleteAndNonDuplicating() {
        List<AccountGroupView> assembled = chart.chart();

        List<Long> fromChart = assembled.stream()
                .flatMap(group -> group.accounts().stream())
                .map(AccountView::id)
                .toList();
        List<Long> fromFlatList = chart.allAccounts().stream().map(AccountView::id).toList();

        assertThat(fromChart).containsExactlyInAnyOrderElementsOf(fromFlatList);
        assertThat(Set.copyOf(fromChart)).hasSameSizeAs(fromChart);

        // Every account's group in the flat list agrees with where the chart put it.
        assertThat(assembled).allSatisfy(group ->
                assertThat(group.accounts()).allSatisfy(account ->
                        assertThat(account.groupId()).isEqualTo(group.id())));
    }

    @Test
    @DisplayName("audit columns are populated on a seeded account")
    void seededAccountsHaveAuditColumns() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM account WHERE created_by <> 'system' OR created_at IS NULL",
                Integer.class))
                .isZero();
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /** Accounts the migration seeded, i.e. excluding anything a test created. */
    private List<AccountView> seededAccounts() {
        return inSeededGroups(chart.allAccounts());
    }

    private static List<AccountView> inSeededGroups(List<AccountView> accounts) {
        return accounts.stream()
                .filter(account -> SEEDED_GROUPS.contains(account.groupName()))
                .toList();
    }

    private Map<String, Integer> accountCountsBySeededGroup() {
        return chart.chart().stream()
                .filter(group -> SEEDED_GROUPS.contains(group.name()))
                .collect(Collectors.toMap(
                        AccountGroupView::name, group -> group.accounts().size()));
    }

    private List<AccountView> accountsIn(long groupId) {
        return chart.chart().stream()
                .filter(group -> group.id() == groupId)
                .findFirst()
                .orElseThrow()
                .accounts();
    }

    private long anySeededGroupId() {
        return chart.groups().stream()
                .filter(group -> group.name().equals("Current Assets"))
                .findFirst()
                .orElseThrow()
                .id();
    }
}
