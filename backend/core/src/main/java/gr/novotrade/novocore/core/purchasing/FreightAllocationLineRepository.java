package gr.novotrade.novocore.core.purchasing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through the purchasing services.
 */
interface FreightAllocationLineRepository extends JpaRepository<FreightAllocationLine, Long> {

    List<FreightAllocationLine> findByAllocationIdOrderByLineNumberAsc(long allocationId);

    /**
     * How much of one freight line has been allocated by documents that <em>stand</em>.
     *
     * <p>Reversal documents carry no lines, so they contribute nothing directly; what matters is the
     * second condition, which drops an allocation once something reverses it. That is what makes
     * reversal a real correction rather than a one-way door — the amount goes back into the line's
     * unallocated remainder and can be allocated again onto the lots it really belonged to.
     *
     * <p>The share is summed as its two posted halves, because the share itself is not a column.
     */
    @Query("select coalesce(sum(l.capitalisedAmount + l.varianceAmount), 0) "
            + "from FreightAllocationLine l "
            + "where l.allocation.sourceLine.id = :purchaseInvoiceLineId "
            + "  and l.allocation.reversalOfId is null "
            + "  and not exists (select 1 from FreightAllocation r "
            + "                  where r.reversalOfId = l.allocation.id)")
    BigDecimal allocatedAgainstLine(long purchaseInvoiceLineId);
}
