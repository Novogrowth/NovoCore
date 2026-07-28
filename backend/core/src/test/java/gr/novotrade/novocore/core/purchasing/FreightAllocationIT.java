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
import gr.novotrade.novocore.core.api.ledger.InvalidJournalEntryException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotAmendableException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationLineView;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationView;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptView;
import gr.novotrade.novocore.core.api.purchasing.InvalidFreightAllocationException;
import gr.novotrade.novocore.core.api.purchasing.NewFreightAllocation;
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
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Freight and duty allocated into the lots they delivered — brief §4, <strong>Q18, ADR 0010</strong>.
 *
 * <p>The tests worth reading twice are the three in {@code TheSplit}. A lot with nothing sold takes
 * its whole share onto its unit cost; a lot with nothing left takes its whole share to
 * {@code Landed cost variance}; and the one in between splits, which is the entire content of Q18.
 * The invariant behind all three is stated in {@code inventoryAgreesWithTheLots}: whatever the split,
 * the Inventory control account still equals what the lots are carried at.
 */
class FreightAllocationIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 15);
    private static final LocalDate MAY = LocalDate.of(2026, 5, 20);

    @Autowired
    private FreightAllocationService allocations;

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
    private UnitOfMeasureService unitsOfMeasure;

    /** For the probes that bypass the service entirely — the only way to prove a rule is the schema's. */
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    private long standardRate() {
        return vatClasses.requireByCode("1410").id();
    }

    private SupplierView supplier(String name) {
        return suppliers.create(NewSupplier.domestic(name, "EL" + Math.abs(name.hashCode())));
    }

    private ProductView product(String sku) {
        return products.create(NewProduct.goods(sku, sku + " goods",
                unitsOfMeasure.requireByCode("PIECE").id(), standardRate(), Money.ofEur("99.00")));
    }

    private ProductView machineProduct(String sku) {
        return products.changeSerialTracking(product(sku).id(), true);
    }

    private long freightAccountId() {
        return chart.requireAccount(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED).id();
    }

    /** A carrier's invoice, recorded exactly as step 8 records one: an expense line pointed at freight. */
    private long freightLine(String number, Money amount) {
        PurchaseInvoiceView carrier = invoices.record(NewPurchaseInvoice.of(
                supplier("FAIT Carrier " + number).id(), number, MARCH,
                List.of(NewPurchaseInvoiceLine.expense(
                        freightAccountId(), amount, standardRate()))));
        return carrier.lines().getFirst().id();
    }

    /** A delivery of pooled stock, which is the only way a lot with a source document comes into being. */
    private long lot(String supplierName, ProductView product, long quantity, String unitCost) {
        GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(
                supplier(supplierName).id(), MARCH,
                List.of(NewGoodsReceiptLine.pooled(
                        product.id(), Quantity.of(quantity), UnitCost.ofEur(unitCost)))));
        return inventory.findLotByReceiptLine(receipt.lines().getFirst().id()).orElseThrow().id();
    }

    private void sell(ProductView product, long quantity) {
        inventory.consume(NewStockConsumption.of(
                product.id(), Quantity.of(quantity), APRIL, JournalSource.SALES_INVOICE));
    }

    private Money balanceChange(AccountSystemKey key, AccountBalance before) {
        return journal.balanceOf(key, MAY).net().minus(before.net());
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("the split — Q18's whole answer")
    class TheSplit {

        @Test
        @DisplayName("a lot with nothing sold takes its whole share onto its unit cost")
        void allCapitalised() {
            ProductView grinder = product("FAIT-01");
            long lotId = lot("FAIT Untouched", grinder, 10, "10.000000");
            long freight = freightLine("FRT-01", Money.ofEur("100.00"));

            AccountBalance inventoryBefore = journal.balanceOf(AccountSystemKey.INVENTORY, MAY);
            AccountBalance freightBefore =
                    journal.balanceOf(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED, MAY);

            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("100.00"), APRIL, null, List.of(lotId)));

            assertThat(allocation.amount()).isEqualTo(Money.ofEur("100.00"));
            assertThat(allocation.capitalised()).isEqualTo(Money.ofEur("100.00"));
            assertThat(allocation.variance()).isEqualTo(Money.ofEur("0.00"));
            assertThat(allocation.hasVariance()).isFalse();

            // Brief §5: a lot's unit cost includes allocated landed costs. €10 of goods, €10 of
            // freight, so what a sale costs out at is €20.
            InventoryLotView lot = inventory.requireLot(lotId);
            assertThat(lot.receivedUnitCost()).isEqualTo(UnitCost.ofEur("10.000000"));
            assertThat(lot.allocatedLandedUnitCost()).isEqualTo(UnitCost.ofEur("10.000000"));
            assertThat(lot.unitCost()).isEqualTo(UnitCost.ofEur("20.000000"));
            assertThat(lot.remainingValue()).isEqualTo(Money.ofEur("200.00"));

            assertThat(balanceChange(AccountSystemKey.INVENTORY, inventoryBefore))
                    .isEqualTo(Money.ofEur("100.00"));
            // The whole point of the account being expected_to_clear: it goes back to where it was.
            assertThat(balanceChange(
                    AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED, freightBefore))
                    .isEqualTo(Money.ofEur("-100.00"));
        }

        @Test
        @DisplayName("a partly sold lot splits: the rest capitalises, the sold part goes to variance")
        void partlySold() {
            // The case Q18 exists for. Ten arrived at €10, four have been sold at €10, and €100 of
            // freight turns up afterwards. Six units can carry €60 of it; the other €40 belongs to
            // stock that is already inside a posted cost of goods sold and cannot be reached.
            ProductView grinder = product("FAIT-02");
            long lotId = lot("FAIT Partly", grinder, 10, "10.000000");
            sell(grinder, 4);

            long freight = freightLine("FRT-02", Money.ofEur("100.00"));
            AccountBalance cogsBefore =
                    journal.balanceOf(AccountSystemKey.COST_OF_GOODS_SOLD, MAY);
            AccountBalance varianceBefore =
                    journal.balanceOf(AccountSystemKey.LANDED_COST_VARIANCE, MAY);

            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("100.00"), APRIL, null, List.of(lotId)));

            assertThat(allocation.capitalised()).isEqualTo(Money.ofEur("60.00"));
            assertThat(allocation.variance()).isEqualTo(Money.ofEur("40.00"));

            FreightAllocationLineView line = allocation.lines().getFirst();
            assertThat(line.quantityReceived()).isEqualTo(Quantity.of(10L));
            assertThat(line.quantityRemainingAtAllocation()).isEqualTo(Quantity.of(6L));
            assertThat(line.quantityGoneAtAllocation()).isEqualTo(Quantity.of(4L));
            assertThat(line.unitCostIncrease()).isEqualTo(UnitCost.ofEur("10.000000"));
            assertThat(line.share()).isEqualTo(Money.ofEur("100.00"));

            assertThat(inventory.requireLot(lotId).unitCost()).isEqualTo(UnitCost.ofEur("20.000000"));

            // ADR 0008, arriving from the other direction: the posted COGS is not touched.
            assertThat(balanceChange(AccountSystemKey.COST_OF_GOODS_SOLD, cogsBefore))
                    .isEqualTo(Money.ofEur("0.00"));
            assertThat(balanceChange(AccountSystemKey.LANDED_COST_VARIANCE, varianceBefore))
                    .isEqualTo(Money.ofEur("40.00"));
        }

        @Test
        @DisplayName("a lot with nothing left takes its whole share to variance and does not move")
        void whollySold() {
            ProductView grinder = product("FAIT-03");
            long lotId = lot("FAIT Sold", grinder, 5, "20.000000");
            sell(grinder, 5);

            long freight = freightLine("FRT-03", Money.ofEur("35.00"));
            AccountBalance inventoryBefore = journal.balanceOf(AccountSystemKey.INVENTORY, MAY);

            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("35.00"), APRIL, null, List.of(lotId)));

            assertThat(allocation.capitalised()).isEqualTo(Money.ofEur("0.00"));
            assertThat(allocation.variance()).isEqualTo(Money.ofEur("35.00"));
            assertThat(allocation.lines().getFirst().wentEntirelyToVariance()).isTrue();

            // The lot is untouched: raising the cost of nothing would leave a figure that a later
            // reversal of the sale would resurrect against an Inventory debit nobody made.
            InventoryLotView lot = inventory.requireLot(lotId);
            assertThat(lot.allocatedLandedUnitCost().isZero()).isTrue();
            assertThat(lot.hasAllocatedLandedCost()).isFalse();
            assertThat(balanceChange(AccountSystemKey.INVENTORY, inventoryBefore))
                    .isEqualTo(Money.ofEur("0.00"));
        }

        @Test
        @DisplayName("whatever the split, Inventory still equals what the lots are carried at")
        void inventoryAgreesWithTheLots() {
            // The invariant the two halves exist to preserve, checked end to end rather than per line.
            ProductView grinder = product("FAIT-04");
            long lotId = lot("FAIT Invariant", grinder, 8, "12.500000");   // 100.00 into Inventory
            sell(grinder, 3);                                              //  37.50 out to COGS
            long freight = freightLine("FRT-04", Money.ofEur("80.00"));

            AccountBalance before = journal.balanceOf(AccountSystemKey.INVENTORY, MAY);
            allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("80.00"), APRIL, null, List.of(lotId)));

            InventoryLotView lot = inventory.requireLot(lotId);
            assertThat(lot.unitCost()).isEqualTo(UnitCost.ofEur("22.500000"));
            assertThat(lot.remainingValue()).isEqualTo(Money.ofEur("112.50"));
            // 100.00 received − 37.50 sold + 50.00 capitalised = 112.50, which is 5 × 22.50.
            assertThat(journal.balanceOf(AccountSystemKey.INVENTORY, MAY).net()
                    .minus(before.net()))
                    .isEqualTo(Money.ofEur("50.00"));
        }
    }

    @Nested
    @DisplayName("proportional by value, against the received cost")
    class Proportions {

        @Test
        @DisplayName("lots share the freight in proportion to what they received")
        void proportionalByReceivedValue() {
            ProductView cheap = product("FAIT-10");
            ProductView dear = product("FAIT-11");
            long cheapLot = lot("FAIT Prop A", cheap, 10, "10.000000");    // basis 100
            long dearLot = lot("FAIT Prop B", dear, 10, "30.000000");      // basis 300
            long freight = freightLine("FRT-10", Money.ofEur("40.00"));

            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("40.00"), APRIL, null, List.of(cheapLot, dearLot)));

            assertThat(allocation.lines().get(0).share()).isEqualTo(Money.ofEur("10.00"));
            assertThat(allocation.lines().get(1).share()).isEqualTo(Money.ofEur("30.00"));
            assertThat(allocation.lines().get(0).basis()).isEqualByComparingTo("100.000000");
            assertThat(allocation.lines().get(1).basis()).isEqualByComparingTo("300.000000");
        }

        @Test
        @DisplayName("a second allocation divides by the received cost, not by the first one's result")
        void theBasisIsFrozen() {
            // The reason received_unit_cost stops changing. Lot A gets a shipment of its own first,
            // which moves what it is carried at; the joint allocation afterwards must still split
            // 100:300, not 150:300.
            ProductView cheap = product("FAIT-12");
            ProductView dear = product("FAIT-13");
            long cheapLot = lot("FAIT Frozen A", cheap, 10, "10.000000");
            long dearLot = lot("FAIT Frozen B", dear, 10, "30.000000");

            allocations.allocate(NewFreightAllocation.of(
                    freightLine("FRT-12", Money.ofEur("50.00")), Money.ofEur("50.00"),
                    APRIL, null, List.of(cheapLot)));
            assertThat(inventory.requireLot(cheapLot).unitCost())
                    .isEqualTo(UnitCost.ofEur("15.000000"));

            FreightAllocationView second = allocations.allocate(NewFreightAllocation.of(
                    freightLine("FRT-13", Money.ofEur("40.00")), Money.ofEur("40.00"),
                    MAY, null, List.of(cheapLot, dearLot)));

            // Received basis 100:300 gives 10:30. Carrying basis 150:300 would have given 13.33:26.67.
            assertThat(second.lines().get(0).share()).isEqualTo(Money.ofEur("10.00"));
            assertThat(second.lines().get(1).share()).isEqualTo(Money.ofEur("30.00"));

            // And the received cost has not moved at all, which is what makes that reproducible.
            assertThat(inventory.requireLot(cheapLot).receivedUnitCost())
                    .isEqualTo(UnitCost.ofEur("10.000000"));
            assertThat(inventory.requireLot(cheapLot).allocatedLandedUnitCost())
                    .isEqualTo(UnitCost.ofEur("6.000000"));
        }

        @Test
        @DisplayName("the shares sum to the amount allocated even when they do not divide evenly")
        void sharesSumExactly() {
            // Three equal lots and ten cents: the whole reason the split is integer-cent largest
            // remainder rather than divide-and-round.
            ProductView grinder = product("FAIT-14");
            List<Long> lots = List.of(
                    lot("FAIT Thirds A", grinder, 1, "1.000000"),
                    lot("FAIT Thirds B", grinder, 1, "1.000000"),
                    lot("FAIT Thirds C", grinder, 1, "1.000000"));
            long freight = freightLine("FRT-14", Money.ofEur("0.10"));

            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("0.10"), APRIL, null, lots));

            Money summed = allocation.lines().stream()
                    .map(FreightAllocationLineView::share)
                    .reduce(Money.zero(Money.EUR), Money::plus);
            assertThat(summed).isEqualTo(Money.ofEur("0.10"));
            assertThat(journal.requireEntry(allocation.journalEntryId()).totalDebits())
                    .isEqualTo(Money.ofEur("0.10"));
        }

        @Test
        @DisplayName("serial-tracked machines allocate like any other lot")
        void serialTracked() {
            ProductView machine = machineProduct("FAIT-15");
            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(
                    supplier("FAIT Machines").id(), MARCH,
                    List.of(NewGoodsReceiptLine.serialized(machine.id(),
                            List.of("FAIT-SN-1", "FAIT-SN-2"), UnitCost.ofEur("1000.000000")))));
            long lotId = inventory.findLotByReceiptLine(receipt.lines().getFirst().id())
                    .orElseThrow().id();

            allocations.allocate(NewFreightAllocation.of(
                    freightLine("FRT-15", Money.ofEur("120.00")), Money.ofEur("120.00"),
                    APRIL, null, List.of(lotId)));

            // A serial-tracked lot stores no quantity — the count of its units is the quantity — and
            // the cost still lives on the lot, shared by every machine in it.
            assertThat(inventory.requireLot(lotId).unitCost())
                    .isEqualTo(UnitCost.ofEur("1060.000000"));
            assertThat(inventory.findUnitBySerialNumber("FAIT-SN-1").orElseThrow().unitCost())
                    .isEqualTo(UnitCost.ofEur("1060.000000"));
        }
    }

    @Nested
    @DisplayName("what is left of a freight line")
    class Remainders {

        @Test
        @DisplayName("a partial allocation leaves the rest allocatable, and the line stays in the queue")
        void partialAllocation() {
            ProductView grinder = product("FAIT-20");
            long lotId = lot("FAIT Partial", grinder, 10, "10.000000");
            long freight = freightLine("FRT-20", Money.ofEur("100.00"));

            assertThat(allocations.unallocatedAmountOf(freight)).isEqualTo(Money.ofEur("100.00"));
            assertThat(allocations.linesAwaitingAllocation())
                    .extracting(line -> line.id())
                    .contains(freight);

            allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("60.00"), APRIL, null, List.of(lotId)));

            assertThat(allocations.unallocatedAmountOf(freight)).isEqualTo(Money.ofEur("40.00"));
            assertThat(allocations.linesAwaitingAllocation())
                    .extracting(line -> line.id())
                    .contains(freight);

            allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("40.00"), MAY, null, List.of(lotId)));

            assertThat(allocations.unallocatedAmountOf(freight)).isEqualTo(Money.ofEur("0.00"));
            assertThat(allocations.linesAwaitingAllocation())
                    .extracting(line -> line.id())
                    .doesNotContain(freight);
        }

        @Test
        @DisplayName("an ordinary expense line is not waiting to be allocated, and says so with zero")
        void nonFreightLineHasNothingToAllocate() {
            AccountView ordinary = chart.activeAccounts().stream()
                    .filter(account -> account.type() == AccountType.EXPENSE)
                    .filter(account -> account.kind() == AccountKind.STANDARD)
                    .filter(account -> account.systemKey() == null)
                    .findFirst()
                    .orElseThrow();
            PurchaseInvoiceView electricity = invoices.record(NewPurchaseInvoice.of(
                    supplier("FAIT Power").id(), "PWR-1", MARCH,
                    List.of(NewPurchaseInvoiceLine.expense(
                            ordinary.id(), Money.ofEur("250.00"), standardRate()))));
            long line = electricity.lines().getFirst().id();

            assertThat(allocations.unallocatedAmountOf(line)).isEqualTo(Money.ofEur("0.00"));
            assertThat(allocations.linesAwaitingAllocation())
                    .extracting(view -> view.id())
                    .doesNotContain(line);
        }
    }

    @Nested
    @DisplayName("what it refuses rather than resolving")
    class Refusals {

        @Test
        @DisplayName("a cost booked somewhere other than the unallocated account is refused")
        void wrongAccount() {
            AccountView ordinary = chart.activeAccounts().stream()
                    .filter(account -> account.type() == AccountType.EXPENSE)
                    .filter(account -> account.kind() == AccountKind.STANDARD)
                    .filter(account -> account.systemKey() == null)
                    .findFirst()
                    .orElseThrow();
            PurchaseInvoiceView other = invoices.record(NewPurchaseInvoice.of(
                    supplier("FAIT Wrong").id(), "WRG-1", MARCH,
                    List.of(NewPurchaseInvoiceLine.expense(
                            ordinary.id(), Money.ofEur("50.00"), standardRate()))));
            long lotId = lot("FAIT Wrong Lot", product("FAIT-30"), 5, "10.000000");

            assertThatExceptionOfType(InvalidFreightAllocationException.class)
                    .isThrownBy(() -> allocations.allocate(NewFreightAllocation.of(
                            other.lines().getFirst().id(), Money.ofEur("50.00"), APRIL, null,
                            List.of(lotId))))
                    .withMessageContaining("drive it negative");
        }

        @Test
        @DisplayName("an inventory line is the goods, not a cost of getting them here")
        void inventoryLineIsNotFreight() {
            ProductView grinder = product("FAIT-31");
            PurchaseInvoiceView goods = invoices.record(NewPurchaseInvoice.of(
                    supplier("FAIT Goods").id(), "GDS-1", MARCH,
                    List.of(NewPurchaseInvoiceLine.inventory(grinder.id(), Quantity.of(5L),
                            UnitCost.ofEur("10.000000"), standardRate()))));
            long lotId = lot("FAIT Goods Lot", grinder, 5, "10.000000");

            assertThatExceptionOfType(InvalidFreightAllocationException.class)
                    .isThrownBy(() -> allocations.allocate(NewFreightAllocation.of(
                            goods.lines().getFirst().id(), Money.ofEur("10.00"), APRIL, null,
                            List.of(lotId))))
                    .withMessageContaining("capitalise the same purchase twice");
        }

        @Test
        @DisplayName("more than the line charged cannot be allocated out of it")
        void overAllocation() {
            long lotId = lot("FAIT Over", product("FAIT-32"), 5, "10.000000");
            long freight = freightLine("FRT-32", Money.ofEur("30.00"));

            allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("20.00"), APRIL, null, List.of(lotId)));

            assertThatExceptionOfType(InvalidFreightAllocationException.class)
                    .isThrownBy(() -> allocations.allocate(NewFreightAllocation.of(
                            freight, Money.ofEur("15.00"), MAY, null, List.of(lotId))))
                    .withMessageContaining("credit Freight / Landed Cost — Unallocated below zero");
        }

        @Test
        @DisplayName("a lot received at zero cost has no share, and saying so beats allocating nothing")
        void zeroCostLot() {
            ProductView sample = product("FAIT-33");
            long freeLot = lot("FAIT Free", sample, 5, "0.000000");
            long freight = freightLine("FRT-33", Money.ofEur("20.00"));

            assertThatExceptionOfType(InvalidFreightAllocationException.class)
                    .isThrownBy(() -> allocations.allocate(NewFreightAllocation.of(
                            freight, Money.ofEur("20.00"), APRIL, null, List.of(freeLot))))
                    .withMessageContaining("no share of this freight");
        }

        @Test
        @DisplayName("a lot from a reversed delivery was never carried anywhere")
        void reversedDelivery() {
            ProductView grinder = product("FAIT-34");
            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(
                    supplier("FAIT Unmade").id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(4L), UnitCost.ofEur("10.000000")))));
            long lotId = inventory.findLotByReceiptLine(receipt.lines().getFirst().id())
                    .orElseThrow().id();
            receipts.reverse(receipt.id(), APRIL, "entered wrong");

            long freight = freightLine("FRT-34", Money.ofEur("20.00"));
            assertThatExceptionOfType(InvalidFreightAllocationException.class)
                    .isThrownBy(() -> allocations.allocate(NewFreightAllocation.of(
                            freight, Money.ofEur("20.00"), MAY, null, List.of(lotId))))
                    .withMessageContaining("has been reversed");
        }

        @Test
        @DisplayName("naming one lot twice would give it double its share")
        void duplicateLot() {
            long lotId = lot("FAIT Dup", product("FAIT-35"), 5, "10.000000");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> NewFreightAllocation.of(1L, Money.ofEur("10.00"), APRIL, null,
                            List.of(lotId, lotId)))
                    .withMessageContaining("double its share");
        }

        @Test
        @DisplayName("an allocation of nothing, or across no lots, is refused at the request")
        void degenerateRequests() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> NewFreightAllocation.of(
                            1L, Money.ofEur("0.00"), APRIL, null, List.of(1L)))
                    .withMessageContaining("not positive");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> NewFreightAllocation.of(
                            1L, Money.ofEur("10.00"), APRIL, null, List.of()))
                    .withMessageContaining("at least one lot");
        }
    }

    @Nested
    @DisplayName("immutable, corrected by reversal")
    class Correction {

        @Test
        @DisplayName("a reversal takes the cost back off the lot and gives the freight line its amount back")
        void reversalUndoesBothHalves() {
            ProductView grinder = product("FAIT-40");
            long lotId = lot("FAIT Rev", grinder, 10, "10.000000");
            long freight = freightLine("FRT-40", Money.ofEur("100.00"));

            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("100.00"), APRIL, null, List.of(lotId)));
            assertThat(inventory.requireLot(lotId).unitCost()).isEqualTo(UnitCost.ofEur("20.000000"));
            assertThat(allocations.unallocatedAmountOf(freight)).isEqualTo(Money.ofEur("0.00"));

            FreightAllocationView reversal =
                    allocations.reverse(allocation.id(), MAY, "wrong shipment");

            assertThat(reversal.isReversal()).isTrue();
            assertThat(reversal.lines()).isEmpty();
            assertThat(allocations.require(allocation.id()).isReversed()).isTrue();
            assertThat(allocations.require(allocation.id()).stands()).isFalse();

            // The lot is back where it was, and the cost is allocatable again — which is what makes
            // reversal a correction rather than a one-way door.
            InventoryLotView lot = inventory.requireLot(lotId);
            assertThat(lot.unitCost()).isEqualTo(UnitCost.ofEur("10.000000"));
            assertThat(lot.hasAllocatedLandedCost()).isFalse();
            assertThat(allocations.unallocatedAmountOf(freight)).isEqualTo(Money.ofEur("100.00"));

            JournalEntryView mirror = journal.requireEntry(reversal.journalEntryId());
            assertThat(mirror.source()).isEqualTo(JournalSource.FREIGHT_ALLOCATION);
            assertThat(mirror.totalDebits()).isEqualTo(Money.ofEur("100.00"));
        }

        @Test
        @DisplayName("a lot that has moved since cannot be un-allocated — ADR 0008's principle")
        void refusedOnceTheLotHasMoved() {
            ProductView grinder = product("FAIT-41");
            long lotId = lot("FAIT Moved", grinder, 10, "10.000000");
            long freight = freightLine("FRT-41", Money.ofEur("100.00"));

            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("100.00"), APRIL, null, List.of(lotId)));

            // Sold at €20, which includes the freight. Reversing now would credit Inventory for
            // stock that is not there and leave the sold units costed at a figure nothing supports.
            sell(grinder, 2);

            assertThatExceptionOfType(InvalidFreightAllocationException.class)
                    .isThrownBy(() -> allocations.reverse(allocation.id(), MAY, "too late"))
                    .withMessageContaining("costed out at the unit cost this allocation raised");
        }

        @Test
        @DisplayName("an allocation cannot be reversed twice, and a reversal cannot be reversed")
        void reversedAtMostOnce() {
            long lotId = lot("FAIT Twice", product("FAIT-42"), 5, "10.000000");
            long freight = freightLine("FRT-42", Money.ofEur("25.00"));
            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("25.00"), APRIL, null, List.of(lotId)));
            FreightAllocationView reversal = allocations.reverse(allocation.id(), MAY, null);

            assertThatExceptionOfType(InvalidFreightAllocationException.class)
                    .isThrownBy(() -> allocations.reverse(allocation.id(), MAY, null))
                    .withMessageContaining("already been reversed");

            assertThatExceptionOfType(InvalidFreightAllocationException.class)
                    .isThrownBy(() -> allocations.reverse(reversal.id(), MAY, null))
                    .withMessageContaining("is itself the reversal");
        }

        @Test
        @DisplayName("the entry is neither amendable nor reversible through the ledger alone")
        void theLedgerRefusesToTouchIt() {
            long lotId = lot("FAIT Immutable", product("FAIT-43"), 5, "10.000000");
            long freight = freightLine("FRT-43", Money.ofEur("25.00"));
            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("25.00"), APRIL, null, List.of(lotId)));
            JournalEntryView entry = journal.requireEntry(allocation.journalEntryId());

            assertThat(entry.source().isAmendable()).isFalse();
            assertThatExceptionOfType(JournalEntryNotAmendableException.class)
                    .isThrownBy(() -> journal.amend(entry.id(), MAY, "edited", entry.lines().stream()
                            .map(line -> line.isDebit()
                                    ? gr.novotrade.novocore.core.api.ledger.NewJournalLine
                                            .debit(line.accountId(), line.amount())
                                    : gr.novotrade.novocore.core.api.ledger.NewJournalLine
                                            .credit(line.accountId(), line.amount()))
                            .toList()));

            // The ledger cannot see the per-unit cost this put on a lot, so it refuses and names the
            // service that can — JournalSource.isReversibleThroughTheLedgerAlone().
            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.reverse(entry.id(), MAY, "ledger reversal"));
        }
    }

    @Nested
    @DisplayName("consequences elsewhere")
    class Consequences {

        @Test
        @DisplayName("the last purchase price stays what was paid, not what the lot now carries")
        void lastPurchasePriceIgnoresFreight() {
            // The step 6 obligation, discharged. Before step 10 this read the lot's unit cost, which
            // would now report a product as having gone up in price because it came by air.
            ProductView grinder = product("FAIT-50");
            long lotId = lot("FAIT Last", grinder, 10, "10.000000");
            allocations.allocate(NewFreightAllocation.of(
                    freightLine("FRT-50", Money.ofEur("50.00")), Money.ofEur("50.00"),
                    APRIL, null, List.of(lotId)));

            assertThat(inventory.requireLot(lotId).unitCost()).isEqualTo(UnitCost.ofEur("15.000000"));
            assertThat(inventory.lastPurchaseCostOf(grinder.id()))
                    .contains(UnitCost.ofEur("10.000000"));
        }

        @Test
        @DisplayName("a sale after the allocation costs out at the landed cost")
        void fifoUsesTheLandedCost() {
            ProductView grinder = product("FAIT-51");
            long lotId = lot("FAIT Fifo", grinder, 10, "10.000000");
            allocations.allocate(NewFreightAllocation.of(
                    freightLine("FRT-51", Money.ofEur("100.00")), Money.ofEur("100.00"),
                    APRIL, null, List.of(lotId)));

            AccountBalance cogsBefore = journal.balanceOf(AccountSystemKey.COST_OF_GOODS_SOLD, MAY);
            sell(grinder, 3);

            // Brief §5's whole reason for allocating: three units now cost €60, not €30.
            assertThat(balanceChange(AccountSystemKey.COST_OF_GOODS_SOLD, cogsBefore))
                    .isEqualTo(Money.ofEur("60.00"));
            assertThat(inventory.requireLot(lotId).remainingValue())
                    .isEqualTo(Money.ofEur("140.00"));
        }

        @Test
        @DisplayName("the lots carrying freight are queryable, and one lot's allocations are too")
        void queries() {
            ProductView grinder = product("FAIT-52");
            long lotId = lot("FAIT Query", grinder, 10, "10.000000");
            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freightLine("FRT-52", Money.ofEur("30.00")), Money.ofEur("30.00"),
                    APRIL, null, List.of(lotId)));

            assertThat(inventory.lotsWithAllocatedLandedCost())
                    .extracting(InventoryLotView::id)
                    .contains(lotId);
            assertThat(allocations.ofLot(lotId))
                    .extracting(FreightAllocationView::id)
                    .containsExactly(allocation.id());
            assertThat(allocations.between(APRIL, MAY))
                    .extracting(FreightAllocationView::id)
                    .contains(allocation.id());
        }
    }

    @Nested
    @DisplayName("the invariants are the database's, not only the service's")
    class Structural {

        @Test
        @DisplayName("a lot's two cost halves cannot disagree about their currency")
        void costHalvesShareACurrency() {
            long lotId = lot("FAIT Currency", product("FAIT-60"), 5, "10.000000");

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE inventory_lot SET allocated_landed_unit_cost_currency = 'USD' WHERE id = ?",
                    lotId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("inventory_lot_cost_halves_share_a_currency");
        }

        @Test
        @DisplayName("a lot cannot be carried below what was paid for it")
        void allocatedLandedCostCannotGoNegative() {
            long lotId = lot("FAIT Negative", product("FAIT-61"), 5, "10.000000");

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE inventory_lot SET allocated_landed_unit_cost = -1 WHERE id = ?", lotId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("inventory_lot_allocated_landed_cost_not_negative");
        }

        @Test
        @DisplayName("one lot cannot appear twice in one allocation, whatever the service does")
        void oneLotOneLinePerAllocation() {
            long lotId = lot("FAIT Unique", product("FAIT-62"), 5, "10.000000");
            long freight = freightLine("FRT-62", Money.ofEur("25.00"));
            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("25.00"), APRIL, null, List.of(lotId)));

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO freight_allocation_line (
                        allocation_id, line_number, lot_id, quantity_remaining_at_allocation,
                        capitalised_amount, capitalised_amount_currency,
                        variance_amount, variance_amount_currency,
                        landed_unit_cost, landed_unit_cost_currency)
                    VALUES (?, 99, ?, 5, 1, 'EUR', 0, 'EUR', 0.2, 'EUR')
                    """, allocation.id(), lotId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("freight_allocation_line_lot_unique");
        }

        @Test
        @DisplayName("an allocation can be reversed at most once, by constraint")
        void reversedAtMostOnceStructurally() {
            long lotId = lot("FAIT StructRev", product("FAIT-63"), 5, "10.000000");
            long freight = freightLine("FRT-63", Money.ofEur("25.00"));
            FreightAllocationView allocation = allocations.allocate(NewFreightAllocation.of(
                    freight, Money.ofEur("25.00"), APRIL, null, List.of(lotId)));
            FreightAllocationView reversal = allocations.reverse(allocation.id(), MAY, null);

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO freight_allocation (purchase_invoice_line_id, allocation_date,
                                                    journal_entry_id, reversal_of_id)
                    VALUES (?, DATE '2026-05-20', ?, ?)
                    """, freight, reversal.journalEntryId(), allocation.id()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
