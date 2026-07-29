package gr.novotrade.novocore.core.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import gr.novotrade.novocore.core.api.testsupport.Property;
import gr.novotrade.novocore.core.api.testsupport.ValueGenerators;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Money}'s laws, checked over generated values rather than chosen ones.
 *
 * <p><strong>What this adds over {@link MoneyTest}.</strong> That test states what {@code Money}
 * does, one worked example at a time, and it is the right place to read to find out. This one
 * states what must remain <em>true of every amount</em> — that adding then subtracting gets back to
 * where it started, that two amounts are equal exactly when they are numerically equal, that
 * rounding never moves a value by a whole cent. Those are the claims {@code CLAUDE.md} rule 5 rests
 * on, and an example-based test can only ever sample them.
 *
 * <p>The distinction matters most for the properties nobody would think to write an example for.
 * Nothing in the existing suite says that {@code compareTo} agrees with {@code equals}, and yet
 * {@code Money} is {@link Comparable} and gets sorted; nothing says the currency guard holds on
 * every operation rather than the two that were tested.
 */
class MoneyPropertiesTest {

    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    @Nested
    @DisplayName("construction and equality")
    class ConstructionAndEquality {

        @Test
        @DisplayName("normalising to two decimals never changes the value")
        void normalisationIsValuePreserving() {
            Property.forAll("Money.of(d).amount() == d numerically",
                    ValueGenerators.decimals(Money.SCALE),
                    d -> assertThat(Money.of(d, Money.EUR).amount()).isEqualByComparingTo(d));
        }

        @Test
        @DisplayName("two amounts are equal exactly when they are numerically equal")
        void equalityIsNumericEquality() {
            // The single reason the scale is fixed on construction. If this ever stops holding,
            // reconciliation and open item matching start failing to match identical amounts, and
            // the symptom appears nowhere near this class.
            Property.forAll("equals <=> compareTo == 0",
                    ValueGenerators.decimals(Money.SCALE), ValueGenerators.decimals(Money.SCALE),
                    (a, b) -> {
                        Money left = Money.of(a, Money.EUR);
                        Money right = Money.of(b, Money.EUR);
                        assertThat(left.equals(right)).isEqualTo(a.compareTo(b) == 0);
                        if (left.equals(right)) {
                            assertThat(left).hasSameHashCodeAs(right);
                        }
                    });
        }

        @Test
        @DisplayName("compareTo is consistent with equals, so sorting and matching agree")
        void compareToIsConsistentWithEquals() {
            Property.forAll("(compareTo == 0) <=> equals",
                    ValueGenerators.money(), ValueGenerators.money(),
                    (a, b) -> assertThat(a.compareTo(b) == 0).isEqualTo(a.equals(b)));
        }

        @Test
        @DisplayName("compareTo orders the same way the underlying amounts do")
        void compareToOrdersByAmount() {
            Property.forAll("signum(a.compareTo(b)) == signum(a.amount - b.amount)",
                    ValueGenerators.money(), ValueGenerators.money(),
                    (a, b) -> assertThat(Integer.signum(a.compareTo(b)))
                            .isEqualTo(a.amount().compareTo(b.amount())));
        }

        @Test
        @DisplayName("a value carrying more decimals is refused, whatever they are")
        void extraPrecisionIsAlwaysRefused() {
            // Refused on scale, not on value: 1.100 is rejected even though it is representable.
            // That is deliberate — "rounding is never implicit" is a rule about the call, not about
            // whether this particular number happens to survive it.
            Property.forAll("scale > 2 is refused", ValueGenerators.decimals(Quantity.SCALE), d -> {
                if (d.scale() <= Money.SCALE) {
                    return;
                }
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> Money.of(d, Money.EUR))
                        .withMessageContaining("decimal places");
            });
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("adding then subtracting the same amount returns to the start")
        void plusAndMinusAreInverse() {
            Property.forAll("a.plus(b).minus(b) == a",
                    ValueGenerators.money(), ValueGenerators.money(),
                    (a, b) -> assertThat(a.plus(b).minus(b)).isEqualTo(a));
        }

