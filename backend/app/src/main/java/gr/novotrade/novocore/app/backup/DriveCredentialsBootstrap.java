package gr.novotrade.novocore.app.backup;

import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Puts each Drive destination's OAuth credentials into Settings once, from the environment.
 *
 * <p>The same one-time hand-off {@code SmtpPasswordBootstrap} makes, for the same reason and with
 * the same shape: migration V23 seeds every other backup setting and leaves the client secret and
 * refresh token blank, because a migration is a file in git and a credential in git is in git
 * permanently. The environment is the route in, not the home.
 *
 * <p>Unlike the SMTP password these arrive in pairs, per destination, and the pair is treated as
 * one thing. Storing a client secret without its refresh token would leave a destination that
 * looks half-configured and fails every night — so a partially-supplied pair is refused with a
 * message naming the missing half.
 *
 * <p>A missing value never stops the application. An instance that cannot reach Drive still backs
 * up locally, and refusing to start would turn a missing credential into an outage of a working
 * financial system.
 */
@Component
class DriveCredentialsBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DriveCredentialsBootstrap.class);

    /** The settings prefix each destination is configured under, matching V23. */
    private static final List<String> DESTINATIONS = List.of("primary", "secondary");

    /** The two secret halves. The non-secret half of the configuration is seeded by migration. */
    private static final List<String> SECRETS = List.of("client-secret", "refresh-token");

    private final SettingsService settings;
    private final Environment environment;
    private final boolean announceKeyObligation;

    DriveCredentialsBootstrap(SettingsService settings, Environment environment,
            @Value("${novocore.backup.encryption-key:${NOVOCORE_BACKUP_ENCRYPTION_KEY:}}")
            String encryptionKey) {
        this.settings = settings;
        this.environment = environment;
        this.announceKeyObligation = !encryptionKey.isBlank();
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String destination : DESTINATIONS) {
            applyTo(destination);
        }
        announceEncryptionKeyObligation();
    }

    private void applyTo(String destination) {
        Map<String, String> supplied = new LinkedHashMap<>();
        for (String secret : SECRETS) {
            supplied.put(secret, environmentValue(destination, secret));
        }

        boolean anySupplied = supplied.values().stream().anyMatch(value -> !value.isBlank());
        boolean allSupplied = supplied.values().stream().noneMatch(String::isBlank);

        if (anySupplied && !allSupplied) {
            String missing = supplied.entrySet().stream()
                    .filter(entry -> entry.getValue().isBlank())
                    .map(entry -> variableName(destination, entry.getKey()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            log.error("Backup destination '{}' was given only part of its credentials — {} {} "
                    + "missing. Nothing was stored: a half-configured destination fails every "
                    + "night while appearing to be set up.",
                    destination, missing, missing.contains(",") ? "are" : "is");
            return;
        }

        boolean alreadyStored = SECRETS.stream().allMatch(secret ->
                settings.find(settingKey(destination, secret))
                        .filter(stored -> !stored.isBlank())
                        .isPresent());

        if (alreadyStored) {
            if (anySupplied) {
                log.info("The credentials for backup destination '{}' are already in Settings; the "
                        + "environment variables are ignored and can be removed. Change them "
                        + "through Settings.", destination);
            }
            return;
        }

        if (!anySupplied) {
            log.warn("Backup destination '{}' has no credentials, so backups will not be copied "
                    + "there. Complete the OAuth consent flow for that Google account and supply "
                    + "{} and {}.", destination,
                    variableName(destination, "client-secret"),
                    variableName(destination, "refresh-token"));
            return;
        }

        supplied.forEach((secret, value) ->
                settings.putSecret(settingKey(destination, secret), value));
        log.info("Stored the OAuth credentials for backup destination '{}' in Settings. Its "
                + "environment variables are ignored from now on and can be removed.", destination);
    }

    /**
     * Says, once per start, that the encryption key has to exist somewhere other than this machine.
     *
     * <p>Not a warning about something being wrong — it is a reminder about something that is only
     * discoverable when it is too late to act on. A key held solely in {@code docker/.env} means
     * losing the host loses both the database and every backup of it, on the one occasion both
     * were meant to help.
     */
    private void announceEncryptionKeyObligation() {
        if (announceKeyObligation) {
            log.info("Backups are encrypted with NOVOCORE_BACKUP_ENCRYPTION_KEY. That key is not "
                    + "stored in this database — it cannot be, since the settings table is inside "
                    + "the dump. If it exists only in docker/.env on this machine, every backup "
                    + "becomes unreadable the day this machine is lost. Record it in a password "
                    + "manager.");
        } else {
            log.error("NOVOCORE_BACKUP_ENCRYPTION_KEY is not set, so NO BACKUPS WILL BE TAKEN. "
                    + "Generate one with `openssl rand -base64 32`.");
        }
    }

    private String environmentValue(String destination, String secret) {
        String value = environment.getProperty(variableName(destination, secret));
        if (value == null) {
            // Spring's relaxed binding also exposes the variable under its dotted form; checked
            // second so a test or an application.yml can supply it without an env var.
            value = environment.getProperty(
                    "novocore.backup.drive.%s.%s".formatted(destination, secret));
        }
        return value == null ? "" : value.trim();
    }

    private static String variableName(String destination, String secret) {
        return "NOVOCORE_BACKUP_DRIVE_%s_%s".formatted(
                destination.toUpperCase(java.util.Locale.ROOT),
                secret.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    }

    private static String settingKey(String destination, String secret) {
        return "backup.drive.%s.%s".formatted(destination, secret);
    }
}
