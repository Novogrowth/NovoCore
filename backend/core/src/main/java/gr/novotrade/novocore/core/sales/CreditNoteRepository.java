package gr.novotrade.novocore.core.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.sales.CreditNoteService}.
 */
interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    List<CreditNote> findBySalesInvoiceIdOrderByIdAsc(long salesInvoiceId);

    List<CreditNote> findByCustomerIdOrderByCreditNoteDateAscIdAsc(long customerId);

    List<CreditNote> findByCreditNoteDateBetweenOrderByCreditNoteDateAscIdAsc(
            LocalDate from, LocalDate to);

    Optional<CreditNote> findByJournalEntryId(long journalEntryId);

    Optional<CreditNote> findByReversalOfId(long creditNoteId);

    /** As {@code SalesInvoiceRepository.existsStandingInvoice}, and for the same reason. */
    @Query("""
            SELECT COUNT(existing) > 0 FROM CreditNote existing
            WHERE upper(existing.documentNumber) = upper(:documentNumber)
              AND existing.reversalOfId IS NULL
              AND NOT EXISTS (SELECT 1 FROM CreditNote reversal
                               WHERE reversal.reversalOfId = existing.id)
            """)
    boolean existsStandingCreditNote(String documentNumber);

    /**
     * How much of one invoice line has already been credited, counting only credit notes that stand.
     *
     * <p>What stops a line being credited twice. Reversed credit notes are excluded because they did
     * not happen; a reversal row is excluded because it is the undoing rather than a second credit.
     */
    @Query("""
            SELECT COALESCE(SUM(line.quantity), 0) FROM CreditNoteLine line
            WHERE line.salesInvoiceLineId = :salesInvoiceLineId
              AND line.creditNote.reversalOfId IS NULL
              AND NOT EXISTS (SELECT 1 FROM CreditNote reversal
                               WHERE reversal.reversalOfId = line.creditNote.id)
            """)
    java.math.BigDecimal creditedQuantityOf(long salesInvoiceLineId);
}
