package gr.novotrade.novocore.core.api.inventory;

/**
 * A write-off that cannot be recorded — more than the lot has left, a unit that is not on hand, a
 * fractional quantity of something sold by the piece, a write-off already reversed.
 */
public class InvalidStockWriteOffException extends RuntimeException {

    public InvalidStockWriteOffException(String message) {
        super(message);
    }
}
