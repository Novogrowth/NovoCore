package gr.novotrade.novocore.core.api.account;

/** No such account group. */
public class AccountGroupNotFoundException extends RuntimeException {

    public AccountGroupNotFoundException(long id) {
        super("No account group with id " + id + ".");
    }
}
