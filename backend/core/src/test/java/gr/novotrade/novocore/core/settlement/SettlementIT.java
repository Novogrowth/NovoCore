package gr.novotrade.novocore.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.customer.CustomerService;
import gr.novotrade.novocore.core.api.customer.CustomerView;
import gr.novotrade.novocore.core.api.customer.NewCustomer;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
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
import gr.novotrade.novocore.core.api.sales.NewCreditNote;
import gr.novotrade.novocore.core.api.sales.NewCreditNoteLine;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoice;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoiceLine;
import gr.novotrade.novocore.core.api.sales.SalesChannel;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceService;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceView;
import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import gr.novotrade.novocore.core.api.settlement.AllocationView;
import gr.novotrade.novocore.core.api.settlement.CustomerCreditView;
import gr.novotrade.novocore.core.api.settlement.InvalidSettlementException;
import gr.novotrade.novocore.core.api.settlement.NewAllocation;
import gr.novotrade.novocore.core.api.settlement.NewSettlement;
import gr.novotrade.novocore.core.api.settlement.OpenItem;
import gr.novotrade.novocore.core.api.settlement.OpenItemRef;
import gr.novotrade.novocore.core.api.settlement.PartyType;
import gr.novotrade.novocore.core.api.settlement.SettlementService;
import gr.novotrade.novocore.core.api.settlement.SettlementView;
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
 * Receipts, Payments and brief §6's open item matching.
 *
 * <p>The load-bearing claim being defended here is that <strong>documents post and allocations do
 * not</strong>. Everything else follows: an open amount is computed rather than stored, an allocation
 * can be reduced or released without touching a posted entry, and Q13's second half — editing a
 * receipt below its allocated total — is implementable at all.
 */
class SettlementIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 20);
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 12);

    private static final AtomicInteger NUMBERS = new AtomicInteger();

    @Autowired
    private SettlementService settlements;

    @Autowired
    private gr.novotrade.novocore.core.api.supplier.SupplierService suppliers;

    @Autowired
    private gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService purchaseInvoices;

    @Autowired
    private SalesInvoiceService salesInvoices;

    @Autowired
    private CreditNoteService creditNotes;

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
        return customers.create(NewCustomer.retail("SETIT — " + name, null, null));
    }

    private ProductView goods(String sku) {
        return products.create(NewProduct.goods(sku, sku + " goods",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur("100.00")));
    }

    /** A sale on account, so it has an open amount. Gross is 124.00 for one at 100.00. */
    private SalesInvoiceView openSale(CustomerView buyer, String sku, long quantity) {
        ProductView product = goods(sku);
        inventory.receive(NewInventoryLot.pooled(product.id(), Quantity.of(quantity + 5),
                UnitCost.ofEur("40.000000"), MARCH, StockLocation.INVENTORY));
        return salesInvoices.record(NewSalesInvoice.of(buyer.id(), SalesChannel.ECOMMERCE,
                SettlementMethod.ON_ACCOUNT, number("SETIT-SI"), JULY,
                List.of(NewSalesInvoiceLine.product(
                        product.id(), Quantity.of(quantity), UnitCost.ofEur("100.000000")))));
    }

    private AccountView bank() {
        return chartOfAccounts.allAccounts().stream()
                .filter(account -> account.name().equals("Alpha Bank"))
                .findFirst().orElseThrow();
    }

    private long accountId(AccountSystemKey key) {
        return chartOfAccounts.requireAccount(key).id();
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("what a receipt posts, and what an allocation does not")
    class Posting {

        @Test
        @DisplayName("debit the bank, credit the customer's receivable — and the allocation posts nothing")
        void receiptPostsOnceAndAllocatesSeparately() {
            CustomerView buyer = customer("Receipt");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-01", 1L);
            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(invoice.id())))
                    .isEqualTo(Money.ofEur("124.00"));

            SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                    buyer.id(), bank().id(), AUGUST, Money.ofEur("124.00"),
                    List.of(NewAllocation.againstSalesInvoice(
                            invoice.id(), Money.ofEur("124.00")))));

            JournalEntryView entry = journal.requireEntry(receipt.journalEntryId());
            assertThat(entry.source()).isEqualTo(JournalSource.RECEIPT);
            assertThat(entry.lines()).hasSize(2)
                    .anySatisfy(line -> {
                        assertThat(line.accountId()).isEqualTo(bank().id());
                        assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                    })
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE));
                        assertThat(line.side()).isEqualTo(BalanceSide.CREDIT);
                        assertThat(line.subLedgerRef())
                                .isEqualTo(SubLedgerRef.customer(buyer.id()));
                    });

            // Two lines, not four: the allocation is open-item bookkeeping, and an entry for it would
            // debit and credit Accounts receivable for the same amount.
            assertThat(receipt.isFullyAllocated()).isTrue();
            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(invoice.id())))
                    .isEqualTo(Money.ofEur("0.00"));
            assertThat(journal.subLedgerBalanceOf(SubLedgerRef.customer(buyer.id()), AUGUST))
                    .isEqualTo(Money.ofEur("0.00"));
        }

        @Test
        @DisplayName("an instalment leaves the rest open, and the second one closes it")
        void instalments() {
            CustomerView buyer = customer("Instalments");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-02", 1L);

            settlements.record(NewSettlement.receiptFrom(buyer.id(), bank().id(), AUGUST,
                    Money.ofEur("50.00"),
                    List.of(NewAllocation.againstSalesInvoice(
                            invoice.id(), Money.ofEur("50.00")))));
            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(invoice.id())))
                    .isEqualTo(Money.ofEur("74.00"));

            settlements.record(NewSettlement.receiptFrom(buyer.id(), bank().id(), AUGUST,
                    Money.ofEur("74.00"),
                    List.of(NewAllocation.againstSalesInvoice(
                            invoice.id(), Money.ofEur("74.00")))));
            assertThat(settlements.openItemsFor(PartyType.CUSTOMER, buyer.id())).isEmpty();
        }

        @Test
        @DisplayName("a bulk remittance can be recorded unmatched and matched later")
        void bulkRemittanceIsMatchedAfterwards() {
            CustomerView buyer = customer("Remittance");
            SalesInvoiceView first = openSale(buyer, "SETIT-03", 1L);
            SalesInvoiceView second = openSale(buyer, "SETIT-04", 1L);

            SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                    buyer.id(), bank().id(), AUGUST, Money.ofEur("248.00"), List.of()));

            // Brief §6's "unmatched lines flagged for Clearing Checks" — a query over computed state,
            // not a review queue that would go stale the moment somebody matched it without visiting.
            assertThat(receipt.unallocatedAmount()).isEqualTo(Money.ofEur("248.00"));
            assertThat(settlements.withUnallocatedAmount())
                    .extracting(SettlementView::id).contains(receipt.id());

            settlements.allocate(receipt.id(), List.of(
                    NewAllocation.againstSalesInvoice(first.id(), Money.ofEur("124.00")),
                    NewAllocation.againstSalesInvoice(second.id(), Money.ofEur("124.00"))));

            assertThat(settlements.require(receipt.id()).isFullyAllocated()).isTrue();
            assertThat(settlements.withUnallocatedAmount())
                    .extracting(SettlementView::id).doesNotContain(receipt.id());
        }
    }

    @Nested
    @DisplayName("open amounts are computed, never stored")
    class OpenAmounts {

        @Test
        @DisplayName("a sale born settled in cash never has an open amount")
        void cashSaleIsNeverAnOpenItem() {
            CustomerView buyer = customer("Cash sale");
            ProductView beans = goods("SETIT-05");
            inventory.receive(NewInventoryLot.pooled(beans.id(), Quantity.of(5L),
                    UnitCost.ofEur("40.000000"), MARCH, StockLocation.INVENTORY));

            SalesInvoiceView invoice = salesInvoices.record(NewSalesInvoice.of(
                    buyer.id(), SalesChannel.STORE_AND_PHONE, SettlementMethod.CASH,
                    number("SETIT-SI"), JULY,
                    List.of(NewSalesInvoiceLine.product(
                            beans.id(), Quantity.of(1L), UnitCost.ofEur("100.000000")))));

            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(invoice.id())))
                    .isEqualTo(Money.ofEur("0.00"));
            assertThat(settlements.openItemsFor(PartyType.CUSTOMER, buyer.id()))
                    .extracting(item -> item.ref().id()).doesNotContain(invoice.id());

            // And nothing may be allocated against it, because it was settled at the till.
            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.record(NewSettlement.receiptFrom(
                            buyer.id(), bank().id(), AUGUST, Money.ofEur("10.00"),
                            List.of(NewAllocation.againstSalesInvoice(
                                    invoice.id(), Money.ofEur("10.00"))))))
                    .withMessageContaining("born fully settled");
        }

        @Test
        @DisplayName("open items come back oldest first — the order a receipt is applied in")
        void openItemsAreOldestFirst() {
            CustomerView buyer = customer("Ordering");
            SalesInvoiceView older = openSale(buyer, "SETIT-06", 1L);
            SalesInvoiceView newer = openSale(buyer, "SETIT-07", 1L);

            List<OpenItem> items = settlements.openItemsFor(PartyType.CUSTOMER, buyer.id());
            assertThat(items).extracting(item -> item.ref().id())
                    .containsExactly(older.id(), newer.id());
            assertThat(items.getFirst().isUntouched()).isTrue();
        }
    }

    @Nested
    @DisplayName("Q13's second half — editing a receipt below its allocated total")
    class AmendingAReceipt {

        @Test
        @DisplayName("allocations are released most-recent-first, and the last is reduced not dropped")
        void allocationsAreReleasedMostRecentFirst() {
            CustomerView buyer = customer("Amend");
            SalesInvoiceView first = openSale(buyer, "SETIT-08", 1L);
            SalesInvoiceView second = openSale(buyer, "SETIT-09", 1L);
            SalesInvoiceView third = openSale(buyer, "SETIT-10", 1L);

            SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                    buyer.id(), bank().id(), AUGUST, Money.ofEur("372.00"),
                    List.of(
                            NewAllocation.againstSalesInvoice(first.id(), Money.ofEur("124.00")),
                            NewAllocation.againstSalesInvoice(second.id(), Money.ofEur("124.00")),
                            NewAllocation.againstSalesInvoice(third.id(), Money.ofEur("124.00")))));
            assertThat(receipt.allocations()).hasSize(3);

            // The amount was typed wrong: it was 200.00, not 372.00.
            SettlementView amended = settlements.amend(receipt.id(), bank().id(), AUGUST,
                    Money.ofEur("200.00"), null, "corrected");

            assertThat(amended.amount()).isEqualTo(Money.ofEur("200.00"));
            assertThat(amended.allocatedAmount()).isEqualTo(Money.ofEur("200.00"));

            // Most-recent-first: the third allocation goes entirely, the second is reduced to what
            // still fits, and the first — the one somebody deliberately matched — is untouched.
            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(first.id())))
                    .isEqualTo(Money.ofEur("0.00"));
            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(second.id())))
                    .isEqualTo(Money.ofEur("48.00"));
            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(third.id())))
                    .isEqualTo(Money.ofEur("124.00"));

            // The ledger moved with it, which is what makes "editable in place" a correction rather
            // than a second document.
            JournalEntryView entry = journal.requireEntry(receipt.journalEntryId());
            assertThat(entry.lines()).allSatisfy(
                    line -> assertThat(line.amount()).isEqualTo(Money.ofEur("200.00")));
        }

        @Test
        @DisplayName("increasing a receipt touches no allocation")
        void increasingLeavesAllocationsAlone() {
            CustomerView buyer = customer("Increase");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-11", 2L);

            SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                    buyer.id(), bank().id(), AUGUST, Money.ofEur("100.00"),
                    List.of(NewAllocation.againstSalesInvoice(
                            invoice.id(), Money.ofEur("100.00")))));

            SettlementView amended = settlements.amend(receipt.id(), bank().id(), AUGUST,
                    Money.ofEur("150.00"), null, null);

            assertThat(amended.allocatedAmount()).isEqualTo(Money.ofEur("100.00"));
            assertThat(amended.unallocatedAmount()).isEqualTo(Money.ofEur("50.00"));
        }

        @Test
        @DisplayName("releasing an allocation puts the open amount back on both ends")
        void releasingAnAllocation() {
            CustomerView buyer = customer("Release");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-12", 1L);

            SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                    buyer.id(), bank().id(), AUGUST, Money.ofEur("124.00"),
                    List.of(NewAllocation.againstSalesInvoice(
                            invoice.id(), Money.ofEur("124.00")))));

            settlements.release(receipt.allocations().getFirst().id());

            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(invoice.id())))
                    .isEqualTo(Money.ofEur("124.00"));
            assertThat(settlements.require(receipt.id()).unallocatedAmount())
                    .isEqualTo(Money.ofEur("124.00"));
            // The money never moved, so nothing about the posted entry changed.
            assertThat(journal.requireEntry(receipt.journalEntryId()).lines()).allSatisfy(
                    line -> assertThat(line.amount()).isEqualTo(Money.ofEur("124.00")));
        }
    }

    @Nested
    @DisplayName("Q16 — unallocated credit as a standalone document")
    class CustomerCredit {

        @Test
        @DisplayName("an overpayment becomes a credit document, and only when the caller says so")
        void overpaymentBecomesCredit() {
            CustomerView buyer = customer("Overpaid");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-13", 1L);

            SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                            buyer.id(), bank().id(), AUGUST, Money.ofEur("200.00"),
                            List.of(NewAllocation.againstSalesInvoice(
                                    invoice.id(), Money.ofEur("124.00"))))
                    .leavingCredit());

            assertThat(receipt.leftCredit()).isTrue();
            List<CustomerCreditView> credits = settlements.customerCreditsOf(buyer.id());
            assertThat(credits).hasSize(1);
            assertThat(credits.getFirst().amount()).isEqualTo(Money.ofEur("76.00"));
            assertThat(credits.getFirst().isUntouched()).isTrue();
        }

        @Test
        @DisplayName("reducing a settlement that left a credit is refused, and names the remedy")
        void amendingASettlementWithALiveCreditIsRefused() {
            // Found by step 15's HTTP narrative. A settlement reduced after it left a customer
            // credit re-posts the ledger for the smaller amount while the credit document carries
            // on claiming the original remainder — so the credit has nothing behind it, and if any
            // of it has been allocated, that allocation reduces an invoice's open amount with no
            // ledger movement at all. Accounts receivable and the sum of the open items then
            // disagree, which ADR 0009 says is impossible by construction. It was measured at
            // exactly the allocated amount.
            //
            // Refused rather than auto-corrected (CLAUDE.md rule 7): silently shrinking the credit
            // would undo, as a side effect of editing this document, an allocation somebody
            // deliberately made against another one. Same stance as ADR 0011's refusal to reverse
            // a freight allocation whose lot has since moved.
            CustomerView buyer = customer("Amend with credit");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-13B", 1L);

            SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                            buyer.id(), bank().id(), AUGUST, Money.ofEur("200.00"),
                            List.of(NewAllocation.againstSalesInvoice(
                                    invoice.id(), Money.ofEur("124.00"))))
                    .leavingCredit());
            CustomerCreditView credit = settlements.customerCreditsOf(buyer.id()).getFirst();

            // Untouched credit: still refused, because the reduction would strand it either way.
            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.amend(receipt.id(), bank().id(), AUGUST,
                            Money.ofEur("150.00"), null, null))
                    .withMessageContaining("left a customer credit")
                    .withMessageContaining("released outright");

            // Once part of it is spent, the message names the harder remedy instead.
            SalesInvoiceView second = openSale(buyer, "SETIT-13C", 1L);
            settlements.allocateCustomerCredit(credit.id(), second.id(), Money.ofEur("20.00"));

            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.amend(receipt.id(), bank().id(), AUGUST,
                            Money.ofEur("150.00"), null, null))
                    .withMessageContaining("20.00 EUR of the credit has already been allocated")
                    .withMessageContaining("release that allocation first");

            // And an amendment that does NOT reduce the settlement is unaffected: the credit is
            // still covered, so there is nothing to protect against.
            assertThat(settlements.amend(receipt.id(), bank().id(), AUGUST,
                    Money.ofEur("250.00"), "SETIT-UP", null).amount())
                    .isEqualTo(Money.ofEur("250.00"));
        }

        @Test
        @DisplayName("credit is spent against a later invoice, and posts nothing")
        void creditIsSpentLater() {
            CustomerView buyer = customer("Spend credit");
            SalesInvoiceView first = openSale(buyer, "SETIT-14", 1L);

            settlements.record(NewSettlement.receiptFrom(buyer.id(), bank().id(), AUGUST,
                            Money.ofEur("200.00"),
                            List.of(NewAllocation.againstSalesInvoice(
                                    first.id(), Money.ofEur("124.00"))))
                    .leavingCredit());
            CustomerCreditView credit = settlements.customerCreditsOf(buyer.id()).getFirst();

            SalesInvoiceView later = openSale(buyer, "SETIT-15", 1L);
            long entriesBefore = journal.entriesBetween(AUGUST, AUGUST).size();

            AllocationView applied = settlements.allocateCustomerCredit(
                    credit.id(), later.id(), Money.ofEur("76.00"));

            assertThat(applied.amount()).isEqualTo(Money.ofEur("76.00"));
            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(later.id())))
                    .isEqualTo(Money.ofEur("48.00"));
            // Both ends are Accounts receivable, so there is nothing for an entry to say.
            assertThat(journal.entriesBetween(AUGUST, AUGUST)).hasSize((int) entriesBefore);

            assertThat(settlements.customerCreditsOf(buyer.id()).getFirst().isExhausted()).isTrue();
        }

        @Test
        @DisplayName("credit cannot be spent twice")
        void creditCannotBeSpentTwice() {
            CustomerView buyer = customer("Double spend");
            SalesInvoiceView first = openSale(buyer, "SETIT-16", 1L);
            settlements.record(NewSettlement.receiptFrom(buyer.id(), bank().id(), AUGUST,
                            Money.ofEur("174.00"),
                            List.of(NewAllocation.againstSalesInvoice(
                                    first.id(), Money.ofEur("124.00"))))
                    .leavingCredit());
            CustomerCreditView credit = settlements.customerCreditsOf(buyer.id()).getFirst();

            SalesInvoiceView second = openSale(buyer, "SETIT-17", 1L);
            settlements.allocateCustomerCredit(credit.id(), second.id(), Money.ofEur("50.00"));

            SalesInvoiceView third = openSale(buyer, "SETIT-18", 1L);
            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.allocateCustomerCredit(
                            credit.id(), third.id(), Money.ofEur("50.00")))
                    .withMessageContaining("cannot supply");
        }
    }

    @Nested
    @DisplayName("credit notes as a source, and refunds")
    class CreditNotes {

        @Test
        @DisplayName("a credit note set against an invoice reduces both, and posts nothing")
        void creditNoteSettlesAnInvoice() {
            CustomerView buyer = customer("Credit note");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-19", 2L);

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("SETIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("100.000000")))));

            assertThat(settlements.openAmountOf(OpenItemRef.creditNote(note.id())))
                    .isEqualTo(Money.ofEur("124.00"));

            settlements.allocateCreditNote(note.id(), invoice.id(), Money.ofEur("124.00"));

            assertThat(settlements.openAmountOf(OpenItemRef.creditNote(note.id())))
                    .isEqualTo(Money.ofEur("0.00"));
            assertThat(settlements.openAmountOf(OpenItemRef.salesInvoice(invoice.id())))
                    .isEqualTo(Money.ofEur("124.00"));
        }

        @Test
        @DisplayName("a refund to a customer is an outgoing settlement against the credit note")
        void refundingACreditNote() {
            CustomerView buyer = customer("Refund");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-20", 1L);

            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("SETIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("100.000000")))));

            SettlementView refund = settlements.record(NewSettlement.refundTo(
                    buyer.id(), bank().id(), AUGUST, Money.ofEur("124.00"),
                    List.of(NewAllocation.againstCreditNote(note.id(), Money.ofEur("124.00")))));

            // Outgoing to a CUSTOMER: debit Accounts receivable, credit the bank. All four
            // direction/party combinations are real, which is why the two are separate enums.
            JournalEntryView entry = journal.requireEntry(refund.journalEntryId());
            assertThat(entry.source()).isEqualTo(JournalSource.PAYMENT);
            assertThat(entry.lines())
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_RECEIVABLE));
                        assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                    })
                    .anySatisfy(line -> {
                        assertThat(line.accountId()).isEqualTo(bank().id());
                        assertThat(line.side()).isEqualTo(BalanceSide.CREDIT);
                    });
            assertThat(settlements.openAmountOf(OpenItemRef.creditNote(note.id())))
                    .isEqualTo(Money.ofEur("0.00"));
        }
    }

    @Nested
    @DisplayName("the payment side — the same table, the other sub-ledger")
    class Payments {

        @Test
        @DisplayName("paying a supplier debits Accounts payable and credits the bank")
        void payingASupplier() {
            var supplier = suppliers.create(
                    gr.novotrade.novocore.core.api.supplier.NewSupplier.domestic(
                            "SETIT — Supplier " + NUMBERS.incrementAndGet(), null));
            var expenseAccount = chartOfAccounts.allAccounts().stream()
                    .filter(account -> account.name().equals("Rent"))
                    .findFirst().orElseThrow();

            var invoice = purchaseInvoices.record(
                    new gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoice(
                            supplier.id(), number("SETIT-PI"), JULY, null,
                            List.of(gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoiceLine
                                    .expense(expenseAccount.id(), Money.ofEur("500.00"),
                                            vatClasses.requireByCode("1410").id()))));

            assertThat(settlements.openAmountOf(OpenItemRef.purchaseInvoice(invoice.id())))
                    .isEqualTo(Money.ofEur("620.00"));

            SettlementView payment = settlements.record(NewSettlement.paymentTo(
                    supplier.id(), bank().id(), AUGUST, Money.ofEur("620.00"),
                    List.of(NewAllocation.againstPurchaseInvoice(
                            invoice.id(), Money.ofEur("620.00")))));

            JournalEntryView entry = journal.requireEntry(payment.journalEntryId());
            assertThat(entry.source()).isEqualTo(JournalSource.PAYMENT);
            assertThat(entry.lines())
                    .anySatisfy(line -> {
                        assertThat(line.accountId())
                                .isEqualTo(accountId(AccountSystemKey.ACCOUNTS_PAYABLE));
                        assertThat(line.side()).isEqualTo(BalanceSide.DEBIT);
                        assertThat(line.subLedgerRef())
                                .isEqualTo(SubLedgerRef.supplier(supplier.id()));
                    })
                    .anySatisfy(line -> {
                        assertThat(line.accountId()).isEqualTo(bank().id());
                        assertThat(line.side()).isEqualTo(BalanceSide.CREDIT);
                    });

            assertThat(settlements.openItemsFor(PartyType.SUPPLIER, supplier.id())).isEmpty();
            assertThat(journal.subLedgerBalanceOf(SubLedgerRef.supplier(supplier.id()), AUGUST))
                    .isEqualTo(Money.ofEur("0.00"));
        }

        @Test
        @DisplayName("money received from a supplier has nothing here to be allocated against")
        void aSupplierRefundHasNoTarget() {
            var supplier = suppliers.create(
                    gr.novotrade.novocore.core.api.supplier.NewSupplier.domestic(
                            "SETIT — Refunder " + NUMBERS.incrementAndGet(), null));

            // NovoCore records no supplier credit note, so an incoming supplier settlement sits
            // unallocated against their payable balance — which is what it actually is. Inventing a
            // document to point at would be worse than saying so.
            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.record(new NewSettlement(
                            gr.novotrade.novocore.core.api.settlement.SettlementDirection.INCOMING,
                            PartyType.SUPPLIER, supplier.id(), bank().id(), AUGUST,
                            Money.ofEur("10.00"), null, null,
                            List.of(NewAllocation.againstPurchaseInvoice(
                                    1L, Money.ofEur("10.00"))), false)))
                    .withMessageContaining("nothing to be allocated against");
        }
    }

    @Nested
    @DisplayName("what is refused")
    class Refusals {

        @Test
        @DisplayName("allocating more than the money that moved")
        void overAllocatingASettlement() {
            CustomerView buyer = customer("Over-allocate");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-21", 2L);

            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.record(NewSettlement.receiptFrom(
                            buyer.id(), bank().id(), AUGUST, Money.ofEur("50.00"),
                            List.of(NewAllocation.againstSalesInvoice(
                                    invoice.id(), Money.ofEur("100.00"))))))
                    .withMessageContaining("left to apply");
        }

        @Test
        @DisplayName("settling another customer's invoice")
        void settlingSomebodyElsesInvoice() {
            CustomerView buyer = customer("Payer");
            CustomerView other = customer("Other");
            SalesInvoiceView theirInvoice = openSale(other, "SETIT-22", 1L);

            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.record(NewSettlement.receiptFrom(
                            buyer.id(), bank().id(), AUGUST, Money.ofEur("124.00"),
                            List.of(NewAllocation.againstSalesInvoice(
                                    theirInvoice.id(), Money.ofEur("124.00"))))))
                    .withMessageContaining("belongs to a different");
        }

        @Test
        @DisplayName("a customer's receipt settling a supplier's invoice")
        void crossingTheTwoSubLedgers() {
            CustomerView buyer = customer("Wrong ledger");
            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.record(NewSettlement.receiptFrom(
                            buyer.id(), bank().id(), AUGUST, Money.ofEur("10.00"),
                            List.of(NewAllocation.againstPurchaseInvoice(
                                    1L, Money.ofEur("10.00"))))))
                    .withMessageContaining("different control accounts");
        }

        @Test
        @DisplayName("money moving through an account it cannot be held in")
        void anAccountMoneyCannotSitIn() {
            CustomerView buyer = customer("Wrong account");
            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.record(NewSettlement.receiptFrom(
                            buyer.id(), accountId(AccountSystemKey.COST_OF_GOODS_SOLD), AUGUST,
                            Money.ofEur("10.00"), List.of())))
                    .withMessageContaining("money cannot be");
        }

        @Test
        @DisplayName("a cash movement at the legal limit")
        void cashLimitApplies() {
            CustomerView buyer = customer("Cash limit");
            assertThatExceptionOfType(InvalidSettlementException.class)
                    .isThrownBy(() -> settlements.record(NewSettlement.receiptFrom(
                            buyer.id(), accountId(AccountSystemKey.CASH), AUGUST,
                            Money.ofEur("500.00"), List.of())))
                    .withMessageContaining("legal cash limit");
        }
    }

    @Nested
    @DisplayName("enforced by the database")
    class DatabaseInvariants {

        @Test
        @DisplayName("a settlement naming a party that does not exist, by trigger")
        void thePartyMustExist() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO settlement (direction, party_type, party_id, account_id,
                        settlement_date, amount, amount_currency, journal_entry_id)
                    SELECT 'INCOMING', 'CUSTOMER', 999999, (SELECT max(id) FROM account),
                           DATE '2026-08-12', 1, 'EUR', (SELECT max(id) FROM journal_entry)
                    """))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasMessageContaining("does not exist");
        }

        @Test
        @DisplayName("an allocation naming a document that does not exist, by trigger")
        void bothEndsMustExist() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO open_item_allocation (source_type, source_id, target_type, target_id,
                        allocation_order, amount, amount_currency)
                    SELECT 'SETTLEMENT', (SELECT max(id) FROM settlement), 'SALES_INVOICE', 999999,
                           99, 1, 'EUR'
                    """))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasMessageContaining("does not exist");
        }

        @Test
        @DisplayName("a credit note settling another credit note — a pairing that means nothing")
        void thePairingMustBeMeaningful() {
            // Real rows on both ends, so the pairing CHECK is what refuses this rather than the
            // existence trigger firing first and proving something else.
            CustomerView buyer = customer("Pairing");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-24", 1L);
            CreditNoteView note = creditNotes.issue(NewCreditNote.of(
                    invoice.id(), number("SETIT-CN"), AUGUST,
                    List.of(NewCreditNoteLine.priceOnly(invoice.lines().getFirst().id(),
                            Quantity.of(1L), UnitCost.ofEur("100.000000")))));

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO open_item_allocation (source_type, source_id, target_type, target_id,
                        allocation_order, amount, amount_currency)
                    VALUES ('CREDIT_NOTE', ?, 'CREDIT_NOTE', ?, 98, 1, 'EUR')
                    """, note.id(), note.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("open_item_allocation");
        }

        @Test
        @DisplayName("one source settling one target twice — the amount carries 'some of it'")
        void onePairOneRow() {
            CustomerView buyer = customer("Pair");
            SalesInvoiceView invoice = openSale(buyer, "SETIT-23", 1L);
            SettlementView receipt = settlements.record(NewSettlement.receiptFrom(
                    buyer.id(), bank().id(), AUGUST, Money.ofEur("124.00"),
                    List.of(NewAllocation.againstSalesInvoice(
                            invoice.id(), Money.ofEur("50.00")))));

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO open_item_allocation (source_type, source_id, target_type, target_id,
                        allocation_order, amount, amount_currency)
                    VALUES ('SETTLEMENT', ?, 'SALES_INVOICE', ?, 97, 1, 'EUR')
                    """, receipt.id(), invoice.id()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("open_item_allocation_pair_unique");
        }
    }
}
