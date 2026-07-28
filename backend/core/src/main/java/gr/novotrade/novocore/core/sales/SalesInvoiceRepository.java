package gr.novotrade.novocore.core.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    Optional<SalesInvoice> findByJournalEntryId(long journalEntryId);

    /** The document that reverses this one, if any. Stored one way, queried the other. */
    Optional<SalesInvoice> findByReversalOfId(long invoiceId);

    /**
     * Whether this number is already held by an invoice that still stands.
     *
     * <p>A reversing document deliberately carries the original's number, and once an invoice has been
     * reversed its number is released for a correct re-entry — so "still stands" means neither a
     * reversal nor reversed. The same rule the database enforces by trigger; stated here as well so
     * the failure explains itself instead of arriving as a constraint name.
     */
    @Query("""
            SELECT COUNT(existing) > 0 FROM SalesInvoice existing
            WHERE upper(existing.documentNumber) = upper(:documentNumber)
              AND existing.reversalOfId IS NULL
              AND NOT EXISTS (SELECT 1 FROM SalesInvoice reversal
                               WHERE reversal.reversalOfId = existing.id)
            """)
    boolean existsStandingInvoice(String documentNumber);

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
