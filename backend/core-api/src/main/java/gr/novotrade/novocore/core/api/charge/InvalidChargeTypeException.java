package gr.novotrade.novocore.core.api.charge;

/**
 * A requested charge type change is not allowed — a duplicate name, an unknown VAT class, or an
 * account that is not on the income side.
 */
public class InvalidChargeTypeException extends RuntimeException {

    public InvalidChargeTypeException(String message) {
        super(message);
    }
}
