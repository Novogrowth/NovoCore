package gr.novotrade.novocore.core.api.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.api.shared.Rate;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The asset register's invariants, as pure logic.
 *
 * <p>Worth testing without a database because these are the rules that decide whether a
 * depreciation charge can be computed at all, and the answer has to be "no" — loudly — while the
 * statutory rates are still unknown.
 */
class AssetViewTest {

    private static final LocalDate ACQUIRED = LocalDate.of(2026, 3, 15);

    private static AssetView asset(Rate ratePercent) {
        return new AssetView(1L, "FA-001", "Roaster", ACQUIRED, ratePercent, null,
                AssetStatus.IN_USE, null);
    }

    /**
     * Takes the rate as text so a refusal test can cover both gates in one lambda: since step 15a
     * the bound lives in {@link Rate}'s constructor and only the zero rule is {@code AssetView}'s
     * own, so {@code asset("0.1")} must throw from the first and {@code asset("0")} from the second.
     */
    private static AssetView asset(String ratePercent) {
        return asset(Rate.of(ratePercent));
    }

    @Nested
    @DisplayName("a rate that is not yet known")
    class UnknownRate {

        @Test
        @DisplayName("an asset with no rate exists, but cannot be depreciated")
        void noRateMeansNoDepreciation() {
            // The state the register is actually in right now: assets are real, the statutory rates
            // per category have not been supplied. Recording the asset is right; charging
            // depreciation against a guessed rate is not.
            AssetView awaitingRate = asset((Rate) null);

            assertThat(awaitingRate.depreciationRate()).isEmpty();
            assertThat(awaitingRate.canDepreciate()).isFalse();
        }

        @Test
        @DisplayName("asking for the multiplier without a rate throws instead of defaulting")
        void multiplierThrowsWithoutRate() {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> asset((Rate) null).annualMultiplier())
                    .withMessageContaining("no depreciation rate")
                    .withMessageContaining("must not be assumed");
        }
    }

    @Nested
    @DisplayName("the rate is a percentage, not a fraction")
    class RateIsAPercentage {

        @Test
        @DisplayName("10% is 10, and its multiplier is 0.10")
        void percentageConvertsExactly() {
            AssetView tenPercent = asset(Rate.of("10"));

            assertThat(tenPercent.canDepreciate()).isTrue();
            // movePointLeft, not a division: exact, and never has to be told what to do about a
            // non-terminating result.
            assertThat(tenPercent.annualMultiplier()).isEqualByComparingTo("0.10");
        }

        @Test
        @DisplayName("a rate written as a fraction is refused rather than accepted as 0.1%")
        void fractionIsRefused() {
            // The case a plain 0-100 range CANNOT catch, which is why the lower bound exists: 0.1
            // meaning 10% sits comfortably inside 0-100, and the charge would simply be a hundred
            // times too small every year with nothing complaining.
            // Since step 15a the bound is Rate's, so this is refused before an AssetView exists —
            // which is the stronger position: no caller can construct the value at all.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> asset("0.1"))
                    .withMessageContaining("between 1 and 100")
                    .withMessageContaining("wrong by a factor of 100");

            // 1% is the boundary and is accepted — a hundred-year life is implausible but at least
            // states itself as a percentage.
            assertThat(asset("1").annualMultiplier()).isEqualByComparingTo("0.01");
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> asset("0.99"));
        }

        @Test
        @DisplayName("zero and negative rates are refused; null is how \"unknown\" is said")
        void zeroAndNegativeAreRefused() {
            // Zero is excluded for a different reason from the fraction bound, and by a different
            // gate: an asset that never depreciates is what null already expresses, so zero would
            // be a second way to say it. Rate itself permits zero — the zero-rated VAT class is
            // real — so this refusal is AssetView's own and has to keep being tested here.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> asset("0"))
                    .withMessageContaining("what a null rate already says");
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> asset("-10"));
        }

        @Test
        @DisplayName("100% is allowed — written off in its first year")
        void hundredPercentIsAllowed() {
            assertThat(asset("100").annualMultiplier()).isEqualByComparingTo("1.00");
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> asset("100.000001"));
        }

        @Test
        @DisplayName("a rate more precise than the schema allows is refused, not rounded")
        void tooPreciseIsRefused() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> asset("10.1234567"))
                    .withMessageContaining("decimal places");
        }
    }

    @Nested
    @DisplayName("dates and disposal")
    class DatesAndDisposal {

        @Test
        @DisplayName("depreciation starts at acquisition unless a later date says otherwise")
        void startDateDefaultsToAcquisition() {
            assertThat(asset(Rate.of("10")).effectiveDepreciationStartDate())
                    .isEqualTo(ACQUIRED);

            LocalDate inService = ACQUIRED.plusMonths(2);
            AssetView placedLater = new AssetView(2L, null, "Grinder", ACQUIRED,
                    Rate.of("20"), inService, AssetStatus.IN_USE, null);

            // Bought in one period, placed in service in another: charging from the invoice date
            // would put the depreciation in the wrong period.
            assertThat(placedLater.effectiveDepreciationStartDate()).isEqualTo(inService);
        }

        @Test
        @DisplayName("a disposed asset needs a disposal date, and an in-use one must not have it")
        void disposalDateIsBiconditional() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new AssetView(3L, null, "Gone", ACQUIRED, null, null,
                            AssetStatus.DISPOSED, null))
                    .withMessageContaining("required exactly when");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new AssetView(4L, null, "Still here", ACQUIRED, null, null,
                            AssetStatus.IN_USE, ACQUIRED.plusYears(1)))
                    .withMessageContaining("required exactly when");
        }

        @Test
        @DisplayName("a disposed asset stops depreciating even with a rate set")
        void disposedAssetsDoNotDepreciate() {
            AssetView disposed = new AssetView(5L, null, "Sold roaster", ACQUIRED,
                    Rate.of("10"), null, AssetStatus.DISPOSED, ACQUIRED.plusYears(3));

            assertThat(disposed.depreciationRate()).isPresent();
            assertThat(disposed.canDepreciate()).isFalse();
            assertThat(disposed.disposal()).contains(ACQUIRED.plusYears(3));
        }

        @Test
        @DisplayName("neither disposal nor depreciation can precede acquisition")
        void datesCannotPrecedeAcquisition() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new AssetView(6L, null, "Time traveller", ACQUIRED, null,
                            null, AssetStatus.DISPOSED, ACQUIRED.minusDays(1)))
                    .withMessageContaining("before it was acquired");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new AssetView(7L, null, "Early starter", ACQUIRED,
                            Rate.of("10"), ACQUIRED.minusDays(1), AssetStatus.IN_USE, null))
                    .withMessageContaining("before it was acquired");
        }
    }

    @Test
    @DisplayName("the register carries no monetary field at all")
    void noMonetaryFields() {
        // Cost and accumulated depreciation are sums of journal lines against the two fixed-asset
        // control accounts, both of which declare ASSET as their sub-ledger. A stored acquisition
        // cost would be a second copy of a figure the ledger holds, and the two would part company
        // at the first correcting entry.
        assertThat(AssetView.class.getRecordComponents())
                .extracting(component -> component.getType().getSimpleName())
                .doesNotContain("Money");
        assertThat(AssetView.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("acquisitionCost", "cost", "accumulatedDepreciation",
                        "carryingValue", "salvageValue", "residualValue");
    }
}
