package gr.novotrade.novocore.core.api.inventory;

/** No such write-off. */
public class StockWriteOffNotFoundException extends RuntimeException {

    public StockWriteOffNotFoundException(long id) {
        super("No stock write-off with id " + id + ".");
    }
}
