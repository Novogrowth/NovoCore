package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.banking.NewBankTransfer;
import gr.novotrade.novocore.core.api.bundle.NewBundleComponent;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.inventory.NewStockWriteOff;
import gr.novotrade.novocore.core.api.inventory.WriteOffReason;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductType;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptMatch;
import gr.novotrade.novocore.core.api.purchasing.NewFreightAllocation;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceiptLine;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoice;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoiceLine;
import gr.novotrade.novocore.core.api.sales.NewCreditNote;
import gr.novotrade.novocore.core.api.sales.NewCreditNoteLine;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoice;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoiceLine;
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import gr.novotrade.novocore.core.api.settlement.NewAllocation;
import gr.novotrade.novocore.core.api.settlement.NewSettlement;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * <strong>One trading quarter for Java Jives, driven entirely through the REST API.</strong>
 *
 * <p>This is step 15's narrative. Nothing here touches a service, a repository or the database — the
 * only way anything gets into this system is an HTTP request an operator could have made, in the
 * order an operator would have made it. That is the whole point: the 133 routes have been exercised
 * by tests that each assert what they were written to assert, and never by a sequence of operations
 * that has to hang together.
 *
 * <h2>Why a quarter and not a year</h2>
 *
 * <p>Three <em>closed months</em> is what exercises the {@code ?from=&to=} filters with something on
 * each side of every boundary, and a month is the unit the accountant package will eventually work
 * in. A year of the same fifty documents would be thinner per month and test the date filters less.
 *
 * <h2>Fixed everything</h2>
 *
 * <p>No {@code LocalDate.now()}, no randomness, no generated names. Every date, quantity and price is
 * a literal, so a failure reproduces exactly and every asserted figure is a number somebody can check
 * by hand. Same trust argument as ADR 0014's fixed default seed: a red run must always mean a defect.
 *
 * <h2>Names are obviously dummy</h2>
 *
 * <p>{@code TEST-CUSTOMER-01}, not a plausible Greek company. Step 20 migrates real Manager.io data
 * into this system, and realistic-looking fake data is a mistake waiting for somebody other than its
 * author to make. The VAT numbers are structurally valid and deliberately in a reserved range.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p>It creates no users and no roles — those have no HTTP route, by step 14's decision, and the
 * caller supplies the sessions. Smuggling those routes in to make the narrative tidy would be step
 * 14 scope arriving through the back door.
 */
final class TradingQuarter {

    // The quarter. Every document falls on one of these, and the boundaries are load-bearing:
    // JANUARY_FIRST and MARCH_LAST are what the date-range filters are asserted against.
    static final LocalDate JANUARY_FIRST = LocalDate.of(2026, 1, 5);
    static final LocalDate JANUARY_MID = LocalDate.of(2026, 1, 19);
    static final LocalDate JANUARY_LAST = LocalDate.of(2026, 1, 30);
    static final LocalDate FEBRUARY_FIRST = LocalDate.of(2026, 2, 3);
    static final LocalDate FEBRUARY_MID = LocalDate.of(2026, 2, 16);
    static final LocalDate FEBRUARY_LAST = LocalDate.of(2026, 2, 27);
    static final LocalDate MARCH_FIRST = LocalDate.of(2026, 3, 4);
    static final LocalDate MARCH_MID = LocalDate.of(2026, 3, 17);
    static final LocalDate MARCH_LAST = LocalDate.of(2026, 3, 31);

    /** A day outside the quarter, so a date filter can be shown to exclude as well as include. */
    static final LocalDate BEFORE_THE_QUARTER = LocalDate.of(2025, 12, 20);

    private final ApiClient.Session api;

    /** Everything the quarter produced, so a check can name the document it disagrees with. */
    private final Map<String, Long> handles = new LinkedHashMap<>();
    private final List<Long> beanLotIds = new ArrayList<>();

    TradingQuarter(ApiClient.Session api) {
        this.api = api;
    }

    long id(String handle) {
        Long value = handles.get(handle);
        assertThat(value).as("the narrative recorded no handle '%s'", handle).isNotNull();
        return value;
    }

    List<Long> beanLotIds() {
        return List.copyOf(beanLotIds);
    }

    Map<String, Long> handles() {
        return Map.copyOf(handles);
    }

