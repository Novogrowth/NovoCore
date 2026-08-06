package gr.novotrade.novocore.core.codification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Core-internal. Reached only through {@code AadePaymentMethodService}. */
interface AadePaymentMethodRepository extends JpaRepository<AadePaymentMethod, Long> {

    /** ⚠️ AADE's own code order, which is the order the annex prints. */
    List<AadePaymentMethod> findAllByOrderByCodeAsc();

    List<AadePaymentMethod> findByActiveTrueOrderByCodeAsc();
}
