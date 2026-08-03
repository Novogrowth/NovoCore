package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One product on one delivery, and the lot it became.
 *
 * @param lotId the inventory lot this line created. Never null: a receipt line that created no lot
 *     would be a delivery verification with nothing verified, which is the reading of brief §6 that
 *     ADR 0004 rejected.
 * @param unitCost what the stock went into the lot at. Taken from the invoice line when there was one,
 *     provisional otherwise — and it stays this figure whatever a later invoice says (ADR 0008).
 * @param openQuantity how much of this delivery no invoice has yet paid for: the other direction of
 *     ADR 0004's clearing, goods received but not invoiced. Non-zero here is the supplier owing us a
 *     document rather than us owing them money.
 */
public record GoodsReceiptLineView(
        long id,
        int lineNumber,
        long productId,
        String productSku,
        @Mandatory Quantity quantity,
        @Mandatory UnitCost unitCost,
        @Mandatory StockLocation location,
        long lotId,
        @Mandatory List<String> serialNumbers,
        Long purchaseInvoiceLineId,
        @Mandatory Quantity matchedQuantity,
        @Mandatory Quantity openQuantity) {

    public GoodsReceiptLineView {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(matchedQuantity, "matchedQuantity");
        Objects.requireNonNull(openQuantity, "openQuantity");
        serialNumbers = List.copyOf(Objects.requireNonNull(serialNumbers, "serialNumbers"));
    }

    public boolean isSerialized() {
        return !serialNumbers.isEmpty();
    }

    /** True when stock has arrived that nobody has invoiced us for yet. */
    public boolean isAwaitingInvoice() {
        return openQuantity.isPositive();
    }

    /**
     * What this line put into stock, rounded once. The amount its Inventory debit carried, and — for
     * the unmatched part — what still sits in GR/IR clearing.
     */
    public Money valueAt(java.math.RoundingMode roundingMode) {
        return unitCost.extend(quantity, roundingMode);
    }

    public Optional<Long> invoiceLine() {
        return Optional.ofNullable(purchaseInvoiceLineId);
    }
}
