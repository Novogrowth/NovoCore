package gr.novotrade.novocore.core.api.asset;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Rate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One fixed asset, as everything outside the core sees it — the sub-ledger behind
 * {@code Fixed assets at cost} and {@code Fixed assets accumulated depreciation}.
 *
 * <p><strong>No monetary fields at all.</strong> Not acquisition cost, not accumulated
 * depreciation, not carrying value. Both control accounts declare {@code ASSET} as their sub-ledger,
 * so every posting to them names the asset it belongs to, and an asset's cost and accumulated
 * depreciation are therefore sums of its journal lines — computed on read, exactly as no account
 * carries a stored balance. A stored acquisition cost would be a second copy of a number the ledger
 * already holds, free to drift from it after the first correcting entry.
 *
 * <p>The consequence to be aware of: until the journal exists (step 7) an asset has no cost, so this
 * record is an asset <em>register</em> rather than a valuation. That is the same shape as a product
 * having no stock until lots exist.
 *
 * <p><strong>What Q12 asked about and what was built.</strong> Straight-line only (brief §5), so
 * there is no depreciation-method field — a single-valued column is dead weight, and a second method
 * arriving is a migration with a decision attached rather than a value nobody set. No useful-life
 * field either: for straight-line depreciation a life is {@code 100 / rate} years, and two columns
 * that must agree are two columns that can disagree — the same argument that keeps
 * {@code normal_balance_side} out of the chart of accounts. Greek statutory rates are published as
 * percentages per category, so the rate is the form the source data actually comes in. No salvage
 * value: Greek tax depreciation writes down to zero, and it would be the one monetary field on an
 * otherwise ledger-derived record. Both are open items rather than closed ones — see
 * {@code PROGRESS.md}.
 *
 * @param depreciationRatePercent the annual straight-line rate as a percentage — {@code 10.000000}
 *     for 10% a year, not {@code 0.1}. Set by hand per asset, as answered. <strong>Null until the
 *     statutory rates are supplied by the accountant</strong>, which is a real state and not a
 *     placeholder: guessing a rate would produce a depreciation charge that looks plausible and is
 *     wrong in a filed set of accounts. {@link #canDepreciate()} is what a run must check.
 * @param depreciationStartDate when depreciation begins, if that is not the acquisition date. Null
 *     means "derive it from {@link #acquisitionDate()}", which is the ordinary case; it exists
 *     because an asset can be bought in one period and placed in service in another, and charging
 *     depreciation from the invoice date would then be wrong.
 * @param disposalDate present exactly when {@link #status()} is {@link AssetStatus#DISPOSED}.
 */
public record AssetView(
        long id,
        String code,
        @Mandatory String name,
        @Mandatory LocalDate acquisitionDate,
        Rate depreciationRatePercent,
        LocalDate depreciationStartDate,
        @Mandatory AssetStatus status,
        LocalDate disposalDate) {

    /**
     * Decimal places for a depreciation rate.
     *
     * <p>Six, the schema's width for a <em>multiplier</em> — the same class of value as a VAT rate
     * or a quantity, as opposed to a posted amount's two. The rate multiplies a cost and the product
     * is rounded once, so the rate must not be what loses the precision.
     */
    public static final int RATE_SCALE = Rate.SCALE;

    /**
     * The lowest depreciation rate accepted, as a percentage.
     *
     * <p>One percent, which is a hundred-year useful life. No Greek statutory category comes close
     * — the lowest is buildings, and the highest life among them is a small fraction of that — so a
     * rate below this is overwhelmingly likely to be a fraction typed where a percentage was meant.
     *
     * <p><strong>The bound itself moved to {@link Rate} in step 15a.</strong> It existed here and,
     * separately, on {@code VatClassView} — the same factor-of-100 trap guarded twice by two pieces
     * of code that did not know about each other. What is left here is the name, and the one rule
     * that is genuinely this type's own: <strong>zero is refused</strong>, because "never
     * depreciates" is what a null rate already says and two ways to state it is one too many.
     * {@code Rate} permits zero, since the zero-rated VAT class is real.
     */
    public static final BigDecimal MIN_RATE_PERCENT = Rate.MIN_NON_ZERO_PERCENT;

    public AssetView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(acquisitionDate, "acquisitionDate");
        Objects.requireNonNull(status, "status");

        if (depreciationRatePercent != null) {
            // Rate already refuses anything strictly between 0 and 1, and anything above 100. The
            // only thing left to say is that an asset's rate may not be exactly zero.
            if (depreciationRatePercent.isZero()) {
                throw new IllegalArgumentException(
                        "A depreciation rate of zero is not a rate — an asset that never "
                                + "depreciates is what a null rate already says, and having two "
                                + "ways to state it means a report has to handle both. Use null "
                                + "for \"rate not yet known\" or \"does not depreciate\".");
            }
        }

        // Biconditional, like the chart's control-account rule: a disposed asset with no disposal
        // date cannot be reported in the period it left, and a disposal date on an asset still in
        // use is a date nothing will ever act on.
        if ((status == AssetStatus.DISPOSED) != (disposalDate != null)) {
            throw new IllegalArgumentException(
                    "Asset '" + name + "' is " + status + " with disposalDate=" + disposalDate
                            + ". A disposal date is required exactly when the asset is disposed.");
        }
        if (disposalDate != null && disposalDate.isBefore(acquisitionDate)) {
            throw new IllegalArgumentException(
                    "Asset '" + name + "' was disposed of on " + disposalDate
                            + ", before it was acquired on " + acquisitionDate + ".");
        }
        if (depreciationStartDate != null && depreciationStartDate.isBefore(acquisitionDate)) {
            throw new IllegalArgumentException(
                    "Asset '" + name + "' would start depreciating on " + depreciationStartDate
                            + ", before it was acquired on " + acquisitionDate + ".");
        }
    }

    public Optional<String> codeIfAny() {
        return Optional.ofNullable(code);
    }

    /** Empty until the statutory rate for this asset's category has been supplied. */
    public Optional<Rate> depreciationRate() {
        return Optional.ofNullable(depreciationRatePercent);
    }

    /** The acquisition date when no separate in-service date was recorded. */
    public LocalDate effectiveDepreciationStartDate() {
        return depreciationStartDate == null ? acquisitionDate : depreciationStartDate;
    }

    public Optional<LocalDate> disposal() {
        return Optional.ofNullable(disposalDate);
    }

    /**
     * True when a depreciation run has everything it needs for this asset.
     *
     * <p>False for a disposed asset, and false while the rate is unknown. The second case is the
     * one that matters: a run must skip and report these rather than substitute a rate, because an
     * invented rate produces a charge in the accounts that nobody chose.
     */
    public boolean canDepreciate() {
        return status.depreciates() && depreciationRatePercent != null;
    }

    /**
     * The rate as a fraction for multiplying against a cost — 10% becomes {@code 0.10}.
     *
     * <p>Exact and never throws: a decimal point shift, where dividing by 100 would have to be told
     * what to do about a non-terminating result.
     *
     * @throws IllegalStateException if no rate has been set, rather than defaulting to something
     */
    public BigDecimal annualMultiplier() {
        if (depreciationRatePercent == null) {
            throw new IllegalStateException(
                    "Asset '" + name + "' has no depreciation rate set, so no depreciation can be "
                            + "computed for it. The statutory rate for its category has to be "
                            + "supplied — it must not be assumed.");
        }
        return depreciationRatePercent.multiplier();
    }
}
