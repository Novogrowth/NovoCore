package gr.novotrade.novocore.core.api.account;

/**
 * No such account. Names what was looked for, so the failure is readable without a debugger.
 *
 * <p>The {@link AccountSystemKey} constructor is the one that matters: a missing keyed account
 * means a posting rule has nowhere to post, which must fail loudly rather than be worked around
 * ({@code CLAUDE.md} rule 8).
 */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(long id) {
        super("No account with id " + id + ".");
    }

    public AccountNotFoundException(AccountSystemKey systemKey) {
        super("No account carries the system key " + systemKey + ". A posting rule depends on "
                + "it, so this is a broken chart of accounts rather than a missing option — "
                + "check that the chart-of-accounts migration ran.");
    }
}
