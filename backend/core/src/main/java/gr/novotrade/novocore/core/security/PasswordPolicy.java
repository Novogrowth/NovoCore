package gr.novotrade.novocore.core.security;

import gr.novotrade.novocore.core.api.security.InvalidUserException;

/**
 * The minimum a password must satisfy.
 *
 * <p><strong>Length only, no composition rules.</strong> Q22 approved session-based
 * authentication but left the password policy unanswered, so this is a stated default rather than
 * a decision: twelve characters minimum, no required mixture of character classes. That follows
 * current guidance (NIST SP 800-63B), which dropped composition rules because they push people
 * towards predictable substitutions — {@code Password1!} satisfies every classic complexity rule
 * and is among the first guesses in any real attack.
 *
 * <p>Recorded in {@code PROGRESS.md} as an open item, along with 2FA, which is not implemented.
 */
final class PasswordPolicy {

    /**
     * Twelve, not eight. The cost of a longer minimum is negligible for a handful of staff
     * accounts, and this protects a system holding the company's financial records.
     */
    static final int MINIMUM_LENGTH = 12;

    /**
     * Guards against a pathological input being fed straight into BCrypt. BCrypt truncates at 72
     * bytes anyway, so nothing above this adds strength, and an unbounded field is a cheap way to
     * make the server do expensive work.
     */
    static final int MAXIMUM_LENGTH = 200;

    private PasswordPolicy() {
    }

    /**
     * @throws InvalidUserException if the password is unacceptable. The message never quotes the
     *     password itself — an exception message ends up in logs.
     */
    static void check(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new InvalidUserException("A password is required.");
        }
        if (rawPassword.length() < MINIMUM_LENGTH) {
            throw new InvalidUserException(
                    "Password must be at least " + MINIMUM_LENGTH + " characters.");
        }
        if (rawPassword.length() > MAXIMUM_LENGTH) {
            throw new InvalidUserException(
                    "Password must be at most " + MAXIMUM_LENGTH + " characters.");
        }
        if (rawPassword.isBlank()) {
            throw new InvalidUserException("Password must not be only whitespace.");
        }
    }
}
