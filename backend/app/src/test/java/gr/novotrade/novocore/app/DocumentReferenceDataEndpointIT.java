package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * R1a's fifty-four routes, driven over real HTTP against the real server.
 *
 * <h2>Why this exists rather than a service-layer test</h2>
 *
 * <p>{@code DocumentReferenceDataIT} already exercises the same domain rules through the service
 * interfaces, and it is not a substitute. {@code CLAUDE.md}'s standing practice is that
 * <strong>when the question is "will the backend accept this", the backend has to answer it</strong>
 * — and the layer that has actually bitten this codebase is the one between the wire and the
 * service: nine of step 15's defects were unreachable from below, a form failed for every user
 * because {@code FAIL_ON_NULL_FOR_PRIMITIVES} refuses an absent primitive before any handler runs,
 * and none of that is visible to a test that calls a Java method.
 *
 * <p>Every JSON literal below is written out rather than serialised from the request record.
 * Building a body from {@code NewSalesDocumentType} would ask Jackson to agree with itself and prove
 * nothing about what a browser sends.
 *
 * <p>⚠️ <strong>This is the whole of R1a's browser-facing verification, and it does not close the
 * browser question.</strong> R1a ships no screens — R2 does — so there is nothing to open a browser
 * against. What is closed here is the contract question.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + DocumentReferenceDataEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + DocumentReferenceDataEndpointIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class DocumentReferenceDataEndpointIT {

    static final String OWNER_USERNAME = "r1a.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    @Autowired private TestRestTemplate rest;

    private ApiClient.Session owner;

    @BeforeEach
    void setUp() {
        owner = new ApiClient(rest).logIn(OWNER_USERNAME, OWNER_PASSWORD);
    }

    // ===========================================================================================
    // Layer 1 — the AADE codification. Read-only apart from the three the contract permits.
    // ===========================================================================================

    @Test
    @DisplayName("the codification serves 55 codes, and the side filters split 34 / 15")
    void theCodificationIsServedWholeAndInHalves() {
        assertThat(items("/api/aade-invoice-types")).hasSize(55);
        assertThat(items("/api/aade-invoice-types?side=ISSUED")).hasSize(34);
        assertThat(items("/api/aade-invoice-types?side=RECEIVED")).hasSize(15);
        assertThat(items("/api/aade-invoice-types?group=ENTITY_ADJUSTING")).hasSize(6);

        // ⚠️ 34 + 15 = 49. The six entity-adjusting codes are in NEITHER side, because they are the
        // entity's own journal entries with no counterparty. Asserted over HTTP because a screen
        // building a document-type form from `side=ISSUED` needs that to be true of the wire, not
        // just of the service.
        JsonNode payroll = byCode(items("/api/aade-invoice-types?group=ENTITY_ADJUSTING"), "17.1");
        assertThat(Json.text(payroll, "description")).isEqualTo("Μισθοδοσία");
        assertThat(Json.text(payroll, "group")).isEqualTo("ENTITY_ADJUSTING");

        // Greek survives the whole path — seed, JDBC, JPA, Jackson, HTTP. Mojibake anywhere in that
        // chain would be silent and would eventually reach AADE.
        assertThat(Json.text(byCode(items("/api/aade-invoice-types"), "1.1"), "description"))
                .isEqualTo("Τιμολόγιο Πώλησης");
        // ⚠️ The two codes annex 8.1 gives no description at all. Read from the artefact's group
        // label rather than invented, and asserted so nobody later "fixes" them.
        assertThat(Json.text(byCode(items("/api/aade-invoice-types"), "4"), "description"))
                .isEqualTo("Για Μελλοντική Χρήση");
    }

    @Test
    @DisplayName("⚠️ the wire carries exactly the properties the spec documents — no derived extras")
    void theWireMatchesTheDocumentedSchema() {
        /*
         * ⚠️ This assertion exists because R1a shipped the defect it catches.
         *
         * `AadeInvoiceTypeView` briefly carried a derived `issuedByUs()` accessor. `OpenApiSchema`
         * describes RECORD COMPONENTS, so the committed spec documented five properties — and
         * Jackson serialises a record's no-arg public accessors too, so the wire would have carried
         * six. It never got that far: the accessor THROWS for the six ENTITY_ADJUSTING codes, so
         * `GET /api/aade-invoice-types` answered 500 "Failed to write request" and made the
         * disagreement loud.
         *
         * ⚠️ **The 500 was luck.** A derived accessor that merely returned a value would have added
         * an undocumented field to every response, the generated TypeScript would not have had it,
         * and nothing anywhere would have said so. That is the case this pins.
         */
        JsonNode first = items("/api/aade-invoice-types").getFirst();

        List<String> properties = new java.util.ArrayList<>();
        first.propertyNames().forEach(properties::add);

        assertThat(properties)
                .as("the wire body must carry exactly what AadeInvoiceTypeView declares as record "
                        + "components, which is what the committed spec describes")
                .containsExactlyInAnyOrder("id", "code", "description", "group", "active");
    }

    @Test
    @DisplayName("⚠️ POST /api/aade-invoice-types does not exist, and that is the contract")
    void theCodificationHasNoCreateRoute() {
        // The absence, asserted from outside. StatutoryCodificationRulesTest asserts it at build
        // time on the service; this asserts that no route reaches one either, which is the half a
        // caller can see. 405 or 404 both mean "no such operation"; 2xx would mean the contract is
        // decorative.
        ResponseEntity<String> refused = owner.post("/api/aade-invoice-types", """
                {"code":"99.9","description":"Invented","group":"ISSUER_MATCHED"}""");

        assertThat(refused.getStatusCode().is2xxSuccessful())
                .as("a route that authored an AADE code would let a compliance defect be typed into "
                        + "a form. Body was: %s", refused.getBody())
                .isFalse();
        assertThat(items("/api/aade-invoice-types")).hasSize(55);
    }

    @Test
    @DisplayName("a code's description is editable; deactivate and reactivate round-trip")
    void theThreePermittedOperations() {
        long id = byCode(items("/api/aade-invoice-types"), "8.6").get("id").asLong();
        String original = Json.text(byCode(items("/api/aade-invoice-types"), "8.6"), "description");

        ResponseEntity<String> described = owner.patch(
                "/api/aade-invoice-types/" + id + "/description", """
                {"description":"Δελτίο Παραγγελίας Εστίασης (edited)"}""");
        assertThat(described.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Json.text(Json.read(described), "code"))
                .as("the code is the identity and no route touches it")
                .isEqualTo("8.6");

        assertThat(owner.post("/api/aade-invoice-types/" + id + "/deactivate", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(items("/api/aade-invoice-types?active=true")).hasSize(54);
        assertThat(owner.post("/api/aade-invoice-types/" + id + "/reactivate", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(items("/api/aade-invoice-types?active=true")).hasSize(55);

        // Restored, because later assertions in this class read the seed.
        owner.patch("/api/aade-invoice-types/" + id + "/description",
                "{\"description\":\"" + original + "\"}");
    }

    @Test
    @DisplayName("a blank description is refused with its reason, never a bare 400")
    void aBlankDescriptionSaysWhy() {
        long id = byCode(items("/api/aade-invoice-types"), "8.6").get("id").asLong();

        ResponseEntity<String> refused = owner.patch(
                "/api/aade-invoice-types/" + id + "/description", """
                {"description":"   "}""");

        assertThat(refused.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(refused.getBody())
                .as("an operator who cannot see why a change was refused cannot fix it")
                .doesNotContain("Bad request.")
                .contains("blank");
    }

    @Test
    @DisplayName("GET /api/aade-invoice-types/{id} answers 404 for an id that names nothing")
    void anUnknownCodeIsANotFound() {
        assertThat(owner.get("/api/aade-invoice-types/999999").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===========================================================================================
    // Layer 2 — the business's own lists. Full CRUD, and they ship empty.
    // ===========================================================================================

    @Test
    @DisplayName("⚠️ every layer-2 table ships EMPTY — the owner creates his own")
    void theBusinessListsShipEmpty() {
        // Asserted first and over HTTP, because "we deliberately seeded nothing" and "the seed
        // silently failed" look identical from a screen, and only one of them is what was decided.
        // The owner's nineteen types are deliberately not seeded and their Go→AADE mappings
        // deliberately not inferred: an inferred mapping is a guess in a statutory field.
        for (String route : List.of(
                "/api/sales-document-types", "/api/purchase-document-types",
                "/api/sales-document-series", "/api/purchase-document-series",
                "/api/delivery-methods")) {
            assertThat(items(route)).as("%s must ship empty", route).isEmpty();
        }
    }

    @Test
    @DisplayName("a sales document type with NO AADE code is created, and that is the ordinary case")
    void aSalesTypeWithoutAnAadeCode() {
        // Six of the owner's nineteen are exactly this. The previous model — where the AADE code
        // WAS the row — could not represent them, which is what R1a corrected.
        ResponseEntity<String> created = owner.post("/api/sales-document-types", """
                {"description":"Προσφορά","affectsStock":false,"transfersStock":false,
                 "requiresMydataTransmission":false,"sortCode":100}""");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode type = Json.read(created);
        assertThat(isAbsent(type, "aadeInvoiceTypeId")).isTrue();
        assertThat(type.get("active").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("⚠️ omitting the stock flags creates an inactive DRAFT, and activating it is refused")
    void anUndecidedTypeIsADraft() {
        // The body a form sends before somebody has answered the stock question. A `false` here
        // would record a decision nobody took, and R1b branches the consumption path on it.
        JsonNode draft = Json.read(owner.post("/api/sales-document-types", """
                {"description":"Undecided over HTTP","requiresMydataTransmission":true,"sortCode":110}""")
                );

        assertThat(isAbsent(draft, "affectsStock"))
                .as("absent or null over the wire — never false, which is the whole reason the "
                        + "column is nullable")
                .isTrue();
        assertThat(draft.get("active").asBoolean()).isFalse();

        long id = draft.get("id").asLong();
        assertThat(items("/api/sales-document-types/drafts"))
                .anySatisfy(item -> assertThat(item.get("id").asLong()).isEqualTo(id));

        ResponseEntity<String> refused =
                owner.post("/api/sales-document-types/" + id + "/reactivate", "");
        assertThat(refused.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(refused.getBody())
                .doesNotContain("Bad request.")
                .contains("stock behaviour is undecided");

        assertThat(owner.put("/api/sales-document-types/" + id + "/stock-behaviour", """
                {"affectsStock":true,"transfersStock":true}""").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.post("/api/sales-document-types/" + id + "/reactivate", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("⚠️ an omitted stock flag on the PUT is a 400 naming it, never a silent false")
    void anOmittedStockFlagIsRefusedByName() {
        long id = Json.read(owner.post("/api/sales-document-types", """
                {"description":"Flag guard","affectsStock":true,"transfersStock":true,
                 "requiresMydataTransmission":true,"sortCode":120}""")).get("id").asLong();

        // ⚠️ The distinction the create route deliberately allows and this one deliberately does
        // not. On POST, an absent flag means "not decided yet" and produces a draft. On this route
        // the caller is *answering* the question, so an absent field is a mistake — and a boxed
        // Boolean is what makes it a 400 naming the field rather than a silent `false`.
        ResponseEntity<String> refused = owner.put(
                "/api/sales-document-types/" + id + "/stock-behaviour", """
                {"affectsStock":true}""");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("transfersStock");
    }

    @Test
    @DisplayName("the wrong side of annex 8.1 is refused, with the code and the group in the message")
    void theSidesAreEnforcedOverHttp() {
        long rentExpense = byCode(items("/api/aade-invoice-types"), "16.1").get("id").asLong();

        ResponseEntity<String> refused = owner.post("/api/sales-document-types",
                "{\"description\":\"Wrong side over HTTP\",\"affectsStock\":false,"
                        + "\"transfersStock\":false,\"requiresMydataTransmission\":true,"
                        + "\"aadeInvoiceTypeId\":" + rentExpense + ",\"sortCode\":700}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(refused.getBody())
                .as("422 with the reason, not 400 — the body was understood and a domain rule "
                        + "refused it, and an operator has to be able to see which")
                .contains("16.1")
                .doesNotContain("Bad request.");
    }

    @Test
    @DisplayName("the AADE mapping is PUT to set and DELETE to clear")
    void theAadeMappingIsSetAndCleared() {
        long salesInvoice = byCode(items("/api/aade-invoice-types"), "1.1").get("id").asLong();
        long id = Json.read(owner.post("/api/sales-document-types", """
                {"description":"Τιμολόγιο over HTTP","affectsStock":false,"transfersStock":false,
                 "requiresMydataTransmission":true,"sortCode":130}""")).get("id").asLong();

        JsonNode mapped = Json.read(owner.put("/api/sales-document-types/" + id
                + "/aade-invoice-type", "{\"aadeInvoiceTypeId\":" + salesInvoice + "}"));
        assertThat(Json.text(mapped, "aadeInvoiceTypeCode"))
                .as("the code travels beside the id, so no screen renders a raw id")
                .isEqualTo("1.1");

        // DELETE and not a PUT of null: the resource removed is the mapping itself, and a body
        // carrying only null says nothing. VatClassController's reduced-counterpart pair set this.
        JsonNode cleared = Json.read(
                owner.delete("/api/sales-document-types/" + id + "/aade-invoice-type"));
        assertThat(isAbsent(cleared, "aadeInvoiceTypeId")).isTrue();

        assertThat(owner.put("/api/sales-document-types/" + id + "/mydata-transmission", """
                {"required":false}""").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.patch("/api/sales-document-types/" + id + "/description", """
                {"description":"Τιμολόγιο over HTTP, renamed"}""").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.post("/api/sales-document-types/" + id + "/deactivate", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(owner.get("/api/sales-document-types/" + id).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("the purchase type surface mirrors it, and enforces the other side")
    void thePurchaseTypeSurface() {
        long intraCommunity = byCode(items("/api/aade-invoice-types"), "14.1").get("id").asLong();
        long salesInvoice = byCode(items("/api/aade-invoice-types"), "1.1").get("id").asLong();

        // 2062 ΤΔΑΑ: a purchase document that brings stock in with a payable behind it.
        long id = Json.read(owner.post("/api/purchase-document-types",
                "{\"description\":\"ΤΔΑΑ over HTTP\",\"affectsStock\":true,"
                        + "\"transfersStock\":false,\"requiresMydataTransmission\":true,"
                        + "\"aadeInvoiceTypeId\":" + intraCommunity + ",\"sortCode\":710}"))
                .get("id").asLong();

        assertThat(owner.put("/api/purchase-document-types/" + id + "/aade-invoice-type",
                        "{\"aadeInvoiceTypeId\":" + salesInvoice + "}")
                        .getStatusCode())
                .as("an issuer-side code on a purchase type is refused")
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        // 2041 Δελτίο Παραλαβής: stock IN with NO payable — the case that justifies the column.
        long receipt = Json.read(owner.post("/api/purchase-document-types", """
                {"description":"Δελτίο Παραλαβής over HTTP","affectsStock":true,
                 "transfersStock":false,"requiresMydataTransmission":false,"sortCode":140}"""))
                .get("id").asLong();

        assertThat(owner.put("/api/purchase-document-types/" + receipt + "/stock-behaviour", """
                {"affectsStock":true,"transfersStock":true}""").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.put("/api/purchase-document-types/" + receipt + "/mydata-transmission", """
                {"required":true}""").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.patch("/api/purchase-document-types/" + receipt + "/description", """
                {"description":"Δελτίο Παραλαβής, renamed"}""").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.delete("/api/purchase-document-types/" + receipt + "/aade-invoice-type")
                        .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.post("/api/purchase-document-types/" + receipt + "/deactivate", "")
                        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(owner.post("/api/purchase-document-types/" + receipt + "/reactivate", "")
                        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(items("/api/purchase-document-types/drafts")).isNotNull();
        assertThat(owner.get("/api/purchase-document-types/" + receipt).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ===========================================================================================
    // Series
    // ===========================================================================================

    @Test
    @DisplayName("a sales series carries a channel, and DELETE says it is not a sales channel")
    void theSalesSeriesSurface() {
        long typeId = Json.read(owner.post("/api/sales-document-types", """
                {"description":"Σειρές over HTTP","affectsStock":true,"transfersStock":true,
                 "requiresMydataTransmission":true,"sortCode":150}""")).get("id").asLong();

        JsonNode series = Json.read(owner.post("/api/sales-document-series",
                "{\"abbreviation\":\"ΑΛΠW\",\"description\":\"Web retail\",\"documentTypeId\":"
                        + typeId + ",\"channel\":\"ECOMMERCE\",\"getsMark\":true,\"sortCode\":740}"));

        long id = series.get("id").asLong();
        assertThat(Json.text(series, "channel")).isEqualTo("ECOMMERCE");
        assertThat(Json.text(series, "documentTypeDescription"))
                .as("resolved server-side, so no screen renders a raw id")
                .isEqualTo("Σειρές over HTTP");

        // ⚠️ Clearing the channel is a real configuration and not a blanked field: a self-supply
        // series is not a sales channel, and in R1b that is what an invoice is REFUSED against.
        assertThat(isAbsent(
                Json.read(owner.delete("/api/sales-document-series/" + id + "/channel")),
                "channel")).isTrue();
        assertThat(Json.text(Json.read(owner.put("/api/sales-document-series/" + id + "/channel",
                """
                {"channel":"STORE_AND_PHONE"}""")), "channel"))
                .isEqualTo("STORE_AND_PHONE");

        // A transformation target, which is all R1a stores — the behaviour needs the Go adapter.
        long target = Json.read(owner.post("/api/sales-document-series",
                "{\"abbreviation\":\"ΠΙΣW\",\"description\":\"Web credit\",\"documentTypeId\":"
                        + typeId + ",\"getsMark\":true,\"sortCode\":750}")).get("id").asLong();

        assertThat(owner.put("/api/sales-document-series/" + id + "/transformation-target",
                        "{\"targetSeriesId\":" + target + "}").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> self = owner.put(
                "/api/sales-document-series/" + id + "/transformation-target",
                "{\"targetSeriesId\":" + id + "}");
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(self.getBody()).contains("cannot transform into itself");

        assertThat(owner.delete("/api/sales-document-series/" + id + "/transformation-target")
                        .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(items("/api/sales-document-series?documentTypeId=" + typeId)).hasSize(2);
        assertThat(owner.get("/api/sales-document-series/" + id).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.patch("/api/sales-document-series/" + id + "/description", """
                {"description":"Web retail, renamed"}""").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.post("/api/sales-document-series/" + id + "/deactivate", "")
                        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(owner.post("/api/sales-document-series/" + id + "/reactivate", "")
                        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("⚠️ the purchase series has NO channel route at all — 404, not an accepted no-op")
    void thePurchaseSeriesHasNoChannelRoute() {
        long typeId = Json.read(owner.post("/api/purchase-document-types", """
                {"description":"Purchase σειρές over HTTP","affectsStock":true,
                 "transfersStock":false,"requiresMydataTransmission":true,"sortCode":160}"""))
                .get("id").asLong();

        long id = Json.read(owner.post("/api/purchase-document-series",
                "{\"abbreviation\":\"ΤΔΑΑ1\",\"description\":\"Purchase series\","
                        + "\"documentTypeId\":" + typeId + ",\"getsMark\":false,\"sortCode\":760}"))
                .get("id").asLong();

        // ⚠️ Asserted from outside, because "there is no route" and "the route silently does
        // nothing" are indistinguishable to a caller — and the second is what a nullable column
        // that nobody reads would have produced. Channel is where a SALE came from.
        ResponseEntity<String> absent = owner.put(
                "/api/purchase-document-series/" + id + "/channel", """
                {"channel":"ECOMMERCE"}""");
        assertThat(absent.getStatusCode().is2xxSuccessful()).isFalse();

        assertThat(owner.get("/api/purchase-document-series/" + id).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(items("/api/purchase-document-series?documentTypeId=" + typeId)).hasSize(1);
        assertThat(owner.patch("/api/purchase-document-series/" + id + "/description", """
                {"description":"Purchase series, renamed"}""").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        long target = Json.read(owner.post("/api/purchase-document-series",
                "{\"abbreviation\":\"ΤΔΑΑ2\",\"description\":\"Second\",\"documentTypeId\":"
                        + typeId + ",\"getsMark\":false,\"sortCode\":770}")).get("id").asLong();
        assertThat(owner.put("/api/purchase-document-series/" + id + "/transformation-target",
                        "{\"targetSeriesId\":" + target + "}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.delete("/api/purchase-document-series/" + id + "/transformation-target")
                        .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.post("/api/purchase-document-series/" + id + "/deactivate", "")
                        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(owner.post("/api/purchase-document-series/" + id + "/reactivate", "")
                        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ===========================================================================================
    // Delivery methods
    // ===========================================================================================

    @Test
    @DisplayName("delivery methods round-trip, and a duplicate abbreviation says so")
    void theDeliveryMethodSurface() {
        JsonNode created = Json.read(owner.post("/api/delivery-methods", """
                {"abbreviation":"ACS","description":"ACS courier"}"""));
        long id = created.get("id").asLong();

        ResponseEntity<String> duplicate = owner.post("/api/delivery-methods", """
                {"abbreviation":"ACS","description":"Second"}""");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(duplicate.getBody()).contains("already exists").doesNotContain("Bad request.");

        assertThat(owner.get("/api/delivery-methods/" + id).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.patch("/api/delivery-methods/" + id + "/description", """
                {"description":"ACS courier, corrected"}""").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.post("/api/delivery-methods/" + id + "/deactivate", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(items("/api/delivery-methods?active=true")).isEmpty();
        assertThat(owner.post("/api/delivery-methods/" + id + "/reactivate", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ===========================================================================================
    // The exemption reasons, which became a statutory codification in R1a
    // ===========================================================================================

    @Test
    @DisplayName("exemption reasons: 31 rows, three write routes, and no create route")
    void theExemptionReasonSurface() {
        List<JsonNode> reasons = items("/api/vat-exemption-reasons");
        assertThat(reasons).hasSize(31);

        // ⚠️ Codes 24 and 28, seeded by V32 from annex 8.3 and carrying NO myDATA string. Annex 8.3
        // gives the reason TEXT, not a wire string — the `N-description` form on the other rows is
        // Prosvasis Go's rendering, and codes 12 and 13 prove that composing one is a bet that
        // loses. Asserted over the wire because null-versus-composed is exactly what a transmitting
        // caller has to be able to tell apart.
        JsonNode twentyFour = reasons.stream()
                .filter(reason -> reason.get("code").asInt() == 24).findFirst().orElseThrow();
        assertThat(Json.text(twentyFour, "description"))
                .isEqualTo("Χωρίς ΦΠΑ - άρθρο 8 του Κώδικα ΦΠΑ");
        assertThat(isAbsent(twentyFour, "mydataCode"))
                .as("no mapping exists, which is a different thing from 'not filled in yet'")
                .isTrue();

        ResponseEntity<String> created = owner.post("/api/vat-exemption-reasons", """
                {"code":99,"description":"Invented","mydataCode":"99-Invented",
                 "inputVatDeductible":false}""");
        assertThat(created.getStatusCode().is2xxSuccessful())
                .as("Q1-b, closed by the contract: row authorship belongs to Flyway")
                .isFalse();

        long id = twentyFour.get("id").asLong();
        assertThat(owner.patch("/api/vat-exemption-reasons/" + id + "/description", """
                {"description":"Χωρίς ΦΠΑ - άρθρο 8 του Κώδικα ΦΠΑ (edited)"}""").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(owner.post("/api/vat-exemption-reasons/" + id + "/deactivate", "")
                        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(owner.post("/api/vat-exemption-reasons/" + id + "/reactivate", "")
                        .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        owner.patch("/api/vat-exemption-reasons/" + id + "/description", """
                {"description":"Χωρίς ΦΠΑ - άρθρο 8 του Κώδικα ΦΠΑ"}""");
    }

    // ===========================================================================================

    private List<JsonNode> items(String route) {
        ResponseEntity<String> response = owner.get(route);
        assertThat(response.getStatusCode())
                .as("GET %s — body was: %s", route, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return Json.items(response, route);
    }

    /**
     * True when a field is absent from the body or explicitly null.
     *
     * <p>⚠️ Both, deliberately. Jackson omits nulls on this surface, so a nullable field that has no
     * value simply is not there — and a test asserting {@code get(field).isNull()} would throw a
     * {@code NullPointerException} rather than fail with a readable message. The domain meaning is
     * the same either way and the assertion should not depend on which one the serialiser chose.
     */
    private static boolean isAbsent(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull();
    }

    private static JsonNode byCode(List<JsonNode> listing, String code) {
        for (JsonNode item : listing) {
            if (code.equals(Json.text(item, "code"))) {
                return item;
            }
        }
        throw new AssertionError("No row with code '" + code + "' — the seed changed.");
    }
}
