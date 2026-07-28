package gr.novotrade.novocore.core.api.email;

import java.util.Optional;

/**
 * What the email configuration currently is, and whether it works.
 *
 * <p>Exists so a Settings screen can answer "will email actually send?" without sending
 * anything, and so the answer is the same one the dispatcher would get — the alternative is an
 * operator reading back six settings and deciding for themselves, which is how a wrong port
 * survives being looked at.
 *
 * <p><strong>Carries no password</strong>, not even redacted, and never will. The username is
 * present because it is the field most likely to be wrong in a way the error message does not
 * make obvious.
 */
public record EmailConfigurationStatus(
        boolean configured,
        boolean reachable,
        String host,
        int port,
        EmailTransportSecurity transportSecurity,
        String username,
        String fromAddress,
        String replyTo,
        String problem) {

    /** Configuration is complete and the server accepted a connection and the credentials. */
    public boolean isUsable() {
        return configured && reachable;
    }

    /** What is wrong, in one line, when {@link #isUsable()} is false. */
    public Optional<String> problemIfAny() {
        return Optional.ofNullable(problem);
    }

    /** A configuration that is missing settings; nothing was attempted against a server. */
    public static EmailConfigurationStatus notConfigured(String problem) {
        return new EmailConfigurationStatus(
                false, false, null, 0, null, null, null, null, problem);
    }
}
