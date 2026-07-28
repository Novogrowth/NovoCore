package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One line of a bundle's component list (brief §5, Q11).
 *
 * <p>Both ends are {@link Product} — a bundle is a product with its own SKU, and so is each component.
 * Same package, same slice, so both are real associations.
 *
 * <p>The one-level-deep rule (a component may not itself be a bundle) lives in
 * {@code BundleServiceImpl}, because a CHECK constraint cannot read the other row's flag. The
 * self-reference half <em>is</em> a CHECK, since that comparison is within one row.
 */
@Entity
@Table(name = "bundle_component")
class BundleComponent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bundle_product_id", nullable = false)
    private Product bundle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_product_id", nullable = false)
    private Product component;

    /** How many of the component per one bundle. */
    @Column(name = "quantity_per_bundle", nullable = false)
    private BigDecimal quantityPerBundle;

    /** For JPA only. */
    protected BundleComponent() {
    }

    BundleComponent(Product bundle, Product component, Quantity quantityPerBundle) {
        this.bundle = bundle;
        this.component = component;
        this.quantityPerBundle = quantityPerBundle.value();
    }

    Long getId() {
        return id;
    }

    Product getBundle() {
        return bundle;
    }

    Product getComponent() {
        return component;
    }

    Quantity getQuantityPerBundle() {
        return Quantity.of(quantityPerBundle);
    }
}
