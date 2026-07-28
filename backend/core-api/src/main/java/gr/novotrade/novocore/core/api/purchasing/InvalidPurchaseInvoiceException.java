package gr.novotrade.novocore.core.api.purchasing;

/** A purchase invoice that cannot be recorded as stated. The message says which rule and why. */
public class InvalidPurchaseInvoiceException extends RuntimeException {

    public InvalidPurchaseInvoiceException(String message) {
        super(message);
    }
}
