package gr.novotrade.novocore.core.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.api.email.EmailNotConfiguredException;
import gr.novotrade.novocore.core.api.email.EmailTransportSecurity;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The configuration reading and the Jakarta Mail property mapping, tested without a server.
 *
 * <p>The property mapping is worth its own test because getting it wrong does not produce an
 * error: a STARTTLS client pointed at an implicit-TLS port sits waiting for a greeting that the
 * server will not send until TLS is negotiated, so the symptom is a timeout minutes later rather
 * than a refusal. Asserting the three property sets is the only cheap way to know which mode is
 * actually in force.
 */
class SmtpConfigurationTest {

    private static FakeSettingsService complete() {
        return new FakeSettingsService()
                .set(SettingKeys.SMTP_HOST, "mail.novotrade.gr")
                .set(SettingKeys.SMTP_PORT, "465")
                .set(SettingKeys.SMTP_USERNAME, "erp@novotrade.gr")
                .set(SettingKeys.SMTP_PASSWORD, "a-password")
                .set(SettingKeys.SMTP_TRANSPORT_SECURITY, "IMPLICIT_TLS")
                .set(SettingKeys.SMTP_FROM_ADDRESS, "erp@novotrade.gr")
                .set(SettingKeys.SMTP_FROM_NAME, "Java Jives")
                .set(SettingKeys.SMTP_REPLY_TO, "kostas@novotrade.gr");
    }

    @Test
    @DisplayName("a complete configuration reads back as supplied")
    void readsCompleteConfiguration() {
        SmtpConfiguration configuration = SmtpConfiguration.readFrom(complete());

        assertThat(configuration.host()).isEqualTo("mail.novotrade.gr");
        assertThat(configuration.port()).isEqualTo(465);
        assertThat(configuration.transportSecurity())
                .isEqualTo(EmailTransportSecurity.IMPLICIT_TLS);
        assertThat(configuration.fromAddress()).isEqualTo("erp@novotrade.gr");
        assertThat(configuration.replyTo()).isEqualTo("kostas@novotrade.gr");
        assertThat(configuration.authenticates()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "smtp.host", "smtp.port", "smtp.transport-security",
        "smtp.from-address", "smtp.reply-to",
    })
    @DisplayName("each required setting names itself when missing")
    void missingRequiredSettingNamesItself(String key) {
        assertThatExceptionOfType(EmailNotConfiguredException.class)
                .isThrownBy(() -> SmtpConfiguration.readFrom(complete().remove(key)))
                .withMessageContaining(key);
    }

    @Test
    @DisplayName("Reply-To is required, not optional")
    void replyToIsRequired() {
        // The load-bearing one. erp@novotrade.gr is an unmonitored send-only mailbox, so a
        // missing Reply-To routes every customer reply into a black hole with no symptom — the
        // exact failure mode this codebase refuses to default its way through.
        assertThatExceptionOfType(EmailNotConfiguredException.class)
                .isThrownBy(() -> SmtpConfiguration.readFrom(
                        complete().remove(SettingKeys.SMTP_REPLY_TO)))
                .withMessageContaining(SettingKeys.SMTP_REPLY_TO);
    }

    @Test
    @DisplayName("a username without a password is refused as half-finished configuration")
    void halfSuppliedCredentialsAreRefused() {
        assertThatExceptionOfType(EmailNotConfiguredException.class)
                .isThrownBy(() -> SmtpConfiguration.readFrom(
                        complete().remove(SettingKeys.SMTP_PASSWORD)))
                .withMessageContaining("NOVOCORE_SMTP_PASSWORD");

        assertThatExceptionOfType(EmailNotConfiguredException.class)
                .isThrownBy(() -> SmtpConfiguration.readFrom(
                        complete().remove(SettingKeys.SMTP_USERNAME)));
    }

    @Test
    @DisplayName("neither username nor password is a valid unauthenticated relay")
    void noCredentialsIsAllowed() {
        SmtpConfiguration configuration = SmtpConfiguration.readFrom(complete()
                .remove(SettingKeys.SMTP_USERNAME)
                .remove(SettingKeys.SMTP_PASSWORD));

        assertThat(configuration.authenticates()).isFalse();
        assertThat(configuration.mailProperties()).containsEntry("mail.smtp.auth", "false");
    }

