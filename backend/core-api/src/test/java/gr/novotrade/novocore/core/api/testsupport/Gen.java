package gr.novotrade.novocore.core.api.testsupport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * A source of random values for {@link Property}, together with the <em>smaller</em> values worth
 * trying once one of them has failed.
 *
 * <p><strong>Shrinking is the half that makes this usable.</strong> A property test that reports
 * "failed for 8371.42 EUR times 0.170034" has found a defect and told nobody what it is; the same
 * test reporting "failed for 0.01 EUR times 0.5" has found the same defect and named it. So a
 * generator is not just {@link #sample}: it also says, for a value it produced, which simpler
 * values are worth re-checking. {@link Property} walks that greedily until nothing smaller fails.
 *
 * <p><strong>Deliberately not {@code map}-able.</strong> A {@code map(Function)} combinator would
 * be two lines and would silently produce generators that cannot shrink, because a mapped value
 * cannot be turned back into the value it came from. Every generator here therefore implements its
 * own shrink, and the combinators below ({@link #pair}, {@link #triple}, {@link #listOf}) compose
 * the shrinks of what they are built from rather than throwing them away.
 *
 * <p>See {@link Property} for why this exists at all rather than jqwik.
 */
@FunctionalInterface
public interface Gen<T> {

    /** One value. Must be a legal instance — a generator never produces input its own type rejects. */
    T sample(RandomGenerator random);

    /**
     * Simpler values to try instead of {@code value}, best-first.
     *
     * <p>Every candidate must be strictly "smaller" by some measure that cannot cycle, or shrinking
     * would not terminate. Returning nothing is always correct and merely means a failure is
     * reported as generated.
     */
    default List<T> shrink(T value) {
        return List.of();
    }

    // ---------------------------------------------------------------------------------------
    // Combinators
    // ---------------------------------------------------------------------------------------

    /** Two independent values. Shrinks one side at a time, so the report names which one matters. */
    static <A, B> Gen<Pair<A, B>> pair(Gen<A> first, Gen<B> second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return new Gen<>() {
            @Override
            public Pair<A, B> sample(RandomGenerator random) {
                return new Pair<>(first.sample(random), second.sample(random));
            }

            @Override
            public List<Pair<A, B>> shrink(Pair<A, B> value) {
                List<Pair<A, B>> candidates = new ArrayList<>();
                for (A a : first.shrink(value.first())) {
                    candidates.add(new Pair<>(a, value.second()));
                }
                for (B b : second.shrink(value.second())) {
                    candidates.add(new Pair<>(value.first(), b));
                }
                return candidates;
            }
        };
    }

    /** Three independent values. */
    static <A, B, C> Gen<Triple<A, B, C>> triple(Gen<A> first, Gen<B> second, Gen<C> third) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(third, "third");
        return new Gen<>() {
            @Override
            public Triple<A, B, C> sample(RandomGenerator random) {
                return new Triple<>(
                        first.sample(random), second.sample(random), third.sample(random));
            }

            @Override
            public List<Triple<A, B, C>> shrink(Triple<A, B, C> value) {
                List<Triple<A, B, C>> candidates = new ArrayList<>();
                for (A a : first.shrink(value.first())) {
                    candidates.add(new Triple<>(a, value.second(), value.third()));
                }
                for (B b : second.shrink(value.second())) {
                    candidates.add(new Triple<>(value.first(), b, value.third()));
                }
                for (C c : third.shrink(value.third())) {
                    candidates.add(new Triple<>(value.first(), value.second(), c));
                }
                return candidates;
            }
        };
    }

    /**
     * A list of between {@code minSize} and {@code maxSize} values.
     *
     * <p>Shrinks by dropping elements <em>first</em> and only then by simplifying the ones that
     * remain, because a failure that survives on a two-element list is a far better bug report than
     * the same failure on a seven-element list of simpler numbers.
     */
    static <T> Gen<List<T>> listOf(Gen<T> element, int minSize, int maxSize) {
        Objects.requireNonNull(element, "element");
        if (minSize < 0 || maxSize < minSize) {
            throw new IllegalArgumentException("Bad size range " + minSize + ".." + maxSize);
        }
        return new Gen<>() {
            @Override
            public List<T> sample(RandomGenerator random) {
                int size = minSize + random.nextInt(maxSize - minSize + 1);
                List<T> values = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    values.add(element.sample(random));
                }
                return List.copyOf(values);
            }

            @Override
            public List<List<T>> shrink(List<T> value) {
                List<List<T>> candidates = new ArrayList<>();
                if (value.size() > minSize) {
                    for (int i = 0; i < value.size(); i++) {
                        List<T> shorter = new ArrayList<>(value);
                        shorter.remove(i);
                        candidates.add(List.copyOf(shorter));
                    }
                }
                for (int i = 0; i < value.size(); i++) {
                    for (T smaller : element.shrink(value.get(i))) {
                        List<T> simplified = new ArrayList<>(value);
                        simplified.set(i, smaller);
                        candidates.add(List.copyOf(simplified));
                    }
                }
                return candidates;
            }
        };
    }

    /**
     * One of a fixed set of values — enum constants, rounding modes, currencies.
     *
     * <p>Shrinks toward the <em>first</em> value listed, so put the least surprising one first: a
     * failure reported against {@code HALF_UP} reads as a real defect, the same failure reported
     * against {@code UNNECESSARY} reads as an artefact of the test.
     */
    @SafeVarargs
    static <T> Gen<T> oneOf(T... values) {
        List<T> options = List.of(values);
        if (options.isEmpty()) {
            throw new IllegalArgumentException("oneOf needs at least one value.");
        }
        return new Gen<>() {
            @Override
            public T sample(RandomGenerator random) {
                return options.get(random.nextInt(options.size()));
            }

            @Override
            public List<T> shrink(T value) {
                int index = options.indexOf(value);
                if (index <= 0) {
                    return List.of();
                }
                Set<T> simpler = new LinkedHashSet<>(options.subList(0, index));
                return List.copyOf(simpler);
            }
        };
    }

    /** An {@code int} in {@code [min, max]}, shrinking toward {@code min}. */
    static Gen<Integer> ints(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("Bad range " + min + ".." + max);
        }
        return new Gen<>() {
            @Override
            public Integer sample(RandomGenerator random) {
                return min + random.nextInt(max - min + 1);
            }

            @Override
            public List<Integer> shrink(Integer value) {
                if (value <= min) {
                    return List.of();
                }
                List<Integer> candidates = new ArrayList<>();
                candidates.add(min);
                int halfway = min + (value - min) / 2;
                if (halfway > min && halfway < value) {
                    candidates.add(halfway);
                }
                candidates.add(value - 1);
                return candidates;
            }
        };
    }

    /** Two values generated together. */
    record Pair<A, B>(A first, B second) {
    }

    /** Three values generated together. */
    record Triple<A, B, C>(A first, B second, C third) {
    }
}
