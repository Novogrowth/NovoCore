package gr.novotrade.novocore.core.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import gr.novotrade.novocore.core.api.testsupport.Gen;
import gr.novotrade.novocore.core.api.testsupport.Property;
import gr.novotrade.novocore.core.api.testsupport.ValueGenerators;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ProportionalAllocation}, over generated splits rather than chosen ones.
 *
 * <p>This is the class with the most to gain from generated input in the whole of {@code core-api}.
 * It has exactly two callers — a bundle's price pushed down onto its components (brief §5) and a
 * freight invoice divided across the lots it delivered (brief §4) — and both of them are wrong in a
 * way nobody notices if a single cent goes astray: the two levels of a dual-level revenue report
 * disagree, or the {@code Freight / Landed Cost — Unallocated} account is credited a different
 * amount from the one debited into it. Those are precisely the failures that surface months later
 * as "the report is a cent out and nobody can say which half is wrong".
 *
 * <p>Three of the properties below are the ones the class's own comment claims for itself. The
 * fourth — that no part is ever more than a cent from its exact share — is the one that says
 * largest-remainder was implemented rather than merely intended, because a floor-only split also
 * sums correctly once a residual is dumped on the last part, and would be wrong.
 *
 * <p><strong>One tempting property is deliberately absent.</strong> Permuting the weights does not
 * simply permute the result: ties in the remainder are broken by position, so two equally-weighted
 * parts take the leftover cent in list order and swapping them swaps which one gets it. That is the
 * documented behaviour and the reason the answer is reproducible at all — asserting the stronger
 * claim would be asserting a bug.
 */
class ProportionalAllocationPropertiesTest {

    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    /** Weights as the two real callers supply them: a value, never negative, often zero. */
    private static Gen<List<BigDecimal>> weightLists() {
        return Gen.listOf(ValueGenerators.nonNegativeDecimals(Quantity.SCALE), 1, 8);
    }

    private static boolean allZero(List<BigDecimal> weights) {
        return weights.stream().allMatch(weight -> weight.signum() == 0);
    }

    private static Money sum(List<Money> parts, Money total) {
        Money running = Money.zero(total.currency());
        for (Money part : parts) {
            running = running.plus(part);
        }
        return running;
    }

    @Nested
    @DisplayName("what the class exists to guarantee")
    class CoreGuarantees {

        @Test
        @DisplayName("the parts always add back up to the whole, exactly")
        void partsSumToTheTotal() {
            Property.forAll("sum(proportionally(total, weights)) == total",
                    ValueGenerators.money(), weightLists(), (total, weights) -> {
                        if (allZero(weights)) {
                            return;
                        }
                        List<Money> parts = ProportionalAllocation.proportionally(total, weights);
                        assertThat(sum(parts, total)).isEqualTo(total);
                    });
        }

        @Test
        @DisplayName("one part per weight, in the order the weights were given")
        void shapeIsPreserved() {
            Property.forAll("result.size() == weights.size(), same currency throughout",
                    ValueGenerators.moneyInAnyCurrency(), weightLists(), (total, weights) -> {
                        if (allZero(weights)) {
                            return;
                        }
                        List<Money> parts = ProportionalAllocation.proportionally(total, weights);
                        assertThat(parts).hasSameSizeAs(weights);
                        assertThat(parts).allSatisfy(part ->
                                assertThat(part.currency()).isEqualTo(total.currency()));
                    });
        }

