package gr.novotrade.novocore.core.charge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.charge.ChargeTypeService}.
 */
interface ChargeTypeRepository extends JpaRepository<ChargeType, Long> {

    List<ChargeType> findAllByOrderByNameAsc();

    List<ChargeType> findByActiveTrueOrderByNameAsc();

    Optional<ChargeType> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
