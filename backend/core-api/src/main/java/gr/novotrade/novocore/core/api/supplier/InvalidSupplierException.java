package gr.novotrade.novocore.core.api.supplier;

/**
 * A requested supplier change is not allowed — a blank name, a duplicate name or VAT number, or a
 * VAT status missing the VAT number or exemption reason it requires.
 */
public class InvalidSupplierException extends RuntimeException {

    public InvalidSupplierException(String message) {
        super(message);
    }
}
