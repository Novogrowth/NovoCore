package gr.novotrade.novocore.core.tax;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.tax.VatExemptionReasonService}.
 */
interface VatExemptionReasonRepository extends JpaRepository<VatExemptionReason, Long> {

    List<VatExemptionReason> findAllByOrderByCodeAsc();

    List<VatExemptionReason> findByActiveTrueOrderByCodeAsc();

    Optional<VatExemptionReason> findByCode(int code);

    boolean existsByCode(int code);

    boolean existsByMydataCode(String mydataCode);
}
