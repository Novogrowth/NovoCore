package gr.novotrade.novocore.core.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewStockConsumption;
import gr.novotrade.novocore.core.api.ledger.AccountBalance;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.VatDirection;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptMatch;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptView;
import gr.novotrade.novocore.core.api.purchasing.InvalidPurchaseInvoiceException;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceiptLine;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoice;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoiceLine;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceView;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.supplier.SupplierView;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import gr.novotrade.novocore.core.api.tax.VatExemptionReasonService;
import gr.novotrade.novocore.core.api.tax.VatStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Purchase invoices, and the GR/IR clearing they resolve against deliveries (ADR 0004, ADR 0008).
 *
 * <p>The tests worth reading twice are the two directions of clearing. Invoice-first is the easy one:
 * the delivery takes its price from the invoice and GR/IR nets to zero with nothing left over.
 * Goods-first is the one ADR 0008 exists for — the lot was costed at a guess, the invoice disagrees,
 * and the difference has to land in the variance account rather than in the lot.
 */
class PurchaseInvoiceIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 15);

    @Autowired
    private PurchaseInvoiceService invoices;

    @Autowired
    private GoodsReceiptService receipts;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private ProductService products;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private ChartOfAccountsService chart;

    @Autowired
    private JournalService journal;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private VatExemptionReasonService exemptionReasons;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    /** For the probes that bypass the service entirely — the only way to prove a rule is the schema's. */
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private long standardRate() {
        return vatClasses.requireByCode("1410").id();
    }

    private SupplierView domestic(String name) {
        return suppliers.create(NewSupplier.domestic(name, "EL" + Math.abs(name.hashCode())));
    }

    private SupplierView intraEu(String name) {
        return suppliers.create(new NewSupplier(name, null, null,
                "DE" + Math.abs(name.hashCode()), VatStatus.INTRA_EU_B2B, null));
    }

    private ProductView product(String sku) {
        return products.create(NewProduct.goods(sku, sku + " goods",
                unitsOfMeasure.requireByCode("PIECE").id(), standardRate(),
                Money.ofEur("50.00")));
    }

    /** Any ordinary expense account: not keyed, not a control account, and therefore postable to. */
    private AccountView anExpenseAccount() {
        return chart.activeAccounts().stream()
                .filter(account -> account.type() == AccountType.EXPENSE)
                .filter(account -> account.kind() == AccountKind.STANDARD)
                .filter(account -> account.systemKey() == null)
                .findFirst()
                .orElseThrow();
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("recording")
    class Recording {

        @Test
        @DisplayName("an inventory line debits GR/IR, not Inventory — the whole of ADR 0004")
        void inventoryLineGoesToClearing() {
            SupplierView acme = domestic("PIIT Acme");
            ProductView grinder = product("PIIT-01");

            AccountBalance inventoryBefore = journal.balanceOf(AccountSystemKey.INVENTORY, APRIL);
            AccountBalance payableBefore =
                    journal.balanceOf(AccountSystemKey.ACCOUNTS_PAYABLE, APRIL);
            AccountBalance clearingBefore = journal.balanceOf(
                    AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, APRIL);

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "INV-1",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(
                            grinder.id(), Quantity.of(10L), UnitCost.ofEur("10.000000"),
                            standardRate()))));

            assertThat(invoice.netTotal()).isEqualTo(Money.ofEur("100.00"));
            assertThat(invoice.vatTotal()).isEqualTo(Money.ofEur("24.00"));
            assertThat(invoice.grossTotal()).isEqualTo(Money.ofEur("124.00"));

            // Nothing has arrived, so Inventory is untouched and the goods sit in clearing.
            assertThat(journal.balanceOf(AccountSystemKey.INVENTORY, APRIL).net())
                    .isEqualTo(inventoryBefore.net());
            assertThat(journal.balanceOf(
                    AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, APRIL).net()
                    .minus(clearingBefore.net()))
                    .isEqualTo(Money.ofEur("100.00"));
            assertThat(payableBefore.net()
                    .minus(journal.balanceOf(AccountSystemKey.ACCOUNTS_PAYABLE, APRIL).net()))
                    .isEqualTo(Money.ofEur("124.00"));
        }

        @Test
        @DisplayName("VAT is computed per line and summed by class, carrying its base (Q14)")
        void vatIsPostedPerClassWithItsBase() {
            SupplierView acme = domestic("PIIT Vat");
            ProductView grinder = product("PIIT-02");
            long reduced = vatClasses.requireByCode("1131").id();

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "INV-2",
                    MARCH, List.of(
                            NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                                    UnitCost.ofEur("100.000000"), standardRate()),
                            NewPurchaseInvoiceLine.expense(anExpenseAccount().id(),
                                    Money.ofEur("200.00"), reduced))));

            JournalEntryView entry = journal.requireEntry(invoice.journalEntryId());
            assertThat(entry.source()).isEqualTo(JournalSource.PURCHASE_INVOICE);

            // Two Input VAT lines, one per class, each carrying the base it was computed on — which is
            // what makes "summed by rate" mean anything at all.
            assertThat(entry.lines().stream().filter(line -> line.vatIfAny().isPresent()))
                    .hasSize(2);
            assertThat(journal.vatTotals(MARCH, APRIL).stream()
                    .filter(total -> total.direction() == VatDirection.INPUT)
                    .map(total -> total.vatClassId()))
                    .contains(standardRate(), reduced);
        }

        @Test
        @DisplayName("an exempt line posts no VAT at all")
        void exemptLinePostsNoVat() {
            SupplierView acme = domestic("PIIT Exempt");
            ProductView grinder = product("PIIT-03");
            long reason = exemptionReasons.active().getFirst().id();

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "INV-3",
                    MARCH, List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(2L), UnitCost.ofEur("40.000000"),
                                    standardRate())
                            .exemptUnder(reason))));

            assertThat(invoice.vatTotal()).isEqualTo(Money.ofEur("0.00"));
            assertThat(invoice.grossTotal()).isEqualTo(Money.ofEur("80.00"));
            assertThat(invoice.lines().getFirst().isExempt()).isTrue();
            assertThat(journal.requireEntry(invoice.journalEntryId()).lines())
                    .noneMatch(line -> line.vatIfAny().isPresent());
        }

        @Test
        @DisplayName("reverse charge posts both sides for the same class and base, and owes nothing extra")
        void reverseChargePostsBothSides() {
            SupplierView berlin = intraEu("PIIT Berlin");
            ProductView grinder = product("PIIT-04");

            AccountBalance payableBefore =
                    journal.balanceOf(AccountSystemKey.ACCOUNTS_PAYABLE, APRIL);

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(berlin.id(), "DE-1",
                    MARCH, List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(1L), UnitCost.ofEur("500.000000"),
                                    standardRate())
                            .reverseCharged())));

            // The supplier charged no VAT, so the payable is the net alone — while both VAT figures
            // stay separately declarable, which is exactly what netting the accounts would destroy.
            assertThat(invoice.grossTotal()).isEqualTo(Money.ofEur("500.00"));
            assertThat(payableBefore.net()
                    .minus(journal.balanceOf(AccountSystemKey.ACCOUNTS_PAYABLE, APRIL).net()))
                    .isEqualTo(Money.ofEur("500.00"));

            JournalEntryView entry = journal.requireEntry(invoice.journalEntryId());
            assertThat(entry.lines().stream().filter(line -> line.vatIfAny().isPresent()))
                    .hasSize(2);
        }

        @Test
        @DisplayName("reverse charge has to agree with the supplier, in both directions")
        void reverseChargeMustAgreeWithTheSupplier() {
            SupplierView acme = domestic("PIIT Domestic");
            SupplierView berlin = intraEu("PIIT Munich");
            ProductView grinder = product("PIIT-05");

            assertThatExceptionOfType(InvalidPurchaseInvoiceException.class)
                    .isThrownBy(() -> invoices.record(NewPurchaseInvoice.of(acme.id(), "X-1", MARCH,
                            List.of(NewPurchaseInvoiceLine
                                    .inventory(grinder.id(), Quantity.of(1L),
                                            UnitCost.ofEur("1.000000"), standardRate())
                                    .reverseCharged()))))
                    .withMessageContaining("cannot be reverse-charged");

            assertThatExceptionOfType(InvalidPurchaseInvoiceException.class)
                    .isThrownBy(() -> invoices.record(NewPurchaseInvoice.of(berlin.id(), "X-2",
                            MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(),
                                    Quantity.of(1L), UnitCost.ofEur("1.000000"), standardRate())))))
                    .withMessageContaining("must be reverse-charged");
        }

        @Test
        @DisplayName("the same supplier invoice number cannot be recorded twice")
        void duplicateNumberIsRefused() {
            SupplierView acme = domestic("PIIT Dup");
            ProductView grinder = product("PIIT-06");

            invoices.record(NewPurchaseInvoice.of(acme.id(), "SAME", MARCH,
                    List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("1.000000"), standardRate()))));

            assertThatExceptionOfType(InvalidPurchaseInvoiceException.class)
                    .isThrownBy(() -> invoices.record(NewPurchaseInvoice.of(acme.id(), "SAME", MARCH,
                            List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                                    UnitCost.ofEur("1.000000"), standardRate())))))
                    .withMessageContaining("duplicate");
        }

        @Test
        @DisplayName("an expense line cannot be posted into a control account")
        void controlAccountsRefuseFreeHandLines() {
            SupplierView acme = domestic("PIIT Control");
            AccountView payable = chart.requireAccount(AccountSystemKey.ACCOUNTS_PAYABLE);

            assertThatExceptionOfType(InvalidPurchaseInvoiceException.class)
                    .isThrownBy(() -> invoices.record(NewPurchaseInvoice.of(acme.id(), "CTRL-1",
                            MARCH, List.of(NewPurchaseInvoiceLine.expense(
                                    payable.id(), Money.ofEur("10.00"), standardRate())))))
                    .withMessageContaining("control account");
        }

        @Test
        @DisplayName("every line states a VAT treatment, and exactly one of the two ways")
        void vatTreatmentIsAlwaysStated() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new NewPurchaseInvoiceLine(
                            gr.novotrade.novocore.core.api.purchasing.PurchaseLineType.EXPENSE,
                            null, null, null, 1L, Money.ofEur("1.00"), null, null, null, false,
                            List.of()))
                    .withMessageContaining("exactly one of them");
        }
    }

    @Nested
    @DisplayName("GR/IR clearing, invoice first")
    class InvoiceFirst {

        @Test
        @DisplayName("the delivery takes the invoice's price, so clearing nets to zero and nothing varies")
        void deliveryAgainstAnInvoiceClearsExactly() {
            SupplierView acme = domestic("PIIT InvFirst");
            ProductView grinder = product("PIIT-07");

            AccountBalance clearingBefore = journal.balanceOf(
                    AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, APRIL);
            AccountBalance varianceBefore =
                    journal.balanceOf(AccountSystemKey.PURCHASE_PRICE_VARIANCE, APRIL);

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "IF-1",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(4L),
                            UnitCost.ofEur("25.000000"), standardRate()))));
            assertThat(invoices.linesAwaitingDelivery())
                    .extracting(line -> line.id())
                    .contains(invoice.lines().getFirst().id());

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), APRIL,
                    List.of(NewGoodsReceiptLine.pooledAgainst(grinder.id(), Quantity.of(4L),
                            invoice.lines().getFirst().id()))));

            InventoryLotView lot = inventory.requireLot(receipt.lines().getFirst().lotId());
            assertThat(lot.unitCost()).isEqualTo(UnitCost.ofEur("25.000000"));

            assertThat(journal.balanceOf(
                    AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, APRIL).net())
                    .isEqualTo(clearingBefore.net());
            assertThat(journal.balanceOf(AccountSystemKey.PURCHASE_PRICE_VARIANCE, APRIL).net())
                    .isEqualTo(varianceBefore.net());

            assertThat(invoices.require(invoice.id()).lines().getFirst().openQuantity())
                    .isEqualTo(Quantity.ZERO);
            assertThat(invoices.matchesOf(invoice.id())).singleElement()
                    .satisfies(match -> assertThat(match.pricesAgreed()).isTrue());
        }

        @Test
        @DisplayName("a partial delivery leaves the rest awaiting delivery")
        void partialDeliveryLeavesTheRestOpen() {
            SupplierView acme = domestic("PIIT Partial");
            ProductView grinder = product("PIIT-08");

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "IF-2",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(10L),
                            UnitCost.ofEur("10.000000"), standardRate()))));
            long invoiceLineId = invoice.lines().getFirst().id();

            receipts.record(NewGoodsReceipt.of(acme.id(), APRIL, List.of(
                    NewGoodsReceiptLine.pooledAgainst(grinder.id(), Quantity.of(6L),
                            invoiceLineId))));

            PurchaseInvoiceView reread = invoices.require(invoice.id());
            assertThat(reread.lines().getFirst().matchedQuantity()).isEqualTo(Quantity.of(6L));
            assertThat(reread.lines().getFirst().openQuantity()).isEqualTo(Quantity.of(4L));
            assertThat(reread.linesAwaitingDelivery()).hasSize(1);

            // And the rest arriving closes it, without any second matching operation existing.
            receipts.record(NewGoodsReceipt.of(acme.id(), APRIL, List.of(
                    NewGoodsReceiptLine.pooledAgainst(grinder.id(), Quantity.of(4L),
                            invoiceLineId))));
            assertThat(invoices.require(invoice.id()).linesAwaitingDelivery()).isEmpty();
        }

        @Test
        @DisplayName("more cannot be received than was invoiced")
        void overDeliveryIsRefused() {
            SupplierView acme = domestic("PIIT Over");
            ProductView grinder = product("PIIT-09");

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "IF-3",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(3L),
                            UnitCost.ofEur("10.000000"), standardRate()))));

            assertThat(invoice.lines()).hasSize(1);
            assertThatExceptionOfType(
                    gr.novotrade.novocore.core.api.purchasing.InvalidGoodsReceiptException.class)
                    .isThrownBy(() -> receipts.record(NewGoodsReceipt.of(acme.id(), APRIL,
                            List.of(NewGoodsReceiptLine.pooledAgainst(grinder.id(),
                                    Quantity.of(5L), invoice.lines().getFirst().id())))))
                    .withMessageContaining("clear GR/IR below zero");
        }
    }

    @Nested
    @DisplayName("GR/IR clearing, goods first — where ADR 0008 happens")
    class GoodsFirst {

        @Test
        @DisplayName("the lot keeps its received cost and the difference posts to purchase price variance")
        void priceDifferenceGoesToVarianceAndNotToTheLot() {
            SupplierView acme = domestic("PIIT GoodsFirst");
            ProductView grinder = product("PIIT-10");

            // Taken before the delivery, because the point of the clearing account is that both halves
            // together leave it where they found it.
            AccountBalance clearingBefore = journal.balanceOf(
                    AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, APRIL);
            AccountBalance varianceBefore =
                    journal.balanceOf(AccountSystemKey.PURCHASE_PRICE_VARIANCE, APRIL);

            // Received at a guess of 10.00 each: no invoice existed yet.
            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(10L),
                            UnitCost.ofEur("10.000000")))));
            long lotId = receipt.lines().getFirst().lotId();

            // The invoice arrives at 11.00 each.
            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "GF-1",
                    APRIL, List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(10L), UnitCost.ofEur("11.000000"),
                                    standardRate())
                            .matching(GoodsReceiptMatch.of(
                                    receipt.lines().getFirst().id(), Quantity.of(10L))))));

            // ADR 0008: the lot is untouched. Retroactively re-costing it would be the same problem
            // as editing a posted entry, expressed as a number.
            assertThat(inventory.requireLot(lotId).unitCost())
                    .isEqualTo(UnitCost.ofEur("10.000000"));

            // GR/IR clears exactly what the delivery credited: 100.00, not 110.00.
            assertThat(journal.balanceOf(
                    AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, APRIL).net())
                    .isEqualTo(clearingBefore.net());

            // The 10.00 difference is visible, in an account somebody can report on.
            assertThat(journal.balanceOf(AccountSystemKey.PURCHASE_PRICE_VARIANCE, APRIL).net()
                    .minus(varianceBefore.net()))
                    .isEqualTo(Money.ofEur("10.00"));
            assertThat(invoice.variance()).isEqualTo(Money.ofEur("10.00"));
            assertThat(invoice.varianceIfAny()).contains(Money.ofEur("10.00"));
            assertThat(invoices.matchesOf(invoice.id())).singleElement()
                    .satisfies(match -> assertThat(match.isUnfavourable()).isTrue());
        }

        @Test
        @DisplayName("an invoice below the expected price is a credit variance, not a hidden gain")
        void favourableVarianceCarriesTheOtherSign() {
            SupplierView acme = domestic("PIIT Cheaper");
            ProductView grinder = product("PIIT-11");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(5L),
                            UnitCost.ofEur("20.000000")))));

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "GF-2",
                    APRIL, List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(5L), UnitCost.ofEur("18.000000"),
                                    standardRate())
                            .matching(GoodsReceiptMatch.of(
                                    receipt.lines().getFirst().id(), Quantity.of(5L))))));

            assertThat(invoice.variance()).isEqualTo(Money.ofEur("-10.00"));
            assertThat(invoices.matchesOf(invoice.id())).singleElement()
                    .satisfies(match -> assertThat(match.isUnfavourable()).isFalse());
        }

        @Test
        @DisplayName("the delivery stops showing as received-not-invoiced once it is paid for")
        void matchingClosesTheAwaitingInvoiceHalf() {
            SupplierView acme = domestic("PIIT Closes");
            ProductView grinder = product("PIIT-12");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(2L),
                            UnitCost.ofEur("10.000000")))));
            long receiptLineId = receipt.lines().getFirst().id();
            assertThat(receipts.linesAwaitingInvoice())
                    .extracting(line -> line.id()).contains(receiptLineId);

            invoices.record(NewPurchaseInvoice.of(acme.id(), "GF-3", APRIL,
                    List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(2L), UnitCost.ofEur("10.000000"),
                                    standardRate())
                            .matching(GoodsReceiptMatch.of(receiptLineId, Quantity.of(2L))))));

            assertThat(receipts.linesAwaitingInvoice())
                    .extracting(line -> line.id()).doesNotContain(receiptLineId);
        }

        @Test
        @DisplayName("an invoice cannot settle more goods than it charges for")
        void matchingMoreThanTheLineIsRefused() {
            SupplierView acme = domestic("PIIT TooMuch");
            ProductView grinder = product("PIIT-13");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(10L),
                            UnitCost.ofEur("10.000000")))));

            assertThatExceptionOfType(InvalidPurchaseInvoiceException.class)
                    .isThrownBy(() -> invoices.record(NewPurchaseInvoice.of(acme.id(), "GF-4",
                            APRIL, List.of(NewPurchaseInvoiceLine
                                    .inventory(grinder.id(), Quantity.of(2L),
                                            UnitCost.ofEur("10.000000"), standardRate())
                                    .matching(GoodsReceiptMatch.of(
                                            receipt.lines().getFirst().id(), Quantity.of(5L)))))))
                    .withMessageContaining("cannot settle more goods than it charges for");
        }

        @Test
        @DisplayName("one supplier's invoice cannot clear another's delivery")
        void crossSupplierMatchingIsRefused() {
            SupplierView acme = domestic("PIIT Acme2");
            SupplierView other = domestic("PIIT Other");
            ProductView grinder = product("PIIT-14");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(other.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("10.000000")))));

            assertThatExceptionOfType(InvalidPurchaseInvoiceException.class)
                    .isThrownBy(() -> invoices.record(NewPurchaseInvoice.of(acme.id(), "GF-5",
                            APRIL, List.of(NewPurchaseInvoiceLine
                                    .inventory(grinder.id(), Quantity.of(1L),
                                            UnitCost.ofEur("10.000000"), standardRate())
                                    .matching(GoodsReceiptMatch.of(
                                            receipt.lines().getFirst().id(), Quantity.of(1L)))))))
                    .withMessageContaining("different supplier");
        }
    }

    @Nested
    @DisplayName("Q13 — correction by reversal")
    class Correction {

        @Test
        @DisplayName("reversing posts the mirror and leaves the original exactly as it was")
        void reversalMirrorsAndLeavesTheOriginalAlone() {
            SupplierView acme = domestic("PIIT Rev");
            ProductView grinder = product("PIIT-15");

            AccountBalance payableBefore =
                    journal.balanceOf(AccountSystemKey.ACCOUNTS_PAYABLE, APRIL);

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "REV-1",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("100.000000"), standardRate()))));

            PurchaseInvoiceView reversal =
                    invoices.reverse(invoice.id(), APRIL, "supplier billed us twice");

            assertThat(reversal.isReversal()).isTrue();
            assertThat(reversal.lines()).isEmpty();
            assertThat(invoices.require(invoice.id()).isReversed()).isTrue();
            assertThat(invoices.require(invoice.id()).lines()).hasSize(1);

            assertThat(journal.balanceOf(AccountSystemKey.ACCOUNTS_PAYABLE, APRIL).net())
                    .isEqualTo(payableBefore.net());
        }

        @Test
        @DisplayName("the same number can be recorded again once the wrong one has been reversed")
        void reversalReleasesTheDocumentNumber() {
            SupplierView acme = domestic("PIIT Renum");
            ProductView grinder = product("PIIT-16");

            PurchaseInvoiceView wrong = invoices.record(NewPurchaseInvoice.of(acme.id(), "AGAIN",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("10.000000"), standardRate()))));
            invoices.reverse(wrong.id(), APRIL, "wrong amount");

            PurchaseInvoiceView right = invoices.record(NewPurchaseInvoice.of(acme.id(), "AGAIN",
                    APRIL, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("12.000000"), standardRate()))));

            assertThat(right.id()).isNotEqualTo(wrong.id());
            assertThat(right.isInForce()).isTrue();
        }

        @Test
        @DisplayName("an invoice whose stock has already been sold cannot be reversed")
        void consumedStockBlocksReversal() {
            SupplierView acme = domestic("PIIT Sold");
            ProductView grinder = product("PIIT-17");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(5L),
                            UnitCost.ofEur("10.000000")))));
            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "REV-2",
                    MARCH, List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(5L), UnitCost.ofEur("12.000000"),
                                    standardRate())
                            .matching(GoodsReceiptMatch.of(
                                    receipt.lines().getFirst().id(), Quantity.of(5L))))));

            inventory.consume(NewStockConsumption.of(
                    grinder.id(), Quantity.of(1L), APRIL, JournalSource.SALES_INVOICE));

            assertThatExceptionOfType(InvalidPurchaseInvoiceException.class)
                    .isThrownBy(() -> invoices.reverse(invoice.id(), APRIL, "too late"))
                    .withMessageContaining("already inside cost of goods sold");
        }

        @Test
        @DisplayName("reversing an invoice releases the delivery it had claimed")
        void reversalReleasesTheDelivery() {
            SupplierView acme = domestic("PIIT Release");
            ProductView grinder = product("PIIT-18");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(3L),
                            UnitCost.ofEur("10.000000")))));
            long receiptLineId = receipt.lines().getFirst().id();

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "REV-3",
                    APRIL, List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(3L), UnitCost.ofEur("10.000000"),
                                    standardRate())
                            .matching(GoodsReceiptMatch.of(receiptLineId, Quantity.of(3L))))));
            assertThat(receipts.linesAwaitingInvoice())
                    .extracting(line -> line.id()).doesNotContain(receiptLineId);

            invoices.reverse(invoice.id(), APRIL, "not ours");

            // Without this, a delivery paid for by an invoice that no longer stands would vanish from
            // the awaiting-invoice half of the GR/IR position and never be chased.
            assertThat(receipts.linesAwaitingInvoice())
                    .extracting(line -> line.id()).contains(receiptLineId);
        }
    }

    @Nested
    @DisplayName("the database enforces it too, not only the service")
    class DatabaseInvariants {

        @Test
        @DisplayName("a duplicate supplier invoice number is refused by raw SQL as well")
        void duplicateNumberIsRefusedBelowTheService() {
            SupplierView acme = domestic("PIIT RawDup");
            ProductView grinder = product("PIIT-20");

            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "RAW-1",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("10.000000"), standardRate()))));

            // "No duplicates" that only holds for callers who came through the service is not a rule,
            // it is a habit. Case-insensitively, so the two obviously-identical spellings agree.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO purchase_invoice (
                        supplier_id, supplier_invoice_number, invoice_date, journal_entry_id)
                    VALUES (?, 'raw-1', DATE '2026-03-11', ?)
                    """, acme.id(), invoice.journalEntryId()))
                    .hasMessageContaining("already sent invoice");
        }

        @Test
        @DisplayName("an invoice line cannot be a third shape, neither inventory nor expense")
        void lineShapeIsEnforced() {
            SupplierView acme = domestic("PIIT RawShape");
            ProductView grinder = product("PIIT-21");
            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "RAW-2",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("10.000000"), standardRate()))));

            // An inventory line naming an expense account, which is neither of the two things a line
            // is allowed to be.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO purchase_invoice_line (
                        invoice_id, line_number, line_type, product_id, quantity,
                        unit_price, unit_price_currency, expense_account_id,
                        net_amount, net_amount_currency, vat_amount, vat_amount_currency,
                        vat_class_id, variance_amount, variance_amount_currency)
                    VALUES (?, 9, 'INVENTORY', ?, 1, 1, 'EUR', ?, 1, 'EUR', 0, 'EUR', ?, 0, 'EUR')
                    """, invoice.id(), grinder.id(), anExpenseAccount().id(), standardRate()))
                    .hasMessageContaining("purchase_invoice_line_shape");
        }

        @Test
        @DisplayName("a line states one VAT treatment: never both, never neither")
        void vatTreatmentIsEnforced() {
            SupplierView acme = domestic("PIIT RawVat");
            ProductView grinder = product("PIIT-22");
            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "RAW-3",
                    MARCH, List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("10.000000"), standardRate()))));

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO purchase_invoice_line (
                        invoice_id, line_number, line_type, product_id, quantity,
                        unit_price, unit_price_currency,
                        net_amount, net_amount_currency, vat_amount, vat_amount_currency,
                        variance_amount, variance_amount_currency)
                    VALUES (?, 8, 'INVENTORY', ?, 1, 1, 'EUR', 1, 'EUR', 0, 'EUR', 0, 'EUR')
                    """, invoice.id(), grinder.id()))
                    .hasMessageContaining("purchase_invoice_line_vat_treatment_is_stated");
        }

        @Test
        @DisplayName("a delivery line and an invoice line cannot be matched twice")
        void matchPairIsUnique() {
            SupplierView acme = domestic("PIIT RawMatch");
            ProductView grinder = product("PIIT-23");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(4L),
                            UnitCost.ofEur("10.000000")))));
            PurchaseInvoiceView invoice = invoices.record(NewPurchaseInvoice.of(acme.id(), "RAW-4",
                    APRIL, List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(4L), UnitCost.ofEur("10.000000"),
                                    standardRate())
                            .matching(GoodsReceiptMatch.of(
                                    receipt.lines().getFirst().id(), Quantity.of(4L))))));

            // A second row for the same pair is a duplicate nobody could tell from a real match; "some
            // of it" is what the quantity is for.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO gr_ir_match (purchase_invoice_line_id, goods_receipt_line_id, quantity)
                    VALUES (?, ?, 1)
                    """, invoice.lines().getFirst().id(), receipt.lines().getFirst().id()))
                    .hasMessageContaining("gr_ir_match_pair_unique");
        }

        @Test
        @DisplayName("one delivery line becomes exactly one lot")
        void oneReceiptLineOneLot() {
            SupplierView acme = domestic("PIIT RawLot");
            ProductView grinder = product("PIIT-24");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(1L),
                            UnitCost.ofEur("10.000000")))));

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO inventory_lot (
                        product_id, quantity_received, quantity_remaining, received_unit_cost,
                        received_unit_cost_currency, allocated_landed_unit_cost,
                        allocated_landed_unit_cost_currency, acquisition_date, location,
                        goods_receipt_line_id)
                    VALUES (?, 1, 1, 1, 'EUR', 0, 'EUR', DATE '2026-03-10', 'INVENTORY', ?)
                    """, grinder.id(), receipt.lines().getFirst().id()))
                    .hasMessageContaining("inventory_lot_receipt_line_unique");
        }

        @Test
        @DisplayName("the consumption source CHECK lists exactly the sources that may consume stock")
        void consumptionSourceCheckAgreesWithTheEnum() {
            // The migration claims these two statements of the rule agree. Held to it here, the way
            // journal_source_is_amendable is held to JournalSource.isAmendable().
            List<JournalSource> permitted = java.util.Arrays.stream(JournalSource.values())
                    .filter(JournalSource::mayConsumeStock)
                    .toList();

            for (JournalSource source : permitted) {
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM pg_constraint
                        WHERE conname = 'stock_consumption_source_may_consume'
                          AND pg_get_constraintdef(oid) LIKE ?
                        """, Integer.class, "%'" + source.name() + "'%"))
                        .as("%s may consume stock in Java and must be listed in the CHECK", source)
                        .isEqualTo(1);
            }
            // And nothing the CHECK allows that Java does not: counting the quoted literals catches a
            // value added to the constraint alone, which no per-value loop can see.
            assertThat(jdbc.queryForObject("""
                    SELECT length(pg_get_constraintdef(oid))
                        - length(replace(pg_get_constraintdef(oid), '''', ''))
                    FROM pg_constraint WHERE conname = 'stock_consumption_source_may_consume'
                    """, Integer.class))
                    .as("no source is listed in the CHECK that mayConsumeStock() refuses")
                    .isEqualTo(permitted.size() * 2);
        }
    }

    @Nested
    @DisplayName("reporting the variance")
    class VarianceReporting {

        @Test
        @DisplayName("the account's balance and the invoices that moved it agree")
        void totalVarianceAgreesWithTheAccount() {
            SupplierView acme = domestic("PIIT Report");
            ProductView grinder = product("PIIT-19");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(grinder.id(), Quantity.of(4L),
                            UnitCost.ofEur("10.000000")))));

            AccountBalance before =
                    journal.balanceOf(AccountSystemKey.PURCHASE_PRICE_VARIANCE, APRIL);

            invoices.record(NewPurchaseInvoice.of(acme.id(), "VR-1", APRIL,
                    List.of(NewPurchaseInvoiceLine
                            .inventory(grinder.id(), Quantity.of(4L), UnitCost.ofEur("12.500000"),
                                    standardRate())
                            .matching(GoodsReceiptMatch.of(
                                    receipt.lines().getFirst().id(), Quantity.of(4L))))));

            Money moved = journal.balanceOf(AccountSystemKey.PURCHASE_PRICE_VARIANCE, APRIL).net()
                    .minus(before.net());
            assertThat(moved).isEqualTo(Money.ofEur("10.00"));
            assertThat(invoices.variancesBetween(APRIL, APRIL))
                    .extracting(PurchaseInvoiceView::supplierInvoiceNumber)
                    .contains("VR-1");
        }
    }
}
