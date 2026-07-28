package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Freight and duty allocated into the lots they delivered — <strong>brief §4, Q18, ADR 0010</strong>.
 *
 * <p>Step 3 created {@code Freight / Landed Cost — Unallocated} and flagged it
 * {@code expected_to_clear}; step 8 gave a carrier's invoice somewhere to land, as an ordinary
 * expense line pointed at it. This is what clears it: an allocation credits that account and debits
 * the goods.
 *
 * <p><strong>Proportional by value, against what the lots were received at.</strong> Brief §4 chose
 * proportional-by-value over weight-based with the approximation accepted, so a lot's weight in the
 * split is everything it received extended at its <em>received</em> unit cost. Received rather than
 * carried, so that a second freight invoice against the same lots divides them in the same
 * proportion as the first instead of in a proportion the first one moved — which is why
 * {@code InventoryLotView.receivedUnitCost} is frozen for the life of a lot. The split itself is
 * {@code ProportionalAllocation}: exact integer cents, largest remainder, so the shares sum to the
 * amount allocated and the entry balances by construction.
 *
 * <p><strong>Q18, answered: a lot's share is split by what is still in it.</strong> The part
 * belonging to stock on hand raises that lot's unit cost, which is brief §5's "unit cost includes
 * allocated landed costs". The part belonging to stock that has already gone cannot: it was costed
 * out into a posted cost-of-goods-sold entry, and ADR 0008 does not reach back into one. That part
 * posts to {@code Landed cost variance} — the exact counterpart of {@code Purchase price variance},
 * for the same reason and in the same account group.
 *
 * <p>So a fully-sold lot may still be named in an allocation, and all of its share goes to variance.
 * That is the case Q18 exists for, and refusing it would be worse: the freight was genuinely
 * incurred, and leaving it sitting in an expected-to-clear account forever states nothing.
 *
 * <p><strong>Immutable, corrected by reversal</strong> (ADR 0010). {@link #reverse} takes the
 * per-unit cost back off each lot and posts the mirror in one transaction, and refuses once any of
 * those lots has moved — the stance {@code GoodsReceiptService.reverse} takes, and for the same
 * reason: stock that has left did so at a cost this allocation is inside.
 *
 * <p><strong>Permissions.</strong> {@link Section#PURCHASING}, as the two purchasing documents are.
 * It reaches inventory, but what it is doing is spending a supplier's invoice — the lot cost is the
 * consequence, not the operation. There is no controller yet.
 */
public interface FreightAllocationService {

    /**
     * Allocates part or all of a freight cost across the lots it delivered, and posts it.
     *
     * <p>One entry: a debit to {@code Inventory} per lot carrying that lot's {@code SubLedgerRef}, a
     * debit to {@code Landed cost variance} per lot whose stock had already gone, and one credit to
     * {@code Freight / Landed Cost — Unallocated} for the whole amount. The lots' unit costs move in
     * the same transaction, because either half alone is worse than neither — the argument the
     * inventory write-off settled.
     *
     * <p>Refused, rather than resolved, when:
     *
     * <ul>
     *   <li>the source line is not an expense line pointed at
     *       {@code FREIGHT_LANDED_COST_UNALLOCATED} — allocating out of some other account would
     *       drive that one negative while the real cost sat untouched elsewhere;
     *   <li>the source invoice is a reversal, or has been reversed — there is nothing left to
     *       allocate out of a document that no longer stands;
     *   <li>the amount exceeds what is left of that line, counting allocations that stand;
     *   <li>a named lot was received at zero cost — proportional-by-value gives it no share, and
     *       including it would claim to have costed stock that got nothing;
     *   <li>a named lot came from a Goods Receipt that has been reversed — that delivery was
     *       un-made, so nothing was carried to it;
     *   <li>the lots and the freight are not all in one currency (ADR 0005 never converts).
     * </ul>
     *
     * @throws InvalidFreightAllocationException for any of the above
     * @throws PurchaseInvoiceNotFoundException if the source line does not exist
     * @throws gr.novotrade.novocore.core.api.inventory.InventoryLotNotFoundException if a named lot
     *     does not exist
     */
    FreightAllocationView allocate(NewFreightAllocation request);

    /**
     * Un-allocates a posted freight allocation: takes the per-unit cost back off each lot and posts
     * the mirror entry, in one transaction.
     *
     * <p><strong>Refused once any of the lots has moved since</strong>, naming the lot. Stock that
     * left after the allocation was costed out at the raised figure, so the freight is already inside
     * a posted cost of goods sold and reversing would credit Inventory for stock that is not there —
     * ADR 0008's principle, and the same refusal {@code GoodsReceiptService.reverse} makes. Refused
     * rather than partially undone: the correction then is a new allocation of the same cost onto the
     * lots it really belonged to, not a claim that this one never happened.
     *
     * @param reversalDate the accounting date of the reversal. A new fact, so normally today.
     * @throws InvalidFreightAllocationException if it has already been reversed, is itself a
     *     reversal, or a lot it touched has moved since
     * @throws FreightAllocationNotFoundException if there is no such allocation
     */
    FreightAllocationView reverse(long allocationId, LocalDate reversalDate, String reason);

    Optional<FreightAllocationView> find(long allocationId);

    /** @throws FreightAllocationNotFoundException if absent */
    FreightAllocationView require(long allocationId);

    /** Every allocation made out of one purchase invoice line, oldest first, reversals included. */
    List<FreightAllocationView> ofPurchaseInvoiceLine(long purchaseInvoiceLineId);

    /** Every allocation that put a share into one lot, oldest first, reversals included. */
    List<FreightAllocationView> ofLot(long lotId);

    /** Allocations in a date range, oldest first. Includes reversals, for {@code writeOffsBetween}'s reason. */
    List<FreightAllocationView> between(LocalDate from, LocalDate to);

    /**
     * How much of one freight line has not been allocated yet.
     *
     * <p>The line's net less everything allocated out of it by documents that stand — a reversed
     * allocation gives its amount back, which is what makes reversal a real correction rather than a
     * one-way door.
     *
     * <p>Zero for a line that is not a freight line at all, which is the honest answer to "how much
     * of the electricity bill is waiting to be allocated into stock".
     */
    Money unallocatedAmountOf(long purchaseInvoiceLineId);

    /**
     * Freight lines with something still unallocated, oldest first.
     *
     * <p><strong>What phase 8's Clearing Checks reads</strong> against the
     * {@code Freight / Landed Cost — Unallocated} balance, the same way
     * {@code linesAwaitingDelivery} and {@code linesAwaitingInvoice} are read against GR/IR. That
     * account is {@code expected_to_clear} and nothing forces the allocation that clears it, so a
     * query that finds what is sitting there is the compensating control — the shape this design has
     * used since the Damaged Goods aging check.
     */
    List<PurchaseInvoiceLineView> linesAwaitingAllocation();
}
