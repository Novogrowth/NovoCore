package gr.novotrade.novocore.core.api.sales;

/** A sale that cannot be recorded as stated. The message says which rule and why. */
public class InvalidSalesInvoiceException extends RuntimeException {

    public InvalidSalesInvoiceException(String message) {
        super(message);
    }
}
