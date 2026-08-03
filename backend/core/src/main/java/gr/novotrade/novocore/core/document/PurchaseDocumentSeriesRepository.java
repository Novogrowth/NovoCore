package gr.novotrade.novocore.core.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.document.PurchaseDocumentSeriesService}.
 */
interface PurchaseDocumentSeriesRepository extends JpaRepository<PurchaseDocumentSeries, Long> {

    List<PurchaseDocumentSeries> findAllByOrderByAbbreviationAsc();

    List<PurchaseDocumentSeries> findByActiveTrueOrderByAbbreviationAsc();

    List<PurchaseDocumentSeries> findByDocumentTypeIdOrderByAbbreviationAsc(long documentTypeId);

    boolean existsByAbbreviationIgnoreCase(String abbreviation);
}
