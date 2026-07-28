package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.sales.SalesLineType;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

/**
 * One line of a recorded sale.
 *
 * <p><strong>The posted figures are frozen</strong>, for {@code PurchaseInvoiceLine}'s reason: they
 * are what a document somebody else holds says, and {@code ledger.rounding.mode} is
 * operator-changeable, so re-deriving them later could produce a different cent from the one that was
 * charged and filed.
 *
 * <p><strong>{@link #vatClassSource} is stored too</strong>, which is the one addition the sales side
 * makes to that pattern. Step 3b built {@code VatClassPrecedence} so that "why is this line at 13%?"
 * has an answer; storing which level won is what keeps that answer true a year later, after the
 * customer's override has been changed and the product's default with it.
 */
@Entity
@Table(name = "sales_invoice_line")
class SalesInvoiceLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SalesInvoice invoice;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 20)
    private SalesLineType lineType;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "charge_type_id")
    private Long chargeTypeId;

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

    @Column(name = "vat_class_id")
    private Long vatClassId;

    @Column(name = "vat_exemption_reason_id")
    private Long vatExemptionReasonId;

    /** Which level of the precedence rule supplied the rate. Null exactly when the line is exempt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "vat_class_source", length = 20)
    private VatClassSource vatClassSource;

    /** The stock this line took out, one direction only. Null when it took none. */
    @Column(name = "stock_consumption_id")
    private Long stockConsumptionId;

    @Column(name = "description", length = 500)
    private String description;

    /** Brief §5's second level of a bundle sale, materialised at the moment of the sale. */
    @OneToMany(mappedBy = "invoiceLine", cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @OrderBy("componentNumber ASC")
    private List<SalesInvoiceLineComponent> components = new ArrayList<>();

    /** For JPA only. */
    protected SalesInvoiceLine() {
    }

    private SalesInvoiceLine(SalesLineType lineType, Long productId, Long chargeTypeId,
            Quantity quantity, UnitCost unitPrice, Money netAmount, Money vatAmount,
            Long vatClassId, VatClassSource vatClassSource, Long vatExemptionReasonId,
            String description) {
        this.lineType = lineType;
        this.productId = productId;
        this.chargeTypeId = chargeTypeId;
        this.quantity = quantity.value();
        this.unitPrice = unitPrice.value();
        this.unitPriceCurrency = unitPrice.currency().getCurrencyCode();
        this.netAmount = netAmount.amount();
        this.netAmountCurrency = netAmount.currency().getCurrencyCode();
        this.vatAmount = vatAmount.amount();
        this.vatAmountCurrency = vatAmount.currency().getCurrencyCode();
        this.vatClassId = vatClassId;
        this.vatClassSource = vatClassSource;
        this.vatExemptionReasonId = vatExemptionReasonId;
        this.description = description;
    }

    static SalesInvoiceLine product(long productId, Quantity quantity, UnitCost unitPrice,
            Money netAmount, Money vatAmount, Long vatClassId, VatClassSource vatClassSource,
            Long vatExemptionReasonId, String description) {
        return new SalesInvoiceLine(SalesLineType.PRODUCT, productId, null, quantity, unitPrice,
                netAmount, vatAmount, vatClassId, vatClassSource, vatExemptionReasonId, description);
    }

    static SalesInvoiceLine charge(long chargeTypeId, Quantity quantity, UnitCost unitPrice,
            Money netAmount, Money vatAmount, Long vatClassId, VatClassSource vatClassSource,
            Long vatExemptionReasonId, String description) {
        return new SalesInvoiceLine(SalesLineType.CHARGE, null, chargeTypeId, quantity, unitPrice,
                netAmount, vatAmount, vatClassId, vatClassSource, vatExemptionReasonId, description);
    }

    SalesInvoiceLineComponent addComponent(
            long componentProductId, Quantity componentQuantity, Money allocatedAmount) {
        SalesInvoiceLineComponent component = new SalesInvoiceLineComponent(
                this, components.size(), componentProductId, componentQuantity, allocatedAmount);
        components.add(component);
        return component;
    }

    void attachTo(SalesInvoice owner, int position) {
        this.invoice = owner;
        this.lineNumber = position;
    }

    void consumedAs(Long consumptionId) {
        this.stockConsumptionId = consumptionId;
    }

    Long getId() {
        return id;
    }

    SalesInvoice getInvoice() {
        return invoice;
    }

    int getLineNumber() {
        return lineNumber;
    }

    SalesLineType getLineType() {
        return lineType;
    }

    boolean isProduct() {
        return lineType == SalesLineType.PRODUCT;
    }

    Long getProductId() {
        return productId;
    }

    Long getChargeTypeId() {
        return chargeTypeId;
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

    Long getVatClassId() {
        return vatClassId;
    }

    VatClassSource getVatClassSource() {
        return vatClassSource;
    }

    Long getVatExemptionReasonId() {
        return vatExemptionReasonId;
    }

    Long getStockConsumptionId() {
        return stockConsumptionId;
    }

    String getDescription() {
        return description;
    }

    List<SalesInvoiceLineComponent> getComponents() {
        return components.stream()
                .sorted(Comparator.comparingInt(SalesInvoiceLineComponent::getComponentNumber))
                .toList();
    }

    boolean isBundleLine() {
        return !components.isEmpty();
    }
}
