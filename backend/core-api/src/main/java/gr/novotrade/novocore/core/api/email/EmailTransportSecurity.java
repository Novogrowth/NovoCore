package gr.novotrade.novocore.core.api.email;

/**
 * How the connection to the SMTP server is encrypted.
 *
 * <p>Three values rather than the boolean {@code smtp.start-tls} originally declared in
 * {@code SettingKeys}, because a boolean cannot say which of the two encrypted modes is meant
 * and they are not interchangeable. Novotrade's own server listens on 465, which is
 * {@link #IMPLICIT_TLS}; pointing a STARTTLS client at that port produces a connection that
 * hangs rather than one that fails, because the server is waiting to negotiate TLS while the
 * client waits for a plaintext greeting.
 */
public enum EmailTransportSecurity {

    /**
     * TLS from the first byte, before any SMTP conversation — the SMTPS convention, port 465.
     * This is what {@code mail.novotrade.gr} uses.
     */
    IMPLICIT_TLS,

    /**
     * Connect in plaintext, then upgrade with the {@code STARTTLS} command — port 587.
     *
     * <p>Configured as <em>required</em>, never merely enabled. A server that declines the
     * upgrade would otherwise leave the credentials being sent in the clear, and the send would
     * still appear to succeed.
     */
    STARTTLS,

    /**
     * No encryption at all.
     *
     * <p>Present because a test double is not going to hold a certificate, and because a future
     * relay on localhost is a legitimate case. <strong>Not appropriate for anything reached over
     * a network</strong>: the SMTP password is sent as base64, which is not encryption.
     */
    NONE
}
