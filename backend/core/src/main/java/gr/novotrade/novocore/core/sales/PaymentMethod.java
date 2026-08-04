package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The presentation half of a {@link SettlementMethod} — one row per enum value.
 *
 * <p>⚠️ <strong>The enum constant IS the primary key.</strong> No surrogate id: the enum name is the
 * identity, and a second identifier would be a second thing to keep in step.
 *
 * <p>⚠️ <strong>Behaviour stays on the enum</strong> — the settlement account, whether it settles
 * immediately, whether it counts against the cash limit, and the myDATA payment code. None of those
 * is stored here, so none of them can drift from the enum. Only the three fields the enum has no
 * room for live in this table, plus the sort code. See {@code V35}.
 */
@Entity
@Table(name = "payment_method")
class PaymentMethod extends AuditableEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 40)
    private SettlementMethod method;

    @Column(name = "abbreviation", nullable = false, length = 20)
    private String abbreviation;

    @Column(name = "description", nullable = false, length = 120)
    private String description;

    /** ⚠️ Ordering only, freely editable. See {@code V34} for the argument. */
    @Column(name = "sort_code", nullable = false)
    private int sortCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected PaymentMethod() {
    }

    SettlementMethod getMethod() {
        return method;
    }

    String getAbbreviation() {
        return abbreviation;
    }

    String getDescription() {
        return description;
    }

    int getSortCode() {
        return sortCode;
    }

    boolean isActive() {
        return active;
    }

    void describe(String newDescription) {
        this.description = newDescription;
    }

    void changeSortCode(int newSortCode) {
        this.sortCode = newSortCode;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
