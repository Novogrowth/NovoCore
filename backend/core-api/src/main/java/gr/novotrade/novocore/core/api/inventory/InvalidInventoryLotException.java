package gr.novotrade.novocore.core.api.inventory;

/**
 * A requested inventory change is not allowed — an unknown, inactive, unstocked or bundled product,
 * a quantity the product's unit of measure cannot express, a duplicate serial number, a serialized
 * receipt for a pooled product or the reverse, or a location move applied to the wrong shape of lot.
 */
public class InvalidInventoryLotException extends RuntimeException {

    public InvalidInventoryLotException(String message) {
        super(message);
    }
}
