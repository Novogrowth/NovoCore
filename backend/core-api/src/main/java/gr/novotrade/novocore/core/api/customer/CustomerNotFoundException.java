package gr.novotrade.novocore.core.api.customer;

/** No such customer. */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(long id) {
        super("No customer with id " + id + ".");
    }
}
