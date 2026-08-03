package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One product arriving on one delivery. Becomes exactly one inventory lot (brief §5, ADR 0004).
 *
 * <p>Two shapes, as everywhere stock is created: pooled stock states a quantity, serial-tracked stock
 * states one serial number per unit and no quantity, because the unit count <em>is</em> the quantity.
 * Which applies follows from the product and is refused rather than absorbed if the request disagrees.
 *
 * <p><strong>The unit cost is stated only when this delivery has no invoice behind it yet.</strong>
 * That is the whole of ADR 0008's first decision, expressed in a request object:
 *
 * <ul>
 *   <li>{@link #purchaseInvoiceLineId} set — the invoice arrived first, so the price is known, and this
 *       line <em>must not</em> restate it. The lot is costed from the invoice line, GR/IR clears
 *       exactly, and no variance can arise.
 *   <li>{@link #purchaseInvoiceLineId} absent — the goods arrived first, so the cost is provisional and
 *       has to be stated: from the purchase order, from the last purchase price, from the delivery
 *       note. When the invoice lands at a different figure, the difference posts to
 *       {@code Purchase price variance} and this lot keeps the cost it was received at.
 * </ul>
 *
 * <p>Zero is a legitimate provisional cost — a supplier's free sample is a real delivery — and it is
 * the only case where a Goods Receipt posts nothing at all.
 *
 * @param location where the stock is going. Normally {@link StockLocation#INVENTORY}, but a delivery
 *     that arrives already broken is receivable straight into Damaged Goods rather than put on the
 *     shelf and moved a second later.
 * @param roastDate coffee only, and only meaningful for pooled stock we roast or buy roasted.
 */
public record NewGoodsReceiptLine(
        long productId,
        Quantity quantity,
        List<String> serialNumbers,
        UnitCost unitCost,
        @Mandatory StockLocation location,
        LocalDate roastDate,
        Long purchaseInvoiceLineId) {

    public NewGoodsReceiptLine {
        Objects.requireNonNull(location, "location");
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);

        if (productId <= 0) {
            throw new IllegalArgumentException(
                    "productId must be a positive NovoCore id, got " + productId);
        }
        if (quantity != null && !serialNumbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "A serialized receipt line states its serial numbers and nothing else: the unit "
                            + "count is the quantity. This one supplies " + quantity + " against "
                            + serialNumbers.size() + " serial numbers, which is two numbers free to "
                            + "disagree.");
        }
        if (quantity == null && serialNumbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "A receipt line needs either a quantity (pooled stock) or at least one serial "
                            + "number (serial-tracked stock).");
        }
        if (quantity != null && !quantity.isPositive()) {
            throw new IllegalArgumentException(
                    "Received quantity " + quantity + " is not positive. A delivery of nothing is not "
                            + "a receipt, and a negative one is a reversal.");
        }
        if (purchaseInvoiceLineId != null) {
            if (purchaseInvoiceLineId <= 0) {
                throw new IllegalArgumentException(
                        "purchaseInvoiceLineId must be a positive NovoCore id, got "
                                + purchaseInvoiceLineId);
            }
            if (unitCost != null) {
                throw new IllegalArgumentException(
                        "This line is received against purchase invoice line " + purchaseInvoiceLineId
                                + ", so its cost comes from that invoice and must not be restated "
                                + "here. Two prices for one delivery is exactly the disagreement the "
                                + "purchase price variance account exists to record, and it has no "
                                + "business arising between a document and itself.");
            }
        } else if (unitCost == null) {
            throw new IllegalArgumentException(
                    "This line has no purchase invoice behind it yet, so its unit cost has to be "
                            + "stated — from the purchase order, the last purchase price or the "
                            + "delivery note. It is provisional, and ADR 0008 says the lot keeps it: "
                            + "when the invoice lands at a different figure, the difference posts to "
                            + "purchase price variance rather than rewriting this lot.");
        }
    }

    /** Pooled stock arriving with no invoice yet: a quantity at a provisional cost. */
    public static NewGoodsReceiptLine pooled(
            long productId, Quantity quantity, UnitCost provisionalUnitCost) {
        return new NewGoodsReceiptLine(productId, quantity, List.of(), provisionalUnitCost,
                StockLocation.INVENTORY, null, null);
    }

    /** Serial-tracked stock arriving with no invoice yet. */
    public static NewGoodsReceiptLine serialized(
            long productId, List<String> serialNumbers, UnitCost provisionalUnitCost) {
        return new NewGoodsReceiptLine(productId, null, serialNumbers, provisionalUnitCost,
                StockLocation.INVENTORY, null, null);
    }

    /** Pooled stock arriving against an invoice line that already exists; the price comes from it. */
    public static NewGoodsReceiptLine pooledAgainst(
            long productId, Quantity quantity, long purchaseInvoiceLineId) {
        return new NewGoodsReceiptLine(productId, quantity, List.of(), null,
                StockLocation.INVENTORY, null, purchaseInvoiceLineId);
    }

    /** Serial-tracked stock arriving against an invoice line that already exists. */
    public static NewGoodsReceiptLine serializedAgainst(
            long productId, List<String> serialNumbers, long purchaseInvoiceLineId) {
        return new NewGoodsReceiptLine(productId, null, serialNumbers, null,
                StockLocation.INVENTORY, null, purchaseInvoiceLineId);
    }

    /** The same line, arriving somewhere other than the shelf. */
    public NewGoodsReceiptLine at(StockLocation destination) {
        return new NewGoodsReceiptLine(productId, quantity, serialNumbers, unitCost, destination,
                roastDate, purchaseInvoiceLineId);
    }

    /** The same line with a roast date, for coffee. */
    public NewGoodsReceiptLine roastedOn(LocalDate newRoastDate) {
        return new NewGoodsReceiptLine(productId, quantity, serialNumbers, unitCost, location,
                newRoastDate, purchaseInvoiceLineId);
    }

    public boolean isSerialized() {
        return !serialNumbers.isEmpty();
    }

    /** The quantity this line receives, however it was stated. */
    public Quantity receivedQuantity() {
        return isSerialized() ? Quantity.of(serialNumbers.size()) : quantity;
    }

    public Optional<Long> invoiceLine() {
        return Optional.ofNullable(purchaseInvoiceLineId);
    }
}
