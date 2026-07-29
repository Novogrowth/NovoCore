package gr.novotrade.novocore.core.api.testsupport;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * Runs a property over many generated values, and reports the <em>smallest</em> value that breaks
 * it.
 *
 * <p><strong>Why this exists rather than jqwik.</strong> jqwik is the obvious library for this, and
 * it cannot be used here for the same reason {@code archunit-junit5} could not (ADR 0002) and
 * {@code greenmail-junit5} could not: it binds to JUnit Platform 1.x, and Spring Boot 4 brings
 * JUnit 6, whose platform artefacts are versioned 6.x. jqwik 1.10.1 — the newest release as of this
 * step — declares {@code junit-platform-engine:1.14.4}, and there is no jqwik 2. Adding it would
 * mean pinning the whole reactor's test platform backwards to accommodate one library, which is a
 * high price for a feature that is a few hundred lines of ordinary code. So the third instance of
 * the same decision, resolved the same way: take the idea, not the artefact.
 *
 * <p><strong>The seed is fixed by default, and that is a deliberate trade-off.</strong> A property
 * runner that reseeds itself every run explores more of the input space over time — but it turns a
 * build gate into something that can go red on a commit that changed nothing, and this repository
 * has an explicit stance that a check which cries wolf is one somebody eventually deletes. So the
 * default seed is a constant: the same {@value #TRIALS} cases run on every machine and every CI
 * run, and a red build always means a real defect. The exploration is still available and is a
 * deliberate act — {@code -Dnovocore.property.seed=<n>} runs a different sample, and any seed that
 * finds something should be pinned here as an ordinary example-based test rather than left to be
 * rediscovered by luck.
 *
 * <p>What buys back the breadth a fixed seed costs is the generators, not the runner:
 * {@link ValueGenerators} biases heavily toward the boundaries — zero, one cent, the scale limit,
 * a value one unit either side of a rounding midpoint — because that is where these types actually
 * break, and a uniformly random 12-digit decimal almost never lands there.
 */
public final class Property {

    /**
     * Cases per property. These are pure {@code BigDecimal} arithmetic with no I/O, so several
     * hundred costs milliseconds; the whole point of the split between {@code *Test} and
     * {@code *IT} is that this half stays fast enough to run constantly.
     */
    public static final int TRIALS = 500;

    /**
     * Cases for a property whose each case costs real work — a database round trip, a posted
     * journal entry, a container.
     *
     * <p>Two orders of magnitude below {@link #TRIALS}, and the number is a budget rather than a
     * statistical choice: a scenario property that replays twenty randomly-shaped histories against
     * a real PostgreSQL is worth having in every build, and one that replays five hundred is a test
     * suite people stop running. Breadth comes from each case being a whole history rather than one
     * value, so twenty of them exercise far more than twenty examples would.
     */
    public static final int SCENARIO_CASES = 20;

    /** Overridable, so a wider sample is one command away without editing anything. */
    public static final String SEED_PROPERTY = "novocore.property.seed";

    /**
     * Arbitrary and fixed. Changing it is exactly as legitimate as passing {@link #SEED_PROPERTY} —
     * but if a new value turns something red, the finding is a defect and belongs in a named test,
     * not in this constant.
     */
    private static final long DEFAULT_SEED = 20260729L;

    /**
     * A failing value is only worth shrinking so far. Each round must find a strictly simpler
     * failing candidate, so this is a backstop against a generator whose shrink can cycle rather
     * than an expected limit.
     */
    private static final int MAX_SHRINK_ROUNDS = 200;

    /**
     * Shrink rounds for a scenario property. Far tighter than {@link #MAX_SHRINK_ROUNDS}, because
     * every candidate here replays a whole history against a real database: an unbounded search for
     * the prettiest counterexample would cost more than the finding is worth. Twelve rounds still
     * strips a scenario down by orders of magnitude, since dropping a step is one round.
     */
    private static final int MAX_SCENARIO_SHRINK_ROUNDS = 12;

    private Property() {
    }

    /** Checks that {@code property} holds for every generated value. */
    public static <T> void forAll(String description, Gen<T> generator, Consumer<T> property) {
        run(description, TRIALS, MAX_SHRINK_ROUNDS, generator, property);
    }

    /**
     * As {@link #forAll}, for a property whose each case costs a database round trip or a posted
     * entry. Runs {@link #SCENARIO_CASES} of them and shrinks a failure only briefly.
     */
    public static <T> void forAllScenarios(
            String description, Gen<T> generator, Consumer<T> property) {
        run(description, SCENARIO_CASES, MAX_SCENARIO_SHRINK_ROUNDS, generator, property);
    }

    private static <T> void run(String description, int trials, int maxShrinkRounds,
            Gen<T> generator, Consumer<T> property) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(property, "property");

        long seed = seed();
        RandomGenerator random = new Random(seed);
        for (int trial = 0; trial < trials; trial++) {
            T value = generator.sample(random);
            Throwable failure = failureFrom(property, value);
            if (failure == null) {
                continue;
            }
            T smallest = shrink(generator, property, value, maxShrinkRounds);
            Throwable smallestFailure = failureFrom(property, smallest);
            throw new AssertionError(report(description, seed, trial, trials, value, smallest,
                    smallestFailure == null ? failure : smallestFailure),
                    smallestFailure == null ? failure : smallestFailure);
        }
    }

    /** Two independent values. */
    public static <A, B> void forAll(
            String description, Gen<A> first, Gen<B> second, BiConsumer<A, B> property) {
        forAll(description, Gen.pair(first, second),
                pair -> property.accept(pair.first(), pair.second()));
    }

    /** Three independent values. */
    public static <A, B, C> void forAll(String description, Gen<A> first, Gen<B> second,
            Gen<C> third, TriConsumer<A, B, C> property) {
        forAll(description, Gen.triple(first, second, third),
                triple -> property.accept(triple.first(), triple.second(), triple.third()));
    }

    /** {@link BiConsumer} with one more argument; the JDK has no such type. */
    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A first, B second, C third);
    }

    // ---------------------------------------------------------------------------------------

    private static long seed() {
        String configured = System.getProperty(SEED_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_SEED;
        }
        try {
            return Long.parseLong(configured.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "-D" + SEED_PROPERTY + "=" + configured + " is not a number.", e);
        }
    }

    /**
     * Greedily takes the first simpler candidate that still fails, and repeats.
     *
     * <p>Greedy rather than exhaustive on purpose: the goal is a value small enough to read, not
     * the provably smallest one, and an exhaustive search over a list generator's candidates is
     * quadratic for no extra diagnostic value.
     */
    private static <T> T shrink(
            Gen<T> generator, Consumer<T> property, T failing, int maxRounds) {
        T current = failing;
        for (int round = 0; round < maxRounds; round++) {
            T simpler = null;
            List<T> candidates = generator.shrink(current);
            for (T candidate : candidates) {
                if (candidate == null || candidate.equals(current)) {
                    continue;
                }
                if (failureFrom(property, candidate) != null) {
                    simpler = candidate;
                    break;
                }
            }
            if (simpler == null) {
                return current;
            }
            current = simpler;
        }
        return current;
    }

    /**
     * Runs the property once and returns what it threw, or null.
     *
     * <p>Catches {@link Throwable} rather than {@link AssertionError} alone, because a property
     * that blows up with an {@code ArithmeticException} on some input has found precisely the kind
     * of defect this is here for — and swallowing that as "not a failure" would make the runner
     * quietly weaker than it looks.
     *
     * <p><strong>Consequence worth knowing: never use a JUnit assumption inside a property.</strong>
     * {@code assumeTrue} throws {@code TestAbortedException}, which this correctly cannot tell apart
     * from a real failure. Narrow the input with a plain {@code if} guard, or — better — with a
     * generator that cannot produce the unwanted value in the first place.
     */
    private static <T> Throwable failureFrom(Consumer<T> property, T value) {
        try {
            property.accept(value);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static <T> String report(String description, long seed, int trial, int trials,
            T original, T smallest, Throwable failure) {
        StringBuilder message = new StringBuilder()
                .append("Property failed: ").append(description)
                .append(System.lineSeparator())
                .append("  smallest failing value: ").append(render(smallest))
                .append(System.lineSeparator());
        if (!smallest.equals(original)) {
            message.append("  originally generated:   ").append(render(original))
                    .append(System.lineSeparator());
        }
        return message
                .append("  failure: ").append(failure)
                .append(System.lineSeparator())
                .append("  reproduce: trial ").append(trial).append(" of ").append(trials)
                .append(" with -D").append(SEED_PROPERTY).append('=').append(seed)
                .toString();
    }

    private static String render(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + " " + value;
    }
}
