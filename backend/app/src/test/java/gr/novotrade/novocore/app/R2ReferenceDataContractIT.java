package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
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
 * R2's seven correction routes, judged by the real server over real HTTP.
 *
 * <h2>What this class is for, and it is not the screens</h2>
 *
 * <p>R2 was scoped as six settings screens. It grew a backend sub-part because a series'
 * {@code abbreviation}, {@code documentTypeId} and {@code getsMark}, and a delivery method's
 * {@code abbreviation}, had <strong>no write route on any installation</strong> — so the owner was
 * about to hand-author nineteen Greek document types and their series with no correction path at
 * all. Deactivate-and-recreate is not one: {@code sales_document_series_abbreviation_unique} is not
 * a partial index, so the abbreviation is burned permanently by the dead row.
 *
 * <p>The rule those routes implement is <strong>editable while unused, frozen once used</strong>,
 * and it has exactly two halves. A test that only proves the refusal would pass against a route
 * that refuses everything; a test that only proves the correction would pass against a route with
 * no guard at all. <strong>Both are asserted here, on the same series, either side of one
 * invoice.</strong>
 *
 * <h2>⚠️ The JSON is written out as literals</h2>
 *
 * <p>Serialising the request records would ask Jackson to agree with itself and prove nothing about
 * what a browser sends — {@code F4WriteContractIT}'s reason, and R1a's standing proof that only the
 * wire can answer a wire question.
 *
 * <h2>⚠️ Two of these refusals cannot fire, and that is stated rather than hidden</h2>
 *
 * <p>Measured 2026-08-04 against the live schema: the only foreign key referencing
 * {@code purchase_document_series} is its own transformation target, and <strong>nothing at all</strong>
 * references {@code delivery_method}. So the purchase-side and delivery-method guards are structurally
 * unreachable until F6 and 18b respectively. Their <em>correction</em> half is asserted here; their
 * <em>refusal</em> half cannot be, and {@link DocumentReferenceGraphIT} is what makes the day it
 * becomes reachable a red build rather than a silent gap.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + R2ReferenceDataContractIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + R2ReferenceDataContractIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class R2ReferenceDataContractIT {

    static final String OWNER_USERNAME = "r2.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    @Autowired private TestRestTemplate rest;

    private ApiClient.Session owner;

    /** Static for the reason {@code R1bWriteContractIT} states: JUnit rebuilds the instance. */
    private static long customerId;
    private static long productId;
    private static long typeId;
    private static long otherTypeId;

    @BeforeEach
    void setUp() {
        owner = new ApiClient(rest).logIn(OWNER_USERNAME, OWNER_PASSWORD);
        if (customerId != 0) {
            return;
        }

        customerId = Json.items(owner.get("/api/customers"), "the customers").stream()
                .filter(customer -> customer.get("systemKey") != null
                        && !customer.get("systemKey").isNull())
                .mapToLong(customer -> customer.get("id").asLong())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no system customer to sell to"));

        long unitId = Json.items(owner.get("/api/units-of-measure"), "the units").getFirst()
                .get("id").asLong();
        long vatClassId = Json.items(owner.get("/api/vat-classes"), "the VAT classes").getFirst()
                .get("id").asLong();

        productId = Json.createdId(owner.post("/api/products", """
                {"sku":"R2-CONTRACT-01","name":"R2 contract product","type":"GOODS",
                 "unitOfMeasureId":%d,"defaultVatClassId":%d,"sellingPrice":
                 {"amount":"50.00","currency":"EUR"},"serialTracked":false}
                """.formatted(unitId, vatClassId)), "the product");

        typeId = Json.createdId(owner.post("/api/sales-document-types", """
                {"description":"R2 Retail receipt","affectsStock":true,"transfersStock":true,
                 "requiresMydataTransmission":true}
                """), "the document type");
        otherTypeId = Json.createdId(owner.post("/api/sales-document-types", """
                {"description":"R2 Plain invoice","affectsStock":false,"transfersStock":false,
                 "requiresMydataTransmission":true}
                """), "the second document type");
    }

    // ===============================================================================================
    // The sales series — the one place both halves of the rule are reachable
    // ===============================================================================================

    @Test
    @DisplayName("⚠️ all three fields are correctable while the series is unused")
    void aFreshSeriesIsFullyCorrectable() {
        long seriesId = createSeries("R2-TYPO", "R2 series with a typo");

        JsonNode renamed = Json.ok(owner.patch("/api/sales-document-series/" + seriesId
                + "/abbreviation", """
                {"abbreviation":"R2-FIXED"}"""), "the corrected abbreviation");
        assertThat(Json.text(renamed, "abbreviation")).isEqualTo("R2-FIXED");
        assertThat(renamed.get("inUse").asBoolean())
                .as("nothing has been recorded in it yet")
                .isFalse();

        JsonNode retyped = Json.ok(owner.put("/api/sales-document-series/" + seriesId
                + "/document-type", """
                {"documentTypeId":%d}""".formatted(otherTypeId)), "the corrected document type");
        assertThat(retyped.get("documentTypeId").asLong()).isEqualTo(otherTypeId);

        JsonNode marked = Json.ok(owner.put("/api/sales-document-series/" + seriesId
                + "/gets-mark", """
                {"getsMark":true}"""), "the corrected ΜΑΡΚ flag");
        assertThat(marked.get("getsMark").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("⚠️ once an invoice names the series, all three are refused 422 WITH THE REASON")
    void arecordedDocumentFreezesTheThreeFields() {
        long seriesId = createSeries("R2-USED", "R2 series that will be used");

        // ⚠️ THE NEGATIVE CONTROL FOR THIS TEST'S OWN APPARATUS.
        //
        // The refusal below is only evidence if the same request SUCCEEDS before the invoice
        // exists. A guard that refuses unconditionally would pass the second half and fail nothing,
        // and a run in which the invoice was never created would look identical. So the correction
        // is made first, watched to succeed, and only then is the series used.
        Json.ok(owner.patch("/api/sales-document-series/" + seriesId + "/abbreviation", """
                {"abbreviation":"R2-USED-OK"}"""), "the correction BEFORE anything is recorded");

        assertThat(Json.ok(owner.get("/api/sales-document-series/" + seriesId), "the series")
                .get("inUse").asBoolean())
                .as("still unused, so the first correction was not a fluke of a broken guard")
                .isFalse();

        recordAnInvoiceIn(seriesId);

        JsonNode afterwards = Json.ok(
                owner.get("/api/sales-document-series/" + seriesId), "the series");
        assertThat(afterwards.get("inUse").asBoolean())
                .as("an invoice names it now")
                .isTrue();

        refusedWithReason(owner.patch("/api/sales-document-series/" + seriesId + "/abbreviation",
                """
                {"abbreviation":"R2-TOO-LATE"}"""), "abbreviation");
        refusedWithReason(owner.put("/api/sales-document-series/" + seriesId + "/document-type",
                """
                {"documentTypeId":%d}""".formatted(otherTypeId)), "document type");
        refusedWithReason(owner.put("/api/sales-document-series/" + seriesId + "/gets-mark",
                """
                {"getsMark":true}"""), "ΜΑΡΚ flag");
    }

    @Test
    @DisplayName("a no-op correction is accepted even on a used series, so a screen may resend")
    void resendingTheSameValueIsNotARefusal() {
        long seriesId = createSeries("R2-NOOP", "R2 series for the no-op case");
        recordAnInvoiceIn(seriesId);

        // A field editor sends on blur. Refusing the value the row already holds would make a
        // locked field un-closeable without an error the operator cannot act on.
        JsonNode same = Json.ok(owner.patch("/api/sales-document-series/" + seriesId
                + "/abbreviation", """
                {"abbreviation":"R2-NOOP"}"""), "the same abbreviation");
        assertThat(Json.text(same, "abbreviation")).isEqualTo("R2-NOOP");
        assertThat(same.get("inUse").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a duplicate abbreviation is refused 422, not a constraint-violation 500")
    void aDuplicateAbbreviationIsARefusal() {
        createSeries("R2-TAKEN", "R2 series holding the abbreviation");
        long other = createSeries("R2-FREE", "R2 series that wants it");

        ResponseEntity<String> refused = owner.patch(
                "/api/sales-document-series/" + other + "/abbreviation", """
                {"abbreviation":"R2-TAKEN"}""");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(refused.getBody()).contains("already exists");
    }

    @Test
    @DisplayName("⚠️ an omitted field is a 400 NAMING it, never a silent false or a 500")
    void anOmittedFieldNamesItself() {
        long seriesId = createSeries("R2-EMPTY", "R2 series for the empty-body case");

        // `getsMark` is boxed precisely so this names the field. A primitive would answer
        // "Cannot map null into type boolean" naming nothing — the defect that broke product
        // creation for every user.
        ResponseEntity<String> refused =
                owner.put("/api/sales-document-series/" + seriesId + "/gets-mark", "{}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("getsMark");
    }

    // ===============================================================================================
    // The purchase series and delivery methods — correction reachable, refusal structurally not
    // ===============================================================================================

    @Test
    @DisplayName("a purchase series is correctable; ⚠️ its refusal cannot fire until F6")
    void aPurchaseSeriesIsCorrectable() {
        long purchaseType = Json.createdId(owner.post("/api/purchase-document-types", """
                {"description":"R2 Supplier invoice","affectsStock":true,"transfersStock":false,
                 "requiresMydataTransmission":true}
                """), "the purchase type");
        long otherPurchaseType = Json.createdId(owner.post("/api/purchase-document-types", """
                {"description":"R2 Goods receipt note","affectsStock":true,"transfersStock":false,
                 "requiresMydataTransmission":false}
                """), "the second purchase type");

        long seriesId = Json.createdId(owner.post("/api/purchase-document-series", """
                {"abbreviation":"R2-P-TYPO","description":"R2 purchase series",
                 "documentTypeId":%d,"getsMark":false}
                """.formatted(purchaseType)), "the purchase series");

        assertThat(Json.text(Json.ok(owner.patch("/api/purchase-document-series/" + seriesId
                + "/abbreviation", """
                {"abbreviation":"R2-P-FIXED"}"""), "the corrected abbreviation"), "abbreviation"))
                .isEqualTo("R2-P-FIXED");

        assertThat(Json.ok(owner.put("/api/purchase-document-series/" + seriesId
                + "/document-type", """
                {"documentTypeId":%d}""".formatted(otherPurchaseType)), "the corrected type")
                .get("documentTypeId").asLong())
                .isEqualTo(otherPurchaseType);

        JsonNode marked = Json.ok(owner.put("/api/purchase-document-series/" + seriesId
                + "/gets-mark", """
                {"getsMark":true}"""), "the corrected ΜΑΡΚ flag");
        assertThat(marked.get("getsMark").asBoolean()).isTrue();

        // ⚠️ Asserted, not assumed: nothing can make a purchase series used today, so this must
        // stay false however much the series is edited. DocumentReferenceGraphIT is what turns F6
        // giving a purchase document a series into a red build rather than a silent gap here.
        assertThat(marked.get("inUse").asBoolean())
                .as("no purchase document carries a series before F6")
                .isFalse();
    }

    @Test
    @DisplayName("a delivery method's abbreviation is correctable; ⚠️ nothing references the table")
    void aDeliveryMethodIsCorrectable() {
        long id = Json.createdId(owner.post("/api/delivery-methods", """
                {"abbreviation":"R2-DM-TYPO","description":"R2 courier"}"""), "the method");

        JsonNode corrected = Json.ok(owner.patch("/api/delivery-methods/" + id + "/abbreviation",
                """
                {"abbreviation":"R2-DM-FIXED"}"""), "the corrected abbreviation");

        assertThat(Json.text(corrected, "abbreviation")).isEqualTo("R2-DM-FIXED");
        assertThat(corrected.get("inUse").asBoolean())
                .as("no table in this schema has a foreign key to delivery_method")
                .isFalse();
    }

    // ===============================================================================================

    private long createSeries(String abbreviation, String description) {
        return Json.createdId(owner.post("/api/sales-document-series", """
                {"abbreviation":"%s","description":"%s","documentTypeId":%d,
                 "channel":"STORE_AND_PHONE","getsMark":false}
                """.formatted(abbreviation, description, typeId)), "the series " + abbreviation);
    }

    /**
     * One recorded sale, which is the whole of what "used" means.
     *
     * <p>No opening stock, for {@code R1bWriteContractIT}'s reason: pooled stock never blocks a sale
     * (Q17 / ADR 0008), so the invoice records and the shortfall is noted. Nothing here is about
     * stock.
     */
    private void recordAnInvoiceIn(long seriesId) {
        Json.createdId(owner.post("/api/sales-invoices", """
                {"customerId":%d,"seriesId":%d,"settlementMethod":"ON_ACCOUNT",
                 "documentNumber":"R2-%d-0001","invoiceDate":"2026-03-01",
                 "lines":[{"lineType":"PRODUCT","productId":%d,"quantity":"1.000000",
                           "unitPrice":{"amount":"50.000000","currency":"EUR"}}]}
                """.formatted(customerId, seriesId, seriesId, productId)),
                "the invoice that uses the series");
    }

    /**
     * ⚠️ The assertion that matters most, and it is three assertions rather than one.
     *
     * <p>A domain refusal must be <strong>422</strong> — not a bare {@code 400 "Bad request."},
     * which is what {@code IllegalArgumentException} produces, and not a {@code 5xx}. And it must
     * <strong>say why</strong>: an operator who cannot see why a field is locked cannot act on it.
     */
    private static void refusedWithReason(ResponseEntity<String> response, String field) {
        assertThat(response.getStatusCode())
                .as("changing the %s of a used series is a domain refusal, not a bad request", field)
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .as("the refusal must name what is wrong")
                .contains("cannot be changed")
                .contains("already been recorded");
    }
}
