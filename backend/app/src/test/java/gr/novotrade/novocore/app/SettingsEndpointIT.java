package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingView;
import gr.novotrade.novocore.core.api.settings.SettingsCatalog;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The Settings API, over real HTTP — an allowlist, not a view of the table.
 *
 * <p>{@code SettingsCatalogIT} asserts the catalogue's shape against the database. This asserts what
 * a caller can actually reach, which is a different claim: the exclusions have to hold at the URL,
 * and the validation has to happen <em>before</em> the write.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + SettingsEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + SettingsEndpointIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class SettingsEndpointIT {

    static final String OWNER_USERNAME = "settings.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String PASSWORD = "a-password-long-enough";

    @Autowired private TestRestTemplate rest;
    @Autowired private SettingsService settings;
    @Autowired private UserService users;
    @Autowired private RoleService roles;

    private ApiClient api;
    private ApiClient.Session owner;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
        owner = api.logIn(OWNER_USERNAME, OWNER_PASSWORD);
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("the listing is the allowlist, and nothing from the backup namespace is in it")
        void theListingIsTheAllowlist() {
            JsonNode listing = Json.ok(owner.get("/api/settings"), "GET /api/settings");

            assertThat(listing.get("items")).hasSize(SettingsCatalog.values().length);

            for (JsonNode item : listing.get("items")) {
                assertThat(Json.text(item, "key"))
                        .as("""
                                A backup setting reached the API. That namespace holds the two Drive \
                                OAuth credentials per destination, and the folder and client ids — \
                                which are not flagged secret and would therefore arrive in the \
                                clear.""")
                        .doesNotStartWith("backup.");
            }
        }

        /**
         * The specific values that would leak if the exclusion were per-key rather than by namespace.
         *
         * <p>{@code folder-id} and {@code client-id} are <strong>not</strong> flagged secret in the
         * table, so a listing built on {@code SettingsService.listRedacted()} would return them
         * verbatim. Asserted against the raw body rather than the parsed items, because the claim is
         * about what crosses the wire.
         */
        @Test
        @DisplayName("no Drive value appears anywhere in the response body")
        void noDriveValueCrossesTheWire() {
            settings.put("backup.drive.primary.folder-id", "a-real-looking-folder-id");
            settings.put("backup.drive.primary.client-id", "a-real-looking-client-id");

            String body = owner.get("/api/settings").getBody();

            assertThat(body)
                    .doesNotContain("a-real-looking-folder-id")
                    .doesNotContain("a-real-looking-client-id")
                    .doesNotContain("backup.");
        }

        @Test
        @DisplayName("a write-only credential is never returned, set or unset")
        void theSmtpPasswordIsNeverReturned() {
            settings.putSecret(SettingKeys.SMTP_PASSWORD, "the-real-smtp-password");

            ResponseEntity<String> response =
                    owner.get("/api/settings/" + SettingsCatalog.SMTP_PASSWORD);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).doesNotContain("the-real-smtp-password");
            assertThat(Json.text(Json.read(response), "value")).isEqualTo(SettingView.REDACTED);
        }

        @Test
        @DisplayName("one setting by key, with its stored value")
        void oneSettingByKey() {
            JsonNode threshold = Json.ok(
                    owner.get("/api/settings/" + SettingsCatalog.LEDGER_ROUNDING_THRESHOLD),
                    "GET /api/settings/{key}");

            assertThat(Json.text(threshold, "key"))
                    .isEqualTo(SettingKeys.LEDGER_ROUNDING_THRESHOLD);
            assertThat(threshold.get("secret").asBoolean()).isFalse();
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("a valid value is stored and read back by the core")
        void aValidValueIsStored() {
            assertThat(owner.putBody(
                            "/api/settings/" + SettingsCatalog.EMAIL_MAX_ATTEMPTS,
                            Map.of("value", "7"))
                    .getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(settings.requireInt(SettingKeys.EMAIL_MAX_ATTEMPTS)).isEqualTo(7);
        }

        /**
         * ⚠️ The guarantee this whole design exists for.
         *
         * <p>{@code SettingsService.put} accepts {@code "0,03"} with a comma quite happily. The
         * failure then arrives on the next invoice somebody records — as an error naming a key they
         * were not thinking about, raised by a document with nothing wrong with it. So the refusal
         * has to happen before the store, and <strong>the old value has to survive it</strong>.
         */
        @Test
        @DisplayName("an invalid value is refused and the previous value survives")
        void anInvalidValueLeavesTheOldOneInPlace() {
            String before = settings.require(SettingKeys.LEDGER_ROUNDING_THRESHOLD);

            ResponseEntity<String> response = owner.putBody(
                    "/api/settings/" + SettingsCatalog.LEDGER_ROUNDING_THRESHOLD,
                    Map.of("value", "0,03"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(Json.read(response).get("detail").asString())
                    .as("the refusal names the key and what was expected")
                    .contains(SettingKeys.LEDGER_ROUNDING_THRESHOLD);

            assertThat(settings.require(SettingKeys.LEDGER_ROUNDING_THRESHOLD))
                    .as("""
                            A rejected write must change nothing. Storing it and failing later \
                            would make the ledger's rounding rule unreadable, and the symptom \
                            would appear on the next document posted rather than here.""")
                    .isEqualTo(before);
        }

        @Test
        @DisplayName("each type refuses what it cannot hold")
        void typesAreEnforced() {
            assertRefused(SettingsCatalog.LEDGER_ROUNDING_MODE, "SIDEWAYS");
            assertRefused(SettingsCatalog.SMTP_TRANSPORT_SECURITY, "MAYBE");
            assertRefused(SettingsCatalog.SMTP_PORT, "not-a-number");
            assertRefused(SettingsCatalog.EMAIL_MAX_ATTEMPTS, "0");
            assertRefused(SettingsCatalog.EMAIL_RETENTION_MESSAGE_DAYS, "for-a-while");
            assertRefused(SettingsCatalog.LEDGER_ROUNDING_THRESHOLD, "0.001");
        }

        @Test
        @DisplayName("FOREVER is a valid retention, because a setting with one legal value is not one")
        void foreverIsARetentionValue() {
            assertThat(owner.putBody(
                            "/api/settings/" + SettingsCatalog.EMAIL_RETENTION_MESSAGE_DAYS,
                            Map.of("value", SettingKeys.RETENTION_FOREVER))
                    .getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(owner.putBody(
                            "/api/settings/" + SettingsCatalog.EMAIL_RETENTION_MESSAGE_DAYS,
                            Map.of("value", "365"))
                    .getStatusCode())
                    .as("and a number is equally valid, or FOREVER would be the only option and "
                            + "the setting would be decorative")
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }

        private void assertRefused(SettingsCatalog setting, String value) {
            assertThat(owner.putBody("/api/settings/" + setting, Map.of("value", value))
                    .getStatusCode())
                    .as("%s should refuse '%s'", setting, value)
                    .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("refusing")
    class Refusing {

        /**
         * The exclusion, asserted at the URL.
         *
         * <p>{@code SettingsCatalogIT} says no catalogue entry names a backup key. This says the
         * route refuses one — which is the claim that matters, and it holds because {@code {key}} is
         * bound to the enum and Spring refuses an unknown value before any of our code runs.
         */
        @Test
        @DisplayName("a backup key has no route at all, for reading or writing")
        void backupKeysHaveNoRoute() {
            assertThat(owner.get("/api/settings/backup.drive.primary.refresh-token").getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            assertThat(owner.putBody("/api/settings/backup.drive.primary.refresh-token",
                            Map.of("value", "stolen"))
                    .getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            assertThat(settings.find("backup.drive.primary.refresh-token").orElse(""))
                    .as("and nothing was written")
                    .isNotEqualTo("stolen");
        }

        @Test
        @DisplayName("an invented key has no route either")
        void unknownKeysHaveNoRoute() {
            assertThat(owner.putBody("/api/settings/something.made.up", Map.of("value", "x"))
                    .getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(settings.find("something.made.up")).isEmpty();
        }

        /**
         * The statutory limit: readable, never writable. See {@code SettingsCatalog.CASH_PAYMENT_LIMIT}.
         */
        @Test
        @DisplayName("the cash payment limit reads but does not write, even for the owner")
        void theCashLimitIsReadOnly() {
            assertThat(owner.get("/api/settings/" + SettingsCatalog.CASH_PAYMENT_LIMIT)
                    .getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            ResponseEntity<String> refused = owner.putBody(
                    "/api/settings/" + SettingsCatalog.CASH_PAYMENT_LIMIT,
                    Map.of("value", "5000.00"));

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(Json.read(refused).get("detail").asString())
                    .as("the refusal says why, because 'you cannot' without 'because it is the law' "
                            + "reads as an oversight")
                    .contains("statutory");
            assertThat(settings.requireEurAmount(SettingKeys.CASH_PAYMENT_LIMIT).toString())
                    .contains("500.00");
        }

        @Test
        @DisplayName("a missing value field is 400 naming it, never a 500")
        void aMissingValueIsNamed() {
            ResponseEntity<String> response =
                    owner.put("/api/settings/" + SettingsCatalog.SMTP_HOST, "{}");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Json.read(response).get("detail").asString()).contains("value");
        }

        @Test
        @DisplayName("VIEW reads, FULL writes, and neither is granted by default")
        void permissionsAreEnforced() {
            RoleView reader = roles.findByName("SETTINGS_READER").orElseGet(() ->
                    roles.create(new NewRole("SETTINGS_READER", "Created by SettingsEndpointIT")));
            roles.grant(reader.id(), Section.SETTINGS, AccessLevel.VIEW);
            if (users.findByUsername("settings.reader").isEmpty()) {
                users.create(new NewUser("settings.reader", "Reader", PASSWORD, reader.id()));
            }
            ApiClient.Session session = api.logIn("settings.reader", PASSWORD);

            assertThat(session.get("/api/settings").getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<String> refused = session.putBody(
                    "/api/settings/" + SettingsCatalog.SMTP_HOST, Map.of("value", "evil.example"));
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(Json.read(refused).get("detail").asString())
                    .doesNotContain("SETTINGS")
                    .doesNotContain("FULL");

            // And a role with no grant at all sees nothing.
            RoleView outsider = roles.findByName("SETTINGS_OUTSIDER").orElseGet(() ->
                    roles.create(new NewRole("SETTINGS_OUTSIDER", "Created by SettingsEndpointIT")));
            if (users.findByUsername("settings.outsider").isEmpty()) {
                users.create(new NewUser("settings.outsider", "Outsider", PASSWORD, outsider.id()));
            }
            assertThat(api.logIn("settings.outsider", PASSWORD).get("/api/settings").getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
