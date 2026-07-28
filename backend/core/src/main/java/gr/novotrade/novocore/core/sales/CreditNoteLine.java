package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
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
 * One line of a credit note, crediting one line of the sale it corrects.
 *
 * <p>The VAT class and exemption reason are copied from the invoice line rather than restated, which
 * is the point of requiring the reference: a credit note cannot credit at a rate the sale never
 * charged, and a VAT return that nets an output at 24% against a return at 13% does not reconcile
 * against anything.
 */
@Entity
@Table(name = "credit_note_line")
class CreditNoteLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "sales_invoice_line_id", nullable = false)
    private Long salesInvoiceLineId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "unit_price_currency", nullable = false, length = 3)
    private String unitPriceCurrency;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "net_amount_currency", nullable = false, length = 3)
    private String netAmountCurrency;

    @Column(name = "vat_amount", nullable = false)
    private BigDecimal vatAmount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "vat_amount_currency", nullable = false, length = 3)
    private String vatAmountCurrency;

    @Column(name = "stock_returned", nullable = false)
    private boolean stockReturned;

    /** Null when the goods came back but were carried at zero, so nothing was posted either way. */
    @Column(name = "return_consumption_id")
    private Long returnConsumptionId;

    @Column(name = "description", length = 500)
    private String description;

    /** For JPA only. */
    protected CreditNoteLine() {
    }

    CreditNoteLine(long salesInvoiceLineId, Quantity quantity, UnitCost unitPrice, Money netAmount,
            Money vatAmount, boolean stockReturned, String description) {
        this.salesInvoiceLineId = salesInvoiceLineId;
        this.quantity = quantity.value();
        this.unitPrice = unitPrice.value();
        this.unitPriceCurrency = unitPrice.currency().getCurrencyCode();
        this.netAmount = netAmount.amount();
        this.netAmountCurrency = netAmount.currency().getCurrencyCode();
        this.vatAmount = vatAmount.amount();
        this.vatAmountCurrency = vatAmount.currency().getCurrencyCode();
        this.stockReturned = stockReturned;
        this.description = description;
    }

    void attachTo(CreditNote owner, int position) {
        this.creditNote = owner;
        this.lineNumber = position;
    }

    void returnedAs(Long consumptionId) {
        this.returnConsumptionId = consumptionId;
    }

    Long getId() {
        return id;
    }

    CreditNote getCreditNote() {
        return creditNote;
    }

    int getLineNumber() {
        return lineNumber;
    }

    Long getSalesInvoiceLineId() {
        return salesInvoiceLineId;
    }

    Quantity getQuantity() {
        return Quantity.of(quantity);
    }

    UnitCost getUnitPrice() {
        return new UnitCost(unitPrice, Currency.getInstance(unitPriceCurrency));
    }

    Money getNetAmount() {
        return Money.of(netAmount, Currency.getInstance(netAmountCurrency));
    }

    Money getVatAmount() {
        return Money.of(vatAmount, Currency.getInstance(vatAmountCurrency));
    }

    boolean isStockReturned() {
        return stockReturned;
    }

    Long getReturnConsumptionId() {
        return returnConsumptionId;
    }

    String getDescription() {
        return description;
    }
}
