package gr.novotrade.novocore.core.api.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary amount and its currency. The single expression of {@code CLAUDE.md} rule 5 —
 * money is always {@code BigDecimal}, never {@code double} or {@code float}.
 *
 * <p><strong>Always exactly {@value #SCALE} decimal places.</strong> The amount is normalised
 * on construction, which is what makes equality safe: {@code BigDecimal} considers
 * {@code 1.10} and {@code 1.1} unequal because it compares scale as well as value, so a
 * {@code Money} of free scale would be a quiet source of "identical amounts don't match"
 * bugs in reconciliation and open item matching. Fixing the scale removes that whole class of
 * problem, and lets this be a record with generated {@code equals}.
 *
 * <p><strong>A value with more precision is rejected, not rounded.</strong> {@link #of} throws
 * rather than silently discarding a fraction of a cent. Rounding is a decision with
 * accounting consequences — brief §6 requires that rounding be independently computed,
 * compared against the source document, and either auto-posted below a threshold or flagged
 * for review — so it must be requested explicitly through {@link #rounded} or
 * {@link #times(BigDecimal, RoundingMode)}, where it is visible in the code doing it.
 *
 * <p>Unit costs are deliberately <em>not</em> represented by this type. After proportional
 * landed-cost allocation a lot's unit cost needs more than two decimals (brief §4), so it
 * carries a wider scale and gets its own type when lots arrive in build step 6. Keeping the
 * two apart means a 6-decimal unit cost cannot reach a 2-decimal posting without an explicit
 * rounding step.
 *
 * <p>Currency is carried even though all Phase 1 logic is EUR-only; see ADR 0005 for why.
 * Arithmetic across two currencies throws — it never converts, and never silently picks one.
 */
public record Money(
        @Mandatory BigDecimal amount,
        @Mandatory Currency currency) implements Comparable<Money> {

    /** Decimal places for a posted monetary amount. Matches {@code numeric(19,2)} in the schema. */
    public static final int SCALE = 2;

    /** Convenience for the only currency Phase 1 handles. */
    public static final Currency EUR = Currency.getInstance("EUR");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException(
                    "%s has %d decimal places, but a monetary amount allows at most %d. Rounding "
                            .formatted(amount.toPlainString(), amount.scale(), SCALE)
                            + "is never implicit: use Money.rounded(..) and state the rounding "
                            + "mode, so the decision is visible where it is made.");
        }
        // Exact: widening 1.1 to 1.10 discards nothing, and normalising here is what makes
        // equals() behave the way callers expect.
        amount = amount.setScale(SCALE);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /**
     * Parses from a decimal string. This is the correct way in from an external system: going
     * via {@code double} would inherit an error before the value ever reached
     * {@code BigDecimal}.
     */
    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money ofEur(String amount) {
        return of(amount, EUR);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /** Rounds an arbitrarily precise value into a monetary amount, stating the mode explicitly. */
    public static Money rounded(BigDecimal amount, Currency currency, RoundingMode roundingMode) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(roundingMode, "roundingMode");
        return new Money(amount.setScale(SCALE, roundingMode), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    /**
     * Multiplies and rounds, stating the mode. Used for VAT, proportional allocation, and
     * anything where the exact product has more precision than a posting can hold.
     */
    public Money times(BigDecimal multiplicand, RoundingMode roundingMode) {
        Objects.requireNonNull(multiplicand, "multiplicand");
        Objects.requireNonNull(roundingMode, "roundingMode");
        return new Money(amount.multiply(multiplicand).setScale(SCALE, roundingMode), currency);
    }

    /**
     * Multiplies where the result must be exact, throwing if it would need rounding. Prefer
     * this for whole-unit quantities, so an unexpected fraction fails loudly instead of being
     * absorbed.
     */
    public Money timesExact(BigDecimal multiplicand) {
        Objects.requireNonNull(multiplicand, "multiplicand");
        BigDecimal product = amount.multiply(multiplicand);
        if (product.stripTrailingZeros().scale() > SCALE) {
            throw new ArithmeticException(
                    "%s x %s = %s, which needs more than %d decimal places. Use "
                            .formatted(amount.toPlainString(), multiplicand.toPlainString(),
                                    product.toPlainString(), SCALE)
                            + "times(multiplicand, roundingMode) if rounding is intended here.");
        }
        return new Money(product.setScale(SCALE), currency);
    }

    /** The exact product, unrounded, for callers that must sum before rounding once. */
    public BigDecimal multiplyExactly(BigDecimal multiplicand) {
        Objects.requireNonNull(multiplicand, "multiplicand");
        return amount.multiply(multiplicand);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public int signum() {
        return amount.signum();
    }

    public boolean isEur() {
        return EUR.equals(currency);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot combine %s with %s. NovoCore does not convert between currencies "
                            .formatted(this, other)
                            + "and will not silently pick one of them; see ADR 0005.");
        }
    }
}
