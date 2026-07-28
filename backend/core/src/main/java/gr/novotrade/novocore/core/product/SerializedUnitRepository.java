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

    Optional<SerializedUnit> findBySerialNumberIgnoreCase(String serialNumber);

    boolean existsBySerialNumberIgnoreCase(String serialNumber);

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
