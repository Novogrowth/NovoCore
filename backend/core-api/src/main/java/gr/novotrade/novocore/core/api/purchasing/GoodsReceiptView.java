package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A recorded physical delivery, and the lots it created.
 *
 * @param journalEntryId nullable, and the case is the same one the stock write-off has: every line of
 *     this delivery arrived at a unit cost of zero, so nothing was capitalised and the ledger rightly
 *     refuses a zero-amount entry. A free sample is a real delivery that derecognises nothing and
 *     recognises nothing. {@link #capitalisedNothing()} is how that reads.
 * @param totalValue what went into Inventory — the sum of the lines, rounded once each.
 */
public record GoodsReceiptView(
        long id,
        long supplierId,
        String supplierName,
        String deliveryNoteNumber,
        LocalDate receiptDate,
        String description,
        Money totalValue,
        Long journalEntryId,
        Long reversalOfReceiptId,
        Long reversedByReceiptId,
        List<GoodsReceiptLineView> lines) {

    public GoodsReceiptView {
        Objects.requireNonNull(supplierName, "supplierName");
        Objects.requireNonNull(receiptDate, "receiptDate");
        Objects.requireNonNull(totalValue, "totalValue");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    /** True when every line arrived free, so there was nothing to post. */
    public boolean capitalisedNothing() {
        return journalEntryId == null;
    }

    public boolean isReversal() {
        return reversalOfReceiptId != null;
    }

    public boolean isReversed() {
        return reversedByReceiptId != null;
    }

    /** True when this delivery still counts — neither a reversal nor reversed. */
    public boolean isInForce() {
        return !isReversal() && !isReversed();
    }

    /** The lines nobody has invoiced us for yet. */
    public List<GoodsReceiptLineView> linesAwaitingInvoice() {
        return lines.stream().filter(GoodsReceiptLineView::isAwaitingInvoice).toList();
    }

    /** The lots this delivery created, oldest line first. */
    public List<Long> lotIds() {
        return lines.stream().map(GoodsReceiptLineView::lotId).toList();
    }
}