    // ===================================================================================
    // Lookups — every form needs these before anything can be created
    // ===================================================================================

    /**
     * The seeded reference data, read through the API rather than through a repository.
     *
     * <p>Worth doing over HTTP even though it is only setup: a frontend has to populate a VAT class
     * picker exactly this way, and if these routes cannot answer, no form in step 16 can be built.
     */
    void readTheLookups() {
        List<JsonNode> vatClasses = Json.items(
                api.get("/api/vat-classes?active=true"), "the active VAT classes");
        assertThat(vatClasses).as("V5 seeds nine VAT classes").hasSize(9);
        for (JsonNode vatClass : vatClasses) {
            handles.put("vat:" + Json.text(vatClass, "code"), vatClass.get("id").asLong());
        }

        List<JsonNode> units = Json.items(
                api.get("/api/units-of-measure?active=true"), "the units of measure");
        assertThat(units).as("V11 seeds the unit-of-measure table").isNotEmpty();
        for (JsonNode unit : units) {
            handles.put("uom:" + Json.text(unit, "code"), unit.get("id").asLong());
        }

        List<JsonNode> chargeTypes = Json.items(
                api.get("/api/charge-types?active=true"), "the charge types");
        assertThat(chargeTypes).as("V7 seeds Delivery and COD fee").hasSizeGreaterThanOrEqualTo(2);
        for (JsonNode chargeType : chargeTypes) {
            handles.put("charge:" + Json.text(chargeType, "name"), chargeType.get("id").asLong());
        }

        // The exemption reasons matter from February, when the intra-EU sale needs one. Reading
        // them here is what a form would do, and V8's real AADE seed is what makes the count 29.
        List<JsonNode> reasons = Json.items(
                api.get("/api/vat-exemption-reasons?active=true"), "the AADE exemption reasons");
        assertThat(reasons).as("V8 seeds the real 29 AADE rows").hasSize(29);
        for (JsonNode reason : reasons) {
            handles.put("exemption:" + reason.get("code").asInt(), reason.get("id").asLong());
        }

        // The accounts the narrative settles against. Located by system key through the chart,
        // because account names are operator-editable and ids are not knowable in advance.
        for (JsonNode group : Json.items(api.get("/api/chart-of-accounts"), "the chart")) {
            group.get("accounts").forEach(account -> {
                String key = Json.text(account, "systemKey");
                if (key != null) {
                    handles.put("account:" + key, account.get("id").asLong());
                }
            });
        }
        assertThat(handles).containsKey("account:CASH");
        assertThat(handles).containsKey("account:FREIGHT_LANDED_COST_UNALLOCATED");

        // A bank account, found through the route a settlement form would use. The banks carry no
        // AccountSystemKey — they are operator data, not accounts NovoCore's own posting rules have
        // to locate — so this is the only way to name one, and it is the right way.
        //
        // It is needed because of a rule the narrative learned from the API rather than the other
        // way round: brief §6's legal cash limit is €500 and is a HARD block, no confirmation
        // offered (N. 5301/2026 penalties run to double the amount). A customer paying €700 is
        // therefore paying into a bank, which is what a real operator would have done anyway.
        List<JsonNode> settlementTargets = Json.items(
                api.get("/api/accounts/settlement-targets"), "the settlement target accounts");
        JsonNode bank = settlementTargets.stream()
                .filter(account -> !"CASH".equals(Json.text(account, "systemKey")))
                .filter(account -> "BANK_CASH".equals(Json.text(account, "kind")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No non-cash Bank-Cash account to settle into. V4 seeds Alpha Bank, "
                                + "Piraeus and NBG; settlement-targets returned: "
                                + settlementTargets));
        handles.put("account:bank", bank.get("id").asLong());
    }

    // ===================================================================================
    // January — the catalogue, then goods and invoices arriving in both orders
    // ===================================================================================

