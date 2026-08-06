package gr.novotrade.novocore.core.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.testsupport.SalesDocumentFixture;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.bundle.BundleService;
import gr.novotrade.novocore.core.api.bundle.NewBundleComponent;
import gr.novotrade.novocore.core.api.charge.ChargeTypeService;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerSystemKey;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.document.DocumentSeriesNotFoundException;
import gr.novotrade.novocore.core.api.document.SalesDocumentSeriesService;
import gr.novotrade.novocore.core.api.document.SalesDocumentTypeService;
import gr.novotrade.novocore.core.api.inventory.InvalidStockConsumptionException;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitNotFoundException;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionView;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.VatTotal;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.sales.InvalidSalesInvoiceException;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoice;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoiceLine;
import gr.novotrade.novocore.core.api.sales.PaymentMethodService;
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceLineView;
import gr.novotrade.novocore.core.api.sales.SalesInvoicePreview;
import gr.novotrade.novocore.core.api.sales.SalesInvoicePreviewLine;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceService;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceSort;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceView;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.PageResponse;
import gr.novotrade.novocore.core.api.shared.SortDirection;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassNotDeterminableException;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.ledger.VatDirection;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Recording sales — and the four things step 9 was carrying obligations about.
 *
 * <p><strong>What is actually being defended.</strong> That a sale produces two entries and both are
 * right; that the Output VAT lines carry the class and base a VAT return needs (the step 7 obligation);
 * that a serialized sale names its machines and records who bought them (the step 6 obligation); and
 * that a rounding difference bigger than a residual cannot be recorded without somebody agreeing to it
 * (Q15's remainder).
 */
class SalesInvoiceIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 20);

    /**
     * A date used by exactly one test. These tests share a database and nothing rolls back, so a
     * period query over a shared date would pick up every other test's invoices too.
     */
    private static final LocalDate VAT_DAY = LocalDate.of(2026, 9, 9);

    /** Document numbers have to be unique across the whole test class, since nothing rolls back. */
    private static final AtomicInteger NUMBERS = new AtomicInteger();

    @Autowired
    private SalesInvoiceService salesInvoices;

    @Autowired
    private CustomerService customers;

    @Autowired
    private ProductService products;

    @Autowired
    private BundleService bundles;

    @Autowired
    private ChargeTypeService chargeTypes;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private JournalService journal;

    @Autowired
    private ChartOfAccountsService chartOfAccounts;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    @Autowired
    private SettingsService settings;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SalesDocumentTypeService salesDocumentTypes;

    @Autowired
    private SalesDocumentSeriesService salesSeries;

    @Autowired
    private PaymentMethodService paymentMethods;

    /**
     * The series every sale in this class is recorded in — R1b made one mandatory.
     *
     * <p>⚠️ <strong>Stock-moving, and that is what keeps this class's assertions true rather than
     * merely passing.</strong> Every test here predates R1b and was written against a sale that
     * consumes stock; giving them a non-stock-moving series would silently stop the consumptions
     * they assert on. The channel each test used to pass is now the channel of the series it names,
     * so the accounts they assert are the accounts they always asserted.
     */
    private SalesDocumentFixture documents;

    private long series(SalesChannel channel) {
        if (documents == null) {
            documents = new SalesDocumentFixture(salesDocumentTypes, salesSeries, "SIIT");
        }
        return documents.stockMoving(channel);
    }

    /** The seeded {@code Delivery} charge type (V7), by name — its id is not fixed. */
    private long deliveryChargeTypeId() {
        return chargeTypes.all().stream()
                .filter(type -> type.name().equals("Delivery"))
                .findFirst().orElseThrow().id();
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    private String number() {
        return "SI-" + NUMBERS.incrementAndGet() + "-" + System.nanoTime();
    }

    private CustomerView customer(String name) {
        return customers.create(NewCustomer.retail("SIIT — " + name, null, null));
    }

    private ProductView goods(String sku, String price) {
        return products.create(NewProduct.goods(sku, sku + " goods",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur(price)));
    }

    private ProductView service(String sku, String price) {
        return products.create(NewProduct.service(sku, sku + " service",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur(price)));
    }

    private void stock(long productId, long quantity, String unitCost) {
        inventory.receive(NewInventoryLot.pooled(productId, Quantity.of(quantity),
                UnitCost.ofEur(unitCost), MARCH, StockLocation.INVENTORY));
    }

    private long accountId(AccountSystemKey key) {
        return chartOfAccounts.requireAccount(key).id();
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("what one sale posts")
    class Posting {

        @Test
        @DisplayName("revenue, output VAT and receivable, with the channel deciding the sales account")
        void ordinaryCreditSale() {
            CustomerView buyer = customer("Ordinary");
            ProductView beans = goods("SIIT-01", "50.00");
            stock(beans.id(), 10L, "20.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(2L), UnitCost.ofEur("50.000000")))));

            assertThat(invoice.netTotal()).isEqualTo(Money.ofEur("100.00"));
            assertThat(invoice.vatTotal()).isEqualTo(Money.ofEur("24.00"));
            assertThat(invoice.grossTotal()).isEqualTo(Money.ofEur("124.00"));
            assertThat(invoice.bornSettled()).isFalse();

            JournalEntryView entry = journal.requireEntry(invoice.journalEntryId());
            assertThat(entry.source()).isEqualTo(JournalSource.SALES_INVOICE);
            assertThat(entry.lines())
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE));
                        assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                        assertThat(line.amount()).isEqualTo(Money.ofEur("124.00"));
                        assertThat(line.subLedgerRef())
                                .isEqualTo(SubLedgerRef.customer(buyer.id()));
                    })
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.SALES_STORE_AND_PHONE));
                        assertThat(line.side()).isEqualTo(BalanceSide.CREDIT);
                        assertThat(line.amount()).isEqualTo(Money.ofEur("100.00"));
                    });
        }

        @Test
        @DisplayName("a sale produces two entries — revenue in one, cost of goods sold in another")
        void aSaleProducesTwoEntries() {
            CustomerView buyer = customer("Two entries");
            ProductView beans = goods("SIIT-02", "50.00");
            stock(beans.id(), 10L, "20.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT, number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(3L), UnitCost.ofEur("50.000000")))));

            // The obligation step 8 recorded, made concrete: InventoryService.consume posts its own
            // entry, because reducing lots without posting is the "half is worse than neither"
            // problem the write-off settled. The link is the consumption, one direction.
            Long consumptionId = invoice.lines().getFirst().stockConsumptionId();
            assertThat(consumptionId).isNotNull();

            StockConsumptionView consumption = inventory.requireConsumption(consumptionId);
            assertThat(consumption.totalCost()).isEqualTo(Money.ofEur("60.00"));
            assertThat(consumption.journalEntryId()).isNotEqualTo(invoice.journalEntryId());

            JournalEntryView cost = journal.requireEntry(consumption.journalEntryId());
            assertThat(cost.lines()).anySatisfy(line -> {
                assertThat(line.accountId())
                        .isEqualTo(accountId(AccountSystemKey.COST_OF_GOODS_SOLD));
                assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                assertThat(line.amount()).isEqualTo(Money.ofEur("60.00"));
            });
        }

        @Test
        @DisplayName("a service credits Services, not the channel's Sales account, and takes no stock")
        void serviceCreditsItsOwnAccount() {
            CustomerView buyer = customer("Service");
            ProductView repair = service("SIIT-03", "80.00");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            repair.id(), Quantity.of(1L), UnitCost.ofEur("80.000000")))));

            assertThat(invoice.lines().getFirst().stockConsumptionId()).isNull();
            assertThat(journal.requireEntry(invoice.journalEntryId()).lines())
                    .anySatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.SERVICES_INCOME)))
                    .noneSatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.SALES_STORE_AND_PHONE)));
        }

        @Test
        @DisplayName("a charge line credits its charge type's income account at its own rate (Q33)")
        void chargeLineUsesItsOwnAccountAndRate() {
            CustomerView buyer = customer("Delivery");
            ProductView beans = goods("SIIT-04", "50.00");
            stock(beans.id(), 5L, "20.000000");
            long delivery = chargeTypes.all().stream()
                    .filter(type -> type.name().equals("Delivery"))
                    .findFirst().orElseThrow().id();

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT, number(), JULY,
                    List.of(
                            NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of(1L), UnitCost.ofEur("50.000000")),
                            NewSalesInvoiceLine.charge(delivery, Money.ofEur("5.00")))));

            assertThat(invoice.lines()).hasSize(2);
            assertThat(invoice.lines().get(1).chargeTypeName()).isEqualTo("Delivery");
            // Q33, settled with the accountant: a fee's rate is independent of the goods on the
            // invoice. Nothing derives it from the lines around it, and nothing should.
            assertThat(invoice.lines().get(1).vatAmount()).isEqualTo(Money.ofEur("1.20"));
        }
    }

    @Nested
    @DisplayName("the VAT dimension — the step 7 obligation")
    class Vat {

        @Test
        @DisplayName("every output VAT line carries its class and taxable base")
        void outputVatCarriesItsDimension() {
            CustomerView buyer = customer("VAT dimension");
            ProductView standard = goods("SIIT-05", "100.00");
            ProductView reduced = products.create(NewProduct.goods("SIIT-06", "Reduced rate",
                    unitsOfMeasure.requireByCode("PIECE").id(),
                    vatClasses.requireByCode("1131").id(), Money.ofEur("100.00")));
            stock(standard.id(), 5L, "40.000000");
            stock(reduced.id(), 5L, "40.000000");

            salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.SKROUTZ), SettlementMethod.ON_ACCOUNT, number(), VAT_DAY,
                    List.of(
                            NewSalesInvoiceLine.product(
                                    standard.id(), Quantity.of(1L), UnitCost.ofEur("100.000000")),
                            NewSalesInvoiceLine.product(
                                    reduced.id(), Quantity.of(1L), UnitCost.ofEur("100.000000")))));

            // Without the class on the line these two would be one indistinguishable amount against
            // one account, and a VAT return assembled from the ledger would silently understate.
            List<VatTotal> totals = journal.vatTotals(VAT_DAY, VAT_DAY);
            assertThat(totals).filteredOn(total -> total.direction() == VatDirection.OUTPUT)
                    .hasSizeGreaterThanOrEqualTo(2)
                    .anySatisfy(total -> {
                        assertThat(total.vatClassId())
                                .isEqualTo(vatClasses.requireByCode("1410").id());
                        assertThat(total.taxableBase()).isEqualTo(Money.ofEur("100.00"));
                        assertThat(total.vatAmount()).isEqualTo(Money.ofEur("24.00"));
                    })
                    .anySatisfy(total -> {
                        assertThat(total.vatClassId())
                                .isEqualTo(vatClasses.requireByCode("1131").id());
                        assertThat(total.vatAmount()).isEqualTo(Money.ofEur("13.00"));
                    });
        }

        @Test
        @DisplayName("the precedence rule runs, and which level won is recorded on the line")
        void precedenceIsRecorded() {
            ProductView beans = goods("SIIT-07", "100.00");
            stock(beans.id(), 10L, "40.000000");

            CustomerView overridden = customers.changeVatClassOverride(
                    customer("Override").id(), vatClasses.requireByCode("1131").id());

            SalesInvoiceView fromCustomer = salesInvoices.record(NewSalesInvoice.of(
                    overridden.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("100.000000")))));
            assertThat(fromCustomer.lines().getFirst().vatClassSource())
                    .isEqualTo(VatClassSource.CUSTOMER);
            assertThat(fromCustomer.vatTotal()).isEqualTo(Money.ofEur("13.00"));

            SalesInvoiceView fromLine = salesInvoices.record(NewSalesInvoice.of(
                    overridden.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine
                            .product(beans.id(), Quantity.of(1L), UnitCost.ofEur("100.000000"))
                            .atVatClass(vatClasses.requireByCode("1091").id()))));
            assertThat(fromLine.lines().getFirst().vatClassSource())
                    .isEqualTo(VatClassSource.INVOICE_LINE);
            assertThat(fromLine.vatTotal()).isEqualTo(Money.ofEur("9.00"));
        }

        @Test
        @DisplayName("a line with no class at any level is refused rather than assumed to be 24%")
        void noFallbackRate() {
            // Deliberately unreachable through the ordinary path — a product's default class is
            // required — so the rule is proven where it can be: the precedence function itself.
            assertThatExceptionOfType(VatClassNotDeterminableException.class)
                    .isThrownBy(() -> gr.novotrade.novocore.core.api.tax.VatClassPrecedence
                            .resolve(null, null, null));
        }
    }

    @Nested
    @DisplayName("settlement methods — brief §6")
    class Settlement {

        @Test
        @DisplayName("a cash sale debits the cash box and is born fully settled")
        void cashSaleIsBornSettled() {
            CustomerView retail = customers.require(CustomerSystemKey.RETAIL_WALK_IN);
            ProductView beans = goods("SIIT-08", "20.00");
            stock(beans.id(), 10L, "8.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    retail.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.CASH,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("20.000000")))));

            assertThat(invoice.bornSettled()).isTrue();
            assertThat(journal.requireEntry(invoice.journalEntryId()).lines())
                    .anySatisfy(line -> {
                        assertThat(line.accountId()).isEqualTo(accountId(AccountSystemKey.CASH));
                        assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                    })
                    .noneSatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE)));
        }

        @Test
        @DisplayName("a card sale sits in POS clearing until the acquirer remits")
        void cardSaleGoesToClearing() {
            CustomerView retail = customers.require(CustomerSystemKey.RETAIL_WALK_IN);
            ProductView beans = goods("SIIT-09", "20.00");
            stock(beans.id(), 10L, "8.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    retail.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.CARD_POS,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("20.000000")))));

            assertThat(journal.requireEntry(invoice.journalEntryId()).lines())
                    .anySatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.PARTNER_CLEARING_POS)));
        }

        @Test
        @DisplayName("a bank deposit stays open against AR — it is not marked paid on the customer's word")
        void bankDepositStaysOpen() {
            CustomerView buyer = customer("Bank deposit");
            ProductView beans = goods("SIIT-10", "20.00");
            stock(beans.id(), 10L, "8.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.BANK_DEPOSIT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("20.000000")))));

            assertThat(invoice.bornSettled()).isFalse();
            assertThat(journal.requireEntry(invoice.journalEntryId()).lines())
                    .anySatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE)));
        }

        @Test
        @DisplayName("a cash sale at the legal limit is blocked, not flagged")
        void cashLimitIsHardBlocked() {
            CustomerView retail = customers.require(CustomerSystemKey.RETAIL_WALK_IN);
            ProductView machine = goods("SIIT-11", "600.00");
            stock(machine.id(), 5L, "300.000000");

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.record(NewSalesInvoice.of(
                            retail.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.CASH,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    machine.id(), Quantity.of(1L), UnitCost.ofEur("600.000000"))))))
                    .withMessageContaining("legal cash limit");
        }
    }

    @Nested
    @DisplayName("paging — tier A, because a year of invoicing is tens of thousands of rows")
    class Paging {

        /** Twelve invoices, all on ONE date, so every ordering below has ties to break. */
        private List<Long> twelveOnOneDay(String skuPrefix) {
            CustomerView buyer = customer("Paging " + skuPrefix);
            ProductView beans = goods(skuPrefix, "10.00");
            stock(beans.id(), 100L, "4.000000");

            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                ids.add(salesInvoices.record(NewSalesInvoice.of(
                        buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                        number(), JULY,
                        List.of(NewSalesInvoiceLine.product(
                                beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))))
                        .id());
            }
            return ids;
        }

        @Test
        @DisplayName("a page reports the whole list's size, not the page's")
        void aPageKnowsHowBigTheListIs() {
            List<Long> all = twelveOnOneDay("SIIT-PG1");
            long customerId = salesInvoices.require(all.getFirst()).customerId();

            PageResponse<SalesInvoiceView> first =
                    salesInvoices.pageOfCustomer(customerId, null, PageRequest.of(0, 5));

            assertThat(first.items()).hasSize(5);
            assertThat(first.totalElements())
                    .as("the count is of the list, which is what a table's 'of 34' comes from")
                    .isEqualTo(12);
            assertThat(first.totalPages()).isEqualTo(3);
            assertThat(first.page()).isZero();
            assertThat(first.size())
                    .as("the size ASKED FOR, not the number returned — a client reading this as "
                            + "'how many are here' would think it had reached the end early")
                    .isEqualTo(5);
            assertThat(first.hasNext()).isTrue();
            assertThat(first.hasPrevious()).isFalse();

            PageResponse<SalesInvoiceView> last =
                    salesInvoices.pageOfCustomer(customerId, null, PageRequest.of(2, 5));
            assertThat(last.items()).as("the last page holds the remainder").hasSize(2);
            assertThat(last.size()).isEqualTo(5);
            assertThat(last.hasNext()).isFalse();
            assertThat(last.hasPrevious()).isTrue();
        }

        /**
         * <strong>The assertion the ordering design exists for.</strong>
         *
         * <p>All twelve invoices share one date. Ordered by date alone the rows are tied, and
         * PostgreSQL is free to return tied rows in a different order per query — so successive
         * pages could show one invoice twice and never show another. That is a wrong answer that
         * looks entirely plausible on screen.
         *
         * <p>{@code SpringPaging} appends the id to every ordering to make it total. This walks the
         * whole list a page at a time and asserts it saw each row exactly once.
         */
        @Test
        @DisplayName("paging a list with tied sort values sees every row exactly once")
        void pagesDoNotRepeatOrSkipOnTiedValues() {
            List<Long> all = twelveOnOneDay("SIIT-PG2");
            long customerId = salesInvoices.require(all.getFirst()).customerId();

            List<Long> seen = new ArrayList<>();
            for (int page = 0; page < 4; page++) {
                salesInvoices.pageOfCustomer(customerId, null,
                                PageRequest.of(page, 4, SalesInvoiceSort.INVOICE_DATE.name(),
                                        SortDirection.ASC))
                        .items().forEach(invoice -> seen.add(invoice.id()));
            }

            assertThat(seen)
                    .as("every invoice exactly once, across pages, on a sort where all twelve tie")
                    .containsExactlyInAnyOrderElementsOf(all)
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("sorting descending reverses the page, tiebreaker included")
        void descendingReversesTheOrder() {
            List<Long> all = twelveOnOneDay("SIIT-PG3");
            long customerId = salesInvoices.require(all.getFirst()).customerId();

            List<Long> ascending = salesInvoices.pageOfCustomer(customerId, null,
                            PageRequest.of(0, 12, SalesInvoiceSort.INVOICE_DATE.name(),
                                    SortDirection.ASC))
                    .items().stream().map(SalesInvoiceView::id).toList();
            List<Long> descending = salesInvoices.pageOfCustomer(customerId, null,
                            PageRequest.of(0, 12, SalesInvoiceSort.INVOICE_DATE.name(),
                                    SortDirection.DESC))
                    .items().stream().map(SalesInvoiceView::id).toList();

            assertThat(descending)
                    .as("the tiebreaker follows the sort direction, so a descending page is the "
                            + "ascending one reversed rather than an arbitrary reshuffle")
                    .containsExactlyElementsOf(ascending.reversed());
        }

        @Test
        @DisplayName("a sort this list does not offer is refused, naming the ones it does")
        void anUnknownSortIsRefused() {
            List<Long> all = twelveOnOneDay("SIIT-PG4");
            long customerId = salesInvoices.require(all.getFirst()).customerId();

            // The routes bind `sort` to the enum so this is unreachable over HTTP — this is the
            // guard that holds if a service is called from somewhere else.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> salesInvoices.pageOfCustomer(customerId, null,
                            PageRequest.of(0, 5, "GROSS_TOTAL", SortDirection.ASC)))
                    .withMessageContaining("INVOICE_DATE");
        }

        @Test
        @DisplayName("an empty list is page 1 of 1, not page 1 of 0")
        void anEmptyListStillHasOnePage() {
            CustomerView nobody = customer("Paging empty");

            PageResponse<SalesInvoiceView> page =
                    salesInvoices.pageOfCustomer(nobody.id(), null, PageRequest.firstPage());

            assertThat(page.items()).isEmpty();
            assertThat(page.totalElements()).isZero();
            assertThat(page.totalPages())
                    .as("a table showing 'page 1 of 0' is reporting a state that cannot exist")
                    .isEqualTo(1);
            assertThat(page.hasNext()).isFalse();
        }

        @Test
        @DisplayName("a page larger than the maximum is refused rather than quietly truncated")
        void anOversizedPageIsRefused() {
            // Silently returning MAX_SIZE rows for a request of 5000 is how a client comes to
            // believe it has seen the whole list. The bound is what makes exposing a large list
            // safe at all, so it is stated rather than applied behind the caller's back.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> PageRequest.of(0, PageRequest.MAX_SIZE + 1))
                    .withMessageContaining("at most");
        }

        @Test
        @DisplayName("the paged and unpaged reads agree about what is in the list")
        void pagedAgreesWithUnpaged() {
            List<Long> all = twelveOnOneDay("SIIT-PG5");
            long customerId = salesInvoices.require(all.getFirst()).customerId();

            List<Long> unpaged = salesInvoices.ofCustomer(customerId).stream()
                    .map(SalesInvoiceView::id).toList();
            List<Long> paged = salesInvoices
                    .pageOfCustomer(customerId, null, PageRequest.of(0, PageRequest.MAX_SIZE))
                    .items().stream().map(SalesInvoiceView::id).toList();

            assertThat(paged)
                    .as("adding paging must not change which invoices the list contains — "
                            + "between() and pageBetween() answer the same question")
                    .containsExactlyElementsOf(unpaged);
        }
    }

    @Nested
    @DisplayName("preview — the same arithmetic, without posting it")
    class Preview {

        /**
         * The guard the whole preview design rests on: <strong>it agrees with the posting, figure
         * for figure.</strong>
         *
         * <p>A preview computed along its own path would drift, and it would drift silently — the
         * operator sees a correct-looking total and the recorded document says something else. The
         * two share {@code compute()} so they cannot, and this asserts that rather than trusting it.
         *
         * <p>Deliberately over a request with everything that makes the arithmetic non-obvious: two
         * rates, a charge line at its own rate independent of the goods (Q33), and a quantity that
         * does not multiply to a round number of cents.
         */
        @Test
        @DisplayName("preview agrees with record, figure for figure, on the same request")
        void previewAgreesWithRecord() {
            CustomerView buyer = customer("Preview agreement");
            ProductView beans = goods("SIIT-PV1", "19.99");
            stock(beans.id(), 100L, "4.000000");

            NewSalesInvoice request = NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(
                            NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of("3.000000"),
                                    UnitCost.ofEur("19.990000")),
                            NewSalesInvoiceLine.charge(
                                    deliveryChargeTypeId(), Money.ofEur("3.23"))));

            SalesInvoicePreview preview = salesInvoices.preview(request);
            SalesInvoiceView recorded = salesInvoices.record(request);

            assertThat(preview.gross())
                    .as("the preview's gross is the invoice's gross")
                    .isEqualTo(recorded.grossTotal());
            assertThat(preview.net()).isEqualTo(recorded.netTotal());
            assertThat(preview.vat()).isEqualTo(recorded.vatTotal());
            assertThat(preview.receivable()).isEqualTo(recorded.grossTotal());

            // Per line, in order — a total that matches while the lines do not is worse than
            // neither matching, because it looks right on the screen that shows the total.
            assertThat(preview.lines()).hasSameSizeAs(recorded.lines());
            for (int i = 0; i < preview.lines().size(); i++) {
                SalesInvoicePreviewLine previewed = preview.lines().get(i);
                SalesInvoiceLineView posted = recorded.lines().get(i);

                assertThat(previewed.net()).as("line %d net", i).isEqualTo(posted.netAmount());
                assertThat(previewed.vat()).as("line %d VAT", i).isEqualTo(posted.vatAmount());
                assertThat(previewed.gross()).as("line %d gross", i)
                        .isEqualTo(posted.grossAmount());
                assertThat(previewed.vatClassId()).as("line %d class", i)
                        .isEqualTo(posted.vatClassId());
                assertThat(previewed.vatClassSource()).as("line %d precedence level", i)
                        .isEqualTo(posted.vatClassSource());
            }
        }

        @Test
        @DisplayName("preview posts nothing — no invoice, no entry, and the number stays free")
        void previewWritesNothing() {
            CustomerView buyer = customer("Preview writes nothing");
            ProductView beans = goods("SIIT-PV2", "10.00");
            stock(beans.id(), 10L, "4.000000");
            String documentNumber = number();

            long entriesBefore = journal.entriesBetween(JULY, JULY).size();
            NewSalesInvoice request = NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                    documentNumber, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000"))));

            salesInvoices.preview(request);
            salesInvoices.preview(request);

            assertThat(journal.entriesBetween(JULY, JULY))
                    .as("a preview that posted would be a rolled-back invoice at best, and this "
                            + "system's audit entries survive a rollback")
                    .hasSize((int) entriesBefore);
            assertThat(salesInvoices.between(JULY, JULY))
                    .extracting(SalesInvoiceView::documentNumber)
                    .doesNotContain(documentNumber);

            // And the number is still free, which is the operative proof: previewing twice did not
            // consume it, so the operator can still record the invoice they were previewing.
            assertThat(salesInvoices.record(request).documentNumber()).isEqualTo(documentNumber);
        }

        /**
         * The one refusal preview reports instead of raising, and why.
         *
         * <p>An entry screen has to show the operator the difference and offer the acceptance
         * <em>before</em> they submit. If asking what the difference is refused to answer, that
         * screen could only be built by computing the difference in the client — which is the thing
         * the preview endpoint exists to make unnecessary.
         */
        @Test
        @DisplayName("an unaccepted large difference is reported by preview and refused by record")
        void previewReportsWhatRecordRefuses() {
            CustomerView buyer = customer("Preview disagreement");
            ProductView beans = goods("SIIT-PV3", "10.00");
            stock(beans.id(), 10L, "4.000000");

            NewSalesInvoice request = NewSalesInvoice.of(
                            buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000"))))
                    .statedAs(Money.ofEur("13.00"));

            SalesInvoicePreview preview = salesInvoices.preview(request);

            assertThat(preview.roundingNeedsAcceptance())
                    .as("the screen must be able to learn this without submitting")
                    .isTrue();
            assertThat(preview.gross()).isEqualTo(Money.ofEur("12.40"));
            assertThat(preview.roundingDifference()).isEqualTo(Money.ofEur("0.60"));
            assertThat(preview.roundingThreshold())
                    .as("the threshold the comparison actually used, so the screen can explain it "
                            + "rather than reading the setting a second time")
                    .isEqualTo(settings.requireEurAmount(SettingKeys.LEDGER_ROUNDING_THRESHOLD));

            // Reporting it is not permitting it.
            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.record(request))
                    .withMessageContaining("rounding threshold");

            // And once accepted, preview stops asking for it.
            assertThat(salesInvoices.preview(
                    request.acceptingRoundingDifference("kostas", "Go rounded it"))
                    .roundingNeedsAcceptance())
                    .isFalse();
        }

        @Test
        @DisplayName("preview refuses what record refuses, with the same message")
        void previewRefusesWhatRecordRefuses() {
            CustomerView buyer = customer("Preview refusals");
            ProductView beans = goods("SIIT-PV4", "10.00");
            stock(beans.id(), 10L, "4.000000");

            // A duplicate document number. Most of a preview's value is telling the operator this
            // before they have filled in the rest of the form.
            String taken = number();
            salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT, taken, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.preview(NewSalesInvoice.of(
                            buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                            taken, JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000"))))))
                    .withMessageContaining("already been recorded");

            // A cash sale over the statutory limit is refused before it is priced into existence.
            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.preview(NewSalesInvoice.of(
                            buyer.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.CASH,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of(1L), UnitCost.ofEur("5000.000000"))))));
        }
    }

    @Nested
    @DisplayName("rounding, per document — Q15")
    class Rounding {

        @Test
        @DisplayName("a residual cent posts automatically to Rounding differences")
        void smallDifferencePostsAutomatically() {
            CustomerView buyer = customer("Residual");
            ProductView beans = goods("SIIT-12", "10.00");
            stock(beans.id(), 10L, "4.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                            buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000"))))
                    // Lines come to 12.40; Go's document says 12.41.
                    .statedAs(Money.ofEur("12.41")));

            assertThat(invoice.roundingAmount()).isEqualTo(Money.ofEur("0.01"));
            assertThat(invoice.roundingNeededReview()).isFalse();
            // Accounts receivable agrees with the document the customer holds, which is the whole
            // reason the difference is posted rather than merely flagged.
            assertThat(invoice.grossTotal()).isEqualTo(Money.ofEur("12.41"));
            assertThat(journal.requireEntry(invoice.journalEntryId()).lines())
                    .anySatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.ROUNDING_DIFFERENCES)));
        }

        @Test
        @DisplayName("a difference above the threshold is refused until somebody accepts it")
        void largeDifferenceIsRefusedUntilAccepted() {
            CustomerView buyer = customer("Disagreement");
            ProductView beans = goods("SIIT-13", "10.00");
            stock(beans.id(), 10L, "4.000000");

            NewSalesInvoice request = NewSalesInvoice.of(
                            buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000"))))
                    .statedAs(Money.ofEur("13.00"));

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.record(request))
                    .withMessageContaining("rounding threshold");

            // Q15's remainder answered: the confirmation happens at entry and is recorded on the
            // record, rather than dropping a row into a review queue somebody visits later.
            SalesInvoiceView accepted = salesInvoices.record(
                    request.acceptingRoundingDifference("kostas", "Go rounded the order total"));

            assertThat(accepted.roundingNeededReview()).isTrue();
            assertThat(accepted.roundingAcceptedBy()).isEqualTo("kostas");
            assertThat(accepted.roundingAcceptedAt()).isNotNull();
            assertThat(accepted.grossTotal()).isEqualTo(Money.ofEur("13.00"));

            assertThat(salesInvoices.withAcceptedRoundingDifference(JULY, JULY))
                    .extracting(SalesInvoiceView::id)
                    .contains(accepted.id());
        }

        @Test
        @DisplayName("with no stated total there is nothing to compare against and no difference")
        void noStatedTotalMeansNoRounding() {
            CustomerView buyer = customer("No document");
            ProductView beans = goods("SIIT-14", "10.00");
            stock(beans.id(), 10L, "4.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT, number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));

            assertThat(invoice.statedTotal()).isNull();
            assertThat(invoice.roundingAmount()).isEqualTo(Money.ofEur("0.00"));
        }
    }

    @Nested
    @DisplayName("serialized stock — the step 6 obligation")
    class Serialized {

        @Test
        @DisplayName("the named machine is marked SOLD and carries its buyer and invoice line")
        void sellingANamedMachine() {
            CustomerView buyer = customer("Machine buyer");
            ProductView machine = products.create(NewProduct.serializedGoods("SIIT-15", "Machine",
                    unitsOfMeasure.requireByCode("PIECE").id(),
                    vatClasses.requireByCode("1410").id(), Money.ofEur("2400.00")));
            inventory.receive(NewInventoryLot.serialized(machine.id(), UnitCost.ofEur("1800.000000"),
                    MARCH, StockLocation.INVENTORY, List.of("SIIT-SN-A", "SIIT-SN-B")));

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.serializedProduct(
                            machine.id(), UnitCost.ofEur("2400.000000"), List.of("SIIT-SN-B")))));

            var sold = inventory.findUnitBySerialNumber("SIIT-SN-B").orElseThrow();
            assertThat(sold.status()).isEqualTo(SerializedUnitStatus.SOLD);
            assertThat(sold.soldToCustomerId()).isEqualTo(buyer.id());
            assertThat(sold.soldOnInvoiceLineId()).isEqualTo(invoice.lines().getFirst().id());
            assertThat(invoice.lines().getFirst().soldSerialNumbers())
                    .containsExactly("SIIT-SN-B");

            // The other machine is untouched: three received together are one lot, and selling one
            // does not move the others.
            assertThat(inventory.findUnitBySerialNumber("SIIT-SN-A").orElseThrow().status())
                    .isEqualTo(SerializedUnitStatus.IN_STOCK);

            // Its own actual cost, with no FIFO — brief §5's exception.
            StockConsumptionView consumption = inventory.requireConsumption(
                    invoice.lines().getFirst().stockConsumptionId());
            assertThat(consumption.totalCost()).isEqualTo(Money.ofEur("1800.00"));
        }

        @Test
        @DisplayName("a machine already sold cannot be sold again, and is refused rather than flagged")
        void aNamedMachineCannotGoNegative() {
            CustomerView buyer = customer("Missing machine");
            ProductView machine = products.create(NewProduct.serializedGoods("SIIT-16", "Machine",
                    unitsOfMeasure.requireByCode("PIECE").id(),
                    vatClasses.requireByCode("1410").id(), Money.ofEur("2400.00")));
            inventory.receive(NewInventoryLot.serialized(machine.id(), UnitCost.ofEur("1800.000000"),
                    MARCH, StockLocation.INVENTORY, List.of("SIIT-SN-C")));

            salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.serializedProduct(
                            machine.id(), UnitCost.ofEur("2400.000000"), List.of("SIIT-SN-C")))));

            // Aggregate stock may go negative because "how many are there" can be wrong (Q17);
            // "is machine C on the shelf" cannot be, and there is nothing a later delivery could
            // arrive to back it with. So this refuses instead of recording a shortfall.
            assertThatExceptionOfType(InvalidStockConsumptionException.class)
                    .isThrownBy(() -> salesInvoices.record(NewSalesInvoice.of(
                            buyer.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.ON_ACCOUNT,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.serializedProduct(machine.id(),
                                    UnitCost.ofEur("2400.000000"), List.of("SIIT-SN-C"))))))
                    .withMessageContaining("not ours to sell");

            // And a serial nobody ever received is a lookup failure, which is a different answer.
            assertThatExceptionOfType(SerializedUnitNotFoundException.class)
                    .isThrownBy(() -> salesInvoices.record(NewSalesInvoice.of(
                            buyer.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.ON_ACCOUNT,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.serializedProduct(machine.id(),
                                    UnitCost.ofEur("2400.000000"), List.of("SIIT-SN-NOPE"))))));
        }
    }

    @Nested
    @DisplayName("bundles — brief §5's two linked levels")
    class Bundles {

        @Test
        @DisplayName("a bundle sale stores its decomposition, and both levels are the same money")
        void bundleDecompositionIsMaterialised() {
            CustomerView buyer = customer("Bundle buyer");
            ProductView grinder = goods("SIIT-17", "120.00");
            ProductView beans = goods("SIIT-18", "30.00");
            stock(grinder.id(), 5L, "60.000000");
            stock(beans.id(), 5L, "12.000000");

            ProductView bundle = goods("SIIT-19", "135.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(grinder.id()), NewBundleComponent.one(beans.id())));

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT, number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            bundle.id(), Quantity.of(1L), UnitCost.ofEur("135.000000")))));

            var line = invoice.lines().getFirst();
            assertThat(line.isBundle()).isTrue();
            assertThat(line.components()).hasSize(2);

            Money allocated = line.components().stream()
                    .map(component -> component.allocatedAmount())
                    .reduce(Money.ofEur("0.00"), Money::plus);
            assertThat(allocated).isEqualTo(line.netAmount());

            // The bundle itself has no stock; the components are what left the shelf.
            assertThat(line.stockConsumptionId()).isNull();
            assertThat(line.components()).allSatisfy(
                    component -> assertThat(component.stockConsumptionId()).isNotNull());
        }

        @Test
        @DisplayName("dissolving a bundle after it has been sold leaves the invoice untouched")
        void dissolveAfterSaleStrandsNothing() {
            CustomerView buyer = customer("Dissolve");
            ProductView grinder = goods("SIIT-20", "120.00");
            ProductView beans = goods("SIIT-21", "30.00");
            stock(grinder.id(), 5L, "60.000000");
            stock(beans.id(), 5L, "12.000000");

            ProductView bundle = goods("SIIT-22", "135.00");
            bundles.define(bundle.id(), List.of(
                    NewBundleComponent.one(grinder.id()), NewBundleComponent.one(beans.id())));

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT, number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            bundle.id(), Quantity.of(1L), UnitCost.ofEur("135.000000")))));
            List<Money> before = invoice.lines().getFirst().components().stream()
                    .map(component -> component.allocatedAmount())
                    .toList();

            bundles.dissolve(bundle.id());

            // The step 6 obligation, discharged by the decomposition being MATERIALISED rather than by
            // a refusal: the invoice does not depend on the definition still existing, so brief §5's
            // "never rewrite history" holds with no alias table.
            assertThat(bundles.componentsOf(bundle.id())).isEmpty();
            SalesInvoiceView reread = salesInvoices.require(invoice.id());
            assertThat(reread.lines().getFirst().components()).hasSize(2);
            assertThat(reread.lines().getFirst().components().stream()
                    .map(component -> component.allocatedAmount()).toList()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("what is refused")
    class Refusals {

        @Test
        @DisplayName("the same document number twice, because it would state the revenue twice")
        void duplicateDocumentNumber() {
            CustomerView buyer = customer("Duplicate");
            ProductView beans = goods("SIIT-23", "10.00");
            stock(beans.id(), 10L, "4.000000");
            String documentNumber = number();

            NewSalesInvoice request = NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                    documentNumber, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000"))));
            salesInvoices.record(request);

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.record(request))
                    .withMessageContaining("already been recorded");
        }

        @Test
        @DisplayName("a sale to an inactive customer")
        void inactiveCustomer() {
            CustomerView buyer = customer("Inactive");
            customers.deactivate(buyer.id());
            ProductView beans = goods("SIIT-24", "10.00");
            stock(beans.id(), 10L, "4.000000");

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.record(NewSalesInvoice.of(
                            buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000"))))))
                    .withMessageContaining("inactive");
        }
    }

    @Nested
    @DisplayName("reversal — Q13")
    class Reversal {

        @Test
        @DisplayName("a reversal posts the mirror and puts the stock back, in one transaction")
        void reversalUndoesBothHalves() {
            CustomerView buyer = customer("Reversal");
            ProductView beans = goods("SIIT-25", "50.00");
            stock(beans.id(), 10L, "20.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT, number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(2L), UnitCost.ofEur("50.000000")))));
            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(8L));

            SalesInvoiceView reversal =
                    salesInvoices.reverse(invoice.id(), JULY, "recorded in error");

            assertThat(reversal.isReversal()).isTrue();
            assertThat(salesInvoices.require(invoice.id()).isReversed()).isTrue();
            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(10L));
            assertThat(journal.subLedgerBalanceOf(SubLedgerRef.customer(buyer.id()), JULY))
                    .isEqualTo(Money.ofEur("0.00"));
        }

        @Test
        @DisplayName("reversing twice is refused, because it would credit the customer twice")
        void reversedAtMostOnce() {
            CustomerView buyer = customer("Twice");
            ProductView beans = goods("SIIT-26", "50.00");
            stock(beans.id(), 10L, "20.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT, number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("50.000000")))));
            salesInvoices.reverse(invoice.id(), JULY, null);

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.reverse(invoice.id(), JULY, null))
                    .withMessageContaining("already been reversed");
        }
    }

    /**
     * R1b — what the series decides.
     *
     * <p>Three refusals and one silent branch. The branch is the one worth reading carefully: a
     * document type that does not move stock creates <strong>no consumption row at all</strong>, and
     * nothing anywhere says so. That is a decision, and a test is the only place it is visible.
     */
    @Nested
    @DisplayName("R1b — the series decides the channel, the stock behaviour, and three refusals")
    class TheSeriesDecides {

        /** Its own fixture namespace, so deactivating a type here cannot affect another test. */
        private SalesDocumentFixture own(String suffix) {
            return new SalesDocumentFixture(salesDocumentTypes, salesSeries, "SIIT" + suffix);
        }

        private SalesInvoiceView saleIn(long seriesId, ProductView product) {
            return salesInvoices.record(NewSalesInvoice.of(
                    customer("Series " + seriesId).id(), seriesId, SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            product.id(), Quantity.of(2L), UnitCost.ofEur("50.000000")))));
        }

        @Test
        @DisplayName("a stock-moving type consumes — the NEGATIVE CONTROL for the branch below")
        void aStockMovingTypeStillConsumes() {
            // ⚠️ This test is why the next one means anything. "No consumption row" is also what
            // a broken branch, a broken fixture or a sale that never happened would produce, and
            // those are indistinguishable from a correct skip by looking at the absence alone.
            // This asserts that the SAME code path, differing only in affectsStock, does consume.
            ProductView beans = goods("SIIT-R1B-01", "50.00");
            stock(beans.id(), 10L, "20.000000");

            SalesInvoiceView invoice = saleIn(series(SalesChannel.ECOMMERCE), beans);

            Long consumptionId = invoice.lines().getFirst().stockConsumptionId();
            assertThat(consumptionId)
                    .as("a stock-moving document type must still take the goods off the shelf")
                    .isNotNull();
            assertThat(inventory.requireConsumption(consumptionId).quantityFilled())
                    .isEqualTo(Quantity.of(2L));
        }

        @Test
        @DisplayName("a non-stock-moving type creates NO consumption row, and says nothing about it")
        void aNonStockMovingTypeConsumesNothing() {
            ProductView beans = goods("SIIT-R1B-02", "50.00");
            stock(beans.id(), 10L, "20.000000");
            Quantity before = inventory.stockOf(beans.id()).total();

            SalesInvoiceView invoice = saleIn(
                    own("-NS").nonStockMoving(SalesChannel.ECOMMERCE), beans);

            assertThat(invoice.lines().getFirst().stockConsumptionId())
                    .as("a plain Τιμολόγιο is purely a sale: the goods leave later on a dispatch "
                            + "document (18b), so nothing is consumed here")
                    .isNull();
            assertThat(inventory.stockOf(beans.id()).total())
                    .as("stock is untouched, not merely unrecorded")
                    .isEqualTo(before);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM stock_consumption WHERE product_id = ?",
                    Long.class, beans.id()))
                    .as("no row at all — not a pending one, and stock_consumption's source CHECK "
                            + "is deliberately not widened, because there is nothing new to record")
                    .isZero();

            // ⚠️ And the revenue side is entirely unaffected: the sale posts, the ledger balances,
            // and only the cost entry is absent. That asymmetry is the known limitation, recorded
            // rather than mitigated.
            assertThat(invoice.journalEntryId()).isNotNull();
            assertThat(journal.requireEntry(invoice.journalEntryId()).isBalanced()).isTrue();
        }

        @Test
        @DisplayName("the channel comes from the series, not from the caller")
        void theChannelComesFromTheSeries() {
            ProductView beans = goods("SIIT-R1B-03", "50.00");
            stock(beans.id(), 10L, "20.000000");

            SalesInvoiceView invoice = saleIn(series(SalesChannel.SKROUTZ), beans);

            assertThat(invoice.channel())
                    .as("nothing in the request said SKROUTZ — the series did")
                    .isEqualTo(SalesChannel.SKROUTZ);
            assertThat(journal.requireEntry(invoice.journalEntryId()).lines())
                    .anySatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.SALES_SKROUTZ)));
            assertThat(invoice.seriesAbbreviation())
                    .as("R1a left this null because nothing had a series; R1b resolves it")
                    .isNotNull();
        }

        @Test
        @DisplayName("a channel-less series is REFUSED, and the message says what R3 is waiting on")
        void aChannelLessSeriesIsRefused() {
            ProductView beans = goods("SIIT-R1B-04", "50.00");
            stock(beans.id(), 10L, "20.000000");
            long selfSupply = own("-CL").channelLess();

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> saleIn(selfSupply, beans))
                    .withMessageContaining("no sales channel")
                    .withMessageContaining("Αυτοπαράδοσης")
                    .withMessageContaining("R3");

            // ⚠️ The constraint is what holds the question open, so it must still be there.
            assertThat(jdbc.queryForObject("""
                    SELECT is_nullable FROM information_schema.columns
                     WHERE table_name = 'sales_invoice' AND column_name = 'channel'
                    """, String.class))
                    .as("sales_invoice.channel must stay NOT NULL — R1b refuses rather than relaxes")
                    .isEqualTo("NO");
        }

        @Test
        @DisplayName("an inactive series is refused, and so is an active series of an inactive type")
        void inactiveSeriesAndTypesAreRefused() {
            ProductView beans = goods("SIIT-R1B-05", "50.00");
            stock(beans.id(), 20L, "20.000000");

            SalesDocumentFixture retired = own("-X");
            long seriesId = retired.stockMoving(SalesChannel.ECOMMERCE);
            // It works before anything is deactivated, so the refusals below are about the
            // deactivation and not about the fixture.
            assertThat(saleIn(seriesId, beans).id()).isPositive();

            salesSeries.deactivate(seriesId);
            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> saleIn(seriesId, beans))
                    .withMessageContaining("is inactive");
            salesSeries.reactivate(seriesId);

            long typeId = retired.stockMovingTypeId(SalesChannel.ECOMMERCE);
            salesDocumentTypes.deactivate(typeId);
            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> saleIn(seriesId, beans))
                    .withMessageContaining("which is inactive");
            salesDocumentTypes.reactivate(typeId);
        }

        /**
         * ⚠️ <strong>R4 A.10 — the guard this asserts shipped in R2b with NO TEST AT ALL, and a
         * document said otherwise.</strong>
         *
         * <p>{@code PROGRESS.md} recorded the payment-method {@code active} guard as
         * <em>"verified in {@code R2ReferenceDataContractIT} over real HTTP"</em>. That class has no
         * payment-method case; its {@code "not for new documents"} assertion is the <em>document
         * type</em> refusal. {@code PaymentMethodIT} round-trips deactivate and never records an
         * invoice, and nothing in the test tree contained the message. Its only evidence was a
         * browser row the owner ran on 2026-08-06 — real evidence, and not a test.
         *
         * <p>This is {@code CLAUDE.md}'s <em>a claim recorded at close-out is a CLAIM</em>, and the
         * test exists so the R4 sweep that is about to rewrite this file <strong>has something that
         * can break</strong>.
         *
         * <p>⚠️ <strong>Setting is refused; holding is not</strong> — the sale recorded before the
         * deactivation is asserted to survive it, because a deactivation that broke existing
         * documents would be destructive and nobody would use it.
         */
        @Test
        @DisplayName("a deactivated payment method is refused on a NEW invoice, and does not disturb an old one")
        void aDeactivatedPaymentMethodIsRefused() {
            ProductView beans = goods("SIIT-R4-01", "50.00");
            stock(beans.id(), 20L, "20.000000");
            long seriesId = own("-PM").stockMoving(SalesChannel.ECOMMERCE);

            // It works before the deactivation, so the refusal below is about the deactivation and
            // not about the fixture — the same shape as the inactive-series test above.
            SalesInvoiceView before = saleIn(seriesId, beans);
            assertThat(before.id()).isPositive();

            paymentMethods.deactivate(SettlementMethod.ON_ACCOUNT);
            try {
                assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                        .isThrownBy(() -> saleIn(seriesId, beans))
                        .withMessageContaining("is inactive")
                        .withMessageContaining("not for new documents");

                // A preview refuses it too: the guard is in compute(), which both paths share, so an
                // entry screen learns before the operator submits.
                assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                        .isThrownBy(() -> salesInvoices.preview(NewSalesInvoice.of(
                                customer("PM preview").id(), seriesId, SettlementMethod.ON_ACCOUNT,
                                number(), JULY,
                                List.of(NewSalesInvoiceLine.product(
                                        beans.id(), Quantity.of(1L), UnitCost.ofEur("50.000000"))))));

                // Holding is not refused: the invoice recorded a moment ago still reads, and still
                // names the method that has since been retired.
                assertThat(salesInvoices.require(before.id()).settlementMethod())
                        .isEqualTo(SettlementMethod.ON_ACCOUNT);
            } finally {
                paymentMethods.reactivate(SettlementMethod.ON_ACCOUNT);
            }
        }

        @Test
        @DisplayName("a preview refuses exactly what a record refuses")
        void previewRefusesTheSameThings() {
            // compute() is shared, which is the point: an entry screen must learn that a series is
            // unusable before the operator submits, not after.
            ProductView beans = goods("SIIT-R1B-06", "50.00");
            stock(beans.id(), 10L, "20.000000");
            long selfSupply = own("-CL").channelLess();

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.preview(NewSalesInvoice.of(
                            customer("Preview refusal").id(), selfSupply,
                            SettlementMethod.ON_ACCOUNT, number(), JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    beans.id(), Quantity.of(1L), UnitCost.ofEur("50.000000"))))))
                    .withMessageContaining("no sales channel");
        }

        @Test
        @DisplayName("a reversal carries the original's series")
        void aReversalCarriesTheSeries() {
            ProductView beans = goods("SIIT-R1B-07", "50.00");
            stock(beans.id(), 10L, "20.000000");

            SalesInvoiceView invoice = saleIn(series(SalesChannel.ECOMMERCE), beans);
            SalesInvoiceView reversal =
                    salesInvoices.reverse(invoice.id(), JULY, "R1b series carry");

            assertThat(reversal.seriesId())
                    .as("a reversal showing no series while its original has one would read as a "
                            + "document from before series existed")
                    .isEqualTo(invoice.seriesId());
            assertThat(reversal.channel()).isEqualTo(invoice.channel());
        }

        @Test
        @DisplayName("a series that names nothing is a 404's exception, not a validation failure")
        void anUnknownSeriesIsNotFound() {
            ProductView beans = goods("SIIT-R1B-08", "50.00");
            stock(beans.id(), 10L, "20.000000");

            assertThatExceptionOfType(DocumentSeriesNotFoundException.class)
                    .isThrownBy(() -> saleIn(999_999_999L, beans));
        }
    }

    @Nested
    @DisplayName("enforced by the database, not only by the service")
    class DatabaseInvariants {

        @Test
        @DisplayName("a line stating both a VAT class and an exemption reason is refused")
        void vatTreatmentIsExactlyOne() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO sales_invoice_line (
                        invoice_id, line_number, line_type, product_id, quantity, unit_price,
                        unit_price_currency, net_amount, net_amount_currency, vat_amount,
                        vat_amount_currency, vat_class_id, vat_class_source,
                        vat_exemption_reason_id)
                    SELECT (SELECT max(id) FROM sales_invoice), 999, 'PRODUCT',
                           (SELECT max(id) FROM product), 1, 1, 'EUR', 1, 'EUR', 0, 'EUR',
                           (SELECT max(id) FROM vat_class), 'PRODUCT',
                           (SELECT max(id) FROM vat_exemption_reason)
                    """))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("sales_invoice_line_vat_treatment_is_stated");
        }

        @Test
        @DisplayName("a product line naming a charge type is refused — the two shapes, and no third")
        void lineShapeIsOneOfTwo() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO sales_invoice_line (
                        invoice_id, line_number, line_type, product_id, charge_type_id, quantity,
                        unit_price, unit_price_currency, net_amount, net_amount_currency,
                        vat_amount, vat_amount_currency, vat_class_id, vat_class_source)
                    SELECT (SELECT max(id) FROM sales_invoice), 998, 'PRODUCT',
                           (SELECT max(id) FROM product), (SELECT max(id) FROM charge_type),
                           1, 1, 'EUR', 1, 'EUR', 0, 'EUR', (SELECT max(id) FROM vat_class), 'PRODUCT'
                    """))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("sales_invoice_line_shape");
        }

        @Test
        @DisplayName("a unit marked SOLD with no buyer is refused, and a buyer on an in-stock unit too")
        void theSaleLinkIsBiconditional() {
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE serialized_unit SET status = 'SOLD' WHERE serial_number = 'SIIT-SN-A'"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("serialized_unit_sale_link_iff_sold");

            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE serialized_unit
                       SET sold_to_customer_id = (SELECT max(id) FROM customer),
                           sold_on_invoice_line_id = (SELECT max(id) FROM sales_invoice_line)
                     WHERE serial_number = 'SIIT-SN-A'
                    """))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("serialized_unit_sale_link_iff_sold");
        }

        @Test
        @DisplayName("a duplicate document number is refused by trigger, not only by the service")
        void duplicateNumberIsRefusedByTheDatabase() {
            CustomerView buyer = customer("DB duplicate");
            ProductView beans = goods("SIIT-27", "10.00");
            stock(beans.id(), 10L, "4.000000");
            String documentNumber = number();

            SalesInvoiceView first = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                    documentNumber, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));

            // Case-insensitively, so 'si-1' and 'SI-1' are the one document they obviously are.
            //
            // ⚠️ THE INSERT NAMES THE SAME SERIES, and since R1b it has to. The key is
            // (COALESCE(series_id, -1), upper(document_number)) — R1a's C.6 — so a row with a NULL
            // series and one with a real series are in different groups and are NOT duplicates of
            // each other. Omitting series_id here would insert a legitimately different document
            // and the trigger would correctly stay silent, which is exactly what this test did on
            // the first run after R1b. The assertion below is unchanged; what changed is that the
            // row being inserted now IS the duplicate this test always meant it to be.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO sales_invoice (customer_id, channel, series_id, settlement_method,
                        document_number, invoice_date, rounding_amount, rounding_amount_currency,
                        journal_entry_id)
                    VALUES (?, 'ECOMMERCE', ?, 'ON_ACCOUNT', ?, DATE '2026-07-20', 0, 'EUR',
                            (SELECT max(id) FROM journal_entry))
                    """, buyer.id(), first.seriesId(),
                    documentNumber.toUpperCase(java.util.Locale.ROOT)))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);

            assertThat(salesInvoices.require(first.id()).documentNumber())
                    .isEqualTo(documentNumber);
        }

        @Test
        @DisplayName("the same number in a DIFFERENT series is not a duplicate — R1a's C.6, now live")
        void theSameNumberInAnotherSeriesIsAllowed() {
            // The other half of the rule above, and it was unobservable until R1b: with every row's
            // series NULL, (COALESCE(series_id, -1), number) behaved exactly like the global index
            // it replaced, so nothing in the suite could tell the two designs apart. Now that an
            // invoice names a series, ΑΛΠ-1 and ΤΠΔΑ-1 are the two different documents they really
            // are — which is the whole reason C.6 changed the key.
            CustomerView buyer = customer("Two series");
            ProductView beans = goods("SIIT-28", "10.00");
            stock(beans.id(), 20L, "4.000000");
            String documentNumber = number();

            SalesInvoiceView inWeb = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.ECOMMERCE), SettlementMethod.ON_ACCOUNT,
                    documentNumber, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));

            SalesInvoiceView inStore = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), series(SalesChannel.STORE_AND_PHONE), SettlementMethod.ON_ACCOUNT,
                    documentNumber, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));

            assertThat(inWeb.documentNumber()).isEqualTo(inStore.documentNumber());
            assertThat(inWeb.seriesId()).isNotEqualTo(inStore.seriesId());
            assertThat(inWeb.id()).isNotEqualTo(inStore.id());
        }
    }

    /**
     * Target-list row 8 — searching a document by things that are not on it (F5 B.1).
     *
     * <p>The customer's name and the series' abbreviation live on other tables, reached by subquery
     * on a scalar id rather than by a mapped association. {@code TextSearch.matchingRelated} says
     * why; this says that it works, and — more importantly — that it does not do the one thing a
     * join would have done.
     */
    @Nested
    @DisplayName("searching a sales invoice — row 8 of the target list")
    class Searching {

        private CustomerView buyer;
        private ProductView beans;
        private long webSeries;
        private String documentNumber;

        /**
         * ⚠️ Fresh fixtures per test, and <strong>never on {@code VAT_DAY}</strong>.
         *
         * <p>Two traps this class documents and I walked into both. Nothing here rolls back, so a
         * fixed SKU would be refused the second time this runs — hence the counter. And {@code
         * VAT_DAY} is reserved: {@code Vat.outputVatCarriesItsDimension} queries that date and sums
         * every invoice on it, so recording anything else there makes a passing test fail with an
         * arithmetic that looks like a costing bug.
         */
        @BeforeEach
        void recordOne() {
            int unique = NUMBERS.incrementAndGet();
            buyer = customer("Καφεκοπτεία Σινιόρ " + unique);
            beans = goods("SIIT-SEARCH-" + unique, "10.00");
            stock(beans.id(), 50L, "4.000000");
            webSeries = series(SalesChannel.ECOMMERCE);
            documentNumber = "SEARCHABLE-" + unique;

            salesInvoices.record(NewSalesInvoice.of(buyer.id(), webSeries,
                    SettlementMethod.ON_ACCOUNT, documentNumber, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));
        }

        private List<String> numbersMatching(String term) {
            return salesInvoices
                    .pageOfCustomer(buyer.id(), term, PageRequest.of(0, PageRequest.MAX_SIZE))
                    .items().stream()
                    .map(SalesInvoiceView::documentNumber)
                    .toList();
        }

        @Test
        @DisplayName("by its own document number, as a substring")
        void byDocumentNumber() {
            assertThat(numbersMatching("SEARCHABLE")).contains(documentNumber);
            assertThat(numbersMatching("no-such-text")).isEmpty();
        }

        @Test
        @DisplayName("by the CUSTOMER's name, which is on another table entirely")
        void byCustomerName() {
            // Case- and accent-insensitive through the same normalisation as every other search,
            // because the term and the column go through the one SQL function.
            assertThat(numbersMatching("σινιορ")).contains(documentNumber);
        }

        @Test
        @DisplayName("by the SERIES' abbreviation, likewise")
        void bySeriesAbbreviation() {
            String abbreviation = salesSeries.require(webSeries).abbreviation();
            assertThat(numbersMatching(abbreviation)).contains(documentNumber);
        }

        @Test
        @DisplayName("a blank term is no filter at all, not a filter matching nothing")
        void blankIsNoFilter() {
            assertThat(numbersMatching(null)).contains(documentNumber);
            assertThat(numbersMatching("   ")).contains(documentNumber);
        }

        /**
         * ⚠️ <strong>The reason the mechanism is a subquery and not a join, asserted rather than
         * argued.</strong>
         *
         * <p>{@code sales_invoice.series_id} is nullable and every invoice recorded before R1b has
         * none — which is every invoice in the production database as of 2026-08-05. A dotted JPA
         * path would have produced an INNER JOIN to the series, and an inner join drops rows whose
         * key is null. So searching for <em>anything</em> would have silently hidden the whole of
         * the pre-R1b history, while looking like it was working perfectly.
         *
         * <p>The null series is planted with SQL because <strong>no route can create one</strong> —
         * the service has required a series since R1b. That is exactly the shape of the rows this
         * has to keep working for: history, not new data.
         */
        @Test
        @DisplayName("an invoice with NO series still appears in a search — an inner join would drop it")
        void aSeriesLessInvoiceIsNotDropped() {
            String orphanNumber = "ORPHAN-" + NUMBERS.incrementAndGet();
            long orphanId = salesInvoices.record(NewSalesInvoice.of(buyer.id(), webSeries,
                    SettlementMethod.ON_ACCOUNT, orphanNumber, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000"))))).id();

            jdbc.update("update sales_invoice set series_id = null where id = ?", orphanId);

            assertThat(numbersMatching("ORPHAN"))
                    .as("a pre-R1b invoice has no series, and searching must not make it disappear")
                    .contains(orphanNumber);
            assertThat(numbersMatching("σινιορ"))
                    .as("nor when the term matches through the customer subquery instead")
                    .contains(orphanNumber);
        }
    }
}
