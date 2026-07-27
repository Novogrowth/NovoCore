package gr.novotrade.novocore.core.api.security;

/**
 * No such user.
 *
 * <p>Only for administrative lookups by id. Never thrown from the authentication path, which
 * returns empty rather than distinguishing an unknown username from a wrong password.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long id) {
        super("No user with id " + id + ".");
    }
}