        @Test
        @DisplayName("no part is ever more than a cent from its exact share")
        void everyPartIsWithinOneCentOfItsExactShare() {
            // This is what distinguishes largest-remainder from "floor everything and give the
            // remainder to the last part". Both sum correctly; only one of them is an allocation.
            Property.forAll("|part_i - total x w_i / W| < 0.01",
                    ValueGenerators.money(), weightLists(), (total, weights) -> {
                        if (allZero(weights)) {
                            return;
                        }
                        List<Money> parts = ProportionalAllocation.proportionally(total, weights);
                        BigDecimal weightSum = weights.stream()
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        for (int i = 0; i < parts.size(); i++) {
                            // 30 places, not 12. The true bound here is "strictly under a cent",
                            // and the closest a part legitimately comes to it is about a cent minus
                            // 1/W — around 1e-15 of a euro for the largest weights generated. A
                            // division rounded any less finely could manufacture a failure out of
                            // its own arithmetic.
                            BigDecimal exactShare = total.amount()
                                    .multiply(weights.get(i))
                                    .divide(weightSum, 30, java.math.RoundingMode.HALF_UP);
                            assertThat(parts.get(i).amount().subtract(exactShare).abs())
                                    .as("part %d of %s split across %s", i, total, weights)
                                    .isLessThan(ONE_CENT);
                        }
                    });
        }

        @Test
        @DisplayName("a weightless part takes nothing")
        void zeroWeightTakesZero() {
            // Not obvious: leftover cents are handed out by remainder, and a zero-weight part has a
            // remainder of zero, so it could in principle be handed one when several parts tie at
            // the bottom. It cannot — there are always strictly more parts with a positive
            // remainder than there are cents to hand out — and this asserts it rather than
            // reasoning about it.
            Property.forAll("w_i == 0 implies part_i == 0",
                    ValueGenerators.money(), weightLists(), (total, weights) -> {
                        if (allZero(weights)) {
                            return;
                        }
                        List<Money> parts = ProportionalAllocation.proportionally(total, weights);
                        for (int i = 0; i < weights.size(); i++) {
                            if (weights.get(i).signum() == 0) {
                                assertThat(parts.get(i).isZero())
                                        .as("weightless part %d of %s", i, weights)
                                        .isTrue();
                            }
                        }
                    });
        }
    }

    @Nested
    @DisplayName("sign")
    class Sign {

        @Test
        @DisplayName("returning a bundle allocates back exactly what selling it allocated out")
        void negationIsSymmetric() {
            // The class comment's claim, and the reason a credit note can decompose the same way
            // the invoice did. Computed on the magnitude and then negated, so this must hold
            // element by element rather than merely in total.
            Property.forAll("proportionally(-total, w) == -proportionally(total, w), elementwise",
                    ValueGenerators.money(), weightLists(), (total, weights) -> {
                        if (allZero(weights)) {
                            return;
                        }
                        List<Money> outward = ProportionalAllocation.proportionally(total, weights);
                        List<Money> back = ProportionalAllocation
                                .proportionally(total.negated(), weights);
                        List<Money> mirrored = new ArrayList<>();
                        for (Money part : outward) {
                            mirrored.add(part.negated());
                        }
                        assertThat(back).isEqualTo(mirrored);
                    });
        }

        @Test
        @DisplayName("no part ever has the opposite sign to the total")
        void partsNeverOpposeTheTotal() {
            Property.forAll("signum(part) is 0 or signum(total)",
                    ValueGenerators.money(), weightLists(), (total, weights) -> {
                        if (allZero(weights)) {
                            return;
                        }
                        List<Money> parts = ProportionalAllocation.proportionally(total, weights);
                        for (Money part : parts) {
                            assertThat(part.signum())
                                    .as("part %s of total %s", part, total)
                                    .isIn(0, total.signum());
                        }
                    });
        }

        @Test
        @DisplayName("nothing to allocate allocates nothing")
        void zeroTotalGivesZeroParts() {
            Property.forAll("all parts of a zero total are zero", weightLists(), weights -> {
                if (allZero(weights)) {
                    return;
                }
                List<Money> parts = ProportionalAllocation
                        .proportionally(Money.zero(Money.EUR), weights);
                assertThat(parts).allSatisfy(part -> assertThat(part.isZero()).isTrue());
            });
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("weights that all weigh nothing have no proportional answer, and none is invented")
        void allZeroWeightsAreRefused() {
            Property.forAll("all-zero weights are refused rather than split equally",
                    ValueGenerators.money(), Gen.ints(1, 6), (total, count) -> {
                        List<BigDecimal> weights = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            weights.add(BigDecimal.ZERO);
                        }
                        assertThatIllegalArgumentException()
                                .isThrownBy(() -> ProportionalAllocation
                                        .proportionally(total, weights))
                                .withMessageContaining("all zero");
                    });
        }

