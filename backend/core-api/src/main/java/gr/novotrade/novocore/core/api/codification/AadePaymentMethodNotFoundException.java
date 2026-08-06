package gr.novotrade.novocore.core.api.codification;

/** No AADE payment-method article has that id. */
public class AadePaymentMethodNotFoundException extends RuntimeException {

    public AadePaymentMethodNotFoundException(long id) {
        super("No AADE payment-method article with id " + id + ".");
    }
}
