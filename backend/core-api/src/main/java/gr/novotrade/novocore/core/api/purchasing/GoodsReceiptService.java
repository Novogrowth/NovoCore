package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.security.Section;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Physical deliveries: brief §6's verification step, and — by ADR 0004 — the event that creates
 * inventory lots.
 *
 * <p><strong>This is the only route by which stock enters NovoCore in ordinary operation.</strong>
 * {@code InventoryService.receive} is the lower layer it calls, and stands to this what
 * {@code JournalService.post} stands to a typed transaction: reachable from inside the core, and not
 * the thing a controller exposes. A receipt creates the lots <em>and</em> posts, in one transaction —
 * debit Inventory once per lot, credit GR/IR clearing against the supplier — because either alone is
 * worse than neither, which is the rule the stock write-off established.
 *
 * <p><strong>Q39, answered: a Goods Receipt is immutable (ADR 0008).</strong> The same reasoning as
 * the write-off, and stronger than the invoice's: this posting reflects a physical stock movement, so
 * editing it would change what the accounts say arrived without changing the lots that arrived. The
 * correction is {@link #reverse}, which un-receives the lots and posts the mirror together, and
 * refuses outright once anything has happened to them.
 *
 * <p><strong>Permissions.</strong> {@link Section#PURCHASING} throughout, checked at the controller.
 */
public interface GoodsReceiptService {

    // ---------------------------------------------------------------------------------------
    // Receiving
    // ---------------------------------------------------------------------------------------

    /**
     * Records a delivery: creates one lot per line and posts, in one transaction.
     *
     * <p>Validation, in the order a caller will hit it:
     *
     * <ul>
     *   <li>the supplier exists and is active;
     *   <li>every line's product exists, is active, is stocked, and agrees with the line's shape about
     *       whether it is serial-tracked;
     *   <li>a line matched to an invoice line names one that exists, belongs to an unreversed invoice
     *       from <em>this</em> supplier, is for the same product, and has that much still undelivered;
     *   <li>serial numbers are unique within the delivery and across all stock;
     *   <li>quantities are expressible in the product's unit of measure.
     * </ul>
     *
     * <p>A line matched to an invoice line is costed from that invoice and produces no variance; an
     * unmatched line is costed at the provisional figure it states, and keeps it (ADR 0008).
     *
     * @throws InvalidGoodsReceiptException for any of the above
     * @throws gr.novotrade.novocore.core.api.supplier.SupplierNotFoundException if the supplier is
     *     unknown
     * @throws PurchaseInvoiceNotFoundException if a line names no such invoice line
     */
    GoodsReceiptView record(NewGoodsReceipt request);

    /**
     * Reverses a delivery: un-receives its lots and posts the mirror entry, in one transaction.
     *
     * <p>Q39's correction route. Both halves together, for the reason
     * {@code InventoryService.reverseWriteOff} gives: removing the stock without the entry leaves the
     * balance sheet carrying goods that are not there, and reversing the entry without the stock does
     * the opposite. This is also why {@code JournalService.reverse} refuses a {@code GOODS_RECEIPT}
     * entry outright and names this method instead.
     *
     * <p><strong>Refused, not partially undone, once anything has happened to the lots.</strong> If
     * stock has been consumed, written off, moved, or a serialized unit is no longer in stock, the
     * delivery cannot be un-made: the goods went somewhere. The correction is then a write-off or a
     * supplier return, which say what actually happened rather than claiming the delivery never
     * occurred. The refusal names what got in the way.
     *
     * <p>Also refused once an invoice has matched against it, because that match cleared GR/IR and
     * possibly posted a variance. Reverse the invoice first.
     *
     * @throws InvalidGoodsReceiptException if the receipt is itself a reversal, has already been
     *     reversed, or its lots have been touched or matched
     * @throws GoodsReceiptNotFoundException if there is no such receipt
     */
    GoodsReceiptView reverse(long receiptId, LocalDate reversalDate, String reason);

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    Optional<GoodsReceiptView> find(long receiptId);

    /** @throws GoodsReceiptNotFoundException if absent */
    GoodsReceiptView require(long receiptId);

    /** Every delivery from one supplier, oldest first. */
    List<GoodsReceiptView> ofSupplier(long supplierId);

    /** Deliveries received in the range, both ends inclusive, oldest first. */
    List<GoodsReceiptView> between(LocalDate from, LocalDate to);

    /** The receipt this journal entry came from, if any. See {@code PurchaseInvoiceService}'s note. */
    Optional<GoodsReceiptView> findByJournalEntry(long journalEntryId);

    /** The delivery a lot came from, if it came from one. Brief §5's source document reference. */
    Optional<GoodsReceiptView> findByLot(long lotId);

    // ---------------------------------------------------------------------------------------
    // The GR/IR position — ADR 0004
    // ---------------------------------------------------------------------------------------

    /**
     * Receipt lines delivered but not yet invoiced, oldest first — <strong>received, not
     * invoiced</strong>.
     *
     * <p>The other half of a non-zero GR/IR balance from
     * {@code PurchaseInvoiceService.linesAwaitingDelivery}. Non-zero here means a supplier owes us a
     * document rather than us owing them money — and brief §6 makes that a supplier compliance signal
     * in its own right, because an invoice missing from myDATA is missing from AADE too.
     */
    List<GoodsReceiptLineView> linesAwaitingInvoice();
}
