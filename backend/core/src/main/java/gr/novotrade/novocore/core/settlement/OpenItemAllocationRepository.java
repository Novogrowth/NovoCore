package gr.novotrade.novocore.core.settlement;

import gr.novotrade.novocore.core.api.settlement.AllocationSourceType;
import gr.novotrade.novocore.core.api.settlement.OpenItemType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.settlement.SettlementService}.
 */
interface OpenItemAllocationRepository extends JpaRepository<OpenItemAllocation, Long> {

    List<OpenItemAllocation> findBySourceTypeAndSourceIdOrderByAllocationOrderAsc(
            AllocationSourceType sourceType, long sourceId);

    List<OpenItemAllocation> findByTargetTypeAndTargetIdOrderByAllocationOrderAsc(
            OpenItemType targetType, long targetId);

    /** What has been applied to one document. The other half of its computed open amount. */
    @Query("""
            SELECT COALESCE(SUM(allocation.amount), 0) FROM OpenItemAllocation allocation
            WHERE allocation.targetType = :targetType AND allocation.targetId = :targetId
            """)
    BigDecimal allocatedAgainst(OpenItemType targetType, long targetId);

    /** What has been applied out of one source. The other half of its unallocated amount. */
    @Query("""
            SELECT COALESCE(SUM(allocation.amount), 0) FROM OpenItemAllocation allocation
            WHERE allocation.sourceType = :sourceType AND allocation.sourceId = :sourceId
            """)
    BigDecimal allocatedFrom(AllocationSourceType sourceType, long sourceId);
}