    void januarySetsUpTheCatalogue() {
        long piece = id("uom:PIECE");
        long standard = id("vat:1410");   // 24%
        long reduced = id("vat:1131");    // 13%

        handles.put("supplier:roaster", created("/api/suppliers",
                new NewSupplier("TEST-SUPPLIER-01 Roaster", "roaster@test.invalid", "2100000001",
                        "EL999000001", VatStatus.DOMESTIC, null), "the roaster"));
        handles.put("supplier:carrier", created("/api/suppliers",
                new NewSupplier("TEST-SUPPLIER-02 Carrier", null, null,
                        "EL999000002", VatStatus.DOMESTIC, null), "the carrier"));
        handles.put("supplier:eu", created("/api/suppliers",
                new NewSupplier("TEST-SUPPLIER-03 EU Machines", null, null,
                        "DE999000003", VatStatus.INTRA_EU_B2B, null), "the EU supplier"));

        // Three rates across the catalogue, so VAT precedence has something to resolve rather than
        // one answer that is right by coincidence.
        handles.put("product:beans", createProduct("TEST-PRODUCT-BEANS-01", "House blend beans",
                ProductType.GOODS, piece, reduced, "18.00", id("supplier:roaster"), false));
        handles.put("product:grinder", createProduct("TEST-PRODUCT-GRINDER-01", "Hand grinder",
                ProductType.GOODS, piece, standard, "120.00", id("supplier:roaster"), false));
        handles.put("product:filters", createProduct("TEST-PRODUCT-FILTERS-01", "Paper filters",
                ProductType.GOODS, piece, standard, "6.00", id("supplier:roaster"), false));
        handles.put("product:machine", createProduct("TEST-PRODUCT-MACHINE-01", "Espresso machine",
                ProductType.GOODS, piece, standard, "1450.00", id("supplier:eu"), true));
        handles.put("product:install", createProduct("TEST-PRODUCT-INSTALL-01", "Installation",
                ProductType.SERVICE, piece, standard, "40.00", null, false));

        // A bundle: grinder + two filters + the installation service. It holds no stock of its own,
        // and only its stocked components constrain how many can be assembled.
        long kit = createProduct("TEST-PRODUCT-KIT-01", "Starter kit",
                ProductType.GOODS, piece, standard, "150.00", null, false);
        Json.ok(api.putBody("/api/products/" + kit + "/components",
                        new ComponentsBody(List.of(
                                NewBundleComponent.one(id("product:grinder")),
                                NewBundleComponent.of(id("product:filters"), 2L),
                                NewBundleComponent.one(id("product:install"))))),
                "defining the starter kit");
        handles.put("product:kit", kit);

        // Customers. The seeded retail customer is structural (ADR 0009 / Q10) and is found rather
        // than created; the two near-duplicates exist so match-suggestions has something to suggest.
        handles.put("customer:wholesale", created("/api/customers",
                new NewCustomer("TEST-CUSTOMER-01 Wholesale", "wholesale@test.invalid", null,
                        "EL999100001", VatStatus.DOMESTIC, reduced, null), "the wholesaler"));
        handles.put("customer:cafe", created("/api/customers",
                new NewCustomer("TEST-CUSTOMER-02 Cafe", "cafe@test.invalid", "2100000002",
                        "EL999100002", VatStatus.DOMESTIC, null, null), "the cafe"));
        handles.put("customer:cafe-similar", created("/api/customers",
                new NewCustomer("TEST-CUSTOMER-03 Cafe", "cafe.two@test.invalid", null,
                        "EL999100003", VatStatus.DOMESTIC, null, null), "the similar cafe"));
        handles.put("customer:eu", created("/api/customers",
                new NewCustomer("TEST-CUSTOMER-04 EU Trade", null, null,
                        "DE999100004", VatStatus.INTRA_EU_B2B, null, null), "the EU customer"));
    }

    /**
     * The invoice arrives first, then the goods. GR/IR clears exactly, because the receipt takes its
     * cost from the invoice line it matches and is refused if it restates one.
     */
    void januaryInvoiceArrivesBeforeTheGoods() {
        long beans = id("product:beans");
        long reduced = id("vat:1131");

        JsonNode invoice = Json.ok(api.post("/api/purchase-invoices", new NewPurchaseInvoice(
                        id("supplier:roaster"), "TEST-PI-2026-001", JANUARY_FIRST,
                        "Beans, invoiced before delivery",
                        List.of(NewPurchaseInvoiceLine.inventory(
                                beans, Quantity.of(60L), UnitCost.ofEur("9.000000"), reduced)))),
                "the invoice-first purchase");
        handles.put("purchase:invoice-first", invoice.get("id").asLong());

        long invoiceLineId = Json.lineIds(invoice).getFirst();
        JsonNode receipt = Json.ok(api.post("/api/goods-receipts", new NewGoodsReceipt(
                        id("supplier:roaster"), "TEST-GR-2026-001", JANUARY_MID,
                        "Beans arriving against an existing invoice",
                        List.of(NewGoodsReceiptLine.pooledAgainst(
                                beans, Quantity.of(60L), invoiceLineId)))),
                "the receipt against the existing invoice");
        handles.put("receipt:against-invoice", receipt.get("id").asLong());
    }

