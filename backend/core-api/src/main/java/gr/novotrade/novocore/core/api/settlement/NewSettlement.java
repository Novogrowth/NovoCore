package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Request to record money moving — a Receipt or a Payment.
 *
 * @param accountId <strong>our</strong> account the money moved through: a bank account, the cash
 *     box, or a partner clearing account. Named explicitly rather than derived from a payment method,
 *     because which bank it landed in is a fact about what happened rather than a policy the software
 *     may choose. Must be a {@code BANK_CASH} or {@code PARTNER_CLEARING} account.
 * @param allocations which documents this settles, and by how much. May be empty — a bulk remittance
 *     nobody has finished matching is a real state, and brief §6 flags unmatched lines for Clearing
 *     Checks rather than refusing them.
 * @param remainderBecomesCustomerCredit whether an unallocated remainder becomes a standalone credit
 *     document (Q16). <strong>Stated, never inferred</strong>: a receipt whose allocations come to
 *     less than its amount has two entirely different meanings — the customer overpaid and the balance
 *     is theirs to spend, or nobody has finished matching a remittance — and guessing between them is
 *     what {@code CLAUDE.md} rule 7 forbids. Only meaningful for an incoming settlement from a
 *     customer.
 * @param reference the bank's own reference, the POS batch, the receipt book number. Not unique and
 *     nothing reconciles against it until the Bank Aggregator adapter exists.
 */
public record NewSettlement(
        SettlementDirection direction,
        PartyType partyType,
        long partyId,
        long accountId,
        LocalDate settlementDate,
        Money amount,
        String reference,
        String description,
        List<NewAllocation> allocations,
        boolean remainderBecomesCustomerCredit) {

    public NewSettlement {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(settlementDate, "settlementDate");
        Objects.requireNonNull(amount, "amount");
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
        reference = (reference == null || reference.isBlank()) ? null : reference.trim();
        description = (description == null || description.isBlank()) ? null : description.trim();

        if (partyId <= 0) {
            throw new IllegalArgumentException(
                    "partyId must be a positive NovoCore id, got " + partyId);
        }
        if (accountId <= 0) {
            throw new IllegalArgumentException(
                    "accountId must be a positive NovoCore id, got " + accountId);
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Settlement amount " + amount + " is not positive. Which way the money went is "
                            + "said by the direction, not by the sign, so a negative amount is a "
                            + "payment written as a receipt.");
        }
        if (remainderBecomesCustomerCredit
                && !(direction == SettlementDirection.INCOMING && partyType == PartyType.CUSTOMER)) {
            throw new IllegalArgumentException(
                    "Only money received from a customer can leave credit behind. A payment out has "
                            + "no remainder to give them, and a supplier's credit is their credit note "
                            + "rather than ours to record.");
        }
    }

    /** Money received from a customer, settling the named documents. */
    public static NewSettlement receiptFrom(long customerId, long accountId, LocalDate date,
            Money amount, List<NewAllocation> allocations) {
        return new NewSettlement(SettlementDirection.INCOMING, PartyType.CUSTOMER, customerId,
                accountId, date, amount, null, null, allocations, false);
    }

    /** Money paid to a supplier, settling the named documents. */
    public static NewSettlement paymentTo(long supplierId, long accountId, LocalDate date,
            Money amount, List<NewAllocation> allocations) {
        return new NewSettlement(SettlementDirection.OUTGOING, PartyType.SUPPLIER, supplierId,
                accountId, date, amount, null, null, allocations, false);
    }

    /** Money refunded to a customer, settling a credit note. */
    public static NewSettlement refundTo(long customerId, long accountId, LocalDate date,
            Money amount, List<NewAllocation> allocations) {
        return new NewSettlement(SettlementDirection.OUTGOING, PartyType.CUSTOMER, customerId,
                accountId, date, amount, null, null, allocations, false);
    }

    /** The same request, with any remainder becoming Q16's standalone credit document. */
    public NewSettlement leavingCredit() {
        return new NewSettlement(direction, partyType, partyId, accountId, settlementDate, amount,
                reference, description, allocations, true);
    }

    public NewSettlement referenced(String bankReference) {
        return new NewSettlement(direction, partyType, partyId, accountId, settlementDate, amount,
                bankReference, description, allocations, remainderBecomesCustomerCredit);
    }

    public NewSettlement describedAs(String settlementDescription) {
        return new NewSettlement(direction, partyType, partyId, accountId, settlementDate, amount,
                reference, settlementDescription, allocations, remainderBecomesCustomerCredit);
    }

    /** What the allocations come to. Refused above {@link #amount()} by the service. */
    public Money allocatedTotal() {
        Money total = Money.zero(amount.currency());
        for (NewAllocation allocation : allocations) {
            total = total.plus(allocation.amount());
        }
        return total;
    }
}
