package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A unit a quantity is expressed in — runtime-editable data rather than a Java enum (Q34).
 *
 * <p>In the same package as {@link Product} deliberately, so the two can be a real JPA association:
 * they are one slice of the core, and a product's unit is not a cross-aggregate reference the way
 * its VAT class or supplier is. That is why this one is a {@code @ManyToOne} while those are plain
 * ids.
 *
 * <p>Neither {@link #code} nor {@link #mydataCode} is freely mutable. The code is what products and,
 * from step 6, lots refer to; the myDATA code is transmitted. A wrong unit is deactivated and
 * replaced rather than corrected in place — the same stance {@code VatClass} takes on a rate.
 */
@Entity
@Table(name = "unit_of_measure")
class UnitOfMeasure extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NovoCore's own identifier for the unit. Not AADE's and not Go's. Never mutated. */
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "fractional_quantity_allowed", nullable = false)
    private boolean fractionalQuantityAllowed;

    /** AADE's unit code. Null where no mapping is known; set once, then never changed. */
    @Column(name = "mydata_code", length = 20)
    private String mydataCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected UnitOfMeasure() {
    }

    UnitOfMeasure(String code, String name, boolean fractionalQuantityAllowed, String mydataCode) {
        this.code = code;
        this.name = name;
        this.fractionalQuantityAllowed = fractionalQuantityAllowed;
        this.mydataCode = mydataCode;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    boolean isFractionalQuantityAllowed() {
        return fractionalQuantityAllowed;
    }

    String getMydataCode() {
        return mydataCode;
    }

    boolean isActive() {
        return active;
    }

    void rename(String newName) {
        this.name = newName;
    }

    /** Set once. The service refuses a second call; there is deliberately no way to change one. */
    void recordMydataCode(String newMydataCode) {
        this.mydataCode = newMydataCode;
    }

    void changeFractionalQuantityAllowed(boolean allowed) {
        this.fractionalQuantityAllowed = allowed;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
