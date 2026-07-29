package gr.novotrade.novocore.core.api.testsupport;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Generators for the core's monetary and quantity types.
 *
 * <p><strong>Biased toward the boundaries on purpose.</strong> {@link Property} runs a fixed seed,
 * so breadth has to come from what is generated rather than from how often it changes — and a
 * uniformly random twelve-digit decimal is a nearly useless test of these types. Everything that
 * has ever gone wrong with them lives at the edges: zero, one cent, a value one unit either side of
 * a rounding midpoint, the exact scale limit, a value written with fewer decimals than the type
 * stores. So roughly a third of every sample is drawn from a hand-written edge list and the rest is
 * random across a randomly chosen order of magnitude, rather than uniform over one huge range.
 *
 * <p>Magnitudes stay well inside {@code numeric(19,2)}. These are pure-Java tests with no database,
 * but a generator that produced amounts no column could hold would be testing arithmetic the system
 * cannot reach, and its failures would be unactionable.
 */
public final class ValueGenerators {

    /** The currencies used where a property needs two that differ. USD is never posted to. */
    public static final Currency USD = Currency.getInstance("USD");

    private static final Currency GBP = Currency.getInstance("GBP");

    /** How often a sample is taken from the edge list rather than generated. */
    private static final double EDGE_CASE_SHARE = 0.35;

    /** Largest magnitude generated, as a power of ten. Comfortably inside {@code numeric(19,2)}. */
    private static final int MAX_MAGNITUDE = 12;

    private ValueGenerators() {
    }

    // ---------------------------------------------------------------------------------------
    // BigDecimal
    // ---------------------------------------------------------------------------------------

    /** Signed decimals with at most {@code scale} places, across many orders of magnitude. */
    public static Gen<BigDecimal> decimals(int scale) {
        return new DecimalGen(scale, MAX_MAGNITUDE, true);
    }

    /** As {@link #decimals}, never negative. */
    public static Gen<BigDecimal> nonNegativeDecimals(int scale) {
        return new DecimalGen(scale, MAX_MAGNITUDE, false);
    }

    /**
     * Small signed decimals — magnitude at most {@code 10^4}.
     *
     * <p>For properties that multiply two generated values together. Two twelve-digit factors give a
     * twenty-four-digit product, which no column holds and no invoice contains, so a failure there
     * would say nothing about the system.
     */
    public static Gen<BigDecimal> smallDecimals(int scale) {
        return new DecimalGen(scale, 4, true);
    }

    /** Multipliers for {@code times} — VAT rates, allocation shares, unit counts. */
    public static Gen<BigDecimal> multipliers() {
        return new DecimalGen(Quantity.SCALE, 3, true);
    }

    // ---------------------------------------------------------------------------------------
    // The core's own types
    // ---------------------------------------------------------------------------------------

    /** Euro amounts, any sign. */
    public static Gen<Money> money() {
        return moneyOf(Money.EUR, decimals(Money.SCALE));
    }

    /** Euro amounts small enough to multiply. */
    public static Gen<Money> smallMoney() {
        return moneyOf(Money.EUR, smallDecimals(Money.SCALE));
    }

    /** Euro amounts that are never negative — a weight, a price, an allocatable total. */
    public static Gen<Money> nonNegativeMoney() {
        return moneyOf(Money.EUR, new DecimalGen(Money.SCALE, MAX_MAGNITUDE, false));
    }

    /** Amounts in a currency the generator also chooses, for the never-convert properties. */
    public static Gen<Money> moneyInAnyCurrency() {
        Gen<Currency> currencies = Gen.oneOf(Money.EUR, USD, GBP);
        Gen<BigDecimal> amounts = decimals(Money.SCALE);
        return new Gen<>() {
            @Override
            public Money sample(RandomGenerator random) {
                return Money.of(amounts.sample(random), currencies.sample(random));
            }

            @Override
            public List<Money> shrink(Money value) {
                List<Money> candidates = new ArrayList<>();
                for (Currency currency : currencies.shrink(value.currency())) {
                    candidates.add(Money.of(value.amount(), currency));
                }
                for (BigDecimal amount : amounts.shrink(value.amount())) {
                    candidates.add(Money.of(amount, value.currency()));
                }
                return candidates;
            }
        };
    }

    /** Quantities, any sign — a reversal and a correction are both negative and both real. */
    public static Gen<Quantity> quantities() {
        return quantityOf(new DecimalGen(Quantity.SCALE, 6, true));
    }

