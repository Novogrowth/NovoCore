package gr.novotrade.novocore.core.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuantityTest {

    @Test
    @DisplayName("normalises to six decimals and compares by value")
    void normalisesAndComparesByValue() {
        assertThat(Quantity.of("2").value().toPlainString()).isEqualTo("2.000000");
        assertThat(Quantity.of("2")).isEqualTo(Quantity.of("2.000000"));
        assertThat(Quantity.of(3L)).isEqualTo(Quantity.of("3"));
    }

    @Test
    @DisplayName("rejects precision beyond six decimals")
    void rejectsExcessPrecision() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Quantity.of("1.0000001"))
                .withMessageContaining("decimal places");
    }

    @Test
    @DisplayName("supports fractional quantities, since not every product sells in whole units")
    void supportsFractionalQuantities() {
        // Coffee sold by weight is the motivating case (brief §5 puts unit of measure on the
        // Product), so an integer count would be wrong for part of the catalogue.
        assertThat(Quantity.of("0.250").plus(Quantity.of("0.750")))
                .isEqualTo(Quantity.of("1"));
    }

    @Test
    @DisplayName("adds and subtracts exactly")
    void addsAndSubtractsExactly() {
        Quantity total = Quantity.ZERO;
        for (int i = 0; i < 10; i++) {
            total = total.plus(Quantity.of("0.1"));
        }
        assertThat(total).isEqualTo(Quantity.of("1"));
        assertThat(Quantity.of("5").minus(Quantity.of("8"))).isEqualTo(Quantity.of("-3"));
    }

    @Test
    @DisplayName("permits negative quantities, which reversals and corrections need")
    void permitsNegativeQuantities() {
        Quantity negative = Quantity.of("-2.5");
        assertThat(negative.isNegative()).isTrue();
        assertThat(negative.negated()).isEqualTo(Quantity.of("2.5"));
        assertThat(negative.signum()).isEqualTo(-1);
    }

    @Test
    @DisplayName("min returns the smaller, which FIFO uses to take what a lot can supply")
    void minReturnsSmaller() {
        Quantity wanted = Quantity.of("10");
        Quantity available = Quantity.of("4");
        assertThat(wanted.min(available)).isEqualTo(available);
        assertThat(available.min(wanted)).isEqualTo(available);
        assertThat(wanted.min(wanted)).isEqualTo(wanted);
    }

    @Test
    @DisplayName("sign predicates, including that zero is neither positive nor negative")
    void signPredicates() {
        assertThat(Quantity.ZERO.isZero()).isTrue();
        assertThat(Quantity.ZERO.isPositive()).isFalse();
        assertThat(Quantity.ZERO.isNegative()).isFalse();
        assertThat(Quantity.of("0.000001").isPositive()).isTrue();
    }

    @Test
    @DisplayName("orders by value")
    void ordersByValue() {
        assertThat(Quantity.of("1")).isLessThan(Quantity.of("2"));
        assertThat(Quantity.of("-1")).isLessThan(Quantity.ZERO);
    }

    @Test
    @DisplayName("extends at a unit cost, multiplying at full precision and rounding once")
    void extendsAtUnitCostRoundingOnce() {
        // 3 x 1.005 = 3.015. Rounding the unit cost to 1.01 first and then multiplying gives
        // 3.03 — a 1.5 cent error on a single line, which is exactly how a document total fails
        // to reconcile.
        Money extended = Quantity.of("3").extendedAt(
                new BigDecimal("1.005"), Money.EUR, RoundingMode.HALF_UP);
        assertThat(extended).isEqualTo(Money.ofEur("3.02"));
    }

    @Test
    @DisplayName("extends a fractional quantity at a six-decimal unit cost")
    void extendsFractionalQuantityAtPreciseUnitCost() {
        // The landed-cost case: after proportional allocation a unit cost carries more than two
        // decimals (brief §4), and the quantity may be fractional too.
        Money extended = Quantity.of("2.5").extendedAt(
                new BigDecimal("4.123456"), Money.EUR, RoundingMode.HALF_UP);
        assertThat(extended).isEqualTo(Money.ofEur("10.31"));
    }

    @Test
    @DisplayName("toString uses plain notation, never scientific")
    void toStringIsPlain() {
        assertThat(Quantity.of("0.000001")).hasToString("0.000001");
    }
}
