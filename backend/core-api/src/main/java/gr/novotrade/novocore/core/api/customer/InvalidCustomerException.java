package gr.novotrade.novocore.core.api.customer;

/**
 * A requested customer change is not allowed — a blank name, a VAT number another customer already
 * has, or a VAT status missing the VAT number or exemption reason it requires.
 */
public class InvalidCustomerException extends RuntimeException {

    public InvalidCustomerException(String message) {
        super(message);
    }
}
