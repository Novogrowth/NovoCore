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

    /**
     * The lowest non-zero rate accepted, as a percentage.
     *
     * <p>Zero is legitimate — the {@code '0'} class is real, seeded, and legally distinct from an
     * exempt line. Anything strictly between zero and one is not: no VAT regime charges a fraction
     * of a percent, which leaves the whole interval available as a trap for the factor-of-100
     * error.
     *
     * <p><strong>This is the bound a plain 0–100 range was missing.</strong> V5 claimed its 0–100
     * CHECK made a rate written as a fraction fail loudly; it did not, because {@code 0.24} sits
     * comfortably inside it and was accepted as a quarter of one percent. The same mistake was
     * caught on {@code Asset}'s depreciation rate in step 5, and it matters more here: brief §6
     * notes that an undercharge is not recoverable from the customer once an invoice is issued.
     */
    public static final BigDecimal MIN_NON_ZERO_RATE_PERCENT = new BigDecimal("1");

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
        if (!isAcceptableRate(ratePercent)) {
            throw new IllegalArgumentException(
                    "VAT rate %s is not a percentage. Valid values are exactly 0 — the zero-rated "
                            .formatted(ratePercent.toPlainString())
                            + "class is real and distinct from an exempt line — or between 1 and "
                            + "100. A rate given as a fraction (0.24 for 24%) would otherwise be "
                            + "accepted as 0.24% and undercharge by a factor of 100, which is not "
                            + "recoverable from the customer once the invoice is issued.");
        }
        // Normalised so equality compares the rate rather than however precisely it arrived,
        // for the same reason Money and Quantity fix their scale.
        ratePercent = ratePercent.setScale(RATE_SCALE);
    }

    /**
     * Whether a value is a rate NovoCore will accept: exactly zero, or 1 through 100.
     *
     * <p>Public and static so that {@code VatClassService} applies the identical rule on the way in
     * rather than a second implementation of it that can drift. The database says the same thing in
     * {@code vat_class_rate_is_a_percentage}, so all three agree by construction.
     */
    public static boolean isAcceptableRate(BigDecimal ratePercent) {
        Objects.requireNonNull(ratePercent, "ratePercent");
        if (ratePercent.signum() == 0) {
            return true;
        }
        return ratePercent.compareTo(MIN_NON_ZERO_RATE_PERCENT) >= 0
                && ratePercent.compareTo(MAX_RATE_PERCENT) <= 0;
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