    @Test
    @DisplayName("a nonsense port is refused rather than dialled")
    void portIsValidated() {
        assertThatExceptionOfType(EmailNotConfiguredException.class)
                .isThrownBy(() -> SmtpConfiguration.readFrom(
                        complete().set(SettingKeys.SMTP_PORT, "0")));
        assertThatExceptionOfType(EmailNotConfiguredException.class)
                .isThrownBy(() -> SmtpConfiguration.readFrom(
                        complete().set(SettingKeys.SMTP_PORT, "70000")));
        assertThatExceptionOfType(EmailNotConfiguredException.class)
                .isThrownBy(() -> SmtpConfiguration.readFrom(
                        complete().set(SettingKeys.SMTP_PORT, "four-six-five")))
                .withMessageContaining("not a number");
    }

    @Test
    @DisplayName("an unknown transport security value names the three that exist")
    void unknownTransportSecurityIsRefused() {
        assertThatExceptionOfType(EmailNotConfiguredException.class)
                .isThrownBy(() -> SmtpConfiguration.readFrom(
                        complete().set(SettingKeys.SMTP_TRANSPORT_SECURITY, "SSL")))
                .withMessageContaining("IMPLICIT_TLS")
                .withMessageContaining("STARTTLS");
    }

    @Test
    @DisplayName("IMPLICIT_TLS enables SSL from the first byte and never STARTTLS")
    void implicitTlsProperties() {
        Properties properties = SmtpConfiguration.readFrom(complete()).mailProperties();

        assertThat(properties)
                .containsEntry("mail.smtp.ssl.enable", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .containsEntry("mail.smtp.auth", "true")
                .doesNotContainKey("mail.smtp.starttls.enable");
    }

    @Test
    @DisplayName("STARTTLS is required, not merely enabled")
    void startTlsIsRequiredNotOptional() {
        Properties properties = SmtpConfiguration.readFrom(
                complete()
                        .set(SettingKeys.SMTP_TRANSPORT_SECURITY, "STARTTLS")
                        .set(SettingKeys.SMTP_PORT, "587"))
                .mailProperties();

        assertThat(properties)
                .containsEntry("mail.smtp.starttls.enable", "true")
                // With `enable` alone, a server that declines the upgrade receives the password
                // in the clear and the send still reports success.
                .containsEntry("mail.smtp.starttls.required", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .doesNotContainKey("mail.smtp.ssl.enable");
    }

    @Test
    @DisplayName("NONE turns both off, so a test double is not accidentally reached over TLS")
    void noneProperties() {
        Properties properties = SmtpConfiguration.readFrom(
                complete().set(SettingKeys.SMTP_TRANSPORT_SECURITY, "NONE"))
                .mailProperties();

        assertThat(properties)
                .containsEntry("mail.smtp.ssl.enable", "false")
                .containsEntry("mail.smtp.starttls.enable", "false");
    }

    @Test
    @DisplayName("every mode sets finite timeouts, because Jakarta Mail's defaults are infinite")
    void timeoutsAreAlwaysSet() {
        for (EmailTransportSecurity security : EmailTransportSecurity.values()) {
            Properties properties = SmtpConfiguration.readFrom(
                    complete().set(SettingKeys.SMTP_TRANSPORT_SECURITY, security.name()))
                    .mailProperties();

            assertThat(properties)
                    .as("%s must not be able to block the dispatcher thread forever", security)
                    .containsKeys("mail.smtp.connectiontimeout", "mail.smtp.timeout",
                            "mail.smtp.writetimeout");
        }
    }

    @Test
    @DisplayName("the From header carries the display name when there is one")
    void fromHeader() {
        assertThat(SmtpConfiguration.readFrom(complete()).fromHeader())
                .isEqualTo("Java Jives <erp@novotrade.gr>");

        assertThat(SmtpConfiguration.readFrom(complete().remove(SettingKeys.SMTP_FROM_NAME))
                .fromHeader())
                .isEqualTo("erp@novotrade.gr");
    }
}
