package gr.novotrade.novocore.core.api.tax;

import java.util.List;
import java.util.Optional;

/**
 * The official AADE VAT exemption reasons.
 *
 * <p><strong>Currently unseeded.</strong> The structure is built; the ~29 rows are supplied
 * separately, because these are legally meaningful codes transmitted to AADE and transcribing
 * them from a screenshot is exactly the kind of thing that should be done once, deliberately,
 * rather than guessed at. Until then {@link #all()} returns empty, and any feature that requires
 * a reason must fail loudly rather than proceed without one.
 */
public interface VatExemptionReasonService {

    /** Every reason, active and inactive, in AADE code order. */
    List<VatExemptionReasonView> all();

    /** Active reasons only — what an "Exempt" picker should offer. */
    List<VatExemptionReasonView> active();

    Optional<VatExemptionReasonView> find(long id);

    /** @throws VatExemptionReasonNotFoundException if absent */
    VatExemptionReasonView require(long id);

    /** By AADE code. Gaps are expected: some numbers in the 1–31 range are retired. */
    Optional<VatExemptionReasonView> findByCode(int code);

    /** @throws VatExemptionReasonNotFoundException if absent */
    VatExemptionReasonView requireByCode(int code);

    /**
     * @throws InvalidVatExemptionReasonException if the AADE code or the myDATA string duplicates
     *     an existing reason
     */
    VatExemptionReasonView create(NewVatExemptionReason request);

    /** Takes a retired reason out of circulation without deleting it. */
    void deactivate(long id);

    void reactivate(long id);
}
