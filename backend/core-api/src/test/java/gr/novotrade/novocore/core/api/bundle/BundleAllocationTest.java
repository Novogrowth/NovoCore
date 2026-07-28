package gr.novotrade.novocore.core.api.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.api.shared.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proportional allocation — the arithmetic brief §5's bundles stand on, and the reason
 * {@code CLAUDE.md} insists on tests for anything touching money.
 *
 * <p>The property that matters is not "roughly proportional", it is <strong>exact</strong>: the parts
 * must add back up to the whole to the cent, or the bundle level and the component level of brief §5's
 * dual-level reporting disagree and neither can be trusted. Nearly every test here is a variation on
 * that one property, because it is the one an innocent-looking edit would break.
 */
class BundleAllocationTest {

    private static List<BigDecimal> weights(String... values) {
        List<BigDecimal> weights = new ArrayList<>(values.length);
        for (String value : values) {
            weights.add(new BigDecimal(value));
        }
        return weights;
    }

    private static Money sum(List<Money> parts) {
        Money total = Money.zero(Money.EUR);
        for (Money part : parts) {
            total = total.plus(part);
        }
        return total;
    }

    @Nested
    @DisplayName("the parts add up to the whole, always")
    class Exactness {

        @Test
        @DisplayName("a clean split is simply proportional")
        void cleanSplit() {
            List<Money> parts = BundleAllocation.proportionally(
                    Money.ofEur("100.00"), weights("60.00", "40.00"));

            assertThat(parts).containsExactly(Money.ofEur("60.00"), Money.ofEur("40.00"));
        }

        @Test
        @DisplayName("a split with no exact answer still sums exactly, by largest remainder")
        void thirdsSumExactly() {
            // The canonical failure: 10.00 over three equal parts is 3.3333... each. Divide-and-round
            // gives 3.33 three times and loses a cent, which then has to be reconciled somewhere.
            List<Money> parts = BundleAllocation.proportionally(
                    Money.ofEur("10.00"), weights("1", "1", "1"));

            assertThat(sum(parts)).isEqualTo(Money.ofEur("10.00"));
            // The leftover cent goes to the earliest of the tied remainders, so the answer is
            // reproducible rather than dependent on iteration order.
            assertThat(parts).containsExactly(
                    Money.ofEur("3.34"), Money.ofEur("3.33"), Money.ofEur("3.33"));
        }

        @Test
        @DisplayName("the same input allocates the same way every time")
        void deterministic() {
            List<Money> first = BundleAllocation.proportionally(
                    Money.ofEur("0.05"), weights("1", "1", "1", "1", "1", "1", "1"));
            List<Money> second = BundleAllocation.proportionally(
                    Money.ofEur("0.05"), weights("1", "1", "1", "1", "1", "1", "1"));

            assertThat(first).isEqualTo(second);
            assertThat(sum(first)).isEqualTo(Money.ofEur("0.05"));
        }

        @Test
        @DisplayName("a realistic discounted gift set sums exactly and favours the larger component")
        void discountedBundle() {
            // Grinder 189.00, 1 kg of coffee 24.50, tamper 19.90 — 233.40 standalone, sold at 199.00.
            List<Money> parts = BundleAllocation.proportionally(
                    Money.ofEur("199.00"), weights("189.00", "24.50", "19.90"));

            assertThat(sum(parts)).isEqualTo(Money.ofEur("199.00"));
            assertThat(parts.get(0)).isGreaterThan(parts.get(1));
            assertThat(parts.get(1)).isGreaterThan(parts.get(2));
        }

        @Test
        @DisplayName("weights of differing precision are handled without loss")
        void mixedScales() {
            // A component's weight is its price extended across a fractional quantity, so eight
            // decimals is an ordinary input here.
            List<Money> parts = BundleAllocation.proportionally(
                    Money.ofEur("50.00"), weights("6.12500000", "1", "0.005"));

            assertThat(sum(parts)).isEqualTo(Money.ofEur("50.00"));
        }

        @Test
        @DisplayName("a total smaller than the number of parts still sums exactly")
        void morePartsThanCents() {
            // Two cents across five parts: three of them get nothing, and that is correct — the
            // alternative is inventing three cents.
            List<Money> parts = BundleAllocation.proportionally(
                    Money.ofEur("0.02"), weights("1", "1", "1", "1", "1"));

            assertThat(sum(parts)).isEqualTo(Money.ofEur("0.02"));
            assertThat(parts).filteredOn(Money::isZero).hasSize(3);
        }

        @Test
        @DisplayName("zero allocates to zero rather than failing")
        void zeroTotal() {
            List<Money> parts = BundleAllocation.proportionally(
                    Money.ofEur("0.00"), weights("3", "1"));

            assertThat(parts).allMatch(Money::isZero);
        }
    }

    @Nested
    @DisplayName("a return allocates back exactly what the sale allocated out")
    class NegativeTotals {

        @Test
        @DisplayName("a negative total mirrors the positive split, part for part")
        void mirrorsTheSale() {
            // Otherwise returning a bundle would leave a residual per component that never clears.
            List<BigDecimal> componentWeights = weights("189.00", "24.50", "19.90");
            List<Money> sold = BundleAllocation.proportionally(
                    Money.ofEur("199.00"), componentWeights);
            List<Money> returned = BundleAllocation.proportionally(
                    Money.ofEur("-199.00"), componentWeights);

            for (int i = 0; i < sold.size(); i++) {
                assertThat(returned.get(i)).isEqualTo(sold.get(i).negated());
            }
            assertThat(sum(returned)).isEqualTo(Money.ofEur("-199.00"));
        }
    }

    @Nested
    @DisplayName("what it refuses, rather than guessing at")
    class Refusals {

        @Test
        @DisplayName("weights that all weigh nothing have no proportional answer")
        void allZeroWeights() {
            // Splitting equally instead would be a different rule, applied silently.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> BundleAllocation.proportionally(
                            Money.ofEur("10.00"), weights("0", "0")))
                    .withMessageContaining("all zero");
        }

        @Test
        @DisplayName("a negative weight is refused")
        void negativeWeight() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> BundleAllocation.proportionally(
                            Money.ofEur("10.00"), weights("5", "-1")))
                    .withMessageContaining("negative");
        }

        @Test
        @DisplayName("nothing to allocate across is refused")
        void noWeights() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> BundleAllocation.proportionally(
                            Money.ofEur("10.00"), List.of()))
                    .withMessageContaining("Nothing to allocate");
        }

        @Test
        @DisplayName("a single zero-weight part alongside a real one takes nothing")
        void oneZeroWeightAmongOthers() {
            List<Money> parts = BundleAllocation.proportionally(
                    Money.ofEur("10.00"), weights("1", "0"));

            assertThat(parts).containsExactly(Money.ofEur("10.00"), Money.ofEur("0.00"));
        }
    }

    @Test
    @DisplayName("the allocated currency follows the total")
    void currencyFollowsTheTotal() {
        List<Money> parts = BundleAllocation.proportionally(
                Money.of("10.00", java.util.Currency.getInstance("USD")), weights("1", "1"));

        assertThat(parts).allSatisfy(part ->
                assertThat(part.currency().getCurrencyCode()).isEqualTo("USD"));
    }
}
