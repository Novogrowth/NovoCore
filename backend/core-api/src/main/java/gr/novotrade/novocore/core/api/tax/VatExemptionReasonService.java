package gr.novotrade.novocore.core.api.tax;

import gr.novotrade.novocore.core.api.codification.StatutoryCodification;
import java.util.Optional;

/**
 * The official AADE VAT exemption reasons — a {@link StatutoryCodification}.
 *
 * <p><strong>31 rows, seeded by Flyway.</strong> V8 seeded 29 from Prosvasis Go's
 * "Διατάξεις απαλλαγής Φ.Π.Α." screen with gaps at 24 and 28, and asked in its own header whether
 * those two were retired by AADE or merely absent from Go. ⭐ <strong>The artefact answered it:</strong>
 * {@code VatExemptionType} in {@code SimpleTypes-v2.0.1.xsd} is {@code xs:int} restricted to
 * {@code 1..31} with no gaps, and annex 8.3 lists all thirty-one. They were absent from Go. V32
 * seeds them.
 *
 * <h2>⚠️ There is no {@code create}, and Q1-b is closed by that rather than by a judgement call</h2>
 *
 * <p>Q1-b asked whether {@code VatExemptionReasonService.create} kept a reason to exist, having no
 * production caller: the seed is Flyway SQL and the route was GET-only. The answer follows from the
 * contract rather than from the usage count — <strong>row authorship here belongs to Flyway</strong>,
 * because these codes are transmitted to the tax authority and a row somebody typed into a form is a
 * compliance defect rather than a data-entry mistake. If AADE adds a code, that is a migration with
 * the artefact it was read from sitting beside it.
 *
 * <p>⚠️ The <em>counter</em>-argument was heard and is why {@link StatutoryCodification} exists at
 * all: deleting a method because nothing called it would have left the next list to rediscover the
 * argument. {@code StatutoryCodificationRulesTest} now makes the absence a build failure.
 *
 * <p>Three of the reasons deliberately carry no myDATA code at all, and V32 adds two more; see
 * {@link VatExemptionReasonView#mydataCode()} for why null there is a real answer rather than
 * missing data.
 */
public interface VatExemptionReasonService
        extends StatutoryCodification<VatExemptionReasonView> {

    /** @throws VatExemptionReasonNotFoundException if absent */
    @Override
    VatExemptionReasonView require(long id);

    /** By AADE code. */
    Optional<VatExemptionReasonView> findByCode(int code);

    /** @throws VatExemptionReasonNotFoundException if absent */
    VatExemptionReasonView requireByCode(int code);

    /**
     * Corrects the description.
     *
     * <p>⚠️ The <strong>description</strong> only. Neither {@link VatExemptionReasonView#code()} nor
     * {@link VatExemptionReasonView#mydataCode()} is editable, and that is the important half: the
     * myDATA string is what goes on the wire, and correcting one in place would retroactively change
     * what every document already transmitted under it appears to have declared.
     *
     * @throws InvalidVatExemptionReasonException if the description is blank
     * @throws VatExemptionReasonNotFoundException if absent
     */
    @Override
    VatExemptionReasonView describe(long id, String description);
}
