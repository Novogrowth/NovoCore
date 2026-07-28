package gr.novotrade.novocore.core.api.purchasing;

/** No freight allocation with that id. */
public class FreightAllocationNotFoundException extends RuntimeException {

    public FreightAllocationNotFoundException(long allocationId) {
        super("No freight allocation with id " + allocationId + ".");
    }
}
