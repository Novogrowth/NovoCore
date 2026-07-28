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
interface StockWriteOffRepository extends JpaRepository<StockWriteOff, Long> {

    List<StockWriteOff> findByLotIdOrderByIdAsc(long lotId);

    List<StockWriteOff> findByWriteOffDateBetweenOrderByWriteOffDateAscIdAsc(
            LocalDate from, LocalDate to);

    /** The write-off that reverses this one, if any. Stored one way, queried the other. */
    Optional<StockWriteOff> findByReversalOfId(long writeOffId);

    /** The batched form, so listing write-offs does not become a query per row. */
    @Query("select w.reversalOfId, w.id from StockWriteOff w where w.reversalOfId in :writeOffIds")
    List<Object[]> findReversalPairs(Collection<Long> writeOffIds);
}
