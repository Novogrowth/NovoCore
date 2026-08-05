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
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Reversing a document and re-recording it under the same number — <strong>the one combination no
 * test in this repository paired</strong> before F5 (A.3, 2026-08-05).
 *
 * <h2>Why this pairing and not another</h2>
 *
 * <p>{@code SalesInvoiceIT} tests reversal. It tests duplicate numbers. It never does both to the
 * same document, and neither does anything else — so a rule stated in three places and enforced by
 * none went unnoticed from step 9 until the first step that could reach it.
 *
 * <p><strong>Nothing could have reached it before F5.</strong> Recording a sales invoice from a
 * screen is what F5 added; until then every {@code sales_invoice} row in the development database
 * had been written by a fixture that never reversed anything.
 *
 * <h2>⚠️ What this asserts, and what it deliberately leaves open</h2>
 *
 * <p>Three things enforce document-number uniqueness: a service pre-check, a table trigger, and a
 * partial unique index on {@code … WHERE reversal_of_id IS NULL}. The first two <em>release</em> a
 * reversed document's number; the index does not, because its predicate excludes the
 * <em>reversing</em> row and not the reversed one.
 *
 * <p>⚠️ <strong>THE DIRECTION IS SETTLED AND THE FIX IS NOT BUILT (owner, 2026-08-05).</strong> The
 * number <strong>is</strong> released, so <strong>the partial unique index is the enforcement that is
 * wrong</strong>; the trigger and the service message are right. The reasoning is not derivable from
 * this codebase and is recorded in {@code CLAUDE.md} §2b and against roadmap row <strong>N1</strong>:
 * a reversal in Novocore undoes <em>Novocore's own mis-recording</em> of a document Go issued, not a
 * cancellation of an issued document — Greek law has no such thing, and an error in an issued
 * document is corrected by a credit invoice. Go's document still exists under its number and must be
 * recorded again correctly.
 *
 * <p><strong>It is N1's to build, not F5's</strong>, because a partial index cannot express
 * <em>"not reversed"</em> by itself and the replacement must keep the concurrency guarantee the index
 * currently provides — a trigger's {@code NOT EXISTS} does not, which was measured rather than
 * argued.
 *
 * <p>So these tests assert <strong>the part that is settled regardless of that answer</strong>: the
 * caller is told, in a body they can read, that the document was refused. <strong>Never a 5xx.</strong>
 * Before A.1b there was no {@link org.springframework.dao.DataIntegrityViolationException} handler
 * at all and both routes answered {@code 500} in Boot's legacy shape — no {@code detail}, not RFC
 * 7807, indistinguishable from the server having crashed.
 *
 * <p>⚠️ <strong>WHEN N1 LANDS, THESE GO RED, AND THAT IS THE POINT.</strong> The re-record becomes
 * {@code 201}, so {@code refusedReadably} will fail on a success — loudly, in the safe direction,
 * naming the exact place to update. <strong>Left exactly as it is until then</strong>: they assert
 * the half that holds either way, which is what makes them useful while the fix is unbuilt.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + DocumentNumberReuseIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + DocumentNumberReuseIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class DocumentNumberReuseIT {

    static final String OWNER_USERNAME = "reuse.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    @Autowired private TestRestTemplate rest;

    private ApiClient.Session owner;

    private static long customerId;
    private static long seriesId;
    private static long otherSeriesId;

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

        // ⚠️ A NON-STOCK-MOVING type, deliberately. A stock-moving one creates a consumption whose
        // whole quantity is an unbacked shortfall on an empty database, and reversing THAT is refused
        // for an unrelated reason — which would make this class measure something else entirely.
        long typeId = Json.createdId(owner.post("/api/sales-document-types", """
                {"description":"Reuse non-moving","affectsStock":false,"transfersStock":false,
                 "requiresMydataTransmission":true,"sortCode":700}
                """), "the document type");
        seriesId = Json.createdId(owner.post("/api/sales-document-series", """
                {"abbreviation":"REUSE-A","description":"Reuse A","documentTypeId":%d,
                 "channel":"ECOMMERCE","getsMark":false,"sortCode":701}
                """.formatted(typeId)), "series A");
        otherSeriesId = Json.createdId(owner.post("/api/sales-document-series", """
                {"abbreviation":"REUSE-B","description":"Reuse B","documentTypeId":%d,
                 "channel":"STORE_AND_PHONE","getsMark":false,"sortCode":702}
                """.formatted(typeId)), "series B");
    }

    private String sale(long series, String number) {
        return """
                {"customerId":%d,"seriesId":%d,"settlementMethod":"ON_ACCOUNT",
                 "documentNumber":"%s","invoiceDate":"2026-07-20",
                 "lines":[{"lineType":"CHARGE","chargeTypeId":1,"quantity":"1.000000",
                           "unitPrice":{"amount":"10.000000","currency":"EUR"}}]}
                """.formatted(customerId, series, number);
    }

    private String note(long invoiceId, long lineId, String number) {
        return """
                {"salesInvoiceId":%d,"documentNumber":"%s","creditNoteDate":"2026-07-22",
                 "lines":[{"salesInvoiceLineId":%d,"quantity":"1.000000",
                           "unitPrice":{"amount":"5.000000","currency":"EUR"},
                           "stockReturned":false}]}
                """.formatted(invoiceId, number, lineId);
    }

    /** The assertion that holds whichever way A.1c goes. */
    private void refusedReadably(ResponseEntity<String> response, String what) {
        assertThat(response.getStatusCode().is5xxServerError())
                .as("%s must not answer a 5xx. Before A.1b this was a 500 in Boot's legacy body, "
                        + "which tells a caller nothing and looks like a crash. Body: %s",
                        what, response.getBody())
                .isFalse();
        assertThat(response.getBody())
                .as("%s must carry an RFC 7807 detail a caller can read", what)
                .contains("\"detail\"");
    }

    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("re-recording a reversed sales invoice's number is refused readably, never a 5xx")
    void reversedSalesInvoiceNumber() {
        ResponseEntity<String> original = owner.post("/api/sales-invoices", sale(seriesId, "RE-1"));
        assertThat(original.getStatusCode().value()).isEqualTo(201);
        long id = Json.read(original).get("id").asLong();

        assertThat(owner.post("/api/sales-invoices/" + id + "/reversal",
                        "{\"reversalDate\":\"2026-07-21\",\"reason\":\"recorded in error\"}")
                .getStatusCode().value())
                .as("a non-stock-moving invoice reverses cleanly")
                .isEqualTo(201);

        refusedReadably(owner.post("/api/sales-invoices", sale(seriesId, "RE-1")),
                "re-recording a reversed sales invoice number");
    }

    @Test
    @DisplayName("re-recording a reversed credit note's number is refused readably, never a 5xx")
    void reversedCreditNoteNumber() {
        ResponseEntity<String> invoice = owner.post("/api/sales-invoices", sale(seriesId, "RE-CN"));
        JsonNode body = Json.read(invoice);
        long invoiceId = body.get("id").asLong();
        long lineId = body.get("lines").get(0).get("id").asLong();

        ResponseEntity<String> created =
                owner.post("/api/credit-notes", note(invoiceId, lineId, "RE-CN-1"));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long noteId = Json.read(created).get("id").asLong();

        assertThat(owner.post("/api/credit-notes/" + noteId + "/reversal",
                        "{\"reversalDate\":\"2026-07-23\",\"reason\":\"x\"}")
                .getStatusCode().value())
                .isEqualTo(201);

        refusedReadably(owner.post("/api/credit-notes", note(invoiceId, lineId, "RE-CN-1")),
                "re-recording a reversed credit note number");
    }

    /**
     * The positive control. Without it, the two tests above would pass just as happily against a
     * server that refused <em>every</em> document — "refused readably" is only meaningful next to
     * something that is accepted.
     */
    @Test
    @DisplayName("the same number in two different series is accepted — R1a's per-series key, live")
    void perSeriesKeyIsReal() {
        assertThat(owner.post("/api/sales-invoices", sale(seriesId, "SHARED")).getStatusCode()
                .value())
                .isEqualTo(201);
        assertThat(owner.post("/api/sales-invoices", sale(otherSeriesId, "SHARED"))
                .getStatusCode().value())
                .as("ΑΛΠ-1 and ΤΠΔΑ-1 are two documents that both legitimately carry the number 1. "
                        + "This is the first thing in the project able to exercise that: until F5 "
                        + "every sales_invoice row had a null series, so the whole table was one "
                        + "group and V32's per-series key was enforced by nothing")
                .isEqualTo(201);

        ResponseEntity<String> duplicate =
                owner.post("/api/sales-invoices", sale(seriesId, "SHARED"));
        assertThat(duplicate.getStatusCode().value())
                .as("and the same number twice in ONE series is still refused")
                .isEqualTo(422);
        assertThat(duplicate.getBody()).contains("already been recorded in series");
    }
}
