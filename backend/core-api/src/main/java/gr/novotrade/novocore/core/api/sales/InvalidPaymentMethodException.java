package gr.novotrade.novocore.core.api.sales;

/** A caller's mistake about a payment method — answered 422 with the reason. */
public class InvalidPaymentMethodException extends RuntimeException {

    public InvalidPaymentMethodException(String message) {
        super(message);
    }
}
