package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Request to allocate a freight or duty cost into the lots it delivered — brief §4, Q18, ADR 0010.
 *
 * <p><strong>One source line, many lots.</strong> The cost being allocated is a
 * <em>purchase invoice expense line</em> pointed at {@code Freight / Landed Cost — Unallocated},
 * which is exactly what step 8 records a carrier's invoice as. Naming a source line rather than a
 * bare amount is what makes "how much of this freight is still unallocated" a question with an
 * answer, and what stops an allocation crediting more out of that account than anything ever put
 * into it — the same shape as a GR/IR match, which names the delivery line it settles.
 *
 * <p>A freight invoice carrying several chargeable lines — transport, then customs handling — is
 * allocated once per line. That is deliberate rather than tedious: each line then has its own
 * unallocated remainder, and a partly-allocated invoice says which part.
 *
 * <p><strong>The lots are named, never guessed.</strong> Nothing infers which stock a shipment
 * carried from dates or suppliers ({@code CLAUDE.md} rule 7); a person who has the packing list says
 * so. They may span several purchase invoices and several deliveries, which is the ordinary case for
 * a consolidated shipment.
 *
 * @param purchaseInvoiceLineId the freight cost being allocated. Must be an expense line on an
 *     invoice that still stands, pointed at {@code FREIGHT_LANDED_COST_UNALLOCATED}.
 * @param amount how much of that line to allocate now. May be less than the line's net — a shipment
 *     whose lots are only half identified is allocated in two goes — but never more than what is
 *     left of it.
 * @param allocationDate the accounting date of the posting. Normally the date the freight is being
 *     matched to the goods rather than the carrier's invoice date, because that is when the lots'
 *     cost actually changes.
 * @param lotIds the lots the cost is divided across, proportionally by their received value. At
 *     least one, each named at most once.
 */
public record NewFreightAllocation(
        long purchaseInvoiceLineId,
        Money amount,
        LocalDate allocationDate,
        String description,
        List<Long> lotIds) {

    public NewFreightAllocation {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(allocationDate, "allocationDate");
        Objects.requireNonNull(lotIds, "lotIds");
        lotIds = List.copyOf(lotIds);

        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Freight allocation amount " + amount + " is not positive. Allocating nothing "
                            + "changes no lot's cost and posts no entry, and a negative allocation is "
                            + "the reversal of an existing one rather than a new document.");
        }
        if (lotIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "A freight allocation must name at least one lot. Nothing infers which stock a "
                            + "shipment carried, so an allocation across no lots has nowhere to go.");
        }

        Set<Long> seen = new LinkedHashSet<>();
        for (Long lotId : lotIds) {
            Objects.requireNonNull(lotId, "lotId");
            if (!seen.add(lotId)) {
                throw new IllegalArgumentException(
                        "Lot " + lotId + " is named twice in this allocation. Two entries for one lot "
                                + "are two weights in the proportional split, so the lot would take "
                                + "double its share of the freight while looking correct on each row.");
            }
        }
    }

    public static NewFreightAllocation of(long purchaseInvoiceLineId, Money amount,
            LocalDate allocationDate, String description, List<Long> lotIds) {
        return new NewFreightAllocation(
                purchaseInvoiceLineId, amount, allocationDate, description, lotIds);
    }
}
