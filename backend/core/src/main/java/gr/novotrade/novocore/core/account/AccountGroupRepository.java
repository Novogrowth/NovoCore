package gr.novotrade.novocore.core.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.account.ChartOfAccountsService}.
 */
interface AccountGroupRepository extends JpaRepository<AccountGroup, Long> {

    List<AccountGroup> findAllByOrderByDisplayOrderAscIdAsc();

    boolean existsByNameIgnoreCase(String name);

    Optional<AccountGroup> findByNameIgnoreCase(String name);

    @Query("select max(g.displayOrder) from AccountGroup g")
    Optional<Integer> findMaxDisplayOrder();
}
