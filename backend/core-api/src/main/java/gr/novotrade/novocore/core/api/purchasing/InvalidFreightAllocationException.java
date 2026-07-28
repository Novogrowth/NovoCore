package gr.novotrade.novocore.core.api.purchasing;

/** A landed-cost allocation that cannot be posted or reversed as stated. */
public class InvalidFreightAllocationException extends RuntimeException {

    public InvalidFreightAllocationException(String message) {
        super(message);
    }
}
