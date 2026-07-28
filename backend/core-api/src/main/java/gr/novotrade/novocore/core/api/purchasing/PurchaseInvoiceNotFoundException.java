package gr.novotrade.novocore.core.api.purchasing;

/** No purchase invoice with that id. */
public class PurchaseInvoiceNotFoundException extends RuntimeException {

    public PurchaseInvoiceNotFoundException(long invoiceId) {
        super("No purchase invoice with id " + invoiceId + ".");
    }

    public static PurchaseInvoiceNotFoundException forLine(long lineId) {
        return new PurchaseInvoiceNotFoundException(
                "No purchase invoice line with id " + lineId + ". A goods receipt cannot be matched "
                        + "against an invoice line that does not exist — that would leave GR/IR "
                        + "clearing carrying a balance nothing can explain.");
    }

    private PurchaseInvoiceNotFoundException(String message) {
        super(message);
    }
}
