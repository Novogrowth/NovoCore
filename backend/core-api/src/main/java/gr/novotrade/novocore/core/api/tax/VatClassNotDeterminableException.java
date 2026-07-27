package gr.novotrade.novocore.core.api.tax;

/**
 * No level specified a VAT class, so the rate for a line cannot be determined.
 *
 * <p>An error rather than a default. Falling back to the standard rate would produce a plausible
 * invoice at a rate nobody chose, and if the fallback were too low the shortfall is not
 * recoverable from the customer once the invoice has been issued.
 */
public class VatClassNotDeterminableException extends RuntimeException {

    public VatClassNotDeterminableException() {
        super("No VAT class is set on the invoice line, the customer, or the product, so the "
                + "rate cannot be determined. NovoCore does not fall back to a standard rate: "
                + "set a default VAT class on the product, or an override on the line.");
    }
}
