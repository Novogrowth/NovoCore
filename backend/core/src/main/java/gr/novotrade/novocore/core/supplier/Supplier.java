package gr.novotrade.novocore.core.supplier;

import gr.novotrade.novocore.core.api.tax.VatStatus;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One supplier — the sub-ledger behind Accounts payable and the GR/IR clearing account.
 *
 * <p><strong>No external system reference ids</strong> ({@code CLAUDE.md} rule 2) and no balance:
 * what we owe is the sum of the journal lines against Accounts payable, computed on read.
 *
 * <p>Holds the VAT exemption reason as a plain id rather than a JPA association, for the reason
 * {@code ChargeType} documents: {@code VatExemptionReason} is package-private in {@code ..core.tax},
 * so the only route to it is that slice's published service. The foreign key still exists in the
 * database.
 */
@Entity
@Table(name = "supplier")
class Supplier extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    /** ΑΦΜ or EU VAT number, unique when present. Never verified against VIES — phase 7. */
    @Column(name = "vat_number", length = 30)
    private String vatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_status", nullable = false, length = 20)
    private VatStatus vatStatus;

    @Column(name = "vat_exemption_reason_id")
    private Long vatExemptionReasonId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected Supplier() {
    }

    Supplier(String name, String email, String phone, String vatNumber, VatStatus vatStatus,
            Long vatExemptionReasonId) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.vatNumber = vatNumber;
        this.vatStatus = vatStatus;
        this.vatExemptionReasonId = vatExemptionReasonId;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getEmail() {
        return email;
    }

    String getPhone() {
        return phone;
    }

    String getVatNumber() {
        return vatNumber;
    }

    VatStatus getVatStatus() {
        return vatStatus;
    }

    Long getVatExemptionReasonId() {
        return vatExemptionReasonId;
    }

    boolean isActive() {
        return active;
    }

    void rename(String newName) {
        this.name = newName;
    }

    void changeContactDetails(String newEmail, String newPhone) {
        this.email = newEmail;
        this.phone = newPhone;
    }

    void changeVatNumber(String newVatNumber) {
        this.vatNumber = newVatNumber;
    }

    /**
     * Both together, never separately. The status and the reason are one fact — an EXEMPT status
     * with no reason is refused by the database, so a setter for either alone could only ever
     * produce a state that fails on flush.
     */
    void changeVatStatus(VatStatus newStatus, Long newVatExemptionReasonId) {
        this.vatStatus = newStatus;
        this.vatExemptionReasonId = newVatExemptionReasonId;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
