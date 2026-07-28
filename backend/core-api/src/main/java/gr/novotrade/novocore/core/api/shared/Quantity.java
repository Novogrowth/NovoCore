package gr.novotrade.novocore.core.api.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A quantity of stock. {@code BigDecimal} for the same reason money is — {@code CLAUDE.md}
 * rule 5 — and because quantities feed straight into cost calculations, so imprecision here
 * becomes imprecision in COGS.
 *
 * <p>Carries {@value #SCALE} decimal places rather than being an integer count, because the
 * unit of measure is a Product-level field (brief §5) and not every product is sold in whole
 * units — weight-based coffee being the obvious case. As with {@link Money}, the scale is
 * fixed on construction so that equality compares values rather than the incidental precision
 * of however the number arrived.
 *
 * <p>Negative quantities are permitted. They are meaningful: a reversal, a credit note, or a
 * correction to a count. Whether stock is allowed to go negative in aggregate is a separate
 * policy question that belongs to the inventory service, not to this type.
 */
public record Quantity(BigDecimal value) implements Comparable<Quantity> {

    /**
     * Decimal places for a quantity. Matches {@code numeric(19,6)} in the schema, and is
     * deliberately wider than {@link Money#SCALE}: a fractional quantity multiplied by a unit
     * cost has to stay precise until the result is rounded once, at the point of posting.
     */
    public static final int SCALE = 6;

    public static final Quantity ZERO = Quantity.of(BigDecimal.ZERO);

    public Quantity {
        Objects.requireNonNull(value, "value");
        if (value.scale() > SCALE) {
            throw new IllegalArgumentException(
                    "%s has %d decimal places, but a quantity allows at most %d."
                            .formatted(value.toPlainString(), value.scale(), SCALE));
        }
        value = value.setScale(SCALE);
    }

    public static Quantity of(BigDecimal value) {
        return new Quantity(value);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public static Quantity of(long units) {
        return new Quantity(BigDecimal.valueOf(units));
    }

    public Quantity plus(Quantity other) {
        Objects.requireNonNull(other, "other");
        return new Quantity(value.add(other.value));
    }

    public Quantity minus(Quantity other) {
        Objects.requireNonNull(other, "other");
        return new Quantity(value.subtract(other.value));
    }

    public Quantity negated() {
        return new Quantity(value.negate());
    }

    /**
     * Multiplies one quantity by another — a per-unit quantity by a number of units.
     *
     * <p>Needed because {@code value().multiply(other.value())} produces up to twelve decimals and
     * this type allows six, so the naive version throws on perfectly ordinary input: one whole bundle
     * containing one whole grinder is {@code 1.000000 x 1.000000 = 1.000000000000}. The extra places
     * there are all zeros, and widening them away is exact.
     *
     * <p><strong>Exact, and throws rather than rounding.</strong> If the product genuinely needs more
     * than {@value #SCALE} decimals, then the answer cannot be expressed as a quantity at all and
     * silently rounding it would misstate how much stock leaves a shelf. That is the same stance
     * {@link Money#timesExact} takes, and for the same reason: a quantity is a physical count, so an
     * unrepresentable one is a modelling error and not a rounding question.
     */
    public Quantity times(Quantity multiplier) {
        Objects.requireNonNull(multiplier, "multiplier");
        BigDecimal product = value.multiply(multiplier.value);
        BigDecimal significant = product.stripTrailingZeros();
        if (significant.scale() > SCALE) {
            throw new ArithmeticException(
                    "%s x %s = %s, which needs more than %d decimal places and therefore is not a "
                            .formatted(value.toPlainString(), multiplier.value.toPlainString(),
                                    product.toPlainString(), SCALE)
                            + "quantity anything could be counted in. Rounding it would misstate how "
                            + "much stock moves.");
        }
        return new Quantity(significant.setScale(SCALE));
    }

    /** The smaller of the two. Used by FIFO consumption to take what a lot can supply. */
    public Quantity min(Quantity other) {
        Objects.requireNonNull(other, "other");
        return value.compareTo(other.value) <= 0 ? this : other;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isNegative() {
        return value.signum() < 0;
    }

    public int signum() {
        return value.signum();
    }

    /**
     * Extends this quantity at the given unit cost, rounding once to a monetary amount.
     *
     * <p>The multiplication happens at full precision and is rounded exactly once at the end.
     * Rounding the unit cost first and then multiplying is how per-line errors of a cent or two
     * appear and then fail to reconcile against the source document.
     */
    public Money extendedAt(BigDecimal unitCost, java.util.Currency currency,
            RoundingMode roundingMode) {
        Objects.requireNonNull(unitCost, "unitCost");
        return Money.rounded(value.multiply(unitCost), currency, roundingMode);
    }

    @Override
    public int compareTo(Quantity other) {
        Objects.requireNonNull(other, "other");
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
