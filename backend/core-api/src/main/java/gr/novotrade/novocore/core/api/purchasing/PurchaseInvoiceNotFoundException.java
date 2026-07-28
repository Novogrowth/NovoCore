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

    /** The same line, reached from step 10's side: the freight cost an allocation spends. */
    public static PurchaseInvoiceNotFoundException forFreightSourceLine(long lineId) {
        return new PurchaseInvoiceNotFoundException(
                "No purchase invoice line with id " + lineId + ". A freight allocation names the cost "
                        + "it is allocating, so that what is left of that cost stays answerable — "
                        + "there is nothing to allocate out of a line that does not exist.");
    }

    private PurchaseInvoiceNotFoundException(String message) {
        super(message);
    }
}
