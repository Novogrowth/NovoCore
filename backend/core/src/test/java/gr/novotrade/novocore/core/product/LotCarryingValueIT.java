package gr.novotrade.novocore.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.inventory.InvalidStockConsumptionException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewStockConsumption;
import gr.novotrade.novocore.core.api.inventory.NewStockWriteOff;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionView;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.WriteOffReason;
import gr.novotrade.novocore.core.api.ledger.JournalLineView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationView;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptView;
import gr.novotrade.novocore.core.api.purchasing.NewFreightAllocation;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceiptLine;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoice;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoiceLine;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceView;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.supplier.NewSupplier;
import gr.novotrade.novocore.core.api.supplier.SupplierService;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * <strong>Q45, and ADR 0015 which answers it — the worked examples.</strong>
 *
 * <p>{@link FifoPropertiesIT} asserts that the Inventory control account agrees with the lots over
 * generated histories, which is the general claim and is what found this. This file holds the
 * specific numbers, because a defect that has been measured deserves a test that names its
 * measurements.
 *
 * <p><strong>Proven to actually fail.</strong> The old formula was reinstated and these run against
 * it: five of the eight go red, including {@link TheReportedCase#thePathThroughTheLotDoesNotChangeItsTotalCost()}
 * at €275.22 against €275.11 — the reported drift, to the cent. The three that stay green are
 * supposed to: the write-off and freight cases exercise paths the probe did not revert, and
 * {@link Reversal#wholeCentLotsAreUnaffectedByTheGuard()} asserts that the new guard is <em>silent</em>
 * on ordinary data, which was as true before as after.
 *
 * <p>What was wrong, in one sentence: a Goods Receipt debited Inventory with the whole delivery
 * rounded once while each sale credited its own line rounded once, and those do not add up unless
 * the unit cost is a whole number of cents — which it is not for any lot a landed cost has been
 * allocated onto. What is right: every posting that moves a lot puts <em>the change in the lot's
 * carrying value</em> on the Inventory line, so the account and the lot cannot drift apart and an
 * emptied lot leaves nothing behind.
 */
class LotCarryingValueIT extends AbstractCoreIntegrationTest {

    /** The cost from the reported reproducer. 22 x 12.505 = 275.11 exactly; one unit does not. */
    private static final String AWKWARD = "12.505000";

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 3);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 4);
    private static final LocalDate MAY = LocalDate.of(2026, 5, 5);

    private static final AtomicInteger N = new AtomicInteger();

    @Autowired private InventoryService inventory;
    @Autowired private ProductService products;
    @Autowired private JournalService journal;
    @Autowired private ChartOfAccountsService chart;
    @Autowired private VatClassService vatClasses;
    @Autowired private UnitOfMeasureService unitsOfMeasure;
    @Autowired private GoodsReceiptService goodsReceipts;
    @Autowired private PurchaseInvoiceService purchaseInvoices;
    @Autowired private FreightAllocationService freight;
    @Autowired private SupplierService suppliers;

    // ---------------------------------------------------------------------------------------

    private ProductView product() {
        return products.create(NewProduct.goods("LCV-" + N.incrementAndGet(), "carrying value",
                unitsOfMeasure.requireByCode("PIECE").id(),
                vatClasses.requireByCode("1410").id(), Money.ofEur("50.00")));
    }

    private long supplier() {
        // Its own counter value: supplier names are unique and one test creates several.
        return suppliers.create(
                NewSupplier.domestic("LCV supplier " + N.incrementAndGet(), null)).id();
    }

    /** A delivery through the document that actually posts one — ADR 0004. */
    private long deliver(ProductView product, long units, String unitCost, LocalDate when) {
        GoodsReceiptView receipt = goodsReceipts.record(NewGoodsReceipt.of(supplier(), when,
                List.of(NewGoodsReceiptLine.pooled(
                        product.id(), Quantity.of(units), UnitCost.ofEur(unitCost)))));
        return receipt.lotIds().getFirst();
    }

    private StockConsumptionView sell(ProductView product, long units, LocalDate when) {
        return inventory.consume(NewStockConsumption.of(
                product.id(), Quantity.of(units), when, JournalSource.SALES_INVOICE));
    }

    /** The Inventory control account's own side, for one lot. */
    private Money inventoryHoldsForLot(long lotId) {
        long inventoryAccount = chart.requireAccount(AccountSystemKey.INVENTORY).id();
        Money held = Money.zero(Money.EUR);
        for (JournalLineView line : journal.linesFor(SubLedgerRef.inventoryLot(lotId))) {
            if (line.accountId() != inventoryAccount) {
                continue;
            }
            held = line.side() == BalanceSide.DEBIT
                    ? held.plus(line.amount()) : held.minus(line.amount());
        }
        return held;
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("the reported case")
    class TheReportedCase {

        @Test
        @DisplayName("22 units at 12.505, sold one at a time, leave Inventory at exactly zero")
        void theLotSelfLiquidates() {
            // The reproducer, verbatim. Before ADR 0015 this ended at -0.11 EUR with the lot empty:
            // the receipt debited 275.11 and twenty-two sales credited 12.51 each, being 275.22.
            ProductView beans = product();
            long lot = deliver(beans, 22L, AWKWARD, MARCH);
            assertThat(inventoryHoldsForLot(lot)).isEqualTo(Money.ofEur("275.11"));

            for (int i = 0; i < 22; i++) {
                sell(beans, 1L, APRIL);
                InventoryLotView after = inventory.requireLot(lot);
                assertThat(inventoryHoldsForLot(lot))
                        .as("after selling %d of 22", i + 1)
                        .isEqualTo(after.remainingValue());
            }

            assertThat(inventory.requireLot(lot).quantityRemaining()).isEqualTo(Quantity.ZERO);
            assertThat(inventoryHoldsForLot(lot))
                    .as("an emptied lot leaves nothing behind in the Inventory control account")
                    .isEqualTo(Money.zero(Money.EUR));
        }

        @Test
        @DisplayName("consecutive units cost 12.50 and 12.51, which is the visible consequence")
        void unitsOfOneLotCostDifferentAmounts() {
            // Worth stating outright rather than leaving to be discovered: two identical units out
            // of one lot do NOT post identical costs. They cannot, if the lot is to end at zero —
            // 22 x 12.505 is 275.11 and no repeated cent figure divides it. Each unit takes the
            // step in the lot's value it actually caused.
            ProductView beans = product();
            long lot = deliver(beans, 22L, AWKWARD, MARCH);

            assertThat(sell(beans, 1L, APRIL).totalCost()).isEqualTo(Money.ofEur("12.50"));
            assertThat(sell(beans, 1L, APRIL).totalCost()).isEqualTo(Money.ofEur("12.51"));
            assertThat(inventoryHoldsForLot(lot))
                    .isEqualTo(inventory.requireLot(lot).remainingValue());
        }

        @Test
        @DisplayName("selling the lot in one go costs the same as selling it unit by unit")
        void thePathThroughTheLotDoesNotChangeItsTotalCost() {
            // The property that makes the fix more than a patch: cost is now path-independent. Under
            // the old rule twenty-two single sales cost 275.22 and one sale of twenty-two cost
            // 275.11, and both claimed to be the cost of the same lot.
            ProductView oneAtATime = product();
            long pieceMeal = deliver(oneAtATime, 22L, AWKWARD, MARCH);
            Money accumulated = Money.zero(Money.EUR);
            for (int i = 0; i < 22; i++) {
                accumulated = accumulated.plus(sell(oneAtATime, 1L, APRIL).totalCost());
            }

            ProductView allAtOnce = product();
            long wholeLot = deliver(allAtOnce, 22L, AWKWARD, MARCH);
            Money atOnce = sell(allAtOnce, 22L, APRIL).totalCost();

            assertThat(accumulated).isEqualTo(atOnce).isEqualTo(Money.ofEur("275.11"));
            assertThat(inventoryHoldsForLot(pieceMeal)).isEqualTo(Money.zero(Money.EUR));
            assertThat(inventoryHoldsForLot(wholeLot)).isEqualTo(Money.zero(Money.EUR));
        }
    }

    @Nested
    @DisplayName("the other ways stock moves")
    class OtherMovements {

        @Test
        @DisplayName("a write-off takes off exactly what a sale of the same units would")
        void writeOffsFollowTheSameRule() {
            ProductView beans = product();
            long lot = deliver(beans, 3L, "0.333333", MARCH);
            assertThat(inventoryHoldsForLot(lot)).isEqualTo(Money.ofEur("1.00"));

            inventory.moveLot(lot, StockLocation.DAMAGED_GOODS);
            for (int i = 0; i < 3; i++) {
                inventory.writeOff(NewStockWriteOff.pooled(
                        lot, Quantity.of(1L), WriteOffReason.DAMAGE, APRIL));
                assertThat(inventoryHoldsForLot(lot))
                        .isEqualTo(inventory.requireLot(lot).remainingValue());
            }
            assertThat(inventoryHoldsForLot(lot)).isEqualTo(Money.zero(Money.EUR));
        }

        @Test
        @DisplayName("a freight allocation raises Inventory by exactly what the lot gains")
        void freightRaisesInventoryByTheLotsActualGain() {
            // ADR 0010's capitalised half is now the lot's real increase rather than a proportional
            // estimate of it. The two halves still sum to the share, so the unallocated account
            // still clears exactly — asserted here as well, because that is what would break if the
            // capitalised figure were computed one way and posted another.
            ProductView grinders = product();
            long lot = deliver(grinders, 3L, "10.000000", MARCH);
            sell(grinders, 1L, APRIL);

            Money inventoryBefore = inventoryHoldsForLot(lot);
            PurchaseInvoiceView carrier = purchaseInvoices.record(NewPurchaseInvoice.of(
                    supplier(), "LCV-FRT-" + N.incrementAndGet(), MAY,
                    List.of(NewPurchaseInvoiceLine.expense(
                            chart.requireAccount(AccountSystemKey.FREIGHT_LANDED_COST_UNALLOCATED)
                                    .id(),
                            Money.ofEur("2.00"), vatClasses.requireByCode("1410").id()))));

            FreightAllocationView allocation = freight.allocate(NewFreightAllocation.of(
                    carrier.lines().getFirst().id(), Money.ofEur("2.00"), MAY,
                    "2.00 over three units is 0.666667 each", List.of(lot)));

            assertThat(allocation.capitalised().plus(allocation.variance()))
                    .as("the two halves are still the whole share")
                    .isEqualTo(allocation.amount());
            assertThat(inventoryHoldsForLot(lot).minus(inventoryBefore))
                    .as("Inventory moved by exactly the capitalised half")
                    .isEqualTo(allocation.capitalised());
            assertThat(inventoryHoldsForLot(lot))
                    .isEqualTo(inventory.requireLot(lot).remainingValue());

            // And the re-costed lot still empties to nothing.
            sell(grinders, 2L, MAY.plusDays(1));
            assertThat(inventory.requireLot(lot).quantityRemaining()).isEqualTo(Quantity.ZERO);
            assertThat(inventoryHoldsForLot(lot)).isEqualTo(Money.zero(Money.EUR));
        }
    }

    @Nested
    @DisplayName("reversal — the one place the two rules pull apart")
    class Reversal {

        @Test
        @DisplayName("reversing the most recent movement is exact, and is allowed")
        void reversingTheLastMovementRestoresTheLotExactly() {
            ProductView beans = product();
            long lot = deliver(beans, 22L, AWKWARD, MARCH);
            sell(beans, 1L, APRIL);
            StockConsumptionView second = sell(beans, 1L, APRIL);

            Money beforeReversal = inventoryHoldsForLot(lot);
            inventory.reverseConsumption(second.id(), MAY, "wrong customer");

            assertThat(inventoryHoldsForLot(lot))
                    .isEqualTo(beforeReversal.plus(Money.ofEur("12.51")))
                    .isEqualTo(inventory.requireLot(lot).remainingValue());
        }

        @Test
        @DisplayName("reversing an earlier one, after the lot has moved, is refused and says why")
        void reversingBehindALaterMovementIsRefused() {
            // The mirror of the first sale is 12.50, but putting one unit back into a lot now
            // holding 20 is worth 12.51. A reversal must be an exact mirror (Q13, ADR 0006), so
            // posting it would strand a cent in Inventory — the very thing ADR 0015 removes. It is
            // refused in exactly this case and no other: it cannot fire for a whole-cent cost, and
            // it did not fire for the test above.
            ProductView beans = product();
            deliver(beans, 22L, AWKWARD, MARCH);
            StockConsumptionView first = sell(beans, 1L, APRIL);
            sell(beans, 1L, APRIL);

            assertThatExceptionOfType(InvalidStockConsumptionException.class)
                    .isThrownBy(() -> inventory.reverseConsumption(first.id(), MAY, "too late"))
                    .withMessageContaining("took 12.50 EUR off Inventory")
                    .withMessageContaining("worth 12.51 EUR")
                    .withMessageContaining("reverse the later movements on this lot first");
        }

        @Test
        @DisplayName("a whole-cent lot reverses freely, however much has happened since")
        void wholeCentLotsAreUnaffectedByTheGuard() {
            // The guard compares two figures rather than testing a condition, so it is silent
            // wherever they agree — which is every lot whose unit cost is a whole number of cents,
            // and that is most of them. Worth asserting, because a guard people meet by surprise on
            // ordinary data is one that gets deleted.
            ProductView beans = product();
            long lot = deliver(beans, 22L, "12.500000", MARCH);
            StockConsumptionView first = sell(beans, 1L, APRIL);
            sell(beans, 3L, APRIL);

            inventory.reverseConsumption(first.id(), MAY, "ordinary correction");

            assertThat(inventoryHoldsForLot(lot))
                    .isEqualTo(inventory.requireLot(lot).remainingValue());
        }
    }
}
