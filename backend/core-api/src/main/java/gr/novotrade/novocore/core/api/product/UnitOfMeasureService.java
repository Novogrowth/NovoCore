package gr.novotrade.novocore.core.api.product;

import java.util.List;
import java.util.Optional;

/**
 * Units of measure: a runtime-editable lookup (Q34).
 *
 * <p>Editable at runtime is the whole point of it being a table — an operator adds a unit the
 * business starts using without a code change or a migration, the same way a VAT class can be
 * added when a statute introduces one.
 *
 * <p><strong>Neither the code nor the myDATA code is editable.</strong> A unit's code is what
 * products and, from step 6, lots refer to; a myDATA code is what gets transmitted. Correcting
 * either in place would change the meaning of records already written against it. A unit that turns
 * out to be wrong is deactivated and replaced, which is how VAT classes handle a rate change.
 *
 * <p>There is no delete, for the reason stated across the core: a product referring to the unit
 * would be left pointing at nothing.
 */
public interface UnitOfMeasureService {

    /** Every unit, active and inactive, by code. */
    List<UnitOfMeasureView> all();

    /** Active units only — what a unit picker should offer. */
    List<UnitOfMeasureView> active();

    /**
     * Units whose code or name contains {@code term}, ignoring case and accents.
     *
     * <p>Row 7 of the search target list. A blank or null term matches everything, so a call site
     * passes an absent query parameter straight through without a conditional of its own.
     *
     * <p><strong>The myDATA code is deliberately not searched.</strong> It is NULL on every seeded
     * unit — the verified ΑΑΔΕ list has not been supplied — so a box that searched it would match
     * nothing on every installation that exists today. {@link #withoutMydataCode()} is the route
     * that answers the question actually being asked about that column.
     */
    List<UnitOfMeasureView> search(String term, boolean activeOnly);

    Optional<UnitOfMeasureView> find(long id);

    /** @throws UnitOfMeasureNotFoundException if absent */
    UnitOfMeasureView require(long id);

    /** By NovoCore's own code for the unit, e.g. {@code "KILOGRAM"}. */
    Optional<UnitOfMeasureView> findByCode(String code);

    /** @throws UnitOfMeasureNotFoundException if absent */
    UnitOfMeasureView requireByCode(String code);

    /**
     * Units with no myDATA mapping yet.
     *
     * <p>Exists so "which units cannot be transmitted?" is a question the system answers before
     * phase 7 asks it, rather than one discovered when AADE rejects an invoice. Every seeded unit is
     * currently in this list.
     */
    List<UnitOfMeasureView> withoutMydataCode();

    /**
     * Adds a unit.
     *
     * @throws InvalidUnitOfMeasureException if the code, name or myDATA code duplicates an existing
     *     unit, or any of them is blank
     */
    UnitOfMeasureView create(NewUnitOfMeasure request);

    /**
     * Renames a unit — the display name only, never the code.
     *
     * @throws InvalidUnitOfMeasureException if the name duplicates another unit
     */
    UnitOfMeasureView rename(long id, String newName);

    /**
     * Records the AADE unit code once the verified list is supplied.
     *
     * <p>Settable exactly once per unit, and not changeable afterwards: a myDATA code that has been
     * transmitted describes documents already filed under it.
     *
     * @throws InvalidUnitOfMeasureException if this unit already has a code, or another unit has
     *     the given one
     */
    UnitOfMeasureView recordMydataCode(long id, String mydataCode);

    /**
     * Changes whether fractional quantities are meaningful in this unit.
     *
     * <p>Editable because it is a judgement about how the business sells, not a physical fact — a
     * pack could legitimately start being split.
     */
    UnitOfMeasureView changeFractionalQuantityAllowed(long id, boolean allowed);

    /**
     * Takes a unit out of circulation.
     *
     * @throws InvalidUnitOfMeasureException if any product still uses it. Refused rather than
     *     cascaded: a product whose unit has been retired has a quantity that no longer states what
     *     it counts, and silently leaving it is worse than making someone move it first.
     */
    void deactivate(long id);

    void reactivate(long id);
}
