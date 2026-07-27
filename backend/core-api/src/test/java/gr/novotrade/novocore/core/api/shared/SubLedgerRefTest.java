package gr.novotrade.novocore.core.api.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SubLedgerRefTest {

    @Test
    @DisplayName("factories produce the matching type")
    void factoriesProduceMatchingType() {
        assertThat(SubLedgerRef.customer(1L).type()).isEqualTo(SubLedgerType.CUSTOMER);
        assertThat(SubLedgerRef.supplier(2L).type()).isEqualTo(SubLedgerType.SUPPLIER);
        assertThat(SubLedgerRef.inventoryLot(3L).type()).isEqualTo(SubLedgerType.INVENTORY_LOT);
        assertThat(SubLedgerRef.asset(4L).type()).isEqualTo(SubLedgerType.ASSET);
    }

    @Test
    @DisplayName("a reference is identified by both type and id, not by id alone")
    void typeAndIdTogetherIdentify() {
        // Ids are per-entity-type sequences, so customer 7 and supplier 7 both exist and are
        // unrelated. Comparing on id alone would silently conflate them.
        assertThat(SubLedgerRef.customer(7L)).isNotEqualTo(SubLedgerRef.supplier(7L));
        assertThat(SubLedgerRef.customer(7L)).isEqualTo(SubLedgerRef.customer(7L));
        assertThat(SubLedgerRef.customer(7L)).hasSameHashCodeAs(SubLedgerRef.customer(7L));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("rejects a non-positive id, which is never a real NovoCore id")
    void rejectsNonPositiveId(long invalidId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SubLedgerRef.customer(invalidId))
                .withMessageContaining("positive");
    }

    @Test
    @DisplayName("rejects a null type")
    void rejectsNullType() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> SubLedgerRef.of(null, 1L));
    }

    @Test
    @DisplayName("toString is readable in a log or a violation message")
    void toStringIsReadable() {
        assertThat(SubLedgerRef.inventoryLot(42L)).hasToString("INVENTORY_LOT#42");
    }

    @Test
    @DisplayName("the four sub-ledger types are exactly those the brief defines")
    void subLedgerTypesMatchTheBrief() {
        // A guard against a fifth type being added without the corresponding decision about
        // which Control account it sits behind. Brief §4 and §5 name four.
        assertThat(SubLedgerType.values()).containsExactly(
                SubLedgerType.CUSTOMER,
                SubLedgerType.SUPPLIER,
                SubLedgerType.INVENTORY_LOT,
                SubLedgerType.ASSET);
    }
}
