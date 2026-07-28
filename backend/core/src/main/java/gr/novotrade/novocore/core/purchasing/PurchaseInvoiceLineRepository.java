package gr.novotrade.novocore.core.purchasing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService}.
 */
interface PurchaseInvoiceLineRepository extends JpaRepository<PurchaseInvoiceLine, Long> {

    /**
     * Inventory lines with quantity nobody has delivered yet — invoiced, not received.
     *
     * <p>One of the two halves of a non-zero GR/IR balance (ADR 0004), and what phase 8's Clearing
     * Checks reads. Lines belonging to a reversal, or to an invoice that has been reversed, are
     * excluded: they are not owed anything.
     */
    @Query("select l from PurchaseInvoiceLine l "
            + "where l.lineType = gr.novotrade.novocore.core.api.purchasing.PurchaseLineType.INVENTORY "
            + "  and l.invoice.reversalOfId is null "
            + "  and not exists (select 1 from PurchaseInvoice r where r.reversalOfId = l.invoice.id) "
            + "  and l.quantity > coalesce("
            + "        (select sum(m.quantity) from GrIrMatch m where m.invoiceLine.id = l.id "
            + "           and not exists (select 1 from GoodsReceipt r "
            + "                           where r.reversalOfId = m.receiptLine.receipt.id)), 0) "
            + "order by l.invoice.invoiceDate asc, l.invoice.id asc, l.lineNumber asc")
    List<PurchaseInvoiceLine> findAwaitingDelivery();

    /**
     * Freight lines with cost nobody has allocated into stock yet — <strong>step 10, ADR 0010</strong>.
     *
     * <p>The Freight / Landed Cost — Unallocated counterpart of {@link #findAwaitingDelivery}, and
     * what phase 8's Clearing Checks reads against that account's balance: it is
     * {@code expected_to_clear} and nothing forces the allocation that clears it.
     *
     * <p>Takes the account id rather than naming the system key in JPQL, because resolving a system
     * key is {@code ChartOfAccountsService}'s job and a repository has no business knowing the chart.
     * Lines belonging to a reversal, or to an invoice that has been reversed, are excluded: there is
     * nothing left to allocate out of a document that no longer stands.
     */
    @Query("select l from PurchaseInvoiceLine l "
            + "where l.lineType = gr.novotrade.novocore.core.api.purchasing.PurchaseLineType.EXPENSE "
            + "  and l.expenseAccountId = :freightAccountId "
            + "  and l.invoice.reversalOfId is null "
            + "  and not exists (select 1 from PurchaseInvoice r where r.reversalOfId = l.invoice.id) "
            + "  and l.netAmount > coalesce("
            + "        (select sum(a.capitalisedAmount + a.varianceAmount) "
            + "           from FreightAllocationLine a "
            + "          where a.allocation.sourceLine.id = l.id "
            + "            and a.allocation.reversalOfId is null "
            + "            and not exists (select 1 from FreightAllocation r2 "
            + "                            where r2.reversalOfId = a.allocation.id)), 0) "
            + "order by l.invoice.invoiceDate asc, l.invoice.id asc, l.lineNumber asc")
    List<PurchaseInvoiceLine> findAwaitingAllocation(long freightAccountId);
}
