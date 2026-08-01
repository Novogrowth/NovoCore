package gr.novotrade.novocore.core.api.tax;

import java.util.List;
import java.util.Optional;

/**
 * VAT classes: a runtime-editable lookup table, not an enum.
 *
 * <p>Built as data specifically so that a new rate can be added through the application. Greek
 * VAT rates change by statute — the 3% and island-reduced 4% classes exist because of
 * αρ.31 ν.5057/2023 — and each change would otherwise be a code change and a deployment.
 *
 * <p><strong>No lookup by rate.</strong> The seeded list contains two classes both charging 4%
 * ({@code 1040} in its own right, {@code 1041} as the island-reduced counterpart of 6%), so a
 * rate does not identify a class. Offering {@code findByRate} would return an arbitrary one of
 * them and be correct most of the time, which is worse than not offering it.
 *
 * <p><strong>Nothing here picks a rate by shipping destination.</strong> The island-reduced
 * mapping is recorded as data by {@link #mapToReducedCounterpart}; the logic that chooses
 * between a mainland rate and its reduced counterpart based on where the goods are going is
 * explicitly future scope, not Phase 1.
 */
public interface VatClassService {

    /** Every VAT class, active and inactive, ordered by rate then code. */
    List<VatClassView> all();

    /** Active classes only — what a rate dropdown should offer. */
    List<VatClassView> active();

    /**
     * Classes whose code or description contains {@code term}, ignoring case and accents.
     *
     * <p>Row 6 of the search target list. A blank or null term matches everything, so a call site
     * passes an absent query parameter straight through without a conditional of its own.
     *
     * <p><strong>The rate is not searchable</strong>, for the reason this interface already gives
     * for the absence of a lookup by rate: two classes charge 4% under different legal bases, so a
     * rate does not identify a class. Ordering is by rate then code — the order {@link #all()}
     * returns — because that is the order an accountant reads this list in.
     */
    List<VatClassView> search(String term, boolean activeOnly);

    Optional<VatClassView> find(long id);

    /** @throws VatClassNotFoundException if absent */
    VatClassView require(long id);

    /**
     * By its invoicing-system code, e.g. {@code "1410"}. The only safe way to look one up by a
     * business identifier, since the rate does not identify a class.
     */
    Optional<VatClassView> findByCode(String code);

    /** @throws VatClassNotFoundException if absent */
    VatClassView requireByCode(String code);

    /**
     * The classes that are an island-reduced counterpart of another class — the 3%, 4%
     * ({@code 1041}), 9% and 17% classes in the seeded list.
     */
    List<VatClassView> reducedCounterparts();

    /**
     * @throws InvalidVatClassException if the code duplicates an existing class, or the rate is
     *     not a percentage between 0 and 100
     */
    VatClassView create(NewVatClass request);

    /**
     * Records that {@code reducedCounterpartId} is the island-reduced equivalent of
     * {@code vatClassId} — the 24% class mapped to the 17% class, for instance.
     *
     * <p>Data only. Recording it does not make anything switch rates automatically.
     *
     * @throws InvalidVatClassException if a class is mapped to itself, if the counterpart's rate
     *     is not lower than the class's own, if the counterpart already has a counterpart of its
     *     own (mappings are one level, not a chain), or if the counterpart is already claimed by
     *     a different class
     */
    VatClassView mapToReducedCounterpart(long vatClassId, long reducedCounterpartId);

    /** Removes the island-reduced mapping from a class, leaving both classes in place. */
    VatClassView clearReducedCounterpart(long vatClassId);

    /**
     * Renames or re-describes a class. The rate is deliberately not changeable — see
     * {@link #deactivate}.
     */
    VatClassView describe(long id, String description);

    /**
     * Takes a class out of circulation without deleting it.
     *
     * <p>This is also how a rate change is handled: create the new class and deactivate the old
     * one, rather than editing a rate in place. Editing would retroactively change what every
     * invoice already issued under it appears to have charged, which is why no method here
     * changes a rate.
     */
    void deactivate(long id);

    void reactivate(long id);
}
