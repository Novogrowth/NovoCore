package gr.novotrade.novocore.core.tax;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One VAT class — a code, a description and a rate.
 *
 * <p>An entity rather than an enum because rates change by statute and must be addable through the
 * application. The 3% and island-reduced 4% classes exist because of αρ.31 ν.5057/2023; an enum
 * would have made that a code change.
 *
 * <p><strong>The rate is not changeable once set.</strong> There is no mutator for it, which is
 * deliberate: editing a rate in place would retroactively change what every invoice already
 * issued under that class appears to have charged. A rate change is a new class plus deactivation
 * of the old one.
 *
 * <p>{@code reducedCounterpart} is a real association rather than a bare id because it points
 * within this same package, so no boundary is crossed. Contrast
 * {@link gr.novotrade.novocore.core.charge.ChargeType}, which holds ids.
 */
@Entity
@Table(name = "vat_class")
class VatClass extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    /**
     * A percentage, not a fraction: 24% is stored as {@code 24.000000}.
     *
     * <p>{@code numeric(19,6)} — the multiplier precision class, not the money one. See
     * {@code VatClassView.RATE_SCALE}.
     */
    @Column(name = "rate_percent", nullable = false, precision = 19, scale = 6)
    private BigDecimal ratePercent;

    /**
     * The island-reduced equivalent of this class, or null. Held on the mainland rate and pointing
     * at the reduced one — the 24% class points at the 17% class.
     *
     * <p>One-to-one: a reduced class belongs to exactly one mainland class, enforced by a unique
     * index on the column.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reduced_counterpart_id")
    private VatClass reducedCounterpart;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected VatClass() {
    }

    VatClass(String code, String description, BigDecimal ratePercent) {
        this.code = code;
        this.description = description;
        this.ratePercent = ratePercent;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getDescription() {
        return description;
    }

    BigDecimal getRatePercent() {
        return ratePercent;
    }

    VatClass getReducedCounterpart() {
        return reducedCounterpart;
    }

    boolean isActive() {
        return active;
    }

    void describe(String newDescription) {
        this.description = newDescription;
    }

    void mapToReducedCounterpart(VatClass counterpart) {
        this.reducedCounterpart = counterpart;
    }

    void clearReducedCounterpart() {
        this.reducedCounterpart = null;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
