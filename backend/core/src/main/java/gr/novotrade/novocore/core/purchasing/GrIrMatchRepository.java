package gr.novotrade.novocore.core.purchasing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through the two purchasing services.
 */
interface GrIrMatchRepository extends JpaRepository<GrIrMatch, Long> {

    List<GrIrMatch> findByInvoiceLineIdOrderByIdAsc(long purchaseInvoiceLineId);

    List<GrIrMatch> findByReceiptLineIdOrderByIdAsc(long goodsReceiptLineId);

    List<GrIrMatch> findByInvoiceLineInvoiceIdOrderByIdAsc(long invoiceId);

    boolean existsByReceiptLineReceiptId(long receiptId);

    /** How much of one invoice line has been delivered by deliveries that stand. Zero when none has. */
    @Query("select coalesce(sum(m.quantity), 0) from GrIrMatch m "
            + "where m.invoiceLine.id = :invoiceLineId "
            + "  and not exists (select 1 from GoodsReceipt r "
            + "                  where r.reversalOfId = m.receiptLine.receipt.id)")
    BigDecimal matchedAgainstInvoiceLine(long invoiceLineId);

    /**
     * How much of one delivery line has been invoiced by invoices that stand.
     *
     * <p>Matches belonging to a <em>reversed</em> invoice are excluded, which is what makes reversing
     * an invoice actually release the delivery it had claimed: without that, a receipt paid for by an
     * invoice that no longer stands would still read as invoiced and would disappear from the
     * awaiting-invoice half of the GR/IR position.
     */
    @Query("select coalesce(sum(m.quantity), 0) from GrIrMatch m "
            + "where m.receiptLine.id = :receiptLineId "
            + "  and not exists (select 1 from PurchaseInvoice i "
            + "                  where i.reversalOfId = m.invoiceLine.invoice.id)")
    BigDecimal matchedAgainstReceiptLine(long receiptLineId);

    /** Whether any invoice that still stands has claimed part of this delivery. */
    @Query("select count(m) > 0 from GrIrMatch m "
            + "where m.receiptLine.receipt.id = :receiptId "
            + "  and not exists (select 1 from PurchaseInvoice i "
            + "                  where i.reversalOfId = m.invoiceLine.invoice.id)")
    boolean existsStandingMatchForReceipt(long receiptId);
}
