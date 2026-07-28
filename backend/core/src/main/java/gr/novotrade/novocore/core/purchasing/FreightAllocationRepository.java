package gr.novotrade.novocore.core.purchasing;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.purchasing.FreightAllocationService}.
 */
interface FreightAllocationRepository extends JpaRepository<FreightAllocation, Long> {

    List<FreightAllocation> findBySourceLineIdOrderByIdAsc(long purchaseInvoiceLineId);

    List<FreightAllocation> findByAllocationDateBetweenOrderByAllocationDateAscIdAsc(
            LocalDate from, LocalDate to);

    /** The document that reverses this one, if any. Stored one way, queried the other. */
    Optional<FreightAllocation> findByReversalOfId(long allocationId);

    /** The batched form, so listing allocations does not become a query per row. */
    @Query("select a.reversalOfId, a.id from FreightAllocation a where a.reversalOfId in :ids")
    List<Object[]> findReversalPairs(Collection<Long> ids);

    /**
     * Every allocation that put a share into one lot, oldest first — one lot's landed-cost history.
     *
     * <p>An allocation that has since been reversed still appears, for the reason
     * {@code writeOffsBetween} gives: it is part of what happened to the lot, and netting it out is
     * the reader's decision rather than this query's. Its reversing document does not appear in its
     * own right, because a reversal carries no lines — the view says so through
     * {@code reversedByAllocationId} instead.
     */
    @Query("select distinct a from FreightAllocation a join a.lines l "
            + "where l.lotId = :lotId "
            + "order by a.allocationDate asc, a.id asc")
    List<FreightAllocation> findTouchingLot(long lotId);
}
