package gr.novotrade.novocore.core.tax;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One official AADE VAT exemption reason, each tied to an article of the Κώδικας ΦΠΑ.
 *
 * <p>Not a 0% VAT class. A zero-rated line charges 0% under a rate that exists; an exempt line is
 * outside VAT because a named article says so. Different legally, reported differently to myDATA.
 *
 * <p>Neither the code nor the myDATA string is mutable. These are AADE's values, transmitted as-is
 * — a typo corrected in place would leave already-issued documents referencing something else.
 * A retired reason is deactivated, not edited.
 */
@Entity
@Table(name = "vat_exemption_reason")
class VatExemptionReason extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The AADE reason number, roughly 1–31 with gaps where numbers are retired.
     *
     * <p>An integer rather than text: myDATA's own field is numeric, and text would sort "10"
     * before "2" in a picker of ~29 entries. If AADE ever issues a letter-suffixed code, that
     * becomes a migration — an explicit one, rather than a speculative varchar plus a separate
     * sort column carried from the start.
     */
    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "description", nullable = false, length = 400)
    private String description;

    /** The exact string myDATA expects. Stored verbatim, not composed at use time. */
    @Column(name = "mydata_code", nullable = false, length = 500)
    private String mydataCode;

    /** AADE's "Δικαίωμα έκπτωσης Φ.Π.Α. εισροών". */
    @Column(name = "input_vat_deductible", nullable = false)
    private boolean inputVatDeductible;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected VatExemptionReason() {
    }

    VatExemptionReason(int code, String description, String mydataCode,
            boolean inputVatDeductible) {
        this.code = code;
        this.description = description;
        this.mydataCode = mydataCode;
        this.inputVatDeductible = inputVatDeductible;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    int getCode() {
        return code;
    }

    String getDescription() {
        return description;
    }

    String getMydataCode() {
        return mydataCode;
    }

    boolean isInputVatDeductible() {
        return inputVatDeductible;
    }

    boolean isActive() {
        return active;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
