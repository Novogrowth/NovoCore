package gr.novotrade.novocore.core.api.inventory;

/**
 * Thrown when something asks a product for its stock and the product does not have any kind — a
 * service, or a bundle assembled entirely from services.
 *
 * <p><strong>This is the step 5 obligation, discharged.</strong> A service has no lots, so the
 * tempting answer is zero, and zero is exactly wrong: on a screen it is indistinguishable from "sold
 * out", and brief §9's Back-in-Stock Reminders would happily promise a customer that a repair service
 * is coming back into stock. "Not applicable" and "none left" are different facts and this is how the
 * difference is said.
 *
 * <p>{@code ProductView.isStocked()} is what a caller checks first to avoid it.
 */
public class StockNotApplicableException extends RuntimeException {

    public StockNotApplicableException(String message) {
        super(message);
    }

    public static StockNotApplicableException forService(long productId, String sku) {
        return new StockNotApplicableException(
                "Product " + productId + " ('" + sku + "') is a service, so it has no stock. This is "
                        + "not zero stock: answering zero would be indistinguishable from sold out, "
                        + "and would put a service into a back-in-stock reminder. Check "
                        + "ProductView.isStocked() first.");
    }

    public static StockNotApplicableException forServiceOnlyBundle(long productId, String sku) {
        return new StockNotApplicableException(
                "Bundle " + productId + " ('" + sku + "') has no stocked components, so its "
                        + "availability is not limited by stock at all — it is a service package. "
                        + "Answering zero would say the opposite of what is true.");
    }
}
