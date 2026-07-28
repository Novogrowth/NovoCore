package gr.novotrade.novocore.core.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.sales.CreditNoteService;
import gr.novotrade.novocore.core.api.sales.CreditNoteView;
import gr.novotrade.novocore.core.api.sales.InvalidCreditNoteException;
import gr.novotrade.novocore.core.api.sales.NewCreditNote;
import gr.novotrade.novocore.core.api.sales.NewCreditNoteLine;
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
import gr.novotrade.novocore.core.api.tax.VatClassService;
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
 * Credit notes — <strong>Q26 as a transaction rather than a policy</strong>.
 *
 * <p>What is being defended: that a return debits the channel's {@code Sales returns} account rather
 * than reducing Sales (which is the whole reason step 3 created three of them); that the VAT credited
 * is the VAT the sale actually charged and not whatever the rate happens to be now; and that goods
 * coming back go back into the lots they left, at the cost they left at, without that being confused
 * with a reversal.
 */
class CreditNoteIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 20);
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 12);

    private static final AtomicInteger NUMBERS = new AtomicInteger();

    @Autowired
    private CreditNoteService creditNotes;

    @Autowired
    private SalesInvoiceService salesInvoices;

    @Autowired
    private CustomerService customers;

    @Autowired
    private ProductService products;

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

    private String number(String prefix) {
        return prefix + "-" + NUMBERS.incrementAndGet() + "-" + System.nanoTime();
    }

    private CustomerView customer(String name) {
        return customers.create(NewCustomer.retail("CNIT — " + name, null, null));
    }

    private ProductView goods(String sku, String price) {
        return products.create(NewProduct.goods(sku, sku + " goods",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur(price)));
    }

    private void stock(long productId, long quantity, String unitCost) {
        inventory.receive(NewInventoryLot.pooled(productId, Quantity.of(quantity),
                UnitCost.ofEur(unitCost), MARCH, StockLocation.INVENTORY));
    }

    private SalesInvoiceView sale(CustomerView buyer, ProductView product, long quantity,
            String unitPrice, SalesChannel channel) {
        return salesInvoices.record(NewSalesInvoice.of(buyer.id(), channel,
                SettlementMethod.ON_ACCOUNT, number("CNIT-SI"), JULY,
                List.of(NewSalesInvoiceLine.product(
                        product.id(), Quantity.of(quantity), UnitCost.ofEur(unitPrice)))));
    }

    private long accountId(AccountSystemKey key) {
        return chartOfAccounts.requireAccount(key).id();
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("what one credit note posts")
    class Posting {

        @Test
        @DisplayName("contra-revenue per channel, output VAT back, and a credit to the customer")
        void creditPostsToTheChannelsReturnsAccount() {
            CustomerView buyer = customer("Returns");
            ProductView beans = goods("CNIT-01", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 4L, "50.000000", SalesChannel.SKROUTZ);

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("50.000000")))));

            assertThat(note.netTotal()).isEqualTo(Money.ofEur("50.00"));
            assertThat(note.vatTotal()).isEqualTo(Money.ofEur("12.00"));
            assertThat(note.channel()).isEqualTo(SalesChannel.SKROUTZ);

            JournalEntryView entry = journal.requireEntry(note.journalEntryId());
            assertThat(entry.source()).isEqualTo(JournalSource.CREDIT_NOTE);
            assertThat(entry.lines())
                    // Contra-revenue, per channel. Netting this into Sales would collapse exactly the
                    // per-channel return rate step 3 split the accounts to keep visible.
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.SALES_RETURNS_SKROUTZ));
                        assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                        assertThat(line.amount()).isEqualTo(Money.ofEur("50.00"));
                    })
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.OUTPUT_VAT));
                        assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                        assertThat(line.vat()).isNotNull();
                    })
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE));
                        assertThat(line.side()).isEqualTo(BalanceSide.CREDIT);
                        assertThat(line.subLedgerRef())
                                .isEqualTo(SubLedgerRef.customer(buyer.id()));
                    })
                    .noneSatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.SALES_SKROUTZ)));
        }

        @Test
        @DisplayName("a cash sale still credits Accounts receivable, not the till")
        void creditAlwaysGoesToReceivable() {
            CustomerView buyer = customer("Cash return");
            ProductView beans = goods("CNIT-02", "20.00");
            stock(beans.id(), 10L, "8.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.CASH,
                    number("CNIT-SI"), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("20.000000")))));

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("20.000000")))));

            // The money is owed back until it is actually refunded; posting the credit straight
            // against the cash box would take money out of the till nobody handed over.
            assertThat(journal.requireEntry(note.journalEntryId()).lines())
                    .anySatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE)))
                    .noneSatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.CASH)));
        }

        @Test
        @DisplayName("the VAT credited is the rate the sale charged, not whatever it is now")
        void vatComesFromTheSale() {
            CustomerView buyer = customer("Rate changed");
            ProductView beans = goods("CNIT-03", "100.00");
            stock(beans.id(), 10L, "40.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 1L, "100.000000", SalesChannel.ECOMMERCE);
            assertThat(invoice.vatTotal()).isEqualTo(Money.ofEur("24.00"));

            // The customer's override changes AFTER the sale. A credit note re-resolving the rate
            // would return VAT at 13% against output collected at 24%, and the return would stop
            // reconciling against what was filed.
            customers.changeVatClassOverride(buyer.id(), vatClasses.requireByCode("1131").id());

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("100.000000")))));

            assertThat(note.vatTotal()).isEqualTo(Money.ofEur("24.00"));
        }
    }

    @Nested
    @DisplayName("goods coming back")
    class Returns {

        @Test
        @DisplayName("a partial return puts stock back into the lot it left, at the cost it left at")
        void partialReturnRestoresTheLot() {
            CustomerView buyer = customer("Partial");
            ProductView beans = goods("CNIT-04", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 5L, "50.000000", SalesChannel.ECOMMERCE);
            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(5L));

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.returning(invoice.lines().getFirst().id(),
                            Quantity.of(2L), UnitCost.ofEur("50.000000")))));

            assertThat(note.returnedStock()).isTrue();
            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(7L));

            // An ordinary entry rather than a mirror: the sale was real and the goods came back, so
            // this is a return and not the un-making of a consumption.
            Long returnId = note.lines().getFirst().returnConsumptionId();
            assertThat(returnId).isNotNull();
            var returned = inventory.requireConsumption(returnId);
            assertThat(returned.isReturn()).isTrue();
            assertThat(returned.isReversal()).isFalse();
            assertThat(returned.totalCost()).isEqualTo(Money.ofEur("40.00"));

            assertThat(journal.requireEntry(returned.journalEntryId()).lines())
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.INVENTORY));
                        assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                    });
        }

        @Test
        @DisplayName("two returns against one sale are allowed, up to what was sold and no further")
        void returnsAccumulateAndAreCapped() {
            CustomerView buyer = customer("Twice back");
            ProductView beans = goods("CNIT-05", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 3L, "50.000000", SalesChannel.ECOMMERCE);
            long line = invoice.lines().getFirst().id();

            creditNotes.issue(NewCreditNote.of(invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.returning(
                            line, Quantity.of(1L), UnitCost.ofEur("50.000000")))));
            creditNotes.issue(NewCreditNote.of(invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.returning(
                            line, Quantity.of(1L), UnitCost.ofEur("50.000000")))));

            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(9L));

            // Crediting more than was sold would reclaim output VAT that was never charged.
            assertThatExceptionOfType(InvalidCreditNoteException.class)
                    .isThrownBy(() -> creditNotes.issue(NewCreditNote.of(
                            invoice.id(), number("CNIT-CN"), AUGUST,
                            List.of(NewCreditNoteLine.returning(
                                    line, Quantity.of(2L), UnitCost.ofEur("50.000000"))))))
                    .withMessageContaining("already been credited");
        }

        @Test
        @DisplayName("a returned machine goes back on the shelf and stops being sold to anybody")
        void aReturnedMachineIsNoLongerSold() {
            CustomerView buyer = customer("Machine back");
            ProductView machine = products.create(NewProduct.serializedGoods("CNIT-06", "Machine",
                    unitsOfMeasure.requireByCode("PIECE").id(),
                    vatClasses.requireByCode("1410").id(), Money.ofEur("2400.00")));
            inventory.receive(NewInventoryLot.serialized(machine.id(), UnitCost.ofEur("1800.000000"),
                    MARCH, StockLocation.INVENTORY, List.of("CNIT-SN-1")));

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.ON_ACCOUNT,
                    number("CNIT-SI"), JULY,
                    List.of(NewSalesInvoiceLine.serializedProduct(
                            machine.id(), UnitCost.ofEur("2400.000000"), List.of("CNIT-SN-1")))));
            assertThat(inventory.findUnitBySerialNumber("CNIT-SN-1").orElseThrow().status())
                    .isEqualTo(SerializedUnitStatus.SOLD);

            creditNotes.issue(NewCreditNote.of(invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.returning(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("2400.000000")))));

            // Brief §5 puts the customer/invoice link on a SOLD unit, so a machine back on the shelf
            // is not sold to anybody. Its history is in the invoice and the credit note.
            var unit = inventory.findUnitBySerialNumber("CNIT-SN-1").orElseThrow();
            assertThat(unit.status()).isEqualTo(SerializedUnitStatus.IN_STOCK);
            assertThat(unit.soldToCustomerId()).isNull();
            assertThat(unit.soldOnInvoiceLineId()).isNull();
        }

        @Test
        @DisplayName("a line that took no stock out cannot have stock come back against it")
        void aServiceCannotReturnStock() {
            CustomerView buyer = customer("Service credit");
            ProductView repair = products.create(NewProduct.service("CNIT-07", "Repair",
                    unitsOfMeasure.requireByCode("PIECE").id(),
                    vatClasses.requireByCode("1410").id(), Money.ofEur("80.00")));

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.ON_ACCOUNT,
                    number("CNIT-SI"), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            repair.id(), Quantity.of(1L), UnitCost.ofEur("80.000000")))));

            assertThatExceptionOfType(InvalidCreditNoteException.class)
                    .isThrownBy(() -> creditNotes.issue(NewCreditNote.of(
                            invoice.id(), number("CNIT-CN"), AUGUST,
                            List.of(NewCreditNoteLine.returning(invoice.lines().getFirst().id(),
                                    Quantity.of(1L), UnitCost.ofEur("80.000000"))))))
                    .withMessageContaining("took no stock out");
        }
    }

    @Nested
    @DisplayName("what is refused")
    class Refusals {

        @Test
        @DisplayName("crediting a line of a different invoice")
        void crossDocumentCredit() {
            CustomerView buyer = customer("Cross");
            ProductView beans = goods("CNIT-08", "50.00");
            stock(beans.id(), 20L, "20.000000");
            SalesInvoiceView first = sale(buyer, beans, 1L, "50.000000", SalesChannel.ECOMMERCE);
            SalesInvoiceView second = sale(buyer, beans, 1L, "50.000000", SalesChannel.ECOMMERCE);

            assertThatExceptionOfType(InvalidCreditNoteException.class)
                    .isThrownBy(() -> creditNotes.issue(NewCreditNote.of(
                            first.id(), number("CNIT-CN"), AUGUST,
                            List.of(NewCreditNoteLine.priceOnly(second.lines().getFirst().id(),
                                    Quantity.of(1L), UnitCost.ofEur("50.000000"))))))
                    .withMessageContaining("belongs to sales invoice");
        }

        @Test
        @DisplayName("crediting above the price the sale charged")
        void creditAboveTheSalePrice() {
            CustomerView buyer = customer("Above");
            ProductView beans = goods("CNIT-09", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 1L, "50.000000", SalesChannel.ECOMMERCE);

            assertThatExceptionOfType(InvalidCreditNoteException.class)
                    .isThrownBy(() -> creditNotes.issue(NewCreditNote.of(
                            invoice.id(), number("CNIT-CN"), AUGUST,
                            List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                                    Quantity.of(1L), UnitCost.ofEur("60.000000"))))))
                    .withMessageContaining("Crediting above what was charged");
        }

        @Test
        @DisplayName("crediting an invoice that was reversed — the sale never happened")
        void creditAgainstAReversedInvoice() {
            CustomerView buyer = customer("Reversed");
            ProductView beans = goods("CNIT-10", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 1L, "50.000000", SalesChannel.ECOMMERCE);
            long lineId = invoice.lines().getFirst().id();
            salesInvoices.reverse(invoice.id(), AUGUST, "typed wrong");

            assertThatExceptionOfType(InvalidCreditNoteException.class)
                    .isThrownBy(() -> creditNotes.issue(NewCreditNote.of(
                            invoice.id(), number("CNIT-CN"), AUGUST,
                            List.of(NewCreditNoteLine.priceOnly(
                                    lineId, Quantity.of(1L), UnitCost.ofEur("50.000000"))))))
                    .withMessageContaining("has been reversed");
        }

        @Test
        @DisplayName("reversing an invoice that has credit notes against it")
        void reversingAnInvoiceThatWasPartlyReturned() {
            CustomerView buyer = customer("Partly returned");
            ProductView beans = goods("CNIT-11", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 2L, "50.000000", SalesChannel.ECOMMERCE);
            creditNotes.issue(NewCreditNote.of(invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("50.000000")))));

            assertThatExceptionOfType(
                    gr.novotrade.novocore.core.api.sales.InvalidSalesInvoiceException.class)
                    .isThrownBy(() -> salesInvoices.reverse(invoice.id(), AUGUST, null))
                    .withMessageContaining("credit notes against it");
        }

        @Test
        @DisplayName("reversing a credit note that brought stock back — ADR 0008's principle")
        void reversingACreditNoteThatRestoredStock() {
            CustomerView buyer = customer("Stock back");
            ProductView beans = goods("CNIT-12", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 2L, "50.000000", SalesChannel.ECOMMERCE);

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.returning(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("50.000000")))));

            // A posting that reflects a physically verified event is not un-made once other things
            // depend on it: the goods are on a shelf, in a lot FIFO may already have sold from again.
            assertThatExceptionOfType(InvalidCreditNoteException.class)
                    .isThrownBy(() -> creditNotes.reverse(note.id(), AUGUST, null))
                    .withMessageContaining("not reversible");
        }

        @Test
        @DisplayName("a price-only credit note is reversible, since nothing physical moved")
        void aPriceOnlyCreditNoteIsReversible() {
            CustomerView buyer = customer("Price only");
            ProductView beans = goods("CNIT-13", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 1L, "50.000000", SalesChannel.ECOMMERCE);

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("50.000000")))));

            CreditNoteView reversal = creditNotes.reverse(note.id(), AUGUST, "issued in error");
            assertThat(reversal.isReversal()).isTrue();
            assertThat(creditNotes.require(note.id()).isReversed()).isTrue();
        }
    }

    @Nested
    @DisplayName("enforced by the database")
    class DatabaseInvariants {

        @Test
        @DisplayName("a return consumption recorded without the flag that says stock came back")
        void aReturnConsumptionNeedsItsFlag() {
            // Real rows first: a probe that trips a NOT NULL because its subquery found nothing has
            // proven the wrong thing, which is the failure mode every probe in this repo guards against.
            CustomerView buyer = customer("Flag probe");
            ProductView beans = goods("CNIT-15", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 1L, "50.000000", SalesChannel.ECOMMERCE);
            creditNotes.issue(NewCreditNote.of(invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("50.000000")))));

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO credit_note_line (
                        credit_note_id, line_number, sales_invoice_line_id, quantity, unit_price,
                        unit_price_currency, net_amount, net_amount_currency, vat_amount,
                        vat_amount_currency, stock_returned, return_consumption_id)
                    SELECT (SELECT max(id) FROM credit_note), 997,
                           (SELECT max(id) FROM sales_invoice_line), 1, 1, 'EUR', 1, 'EUR', 0, 'EUR',
                           false, (SELECT max(id) FROM stock_consumption)
                    """))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("credit_note_line_return_needs_the_flag");
        }

        @Test
        @DisplayName("returning more than a consumption ever took out, by trigger")
        void returnsCannotExceedWhatWasTaken() {
            CustomerView buyer = customer("Over-return");
            ProductView beans = goods("CNIT-14", "50.00");
            stock(beans.id(), 10L, "20.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 2L, "50.000000", SalesChannel.ECOMMERCE);
            long consumptionId = invoice.lines().getFirst().stockConsumptionId();

            // Straight to the table, bypassing the service entirely: the invariant has to hold against
            // a psql session too, which is what "structurally" has meant since rule 6.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO stock_consumption (product_id, quantity_requested, quantity_filled,
                        consumption_date, source, returns_consumption_id)
                    VALUES (?, 5, 5, DATE '2026-08-12', 'SALES_INVOICE', ?)
                    """, beans.id(), consumptionId))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasMessageContaining("cannot");
        }

        @Test
        @DisplayName("a row that is both a reversal and a return")
        void aRowIsNeverBoth() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO stock_consumption (product_id, quantity_requested, quantity_filled,
                        consumption_date, source, reversal_of_id, returns_consumption_id)
                    SELECT (SELECT max(id) FROM product), 1, 1, DATE '2026-08-12', 'SALES_INVOICE',
                           (SELECT max(id) FROM stock_consumption),
                           (SELECT max(id) FROM stock_consumption)
                    """))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("stock_consumption_reversal_or_return");
        }
    }
}
