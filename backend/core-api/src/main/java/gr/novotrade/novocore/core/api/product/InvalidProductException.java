package gr.novotrade.novocore.core.api.product;

/**
 * A requested product change is not allowed — a duplicate SKU or barcode, an unknown or inactive
 * VAT class or supplier, a supplier SKU with no supplier, or a price of zero.
 */
public class InvalidProductException extends RuntimeException {

    public InvalidProductException(String message) {
        super(message);
    }
}
