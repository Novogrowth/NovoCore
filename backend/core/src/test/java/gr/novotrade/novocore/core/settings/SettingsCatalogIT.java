package gr.novotrade.novocore.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.settings.SettingsCatalog;
import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The guards that keep the Settings API's exclusions structural rather than remembered.
 *
 * <p>Each catches something the others cannot. Together they are the reason
 * {@code CLAUDE.md}'s "add a test/ArchUnit-style guard if practical to make that exclusion
 * structural" is answered rather than promised — plus the ArchUnit rule in
 * {@code CoreBoundaryRulesTest}, which stops a controller reaching past the catalogue entirely.
 */
class SettingsCatalogIT extends AbstractCoreIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    /**
     * ⚠️ The one that matters most, and the only one that is one line.
     *
     * <p>Per-key exclusion would have to individually catch {@code backup.drive.primary.folder-id}
     * and {@code .client-id} — which are <strong>not</strong> flagged secret in the table and would
     * therefore be returned in the clear by any "redacted" listing — and would be missed again by
     * whoever adds a third destination. A namespace rule cannot rot that way.
     */
    @ParameterizedTest
    @EnumSource(SettingsCatalog.class)
    @DisplayName("no catalogued setting is in the backup namespace")
    void nothingFromTheBackupNamespaceIsExposed(SettingsCatalog entry) {
        assertThat(entry.key())
                .as("""
                        %s exposes a backup setting. That namespace holds the two Drive OAuth \
                        credentials per destination — and the folder and client ids, which are not \
                        flagged secret and would come back in the clear.""", entry)
                .doesNotStartWith(SettingsCatalog.EXCLUDED_NAMESPACE);
    }

    /**
     * A credential can be settable, never readable.
     *
     * <p>So a future secret added to the table cannot be exposed by carelessly adding a constant
     * here: it would have to be declared write-only, which is a deliberate act with a visible name.
     */
    @Test
    @DisplayName("every catalogued setting stored as secret is write-only")
    void secretsAreWriteOnly() {
        List<String> secretKeys = jdbc.queryForList(
                "select setting_key from setting where secret", String.class);

        for (SettingsCatalog entry : SettingsCatalog.values()) {
            if (secretKeys.contains(entry.key())) {
                assertThat(entry.isWriteOnly())
                        .as("%s is stored as a secret, so it must never be readable through the API",
                                entry)
                        .isTrue();
            }
        }
    }

    /**
     * A catalogued key naming no real setting is a 404 nobody can explain.
     *
     * <p>{@code smtp.password} is the deliberate exception: <strong>no migration seeds it</strong>,
     * because a migration is a file in git and a credential in git is in git permanently. It arrives
     * once by environment variable.
     */
    @ParameterizedTest
    @EnumSource(SettingsCatalog.class)
    @DisplayName("every catalogued key exists in the setting table, except the one never seeded")
    void everyCatalogueKeyIsReal(SettingsCatalog entry) {
        if (entry == SettingsCatalog.SMTP_PASSWORD) {
            return;
        }
        Integer found = jdbc.queryForObject(
                "select count(*) from setting where setting_key = ?", Integer.class, entry.key());

        assertThat(found)
                .as("%s names '%s', which no migration seeds. Either the key is misspelled or the "
                        + "setting was never created.", entry, entry.key())
                .isEqualTo(1);
    }

    /**
     * ADR 0013's invariant, asserted rather than trusted.
     *
     * <p>The backup encryption key is an environment variable <strong>because the setting table is
     * inside the dump it decrypts</strong> — a key kept there would be encrypted inside the artefact
     * it is needed to open. It therefore has no row, and cannot be reached by any route however the
     * catalogue changes. This is the assertion that notices if somebody ever "helpfully" seeds it.
     */
    @Test
    @DisplayName("the backup encryption key has no row in the settings table at all")
    void theEncryptionKeyIsNotASetting() {
        List<String> keyish = jdbc.queryForList(
                "select setting_key from setting where setting_key like '%encryption%' "
                        + "or setting_key like '%.key' or setting_key like '%secret-key%'",
                String.class);

        assertThat(keyish)
                .as("""
                        The backup encryption key must never be a setting: the setting table is \
                        inside the dump, so a key stored there would be encrypted inside the \
                        artefact it exists to decrypt. It lives in NOVOCORE_BACKUP_ENCRYPTION_KEY \
                        and in a password manager, and nowhere else.""")
                .isEmpty();
    }

    /**
     * Two constants naming one key would make {@code forKey} return whichever came first, and the
     * two could disagree about type or writability.
     */
    @Test
    @DisplayName("no two catalogue entries name the same key")
    void keysAreUnique() {
        List<String> keys = Arrays.stream(SettingsCatalog.values())
                .map(SettingsCatalog::key)
                .toList();

        assertThat(keys).doesNotHaveDuplicates();
    }

    /**
     * Every value currently in the database is one its declared type accepts.
     *
     * <p>Catches a catalogue entry typed wrongly against what the migrations actually seeded — a
     * {@code POSITIVE_INTEGER} over a key seeded {@code FOREVER}, say. Without it the mistake
     * surfaces the first time somebody edits that setting and is told their correct value is
     * invalid.
     */
    @ParameterizedTest
    @EnumSource(SettingsCatalog.class)
    @DisplayName("the seeded value of every catalogued setting satisfies its declared type")
    void seededValuesMatchTheirDeclaredType(SettingsCatalog entry) {
        List<String> values = jdbc.queryForList(
                "select value from setting where setting_key = ?", String.class, entry.key());
        if (values.isEmpty() || values.getFirst().isBlank()) {
            return; // Never seeded (smtp.password), or seeded blank on purpose.
        }

        entry.validate(values.getFirst());
    }
}
