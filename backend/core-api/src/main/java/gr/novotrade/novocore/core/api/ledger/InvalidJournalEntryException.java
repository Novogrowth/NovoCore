package gr.novotrade.novocore.core.api.ledger;

/**
 * An entry that cannot be posted, for a reason other than being out of balance — a missing sub-ledger
 * reference on a control account, an inactive account, a VAT dimension on a line that is not a VAT line,
 * two currencies in one entry.
 *
 * <p>Out of balance has its own exception, {@link UnbalancedJournalEntryException}, because that one is
 * {@code CLAUDE.md} rule 6 and a caller may well want to catch exactly it.
 */
public class InvalidJournalEntryException extends RuntimeException {

    public InvalidJournalEntryException(String message) {
        super(message);
    }
}