        @Test
        @DisplayName("a negative weight is refused, whatever else is in the list")
        void negativeWeightsAreRefused() {
            Property.forAll("one negative weight refuses the whole split",
                    ValueGenerators.money(), weightLists(),
                    ValueGenerators.nonNegativeDecimals(Money.SCALE), (total, weights, extra) -> {
                        if (extra.signum() == 0) {
                            return;
                        }
                        List<BigDecimal> withNegative = new ArrayList<>(weights);
                        withNegative.add(extra.negate());
                        assertThatIllegalArgumentException()
                                .isThrownBy(() -> ProportionalAllocation
                                        .proportionally(total, withNegative))
                                .withMessageContaining("negative");
                    });
        }

        @Test
        @DisplayName("there is nothing to allocate an amount across")
        void emptyWeightsAreRefused() {
            Property.forAll("an empty weight list is refused", ValueGenerators.money(),
                    total -> assertThatIllegalArgumentException()
                            .isThrownBy(() -> ProportionalAllocation
                                    .proportionally(total, List.of())));
        }
    }

    @Nested
    @DisplayName("against an independent implementation")
    class AgainstAnIndependentImplementation {

        @Test
        @DisplayName("it agrees with largest-remainder computed a different way")
        void agreesWithAnIndependentLargestRemainder() {
            // Deliberately not the same algorithm restated: this one computes the exact share as a
            // rational, floors it, and hands out the leftover by comparing exact fractional parts —
            // no shared scaling step, no shared sort key. Two independent routes to the same answer
            // is a materially stronger claim than either route being self-consistent.
            Property.forAll("agrees with an independently written largest-remainder split",
                    ValueGenerators.nonNegativeMoney(), weightLists(), (total, weights) -> {
                        if (allZero(weights)) {
                            return;
                        }
                        assertThat(ProportionalAllocation.proportionally(total, weights))
                                .isEqualTo(referenceSplit(total, weights));
                    });
        }

        private List<Money> referenceSplit(Money total, List<BigDecimal> weights) {
            BigInteger totalCents = total.amount().movePointRight(Money.SCALE).toBigIntegerExact();
            List<BigDecimal> shares = new ArrayList<>();
            BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            for (BigDecimal weight : weights) {
                shares.add(new BigDecimal(totalCents).multiply(weight)
                        .divide(weightSum, 40, java.math.RoundingMode.HALF_UP));
            }

            List<BigInteger> floored = new ArrayList<>();
            BigInteger handedOut = BigInteger.ZERO;
            for (BigDecimal share : shares) {
                BigInteger whole = share.setScale(0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
                floored.add(whole);
                handedOut = handedOut.add(whole);
            }

            int leftover = totalCents.subtract(handedOut).intValueExact();
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < weights.size(); i++) {
                order.add(i);
            }
            order.sort((left, right) -> {
                BigDecimal leftFraction = shares.get(left)
                        .subtract(new BigDecimal(floored.get(left)));
                BigDecimal rightFraction = shares.get(right)
                        .subtract(new BigDecimal(floored.get(right)));
                int byFraction = rightFraction.compareTo(leftFraction);
                return byFraction != 0 ? byFraction : Integer.compare(left, right);
            });
            for (int i = 0; i < leftover; i++) {
                int index = order.get(i);
                floored.set(index, floored.get(index).add(BigInteger.ONE));
            }

            List<Money> parts = new ArrayList<>();
            for (BigInteger cents : floored) {
                parts.add(Money.of(new BigDecimal(cents).movePointLeft(Money.SCALE),
                        total.currency()));
            }
            return List.copyOf(parts);
        }
    }
}
