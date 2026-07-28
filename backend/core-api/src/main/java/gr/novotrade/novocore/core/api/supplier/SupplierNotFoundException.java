package gr.novotrade.novocore.core.api.supplier;

/** No such supplier. */
public class SupplierNotFoundException extends RuntimeException {

    public SupplierNotFoundException(long id) {
        super("No supplier with id " + id + ".");
    }
}
