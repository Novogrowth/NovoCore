package gr.novotrade.novocore.core.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.document.SalesDocumentSeriesService}.
 */
interface SalesDocumentSeriesRepository extends JpaRepository<SalesDocumentSeries, Long> {

    List<SalesDocumentSeries> findAllByOrderByAbbreviationAsc();

    List<SalesDocumentSeries> findByActiveTrueOrderByAbbreviationAsc();

    List<SalesDocumentSeries> findByDocumentTypeIdOrderByAbbreviationAsc(long documentTypeId);

    boolean existsByAbbreviationIgnoreCase(String abbreviation);
}