        @Test
        @DisplayName("addition is commutative and associative, so a total does not depend on order")
        void additionIsCommutativeAndAssociative() {
            Property.forAll("a + b == b + a",
                    ValueGenerators.money(), ValueGenerators.money(),
                    (a, b) -> assertThat(a.plus(b)).isEqualTo(b.plus(a)));
            // The property behind a trial balance: totalling an account's lines in a different
            // order must not produce a different balance.
            Property.forAll("(a + b) + c == a + (b + c)",
                    ValueGenerators.money(), ValueGenerators.money(), ValueGenerators.money(),
                    (a, b, c) -> assertThat(a.plus(b).plus(c)).isEqualTo(a.plus(b.plus(c))));
        }

        @Test
        @DisplayName("zero is the identity and negation is its own inverse")
        void zeroAndNegation() {
            Property.forAll("a + 0 == a and -(-a) == a and a + (-a) == 0",
                    ValueGenerators.money(), a -> {
                        assertThat(a.plus(Money.zero(a.currency()))).isEqualTo(a);
                        assertThat(a.negated().negated()).isEqualTo(a);
                        assertThat(a.plus(a.negated()).isZero()).isTrue();
                    });
        }

        @Test
        @DisplayName("subtraction is addition of the negation")
        void minusIsPlusNegated() {
            Property.forAll("a - b == a + (-b)",
                    ValueGenerators.money(), ValueGenerators.money(),
                    (a, b) -> assertThat(a.minus(b)).isEqualTo(a.plus(b.negated())));
        }

        @Test
        @DisplayName("abs and signum agree with each other and with zero")
        void absAndSignum() {
            Property.forAll("abs and signum are consistent", ValueGenerators.money(), a -> {
                assertThat(a.abs().isNegative()).isFalse();
                assertThat(a.abs()).isIn(a, a.negated());
                assertThat(a.signum()).isEqualTo(a.amount().signum());
                assertThat(a.isZero()).isEqualTo(a.signum() == 0);
                assertThat(a.isPositive()).isEqualTo(a.signum() > 0);
                assertThat(a.isNegative()).isEqualTo(a.signum() < 0);
            });
        }
    }

    @Nested
    @DisplayName("rounding and multiplication")
    class RoundingAndMultiplication {

        @Test
        @DisplayName("rounding never moves a value by a whole cent, in any mode")
        void roundingStaysWithinOneCent() {
            Property.forAll("|rounded(d, mode) - d| < 0.01",
                    ValueGenerators.decimals(Quantity.SCALE), ValueGenerators.roundingModes(),
                    (d, mode) -> assertThat(Money.rounded(d, Money.EUR, mode).amount()
                            .subtract(d).abs()).isLessThan(ONE_CENT));
        }

        @Test
        @DisplayName("rounding an amount that is already an amount changes nothing")
        void roundingIsIdempotent() {
            Property.forAll("rounded(m.amount(), mode) == m",
                    ValueGenerators.money(), ValueGenerators.roundingModes(),
                    (m, mode) -> assertThat(Money.rounded(m.amount(), m.currency(), mode))
                            .isEqualTo(m));
        }

        @Test
        @DisplayName("times is the exact product rounded once, never anything else")
        void timesIsMultiplyExactlyThenRound() {
            // Guards the arrangement rather than the arithmetic: multiplyExactly exists so a caller
            // can sum several lines before rounding once, and it has to be the same multiplication
            // times performs, or the two routes would disagree by a cent on some lines and not
            // others.
            Property.forAll("a.times(m, mode) == rounded(a.multiplyExactly(m), mode)",
                    ValueGenerators.smallMoney(), ValueGenerators.multipliers(),
                    ValueGenerators.roundingModes(),
                    (a, m, mode) -> assertThat(a.times(m, mode))
                            .isEqualTo(Money.rounded(a.multiplyExactly(m), a.currency(), mode)));
        }

