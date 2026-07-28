package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of a recorded purchase invoice, with what it posted and how much of it is still awaiting
 * delivery.
 *
 * @param netAmount the posted net, rounded once from quantity × unit price. Read back rather than
 *     recomputed, for {@code StockWriteOffView}'s reason: a figure recomputed later can disagree with
 *     the one that actually posted, and nothing then says which is historical.
 * @param vatAmount the VAT computed for this line, or zero when it is exempt. Per line and then summed
 *     by class for posting — Q14's rule, and the reason the per-line figure is worth keeping.
 * @param openQuantity how much of this line has not yet been matched by a Goods Receipt — ADR 0004's
 *     "open receiving amount", now with real ledger meaning: it is the part of this line's GR/IR debit
 *     that no receipt has credited back. Always zero on an expense line, which receives nothing.
 */
public record PurchaseInvoiceLineView(
        long id,
        int lineNumber,
        PurchaseLineType type,
        Long productId,
        String productSku,
        Quantity quantity,
        UnitCost unitPrice,
        Long expenseAccountId,
        Money netAmount,
        Long vatClassId,
        Money vatAmount,
        Long vatExemptionReasonId,
        boolean reverseCharge,
        String description,
        Quantity matchedQuantity,
        Quantity openQuantity) {

    public PurchaseInvoiceLineView {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(netAmount, "netAmount");
        Objects.requireNonNull(vatAmount, "vatAmount");
        Objects.requireNonNull(matchedQuantity, "matchedQuantity");
        Objects.requireNonNull(openQuantity, "openQuantity");
    }

    public boolean isInventory() {
        return type == PurchaseLineType.INVENTORY;
    }

    public boolean isExempt() {
        return vatExemptionReasonId != null;
    }

    /** True when stock this line paid for has not all arrived. */
    public boolean isAwaitingDelivery() {
        return openQuantity.isPositive();
    }

    /** Net plus VAT — what this line contributes to what the supplier is owed. */
    public Money grossAmount() {
        return reverseCharge ? netAmount : netAmount.plus(vatAmount);
    }

    public Optional<Long> vatClass() {
        return Optional.ofNullable(vatClassId);
    }
}
