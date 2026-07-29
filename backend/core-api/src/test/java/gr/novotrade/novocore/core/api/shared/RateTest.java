package gr.novotrade.novocore.core.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The rate bound, tested once now that it is defined once.
 *
 * <p>Before step 15a these rules were asserted twice — in {@code VatClassViewTest} and
 * {@code AssetViewTest} — against two implementations that differed slightly and neither of which
 * knew about the other. Those tests still exist and now cover what is genuinely each type's own:
 * VAT's arithmetic and zero-rating, and the asset's refusal of zero.
 */
class RateTest {

    @Nested
    @DisplayName("percent, never a fraction")
    class PercentNotFraction {

        @Test
        @DisplayName("the interval between 0 and 1 is a trap for the factor-of-100 error")
        void fractionsAreRefused() {
            // The whole reason the lower bound is not simply 0: 0.24 written for 24% sits
            // comfortably inside a plain 0-100 range and is accepted as a quarter of one percent.
            // No VAT regime and no statutory depreciation category charges a fraction of a percent,
            // so the interval is unreachable by legitimate data and available as a trap.
            for (String fraction : new String[] {
                    "0.24", "0.17", "0.13", "0.09", "0.06", "0.04", "0.03", "0.1", "0.999999"}) {
                assertThatExceptionOfType(IllegalArgumentException.class)
                        .as("%s should be refused as a fraction", fraction)
                        .isThrownBy(() -> Rate.of(fraction))
                        .withMessageContaining("wrong by a factor of 100");
            }
        }

        @Test
        @DisplayName("the boundaries themselves: 0, 1 and 100 are all valid")
        void boundariesAreInclusive() {
            assertThat(Rate.of("0").isZero()).isTrue();
            assertThat(Rate.of("1").multiplier()).isEqualByComparingTo("0.01");
            assertThat(Rate.of("100").multiplier()).isEqualByComparingTo("1");
        }

        @Test
        @DisplayName("above 100 and below zero are refused")
        void outsideTheRangeIsRefused() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> Rate.of("100.000001"));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> Rate.of("101"));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> Rate.of("-1"));
        }

        @Test
        @DisplayName("zero is valid here, because the zero-rated VAT class is real")
        void zeroIsValid() {
            // Rate cannot refuse zero: the '0' class (Μηδενικός Συντελεστής ΦΠΑ) is seeded and
            // legally distinct from an exempt line. An asset's rate may not be zero, and that rule
            // therefore lives on AssetView rather than here — see AssetViewTest.
            assertThat(Rate.ZERO.isZero()).isTrue();
            assertThat(Rate.of("0")).isEqualTo(Rate.ZERO);
            assertThat(Rate.ZERO.multiplier()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("the predicate and the constructor agree, value for value")
        void thePredicateMatchesTheConstructor() {
            // isAcceptable is what services and the database's CHECK constraints are held to, so it
            // must not be able to disagree with the type it describes.
            for (String candidate : new String[] {
                    "0", "0.000001", "0.24", "0.999999", "1", "4", "13", "24", "100",
                    "100.000001", "-1", "-0.5"}) {
                BigDecimal value = new BigDecimal(candidate);
                boolean acceptable = Rate.isAcceptable(value);
                try {
                    Rate.of(value);
                    assertThat(acceptable).as("%s was constructed, so it must be acceptable",
                            candidate).isTrue();
                } catch (IllegalArgumentException refused) {
                    assertThat(acceptable).as("%s was refused, so it must not be acceptable",
                            candidate).isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("scale")
    class Scale {

        @Test
        @DisplayName("normalised to six decimals, so equality compares the rate not its precision")
        void normalisedOnConstruction() {
            assertThat(Rate.of("24")).isEqualTo(Rate.of("24.000000"));
            assertThat(Rate.of("24").percent().scale()).isEqualTo(Rate.SCALE);
            assertThat(Rate.of("24").percent().scale()).isEqualTo(6);
        }

        @Test
        @DisplayName("more precise than six decimals is refused, never rounded")
        void overPreciseIsRefused() {
            // Same stance as Money refusing a third decimal: silently rounding here would make this
            // the layer that lost a value nobody asked it to lose.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> Rate.of("24.0000001"))
                    .withMessageContaining("decimal places");
        }

        @Test
        @DisplayName("the wire and log form is plain, never scientific notation")
        void toStringIsPlain() {
            // toPlainString, for the same reason Money's serialiser uses it: an exponent form is a
            // second representation of the same number for every reader to have to handle.
            assertThat(Rate.of("24").toString()).isEqualTo("24.000000");
            assertThat(Rate.of("0").toString()).isEqualTo("0.000000");
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("the multiplier is an exact decimal shift, not a division")
        void multiplierIsExact() {
            // movePointLeft rather than divide(100): exact, and never has to be told what to do
            // about a non-terminating result.
            assertThat(Rate.of("24").multiplier()).isEqualByComparingTo("0.24");
            assertThat(Rate.of("13").multiplier()).isEqualByComparingTo("0.13");
            assertThat(Rate.of("3").multiplier()).isEqualByComparingTo("0.03");
        }

        @Test
        @DisplayName("rates compare by value, and equality is consistent with it")
        void comparison() {
            assertThat(Rate.of("13")).isLessThan(Rate.of("24"));
            assertThat(Rate.of("24")).isEqualByComparingTo(Rate.of("24.000000"));
            assertThat(Rate.of("24")).isEqualTo(Rate.of("24.000000"));
        }
    }

    @Test
    @DisplayName("null is refused rather than treated as an unset rate")
    void nullIsRefused() {
        // "Not known yet" is expressed by a null *field* on the owning record — AssetView's rate —
        // not by a Rate that holds nothing. A nullable value inside a value type would push the
        // absent case into every arithmetic method.
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Rate.of((BigDecimal) null));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Rate.of((String) null));
    }
}
