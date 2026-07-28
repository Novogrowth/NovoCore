package gr.novotrade.novocore.core.api.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * The cost of one unit of stock, with its currency. The type {@link Money} has been pointing at
 * since step 2 — see its class comment — built here because inventory lots now exist to carry one.
 *
 * <p><strong>Why this is not {@code Money}.</strong> {@code Money} is exactly
 * {@value Money#SCALE} decimal places and <em>rejects</em> anything more precise, deliberately, so
 * that rounding is always an explicit decision. A unit cost cannot live inside that: brief §4's
 * proportional landed-cost allocation divides a freight total across lines by value, and the result
 * per unit is a repeating decimal far more often than not. Two euros of freight over three units is
 * 0.666667 per unit, and forcing that to 0.67 before it is multiplied back out overstates the lot by
 * a cent for no reason anyone can later find.
 *
 * <p>So a unit cost carries {@value #SCALE} decimals — the same precision class as
 * {@link Quantity}, and for the same reason. It is a <em>multiplier</em>, not a posted amount: the
 * one rounding that matters happens once, when a quantity is extended at a cost and the product
 * becomes a journal line. {@link #extend} is that step, and it is the only way out of this type into
 * {@code Money}.
 *
 * <p>Keeping the two types apart is the point. A six-decimal cost cannot reach a two-decimal posting
 * without passing through a method that names its rounding mode, so the place where precision is
 * given up is visible in the code that gives it up.
 *
 * <p><strong>Zero is allowed; negative is not.</strong> A zero-cost lot is a real thing — a
 * supplier's free sample, promotional stock, a warranty replacement received at no charge — and it
 * is not the ambiguous state a zero <em>selling price</em> is, because nothing is being given away
 * by recording it. A negative unit cost is not a fact about any lot; a purchase credit reduces the
 * quantity or reverses the receipt, it does not make each unit worth less than nothing.
 *
 * <p>Currency is carried for ADR 0005's reason, and arithmetic never crosses two of them.
 */
public record UnitCost(BigDecimal value, Currency currency) implements Comparable<UnitCost> {

    /**
     * Decimal places for a unit cost. Matches {@code numeric(19,6)} in the schema, and matches
     * {@link Quantity#SCALE} rather than {@link Money#SCALE} because this is a multiplier.
     */
    public static final int SCALE = 6;

    public UnitCost {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(currency, "currency");
        if (value.scale() > SCALE) {
            throw new IllegalArgumentException(
                    "%s has %d decimal places, but a unit cost allows at most %d. Use "
                            .formatted(value.toPlainString(), value.scale(), SCALE)
                            + "UnitCost.rounded(..) and state the rounding mode, so the decision is "
                            + "visible where it is made.");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Unit cost " + value.toPlainString() + " is negative. A purchase credit reduces "
                            + "the quantity received or reverses the receipt; it does not make a "
                            + "unit worth less than nothing. Zero is allowed — a free sample is a "
                            + "real lot.");
        }
        // Exact widening, for the reason Money's constructor documents: it is what makes equals()
        // compare values rather than however precisely the number happened to arrive.
        value = value.setScale(SCALE);
    }

    public static UnitCost of(BigDecimal value, Currency currency) {
        return new UnitCost(value, currency);
    }

    /**
     * Parses from a decimal string, which is the correct way in from an external system: going via
     * {@code double} would inherit an error before the value ever reached {@code BigDecimal}.
     */
    public static UnitCost of(String value, Currency currency) {
        return new UnitCost(new BigDecimal(value), currency);
    }

    public static UnitCost ofEur(String value) {
        return of(value, Money.EUR);
    }

    public static UnitCost zero(Currency currency) {
        return new UnitCost(BigDecimal.ZERO, currency);
    }

    /** Rounds an arbitrarily precise value into a unit cost, stating the mode explicitly. */
    public static UnitCost rounded(BigDecimal value, Currency currency, RoundingMode roundingMode) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(roundingMode, "roundingMode");
        return new UnitCost(value.setScale(SCALE, roundingMode), currency);
    }

    /**
     * A posted amount read as a unit cost — an invoice line's unit price, say.
     *
     * <p>Always exact: widening two decimals to six discards nothing. This direction is safe and
     * needs no rounding mode, which is precisely why the other direction does.
     */
    public static UnitCost from(Money amount) {
        Objects.requireNonNull(amount, "amount");
        return new UnitCost(amount.amount(), amount.currency());
    }

    /**
     * Extends this cost across a quantity, rounding once into a postable amount.
     *
     * <p>The multiplication happens at full precision and the single rounding is at the end. This is
     * the only route from a unit cost to {@link Money}, so every place that gives up precision does
     * it here and says which way.
     */
    public Money extend(Quantity quantity, RoundingMode roundingMode) {
        Objects.requireNonNull(quantity, "quantity");
        return Money.rounded(value.multiply(quantity.value()), currency, roundingMode);
    }

    /**
     * The exact extended value, unrounded, for a caller that must sum several lines before rounding
     * once. Used by proportional allocation, where rounding each line first is what makes the parts
     * fail to add up to the whole.
     */
    public BigDecimal extendExactly(Quantity quantity) {
        Objects.requireNonNull(quantity, "quantity");
        return value.multiply(quantity.value());
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public boolean isEur() {
        return Money.EUR.equals(currency);
    }

    @Override
    public int compareTo(UnitCost other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot compare %s with %s. NovoCore does not convert between currencies and "
                            .formatted(this, other) + "will not silently pick one; see ADR 0005.");
        }
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString() + " " + currency.getCurrencyCode() + "/unit";
    }
}
