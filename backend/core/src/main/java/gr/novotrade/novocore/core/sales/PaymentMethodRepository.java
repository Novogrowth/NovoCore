package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.api.sales.SettlementMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.sales.PaymentMethodService}.
 */
interface PaymentMethodRepository extends JpaRepository<PaymentMethod, SettlementMethod> {

    /** ⚠️ Sort code, not enum declaration order — that is what the column is for. */
    List<PaymentMethod> findAllByOrderBySortCodeAsc();

    List<PaymentMethod> findByActiveTrueOrderBySortCodeAsc();

    boolean existsBySortCode(int sortCode);
}
