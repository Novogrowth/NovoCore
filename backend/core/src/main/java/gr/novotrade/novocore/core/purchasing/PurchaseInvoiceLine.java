package gr.novotrade.novocore.core.purchasing;

import gr.novotrade.novocore.core.api.purchasing.PurchaseLineType;
import gr.novotrade.novocore.core.api.shared.Money;
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
 * One line of a supplier's invoice.
 *
 * <p><strong>Two shapes, and the nullable columns are the mechanism</strong> — V12's rule for lots,
 * applied here. An inventory line is a quantity at a unit price and names no account; an expense line
 * names an account and buys no stock. A CHECK refuses any third shape.
 *
 * <p><strong>The posted figures are stored, and that is not inconsistent with
 * {@code StockWriteOff}</strong>, which deliberately stores no amount. A write-off's amount is purely
 * a consequence of our own posting, so reading it back off the entry is the honest source. An invoice
 * line's net and VAT are what <em>somebody else's document says</em>, and they have to keep
 * reconciling against what the supplier and AADE hold. {@code ledger.rounding.mode} is
 * operator-changeable, so re-deriving them later could produce a different cent from the one that was
 * agreed and filed.
 */
@Entity
@Table(name = "purchase_invoice_line")
class PurchaseInvoiceLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private PurchaseInvoice invoice;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private PurchaseLineType lineType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "unit_price_currency", length = 3)
    private String unitPriceCurrency;

    @Column(name = "expense_account_id")
    private Long expenseAccountId;

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

    @Column(name = "vat_class_id")
    private Long vatClassId;

    @Column(name = "vat_exemption_reason_id")
    private Long vatExemptionReasonId;

    @Column(name = "reverse_charge", nullable = false)
    private boolean reverseCharge;

    /** ADR 0008. The residual that makes this line's debits sum exactly to what was charged. */
    @Column(name = "variance_amount", nullable = false)
    private BigDecimal varianceAmount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "variance_amount_currency", nullable = false, length = 3)
    private String varianceAmountCurrency;

    @Column(name = "description", length = 500)
    private String description;

    /** For JPA only. */
    protected PurchaseInvoiceLine() {
    }

    private PurchaseInvoiceLine(PurchaseLineType lineType, Long productId, Quantity quantity,
            UnitCost unitPrice, Long expenseAccountId, Money netAmount, Money vatAmount,
            Long vatClassId, Long vatExemptionReasonId, boolean reverseCharge, Money variance,
            String description) {
        this.lineType = lineType;
        this.productId = productId;
        this.quantity = quantity == null ? null : quantity.value();
        this.unitPrice = unitPrice == null ? null : unitPrice.value();
        this.unitPriceCurrency = unitPrice == null ? null : unitPrice.currency().getCurrencyCode();
        this.expenseAccountId = expenseAccountId;
        this.netAmount = netAmount.amount();
        this.netAmountCurrency = netAmount.currency().getCurrencyCode();
        this.vatAmount = vatAmount.amount();
        this.vatAmountCurrency = vatAmount.currency().getCurrencyCode();
        this.vatClassId = vatClassId;
        this.vatExemptionReasonId = vatExemptionReasonId;
        this.reverseCharge = reverseCharge;
        this.varianceAmount = variance.amount();
        this.varianceAmountCurrency = variance.currency().getCurrencyCode();
        this.description = description;
    }

    static PurchaseInvoiceLine inventory(long productId, Quantity quantity, UnitCost unitPrice,
            Money netAmount, Money vatAmount, Long vatClassId, Long vatExemptionReasonId,
            boolean reverseCharge, Money variance, String description) {
        return new PurchaseInvoiceLine(PurchaseLineType.INVENTORY, productId, quantity, unitPrice,
                null, netAmount, vatAmount, vatClassId, vatExemptionReasonId, reverseCharge,
                variance, description);
    }

    static PurchaseInvoiceLine expense(long expenseAccountId, Money netAmount, Money vatAmount,
            Long vatClassId, Long vatExemptionReasonId, boolean reverseCharge, String description) {
        return new PurchaseInvoiceLine(PurchaseLineType.EXPENSE, null, null, null, expenseAccountId,
                netAmount, vatAmount, vatClassId, vatExemptionReasonId, reverseCharge,
                Money.zero(netAmount.currency()), description);
    }

    void attachTo(PurchaseInvoice owner, int position) {
        this.invoice = owner;
        this.lineNumber = position;
    }

    Long getId() {
        return id;
    }

    PurchaseInvoice getInvoice() {
        return invoice;
    }

    int getLineNumber() {
        return lineNumber;
    }

    PurchaseLineType getLineType() {
        return lineType;
    }

    boolean isInventory() {
        return lineType == PurchaseLineType.INVENTORY;
    }

    Long getProductId() {
        return productId;
    }

    Quantity getQuantity() {
        return quantity == null ? null : Quantity.of(quantity);
    }

    UnitCost getUnitPrice() {
        return unitPrice == null
                ? null : new UnitCost(unitPrice, Currency.getInstance(unitPriceCurrency));
    }

    Long getExpenseAccountId() {
        return expenseAccountId;
    }

    Money getNetAmount() {
        return Money.of(netAmount, Currency.getInstance(netAmountCurrency));
    }

    Money getVatAmount() {
        return Money.of(vatAmount, Currency.getInstance(vatAmountCurrency));
    }

    Money getVarianceAmount() {
        return Money.of(varianceAmount, Currency.getInstance(varianceAmountCurrency));
    }

    Long getVatClassId() {
        return vatClassId;
    }

    Long getVatExemptionReasonId() {
        return vatExemptionReasonId;
    }

    boolean isReverseCharge() {
        return reverseCharge;
    }

    String getDescription() {
        return description;
    }
}
