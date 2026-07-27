package gr.novotrade.novocore.core.api.security;

import java.util.Objects;

/**
 * Request to create a user account.
 *
 * <p>The password arrives as plain text and is hashed inside the core before anything is stored.
 * It is a {@code String} rather than {@code char[]}: the value has already been through HTTP
 * parsing, JSON deserialisation and the servlet container by this point, so several copies exist
 * that no zeroing here could reach, and pretending otherwise would be security theatre.
 *
 * @param rawPassword checked against the password policy, then hashed. Never stored or logged.
 */
public record NewUser(
        String username,
        String displayName,
        String rawPassword,
        long roleId) {

    public NewUser {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(rawPassword, "rawPassword");
    }

    /** Keeps the password out of logs and stack traces that print the record. */
    @Override
    public String toString() {
        return "NewUser[username=%s, displayName=%s, roleId=%d, rawPassword=<not shown>]"
                .formatted(username, displayName, roleId);
    }
}
