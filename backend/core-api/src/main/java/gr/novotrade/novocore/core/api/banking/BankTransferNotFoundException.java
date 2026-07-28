package gr.novotrade.novocore.core.api.banking;

/** No bank transfer with that id. */
public class BankTransferNotFoundException extends RuntimeException {

    public BankTransferNotFoundException(long transferId) {
        super("No bank transfer with id " + transferId + ".");
    }
}
