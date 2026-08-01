package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.settings.SettingsCatalog;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
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
 * The bodies the Settings screens actually send, judged by the real server.
 *
 * <h2>Why this exists, and why a screen test is not a substitute</h2>
 *
 * <p>{@code CLAUDE.md}'s standing practice: <strong>when the question is "will the backend accept
 * this", the backend has to answer it.</strong> A screen test runs against {@code msw}, which
 * answers whatever it was told to — so it can prove a form is wired to a route and cannot prove the
 * body satisfies the contract. That distinction has already shipped a create form that failed for
 * every user, cleared by a headless check that fabricated its own {@code 201}.
 *
 * <p>Every JSON literal below is <strong>the exact string the F4 screens build</strong>, written out
 * rather than constructed from the request record — building it from {@code NewUnitOfMeasure} would
 * ask Jackson to agree with itself and prove nothing about what the browser sends. Read them beside
 * {@code frontend/src/pages/settings/}, {@code .../vat-classes/} and {@code .../units-of-measure/}.
 *
 * <p>⚠️ <strong>This is not the whole verification.</strong> A browser driving these forms against
 * the running Compose stack needs the Owner password, which is deliberately not in this repository —
 * the same leg S1 and S2 needed and the owner ran personally. This closes the *contract* question,
 * which is the one that has actually bitten; it does not close the *browser* question.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + F4WriteContractIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + F4WriteContractIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class F4WriteContractIT {

    static final String OWNER_USERNAME = "f4.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    @Autowired private TestRestTemplate rest;

    private ApiClient.Session owner;

    @BeforeEach
    void setUp() {
        owner = new ApiClient(rest).logIn(OWNER_USERNAME, OWNER_PASSWORD);
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("the settings screens")
    class Settings {

        /**
         * ⚠️ The path segment is the <strong>enum constant</strong>, not the dotted key.
         *
         * <p>{@code @PathVariable SettingsCatalog} with no converter registered, so Spring's default
         * enum binding applies. The response body's own {@code key} field is the *dotted* spelling,
         * which is exactly how somebody comes to build the URL from the wrong one — and the wrong
         * one is refused before any of our code runs.
         */
        @Test
        @DisplayName("a value is written against the enum constant, and the dotted key is not a URL")
        void theKeyInThePathIsTheEnumConstant() {
            ResponseEntity<String> accepted = owner.put(
                    "/api/settings/" + SettingsCatalog.LEDGER_ROUNDING_THRESHOLD,
                    """
                    {"value":"0.05"}""");
            assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            ResponseEntity<String> dotted = owner.put(
                    "/api/settings/ledger.rounding.threshold", """
                    {"value":"0.05"}""");
            assertThat(dotted.getStatusCode())
                    .as("the dotted key is not a path segment — this is what the screen must not send")
                    .isNotEqualTo(HttpStatus.NO_CONTENT);

            // Put it back, so this test leaves the seeded configuration as it found it.
            owner.put("/api/settings/" + SettingsCatalog.LEDGER_ROUNDING_THRESHOLD, """
                    {"value":"0.03"}""");
        }

        /**
         * ⚠️ The finding this whole file paid for.
         *
         * <p>{@code SettingType}'s javadoc listed the accepted values as {@code NONE},
         * {@code STARTTLS} or <strong>{@code TLS}</strong> — and no such constant exists. A select
         * built from that sentence offers an option every save refuses. The value is an opaque string
         * in the OpenAPI document, so no generated enum exists and nothing on the frontend could have
         * caught it. Only the server can say which spelling is real, so it does.
         */
        @Test
        @DisplayName("IMPLICIT_TLS is accepted and TLS is refused — the javadoc named the wrong one")
        void transportSecurityAcceptsOnlyRealConstants() {
            assertThat(owner
                            .put("/api/settings/" + SettingsCatalog.SMTP_TRANSPORT_SECURITY, """
                            {"value":"IMPLICIT_TLS"}""")
                            .getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            ResponseEntity<String> refused = owner.put(
                    "/api/settings/" + SettingsCatalog.SMTP_TRANSPORT_SECURITY, """
                    {"value":"TLS"}""");

            assertThat(refused.getStatusCode().is2xxSuccessful())
                    .as("'TLS' is what the javadoc said; the enum has IMPLICIT_TLS")
                    .isFalse();
            // And the refusal says what it wanted, which is what makes it fixable from a screen.
            assertThat(refused.getBody()).contains("IMPLICIT_TLS");
        }

        /**
         * The statutory limit, from the other end: the screen renders no control, and the reason it
         * renders none is that no route exists. That second half is what this asserts.
         */
        @Test
        @DisplayName("the cash payment limit is readable and refuses every write")
        void cashPaymentLimitIsReadOnly() {
            assertThat(owner.get("/api/settings/" + SettingsCatalog.CASH_PAYMENT_LIMIT)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            assertThat(owner
                            .put("/api/settings/" + SettingsCatalog.CASH_PAYMENT_LIMIT, """
                            {"value":"1000.00"}""")
                            .getStatusCode()
                            .is2xxSuccessful())
                    .as("statutory — N. 5301/2026. Raising it must not be a two-click operation")
                    .isFalse();
        }

        @Test
        @DisplayName("FOREVER and a number of days are both accepted for a retention setting")
        void retentionAcceptsBothForms() {
            // Q43: FOREVER is a word rather than a blank or a zero, so "keep everything" is
            // something an operator can actually write down. The screen offers it as a placeholder.
            assertThat(owner
                            .put("/api/settings/" + SettingsCatalog.EMAIL_RETENTION_MESSAGE_DAYS, """
                            {"value":"FOREVER"}""")
                            .getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(owner
                            .put("/api/settings/" + SettingsCatalog.EMAIL_RETENTION_MESSAGE_DAYS, """
                            {"value":"365"}""")
                            .getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);

            owner.put("/api/settings/" + SettingsCatalog.EMAIL_RETENTION_MESSAGE_DAYS, """
                    {"value":"FOREVER"}""");
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("the VAT classes screen")
    class VatClasses {

        /**
         * ⚠️ Exercised against a code the seed already holds, so the whole path runs and nothing is
         * written.
         *
         * <p>This is {@code CLAUDE.md}'s refusal trick: a duplicate code answers a domain refusal
         * <strong>only if the body parsed</strong>, which distinguishes a parser rejection from a
         * domain one. `1410` is seeded by V5, so this proves the create body is well-formed without
         * adding a row to a table whose rows are permanent — a VAT class cannot be deleted.
         */
        @Test
        @DisplayName("the create body parses — proved by a domain refusal, not by a parser one")
        void theCreateBodyParses() {
            ResponseEntity<String> response = owner.post("/api/vat-classes", """
                    {"code":"1410","description":"ΦΠΑ 24%","ratePercent":"24.000000"}""");

            assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
            assertThat(response.getBody())
                    .as("a refusal naming the DUPLICATE means the body was understood; one naming a "
                            + "field or nothing at all would mean it was not")
                    .containsIgnoringCase("already exists");
        }

        /**
         * ⚠️ The rate is a percentage and crosses the wire as a STRING.
         *
         * <p>{@code 0.24} for 24% sits comfortably inside the original 0–100 CHECK and was accepted
         * as a quarter of one percent, which is why V10 narrowed it to "zero, or 1 to 100". The
         * create form's hint says so; this is the server agreeing.
         */
        @Test
        @DisplayName("a fraction where a percentage was meant is refused, and says so")
        void aFractionIsRefused() {
            ResponseEntity<String> response = owner.post("/api/vat-classes", """
                    {"code":"F4-PROBE","description":"F4 probe","ratePercent":"0.240000"}""");

            assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
            assertThat(response.getBody()).isNotBlank();
        }

        @Test
        @DisplayName("a JSON number for the rate is refused outright — Money and Rate are strings")
        void aJsonNumberIsRefused() {
            ResponseEntity<String> response = owner.post("/api/vat-classes", """
                    {"code":"F4-PROBE","description":"F4 probe","ratePercent":24}""");

            assertThat(response.getStatusCode().is2xxSuccessful())
                    .as("the deserializer reports an input mismatch rather than coercing")
                    .isFalse();
        }

        /**
         * The one editable field, end to end and put back afterwards.
         *
         * <p>The rate and the code have no route at all, which {@code TaxLookupSurfaceIT} and the
         * controller's own javadoc already assert; the screen renders them as plain text on the
         * strength of that.
         */
        @Test
        @DisplayName("a description is changed through PATCH …/description, and only a description")
        void describeIsTheOnlyFieldMutation() {
            JsonNode listing = Json.ok(owner.get("/api/vat-classes"), "GET /api/vat-classes");
            JsonNode standard = byCode(listing, "1410");
            long id = standard.get("id").asLong();
            String original = Json.text(standard, "description");

            ResponseEntity<String> response = owner.patch(
                    "/api/vat-classes/" + id + "/description", """
                    {"description":"F4 probe description"}""");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(Json.text(Json.read(response), "description")).isEqualTo("F4 probe description");
            // ⚠️ And the rate is untouched by a description change, which is the whole premise.
            assertThat(Json.text(Json.read(response), "ratePercent")).isEqualTo("24.000000");

            owner.patch("/api/vat-classes/" + id + "/description",
                    "{\"description\":\"" + original + "\"}");
        }

        @Test
        @DisplayName("deactivate and reactivate both answer 204, which is what the screen expects")
        void deactivateAndReactivate() {
            JsonNode listing = Json.ok(owner.get("/api/vat-classes"), "GET /api/vat-classes");
            // The zero-rate class: nothing in the seed maps to it as a counterpart, so toggling it
            // cannot disturb the island chain this business actually uses.
            long id = byCode(listing, "0").get("id").asLong();

            assertThat(owner.post("/api/vat-classes/" + id + "/deactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(owner.post("/api/vat-classes/" + id + "/reactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }

        /** Row 6 of the search target list, over HTTP rather than through the service. */
        @Test
        @DisplayName("?search= matches the code and the description, and folds Greek case")
        void searchMatchesCodeAndDescription() {
            assertThat(codes(Json.ok(
                            owner.get("/api/vat-classes?search=1410"), "search by code")))
                    .containsExactly("1410");

            assertThat(codes(Json.ok(
                            owner.get("/api/vat-classes?search=φπα 24"),
                            "search by lowercase Greek description")))
                    .as("φπα 24 — lowercase, unaccented, the case S1's collation defect broke")
                    .contains("1410");
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("the units of measure screen")
    class UnitsOfMeasure {

        /**
         * ⚠️ The body the create form sends, including the field the backend cannot police.
         *
         * <p>{@code fractionalQuantityAllowed} is a <strong>primitive boolean</strong> on
         * {@code NewUnitOfMeasure}, so it is mandatory in fact and the spec declares it — one of the
         * 78 schemas the step-15 sweep fixed. The form always sends it; the two tests below say what
         * happens when it is omitted and what happens when it is sent as an explicit {@code false},
         * which are different answers.
         *
         * <p>Created for real and then deactivated, because there is no delete: a product could be
         * pointing at it. The residue is stated rather than implied — one row, inactive, plus an
         * advanced sequence and an append-only audit entry.
         */
        @Test
        @DisplayName("the create body is accepted, and the unit comes back as it was described")
        void theCreateBodyIsAccepted() {
            ResponseEntity<String> response = owner.post("/api/units-of-measure", """
                    {"code":"F4-PROBE-PALLET","name":"F4 probe pallet","fractionalQuantityAllowed":false}""");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            JsonNode created = Json.read(response);
            assertThat(created.get("fractionalQuantityAllowed").asBoolean()).isFalse();
            assertThat(created.has("mydataCode"))
                    .as("absent, not empty — NULL means 'no mapping exists', and `non_null` "
                            + "inclusion drops the key entirely rather than sending \"\"")
                    .isFalse();

            long id = created.get("id").asLong();

            // The rest of the screen's mutations, against the row it just made.
            assertThat(owner.patch("/api/units-of-measure/" + id + "/name", """
                    {"name":"F4 probe pallet renamed"}""").getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            assertThat(owner.patch("/api/units-of-measure/" + id + "/fractional-quantity", """
                    {"allowed":true}""").getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            assertThat(owner.patch("/api/units-of-measure/" + id + "/mydata-code", """
                    {"mydataCode":"F4-PROBE-CODE"}""").getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            // ⚠️ Settable exactly once. This is the rule the screen mirrors as a `lockedReason`,
            // and it is the server that decides it.
            assertThat(owner.patch("/api/units-of-measure/" + id + "/mydata-code", """
                    {"mydataCode":"F4-PROBE-CODE-2"}""").getStatusCode().is2xxSuccessful())
                    .as("a myDATA code is recorded once and has no correction path")
                    .isFalse();

            assertThat(owner.post("/api/units-of-measure/" + id + "/deactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }

        /**
         * ⚠️ <strong>This test was written asserting the opposite, and the server corrected it.</strong>
         *
         * <p>The claim it started from — that omitting the primitive is accepted and silently means
         * {@code false}, because the compact constructor only null-checks {@code code} and
         * {@code name} — is <strong>wrong</strong>, and it is wrong in the direction that matters: it
         * described a hazard that does not exist. The constructor is not where an absent primitive is
         * caught. Jackson hands an absent creator property to the canonical constructor as
         * {@code null}, and {@code FAIL_ON_NULL_FOR_PRIMITIVES} — on in this application — refuses
         * the body <em>before any handler runs</em>. This is the same mechanism that broke product
         * creation via {@code NewProduct.serialTracked}, and the whole reason the spec generator now
         * marks primitives required.
         *
         * <p><strong>The refusal names no field</strong>, which is the part that still costs
         * something: a client that omits one gets a bare {@code 400} and no way to tell which. So the
         * create form must still never omit it — but because an omission is an unexplainable refusal,
         * not because it is a silent {@code false}.
         */
        @Test
        @DisplayName("omitting fractionalQuantityAllowed is REFUSED — the primitive rule catches it")
        void omittingThePrimitiveIsRefused() {
            ResponseEntity<String> response = owner.post("/api/units-of-measure", """
                    {"code":"F4-PROBE-OMIT","name":"F4 probe omit"}""");

            assertThat(response.getStatusCode())
                    .as("FAIL_ON_NULL_FOR_PRIMITIVES refuses an absent primitive, not only an "
                            + "explicit null — so no row is created")
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            // And nothing was written, which is the half a status code alone does not prove.
            assertThat(codes(Json.ok(owner.get("/api/units-of-measure"), "GET /api/units-of-measure")))
                    .doesNotContain("F4-PROBE-OMIT");
        }

        /**
         * The same field sent explicitly as {@code false} <em>is</em> accepted — which is what makes
         * the test above a statement about omission rather than about the value.
         *
         * <p>⚠️ And it is why the create form makes the choice <strong>required</strong> rather than
         * offering a checkbox: an unticked checkbox sends {@code false} explicitly, the server accepts
         * it, and a unit that cannot be sold by the half looks exactly like one somebody decided
         * should not be.
         */
        @Test
        @DisplayName("an explicit false is accepted — so an unticked checkbox would be a real decision")
        void anExplicitFalseIsAccepted() {
            ResponseEntity<String> response = owner.post("/api/units-of-measure", """
                    {"code":"F4-PROBE-EXPLICIT","name":"F4 probe explicit","fractionalQuantityAllowed":false}""");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(Json.read(response).get("fractionalQuantityAllowed").asBoolean()).isFalse();

            owner.post("/api/units-of-measure/"
                    + Json.read(response).get("id").asLong() + "/deactivate", "");
        }

        /** Row 7 of the search target list, over HTTP. */
        @Test
        @DisplayName("?search= matches the code and the name, in the middle of either")
        void searchMatchesCodeAndName() {
            assertThat(codes(Json.ok(
                            owner.get("/api/units-of-measure?search=gram"), "search by substring")))
                    .containsExactlyInAnyOrder("GRAM", "KILOGRAM");

            assertThat(codes(Json.ok(
                            owner.get("/api/units-of-measure?search=Millilitre"), "search by name")))
                    .containsExactly("MILLILITRE");
        }

        @Test
        @DisplayName("the unmapped list is a real outstanding item — every seeded unit is in it")
        void everySeededUnitIsUnmapped() {
            // Q38. The screen surfaces this as a standing to-do banner rather than a debug panel,
            // and the count it shows is this one.
            assertThat(codes(Json.ok(
                            owner.get("/api/units-of-measure/without-mydata-code"),
                            "GET /api/units-of-measure/without-mydata-code")))
                    .contains("PIECE", "KILOGRAM", "GRAM", "LITRE", "MILLILITRE", "METRE", "SET",
                            "PACK");
        }
    }

    // -------------------------------------------------------------------------------------------

    private static JsonNode byCode(JsonNode listing, String code) {
        for (JsonNode item : listing.get("items")) {
            if (code.equals(Json.text(item, "code"))) {
                return item;
            }
        }
        throw new AssertionError("No row with code '" + code + "' — the seed changed.");
    }

    private static java.util.List<String> codes(JsonNode listing) {
        java.util.List<String> codes = new java.util.ArrayList<>();
        for (JsonNode item : listing.get("items")) {
            codes.add(Json.text(item, "code"));
        }
        return codes;
    }
}
