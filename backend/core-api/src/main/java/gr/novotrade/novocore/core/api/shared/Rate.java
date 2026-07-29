package gr.novotrade.novocore.core.api.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * <strong>A percentage: 24% is {@code 24.000000}, never {@code 0.24}.</strong>
 *
 * <p>The fourth value type beside {@link Money}, {@link Quantity} and {@link UnitCost}, and it
 * exists for the same reason they do — a bare {@link BigDecimal} carries no rule, so every place
 * that handled one had to restate the rule or quietly not.
 *
 * <p><strong>Introduced in step 15a, by a defect the HTTP sweep found.</strong> Step 14 decided
 * that decimals cross the wire as strings and applied it to the three value types above; a rate was
 * a raw {@code BigDecimal}, so {@code GET /api/vat-classes} answered {@code "ratePercent":24.000000}
 * — a JSON number, which is an IEEE-754 double the moment a browser parses it. Nothing was lost
 * <em>yet</em>, because every seeded Greek rate happens to be whole; that is the same "it is fine
 * for the values we have" that let Q45 survive twelve build steps. The island-reduced classes and
 * the statutory depreciation rates still to come from the accountant are both places a fractional
 * rate can legitimately appear.
 *
 * <h2>The bound, which now has one home instead of two</h2>
 *
 * A rate is <strong>exactly 0, or between 1 and 100.</strong> The lower bound is the load-bearing
 * half and it is not an ordinary range check: a plain 0–100 range <em>cannot</em> catch {@code 0.24}
 * written for 24%, because it sits comfortably inside and is accepted as a quarter of one percent.
 * That was a real defect twice — {@code vat_class} until V10, and {@code asset}'s rate in step 5 —
 * and it matters most for VAT, where brief §6 notes an undercharge is not recoverable from the
 * customer once the invoice is issued.
 *
 * <p>One percent is a hundred-year useful life and a hundredth of the smallest real VAT rate, so
 * nothing legitimate lives below it. If something ever does, raising the bound is a deliberate
 * change here and in the two CHECK constraints, rather than a silent acceptance.
 *
 * <p><strong>Zero is valid here and not everywhere.</strong> The zero-rated VAT class is real and
 * distinct from an exempt line, so {@code VatClass} needs it. An asset does not: "never
 * depreciates" is what a null rate already says, and {@code asset_depreciation_rate_is_a_percentage}
 * excludes zero for that reason. That extra rule stays with the asset — folding it in here would
 * make this type refuse a legitimate VAT class, and folding VAT's permission into the asset would
 * create a second way to say "no depreciation".
 *
 * <p><strong>The database says the same thing independently</strong>, in
 * {@code vat_class_rate_is_a_percentage} and {@code asset_depreciation_rate_is_a_percentage}. No
 * migration accompanied this type, deliberately: both constraints already state a rule at least as
 * strict as this one, and {@code RateAgreesWithTheDatabaseIT} holds them to it per value rather
 * than by assertion — the same arrangement as {@code journal_source_is_amendable}.
 */
public record Rate(BigDecimal percent) implements Comparable<Rate> {

    /**
     * Six decimals, matching the {@code numeric(19,6)} columns that store rates. A rate is a
     * multiplier, not an amount: it must not lose precision before the product is rounded once.
     */
    public static final int SCALE = 6;

    /** The zero-rated case. Distinct from exempt, which is not a rate at all. */
    public static final Rate ZERO = new Rate(BigDecimal.ZERO);

    /** The smallest non-zero rate this system accepts. See the class javadoc for why it is 1. */
    public static final BigDecimal MIN_NON_ZERO_PERCENT = BigDecimal.ONE;

    public static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    public Rate {
        Objects.requireNonNull(percent, "percent");
        if (percent.scale() > SCALE) {
            throw new IllegalArgumentException(
                    "Rate %s has %d decimal places, but at most %d are allowed."
                            .formatted(percent.toPlainString(), percent.scale(), SCALE));
        }
        if (!isAcceptable(percent)) {
            throw new IllegalArgumentException(
                    ("Rate %s is not a percentage. Valid values are exactly 0, or between 1 and "
                            + "100. A rate given as a fraction (0.24 for 24%%) would otherwise be "
                            + "accepted as 0.24%% and be wrong by a factor of 100 — which, for "
                            + "VAT, is not recoverable from the customer once the invoice is "
                            + "issued.").formatted(percent.toPlainString()));
        }
        // Normalised so equality compares the rate rather than however precisely it arrived, for
        // the same reason Money and Quantity fix their scale.
        percent = percent.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Rate of(BigDecimal percent) {
        return new Rate(percent);
    }

    /**
     * @param percent a plain decimal string such as {@code "24"} or {@code "24.000000"}
     */
    public static Rate of(String percent) {
        Objects.requireNonNull(percent, "percent");
        return new Rate(new BigDecimal(percent));
    }

    /**
     * Whether a value is a rate NovoCore will accept: exactly zero, or 1 through 100.
     *
     * <p>Public and static so a service can apply the identical rule on the way in — and so a test
     * can hold the database's CHECK constraints to it — rather than a second implementation of it
     * that can drift.
     */
    public static boolean isAcceptable(BigDecimal percent) {
        Objects.requireNonNull(percent, "percent");
        if (percent.signum() == 0) {
            return true;
        }
        return percent.compareTo(MIN_NON_ZERO_PERCENT) >= 0
                && percent.compareTo(MAX_PERCENT) <= 0;
    }

    /**
     * The rate as a fraction for multiplying against an amount — 24% becomes {@code 0.24}.
     *
     * <p>Exact and never throws: {@code movePointLeft} is a decimal shift, where dividing by 100
     * would have to be told what to do about a non-terminating result.
     */
    public BigDecimal multiplier() {
        return percent.movePointLeft(2);
    }

    public boolean isZero() {
        return percent.signum() == 0;
    }

    @Override
    public int compareTo(Rate other) {
        return percent.compareTo(other.percent);
    }

    /** The wire and log form: {@code "24.000000"}, scale preserved as stored. */
    @Override
    public String toString() {
        return percent.toPlainString();
    }
}
