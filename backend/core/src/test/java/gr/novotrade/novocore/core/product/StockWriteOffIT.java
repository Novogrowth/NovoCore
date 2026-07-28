package gr.novotrade.novocore.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.inventory.InvalidStockWriteOffException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.NewStockWriteOff;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitView;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.StockWriteOffView;
import gr.novotrade.novocore.core.api.inventory.WriteOffReason;
import gr.novotrade.novocore.core.api.ledger.AccountBalance;
import gr.novotrade.novocore.core.api.ledger.InvalidJournalEntryException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryNotAmendableException;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalLineView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
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
 * The inventory write-off: the step 3 and step 6 obligation, and the one place in step 7 where the ledger
 * and something outside it have to move together.
 *
 * <p>The chart of accounts has <em>one</em> {@code Inventory write-off / shrinkage} account rather than
 * three, on the explicit grounds that which of shrinkage, damage or expiry a write-off was belongs on the
 * transaction. These tests are what make that argument true rather than merely stated.
 */
class StockWriteOffIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 15);

    @Autowired
    private InventoryService inventory;

    @Autowired
    private ProductService products;

    @Autowired
    private JournalService journal;

    @Autowired
    private ChartOfAccountsService chart;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    private long pieceId() {
        return unitsOfMeasure.requireByCode("PIECE").id();
    }

    private long kilogramId() {
        return unitsOfMeasure.requireByCode("KILOGRAM").id();
    }

    private long standardRateId() {
        return vatClasses.requireByCode("1410").id();
    }

    private ProductView pooledProduct(String sku) {
        return products.create(NewProduct.goods(
                sku, sku + " pooled", pieceId(), standardRateId(), Money.ofEur("50.00")));
    }

    private ProductView coffeeProduct(String sku) {
        return products.create(NewProduct.goods(
                sku, sku + " coffee", kilogramId(), standardRateId(), Money.ofEur("24.50")));
    }

    private ProductView serialisedProduct(String sku) {
        return products.create(NewProduct.serializedGoods(
                sku, sku + " machine", pieceId(), standardRateId(), Money.ofEur("2400.00")));
    }

    private InventoryLotView pooledLot(String sku, long quantity, String unitCost) {
        return inventory.receive(NewInventoryLot.pooled(pooledProduct(sku).id(),
                Quantity.of(quantity), UnitCost.ofEur(unitCost), MARCH, StockLocation.INVENTORY));
    }

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("pooled stock")
    class Pooled {

        @Test
        @DisplayName("the stock leaves and the loss posts, in one transaction")
        void writeOffReducesTheLotAndPosts() {
            InventoryLotView lot = pooledLot("WOIT-POOL-01", 10L, "12.500000");

            AccountBalance inventoryBefore =
                    journal.balanceOf(AccountSystemKey.INVENTORY, APRIL);
            AccountBalance expenseBefore =
                    journal.balanceOf(AccountSystemKey.INVENTORY_WRITE_OFF, APRIL);

            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    lot.id(), Quantity.of(4L), WriteOffReason.DAMAGE, APRIL));

            assertThat(written.quantity()).isEqualTo(Quantity.of(4L));
            assertThat(written.reason()).isEqualTo(WriteOffReason.DAMAGE);
            assertThat(written.isSerialized()).isFalse();
            assertThat(written.derecognisedNothing()).isFalse();
            assertThat(written.postedValue()).contains(Money.ofEur("50.00"));

            // The stock is gone...
            assertThat(inventory.requireLot(lot.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(6L));
            // ...and the balance sheet agrees, which is the whole reason step 6 refused to build this
            // without the ledger.
            assertThat(journal.balanceOf(AccountSystemKey.INVENTORY, APRIL).onNormalSide())
                    .isEqualTo(inventoryBefore.onNormalSide().minus(Money.ofEur("50.00")));
            assertThat(journal.balanceOf(AccountSystemKey.INVENTORY_WRITE_OFF, APRIL)
                    .onNormalSide())
                    .isEqualTo(expenseBefore.onNormalSide().plus(Money.ofEur("50.00")));
        }

        @Test
        @DisplayName("both lines carry the lot, so the loss is traceable to what it came out of")
        void bothLinesNameTheLot() {
            InventoryLotView lot = pooledLot("WOIT-POOL-02", 5L, "20.000000");

            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    lot.id(), Quantity.of(1L), WriteOffReason.SHRINKAGE, APRIL));

            JournalEntryView entry = journal.requireEntry(written.journalEntry().orElseThrow());
            assertThat(entry.source()).isEqualTo(JournalSource.INVENTORY_WRITE_OFF);
            assertThat(entry.description()).contains("SHRINKAGE").contains("WOIT-POOL-02");
            assertThat(entry.lines()).hasSize(2);
            // The credit side must name the lot — Inventory is a Control account. The debit side is on
            // a STANDARD account and names it anyway: knowing which lot a loss came out of is what
            // having lots is for.
            assertThat(entry.lines()).allSatisfy(line ->
                    assertThat(line.subLedger()).contains(SubLedgerRef.inventoryLot(lot.id())));
            assertThat(entry.lines()).filteredOn(line -> line.side() == BalanceSide.DEBIT)
                    .singleElement()
                    .satisfies(line -> assertThat(line.accountId())
                            .isEqualTo(chart.requireAccount(
                                    AccountSystemKey.INVENTORY_WRITE_OFF).id()));

            // And the lot's own sub-ledger history now shows it.
            List<JournalLineView> lotHistory =
                    journal.linesFor(SubLedgerRef.inventoryLot(lot.id()));
            assertThat(lotHistory).hasSize(2);
        }

        @Test
        @DisplayName("the extended cost is rounded once, using the configured mode")
        void oneRounding() {
            // 3 x 18.456789 = 55.370367. Rounding the unit cost to 18.46 first and multiplying gives
            // 55.38 — a cent invented from nothing, which is what UnitCost's six decimals exist to
            // prevent.
            InventoryLotView lot = pooledLot("WOIT-POOL-03", 10L, "18.456789");

            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    lot.id(), Quantity.of(3L), WriteOffReason.EXPIRY, APRIL));

            assertThat(written.postedValue()).contains(Money.ofEur("55.37"));
        }

        @Test
        @DisplayName("writing off more than the lot has left is refused")
        void moreThanRemainingIsRefused() {
            InventoryLotView lot = pooledLot("WOIT-POOL-04", 3L, "10.000000");

            assertThatExceptionOfType(InvalidStockWriteOffException.class)
                    .isThrownBy(() -> inventory.writeOff(NewStockWriteOff.pooled(
                            lot.id(), Quantity.of(4L), WriteOffReason.SHRINKAGE, APRIL)))
                    .withMessageContaining("cannot be lost");
        }

        @Test
        @DisplayName("a fraction of something sold by the piece is refused")
        void fractionalQuantitiesFollowTheUnitOfMeasure() {
            InventoryLotView lot = pooledLot("WOIT-POOL-05", 5L, "10.000000");

            assertThatExceptionOfType(InvalidStockWriteOffException.class)
                    .isThrownBy(() -> inventory.writeOff(NewStockWriteOff.pooled(
                            lot.id(), Quantity.of("1.500"), WriteOffReason.DAMAGE, APRIL)))
                    .withMessageContaining("does not allow one");
        }

        @Test
        @DisplayName("coffee, which is sold by weight, may be written off in fractions")
        void fractionsAreFineWhereTheUnitAllowsThem() {
            InventoryLotView beans = inventory.receive(NewInventoryLot.pooled(
                    coffeeProduct("WOIT-COFFEE-01").id(), Quantity.of("6.000"),
                    UnitCost.ofEur("11.200000"), MARCH, StockLocation.INVENTORY));

            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    beans.id(), Quantity.of("0.750"), WriteOffReason.EXPIRY, APRIL));

            assertThat(written.postedValue()).contains(Money.ofEur("8.40"));
            assertThat(inventory.requireLot(beans.id()).quantityRemaining())
                    .isEqualTo(Quantity.of("5.250"));
        }

        @Test
        @DisplayName("a zero or negative quantity is refused")
        void quantityMustBePositive() {
            InventoryLotView lot = pooledLot("WOIT-POOL-06", 5L, "10.000000");

            assertThatExceptionOfType(InvalidStockWriteOffException.class)
                    .isThrownBy(() -> inventory.writeOff(NewStockWriteOff.pooled(
                            lot.id(), Quantity.ZERO, WriteOffReason.OTHER, APRIL)))
                    .withMessageContaining("not a write-off");
        }

        @Test
        @DisplayName("a quantity cannot be given for a serial-tracked lot")
        void theShapeMustMatchTheLot() {
            InventoryLotView machines = inventory.receive(NewInventoryLot.serialized(
                    serialisedProduct("WOIT-SER-90").id(), UnitCost.ofEur("1800.000000"), MARCH,
                    StockLocation.INVENTORY, List.of("WOIT-SN-9001")));

            assertThatExceptionOfType(InvalidStockWriteOffException.class)
                    .isThrownBy(() -> inventory.writeOff(NewStockWriteOff.pooled(
                            machines.id(), Quantity.of(1L), WriteOffReason.DAMAGE, APRIL)))
                    .withMessageContaining("no FIFO logic");
        }
    }

    @Nested
    @DisplayName("serialized stock — brief §5's exception")
    class Serialized {

        @Test
        @DisplayName("the named unit is derecognised at its own actual cost, with no FIFO")
        void oneUnitAtItsOwnCost() {
            ProductView product = serialisedProduct("WOIT-SER-01");
            // Two lots at different costs. If anything FIFO-like crept in, writing off a unit from the
            // second lot would post the first lot's cost — which is exactly what brief §5 forbids.
            inventory.receive(NewInventoryLot.serialized(product.id(),
                    UnitCost.ofEur("1000.000000"), MARCH, StockLocation.INVENTORY,
                    List.of("WOIT-SN-1001")));
            InventoryLotView dearer = inventory.receive(NewInventoryLot.serialized(product.id(),
                    UnitCost.ofEur("1750.000000"), MARCH.plusDays(1), StockLocation.INVENTORY,
                    List.of("WOIT-SN-1002")));
            SerializedUnitView unit = dearer.units().getFirst();

            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.unit(
                    dearer.id(), unit.id(), WriteOffReason.DAMAGE, APRIL));

            assertThat(written.isSerialized()).isTrue();
            assertThat(written.serialNumber()).isEqualTo("WOIT-SN-1002");
            assertThat(written.quantity()).isEqualTo(Quantity.of(1L));
            assertThat(written.postedValue()).contains(Money.ofEur("1750.00"));

            // SerializedUnitStatus.WRITTEN_OFF was declared in step 6 and unreachable until now.
            assertThat(inventory.requireUnit(unit.id()).status())
                    .isEqualTo(SerializedUnitStatus.WRITTEN_OFF);
            // The stock count reads the status column, so it is already right without being revisited.
            assertThat(inventory.stockOf(product.id()).at(StockLocation.INVENTORY))
                    .isEqualTo(Quantity.of(1L));
        }

        @Test
        @DisplayName("the location is left alone, because the machine is still physically somewhere")
        void theLocationSurvives() {
            InventoryLotView lot = inventory.receive(NewInventoryLot.serialized(
                    serialisedProduct("WOIT-SER-02").id(), UnitCost.ofEur("900.000000"), MARCH,
                    StockLocation.DAMAGED_GOODS, List.of("WOIT-SN-2001")));
            long unitId = lot.units().getFirst().id();

            inventory.writeOff(NewStockWriteOff.unit(
                    lot.id(), unitId, WriteOffReason.DAMAGE, APRIL));

            // Clearing it would lose the one fact somebody looking for the machine still needs. The
            // stock count already excludes it by status rather than by where it is.
            assertThat(inventory.requireUnit(unitId).location())
                    .isEqualTo(StockLocation.DAMAGED_GOODS);
        }

        @Test
        @DisplayName("a unit from another lot, or one no longer on hand, is refused")
        void theUnitMustBelongToTheLotAndBeOnHand() {
            ProductView product = serialisedProduct("WOIT-SER-03");
            InventoryLotView first = inventory.receive(NewInventoryLot.serialized(product.id(),
                    UnitCost.ofEur("500.000000"), MARCH, StockLocation.INVENTORY,
                    List.of("WOIT-SN-3001")));
            InventoryLotView second = inventory.receive(NewInventoryLot.serialized(product.id(),
                    UnitCost.ofEur("600.000000"), MARCH, StockLocation.INVENTORY,
                    List.of("WOIT-SN-3002")));
            long unitOfFirst = first.units().getFirst().id();

            assertThatExceptionOfType(InvalidStockWriteOffException.class)
                    .isThrownBy(() -> inventory.writeOff(NewStockWriteOff.unit(
                            second.id(), unitOfFirst, WriteOffReason.DAMAGE, APRIL)))
                    .withMessageContaining("would derecognise the wrong amount");

            inventory.writeOff(NewStockWriteOff.unit(
                    first.id(), unitOfFirst, WriteOffReason.DAMAGE, APRIL));
            assertThatExceptionOfType(InvalidStockWriteOffException.class)
                    .isThrownBy(() -> inventory.writeOff(NewStockWriteOff.unit(
                            first.id(), unitOfFirst, WriteOffReason.DAMAGE, APRIL)))
                    .withMessageContaining("not ours to write off");
        }
    }

    @Nested
    @DisplayName("a lot carried at zero derecognises nothing")
    class ZeroCost {

        @Test
        @DisplayName("the stock leaves and no entry is posted")
        void freeSamplesPostNothing() {
            // UnitCost explicitly allows zero — a supplier's free sample is a real lot. Writing it off
            // derecognises nothing because nothing was carried, and a zero-amount entry is refused by
            // the ledger for good reason. The honest record is a write-off with no entry.
            InventoryLotView samples = pooledLot("WOIT-FREE-01", 6L, "0.000000");

            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    samples.id(), Quantity.of(2L), WriteOffReason.SHRINKAGE, APRIL));

            assertThat(written.derecognisedNothing()).isTrue();
            assertThat(written.journalEntry()).isEmpty();
            assertThat(written.postedValue()).isEmpty();
            assertThat(inventory.requireLot(samples.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(4L));
        }

        @Test
        @DisplayName("and reversing it restores the stock, still posting nothing")
        void reversingPostsNothingEither() {
            InventoryLotView samples = pooledLot("WOIT-FREE-02", 6L, "0.000000");
            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    samples.id(), Quantity.of(2L), WriteOffReason.SHRINKAGE, APRIL));

            StockWriteOffView reversal = inventory.reverseWriteOff(
                    written.id(), APRIL.plusDays(1), null);

            assertThat(reversal.journalEntry()).isEmpty();
            assertThat(inventory.requireLot(samples.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(6L));
        }
    }

    @Nested
    @DisplayName("reversal — Q13 applied to something that moved stock as well as money")
    class Reversal {

        @Test
        @DisplayName("the quantity comes back and the mirror entry posts")
        void bothHalvesTogether() {
            InventoryLotView lot = pooledLot("WOIT-REV-01", 10L, "30.000000");
            AccountBalance before = journal.balanceOf(AccountSystemKey.INVENTORY, APRIL.plusDays(5));

            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    lot.id(), Quantity.of(3L), WriteOffReason.SHRINKAGE, APRIL));
            StockWriteOffView reversal = inventory.reverseWriteOff(
                    written.id(), APRIL.plusDays(1), "Miscounted — they were behind the crate");

            assertThat(reversal.isReversal()).isTrue();
            assertThat(reversal.reversalOfWriteOffId()).isEqualTo(written.id());
            assertThat(reversal.reason()).isEqualTo(WriteOffReason.SHRINKAGE);
            assertThat(inventory.requireWriteOff(written.id()).reversedByWriteOffId())
                    .isEqualTo(reversal.id());

            assertThat(inventory.requireLot(lot.id()).quantityRemaining())
                    .isEqualTo(Quantity.of(10L));
            assertThat(journal.balanceOf(AccountSystemKey.INVENTORY, APRIL.plusDays(5))
                    .onNormalSide())
                    .isEqualTo(before.onNormalSide());

            JournalEntryView original = journal.requireEntry(written.journalEntry().orElseThrow());
            assertThat(original.isReversed()).isTrue();
            assertThat(original.reversedByEntryId())
                    .isEqualTo(reversal.journalEntry().orElseThrow());
        }

        @Test
        @DisplayName("a written-off unit comes back on hand")
        void serializedReversal() {
            InventoryLotView lot = inventory.receive(NewInventoryLot.serialized(
                    serialisedProduct("WOIT-REV-02").id(), UnitCost.ofEur("1200.000000"), MARCH,
                    StockLocation.INVENTORY, List.of("WOIT-SN-4001")));
            long unitId = lot.units().getFirst().id();

            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.unit(
                    lot.id(), unitId, WriteOffReason.DAMAGE, APRIL));
            inventory.reverseWriteOff(written.id(), APRIL.plusDays(1), null);

            assertThat(inventory.requireUnit(unitId).status())
                    .isEqualTo(SerializedUnitStatus.IN_STOCK);
        }

        @Test
        @DisplayName("a write-off can be reversed at most once, and a reversal is not itself reversible")
        void reversalIsNotRepeatable() {
            InventoryLotView lot = pooledLot("WOIT-REV-03", 8L, "15.000000");
            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    lot.id(), Quantity.of(2L), WriteOffReason.DAMAGE, APRIL));
            StockWriteOffView reversal = inventory.reverseWriteOff(
                    written.id(), APRIL.plusDays(1), null);

            assertThatExceptionOfType(InvalidStockWriteOffException.class)
                    .isThrownBy(() -> inventory.reverseWriteOff(
                            written.id(), APRIL.plusDays(2), null))
                    .withMessageContaining("already been reversed");
            assertThatExceptionOfType(InvalidStockWriteOffException.class)
                    .isThrownBy(() -> inventory.reverseWriteOff(
                            reversal.id(), APRIL.plusDays(2), null))
                    .withMessageContaining("write the stock off again");
        }

        @Test
        @DisplayName("the ledger refuses to reverse a write-off entry, naming the service that can")
        void theLedgerRefusesToStrandTheStock() {
            // The point of JournalSource.isReversibleThroughTheLedgerAlone(): reversing the money here
            // would credit the loss back while the lot stayed short, and the ledger cannot see that.
            InventoryLotView lot = pooledLot("WOIT-REV-04", 4L, "25.000000");
            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    lot.id(), Quantity.of(1L), WriteOffReason.DAMAGE, APRIL));

            assertThatExceptionOfType(InvalidJournalEntryException.class)
                    .isThrownBy(() -> journal.reverse(
                            written.journalEntry().orElseThrow(), APRIL.plusDays(1), null))
                    .withMessageContaining("InventoryService.reverseWriteOff")
                    .withMessageContaining("the lot quantity it reduced");
        }

        @Test
        @DisplayName("a write-off entry is immutable, so the reason cannot drift from the stock")
        void writeOffEntriesAreImmutable() {
            InventoryLotView lot = pooledLot("WOIT-REV-05", 4L, "25.000000");
            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    lot.id(), Quantity.of(1L), WriteOffReason.DAMAGE, APRIL));

            assertThatExceptionOfType(JournalEntryNotAmendableException.class)
                    .isThrownBy(() -> journal.amend(
                            written.journalEntry().orElseThrow(), APRIL, "Edited",
                            List.of(
                                    NewJournalLine.debit(chart.requireAccount(
                                            AccountSystemKey.INVENTORY_WRITE_OFF).id(),
                                            Money.ofEur("1.00")),
                                    NewJournalLine.credit(chart.requireAccount(
                                            AccountSystemKey.ROUNDING_DIFFERENCES).id(),
                                            Money.ofEur("1.00")))))
                    .withMessageContaining("INVENTORY_WRITE_OFF");
        }
    }

    @Nested
    @DisplayName("reading write-offs")
    class Reading {

        @Test
        @DisplayName("a lot's write-offs include its reversals, so nothing is silently netted away")
        void reversalsAreListedNotHidden() {
            InventoryLotView lot = pooledLot("WOIT-READ-01", 10L, "10.000000");
            StockWriteOffView written = inventory.writeOff(NewStockWriteOff.pooled(
                    lot.id(), Quantity.of(2L), WriteOffReason.EXPIRY, APRIL));
            inventory.reverseWriteOff(written.id(), APRIL.plusDays(1), null);

            // A report that dropped the reversal would show a loss that was corrected as though it
            // stood; netting them is the reader's decision, not this query's.
            assertThat(inventory.writeOffsOf(lot.id())).hasSize(2);
            assertThat(inventory.writeOffsOf(lot.id()))
                    .extracting(StockWriteOffView::isReversal)
                    .containsExactly(false, true);
        }

        @Test
        @DisplayName("write-offs come back by date, with their notes")
        void byDateWithNotes() {
            InventoryLotView lot = pooledLot("WOIT-READ-02", 10L, "10.000000");
            LocalDate day = LocalDate.of(2026, 9, 9);

            inventory.writeOff(NewStockWriteOff
                    .pooled(lot.id(), Quantity.of(1L), WriteOffReason.OTHER, day)
                    .withNote("Given to the roastery next door for a calibration test"));

            assertThat(inventory.writeOffsBetween(day, day))
                    .filteredOn(view -> view.lotId() == lot.id())
                    .singleElement()
                    .satisfies(view -> {
                        assertThat(view.reason()).isEqualTo(WriteOffReason.OTHER);
                        assertThat(view.noteIfAny()).contains(
                                "Given to the roastery next door for a calibration test");
                    });
        }

        @Test
        @DisplayName("a backwards date range is refused rather than answered with nothing")
        void backwardsRangesAreRefused() {
            assertThatThrownBy(() -> inventory.writeOffsBetween(
                    LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("runs backwards");
        }
    }
}
