package gr.novotrade.novocore.core.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Q7's answer, as pure logic: stock per location, plus a computed sellable figure that is the Inventory
 * location only.
 */
class StockLevelsTest {

    @Nested
    @DisplayName("sellable is Inventory only (Q7)")
    class Sellability {

        @Test
        @DisplayName("stock at Damaged Goods and Service is on hand and not sellable")
        void onlyInventoryIsSellable() {
            // The case that makes a single stock number wrong. Nine on hand, three you may sell.
            StockLevels levels = new StockLevels(1L, Map.of(
                    StockLocation.INVENTORY, Quantity.of(3L),
                    StockLocation.SERVICE, Quantity.of(2L),
                    StockLocation.DAMAGED_GOODS, Quantity.of(4L)));

            assertThat(levels.sellable()).isEqualTo(Quantity.of(3L));
            assertThat(levels.total()).isEqualTo(Quantity.of(9L));
            assertThat(levels.hasSellableStock()).isTrue();
        }

        @Test
        @DisplayName("stock sitting entirely in Damaged Goods is not sellable and not absent")
        void unsellableIsNotTheSameAsEmpty() {
            // The distinction a back-in-stock reminder depends on: there is nothing to sell, and
            // nothing is going to arrive either, because the stock is already here and broken.
            StockLevels levels = new StockLevels(1L, Map.of(
                    StockLocation.DAMAGED_GOODS, Quantity.of(4L)));

            assertThat(levels.hasSellableStock()).isFalse();
            assertThat(levels.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("the sellable set is derived from the flag, not from a hardcoded location")
        void sellableSetComesFromTheEnum() {
            assertThat(StockLocation.sellableLocations())
                    .containsExactly(StockLocation.INVENTORY);
            assertThat(StockLocation.INVENTORY.isSellable()).isTrue();
            assertThat(StockLocation.SERVICE.isSellable()).isFalse();
            assertThat(StockLocation.DAMAGED_GOODS.isSellable()).isFalse();
        }
    }

    @Nested
    @DisplayName("every location is present, so a caller never sees a missing key")
    class Completeness {

        @Test
        @DisplayName("locations with no stock read as zero")
        void missingLocationsAreZero() {
            StockLevels levels = new StockLevels(1L, Map.of(
                    StockLocation.INVENTORY, Quantity.of(5L)));

            assertThat(levels.byLocation()).hasSize(StockLocation.values().length);
            assertThat(levels.at(StockLocation.DAMAGED_GOODS)).isEqualTo(Quantity.ZERO);
        }

        @Test
        @DisplayName("nothing anywhere is empty, and its sellable figure is zero rather than absent")
        void emptyLevels() {
            StockLevels levels = StockLevels.empty(1L);

            assertThat(levels.isEmpty()).isTrue();
            assertThat(levels.sellable()).isEqualTo(Quantity.ZERO);
            assertThat(levels.total()).isEqualTo(Quantity.ZERO);
        }

        @Test
        @DisplayName("the map is unmodifiable, so a projection cannot be edited after the fact")
        void mapIsUnmodifiable() {
            StockLevels levels = StockLevels.empty(1L);

            assertThat(levels.byLocation().getClass().getName())
                    .as("wrapped on construction, like ProductView.hiddenFields")
                    .contains("Unmodifiable");
        }
    }

    @Nested
    @DisplayName("fractional quantities, because coffee sells by weight")
    class FractionalStock {

        @Test
        @DisplayName("weights add up at six decimals without drifting")
        void fractionalTotals() {
            StockLevels levels = new StockLevels(1L, Map.of(
                    StockLocation.INVENTORY, Quantity.of("2.750000"),
                    StockLocation.SERVICE, Quantity.of("0.250000")));

            assertThat(levels.total()).isEqualTo(Quantity.of(3L));
            assertThat(levels.sellable()).isEqualTo(Quantity.of("2.75"));
        }
    }

    @Test
    @DisplayName("Q25's write-off reasons are a fixed, reportable set")
    void writeOffReasonsAreFixed() {
        // An enum rather than free text precisely so the shrinkage-versus-expiry question is
        // answerable. A fifth value arriving should be a deliberate change, which is what this asserts.
        assertThat(WriteOffReason.values()).containsExactly(
                WriteOffReason.SHRINKAGE,
                WriteOffReason.DAMAGE,
                WriteOffReason.EXPIRY,
                WriteOffReason.OTHER);
    }

    @Test
    @DisplayName("only IN_STOCK counts towards stock on hand")
    void onlyInStockCounts() {
        assertThat(SerializedUnitStatus.IN_STOCK.isOnHand()).isTrue();
        assertThat(SerializedUnitStatus.SOLD.isOnHand()).isFalse();
        assertThat(SerializedUnitStatus.WRITTEN_OFF.isOnHand()).isFalse();
    }
}
