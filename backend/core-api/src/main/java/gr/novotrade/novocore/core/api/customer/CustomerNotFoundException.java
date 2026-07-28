package gr.novotrade.novocore.core.api.customer;

/** No such customer. */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(long id) {
        super("No customer with id " + id + ".");
    }

    /** For a lookup by {@link CustomerSystemKey}, where the absence means a broken seed. */
    public CustomerNotFoundException(String message) {
        super(message);
    }
}
