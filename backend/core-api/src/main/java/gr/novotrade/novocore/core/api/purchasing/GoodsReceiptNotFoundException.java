package gr.novotrade.novocore.core.api.purchasing;

/** No goods receipt with that id. */
public class GoodsReceiptNotFoundException extends RuntimeException {

    public GoodsReceiptNotFoundException(long receiptId) {
        super("No goods receipt with id " + receiptId + ".");
    }

    public static GoodsReceiptNotFoundException forLine(long lineId) {
        return new GoodsReceiptNotFoundException(
                "No goods receipt line with id " + lineId + ". An invoice cannot be matched against a "
                        + "delivery that does not exist.");
    }

    private GoodsReceiptNotFoundException(String message) {
        super(message);
    }
}
