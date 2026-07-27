package gr.novotrade.novocore.core.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Nested
    @DisplayName("scale normalisation")
    class ScaleNormalisation {

        @ParameterizedTest
        @CsvSource({"5, 5.00", "5.1, 5.10", "5.10, 5.10", "0, 0.00", "-3.4, -3.40"})
        @DisplayName("widens to exactly two decimals without changing the value")
        void normalisesToTwoDecimals(String input, String expected) {
            assertThat(Money.ofEur(input).amount().toPlainString()).isEqualTo(expected);
        }

        @Test
        @DisplayName("amounts equal in value are equal regardless of how they were written")
        void equalityIgnoresIncidentalPrecision() {
            // The whole reason the scale is fixed. BigDecimal.equals("1.10", "1.1") is false,
            // so without normalisation two genuinely identical amounts would fail to match
            // during reconciliation or open item matching.
            assertThat(Money.ofEur("1.1")).isEqualTo(Money.ofEur("1.10"));
            assertThat(Money.ofEur("1.1")).hasSameHashCodeAs(Money.ofEur("1.10"));
            assertThat(Money.of(new BigDecimal("2"), Money.EUR))
                    .isEqualTo(Money.of(new BigDecimal("2.00"), Money.EUR));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.001", "1.005", "-2.999", "0.12345"})
        @DisplayName("rejects extra precision rather than silently discarding it")
        void rejectsMorePreciseValues(String tooPrecise) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Money.ofEur(tooPrecise))
                    .withMessageContaining("decimal places")
                    .withMessageContaining("Rounding");
        }

        @Test
        @DisplayName("the same value is accepted once rounding is stated explicitly")
        void roundingIsAvailableWhenAsked() {
            assertThat(Money.rounded(new BigDecimal("1.005"), Money.EUR, RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("1.01"));
        }
    }

    @Nested
    @DisplayName("rounding modes")
    class Rounding {

        @Test
        @DisplayName("HALF_UP and HALF_EVEN differ on a midpoint, so the mode is never implicit")
        void modesDifferOnMidpoints() {
            BigDecimal midpoint = new BigDecimal("2.345");
            assertThat(Money.rounded(midpoint, Money.EUR, RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("2.35"));
            assertThat(Money.rounded(midpoint, Money.EUR, RoundingMode.HALF_EVEN))
                    .isEqualTo(Money.ofEur("2.34"));
        }

        @Test
        @DisplayName("HALF_UP rounds a negative midpoint away from zero")
        void negativeMidpointRoundsAwayFromZero() {
            // Worth pinning: it is easy to assume -2.345 rounds to -2.34. It does not under
            // HALF_UP, and a VAT or allocation calculation on a credit note depends on which.
            assertThat(Money.rounded(new BigDecimal("-2.345"), Money.EUR, RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("-2.35"));
        }

        @Test
        @DisplayName("UNNECESSARY throws instead of rounding, for callers that require exactness")
        void unnecessaryThrowsWhenRoundingWouldBeNeeded() {
            assertThatExceptionOfType(ArithmeticException.class).isThrownBy(
                    () -> Money.rounded(new BigDecimal("1.001"), Money.EUR,
                            RoundingMode.UNNECESSARY));
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("adds and subtracts exactly, with no binary floating point drift")
        void addsAndSubtractsExactly() {
            // The canonical float failure: 0.1 + 0.2 != 0.3 in binary floating point. Summing
            // a hundred 0.01 amounts is the same failure at invoice-line scale.
            Money total = Money.ofEur("0.00");
            for (int i = 0; i < 100; i++) {
                total = total.plus(Money.ofEur("0.01"));
            }
            assertThat(total).isEqualTo(Money.ofEur("1.00"));

            assertThat(Money.ofEur("0.10").plus(Money.ofEur("0.20")))
                    .isEqualTo(Money.ofEur("0.30"));
            assertThat(Money.ofEur("10.00").minus(Money.ofEur("9.99")))
                    .isEqualTo(Money.ofEur("0.01"));
        }

        @Test
        @DisplayName("negation and absolute value")
        void negationAndAbsoluteValue() {
            assertThat(Money.ofEur("5.00").negated()).isEqualTo(Money.ofEur("-5.00"));
            assertThat(Money.ofEur("-5.00").abs()).isEqualTo(Money.ofEur("5.00"));
            assertThat(Money.ofEur("0.00").negated()).isEqualTo(Money.ofEur("0.00"));
        }

        @Test
        @DisplayName("timesExact succeeds when the product needs no rounding")
        void timesExactOnCleanProduct() {
            assertThat(Money.ofEur("2.50").timesExact(new BigDecimal("4")))
                    .isEqualTo(Money.ofEur("10.00"));
        }

        @Test
        @DisplayName("timesExact throws rather than absorb an unexpected fraction of a cent")
        void timesExactRefusesToRound() {
            assertThatExceptionOfType(ArithmeticException.class)
                    .isThrownBy(() -> Money.ofEur("0.07").timesExact(new BigDecimal("1.5")))
                    .withMessageContaining("roundingMode");
        }

        @Test
        @DisplayName("multiplyExactly keeps full precision so a sum can be rounded just once")
        void multiplyExactlyKeepsPrecision() {
            // Rounding each line and then summing gives a different answer from summing and
            // rounding once. Brief §6 compares an independently computed total against the
            // source document, so which one we do matters.
            BigDecimal exact = Money.ofEur("0.07").multiplyExactly(new BigDecimal("1.5"));
            assertThat(exact).isEqualByComparingTo("0.105");
        }
    }

    @Nested
    @DisplayName("currency")
    class CurrencyRules {

        @Test
        @DisplayName("mixing currencies throws rather than converting or picking one")
        void mixingCurrenciesThrows() {
            Money euros = Money.ofEur("10.00");
            Money dollars = Money.of("10.00", USD);

            assertThatIllegalArgumentException().isThrownBy(() -> euros.plus(dollars))
                    .withMessageContaining("does not convert");
            assertThatIllegalArgumentException().isThrownBy(() -> euros.minus(dollars));
            assertThatIllegalArgumentException().isThrownBy(() -> euros.compareTo(dollars));
        }

        @Test
        @DisplayName("same amount in different currencies is not equal")
        void sameAmountDifferentCurrencyIsNotEqual() {
            assertThat(Money.ofEur("10.00")).isNotEqualTo(Money.of("10.00", USD));
        }

        @Test
        @DisplayName("currency is retained, and EUR is recognised")
        void currencyIsRetained() {
            assertThat(Money.ofEur("1.00").isEur()).isTrue();
            assertThat(Money.of("1.00", USD).isEur()).isFalse();
            assertThat(Money.of("1.00", USD).currency()).isEqualTo(USD);
        }
    }

    @Nested
    @DisplayName("comparison and sign")
    class ComparisonAndSign {

        @Test
        @DisplayName("orders by amount")
        void ordersByAmount() {
            assertThat(Money.ofEur("1.00")).isLessThan(Money.ofEur("2.00"));
            assertThat(Money.ofEur("-1.00")).isLessThan(Money.ofEur("0.00"));
            assertThat(Money.ofEur("2.00")).isEqualByComparingTo(Money.ofEur("2.00"));
        }

        @Test
        @DisplayName("sign predicates, including that zero is neither positive nor negative")
        void signPredicates() {
            assertThat(Money.ofEur("0.00").isZero()).isTrue();
            assertThat(Money.ofEur("0.00").isPositive()).isFalse();
            assertThat(Money.ofEur("0.00").isNegative()).isFalse();
            assertThat(Money.ofEur("-0.01").isNegative()).isTrue();
            assertThat(Money.ofEur("0.01").isPositive()).isTrue();
            assertThat(Money.ofEur("-2.00").signum()).isEqualTo(-1);
        }

        @Test
        @DisplayName("negative zero is treated as zero")
        void negativeZeroIsZero() {
            assertThat(Money.ofEur("-0.00").isZero()).isTrue();
            assertThat(Money.ofEur("-0.00")).isEqualTo(Money.ofEur("0.00"));
        }
    }

    @Nested
    @DisplayName("construction guards")
    class ConstructionGuards {

        @Test
        @DisplayName("null amount or currency is rejected")
        void nullsRejected() {
            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> Money.of((BigDecimal) null, Money.EUR));
            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> Money.of(BigDecimal.ONE, null));
        }

        @Test
        @DisplayName("a non-numeric string is rejected loudly")
        void nonNumericStringRejected() {
            assertThatExceptionOfType(NumberFormatException.class)
                    .isThrownBy(() -> Money.ofEur("not a number"));
        }

        @Test
        @DisplayName("zero is scale-normalised like any other amount")
        void zeroIsNormalised() {
            assertThat(Money.zero(Money.EUR).amount().toPlainString()).isEqualTo("0.00");
            assertThat(Money.zero(Money.EUR).isZero()).isTrue();
        }

        @Test
        @DisplayName("toString shows the plain amount and currency code")
        void toStringIsReadable() {
            // Plain string, never scientific notation: 0.00000001 formatted as 1E-8 in a log or
            // an exported report is unreadable at best and misparsed at worst.
            assertThat(Money.ofEur("1234.50")).hasToString("1234.50 EUR");
        }
    }
}
