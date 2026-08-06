package gr.novotrade.novocore.core.api.sales;

/**
 * No payment method has that id.
 *
 * <p>⚠️ <strong>Its meaning changed in R4, and the change is worth noticing.</strong> It used to be
 * raised when a {@code SettlementMethod} enum value had no seeded row — a <em>drift bug</em> rather
 * than a caller's mistake. Payment methods are now user-created rows with surrogate ids, so there is
 * no seed to drift from, and this is an ordinary "the id names nothing": exactly the case
 * {@code CLAUDE.md} requires a {@code ...NotFoundException} for, answering 404 rather than a bare 400.
 */
public class PaymentMethodNotFoundException extends RuntimeException {

    public PaymentMethodNotFoundException(long id) {
        super("No payment method with id " + id + ".");
    }
}
