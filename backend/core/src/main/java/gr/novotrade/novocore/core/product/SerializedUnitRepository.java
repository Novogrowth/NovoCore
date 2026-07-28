package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.inventory.SerializedUnitStatus;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.inventory.InventoryService}.
 */
interface SerializedUnitRepository extends JpaRepository<SerializedUnit, Long> {

    List<SerializedUnit> findByLotIdOrderBySerialNumberAsc(long lotId);

    List<SerializedUnit> findByLotProductIdOrderBySerialNumberAsc(long productId);

    /**
     * The unit holding this serial number.
     *
     * <p>Excludes {@code UNRECEIVED} units, and that is what keeps this {@code Optional} rather than a
     * list: since V16 a reversed delivery releases its serial numbers, so the same string can appear on
     * a unit that was never really ours <em>and</em> on the one that genuinely is. The partial unique
     * index guarantees at most one of the latter.
     */
    Optional<SerializedUnit> findBySerialNumberIgnoreCaseAndStatusNot(
            String serialNumber, SerializedUnitStatus excluded);

    boolean existsBySerialNumberIgnoreCaseAndStatusNot(
            String serialNumber, SerializedUnitStatus excluded);

    List<SerializedUnit> findByStatusAndLocationOrderBySerialNumberAsc(
            SerializedUnitStatus status, StockLocation location);

    /**
     * Units on hand, grouped by location. The serialized half of the Q7 stock query — a count rather
     * than a sum, because for serialized stock the quantity <em>is</em> the number of units.
     */
    @Query("""
            SELECT unit.location, COUNT(unit)
            FROM SerializedUnit unit
            WHERE unit.lot.product.id = :productId
              AND unit.status = :status
            GROUP BY unit.location
            """)
    List<Object[]> countOnHandByLocation(
            @Param("productId") long productId, @Param("status") SerializedUnitStatus status);
}
