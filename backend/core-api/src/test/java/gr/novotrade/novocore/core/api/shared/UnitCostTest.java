package gr.novotrade.novocore.core.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The type {@code Money} has been pointing at since step 2 — a unit cost is not a posted amount, and
 * conflating the two is how a cent goes missing between a lot and its COGS.
 */
class UnitCostTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Nested
    @DisplayName("six decimals, because it is a multiplier and not an amount")
    class Precision {

        @Test
        @DisplayName("a value is normalised to six places, so equality compares values")
        void normalisedOnConstruction() {
            assertThat(UnitCost.ofEur("12.5")).isEqualTo(UnitCost.ofEur("12.500000"));
            assertThat(UnitCost.ofEur("12.5").value().scale()).isEqualTo(UnitCost.SCALE);
        }

        @Test
        @DisplayName("more than six places is refused rather than rounded")
        void tooPreciseIsRefused() {
            // Same stance as Money: rounding is a decision, so it has to be asked for by name.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> UnitCost.ofEur("0.1234567"))
                    .withMessageContaining("at most 6")
                    .withMessageContaining("rounded");
        }

        @Test
        @DisplayName("the precision Money could not hold is exactly why this type exists")
        void holdsWhatMoneyCannot() {
            // Two euros of freight across three units. Money would refuse this outright, and forcing it
            // to 0.67 and multiplying back gives 2.01 — a cent invented from nothing.
            UnitCost allocated = UnitCost.rounded(
                    new BigDecimal("2.00").divide(new BigDecimal("3"), 10, RoundingMode.HALF_UP),
                    Money.EUR, RoundingMode.HALF_UP);

            assertThat(allocated).isEqualTo(UnitCost.ofEur("0.666667"));
            assertThat(allocated.extend(Quantity.of(3L), RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("2.00"));
        }

        @Test
        @DisplayName("widening a Money into a unit cost is exact and needs no rounding mode")
        void fromMoneyIsExact() {
            assertThat(UnitCost.from(Money.ofEur("24.00"))).isEqualTo(UnitCost.ofEur("24.000000"));
        }
    }

    @Nested
    @DisplayName("zero is a real cost; negative is not")
    class Sign {

        @Test
        @DisplayName("a free sample is a lot at zero cost")
        void zeroIsAllowed() {
            UnitCost free = UnitCost.zero(Money.EUR);

            assertThat(free.isZero()).isTrue();
            assertThat(free.isPositive()).isFalse();
            assertThat(free.extend(Quantity.of(10L), RoundingMode.HALF_UP))
                    .isEqualTo(Money.zero(Money.EUR));
        }

        @Test
        @DisplayName("a negative unit cost is refused, and the message says why")
        void negativeIsRefused() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> UnitCost.ofEur("-1.00"))
                    .withMessageContaining("negative")
                    .withMessageContaining("purchase credit");
        }
    }

    @Nested
    @DisplayName("extending across a quantity rounds exactly once")
    class Extending {

        @Test
        @DisplayName("a fractional quantity at a six-decimal cost still lands on a clean amount")
        void roundsOnce() {
            // 0.250 kg at 18.456789/kg. Rounding the cost to 18.46 first gives 4.615 -> 4.62;
            // rounding once at the end gives 4.61. The difference is the whole point of the type.
            UnitCost coffee = UnitCost.ofEur("18.456789");

            assertThat(coffee.extend(Quantity.of("0.250"), RoundingMode.HALF_UP))
                    .isEqualTo(Money.ofEur("4.61"));
        }

        @Test
        @DisplayName("the unrounded product is available for callers that sum before rounding")
        void exactExtensionIsAvailable() {
            // Rounding each line and then summing is how a document total ends up a cent off its lines.
            UnitCost cost = UnitCost.ofEur("0.333333");

            assertThat(cost.extendExactly(Quantity.of(3L)))
                    .isEqualByComparingTo(new BigDecimal("0.999999"));
        }
    }

    @Nested
    @DisplayName("currency is carried and never crossed (ADR 0005)")
    class Currencies {

        @Test
        @DisplayName("comparing across currencies throws rather than picking one")
        void noSilentConversion() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> UnitCost.ofEur("1.00")
                            .compareTo(UnitCost.of("1.00", USD)))
                    .withMessageContaining("does not convert");
        }

        @Test
        @DisplayName("the extended amount inherits the cost's currency")
        void extendedAmountKeepsCurrency() {
            assertThat(UnitCost.of("2.00", USD).extend(Quantity.of(2L), RoundingMode.HALF_UP)
                    .currency()).isEqualTo(USD);
            assertThat(UnitCost.ofEur("2.00").isEur()).isTrue();
        }

        @Test
        @DisplayName("toString says it is per unit, so it cannot be mistaken for a total")
        void readableToString() {
            assertThat(UnitCost.ofEur("18.456789")).hasToString("18.456789 EUR/unit");
        }
    }
}
