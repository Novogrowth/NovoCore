package gr.novotrade.novocore.core.api.tax;

import gr.novotrade.novocore.core.api.shared.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * One VAT class, as everything outside the core sees it.
 *
 * <p><strong>The code is the identity, never the rate.</strong> The seeded list contains two
 * distinct classes both charging 4% — {@code 1040} (a rate in its own right) and {@code 1041}
 * (the island-reduced counterpart of 6% under αρ.31 ν.5057/2023). They are the same percentage
 * with different legal bases and different myDATA codes, so any lookup by rate is ambiguous by
 * construction. {@link VatClassService} deliberately offers no such lookup.
 *
 * @param code the invoicing-system code, e.g. {@code "1410"} for 24%. Unique.
 * @param ratePercent a percentage, not a fraction — 24% is {@code 24.000000}, not {@code 0.24}.
 *     Percent because that is how AADE, the accountant and every invoice state it; converting
 *     once in {@link #multiplier()} is safer than storing a form nobody reads.
 * @param reducedCounterpartId the island-reduced class this one maps to, or null. Present on the
 *     mainland rate and pointing at the reduced one — the 24% class points at the 17% class.
 *     Captured as data only: nothing in Phase 1 switches rate by destination.
 */
public record VatClassView(
        long id,
        String code,
        String description,
        BigDecimal ratePercent,
        Long reducedCounterpartId,
        boolean active) {

    /**
     * Decimal places for a VAT rate.
     *
     * <p>Six, matching {@code numeric(19,6)} in the schema and the same width as a quantity or
     * unit cost. That is the precision class for a <em>multiplier</em>, as opposed to
     * {@link Money#SCALE} for a posted amount: a rate is multiplied by money and the product is
     * rounded once, at the end, so the rate itself must not be the thing that loses precision.
     * Every current Greek rate is a whole number, but the column is not the place to bet on
     * that staying true.
     */
    public static final int RATE_SCALE = 6;

    private static final BigDecimal MAX_RATE_PERCENT = new BigDecimal("100");

    public VatClassView {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(ratePercent, "ratePercent");
        if (ratePercent.scale() > RATE_SCALE) {
            throw new IllegalArgumentException(
                    "VAT rate %s has %d decimal places, but at most %d are allowed."
                            .formatted(ratePercent.toPlainString(), ratePercent.scale(),
                                    RATE_SCALE));
        }
        if (ratePercent.signum() < 0 || ratePercent.compareTo(MAX_RATE_PERCENT) > 0) {
            throw new IllegalArgumentException(
                    "VAT rate %s is not a percentage between 0 and 100. A rate given as a "
                            .formatted(ratePercent.toPlainString())
                            + "fraction (0.24 for 24%) would silently undercharge by a factor "
                            + "of 100, so it is refused rather than accepted as 0.24%.");
        }
        // Normalised so equality compares the rate rather than however precisely it arrived,
        // for the same reason Money and Quantity fix their scale.
        ratePercent = ratePercent.setScale(RATE_SCALE);
    }

    /**
     * The rate as a fraction for multiplying against an amount — 24% becomes {@code 0.24}.
     *
     * <p>Exact and never throws: {@code movePointLeft} is a decimal shift, where dividing by 100
     * would have to be told what to do about a non-terminating result.
     */
    public BigDecimal multiplier() {
        return ratePercent.movePointLeft(2);
    }

    /**
     * The VAT on a net amount, rounded once with the mode stated explicitly.
     *
     * <p>Arithmetic only. This says nothing about which accounts the VAT posts to or whether it
     * is computed per line or per document — that is unresolved (PROGRESS.md Q14) and belongs to
     * the journal engine. It lives here so the multiply-and-round-once step exists in exactly one
     * place instead of being rewritten at every call site, which is where per-line cent
     * discrepancies come from.
     */
    public Money vatOn(Money netAmount, RoundingMode roundingMode) {
        Objects.requireNonNull(netAmount, "netAmount");
        Objects.requireNonNull(roundingMode, "roundingMode");
        return netAmount.times(multiplier(), roundingMode);
    }

    /** True for a 0% class, which is distinct from an exempt line — see {@link VatExemptionReasonView}. */
    public boolean isZeroRated() {
        return ratePercent.signum() == 0;
    }

    /** True when this class has an island-reduced counterpart recorded against it. */
    public boolean hasReducedCounterpart() {
        return reducedCounterpartId != null;
    }

    public Optional<Long> reducedCounterpart() {
        return Optional.ofNullable(reducedCounterpartId);
    }
}