    /**
     * The goods arrive first and the invoice disagrees on price. ADR 0008: the lot keeps the cost it
     * was received at and the difference posts to purchase price variance, because re-costing a lot
     * FIFO may already have consumed is the same problem as editing a posted entry.
     */
    void januaryGoodsArriveBeforeTheInvoice() {
        long standard = id("vat:1410");
        long reduced = id("vat:1131");

        JsonNode receipt = Json.ok(api.post("/api/goods-receipts", new NewGoodsReceipt(
                        id("supplier:roaster"), "TEST-GR-2026-002", JANUARY_MID,
                        "Beans, grinders and filters arriving before their invoice",
                        List.of(
                                NewGoodsReceiptLine.pooled(id("product:beans"),
                                        Quantity.of(40L), UnitCost.ofEur("10.000000")),
                                NewGoodsReceiptLine.pooled(id("product:grinder"),
                                        Quantity.of(10L), UnitCost.ofEur("70.000000")),
                                NewGoodsReceiptLine.pooled(id("product:filters"),
                                        Quantity.of(100L), UnitCost.ofEur("1.000000"))))),
                "the goods-first receipt");
        handles.put("receipt:goods-first", receipt.get("id").asLong());

        List<Long> receiptLines = Json.lineIds(receipt);
        JsonNode invoice = Json.ok(api.post("/api/purchase-invoices", new NewPurchaseInvoice(
                        id("supplier:roaster"), "TEST-PI-2026-002", JANUARY_LAST,
                        "The invoice for the January delivery — beans priced above receipt",
                        List.of(
                                // 40 beans invoiced at 10.50, received at 10.00: a 20.00 variance.
                                NewPurchaseInvoiceLine
                                        .inventory(id("product:beans"), Quantity.of(40L),
                                                UnitCost.ofEur("10.500000"), reduced)
                                        .matching(GoodsReceiptMatch.of(
                                                receiptLines.get(0), Quantity.of(40L))),
                                NewPurchaseInvoiceLine
                                        .inventory(id("product:grinder"), Quantity.of(10L),
                                                UnitCost.ofEur("70.000000"), standard)
                                        .matching(GoodsReceiptMatch.of(
                                                receiptLines.get(1), Quantity.of(10L))),
                                NewPurchaseInvoiceLine
                                        .inventory(id("product:filters"), Quantity.of(100L),
                                                UnitCost.ofEur("1.000000"), standard)
                                        .matching(GoodsReceiptMatch.of(
                                                receiptLines.get(2), Quantity.of(100L)))))),
                "the goods-first invoice");
        handles.put("purchase:goods-first", invoice.get("id").asLong());

        // Both halves matched when the second document was created; nothing matches them later
        // (Q41), which is why this is asserted here rather than assumed at the end.
        assertThat(Json.items(api.get("/api/purchase-invoices/"
                + invoice.get("id").asLong() + "/gr-ir-matches"), "the GR/IR matches"))
                .as("the invoice recorded after the delivery matched itself against it")
                .hasSize(3);

        beanLotIds.addAll(Json.idsIn(
                api.get("/api/inventory/lots?productId=" + id("product:beans")),
                "the bean lots"));
        assertThat(beanLotIds).as("two bean deliveries, two lots").hasSize(2);
    }

