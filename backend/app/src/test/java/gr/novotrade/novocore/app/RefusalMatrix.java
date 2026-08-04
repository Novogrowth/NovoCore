package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptMatch;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceiptLine;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoice;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoiceLine;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoice;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoiceLine;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import gr.novotrade.novocore.core.api.settlement.NewAllocation;
import gr.novotrade.novocore.core.api.settlement.NewSettlement;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * <strong>Step 15's refusal matrix: what the API does when it is asked for something wrong.</strong>
 *
 * <p>The trading quarter proves the happy path produces correct books. This proves the other half,
 * which nothing else in the repository tests at all: <em>a refused request is refused with the right
 * status and a body that says why.</em> A frontend collides with this surface on its first day —
 * every form a user fills in wrong arrives here — and until now the only evidence any of it worked
 * was that the service layer threw the right exception, which says nothing about what reached the
 * caller. Defect 5 of this step is exactly that gap made concrete: seventeen controller messages
 * across nine controllers were being thrown away, and every service-layer test still passed.
 *
 * <h2>Three claims per entry, and the third is the one that matters</h2>
 *
 * <ol>
 *   <li><strong>The status is right.</strong> 400 for a request the caller can fix by correcting its
 *       JSON, 404 for a thing that is not there, 405 for a verb the route does not serve, 422 for a
 *       well-formed request a business rule refused. The distinction between 400 and 422 is not
 *       decoration: a client that cannot tell them apart cannot tell "fix your payload" from "this
 *       is not allowed".
 *   <li><strong>The body is RFC 7807, uniformly.</strong> {@code application/problem+json}, with a
 *       {@code status} field agreeing with the HTTP status and a non-blank {@code detail}. Asserted
 *       on <em>every</em> entry including the ones Spring itself answers, because defect 6 of this
 *       step was precisely that Spring's own refusals returned a different shape — Boot's legacy
 *       {@code {timestamp,status,error,path}}, no {@code detail} at all — so a client could not read
 *       errors uniformly. Nothing in the repository asserted the media type before this.
 *   <li><strong>The detail names the reason, or deliberately does not.</strong> This is the claim
 *       with teeth. {@code WebExceptionHandler} draws a line down the middle of the surface:
 *       validation refusals return the core's own message because an operator who cannot see why a
 *       document was refused cannot fix it, while permission refusals, 404s and bare programming
 *       errors say nothing, because their messages describe the system rather than the request. Both
 *       halves are asserted here — {@code mustSay} for the first, {@code mustNotSay} for the second —
 *       since a policy that is only tested in the generous direction quietly becomes a leak.
 * </ol>
 *
 * <h2>What is deliberately not here</h2>
 *
 * <p><strong>403.</strong> Permission refusals are swept across all 133 routes by
 * {@code PermissionSweepIT}, which is where the claim belongs: one entry here would prove the mapping
 * and say nothing about the other hundred and thirty-two routes.
 *
 * <p><strong>409 and 410.</strong> {@code JournalEntryNotAmendableException} and
 * {@code EmailAttachmentUnavailableException} are mapped, and neither is reachable from this
 * narrative — the journal has no amendment route and no attachment has been deleted. Stated rather
 * than quietly omitted, because an untested mapping is a real if small gap and the reader should know
 * which two they are.
 */
final class RefusalMatrix {

    private final ApiClient.Session api;
    private final TradingQuarter quarter;

    RefusalMatrix(ApiClient.Session api, TradingQuarter quarter) {
        this.api = api;
        this.quarter = quarter;
    }

