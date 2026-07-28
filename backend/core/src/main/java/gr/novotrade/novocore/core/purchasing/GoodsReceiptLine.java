package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;

/**
 * One product arriving on one delivery. Becomes exactly one inventory lot.
 *
 * <p><strong>There is no {@code lot_id} here.</strong> The lot carries the line
 * ({@code inventory_lot.goods_receipt_line_id}, UNIQUE), because brief §5 puts the source document on
 * the lot and because a lot is what a later reader holds when they ask where stock came from. Storing
 * the relation both ways would be two copies of one fact.
 *
 * <p><strong>{@link #unitCost} is stored rather than read off the lot</strong>, and the reason is
 * knowable in advance: step 10 will allocate landed costs onto a lot and move its unit cost away from
 * this figure. This is what the GR/IR credit was made at, and it is one half of every variance
 * ADR 0008 computes, so it has to stay recoverable.
 */
@Entity
@Table(name = "goods_receipt_line")
class GoodsReceiptLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false)
    private GoodsReceipt receipt;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** For a serialized line this is the unit count, which the lot's units then state independently. */
    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "unit_cost_currency", nullable = false, length = 3)
    private String unitCostCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "location", nullable = false, length = 20)
    private StockLocation location;

    /** Set when the invoice arrived first and this delivery was received against it. */
    @Column(name = "purchase_invoice_line_id")
    private Long purchaseInvoiceLineId;

    /** For JPA only. */
    protected GoodsReceiptLine() {
    }

    GoodsReceiptLine(long productId, Quantity quantity, UnitCost unitCost, StockLocation location,
            Long purchaseInvoiceLineId) {
        this.productId = productId;
        this.quantity = quantity.value();
        this.unitCost = unitCost.value();
        this.unitCostCurrency = unitCost.currency().getCurrencyCode();
        this.location = location;
        this.purchaseInvoiceLineId = purchaseInvoiceLineId;
    }

    void attachTo(GoodsReceipt owner, int position) {
        this.receipt = owner;
        this.lineNumber = position;
    }

    Long getId() {
        return id;
    }

    GoodsReceipt getReceipt() {
        return receipt;
    }

    int getLineNumber() {
        return lineNumber;
    }

    Long getProductId() {
        return productId;
    }

    Quantity getQuantity() {
        return Quantity.of(quantity);
    }

    UnitCost getUnitCost() {
        return new UnitCost(unitCost, Currency.getInstance(unitCostCurrency));
    }

    StockLocation getLocation() {
        return location;
    }

    Long getPurchaseInvoiceLineId() {
        return purchaseInvoiceLineId;
    }
}
