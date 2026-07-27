package gr.novotrade.novocore.core.api.security;

/**
 * A requested user change is not allowed — a taken username, a password failing the policy, or an
 * action that would leave nobody able to administer the system.
 *
 * <p>Messages here must never quote the password.
 */
public class InvalidUserException extends RuntimeException {

    public InvalidUserException(String message) {
        super(message);
    }
}
