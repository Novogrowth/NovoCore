package gr.novotrade.novocore.core.api.bundle;

import gr.novotrade.novocore.core.api.shared.Money;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Splits one amount across several parts in proportion to their weights, exactly.
 *
 * <p>Brief §5's "automatic proportional price/cost allocation" for bundles. A bundle sells for less
 * than the sum of its parts, so a bundle line's value has to be pushed down onto its components
 * before anything can be said about revenue or margin per product — and the parts must add back up to
 * the whole, to the cent, or the two levels of brief §5's dual-level reporting disagree and neither
 * can be trusted.
 *
 * <p><strong>The arithmetic is integer, in cents, with no division error anywhere.</strong> The
 * obvious implementation — divide, multiply, round each line — loses a cent or two on almost every
 * split, and those are exactly the residuals brief §6 then has to reconcile against a source
 * document. Instead each part's exact numerator is computed as a whole number, floored by integer
 * division, and the leftover cents are handed out one at a time by <strong>largest remainder</strong>:
 * the parts whose exact share was cut by the most get the cents back first. Ties go to the earlier
 * part, so the answer is reproducible rather than dependent on iteration order.
 *
 * <p>The consequence worth stating: this never needs a rounding mode and never produces a rounding
 * difference. {@code Rounding differences} is for reconciling against an <em>external</em> document,
 * not for absorbing our own arithmetic.
 *
 * <p>Negative totals are supported and mean what they should — a credit note or a return decomposes
 * the same way it was sold. The split is computed on the magnitude and then negated, so returning a
 * bundle allocates back exactly what selling it allocated out.
 */
public final class BundleAllocation {

    private BundleAllocation() {
    }

    /**
     * Divides {@code total} across parts in proportion to {@code weights}.
     *
     * <p>The result has one entry per weight, in the same order, and sums to {@code total} exactly.
     *
     * @param weights relative shares — for a bundle, each component's standalone value, being its own
     *     selling price extended across its quantity in the bundle. Must be non-negative and must not
     *     all be zero: proportional allocation across parts that all weigh nothing has no answer, and
     *     splitting equally instead would be inventing one.
     * @throws IllegalArgumentException if there are no weights, if one is negative, or if they sum to
     *     zero
     */
    public static List<Money> proportionally(Money total, List<BigDecimal> weights) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(weights, "weights");
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("Nothing to allocate " + total + " across.");
        }

        // Weights are scaled to whole numbers first, so every later step is exact integer work. The
        // common scale is the widest one present: a component's value is a 2-decimal price extended
        // across a 6-decimal quantity, so this is bounded and small.
        int commonScale = 0;
        for (BigDecimal weight : weights) {
            Objects.requireNonNull(weight, "weight");
            if (weight.signum() < 0) {
                throw new IllegalArgumentException(
                        "Weight " + weight.toPlainString() + " is negative. A part cannot take a "
                                + "negative share of an amount; if a component reduces the value of "
                                + "a bundle, that is a different bundle.");
            }
            commonScale = Math.max(commonScale, weight.scale());
        }

        List<BigInteger> scaledWeights = new ArrayList<>(weights.size());
        BigInteger weightSum = BigInteger.ZERO;
        for (BigDecimal weight : weights) {
            BigInteger scaled = weight.movePointRight(commonScale).toBigIntegerExact();
            scaledWeights.add(scaled);
            weightSum = weightSum.add(scaled);
        }
        if (weightSum.signum() == 0) {
            throw new IllegalArgumentException(
                    "Cannot allocate " + total + " in proportion to weights that are all zero. "
                            + "There is no proportional answer, and dividing equally instead would "
                            + "be a different rule applied silently.");
        }

        boolean negative = total.isNegative();
        // Money is exactly two decimals by construction, so this is exact rather than a truncation.
        BigInteger totalCents = total.abs().amount().movePointRight(Money.SCALE).toBigIntegerExact();

        BigInteger[] allocatedCents = new BigInteger[weights.size()];
        BigInteger[] remainders = new BigInteger[weights.size()];
        BigInteger allocatedSoFar = BigInteger.ZERO;
        for (int i = 0; i < weights.size(); i++) {
            BigInteger numerator = totalCents.multiply(scaledWeights.get(i));
            BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(weightSum);
            allocatedCents[i] = quotientAndRemainder[0];
            remainders[i] = quotientAndRemainder[1];
            allocatedSoFar = allocatedSoFar.add(quotientAndRemainder[0]);
        }

        // Strictly fewer leftover cents than there are parts, because each part lost less than one
        // whole cent to the floor above.
        int leftoverCents = totalCents.subtract(allocatedSoFar).intValueExact();
        List<Integer> byLargestRemainder = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            byLargestRemainder.add(i);
        }
        byLargestRemainder.sort(Comparator
                .<Integer, BigInteger>comparing(index -> remainders[index]).reversed()
                // The tie-break is what makes this reproducible. Without it two components with equal
                // remainders would take the extra cent in whichever order the list happened to arrive
                // in, and the same bundle would decompose two ways.
                .thenComparing(Comparator.naturalOrder()));
        for (int i = 0; i < leftoverCents; i++) {
            int index = byLargestRemainder.get(i);
            allocatedCents[index] = allocatedCents[index].add(BigInteger.ONE);
        }

        List<Money> allocation = new ArrayList<>(weights.size());
        for (BigInteger cents : allocatedCents) {
            BigDecimal amount = new BigDecimal(negative ? cents.negate() : cents)
                    .movePointLeft(Money.SCALE);
            allocation.add(Money.of(amount, total.currency()));
        }

        // The property this whole class exists for. Provable from the arithmetic above, asserted here
        // anyway: it is the invariant a future edit would break, and a silent breakage would show up
        // as a report where the component lines and the bundle line disagree by a cent.
        Money sum = Money.zero(total.currency());
        for (Money part : allocation) {
            sum = sum.plus(part);
        }
        if (!sum.equals(total)) {
            throw new IllegalStateException(
                    "Allocation of " + total + " summed to " + sum + ". Proportional allocation must "
                            + "be exact; the parts of a bundle are the same revenue as the bundle.");
        }
        return List.copyOf(allocation);
    }
}
