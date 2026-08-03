package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** How goods reach the customer. The business's own list. */
@Entity
@Table(name = "delivery_method")
class DeliveryMethod extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The identity, and never mutated — it is what a document prints. */
    @Column(name = "abbreviation", nullable = false, length = 20)
    private String abbreviation;

    @Column(name = "description", nullable = false, length = 120)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected DeliveryMethod() {
    }

    DeliveryMethod(String abbreviation, String description) {
        this.abbreviation = abbreviation;
        this.description = description;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getAbbreviation() {
        return abbreviation;
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
