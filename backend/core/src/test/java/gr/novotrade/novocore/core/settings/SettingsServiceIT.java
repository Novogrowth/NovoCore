package gr.novotrade.novocore.core.settings;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingNotFoundException;
import gr.novotrade.novocore.core.api.settings.SettingValueException;
import gr.novotrade.novocore.core.api.settings.SettingView;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SettingsServiceIT extends AbstractCoreIntegrationTest {

    @Autowired
    private SettingsService settings;

    @Autowired
    private AuditLogService auditLog;

    @Test
    @DisplayName("the seeded rounding threshold reads back as the €0.03 the brief specifies")
    void seededRoundingThreshold() {
        assertThat(settings.requireEurAmount(SettingKeys.LEDGER_ROUNDING_THRESHOLD))
                .isEqualTo(Money.ofEur("0.03"));
    }

    @Test
    @DisplayName("the seeded cash limit reads back as €500")
    void seededCashLimit() {
        assertThat(settings.requireEurAmount(SettingKeys.CASH_PAYMENT_LIMIT))
                .isEqualTo(Money.ofEur("500.00"));
    }

    @Test
    @DisplayName("the seeded rounding mode parses to a real RoundingMode")
    void seededRoundingMode() {
        assertThat(settings.requireRoundingMode(SettingKeys.LEDGER_ROUNDING_MODE))
                .isEqualTo(RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("no migration seeds the SMTP password")
    void noMigrationSeedsTheSmtpPassword() throws Exception {
        // Asserted against the migration files rather than against the settings table, and
        // deliberately: "is it seeded?" is a question about what is committed to this
        // repository, and the live table is written to by the email service's own tests, which
        // would make a database-level assertion depend on test ordering.
        //
        // Why it matters: a migration is a file in git, and a credential in git is in git
        // permanently — readable by anyone who ever clones the repository, present in every CI
        // checkout, and not removable by editing the file. The password reaches Settings once
        // from NOVOCORE_SMTP_PASSWORD instead, the same route the first owner's password takes.
        // A placeholder value would be worse still, because it would look like configuration.
        for (String migration : migrationSources()) {
            assertThat(migration)
                    .as("a migration must never insert a credential")
                    .doesNotContain("'" + SettingKeys.SMTP_PASSWORD + "'");
        }
    }

    @Test
    @DisplayName("V20 seeds the real email configuration, and the reply address is not the sender")
    void emailConfigurationIsSeeded() throws Exception {
        String v20 = Files.readString(migrationPath("V20__email_outbox.sql"), UTF_8);

        assertThat(v20)
                .contains("'mail.novotrade.gr'")
                .contains("'465'")
                .contains("'IMPLICIT_TLS'")
                .contains("'erp@novotrade.gr'")
                .contains("'kostas@novotrade.gr'");

        // The entire reason smtp.reply-to exists and is required: erp@novotrade.gr is a
        // send-only mailbox nobody reads, so if these two were ever the same address, every
        // customer reply would land in it with no visible symptom.
        assertThat(settings.require(SettingKeys.SMTP_REPLY_TO))
                .isNotEqualTo(settings.require(SettingKeys.SMTP_FROM_ADDRESS));
    }

    @Test
    @DisplayName("every seeded email setting is actually present in the database")
    void emailSettingsReachedTheDatabase() {
        // Presence rather than exact values — those are asserted against V20 above. This is the
        // half that proves the migration ran, and it holds regardless of what the email
        // service's own tests have written over the top.
        assertThat(List.of(
                SettingKeys.SMTP_HOST,
                SettingKeys.SMTP_PORT,
                SettingKeys.SMTP_USERNAME,
                SettingKeys.SMTP_TRANSPORT_SECURITY,
                SettingKeys.SMTP_FROM_ADDRESS,
                SettingKeys.SMTP_FROM_NAME,
                SettingKeys.SMTP_REPLY_TO,
                SettingKeys.EMAIL_MAX_ATTEMPTS,
                SettingKeys.EMAIL_RETRY_BACKOFF_SECONDS,
                SettingKeys.EMAIL_RETRY_BACKOFF_MAX_SECONDS,
                SettingKeys.EMAIL_DISPATCH_BATCH_SIZE))
                .allSatisfy(key -> assertThat(settings.find(key))
                        .as("setting '%s'", key)
                        .isPresent());
    }

    private static Path migrationPath(String filename) throws Exception {
        return Path.of(SettingsServiceIT.class.getResource("/db/migration/" + filename).toURI());
    }

    private static List<String> migrationSources() throws Exception {
        Path directory = Path.of(
                SettingsServiceIT.class.getResource("/db/migration").toURI());
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(file -> file.toString().endsWith(".sql"))
                    .map(file -> {
                        try {
                            return Files.readString(file, UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        }
    }

    @Test
    @DisplayName("a missing required setting names the key it wants")
    void missingRequiredSettingNamesTheKey() {
        assertThatExceptionOfType(SettingNotFoundException.class)
                .isThrownBy(() -> settings.require("does.not.exist"))
                .withMessageContaining("does.not.exist");
    }

    @Test
    @DisplayName("a decimal comma is rejected, not silently read as zero")
    void decimalCommaIsRejected() {
        // The realistic way this threshold gets mistyped in Greece. Java's BigDecimal will not
        // parse it, and quietly defaulting to zero would change how every rounding difference
        // is treated without telling anyone.
        settings.put("test.decimal-comma", "0,03");

        assertThatExceptionOfType(SettingValueException.class)
                .isThrownBy(() -> settings.requireDecimal("test.decimal-comma"))
                .withMessageContaining("test.decimal-comma")
                .withMessageContaining("use '.' not ','");
    }

    @Test
    @DisplayName("an unrecognised boolean throws instead of defaulting to false")
    void unrecognisedBooleanThrows() {
        // Boolean.parseBoolean would return false for "ture", quietly disabling whatever the
        // setting controls.
        settings.put("test.bad-boolean", "ture");

        assertThatExceptionOfType(SettingValueException.class)
                .isThrownBy(() -> settings.requireBoolean("test.bad-boolean"));

        settings.put("test.good-boolean", "YES");
        assertThat(settings.requireBoolean("test.good-boolean")).isTrue();
        settings.put("test.good-boolean", "no");
        assertThat(settings.requireBoolean("test.good-boolean")).isFalse();
    }

    @Test
    @DisplayName("an amount with more than two decimals is rejected as a setting problem")
    void overPreciseAmountIsRejected() {
        settings.put("test.over-precise", "0.0349");

        assertThatExceptionOfType(SettingValueException.class)
                .isThrownBy(() -> settings.requireEurAmount("test.over-precise"))
                .withMessageContaining("test.over-precise")
                .withMessageContaining("monetary amount");
    }

    @Test
    @DisplayName("writing then reading round-trips, and updates replace rather than duplicate")
    void writeAndUpdate() {
        settings.put("test.round-trip", "first");
        assertThat(settings.require("test.round-trip")).isEqualTo("first");

        settings.put("test.round-trip", "second");
        assertThat(settings.require("test.round-trip")).isEqualTo("second");

        assertThat(settings.listRedacted())
                .filteredOn(view -> view.key().equals("test.round-trip"))
                .hasSize(1);
    }

    @Test
    @DisplayName("a secret is redacted when listed, but still readable by the feature using it")
    void secretIsRedactedInListings() {
        settings.putSecret("test.secret-credential", "s3cr3t-value");

        SettingView view = settings.listRedacted().stream()
                .filter(candidate -> candidate.key().equals("test.secret-credential"))
                .findFirst()
                .orElseThrow();

        assertThat(view.secret()).isTrue();
        assertThat(view.value()).isEqualTo(SettingView.REDACTED).isNotEqualTo("s3cr3t-value");

        // The email service still needs the real value; redaction is for what leaves the API.
        assertThat(settings.require("test.secret-credential")).isEqualTo("s3cr3t-value");
    }

    @Test
    @DisplayName("secrecy is not lost by a later plain write")
    void secrecyIsStickyAcrossWrites() {
        settings.putSecret("test.sticky-secret", "original");
        settings.put("test.sticky-secret", "replaced");

        SettingView view = settings.listRedacted().stream()
                .filter(candidate -> candidate.key().equals("test.sticky-secret"))
                .findFirst()
                .orElseThrow();

        assertThat(view.secret())
                .as("a value already treated as a credential must not become publicly readable "
                        + "because someone later wrote it via put() instead of putSecret()")
                .isTrue();
        assertThat(view.value()).isEqualTo(SettingView.REDACTED);
    }

    @Test
    @DisplayName("audit columns are populated on write")
    void auditColumnsArePopulated() {
        settings.put("test.audit-columns", "value");

        SettingView view = settings.listRedacted().stream()
                .filter(candidate -> candidate.key().equals("test.audit-columns"))
                .findFirst()
                .orElseThrow();

        assertThat(view.updatedAt()).isNotNull();
        assertThat(view.updatedBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("a settings change is recorded in the audit log, with the value when not secret")
    void changeIsAudited() {
        settings.put("test.audited-change", "visible-value");

        List<AuditEntry> entries = auditLog.findForEntity("Setting", "test.audited-change", 10);
        assertThat(entries).isNotEmpty();
        assertThat(entries.getFirst().action()).isEqualTo("setting.created");
        assertThat(entries.getFirst().detail())
                .containsEntry("key", "test.audited-change")
                .containsEntry("value", "visible-value");
    }

    @Test
    @DisplayName("a secret's value never reaches the audit log")
    void secretValueIsNeverAudited() {
        settings.putSecret("test.audited-secret", "do-not-log-me");

        List<AuditEntry> entries = auditLog.findForEntity("Setting", "test.audited-secret", 10);

        assertThat(entries).isNotEmpty();
        assertThat(entries)
                .as("an audit log capturing SMTP passwords in plain text turns a control into a "
                        + "second place secrets leak from")
                .noneMatch(entry -> entry.detail().containsValue("do-not-log-me"));
        assertThat(entries.getFirst().detail()).containsEntry("secret", "true");
    }
}
