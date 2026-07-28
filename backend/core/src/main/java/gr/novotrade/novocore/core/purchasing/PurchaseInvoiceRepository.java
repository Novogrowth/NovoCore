package gr.novotrade.novocore.core.purchasing;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService}.
 */
interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {

    List<PurchaseInvoice> findBySupplierIdOrderByInvoiceDateAscIdAsc(long supplierId);

    List<PurchaseInvoice> findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(
            LocalDate from, LocalDate to);

    Optional<PurchaseInvoice> findByJournalEntryId(long journalEntryId);

    /** The document that reverses this one, if any. Stored one way, queried the other. */
    Optional<PurchaseInvoice> findByReversalOfId(long invoiceId);

    /** The batched form, so listing invoices does not become a query per row. */
    @Query("select i.reversalOfId, i.id from PurchaseInvoice i where i.reversalOfId in :invoiceIds")
    List<Object[]> findReversalPairs(Collection<Long> invoiceIds);

    /**
     * A duplicate check against the supplier's own number, among documents that stand.
     *
     * <p>Reversal documents are excluded because they deliberately carry the number of the invoice
     * they correct — and once an invoice has been reversed, re-entering it correctly under the same
     * number is the ordinary thing to want.
     */
    @Query("select count(i) > 0 from PurchaseInvoice i "
            + "where i.supplierId = :supplierId "
            + "  and upper(i.supplierInvoiceNumber) = upper(:number) "
            + "  and i.reversalOfId is null "
            + "  and not exists (select 1 from PurchaseInvoice r where r.reversalOfId = i.id)")
    boolean existsStandingInvoice(long supplierId, String number);

    /**
     * Invoices that posted a purchase price variance over a period, oldest first.
     *
     * <p>Reversals and reversed documents are excluded: a variance that has been corrected is not one
     * anybody should be reading a purchasing report from.
     */
    @Query("select distinct i from PurchaseInvoice i join i.lines l "
            + "where i.invoiceDate between :from and :to "
            + "  and l.varianceAmount <> 0 "
            + "  and i.reversalOfId is null "
            + "  and not exists (select 1 from PurchaseInvoice r where r.reversalOfId = i.id) "
            + "order by i.invoiceDate asc, i.id asc")
    List<PurchaseInvoice> findWithVarianceBetween(LocalDate from, LocalDate to);
}
