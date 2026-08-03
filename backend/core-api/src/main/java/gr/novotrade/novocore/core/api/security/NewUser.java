package gr.novotrade.novocore.core.api.security;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Required;

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
        @Mandatory String username,
        @Mandatory String displayName,
        @Mandatory String rawPassword,
        long roleId) {

    /**
     * ⚠️ <strong>These were {@code Objects.requireNonNull} until Q1 (2026-08-03)</strong> — the
     * fifth confirmed instance of {@code CLAUDE.md}'s <em>a client's mistake raised as a programming
     * error</em>, and the one that showed why the anti-pattern is not a web-layer phenomenon:
     * {@link Required} lived in {@code core.web}, which this module cannot see, so the remedy was
     * <em>structurally unreachable</em> from here. It moved down; these are the first use of it.
     *
     * <p>⚠️ <strong>{@code field} and deliberately not {@code text}</strong>, even though a blank
     * username is also refused. {@code UserServiceImpl} already refuses all three as blank with
     * messages naming the rule — <em>"Username must not be blank."</em>, <em>"Password must be at
     * least 12 characters."</em> — and those are 422s a caller can act on, measured against the
     * running server in Q1's probe. {@code Required.text} here would intercept them one layer
     * earlier and answer a 400 saying less. <strong>Only the absent case was broken; only the absent
     * case is changed.</strong>
     */
    public NewUser {
        Required.field(username, "username");
        Required.field(displayName, "displayName");
        Required.field(rawPassword, "rawPassword");
    }

    /** Keeps the password out of logs and stack traces that print the record. */
    @Override
    public String toString() {
        return "NewUser[username=%s, displayName=%s, roleId=%d, rawPassword=<not shown>]"
                .formatted(username, displayName, roleId);
    }
}
