package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.NewAccount;
import gr.novotrade.novocore.core.api.asset.NewAsset;
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
import gr.novotrade.novocore.core.api.shared.Rate;
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
        // Kept because the refusal matrix needs a fully delivered invoice line: receiving against
        // one that is already satisfied is what drives GR/IR below zero.
        handles.put("purchase-line:invoice-first", invoiceLineId);
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
    // Quarter end — the reads and corrections an operator actually makes
    // ===================================================================================

    /**
     * Everything somebody looks at when the quarter closes. Reads only, and worth driving for two
     * reasons: these are the screens step 16 has to build, and a listing that cannot answer is a
     * defect whether or not any document depended on it.
     */
    void quarterEndReview() {
        // What is still outstanding, both ways round.
        Json.items(api.get("/api/open-items?partyType=CUSTOMER"), "all customer open items");
        Json.items(api.get("/api/open-items?partyType=SUPPLIER"), "all supplier open items");
        Json.items(api.get("/api/settlements/unallocated"), "settlements not fully applied");
        Json.items(api.get("/api/customer-credits?open=true"), "open customer credits");

        // The expected-to-clear accounts, which is what phase 8's Clearing Checks will read.
        Json.items(api.get("/api/purchase-invoice-lines/awaiting-delivery"), "invoiced, undelivered");
        Json.items(api.get("/api/goods-receipt-lines/awaiting-invoice"), "delivered, uninvoiced");
        Json.items(api.get("/api/purchase-invoice-lines/awaiting-allocation"), "freight to allocate");
        Json.ok(api.get("/api/purchase-invoice-lines/" + id("purchase-line:freight")
                + "/unallocated-amount"), "what is left on the freight line");

        // Variances and rounding — the two figures that mean somebody should look.
        Json.ok(api.get("/api/purchase-invoices/variances?from=" + JANUARY_FIRST
                + "&to=" + MARCH_LAST), "the purchase price variances");
        Json.ok(api.get("/api/sales-invoices/rounding-differences?from=" + JANUARY_FIRST
                + "&to=" + MARCH_LAST), "the accepted rounding differences");

        // Stock, from both ends: the product-level figure an order picker needs, and the lots that
        // say what it cost.
        Json.ok(api.get("/api/products/" + id("product:beans") + "/stock"), "bean stock");
        Json.items(api.get("/api/inventory/lots?productId=" + id("product:beans")), "the bean lots");
        Json.ok(api.get("/api/inventory/lots/" + beanLotIds.getFirst()), "one bean lot");
        Json.items(api.get("/api/inventory/lots/with-landed-cost"), "lots carrying landed cost");
        Json.items(api.get("/api/inventory/units?productId=" + id("product:machine")), "the machines");
        Json.items(api.get("/api/inventory/consumptions?from=" + JANUARY_FIRST + "&to=" + MARCH_LAST),
                "every consumption in the quarter");
        Json.items(api.get("/api/inventory/consumptions/with-shortfall"), "the oversold ones");
        Json.items(api.get("/api/inventory/write-offs?from=" + JANUARY_FIRST + "&to=" + MARCH_LAST),
                "the quarter's write-offs");
        Json.ok(api.get("/api/inventory/write-offs/" + id("write-off:damage")), "one write-off");

        // The documents themselves, by id and by filter.
        Json.ok(api.get("/api/purchase-invoices/" + id("purchase:goods-first")), "one purchase");
        Json.ok(api.get("/api/goods-receipts/" + id("receipt:goods-first")), "one receipt");
        Json.items(api.get("/api/goods-receipts/by-lot/" + beanLotIds.getFirst()), "the lot's receipt");
        Json.ok(api.get("/api/freight-allocations/" + id("freight:january")), "the allocation");
        Json.items(api.get("/api/freight-allocations?from=" + JANUARY_FIRST + "&to=" + MARCH_LAST),
                "the quarter's allocations");
        Json.ok(api.get("/api/sales-invoices/" + id("sale:bundle")), "the bundle sale");
        Json.ok(api.get("/api/credit-notes/" + id("credit-note:stock")), "one credit note");
        Json.items(api.get("/api/credit-notes?salesInvoiceId=" + id("sale:wholesale-january")),
                "credit notes against one invoice");
        Json.ok(api.get("/api/settlements/" + id("settlement:roaster-part")), "one settlement");
        Json.items(api.get("/api/settlements?partyType=SUPPLIER&partyId=" + id("supplier:roaster")),
                "the roaster's settlements");
        Json.ok(api.get("/api/bank-transfers/" + id("transfer:float")), "the transfer");
        Json.items(api.get("/api/bank-transfers?from=" + JANUARY_FIRST + "&to=" + MARCH_LAST),
                "the quarter's transfers");

        // Master data, including the bundle views and the matching that is split by certainty.
        Json.items(api.get("/api/products"), "every product");
        Json.ok(api.get("/api/products/" + id("product:beans")), "one product");
        Json.items(api.get("/api/bundles"), "the bundles");
        Json.items(api.get("/api/bundles/unpriced-components"), "bundles that cannot be priced");
        Json.items(api.get("/api/products/" + id("product:kit") + "/components"), "the kit's parts");
        Json.items(api.get("/api/products/" + id("product:grinder") + "/in-bundles"),
                "what the grinder is part of");
        Json.items(api.get("/api/customers"), "every customer");
        Json.ok(api.get("/api/customers/" + id("customer:cafe")), "one customer");
        Json.ok(api.get("/api/customers/by-vat-number/EL999100002"), "a customer by VAT number");
        Json.items(api.get("/api/customers/match-suggestions?name=TEST-CUSTOMER"),
                "the near-duplicate customers");
        Json.items(api.get("/api/suppliers"), "every supplier");
        Json.ok(api.get("/api/suppliers/" + id("supplier:roaster")), "one supplier");
        Json.ok(api.get("/api/suppliers/by-vat-number/EL999000001"), "a supplier by VAT number");
        Json.items(api.get("/api/suppliers/match-suggestions?name=TEST-SUPPLIER"),
                "the near-duplicate suppliers");
        Json.items(api.get("/api/accounts"), "the accounts");
        Json.items(api.get("/api/account-groups"), "the account groups");
        Json.ok(api.get("/api/accounts/" + id("account:CASH")), "one account");
        Json.ok(api.get("/api/vat-classes/" + id("vat:1410")), "one VAT class");
    }

    /**
     * The corrections a quarter accumulates: a product renamed, a price changed, a customer's
     * details put right, an asset recorded. All {@code PATCH} routes, which nothing else drives.
     */
    void quarterEndCorrections() {
        long beans = id("product:beans");
        Json.ok(api.patchBody("/api/products/" + beans + "/name",
                new NameBody("House blend beans (250g)")), "renaming the beans");
        Json.ok(api.patchBody("/api/products/" + beans + "/selling-price",
                new SellingPriceBody(Money.ofEur("19.50"))), "repricing the beans");
        Json.ok(api.patchBody("/api/products/" + beans + "/ean",
                new EanBody("5201234567890")), "giving the beans an EAN");
        Json.ok(api.patchBody("/api/products/" + beans + "/vat-class",
                new VatClassBody(id("vat:1131"))), "restating the beans' VAT class");
        // On the installation service, not the beans: a product with lots refuses this, and rightly
        // — reinterpreting a recorded quantity in a different unit is a different quantity, not a
        // correction. The narrative learned that from the API.
        Json.ok(api.patchBody("/api/products/" + id("product:install") + "/unit-of-measure",
                new UnitOfMeasureBody(id("uom:PIECE"))), "restating the unit of measure");
        Json.ok(api.patchBody("/api/products/" + beans + "/supplier",
                new SupplierBody(id("supplier:roaster"), "ROAST-BEANS-01")), "the supplier's code");

        Json.ok(api.patchBody("/api/customers/" + id("customer:cafe") + "/name",
                new NameBody("TEST-CUSTOMER-02 Cafe (renamed)")), "renaming the cafe");
        Json.ok(api.patchBody("/api/customers/" + id("customer:cafe") + "/contact-details",
                new ContactDetailsBody("cafe.new@test.invalid", "2100000009")), "new contact details");
        Json.ok(api.patchBody("/api/suppliers/" + id("supplier:carrier") + "/contact-details",
                new ContactDetailsBody("carrier@test.invalid", null)), "the carrier's details");

        // An asset, which the quarter otherwise never touches. Its depreciation rate is deliberately
        // left unset: the statutory rates are still pending from the accountant, and a guessed rate
        // produces a charge in the accounts nobody chose.
        long asset = Json.createdId(api.post("/api/assets", new NewAsset(
                "TEST-ASSET-01", "TEST-ASSET-01 Roaster", JANUARY_FIRST, null, null)),
                "the roaster asset");
        handles.put("asset:roaster", asset);
        Json.items(api.get("/api/assets"), "every asset");
        Json.ok(api.get("/api/assets/" + asset), "one asset");
        Json.ok(api.get("/api/assets/by-code/TEST-ASSET-01"), "an asset by code");
        Json.items(api.get("/api/assets/without-depreciation-rate"), "assets awaiting a rate");
        Json.items(api.get("/api/assets/depreciable"), "assets that can be depreciated");
    }

    // ===================================================================================
    // Quarter end — the housekeeping, and the corrections that undo something
    // ===================================================================================

    /**
     * <strong>Everything an operator does that the story above had no natural place for.</strong>
     *
     * <p>Written because {@code assertEveryRouteCoveredExcept} forced the question. Forty-three routes
     * were uncovered, and going through them one at a time turned out to be the useful part: most were
     * not unreachable, they were simply the second half of something the narrative did once — a
     * supplier renamed where a product had been, a settlement allocated <em>later</em> where every
     * other one was allocated as it was recorded. Those are ordinary operator actions and there was no
     * reason not to drive them; excusing them would have been the coverage ledger being used as a place
     * to put things rather than as a question to answer.
     *
     * <h2>Nothing here disturbs the quarter the invariants are asserted against</h2>
     *
     * <p>That is the constraint every line below is written to, and it is why so much of this creates
     * its own throwaway record rather than reusing one from the story:
     *
     * <ul>
     *   <li><strong>Each reversal reverses a document created for the purpose</strong>, one line above
     *       it. Reversing a load-bearing one would be a correction the ledger is entitled to reflect,
     *       and then twelve invariants would be asserting something other than what January to March
     *       actually was.
     *   <li><strong>The bundle dissolved is a second bundle</strong>, not the starter kit. The kit was
     *       sold in February and the refusal matrix still needs it to <em>be</em> a bundle; a
     *       dissolution is exactly the kind of change that reads as harmless and quietly turns another
     *       test's premise false.
     *   <li><strong>The asset put through its lifecycle is a second asset</strong>, because the
     *       roaster's absent depreciation rate is a read-back assertion — Q12's "not known yet" must
     *       still be not known.
     *   <li><strong>Deactivations are paired with the reactivation</strong>, so a record the story
     *       depends on ends where it started.
     * </ul>
     */
    void quarterEndHousekeeping() {
        chartOfAccountsMaintenance();
        masterDataCorrections();
        theAssetThatWasDisposedOfInError();
        theProductThatNeverSold();
        stockMovedAndRead();
        settlementsAllocatedLaterAndOneReleased();
        theSixReversals();
        theBundleNobodyWanted();
    }

    /**
     * Adding an account and a group, renaming both, and reordering.
     *
     * <p>The chart is the spine of the ledger and <strong>no test had ever written to it over
     * HTTP</strong> — {@code ChartOfAccountsEndpointIT} and the quarter's own reads only ever read it.
     */
    private void chartOfAccountsMaintenance() {
        long group = Json.createdId(api.post("/api/account-groups",
                new NameBody("TEST-GROUP Sundries")), "a new account group");
        handles.put("group:sundries", group);

        long account = Json.createdId(api.post("/api/accounts",
                new NewAccount("TEST-ACCOUNT Sundry expenses", AccountType.EXPENSE,
                        AccountKind.STANDARD, null, group, false)), "a new account");
        handles.put("account:sundries", account);

        Json.ok(api.patchBody("/api/accounts/" + account + "/name",
                new NameBody("TEST-ACCOUNT Sundry expenses (renamed)")), "renaming the account");
        Json.ok(api.patchBody("/api/account-groups/" + group + "/name",
                new NameBody("TEST-GROUP Sundries (renamed)")), "renaming the group");

        // Deactivate and reactivate: there is no delete in this chart, because with no period
        // locking there is no point at which an account is safely finished with.
        Json.succeeded(api.post("/api/accounts/" + account + "/deactivate", "{}"), "deactivating it");
        Json.succeeded(api.post("/api/accounts/" + account + "/reactivate", "{}"), "reactivating it");

        // A reorder must name every member exactly once — a partial list is refused rather than
        // leaving the remainder in an order nobody chose (CLAUDE.md rule 7). So both of these read
        // the current order first and send it back whole, which is the only way to call these routes
        // at all and is what a drag-and-drop screen will do.
        Json.succeeded(api.putBody("/api/account-groups/" + group + "/account-order",
                new OrderBody(List.of(account))), "reordering one group's accounts");

        List<Long> groupIds = Json.idsIn(api.get("/api/account-groups"), "every account group");
        Json.succeeded(api.putBody("/api/account-groups/order", new OrderBody(groupIds)),
                "reordering the groups themselves");
    }

    /** The corrections whose counterparts the narrative already made on the other party. */
    private void masterDataCorrections() {
        long cafe = id("customer:cafe");
        Json.ok(api.patchBody("/api/customers/" + cafe + "/vat-number",
                new VatNumberBody("EL999100022")), "correcting the cafe's VAT number");
        // DOMESTIC to DOMESTIC and no exemption reason: the point here is the route, and changing a
        // party's VAT status to something the quarter's issued invoices contradict would be a real
        // correction with real consequences rather than housekeeping.
        Json.ok(api.patchBody("/api/customers/" + cafe + "/vat-status",
                new VatStatusBody(VatStatus.DOMESTIC, null)), "restating the cafe's VAT status");
        Json.ok(api.patchBody("/api/customers/" + cafe + "/vat-class-override",
                new VatClassOverrideBody(null)), "clearing the cafe's VAT class override");

        long carrier = id("supplier:carrier");
        Json.ok(api.patchBody("/api/suppliers/" + carrier + "/name",
                new NameBody("TEST-SUPPLIER-02 Carrier (renamed)")), "renaming the carrier");
        Json.ok(api.patchBody("/api/suppliers/" + carrier + "/vat-number",
                new VatNumberBody("EL999000022")), "correcting the carrier's VAT number");
        Json.ok(api.patchBody("/api/suppliers/" + carrier + "/vat-status",
                new VatStatusBody(VatStatus.DOMESTIC, null)), "restating the carrier's VAT status");

        // Deactivate and reactivate, on both parties. Paired deliberately — the story still needs
        // these records, and a deactivation left standing would change what the listings return.
        Json.succeeded(api.post("/api/customers/" + id("customer:cafe-similar") + "/deactivate", "{}"),
                "deactivating the near-duplicate cafe");
        Json.succeeded(api.post("/api/customers/" + id("customer:cafe-similar") + "/reactivate", "{}"),
                "reactivating it");
        Json.succeeded(api.post("/api/suppliers/" + carrier + "/deactivate", "{}"),
                "deactivating the carrier");
        Json.succeeded(api.post("/api/suppliers/" + carrier + "/reactivate", "{}"), "reactivating it");
    }

    /**
     * A second asset, given a rate, disposed of, and reinstated.
     *
     * <p>A second one because {@code asset:roaster} is the register's honest state — Q12's rate is
     * still pending the accountant, and a read-back asserts that it arrives as absent rather than as a
     * zero anybody could depreciate by.
     */
    private void theAssetThatWasDisposedOfInError() {
        long asset = Json.createdId(api.post("/api/assets", new NewAsset(
                "TEST-ASSET-02", "TEST-ASSET-02 Delivery van", JANUARY_FIRST, null, null)),
                "the second asset");
        handles.put("asset:van", asset);

        Json.ok(api.patchBody("/api/assets/" + asset + "/name",
                new NameBody("TEST-ASSET-02 Delivery van (renamed)")), "renaming the asset");
        // 10% — a hundred-year life is what the 1% lower bound exists to refuse, and 0.1 written for
        // 10% is what it exists to catch. Nothing posts to assets yet, so this changes no figure.
        Json.ok(api.patchBody("/api/assets/" + asset + "/depreciation-rate",
                new DepreciationRateBody(Rate.of("10"))), "setting a depreciation rate");
        Json.ok(api.patchBody("/api/assets/" + asset + "/depreciation-start-date",
                new DepreciationStartDateBody(FEBRUARY_FIRST)), "a later depreciation start");

        Json.ok(api.post("/api/assets/" + asset + "/disposal",
                new DisposalBody(MARCH_LAST)), "disposing of the van");
        // And undone: the disposal date is required exactly when disposed and refused otherwise, so
        // reinstatement has to clear it rather than leave a date on an asset still in use.
        Json.ok(api.post("/api/assets/" + asset + "/reinstatement", "{}"),
                "reinstating it — the disposal was recorded against the wrong asset");
    }

    /** A product that never sells: serial tracking switched on, then deactivated and brought back. */
    private void theProductThatNeverSold() {
        long product = createProduct("TEST-PRODUCT-SPARE-01", "Spare portafilter",
                ProductType.GOODS, id("uom:PIECE"), id("vat:1410"), "35.00", null, false);
        handles.put("product:spare", product);

        // Refused once the product has lots, for the same reason the unit of measure is: it would
        // mean inventing identities for stock nothing tracked. This one has none.
        Json.ok(api.patchBody("/api/products/" + product + "/serial-tracking",
                new SerialTrackingBody(true)), "turning on serial tracking");

        Json.succeeded(api.post("/api/products/" + product + "/deactivate", "{}"), "discontinuing it");
        Json.succeeded(api.post("/api/products/" + product + "/reactivate", "{}"), "bringing it back");
    }

    /** One machine goes out for repair, and the reads that follow a unit and a consumption. */
    private void stockMovedAndRead() {
        List<JsonNode> machineUnits = Json.items(
                api.get("/api/inventory/units?productId=" + id("product:machine")), "the machines");
        // TEST-SN-0002, not the one February sold. A serial-tracked lot stores its location per
        // unit precisely so this move does not take the others with it.
        // By the derived onHand flag rather than by naming a status: the view computes it, and a
        // test that re-derives it from the enum would be a second copy of the rule (SOLD is on hand
        // for nobody, and UNRECEIVED exists too).
        JsonNode onHand = machineUnits.stream()
                .filter(unit -> unit.get("onHand").asBoolean())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no machine is still on hand to move: " + machineUnits));
        long unitId = onHand.get("id").asLong();
        Json.ok(api.post("/api/inventory/units/" + unitId + "/location",
                new LocationBody("DAMAGED_GOODS")), "a machine going out for repair");

        // Moving to Damaged Goods posts nothing, so the lot still carries it — which is the whole
        // reason phase 8's Clearing Checks has to surface stock aging there.
        long machineLot = Json.idsIn(api.get("/api/inventory/lots?productId=" + id("product:machine")),
                "the machine lots").getFirst();
        Json.items(api.get("/api/inventory/lots/" + machineLot + "/units"), "that lot's units");

        List<Long> consumptions = Json.idsIn(
                api.get("/api/inventory/consumptions?from=" + JANUARY_FIRST + "&to=" + MARCH_LAST),
                "the quarter's consumptions");
        Json.ok(api.get("/api/inventory/consumptions/" + consumptions.getFirst()),
                "one consumption on its own");
    }

    /**
     * A receipt banked now and matched to an invoice later, then one match undone.
     *
     * <p>Every other settlement in the quarter allocates as it is recorded. This is the other shape,
     * and it is the ordinary one: money arrives, and which invoices it settles is worked out
     * afterwards. <strong>The release is the only {@code DELETE} in this API that removes a row</strong>
     * — an allocation states a current relationship rather than records an event, which is exactly what
     * makes Q13's second half implementable and why nothing has to be un-posted here.
     */
    private void settlementsAllocatedLaterAndOneReleased() {
        // An invoice with something outstanding to allocate against, which turns out to be a
        // narrower requirement than it sounds: the narrative first tried the bundle sale and was
        // told that "a sales invoice paid in cash or through a partner clearing account is born
        // fully settled and never has an open amount". CARD_POS is one of those. ON_ACCOUNT is the
        // only settlement method that leaves a receivable, which is the whole point of it — so this
        // is a service-only ON_ACCOUNT sale, moving no stock and owing 40.00 plus VAT.
        long onAccountSale = created("/api/sales-invoices",
                NewSalesInvoice.of(id("customer:cafe"), SalesChannel.STORE_AND_PHONE,
                        SettlementMethod.ON_ACCOUNT, "TEST-SI-2026-0008", MARCH_LAST,
                        List.of(NewSalesInvoiceLine.product(
                                id("product:install"), Quantity.of(1L), UnitCost.ofEur("40.000000")))),
                "an installation invoiced on account");
        handles.put("sale:on-account", onAccountSale);

        JsonNode receipt = Json.ok(api.post("/api/settlements",
                NewSettlement.receiptFrom(id("customer:cafe"), id("account:bank"), MARCH_LAST,
                        Money.ofEur("49.60"), List.of())),
                "a receipt banked before anybody worked out what it settles");
        long settlementId = receipt.get("id").asLong();
        handles.put("settlement:unallocated", settlementId);

        JsonNode allocated = Json.ok(api.post("/api/settlements/" + settlementId + "/allocations",
                        new AllocationsBody(List.of(NewAllocation.againstSalesInvoice(
                                onAccountSale, Money.ofEur("20.00"))))),
                "matching it against the invoice, afterwards");

        JsonNode allocation = allocated.get("allocations").get(0);
        Json.succeeded(api.delete("/api/allocations/" + allocation.get("id").asLong()),
                "releasing the match — it was entered against the wrong invoice");

        // Released, not reversed: an allocation posts nothing, so unsaying it needs nothing
        // un-posted and the customer's position is exactly what it was before.
        assertThat(Json.amount(Json.ok(api.get("/api/settlements/" + settlementId),
                        "the settlement after the release"), "unallocatedAmount"))
                .as("releasing the only allocation leaves the whole receipt unapplied")
                .isEqualTo("49.60");

        // Then applied properly. Left unallocated it would be a real open item — a legitimate state,
        // and not one to leave lying around at the end of a quarter the invariants are asserted on.
        Json.ok(api.post("/api/settlements/" + settlementId + "/allocations",
                        new AllocationsBody(List.of(NewAllocation.againstSalesInvoice(
                                onAccountSale, Money.ofEur("49.60"))))),
                "applying the receipt to the invoice it was actually for");
    }

    /**
     * Six documents recorded in error and reversed, one per reversible type.
     *
     * <p>Each is created here and reversed immediately, which is both what makes it safe and what
     * makes it realistic: the commonest reason to reverse a document is that it was entered wrong,
     * and the second commonest — reversing something the business has since acted on — is refused
     * anyway, by design and with a named remedy.
     */
    private void theSixReversals() {
        long standard = id("vat:1410");

        // A sale of the installation service alone: no stock moves, so the reversal is a clean
        // mirror with no lot to have been re-costed underneath it (ADR 0011).
        long strandedSale = created("/api/sales-invoices",
                NewSalesInvoice.of(id("customer:cafe"), SalesChannel.STORE_AND_PHONE,
                        SettlementMethod.ON_ACCOUNT, "TEST-SI-2026-0007", MARCH_LAST,
                        List.of(NewSalesInvoiceLine.product(
                                id("product:install"), Quantity.of(1L), UnitCost.ofEur("40.000000")))),
                "a sale billed to the wrong customer");
        Json.ok(api.post("/api/sales-invoices/" + strandedSale + "/reversal",
                new ReversalBody(MARCH_LAST, "Billed to the wrong customer")), "reversing it");

        // A price-only credit note, which moves no stock and therefore stays reversible where a
        // stock-returning one does not.
        //
        // Against its own invoice, and not against the Skroutz sale as this first tried: March
        // already credited that line in full, and the API said so — "6.000000 has already been
        // credited, so 1.000000 cannot be. Crediting more than was sold would reclaim output VAT
        // that was never charged." Another case of the system being right and the narrative wrong.
        JsonNode creditable = Json.ok(api.post("/api/sales-invoices", NewSalesInvoice.of(
                        id("customer:cafe"), SalesChannel.STORE_AND_PHONE,
                        SettlementMethod.ON_ACCOUNT, "TEST-SI-2026-0009", MARCH_LAST,
                        List.of(NewSalesInvoiceLine.product(id("product:install"),
                                Quantity.of(1L), UnitCost.ofEur("40.000000"))))),
                "a second installation, invoiced on account");
        long strayNote = created("/api/credit-notes",
                NewCreditNote.of(creditable.get("id").asLong(), "TEST-CN-2026-0003", MARCH_LAST,
                        List.of(NewCreditNoteLine.priceOnly(
                                Json.lineIds(creditable).getFirst(), Quantity.of(1L),
                                UnitCost.ofEur("1.000000")))),
                "a credit note for a discount nobody agreed");
        Json.ok(api.post("/api/credit-notes/" + strayNote + "/reversal",
                new ReversalBody(MARCH_LAST, "The discount was never agreed")), "reversing it");

        // A delivery that never arrived. Refused once its lots have been touched — this one's have
        // not, which is the only state in which un-receiving is honest.
        JsonNode phantom = Json.ok(api.post("/api/goods-receipts", new NewGoodsReceipt(
                        id("supplier:roaster"), "TEST-GR-2026-004", MARCH_LAST,
                        "Entered against the wrong delivery note",
                        List.of(NewGoodsReceiptLine.pooled(id("product:filters"),
                                Quantity.of(10L), UnitCost.ofEur("1.000000"))))),
                "a delivery entered in error");
        Json.ok(api.post("/api/goods-receipts/" + phantom.get("id").asLong() + "/reversal",
                new ReversalBody(MARCH_LAST, "Entered against the wrong delivery note")),
                "un-receiving it");

        // A transfer between our own accounts, the wrong way round.
        long strayTransfer = created("/api/bank-transfers",
                NewBankTransfer.of(id("account:bank"), id("account:CASH"), MARCH_LAST,
                        Money.ofEur("20.00")).describedAs("The wrong way round"),
                "a transfer in the wrong direction");
        Json.ok(api.post("/api/bank-transfers/" + strayTransfer + "/reversal",
                new ReversalBody(MARCH_LAST, "The wrong way round")), "reversing it");

        // Freight allocated to the wrong shipment, on a lot received above and nowhere else — so
        // the reversal cannot collide with the January allocation the invariants examine.
        JsonNode strayFreightInvoice = Json.ok(api.post("/api/purchase-invoices",
                        new NewPurchaseInvoice(id("supplier:carrier"), "TEST-PI-FREIGHT-003",
                                MARCH_LAST, "Freight allocated to the wrong shipment",
                                List.of(NewPurchaseInvoiceLine.expense(
                                        id("account:FREIGHT_LANDED_COST_UNALLOCATED"),
                                        Money.ofEur("30.00"), standard)))),
                "a third carrier invoice");
        long filterLot = Json.idsIn(api.get("/api/inventory/lots?productId=" + id("product:filters")),
                "the filter lots").getFirst();
        long strayAllocation = created("/api/freight-allocations",
                new NewFreightAllocation(Json.lineIds(strayFreightInvoice).getFirst(),
                        Money.ofEur("30.00"), MARCH_LAST, "Allocated to the wrong shipment",
                        List.of(filterLot)),
                "a freight allocation onto the wrong lot");
        Json.ok(api.post("/api/freight-allocations/" + strayAllocation + "/reversal",
                new ReversalBody(MARCH_LAST, "Allocated to the wrong shipment")), "reversing it");

        // And a write-off that was a miscount. Restores the quantity and posts the mirror in one
        // transaction, which is why JournalService.reverse refuses this source outright.
        //
        // From the grinder lot, and the choice is forced twice over. The filter lot is empty —
        // March oversold it, and the API said so: "Lot 4 has 0.000000 left, so 1.000000 cannot be
        // written off it. Stock that was never there cannot be lost." And a bean lot would be
        // worse: freight re-costed both, and ADR 0011 refuses to reverse a movement on a re-costed
        // lot, so the write-off would go in and the reversal would be turned down. The grinder lot
        // carries no landed cost and still has stock.
        long strayWriteOff = created("/api/inventory/write-offs",
                NewStockWriteOff.pooled(id("lot:damaged"), Quantity.of(1L), WriteOffReason.SHRINKAGE,
                                MARCH_LAST).withNote("Counted short"),
                "a write-off from a miscount");
        Json.ok(api.post("/api/inventory/write-offs/" + strayWriteOff + "/reversal",
                new WriteOffReversalBody(MARCH_LAST, "The count was wrong, not the stock")),
                "reversing the write-off");
    }

    /**
     * A bundle defined and then dissolved.
     *
     * <p>Its own bundle rather than the starter kit, and the reason is worth keeping: the kit was sold
     * in February, so dissolving it would be the very case brief §5's "alias forward, never rewrite
     * history" is about — safe in the ledger, since a sale materialises its own decomposition, and
     * quietly false for anything still asserting that the kit <em>is</em> a bundle.
     */
    private void theBundleNobodyWanted() {
        long spare = createProduct("TEST-PRODUCT-KIT-02", "Cleaning kit",
                ProductType.GOODS, id("uom:PIECE"), id("vat:1410"), "25.00", null, false);
        Json.ok(api.putBody("/api/products/" + spare + "/components",
                        new ComponentsBody(List.of(NewBundleComponent.of(id("product:filters"), 4L)))),
                "defining a cleaning kit");
        Json.succeeded(api.delete("/api/products/" + spare + "/components"),
                "dissolving it again — it never sold");
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

    /** Mirrors the {@code NameRequest} several controllers declare. */
    record NameBody(String name) {
    }

    /** Mirrors {@code ChartOfAccountsController.OrderRequest}. */
    record OrderBody(List<Long> idsInOrder) {
    }

    /** Mirrors the {@code VatNumberRequest} on Customer and Supplier. */
    record VatNumberBody(String vatNumber) {
    }

    /** Mirrors the {@code VatStatusRequest} on Customer and Supplier. */
    record VatStatusBody(VatStatus vatStatus, Long vatExemptionReasonId) {
    }

    /** Mirrors {@code CustomerController.VatClassOverrideRequest}. */
    record VatClassOverrideBody(Long vatClassId) {
    }

    /** Mirrors {@code AssetController.DepreciationRateRequest}. */
    record DepreciationRateBody(Rate ratePercent) {
    }

    /** Mirrors {@code AssetController.DepreciationStartDateRequest}. */
    record DepreciationStartDateBody(LocalDate depreciationStartDate) {
    }

    /** Mirrors {@code AssetController.DisposalRequest}. */
    record DisposalBody(LocalDate disposalDate) {
    }

    /** Mirrors {@code ProductController.SerialTrackingRequest}. */
    record SerialTrackingBody(boolean serialTracked) {
    }

    /** Mirrors {@code SettlementController.AllocationsRequest}. */
    record AllocationsBody(List<NewAllocation> allocations) {
    }

    /** Mirrors {@code InventoryController.WriteOffReversalRequest}. */
    record WriteOffReversalBody(LocalDate reversalDate, String note) {
    }

    record SellingPriceBody(Money sellingPrice) {
    }

    record EanBody(String ean) {
    }

    record VatClassBody(long vatClassId) {
    }

    record UnitOfMeasureBody(long unitOfMeasureId) {
    }

    record SupplierBody(Long supplierId, String supplierSku) {
    }

    record ContactDetailsBody(String email, String phone) {
    }
}
