package gr.novotrade.novocore.core.settlement;

import gr.novotrade.novocore.core.api.settlement.AllocationSourceType;
import gr.novotrade.novocore.core.api.settlement.PartyType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.settlement.SettlementService}.
 */
interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByPartyTypeAndPartyIdOrderBySettlementDateAscIdAsc(
            PartyType partyType, long partyId);

    List<Settlement> findBySettlementDateBetweenOrderBySettlementDateAscIdAsc(
            LocalDate from, LocalDate to);

    Optional<Settlement> findByJournalEntryId(long journalEntryId);

    List<Settlement> findAllByOrderByIdAsc();

    /**
     * Settlements with money not applied to anything — brief §6's "unmatched lines flagged for
     * Clearing Checks".
     *
     * <p>A query over computed state rather than a stored flag, for the reason nothing here stores an
     * open amount: two numbers that must agree are two numbers that can disagree.
     */
    @Query("""
            SELECT settlement FROM Settlement settlement
            WHERE settlement.amount > COALESCE((
                    SELECT SUM(allocation.amount) FROM OpenItemAllocation allocation
                    WHERE allocation.sourceType = :sourceType
                      AND allocation.sourceId = settlement.id), 0)
            ORDER BY settlement.settlementDate ASC, settlement.id ASC
            """)
    List<Settlement> findWithUnallocatedAmount(AllocationSourceType sourceType);
}
