package gr.novotrade.novocore.core.api.sales;

/** A credit note that cannot be issued as stated. The message says which rule and why. */
public class InvalidCreditNoteException extends RuntimeException {

    public InvalidCreditNoteException(String message) {
        super(message);
    }
}
