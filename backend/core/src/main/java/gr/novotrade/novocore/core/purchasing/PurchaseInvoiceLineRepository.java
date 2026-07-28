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
}
