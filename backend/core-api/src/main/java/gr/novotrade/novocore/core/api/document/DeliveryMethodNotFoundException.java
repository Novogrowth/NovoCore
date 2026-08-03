package gr.novotrade.novocore.core.api.document;

/** No delivery method with that id. */
public class DeliveryMethodNotFoundException extends RuntimeException {

    public DeliveryMethodNotFoundException(long id) {
        super("No delivery method with id " + id + ".");
    }
}
