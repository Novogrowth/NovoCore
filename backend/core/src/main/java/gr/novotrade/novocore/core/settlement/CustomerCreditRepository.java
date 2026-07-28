package gr.novotrade.novocore.core.settlement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.settlement.SettlementService}.
 */
interface CustomerCreditRepository extends JpaRepository<CustomerCredit, Long> {

    List<CustomerCredit> findByCustomerIdOrderByCreditDateAscIdAsc(long customerId);

    Optional<CustomerCredit> findBySettlementId(long settlementId);

    List<CustomerCredit> findAllByOrderByCreditDateAscIdAsc();
}
