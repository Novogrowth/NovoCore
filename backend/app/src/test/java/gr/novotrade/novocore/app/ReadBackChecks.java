package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * <strong>Step 15: what was written comes back, and a date range means what it says.</strong>
 *
 * <p>Two questions the trading quarter does not ask. It <em>creates</em> fifty documents and then
 * checks that the <em>ledger</em> behind them is sound, which is a strong claim about the postings and
 * says nothing about the documents themselves: an invoice could come back with the wrong date, or a
 * list could quietly omit the document on its own boundary, and every one of the twelve invariants
 * would still pass. Both are things a frontend shows a user directly.
 *
 * <h2>Read-back</h2>
 *
 * <p>Each document is re-fetched by id and compared against the literals the narrative sent. The point
 * is not that the database can store a date — it is that the value survives <em>both</em> crossings of
 * the boundary, serialised out having been deserialised in. Step 15 has already found two defects of
 * exactly that shape, where a value was correct in the service and wrong on the wire.
 *
 * <h2>The date boundaries, which are the part with no coverage at all</h2>
 *
 * <p>Eleven listings take {@code ?from=} and {@code ?to=}, and <strong>whether either bound is
 * inclusive is asserted nowhere in this repository.</strong> The quarter's own reads all pass
 * {@code JANUARY_FIRST} to {@code MARCH_LAST}, which spans every document and therefore cannot tell an
 * inclusive bound from an exclusive one. Off-by-one on a date range is the classic silent defect: a
 * month-end report that drops the last day's sales is wrong by an amount nobody notices, because the
 * total is plausible and the missing document is in nobody's field of view.
 *
 * <p>So each listing is asked three questions <em>about a document on the boundary itself</em>:
 *
 * <ol>
 *   <li>{@code from = to = the document's own date} — it must be there. A single-day range is also
 *       the narrowest possible one, so this proves both bounds inclusive in one request.
 *   <li>{@code from = the day after} — it must be gone. Proves {@code from} is a real lower bound and
 *       not decoration.
 *   <li>{@code to = the day before} — it must be gone. Same for the upper bound.
 * </ol>
 *
 * <p><strong>The anchor date is read from the response, not assumed.</strong> Each listing is fetched
 * over the whole quarter first and its first item supplies both the id and the date to probe with.
 * That keeps the check honest if the narrative's dates change, and — more to the point — it means the
 * date being tested is the one the API itself says the document has.
 */
final class ReadBackChecks {

    private final ApiClient.Session api;
    private final TradingQuarter quarter;

    ReadBackChecks(ApiClient.Session api, TradingQuarter quarter) {
        this.api = api;
        this.quarter = quarter;
    }

    // ===================================================================================
    // Read-back
    // ===================================================================================

    /** One document, and what it must still say when it is read back. */
    record ReadBack(String what, Runnable check) {
    }

    List<ReadBack> documents() {
        return List.of(
                readBack("the invoice-first purchase", () -> {
                    JsonNode it = get("/api/purchase-invoices/" + quarter.id("purchase:invoice-first"));
                    assertThat(Json.text(it, "supplierInvoiceNumber")).isEqualTo("TEST-PI-2026-001");
                    assertThat(Json.text(it, "invoiceDate"))
                            .isEqualTo(TradingQuarter.JANUARY_FIRST.toString());
                    assertThat(it.get("supplierId").asLong()).isEqualTo(quarter.id("supplier:roaster"));
                    // 60 at 9.00 — the one figure on this document that is arithmetic rather than
                    // storage, and the one a rounding or serialisation defect would move.
                    assertThat(Json.amount(it, "netTotal")).isEqualTo("540.00");
                    assertThat(lines(it)).hasSize(1);
                }),

                readBack("the goods-first delivery", () -> {
                    JsonNode it = get("/api/goods-receipts/" + quarter.id("receipt:goods-first"));
                    assertThat(Json.text(it, "deliveryNoteNumber")).isEqualTo("TEST-GR-2026-002");
                    assertThat(Json.text(it, "receiptDate"))
                            .isEqualTo(TradingQuarter.JANUARY_MID.toString());
                    // 40×10 + 10×70 + 100×1 = 1200.00, across three lines and three products.
                    assertThat(Json.amount(it, "totalValue")).isEqualTo("1200.00");
                    assertThat(lines(it)).hasSize(3);
                }),

                readBack("the first wholesale sale", () -> {
                    JsonNode it = get("/api/sales-invoices/" + quarter.id("sale:wholesale-january"));
                    assertThat(Json.text(it, "documentNumber")).isEqualTo("TEST-SI-2026-0001");
                    assertThat(Json.text(it, "invoiceDate"))
                            .isEqualTo(TradingQuarter.JANUARY_LAST.toString());
                    assertThat(Json.text(it, "channel")).isEqualTo("ECOMMERCE");
                    assertThat(Json.text(it, "settlementMethod")).isEqualTo("ON_ACCOUNT");
                    // Three product lines plus the delivery charge. The charge line is the one a
                    // reader is most likely to drop, since it names no product.
                    assertThat(lines(it)).hasSize(4);
                    assertThat(Json.amount(it, "grossTotal"))
                            .as("net + VAT must add up on the wire, not only in the ledger")
                            .isEqualTo(sum(Json.amount(it, "netTotal"), Json.amount(it, "vatTotal")));
                }),

                readBack("the machine sale, by serial number", () -> {
                    JsonNode it = get("/api/sales-invoices/" + quarter.id("sale:machine"));
                    assertThat(Json.text(it, "documentNumber")).isEqualTo("TEST-SI-2026-0003");
                    assertThat(it.toString())
                            .as("a serialized line names the unit it sold, which is the whole reason "
                                    + "serial tracking exists")
                            .contains("TEST-SN-0001");
                }),

                readBack("the stock-returning credit note", () -> {
                    JsonNode it = get("/api/credit-notes/" + quarter.id("credit-note:stock"));
                    assertThat(Json.text(it, "documentNumber")).isEqualTo("TEST-CN-2026-0001");
                    assertThat(Json.text(it, "creditNoteDate"))
                            .isEqualTo(TradingQuarter.MARCH_FIRST.toString());
                    assertThat(it.get("salesInvoiceId").asLong())
                            .as("a credit note only exists against the invoice it corrects")
                            .isEqualTo(quarter.id("sale:wholesale-january"));
                }),

                readBack("the wholesaler's overpayment", () -> {
                    JsonNode it = get("/api/settlements/" + quarter.id("settlement:overpayment"));
                    assertThat(Json.amount(it, "amount")).isEqualTo("700.00");
                    assertThat(Json.text(it, "settlementDate"))
                            .isEqualTo(TradingQuarter.FEBRUARY_LAST.toString());
                    // INCOMING, not RECEIPT: the direction is the money's, and RECEIPT is the
                    // JournalSource it posts under. Two names for two things, deliberately.
                    assertThat(Json.text(it, "direction")).isEqualTo("INCOMING");
                    assertThat(it.get("customerCreditId"))
                            .as("Q16: the surplus is a standalone credit document, and the "
                                    + "settlement says which one")
                            .isNotNull();
                    assertThat(it.get("customerCreditId").isNull()).isFalse();
                }),

                readBack("the float moved to the POS account", () -> {
                    JsonNode it = get("/api/bank-transfers/" + quarter.id("transfer:float"));
                    assertThat(Json.amount(it, "amount")).isEqualTo("50.00");
                    assertThat(Json.text(it, "transferDate"))
                            .isEqualTo(TradingQuarter.MARCH_MID.toString());
                    assertThat(it.get("fromAccountId").asLong()).isEqualTo(quarter.id("account:CASH"));
                }),

                readBack("the freight allocation", () -> {
                    JsonNode it = get("/api/freight-allocations/" + quarter.id("freight:january"));
                    assertThat(Json.amount(it, "amount")).isEqualTo("120.00");
                    assertThat(Json.text(it, "allocationDate"))
                            .isEqualTo(TradingQuarter.JANUARY_LAST.toString());
                    // ADR 0010's split: the half riding on stock still held, and the half posted to
                    // Landed cost variance because those units have already gone. They must add up
                    // to what was allocated, which is the arithmetic the whole ADR is about.
                    assertThat(sum(Json.amount(it, "capitalised"), Json.amount(it, "variance")))
                            .as("capitalised + variance must be the amount allocated")
                            .isEqualTo("120.00");
                }),

                readBack("the damaged grinders written off", () -> {
                    JsonNode it = get("/api/inventory/write-offs/" + quarter.id("write-off:damage"));
                    assertThat(Json.text(it, "reason")).isEqualTo("DAMAGE");
                    assertThat(Json.text(it, "writeOffDate"))
                            .isEqualTo(TradingQuarter.MARCH_MID.toString());
                    assertThat(Json.text(it, "quantity"))
                            .as("a quantity is a string on the wire, at six decimals")
                            .isEqualTo("2.000000");
                    assertThat(Json.text(it, "note")).isEqualTo("Dropped in the stockroom");
                }),

                readBack("the asset, which carries no rate because none is known", () -> {
                    JsonNode it = get("/api/assets/" + quarter.id("asset:roaster"));
                    assertThat(Json.text(it, "code")).isEqualTo("TEST-ASSET-01");
                    assertThat(Json.text(it, "acquisitionDate"))
                            .isEqualTo(TradingQuarter.JANUARY_FIRST.toString());
                    // Q12: null means "the statutory rate is not known yet", and what must never
                    // happen is that it arrives as a zero somebody could depreciate by.
                    //
                    // It arrives as *nothing at all*, and that is worth asserting rather than
                    // working around: application.yml sets Jackson's default-property-inclusion to
                    // non_null, so across this whole API an unset field is absent from the body
                    // rather than present and null. A client must therefore read "missing" as "not
                    // set" — which is a contract, and this is the only test that states it.
                    assertThat(it.has("depreciationRatePercent"))
                            .as("an unknown depreciation rate is omitted, never sent as 0")
                            .isFalse();
                    assertThat(it.toString()).doesNotContain("depreciationRatePercent");
                }),

                readBack("the beans, after four separate corrections", () -> {
                    JsonNode it = get("/api/products/" + quarter.id("product:beans"));
                    // Every one of these was a separate PATCH in quarterEndCorrections, and this is
                    // the only place that checks they all landed on the same product rather than
                    // each being the last writer.
                    assertThat(Json.text(it, "name")).isEqualTo("House blend beans (250g)");
                    assertThat(Json.text(it, "ean")).isEqualTo("5201234567890");
                    assertThat(Json.amount(it, "sellingPrice")).isEqualTo("19.50");
                    assertThat(Json.text(it, "supplierSku")).isEqualTo("ROAST-BEANS-01");
                    assertThat(it.get("supplierId").asLong()).isEqualTo(quarter.id("supplier:roaster"));
                }));
    }

    // ===================================================================================
    // Date boundaries
    // ===================================================================================

    /**
     * One date-filtered listing, and the field its items date themselves by.
     *
     * @param path      the listing, with no query string
     * @param dateField the item field naming the date the filter works on. Named per listing rather
     *                  than guessed, because they genuinely differ — {@code invoiceDate},
     *                  {@code receiptDate}, {@code settlementDate} — and a single convention would
     *                  have to be invented for the test's convenience.
     */
    record DateFiltered(String path, String dateField) {
    }

    static List<DateFiltered> dateFilteredListings() {
        return List.of(
                new DateFiltered("/api/purchase-invoices", "invoiceDate"),
                new DateFiltered("/api/goods-receipts", "receiptDate"),
                new DateFiltered("/api/sales-invoices", "invoiceDate"),
                new DateFiltered("/api/credit-notes", "creditNoteDate"),
                new DateFiltered("/api/settlements", "settlementDate"),
                new DateFiltered("/api/bank-transfers", "transferDate"),
                new DateFiltered("/api/freight-allocations", "allocationDate"),
                new DateFiltered("/api/inventory/write-offs", "writeOffDate"),
                new DateFiltered("/api/inventory/consumptions", "consumptionDate"));
    }

    /**
     * Asks one listing the three boundary questions.
     *
     * <p>Fails rather than skips when the listing is empty over the whole quarter: an empty list
     * satisfies all three probes perfectly and would report a passing boundary check for a filter
     * nothing had exercised.
     */
    void assertBoundariesAreInclusive(DateFiltered listing) {
        List<JsonNode> everything = Json.items(
                api.get(range(listing.path(), TradingQuarter.JANUARY_FIRST, TradingQuarter.MARCH_LAST)),
                "everything in " + listing.path());
        assertThat(everything)
                .as("%s returned nothing across the whole quarter, so its boundaries cannot be "
                        + "tested — an empty list passes every probe below while proving nothing",
                        listing.path())
                .isNotEmpty();

        JsonNode anchor = everything.getFirst();
        long anchorId = anchor.get("id").asLong();
        String rawDate = Json.text(anchor, listing.dateField());
        assertThat(rawDate)
                .as("%s items carry no '%s'; the field name in dateFilteredListings is wrong, and a "
                        + "wrong one would silently make this whole check vacuous",
                        listing.path(), listing.dateField())
                .isNotNull();
        LocalDate on = LocalDate.parse(rawDate);

        assertThat(idsIn(listing.path(), on, on))
                .as("%s?from=%s&to=%s must contain document %d, which is dated exactly that day. "
                        + "Both bounds inclusive, proven by the narrowest range there is.",
                        listing.path(), on, on, anchorId)
                .contains(anchorId);

        assertThat(idsIn(listing.path(), on.plusDays(1), TradingQuarter.MARCH_LAST.plusYears(1)))
                .as("%s?from=%s must exclude document %d, dated the day before. A lower bound that "
                        + "lets it through is not a lower bound.",
                        listing.path(), on.plusDays(1), anchorId)
                .doesNotContain(anchorId);

        assertThat(idsIn(listing.path(), TradingQuarter.BEFORE_THE_QUARTER, on.minusDays(1)))
                .as("%s?to=%s must exclude document %d, dated the day after.",
                        listing.path(), on.minusDays(1), anchorId)
                .doesNotContain(anchorId);
    }

    // ===================================================================================
    // List membership under a filter
    // ===================================================================================

    /**
     * A filtered list must contain what belongs to it and nothing that does not.
     *
     * <p>The second half is the one worth writing. "Contains what I asked for" passes against a
     * listing that ignores its filter entirely and returns everything — which is a real defect, and
     * one that looks like working software until two parties' figures are compared.
     */
    void assertFiltersActuallyFilter() {
        long wholesale = quarter.id("customer:wholesale");
        long cafe = quarter.id("customer:cafe");

        List<Long> wholesaleSales = idsIn("/api/sales-invoices?customerId=" + wholesale);
        assertThat(wholesaleSales).contains(quarter.id("sale:wholesale-january"));
        assertThat(wholesaleSales)
                .as("a customer's sales list must not include another customer's")
                .doesNotContain(quarter.id("sale:bundle"), quarter.id("sale:machine"));

        assertThat(idsIn("/api/sales-invoices?customerId=" + cafe))
                .contains(quarter.id("sale:bundle"), quarter.id("sale:machine"))
                .doesNotContain(quarter.id("sale:wholesale-january"));

        assertThat(idsIn("/api/credit-notes?salesInvoiceId=" + quarter.id("sale:wholesale-january")))
                .contains(quarter.id("credit-note:stock"))
                .doesNotContain(quarter.id("credit-note:price"));

        assertThat(idsIn("/api/settlements?partyType=SUPPLIER&partyId=" + quarter.id("supplier:roaster")))
                .as("a supplier's settlements are payments; a customer's receipt is not one of them")
                .contains(quarter.id("settlement:roaster-part"))
                .doesNotContain(quarter.id("settlement:overpayment"));

        assertThat(idsIn("/api/purchase-invoices?supplierId=" + quarter.id("supplier:carrier")))
                .contains(quarter.id("purchase:freight"), quarter.id("purchase:stray"))
                .doesNotContain(quarter.id("purchase:invoice-first"));

        // The wholesaler's credit, and specifically not everybody's: a customer-credit list that
        // ignored its filter would show one customer another's money.
        List<Long> wholesaleCredits = idsIn("/api/customer-credits?customerId=" + wholesale);
        assertThat(wholesaleCredits).contains(quarter.id("credit:wholesale"));
        assertThat(idsIn("/api/customer-credits?customerId=" + cafe))
                .doesNotContain(quarter.id("credit:wholesale"));

        // And the lots, which is the filter every stock screen in step 16 will use.
        assertThat(idsIn("/api/inventory/lots?productId=" + quarter.id("product:beans")))
                .containsAll(quarter.beanLotIds());
        assertThat(idsIn("/api/inventory/lots?productId=" + quarter.id("product:grinder")))
                .doesNotContainAnyElementsOf(quarter.beanLotIds());
    }

    // ===================================================================================
    // Helpers
    // ===================================================================================

    private ReadBack readBack(String what, Runnable check) {
        return new ReadBack(what, check);
    }

    private JsonNode get(String path) {
        return Json.ok(api.get(path), "reading back " + path);
    }

    private List<JsonNode> lines(JsonNode document) {
        List<JsonNode> lines = new java.util.ArrayList<>();
        document.get("lines").forEach(lines::add);
        return lines;
    }

    private List<Long> idsIn(String path) {
        return Json.idsIn(api.get(path), path);
    }

    private List<Long> idsIn(String path, LocalDate from, LocalDate to) {
        String url = range(path, from, to);
        return Json.idsIn(api.get(url), url);
    }

    private static String range(String path, LocalDate from, LocalDate to) {
        return path + "?from=" + from + "&to=" + to;
    }

    /** Two wire amounts added, as strings, so the assertion never touches a double. */
    private static String sum(String left, String right) {
        return new java.math.BigDecimal(left).add(new java.math.BigDecimal(right)).toPlainString();
    }
}