        @Test
        @DisplayName("when timesExact succeeds, every rounding mode would have agreed with it")
        void timesExactAgreesWithEveryMode() {
            Property.forAll("timesExact(m) == times(m, any mode) when exact",
                    ValueGenerators.smallMoney(), ValueGenerators.multipliers(), (a, m) -> {
                        Money exact;
                        try {
                            exact = a.timesExact(m);
                        } catch (ArithmeticException needsRounding) {
                            return;
                        }
                        for (RoundingMode mode : RoundingMode.values()) {
                            if (mode == RoundingMode.UNNECESSARY) {
                                continue;
                            }
                            assertThat(a.times(m, mode)).isEqualTo(exact);
                        }
                    });
        }

        @Test
        @DisplayName("multiplying by one and by zero do what they say")
        void multiplicativeIdentities() {
            Property.forAll("a x 1 == a and a x 0 == 0", ValueGenerators.money(), a -> {
                assertThat(a.timesExact(BigDecimal.ONE)).isEqualTo(a);
                assertThat(a.timesExact(BigDecimal.ZERO).isZero()).isTrue();
            });
        }

        @Test
        @DisplayName("rounding once beats rounding twice by at most a cent — which is why it rounds once")
        void roundingPerLineDivergesFromRoundingOnce() {
            // Not a defect: it is the reason Quantity.extendedAt and UnitCost.extend multiply at
            // full precision and round at the end. The bound is what makes that a decision about a
            // cent rather than an unbounded drift, and it is worth having asserted.
            Property.forAll("|(a+b)xm - (axm + bxm)| <= 0.01",
                    ValueGenerators.smallMoney(), ValueGenerators.smallMoney(),
                    ValueGenerators.multipliers(), (a, b, m) -> {
                        Money onceRounded = a.plus(b).times(m, RoundingMode.HALF_UP);
                        Money twiceRounded = a.times(m, RoundingMode.HALF_UP)
                                .plus(b.times(m, RoundingMode.HALF_UP));
                        assertThat(onceRounded.minus(twiceRounded).abs().amount())
                                .isLessThanOrEqualTo(ONE_CENT);
                    });
        }
    }

    @Nested
    @DisplayName("currency")
    class CurrencyGuard {

        @Test
        @DisplayName("no operation ever combines two currencies, and none of them picks one")
        void currenciesNeverMix() {
            // ADR 0005. Asserted across every binary operation rather than the two an example test
            // happened to cover, because the guard is a private method and a new operation that
            // forgets to call it would look exactly like the ones that do.
            Property.forAll("mixed currencies always throw",
                    ValueGenerators.moneyInAnyCurrency(), ValueGenerators.moneyInAnyCurrency(),
                    (a, b) -> {
                        if (a.currency().equals(b.currency())) {
                            return;
                        }
                        assertThatIllegalArgumentException().isThrownBy(() -> a.plus(b));
                        assertThatIllegalArgumentException().isThrownBy(() -> a.minus(b));
                        assertThatIllegalArgumentException().isThrownBy(() -> a.compareTo(b));
                        assertThat(a).isNotEqualTo(b);
                    });
        }

        @Test
        @DisplayName("every operation preserves the currency it was given")
        void currencyIsCarriedThrough() {
            Property.forAll("currency survives arithmetic",
                    ValueGenerators.moneyInAnyCurrency(), ValueGenerators.multipliers(), (a, m) -> {
                        assertThat(a.negated().currency()).isEqualTo(a.currency());
                        assertThat(a.abs().currency()).isEqualTo(a.currency());
                        assertThat(a.times(m, RoundingMode.HALF_UP).currency())
                                .isEqualTo(a.currency());
                        assertThat(a.plus(Money.zero(a.currency())).currency())
                                .isEqualTo(a.currency());
                    });
        }
    }
}
