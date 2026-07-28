package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Receipts, Payments, and brief §6's open item matching.
 *
 * <p><strong>One service and one table for both typed transactions.</strong> A Receipt and a Payment
 * are structurally identical — money moving between one of our accounts and one counterparty's
 * sub-ledger, with allocations against that counterparty's documents — and every column, rule and line
 * of allocation logic would be duplicated in a second copy. The {@link SettlementDirection} decides
 * the side of the entry and the {@code JournalSource} it carries, so Q13's per-source policy is
 * untouched and the ledger cannot tell. The trigger for splitting them is the first column that
 * belongs to one and not the other; there is none today.
 *
 * <p><strong>Documents post; allocations do not.</strong> The invoice posted, the receipt posted, and
 * saying which one paid the other would debit and credit the same control account for the same amount.
 * So an allocation is pure open-item bookkeeping — which is exactly what makes it freely reducible and
 * releasable without touching a posted entry, and therefore what makes Q13's second half implementable
 * at all.
 *
 * <p><strong>Nothing stores an open amount or a "paid" flag.</strong> {@link #openAmountOf} computes
 * it on every read, for the reason no account holds a balance.
 *
 * <p><strong>Permissions.</strong> {@link Section#SETTLEMENTS}, its own section: reading what the
 * business was paid, by whom and into which account is a different job from recording a sale, and a
 * settlement reaches both sub-ledgers at once.
 */
public interface SettlementService {

    // ---------------------------------------------------------------------------------------
    // Recording money moving
    // ---------------------------------------------------------------------------------------

    /**
     * Records a Receipt or a Payment and applies its allocations.
     *
     * <p>Posts one entry: for money in, debit the account it landed in and credit the party's control
     * account carrying their sub-ledger reference; for money out, the other way round. The allocations
     * post nothing.
     *
     * <p>Brief §6's cases, all of them the same operation: a simple payment is one allocation, an
     * instalment a smaller one, a bulk remittance several across different invoices, an overpayment
     * the remainder when they come to less than the amount. That remainder becomes Q16's standalone
     * credit document <em>only</em> when {@code NewSettlement.remainderBecomesCustomerCredit} says so
     * — otherwise it stays unmatched and queryable, because "the customer overpaid" and "nobody has
     * finished matching this remittance" are different facts and guessing between them is what
     * {@code CLAUDE.md} rule 7 forbids.
     *
     * @throws InvalidSettlementException if the party or account is unknown or inactive; if the
     *     account is not a {@code BANK_CASH} or {@code PARTNER_CLEARING} account; if a cash movement
     *     reaches {@code SettingKeys.CASH_PAYMENT_LIMIT}; if the allocations exceed the amount; if an
     *     allocated document belongs to another party or to the other side of the ledger; if it would
     *     be allocated beyond its open amount; or if it is a sales invoice that was born settled and
     *     therefore never had an open amount
     */
    SettlementView record(NewSettlement request);

    /**
     * Edits a Receipt or Payment in place. <strong>Q13's editable half.</strong>
     *
     * <p>Permitted because a receipt is <em>our own</em> record of money moving, unlike an invoice
     * which has been issued to somebody else. The previous state is written to the audit log before
     * being overwritten, which is the mechanism Q13 names, and the journal entry is amended through
     * {@code JournalService.amend} so the ledger records the change the same way.
     *
     * <p><strong>Q13's second half, implemented here.</strong> Reducing the amount below what has
     * already been allocated releases allocations <em>starting with the most recently applied one and
     * working backward</em>, reducing the last one partially if that is enough. Most-recent-first
     * because the earlier allocations are the ones somebody deliberately matched; releasing those and
     * keeping the newest would undo a decision in favour of an accident of ordering. Every release is
     * recorded in the audit log with the allocation it touched.
     *
     * @throws InvalidSettlementException for the conditions {@link #record} checks, and if the new
     *     account is of the wrong kind
     * @throws SettlementNotFoundException if there is no such settlement
     */
    SettlementView amend(long settlementId, long accountId, LocalDate settlementDate, Money amount,
            String reference, String description);

    // ---------------------------------------------------------------------------------------
    // Allocating, without moving money
    // ---------------------------------------------------------------------------------------

    /**
     * Applies more of an existing settlement to documents.
     *
     * <p>The bulk-remittance case finished later: a receipt recorded with nothing matched, then
     * matched once the partner's remittance report says which orders it covered.
     */
    SettlementView allocate(long settlementId, List<NewAllocation> allocations);

    /**
     * Sets a credit note against an invoice instead of refunding it — posts nothing, both ends being
     * Accounts receivable.
     *
     * @throws InvalidSettlementException if the two belong to different customers, or if either has
     *     less open than the amount
     */
    AllocationView allocateCreditNote(long creditNoteId, long salesInvoiceId, Money amount);

    /**
     * Spends Q16's standalone customer credit against an invoice — posts nothing, for the same reason.
     *
     * @throws InvalidSettlementException if the two belong to different customers, or if either has
     *     less open than the amount
     */
    AllocationView allocateCustomerCredit(long customerCreditId, long salesInvoiceId, Money amount);

    /**
     * Releases an allocation, putting the open amount back on both ends.
     *
     * <p>Deleting a row rather than reversing it, which is the one place in this schema that happens
     * — and it is right here for the reason the rest of the schema refuses it: an allocation is not a
     * record of an event, it is a statement about the current relationship between two documents.
     * Nothing was posted for it, so nothing is being erased from the ledger, and the audit log records
     * the release.
     *
     * @throws InvalidSettlementException if there is no such allocation
     */
    void release(long allocationId);

    // ---------------------------------------------------------------------------------------
    // Reading — open items
    // ---------------------------------------------------------------------------------------

    /**
     * What is still outstanding on one document. Computed, never stored.
     *
     * <p>Zero for a sales invoice that was born settled: cash and the partner clearing methods debit
     * their own account, so there never was an open amount to settle.
     */
    Money openAmountOf(OpenItemRef ref);

    /** One document as an open item, or empty if it does not exist. */
    Optional<OpenItem> openItem(OpenItemRef ref);

    /**
     * Everything still outstanding for one party, oldest document first.
     *
     * <p>Oldest first because that is the order a receipt is normally applied in, and because an aged
     * debtors report reads the same list. Fully settled documents are absent.
     */
    List<OpenItem> openItemsFor(PartyType partyType, long partyId);

    /** Every document with something outstanding on one side of the ledger, oldest first. */
    List<OpenItem> allOpenItems(PartyType partyType);

    // ---------------------------------------------------------------------------------------
    // Reading — settlements and credit
    // ---------------------------------------------------------------------------------------

    Optional<SettlementView> find(long settlementId);

    /** @throws SettlementNotFoundException if absent */
    SettlementView require(long settlementId);

    /** Every receipt or payment for one party, oldest first. */
    List<SettlementView> ofParty(PartyType partyType, long partyId);

    /** Settlements in a date range, both ends inclusive, oldest first. */
    List<SettlementView> between(LocalDate from, LocalDate to);

    Optional<SettlementView> findByJournalEntry(long journalEntryId);

    /**
     * Settlements with money not applied to anything — <strong>brief §6's "unmatched lines flagged for
     * Clearing Checks"</strong>.
     *
     * <p>A query over computed state rather than a review queue, which is the same answer Q15's
     * remainder gets and for the same reason: a queue is a second copy of state that goes stale the
     * moment somebody matches the remittance without visiting it.
     */
    List<SettlementView> withUnallocatedAmount();

    /** Q16's credit documents for one customer, oldest first. Includes exhausted ones. */
    List<CustomerCreditView> customerCreditsOf(long customerId);

    /** Q16's credit documents with something left to spend, oldest first. */
    List<CustomerCreditView> openCustomerCredits();

    /** The allocations against one document, in the order they were applied. */
    List<AllocationView> allocationsAgainst(OpenItemRef ref);
}