    /**
     * A sale before the freight lands, so ADR 0010's allocation has to split: the part belonging to
     * stock already gone cannot ride on a unit cost, because those units are not there to carry it.
     */
    void januaryFirstSalesAndTheFreightInvoice() {
        long standard = id("vat:1410");

        JsonNode sale = Json.ok(api.post("/api/sales-invoices", NewSalesInvoice.of(
                        id("customer:wholesale"), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
                        "TEST-SI-2026-0001", JANUARY_LAST,
                        List.of(
                                // Product default 13%, customer override 13%, and an explicit 24%
                                // on the third line: all three precedence levels on one document.
                                NewSalesInvoiceLine.product(id("product:beans"),
                                        Quantity.of(20L), UnitCost.ofEur("18.000000")),
                                NewSalesInvoiceLine.product(id("product:grinder"),
                                        Quantity.of(2L), UnitCost.ofEur("120.000000")),
                                NewSalesInvoiceLine.product(id("product:filters"),
                                        Quantity.of(10L), UnitCost.ofEur("6.000000"))
                                        .atVatClass(standard),
                                NewSalesInvoiceLine.charge(
                                        id("charge:Delivery"), Money.ofEur("5.00"))))),
                "the first wholesale sale");
        handles.put("sale:wholesale-january", sale.get("id").asLong());

        // The carrier's invoice, then allocated across both bean lots — one of which has already
        // partly sold.
        JsonNode carrierInvoice = Json.ok(api.post("/api/purchase-invoices", new NewPurchaseInvoice(
                        id("supplier:carrier"), "TEST-PI-FREIGHT-001", JANUARY_LAST,
                        "Inbound freight on the January bean deliveries",
                        List.of(NewPurchaseInvoiceLine.expense(
                                id("account:FREIGHT_LANDED_COST_UNALLOCATED"),
                                Money.ofEur("120.00"), standard)))),
                "the carrier's invoice");
        handles.put("purchase:freight", carrierInvoice.get("id").asLong());

        long freightLineId = Json.lineIds(carrierInvoice).getFirst();
        handles.put("purchase-line:freight", freightLineId);
        handles.put("freight:january", created("/api/freight-allocations",
                new NewFreightAllocation(freightLineId, Money.ofEur("120.00"), JANUARY_LAST,
                        "Inbound freight on the two bean deliveries", beanLotIds),
                "the freight allocation"));
    }

    // ===================================================================================
    // February — new channels, the bundle, serial-tracked machines, reverse charge
    // ===================================================================================

    /**
     * Machines arrive from the EU supplier. Two things here exist nowhere else in the narrative:
     * <strong>serial-tracked stock</strong>, where the quantity is the count of units and each unit
     * carries its own location, and <strong>reverse charge</strong>, which is a flag that must agree
     * with the supplier's {@code VatStatus} and is never inferred.
     */
    void februaryTheMachinesArrive() {
        long standard = id("vat:1410");

        JsonNode receipt = Json.ok(api.post("/api/goods-receipts", new NewGoodsReceipt(
                        id("supplier:eu"), "TEST-GR-2026-003", FEBRUARY_FIRST,
                        "Three espresso machines from the EU supplier",
                        List.of(NewGoodsReceiptLine.serialized(id("product:machine"),
                                List.of("TEST-SN-0001", "TEST-SN-0002", "TEST-SN-0003"),
                                UnitCost.ofEur("900.000000"))))),
                "the machine delivery");
        handles.put("receipt:machines", receipt.get("id").asLong());

        JsonNode invoice = Json.ok(api.post("/api/purchase-invoices", new NewPurchaseInvoice(
                        id("supplier:eu"), "TEST-PI-2026-003", FEBRUARY_FIRST,
                        "Machines, reverse charged",
                        List.of(NewPurchaseInvoiceLine
                                .inventory(id("product:machine"), Quantity.of(3L),
                                        UnitCost.ofEur("900.000000"), standard)
                                .reverseCharged()
                                .matching(GoodsReceiptMatch.of(
                                        Json.lineIds(receipt).getFirst(), Quantity.of(3L)))))),
                "the reverse-charged machine invoice");
        handles.put("purchase:machines", invoice.get("id").asLong());

        // Each unit is individually identified, which is what makes selling one by name possible.
        List<JsonNode> units = Json.items(
                api.get("/api/inventory/units?productId=" + id("product:machine")),
                "the machine units");
        assertThat(units).as("three machines received, three units").hasSize(3);
    }

