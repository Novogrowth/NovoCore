package gr.novotrade.novocore.core.api.product;

/**
 * A requested unit-of-measure change is not allowed — a blank or duplicate code, name or myDATA
 * code, an attempt to overwrite a myDATA code that has already been recorded, or deactivating a
 * unit that products still use.
 */
public class InvalidUnitOfMeasureException extends RuntimeException {

    public InvalidUnitOfMeasureException(String message) {
        super(message);
    }
}
