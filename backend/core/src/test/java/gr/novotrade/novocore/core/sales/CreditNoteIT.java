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
import gr.novotrade.novocore.core.api.sales.CreditNotePreview;
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
    @DisplayName("preview — the same arithmetic, without issuing it")
    class Preview {

        /**
         * The guard the preview design rests on: <strong>it agrees with what gets issued.</strong>
         *
         * <p>Same argument as {@code SalesInvoiceIT.previewAgreesWithRecord}. The two share
         * {@code compute()} so they cannot disagree, and this asserts it rather than trusting it.
         */
        @Test
        @DisplayName("preview agrees with issue, figure for figure, on the same request")
        void previewAgreesWithIssue() {
            CustomerView buyer = customer("Preview agreement");
            ProductView beans = goods("CNIT-PV1", "19.99");
            stock(beans.id(), 50L, "4.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 10L, "19.990000", SalesChannel.ECOMMERCE);

            NewCreditNote request = NewCreditNote.of(
                    invoice.id(), number("CNIT-PV"), JULY,
                    List.of(NewCreditNoteLine.returning(
                            invoice.lines().getFirst().id(), Quantity.of(3L),
                            UnitCost.ofEur("19.990000"))));

            CreditNotePreview preview = creditNotes.preview(request);
            CreditNoteView issued = creditNotes.issue(request);

            assertThat(preview.gross()).isEqualTo(issued.grossTotal());
            assertThat(preview.net()).isEqualTo(issued.netTotal());
            assertThat(preview.vat()).isEqualTo(issued.vatTotal());
            assertThat(preview.payable()).isEqualTo(issued.grossTotal());

            assertThat(preview.lines()).hasSameSizeAs(issued.lines());
            for (int i = 0; i < preview.lines().size(); i++) {
                assertThat(preview.lines().get(i).net()).as("line %d net", i)
                        .isEqualTo(issued.lines().get(i).netAmount());
                assertThat(preview.lines().get(i).vat()).as("line %d VAT", i)
                        .isEqualTo(issued.lines().get(i).vatAmount());
            }
        }

        /**
         * The rate comes off the invoice, not from resolving it again — and the preview shows that.
         *
         * <p>This is the reason a credit note needs a preview of its own rather than a client
         * reusing the invoice's arithmetic: <strong>the customer's VAT override is changed between
         * the sale and the credit</strong>, and the credit must still return what was charged. A
         * frontend recomputing the rate would hand back the new one and under- or over-return VAT
         * on a real document.
         */
        @Test
        @DisplayName("preview credits the rate the sale charged, not the customer's rate today")
        void previewUsesTheRateTheSaleCharged() {
            CustomerView buyer = customer("Rate moved");
            ProductView beans = goods("CNIT-PV2", "100.00");
            stock(beans.id(), 20L, "40.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 2L, "100.000000", SalesChannel.ECOMMERCE);

            Money vatCharged = invoice.lines().getFirst().vatAmount();
            Long classCharged = invoice.lines().getFirst().vatClassId();

            // The customer moves to the reduced class after buying.
            customers.changeVatClassOverride(buyer.id(), vatClasses.requireByCode("1131").id());

            CreditNotePreview preview = creditNotes.preview(NewCreditNote.of(
                    invoice.id(), number("CNIT-PV"), JULY,
                    List.of(NewCreditNoteLine.returning(
                            invoice.lines().getFirst().id(), Quantity.of(2L),
                            UnitCost.ofEur("100.000000")))));

            assertThat(preview.vat())
                    .as("a return gives back the VAT the sale actually took, whatever the customer's "
                            + "override says now")
                    .isEqualTo(vatCharged);
            assertThat(preview.lines().getFirst().vatClassId()).isEqualTo(classCharged);
        }

        @Test
        @DisplayName("preview issues nothing — no note, no entry, and the number stays free")
        void previewWritesNothing() {
            CustomerView buyer = customer("Preview writes nothing");
            ProductView beans = goods("CNIT-PV3", "10.00");
            stock(beans.id(), 20L, "4.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 5L, "10.000000", SalesChannel.ECOMMERCE);
            String documentNumber = number("CNIT-PV");

            NewCreditNote request = NewCreditNote.of(
                    invoice.id(), documentNumber, JULY,
                    List.of(NewCreditNoteLine.returning(
                            invoice.lines().getFirst().id(), Quantity.of(1L),
                            UnitCost.ofEur("10.000000"))));

            creditNotes.preview(request);
            creditNotes.preview(request);

            assertThat(creditNotes.againstInvoice(invoice.id()))
                    .as("previewing twice issued nothing")
                    .isEmpty();

            // The stock did not come back either — a preview that restored stock would be the worst
            // of both, since nothing posted to carry it.
            assertThat(inventory.stockOf(beans.id()).sellable()).isEqualTo(Quantity.of(15L));

            // And the number is still free, which is the operative proof.
            assertThat(creditNotes.issue(request).documentNumber()).isEqualTo(documentNumber);
        }

        @Test
        @DisplayName("an unaccepted large difference is reported by preview and refused by issue")
        void previewReportsWhatIssueRefuses() {
            CustomerView buyer = customer("Preview disagreement");
            ProductView beans = goods("CNIT-PV4", "10.00");
            stock(beans.id(), 20L, "4.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 5L, "10.000000", SalesChannel.ECOMMERCE);

            NewCreditNote request = NewCreditNote.of(
                            invoice.id(), number("CNIT-PV"), JULY,
                            List.of(NewCreditNoteLine.returning(
                                    invoice.lines().getFirst().id(), Quantity.of(1L),
                                    UnitCost.ofEur("10.000000"))))
                    .statedAs(Money.ofEur("15.00"));

            CreditNotePreview preview = creditNotes.preview(request);

            assertThat(preview.roundingNeedsAcceptance())
                    .as("the screen must be able to learn this without submitting")
                    .isTrue();
            assertThat(preview.gross()).isEqualTo(Money.ofEur("12.40"));
            assertThat(preview.roundingDifference()).isEqualTo(Money.ofEur("2.60"));

            assertThatExceptionOfType(InvalidCreditNoteException.class)
                    .isThrownBy(() -> creditNotes.issue(request))
                    .withMessageContaining("rounding threshold");

            assertThat(creditNotes.preview(
                    request.acceptingRoundingDifference("kostas", "Go rounded it"))
                    .roundingNeedsAcceptance())
                    .isFalse();
        }

        @Test
        @DisplayName("preview refuses what issue refuses — including crediting more than was sold")
        void previewRefusesWhatIssueRefuses() {
            CustomerView buyer = customer("Preview refusals");
            ProductView beans = goods("CNIT-PV5", "10.00");
            stock(beans.id(), 20L, "4.000000");
            SalesInvoiceView invoice = sale(buyer, beans, 2L, "10.000000", SalesChannel.ECOMMERCE);

            // Crediting more than the line sold. This is the refusal most worth having before the
            // operator submits, because what is left to credit depends on every earlier credit note
            // and is not something the screen could work out for itself.
            assertThatExceptionOfType(InvalidCreditNoteException.class)
                    .isThrownBy(() -> creditNotes.preview(NewCreditNote.of(
                            invoice.id(), number("CNIT-PV"), JULY,
                            List.of(NewCreditNoteLine.returning(
                                    invoice.lines().getFirst().id(), Quantity.of(5L),
                                    UnitCost.ofEur("10.000000"))))));
        }
    }

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
        @DisplayName("a cash sale's credit note credits the till, not Accounts receivable")
        void aBornSettledCreditNoteMirrorsItsInvoice() {
            // REVERSED IN STEP 15, and this test used to assert the opposite. The old rule — always
            // credit AR, "the money is owed back until it is actually refunded" — made the two
            // halves of a born-settled transaction asymmetric: a CASH sale debits Cash and never
            // touches AR, while its credit note moved AR. Since bornSettled() also keeps such an
            // invoice out of the open-item layer, the AR control account and the sum of the open
            // items disagreed by exactly the credit note. ADR 0009 says that cannot happen.
            //
            // Step 15's HTTP narrative measured it; nothing before it had credited a cash, POS or
            // Skroutz sale. Mirroring the invoice closes the class structurally: neither half ever
            // touches AR, so there is nothing left for them to disagree about.
            CustomerView buyer = customer("Cash return");
            ProductView beans = goods("CNIT-02", "20.00");
            stock(beans.id(), 10L, "8.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.CASH,
                    number("CNIT-SI"), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("20.000000")))));
            assertThat(invoice.bornSettled()).isTrue();

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("20.000000")))));

            assertThat(note.bornSettled())
                    .as("the credit note carries its invoice's settlement method, so the open-item "
                            + "layer can exclude it exactly as it excludes the invoice")
                    .isTrue();
            assertThat(journal.requireEntry(note.journalEntryId()).lines())
                    .anySatisfy(line -> {
                        assertThat(line.accountId()).isEqualTo(accountId(AccountSystemKey.CASH));
                        assertThat(line.side()).isEqualTo(BalanceSide.CREDIT);
                        // Cash is not a Control account, so no sub-ledger reference — the same
                        // asymmetry the invoice already has on its debit side.
                        assertThat(line.subLedgerRef()).isNull();
                    })
                    .noneSatisfy(line -> assertThat(line.accountId())
                            .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE)));
        }

        @Test
        @DisplayName("an on-account sale's credit note still credits Accounts receivable")
        void anOnAccountCreditNoteStillUsesReceivable() {
            // The other half of the same rule, and the reason it is a mirror rather than a blanket
            // change: an ON_ACCOUNT sale really does debit AR and really is an open item, so its
            // credit note has to credit AR or the invoice would never be settleable against it.
            CustomerView buyer = customer("On account return");
            ProductView beans = goods("CNIT-02B", "20.00");
            stock(beans.id(), 10L, "8.000000");

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), SalesChannel.ECOMMERCE, SettlementMethod.ON_ACCOUNT,
                    number("CNIT-SI"), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("20.000000")))));
            assertThat(invoice.bornSettled()).isFalse();

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("CNIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("20.000000")))));

            assertThat(note.bornSettled()).isFalse();
            assertThat(journal.requireEntry(note.journalEntryId()).lines())
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE));
                        assertThat(line.side()).isEqualTo(BalanceSide.CREDIT);
                        assertThat(line.subLedgerRef())
                                .isEqualTo(SubLedgerRef.customer(buyer.id()));
                    })
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
