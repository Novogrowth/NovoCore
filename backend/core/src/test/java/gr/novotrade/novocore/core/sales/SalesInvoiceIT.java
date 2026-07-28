package gr.novotrade.novocore.core.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
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
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceService;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceView;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassNotDeterminableException;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.ledger.VatDirection;
import gr.novotrade.novocore.core.api.tax.VatClassSource;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
    private JdbcTemplate jdbc;

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
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.ON_ACCOUNT,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT, number(), JULY,
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
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.ON_ACCOUNT,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT, number(), JULY,
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
                    buyer.id(), SalesChannel.SKROUTZ, SettlementMethod.ON_ACCOUNT, number(), VAT_DAY,
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
                    overridden.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("100.000000")))));
            assertThat(fromCustomer.lines().getFirst().vatClassSource())
                    .isEqualTo(VatClassSource.CUSTOMER);
            assertThat(fromCustomer.vatTotal()).isEqualTo(Money.ofEur("13.00"));

            SalesInvoiceView fromLine = salesInvoices.record(NewSalesInvoice.of(
                    overridden.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
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
                    retail.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.CASH,
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
                    retail.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.CARD_POS,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.BANK_DEPOSIT,
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
                            retail.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.CASH,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.product(
                                    machine.id(), Quantity.of(1L), UnitCost.ofEur("600.000000"))))))
                    .withMessageContaining("legal cash limit");
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
                            buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
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
                            buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT, number(), JULY,
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
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.ON_ACCOUNT,
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
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.ON_ACCOUNT,
                    number(), JULY,
                    List.of(NewSalesInvoiceLine.serializedProduct(
                            machine.id(), UnitCost.ofEur("2400.000000"), List.of("SIIT-SN-C")))));

            // Aggregate stock may go negative because "how many are there" can be wrong (Q17);
            // "is machine C on the shelf" cannot be, and there is nothing a later delivery could
            // arrive to back it with. So this refuses instead of recording a shortfall.
            assertThatExceptionOfType(InvalidStockConsumptionException.class)
                    .isThrownBy(() -> salesInvoices.record(NewSalesInvoice.of(
                            buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.ON_ACCOUNT,
                            number(), JULY,
                            List.of(NewSalesInvoiceLine.serializedProduct(machine.id(),
                                    UnitCost.ofEur("2400.000000"), List.of("SIIT-SN-C"))))))
                    .withMessageContaining("not ours to sell");

            // And a serial nobody ever received is a lookup failure, which is a different answer.
            assertThatExceptionOfType(SerializedUnitNotFoundException.class)
                    .isThrownBy(() -> salesInvoices.record(NewSalesInvoice.of(
                            buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.ON_ACCOUNT,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT, number(), JULY,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT, number(), JULY,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
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
                            buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT, number(), JULY,
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT, number(), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("50.000000")))));
            salesInvoices.reverse(invoice.id(), JULY, null);

            assertThatExceptionOfType(InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.reverse(invoice.id(), JULY, null))
                    .withMessageContaining("already been reversed");
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
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
                    documentNumber, JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));

            // Case-insensitively, so 'si-1' and 'SI-1' are the one document they obviously are.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO sales_invoice (customer_id, channel, settlement_method,
                        document_number, invoice_date, rounding_amount, rounding_amount_currency,
                        journal_entry_id)
                    VALUES (?, 'ECOMMERCE', 'ON_ACCOUNT', ?, DATE '2026-07-20', 0, 'EUR',
                            (SELECT max(id) FROM journal_entry))
                    """, buyer.id(), documentNumber.toUpperCase(java.util.Locale.ROOT)))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);

            assertThat(salesInvoices.require(first.id()).documentNumber())
                    .isEqualTo(documentNumber);
        }
    }
}
