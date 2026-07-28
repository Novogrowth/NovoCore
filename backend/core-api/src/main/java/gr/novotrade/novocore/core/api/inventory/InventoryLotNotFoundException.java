package gr.novotrade.novocore.core.api.inventory;

/** No such inventory lot. */
public class InventoryLotNotFoundException extends RuntimeException {

    public InventoryLotNotFoundException(long id) {
        super("No inventory lot with id " + id + ".");
    }
}
