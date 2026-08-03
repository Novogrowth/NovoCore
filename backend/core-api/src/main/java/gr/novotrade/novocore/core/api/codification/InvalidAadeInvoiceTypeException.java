package gr.novotrade.novocore.core.api.codification;

/**
 * A change to an AADE invoice type the domain refuses — a blank description, in practice.
 *
 * <p>There is no "already exists" case here and there never will be: this list is seeded and has no
 * create path.
 */
public class InvalidAadeInvoiceTypeException extends RuntimeException {

    public InvalidAadeInvoiceTypeException(String message) {
        super(message);
    }
}
