package gr.novotrade.novocore.core.charge;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A chargeable fee that can appear as a sales invoice line — a COD charge, a delivery cost.
 *
 * <p><strong>Holds plain ids, not JPA associations.</strong> {@code VatClass} and {@code Account}
 * are package-private entities in {@code ..core.tax} and {@code ..core.account}, so this class
 * cannot reference them without one of those packages publishing its entity — which would make
 * every future entity in this package a potential back door into another aggregate's internals.
 * The foreign keys still exist in the database, and the ids are validated through
 * {@code VatClassService} and {@code ChartOfAccountsService} on the way in, so nothing is lost
 * except the ability to navigate across an aggregate boundary by accident.
 */
@Entity
@Table(name = "charge_type")
class ChargeType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Fees are taxable. Overridable per line by the ordinary VAT precedence rule. */
    @Column(name = "default_vat_class_id", nullable = false)
    private Long defaultVatClassId;

    /** Constrained to an INCOME-type account by the service — these are revenue, not cost offsets. */
    @Column(name = "income_account_id", nullable = false)
    private Long incomeAccountId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected ChargeType() {
    }

    ChargeType(String name, Long defaultVatClassId, Long incomeAccountId) {
        this.name = name;
        this.defaultVatClassId = defaultVatClassId;
        this.incomeAccountId = incomeAccountId;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    Long getDefaultVatClassId() {
        return defaultVatClassId;
    }

    Long getIncomeAccountId() {
        return incomeAccountId;
    }

    boolean isActive() {
        return active;
    }

    void rename(String newName) {
        this.name = newName;
    }

    void changeDefaultVatClass(Long vatClassId) {
        this.defaultVatClassId = vatClassId;
    }

    void changeIncomeAccount(Long accountId) {
        this.incomeAccountId = accountId;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
