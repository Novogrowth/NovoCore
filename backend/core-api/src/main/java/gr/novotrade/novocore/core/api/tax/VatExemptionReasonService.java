package gr.novotrade.novocore.core.api.tax;

import java.util.List;
import java.util.Optional;

/**
 * The official AADE VAT exemption reasons.
 *
 * <p><strong>Seeded with the verified list</strong> as configured in Prosvasis Go — 29 entries in
 * the recodified article numbering, with gaps at codes 24 and 28. These are legally meaningful
 * values transmitted to AADE, so the list came from the invoicing system of record rather than
 * from memory, and three of the entries deliberately carry no myDATA code at all; see
 * {@link VatExemptionReasonView#mydataCode()}.
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
