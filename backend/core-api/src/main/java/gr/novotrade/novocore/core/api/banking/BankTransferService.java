package gr.novotrade.novocore.core.api.banking;

import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Transfers between our own accounts — brief §6's Bank Transfer typed transaction.
 *
 * <p>The smallest of the six, and the only document-shaped one that is <strong>reversible through the
 * ledger alone</strong>: it allocates against nothing and moves no stock, so its journal entry is the
 * whole of it. {@code JournalSource.BANK_TRANSFER} says so, and {@link #reverse} here is a thin
 * wrapper that keeps the document in step rather than a second reversal mechanism.
 *
 * <p>Editable in place as well (Q13), because it is our own record of money moving — the same category
 * as a receipt.
 *
 * <p><strong>Permissions.</strong> {@link Section#SETTLEMENTS}.
 */
public interface BankTransferService {

    /**
     * Records a transfer: debit the destination, credit the source.
     *
     * <p>Both accounts must be {@code BANK_CASH} or {@code PARTNER_CLEARING}. A transfer into an
     * expense account is not a transfer, and letting one through here would be a payment recorded as
     * an internal movement — which is how money leaves the business without appearing to.
     *
     * @throws InvalidBankTransferException if either account is unknown, inactive, or of the wrong
     *     kind, or if a cash movement reaches {@code SettingKeys.CASH_PAYMENT_LIMIT}
     */
    BankTransferView record(NewBankTransfer request);

    /**
     * Edits a transfer in place (Q13), with the previous state written to the audit log.
     *
     * @throws InvalidBankTransferException for the conditions {@link #record} checks
     * @throws BankTransferNotFoundException if there is no such transfer
     */
    BankTransferView amend(long transferId, long fromAccountId, long toAccountId,
            LocalDate transferDate, Money amount, String reference, String description);

    /**
     * Posts the mirror of a transfer.
     *
     * <p>Present for completeness rather than necessity — a transfer is editable in place, so an
     * amount typed wrong is corrected rather than reversed. Reversal is for a transfer that did not
     * happen at all.
     *
     * @throws BankTransferNotFoundException if there is no such transfer
     */
    BankTransferView reverse(long transferId, LocalDate reversalDate, String reason);

    Optional<BankTransferView> find(long transferId);

    /** @throws BankTransferNotFoundException if absent */
    BankTransferView require(long transferId);

    /** Transfers touching one account, either side, oldest first. */
    List<BankTransferView> involving(long accountId);

    /** Transfers in a date range, both ends inclusive, oldest first. */
    List<BankTransferView> between(LocalDate from, LocalDate to);

    Optional<BankTransferView> findByJournalEntry(long journalEntryId);
}
