package gr.novotrade.novocore.core.api.sales;

/**
 * ⚠️ Raised when a {@link SettlementMethod} value has no {@code payment_method} row.
 *
 * <p>That is a <strong>drift bug</strong> rather than a caller's mistake — the seed is supposed to
 * carry exactly one row per enum value, and {@code PaymentMethodIT} asserts it in both directions.
 * It is a NotFoundException rather than an IllegalStateException because a caller naming a method
 * that genuinely has no row should get a 404 saying so, not a 500 describing internal state.
 */
public class PaymentMethodNotFoundException extends RuntimeException {

    public PaymentMethodNotFoundException(SettlementMethod method) {
        super("Payment method " + method + " has no row. The seed must carry one row per "
                + "SettlementMethod value; this is a defect rather than a bad request.");
    }
}
