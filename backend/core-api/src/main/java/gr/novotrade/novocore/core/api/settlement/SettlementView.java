package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A Receipt or a Payment, with what it settled.
 *
 * <p>One record for both, because structurally they are one thing — money moving between one of our
 * accounts and one counterparty's sub-ledger, with allocations against that counterparty's documents.
 * The {@link #direction()} decides the side of the entry and which {@code JournalSource} it carries,
 * so the ledger cannot tell they share a table and Q13's per-source policy is unchanged.
 *
 * @param unallocatedAmount what has not been applied to anything. Brief §6's "unmatched lines flagged
 *     for Clearing Checks" is a query over this, and it is a genuinely different state from a
 *     customer credit — which is why {@code NewSettlement.remainderBecomesCustomerCredit} has to be
 *     stated rather than inferred (Q16).
 * @param customerCreditId the standalone credit document this settlement left behind, or null.
 */
public record SettlementView(
        long id,
        SettlementDirection direction,
        PartyType partyType,
        long partyId,
        String partyName,
        long accountId,
        String accountName,
        LocalDate settlementDate,
        Money amount,
        Money allocatedAmount,
        Money unallocatedAmount,
        String reference,
        String description,
        long journalEntryId,
        Long customerCreditId,
        List<AllocationView> allocations) {

    public SettlementView {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(partyName, "partyName");
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(settlementDate, "settlementDate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(allocatedAmount, "allocatedAmount");
        Objects.requireNonNull(unallocatedAmount, "unallocatedAmount");
        allocations = List.copyOf(Objects.requireNonNull(allocations, "allocations"));
    }

    /** True when every cent of it has been applied to a document. */
    public boolean isFullyAllocated() {
        return unallocatedAmount.isZero();
    }

    /** True when this is a Receipt — money in. */
    public boolean isReceipt() {
        return direction.isIncoming();
    }

    /** True when it left Q16's standalone credit document behind. */
    public boolean leftCredit() {
        return customerCreditId != null;
    }
}
