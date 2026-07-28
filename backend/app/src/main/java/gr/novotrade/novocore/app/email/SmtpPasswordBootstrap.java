package gr.novotrade.novocore.app.email;

import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Puts the SMTP password into Settings once, from {@code NOVOCORE_SMTP_PASSWORD}.
 *
 * <p><strong>The password lives in Settings.</strong> That was decided deliberately: all email
 * configuration is stored the same way as the rest of this system's configuration, and the
 * exposure argument for keeping it in the environment instead — that Settings is inside the
 * backup, and step 12 copies backups to Google Drive — does not apply, because access to that
 * Drive is scoped to one person.
 *
 * <p>What the environment provides is the <em>route in</em>, not the home. Every other email
 * setting is seeded by migration V20; the password is not, because a migration is a file in git
 * and a credential in git is in git permanently — readable by anyone who ever clones the
 * repository, present in every CI checkout, and not removable by editing the file. This is the
 * same one-time hand-off {@code NOVOCORE_BOOTSTRAP_OWNER_PASSWORD} makes in step 4, and the
 * variable can be removed from {@code docker/.env} once it has run.
 *
 * <h2>Two deliberate differences from {@code InitialOwnerBootstrap}</h2>
 *
 * <ol>
 *   <li><strong>A missing value does not stop the application.</strong> An instance with no user
 *       accounts cannot be logged into and is useless, so refusing to start is the honest
 *       outcome there. An instance that cannot send email is entirely usable — the outbox holds
 *       what is waiting and the dispatcher reports why — so refusing to start would take a
 *       working financial system down over a notification.
 *   <li><strong>A value that is set but ignored is logged.</strong> {@code InitialOwnerBootstrap}
 *       stays silent once a user exists. Here, somebody editing {@code .env} to change a password
 *       and finding authentication still failing needs to be told where the value actually lives,
 *       or they will keep editing the wrong thing.
 * </ol>
 */
@Component
class SmtpPasswordBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordBootstrap.class);

    private final SettingsService settings;
    private final String suppliedPassword;

    SmtpPasswordBootstrap(
            SettingsService settings,
            @Value("${novocore.smtp.password:}") String suppliedPassword) {
        this.settings = settings;
        this.suppliedPassword = suppliedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean alreadyStored = settings.find(SettingKeys.SMTP_PASSWORD)
                .filter(stored -> !stored.isBlank())
                .isPresent();

        if (alreadyStored) {
            if (!suppliedPassword.isBlank()) {
                log.info("NOVOCORE_SMTP_PASSWORD is set but ignored: the SMTP password is already "
                        + "stored in Settings under '{}', which is where it lives. Change it "
                        + "there, not in the environment, and remove the variable.",
                        SettingKeys.SMTP_PASSWORD);
            }
            return;
        }

        if (suppliedPassword.isBlank()) {
            log.warn("No SMTP password is configured. Email will queue but not send until one is "
                    + "supplied — set NOVOCORE_SMTP_PASSWORD and restart, or write '{}' through "
                    + "Settings. Everything else email needs is seeded.",
                    SettingKeys.SMTP_PASSWORD);
            return;
        }

        // putSecret, so the value is redacted from API responses and the audit log records that
        // it was set without recording what it was set to.
        settings.putSecret(SettingKeys.SMTP_PASSWORD, suppliedPassword);

        log.info("Stored the SMTP password in Settings under '{}'. NOVOCORE_SMTP_PASSWORD is "
                + "ignored from now on and can be removed from the environment.",
                SettingKeys.SMTP_PASSWORD);
    }
}
