package gr.novotrade.novocore.core.api.ledger;

/**
 * This entry may not be edited in place — <strong>Q13</strong>.
 *
 * <p>Separate from {@link InvalidJournalEntryException} because the answer is always the same and worth
 * stating in the message: post a reversing entry, then post the corrected one.
 */
public class JournalEntryNotAmendableException extends RuntimeException {

    public JournalEntryNotAmendableException(long entryId, JournalSource source) {
        super("Journal entry " + entryId + " came from a " + source + ", which is immutable once "
                + "posted (Q13). Correct it by posting a reversing entry and then the corrected one, "
                + "so the ledger shows what was recorded, that it was withdrawn, and what replaced "
                + "it — none of which an in-place edit leaves behind.");
    }

    public JournalEntryNotAmendableException(long entryId, String reason) {
        super("Journal entry " + entryId + " cannot be amended: " + reason);
    }
}
