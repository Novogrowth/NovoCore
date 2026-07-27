package gr.novotrade.novocore.core.api.security;

/**
 * A requested role change is not allowed — a taken name, an attempt to modify a system role, or
 * deactivating a role that users still hold.
 */
public class InvalidRoleException extends RuntimeException {

    public InvalidRoleException(String message) {
        super(message);
    }
}