    /**
     * The bundle sells as one line and is stored decomposed into its components — brief §5's
     * "linked, not duplicated" as a property of the data. A serial-tracked machine sells on its own
     * line, by name, because a bundle line names no serial numbers (Q42).
     */
    void februaryTheBundleAndAMachineSell() {
        handles.put("sale:bundle", created("/api/sales-invoices",
                NewSalesInvoice.of(id("customer:cafe"), SalesChannel.STORE_AND_PHONE,
                        SettlementMethod.CARD_POS, "TEST-SI-2026-0002", FEBRUARY_MID,
                        List.of(NewSalesInvoiceLine.product(
                                id("product:kit"), Quantity.of(1L), UnitCost.ofEur("150.000000")))),
                "the bundle sale"));

        handles.put("sale:machine", created("/api/sales-invoices",
                NewSalesInvoice.of(id("customer:cafe"), SalesChannel.STORE_AND_PHONE,
                        SettlementMethod.BANK_DEPOSIT, "TEST-SI-2026-0003", FEBRUARY_MID,
                        List.of(NewSalesInvoiceLine.serializedProduct(id("product:machine"),
                                UnitCost.ofEur("1450.000000"), List.of("TEST-SN-0001")))),
                "the machine sale"));

        // Skroutz, so all three revenue channels are exercised — the only place channel exists in
        // the model is which Sales account gets credited (step 3).
        handles.put("sale:skroutz", created("/api/sales-invoices",
                NewSalesInvoice.of(id("customer:cafe-similar"), SalesChannel.SKROUTZ,
                        SettlementMethod.SKROUTZ, "TEST-SI-2026-0004", FEBRUARY_MID,
                        List.of(NewSalesInvoiceLine.product(
                                id("product:beans"), Quantity.of(6L), UnitCost.ofEur("18.000000")))),
                "the Skroutz sale"));

        // An intra-EU B2B sale: VAT-free under a named article, so the line states an exemption
        // reason rather than a zero rate. Q9's whole point — exempt is not zero-rated.
        handles.put("sale:eu", created("/api/sales-invoices",
                NewSalesInvoice.of(id("customer:eu"), SalesChannel.ECOMMERCE,
                        SettlementMethod.ON_ACCOUNT, "TEST-SI-2026-0005", FEBRUARY_LAST,
                        List.of(NewSalesInvoiceLine
                                .product(id("product:beans"), Quantity.of(10L),
                                        UnitCost.ofEur("18.000000"))
                                .exemptUnder(id("exemption:14")))),
                "the intra-EU sale"));
    }

    /**
     * Money moves both ways, and one customer overpays. Q16: the surplus becomes a standalone
     * customer credit rather than a bare negative receivable, so it can be allocated deliberately.
     */
    void februarySettlesAndLeavesACredit() {
        long cash = id("account:CASH");

        // A partial payment to the roaster: the invoice stays open for the remainder, which is what
        // the open-items layer is for.
        handles.put("settlement:roaster-part", created("/api/settlements",
                NewSettlement.paymentTo(id("supplier:roaster"), cash, FEBRUARY_MID,
                        Money.ofEur("300.00"),
                        List.of(NewAllocation.againstPurchaseInvoice(
                                id("purchase:invoice-first"), Money.ofEur("300.00")))),
                "the partial payment to the roaster"));

        // The wholesaler pays more than the invoice: the remainder becomes a customer credit.
        JsonNode overpayment = Json.ok(api.post("/api/settlements",
                        NewSettlement.receiptFrom(id("customer:wholesale"), id("account:bank"), FEBRUARY_LAST,
                                        Money.ofEur("700.00"),
                                        List.of(NewAllocation.againstSalesInvoice(
                                                id("sale:wholesale-january"), Money.ofEur("600.00"))))
                                .leavingCredit()),
                "the wholesaler's overpayment");
        handles.put("settlement:overpayment", overpayment.get("id").asLong());

        List<JsonNode> credits = Json.items(
                api.get("/api/customer-credits?customerId=" + id("customer:wholesale") + "&open=true"),
                "the wholesaler's open credits");
        assertThat(credits)
                .as("Q16: an overpayment leaves a standalone credit document, not a negative balance")
                .hasSize(1);
        handles.put("credit:wholesale", credits.getFirst().get("id").asLong());
    }

    // ===================================================================================
    // March — corrections, a write-off, an oversell, and the quarter closing
    // ===================================================================================

