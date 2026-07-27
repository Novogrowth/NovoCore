package gr.novotrade.novocore.core.api.tax;

import java.util.Objects;

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
 *     costs nothing to avoid.
 * @param inputVatDeductible whether input VAT deduction rights apply — AADE's
 *     "Δικαίωμα έκπτωσης Φ.Π.Α. εισροών"
 */
public record VatExemptionReasonView(
        long id,
        int code,
        String description,
        String mydataCode,
        boolean inputVatDeductible,
        boolean active) {

    public VatExemptionReasonView {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(mydataCode, "mydataCode");
    }

    /** True when {@link #mydataCode} is the plain {@code code-description} composition. */
    public boolean mydataCodeMatchesDescription() {
        return mydataCode.equals(code + "-" + description);
    }
}
