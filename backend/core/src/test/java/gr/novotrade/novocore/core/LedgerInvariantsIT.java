package gr.novotrade.novocore.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import gr.novotrade.novocore.core.testsupport.LedgerInvariants;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <strong>Proof that {@link LedgerInvariants} actually fails.</strong>
 *
 * <p>Written for the same reason {@code PropertyTest} exists, and the same reason the ArchUnit
 * rules and {@code SchemaConventionsIT} were each proven against a deleted probe: <em>a checker
 * that is silently broken is worse than no checker</em>, because it converts an unexamined risk
 * into a green tick. Step 15 is about to rest a great deal on this class — every claim that the
 * HTTP-driven scenario produced correct books is a claim made by these methods — so the methods
 * themselves need a test.
 *
 * <p>Two of the three probes below cover invariants that no existing test could have exercised:
 *
 * <ul>
 *   <li>{@code everySubLedgerReferenceIsLive} is <strong>new in step 15a</strong>. V15's trigger
 *       refuses a dangling reference <em>on write</em>, which says nothing about a row deleted
 *       afterwards — and since the reference is polymorphic there is no foreign key to stop that.
 *       So the state this invariant exists to catch is unreachable through any service, and the
 *       probe has to manufacture it in SQL.
 *   <li>{@code theLedgerIsNotTrivial} is the guard against every other invariant passing vacuously.
 *       If <em>it</em> can never fail, nothing is guarding anything.
 * </ul>
 *
 * <p>The orphaning probe runs inside a transaction it rolls back, so the corrupted state exists
 * only for the duration of the assertion. That matters here specifically: this class shares its
 * database with every other core integration test, and a genuinely orphaned journal line left
 * behind would fail {@code WholeScenarioIT}'s sweep in a way that looks like a real defect.
 */
class LedgerInvariantsIT extends AbstractCoreIntegrationTest {

    private static final LocalDate WHEN = LocalDate.of(2026, 5, 4);

    @Autowired private ApplicationContext applicationContext;
    @Autowired private ChartOfAccountsService chart;
    @Autowired private CustomerService customers;
    @Autowired private JournalService journal;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private LedgerInvariants invariants;

    @BeforeEach
    void resolveInvariants() {
        invariants = LedgerInvariants.from(applicationContext);
    }

    @Test
    @DisplayName("the set is non-empty and every invariant is distinctly named")
    void theSetIsWellFormed() {
        // A caller reports these by name, so two invariants sharing one would silently report a
        // single result for two checks. And an empty list would make every caller pass trivially.
        List<String> names = invariants.all(WHEN, WHEN).stream()
                .map(LedgerInvariants.Invariant::name)
                .toList();

        assertThat(names).isNotEmpty();
        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allSatisfy(name -> assertThat(name).isNotBlank());
    }

    @Test
    @DisplayName("a sub-ledger reference orphaned after the fact is caught, and names the row")
    void anOrphanedSubLedgerReferenceIsCaught() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            long customerId = customers.create(
                    NewCustomer.retail("INV-PROBE Customer", null, null)).id();
            long receivable = chart.requireAccount(AccountSystemKey.ACCOUNTS_RECEIVABLE).id();
            long rounding = chart.requireAccount(AccountSystemKey.ROUNDING_DIFFERENCES).id();

            journal.postManualEntry(WHEN, "Probe for the orphan sweep", List.of(
                    NewJournalLine.debit(receivable, Money.ofEur("10.00"))
                            .forSubLedger(SubLedgerRef.of(SubLedgerType.CUSTOMER, customerId)),
                    NewJournalLine.credit(rounding, Money.ofEur("10.00"))));

            // Sound so far — the reference points at a customer that exists.
            invariants.everySubLedgerReferenceIsLive();

            // Now take the customer away underneath the line. Nothing prevents this: the reference
            // is polymorphic, so there is no foreign key, and V15's trigger only fires on write.
            jdbc.update("DELETE FROM customer WHERE id = ?", customerId);

            assertThatThrownBy(invariants::everySubLedgerReferenceIsLive)
                    .as("the invariant must notice, and say which line and which row")
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("CUSTOMER#" + customerId)
                    .hasMessageContaining("absent from customer");

            // Rolled back: the corrupted state was only ever needed for the assertion above, and
            // leaving it would break every other test that sweeps this database.
            status.setRollbackOnly();
        });

        // And the database is genuinely back where it started.
        invariants.everySubLedgerReferenceIsLive();
    }

    @Test
    @DisplayName("the anti-vacuous guard is itself capable of failing")
    void theTrivialityGuardCanFail() {
        // theLedgerIsNotTrivial is what stops every other invariant here passing on an empty
        // database, so each of its three thresholds has to be capable of refusing.
        //
        // The thresholds are read from the database rather than written as literals. This class
        // shares a context with the other core integration tests but not with WholeScenarioIT,
        // which takes its own container, so how much is in this ledger depends on execution order
        // — and a probe that only fails on an empty database would quietly stop proving anything
        // the day something else posted first.
        long entries = jdbc.queryForObject("SELECT count(*) FROM journal_entry", Long.class);
        long lines = jdbc.queryForObject("SELECT count(*) FROM journal_line", Long.class);

        // Asking for strictly more than there is, one threshold at a time, with the other two set
        // below what is there so the failure is attributable to the one under test.
        assertThatThrownBy(() -> invariants.theLedgerIsNotTrivial(WHEN, entries, -1L, -1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("journal entries produced by the scenario");

        assertThatThrownBy(() -> invariants.theLedgerIsNotTrivial(WHEN, entries - 1, lines, -1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("journal lines produced by the scenario");

        assertThatThrownBy(() ->
                invariants.theLedgerIsNotTrivial(WHEN, entries - 1, lines - 1, Integer.MAX_VALUE - 1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("accounts with activity");
    }
}