    /**
     * A credit note that brings stock back into a lot the freight allocation has since re-costed.
     * ADR 0011: a return says the sale was real, so the split was right and only the returning
     * units' share is owed — it posts a catch-up in the same entry rather than refusing.
     */
    void marchTheReturnsComeBack() {
        JsonNode januarySale = Json.ok(
                api.get("/api/sales-invoices/" + id("sale:wholesale-january")),
                "the January wholesale sale");
        List<Long> saleLines = Json.lineIds(januarySale);

        handles.put("credit-note:stock", created("/api/credit-notes",
                NewCreditNote.of(id("sale:wholesale-january"), "TEST-CN-2026-0001", MARCH_FIRST,
                        List.of(NewCreditNoteLine.returning(
                                saleLines.getFirst(), Quantity.of(4L), UnitCost.ofEur("18.000000")))),
                "the stock-returning credit note"));

        // A price-only credit note against a different invoice: no stock moves, so this one stays
        // reversible where the one above does not (ADR 0009).
        JsonNode skroutzSale = Json.ok(api.get("/api/sales-invoices/" + id("sale:skroutz")),
                "the Skroutz sale");
        handles.put("credit-note:price", created("/api/credit-notes",
                NewCreditNote.of(id("sale:skroutz"), "TEST-CN-2026-0002", MARCH_FIRST,
                        List.of(NewCreditNoteLine.priceOnly(
                                Json.lineIds(skroutzSale).getFirst(), Quantity.of(6L),
                                UnitCost.ofEur("2.000000")))),
                "the price-only credit note"));

        // The credit note is allocated against the invoice it corrects — an allocation posts
        // nothing (ADR 0009), which is exactly why open items and AR must still agree afterwards.
        Json.ok(api.post("/api/credit-notes/" + id("credit-note:stock") + "/allocations",
                        new TargetedAllocationBody(id("sale:wholesale-january"), Money.ofEur("81.36"))),
                "allocating the credit note");

        // And so is the customer credit February left behind — against one of the wholesaler's own
        // invoices. The API refused the EU customer's, and it is right to: one customer's credit
        // balance cannot settle another's. Recorded here because the narrative got that wrong first
        // and the system corrected it, which is what this step is for.
        Json.ok(api.post("/api/customer-credits/" + id("credit:wholesale") + "/allocations",
                        new TargetedAllocationBody(
                                id("sale:wholesale-january"), Money.ofEur("50.00"))),
                "allocating the customer credit");
    }

    /**
     * Damage, a write-off, and a sale of stock we did not have. Q17: overselling is recorded as a
     * shortfall rather than blocked, the product genuinely reads negative, and no COGS is invented
     * for stock that was never there.
     */
    void marchLosesSomeStock() {
        // Moving to Damaged Goods posts nothing — the step 3 decision, and the reason phase 8's
        // Clearing Checks has to surface lots aging there.
        long grinderLot = Json.idsIn(
                api.get("/api/inventory/lots?productId=" + id("product:grinder")),
                "the grinder lots").getFirst();
        Json.ok(api.post("/api/inventory/lots/" + grinderLot + "/location",
                new LocationBody("DAMAGED_GOODS")), "moving grinders to Damaged Goods");
        handles.put("lot:damaged", grinderLot);

        handles.put("write-off:damage", created("/api/inventory/write-offs",
                NewStockWriteOff.pooled(grinderLot, Quantity.of(2L),
                                WriteOffReason.DAMAGE, MARCH_MID)
                        .withNote("Dropped in the stockroom"), "the write-off"));

        // Overselling: 500 filters against the hundred received.
        handles.put("sale:oversold", created("/api/sales-invoices",
                NewSalesInvoice.of(id("customer:cafe"), SalesChannel.SKROUTZ,
                        SettlementMethod.SKROUTZ, "TEST-SI-2026-0006", MARCH_MID,
                        List.of(NewSalesInvoiceLine.product(
                                id("product:filters"), Quantity.of(500L),
                                UnitCost.ofEur("6.000000")))),
                "the oversold sale"));
    }

