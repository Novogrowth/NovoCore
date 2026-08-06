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
 * R1b's changed write routes, judged by the real server over real HTTP.
 *
 * <h2>Why the service tests are not enough</h2>
 *
 * <p>{@code SalesInvoiceIT} exercises every one of these rules at the service layer, and that is the
 * right place for them. It cannot answer the question this class exists for: <strong>what does an
 * HTTP caller actually get?</strong> R1a's own defect is the standing proof — a derived accessor
 * that every service-layer test was happy with answered {@code 500} for a whole codification, and
 * only the wire could say so.
 *
 * <p>Three things below are only observable here:
 *
 * <ul>
 *   <li>the request body no longer carries {@code channel} and <strong>does</strong> carry
 *       {@code seriesId} — a screen builds JSON, not a record, and the record cannot tell it so;
 *   <li>each refusal's <strong>status and message reach the caller</strong>. A domain refusal must
 *       be {@code 422} with its reason, not a bare {@code 400 "Bad request."} and not a {@code 5xx}
 *       — the failure {@code CLAUDE.md} names as <em>a client's mistake raised as a programming
 *       error</em>;
 *   <li>the response body carries the resolved {@code channel} and {@code seriesAbbreviation},
 *       which R1a shipped as {@code null} because nothing had a series.
 * </ul>
 *
 * <p>⚠️ <strong>The JSON is written out as literals, deliberately.</strong> Serialising
 * {@code NewSalesInvoice} here would ask Jackson to agree with itself and would prove nothing about
 * what a browser sends — the same reason {@code F4WriteContractIT} spells its bodies out.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + R1bWriteContractIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + R1bWriteContractIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class R1bWriteContractIT {

    static final String OWNER_USERNAME = "r1b.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    @Autowired private TestRestTemplate rest;

    private ApiClient.Session owner;

    /**
     * Ids resolved once against the running server, not assumed.
     *
     * <p>⚠️ <strong>Static, and that is not a style choice.</strong> JUnit builds a new test-class
     * instance per test method, so instance fields are empty every time and the second method would
     * re-create a product whose SKU already exists — which the server correctly refuses with a 422.
     * The Spring context is shared across the class, so one-time setup has to be held somewhere that
     * outlives the instance.
     */
    private static long customerId;

    /** ⚠️ R4 ships payment_method EMPTY — this test authors the method it settles with. */
    private static long paymentMethodId;
    private static long productId;
    private static long movingSeriesId;
    private static long nonMovingSeriesId;
    private static long channelLessSeriesId;

    @BeforeEach
    void setUp() {
        owner = new ApiClient(rest).logIn(OWNER_USERNAME, OWNER_PASSWORD);
        if (customerId != 0) {
            return;
        }

        // The retail customer is seeded and structural (ADR 0009 / Q10), so it is found rather than
        // created — and a sale must be against somebody.
        customerId = Json.items(owner.get("/api/customers"), "the customers").stream()
                .filter(customer -> customer.get("systemKey") != null
                        && !customer.get("systemKey").isNull())
                .mapToLong(customer -> customer.get("id").asLong())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no system customer to sell to"));

        paymentMethodId = PaymentMethods.onAccount(owner, "r1b-write");

        long unitId = Json.items(owner.get("/api/units-of-measure"), "the units").getFirst()
                .get("id").asLong();
        long vatClassId = Json.items(owner.get("/api/vat-classes"), "the VAT classes").getFirst()
                .get("id").asLong();

        productId = Json.createdId(owner.post("/api/products", """
                {"sku":"R1B-CONTRACT-01","name":"R1b contract product","type":"GOODS",
                 "unitOfMeasureId":%d,"defaultVatClassId":%d,"sellingPrice":
                 {"amount":"50.00","currency":"EUR"},"serialTracked":false}
                """.formatted(unitId, vatClassId)), "the product");

        long movingTypeId = Json.createdId(owner.post("/api/sales-document-types", """
                {"description":"R1B Moving","affectsStock":true,"transfersStock":true,
                 "requiresMydataTransmission":true,"sortCode":100}
                """), "the stock-moving type");
        long nonMovingTypeId = Json.createdId(owner.post("/api/sales-document-types", """
                {"description":"R1B Non-moving","affectsStock":false,"transfersStock":false,
                 "requiresMydataTransmission":true,"sortCode":110}
                """), "the non-stock-moving type");

        movingSeriesId = Json.createdId(owner.post("/api/sales-document-series", """
                {"abbreviation":"R1B-W","description":"R1b web series","documentTypeId":%d,
                 "channel":"ECOMMERCE","getsMark":false,"sortCode":120}
                """.formatted(movingTypeId)), "the web series");
        nonMovingSeriesId = Json.createdId(owner.post("/api/sales-document-series", """
                {"abbreviation":"R1B-T","description":"R1b invoice series","documentTypeId":%d,
                 "channel":"STORE_AND_PHONE","getsMark":false,"sortCode":130}
                """.formatted(nonMovingTypeId)), "the non-moving series");
        // ⚠️ No channel at all — the self-supply shape. Recording against it must be refused.
        channelLessSeriesId = Json.createdId(owner.post("/api/sales-document-series", """
                {"abbreviation":"R1B-SELF","description":"R1b self-supply series",
                 "documentTypeId":%d,"getsMark":false,"sortCode":140}
                """.formatted(movingTypeId)), "the channel-less series");
        // ⚠️ NO OPENING STOCK, DELIBERATELY, AND IT MAKES THE TEST STRONGER RATHER THAN WEAKER.
        //
        // There is no route that creates an inventory lot directly — stock arrives through a goods
        // receipt, which would mean a supplier and a purchase invoice for a test that is about the
        // sales contract. It is not needed: pooled stock NEVER blocks a sale (Q17 / ADR 0008), so a
        // stock-moving type still creates its consumption row and records the shortfall on it.
        //
        // So the contrast these tests turn on — a consumption row versus no row at all — is driven
        // purely by the document type, with stock held constant at zero for both. That is a cleaner
        // comparison than one where the two sides also differ in what was on the shelf.
    }

    /** The exact body an F5 form will send — no {@code channel}, a {@code seriesId}. */
    private String saleIn(long seriesId, String documentNumber) {
        return """
                {"customerId":%d,"seriesId":%d,"paymentMethodId":%d,
                 "documentNumber":"%s","invoiceDate":"2026-07-20",
                 "lines":[{"lineType":"PRODUCT","productId":%d,"quantity":"2.000000",
                           "unitPrice":{"amount":"50.000000","currency":"EUR"}}]}
                """.formatted(customerId, seriesId, paymentMethodId, documentNumber, productId);
    }

    private static String number(String suffix) {
        return "R1B-" + suffix + "-" + System.nanoTime();
    }

    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a body with seriesId and NO channel is accepted, and the response resolves both")
    void theSeriesSuppliesTheChannelOnTheWire() {
        ResponseEntity<String> response =
                owner.post("/api/sales-invoices", saleIn(movingSeriesId, number("OK")));

        assertThat(response.getStatusCode())
                .as("the body an F5 form will send must be accepted by the real server; a %s body "
                        + "is %s", response.getStatusCode(), response.getBody())
                .isEqualTo(HttpStatus.CREATED);

        JsonNode invoice = Json.read(response);
        assertThat(Json.text(invoice, "channel"))
                .as("nothing in the request said ECOMMERCE — the series did")
                .isEqualTo("ECOMMERCE");
        assertThat(invoice.get("seriesId").asLong()).isEqualTo(movingSeriesId);
        assertThat(Json.text(invoice, "seriesAbbreviation"))
                .as("R1a shipped this null because nothing had a series; R1b resolves it, and only "
                        + "the wire can say whether it reaches a caller")
                .isEqualTo("R1B-W");

        // The stock-moving type consumed — the negative control for the next test.
        assertThat(invoice.get("lines").get(0).get("stockConsumptionId").isNull())
                .as("a stock-moving type must still take the goods off the shelf")
                .isFalse();
    }

    @Test
    @DisplayName("a non-stock-moving type creates no consumption, and the response says nothing")
    void aNonStockMovingTypeIsSilentOnTheWire() {
        JsonNode invoice = Json.ok(
                owner.post("/api/sales-invoices", saleIn(nonMovingSeriesId, number("NS"))),
                "the non-stock-moving sale");

        // ⚠️ ABSENT, not null — a wire fact worth stating rather than working around. Where a
        // consumption exists the property is present (the test above asserts exactly that); where
        // there is none the serialiser omits it entirely. Both satisfy the spec, which declares it
        // optional, and orval renders it `stockConsumptionId?: number` accordingly.
        JsonNode consumptionId = invoice.get("lines").get(0).get("stockConsumptionId");
        assertThat(consumptionId == null || consumptionId.isNull())
                .as("a plain Τιμολόγιο is purely a sale — the goods leave later on a dispatch "
                        + "document (18b) — so the line carries no consumption at all")
                .isTrue();
        // ⚠️ And there is deliberately nothing else to assert. No pending state, no marker, no
        // warning field: the behaviour is silent by decision, and this is what "silent" looks like
        // from the outside. A future field claiming to report it would fail this test, which is
        // the point of writing the absence down.
        java.util.List<String> properties = new java.util.ArrayList<>();
        invoice.propertyNames().forEach(properties::add);
        assertThat(properties)
                .as("no field on the wire reports the gap; the limitation lives in CLAUDE.md")
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("stockmoved")
                        || name.toLowerCase(java.util.Locale.ROOT).contains("pending"));
    }

    @Test
    @DisplayName("a channel-less series is refused 422, with the reason and R3 in the message")
    void aChannelLessSeriesIsRefusedWithItsReason() {
        ResponseEntity<String> response =
                owner.post("/api/sales-invoices", saleIn(channelLessSeriesId, number("CL")));

        assertThat(response.getStatusCode())
                .as("a domain refusal is 422 with its reason — not a bare 400 and not a 5xx")
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .contains("no sales channel")
                .contains("R3")
                .doesNotContain("Bad request.");
    }

    @Test
    @DisplayName("a preview refuses a channel-less series too, so a screen learns before submitting")
    void thePreviewRefusesTheSameThing() {
        ResponseEntity<String> response =
                owner.post("/api/sales-invoices/preview", saleIn(channelLessSeriesId, number("PV")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("no sales channel");
    }

    @Test
    @DisplayName("an omitted seriesId is refused 400 NAMING the field, not 'Malformed request body'")
    void anOmittedSeriesIsNamed() {
        // Required.field rather than requireNonNull is what makes this message exist. Only the wire
        // can show which of the two a caller actually gets.
        ResponseEntity<String> response = owner.post("/api/sales-invoices", """
                {"customerId":%d,"paymentMethodId":%d,"documentNumber":"%s",
                 "invoiceDate":"2026-07-20","lines":[{"lineType":"PRODUCT","productId":%d,
                 "quantity":"1.000000","unitPrice":{"amount":"50.000000","currency":"EUR"}}]}
                """.formatted(customerId, paymentMethodId, number("NOSERIES"), productId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .as("the caller must be told WHICH field, or they cannot fix it")
                .contains("seriesId");
    }

    @Test
    @DisplayName("a seriesId naming nothing is 404, like every other unknown id on this surface")
    void anUnknownSeriesIsNotFound() {
        ResponseEntity<String> response =
                owner.post("/api/sales-invoices", saleIn(999_999_999L, number("GHOST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("an inactive series and an inactive type are both refused 422")
    void inactiveReferenceDataIsRefused() {
        long typeId = Json.createdId(owner.post("/api/sales-document-types", """
                {"description":"R1B Retired","affectsStock":true,"transfersStock":true,
                 "requiresMydataTransmission":true,"sortCode":150}
                """), "the type to retire");
        long seriesId = Json.createdId(owner.post("/api/sales-document-series", """
                {"abbreviation":"R1B-X","description":"R1b retiring series","documentTypeId":%d,
                 "channel":"SKROUTZ","getsMark":false,"sortCode":160}
                """.formatted(typeId)), "the series to retire");

        // It works first, so the refusals below are about the deactivation and not the fixture.
        assertThat(owner.post("/api/sales-invoices", saleIn(seriesId, number("PRE")))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Json.succeeded(owner.post("/api/sales-document-series/" + seriesId + "/deactivate", "{}"),
                "deactivating the series");
        ResponseEntity<String> refusedSeries =
                owner.post("/api/sales-invoices", saleIn(seriesId, number("INACT-S")));
        assertThat(refusedSeries.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(refusedSeries.getBody()).contains("is inactive");

        Json.succeeded(owner.post("/api/sales-document-series/" + seriesId + "/reactivate", "{}"),
                "reactivating the series");
        Json.succeeded(owner.post("/api/sales-document-types/" + typeId + "/deactivate", "{}"),
                "deactivating the type");
        ResponseEntity<String> refusedType =
                owner.post("/api/sales-invoices", saleIn(seriesId, number("INACT-T")));
        assertThat(refusedType.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(refusedType.getBody()).contains("which is inactive");
    }

    @Test
    @DisplayName("the same number in two different series is two documents — R1a's C.6, over HTTP")
    void theSameNumberInTwoSeriesIsAllowed() {
        String documentNumber = number("SHARED");

        assertThat(owner.post("/api/sales-invoices", saleIn(movingSeriesId, documentNumber))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(owner.post("/api/sales-invoices", saleIn(nonMovingSeriesId, documentNumber))
                .getStatusCode())
                .as("ΑΛΠ-1 and ΤΠΔΑ-1 are two different documents that both legitimately carry the "
                        + "number 1 — the reason V32 made the key per-series")
                .isEqualTo(HttpStatus.CREATED);

        // ...and the same number in the SAME series is still one document.
        ResponseEntity<String> duplicate =
                owner.post("/api/sales-invoices", saleIn(movingSeriesId, documentNumber));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(duplicate.getBody()).contains("already been recorded in series");
    }
}
