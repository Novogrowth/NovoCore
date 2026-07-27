package gr.novotrade.novocore.core.api.tax;

/**
 * A requested VAT class change is not allowed — a duplicate code, a rate outside 0–100, or an
 * island-reduced mapping that does not make sense.
 */
public class InvalidVatClassException extends RuntimeException {

    public InvalidVatClassException(String message) {
        super(message);
    }
}
