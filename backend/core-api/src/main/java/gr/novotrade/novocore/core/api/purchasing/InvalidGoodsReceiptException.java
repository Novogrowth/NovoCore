package gr.novotrade.novocore.core.api.purchasing;

/** A goods receipt that cannot be recorded or reversed as stated. */
public class InvalidGoodsReceiptException extends RuntimeException {

    public InvalidGoodsReceiptException(String message) {
        super(message);
    }
}
