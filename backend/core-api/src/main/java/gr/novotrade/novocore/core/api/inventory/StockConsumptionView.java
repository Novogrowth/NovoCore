package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Stock taken out of inventory as a cost of sale, and which lots it came from.
 *
 * <p><strong>{@link #shortfallQuantity} is Q17's answer made visible</strong> (ADR 0008). A sale may
 * post even when there is not enough stock to back it — brief §6 already treats goods arriving after
 * their invoice as routine, so blocking a real sale over paperwork timing would contradict the design
 * — but it is never silent. The part FIFO could not fill is recorded here, {@code stockOf} subtracts
 * it so the product genuinely reads negative, and
 * {@code InventoryService.consumptionsWithShortfall()} is what a review reads.
 *
 * <p><strong>No cost of goods sold is posted for the shortfall.</strong> There is no lot to take a
 * cost from, and reaching for the last purchase price would be exactly the silent guess
 * {@code CLAUDE.md} rule 7 forbids. So COGS is understated for as long as the shortfall stands, and
 * the flag is what says so. A later Goods Receipt does not retro-cost it either — that would be
 * ADR 0008's first decision in reverse; the correction is to reverse this consumption and consume
 * again once the stock exists.
 *
 * @param journalEntryId nullable, and for two reasons that both really happen: every lot consumed was
 *     carried at zero (a free sample being sold), or nothing could be filled at all and the whole
 *     quantity is shortfall.
 */
public record StockConsumptionView(
        long id,
        long productId,
        String productSku,
        Quantity quantityRequested,
        Quantity quantityFilled,
        Quantity shortfallQuantity,
        LocalDate consumptionDate,
        JournalSource source,
        String note,
        Money totalCost,
        Long journalEntryId,
        Long reversalOfConsumptionId,
        Long reversedByConsumptionId,
        List<StockConsumptionLineView> lines) {

    public StockConsumptionView {
        Objects.requireNonNull(productSku, "productSku");
        Objects.requireNonNull(quantityRequested, "quantityRequested");
        Objects.requireNonNull(quantityFilled, "quantityFilled");
        Objects.requireNonNull(shortfallQuantity, "shortfallQuantity");
        Objects.requireNonNull(consumptionDate, "consumptionDate");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(totalCost, "totalCost");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    /**
     * True when this consumption drove aggregate stock negative — Q17's flag.
     *
     * <p>A flag on the record rather than an entry in a review queue, deliberately: Q15's remainder
     * — whether a flagged item lives in a queue or on the record — is still open, and inventing a
     * queue here would answer it by accident for everything else that gets flagged.
     */
    public boolean droveStockNegative() {
        return shortfallQuantity.isPositive();
    }

    /** True when nothing was posted: every lot was carried at zero, or nothing could be filled. */
    public boolean costedNothing() {
        return journalEntryId == null;
    }

    public boolean isReversal() {
        return reversalOfConsumptionId != null;
    }

    public boolean isReversed() {
        return reversedByConsumptionId != null;
    }

    /** True when this consumption still stands — neither a reversal nor reversed. */
    public boolean isInForce() {
        return !isReversal() && !isReversed();
    }

    /** How many distinct lots this consumption reached into. One is the ordinary case. */
    public int lotsTouched() {
        return lines.size();
    }
}
