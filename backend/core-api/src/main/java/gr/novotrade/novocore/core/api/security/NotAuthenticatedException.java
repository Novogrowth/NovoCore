package gr.novotrade.novocore.core.api.security;

/** Something required an authenticated user and there was none. */
public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException() {
        super("No authenticated user for this call.");
    }
}
