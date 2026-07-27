package gr.novotrade.novocore.core.api.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.api.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Rate arithmetic and the guards on how a rate may be expressed. */
class VatClassViewTest {

    private static VatClassView rate(String ratePercent) {
        return new VatClassView(1L, "test", "Test rate", new BigDecimal(ratePercent), null, true);
    }

    @Nested
    @DisplayName("percent, never a fraction")
    class PercentNotFraction {

        @Test
        @DisplayName("a rate above 100 is refused")
        void rateAboveHundredIsRefused() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> rate("101"))
                    .withMessageContaining("percentage between 0 and 100");
        }

        @Test
        @DisplayName("a negative rate is refused")
        void negativeRateIsRefused() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> rate("-1"));
        }

        @Test
        @DisplayName("0.24 is accepted as 0.24%, and the message says why that is a trap")
        void aFractionIsAcceptedAsATinyPercentage() {
            // Honest about the limit: 0.24 is a *valid* percentage, so construction cannot reject
            // it. What protects against "0.24 meaning 24%" is that the rate is documented as a
            // percentage everywhere, the seed is stated in percent, and the exception message on
            // the out-of-range path names the factor-of-100 mistake explicitly. A caller who
            // writes 0.24 intending 24% gets a 0.24% invoice, which is why the convention is
            // stated in the column comment, the record, the entity and the service.
            VatClassView quarterOfAPercent = rate("0.24");

            assertThat(quarterOfAPercent.multiplier()).isEqualByComparingTo("0.0024");
            assertThat(quarterOfAPercent.vatOn(Money.ofEur("100.00"), RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("0.24"));
        }

        @Test
        @DisplayName("a rate more precise than six decimals is refused, not rounded")
        void overPreciseRateIsRefused() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> rate("24.0000001"))
                    .withMessageContaining("decimal places");
        }

        @Test
        @DisplayName("the rate is normalised so equality compares the rate, not its precision")
        void rateIsNormalised() {
            assertThat(rate("24")).isEqualTo(rate("24.000000"));
            assertThat(rate("24").ratePercent().scale()).isEqualTo(VatClassView.RATE_SCALE);
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("the multiplier is the rate shifted two places, exactly")
        void multiplierIsExact() {
            assertThat(rate("24").multiplier()).isEqualByComparingTo("0.24");
            assertThat(rate("13").multiplier()).isEqualByComparingTo("0.13");
            assertThat(rate("17").multiplier()).isEqualByComparingTo("0.17");
            assertThat(rate("3").multiplier()).isEqualByComparingTo("0.03");
            assertThat(rate("0").multiplier()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("VAT on an amount is rounded once, with the mode stated")
        void vatIsRoundedOnce() {
            // 12.99 * 0.24 = 3.1176 exactly; rounding once at the end is the whole point.
            assertThat(rate("24").vatOn(Money.ofEur("12.99"), RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("3.12"));
            assertThat(rate("24").vatOn(Money.ofEur("12.99"), RoundingMode.DOWN))
                    .isEqualTo(Money.ofEur("3.11"));
        }

        @Test
        @DisplayName("the two 4% classes compute identically despite being different classes")
        void bothFourPercentClassesAgreeOnArithmetic() {
            // 1040 and 1041 differ in legal basis and code, not in arithmetic. Worth pinning:
            // it is the reason a lookup by rate is ambiguous while the maths is not.
            VatClassView standardFour =
                    new VatClassView(1L, "1040", "ΦΠΑ 4%", new BigDecimal("4"), null, true);
            VatClassView islandFour = new VatClassView(
                    2L, "1041", "ΦΠΑ 4% (αρ.31 ν.5057/2023)", new BigDecimal("4"), null, true);

            Money net = Money.ofEur("250.00");
            assertThat(standardFour.vatOn(net, RoundingMode.HALF_UP))
                    .isEqualTo(islandFour.vatOn(net, RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("10.00"));
            assertThat(standardFour).isNotEqualTo(islandFour);
        }

        @Test
        @DisplayName("zero-rated is distinguishable, and is not the same as exempt")
        void zeroRated() {
            assertThat(rate("0").isZeroRated()).isTrue();
            assertThat(rate("24").isZeroRated()).isFalse();
            assertThat(rate("0").vatOn(Money.ofEur("500.00"), RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("0.00"));
        }
    }

    @Nested
    @DisplayName("island-reduced counterpart")
    class ReducedCounterpart {

        @Test
        @DisplayName("a counterpart is reported when one is recorded")
        void counterpartIsReported() {
            VatClassView mainland =
                    new VatClassView(9L, "1410", "ΦΠΑ 24%", new BigDecimal("24"), 8L, true);

            assertThat(mainland.hasReducedCounterpart()).isTrue();
            assertThat(mainland.reducedCounterpart()).contains(8L);
        }

        @Test
        @DisplayName("no counterpart is the normal case, including for the reduced classes")
        void noCounterpart() {
            assertThat(rate("17").hasReducedCounterpart()).isFalse();
            assertThat(rate("17").reducedCounterpart()).isEmpty();
        }
    }
}
