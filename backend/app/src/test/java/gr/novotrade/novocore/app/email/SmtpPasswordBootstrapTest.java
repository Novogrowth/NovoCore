package gr.novotrade.novocore.app.email;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one-time hand-off of the SMTP password from the environment into Settings.
 *
 * <p>Mocked rather than run against a container, for the reason {@code InitialOwnerBootstrapTest}
 * gives: what is being tested is a decision about where a value lives, and that needs no database.
 */
class SmtpPasswordBootstrapTest {

    @Test
    @DisplayName("stores the supplied password as a secret when none is held yet")
    void storesTheSuppliedPassword() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.find(SettingKeys.SMTP_PASSWORD)).thenReturn(Optional.empty());

        new SmtpPasswordBootstrap(settings, "a-real-password").run(null);

        // putSecret, not put: the value must be redacted from API responses and must never reach
        // the audit log.
        verify(settings).putSecret(SettingKeys.SMTP_PASSWORD, "a-real-password");
        verify(settings, never()).put(eq(SettingKeys.SMTP_PASSWORD), any());
    }

    @Test
    @DisplayName("does not overwrite a password already held in Settings")
    void doesNotOverwriteWhatIsAlreadyStored() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.find(SettingKeys.SMTP_PASSWORD))
                .thenReturn(Optional.of("the-one-that-is-actually-in-use"));

        new SmtpPasswordBootstrap(settings, "a-stale-value-left-in-dotenv").run(null);

        // Settings is where the password lives, so a value someone forgot to remove from the
        // environment must not silently replace one changed through the application.
        verify(settings, never()).putSecret(any(), any());
    }

    @Test
    @DisplayName("a blank stored value is treated as absent, not as a password")
    void blankStoredValueIsNotAPassword() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.find(SettingKeys.SMTP_PASSWORD)).thenReturn(Optional.of("   "));

        new SmtpPasswordBootstrap(settings, "a-real-password").run(null);

        verify(settings).putSecret(SettingKeys.SMTP_PASSWORD, "a-real-password");
    }

    @Test
    @DisplayName("no password anywhere is a warning, not a refusal to start")
    void missingPasswordDoesNotStopTheApplication() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.find(SettingKeys.SMTP_PASSWORD)).thenReturn(Optional.empty());

        // Deliberately unlike InitialOwnerBootstrap. An instance with no user accounts cannot be
        // logged into and is useless, so refusing to start is honest there. An instance that
        // cannot send email is entirely usable, and taking a financial system down over a
        // notification would be the wrong trade.
        new SmtpPasswordBootstrap(settings, "").run(null);

        verify(settings, never()).putSecret(any(), any());
    }
}
