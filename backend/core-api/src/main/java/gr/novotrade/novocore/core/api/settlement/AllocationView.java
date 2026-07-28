package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.shared.Money;
import java.util.Objects;

/**
 * One allocation: this much of that source settled this much of that document.
 *
 * <p><strong>It posted nothing, and that is the design rather than an omission.</strong> Open item
 * matching is a layer over Accounts receivable and Accounts payable, not a second ledger beside them.
 * The invoice posted, the receipt posted; saying which one paid the other would debit and credit the
 * same control account for the same amount. That is also what makes an allocation freely reducible
 * and releasable — the thing Q13's second half requires — without touching a posted entry.
 *
 * @param allocationOrder the order this was applied in, ascending and unique per source.
 *     <strong>Q13's second half depends on it</strong>: editing a receipt below its allocated total
 *     releases allocations most-recent-first, and {@code created_at} cannot answer which was last
 *     because several created in one transaction share a timestamp to the microsecond.
 */
public record AllocationView(
        long id,
        AllocationSourceType sourceType,
        long sourceId,
        OpenItemRef target,
        int allocationOrder,
        Money amount) {

    public AllocationView {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(amount, "amount");
    }
}
