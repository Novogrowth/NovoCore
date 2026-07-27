package gr.novotrade.novocore.core.api.security;

/** No such role. */
public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(long id) {
        super("No role with id " + id + ".");
    }

    public RoleNotFoundException(String name) {
        super("No role named '" + name + "'.");
    }
}
