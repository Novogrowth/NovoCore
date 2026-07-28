package gr.novotrade.novocore.core.api.product;

import gr.novotrade.novocore.core.api.shared.Quantity;

/**
 * The unit a product's quantity is expressed in.
 *
 * <p>Why this exists at all: {@link Quantity} carries six decimal places rather than being an
 * integer count because coffee sells by weight, and a quantity of {@code 0.250} is only meaningful
 * next to the unit it is 0.250 of.
 *
 * <p><strong>An enum for now, and this may need to become a lookup table.</strong> Prosvasis Go
 * holds "Μονάδες μέτρησης" as a runtime-editable list, and myDATA has its own unit codes that a
 * transmitted line has to carry — either of which is an argument for a table, the way VAT classes
 * are a table. It is an enum here because the set NovoCore actually needs is small, known, and
 * physical rather than statutory, and a lookup table nobody can yet map to a myDATA code would be
 * a table with a column waiting for a purpose. Recorded as an open question rather than settled.
 *
 * <p>{@link #allowsFractionalQuantity()} is the part with behaviour attached: three of a product
 * sold by the piece is three, and 2.5 pieces is a data-entry error worth catching rather than a
 * quantity to average out later.
 */
public enum UnitOfMeasure {

    /** Discrete items. Fractions are refused. */
    PIECE(false),

    /** A set or kit sold as one unit. Fractions are refused. */
    SET(false),

    /** A pack sold as one unit — a case of twelve, a box of filters. Fractions are refused. */
    PACK(false),

    /** Kilograms. The unit most green and roasted coffee is bought and sold in. */
    KILOGRAM(true),

    GRAM(true),

    LITRE(true),

    MILLILITRE(true),

    /** Metres — tubing, cabling. */
    METRE(true);

    private final boolean fractional;

    UnitOfMeasure(boolean fractional) {
        this.fractional = fractional;
    }

    /**
     * True when a fractional quantity is meaningful in this unit.
     *
     * <p>Nothing enforces this yet — quantities arrive with inventory lots in step 6, which is
     * where the check belongs. It is stated here, with the unit, so that step 6 reads the rule off
     * the unit instead of re-deriving "which units can be fractional" from a list of its own.
     */
    public boolean allowsFractionalQuantity() {
        return fractional;
    }
}
