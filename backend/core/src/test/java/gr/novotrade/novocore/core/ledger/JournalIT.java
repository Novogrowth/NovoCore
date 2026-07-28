package gr.novotrade.novocore.core.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.ledger.AccountBalance;
import gr.novotrade.novocore.core.api.ledger.InvalidJournalEntryException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotAmendableException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotFoundException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalLineView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.ledger.TrialBalance;
import gr.novotrade.novocore.core.api.ledger.UnbalancedJournalEntryException;
import gr.novotrade.novocore.core.api.ledger.VatDimension;
import gr.novotrade.novocore.core.api.ledger.VatDirection;
import gr.novotrade.novocore.core.api.ledger.VatTotal;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The journal engine against a real PostgreSQL, so that everything described as "structural" is proven
 * to be a constraint or a trigger rather than a Java check somebody could route around.
 *
 * <p><strong>Balance assertions are deltas, not absolutes.</strong> These tests share a database and are
 * deliberately not transactional (see {@code AbstractCoreIntegrationTest}), so an account's cumulative
 * balance depends on every other test in the run. Reading before and after and asserting the difference
 * is isolation-proof and tests the same arithmetic.
 *
 * <p><strong>Raw-SQL probes use a {@code DO} block.</strong> The balance invariant is a <em>deferred</em>
 * constraint trigger, checked at commit. Under autocommit each statement is its own transaction, so an
 * entry and its lines have to be written inside a single statement for the check to see the finished
 * state — which a {@code DO} block is.
 */
class JournalIT extends AbstractCoreIntegrationTest {

    @Autowired
    private JournalService journal;

    @Autowired
    private ChartOfAccountsService chart;

    @Autowired
    private CustomerService customers;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    private AccountView account(AccountSystemKey key) {
        return chart.requireAccount(key);
    }

    private AccountView byName(String name) {
        return chart.activeAccounts().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No seeded account named '" + name + "'."));
    }

    private long cash() {
        return byName("Cash").id();
    }

    private long sales() {
        return byName("Sales — Store & Phone").id();
    }

    private long standardRateVatClassId() {
        return vatClasses.requireByCode("1410").id();
    }

    private long reducedRateVatClassId() {
        return vatClasses.requireByCode("1131").id();
    }

    private long newCustomerId(String name) {
        return customers.create(NewCustomer.retail(name, null, null)).id();
    }

