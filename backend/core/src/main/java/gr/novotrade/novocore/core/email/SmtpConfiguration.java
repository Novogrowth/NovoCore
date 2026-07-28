package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.email.EmailNotConfiguredException;
import gr.novotrade.novocore.core.api.email.EmailTransportSecurity;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingValueException;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * The SMTP configuration, read from Settings.
 *
 * <p>Read fresh on each dispatch cycle rather than held in a bean built at startup. A setting is
 * changed precisely because something is wrong, and a configuration cached at boot would mean
 * the fix does not take effect until somebody restarts the application — which, given the fix is
 * usually being made because email is not arriving, is exactly the moment nobody wants to be
 * told to restart. Reading eleven rows from a table is not a cost worth optimising against that.
 *
 * @param replyTo where replies go. Never null: it is required, because the sending mailbox is
 *     unmonitored and a missing value would route every reply into it silently.
 */
record SmtpConfiguration(
        String host,
        int port,
        String username,
        String password,
        EmailTransportSecurity transportSecurity,
        String fromAddress,
        String fromName,
        String replyTo) {

    /**
     * How long to wait on the network before giving up, in milliseconds.
     *
     * <p>Constants rather than settings, deliberately — four more rows nobody would ever tune,
     * guarding against something that is not a matter of preference. Jakarta Mail's own defaults
     * are <em>infinite</em>, so without these a mail server that accepts a connection and then
     * stops responding blocks the dispatcher thread permanently and every queued message behind
     * it stops moving, with the outbox showing nothing wrong.
     */
    private static final String CONNECT_TIMEOUT_MS = "15000";
    private static final String READ_TIMEOUT_MS = "30000";
    private static final String WRITE_TIMEOUT_MS = "30000";

    /**
     * Reads the configuration, naming the first thing that is missing or unusable.
     *
     * @throws EmailNotConfiguredException if a required setting is absent or malformed. Named
     *     rather than letting {@link SettingNotFoundException} escape, so the caller sees "email
     *     is not configured, smtp.host is missing" rather than a bare missing-key complaint that
     *     does not say what feature it belongs to.
     */
    static SmtpConfiguration readFrom(SettingsService settings) {
        String host = required(settings, SettingKeys.SMTP_HOST);
        int port = requiredPort(settings);
        EmailTransportSecurity security = requiredSecurity(settings);
        String from = required(settings, SettingKeys.SMTP_FROM_ADDRESS);
        String replyTo = required(settings, SettingKeys.SMTP_REPLY_TO);

        // Username and password are optional as a pair: an unauthenticated relay on localhost is
        // a real deployment. One without the other is not — it is half-finished configuration,
        // and it would authenticate as nobody against a server that expects credentials.
        String username = settings.find(SettingKeys.SMTP_USERNAME).map(String::trim)
                .filter(value -> !value.isEmpty()).orElse(null);
        String password = settings.find(SettingKeys.SMTP_PASSWORD)
                .filter(value -> !value.isEmpty()).orElse(null);
        if ((username == null) != (password == null)) {
            throw new EmailNotConfiguredException(
                    "Email is not configured: '%s' and '%s' must either both be set or both be "
                            .formatted(SettingKeys.SMTP_USERNAME, SettingKeys.SMTP_PASSWORD)
                            + "absent. Only "
                            + (username == null ? "the password" : "the username") + " is set. "
                            + "If the password has not been supplied yet, set NOVOCORE_SMTP_PASSWORD "
                            + "and restart, or write it through Settings.");
        }

        String fromName = settings.find(SettingKeys.SMTP_FROM_NAME).map(String::trim)
                .filter(value -> !value.isEmpty()).orElse(null);

        return new SmtpConfiguration(
                host, port, username, password, security, from, fromName, replyTo);
    }

    boolean authenticates() {
        return username != null;
    }

    /** Builds the sender used for one dispatch cycle, or for a connection test. */
    JavaMailSenderImpl toMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        // Bodies, subjects and attachment filenames are routinely Greek. Left to the platform
        // default this would be windows-1252 on the development machine and UTF-8 in the
        // container, so the same message would arrive readable from one and mojibake from the
        // other — with nothing failing in either case.
        sender.setDefaultEncoding("UTF-8");
        sender.setJavaMailProperties(mailProperties());
        return sender;
    }

    /**
     * The Jakarta Mail properties for this configuration. Separate from
     * {@link #toMailSender()} so the mapping can be asserted without a server — the difference
     * between implicit TLS and STARTTLS is three property names and getting it wrong produces a
     * connection that hangs rather than one that reports an error.
     */
    Properties mailProperties() {
        Properties properties = new Properties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", String.valueOf(authenticates()));

        properties.put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MS);
        properties.put("mail.smtp.timeout", READ_TIMEOUT_MS);
        properties.put("mail.smtp.writetimeout", WRITE_TIMEOUT_MS);

        switch (transportSecurity) {
            case IMPLICIT_TLS -> {
                properties.put("mail.smtp.ssl.enable", "true");
                properties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                // Stated rather than relied upon. Without hostname verification, TLS proves only
                // that somebody holds a valid certificate for some name — not that it is the
                // server we meant to hand the password to.
                properties.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case STARTTLS -> {
                properties.put("mail.smtp.starttls.enable", "true");
                // required, not merely enabled: with only `enable`, a server that declines the
                // upgrade gets the credentials in the clear and the send still reports success.
                properties.put("mail.smtp.starttls.required", "true");
                properties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                properties.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NONE -> {
                properties.put("mail.smtp.ssl.enable", "false");
                properties.put("mail.smtp.starttls.enable", "false");
            }
            default -> throw new IllegalStateException(
                    "Unhandled transport security " + transportSecurity);
        }
        return properties;
    }

    /** The sender as it appears in the From header, with the display name when one is set. */
    String fromHeader() {
        return fromName == null ? fromAddress : "%s <%s>".formatted(fromName, fromAddress);
    }

    private static String required(SettingsService settings, String key) {
        String value = settings.find(key).map(String::trim).orElse("");
        if (value.isEmpty()) {
            throw new EmailNotConfiguredException(
                    "Email is not configured: setting '%s' is missing.".formatted(key));
        }
        return value;
    }

    private static int requiredPort(SettingsService settings) {
        int port;
        try {
            port = settings.requireInt(SettingKeys.SMTP_PORT);
        } catch (SettingNotFoundException | SettingValueException e) {
            throw new EmailNotConfiguredException(
                    "Email is not configured: setting '%s' is missing or not a number."
                            .formatted(SettingKeys.SMTP_PORT), e);
        }
        if (port < 1 || port > 65535) {
            throw new EmailNotConfiguredException(
                    "Setting '%s' is %d, which is not a TCP port."
                            .formatted(SettingKeys.SMTP_PORT, port));
        }
        return port;
    }

    private static EmailTransportSecurity requiredSecurity(SettingsService settings) {
        String value = required(settings, SettingKeys.SMTP_TRANSPORT_SECURITY);
        try {
            return EmailTransportSecurity.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EmailNotConfiguredException(
                    "Setting '%s' is '%s'; expected one of IMPLICIT_TLS, STARTTLS or NONE."
                            .formatted(SettingKeys.SMTP_TRANSPORT_SECURITY, value), e);
        }
    }
}
