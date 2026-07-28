package gr.novotrade.novocore.core.api.inventory;

/**
 * A consumption that cannot be recorded or reversed as stated.
 *
 * <p><strong>Not thrown for insufficient stock.</strong> Q17 is answered: a sale posts anyway and the
 * shortfall is flagged (ADR 0008). The refusals here are about requests that do not describe anything
 * — a serial-tracked product, a service, a bundle, a quantity the unit of measure cannot express.
 */
public class InvalidStockConsumptionException extends RuntimeException {

    public InvalidStockConsumptionException(String message) {
        super(message);
    }
}