    /**
     * One deliberate mistake, and what the caller must be told about it.
     *
     * @param mustSay    fragments the {@code detail} must contain. Checked against {@code detail}
     *                   only and never the whole body, so a fragment cannot pass by appearing in the
     *                   echoed request URI.
     * @param mustNotSay fragments the {@code detail} must <em>not</em> contain — the withholding half
     *                   of {@code WebExceptionHandler}'s policy, which nothing asserted before.
     */
    record Refusal(
            String what,
            HttpStatus expected,
            List<String> mustSay,
            List<String> mustNotSay,
            Supplier<ResponseEntity<String>> send) {

        /** Applies all three claims. See the class javadoc for why each one is here. */
        void verify() {
            ResponseEntity<String> response = send.get();

            assertThat(response.getStatusCode())
                    .as("%s — wrong status. Body: %s", what, response.getBody())
                    .isEqualTo(expected);

            MediaType contentType = response.getHeaders().getContentType();
            assertThat(contentType)
                    .as("%s — a refusal must carry a content type", what)
                    .isNotNull();
            assertThat(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType))
                    .as("%s — refusals must all be RFC 7807; this one was %s. A client that has to "
                            + "branch on which advice answered cannot read errors uniformly, which "
                            + "is the defect spring.mvc.problemdetails was turned on to fix.",
                            what, contentType)
                    .isTrue();

            JsonNode body = Json.read(response);
            assertThat(body.get("status"))
                    .as("%s — no 'status' in the problem body: %s", what, body)
                    .isNotNull();
            assertThat(body.get("status").asInt())
                    .as("%s — the body's status disagrees with the HTTP status", what)
                    .isEqualTo(expected.value());

            JsonNode detailNode = body.get("detail");
            assertThat(detailNode)
                    .as("%s — no 'detail' in the problem body: %s", what, body)
                    .isNotNull();
            String detail = detailNode.asString();
            assertThat(detail).as("%s — an empty detail is no better than none", what).isNotBlank();

            // Not every refusal has something it must say — the framework's own 404 for a path
            // nobody serves is here for the shape of its body, not for its wording.
            if (!mustSay.isEmpty()) {
                assertThat(detail)
                        .as("%s — the caller was refused without being told why", what)
                        .contains(mustSay.toArray(String[]::new));
            }
            for (String forbidden : mustNotSay) {
                assertThat(detail)
                        .as("%s — the refusal disclosed '%s', which it is not supposed to",
                                what, forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }

    /**
     * The matrix itself. Each entry sends its request lazily, so one failure reports as one failing
     * test rather than aborting the rest — the same reason the invariant sweep is a
     * {@code @TestFactory}.
     */
    List<Refusal> all() {
        return List.of(
                // -------------------------------------------------------------------------------
                // 400 — the request itself is wrong, and the message says how
                // -------------------------------------------------------------------------------

                // The single most load-bearing message in the REST surface, and the one defect 6's
                // fix silently broke: turning problemdetails on registered a second advice over
                // HttpMessageNotReadableException and Boot's won, replacing this with "Failed to
                // read request". Asserted here inside a nested document line, where a client is
                // most likely to get it wrong and least likely to guess the rule from a bare 400.
                new Refusal("an amount sent as a JSON number, nested in an invoice line",
                        HttpStatus.BAD_REQUEST,
                        List.of("must be a JSON string", "not a number"),
                        List.of(),
                        () -> api.post("/api/sales-invoices", withAmountAsANumber())),

                new Refusal("a quantity sent as a JSON number",
                        HttpStatus.BAD_REQUEST,
                        List.of("a quantity must be a JSON string"),
                        List.of(),
                        () -> api.post("/api/sales-invoices", withQuantityAsANumber())),

                new Refusal("a body that is not JSON at all",
                        HttpStatus.BAD_REQUEST,
                        List.of("Malformed request body"),
                        List.of(),
                        () -> api.post("/api/customers", "{\"name\": ")),

                // -------------------------------------------------------------------------------
                // 404 — and the deliberate silence about what was asked for
                // -------------------------------------------------------------------------------

                new Refusal("a sales invoice id that names nothing",
                        HttpStatus.NOT_FOUND,
                        List.of("Not found."),
                        // The id is in the caller's own request; echoing it back is harmless. What
                        // must not happen is the core's message, which says what kind of record was
                        // looked for and would let a caller probing ids map the ledger.
                        List.of("Sales invoice", "sales invoice"),
                        () -> api.get("/api/sales-invoices/999999999")),

                new Refusal("a path nobody serves",
                        HttpStatus.NOT_FOUND,
                        List.of(),
                        List.of(),
                        () -> api.get("/api/no-such-collection")),

                // -------------------------------------------------------------------------------
                // 405 — Spring's own refusal, which must look like all the others
                // -------------------------------------------------------------------------------

                new Refusal("a verb the route does not serve",
                        HttpStatus.METHOD_NOT_ALLOWED,
                        List.of("not supported"),
                        List.of(),
                        () -> api.delete("/api/products/" + quarter.id("product:beans"))),

                // -------------------------------------------------------------------------------
                // 422 — well-formed, and a business rule refuses it. These must explain themselves.
                // -------------------------------------------------------------------------------

                // Proven during the narrative, which tried to bank €700 in cash and was told not to.
                // Brief §6 / N. 5301/2026: a hard block with no override anywhere in the surface.
                new Refusal("a cash receipt at the legal cash limit",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("legal cash limit", "5301/2026"),
                        List.of(),
                        () -> api.post("/api/settlements", NewSettlement.receiptFrom(
                                quarter.id("customer:wholesale"), quarter.id("account:CASH"),
                                TradingQuarter.MARCH_LAST, Money.ofEur("700.00"),
                                List.of(NewAllocation.againstSalesInvoice(
                                        quarter.id("sale:wholesale-january"),
                                        Money.ofEur("600.00")))).leavingCredit())),

                // Also proven during the narrative. The sub-ledger is per party, so allowing this
                // would leave two customers' balances both wrong and both plausible.
                new Refusal("one customer's credit settling another customer's invoice",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("cannot settle another's invoice"),
                        List.of(),
                        () -> api.post(
                                "/api/customer-credits/" + quarter.id("credit:wholesale")
                                        + "/allocations",
                                new TradingQuarter.TargetedAllocationBody(
                                        quarter.id("sale:eu"), Money.ofEur("10.00")))),

                // Uniqueness is per supplier and case-insensitive, enforced by trigger. It matters
                // because the myDATA import will retry, and a silent duplicate is a payable recorded
                // twice.
                new Refusal("a supplier invoice number that supplier has already sent",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("has already sent invoice"),
                        List.of(),
                        () -> api.post("/api/purchase-invoices", new NewPurchaseInvoice(
                                quarter.id("supplier:roaster"), "test-pi-2026-001",
                                TradingQuarter.MARCH_LAST, "The same number, in lower case",
                                List.of(NewPurchaseInvoiceLine.inventory(
                                        quarter.id("product:beans"), Quantity.of(1L),
                                        UnitCost.ofEur("9.000000"), quarter.id("vat:1131")))))),

                // Reverse charge is never inferred from the supplier — it is stated and then checked
                // to agree. Both directions are refused, and both are here because they are
                // different mistakes with different consequences: one reclaims tax nobody paid, the
                // other declares output VAT on a purchase.
                new Refusal("reverse charge claimed on a domestic supplier",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("cannot be reverse-charged"),
                        List.of(),
                        () -> api.post("/api/purchase-invoices", new NewPurchaseInvoice(
                                quarter.id("supplier:roaster"), "TEST-PI-REFUSED-001",
                                TradingQuarter.MARCH_LAST, "Domestic supplier, reverse charged",
                                List.of(NewPurchaseInvoiceLine.inventory(
                                                quarter.id("product:beans"), Quantity.of(1L),
                                                UnitCost.ofEur("9.000000"), quarter.id("vat:1131"))
                                        .reverseCharged())))),

                new Refusal("reverse charge omitted on an intra-EU supplier",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("must be reverse-charged"),
                        List.of(),
                        () -> api.post("/api/purchase-invoices", new NewPurchaseInvoice(
                                quarter.id("supplier:eu"), "TEST-PI-REFUSED-002",
                                TradingQuarter.MARCH_LAST, "Intra-EU, not reverse charged",
                                List.of(NewPurchaseInvoiceLine.inventory(
                                        quarter.id("product:machine"), Quantity.of(1L),
                                        UnitCost.ofEur("900.000000"), quarter.id("vat:1410")))))),

                // The narrative learned this one from the API rather than the other way round.
                new Refusal("changing the unit of measure of a product that has lots",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("it is a different quantity"),
                        List.of(),
                        () -> api.patchBody(
                                "/api/products/" + quarter.id("product:beans") + "/unit-of-measure",
                                new TradingQuarter.UnitOfMeasureBody(quarter.id("uom:PIECE")))),

                // A bundle is typed GOODS and holds no stock of its own, so this is the one shape
                // where "is it stocked?" cannot be answered from the type alone.
                new Refusal("a delivery against a bundle",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("bundle"),
                        List.of(),
                        () -> api.post("/api/goods-receipts", new NewGoodsReceipt(
                                quarter.id("supplier:roaster"), "TEST-GR-REFUSED-001",
                                TradingQuarter.MARCH_LAST, "Receiving a bundle",
                                List.of(NewGoodsReceiptLine.pooled(quarter.id("product:kit"),
                                        Quantity.of(1L), UnitCost.ofEur("150.000000")))))),

                new Refusal("a bare quantity delivered against serial-tracked stock",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("serial-tracked", "one serial number per unit"),
                        List.of(),
                        () -> api.post("/api/goods-receipts", new NewGoodsReceipt(
                                quarter.id("supplier:eu"), "TEST-GR-REFUSED-002",
                                TradingQuarter.MARCH_LAST, "Machines without serial numbers",
                                List.of(NewGoodsReceiptLine.pooled(quarter.id("product:machine"),
                                        Quantity.of(2L), UnitCost.ofEur("900.000000")))))),

                // ADR 0006: a document is corrected by a reversal, and a second reversal would
                // credit the supplier twice with both halves looking correct. The quarter already
                // reversed this invoice, so this is the genuine second attempt.
                new Refusal("reversing a purchase invoice that is already reversed",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("has already been reversed"),
                        List.of(),
                        () -> api.post(
                                "/api/purchase-invoices/" + quarter.id("purchase:stray")
                                        + "/reversal",
                                new TradingQuarter.ReversalBody(
                                        TradingQuarter.MARCH_LAST, "Twice, by mistake"))),

                // GR/IR is expected to clear, so receiving more than was invoiced would drive it
                // below zero — a residual that looks like the ordinary timing gap and is not one.
                new Refusal("receiving more than the invoice line charges for",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("has already been delivered", "below zero"),
                        List.of(),
                        () -> api.post("/api/goods-receipts", new NewGoodsReceipt(
                                quarter.id("supplier:roaster"), "TEST-GR-REFUSED-003",
                                TradingQuarter.MARCH_LAST, "More beans than were invoiced",
                                List.of(NewGoodsReceiptLine.pooledAgainst(
                                        quarter.id("product:beans"), Quantity.of(5L),
                                        quarter.id("purchase-line:invoice-first")))))),

                // One supplier's delivery cannot clear another's payable, for the same reason one
                // customer's credit cannot settle another's invoice: the sub-ledger is per party.
                new Refusal("a delivery matched to a different supplier's invoice line",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("belongs to a different supplier"),
                        List.of(),
                        () -> api.post("/api/goods-receipts", new NewGoodsReceipt(
                                quarter.id("supplier:eu"), "TEST-GR-REFUSED-004",
                                TradingQuarter.MARCH_LAST, "The wrong supplier's invoice",
                                List.of(NewGoodsReceiptLine.pooledAgainst(
                                        quarter.id("product:beans"), Quantity.of(1L),
                                        quarter.id("purchase-line:invoice-first")))))),

                // An expense line buys no stock, so there is nothing to deliver against it. The
                // freight line is the one in the narrative that is genuinely an expense.
                new Refusal("a delivery against an expense line",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        List.of("expense line"),
                        List.of(),
                        () -> api.post("/api/goods-receipts", new NewGoodsReceipt(
                                quarter.id("supplier:carrier"), "TEST-GR-REFUSED-005",
                                TradingQuarter.MARCH_LAST, "Delivering freight",
                                List.of(NewGoodsReceiptLine.pooledAgainst(
                                        quarter.id("product:beans"), Quantity.of(1L),
                                        quarter.id("purchase-line:freight")))))));
    }

    // ===================================================================================
    // The two malformed bodies, built by breaking a valid one
    // ===================================================================================

    /**
     * A real request with exactly one thing wrong with it.
     *
     * <p>Built by serialising a valid document and then editing the one token under test, rather
     * than by hand-writing JSON. Hand-written JSON for a document this shape would fail for whichever
     * field was mistyped first, and the test would pass while proving nothing about the money rule.
     * The assertion that the edit actually changed something is what keeps that guarantee: a
     * substitution that silently matched nothing would leave a perfectly valid request behind, and a
     * 201 is not a refusal.
     */
    private String withAmountAsANumber() {
        String valid = Json.write(NewSalesInvoice.of(
                quarter.id("customer:cafe"), quarter.id("series:store"),
                SettlementMethod.ON_ACCOUNT, "TEST-SI-REFUSED-001", TradingQuarter.MARCH_LAST,
                List.of(NewSalesInvoiceLine.charge(
                        quarter.id("charge:Delivery"), Money.ofEur("7.77")))));
        // "7.770000", not "7.77": an invoice line's unit price is a UnitCost, which carries six
        // decimals. Worth knowing, and worth the assertion inside breaking() that would have caught
        // it silently otherwise — a token that matches nothing leaves a valid request behind.
        return breaking(valid, "\"7.770000\"", "7.770000");
    }

    private String withQuantityAsANumber() {
        String valid = Json.write(NewSalesInvoice.of(
                quarter.id("customer:cafe"), quarter.id("series:store"),
                SettlementMethod.ON_ACCOUNT, "TEST-SI-REFUSED-002", TradingQuarter.MARCH_LAST,
                List.of(NewSalesInvoiceLine.product(quarter.id("product:beans"),
                        Quantity.of(7L), UnitCost.ofEur("18.000000")))));
        return breaking(valid, "\"7.000000\"", "7.000000");
    }

    private static String breaking(String json, String quoted, String bare) {
        assertThat(json)
                .as("the token %s is not in the serialised request, so replacing it would leave a "
                        + "perfectly valid body and the refusal under test would never happen", quoted)
                .contains(quoted);
        return json.replace(quoted, bare);
    }
}
