package gr.novotrade.novocore.core.api.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The property runner, proven to actually fail.
 *
 * <p><strong>Why this test exists.</strong> Everything else in this package is a check that other
 * code is correct, so if it is itself broken the symptom is a suite that passes and proves nothing.
 * This repository has already been bitten by exactly that shape once — the {@code ..core.web..}
 * ArchUnit rule carried {@code allowEmptyShould(true)} and passed while checking nothing — and the
 * remedy adopted then is the one applied here: prove the check fails against something that should
 * fail it, rather than trusting that it would.
 *
 * <p>Three things need proving, and none of them are visible from a green run of the properties
 * themselves: that a false property is reported at all, that the value reported is the shrunk one
 * rather than whatever was generated, and that an exception which is not an {@link AssertionError}
 * counts as a failure rather than escaping.
 */
class PropertyTest {

    @Nested
    @DisplayName("it fails when it should")
    class Failing {

        @Test
        @DisplayName("a false property is reported, naming the seed that reproduces it")
        void falsePropertyFails() {
            AssertionError failure = assertThatExceptionOfType(AssertionError.class)
                    .isThrownBy(() -> Property.forAll("every decimal is negative",
                            ValueGenerators.decimals(2),
                            d -> assertThat(d.signum()).isNegative()))
                    .actual();

            assertThat(failure).hasMessageContaining("every decimal is negative")
                    .hasMessageContaining("smallest failing value")
                    .hasMessageContaining(Property.SEED_PROPERTY);
            // The cause is kept, so the original assertion's own message survives into the report.
            assertThat(failure.getCause()).isInstanceOf(AssertionError.class);
        }

        @Test
        @DisplayName("an exception that is not an assertion still counts as a failure")
        void nonAssertionThrowableIsAFailure() {
            // A property that blows up with an ArithmeticException on some input has found the kind
            // of defect this exists for. Treating it as "not a failure" would make the runner
            // quietly weaker than it looks.
            assertThatExceptionOfType(AssertionError.class)
                    .isThrownBy(() -> Property.forAll("division by the value",
                            ValueGenerators.decimals(2),
                            d -> BigDecimal.ONE.divide(d)))
                    .withMessageContaining("division by the value");
        }

        @Test
        @DisplayName("a property that holds runs every trial and reports nothing")
        void truePropertyRunsEveryTrial() {
            AtomicInteger calls = new AtomicInteger();
            Property.forAll("counting", ValueGenerators.decimals(2), d -> calls.incrementAndGet());
            assertThat(calls).hasValue(Property.TRIALS);
        }
    }

    @Nested
    @DisplayName("shrinking")
    class Shrinking {

        @Test
        @DisplayName("the reported value is the smallest failing one, not the one generated")
        void reportsTheShrunkValue() {
            // "Every decimal is under a thousand" fails for a great many generated values, almost
            // all of them ugly. The smallest failing value reachable by this generator's shrinks is
            // 1000 exactly, and that is what the report has to name for it to be worth reading.
            AssertionError failure = assertThatExceptionOfType(AssertionError.class)
                    .isThrownBy(() -> Property.forAll("every decimal is under a thousand",
                            ValueGenerators.decimals(2),
                            d -> assertThat(d).isLessThan(new BigDecimal("1000"))))
                    .actual();

            assertThat(failure).hasMessageContaining("smallest failing value: BigDecimal 1000");
        }

        @Test
        @DisplayName("a list shrinks by losing elements before its elements get simpler")
        void listsShrinkByDroppingElementsFirst() {
            // Dropping first is what makes a failure on a two-element list rather than a
            // seven-element one, which is the difference between a bug report somebody reads and
            // one they skip.
            AssertionError failure = assertThatExceptionOfType(AssertionError.class)
                    .isThrownBy(() -> Property.forAll("no list contains a positive value",
                            Gen.listOf(ValueGenerators.decimals(2), 0, 8),
                            values -> assertThat(values).allSatisfy(
                                    value -> assertThat(value.signum()).isNotPositive())))
                    .actual();

            assertThat(failure).hasMessageContaining("smallest failing value")
                    .hasMessageContaining("[0.01]");
        }

        @Test
        @DisplayName("a generator that cannot shrink reports the value as generated")
        void unshrinkableGeneratorsStillReport() {
            Gen<Integer> constant = random -> 42;
            AssertionError failure = assertThatExceptionOfType(AssertionError.class)
                    .isThrownBy(() -> Property.forAll("never 42", constant,
                            value -> assertThat(value).isNotEqualTo(42)))
                    .actual();

            assertThat(failure).hasMessageContaining("smallest failing value: Integer 42");
            assertThat(failure.getMessage()).doesNotContain("originally generated");
        }
    }

    @Nested
    @DisplayName("determinism")
    class Determinism {

        @Test
        @DisplayName("the same property sees the same values on every run")
        void generationIsReproducible() {
            // The reason a red build always means a defect rather than today's dice — and the
            // reason the seed is worth printing when one does go red.
            assertThat(collect()).isEqualTo(collect());
        }

        private List<BigDecimal> collect() {
            List<BigDecimal> seen = new java.util.ArrayList<>();
            Property.forAll("collecting", ValueGenerators.decimals(2), seen::add);
            return List.copyOf(seen);
        }
    }
}
