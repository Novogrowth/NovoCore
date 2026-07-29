package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Rate;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Output or input VAT for one VAT class over a period: the taxable base it was computed on, and the VAT
 * itself.
 *
 * <p><strong>Why this exists in step 7 rather than with the reports.</strong> Q14 puts a VAT class and a
 * taxable base on the journal line (see {@link VatDimension}). A column nothing reads is indistinguishable
 * from a column nobody thought about, so the read that justifies it lands in the same step. This is a
 * query, not a report: phase 8's VAT report and phase 7's myDATA adapter are what present it, and both
 * need the same two figures per class per direction.
 *
 * <p>Grouped by class rather than by rate, because two seeded classes both charge 4% under different
 * legal bases and different myDATA codes — collapsing them by rate would report as one thing what AADE
 * requires to be reported as two.
 *
 * @param vatClassCode the class's own code, e.g. {@code "1410"} for 24%
 * @param ratePercent a percentage, not a fraction — 24% is {@code 24.000000}
 */
public record VatTotal(
        VatDirection direction,
        long vatClassId,
        String vatClassCode,
        Rate ratePercent,
        Money taxableBase,
        Money vatAmount) {

    public VatTotal {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(vatClassCode, "vatClassCode");
        Objects.requireNonNull(ratePercent, "ratePercent");
        Objects.requireNonNull(taxableBase, "taxableBase");
        Objects.requireNonNull(vatAmount, "vatAmount");
    }

    /**
     * The VAT that the base and rate imply, for comparison against {@link #vatAmount()}.
     *
     * <p>These will usually differ by a cent or two and that is not an error: VAT is computed per line
     * and summed by rate (Q14), so the posted figure is a sum of per-line roundings while this is one
     * rounding of the summed base. The gap is the arithmetic of doing it per line, which is what the
     * document requires. It is worth being able to see, which is why the method exists — a gap of euros
     * rather than cents means something posted at the wrong rate.
     */
    public Money vatImpliedByTheRate() {
        return taxableBase.times(ratePercent.multiplier(), RoundingMode.HALF_UP);
    }

    /** How far the posted VAT is from what one rounding of the whole base would have given. */
    public Money roundingDivergence() {
        return vatAmount.minus(vatImpliedByTheRate());
    }
}
