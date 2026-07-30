package gr.novotrade.novocore.core.ledger;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through {@link gr.novotrade.novocore.core.api.ledger.JournalService}.
 *
 * <p><strong>No delete method, and none inherited that is safe to call.</strong> {@code JpaRepository}
 * declares several; the database refuses every one of them by trigger, which is the guarantee rather
 * than the absence of a call site.
 */
interface JournalEntryRepository
        extends JpaRepository<JournalEntry, Long>, JpaSpecificationExecutor<JournalEntry> {

    /** One entry with its lines already fetched, so a projection is a single query. */
    @Query("select e from JournalEntry e left join fetch e.lines where e.id = :id")
    Optional<JournalEntry> findByIdWithLines(long id);

    /** Several entries with their lines, oldest first — the batched form of {@link #findByIdWithLines}. */
    @Query("select e from JournalEntry e left join fetch e.lines "
            + "where e.id in :ids order by e.entryDate asc, e.id asc")
    List<JournalEntry> findAllByIdWithLines(Collection<Long> ids);

    /**
     * Entries in an accounting-date range with their lines, oldest first.
     *
     * <p>Ordered by {@code entryDate} then {@code id} — the same shape as the FIFO ordering on lots: the
     * business date first, the surrogate key only to break ties within a day, so the order is stable and
     * does not depend on when a backdated entry happened to be typed in.
     */
    @Query("select e from JournalEntry e left join fetch e.lines "
            + "where e.entryDate between :from and :to order by e.entryDate asc, e.id asc")
    List<JournalEntry> findBetweenWithLines(LocalDate from, LocalDate to);

    /**
     * The entry that reverses this one, if any.
     *
     * <p>This is how {@code JournalEntryView.reversedByEntryId} is answered. Stored one way and queried
     * the other, so the two cannot disagree.
     */
    Optional<JournalEntry> findByReversalOfId(long reversedEntryId);

    /** The batched form, so listing entries does not become a query per entry. */
    @Query("select e.reversalOfId, e.id from JournalEntry e where e.reversalOfId in :reversedEntryIds")
    List<Object[]> findReversalPairs(Collection<Long> reversedEntryIds);

    /**
     * Per-entry totals for one page: entry id, debit total, line count, currency.
     *
     * <p>One aggregate query for the whole page rather than one per entry — the same batching the
     * reversal-pair query does, and for the same reason.
     *
     * <p><strong>Debits only, because debits equal credits.</strong> Rule 6 is enforced by a deferred
     * constraint trigger, so summing one side is summing both; summing each side separately would
     * produce two figures whose only possible relationship is equality and invite somebody to compare
     * them.
     *
     * <p>{@code min(l.amountCurrency)} rather than a group-by on it: {@code post} refuses an entry
     * whose lines span two currencies, so an entry has exactly one and the aggregate picks it without
     * splitting the row. If that validation were ever removed this would report one of the two rather
     * than failing — which is why the validation is the thing that guarantees it, stated here so the
     * dependency is visible.
     */
    @Query("""
            select l.entry.id,
                   sum(case when l.side = gr.novotrade.novocore.core.api.account.BalanceSide.DEBIT
                            then l.amount else 0 end),
                   count(l),
                   min(l.amountCurrency)
            from JournalLine l
            where l.entry.id in :entryIds
            group by l.entry.id
            """)
    List<Object[]> summariseLines(Collection<Long> entryIds);
}
