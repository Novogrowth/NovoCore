package gr.novotrade.novocore.core.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.banking.BankTransferService;
import gr.novotrade.novocore.core.api.banking.BankTransferView;
import gr.novotrade.novocore.core.api.banking.InvalidBankTransferException;
import gr.novotrade.novocore.core.api.banking.NewBankTransfer;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Transfers between our own accounts — two lines and nothing more.
 *
 * <p>Brief §4 drops Manager's "Inter Account Transfers" account (which Manager had under Equity, the
 * error the brief corrects), so what is being defended here is that a transfer needs no third account
 * to pass through, and that both ends have to be accounts money can actually sit in.
 */
class BankTransferIT extends AbstractCoreIntegrationTest {

    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 12);

    @Autowired
    private BankTransferService transfers;

    @Autowired
    private ChartOfAccountsService chartOfAccounts;

    @Autowired
    private JournalService journal;

    @Autowired
    private JdbcTemplate jdbc;

    private AccountView account(String name) {
        return chartOfAccounts.allAccounts().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("debit the destination, credit the source, and nothing in between")
    void aTransferIsTwoLines() {
        AccountView alpha = account("Alpha Bank");
        AccountView piraeus = account("Piraeus Bank");

        BankTransferView transfer = transfers.record(NewBankTransfer
                .of(alpha.id(), piraeus.id(), AUGUST, Money.ofEur("2500.00"))
                .referenced("SEPA-99"));

        JournalEntryView entry = journal.requireEntry(transfer.journalEntryId());
        assertThat(entry.source()).isEqualTo(JournalSource.BANK_TRANSFER);
        assertThat(entry.lines()).hasSize(2)
                .anySatisfy(line -> {
                    assertThat(line.accountId()).isEqualTo(piraeus.id());
                    assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                })
                .anySatisfy(line -> {
                    assertThat(line.accountId()).isEqualTo(alpha.id());
                    assertThat(line.side()).isEqualTo(BalanceSide.CREDIT);
                });

        assertThat(transfers.involving(alpha.id()))
                .extracting(BankTransferView::id).contains(transfer.id());
        assertThat(transfers.involving(piraeus.id()))
                .extracting(BankTransferView::id).contains(transfer.id());
    }

    @Test
    @DisplayName("editing one in place moves the ledger with it — Q13")
    void amendingATransfer() {
        AccountView alpha = account("Alpha Bank");
        AccountView nbg = account("National Bank of Greece");

        BankTransferView transfer = transfers.record(
                NewBankTransfer.of(alpha.id(), nbg.id(), AUGUST, Money.ofEur("1000.00")));

        BankTransferView amended = transfers.amend(transfer.id(), alpha.id(), nbg.id(), AUGUST,
                Money.ofEur("1200.00"), "corrected", null);

        assertThat(amended.amount()).isEqualTo(Money.ofEur("1200.00"));
        assertThat(journal.requireEntry(transfer.journalEntryId()).lines())
                .allSatisfy(line -> assertThat(line.amount()).isEqualTo(Money.ofEur("1200.00")));
    }

    @Test
    @DisplayName("it is the one document-shaped transaction the ledger can reverse by itself")
    void reversalGoesThroughTheLedger() {
        AccountView alpha = account("Alpha Bank");
        AccountView piraeus = account("Piraeus Bank");

        BankTransferView transfer = transfers.record(
                NewBankTransfer.of(alpha.id(), piraeus.id(), AUGUST, Money.ofEur("300.00")));

        transfers.reverse(transfer.id(), AUGUST, "never happened");

        assertThat(journal.requireEntry(transfer.journalEntryId()).reversedByEntryId()).isNotNull();
    }

    @Test
    @DisplayName("an account money cannot sit in is refused at both ends")
    void bothEndsMustHoldMoney() {
        AccountView alpha = account("Alpha Bank");
        long cogs = chartOfAccounts.requireAccount(AccountSystemKey.COST_OF_GOODS_SOLD).id();

        // A transfer into an expense account is not a transfer: it is a payment recorded as an
        // internal movement, which is how money leaves the business without appearing to.
        assertThatExceptionOfType(InvalidBankTransferException.class)
                .isThrownBy(() -> transfers.record(
                        NewBankTransfer.of(alpha.id(), cogs, AUGUST, Money.ofEur("10.00"))))
                .withMessageContaining("money cannot be transferred");
    }

    @Test
    @DisplayName("a cash movement at the legal limit is blocked")
    void cashLimitApplies() {
        AccountView alpha = account("Alpha Bank");
        long cash = chartOfAccounts.requireAccount(AccountSystemKey.CASH).id();

        assertThatExceptionOfType(InvalidBankTransferException.class)
                .isThrownBy(() -> transfers.record(
                        NewBankTransfer.of(alpha.id(), cash, AUGUST, Money.ofEur("500.00"))))
                .withMessageContaining("legal cash limit");
    }

    @Test
    @DisplayName("a transfer to itself is refused by the request and by the database")
    void noSelfTransfer() {
        AccountView alpha = account("Alpha Bank");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> NewBankTransfer.of(
                        alpha.id(), alpha.id(), AUGUST, Money.ofEur("10.00")))
                .withMessageContaining("cancel");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO bank_transfer (from_account_id, to_account_id, transfer_date, amount,
                    amount_currency, journal_entry_id)
                SELECT ?, ?, DATE '2026-08-12', 10, 'EUR', (SELECT max(id) FROM journal_entry)
                """, alpha.id(), alpha.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bank_transfer_accounts_differ");
    }
}
