package gr.novotrade.novocore.core.api.ledger;

/** No such journal entry. */
public class JournalEntryNotFoundException extends RuntimeException {

    public JournalEntryNotFoundException(long id) {
        super("No journal entry with id " + id + ".");
    }
}
