package gr.novotrade.novocore.core.purchasing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService}.
 */
interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, Long> {

    /**
     * Delivery lines nobody has invoiced us for yet — received, not invoiced.
     *
     * <p>The other half of a non-zero GR/IR balance (ADR 0004). Non-zero here means a supplier owes us
     * a document rather than us owing them money, and brief §6 makes that a supplier compliance signal
     * in its own right: an invoice missing from myDATA is missing from AADE too.
     */
    @Query("select l from GoodsReceiptLine l "
            + "where l.receipt.reversalOfId is null "
            + "  and not exists (select 1 from GoodsReceipt r where r.reversalOfId = l.receipt.id) "
            + "  and l.quantity > coalesce("
            + "        (select sum(m.quantity) from GrIrMatch m where m.receiptLine.id = l.id "
            + "           and not exists (select 1 from PurchaseInvoice i "
            + "                           where i.reversalOfId = m.invoiceLine.invoice.id)), 0) "
            + "order by l.receipt.receiptDate asc, l.receipt.id asc, l.lineNumber asc")
    List<GoodsReceiptLine> findAwaitingInvoice();
}
