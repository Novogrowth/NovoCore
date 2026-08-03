package gr.novotrade.novocore.core.api.document;

/** A delivery method the domain refuses — a duplicate abbreviation, in practice. */
public class InvalidDeliveryMethodException extends RuntimeException {

    public InvalidDeliveryMethodException(String message) {
        super(message);
    }
}
