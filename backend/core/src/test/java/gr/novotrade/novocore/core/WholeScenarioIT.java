package gr.novotrade.novocore.core;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.backup.BackupRunStatus;
import gr.novotrade.novocore.core.api.backup.BackupService;
import gr.novotrade.novocore.core.api.backup.BackupView;
import gr.novotrade.novocore.core.api.backup.RestoreCheckStatus;
import gr.novotrade.novocore.core.api.backup.RestoreCheckView;
import gr.novotrade.novocore.core.api.banking.BankTransferService;
import gr.novotrade.novocore.core.api.banking.NewBankTransfer;
import gr.novotrade.novocore.core.api.bundle.BundleService;
import gr.novotrade.novocore.core.api.bundle.NewBundleComponent;
import gr.novotrade.novocore.core.api.charge.ChargeTypeService;
import gr.novotrade.novocore.core.api.charge.ChargeTypeView;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewStockWriteOff;
import gr.novotrade.novocore.core.api.inventory.StockLevels;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.WriteOffReason;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.VatTotal;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationView;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptMatch;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptView;
import gr.novotrade.novocore.core.api.purchasing.NewFreightAllocation;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceiptLine;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoice;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoiceLine;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceView;
import gr.novotrade.novocore.core.api.sales.CreditNoteService;
import gr.novotrade.novocore.core.api.sales.CreditNoteView;
import gr.novotrade.novocore.core.api.sales.NewCreditNote;
import gr.novotrade.novocore.core.api.sales.NewCreditNoteLine;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoice;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoiceLine;
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceService;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceView;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.settlement.NewAllocation;
import gr.novotrade.novocore.core.api.settlement.NewSettlement;
import gr.novotrade.novocore.core.api.settlement.SettlementService;
import gr.novotrade.novocore.core.api.settlement.SettlementView;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import gr.novotrade.novocore.core.backup.PostgresTools;
import gr.novotrade.novocore.core.backup.StubDriveServer;
import gr.novotrade.novocore.core.testsupport.LedgerInvariants;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * <strong>One trading year, then every invariant the system has, over the whole database.</strong>
 *
 * <p>Steps 1 to 12 each proved their own feature in isolation, and every one of those tests is
 * still the right place to read about that feature. What none of them can prove is the thing an
 * accounting system is actually judged on: that after a real mixture of work — purchases arriving
 * before and after their invoices, freight allocated onto stock that has partly been sold, a bundle
 * decomposed across its components, a sale nobody had the stock for, corrections, refunds, a
 * write-off — <em>the books still balance and every control account still reconciles</em>. A
 * defect in the interaction between two features is invisible to two tests that each exercise one.
 *
 * <p><strong>The headline assertion is deliberately crude and deliberately total:</strong>
 * {@link #noEntryInTheDatabaseIsUnbalanced()} goes straight to the tables with SQL and asks whether
 * any journal entry anywhere fails to balance. It bypasses every service, every view and every
 * Java-side check, which is what makes it a statement about the data rather than about the code
 * that produced it — the same reason {@code JournalIT}'s probes write raw SQL. It is also the one
 * assertion in this file that would still be worth running if everything else here were deleted.
 *
 * <p><strong>Why this class gets its own database.</strong> It declares a
 * {@code @DynamicPropertySource} for the backup leg, so Spring gives it its own application context
 * and therefore its own container. That is a feature rather than a cost: the sweeps below cover
 * exactly the scenario this class built and nothing else, so "the Inventory control account equals
 * the sum of what the lots carry" can be asserted as an equality rather than as a delta against
 * whatever the rest of the suite happened to leave behind.
 *
 * <p>The scenario is built once, by {@link #aTradingYearHappens()}, and the invariants are separate
 * ordered tests reading static state. One giant test method would report the first failure and hide
 * the rest; this way a break in VAT tells you it is VAT.
 *
 * <p><strong>Requires the PostgreSQL client tools</strong> for the backup leg only, and that one
 * test is skipped without them, for {@code BackupIT}'s reason. Everything else runs regardless.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WholeScenarioIT extends AbstractCoreIntegrationTest {

    /** base64 of exactly 32 bytes. Fixed, and the same one {@code BackupIT} uses. */
    private static final String KEY = "bm92b2NvcmUtdGVzdC1iYWNrdXAta2V5LTMyYnl0ZSE=";

    private static final LocalDate JANUARY = LocalDate.of(2026, 1, 15);
    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 20);
    private static final LocalDate MAY = LocalDate.of(2026, 5, 18);
    private static final LocalDate JUNE = LocalDate.of(2026, 6, 12);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 25);
    private static final LocalDate YEAR_END = LocalDate.of(2026, 12, 31);

    private static StubDriveServer drive;

    /** Everything the year produced, so the invariant tests can name what they are checking. */
    private static Scenario world;

    @Autowired private ChartOfAccountsService chart;
    @Autowired private JournalService journal;
    @Autowired private ProductService products;
    @Autowired private BundleService bundles;
    @Autowired private CustomerService customers;
    @Autowired private SupplierService suppliers;
    @Autowired private InventoryService inventory;
    @Autowired private PurchaseInvoiceService purchaseInvoices;
    @Autowired private GoodsReceiptService goodsReceipts;
    @Autowired private FreightAllocationService freight;
    @Autowired private SalesInvoiceService salesInvoices;
    @Autowired private CreditNoteService creditNotes;
    @Autowired private SettlementService settlements;
    @Autowired private BankTransferService bankTransfers;
    @Autowired private ChargeTypeService chargeTypes;
    @Autowired private VatClassService vatClasses;
    @Autowired private UnitOfMeasureService unitsOfMeasure;
    @Autowired private SettingsService settings;
    @Autowired private BackupService backups;
    @Autowired private PostgresTools postgres;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext applicationContext;

    @TempDir
    static Path backupDirectory;

    @DynamicPropertySource
    static void driveAndKey(DynamicPropertyRegistry registry) throws IOException {
        drive = new StubDriveServer();
        registry.add("novocore.backup.encryption-key", () -> KEY);
        registry.add("novocore.backup.drive.token-endpoint", drive::tokenEndpoint);
        registry.add("novocore.backup.drive.api-base", drive::apiBase);
        registry.add("novocore.backup.drive.upload-base", drive::uploadBase);
    }

    /** The handles the year produced, so an invariant can say which record it disagrees with. */
    private record Scenario(
            long roasterId,
            long carrierId,
            long beansId,
            long grinderId,
            long filtersId,
            long bundleId,
            long serviceId,
            long walkInId,
            long wholesalerId,
            List<Long> beanLotIds,
            long invoiceFirstPurchaseId,
            long goodsFirstPurchaseId,
            long freightAllocationId,
            long onAccountSaleId,
            long cashSaleId,
            long bundleSaleId,
            long oversoldSaleId,
            long creditNoteId,
            long receiptSettlementId,
            long reversedPurchaseId,
            long writtenOffLotId) {
    }

    // =======================================================================================
    // The year
    // =======================================================================================

    @Test
    @Order(1)
    @DisplayName("a trading year happens: buying, landing, bundling, selling, correcting, settling")
    void aTradingYearHappens() {
        long standard = vatClasses.requireByCode("1410").id();   // 24%
        long reduced = vatClasses.requireByCode("1131").id();    // 13%
        long piece = unitsOfMeasure.requireByCode("PIECE").id();

        // --- The parties and the catalogue -------------------------------------------------
        long roasterId = suppliers.create(NewSupplier.domestic("WS Roaster", "EL100000001")).id();
        long carrierId = suppliers.create(NewSupplier.domestic("WS Carrier", "EL100000002")).id();

        // Three products at three different default rates, so VAT precedence has something to
        // resolve rather than one answer that is right by coincidence.
        long beansId = products.create(NewProduct.goods(
                "WS-BEANS", "House blend", piece, reduced, Money.ofEur("18.00"))).id();
        long grinderId = products.create(NewProduct.goods(
                "WS-GRINDER", "Hand grinder", piece, standard, Money.ofEur("120.00"))).id();
        long filtersId = products.create(NewProduct.goods(
                "WS-FILTERS", "Paper filters", piece, standard, Money.ofEur("6.00"))).id();
        long serviceId = products.create(NewProduct.service(
                "WS-SETUP", "Installation", piece, standard, Money.ofEur("40.00"))).id();

        // A bundle of a grinder, filters and the installation service. A bundle holds no stock of
        // its own; only its stocked components constrain how many can be assembled.
        long bundleId = products.create(NewProduct.goods(
                "WS-KIT", "Starter kit", piece, standard, Money.ofEur("150.00"))).id();
        bundles.define(bundleId, List.of(
                NewBundleComponent.one(grinderId),
                NewBundleComponent.of(filtersId, 2L),
                NewBundleComponent.one(serviceId)));

        // A walk-in, and a wholesaler whose own VAT class overrides the product's (Q9 / precedence).
        long walkInId = customers.create(NewCustomer.retail("WS Walk-in", null, null)).id();
        long wholesalerId = customers.create(new NewCustomer(
                "WS Wholesaler", null, null, "EL200000001", VatStatus.DOMESTIC, reduced, null)).id();

        // --- Purchasing, invoice first: GR/IR clears exactly -------------------------------
        PurchaseInvoiceView invoiceFirst = purchaseInvoices.record(NewPurchaseInvoice.of(
                roasterId, "ROAST-2026-001", JANUARY,
                List.of(NewPurchaseInvoiceLine.inventory(
                        beansId, Quantity.of(60L), UnitCost.ofEur("9.000000"), reduced))));
        goodsReceipts.record(NewGoodsReceipt.of(roasterId, MARCH,
                List.of(NewGoodsReceiptLine.pooledAgainst(beansId, Quantity.of(60L),
                        invoiceFirst.lines().getFirst().id()))));

        // --- Purchasing, goods first: the price differs, so a variance posts ----------------
        GoodsReceiptView goodsFirst = goodsReceipts.record(NewGoodsReceipt.of(roasterId, APRIL,
                List.of(NewGoodsReceiptLine.pooled(
                        beansId, Quantity.of(40L), UnitCost.ofEur("10.000000")),
                        NewGoodsReceiptLine.pooled(
                                grinderId, Quantity.of(10L), UnitCost.ofEur("70.000000")),
                        NewGoodsReceiptLine.pooled(
                                filtersId, Quantity.of(100L), UnitCost.ofEur("1.000000")))));
        // The invoice names the deliveries it pays for. There is deliberately no later matching
        // operation (Q41) — matching happens when the second document is created, and it is stated
        // rather than inferred, because inferring it is exactly the silent guess rule 7 forbids.
        List<Long> receiptLineIds =
                goodsFirst.lines().stream().map(line -> line.id()).toList();
        PurchaseInvoiceView goodsFirstInvoice = purchaseInvoices.record(NewPurchaseInvoice.of(
                roasterId, "ROAST-2026-002", APRIL,
                List.of(
                        // 40 beans invoiced at 10.50, received at 10.00 — ADR 0008's variance. The
                        // lot keeps the cost it was received at and the 20.00 difference posts to
                        // Purchase price variance, because re-costing a lot FIFO may already have
                        // consumed is the same problem as editing a posted entry.
                        NewPurchaseInvoiceLine
                                .inventory(beansId, Quantity.of(40L), UnitCost.ofEur("10.500000"),
                                        reduced)
                                .matching(GoodsReceiptMatch.of(
                                        receiptLineIds.get(0), Quantity.of(40L))),
                        NewPurchaseInvoiceLine
                                .inventory(grinderId, Quantity.of(10L), UnitCost.ofEur("70.000000"),
                                        standard)
                                .matching(GoodsReceiptMatch.of(
                                        receiptLineIds.get(1), Quantity.of(10L))),
                        NewPurchaseInvoiceLine
                                .inventory(filtersId, Quantity.of(100L), UnitCost.ofEur("1.000000"),
                                        standard)
                                .matching(GoodsReceiptMatch.of(
                                        receiptLineIds.get(2), Quantity.of(100L))))));
        assertThat(purchaseInvoices.matchesOf(goodsFirstInvoice.id()))
                .as("the invoice recorded after the delivery matched itself against it")
                .hasSize(receiptLineIds.size());

        List<Long> beanLotIds = beanLots(beansId);

        // --- A sale, before the freight lands, so the allocation has to split --------------
        // ADR 0010's whole point: the part of the freight belonging to stock already gone cannot
        // ride on a unit cost, because those units are not there to carry it.
        long onAccountSaleId = salesInvoices.record(NewSalesInvoice.of(
                wholesalerId, SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
                "GO-2026-0001", MAY,
                List.of(
                        // Product default 13%, customer override 13%, and an explicit 24% on the
                        // third line: all three levels of the precedence rule, on one document.
                        NewSalesInvoiceLine.product(
                                beansId, Quantity.of(20L), UnitCost.ofEur("18.000000")),
                        NewSalesInvoiceLine.product(
                                grinderId, Quantity.of(2L), UnitCost.ofEur("120.000000")),
                        NewSalesInvoiceLine.product(
                                filtersId, Quantity.of(10L), UnitCost.ofEur("6.000000"))
                                .atVatClass(standard),
                        NewSalesInvoiceLine.charge(deliveryCharge().id(), Money.ofEur("5.00"))))).id();

        // --- Freight: a carrier's invoice, then allocated across the bean lots --------------
        PurchaseInvoiceView carrierInvoice = purchaseInvoices.record(NewPurchaseInvoice.of(
                carrierId, "ACS-2026-77", MAY,
                List.of(NewPurchaseInvoiceLine.expense(
                        chart.requireAccount(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED).id(),
                        Money.ofEur("120.00"), standard))));
        FreightAllocationView allocation = freight.allocate(NewFreightAllocation.of(
                carrierInvoice.lines().getFirst().id(), Money.ofEur("120.00"), MAY,
                "Inbound freight on the two bean deliveries", beanLotIds));

        // --- A cash sale, and the bundle -----------------------------------------------------
        long cashSaleId = salesInvoices.record(NewSalesInvoice.of(
                walkInId, SalesChannel.STORE_AND_PHONE, SettlementMethod.CASH,
                "GO-2026-0002", JUNE,
                List.of(NewSalesInvoiceLine.product(
                        beansId, Quantity.of(5L), UnitCost.ofEur("18.000000"))))).id();

        long bundleSaleId = salesInvoices.record(NewSalesInvoice.of(
                walkInId, SalesChannel.STORE_AND_PHONE, SettlementMethod.CARD_POS,
                "GO-2026-0003", JUNE,
                List.of(NewSalesInvoiceLine.product(
                        bundleId, Quantity.of(1L), UnitCost.ofEur("150.000000"))))).id();

        // --- Overselling: Q17's negative stock, recorded and flagged rather than blocked ----
        long oversoldSaleId = salesInvoices.record(NewSalesInvoice.of(
                walkInId, SalesChannel.SKROUTZ, SettlementMethod.SKROUTZ,
                "GO-2026-0004", JULY,
                List.of(NewSalesInvoiceLine.product(
                        filtersId, Quantity.of(500L), UnitCost.ofEur("6.000000"))))).id();

        // --- A credit note that brings stock back --------------------------------------------
        SalesInvoiceView onAccountSale = salesInvoices.require(onAccountSaleId);
        long creditNoteId = creditNotes.record(NewCreditNote.of(onAccountSaleId, "GO-CN-2026-0001",
                JULY,
                List.of(NewCreditNoteLine.returning(
                        onAccountSale.lines().getFirst().id(), Quantity.of(4L),
                        UnitCost.ofEur("18.000000"))))).id();

        // --- Settlement: money in against the open invoice, money out to the supplier -------
        SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                wholesalerId, chart.requireAccount(AccountSystemKey.CASH).id(), JULY,
                Money.ofEur("200.00"),
                List.of(NewAllocation.againstSalesInvoice(onAccountSaleId, Money.ofEur("200.00")))));
        settlements.record(NewSettlement.paymentTo(
                roasterId, chart.requireAccount(AccountSystemKey.CASH).id(), JULY,
                Money.ofEur("300.00"),
                List.of(NewAllocation.againstPurchaseInvoice(
                        invoiceFirst.id(), Money.ofEur("300.00")))));
        settlements.allocateCreditNote(creditNoteId, onAccountSaleId, Money.ofEur("81.36"));

        // --- A correction: a purchase invoice reversed in full -------------------------------
        PurchaseInvoiceView strayInvoice = purchaseInvoices.record(NewPurchaseInvoice.of(
                carrierId, "ACS-2026-78", JULY,
                List.of(NewPurchaseInvoiceLine.expense(
                        chart.requireAccount(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED).id(),
                        Money.ofEur("40.00"), standard))));
        purchaseInvoices.reverse(strayInvoice.id(), JULY, "Billed to the wrong company");

        // --- Damage and a write-off ----------------------------------------------------------
        // Moving to Damaged Goods posts nothing by decision (step 3); only the write-off
        // derecognises. The lot chosen has no allocated landed cost, so ADR 0011's refusal on a
        // re-costed lot is not in play here — that refusal has its own test.
        // Grinders rather than filters: the filters lot was emptied by the oversold sale, and a lot
        // with nothing in it has nothing to write off. Grinders also carry no allocated landed
        // cost, so ADR 0011's refusal on a re-costed lot is not in play — that refusal has its own
        // test and belongs there, not tangled into this one.
        long damagedLot = inventory.openLotsOf(grinderId).getFirst().id();
        inventory.moveLot(damagedLot, StockLocation.DAMAGED_GOODS);
        inventory.writeOff(NewStockWriteOff
                .pooled(damagedLot, Quantity.of(2L), WriteOffReason.DAMAGE, JULY)
                .withNote("Dropped in the stockroom"));

        // --- Moving our own money ------------------------------------------------------------
        bankTransfers.record(NewBankTransfer.of(
                chart.requireAccount(AccountSystemKey.CASH).id(),
                chart.requireAccount(AccountSystemKey.PARTNER_CLEARING_POS).id(),
                JULY, Money.ofEur("50.00")).describedAs("Float to the POS clearing account"));

        world = new Scenario(roasterId, carrierId, beansId, grinderId, filtersId, bundleId,
                serviceId, walkInId, wholesalerId, beanLotIds, invoiceFirst.id(),
                goodsFirstInvoice.id(), allocation.id(), onAccountSaleId, cashSaleId, bundleSaleId,
                oversoldSaleId, creditNoteId, receipt.id(), strayInvoice.id(), damagedLot);
    }

    // =======================================================================================
    // The invariants — over the whole database, not over what the acts above returned
    // =======================================================================================

    /**
     * <strong>Every invariant that holds over any database, asked of this one.</strong>
     *
     * <p>These used to be nine separate test methods here. They are now defined once in
     * {@link LedgerInvariants} and shared with step 15's HTTP-driven scenario, because the same
     * questions have to be asked of a database built through the REST surface and two copies of
     * "the Inventory account equals what the lots carry" are two copies that can disagree.
     *
     * <p>A {@code @TestFactory} rather than a loop inside one test: JUnit reports one result per
     * invariant, so a break in VAT still says it is VAT — which is the property this class was
     * built around and the reason {@link LedgerInvariants} has no {@code sweepAll} method.
     */
    @TestFactory
    @Order(10)
    @DisplayName("the universal ledger invariants")
    Stream<DynamicTest> theUniversalInvariantsHold() {
        return LedgerInvariants.from(applicationContext).all(JANUARY, YEAR_END).stream()
                .map(invariant -> DynamicTest.dynamicTest(invariant.name(), invariant::run));
    }

    @Test
    @Order(12)
    @DisplayName("the scenario actually produced a substantial ledger, so the sweeps mean something")
    void theLedgerIsNotTrivial() {
        // Guards the failure mode every whole-system test has: passing because it did nothing. An
        // empty database satisfies every invariant above perfectly. The thresholds are this
        // scenario's own claim about itself, which is why they are passed in rather than built in.
        LedgerInvariants.from(applicationContext).theLedgerIsNotTrivial(YEAR_END, 15L, 60L, 10);
    }

    @Test
    @Order(31)
    @DisplayName("GR/IR holds exactly the deliveries still waiting for an invoice, and vice versa")
    void grIrHoldsOnlyTheTimingGap() {
        // ADR 0004's clearing account earning its name. Everything matched has netted out; what is
        // left is the timing difference, and both halves of it are queryable rather than merely
        // asserted to exist.
        //
        // Zero tolerance, stated rather than assumed: step 8 accepted that one receipt line matched
        // by two invoices can leave a cent behind, and this year contains no such partial match.
        LedgerInvariants.from(applicationContext)
                .grIrHoldsOnlyTheTimingGap(YEAR_END, Money.zero(Money.EUR));
    }

    @Test
    @Order(32)
    @DisplayName("both variances actually posted, in the directions this year should produce them")
    void theVariancesArePresentAndPointTheRightWay() {
        // That each variance account equals what the documents recorded is universal, and moved to
        // LedgerInvariants. What stays here is the part that is a claim about *this* year: that the
        // year actually exercised both of them, and in which direction. Without these the
        // equalities above would be satisfied perfectly by two accounts holding nothing.
        assertThat(journal.balanceOf(AccountSystemKey.PURCHASE_PRICE_VARIANCE, YEAR_END).net()
                .isPositive())
                .as("the goods-first invoice charged more than the receipt provisioned, so a "
                        + "variance must actually have posted — otherwise this test proves nothing")
                .isTrue();

        // Landed cost variance has two contributors, and only the whole-scenario view shows both:
        // the allocations put the share belonging to stock already gone into it (ADR 0010), and
        // ADR 0011's catch-up takes some back out again when returned stock re-enters a re-costed
        // lot. Netting them and comparing against the allocations alone was wrong, and this test
        // found it — the account was 18.38 against 22.98 allocated, the 4.60 difference being
        // exactly the credit note's four returned units catching their freight up.
        long landedVarianceAccount =
                chart.requireAccount(AccountSystemKey.LANDED_COST_VARIANCE).id();
        Money allocatedToVariance = Money.zero(Money.EUR);
        Money caughtUpByReturns = Money.zero(Money.EUR);
        for (var line : journal.linesOf(landedVarianceAccount, JANUARY, YEAR_END)) {
            if (line.source() == JournalSource.FREIGHT_ALLOCATION) {
                allocatedToVariance = allocatedToVariance.plus(line.debitPositiveEffect());
            } else {
                caughtUpByReturns = caughtUpByReturns.plus(line.debitPositiveEffect());
            }
        }

        assertThat(allocatedToVariance.isPositive())
                .as("beans had been sold before the freight was allocated, so part of the freight "
                        + "belonged to stock that was already gone")
                .isTrue();
        assertThat(caughtUpByReturns.isNegative())
                .as("the credit note returned stock into a re-costed lot, so ADR 0011's catch-up "
                        + "credited some of the variance back")
                .isTrue();
    }

    @Test
    @Order(33)
    @DisplayName("the freight invoice is fully spent and the unallocated account is clear")
    void freightIsFullyAllocated() {
        FreightAllocationView allocation = freight.require(world.freightAllocationId());
        assertThat(allocation.capitalised().plus(allocation.variance()))
                .as("what the freight became: cost on the lots plus cost on stock already gone")
                .isEqualTo(allocation.amount());
        assertThat(freight.unallocatedAmountOf(allocation.purchaseInvoiceLineId()).isZero())
                .as("the carrier's line has nothing left to allocate")
                .isTrue();
        assertThat(journal
                .balanceOf(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED, YEAR_END).isZero())
                .as("the expected-to-clear account has cleared: the only other freight invoice "
                        + "was reversed in full")
                .isTrue();
    }

    @Test
    @Order(40)
    @DisplayName("VAT precedence resolved at all three levels, and each line says which one won")
    void vatPrecedenceIsRecordedPerLine() {
        SalesInvoiceView invoice = salesInvoices.require(world.onAccountSaleId());
        Map<String, VatClassSource> byProduct = new java.util.LinkedHashMap<>();
        invoice.lines().forEach(line -> byProduct.put(
                line.productSku() == null ? line.chargeTypeName() : line.productSku(),
                line.vatClassSource()));

        // The beans' own default is 13% and so is the customer's override; the customer wins,
        // because precedence is about which level supplied the class and not about which number
        // came out. Recording only the number would make "why is this line at 13%?" unanswerable.
        assertThat(byProduct.get("WS-BEANS")).isEqualTo(VatClassSource.CUSTOMER);
        assertThat(byProduct.get("WS-GRINDER")).isEqualTo(VatClassSource.CUSTOMER);
        assertThat(byProduct.get("WS-FILTERS")).isEqualTo(VatClassSource.INVOICE_LINE);

        // And the cash sale, where the customer has no override, falls to the product.
        assertThat(salesInvoices.require(world.cashSaleId()).lines().getFirst().vatClassSource())
                .isEqualTo(VatClassSource.PRODUCT);
    }

    @Test
    @Order(41)
    @DisplayName("output and input VAT are separate figures, and each equals its own lines")
    void vatTotalsAgreeWithTheVatAccounts() {
        // Q14: never netted. The two figures a VAT return is made of are read back off the
        // dimension every VAT line carries, and compared against the accounts those lines posted
        // to — two readings of the same rows, which is the only kind of check worth having here.
        List<VatTotal> totals = journal.vatTotals(JANUARY, YEAR_END);

        Money output = Money.zero(Money.EUR);
        Money input = Money.zero(Money.EUR);
        for (VatTotal total : totals) {
            switch (total.direction()) {
                case OUTPUT -> output = output.plus(total.vatAmount());
                case INPUT -> input = input.plus(total.vatAmount());
            }
        }

        // That each account equals its own side is universal and moved to LedgerInvariants. What
        // stays is this year's own claim: that it actually charged and suffered VAT, without which
        // the equality is satisfied by two empty accounts.
        assertThat(totals).as("VAT totals over the year").isNotEmpty();
        assertThat(output.isPositive()).as("the year charged output VAT").isTrue();
        assertThat(input.isPositive()).as("the year suffered input VAT").isTrue();
    }

    @Test
    @Order(51)
    @DisplayName("the receipt this year recorded did not over-allocate")
    void theReceiptDidNotOverAllocate() {
        // The sweep over every settlement is universal and moved to LedgerInvariants; this names
        // the one document the year built, so a failure says which.
        SettlementView receipt = settlements.require(world.receiptSettlementId());
        assertThat(receipt.allocatedAmount()).isLessThanOrEqualTo(receipt.amount());
    }

    @Test
    @Order(60)
    @DisplayName("the oversold product reads negative, is flagged, and cost nothing it did not have")
    void negativeStockIsRecordedRatherThanHidden() {
        // Q17 / ADR 0008. The three halves of the answer: the number is honest, the case is
        // findable, and no COGS was invented for stock that was never there.
        StockLevels filters = inventory.stockOf(world.filtersId());
        assertThat(filters.isOversold())
                .as("filters stock after selling 500 against a hundred received")
                .isTrue();
        assertThat(filters.total().isNegative()).isTrue();

        assertThat(inventory.consumptionsWithShortfall())
                .as("the oversold sale is findable")
                .isNotEmpty()
                .allSatisfy(consumption ->
                        assertThat(consumption.shortfallQuantity().isPositive()).isTrue());
    }

    @Test
    @Order(61)
    @DisplayName("the bundle sold as one line and is stored decomposed into its components")
    void theBundleIsMaterialisedOnTheInvoice() {
        // Brief §5's "linked, not duplicated", as a property of the data: either level gives the
        // same revenue, so adding them together is visibly double-counting. Read off the stored
        // components rather than recomputed, which is what makes dissolving the bundle later safe.
        SalesInvoiceView sale = salesInvoices.require(world.bundleSaleId());
        var bundleLine = sale.lines().getFirst();
        assertThat(bundleLine.components())
                .as("the components stored against the bundle line")
                .hasSize(3);

        Money fromComponents = Money.zero(Money.EUR);
        for (var component : bundleLine.components()) {
            fromComponents = fromComponents.plus(component.allocatedAmount());
        }
        assertThat(fromComponents)
                .as("component revenue vs the bundle line it was pushed down from")
                .isEqualTo(bundleLine.netAmount());
    }

    @Test
    @Order(62)
    @DisplayName("a reversal is an exact mirror, and leaves the ledger where it found it")
    void reversalsNetToNothing() {
        PurchaseInvoiceView reversed = purchaseInvoices.require(world.reversedPurchaseId());
        assertThat(reversed.isReversed()).isTrue();

        var original = journal.requireEntry(reversed.journalEntryId());
        var mirror = journal.requireEntry(
                purchaseInvoices.require(reversed.reversedByInvoiceId()).journalEntryId());

        assertThat(mirror.totalDebits()).isEqualTo(original.totalCredits());
        assertThat(mirror.totalCredits()).isEqualTo(original.totalDebits());
        assertThat(mirror.lines()).hasSameSizeAs(original.lines());
    }

    @Test
    @Order(63)
    @DisplayName("a credit note returns stock at the cost it left at, and credits the VAT charged")
    void theCreditNoteIsConsistentWithTheSaleItCorrects() {
        CreditNoteView note = creditNotes.require(world.creditNoteId());
        assertThat(note.salesInvoiceId()).isEqualTo(world.onAccountSaleId());
        assertThat(note.grossTotal().isPositive()).isTrue();
        // Always credits AR, even against a cash sale — the money going back out is a settlement.
        assertThat(journal.requireEntry(note.journalEntryId()).lines())
                .anySatisfy(line -> assertThat(line.accountId()).isEqualTo(
                        chart.requireAccount(AccountSystemKey.ACCOUNTS_RECEIVABLE).id()));
    }

    @Test
    @Order(64)
    @DisplayName("the write-off derecognised the stock and moved it out of Inventory in one step")
    void theWriteOffBothReducedStockAndPosted() {
        assertThat(inventory.writeOffsOf(world.writtenOffLotId()))
                .singleElement()
                .satisfies(writeOff -> {
                    assertThat(writeOff.reason()).isEqualTo(WriteOffReason.DAMAGE);
                    assertThat(writeOff.journalEntryId()).isNotNull();
                });
        assertThat(journal.balanceOf(AccountSystemKey.INVENTORY_WRITE_OFF, YEAR_END).net()
                .isPositive())
                .as("the write-off account carries the loss")
                .isTrue();
    }

    // =======================================================================================
    // And the whole thing survives a backup and a restore
    // =======================================================================================

    @Test
    @Order(90)
    @DisplayName("the year backs up, restores into a fresh database, and still balances there")
    void theWholeYearRestoresAndStillBalances() {
        Assumptions.assumeTrue(postgres.pgDumpVersion().isPresent(),
                "pg_dump is not on the PATH. Install postgresql-client-17 to run this; the runtime "
                        + "image already has it.");

        settings.put("backup.local-directory", backupDirectory.toString());
        settings.put("backup.drive.primary.folder-id", "folder-whole-scenario");
        settings.put("backup.drive.primary.client-id", "client-whole-scenario");
        settings.putSecret("backup.drive.primary.client-secret", "secret-whole-scenario");
        settings.putSecret("backup.drive.primary.refresh-token", "refresh-whole-scenario");

        BackupView backup = backups.runNow();
        assertThat(backup.status()).isEqualTo(BackupRunStatus.SUCCEEDED);

        // This is what step 12 exists for, and it is worth a great deal more here than in BackupIT:
        // there, the restore check asserts that a nearly-empty ledger balances. Here it restores a
        // year of interacting documents and asserts the restored copy still balances — which is the
        // difference between "the file restored" and "the books restored".
        RestoreCheckView check = backups.verifyRestore(backup.id());
        assertThat(check.status()).isEqualTo(RestoreCheckStatus.PASSED);
        assertThat(check.findings())
                .as("the restore check states, in its own words, that the restored ledger balances")
                .anySatisfy(finding -> assertThat(finding).contains("restored ledger balances"));

        // And that it restored the year rather than an empty schema. The verifier compares row
        // counts against the live database and throws if they differ, so a PASSED check on a
        // ledger this size is the claim; this reads its own report back to make that visible in
        // the failure message rather than buried in a status enum.
        long liveEntries = jdbc.queryForObject("SELECT count(*) FROM journal_entry", Long.class);
        assertThat(check.findings())
                .anySatisfy(finding -> assertThat(finding)
                        .startsWith("journal_entry:")
                        .contains(String.format("%,d", liveEntries)));
    }

    // =======================================================================================
    // Fixtures and readers
    // =======================================================================================

    private ChargeTypeView deliveryCharge() {
        return chargeTypes.active().stream()
                .filter(type -> type.name().toLowerCase(java.util.Locale.ROOT).contains("delivery"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "V7 seeds a Delivery charge type; it is missing."));
    }

    private List<Long> beanLots(long beansId) {
        return inventory.lotsOf(beansId).stream().map(InventoryLotView::id).toList();
    }

}