    /** A simple balanced cash sale, with no VAT and no sub-ledger, for tests about mechanics. */
    private JournalEntryView postCashSale(LocalDate date, String description, String amount) {
        return journal.postManualEntry(date, description, List.of(
                NewJournalLine.debit(cash(), Money.ofEur(amount)),
                NewJournalLine.credit(sales(), Money.ofEur(amount))));
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("posting: debits must equal credits (CLAUDE.md rule 6)")
    class Posting {

        @Test
        @DisplayName("a balanced entry posts, and comes back with its lines in order")
        void aBalancedEntryPosts() {
            LocalDate date = LocalDate.of(2026, 3, 2);

            JournalEntryView posted = journal.postManualEntry(date, "Cash sale", List.of(
                    NewJournalLine.debit(cash(), Money.ofEur("124.00")),
                    NewJournalLine.credit(sales(), Money.ofEur("100.00")),
                    NewJournalLine.credit(account(AccountSystemKey.OUTPUT_VAT).id(),
                                    Money.ofEur("24.00"))
                            .withVat(VatDimension.of(
                                    standardRateVatClassId(), Money.ofEur("100.00")))));

            assertThat(posted.id()).isPositive();
            assertThat(posted.source()).isEqualTo(JournalSource.MANUAL_JOURNAL_ENTRY);
            assertThat(posted.isBalanced()).isTrue();
            assertThat(posted.totalDebits()).isEqualTo(Money.ofEur("124.00"));
            assertThat(posted.lines()).extracting(JournalLineView::lineNumber)
                    .containsExactly(0, 1, 2);
            // The line carries the account's name and type, so a ledger listing needs no second lookup.
            assertThat(posted.lines().getFirst().accountName()).isEqualTo("Cash");
            assertThat(posted.lines().getFirst().side()).isEqualTo(BalanceSide.DEBIT);

            JournalEntryView reread = journal.requireEntry(posted.id());
            assertThat(reread).isEqualTo(posted);
        }

        @Test
        @DisplayName("an unbalanced entry is refused, naming both totals and the difference")
        void unbalancedIsRefused() {
            assertThatExceptionOfType(UnbalancedJournalEntryException.class)
                    .isThrownBy(() -> journal.postManualEntry(
                            LocalDate.of(2026, 3, 3), "Out by a cent", List.of(
                                    NewJournalLine.debit(cash(), Money.ofEur("100.00")),
                                    NewJournalLine.credit(sales(), Money.ofEur("99.99")))))
                    .withMessageContaining("100.00 EUR")
                    .withMessageContaining("99.99 EUR")
                    .withMessageContaining("0.01 EUR");
        }

        @Test
        @DisplayName("two currencies in one entry are refused rather than converted")
        void mixedCurrenciesAreRefused() {
            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.postManualEntry(
                            LocalDate.of(2026, 3, 4), "Mixed", List.of(
                                    NewJournalLine.debit(cash(), Money.ofEur("10.00")),
                                    NewJournalLine.credit(sales(),
                                            Money.of("10.00",
                                                    java.util.Currency.getInstance("USD"))))))
                    .withMessageContaining("ADR 0005");
        }

        @Test
        @DisplayName("an inactive account refuses new postings but still accepts a reversal")
        void inactiveAccountsAreRefusedExceptOnReversal() {
            AccountView spare = chart.createAccount(new gr.novotrade.novocore.core.api.account
                    .NewAccount("JournalIT — retiring account",
                    gr.novotrade.novocore.core.api.account.AccountType.EXPENSE,
                    gr.novotrade.novocore.core.api.account.AccountKind.STANDARD,
                    null, byName("Other general expenses").groupId(), false));

            JournalEntryView posted = journal.postManualEntry(
                    LocalDate.of(2026, 3, 5), "Before retirement", List.of(
                            NewJournalLine.debit(spare.id(), Money.ofEur("10.00")),
                            NewJournalLine.credit(cash(), Money.ofEur("10.00"))));

            chart.deactivate(spare.id());

            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.postManualEntry(
                            LocalDate.of(2026, 3, 6), "After retirement", List.of(
                                    NewJournalLine.debit(spare.id(), Money.ofEur("10.00")),
                                    NewJournalLine.credit(cash(), Money.ofEur("10.00")))))
                    .withMessageContaining("inactive");

            // The point of the exception: deactivating an account is an ordinary administrative act,
            // and it must not make an entry that already used it permanently uncorrectable.
            JournalEntryView reversal = journal.reverse(
                    posted.id(), LocalDate.of(2026, 3, 6), null);
            assertThat(reversal.reversalOfEntryId()).isEqualTo(posted.id());
        }
    }

    @Nested
    @DisplayName("posting: a line must agree with the account it posts to")
    class LineRules {

        @Test
        @DisplayName("a control-account line without a sub-ledger reference is refused")
        void controlAccountsNeedASubLedgerReference() {
            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.postManualEntry(
                            LocalDate.of(2026, 3, 7), "Bare AR line", List.of(
                                    NewJournalLine.debit(
                                            account(AccountSystemKey.ACCOUNTS_RECEIVABLE).id(),
                                            Money.ofEur("50.00")),
                                    NewJournalLine.credit(sales(), Money.ofEur("50.00")))))
                    .withMessageContaining("brief §6");
        }

        @Test
        @DisplayName("a customer reference cannot land on an accounts payable line")
        void theSubLedgerTypeMustMatchTheAccount() {
            long customerId = newCustomerId("JournalIT — mismatch customer");

            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.postManualEntry(
                            LocalDate.of(2026, 3, 8), "Wrong sub-ledger", List.of(
                                    NewJournalLine.debit(cash(), Money.ofEur("50.00")),
                                    NewJournalLine.credit(
                                                    account(AccountSystemKey.ACCOUNTS_PAYABLE).id(),
                                                    Money.ofEur("50.00"))
                                            .forSubLedger(SubLedgerRef.customer(customerId)))))
                    .withMessageContaining("accounts payable line");
        }

        @Test
        @DisplayName("a sub-ledger reference is permitted on a non-control account")
        void nonControlAccountsMayCarryOne() {
            // Cost of goods sold is STANDARD and its lines still name lots (brief §6, one line per lot
            // consumed). Control-ness governs whether a reference is required, not whether one may be
            // present — asserted here so a later refactor does not "tidy" that into a blanket rule.
            long customerId = newCustomerId("JournalIT — referenced on a standard account");

            JournalEntryView posted = journal.postManualEntry(
                    LocalDate.of(2026, 3, 9), "Standard account with a reference", List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("10.00")),
                            NewJournalLine.credit(sales(), Money.ofEur("10.00"))
                                    .forSubLedger(SubLedgerRef.customer(customerId))));

            assertThat(posted.lines().get(1).subLedger())
                    .contains(SubLedgerRef.customer(customerId));
        }

        @Test
        @DisplayName("a dangling sub-ledger reference is refused by the database")
        void danglingSubLedgerReferencesAreRefused() {
            // No foreign key is possible — the reference is polymorphic — so this is a trigger, and it
            // is the only thing standing between a control account and a balance nobody can explain.
            assertThatThrownBy(() -> journal.postManualEntry(
                    LocalDate.of(2026, 3, 10), "Ghost customer", List.of(
                            NewJournalLine.debit(
                                            account(AccountSystemKey.ACCOUNTS_RECEIVABLE).id(),
                                            Money.ofEur("50.00"))
                                    .forSubLedger(SubLedgerRef.customer(999_000_111L)),
                            NewJournalLine.credit(sales(), Money.ofEur("50.00")))))
                    .hasMessageContaining("does not exist");
        }

        @Test
        @DisplayName("a VAT class on a line that is not a VAT line is refused")
        void vatBelongsOnlyOnVatAccounts() {
            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.postManualEntry(
                            LocalDate.of(2026, 3, 11), "Rate on a revenue line", List.of(
                                    NewJournalLine.debit(cash(), Money.ofEur("10.00")),
                                    NewJournalLine.credit(sales(), Money.ofEur("10.00"))
                                            .withVat(VatDimension.of(standardRateVatClassId(),
                                                    Money.ofEur("10.00"))))))
                    .withMessageContaining("Q14");
        }

        @Test
        @DisplayName("the VAT-account rule is a trigger, not only a service check")
        void theVatRuleIsEnforcedByTheDatabase() {
            long entryId = postCashSale(
                    LocalDate.of(2026, 3, 12), "JournalIT — VAT probe host", "10.00").id();

            // Asserted on the message rather than on the exception type: a trigger's RAISE arrives as
            // SQLSTATE P0001, which Spring maps to UncategorizedSQLException rather than to
            // DataIntegrityViolationException. That is true of every trigger-enforced rule here, and
            // the message is the part that has to be readable anyway.
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE journal_line
                    SET vat_class_id = ?, taxable_base = 5.00, taxable_base_currency = 'EUR'
                    WHERE entry_id = ? AND line_number = 1
                    """, standardRateVatClassId(), entryId))
                    .hasMessageContaining("OUTPUT_VAT");
        }
    }

    @Nested
    @DisplayName("the balance invariant is structural, not merely validated")
    class StructuralInvariant {

        private long anAccountId() {
            return cash();
        }

        @Test
        @DisplayName("an unbalanced entry written straight to SQL is refused at commit")
        void rawSqlCannotWriteAnUnbalancedEntry() {
            // The whole content of "structurally" in CLAUDE.md rule 6: this path never touches Java.
            assertThatThrownBy(() -> jdbc.execute("""
                    DO $$
                    DECLARE new_entry bigint;
                    BEGIN
                        INSERT INTO journal_entry (entry_date, description, source)
                        VALUES (DATE '2026-03-13', 'probe: unbalanced', 'MANUAL_JOURNAL_ENTRY')
                        RETURNING id INTO new_entry;

                        INSERT INTO journal_line
                            (entry_id, line_number, account_id, side, amount, amount_currency)
                        SELECT new_entry, 0, id, 'DEBIT', 100.00, 'EUR' FROM account WHERE name = 'Cash';
                        INSERT INTO journal_line
                            (entry_id, line_number, account_id, side, amount, amount_currency)
                        SELECT new_entry, 1, id, 'CREDIT', 99.99, 'EUR'
                        FROM account WHERE name = 'Sales — Store & Phone';
                    END $$;
                    """))
                    .hasMessageContaining("does not balance")
                    .hasMessageContaining("rule 6");
        }

        @Test
        @DisplayName("an entry with no lines at all is refused, which the line trigger cannot catch")
        void anEmptyEntryIsRefused() {
            // The reason there are two triggers. A trigger on journal_line never fires for an entry
            // that has no lines, so without the entry-level one an empty entry would be storable.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO journal_entry (entry_date, description, source)
                    VALUES (DATE '2026-03-13', 'probe: no lines', 'MANUAL_JOURNAL_ENTRY')
                    """))
                    .hasMessageContaining("at least two");
        }

        @Test
        @DisplayName("a one-line entry is refused for the same reason")
        void aSingleLineEntryIsRefused() {
            assertThatThrownBy(() -> jdbc.execute("""
                    DO $$
                    DECLARE new_entry bigint;
                    BEGIN
                        INSERT INTO journal_entry (entry_date, description, source)
                        VALUES (DATE '2026-03-13', 'probe: one line', 'MANUAL_JOURNAL_ENTRY')
                        RETURNING id INTO new_entry;
                        INSERT INTO journal_line
                            (entry_id, line_number, account_id, side, amount, amount_currency)
                        SELECT new_entry, 0, id, 'DEBIT', 1.00, 'EUR' FROM account WHERE name = 'Cash';
                    END $$;
                    """))
                    .hasMessageContaining("at least two");
        }

        @Test
        @DisplayName("a zero or negative line amount is refused by CHECK")
        void lineAmountsAreStrictlyPositive() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO journal_line
                        (entry_id, line_number, account_id, side, amount, amount_currency)
                    VALUES (1, 99, ?, 'DEBIT', 0.00, 'EUR')
                    """, anAccountId()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("journal_line_amount_positive");
        }

        @Test
        @DisplayName("an entry mixing currencies is refused by the trigger too")
        void mixedCurrencyEntriesAreRefusedByTheDatabase() {
            assertThatThrownBy(() -> jdbc.execute("""
                    DO $$
                    DECLARE new_entry bigint;
                    BEGIN
                        INSERT INTO journal_entry (entry_date, description, source)
                        VALUES (DATE '2026-03-13', 'probe: two currencies', 'MANUAL_JOURNAL_ENTRY')
                        RETURNING id INTO new_entry;
                        INSERT INTO journal_line
                            (entry_id, line_number, account_id, side, amount, amount_currency)
                        SELECT new_entry, 0, id, 'DEBIT', 10.00, 'EUR' FROM account WHERE name = 'Cash';
                        INSERT INTO journal_line
                            (entry_id, line_number, account_id, side, amount, amount_currency)
                        SELECT new_entry, 1, id, 'CREDIT', 10.00, 'USD'
                        FROM account WHERE name = 'Sales — Store & Phone';
                    END $$;
                    """))
                    .hasMessageContaining("different currencies");
        }

        @Test
        @DisplayName("no journal entry can ever be deleted, whatever its source")
        void nothingIsEverDeleted() {
            long entryId = postCashSale(
                    LocalDate.of(2026, 3, 14), "JournalIT — delete probe", "5.00").id();

            // Manual entries are the MOST permissive source under Q13 — editable in place — and even
            // they cannot be deleted. That is the strongest form of the claim.
            assertThatThrownBy(() -> jdbc.update(
                    "DELETE FROM journal_entry WHERE id = ?", entryId))
                    .hasMessageContaining("cannot be deleted");
            assertThat(journal.findEntry(entryId)).isPresent();
        }

        @Test
        @DisplayName("an entry's source can never change")
        void theSourceIsFixed() {
            long entryId = postCashSale(
                    LocalDate.of(2026, 3, 15), "JournalIT — relabel probe", "5.00").id();

            // Otherwise an immutable entry could be relabelled into an editable one and then edited.
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE journal_entry SET source = 'RECEIPT' WHERE id = ?", entryId))
                    .hasMessageContaining("cannot change source");
        }
    }

    @Nested
    @DisplayName("Q13 — correction policy")
    class CorrectionPolicy {

        @Test
        @DisplayName("the database and the Java enum agree about which sources are amendable")
        void theTwoStatementsOfThePolicyAgree() {
            // The policy is written twice — once as JournalSource.isAmendable(), once as the SQL
            // function the triggers use — because a trigger cannot call Java and a service check is not
            // structural. This is what stops the two drifting.
            for (JournalSource source : JournalSource.values()) {
                Boolean fromDatabase = jdbc.queryForObject(
                        "SELECT journal_source_is_amendable(?)", Boolean.class, source.name());
                assertThat(fromDatabase)
                        .as("journal_source_is_amendable('%s')", source)
                        .isEqualTo(source.isAmendable());
            }
        }

        @Test
        @DisplayName("the source CHECK constraint lists exactly the enum's values")
        void theSourceListsAgree() {
            // A value in Java that the CHECK rejects fails only when somebody first posts one; a value
            // in the CHECK that Java lacks is a row nothing can read back.
            for (JournalSource source : JournalSource.values()) {
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM pg_constraint
                        WHERE conname = 'journal_entry_source_known'
                          AND pg_get_constraintdef(oid) LIKE ?
                        """, Integer.class, "%'" + source.name() + "'%"))
                        .as("%s is listed in journal_entry_source_known", source)
                        .isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("""
                    SELECT length(pg_get_constraintdef(oid))
                        - length(replace(pg_get_constraintdef(oid), '''', ''))
                    FROM pg_constraint WHERE conname = 'journal_entry_source_known'
                    """, Integer.class))
                    .as("no source is listed in the CHECK that the enum does not have")
                    .isEqualTo(JournalSource.values().length * 2);
        }

        @Test
        @DisplayName("a manual entry is editable in place, and the previous state goes to the audit log")
        void amendableSourcesCanBeEdited() {
            LocalDate date = LocalDate.of(2026, 4, 1);
            JournalEntryView posted = postCashSale(date, "JournalIT — typo, 90 euros", "90.00");

            JournalEntryView amended = journal.amend(posted.id(), date,
                    "JournalIT — corrected to 100 euros", List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("100.00")),
                            NewJournalLine.credit(sales(), Money.ofEur("100.00"))));

            assertThat(amended.id()).isEqualTo(posted.id());
            assertThat(amended.totalDebits()).isEqualTo(Money.ofEur("100.00"));
            assertThat(amended.lines()).hasSize(2);

            // Q13 names the audit log as the mechanism that makes editing in place acceptable, so the
            // previous figures have to survive the edit. Without this the edit is indistinguishable
            // from the entry having always said the new thing.
            AuditEntry recorded = auditLog.findForEntity(
                    "JournalEntry", String.valueOf(posted.id()), 10).stream()
                    .filter(candidate -> candidate.action().equals("journal-entry.amended"))
                    .findFirst()
                    .orElseThrow();
            assertThat(recorded.detail())
                    .containsEntry("previousDescription", "JournalIT — typo, 90 euros");
            assertThat(recorded.detail().get("previousLines")).contains("90.00 EUR");
            assertThat(recorded.detail().get("newLines")).contains("100.00 EUR");
        }

        @Test
        @DisplayName("an amendment replaces every line rather than merging")
        void amendmentReplacesTheWholeList() {
            LocalDate date = LocalDate.of(2026, 4, 2);
            JournalEntryView posted = journal.postManualEntry(date, "JournalIT — three lines",
                    List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("124.00")),
                            NewJournalLine.credit(sales(), Money.ofEur("100.00")),
                            NewJournalLine.credit(account(AccountSystemKey.OUTPUT_VAT).id(),
                                            Money.ofEur("24.00"))
                                    .withVat(VatDimension.of(standardRateVatClassId(),
                                            Money.ofEur("100.00")))));

            JournalEntryView amended = journal.amend(posted.id(), date, "JournalIT — now two lines",
                    List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("50.00")),
                            NewJournalLine.credit(sales(), Money.ofEur("50.00"))));

            assertThat(amended.lines()).hasSize(2);
            assertThat(amended.lines()).extracting(JournalLineView::lineNumber)
                    .containsExactly(0, 1);
        }

        @Test
        @DisplayName("an invoice is immutable once posted, in the service and in the database")
        void immutableSourcesAreRefused() {
            LocalDate date = LocalDate.of(2026, 4, 3);
            long customerId = newCustomerId("JournalIT — invoice customer");
            JournalEntryView invoice = journal.post(NewJournalEntry.of(date,
                    "JournalIT — sales invoice", JournalSource.SALES_INVOICE, List.of(
                            NewJournalLine.debit(
                                            account(AccountSystemKey.ACCOUNTS_RECEIVABLE).id(),
                                            Money.ofEur("124.00"))
                                    .forSubLedger(SubLedgerRef.customer(customerId)),
                            NewJournalLine.credit(sales(), Money.ofEur("124.00")))));

            assertThatExceptionOfType(JournalEntryNotAmendableException.class)
                    .isThrownBy(() -> journal.amend(invoice.id(), date, "Edited", List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("1.00")),
                            NewJournalLine.credit(sales(), Money.ofEur("1.00")))))
                    .withMessageContaining("reversing entry");

            // And the same claim without going through the service at all, which is the difference
            // between a policy and a rule.
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE journal_entry SET description = 'edited' WHERE id = ?", invoice.id()))
                    .hasMessageContaining("immutable once posted");
            assertThatThrownBy(() -> jdbc.update(
                    "DELETE FROM journal_line WHERE entry_id = ?", invoice.id()))
                    .hasMessageContaining("immutable once posted");
        }

        @Test
        @DisplayName("a reversal cannot be amended, nor can an entry that has been reversed")
        void reversalPairsAreFrozen() {
            LocalDate date = LocalDate.of(2026, 4, 4);
            JournalEntryView posted = postCashSale(date, "JournalIT — to be reversed", "30.00");
            JournalEntryView reversal = journal.reverse(posted.id(), date, null);

            // Both halves, because a reversal's lines are DEFINED as the mirror of the other's: editing
            // either would leave a pair that no longer nets to zero while still claiming to.
            assertThatExceptionOfType(JournalEntryNotAmendableException.class)
                    .isThrownBy(() -> journal.amend(reversal.id(), date, "Edited", List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("1.00")),
                            NewJournalLine.credit(sales(), Money.ofEur("1.00")))))
                    .withMessageContaining("mirror");
            assertThatExceptionOfType(JournalEntryNotAmendableException.class)
                    .isThrownBy(() -> journal.amend(posted.id(), date, "Edited", List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("1.00")),
                            NewJournalLine.credit(sales(), Money.ofEur("1.00")))))
                    .withMessageContaining("already been reversed");
        }
    }

    @Nested
    @DisplayName("reversal")
    class Reversal {

        @Test
        @DisplayName("a reversal is the exact mirror, keeps the source, and links both ways")
        void reversalMirrorsTheOriginal() {
            LocalDate date = LocalDate.of(2026, 5, 1);
            JournalEntryView posted = postCashSale(date, "JournalIT — mirror me", "42.50");

            JournalEntryView reversal = journal.reverse(
                    posted.id(), LocalDate.of(2026, 5, 2), null);

            assertThat(reversal.source()).isEqualTo(posted.source());
            assertThat(reversal.reversalOfEntryId()).isEqualTo(posted.id());
            assertThat(reversal.entryDate()).isEqualTo(LocalDate.of(2026, 5, 2));
            assertThat(reversal.description()).contains("Reversal of entry " + posted.id());
            assertThat(reversal.lines()).extracting(JournalLineView::side)
                    .containsExactly(BalanceSide.CREDIT, BalanceSide.DEBIT);
            assertThat(reversal.totalDebits()).isEqualTo(posted.totalDebits());

            // The back-link is derived, not stored, so this proves the query rather than a column.
            JournalEntryView original = journal.requireEntry(posted.id());
            assertThat(original.reversedByEntryId()).isEqualTo(reversal.id());
            assertThat(original.isReversed()).isTrue();
            assertThat(original.isAmendable()).isFalse();
        }

        @Test
        @DisplayName("the pair nets to nothing, which is the point of a reversal")
        void thePairNetsToZero() {
            LocalDate date = LocalDate.of(2026, 5, 3);
            AccountBalance before = journal.balanceOf(cash(), LocalDate.of(2026, 5, 4));

            JournalEntryView posted = postCashSale(date, "JournalIT — netting", "77.00");
            journal.reverse(posted.id(), date, null);

            AccountBalance after = journal.balanceOf(cash(), LocalDate.of(2026, 5, 4));
            assertThat(after.onNormalSide()).isEqualTo(before.onNormalSide());
            // Not by removing anything: both movements are still there and visible.
            assertThat(after.debits()).isEqualTo(before.debits().plus(Money.ofEur("77.00")));
            assertThat(after.credits()).isEqualTo(before.credits().plus(Money.ofEur("77.00")));
        }

        @Test
        @DisplayName("an entry can be reversed at most once")
        void reversingTwiceIsRefused() {
            LocalDate date = LocalDate.of(2026, 5, 5);
            JournalEntryView posted = postCashSale(date, "JournalIT — reverse once", "10.00");
            journal.reverse(posted.id(), date, null);

            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.reverse(posted.id(), date, null))
                    .withMessageContaining("already been reversed");
        }

        @Test
        @DisplayName("a source that owns state outside the ledger is refused, naming what to use")
        void sourcesWithSideEffectsAreRefused() {
            LocalDate date = LocalDate.of(2026, 5, 6);
            long customerId = newCustomerId("JournalIT — receipt customer");
            JournalEntryView receipt = journal.post(NewJournalEntry.of(date,
                    "JournalIT — receipt", JournalSource.RECEIPT, List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("124.00")),
                            NewJournalLine.credit(
                                            account(AccountSystemKey.ACCOUNTS_RECEIVABLE).id(),
                                            Money.ofEur("124.00"))
                                    .forSubLedger(SubLedgerRef.customer(customerId)))));

            // Reversing the money without releasing the allocations would leave invoices reported as
            // settled by a receipt that no longer exists.
            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.reverse(receipt.id(), date, null))
                    .withMessageContaining("allocations it made against invoices");
        }

        @Test
        @DisplayName("an entry claiming to be a reversal but posting something else is refused")
        void aReversalMustActuallyMirror() {
            LocalDate date = LocalDate.of(2026, 5, 7);
            JournalEntryView posted = postCashSale(date, "JournalIT — mirror check", "60.00");

            // This is what makes post() with reversalOfEntryId set a safe path for a service reversing
            // its own document, rather than a second and weaker way in.
            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.post(NewJournalEntry.reversalOf(
                            posted.id(), date, "Not really a reversal",
                            JournalSource.MANUAL_JOURNAL_ENTRY, List.of(
                                    NewJournalLine.credit(cash(), Money.ofEur("59.00")),
                                    NewJournalLine.debit(sales(), Money.ofEur("59.00"))))))
                    .withMessageContaining("is not its mirror");
        }

        @Test
        @DisplayName("mirrorOf produces exactly what post accepts as a reversal")
        void mirrorOfAndPostAgree() {
            LocalDate date = LocalDate.of(2026, 5, 8);
            JournalEntryView posted = postCashSale(date, "JournalIT — mirrorOf", "15.00");

            List<NewJournalLine> mirror = journal.mirrorOf(posted.id());
            JournalEntryView reversal = journal.post(NewJournalEntry.reversalOf(
                    posted.id(), date, "Hand-built reversal",
                    JournalSource.MANUAL_JOURNAL_ENTRY, mirror));

            assertThat(reversal.reversalOfEntryId()).isEqualTo(posted.id());
        }

        @Test
        @DisplayName("reversing an unknown entry says so rather than posting nothing")
        void unknownEntry() {
            assertThatExceptionOfType(JournalEntryNotFoundException.class)
                    .isThrownBy(() -> journal.reverse(999_000_222L, LocalDate.of(2026, 5, 9), null));
        }
    }

    @Nested
    @DisplayName("reading: balances, ledgers and the trial balance")
    class Reading {

        @Test
        @DisplayName("a balance is the sum of the lines, computed on read and dated")
        void balancesAreComputed() {
            // Dates chosen so that no other test in this run posts to Cash between them, and every
            // assertion below is still a DIFFERENCE between two reads rather than an absolute figure —
            // these tests share a database and are deliberately not transactional.
            LocalDate early = LocalDate.of(2026, 8, 10);
            LocalDate midpoint = LocalDate.of(2026, 8, 15);
            LocalDate late = LocalDate.of(2026, 8, 20);
            AccountBalance before = journal.balanceOf(cash(), late);

            postCashSale(early, "JournalIT — balance early", "11.00");
            postCashSale(late, "JournalIT — balance late", "22.00");

            assertThat(journal.balanceOf(cash(), late).debits())
                    .isEqualTo(before.debits().plus(Money.ofEur("33.00")));
            // asOf is inclusive and cumulative, so a date between the two sees only the first.
            assertThat(journal.balanceOf(cash(), midpoint).debits()
                    .minus(journal.balanceOf(cash(), early.minusDays(1)).debits()))
                    .isEqualTo(Money.ofEur("11.00"));
        }

        @Test
        @DisplayName("a keyed account can be asked for its balance without knowing its id")
        void balanceBySystemKey() {
            AccountBalance byKey = journal.balanceOf(
                    AccountSystemKey.OUTPUT_VAT, LocalDate.of(2026, 6, 30));
            assertThat(byKey.accountId())
                    .isEqualTo(account(AccountSystemKey.OUTPUT_VAT).id());
        }

        @Test
        @DisplayName("the trial balance balances, which is rule 6 restated for the whole ledger")
        void theTrialBalanceBalances() {
            postCashSale(LocalDate.of(2026, 6, 2), "JournalIT — trial balance", "13.00");

            TrialBalance trial = journal.trialBalance(LocalDate.of(2026, 12, 31));

            assertThat(trial.isEmpty()).isFalse();
            assertThat(trial.isBalanced()).isTrue();
            assertThat(trial.difference()).isEqualTo(Money.ofEur("0.00"));
            // Accounts nothing has posted to are omitted rather than listed as zero rows.
            assertThat(trial.balances()).allSatisfy(
                    balance -> assertThat(balance.hasNoActivity()).isFalse());
        }

        @Test
        @DisplayName("an account's ledger comes back oldest first, carrying its entry's context")
        void theAccountLedger() {
            LocalDate from = LocalDate.of(2026, 7, 1);
            LocalDate to = LocalDate.of(2026, 7, 31);
            postCashSale(LocalDate.of(2026, 7, 20), "JournalIT — ledger second", "2.00");
            postCashSale(LocalDate.of(2026, 7, 5), "JournalIT — ledger first", "1.00");

            List<JournalLineView> ledger = journal.linesOf(cash(), from, to).stream()
                    .filter(line -> line.entryDescription().startsWith("JournalIT — ledger"))
                    .toList();

            assertThat(ledger).extracting(JournalLineView::entryDescription)
                    .containsExactly("JournalIT — ledger first", "JournalIT — ledger second");
            // Ordered by accounting date, not by when it was typed in: the backdated entry sorts first.
            assertThat(ledger.getFirst().entryDate()).isEqualTo(LocalDate.of(2026, 7, 5));
            assertThat(ledger.getFirst().debitPositiveEffect()).isEqualTo(Money.ofEur("1.00"));
        }

        @Test
        @DisplayName("a sub-ledger reconciles against its control account by construction")
        void subLedgerAndControlAccountAgree() {
            // This is what a control account is for, and the two figures come from the same rows read
            // two ways — so they cannot disagree unless a line lost its reference, which the trigger
            // prevents.
            LocalDate date = LocalDate.of(2026, 8, 1);
            long customerId = newCustomerId("JournalIT — reconciling customer");
            SubLedgerRef ref = SubLedgerRef.customer(customerId);

            journal.post(NewJournalEntry.of(date, "JournalIT — invoice to reconcile",
                    JournalSource.SALES_INVOICE, List.of(
                            NewJournalLine.debit(
                                            account(AccountSystemKey.ACCOUNTS_RECEIVABLE).id(),
                                            Money.ofEur("310.00"))
                                    .forSubLedger(ref),
                            NewJournalLine.credit(sales(), Money.ofEur("310.00")))));
            journal.post(NewJournalEntry.of(date, "JournalIT — part payment",
                    JournalSource.RECEIPT, List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("110.00")),
                            NewJournalLine.credit(
                                            account(AccountSystemKey.ACCOUNTS_RECEIVABLE).id(),
                                            Money.ofEur("110.00"))
                                    .forSubLedger(ref))));

            assertThat(journal.subLedgerBalanceOf(ref, date)).isEqualTo(Money.ofEur("200.00"));
            assertThat(journal.linesFor(ref)).hasSize(2);
        }

        @Test
        @DisplayName("a backwards date range is refused rather than answered with nothing")
        void backwardsRangesAreRefused() {
            assertThatThrownBy(() -> journal.entriesBetween(
                    LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("runs backwards");
        }

        @Test
        @DisplayName("entries are listed by accounting date, and unknown ids are simply absent")
        void listingEntries() {
            LocalDate date = LocalDate.of(2026, 10, 5);
            JournalEntryView posted = postCashSale(date, "JournalIT — listed", "9.00");

            assertThat(journal.entriesBetween(date, date))
                    .extracting(JournalEntryView::id)
                    .contains(posted.id());
            assertThat(journal.findEntries(List.of(posted.id(), 999_000_333L)))
                    .extracting(JournalEntryView::id)
                    .containsExactly(posted.id());
            assertThat(journal.findEntries(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Q14 — VAT posting")
    class Vat {

        @Test
        @DisplayName("output and input VAT come back separately and are never netted")
        void outputAndInputAreKeptApart() {
            // A single day rather than the whole month: every test in this class posts VAT, and a
            // month-wide window would aggregate all of them into one answer.
            LocalDate date = LocalDate.of(2026, 11, 3);
            long rate = standardRateVatClassId();

            journal.postManualEntry(date, "JournalIT — VAT sale", List.of(
                    NewJournalLine.debit(cash(), Money.ofEur("124.00")),
                    NewJournalLine.credit(sales(), Money.ofEur("100.00")),
                    NewJournalLine.credit(account(AccountSystemKey.OUTPUT_VAT).id(),
                                    Money.ofEur("24.00"))
                            .withVat(VatDimension.of(rate, Money.ofEur("100.00")))));
            journal.postManualEntry(date, "JournalIT — VAT purchase", List.of(
                    NewJournalLine.debit(byName("Other general expenses").id(),
                            Money.ofEur("50.00")),
                    NewJournalLine.debit(account(AccountSystemKey.INPUT_VAT).id(),
                                    Money.ofEur("12.00"))
                            .withVat(VatDimension.of(rate, Money.ofEur("50.00"))),
                    NewJournalLine.credit(cash(), Money.ofEur("62.00"))));

            List<VatTotal> vat = journal.vatTotals(date, date);

            assertThat(vat).filteredOn(total -> total.direction() == VatDirection.OUTPUT)
                    .singleElement()
                    .satisfies(total -> {
                        assertThat(total.vatAmount()).isEqualTo(Money.ofEur("24.00"));
                        assertThat(total.taxableBase()).isEqualTo(Money.ofEur("100.00"));
                        assertThat(total.vatClassCode()).isEqualTo("1410");
                    });
            assertThat(vat).filteredOn(total -> total.direction() == VatDirection.INPUT)
                    .singleElement()
                    .satisfies(total ->
                            assertThat(total.vatAmount()).isEqualTo(Money.ofEur("12.00")));
        }

        @Test
        @DisplayName("two rates on one invoice post as two lines and report as two figures")
        void ratesAreSummedSeparately() {
            // Q14's "per line, summed by rate". The whole reason a journal line carries its VAT class:
            // without it these two lines would be indistinguishable amounts against one account, and
            // summing by rate would buy nothing over posting a single total.
            LocalDate date = LocalDate.of(2026, 11, 10);

            journal.postManualEntry(date, "JournalIT — mixed rates", List.of(
                    NewJournalLine.debit(cash(), Money.ofEur("237.00")),
                    NewJournalLine.credit(sales(), Money.ofEur("200.00")),
                    NewJournalLine.credit(account(AccountSystemKey.OUTPUT_VAT).id(),
                                    Money.ofEur("24.00"))
                            .withVat(VatDimension.of(
                                    standardRateVatClassId(), Money.ofEur("100.00"))),
                    NewJournalLine.credit(account(AccountSystemKey.OUTPUT_VAT).id(),
                                    Money.ofEur("13.00"))
                            .withVat(VatDimension.of(
                                    reducedRateVatClassId(), Money.ofEur("100.00")))));

            assertThat(journal.vatTotals(date, date))
                    .filteredOn(total -> total.direction() == VatDirection.OUTPUT)
                    .extracting(VatTotal::vatClassCode, VatTotal::vatAmount)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple("1410", Money.ofEur("24.00")),
                            org.assertj.core.groups.Tuple.tuple("1131", Money.ofEur("13.00")));
        }

        @Test
        @DisplayName("reverse charge self-assesses both sides and nets to zero in cash")
        void reverseChargeNetsToZero() {
            // Q14's own path for INTRA_EU_B2B. Structurally it needs nothing new: two lines in one
            // ordinary entry, one to each VAT account, for the same base. It still has to be declared
            // as two real figures, which is why they come back as two rows rather than cancelling.
            LocalDate date = LocalDate.of(2026, 11, 17);
            long rate = standardRateVatClassId();
            VatDimension dimension = VatDimension.of(rate, Money.ofEur("400.00"));

            journal.postManualEntry(date, "JournalIT — intra-EU acquisition, reverse charge",
                    List.of(
                            NewJournalLine.debit(account(AccountSystemKey.INPUT_VAT).id(),
                                    Money.ofEur("96.00")).withVat(dimension),
                            NewJournalLine.credit(account(AccountSystemKey.OUTPUT_VAT).id(),
                                    Money.ofEur("96.00")).withVat(dimension)));

            List<VatTotal> vat = journal.vatTotals(date, date);
            assertThat(vat).hasSize(2);
            assertThat(vat).extracting(VatTotal::vatAmount)
                    .containsExactly(Money.ofEur("96.00"), Money.ofEur("96.00"));
            // Net effect on the VAT position is nil — the two sides cancel — while both figures remain
            // separately reportable, which is exactly what netting them into one account would destroy.
            assertThat(vat.stream()
                    .map(total -> total.direction() == VatDirection.OUTPUT
                            ? total.vatAmount() : total.vatAmount().negated())
                    .reduce(Money.zero(Money.EUR), Money::plus))
                    .isEqualTo(Money.ofEur("0.00"));
        }

        @Test
        @DisplayName("a credit note reduces output VAT rather than adding a second figure")
        void debitsToTheOutputAccountNetDown() {
            LocalDate date = LocalDate.of(2026, 11, 24);
            long rate = standardRateVatClassId();

            journal.postManualEntry(date, "JournalIT — sale to be credited", List.of(
                    NewJournalLine.debit(cash(), Money.ofEur("124.00")),
                    NewJournalLine.credit(sales(), Money.ofEur("100.00")),
                    NewJournalLine.credit(account(AccountSystemKey.OUTPUT_VAT).id(),
                                    Money.ofEur("24.00"))
                            .withVat(VatDimension.of(rate, Money.ofEur("100.00")))));
            journal.postManualEntry(date, "JournalIT — credit note", List.of(
                    NewJournalLine.credit(cash(), Money.ofEur("31.00")),
                    NewJournalLine.debit(byName("Sales returns — Store & Phone").id(),
                            Money.ofEur("25.00")),
                    NewJournalLine.debit(account(AccountSystemKey.OUTPUT_VAT).id(),
                                    Money.ofEur("6.00"))
                            .withVat(VatDimension.of(rate, Money.ofEur("25.00")))));

            assertThat(journal.vatTotals(date, date))
                    .filteredOn(total -> total.direction() == VatDirection.OUTPUT)
                    .singleElement()
                    .satisfies(total -> {
                        assertThat(total.vatAmount()).isEqualTo(Money.ofEur("18.00"));
                        assertThat(total.taxableBase()).isEqualTo(Money.ofEur("75.00"));
                    });
        }

        @Test
        @DisplayName("an exempt line posts no VAT line at all")
        void exemptLinesPostNoVat() {
            // Q14: an exempt line posts the net amount only, and its exemption reason lives on the
            // invoice line because there is no VAT line to hang it on. Asserted so that "no VAT line"
            // reads as the design rather than as an omission.
            LocalDate date = LocalDate.of(2026, 11, 26);

            JournalEntryView exempt = journal.postManualEntry(date,
                    "JournalIT — exempt supply", List.of(
                            NewJournalLine.debit(cash(), Money.ofEur("500.00")),
                            NewJournalLine.credit(sales(), Money.ofEur("500.00"))));

            assertThat(exempt.lines()).allSatisfy(
                    line -> assertThat(line.vatIfAny()).isEmpty());
            assertThat(journal.vatTotals(date, date)).isEmpty();
        }
    }
}
