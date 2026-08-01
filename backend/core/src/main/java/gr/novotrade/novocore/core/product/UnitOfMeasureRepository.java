package gr.novotrade.novocore.core.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.product.UnitOfMeasureService}.
 */
interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long>,
        JpaSpecificationExecutor<UnitOfMeasure> {

    List<UnitOfMeasure> findAllByOrderByCodeAsc();

    List<UnitOfMeasure> findByActiveTrueOrderByCodeAsc();

    List<UnitOfMeasure> findByMydataCodeIsNullOrderByCodeAsc();

    Optional<UnitOfMeasure> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByMydataCode(String mydataCode);
}
