package gr.novotrade.novocore.core.document;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.document.SalesDocumentSeriesService}.
 */
interface SalesDocumentSeriesRepository extends JpaRepository<SalesDocumentSeries, Long> {


    /**
     * ⚠️ Ordered by {@code sort_code}. That is what the column exists for — the previous default
     * ordered by abbreviation, which is the Greek alphabet and therefore arbitrary to the person
     * reading the list. See {@code V34}.
     */
    List<SalesDocumentSeries> findAllByOrderBySortCodeAsc();

    List<SalesDocumentSeries> findByActiveTrueOrderBySortCodeAsc();

    List<SalesDocumentSeries> findByDocumentTypeIdOrderBySortCodeAsc(long documentTypeId);

    /** Unique per table, so the ordering is deterministic. */
    boolean existsBySortCode(int sortCode);

    boolean existsByAbbreviationIgnoreCase(String abbreviation);

    /**
     * Whether any sales invoice names this series — the predicate that freezes its abbreviation,
     * document type and ΜΑΡΚ flag.
     *
     * <p>⚠️ <strong>Native SQL rather than a reference to {@code SalesInvoice}, deliberately.</strong>
     * {@code SalesInvoiceServiceImpl} already depends on this package through
     * {@code SalesDocumentSeriesService}; a Java-level dependency the other way would put
     * {@code core.document} and {@code core.sales} in a cycle for one boolean. The question is a
     * referential one — <em>does a row point at me</em> — and the FK that answers it lives on the
     * table named here, so SQL is the honest instrument.
     *
     * <p>⚠️ <strong>A reversed invoice still counts.</strong> Its {@code series_id} is set, it is in
     * the journal, and its number is in the books. "Recorded" is not "standing".
     *
     * <p>Index-only: {@code sales_invoice_series_idx} is {@code (series_id, id)}.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM sales_invoice WHERE series_id = :id)",
            nativeQuery = true)
    boolean isNamedByARecordedDocument(long id);

    /**
     * The same question for a whole list, as <strong>one</strong> query.
     *
     * <p>A list screen renders every series with its own locked/unlocked state; asking per row would
     * be an N+1 on a page that exists to be scanned.
     */
    @Query(value = "SELECT DISTINCT series_id FROM sales_invoice WHERE series_id IS NOT NULL",
            nativeQuery = true)
    Set<Long> idsNamedByARecordedDocument();
}
