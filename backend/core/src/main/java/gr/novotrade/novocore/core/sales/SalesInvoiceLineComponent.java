package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.shared.Money;
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
import java.util.Currency;

/**
 * One component of a bundle line, as allocated on the day of the sale — brief §5's second revenue
 * level.
 *
 * <p><strong>Materialised, never recomputed.</strong> These rows are a copy of what
 * {@code ProportionalAllocation} worked out when the sale was recorded, not a live read of the bundle's
 * current definition. That is what discharges the step 6 obligation about dissolving a bundle that has
 * been sold: there is no history to strand, because a recorded invoice does not depend on the
 * definition still existing. It is brief §5's "alias forward, never rewrite history" achieved without
 * an alias table, by not needing one.
 *
 * <p>The obligation it creates in exchange: a report showing both levels reads <em>these</em> and
 * never {@code BundleService.componentsOf}.
 */
@Entity
@Table(name = "sales_invoice_line_component")
class SalesInvoiceLineComponent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_line_id", nullable = false)
    private SalesInvoiceLine invoiceLine;

    @Column(name = "component_number", nullable = false)
    private int componentNumber;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    /**
     * This component's share of the bundle line's net, from {@code ProportionalAllocation} — exact integer
     * arithmetic in cents, largest-remainder, so the parts add up to the whole with no residual and no
     * rounding mode involved at all.
     */
    @Column(name = "allocated_amount", nullable = false)
    private BigDecimal allocatedAmount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "allocated_amount_currency", nullable = false, length = 3)
    private String allocatedAmountCurrency;

    @Column(name = "stock_consumption_id")
    private Long stockConsumptionId;

    /** For JPA only. */
    protected SalesInvoiceLineComponent() {
    }

    SalesInvoiceLineComponent(SalesInvoiceLine invoiceLine, int componentNumber, long productId,
            Quantity quantity, Money allocatedAmount) {
        this.invoiceLine = invoiceLine;
        this.componentNumber = componentNumber;
        this.productId = productId;
        this.quantity = quantity.value();
        this.allocatedAmount = allocatedAmount.amount();
        this.allocatedAmountCurrency = allocatedAmount.currency().getCurrencyCode();
    }

    void consumedAs(Long consumptionId) {
        this.stockConsumptionId = consumptionId;
    }

    Long getId() {
        return id;
    }

    SalesInvoiceLine getInvoiceLine() {
        return invoiceLine;
    }

    int getComponentNumber() {
        return componentNumber;
    }

    Long getProductId() {
        return productId;
    }

    Quantity getQuantity() {
        return Quantity.of(quantity);
    }

    Money getAllocatedAmount() {
        return Money.of(allocatedAmount, Currency.getInstance(allocatedAmountCurrency));
    }

    Long getStockConsumptionId() {
        return stockConsumptionId;
    }
}
