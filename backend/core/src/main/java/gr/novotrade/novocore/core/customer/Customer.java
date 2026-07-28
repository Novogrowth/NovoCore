package gr.novotrade.novocore.core.customer;

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
 * One customer — the sub-ledger behind Accounts receivable.
 *
 * <p><strong>No external system reference ids</strong> ({@code CLAUDE.md} rule 2): no WooCommerce
 * customer id, no Skroutz id, no Prosvasis Go id. The generated {@link #id} is brief §5's "own
 * internal ID", and each adapter keeps its own mapping table pointing at it.
 *
 * <p>No balance either — what a customer owes is the sum of their journal lines.
 *
 * <p>The VAT class and exemption reason are plain ids rather than associations, because both live
 * in {@code ..core.tax} as package-private entities. The database keeps the foreign keys.
 */
@Entity
@Table(name = "customer")
class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Not unique: two unrelated retail customers can genuinely share a name. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Single address (Q8). Suggestive-only for matching — never authoritative. */
    @Column(name = "email", length = 255)
    private String email;

    /** Single number (Q8). Suggestive-only, and compared as stored: nothing normalises +30 yet. */
    @Column(name = "phone", length = 40)
    private String phone;

    /** ΑΦΜ or EU VAT number. Unique when present — brief §5's authoritative identifier. */
    @Column(name = "vat_number", length = 30)
    private String vatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_status", nullable = false, length = 20)
    private VatStatus vatStatus;

    /** The customer level of the VAT precedence rule. Null means the product's default stands. */
    @Column(name = "vat_class_override_id")
    private Long vatClassOverrideId;

    @Column(name = "vat_exemption_reason_id")
    private Long vatExemptionReasonId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected Customer() {
    }

    Customer(String name, String email, String phone, String vatNumber, VatStatus vatStatus,
            Long vatClassOverrideId, Long vatExemptionReasonId) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.vatNumber = vatNumber;
        this.vatStatus = vatStatus;
        this.vatClassOverrideId = vatClassOverrideId;
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

    Long getVatClassOverrideId() {
        return vatClassOverrideId;
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

    /** Status and reason are one fact, so they move together — see {@code Supplier}. */
    void changeVatStatus(VatStatus newStatus, Long newVatExemptionReasonId) {
        this.vatStatus = newStatus;
        this.vatExemptionReasonId = newVatExemptionReasonId;
    }

    void changeVatClassOverride(Long newVatClassId) {
        this.vatClassOverrideId = newVatClassId;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
