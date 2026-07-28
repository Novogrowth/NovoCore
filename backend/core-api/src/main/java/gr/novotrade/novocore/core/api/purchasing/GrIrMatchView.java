package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One invoice line settled against one receipt line, for a quantity.
 *
 * <p>This is the record that makes ADR 0004's clearing account arithmetic auditable rather than
 * implied. GR/IR nets to zero for a matched quantity because the receipt credited it at
 * {@link #receiptUnitCost} and the invoice debited it at exactly the same figure; whatever the invoice
 * charged on top of that became the line's variance and went to {@code Purchase price variance}
 * rather than back into the lot (ADR 0008).
 *
 * <p><strong>It carries no money amount, deliberately.</strong> The variance is stored per invoice
 * <em>line</em>, computed as the residual that makes that line's debits sum exactly to what the
 * supplier charged. A per-match figure would be a second decomposition of the same amount, and the two
 * would differ by a cent the moment rounding got involved — the kind of disagreement this codebase
 * keeps refusing to create. What a match states is the physical fact: this many units, at these two
 * unit figures.
 */
public record GrIrMatchView(
        long id,
        long purchaseInvoiceLineId,
        long goodsReceiptLineId,
        long lotId,
        Quantity quantity,
        UnitCost receiptUnitCost,
        UnitCost invoiceUnitPrice) {

    public GrIrMatchView {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(receiptUnitCost, "receiptUnitCost");
        Objects.requireNonNull(invoiceUnitPrice, "invoiceUnitPrice");
    }

    /** What the supplier charged per unit above what the goods went into stock at. */
    public BigDecimal unitDifference() {
        return invoiceUnitPrice.value().subtract(receiptUnitCost.value());
    }

    /** True when the invoice charged more per unit than the goods were received at. */
    public boolean isUnfavourable() {
        return unitDifference().signum() > 0;
    }

    /**
     * True when the two documents agree on the price — always so when the delivery was received
     * <em>against</em> the invoice, because it then took its cost from it and had nothing to disagree
     * with.
     */
    public boolean pricesAgreed() {
        return unitDifference().signum() == 0;
    }
}
