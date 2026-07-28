package gr.novotrade.novocore.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.inventory.InvalidStockConsumptionException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.NewStockConsumption;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionView;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.ledger.AccountBalance;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FIFO consumption, and <strong>Q17 answered</strong> (ADR 0008): aggregate stock may go negative, and
 * it is flagged.
 *
 * <p>Two things are being defended here. First, that FIFO means what the lots say and not what the
 * product's latest cost says — three taken from a March lot and two from a June lot cost what March
 * and June cost, which is why one sale posts a list of figures rather than one. Second, that a sale
 * with nothing behind it is recorded honestly: it posts, the stock reads negative, and somebody can
 * find it.
 */
class StockConsumptionIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate JUNE = LocalDate.of(2026, 6, 5);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 20);

    @Autowired
    private InventoryService inventory;

    @Autowired
    private ProductService products;

    @Autowired
    private JournalService journal;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    private ProductView pooledProduct(String sku) {
        return products.create(NewProduct.goods(sku, sku + " pooled",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur("50.00")));
    }

    private InventoryLotView lot(long productId, long quantity, String unitCost, LocalDate when) {
        return inventory.receive(NewInventoryLot.pooled(productId, Quantity.of(quantity),
                UnitCost.ofEur(unitCost), when, StockLocation.INVENTORY));
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("FIFO")
    class Fifo {

        @Test
        @DisplayName("the oldest lot goes first, and a consumption spanning two costs both")
        void consumptionSpansLotsInAcquisitionOrder() {
            ProductView beans = pooledProduct("SCIT-01");
            InventoryLotView march = lot(beans.id(), 3L, "10.000000", MARCH);
            InventoryLotView june = lot(beans.id(), 5L, "14.000000", JUNE);

            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(5L), JULY, JournalSource.SALES_INVOICE));

            assertThat(sale.lotsTouched()).isEqualTo(2);
            assertThat(sale.lines().get(0).lotId()).isEqualTo(march.id());
            assertThat(sale.lines().get(0).quantity()).isEqualTo(Quantity.of(3L));
            assertThat(sale.lines().get(1).lotId()).isEqualTo(june.id());
            assertThat(sale.lines().get(1).quantity()).isEqualTo(Quantity.of(2L));

            // 3 x 10.00 + 2 x 14.00. An average cost would have said 5 x 12.50 and been wrong about
            // both lots.
            assertThat(sale.totalCost()).isEqualTo(Money.ofEur("58.00"));
            assertThat(inventory.requireLot(march.id()).quantityRemaining()).isEqualTo(Quantity.ZERO);
            assertThat(inventory.requireLot(june.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(3L));
        }

        @Test
        @DisplayName("a backdated receipt is consumed where it belongs, not where it was typed")
        void fifoIsByAcquisitionDateNotInsertionOrder() {
            ProductView beans = pooledProduct("SCIT-02");
            InventoryLotView typedFirst = lot(beans.id(), 2L, "20.000000", JUNE);
            InventoryLotView backdated = lot(beans.id(), 2L, "9.000000", MARCH);

            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(2L), JULY, JournalSource.SALES_INVOICE));

            assertThat(sale.lines()).singleElement()
                    .satisfies(line -> assertThat(line.lotId()).isEqualTo(backdated.id()));
            assertThat(inventory.requireLot(typedFirst.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(2L));
        }

        @Test
        @DisplayName("one line per lot, both sides carrying the lot reference")
        void postingIsOneLinePerLot() {
            ProductView beans = pooledProduct("SCIT-03");
            InventoryLotView march = lot(beans.id(), 2L, "10.000000", MARCH);
            InventoryLotView june = lot(beans.id(), 2L, "20.000000", JUNE);

            AccountBalance cogsBefore =
                    journal.balanceOf(AccountSystemKey.COST_OF_GOODS_SOLD, JULY);

            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(3L), JULY, JournalSource.SALES_INVOICE));

            JournalEntryView entry = journal.requireEntry(sale.journalEntryId());
            assertThat(entry.lines()).hasSize(4);
            assertThat(entry.lines().stream()
                    .filter(line -> line.side() == BalanceSide.DEBIT)
                    .map(line -> line.subLedger().orElseThrow()))
                    .containsExactly(SubLedgerRef.inventoryLot(march.id()),
                            SubLedgerRef.inventoryLot(june.id()));

            assertThat(journal.balanceOf(AccountSystemKey.COST_OF_GOODS_SOLD, JULY).net()
                    .minus(cogsBefore.net()))
                    .isEqualTo(Money.ofEur("40.00"));
        }

        @Test
        @DisplayName("stock in Damaged Goods is not a FIFO candidate")
        void damagedStockIsNotConsumed() {
            ProductView beans = pooledProduct("SCIT-04");
            InventoryLotView shelf = lot(beans.id(), 2L, "10.000000", MARCH);
            InventoryLotView damaged = lot(beans.id(), 5L, "10.000000", MARCH);
            inventory.moveLot(damaged.id(), StockLocation.DAMAGED_GOODS);

            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(4L), JULY, JournalSource.SALES_INVOICE));

            // Selling damaged stock is not a decision a costing rule gets to make quietly, so the
            // shortfall is real even though there are five units in the building.
            assertThat(sale.quantityFilled()).isEqualTo(Quantity.of(2L));
            assertThat(sale.droveStockNegative()).isTrue();
            assertThat(inventory.requireLot(damaged.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(5L));
            assertThat(inventory.requireLot(shelf.id()).quantityRemaining()).isEqualTo(Quantity.ZERO);
        }

        @Test
        @DisplayName("consuming free stock posts nothing and the stock still leaves")
        void zeroCostLotCostsNothing() {
            ProductView samples = pooledProduct("SCIT-05");
            InventoryLotView free = lot(samples.id(), 4L, "0.000000", MARCH);

            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    samples.id(), Quantity.of(2L), JULY, JournalSource.SALES_INVOICE));

            assertThat(sale.costedNothing()).isTrue();
            assertThat(sale.lines()).hasSize(1);
            assertThat(inventory.requireLot(free.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(2L));
        }
    }

    @Nested
    @DisplayName("Q17 — negative stock is allowed and flagged")
    class NegativeStock {

        @Test
        @DisplayName("a sale posts with nothing behind it, and the product reads negative")
        void oversellingIsAllowedAndVisible() {
            ProductView beans = pooledProduct("SCIT-06");
            lot(beans.id(), 2L, "10.000000", MARCH);

            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(5L), JULY, JournalSource.SALES_INVOICE));

            assertThat(sale.quantityRequested()).isEqualTo(Quantity.of(5L));
            assertThat(sale.quantityFilled()).isEqualTo(Quantity.of(2L));
            assertThat(sale.shortfallQuantity()).isEqualTo(Quantity.of(3L));
            assertThat(sale.droveStockNegative()).isTrue();

            // Reading zero would be the same failure as answering zero for a service: technically a
            // number, and the opposite of informative.
            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(-3L));
            assertThat(inventory.stockOf(beans.id()).isOversold()).isTrue();
            assertThat(inventory.stockOf(beans.id()).hasSellableStock()).isFalse();
        }

        @Test
        @DisplayName("only the backed part is costed — nothing is guessed for the shortfall")
        void shortfallIsNotCosted() {
            ProductView beans = pooledProduct("SCIT-07");
            lot(beans.id(), 2L, "10.000000", MARCH);

            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(5L), JULY, JournalSource.SALES_INVOICE));

            // Reaching for the last purchase price would be the silent guess rule 7 forbids. COGS is
            // understated for as long as the shortfall stands, and the flag is what says so.
            assertThat(sale.totalCost()).isEqualTo(Money.ofEur("20.00"));
        }

        @Test
        @DisplayName("a sale with no stock at all posts nothing and is still recorded")
        void sellingFromEmptyStock() {
            ProductView beans = pooledProduct("SCIT-08");

            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(4L), JULY, JournalSource.SALES_INVOICE));

            assertThat(sale.quantityFilled()).isEqualTo(Quantity.ZERO);
            assertThat(sale.lines()).isEmpty();
            assertThat(sale.costedNothing()).isTrue();
            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(-4L));
        }

        @Test
        @DisplayName("the shortfall is findable, which is the whole point of allowing it")
        void shortfallsAreQueryable() {
            ProductView beans = pooledProduct("SCIT-09");
            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(1L), JULY, JournalSource.SALES_INVOICE));

            assertThat(inventory.consumptionsWithShortfall())
                    .extracting(StockConsumptionView::id)
                    .contains(sale.id());
        }

        @Test
        @DisplayName("a later delivery closes the gap without retro-costing it")
        void aLaterReceiptRestoresTheFigureWithoutTouchingTheCost() {
            ProductView beans = pooledProduct("SCIT-10");
            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(2L), JULY, JournalSource.SALES_INVOICE));
            assertThat(sale.costedNothing()).isTrue();

            lot(beans.id(), 10L, "10.000000", JULY);

            // The figure is right again — 10 received against 2 owed — and the two units sold before
            // they arrived stay uncosted. Retro-costing them would be ADR 0008's first decision in
            // reverse; the correction is to reverse the consumption and consume again.
            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(8L));
            assertThat(inventory.requireConsumption(sale.id()).costedNothing()).isTrue();
        }
    }

    @Nested
    @DisplayName("reversal")
    class Reversal {

        @Test
        @DisplayName("the lots get exactly what they gave, and the mirror posts with it")
        void reversalRestoresLotsAndPosts() {
            ProductView beans = pooledProduct("SCIT-11");
            InventoryLotView march = lot(beans.id(), 3L, "10.000000", MARCH);
            InventoryLotView june = lot(beans.id(), 5L, "14.000000", JUNE);

            AccountBalance cogsBefore =
                    journal.balanceOf(AccountSystemKey.COST_OF_GOODS_SOLD, JULY);
            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(5L), JULY, JournalSource.SALES_INVOICE));

            StockConsumptionView reversal =
                    inventory.reverseConsumption(sale.id(), JULY, "customer cancelled");

            assertThat(reversal.isReversal()).isTrue();
            assertThat(inventory.requireConsumption(sale.id()).isReversed()).isTrue();
            assertThat(inventory.requireLot(march.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(3L));
            assertThat(inventory.requireLot(june.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(5L));
            assertThat(journal.balanceOf(AccountSystemKey.COST_OF_GOODS_SOLD, JULY).net())
                    .isEqualTo(cogsBefore.net());
        }

        @Test
        @DisplayName("a consumption cannot be reversed twice, nor a reversal reversed")
        void reversalIsOnceOnly() {
            ProductView beans = pooledProduct("SCIT-12");
            lot(beans.id(), 5L, "10.000000", MARCH);
            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(2L), JULY, JournalSource.SALES_INVOICE));

            StockConsumptionView reversal = inventory.reverseConsumption(sale.id(), JULY, "first");

            assertThatExceptionOfType(InvalidStockConsumptionException.class)
                    .isThrownBy(() -> inventory.reverseConsumption(sale.id(), JULY, "second"))
                    .withMessageContaining("already been reversed");
            assertThatExceptionOfType(InvalidStockConsumptionException.class)
                    .isThrownBy(() -> inventory.reverseConsumption(reversal.id(), JULY, "again"))
                    .withMessageContaining("itself the reversal");
        }

        @Test
        @DisplayName("a consumption that filled nothing has nothing to reverse, and says so")
        void reversingAPureShortfallIsRefused() {
            ProductView beans = pooledProduct("SCIT-13");
            StockConsumptionView sale = inventory.consume(NewStockConsumption.of(
                    beans.id(), Quantity.of(2L), JULY, JournalSource.SALES_INVOICE));

            assertThatExceptionOfType(InvalidStockConsumptionException.class)
                    .isThrownBy(() -> inventory.reverseConsumption(sale.id(), JULY, "undo"))
                    .withMessageContaining("filled nothing");
        }
    }

    @Nested
    @DisplayName("what cannot be consumed")
    class Refusals {

        @Test
        @DisplayName("a serial-tracked product is refused, and names step 9")
        void serialTrackedNeedsItsUnitsNamed() {
            ProductView machine = products.create(NewProduct.serializedGoods("SCIT-14",
                    "Machine", unitsOfMeasure.requireByCode("PIECE").id(),
                    vatClasses.requireByCode("1410").id(), Money.ofEur("2400.00")));
            inventory.receive(NewInventoryLot.serialized(machine.id(), UnitCost.ofEur("1800.000000"),
                    MARCH, StockLocation.INVENTORY, List.of("SCIT-SN-1")));

            // Step 9 made this reachable, but not by FIFO: brief §5 costs a serialized item at its
            // own actual cost, so which machine left the shelf is a fact somebody scanned rather than
            // something a costing rule may choose.
            assertThatExceptionOfType(InvalidStockConsumptionException.class)
                    .isThrownBy(() -> inventory.consume(NewStockConsumption.of(
                            machine.id(), Quantity.of(1L), JULY, JournalSource.SALES_INVOICE)))
                    .withMessageContaining("must name the units");
        }

        @Test
        @DisplayName("a service has no lots and therefore no FIFO")
        void serviceIsRefused() {
            ProductView service = products.create(NewProduct.service("SCIT-15", "Installation",
                    unitsOfMeasure.requireByCode("PIECE").id(),
                    vatClasses.requireByCode("1410").id(), Money.ofEur("80.00")));

            assertThatExceptionOfType(InvalidStockConsumptionException.class)
                    .isThrownBy(() -> inventory.consume(NewStockConsumption.of(
                            service.id(), Quantity.of(1L), JULY, JournalSource.SALES_INVOICE)))
                    .withMessageContaining("Cost of service sold");
        }

        @Test
        @DisplayName("a source that may not consume stock is refused by the request itself")
        void onlyPermittedSourcesConsume() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> NewStockConsumption.of(
                            1L, Quantity.of(1L), JULY, JournalSource.MANUAL_JOURNAL_ENTRY))
                    .withMessageContaining("may not consume stock");
        }
    }
}