    /**
     * The quarter closes: a correction reversed in full, our own money moved between accounts, and a
     * settlement amended below what it had already allocated — which cascades a release (Q13's
     * second half, and the one document-shaped PATCH in the whole surface).
     */
    void marchClosesTheQuarter() {
        long standard = id("vat:1410");

        // An invoice billed to the wrong company, reversed rather than edited (ADR 0006).
        JsonNode stray = Json.ok(api.post("/api/purchase-invoices", new NewPurchaseInvoice(
                        id("supplier:carrier"), "TEST-PI-FREIGHT-002", MARCH_FIRST,
                        "Billed to the wrong company",
                        List.of(NewPurchaseInvoiceLine.expense(
                                id("account:FREIGHT_LANDED_COST_UNALLOCATED"),
                                Money.ofEur("40.00"), standard)))),
                "the stray freight invoice");
        handles.put("purchase:stray", stray.get("id").asLong());
        Json.ok(api.post("/api/purchase-invoices/" + stray.get("id").asLong() + "/reversal",
                new ReversalBody(MARCH_MID, "Billed to the wrong company")),
                "reversing the stray invoice");

        handles.put("transfer:float", created("/api/bank-transfers",
                NewBankTransfer.of(id("account:CASH"), id("account:PARTNER_CLEARING_POS"),
                        MARCH_MID, Money.ofEur("50.00")).describedAs("Float to the POS account"),
                "the bank transfer"));

        // Amending a settlement below its allocated total releases allocations most-recent-first,
        // each release audit-logged. One route, and it cascades.
        //
        // The supplier payment, deliberately, and not the customer overpayment: that one left a
        // customer credit behind, and reducing a settlement whose credit is still live is now
        // refused outright rather than silently leaving the credit unbacked. The refusal has its
        // own test — this is the path that is meant to work.
        JsonNode amended = Json.ok(api.patchBody("/api/settlements/" + id("settlement:roaster-part"),
                        new AmendBody(id("account:CASH"), MARCH_LAST, Money.ofEur("250.00"),
                                "TEST-AMEND-001", "Corrected: 50.00 of this was another invoice")),
                "amending the supplier payment down");
        assertThat(Json.amount(amended, "amount"))
                .as("the amendment took effect")
                .isEqualTo("250.00");

        // And releasing an allocation outright — the only DELETE in the surface that removes a row,
        // because an allocation is a statement about a current relationship, not a record of an
        // event (ADR 0009).
        List<JsonNode> allocations = Json.items(
                api.get("/api/open-items?partyType=CUSTOMER&partyId=" + id("customer:wholesale")),
                "the wholesaler's open items");
        assertThat(allocations).as("the wholesaler still has open items after the amendment")
                .isNotEmpty();
    }

    // ===================================================================================
    // Helpers
    // ===================================================================================

    /**
     * POST a body, assert it was created, and return its id.
     *
     * <p>Exists because writing {@code Json.createdId(api.post(path, body), what)} inline put the
     * description one closing bracket away from being passed to {@code post} instead — and
     * {@code post} has a {@code (String, String)} overload, so that mistake binds silently and
     * sends the description as the request body. It did, ten times. A helper with one shape is the
     * fix; the overload staying is deliberate, since raw-JSON requests are what the refusal matrix
     * needs.
     */
    private long created(String path, Object body, String what) {
        return Json.createdId(api.post(path, body), what);
    }

    private long createProduct(String sku, String name, ProductType type, long unitOfMeasureId,
            long vatClassId, String sellingPrice, Long supplierId, boolean serialTracked) {
        return created("/api/products", new NewProduct(
                sku, null, name, type, unitOfMeasureId, vatClassId,
                sellingPrice == null ? null : Money.ofEur(sellingPrice),
                supplierId, supplierId == null ? null : "SUP-" + sku, serialTracked), sku);
    }

    // The controllers' request records are package-private to the core, so the scenario mirrors the
    // few it needs. Deliberately not made public there: a request shape is the web layer's, and
    // widening its visibility for a test would be the test shaping the production code.

    /** Mirrors {@code BundleController.ComponentsRequest}. */
    record ComponentsBody(List<NewBundleComponent> components) {
    }

    /** Mirrors {@code InventoryController.LocationRequest}. */
    record LocationBody(String location) {
    }

    /** Mirrors {@code SettlementController.TargetedAllocationRequest}. */
    record TargetedAllocationBody(long salesInvoiceId, Money amount) {
    }

    /** Mirrors {@code SettlementController.AmendRequest}. */
    record AmendBody(long accountId, LocalDate settlementDate, Money amount, String reference,
            String description) {
    }

    /** Mirrors the reversal request the document controllers share. */
    record ReversalBody(LocalDate reversalDate, String reason) {
    }
}
