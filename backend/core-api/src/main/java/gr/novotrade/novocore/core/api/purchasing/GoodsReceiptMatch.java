package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.Objects;

/**
 * A quantity of one goods receipt line that a purchase invoice line is paying for — the goods-first
 * half of ADR 0004's clearing.
 *
 * <p><strong>Why a quantity and not just a link.</strong> Brief §6 handles partial delivery across
 * several days, so one invoice line is routinely settled by several receipts, and one receipt can be
 * split across two invoices when a supplier bills in instalments. A bare foreign key either way would
 * make one of those two cases unrepresentable, and the "open receiving amount" ADR 0004 gives real
 * ledger meaning to is precisely invoice quantity minus the matched quantities.
 *
 * <p>The matched quantity is what clears GR/IR at the <em>receipt's</em> unit cost, and it is also
 * what the purchase price variance is computed across (ADR 0008): matched quantity valued at the
 * invoice price, less the same quantity valued at what the receipt put into stock.
 */
public record GoodsReceiptMatch(long goodsReceiptLineId, Quantity quantity) {

    public GoodsReceiptMatch {
        Objects.requireNonNull(quantity, "quantity");
        if (goodsReceiptLineId <= 0) {
            throw new IllegalArgumentException(
                    "goodsReceiptLineId must be a positive NovoCore id, got " + goodsReceiptLineId);
        }
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException(
                    "Matched quantity " + quantity + " is not positive. A match of nothing clears "
                            + "nothing, and leaving it out says the same thing more honestly.");
        }
    }

    public static GoodsReceiptMatch of(long goodsReceiptLineId, Quantity quantity) {
        return new GoodsReceiptMatch(goodsReceiptLineId, quantity);
    }
}