    /** Quantities that are never negative. */
    public static Gen<Quantity> nonNegativeQuantities() {
        return quantityOf(new DecimalGen(Quantity.SCALE, 6, false));
    }

    /** Whole positive counts in {@code [1, max]} — units off a shelf, machines in a delivery. */
    public static Gen<Quantity> wholeQuantities(int max) {
        Gen<Integer> counts = Gen.ints(1, max);
        return new Gen<>() {
            @Override
            public Quantity sample(RandomGenerator random) {
                return Quantity.of(counts.sample(random).longValue());
            }

            @Override
            public List<Quantity> shrink(Quantity value) {
                List<Quantity> candidates = new ArrayList<>();
                for (Integer smaller : counts.shrink(value.value().intValue())) {
                    candidates.add(Quantity.of(smaller.longValue()));
                }
                return candidates;
            }
        };
    }

    /** Unit costs in euro. Never negative, because {@link UnitCost} refuses one. */
    public static Gen<UnitCost> unitCosts() {
        Gen<BigDecimal> values = new DecimalGen(UnitCost.SCALE, 5, false);
        return new Gen<>() {
            @Override
            public UnitCost sample(RandomGenerator random) {
                return UnitCost.of(values.sample(random), Money.EUR);
            }

            @Override
            public List<UnitCost> shrink(UnitCost value) {
                List<UnitCost> candidates = new ArrayList<>();
                for (BigDecimal smaller : values.shrink(value.value())) {
                    candidates.add(UnitCost.of(smaller, value.currency()));
                }
                return candidates;
            }
        };
    }

    /** Unit costs in a currency the generator also chooses, for the never-convert properties. */
    public static Gen<UnitCost> unitCostsInAnyCurrency() {
        Gen<Currency> currencies = Gen.oneOf(Money.EUR, USD, GBP);
        Gen<BigDecimal> values = new DecimalGen(UnitCost.SCALE, 5, false);
        return new Gen<>() {
            @Override
            public UnitCost sample(RandomGenerator random) {
                return UnitCost.of(values.sample(random), currencies.sample(random));
            }

            @Override
            public List<UnitCost> shrink(UnitCost value) {
                List<UnitCost> candidates = new ArrayList<>();
                for (Currency currency : currencies.shrink(value.currency())) {
                    candidates.add(UnitCost.of(value.value(), currency));
                }
                for (BigDecimal smaller : values.shrink(value.value())) {
                    candidates.add(UnitCost.of(smaller, value.currency()));
                }
                return candidates;
            }
        };
    }

    /**
     * Rounding modes, {@code HALF_UP} first.
     *
     * <p>{@code UNNECESSARY} is deliberately absent: it throws by design on any value that needs
     * rounding, so including it would make half the samples fail for a reason that is not a defect.
     */
    public static Gen<RoundingMode> roundingModes() {
        return Gen.oneOf(RoundingMode.HALF_UP, RoundingMode.HALF_EVEN, RoundingMode.DOWN,
                RoundingMode.UP, RoundingMode.FLOOR, RoundingMode.CEILING, RoundingMode.HALF_DOWN);
    }

    // ---------------------------------------------------------------------------------------

    private static Gen<Money> moneyOf(Currency currency, Gen<BigDecimal> amounts) {
        return new Gen<>() {
            @Override
            public Money sample(RandomGenerator random) {
                return Money.of(amounts.sample(random), currency);
            }

            @Override
            public List<Money> shrink(Money value) {
                List<Money> candidates = new ArrayList<>();
                for (BigDecimal smaller : amounts.shrink(value.amount())) {
                    candidates.add(Money.of(smaller, value.currency()));
                }
                return candidates;
            }
        };
    }

    private static Gen<Quantity> quantityOf(Gen<BigDecimal> values) {
        return new Gen<>() {
            @Override
            public Quantity sample(RandomGenerator random) {
                return Quantity.of(values.sample(random));
            }

            @Override
            public List<Quantity> shrink(Quantity value) {
                List<Quantity> candidates = new ArrayList<>();
                for (BigDecimal smaller : values.shrink(value.value())) {
                    candidates.add(Quantity.of(smaller));
                }
                return candidates;
            }
        };
    }

    /**
     * Decimals of a bounded scale and magnitude, shrinking toward zero.
     *
     * <p>The shrink order is the diagnostic: zero, then positive, then fewer decimal places, then
     * halved, then one unit closer to zero. A failure that survives all of those is being reported
     * as simply as this can state it.
     */
    private record DecimalGen(int scale, int maxMagnitude, boolean signed) implements Gen<BigDecimal> {

