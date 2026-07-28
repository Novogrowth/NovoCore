package gr.novotrade.novocore.core.api.settlement;

/** A receipt, payment or allocation that cannot stand as stated. The message says which rule and why. */
public class InvalidSettlementException extends RuntimeException {

    public InvalidSettlementException(String message) {
        super(message);
    }
}
