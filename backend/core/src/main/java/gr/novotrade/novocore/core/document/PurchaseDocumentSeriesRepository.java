package gr.novotrade.novocore.core.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.document.PurchaseDocumentSeriesService}.
 */
interface PurchaseDocumentSeriesRepository extends JpaRepository<PurchaseDocumentSeries, Long> {


    /**
     * ⚠️ Ordered by {@code sort_code}. That is what the column exists for — the previous default
     * ordered by abbreviation, which is the Greek alphabet and therefore arbitrary to the person
     * reading the list. See {@code V34}.
     */
    List<PurchaseDocumentSeries> findAllByOrderBySortCodeAsc();

    List<PurchaseDocumentSeries> findByActiveTrueOrderBySortCodeAsc();

    List<PurchaseDocumentSeries> findByDocumentTypeIdOrderBySortCodeAsc(long documentTypeId);

    /** Unique per table, so the ordering is deterministic. */
    boolean existsBySortCode(int sortCode);

    boolean existsByAbbreviationIgnoreCase(String abbreviation);
}
