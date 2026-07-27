package gr.novotrade.novocore.core.api.account;

/**
 * A requested change to the chart is not allowed.
 *
 * <p>Separate from {@link AccountNotFoundException} because these are refusals, not absences: a
 * Control account without a sub-ledger, a duplicate name within a group, or an attempt to
 * deactivate an account a posting rule depends on.
 */
public class InvalidAccountException extends RuntimeException {

    public InvalidAccountException(String message) {
        super(message);
    }
}
