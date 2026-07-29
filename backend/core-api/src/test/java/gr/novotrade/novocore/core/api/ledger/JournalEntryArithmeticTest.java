package gr.novotrade.novocore.core.api.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Rate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The pure arithmetic and shape rules of the ledger's request and view types.
 *
 * <p>Everything here runs with no database, which is the point: the sign convention, the balance sum
 * and the normal-side derivation are the parts that would be tedious to exercise exhaustively through
 * a service and are trivial to get subtly wrong.
 */
class JournalEntryArithmeticTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 14);
    private static final Currency USD = Currency.getInstance("USD");

    private static NewJournalLine debit(String amount) {
        return NewJournalLine.debit(1L, Money.ofEur(amount));
    }

    private static NewJournalLine credit(String amount) {
        return NewJournalLine.credit(2L, Money.ofEur(amount));
    }

    private static NewJournalEntry entry(NewJournalLine... lines) {
        return NewJournalEntry.of(
                MARCH, "Test entry", JournalSource.MANUAL_JOURNAL_ENTRY, List.of(lines));
    }

    @Nested
    @DisplayName("a line is a side and a positive amount")
    class Lines {

        @Test
        @DisplayName("zero is refused as firmly as negative")
        void zeroAndNegativeAreBothRefused() {
            // Zero matters more than negative here: a zero line still BALANCES, so an entry padded
            // with them would satisfy rule 6 while meaning nothing.
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> NewJournalLine.debit(1L, Money.ofEur("0.00")))
                    .withMessageContaining("states nothing");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> NewJournalLine.debit(1L, Money.ofEur("-5.00")))
                    .withMessageContaining("said by its side");
        }

        @Test
        @DisplayName("mirroring flips the side and changes nothing else")
        void mirroringOnlyFlipsTheSide() {
            NewJournalLine original = NewJournalLine.debit(7L, Money.ofEur("12.34"))
                    .describedAs("Coffee")
                    .forSubLedger(
                            gr.novotrade.novocore.core.api.shared.SubLedgerRef.inventoryLot(9L));

            NewJournalLine mirrored = original.mirrored();

            assertThat(mirrored.side()).isEqualTo(BalanceSide.CREDIT);
            assertThat(mirrored.accountId()).isEqualTo(original.accountId());
            assertThat(mirrored.amount()).isEqualTo(original.amount());
            assertThat(mirrored.subLedgerRef()).isEqualTo(original.subLedgerRef());
            assertThat(mirrored.description()).isEqualTo(original.description());
            // And mirroring twice is the identity, which is what makes reversing a reversal coherent.
            assertThat(mirrored.mirrored()).isEqualTo(original);
        }

        @Test
        @DisplayName("a blank line description becomes absent rather than empty")
        void blankDescriptionsAreNormalised() {
            assertThat(debit("1.00").describedAs("   ").description()).isNull();
        }
    }

    @Nested
    @DisplayName("an entry's totals")
    class Totals {

        @Test
        @DisplayName("debits and credits are summed separately, never netted")
        void sidesAreSummedSeparately() {
            NewJournalEntry balanced = entry(
                    debit("100.00"), debit("24.00"), credit("124.00"));

            assertThat(balanced.totalDebits()).isEqualTo(Money.ofEur("124.00"));
            assertThat(balanced.totalCredits()).isEqualTo(Money.ofEur("124.00"));
            assertThat(balanced.balances()).isTrue();
        }

        @Test
        @DisplayName("a cent out is out")
        void aCentOutDoesNotBalance() {
            assertThat(entry(debit("100.00"), credit("99.99")).balances()).isFalse();
        }

        @Test
        @DisplayName("an entry needs at least two lines")
        void oneLineIsRefused() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> entry(debit("10.00")))
                    .withMessageContaining("cannot balance against anything");
        }

        @Test
        @DisplayName("a blank description is refused")
        void aDescriptionIsRequired() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> NewJournalEntry.of(MARCH, "  ",
                            JournalSource.MANUAL_JOURNAL_ENTRY,
                            List.of(debit("1.00"), credit("1.00"))))
                    .withMessageContaining("cannot reconstruct");
        }

        @Test
        @DisplayName("summing across two currencies throws rather than picking one")
        void mixedCurrenciesThrowOnSummation() {
            NewJournalEntry mixed = entry(
                    debit("10.00"),
                    NewJournalLine.debit(3L, Money.of("10.00", USD)),
                    credit("20.00"));

            // ADR 0005. The service refuses this with a named message before it gets here; this proves
            // the type cannot be tricked into producing a total in whichever currency came first.
            assertThatIllegalArgumentException().isThrownBy(mixed::totalDebits);
        }
    }

    @Nested
    @DisplayName("an account balance presents itself on the right side")
    class Balances {

        private AccountBalance balance(AccountType type, String debits, String credits) {
            return new AccountBalance(1L, "Test", type,
                    Money.ofEur(debits), Money.ofEur(credits), MARCH);
        }

        @Test
        @DisplayName("a debit-normal account reads debits minus credits")
        void debitNormal() {
            AccountBalance cash = balance(AccountType.ASSET, "500.00", "120.00");

            assertThat(cash.onNormalSide()).isEqualTo(Money.ofEur("380.00"));
            assertThat(cash.net()).isEqualTo(Money.ofEur("380.00"));
            assertThat(cash.isOnItsNormalSide()).isTrue();
        }

        @Test
        @DisplayName("a credit-normal account reads credits minus debits")
        void creditNormal() {
            AccountBalance payable = balance(AccountType.LIABILITY, "120.00", "500.00");

            assertThat(payable.onNormalSide()).isEqualTo(Money.ofEur("380.00"));
            // net() stays debit-positive whatever the account is, which is what makes it summable.
            assertThat(payable.net()).isEqualTo(Money.ofEur("-380.00"));
            assertThat(payable.isOnItsNormalSide()).isTrue();
        }

        @Test
        @DisplayName("a contra-asset account is credit-normal, which is the whole reason it exists")
        void contraAssetIsCreditNormal() {
            // Accumulated depreciation. Typed as ASSET it would derive debit-normal and report fixed
            // assets at roughly double carrying value — the argument that produced the type.
            AccountBalance accumulated = balance(AccountType.CONTRA_ASSET, "0.00", "1200.00");

            assertThat(accumulated.normalBalance()).isEqualTo(BalanceSide.CREDIT);
            assertThat(accumulated.onNormalSide()).isEqualTo(Money.ofEur("1200.00"));
        }

        @Test
        @DisplayName("a balance on the wrong side reads negative rather than being made positive")
        void theWrongSideIsVisible() {
            // A credit balance on a bank account is an overdraft and a debit balance on Accounts
            // payable is an overpaid supplier. Both are real, and an absolute value would hide them.
            AccountBalance overdrawn = balance(AccountType.ASSET, "100.00", "250.00");

            assertThat(overdrawn.onNormalSide()).isEqualTo(Money.ofEur("-150.00"));
            assertThat(overdrawn.isOnItsNormalSide()).isFalse();
        }

        @Test
        @DisplayName("no activity is distinguished from a balance of zero")
        void noActivityIsNotZero() {
            assertThat(balance(AccountType.EXPENSE, "0.00", "0.00").hasNoActivity()).isTrue();
            // Equal, large movements net to zero and are not the same situation at all.
            AccountBalance busy = balance(AccountType.EXPENSE, "9000.00", "9000.00");
            assertThat(busy.hasNoActivity()).isFalse();
            assertThat(busy.isZero()).isTrue();
        }

        @Test
        @DisplayName("negative totals are refused, because both sides are sums of positive amounts")
        void negativeTotalsAreAProjectionBug() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> balance(AccountType.ASSET, "-1.00", "0.00"))
                    .withMessageContaining("sides were mixed up");
        }
    }

    @Nested
    @DisplayName("a trial balance restates rule 6 for the whole ledger")
    class Trial {

        @Test
        @DisplayName("it balances when every entry did")
        void itBalances() {
            TrialBalance trial = new TrialBalance(MARCH, Money.EUR, List.of(
                    new AccountBalance(1L, "Cash", AccountType.ASSET,
                            Money.ofEur("124.00"), Money.zero(Money.EUR), MARCH),
                    new AccountBalance(2L, "Sales", AccountType.INCOME,
                            Money.zero(Money.EUR), Money.ofEur("100.00"), MARCH),
                    new AccountBalance(3L, "Output VAT", AccountType.LIABILITY,
                            Money.zero(Money.EUR), Money.ofEur("24.00"), MARCH)));

            assertThat(trial.totalDebits()).isEqualTo(Money.ofEur("124.00"));
            assertThat(trial.totalCredits()).isEqualTo(Money.ofEur("124.00"));
            assertThat(trial.isBalanced()).isTrue();
            assertThat(trial.difference()).isEqualTo(Money.ofEur("0.00"));
        }

        @Test
        @DisplayName("an empty ledger balances trivially and says so")
        void emptyBalances() {
            TrialBalance empty = new TrialBalance(MARCH, Money.EUR, List.of());

            assertThat(empty.isEmpty()).isTrue();
            assertThat(empty.isBalanced()).isTrue();
        }
    }

    @Nested
    @DisplayName("the VAT dimension")
    class Vat {

        @Test
        @DisplayName("a zero or negative taxable base is refused")
        void baseMustBePositive() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> VatDimension.of(1L, Money.ofEur("0.00")))
                    .withMessageContaining("no VAT line at all");
        }

        @Test
        @DisplayName("the divergence from one rounding of the whole base is visible")
        void perLineRoundingIsVisible() {
            // Q14 computes VAT per line and sums by rate, so the posted figure is a sum of per-line
            // roundings. Two lines of 0.05 at 24% give 0.01 each and 0.02 posted, while one rounding
            // of 0.10 gives 0.02 as well — so this example diverges by nothing, and that is the point
            // of having the method: a gap of euros rather than cents means a wrong rate, not rounding.
            VatTotal total = new VatTotal(VatDirection.OUTPUT, 1L, "1410",
                    Rate.of("24.000000"), Money.ofEur("0.10"), Money.ofEur("0.02"));

            assertThat(total.vatImpliedByTheRate()).isEqualTo(Money.ofEur("0.02"));
            assertThat(total.roundingDivergence()).isEqualTo(Money.ofEur("0.00"));

            VatTotal drifted = new VatTotal(VatDirection.OUTPUT, 1L, "1410",
                    Rate.of("24.000000"), Money.ofEur("100.00"), Money.ofEur("24.03"));
            assertThat(drifted.roundingDivergence()).isEqualTo(Money.ofEur("0.03"));
        }

        @Test
        @DisplayName("a direction knows its account, and only those two accounts are VAT accounts")
        void directionsMapToAccounts() {
            assertThat(VatDirection.OUTPUT.account())
                    .isEqualTo(gr.novotrade.novocore.core.api.account.AccountSystemKey.OUTPUT_VAT);
            assertThat(VatDirection.INPUT.account())
                    .isEqualTo(gr.novotrade.novocore.core.api.account.AccountSystemKey.INPUT_VAT);
            assertThat(VatDirection.isVatAccount(
                    gr.novotrade.novocore.core.api.account.AccountSystemKey.INVENTORY)).isFalse();
            assertThat(VatDirection.isVatAccount(null)).isFalse();
        }
    }
}
