package gr.novotrade.novocore.core.api.product;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.Objects;
import java.util.Optional;

/**
 * A unit a product's quantity is expressed in.
 *
 * <p>Why this exists at all: {@link Quantity} carries six decimal places rather than being an
 * integer count because coffee sells by weight, and a quantity of {@code 0.250} is only meaningful
 * next to the unit it is 0.250 of.
 *
 * <p><strong>A table rather than an enum (Q34).</strong> Step 5 built this as a Java enum, on the
 * reasoning that the set NovoCore needs is small, known and physical rather than statutory. Two
 * things outweighed that, and both are the argument V5 made for VAT classes: Prosvasis Go holds
 * "Μονάδες μέτρησης" as an editable list, and myDATA has its own unit codes that a transmitted
 * invoice line must carry. The second is decisive — a myDATA code has to live somewhere, and
 * hanging AADE's data off an enum constant is exactly the shape that made
 * {@link VatExemptionReasonView} a table.
 *
 * @param code NovoCore's own stable identifier for the unit — not AADE's and not Go's.
 * @param mydataCode AADE's unit code, or null where no mapping is known. Null means "no mapping
 *     exists", not "not filled in yet": the verified AADE list has not been supplied, and guessing
 *     at it would produce a plausible wrong code that gets transmitted. Phase 7 must refuse a line
 *     whose unit has no code rather than composing one — {@link #requireMydataCode()} is how.
 * @param fractionalQuantityAllowed whether a fractional quantity is meaningful in this unit
 */
public record UnitOfMeasureView(
        long id,
        @Mandatory String code,
        @Mandatory String name,
        boolean fractionalQuantityAllowed,
        String mydataCode,
        boolean active) {

    public UnitOfMeasureView {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }

    /**
     * True when a fractional quantity is meaningful in this unit.
     *
     * <p>Nothing enforces this yet — quantities arrive with inventory lots in step 6, which is
     * where the check belongs. It is stated on the unit so that step 6 reads the rule off here
     * instead of keeping a list of its own of which units may be fractional.
     */
    public boolean allowsFractionalQuantity() {
        return fractionalQuantityAllowed;
    }

    /** Empty when no myDATA mapping is known for this unit. */
    public Optional<String> mydataCodeIfAny() {
        return Optional.ofNullable(mydataCode);
    }

    /**
     * The myDATA unit code, for a caller about to transmit one.
     *
     * <p>Throws rather than returning null, for the reason
     * {@link VatExemptionReasonView#requireMydataCode()} does: a line transmitted with a blank unit
     * is a rejected or misdeclared filing either way, and the failure should name the unit that has
     * no mapping instead of surfacing as an AADE error later.
     */
    public String requireMydataCode() {
        if (mydataCode == null) {
            throw new IllegalStateException(
                    "Unit of measure '" + code + "' has no myDATA code, so a line in it cannot be "
                            + "transmitted. Supply the mapping rather than sending a composed or "
                            + "blank value.");
        }
        return mydataCode;
    }
}
