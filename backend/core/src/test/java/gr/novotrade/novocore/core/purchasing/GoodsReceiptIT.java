package gr.novotrade.novocore.core.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.inventory.InvalidInventoryLotException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewStockConsumption;
import gr.novotrade.novocore.core.api.inventory.NewStockWriteOff;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitView;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.WriteOffReason;
import gr.novotrade.novocore.core.api.ledger.AccountBalance;
import gr.novotrade.novocore.core.api.ledger.InvalidJournalEntryException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotAmendableException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalLineView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptView;
import gr.novotrade.novocore.core.api.purchasing.InvalidGoodsReceiptException;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceiptLine;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
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

/**
 * The Goods Receipt — ADR 0004's inventory event, and Q39's immutability (ADR 0008).
 *
 * <p>What these tests are really defending is the claim that made ADR 0004 worth writing: stock exists
 * because a delivery arrived, not because a document was recorded. So the receipt has to create lots
 * <em>and</em> post, together, and it has to be able to do so with no invoice anywhere in sight.
 */
class GoodsReceiptIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 15);

    @Autowired
    private GoodsReceiptService receipts;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private ProductService products;

    @Autowired
    private SupplierService suppliers;

    @Autowired
    private JournalService journal;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    private SupplierView supplier(String name) {
        return suppliers.create(NewSupplier.domestic(name, "EL" + Math.abs(name.hashCode())));
    }

    private ProductView pooledProduct(String sku) {
        return products.create(NewProduct.goods(sku, sku + " pooled",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur("50.00")));
    }

    private ProductView serialisedProduct(String sku) {
        return products.create(NewProduct.serializedGoods(sku, sku + " machine",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur("2400.00")));
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("receiving")
    class Receiving {

        @Test
        @DisplayName("a delivery creates one lot per line and posts Inventory against GR/IR")
        void receiptCreatesLotsAndPosts() {
            SupplierView acme = supplier("GRIT Acme");
            ProductView grinder = pooledProduct("GRIT-01");

            AccountBalance inventoryBefore =
                    journal.balanceOf(AccountSystemKey.INVENTORY, APRIL);
            AccountBalance clearingBefore = journal.balanceOf(
                    AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, APRIL);

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(10L), UnitCost.ofEur("12.500000")))));

            assertThat(receipt.lines()).hasSize(1);
            assertThat(receipt.totalValue()).isEqualTo(Money.ofEur("125.00"));
            assertThat(receipt.capitalisedNothing()).isFalse();

            InventoryLotView lot = inventory.requireLot(receipt.lines().getFirst().lotId());
            assertThat(lot.quantityRemaining()).isEqualTo(Quantity.of(10L));
            assertThat(lot.unitCost()).isEqualTo(UnitCost.ofEur("12.500000"));
            // Brief §5's source document reference, which V12 deliberately deferred to this step.
            assertThat(lot.sourceReceiptLine())
                    .contains(receipt.lines().getFirst().id());

            assertThat(journal.balanceOf(AccountSystemKey.INVENTORY, APRIL).net()
                    .minus(inventoryBefore.net()))
                    .isEqualTo(Money.ofEur("125.00"));
            // ADR 0004: the credit is GR/IR, never Accounts payable. Nobody has invoiced us yet.
            assertThat(clearingBefore.net()
                    .minus(journal.balanceOf(
                            AccountSystemKey.GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING, APRIL).net()))
                    .isEqualTo(Money.ofEur("125.00"));
        }

        @Test
        @DisplayName("the Inventory line names the lot and the GR/IR line names the supplier")
        void postingCarriesBothSubLedgerReferences() {
            SupplierView acme = supplier("GRIT SubLedger");
            ProductView grinder = pooledProduct("GRIT-02");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(4L), UnitCost.ofEur("30.000000")))));

            JournalEntryView entry = journal.requireEntry(receipt.journalEntryId());
            assertThat(entry.source()).isEqualTo(JournalSource.GOODS_RECEIPT);

            JournalLineView debit = entry.lines().stream()
                    .filter(line -> line.side() == BalanceSide.DEBIT).findFirst().orElseThrow();
            JournalLineView credit = entry.lines().stream()
                    .filter(line -> line.side() == BalanceSide.CREDIT).findFirst().orElseThrow();

            assertThat(debit.subLedger())
                    .contains(SubLedgerRef.inventoryLot(receipt.lines().getFirst().lotId()));
            assertThat(credit.subLedger()).contains(SubLedgerRef.supplier(acme.id()));
        }

        @Test
        @DisplayName("a delivery of serial-tracked machines creates one lot with its units")
        void serializedDelivery() {
            SupplierView acme = supplier("GRIT Serial");
            ProductView machine = serialisedProduct("GRIT-03");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.serialized(machine.id(),
                            List.of("SN-A", "SN-B"), UnitCost.ofEur("1800.000000")))));

            assertThat(receipt.lines().getFirst().serialNumbers()).containsExactly("SN-A", "SN-B");
            assertThat(receipt.totalValue()).isEqualTo(Money.ofEur("3600.00"));
            assertThat(inventory.findUnitBySerialNumber("SN-A")).isPresent();
        }

        @Test
        @DisplayName("a free sample arrives and nothing is posted, which is the honest record")
        void zeroCostDeliveryCapitalisesNothing() {
            SupplierView acme = supplier("GRIT Free");
            ProductView sample = pooledProduct("GRIT-04");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            sample.id(), Quantity.of(3L), UnitCost.zero(Money.EUR)))));

            assertThat(receipt.capitalisedNothing()).isTrue();
            assertThat(receipt.journalEntryId()).isNull();
            // The stock still arrived. A free sample derecognises nothing and recognises nothing.
            assertThat(inventory.sellableStockOf(sample.id())).isEqualTo(Quantity.of(3L));
        }

        @Test
        @DisplayName("a delivery straight into Damaged Goods is not sellable stock")
        void deliveryIntoDamagedGoods() {
            SupplierView acme = supplier("GRIT Damaged");
            ProductView grinder = pooledProduct("GRIT-05");

            receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine
                            .pooled(grinder.id(), Quantity.of(2L), UnitCost.ofEur("40.000000"))
                            .at(StockLocation.DAMAGED_GOODS))));

            assertThat(inventory.stockOf(grinder.id()).at(StockLocation.DAMAGED_GOODS))
                    .isEqualTo(Quantity.of(2L));
            assertThat(inventory.sellableStockOf(grinder.id())).isEqualTo(Quantity.ZERO);
        }

        @Test
        @DisplayName("a bundle, a service and a shape mismatch are all refused")
        void refusals() {
            SupplierView acme = supplier("GRIT Refuse");
            ProductView service = products.create(NewProduct.service("GRIT-SVC",
                    "Installation", unitsOfMeasure.requireByCode("PIECE").id(),
                    vatClasses.requireByCode("1410").id(), Money.ofEur("80.00")));
            ProductView machine = serialisedProduct("GRIT-06");

            assertThatExceptionOfType(InvalidGoodsReceiptException.class)
                    .isThrownBy(() -> receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                            List.of(NewGoodsReceiptLine.pooled(
                                    service.id(), Quantity.of(1L), UnitCost.ofEur("1.000000"))))))
                    .withMessageContaining("has no stock");

            assertThatExceptionOfType(InvalidGoodsReceiptException.class)
                    .isThrownBy(() -> receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                            List.of(NewGoodsReceiptLine.pooled(
                                    machine.id(), Quantity.of(2L), UnitCost.ofEur("1.000000"))))))
                    .withMessageContaining("serial-tracked");
        }

        @Test
        @DisplayName("a line with no invoice behind it has to state a cost, and one with an invoice may not")
        void theCostIsStatedExactlyWhereItIsNotAlreadyKnown() {
            // The request type refuses both mistakes before any service sees them, because ADR 0008
            // turns on exactly this: a provisional cost is only provisional when nothing better exists.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new NewGoodsReceiptLine(1L, Quantity.of(1L), List.of(),
                            null, StockLocation.INVENTORY, null, null))
                    .withMessageContaining("its unit cost has to be stated");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new NewGoodsReceiptLine(1L, Quantity.of(1L), List.of(),
                            UnitCost.ofEur("1.000000"), StockLocation.INVENTORY, null, 99L))
                    .withMessageContaining("must not be restated");
        }
    }

    @Nested
    @DisplayName("Q39 — immutability and reversal")
    class Immutability {

        @Test
        @DisplayName("the posted entry cannot be amended in place")
        void entryIsImmutable() {
            SupplierView acme = supplier("GRIT Immutable");
            ProductView grinder = pooledProduct("GRIT-07");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));
            JournalEntryView entry = journal.requireEntry(receipt.journalEntryId());

            assertThatExceptionOfType(JournalEntryNotAmendableException.class)
                    .isThrownBy(() -> journal.amend(entry.id(), APRIL, "edited", entry.lines()
                            .stream()
                            .map(line -> gr.novotrade.novocore.core.api.ledger.NewJournalLine
                                    .of(line.accountId(), line.side(), line.amount()))
                            .toList()));
        }

        @Test
        @DisplayName("the ledger refuses to reverse it alone, and names the service that can")
        void ledgerRefusesToReverseAlone() {
            SupplierView acme = supplier("GRIT LedgerReverse");
            ProductView grinder = pooledProduct("GRIT-08");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));

            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.reverse(
                            receipt.journalEntryId(), APRIL, "undo"))
                    .withMessageContaining("GoodsReceiptService");
        }

        @Test
        @DisplayName("reversing un-receives the lots and posts the mirror, together")
        void reversalUndoesBothHalves() {
            SupplierView acme = supplier("GRIT Reverse");
            ProductView grinder = pooledProduct("GRIT-09");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(6L), UnitCost.ofEur("20.000000")))));
            AccountBalance inventoryAfterReceipt =
                    journal.balanceOf(AccountSystemKey.INVENTORY, APRIL);

            GoodsReceiptView reversal =
                    receipts.reverse(receipt.id(), APRIL, "entered against the wrong supplier");

            assertThat(reversal.isReversal()).isTrue();
            assertThat(reversal.lines()).isEmpty();
            assertThat(receipts.require(receipt.id()).isReversed()).isTrue();
            assertThat(receipts.require(receipt.id()).isInForce()).isFalse();

            assertThat(inventory.sellableStockOf(grinder.id())).isEqualTo(Quantity.ZERO);
            assertThat(inventoryAfterReceipt.net()
                    .minus(journal.balanceOf(AccountSystemKey.INVENTORY, APRIL).net()))
                    .isEqualTo(Money.ofEur("120.00"));
        }

        @Test
        @DisplayName("reversing a delivery of machines frees their serial numbers for a re-entry")
        void reversalReleasesSerialNumbers() {
            SupplierView acme = supplier("GRIT Serials");
            ProductView machine = serialisedProduct("GRIT-10");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.serialized(
                            machine.id(), List.of("SN-DUP-1"), UnitCost.ofEur("900.000000")))));
            receipts.reverse(receipt.id(), APRIL, "typed the wrong serial");

            // The commonest reason to reverse a delivery is that it was entered wrong, so re-entering
            // it correctly must not be blocked by the mistake.
            assertThat(inventory.findUnitBySerialNumber("SN-DUP-1")).isEmpty();
            GoodsReceiptView again = receipts.record(NewGoodsReceipt.of(acme.id(), APRIL,
                    List.of(NewGoodsReceiptLine.serialized(
                            machine.id(), List.of("SN-DUP-1"), UnitCost.ofEur("900.000000")))));

            SerializedUnitView unit =
                    inventory.findUnitBySerialNumber("SN-DUP-1").orElseThrow();
            assertThat(unit.status()).isEqualTo(SerializedUnitStatus.IN_STOCK);
            assertThat(again.lines().getFirst().lotId()).isNotEqualTo(
                    receipt.lines().getFirst().lotId());
        }

        @Test
        @DisplayName("a delivery whose stock has already been consumed cannot be un-made")
        void consumedStockBlocksReversal() {
            SupplierView acme = supplier("GRIT Consumed");
            ProductView grinder = pooledProduct("GRIT-11");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(5L), UnitCost.ofEur("10.000000")))));
            inventory.consume(NewStockConsumption.of(
                    grinder.id(), Quantity.of(2L), APRIL, JournalSource.SALES_INVOICE));

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> receipts.reverse(receipt.id(), APRIL, "undo"))
                    .withMessageContaining("has been consumed");
        }

        @Test
        @DisplayName("a delivery whose stock has been written off cannot be un-made either")
        void writtenOffStockBlocksReversal() {
            SupplierView acme = supplier("GRIT WrittenOff");
            ProductView grinder = pooledProduct("GRIT-12");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(5L), UnitCost.ofEur("10.000000")))));
            inventory.writeOff(NewStockWriteOff.pooled(receipt.lines().getFirst().lotId(),
                    Quantity.of(1L), WriteOffReason.DAMAGE, APRIL));

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> receipts.reverse(receipt.id(), APRIL, "undo"))
                    .withMessageContaining("written off");
        }

        @Test
        @DisplayName("a delivery cannot be reversed twice")
        void reversedAtMostOnce() {
            SupplierView acme = supplier("GRIT Twice");
            ProductView grinder = pooledProduct("GRIT-13");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(1L), UnitCost.ofEur("10.000000")))));
            receipts.reverse(receipt.id(), APRIL, "first");

            assertThatExceptionOfType(InvalidGoodsReceiptException.class)
                    .isThrownBy(() -> receipts.reverse(receipt.id(), APRIL, "second"))
                    .withMessageContaining("already been reversed");
        }
    }

    @Nested
    @DisplayName("the GR/IR position")
    class ClearingPosition {

        @Test
        @DisplayName("an uninvoiced delivery shows as received-not-invoiced")
        void deliveriesAwaitingInvoiceAreVisible() {
            SupplierView acme = supplier("GRIT Awaiting");
            ProductView grinder = pooledProduct("GRIT-14");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(7L), UnitCost.ofEur("11.000000")))));

            assertThat(receipts.linesAwaitingInvoice())
                    .extracting(line -> line.id())
                    .contains(receipt.lines().getFirst().id());
            assertThat(receipt.linesAwaitingInvoice()).hasSize(1);
            assertThat(receipt.lines().getFirst().openQuantity()).isEqualTo(Quantity.of(7L));
        }

        @Test
        @DisplayName("a lot can be traced back to the delivery it came from")
        void lotTracesBackToItsDelivery() {
            SupplierView acme = supplier("GRIT Trace");
            ProductView grinder = pooledProduct("GRIT-15");

            GoodsReceiptView receipt = receipts.record(NewGoodsReceipt.of(acme.id(), MARCH,
                    List.of(NewGoodsReceiptLine.pooled(
                            grinder.id(), Quantity.of(2L), UnitCost.ofEur("15.000000")))));

            assertThat(receipts.findByLot(receipt.lines().getFirst().lotId()))
                    .map(GoodsReceiptView::id)
                    .contains(receipt.id());
            assertThat(receipts.findByJournalEntry(receipt.journalEntryId()))
                    .map(GoodsReceiptView::id)
                    .contains(receipt.id());
        }
    }
}