        @Override
        public BigDecimal sample(RandomGenerator random) {
            if (random.nextDouble() < EDGE_CASE_SHARE) {
                List<BigDecimal> edges = edgeCases();
                return edges.get(random.nextInt(edges.size()));
            }
            // A magnitude drawn per sample rather than one huge uniform range: otherwise almost
            // every value has the maximum number of digits and small amounts never appear.
            int digits = random.nextInt(maxMagnitude + 1);
            int chosenScale = random.nextInt(scale + 1);
            long unscaled = random.nextLong(powerOfTen(digits + chosenScale));
            if (signed && random.nextBoolean()) {
                unscaled = -unscaled;
            }
            return new BigDecimal(BigInteger.valueOf(unscaled), chosenScale);
        }

        /**
         * Simpler values, best-first: zero, then positive, then fewer decimals, then a halving
         * ladder toward zero.
         *
         * <p><strong>The ladder is what makes this converge.</strong> An earlier version offered
         * "half" and "one unit closer to zero" and nothing between, which is a binary search that
         * gives up after one step: a property failing above 1000 shrank 12345 to 1543 in four
         * rounds and then crawled down by 0.01 until {@link Property}'s round limit stopped it,
         * reporting a number nobody would recognise as the boundary. Offering
         * {@code value - |value|/2^k} for every {@code k} restores the search, and the ladder
         * terminates because the halving is floored at this generator's scale.
         */
        @Override
        public List<BigDecimal> shrink(BigDecimal value) {
            if (value.signum() == 0 && value.scale() == 0) {
                return List.of();
            }
            Set<BigDecimal> candidates = new LinkedHashSet<>();
            candidates.add(BigDecimal.ZERO);
            if (value.signum() < 0) {
                candidates.add(value.negate());
            }
            if (value.scale() > 0) {
                // Fewer decimals first: 12.34 is a far better bug report than 12.34999.
                for (int fewer = 0; fewer < value.scale(); fewer++) {
                    candidates.add(value.setScale(fewer, RoundingMode.DOWN));
                }
            }
            BigDecimal step = value.abs();
            BigDecimal towardsZero = BigDecimal.valueOf(-value.signum());
            while (step.signum() > 0) {
                candidates.add(value.add(step.multiply(towardsZero)));
                step = step.divide(BigDecimal.valueOf(2), scale, RoundingMode.DOWN);
            }

            List<BigDecimal> usable = new ArrayList<>();
            for (BigDecimal candidate : candidates) {
                if (candidate.compareTo(value) == 0 || candidate.scale() > scale) {
                    continue;
                }
                if (!signed && candidate.signum() < 0) {
                    continue;
                }
                if (candidate.abs().compareTo(value.abs()) > 0) {
                    continue;
                }
                usable.add(candidate);
            }
            return usable;
        }

        /**
         * {@code 10^exponent} as a {@code long}. The caller's bounds keep {@code exponent} at 18 or
         * below — {@value #MAX_MAGNITUDE} digits plus {@link UnitCost#SCALE} — which is inside
         * {@code Long.MAX_VALUE}; anything wider would need the magnitude bound raised deliberately.
         */
        private static long powerOfTen(int exponent) {
            long value = 1L;
            for (int i = 0; i < exponent; i++) {
                value *= 10L;
            }
            return value;
        }

        private List<BigDecimal> edgeCases() {
            List<BigDecimal> edges = new ArrayList<>();
            BigDecimal one = BigDecimal.ONE.setScale(Math.min(scale, 2));
            BigDecimal smallest = BigDecimal.ONE.movePointLeft(scale);
            edges.add(BigDecimal.ZERO);
            edges.add(BigDecimal.ZERO.setScale(scale));
            edges.add(smallest);
            edges.add(one);
            edges.add(one.add(smallest));
            edges.add(one.subtract(smallest));
            // Midpoints, where two rounding modes are allowed to disagree and a third must not.
            edges.add(new BigDecimal("0.5").setScale(Math.min(scale, 1)));
            edges.add(new BigDecimal("2.5").setScale(Math.min(scale, 1)));
            edges.add(BigDecimal.TEN.pow(maxMagnitude));
            edges.add(BigDecimal.TEN.pow(maxMagnitude).subtract(smallest).setScale(scale));
            if (signed) {
                List<BigDecimal> mirrored = new ArrayList<>(edges);
                for (BigDecimal edge : mirrored) {
                    if (edge.signum() != 0) {
                        edges.add(edge.negate());
                    }
                }
            }
            return List.copyOf(edges);
        }
    }
}
