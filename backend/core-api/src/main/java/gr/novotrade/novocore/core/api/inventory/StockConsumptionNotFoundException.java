package gr.novotrade.novocore.core.api.inventory;

/** No stock consumption with that id. */
public class StockConsumptionNotFoundException extends RuntimeException {

    public StockConsumptionNotFoundException(long consumptionId) {
        super("No stock consumption with id " + consumptionId + ".");
    }
}
