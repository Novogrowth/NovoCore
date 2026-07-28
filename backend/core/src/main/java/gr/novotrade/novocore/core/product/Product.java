package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.product.ProductType;
import gr.novotrade.novocore.core.api.product.UnitOfMeasure;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One product.
 *
 * <p><strong>No external system reference ids</strong> ({@code CLAUDE.md} rule 2): the core knows
 * its own {@link #sku}, and the Go and Woo adapters keep their own mapping tables.
 *
 * <p><strong>No stock field and no last-purchase-price field.</strong> Both are derived from
 * inventory lots (brief §5 states stock is never stored, and Q6 answers last purchase price the
 * same way for consistency). Lots arrive in step 6.
 *
 * <p><strong>The selling price is stored as two columns, not as a {@link Money}.</strong> {@code
 * Money} lives in {@code core-api}, which must not depend on {@code jakarta.persistence} — that is
 * ADR 0003 and an ArchUnit rule — so it cannot be an {@code @Embeddable}. The amount and its
 * currency are therefore separate columns, tied together by a CHECK constraint, and reassembled
 * into a {@code Money} by the service on the way out. Keeping the pair in one setter is what stops
 * an amount and a currency being changed independently.
 */
@Entity
@Table(name = "product")
class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false, length = 60)
    private String sku;

    @Column(name = "ean", length = 20)
    private String ean;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private UnitOfMeasure unitOfMeasure;

    /** The product level of the VAT precedence rule. Not null: there is no fallback rate. */
    @Column(name = "default_vat_class_id", nullable = false)
    private Long defaultVatClassId;

    @Column(name = "selling_price")
    private BigDecimal sellingPrice;

    /**
     * The ISO 4217 code, in a {@code char(3)} column as V1 and ADR 0005 specify.
     *
     * <p>The explicit {@link SqlTypes#CHAR} is load-bearing. A plain {@code String} field maps to
     * {@code varchar}, and Hibernate running with {@code ddl-auto=validate} rejects the mismatch
     * against the {@code char(3)} column — which is the validation doing its job. The right fix is
     * to state the JDBC type here rather than widen the column: a currency code is exactly three
     * characters, and that is the first thing a reader of the schema should be told about it.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "selling_price_currency", length = 3)
    private String sellingPriceCurrency;

    /** One supplier or none (Q5). A plain id, since Supplier is package-private in its own slice. */
    @Column(name = "supplier_id")
    private Long supplierId;

    /** Refused without a supplier by the database — it identifies nothing on its own. */
    @Column(name = "supplier_sku", length = 60)
    private String supplierSku;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected Product() {
    }

    Product(String sku, String ean, String name, ProductType type, UnitOfMeasure unitOfMeasure,
            Long defaultVatClassId, Money sellingPrice, Long supplierId, String supplierSku) {
        this.sku = sku;
        this.ean = ean;
        this.name = name;
        this.type = type;
        this.unitOfMeasure = unitOfMeasure;
        this.defaultVatClassId = defaultVatClassId;
        // Assigned directly rather than through setSellingPrice: Hibernate refuses a final setter
        // on a lazily-proxied entity, and calling an overridable method from a constructor is the
        // thing that made it want to be final in the first place.
        this.sellingPrice = sellingPrice == null ? null : sellingPrice.amount();
        this.sellingPriceCurrency =
                sellingPrice == null ? null : sellingPrice.currency().getCurrencyCode();
        this.supplierId = supplierId;
        this.supplierSku = supplierSku;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getSku() {
        return sku;
    }

    String getEan() {
        return ean;
    }

    String getName() {
        return name;
    }

    ProductType getType() {
        return type;
    }

    UnitOfMeasure getUnitOfMeasure() {
        return unitOfMeasure;
    }

    Long getDefaultVatClassId() {
        return defaultVatClassId;
    }

    /** Reassembled from the two columns, or null where no price is set. */
    Money getSellingPrice() {
        if (sellingPrice == null) {
            return null;
        }
        return new Money(sellingPrice, Currency.getInstance(sellingPriceCurrency));
    }

    Long getSupplierId() {
        return supplierId;
    }

    String getSupplierSku() {
        return supplierSku;
    }

    boolean isActive() {
        return active;
    }

    void rename(String newName) {
        this.name = newName;
    }

    void changeEan(String newEan) {
        this.ean = newEan;
    }

    /**
     * Sets both price columns together, or clears both.
     *
     * <p>One setter for the pair, so the amount and its currency cannot drift apart. The CHECK
     * constraint refuses the mismatched state anyway; this makes it unreachable from Java.
     */
    void setSellingPrice(Money newPrice) {
        if (newPrice == null) {
            this.sellingPrice = null;
            this.sellingPriceCurrency = null;
            return;
        }
        this.sellingPrice = newPrice.amount();
        this.sellingPriceCurrency = newPrice.currency().getCurrencyCode();
    }

    void changeDefaultVatClass(Long vatClassId) {
        this.defaultVatClassId = vatClassId;
    }

    /**
     * Sets or clears the supplier and its product code together (Q5).
     *
     * <p>Not two setters. A supplier SKU without a supplier is the meaningless state Q5 was about,
     * and separate setters would let one be cleared while the other survived.
     */
    void changeSupplier(Long newSupplierId, String newSupplierSku) {
        this.supplierId = newSupplierId;
        this.supplierSku = newSupplierSku;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
