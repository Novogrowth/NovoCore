package gr.novotrade.novocore.core.api.tax;

/** No such VAT class. */
public class VatClassNotFoundException extends RuntimeException {

    public VatClassNotFoundException(long id) {
        super("No VAT class with id " + id + ".");
    }

    public VatClassNotFoundException(String code) {
        super("No VAT class with code '" + code + "'.");
    }
}
