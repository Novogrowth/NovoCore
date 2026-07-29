package gr.novotrade.novocore.core.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import gr.novotrade.novocore.core.api.testsupport.Property;
import gr.novotrade.novocore.core.api.testsupport.ValueGenerators;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two six-decimal types, and the one rounding step that turns them into a posting.
 *
 * <p>{@link Quantity} and {@link UnitCost} are the other half of {@code CLAUDE.md} rule 5: a
 * quantity extended at a unit cost is where a cent goes missing if precision is given up too early,
 * and both types exist in the shape they do to make that impossible. The properties that matter are
 * therefore about the <em>boundary</em> between them and {@link Money} — that
 * {@link UnitCost#extend} is the exact product rounded exactly once, and that the only route from
 * six decimals to two names its rounding mode.
 *
 * <p>{@link Quantity#times} and {@link UnitCost#minus} both refuse rather than round. The properties
 * below assert <em>when</em> they refuse, not merely that they can — a guard that fires more often
 * than it should is as wrong as one that never fires, and only a generated sample notices.
 */
class QuantityAndUnitCostPropertiesTest {

    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    @Nested
    @DisplayName("Quantity — arithmetic")
    class QuantityArithmetic {

        @Test
        @DisplayName("normalising to six decimals never changes the value")
        void normalisationIsValuePreserving() {
            Property.forAll("Quantity.of(d).value() == d numerically",
                    ValueGenerators.decimals(Quantity.SCALE),
                    d -> assertThat(Quantity.of(d).value()).isEqualByComparingTo(d));
        }

        @Test
        @DisplayName("adding then subtracting returns to the start, and order never matters")
        void additionIsWellBehaved() {
            Property.forAll("q.plus(r).minus(r) == q",
                    ValueGenerators.quantities(), ValueGenerators.quantities(),
                    (q, r) -> assertThat(q.plus(r).minus(r)).isEqualTo(q));
            Property.forAll("q + r == r + q",
                    ValueGenerators.quantities(), ValueGenerators.quantities(),
                    (q, r) -> assertThat(q.plus(r)).isEqualTo(r.plus(q)));
            Property.forAll("(q + r) + s == q + (r + s)",
                    ValueGenerators.quantities(), ValueGenerators.quantities(),
                    ValueGenerators.quantities(),
                    (q, r, s) -> assertThat(q.plus(r).plus(s)).isEqualTo(q.plus(r.plus(s))));
        }

        @Test
        @DisplayName("zero is the identity and negation is its own inverse")
        void zeroAndNegation() {
            Property.forAll("q + 0 == q, -(-q) == q, q + (-q) == 0",
                    ValueGenerators.quantities(), q -> {
                        assertThat(q.plus(Quantity.ZERO)).isEqualTo(q);
                        assertThat(q.negated().negated()).isEqualTo(q);
                        assertThat(q.plus(q.negated()).isZero()).isTrue();
                    });
        }

        @Test
        @DisplayName("equality and ordering agree, and both follow the value")
        void equalityAndOrdering() {
            Property.forAll("(compareTo == 0) <=> equals, and ordering follows the value",
                    ValueGenerators.quantities(), ValueGenerators.quantities(), (q, r) -> {
                        assertThat(q.compareTo(r) == 0).isEqualTo(q.equals(r));
                        assertThat(Integer.signum(q.compareTo(r)))
                                .isEqualTo(q.value().compareTo(r.value()));
                    });
        }

        @Test
        @DisplayName("min returns one of the two, and never the larger")
        void minIsTotalAndCorrect() {
            // FIFO takes what a lot can supply with exactly this call, so "never the larger" is the
            // difference between consuming a lot and overdrawing it.
            Property.forAll("min is one of the arguments and is <= both",
                    ValueGenerators.quantities(), ValueGenerators.quantities(), (q, r) -> {
                        Quantity smaller = q.min(r);
                        assertThat(smaller).isIn(q, r);
                        assertThat(smaller).isLessThanOrEqualTo(q).isLessThanOrEqualTo(r);
                    });
        }
    }

    @Nested
    @DisplayName("Quantity — the multiplication that refuses to round")
    class QuantityMultiplication {

        @Test
        @DisplayName("it succeeds exactly when the product fits in six decimals")
        void refusalConditionIsExactlyRepresentability() {
            Property.forAll("times throws iff the exact product needs more than six decimals",
                    ValueGenerators.quantities(), ValueGenerators.quantities(), (q, r) -> {
                        BigDecimal exact = q.value().multiply(r.value());
                        boolean representable = exact.stripTrailingZeros().scale() <= Quantity.SCALE;
                        if (representable) {
                            assertThat(q.times(r).value()).isEqualByComparingTo(exact);
                        } else {
                            assertThatExceptionOfType(ArithmeticException.class)
                                    .isThrownBy(() -> q.times(r));
                        }
                    });
        }

        @Test
        @DisplayName("it is commutative, including in which inputs it refuses")
        void multiplicationIsCommutative() {
            Property.forAll("q x r == r x q, and both refuse together",
                    ValueGenerators.quantities(), ValueGenerators.quantities(), (q, r) -> {
                        Quantity forwards = attempt(q, r);
                        Quantity backwards = attempt(r, q);
                        assertThat(forwards).isEqualTo(backwards);
                    });
        }

        @Test
        @DisplayName("one is the identity — the ordinary bundle case that forced this method to exist")
        void oneIsTheIdentity() {
            // One whole bundle containing one whole grinder is 1.000000 x 1.000000, twelve decimals
            // of which six are zeros. The naive implementation threw on it.
            Property.forAll("q x 1 == q", ValueGenerators.quantities(),
                    q -> assertThat(q.times(Quantity.of(1L))).isEqualTo(q));
        }

        private Quantity attempt(Quantity left, Quantity right) {
            try {
                return left.times(right);
            } catch (ArithmeticException notAQuantity) {
                return null;
            }
        }
    }

    @Nested
    @DisplayName("UnitCost")
    class UnitCostRules {

        @Test
        @DisplayName("a negative unit cost is refused, whatever it is")
        void negativeIsAlwaysRefused() {
            Property.forAll("UnitCost refuses every negative value",
                    ValueGenerators.decimals(UnitCost.SCALE), d -> {
                        if (d.signum() >= 0) {
                            assertThat(UnitCost.of(d, Money.EUR).value()).isEqualByComparingTo(d);
                            return;
                        }
                        assertThatIllegalArgumentException()
                                .isThrownBy(() -> UnitCost.of(d, Money.EUR))
                                .withMessageContaining("negative");
                    });
        }

        @Test
        @DisplayName("adding then subtracting a landed cost returns to the received cost")
        void plusAndMinusAreInverse() {
            // Step 10's whole arrangement: allocated landed cost is added to a frozen received cost
            // and a reversal takes exactly it back off, so the lot returns to what it was received
            // at rather than to something that rounds to it.
            Property.forAll("c.plus(d).minus(d) == c",
                    ValueGenerators.unitCosts(), ValueGenerators.unitCosts(),
                    (c, d) -> assertThat(c.plus(d).minus(d)).isEqualTo(c));
        }

        @Test
        @DisplayName("subtraction refuses exactly when it would go below zero")
        void subtractionRefusesBelowZero() {
            Property.forAll("c.minus(d) throws iff d > c",
                    ValueGenerators.unitCosts(), ValueGenerators.unitCosts(), (c, d) -> {
                        if (d.compareTo(c) <= 0) {
                            assertThat(c.minus(d).value())
                                    .isEqualByComparingTo(c.value().subtract(d.value()));
                        } else {
                            assertThatIllegalArgumentException().isThrownBy(() -> c.minus(d));
                        }
                    });
        }

        @Test
        @DisplayName("extend is the exact product rounded exactly once")
        void extendRoundsOnce() {
            // The single route from six decimals to two. If it ever stops agreeing with
            // extendExactly, proportional allocation and per-line posting start disagreeing about
            // the same lot by a cent.
            Property.forAll("extend(q, mode) == rounded(extendExactly(q), mode)",
                    ValueGenerators.unitCosts(), ValueGenerators.nonNegativeQuantities(),
                    ValueGenerators.roundingModes(),
                    (c, q, mode) -> assertThat(c.extend(q, mode)).isEqualTo(
                            Money.rounded(c.extendExactly(q), c.currency(), mode)));
        }

        @Test
        @DisplayName("extending never moves the value by a whole cent")
        void extendStaysWithinOneCent() {
            Property.forAll("|extend(q, mode) - c x q| < 0.01",
                    ValueGenerators.unitCosts(), ValueGenerators.nonNegativeQuantities(),
                    ValueGenerators.roundingModes(),
                    (c, q, mode) -> assertThat(c.extend(q, mode).amount()
                            .subtract(c.extendExactly(q)).abs()).isLessThan(ONE_CENT));
        }

        @Test
        @DisplayName("a posted amount read as a unit cost and extended over one unit is itself")
        void fromMoneyRoundTrips() {
            // Widening two decimals to six discards nothing, which is why `from` needs no rounding
            // mode — and this is the assertion that says so rather than the comment claiming it.
            Property.forAll("from(m).extend(1, mode) == m for m >= 0",
                    ValueGenerators.nonNegativeMoney(), ValueGenerators.roundingModes(),
                    (m, mode) -> {
                        UnitCost cost = UnitCost.from(m);
                        assertThat(cost.value()).isEqualByComparingTo(m.amount());
                        assertThat(cost.extend(Quantity.of(1L), mode)).isEqualTo(m);
                    });
        }

        @Test
        @DisplayName("equality and ordering agree, and neither crosses a currency")
        void equalityOrderingAndCurrency() {
            Property.forAll("consistent within a currency, refused across two",
                    ValueGenerators.unitCostsInAnyCurrency(),
                    ValueGenerators.unitCostsInAnyCurrency(), (c, d) -> {
                        if (c.currency().equals(d.currency())) {
                            assertThat(c.compareTo(d) == 0).isEqualTo(c.equals(d));
                            assertThat(Integer.signum(c.compareTo(d)))
                                    .isEqualTo(c.value().compareTo(d.value()));
                            return;
                        }
                        assertThat(c).isNotEqualTo(d);
                        assertThatIllegalArgumentException().isThrownBy(() -> c.compareTo(d));
                        assertThatIllegalArgumentException().isThrownBy(() -> c.plus(d));
                        assertThatIllegalArgumentException().isThrownBy(() -> c.minus(d));
                    });
        }
    }

    @Nested
    @DisplayName("the boundary between the two scales")
    class ScaleBoundary {

        @Test
        @DisplayName("extending a quantity at a cost gives the same answer either way round")
        void quantityAndUnitCostAgree() {
            // Two entry points into the same multiplication — Quantity.extendedAt takes a bare
            // BigDecimal cost, UnitCost.extend takes a quantity. They post the same journal lines
            // in different parts of the core, so a divergence would be a real accounting difference
            // and would look like a data problem.
            Property.forAll("q.extendedAt(c) == c.extend(q)",
                    ValueGenerators.unitCosts(), ValueGenerators.nonNegativeQuantities(),
                    ValueGenerators.roundingModes(),
                    (c, q, mode) -> assertThat(q.extendedAt(c.value(), c.currency(), mode))
                            .isEqualTo(c.extend(q, mode)));
        }

        @Test
        @DisplayName("a unit cost never becomes a posting except through a stated rounding mode")
        void precisionIsOnlyGivenUpExplicitly() {
            // A UnitCost is normalised to six places on construction, so its raw value is never
            // acceptable to Money — not even when it is a whole number of cents. That looks
            // pedantic and is the point: the type system, not the caller's care, is what stops a
            // six-decimal cost reaching a two-decimal posting.
            Property.forAll("Money.of(cost.value()) is always refused; rounding is the only way",
                    ValueGenerators.unitCosts(), ValueGenerators.roundingModes(), (c, mode) -> {
                        assertThat(c.value().scale()).isEqualTo(UnitCost.SCALE);
                        assertThatIllegalArgumentException()
                                .isThrownBy(() -> Money.of(c.value(), c.currency()))
                                .withMessageContaining("Rounding");
                        Money rounded = Money.rounded(c.value(), c.currency(), mode);
                        assertThat(rounded.amount().scale()).isEqualTo(Money.SCALE);
                        assertThat(rounded.amount().subtract(c.value()).abs())
                                .isLessThan(ONE_CENT);
                    });
        }
    }
}
