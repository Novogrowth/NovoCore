package gr.novotrade.novocore.core.product;

import gr.novotrade.novocore.core.api.inventory.StockLocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.inventory.InventoryService}, except by
 * {@code ProductServiceImpl}, which shares this slice and reads a last purchase price off it.
 */
interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {

    /**
     * FIFO order: acquisition date, then id as the tie-break within a day. One definition of it, read
     * by every caller that needs lots in order, so step 8's consumption cannot quietly use another.
     */
    List<InventoryLot> findByProductIdOrderByAcquisitionDateAscIdAsc(long productId);

    /** The most recent lot, which is where Q6's last purchase price comes from. */
    Optional<InventoryLot> findFirstByProductIdOrderByAcquisitionDateDescIdDesc(long productId);

    boolean existsByProductId(long productId);

    long countByProductId(long productId);

    List<InventoryLot> findByLocationOrderByAcquisitionDateAscIdAsc(StockLocation location);

    /** Serial-tracked lots, whose location lives on their units rather than on them. */
    List<InventoryLot> findByLocationIsNullOrderByAcquisitionDateAscIdAsc();

    /**
     * Pooled stock on hand, grouped by location. The non-serialized half of the Q7 stock query.
     *
     * <p>Only lots with something left: an exhausted lot contributes nothing and including it would
     * put a zero row in the result for a location that has no stock.
     */
    @Query("""
            SELECT lot.location, SUM(lot.quantityRemaining)
            FROM InventoryLot lot
            WHERE lot.product.id = :productId
              AND lot.quantityRemaining IS NOT NULL
              AND lot.quantityRemaining > 0
            GROUP BY lot.location
            """)
    List<Object[]> sumRemainingByLocation(@Param("productId") long productId);

    /**
     * The latest lot cost for every product that has one, in a single query.
     *
     * <p>{@code DISTINCT ON} is PostgreSQL-specific and deliberate — NovoCore is PostgreSQL-only
     * (ADR 0001, and Flyway owns the schema). The alternative is one query per row of a product list,
     * which is the same N+1 in slower clothing.
     */
    @Query(value = """
            SELECT DISTINCT ON (product_id) product_id, unit_cost, unit_cost_currency
            FROM inventory_lot
            ORDER BY product_id, acquisition_date DESC, id DESC
            """, nativeQuery = true)
    List<Object[]> findLatestLotCostPerProduct();
}
