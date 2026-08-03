package gr.novotrade.novocore.core.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.document.DeliveryMethodService}.
 */
interface DeliveryMethodRepository extends JpaRepository<DeliveryMethod, Long> {

    List<DeliveryMethod> findAllByOrderByAbbreviationAsc();

    List<DeliveryMethod> findByActiveTrueOrderByAbbreviationAsc();

    boolean existsByAbbreviationIgnoreCase(String abbreviation);
}
