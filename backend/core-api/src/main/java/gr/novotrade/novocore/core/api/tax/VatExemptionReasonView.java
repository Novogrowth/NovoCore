package gr.novotrade.novocore.core.api.tax;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Objects;
import java.util.Optional;

/**
 * One official AADE VAT exemption reason, each tied to an article of the Κώδικας ΦΠΑ.
 *
 * <p>A separate entity from {@link VatClassView} on purpose, and not a 0% rate. A zero-rated line
 * charges 0% under a rate that exists; an exempt line charges nothing because a specific article
 * of the VAT Code says it is outside VAT. They are different legally, reported differently to
 * myDATA, and conflating them would make the 0% class mean two things.
 *
 * <p>These values are not NovoCore's to invent. The list is published and codified by AADE, and
 * the codes are transmitted, so a wrong one is a compliance defect rather than a display bug.
 *
 * @param code the AADE reason number, roughly 1–31 with some numbers retired
 * @param mydataCode the exact string myDATA expects, e.g.
 *     {@code "6-Χωρίς ΦΠΑ - άρθρο 24 του Κώδικα ΦΠΑ"}. Stored verbatim rather than composed from
 *     {@link #code} and {@link #description} at use time: it is what actually goes on the wire,
 *     and reproducing AADE's exact punctuation and spacing by string concatenation is a bet that
 *     costs nothing to avoid. The seeded codes 12 and 13 prove the point — their description names
 *     "Πλοία Ανοικτής Θαλάσσης" and their myDATA string does not.
 *     <p>Null where the invoicing system has no myDATA mapping for the reason, which is the case
 *     for the OSS and IOSS entries. Null means "no mapping exists", not "not filled in yet": a
 *     caller transmitting to myDATA must refuse it rather than substituting a composed value, and
 *     {@link #requireMydataCode()} is the way to do that.
 * @param inputVatDeductible whether input VAT deduction rights apply — AADE's
 *     "Δικαίωμα έκπτωσης Φ.Π.Α. εισροών"
 */
public record VatExemptionReasonView(
        long id,
        int code,
        @Mandatory String description,
        String mydataCode,
        boolean inputVatDeductible,
        boolean active) {

    public VatExemptionReasonView {
        Objects.requireNonNull(description, "description");
    }

    /** Empty when no myDATA mapping exists for this reason. */
    public Optional<String> mydataCodeIfAny() {
        return Optional.ofNullable(mydataCode);
    }

    /**
     * The myDATA string, for a caller that is about to transmit one.
     *
     * <p>Throws rather than returning null or an empty string, because a document transmitted with
     * a blank exemption category is a rejected or misdeclared filing either way, and the failure
     * should name the reason that has no mapping instead of surfacing as an AADE error later.
     */
    public String requireMydataCode() {
        if (mydataCode == null) {
            throw new IllegalStateException(
                    "AADE exemption reason " + code + " (" + description + ") has no myDATA code, "
                            + "so it cannot be transmitted. Supply the mapping rather than "
                            + "sending a composed or blank value.");
        }
        return mydataCode;
    }

    /**
     * True when {@link #mydataCode} is the plain {@code code-description} composition.
     *
     * <p>False when there is no myDATA code at all — absence is not a match.
     */
    public boolean mydataCodeMatchesDescription() {
        return mydataCode != null && mydataCode.equals(code + "-" + description);
    }
}
