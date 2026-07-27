package gr.novotrade.novocore.core.account;

import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.account.ChartOfAccountsService}.
 */
interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Every account in chart order, with its group already fetched.
     *
     * <p>The join fetch is the point: without it, reading the group name for each of ~70 accounts
     * is a query per account. The ordering is group position then account position, so the result
     * can be assembled into the chart in a single pass.
     */
    @Query("select a from Account a join fetch a.group g "
            + "order by g.displayOrder asc, a.displayOrder asc, a.id asc")
    List<Account> findAllInChartOrder();

    @Query("select a from Account a join fetch a.group g where a.active "
            + "order by g.displayOrder asc, a.displayOrder asc, a.id asc")
    List<Account> findActiveInChartOrder();

    @Query("select a from Account a join fetch a.group g where a.active and a.kind = :kind "
            + "order by g.displayOrder asc, a.displayOrder asc, a.id asc")
    List<Account> findActiveByKindInChartOrder(AccountKind kind);

    @Query("select a from Account a join fetch a.group g "
            + "where a.active and a.kind = gr.novotrade.novocore.core.api.account.AccountKind.CONTROL "
            + "and a.subLedgerType = :subLedgerType "
            + "order by g.displayOrder asc, a.displayOrder asc, a.id asc")
    List<Account> findActiveControlAccountsFor(SubLedgerType subLedgerType);

    @Query("select a from Account a join fetch a.group g where a.active and a.expectedToClear "
            + "order by g.displayOrder asc, a.displayOrder asc, a.id asc")
    List<Account> findActiveExpectedToClearInChartOrder();

    @Query("select a from Account a join fetch a.group g where a.systemKey = :systemKey")
    Optional<Account> findBySystemKey(AccountSystemKey systemKey);

    List<Account> findByGroupIdOrderByDisplayOrderAscIdAsc(long groupId);

    boolean existsByGroupIdAndNameIgnoreCase(long groupId, String name);

    /** Highest position currently used in a group, so a new account can be appended after it. */
    @Query("select max(a.displayOrder) from Account a where a.group.id = :groupId")
    Optional<Integer> findMaxDisplayOrderInGroup(long groupId);
}
