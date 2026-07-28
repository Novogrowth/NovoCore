package gr.novotrade.novocore.core.api.settlement;

/** No receipt or payment with that id. */
public class SettlementNotFoundException extends RuntimeException {

    public SettlementNotFoundException(long settlementId) {
        super("No receipt or payment with id " + settlementId + ".");
    }
}
