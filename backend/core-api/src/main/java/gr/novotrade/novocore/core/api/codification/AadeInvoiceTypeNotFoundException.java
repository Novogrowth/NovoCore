package gr.novotrade.novocore.core.api.codification;

/** No AADE invoice type with that id or code. */
public class AadeInvoiceTypeNotFoundException extends RuntimeException {

    public AadeInvoiceTypeNotFoundException(long id) {
        super("No AADE invoice type with id " + id + ".");
    }

    private AadeInvoiceTypeNotFoundException(String message) {
        super(message);
    }

    public static AadeInvoiceTypeNotFoundException forCode(String code) {
        return new AadeInvoiceTypeNotFoundException(
                "No AADE invoice type with code \"" + code + "\". The 55 codes are seeded from "
                        + "SimpleTypes-v2.0.1.xsd; a code outside that list is not one AADE "
                        + "publishes for this specification version.");
    }
}
