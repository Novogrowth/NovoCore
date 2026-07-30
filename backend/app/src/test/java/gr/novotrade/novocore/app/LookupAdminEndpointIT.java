package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.product.NewUnitOfMeasure;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.shared.Rate;
import gr.novotrade.novocore.core.api.tax.NewVatClass;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassView;
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
 * Administering the two runtime-editable lookups: VAT classes and units of measure.
 *
 * <h2>Why these are not under {@code Section.SETTINGS}</h2>
 *
 * <p>They are operator-editable configuration, which is what the step 16b brief meant by "Settings"
 * — but they already had read routes under {@link Section#TAX_AND_CHARGES} and
 * {@link Section#PRODUCTS} respectively, from step 14. Moving them would have meant either two
 * sections governing one resource or moving the reads, which changes what {@code /api/me} reports and
 * contradicts {@code Section.TAX_AND_CHARGES}'s own javadoc. So the writes joined the reads.
 *
 * <p>This test asserts that placement rather than leaving it to the sweep, because it is the kind of
 * decision that reads as an inconsistency later.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + LookupAdminEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + LookupAdminEndpointIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class LookupAdminEndpointIT {

    static final String OWNER_USERNAME = "lookup.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String PASSWORD = "a-password-long-enough";

    @Autowired private TestRestTemplate rest;
    @Autowired private VatClassService vatClasses;
    @Autowired private UnitOfMeasureService units;
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
    @DisplayName("VAT classes")
    class VatClasses {

        @Test
        @DisplayName("create, describe, and retire — the rate-change workflow end to end")
        void theRateChangeWorkflow() {
            // A statutory rate change is a new class plus a deactivation of the old one. This is
            // that workflow, driven the way an operator would after a change to the VAT code.
            ResponseEntity<String> created = owner.post("/api/vat-classes",
                    new NewVatClass("9001", "Test rate 11%", Rate.of("11.000000")));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            long id = Json.read(created).get("id").asLong();

            JsonNode described = Json.ok(
                    owner.patchBody("/api/vat-classes/" + id + "/description",
                            Map.of("description", "Test rate 11% — corrected label")),
                    "PATCH description");
            assertThat(Json.text(described, "description")).contains("corrected label");

            assertThat(owner.post("/api/vat-classes/" + id + "/deactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(vatClasses.require(id).active()).isFalse();

            assertThat(owner.post("/api/vat-classes/" + id + "/reactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(vatClasses.require(id).active()).isTrue();
        }

        @Test
        @DisplayName("the island-reduced mapping can be set and cleared")
        void reducedCounterpart() {
            long mainland = vatClasses.create(
                    new NewVatClass("9010", "Test mainland 20%", Rate.of("20.000000"))).id();
            long island = vatClasses.create(
                    new NewVatClass("9011", "Test island 14%", Rate.of("14.000000"))).id();

            JsonNode mapped = Json.ok(
                    owner.putBody("/api/vat-classes/" + mainland + "/reduced-counterpart",
                            Map.of("reducedCounterpartId", island)),
                    "PUT reduced-counterpart");
            assertThat(mapped.get("reducedCounterpartId").asLong()).isEqualTo(island);

            ResponseEntity<String> cleared =
                    owner.delete("/api/vat-classes/" + mainland + "/reduced-counterpart");
            assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(vatClasses.require(mainland).reducedCounterpartId()).isNull();
        }

        /**
         * The rule that makes the mapping safe to expose: one level deep and strictly lower-rated.
         *
         * <p>A counterpart that is not lower would make "reduced" a lie, and a chain would make
         * resolution multi-pass. Both are refused by the service; this asserts the refusal survives
         * the trip over HTTP with its message intact.
         */
        @Test
        @DisplayName("a counterpart that is not lower-rated is refused, saying why")
        void aHigherCounterpartIsRefused() {
            long low = vatClasses.create(
                    new NewVatClass("9020", "Test low 5%", Rate.of("5.000000"))).id();
            long high = vatClasses.create(
                    new NewVatClass("9021", "Test high 25%", Rate.of("25.000000"))).id();

            ResponseEntity<String> response = owner.putBody(
                    "/api/vat-classes/" + low + "/reduced-counterpart",
                    Map.of("reducedCounterpartId", high));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(Json.read(response).get("detail").asString())
                    .isNotEqualTo("Bad request.")
                    .isNotBlank();
        }

        /**
         * ⚠️ There is deliberately no route to change a rate — asserted, so nobody adds one for
         * symmetry.
         *
         * <p>Editing a rate in place would retroactively change what every invoice already issued
         * under that class appears to have charged. The service offers no such method, and this is
         * the test that says the <em>API</em> offers no such path either.
         */
        @Test
        @DisplayName("no route changes a rate in place")
        void thereIsNoRouteToChangeARate() {
            long id = vatClasses.requireByCode("1030").id();
            VatClassView before = vatClasses.require(id);

            for (String path : new String[] {
                "/api/vat-classes/" + id + "/rate",
                "/api/vat-classes/" + id + "/rate-percent",
                "/api/vat-classes/" + id,
            }) {
                assertThat(owner.patchBody(path, Map.of("ratePercent", "1.000000")).getStatusCode())
                        .as("%s must not be a route that changes a rate", path)
                        .isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
            }

            assertThat(vatClasses.require(id).ratePercent())
                    .as("a rate change is a new class plus a deactivation, never an edit")
                    .isEqualTo(before.ratePercent());
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("units of measure")
    class UnitsOfMeasure {

        @Test
        @DisplayName("create, rename, record a myDATA code, flip fractional quantity, retire")
        void theLifecycle() {
            ResponseEntity<String> created = owner.post("/api/units-of-measure",
                    NewUnitOfMeasure.withoutMydataCode("TESTBOX", "Test box", false));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            long id = Json.read(created).get("id").asLong();

            JsonNode renamed = Json.ok(
                    owner.patchBody("/api/units-of-measure/" + id + "/name",
                            Map.of("name", "Test carton")),
                    "PATCH name");
            assertThat(Json.text(renamed, "name")).isEqualTo("Test carton");

            JsonNode coded = Json.ok(
                    owner.patchBody("/api/units-of-measure/" + id + "/mydata-code",
                            Map.of("mydataCode", "999")),
                    "PATCH mydata-code");
            assertThat(Json.text(coded, "mydataCode")).isEqualTo("999");

            JsonNode fractional = Json.ok(
                    owner.patchBody("/api/units-of-measure/" + id + "/fractional-quantity",
                            Map.of("allowed", true)),
                    "PATCH fractional-quantity");
            assertThat(fractional.get("fractionalQuantityAllowed").asBoolean()).isTrue();

            assertThat(owner.post("/api/units-of-measure/" + id + "/deactivate", "").getStatusCode())
                    .as("nothing uses it, so it can be retired")
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(owner.post("/api/units-of-measure/" + id + "/reactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }

        /**
         * Q38's outstanding list, which had no way to be seen before this route existed.
         *
         * <p>The myDATA unit codes are pending the accountant. A question waiting on somebody else
         * that is only visible from {@code psql} is a question nobody remembers to ask.
         */
        @Test
        @DisplayName("the units still waiting on an AADE code are listable")
        void unitsWaitingOnTheAccountant() {
            units.create(NewUnitOfMeasure.withoutMydataCode("TESTPEND", "Test pending", false));

            JsonNode waiting = Json.ok(
                    owner.get("/api/units-of-measure/without-mydata-code"),
                    "GET /api/units-of-measure/without-mydata-code");

            assertThat(waiting.get("items")).isNotEmpty();
            for (JsonNode item : waiting.get("items")) {
                assertThat(item.has("mydataCode"))
                        .as("""
                                default-property-inclusion is non_null, so a unit with no code has \
                                no key at all — which is how a client tells "no mapping exists" \
                                from "not filled in yet".""")
                        .isFalse();
            }
        }
    }

    // -------------------------------------------------------------------------------------------

    /**
     * The placement decision, asserted: these live under their existing sections, not
     * {@code SETTINGS}.
     */
    @Test
    @DisplayName("the lookups are governed by their own sections, not by SETTINGS")
    void sectionPlacementIsAsDecided() {
        RoleView settingsOnly = roles.findByName("LOOKUP_SETTINGS_ONLY").orElseGet(() ->
                roles.create(new NewRole("LOOKUP_SETTINGS_ONLY", "Created by LookupAdminEndpointIT")));
        roles.grant(settingsOnly.id(), Section.SETTINGS, AccessLevel.FULL);
        if (users.findByUsername("lookup.settingsonly").isEmpty()) {
            users.create(new NewUser(
                    "lookup.settingsonly", "Settings only", PASSWORD, settingsOnly.id()));
        }
        ApiClient.Session session = api.logIn("lookup.settingsonly", PASSWORD);

        assertThat(session.get("/api/settings").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(session.get("/api/vat-classes").getStatusCode())
                .as("SETTINGS does not grant the tax lookups")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(session.get("/api/units-of-measure").getStatusCode())
                .as("nor the product lookups")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
