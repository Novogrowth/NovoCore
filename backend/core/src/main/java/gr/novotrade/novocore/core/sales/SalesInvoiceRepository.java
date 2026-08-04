package gr.novotrade.novocore.core.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.sales.SalesInvoiceService}.
 */
interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {

    List<SalesInvoice> findByCustomerIdOrderByInvoiceDateAscIdAsc(long customerId);

    List<SalesInvoice> findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(
            LocalDate from, LocalDate to);

    // The paged forms. No OrderBy in the method name: the ordering comes from the Pageable, which
    // SpringPaging builds from the caller's sort key and always makes total by appending the id.
    Page<SalesInvoice> findByCustomerId(long customerId, Pageable pageable);

    Page<SalesInvoice> findByInvoiceDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    Optional<SalesInvoice> findByJournalEntryId(long journalEntryId);

    /** The document that reverses this one, if any. Stored one way, queried the other. */
    Optional<SalesInvoice> findByReversalOfId(long invoiceId);

    /**
     * Whether this number is already held, <strong>in this series</strong>, by an invoice that still
     * stands.
     *
     * <p>A reversing document deliberately carries the original's number, and once an invoice has been
     * reversed its number is released for a correct re-entry — so "still stands" means neither a
     * reversal nor reversed. The same rule the database enforces by trigger; stated here as well so
     * the failure explains itself instead of arriving as a constraint name.
     *
     * <p>⚠️ <strong>SCOPED TO THE SERIES SINCE R1b, and it had to be.</strong> {@code V32} made the
     * database key {@code (COALESCE(series_id, -1), upper(document_number))} because ΑΛΠ-1 and ΤΠΔΑ-1
     * are two different documents that both legitimately carry the number 1. This query was written
     * when there were no series, and it checked the number <em>globally</em>. That agreed with the
     * database only by accident: every row's series was null, so every row was in one group. The
     * moment R1b gave an invoice a series the two rules diverged — the database allowed the second
     * document and this refused it — and <strong>the per-series key R1a built would have been
     * unreachable</strong>, enforced by nothing and contradicted by the layer above.
     *
     * <p>{@code IS NOT DISTINCT FROM} in SQL terms, spelled out here because JPQL has no such
     * operator: a null series must collide with a null series, or the guarantee is lost for exactly
     * the rows that have it today — every invoice recorded before R1b.
     */
    @Query("""
            SELECT COUNT(existing) > 0 FROM SalesInvoice existing
            WHERE upper(existing.documentNumber) = upper(:documentNumber)
              AND (existing.seriesId = :seriesId
                   OR (existing.seriesId IS NULL AND :seriesId IS NULL))
              AND existing.reversalOfId IS NULL
              AND NOT EXISTS (SELECT 1 FROM SalesInvoice reversal
                               WHERE reversal.reversalOfId = existing.id)
            """)
    boolean existsStandingInvoice(Long seriesId, String documentNumber);

    /** Q15's query: the invoices whose rounding difference somebody had to accept. */
    @Query("""
            SELECT invoice FROM SalesInvoice invoice
            WHERE invoice.roundingNeededReview = true
              AND invoice.invoiceDate BETWEEN :from AND :to
            ORDER BY invoice.invoiceDate ASC, invoice.id ASC
            """)
    List<SalesInvoice> findWithAcceptedRoundingDifference(LocalDate from, LocalDate to);

    @Query("""
            SELECT invoice FROM SalesInvoice invoice
            WHERE invoice.roundingAmount <> 0
              AND invoice.invoiceDate BETWEEN :from AND :to
            """)
    List<SalesInvoice> findWithRoundingBetween(LocalDate from, LocalDate to);
}
