package gr.novotrade.novocore.core.api.tax;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Rate;
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
 *     once in {@link Rate#multiplier()} is safer than storing a form nobody reads. A {@link Rate}
 *     since step 15a, which is where the bound and the wire format now live.
 * @param reducedCounterpartId the island-reduced class this one maps to, or null. Present on the
 *     mainland rate and pointing at the reduced one — the 24% class points at the 17% class.
 *     Captured as data only: nothing in Phase 1 switches rate by destination.
 */
public record VatClassView(
        long id,
        @Mandatory String code,
        @Mandatory String description,
        @Mandatory Rate ratePercent,
        Long reducedCounterpartId,
        boolean active) {

    /**
     * Decimal places for a VAT rate.
     *
     * <p>Retained as the name the VAT code reads, delegating to {@link Rate#SCALE} so there is one
     * definition. Six, matching {@code numeric(19,6)} in the schema and the same width as a quantity
     * or unit cost — the precision class for a <em>multiplier</em>, as opposed to
     * {@link Money#SCALE} for a posted amount.
     */
    public static final int RATE_SCALE = Rate.SCALE;

    /**
     * The lowest non-zero rate accepted, as a percentage.
     *
     * <p><strong>The rule itself moved to {@link Rate} in step 15a</strong>, which is what closed
     * the duplication between this and {@code AssetView}: the same factor-of-100 trap was guarded
     * twice, in two slightly different ways, and neither knew about the other. What is left here is
     * the name the VAT code already used.
     *
     * <p>Zero is legitimate for VAT — the {@code '0'} class is real, seeded, and legally distinct
     * from an exempt line. Anything strictly between zero and one is not: no VAT regime charges a
     * fraction of a percent, which leaves the whole interval available as a trap. V5 claimed its
     * 0–100 CHECK made a rate written as a fraction fail loudly; it did not, because {@code 0.24}
     * sits comfortably inside it and was accepted as a quarter of one percent.
     */
    public static final BigDecimal MIN_NON_ZERO_RATE_PERCENT = Rate.MIN_NON_ZERO_PERCENT;

    public VatClassView {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(ratePercent, "ratePercent");
    }

    /**
     * Whether a value is a rate NovoCore will accept: exactly zero, or 1 through 100.
     *
     * <p>Delegates to {@link Rate#isAcceptable}, so {@code VatClassService}, this view and the
     * database's {@code vat_class_rate_is_a_percentage} all state one rule rather than three that
     * can drift.
     */
    public static boolean isAcceptableRate(BigDecimal ratePercent) {
        return Rate.isAcceptable(ratePercent);
    }

    /**
     * The rate as a fraction for multiplying against an amount — 24% becomes {@code 0.24}.
     */
    public BigDecimal multiplier() {
        return ratePercent.multiplier();
    }

    /**
     * The VAT on a net amount, rounded once with the mode stated explicitly.
     *
     * <p>Arithmetic only. This says nothing about which accounts the VAT posts to or whether it
     * is computed per line or per document — that is unresolved (HISTORY.md Q14) and belongs to
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
        return ratePercent.isZero();
    }

    /** True when this class has an island-reduced counterpart recorded against it. */
    public boolean hasReducedCounterpart() {
        return reducedCounterpartId != null;
    }

    public Optional<Long> reducedCounterpart() {
        return Optional.ofNullable(reducedCounterpartId);
    }
}
