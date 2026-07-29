package gr.novotrade.novocore.core.tax;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.shared.Rate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <strong>{@link Rate} and the database's CHECK constraints say the same thing — asserted per
 * value, not by reading the two and believing they match.</strong>
 *
 * <p>Step 15a introduced {@code Rate} and deliberately shipped <em>no migration</em> with it. That
 * is a claim worth testing rather than asserting in a comment: {@code vat_class_rate_is_a_percentage}
 * (V10) and {@code asset_depreciation_rate_is_a_percentage} (V9) were written before the type
 * existed, and the whole argument for adding no migration is that they already state a rule at least
 * as strict as the type's. If that were wrong, a value Java accepts would fail on insert — the
 * defect showing up as a constraint violation in some unrelated feature months later.
 *
 * <p>Same arrangement as {@code journal_source_is_amendable} being held to {@code isAmendable()} and
 * the section CHECK being held to the {@code Section} enum: <strong>the database states the rule
 * independently, and a test proves the two statements agree.</strong> The price of that arrangement
 * is that changing the bound means changing both; the payoff is that neither side can drift
 * unnoticed.
 *
 * <p>Every probe writes raw SQL and rolls back, so this bypasses the services entirely — what it
 * reports is what the <em>database</em> will accept, not what some Java layer permitted first.
 *
 * <p><strong>The one place the two deliberately differ is zero</strong>, and that asymmetry is
 * asserted rather than smoothed over: {@code Rate} must permit it because the zero-rated VAT class
 * is real, and {@code asset} must refuse it because a null rate already says "does not depreciate".
 */
class RateAgreesWithTheDatabaseIT extends AbstractCoreIntegrationTest {

    /**
     * Chosen to sit on both sides of every boundary the rule has, including the interval between 0
     * and 1 that the whole lower bound exists for.
     */
    private static final List<String> CANDIDATES = List.of(
            "0", "0.000001", "0.24", "0.999999", "1", "1.000001", "4", "13", "24",
            "99.999999", "100", "100.000001", "101", "-0.000001", "-1");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("vat_class accepts exactly the rates Rate accepts, value for value")
    void vatClassCheckAgreesWithRate() {
        List<String> disagreements = new ArrayList<>();

        for (String candidate : CANDIDATES) {
            boolean javaAccepts = Rate.isAcceptable(new BigDecimal(candidate));
            boolean databaseAccepts = databaseAcceptsVatRate(candidate);

            if (javaAccepts != databaseAccepts) {
                disagreements.add("%s — Rate.isAcceptable=%s, vat_class CHECK accepts=%s"
                        .formatted(candidate, javaAccepts, databaseAccepts));
            }
        }

        assertThat(disagreements)
                .as("Rate and vat_class_rate_is_a_percentage must agree, or a rate Java accepts "
                        + "fails on insert. This is what makes shipping Rate without a migration "
                        + "correct rather than merely convenient.")
                .isEmpty();
    }

    @Test
    @DisplayName("asset accepts the same rates except zero, which it refuses on purpose")
    void assetCheckAgreesWithRateExceptForZero() {
        List<String> disagreements = new ArrayList<>();

        for (String candidate : CANDIDATES) {
            BigDecimal value = new BigDecimal(candidate);
            // The asset's rule is Rate's, minus zero. Stated here as an expression rather than a
            // second list, so it cannot fall out of step with what Rate says.
            boolean expected = Rate.isAcceptable(value) && value.signum() != 0;
            boolean databaseAccepts = databaseAcceptsAssetRate(candidate);

            if (expected != databaseAccepts) {
                disagreements.add("%s — expected=%s, asset CHECK accepts=%s"
                        .formatted(candidate, expected, databaseAccepts));
            }
        }

        assertThat(disagreements)
                .as("asset_depreciation_rate_is_a_percentage is Rate's rule minus zero")
                .isEmpty();

        // And the asymmetry itself, named rather than left implicit in the loop above.
        assertThat(Rate.isAcceptable(BigDecimal.ZERO))
                .as("Rate must permit zero — the zero-rated VAT class is real")
                .isTrue();
        assertThat(databaseAcceptsAssetRate("0"))
                .as("an asset must refuse zero — a null rate already says \"does not depreciate\"")
                .isFalse();
    }

    @Test
    @DisplayName("a null asset rate is accepted, because \"not known yet\" is a real state")
    void assetRateMayBeNull() {
        assertThat(databaseAcceptsAssetRate(null))
                .as("the statutory rates are still pending from the accountant; null is how the "
                        + "register says so, and nothing may substitute a value for it")
                .isTrue();
    }

    // ---------------------------------------------------------------------------------------

    private boolean databaseAcceptsVatRate(String ratePercent) {
        return accepts(() -> jdbc.update("""
                INSERT INTO vat_class (code, description, rate_percent, active,
                                       created_at, created_by, updated_at, updated_by)
                VALUES (?, 'Rate agreement probe', CAST(? AS numeric), true,
                        now(), 'test', now(), 'test')
                """, "TEST-RATE-PROBE", ratePercent));
    }

    private boolean databaseAcceptsAssetRate(String ratePercent) {
        return accepts(() -> jdbc.update("""
                INSERT INTO asset (name, acquisition_date, depreciation_rate_percent, status,
                                   created_at, created_by, updated_at, updated_by)
                VALUES ('Rate agreement probe', ?, CAST(? AS numeric), 'IN_USE',
                        now(), 'test', now(), 'test')
                """, LocalDate.of(2026, 1, 1), ratePercent));
    }

    /**
     * Runs one insert and rolls it back, reporting only whether the database allowed it.
     *
     * <p>Rolled back because this class shares its database with every other core integration test,
     * and a probe row left in {@code vat_class} would break the seed assertions in {@code
     * VatClassIT} — which count and name exactly the nine seeded classes.
     */
    private boolean accepts(Runnable insert) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            return Boolean.TRUE.equals(transaction.execute(status -> {
                try {
                    insert.run();
                    return true;
                } catch (RuntimeException refusedByTheDatabase) {
                    return false;
                } finally {
                    status.setRollbackOnly();
                }
            }));
        } catch (RuntimeException rolledBack) {
            // A constraint that fires at commit rather than at statement time still means refused.
            return false;
        }
    }
}
