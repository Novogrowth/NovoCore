package gr.novotrade.novocore.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.inventory.InvalidInventoryLotException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotNotFoundException;
import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewInventoryLot;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitView;
import gr.novotrade.novocore.core.api.inventory.StockLevels;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.StockNotApplicableException;
import gr.novotrade.novocore.core.api.product.InvalidProductException;
import gr.novotrade.novocore.core.api.product.NewProduct;
import gr.novotrade.novocore.core.api.product.ProductService;
import gr.novotrade.novocore.core.api.product.ProductView;
import gr.novotrade.novocore.core.api.product.UnitOfMeasureService;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import gr.novotrade.novocore.core.api.tax.VatClassService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inventory lots, serialized units, locations and the stock queries — against a real PostgreSQL, so
 * the invariants that are CHECK constraints are proven to be constraints and not just Java.
 */
class InventoryIT extends AbstractCoreIntegrationTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 10);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 20);

    @Autowired
    private InventoryService inventory;

    @Autowired
    private ProductService products;

    @Autowired
    private VatClassService vatClasses;

    @Autowired
    private UnitOfMeasureService unitsOfMeasure;

    @Autowired
    private JdbcTemplate jdbc;

    private long standardRateId() {
        return vatClasses.requireByCode("1410").id();
    }

    private long pieceId() {
        return unitsOfMeasure.requireByCode("PIECE").id();
    }

    private long kilogramId() {
        return unitsOfMeasure.requireByCode("KILOGRAM").id();
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

    // ---------------------------------------------------------------------------------------

    @Nested
    @DisplayName("pooled stock")
    class PooledStock {

        @Test
        @DisplayName("a lot round-trips, with its unit cost reassembled at six decimals")
        void receiveAndRead() {
            ProductView product = pooledProduct("InvIT-POOL-01");

            InventoryLotView lot = inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(12L), UnitCost.ofEur("18.456789"), MARCH,
                    StockLocation.INVENTORY));

            assertThat(lot.serialTracked()).isFalse();
            assertThat(lot.quantityReceived()).isEqualTo(Quantity.of(12L));
            assertThat(lot.quantityRemaining()).isEqualTo(Quantity.of(12L));
            assertThat(lot.quantityConsumed()).isEqualTo(Quantity.ZERO);
            assertThat(lot.isOpen()).isTrue();
            assertThat(lot.unitCost()).isEqualTo(UnitCost.ofEur("18.456789"));
            assertThat(lot.locationIfPooled()).contains(StockLocation.INVENTORY);
            assertThat(lot.units()).isEmpty();

            // The remaining stock's value: rounded once, at the end.
            assertThat(lot.remainingValue()).isEqualTo(Money.ofEur("221.48"));
            assertThat(inventory.requireLot(lot.id())).isEqualTo(lot);
        }

        @Test
        @DisplayName("a roast date is recorded for coffee and absent for everything else")
        void roastDate() {
            ProductView beans = coffeeProduct("InvIT-ROAST-01");

            InventoryLotView roasted = inventory.receive(NewInventoryLot
                    .pooled(beans.id(), Quantity.of("6.500"), UnitCost.ofEur("11.20"), MARCH,
                            StockLocation.INVENTORY)
                    .roastedOn(MARCH.minusDays(3)));

            assertThat(roasted.roastDateIfAny()).contains(MARCH.minusDays(3));
            assertThat(inventory.receive(NewInventoryLot.pooled(
                    beans.id(), Quantity.of("1.000"), UnitCost.ofEur("11.20"), MARCH,
                    StockLocation.INVENTORY)).roastDateIfAny()).isEmpty();
        }

        @Test
        @DisplayName("lots come back in FIFO order, oldest first, with id breaking a same-day tie")
        void fifoOrder() {
            ProductView product = pooledProduct("InvIT-FIFO-01");

            InventoryLotView april = inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(5L), UnitCost.ofEur("20.00"), APRIL,
                    StockLocation.INVENTORY));
            InventoryLotView marchFirst = inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(5L), UnitCost.ofEur("18.00"), MARCH,
                    StockLocation.INVENTORY));
            InventoryLotView marchSecond = inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(5L), UnitCost.ofEur("19.00"), MARCH,
                    StockLocation.INVENTORY));

            // Received out of order deliberately: a backdated receipt must still sort first, which is
            // what step 8's consumption will depend on.
            assertThat(inventory.lotsOf(product.id())).extracting(InventoryLotView::id)
                    .containsExactly(marchFirst.id(), marchSecond.id(), april.id());
        }

        @Test
        @DisplayName("a fraction is refused in a unit that does not allow one, and allowed in one that does")
        void fractionalQuantityFollowsTheUnit() {
            // The V11 obligation. The rule is read off the unit rather than from a list kept here.
            ProductView pieces = pooledProduct("InvIT-FRAC-01");
            ProductView beans = coffeeProduct("InvIT-FRAC-02");

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.receive(NewInventoryLot.pooled(
                            pieces.id(), Quantity.of("2.5"), UnitCost.ofEur("10.00"), MARCH,
                            StockLocation.INVENTORY)))
                    .withMessageContaining("has a fraction")
                    .withMessageContaining("PIECE");

            assertThat(inventory.receive(NewInventoryLot.pooled(
                    beans.id(), Quantity.of("0.250"), UnitCost.ofEur("24.00"), MARCH,
                    StockLocation.INVENTORY)).quantityReceived())
                    .isEqualTo(Quantity.of("0.25"));
        }

        @Test
        @DisplayName("a receipt of nothing, or of a negative quantity, is refused")
        void quantityMustBePositive() {
            ProductView product = pooledProduct("InvIT-QTY-01");

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.receive(NewInventoryLot.pooled(
                            product.id(), Quantity.ZERO, UnitCost.ofEur("10.00"), MARCH,
                            StockLocation.INVENTORY)))
                    .withMessageContaining("not positive");

            // And structurally, bypassing the service entirely.
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO inventory_lot (product_id, quantity_received, quantity_remaining,
                                               unit_cost, unit_cost_currency, acquisition_date,
                                               location)
                    VALUES (?, 0, 0, 10, 'EUR', ?, 'INVENTORY')
                    """, product.id(), MARCH))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("inventory_lot_received_positive");
        }

        @Test
        @DisplayName("a lot cannot have more left than arrived, nor less than nothing")
        void remainingIsBounded() {
            ProductView product = pooledProduct("InvIT-BOUND-01");

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO inventory_lot (product_id, quantity_received, quantity_remaining,
                                               unit_cost, unit_cost_currency, acquisition_date,
                                               location)
                    VALUES (?, 5, 6, 10, 'EUR', ?, 'INVENTORY')
                    """, product.id(), MARCH))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("inventory_lot_remaining_within_received");

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO inventory_lot (product_id, quantity_received, quantity_remaining,
                                               unit_cost, unit_cost_currency, acquisition_date,
                                               location)
                    VALUES (?, 5, -1, 10, 'EUR', ?, 'INVENTORY')
                    """, product.id(), MARCH))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("inventory_lot_remaining_within_received");
        }

        @Test
        @DisplayName("a free lot is allowed; a negative unit cost is not")
        void unitCostSign() {
            ProductView product = pooledProduct("InvIT-FREE-01");

            // A supplier's free sample is a real lot, unlike a zero selling price.
            assertThat(inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(3L), UnitCost.zero(Money.EUR), MARCH,
                    StockLocation.INVENTORY)).unitCost().isZero()).isTrue();

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO inventory_lot (product_id, quantity_received, quantity_remaining,
                                               unit_cost, unit_cost_currency, acquisition_date,
                                               location)
                    VALUES (?, 1, 1, -5, 'EUR', ?, 'INVENTORY')
                    """, product.id(), MARCH))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("inventory_lot_unit_cost_not_negative");
        }
    }

    @Nested
    @DisplayName("the two shapes of lot, and no third one")
    class LotShapes {

        @Test
        @DisplayName("a pooled lot with no location, or a quantity with no location, is refused")
        void pooledColumnsGoTogether() {
            // One constraint stating one rule: a lot either holds pooled quantity at a location or
            // holds units that carry both. Anything else is a lot nothing can count correctly.
            ProductView product = pooledProduct("InvIT-SHAPE-01");

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO inventory_lot (product_id, quantity_received, quantity_remaining,
                                               unit_cost, unit_cost_currency, acquisition_date)
                    VALUES (?, 5, 5, 10, 'EUR', ?)
                    """, product.id(), MARCH))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("inventory_lot_pooled_columns_go_together");

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO inventory_lot (product_id, unit_cost, unit_cost_currency,
                                               acquisition_date, location)
                    VALUES (?, 10, 'EUR', ?, 'INVENTORY')
                    """, product.id(), MARCH))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("inventory_lot_pooled_columns_go_together");
        }

        @Test
        @DisplayName("a serialized receipt against a pooled product is refused, and the reverse too")
        void shapeMustMatchTheProduct() {
            ProductView pooled = pooledProduct("InvIT-SHAPE-02");
            ProductView machine = serialisedProduct("InvIT-SHAPE-03");

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.receive(NewInventoryLot.serialized(
                            pooled.id(), UnitCost.ofEur("10.00"), MARCH, StockLocation.INVENTORY,
                            List.of("SHOULD-NOT-EXIST"))))
                    .withMessageContaining("pooled stock");

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.receive(NewInventoryLot.pooled(
                            machine.id(), Quantity.of(2L), UnitCost.ofEur("1800.00"), MARCH,
                            StockLocation.INVENTORY)))
                    .withMessageContaining("one serial number per unit");
        }

        @Test
        @DisplayName("a request cannot state both a quantity and serial numbers")
        void quantityAndSerialsAreMutuallyExclusive() {
            // Refused by the request type itself, before any product is loaded: two numbers that can
            // disagree, and no rule for which one wins.
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new NewInventoryLot(1L, Quantity.of(5L),
                            UnitCost.ofEur("10.00"), MARCH, null, StockLocation.INVENTORY,
                            List.of("A", "B", "C", "D"), null))
                    .withMessageContaining("the unit count is the quantity");

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> new NewInventoryLot(1L, null, UnitCost.ofEur("10.00"), MARCH,
                            null, StockLocation.INVENTORY, List.of(), null))
                    .withMessageContaining("either a quantity");
        }
    }

    @Nested
    @DisplayName("serialized stock")
    class SerializedStock {

        @Test
        @DisplayName("a lot and its units are created together, and the unit count is the quantity")
        void receiveSerialized() {
            ProductView machine = serialisedProduct("InvIT-SER-01");

            InventoryLotView lot = inventory.receive(NewInventoryLot.serialized(
                    machine.id(), UnitCost.ofEur("1800.00"), MARCH, StockLocation.INVENTORY,
                    List.of("SN-003", "SN-001", "SN-002")));

            assertThat(lot.serialTracked()).isTrue();
            assertThat(lot.quantityReceived()).isEqualTo(Quantity.of(3L));
            assertThat(lot.quantityRemaining()).isEqualTo(Quantity.of(3L));
            // No location on the lot itself — the units carry it.
            assertThat(lot.locationIfPooled()).isEmpty();
            assertThat(lot.units()).extracting(SerializedUnitView::serialNumber)
                    .containsExactly("SN-001", "SN-002", "SN-003");
            assertThat(lot.units()).allSatisfy(unit -> {
                assertThat(unit.status()).isEqualTo(SerializedUnitStatus.IN_STOCK);
                assertThat(unit.location()).isEqualTo(StockLocation.INVENTORY);
                // Each unit carries its own actual cost, which is its lot's (brief §5) — no FIFO.
                assertThat(unit.unitCost()).isEqualTo(UnitCost.ofEur("1800.00"));
                assertThat(unit.isSellable()).isTrue();
            });

            // The quantity columns are genuinely absent, not merely zero: the count is the only copy.
            assertThat(jdbc.queryForObject("""
                    SELECT quantity_received IS NULL AND quantity_remaining IS NULL
                           AND location IS NULL
                    FROM inventory_lot WHERE id = ?
                    """, Boolean.class, lot.id()))
                    .as("a serial-tracked lot stores no quantity and no location of its own")
                    .isTrue();
        }

        @Test
        @DisplayName("a duplicate serial is refused within a receipt and across all stock")
        void serialNumbersAreUnique() {
            ProductView machine = serialisedProduct("InvIT-SER-02");

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.receive(NewInventoryLot.serialized(
                            machine.id(), UnitCost.ofEur("1800.00"), MARCH, StockLocation.INVENTORY,
                            List.of("InvIT-DUP-1", "invit-dup-1"))))
                    .withMessageContaining("appears twice");

            inventory.receive(NewInventoryLot.serialized(
                    machine.id(), UnitCost.ofEur("1800.00"), MARCH, StockLocation.INVENTORY,
                    List.of("InvIT-SN-EXISTING")));

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.receive(NewInventoryLot.serialized(
                            machine.id(), UnitCost.ofEur("1900.00"), APRIL, StockLocation.INVENTORY,
                            List.of("invit-sn-existing"))))
                    .withMessageContaining("already held by another unit");
        }

        @Test
        @DisplayName("a unit is findable by serial number, and a blank scan matches nothing")
        void findBySerialNumber() {
            ProductView machine = serialisedProduct("InvIT-SER-03");
            inventory.receive(NewInventoryLot.serialized(
                    machine.id(), UnitCost.ofEur("2100.00"), MARCH, StockLocation.INVENTORY,
                    List.of("InvIT-FIND-77")));

            assertThat(inventory.findUnitBySerialNumber("invit-find-77")).isPresent();
            assertThat(inventory.findUnitBySerialNumber("InvIT-NOT-A-SERIAL")).isEmpty();
            // A misread must not resolve to whichever unit happens to be first.
            assertThat(inventory.findUnitBySerialNumber(null)).isEmpty();
            assertThat(inventory.findUnitBySerialNumber("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("stock levels — Q7's answer")
    class StockLevelQueries {

        @Test
        @DisplayName("pooled stock is reported per location, with sellable being Inventory only")
        void pooledStockPerLocation() {
            ProductView product = pooledProduct("InvIT-STOCK-01");

            InventoryLotView shelf = inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(10L), UnitCost.ofEur("15.00"), MARCH,
                    StockLocation.INVENTORY));
            inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(4L), UnitCost.ofEur("15.00"), MARCH,
                    StockLocation.INVENTORY));
            inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(3L), UnitCost.ofEur("15.00"), MARCH,
                    StockLocation.DAMAGED_GOODS));

            StockLevels levels = inventory.stockOf(product.id());

            assertThat(levels.at(StockLocation.INVENTORY)).isEqualTo(Quantity.of(14L));
            assertThat(levels.at(StockLocation.DAMAGED_GOODS)).isEqualTo(Quantity.of(3L));
            assertThat(levels.at(StockLocation.SERVICE)).isEqualTo(Quantity.ZERO);
            assertThat(levels.total()).isEqualTo(Quantity.of(17L));
            assertThat(levels.sellable())
                    .as("Q7: Inventory only, excluding Damaged Goods and Service")
                    .isEqualTo(Quantity.of(14L));
            assertThat(inventory.sellableStockOf(product.id())).isEqualTo(Quantity.of(14L));

            // Moving to Damaged Goods reduces sellable stock and not total stock — because the move
            // posts nothing and the goods are still an asset at cost (the step 3 decision).
            inventory.moveLot(shelf.id(), StockLocation.DAMAGED_GOODS);

            StockLevels afterMove = inventory.stockOf(product.id());
            assertThat(afterMove.sellable()).isEqualTo(Quantity.of(4L));
            assertThat(afterMove.total()).isEqualTo(Quantity.of(17L));
        }

        @Test
        @DisplayName("serialized stock is counted per unit, so one machine can be out for repair")
        void serializedStockPerLocation() {
            ProductView machine = serialisedProduct("InvIT-STOCK-02");
            InventoryLotView lot = inventory.receive(NewInventoryLot.serialized(
                    machine.id(), UnitCost.ofEur("1800.00"), MARCH, StockLocation.INVENTORY,
                    List.of("InvIT-M-1", "InvIT-M-2", "InvIT-M-3")));

            long unitId = inventory.unitsOf(lot.id()).getFirst().id();
            inventory.moveUnit(unitId, StockLocation.SERVICE);

            StockLevels levels = inventory.stockOf(machine.id());

            // The whole reason a serial-tracked lot has no location of its own: its lot-mates stayed.
            assertThat(levels.at(StockLocation.INVENTORY)).isEqualTo(Quantity.of(2L));
            assertThat(levels.at(StockLocation.SERVICE)).isEqualTo(Quantity.of(1L));
            assertThat(levels.sellable()).isEqualTo(Quantity.of(2L));
            assertThat(inventory.unitsAt(StockLocation.SERVICE))
                    .extracting(SerializedUnitView::serialNumber)
                    .contains("InvIT-M-1");
        }

        @Test
        @DisplayName("fractional stock adds up without drifting")
        void fractionalStock() {
            ProductView beans = coffeeProduct("InvIT-STOCK-03");
            inventory.receive(NewInventoryLot.pooled(
                    beans.id(), Quantity.of("6.750"), UnitCost.ofEur("11.20"), MARCH,
                    StockLocation.INVENTORY));
            inventory.receive(NewInventoryLot.pooled(
                    beans.id(), Quantity.of("0.250"), UnitCost.ofEur("11.20"), APRIL,
                    StockLocation.INVENTORY));

            assertThat(inventory.sellableStockOf(beans.id())).isEqualTo(Quantity.of(7L));
        }

        @Test
        @DisplayName("a product with no lots has nothing, which is not the same as having no stock concept")
        void noLotsYet() {
            ProductView product = pooledProduct("InvIT-STOCK-04");

            StockLevels levels = inventory.stockOf(product.id());

            assertThat(levels.isEmpty()).isTrue();
            assertThat(levels.sellable()).isEqualTo(Quantity.ZERO);
        }

        @Test
        @DisplayName("a service refuses to answer, rather than answering zero")
        void serviceHasNoStock() {
            // The step 5 obligation, discharged. Zero and "not applicable" look identical on a screen,
            // and zero would put a repair service into a back-in-stock reminder.
            ProductView repair = products.create(NewProduct.service(
                    "InvIT-SVC-01", "InvIT machine service", pieceId(), standardRateId(),
                    Money.ofEur("60.00")));

            assertThat(repair.isStocked()).isFalse();
            assertThatExceptionOfType(StockNotApplicableException.class)
                    .isThrownBy(() -> inventory.stockOf(repair.id()))
                    .withMessageContaining("is a service, so it has no stock")
                    .withMessageContaining("back-in-stock");

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.receive(NewInventoryLot.pooled(
                            repair.id(), Quantity.of(1L), UnitCost.ofEur("1.00"), MARCH,
                            StockLocation.INVENTORY)))
                    .withMessageContaining("no inventory lots");
        }

        @Test
        @DisplayName("a batch read skips products with no stock concept rather than reporting zero")
        void batchSkipsUnstockedProducts() {
            ProductView goods = pooledProduct("InvIT-BATCH-01");
            ProductView repair = products.create(NewProduct.service(
                    "InvIT-BATCH-02", "InvIT batch service", pieceId(), standardRateId(), null));
            inventory.receive(NewInventoryLot.pooled(
                    goods.id(), Quantity.of(2L), UnitCost.ofEur("15.00"), MARCH,
                    StockLocation.INVENTORY));

            List<StockLevels> levels = inventory.stockOfAll(List.of(goods.id(), repair.id()));

            assertThat(levels).extracting(StockLevels::productId).containsExactly(goods.id());
            assertThat(inventory.stockOfAll(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("moving stock — posts nothing, by the step 3 decision")
    class Moves {

        @Test
        @DisplayName("a serial-tracked lot refuses a lot-level move and names the alternative")
        void serialTrackedLotCannotMoveWholesale() {
            ProductView machine = serialisedProduct("InvIT-MOVE-01");
            InventoryLotView lot = inventory.receive(NewInventoryLot.serialized(
                    machine.id(), UnitCost.ofEur("1800.00"), MARCH, StockLocation.INVENTORY,
                    List.of("InvIT-MV-1", "InvIT-MV-2")));

            assertThatExceptionOfType(InvalidInventoryLotException.class)
                    .isThrownBy(() -> inventory.moveLot(lot.id(), StockLocation.SERVICE))
                    .withMessageContaining("move its units instead");
        }

        @Test
        @DisplayName("moving to the same location changes nothing and does not fail")
        void movingNowhereIsHarmless() {
            ProductView product = pooledProduct("InvIT-MOVE-02");
            InventoryLotView lot = inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(2L), UnitCost.ofEur("15.00"), MARCH,
                    StockLocation.INVENTORY));

            assertThat(inventory.moveLot(lot.id(), StockLocation.INVENTORY).locationIfPooled())
                    .contains(StockLocation.INVENTORY);
        }

        @Test
        @DisplayName("lotsAt is what the phase 8 Damaged Goods check reads, for both shapes")
        void lotsAtALocation() {
            // The step 3 obligation's compensating control needs this query to exist and to cover
            // serial-tracked lots too, which have no location of their own.
            ProductView pooled = pooledProduct("InvIT-DAMAGED-01");
            ProductView machine = serialisedProduct("InvIT-DAMAGED-02");

            InventoryLotView pooledLot = inventory.receive(NewInventoryLot.pooled(
                    pooled.id(), Quantity.of(2L), UnitCost.ofEur("15.00"), MARCH,
                    StockLocation.DAMAGED_GOODS));
            InventoryLotView serialLot = inventory.receive(NewInventoryLot.serialized(
                    machine.id(), UnitCost.ofEur("1800.00"), MARCH, StockLocation.INVENTORY,
                    List.of("InvIT-DMG-1")));
            inventory.moveUnit(
                    inventory.unitsOf(serialLot.id()).getFirst().id(), StockLocation.DAMAGED_GOODS);

            assertThat(inventory.lotsAt(StockLocation.DAMAGED_GOODS))
                    .extracting(InventoryLotView::id)
                    .contains(pooledLot.id(), serialLot.id());
        }

        @Test
        @DisplayName("a missing lot or unit names what was asked for")
        void missingLot() {
            assertThatExceptionOfType(InventoryLotNotFoundException.class)
                    .isThrownBy(() -> inventory.requireLot(999_999L))
                    .withMessageContaining("999999");
        }
    }

    @Nested
    @DisplayName("what a lot's existence now forbids on its product")
    class ProductGuards {

        @Test
        @DisplayName("the unit of measure cannot change once stock exists")
        void unitOfMeasureIsFrozenByStock() {
            // The step 5 obligation, discharged: reinterpreting 12 pieces as 12 kilograms is a different
            // quantity, not a units change.
            ProductView product = pooledProduct("InvIT-GUARD-01");
            inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(12L), UnitCost.ofEur("15.00"), MARCH,
                    StockLocation.INVENTORY));

            assertThatExceptionOfType(InvalidProductException.class)
                    .isThrownBy(() -> products.changeUnitOfMeasure(product.id(), kilogramId()))
                    .withMessageContaining("has inventory lots");
        }

        @Test
        @DisplayName("serial tracking cannot change once stock exists, but can before")
        void serialTrackingIsFrozenByStock() {
            ProductView product = pooledProduct("InvIT-GUARD-02");

            // The window this method exists for: the mistake is correctable until stock arrives.
            assertThat(products.changeSerialTracking(product.id(), true).isSerialTracked()).isTrue();
            assertThat(products.changeSerialTracking(product.id(), false).isSerialTracked()).isFalse();

            inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(4L), UnitCost.ofEur("15.00"), MARCH,
                    StockLocation.INVENTORY));

            assertThatExceptionOfType(InvalidProductException.class)
                    .isThrownBy(() -> products.changeSerialTracking(product.id(), true))
                    .withMessageContaining("no serial numbers to recover");
        }

        @Test
        @DisplayName("the last purchase price is computed from the most recent lot (Q6)")
        void lastPurchasePriceComesFromTheLatestLot() {
            ProductView product = pooledProduct("InvIT-LASTPRICE-01");
            assertThat(products.require(product.id()).lastPurchasePriceIfAny()).isEmpty();

            inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(5L), UnitCost.ofEur("18.000000"), MARCH,
                    StockLocation.INVENTORY));
            inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(5L), UnitCost.ofEur("19.250000"), APRIL,
                    StockLocation.INVENTORY));
            // Backdated, so it must not win: "latest" is by acquisition date, not by insertion order.
            inventory.receive(NewInventoryLot.pooled(
                    product.id(), Quantity.of(5L), UnitCost.ofEur("11.000000"),
                    MARCH.minusMonths(1), StockLocation.INVENTORY));

            assertThat(products.require(product.id()).lastPurchasePriceIfAny())
                    .contains(UnitCost.ofEur("19.250000"));
            assertThat(inventory.lastPurchaseCostOf(product.id()))
                    .contains(UnitCost.ofEur("19.250000"));

            // And the batched read agrees with the single one — different queries, same answer.
            assertThat(products.all()).filteredOn(view -> view.id() == product.id())
                    .singleElement()
                    .satisfies(view -> assertThat(view.lastPurchasePriceIfAny())
                            .contains(UnitCost.ofEur("19.250000")));
        }
    }
}
