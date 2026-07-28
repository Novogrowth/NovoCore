package gr.novotrade.novocore.core.purchasing;

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
 * A quantity of one delivery line that one invoice line is paying for — ADR 0004's open receiving
 * amount, made explicit.
 *
 * <p>A table rather than a nullable foreign key in either direction, because brief §6 handles partial
 * delivery across several days: one invoice line is routinely settled by several receipts, and a
 * supplier billing in instalments splits one receipt across two invoices. A link either way would make
 * one of those unrepresentable.
 *
 * <p><strong>It carries no money.</strong> The variance is computed and stored per invoice <em>line</em>,
 * as the residual that makes that line's debits sum exactly to what the supplier charged; a per-match
 * figure would be a second decomposition of the same amount, differing by a cent as soon as rounding
 * got involved. What a match states is the physical fact — this many units — and the two unit figures
 * are read off the lines it joins.
 */
@Entity
@Table(name = "gr_ir_match")
class GrIrMatch extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_invoice_line_id", nullable = false)
    private PurchaseInvoiceLine invoiceLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_line_id", nullable = false)
    private GoodsReceiptLine receiptLine;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    /** For JPA only. */
    protected GrIrMatch() {
    }

    GrIrMatch(PurchaseInvoiceLine invoiceLine, GoodsReceiptLine receiptLine, Quantity quantity) {
        this.invoiceLine = invoiceLine;
        this.receiptLine = receiptLine;
        this.quantity = quantity.value();
    }

    Long getId() {
        return id;
    }

    PurchaseInvoiceLine getInvoiceLine() {
        return invoiceLine;
    }

    GoodsReceiptLine getReceiptLine() {
        return receiptLine;
    }

    Quantity getQuantity() {
        return Quantity.of(quantity);
    }
}
