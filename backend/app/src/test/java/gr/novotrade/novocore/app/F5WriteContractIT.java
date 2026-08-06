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
 * F5's screens judged by the real server — <strong>the literal JSON they build, over real HTTP</strong>.
 *
 * <h2>Why this exists when three screen-test files already pass</h2>
 *
 * <p><strong>A screen test proves wiring, never contract.</strong> {@code msw} answers whatever it
 * was told to answer, so it cannot say whether a body satisfies the real one — and this repository
 * has already paid for that: the Products create form was cleared by a headless check that
 * intercepted the {@code POST} and fabricated a {@code 201}, while creating a product failed for
 * every user, every time. {@code CLAUDE.md} names it <em>a verification that answers its own
 * request</em>.
 *
 * <p><strong>Scope, and what is deliberately NOT here.</strong> {@code R1bWriteContractIT} already
 * drives the sales-invoice write contract over HTTP — the series supplying the channel, the four
 * refusals, per-series numbering. Repeating it would add runtime and no evidence. What is left is
 * everything F5's screens do that nothing has ever sent:
 *
 * <ul>
 *   <li>the <strong>credit note</strong> body the record form builds — {@code salesInvoiceId} plus
 *       lines naming a {@code salesInvoiceLineId} and a per-line {@code stockReturned}. R1b never
 *       touched credit notes;
 *   <li>the <strong>preview → refusal → accept</strong> sequence the rounding control depends on.
 *       The screen renders a control only because a preview said {@code roundingNeedsAcceptance},
 *       and refuses to submit without a name only because the server would refuse it. Both halves
 *       are contract, and a mock can assert neither;
 *   <li>the <strong>list parameters</strong> the two list screens send — a date range, {@code page},
 *       {@code size}, {@code sort} and {@code search}. ⚠️ An unknown query parameter is
 *       <strong>silently ignored</strong> by Spring (measured in F5's Phase 0), so a screen sending
 *       a misspelled one would look like it had filtered and would have filtered nothing;
 *   <li>{@code documentNumber}'s refusal <strong>naming the field</strong>, which is what A.2 bought.
 * </ul>
 *
 * <p>⚠️ <strong>The JSON is written out as literals, deliberately</strong> — building it from
 * {@code NewCreditNote} would ask Jackson to agree with itself and would prove nothing about what a
 * browser sends. Same reason {@code F4WriteContractIT} and {@code R1bWriteContractIT} spell theirs
 * out.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + F5WriteContractIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + F5WriteContractIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class F5WriteContractIT {

    static final String OWNER_USERNAME = "f5.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    @Autowired private TestRestTemplate rest;

    private ApiClient.Session owner;

    /**
     * Resolved once against the running server.
     *
     * <p>⚠️ Static for the reason {@code R1bWriteContractIT} records: JUnit builds a new instance per
     * method, so instance fields would re-run setup and the second method would re-create a product
     * whose SKU already exists — which the server correctly refuses.
     */
    private static long customerId;

    /** ⚠️ R4 ships payment_method EMPTY — this test authors the method it settles with. */
    private static long paymentMethodId;
    private static long productId;
    private static long seriesId;

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

        paymentMethodId = PaymentMethods.onAccount(owner, "f5-write");

        long unitId = Json.items(owner.get("/api/units-of-measure"), "the units").getFirst()
                .get("id").asLong();
        long vatClassId = Json.items(owner.get("/api/vat-classes"), "the VAT classes").getFirst()
                .get("id").asLong();

        productId = Json.createdId(owner.post("/api/products", """
                {"sku":"F5-CONTRACT-01","name":"F5 contract product","type":"GOODS",
                 "unitOfMeasureId":%d,"defaultVatClassId":%d,"sellingPrice":
                 {"amount":"10.00","currency":"EUR"},"serialTracked":false}
                """.formatted(unitId, vatClassId)), "the product");

        long typeId = Json.createdId(owner.post("/api/sales-document-types", """
                {"description":"F5 Contract","affectsStock":true,"transfersStock":true,
                 "requiresMydataTransmission":true,"sortCode":200}
                """), "the document type");
        seriesId = Json.createdId(owner.post("/api/sales-document-series", """
                {"abbreviation":"F5-W","description":"F5 contract series","documentTypeId":%d,
                 "channel":"ECOMMERCE","getsMark":false,"sortCode":210}
                """.formatted(typeId)), "the series");
    }

    private static String number(String suffix) {
        return "F5-" + suffix + "-" + System.nanoTime();
    }

    /** Exactly what {@code sales-invoice-record.tsx} builds — plus an optional stated total. */
    private String saleBody(String documentNumber, String quantity, String statedTotal) {
        return """
                {"customerId":%d,"seriesId":%d,"paymentMethodId":%d,
                 "documentNumber":"%s","invoiceDate":"2026-07-20",%s
                 "lines":[{"lineType":"PRODUCT","productId":%d,"quantity":"%s",
                           "unitPrice":{"amount":"10.000000","currency":"EUR"},
                           "serialNumbers":[]}]}
                """.formatted(customerId, seriesId, paymentMethodId, documentNumber,
                        statedTotal == null
                                ? ""
                                : "\"statedTotal\":{\"amount\":\"" + statedTotal
                                        + "\",\"currency\":\"EUR\"},",
                        productId, quantity);
    }

    private JsonNode recordSale(String documentNumber) {
        return Json.ok(owner.post("/api/sales-invoices", saleBody(documentNumber, "2.000000", null)),
                "the sale to credit");
    }

    // -------------------------------------------------------------------------------------------
    // The credit note record form's body — never sent by anything before F5
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the credit note body the form builds is accepted, and the note derives the sale's facts")
    void theCreditNoteFormsBodyIsAccepted() {
        JsonNode sale = recordSale(number("CN-SALE"));
        long lineId = Json.lineIds(sale).getFirst();

        // ⚠️ EXACTLY the shape `credit-note-record.tsx` builds: no customer, no channel, no
        // settlement method, and a line that names an INVOICE LINE rather than a product.
        ResponseEntity<String> response = owner.post("/api/credit-notes", """
                {"salesInvoiceId":%d,"documentNumber":"%s","creditNoteDate":"2026-07-25",
                 "lines":[{"salesInvoiceLineId":%d,"quantity":"1.000000",
                           "unitPrice":{"amount":"10.000000","currency":"EUR"},
                           "stockReturned":false}]}
                """.formatted(sale.get("id").asLong(), number("CN"), lineId));

        assertThat(response.getStatusCode())
                .as("the body an F5 credit-note form sends must be accepted; a %s body is %s",
                        response.getStatusCode(), response.getBody())
                .isEqualTo(HttpStatus.CREATED);

        JsonNode note = Json.read(response);
        // The screen renders these three as DERIVED, with a line saying so. That claim is only true
        // if the server really supplies them from the invoice — nothing in the request did.
        assertThat(Json.text(note, "channel")).isEqualTo("ECOMMERCE");
        assertThat(note.get("paymentMethodId").asLong()).isEqualTo(paymentMethodId);
        assertThat(note.get("customerId").asLong()).isEqualTo(customerId);
        assertThat(Json.text(note, "salesInvoiceNumber"))
                .as("the list links each note to the sale it credits, so the number has to be on "
                        + "the wire rather than fetched separately")
                .isEqualTo(Json.text(sale, "documentNumber"));
    }

    @Test
    @DisplayName("crediting more than was sold is refused 422 with a readable reason, not a 5xx")
    void creditingMoreThanWasSoldIsRefusedWithItsReason() {
        JsonNode sale = recordSale(number("CN-OVER-SALE"));
        long lineId = Json.lineIds(sale).getFirst();

        // The sale was 2; this credits 3.
        ResponseEntity<String> response = owner.post("/api/credit-notes", """
                {"salesInvoiceId":%d,"documentNumber":"%s","creditNoteDate":"2026-07-25",
                 "lines":[{"salesInvoiceLineId":%d,"quantity":"3.000000",
                           "unitPrice":{"amount":"10.000000","currency":"EUR"},
                           "stockReturned":true}]}
                """.formatted(sale.get("id").asLong(), number("CN-OVER"), lineId));

        assertThat(response.getStatusCode())
                .as("a domain refusal is 422 with its reason — this is the one an operator will "
                        + "actually meet, and the screen renders whatever sentence arrives")
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).doesNotContain("Bad request.");
    }

    // -------------------------------------------------------------------------------------------
    // Preview → refusal → accept: the sequence the rounding control is built on
    // -------------------------------------------------------------------------------------------

    /**
     * ⭐ The contract behind C.5 and C.6, end to end.
     *
     * <p>The record form shows an acceptance control <strong>only</strong> when a preview reports
     * {@code roundingNeedsAcceptance}, and keeps its submit button disabled until a name is typed.
     * Both behaviours are assertions about <em>the server</em>: that the preview names the condition,
     * that recording without acceptance is refused, and that the same body plus a name is accepted.
     * A screen test can show the control appearing when a fixture says so, which is a statement
     * about the fixture.
     */
    @Test
    @DisplayName("preview asks for acceptance, the record is refused without it, and accepted with it")
    void thePreviewRefusalAndAcceptanceAgree() {
        // 2 × 10.00 = 20.00 net; the stated total is far enough off to exceed the threshold.
        String documentNumber = number("ROUND");
        String body = saleBody(documentNumber, "2.000000", "30.00");

        JsonNode preview = Json.ok(owner.post("/api/sales-invoices/preview", body), "the preview");
        assertThat(preview.get("roundingNeedsAcceptance").asBoolean())
                .as("the screen renders the acceptance control off this field and nothing else")
                .isTrue();
        // Both figures, because the screen shows the difference AND the threshold — "there is a
        // difference" without the threshold is not something an operator can act on.
        assertThat(Json.amount(preview, "roundingDifference")).isNotNull();
        assertThat(Json.amount(preview, "roundingThreshold")).isNotNull();
        assertThat(preview.get("receivable"))
                .as("the form renders `receivable` by that name")
                .isNotNull();

        ResponseEntity<String> refused = owner.post("/api/sales-invoices", body);
        assertThat(refused.getStatusCode())
                .as("recording without acceptance must be refused, or the disabled button is "
                        + "mirroring a rule that does not exist")
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        String accepted = body.replace("\"lines\":",
                "\"roundingAcceptedBy\":\"Kostas\",\"roundingNote\":\"Agreed at the counter\","
                        + "\"lines\":");
        JsonNode recorded = Json.ok(owner.post("/api/sales-invoices", accepted), "the accepted sale");
        assertThat(recorded.get("roundingNeededReview").asBoolean()).isTrue();
        assertThat(Json.text(recorded, "roundingAcceptedBy")).isEqualTo("Kostas");
        assertThat(Json.text(recorded, "roundingAcceptedAt"))
                .as("stamped by the server, never by the form")
                .isNotNull();
    }

    /**
     * ⚠️ The negative half, and the reason the control is conditional rather than always visible.
     *
     * <p>Below the threshold {@code roundingAcceptedBy} is <strong>silently dropped</strong>. A form
     * that always showed the field would collect a name that goes nowhere, and nothing would say so.
     */
    @Test
    @DisplayName("under the threshold, an acceptance name is silently dropped — which is why the control hides")
    void anAcceptanceUnderTheThresholdIsDropped() {
        // 20.00 computed against a stated 20.01 — one cent, inside the 0.03 threshold.
        String body = saleBody(number("SMALL"), "2.000000", "20.01")
                .replace("\"lines\":", "\"roundingAcceptedBy\":\"Kostas\",\"lines\":");

        JsonNode preview = Json.ok(owner.post("/api/sales-invoices/preview", body), "the preview");
        assertThat(preview.get("roundingNeedsAcceptance").asBoolean())
                .as("a difference inside the threshold posts automatically")
                .isFalse();

        JsonNode recorded = Json.ok(owner.post("/api/sales-invoices", body), "the small-difference sale");
        assertThat(recorded.get("roundingNeededReview").asBoolean()).isFalse();
        assertThat(Json.text(recorded, "roundingAcceptedBy"))
                .as("supplied and discarded — the screen must not offer the field here")
                .isNull();
    }

    // -------------------------------------------------------------------------------------------
    // What A.2 bought, and what the list screens send
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("an omitted documentNumber is refused NAMING the field, on both document routes")
    void anOmittedDocumentNumberIsNamed() {
        ResponseEntity<String> sale = owner.post("/api/sales-invoices", """
                {"customerId":%d,"seriesId":%d,"paymentMethodId":%d,
                 "invoiceDate":"2026-07-20","lines":[{"lineType":"PRODUCT","productId":%d,
                 "quantity":"1.000000","unitPrice":{"amount":"10.000000","currency":"EUR"}}]}
                """.formatted(customerId, seriesId, paymentMethodId, productId));

        assertThat(sale.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sale.getBody())
                .as("before A.2 this was an inline isBlank() check labelled 'Malformed request "
                        + "body' for a body that parsed, and the field was undeclared in the spec")
                .contains("documentNumber");

        JsonNode recorded = recordSale(number("DN-SALE"));
        ResponseEntity<String> note = owner.post("/api/credit-notes", """
                {"salesInvoiceId":%d,"creditNoteDate":"2026-07-25",
                 "lines":[{"salesInvoiceLineId":%d,"quantity":"1.000000",
                           "unitPrice":{"amount":"10.000000","currency":"EUR"},
                           "stockReturned":false}]}
                """.formatted(recorded.get("id").asLong(), Json.lineIds(recorded).getFirst()));

        assertThat(note.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(note.getBody()).contains("documentNumber");
    }

    /**
     * ⚠️ The parameters the list screens send, checked against the server rather than against the
     * generated client.
     *
     * <p><strong>An unknown query parameter is silently ignored</strong> — measured in F5's Phase 0,
     * where {@code GET /api/sales-invoices?search=…} answered {@code 200} with the full list before
     * the parameter existed. So a screen sending {@code searchTerm=} would look exactly like one
     * that filtered, and nothing anywhere would report it. The only defence is asking the server
     * whether the parameter it is given actually narrows the answer.
     */
    @Test
    @DisplayName("the list parameters the screens send are the ones the server acts on")
    void theListScreensParametersAreReal() {
        String documentNumber = number("SEARCHABLE");
        recordSale(documentNumber);

        // No range at all — the reason both list screens open on a default one.
        assertThat(owner.get("/api/sales-invoices").getStatusCode())
                .as("the invoice list cannot open unfiltered, which is why C.1 had to CHOOSE a range")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(owner.get("/api/credit-notes").getStatusCode())
                .as("the note list reaches the same requireRange, so the same default is a "
                        + "precondition there too")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        String range = "from=2026-01-01&to=2026-12-31";
        List<Long> unfiltered =
                Json.idsIn(owner.get("/api/sales-invoices?" + range), "the ranged list");
        assertThat(unfiltered).as("the sale just recorded is in the range").isNotEmpty();

        // ⭐ The search actually narrows. This is what a mock structurally cannot answer.
        List<Long> matched = Json.idsIn(
                owner.get("/api/sales-invoices?" + range + "&search=" + documentNumber),
                "the searched list");
        assertThat(matched).hasSize(1);

        List<Long> unmatched = Json.idsIn(
                owner.get("/api/sales-invoices?" + range + "&search=NOTHINGMATCHESTHIS"),
                "the list for a search that matches nothing");
        assertThat(unmatched)
                .as("if this came back full, `search=` would be being ignored and the screen would "
                        + "look like it had filtered")
                .isEmpty();

        // The three sort keys C.2 put on the columns, taken from the generated enum, and the paging
        // block the table reads. A key the server does not know is refused rather than ignored,
        // because it binds to an enum.
        JsonNode paged = Json.ok(
                owner.get("/api/sales-invoices?" + range + "&page=0&size=1&sort=DOCUMENT_NUMBER"),
                "the paged list");
        assertThat(paged.get("page"))
                .as("DataTable switches to server paging on the presence of this block")
                .isNotNull();
        assertThat(paged.get("items").size()).isLessThanOrEqualTo(1);
        assertThat(owner.get("/api/sales-invoices?" + range + "&sort=CUSTOMER_NAME").getStatusCode())
                .as("B.3 decided CUSTOMER_NAME is NOT a sort key; the columns file says so and the "
                        + "server has to agree, or the screen is documenting a fiction")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
