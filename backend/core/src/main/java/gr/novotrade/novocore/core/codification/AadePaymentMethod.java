package gr.novotrade.novocore.core.codification;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One payment-method article from annex 8.12. Seeded by {@code V37}; no create path, ever.
 *
 * <p>⚠️ Its codes are the one annex the XSDs carry as a <em>range</em> rather than an enumeration, so
 * both the codes and the descriptions came from a rasterised page. See {@code V37}'s header.
 */
@Entity
@Table(name = "aade_payment_method")
class AadePaymentMethod extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected AadePaymentMethod() {
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

    boolean isActive() {
        return active;
    }

    void describe(String newDescription) {
        this.description = newDescription;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
