package gr.novotrade.novocore.core.product;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.inventory.InventoryService}.
 */
interface StockConsumptionRepository extends JpaRepository<StockConsumption, Long> {

    List<StockConsumption> findByProductIdOrderByIdAsc(long productId);

    List<StockConsumption> findByConsumptionDateBetweenOrderByConsumptionDateAscIdAsc(
            LocalDate from, LocalDate to);

    /** The consumption that reverses this one, if any. Stored one way, queried the other. */
    Optional<StockConsumption> findByReversalOfId(long consumptionId);

    /** Every return recorded against one consumption, oldest first. */
    List<StockConsumption> findByReturnsConsumptionIdOrderByIdAsc(long consumptionId);

    /**
     * How much of a consumption has already come back. Computed, never stored — a third column would
     * be a number that has to agree with the rows it summarises.
     */
    @Query("select coalesce(sum(c.quantityFilled), 0) from StockConsumption c "
            + "where c.returnsConsumptionId = :consumptionId")
    java.math.BigDecimal returnedQuantityOf(long consumptionId);

    /** The batched form, so listing consumptions does not become a query per row. */
    @Query("select c.reversalOfId, c.id from StockConsumption c "
            + "where c.reversalOfId in :consumptionIds")
    List<Object[]> findReversalPairs(Collection<Long> consumptionIds);

    /**
     * Q17's flag: consumptions that drove stock negative and have not been corrected.
     *
     * <p>Reversals are excluded twice over — a reversal row always fills what it restores, and one
     * that has been reversed is no longer outstanding. What is left is the list phase 8's Clearing
     * Checks has to look at.
     */
    @Query("select c from StockConsumption c "
            + "where c.quantityFilled < c.quantityRequested "
            + "  and c.reversalOfId is null "
            + "  and not exists (select 1 from StockConsumption r where r.reversalOfId = c.id) "
            + "order by c.id asc")
    List<StockConsumption> findOutstandingShortfalls();

    /**
     * How much of this product has been consumed but never backed by a lot — the sum of the
     * outstanding shortfalls, which {@code stockOf} subtracts so a product reads negative rather than
     * reading zero (ADR 0008).
     */
    @Query("select coalesce(sum(c.quantityRequested - c.quantityFilled), 0) "
            + "from StockConsumption c "
            + "where c.product.id = :productId "
            + "  and c.reversalOfId is null "
            + "  and not exists (select 1 from StockConsumption r where r.reversalOfId = c.id)")
    java.math.BigDecimal outstandingShortfallOf(long productId);

    /** The same figure for many products at once, so a product list stays one query. */
    @Query("select c.product.id, coalesce(sum(c.quantityRequested - c.quantityFilled), 0) "
            + "from StockConsumption c "
            + "where c.product.id in :productIds "
            + "  and c.reversalOfId is null "
            + "  and not exists (select 1 from StockConsumption r where r.reversalOfId = c.id) "
            + "group by c.product.id")
    List<Object[]> outstandingShortfallsOf(Collection<Long> productIds);
}
