package gr.novotrade.novocore.core.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.product.UnitOfMeasureService}.
 */
interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {

    List<UnitOfMeasure> findAllByOrderByCodeAsc();

    List<UnitOfMeasure> findByActiveTrueOrderByCodeAsc();

    List<UnitOfMeasure> findByMydataCodeIsNullOrderByCodeAsc();

    Optional<UnitOfMeasure> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByMydataCode(String mydataCode);
}
