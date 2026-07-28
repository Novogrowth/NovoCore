package gr.novotrade.novocore.core.api.sales;

/** No sales invoice with that id. */
public class SalesInvoiceNotFoundException extends RuntimeException {

    public SalesInvoiceNotFoundException(long invoiceId) {
        super("No sales invoice with id " + invoiceId + ".");
    }

    public static SalesInvoiceNotFoundException forLine(long lineId) {
        return new SalesInvoiceNotFoundException(
                "No sales invoice line with id " + lineId + ". A credit note references the line it "
                        + "credits, which is what supplies the rate, the product and the channel, so "
                        + "it cannot credit a line that does not exist.");
    }

    private SalesInvoiceNotFoundException(String message) {
        super(message);
    }
}
