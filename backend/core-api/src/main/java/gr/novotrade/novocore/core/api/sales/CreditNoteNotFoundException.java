package gr.novotrade.novocore.core.api.sales;

/** No credit note with that id. */
public class CreditNoteNotFoundException extends RuntimeException {

    public CreditNoteNotFoundException(long creditNoteId) {
        super("No credit note with id " + creditNoteId + ".");
    }
}
