package gr.novotrade.novocore.core.api.banking;

/** A transfer between our own accounts that cannot stand as stated. */
public class InvalidBankTransferException extends RuntimeException {

    public InvalidBankTransferException(String message) {
        super(message);
    }
}
