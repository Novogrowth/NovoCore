package gr.novotrade.novocore.core.banking;

import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountView;
import gr.novotrade.novocore.core.api.account.ChartOfAccountsService;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.banking.BankTransferNotFoundException;
import gr.novotrade.novocore.core.api.banking.BankTransferService;
import gr.novotrade.novocore.core.api.banking.BankTransferView;
import gr.novotrade.novocore.core.api.banking.InvalidBankTransferException;
import gr.novotrade.novocore.core.api.banking.NewBankTransfer;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.ledger.NewJournalEntry;
import gr.novotrade.novocore.core.api.ledger.NewJournalLine;
import gr.novotrade.novocore.core.api.settings.SettingKeys;
import gr.novotrade.novocore.core.api.settings.SettingsService;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transfers between our own accounts — the smallest of brief §6's six typed transactions.
 *
 * <p>Two lines and nothing more, which is what makes it the one document-shaped transaction reversible
 * through the ledger alone. {@link #reverse} here is a thin wrapper keeping the document in step, not a
 * second reversal mechanism.
 */
@Service
class BankTransferServiceImpl implements BankTransferService {

    private static final String ENTITY_TYPE = "BankTransfer";

    private final BankTransferRepository transfers;
    private final ChartOfAccountsService chartOfAccounts;
    private final JournalService journal;
    private final SettingsService settings;
    private final AuditLogService auditLog;

    BankTransferServiceImpl(BankTransferRepository transfers,
            ChartOfAccountsService chartOfAccounts, JournalService journal, SettingsService settings,
            AuditLogService auditLog) {
        this.transfers = transfers;
        this.chartOfAccounts = chartOfAccounts;
        this.journal = journal;
        this.settings = settings;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional
    public BankTransferView record(NewBankTransfer request) {
        Objects.requireNonNull(request, "request");
        AccountView from = requireMovableAccount(request.fromAccountId());
        AccountView to = requireMovableAccount(request.toAccountId());
        requireWithinCashLimit(from, to, request.amount());

        long entryId = journal.post(NewJournalEntry.of(
                request.transferDate(),
                describe(request.description(), from, to, request.reference()),
                JournalSource.BANK_TRANSFER,
                linesFor(from, to, request.amount()))).id();

        BankTransfer transfer = new BankTransfer(from.id(), to.id(), request.transferDate(),
                request.amount(), request.reference(), request.description());
        transfer.postedAs(entryId);
        BankTransfer saved = transfers.save(transfer);

        auditLog.record("bank-transfer.recorded", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "from", from.name(),
                "to", to.name(),
                "transferDate", saved.getTransferDate().toString(),
                "amount", saved.getAmount().toString(),
                "journalEntry", String.valueOf(entryId)));

        return toView(saved);
    }

    @Override
    @Transactional
    public BankTransferView amend(long transferId, long fromAccountId, long toAccountId,
            LocalDate transferDate, Money amount, String reference, String description) {
        Objects.requireNonNull(transferDate, "transferDate");
        Objects.requireNonNull(amount, "amount");
        BankTransfer transfer = load(transferId);

        if (fromAccountId == toAccountId) {
            throw new InvalidBankTransferException(
                    "A transfer from an account to itself is two lines that cancel: it balances, it "
                            + "states nothing, and it appears on that account's ledger twice.");
        }
        if (!amount.isPositive()) {
            throw new InvalidBankTransferException(
                    "Transfer amount " + amount + " is not positive. Which way the money went is said "
                            + "by which account is which, not by the sign.");
        }
        AccountView from = requireMovableAccount(fromAccountId);
        AccountView to = requireMovableAccount(toAccountId);
        requireWithinCashLimit(from, to, amount);

        // Q13: the previous state goes to the audit log before it is overwritten.
        auditLog.record("bank-transfer.amended", ENTITY_TYPE, String.valueOf(transferId), Map.of(
                "previousFromAccountId", String.valueOf(transfer.getFromAccountId()),
                "previousToAccountId", String.valueOf(transfer.getToAccountId()),
                "previousDate", transfer.getTransferDate().toString(),
                "previousAmount", transfer.getAmount().toString(),
                "newFromAccountId", String.valueOf(fromAccountId),
                "newToAccountId", String.valueOf(toAccountId),
                "newDate", transferDate.toString(),
                "newAmount", amount.toString()));

        transfer.amend(fromAccountId, toAccountId, transferDate, amount,
                blankToNull(reference), blankToNull(description));

        journal.amend(transfer.getJournalEntryId(), transferDate,
                describe(transfer.getDescription(), from, to, transfer.getReference()),
                linesFor(from, to, amount));

        return toView(transfer);
    }

    @Override
    @Transactional
    public BankTransferView reverse(long transferId, LocalDate reversalDate, String reason) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        BankTransfer transfer = load(transferId);

        journal.reverse(transfer.getJournalEntryId(), reversalDate,
                "Reversal of bank transfer " + transferId
                        + (reason == null || reason.isBlank() ? "" : " — " + reason.trim()));

        auditLog.record("bank-transfer.reversed", ENTITY_TYPE, String.valueOf(transferId), Map.of(
                "reversalDate", reversalDate.toString(),
                "amount", transfer.getAmount().toString()));

        return toView(transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BankTransferView> find(long transferId) {
        return transfers.findById(transferId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public BankTransferView require(long transferId) {
        return toView(load(transferId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankTransferView> involving(long accountId) {
        return transfers.findInvolving(accountId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankTransferView> between(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range " + from + " to " + to + " runs backwards. An empty result would look "
                            + "identical to a period with no transfers in it.");
        }
        return transfers.findByTransferDateBetweenOrderByTransferDateAscIdAsc(from, to).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BankTransferView> findByJournalEntry(long journalEntryId) {
        return transfers.findByJournalEntryId(journalEntryId).map(this::toView);
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private static List<NewJournalLine> linesFor(AccountView from, AccountView to, Money amount) {
        return List.of(
                NewJournalLine.debit(to.id(), amount),
                NewJournalLine.credit(from.id(), amount));
    }

    private static String describe(
            String description, AccountView from, AccountView to, String reference) {
        if (description != null && !description.isBlank()) {
            return description;
        }
        return "Transfer " + from.name() + " → " + to.name()
                + (reference == null ? "" : " — " + reference);
    }

    /**
     * Both ends have to be accounts money can be held in.
     *
     * <p>A transfer into an expense account is not a transfer, and letting one through would be a
     * payment recorded as an internal movement — which is how money leaves the business without
     * appearing to.
     */
    private AccountView requireMovableAccount(long accountId) {
        AccountView account = chartOfAccounts.requireAccount(accountId);
        if (!account.active()) {
            throw new InvalidBankTransferException(
                    "Account '" + account.name() + "' is inactive, so nothing new may post to it.");
        }
        if (account.kind() != AccountKind.BANK_CASH
                && account.kind() != AccountKind.PARTNER_CLEARING) {
            throw new InvalidBankTransferException(
                    "Account '" + account.name() + "' is " + account.kind() + ", so money cannot be "
                            + "transferred into or out of it. A transfer moves money between a bank "
                            + "account, the cash box, and a partner clearing account.");
        }
        return account;
    }

    private void requireWithinCashLimit(AccountView from, AccountView to, Money amount) {
        boolean cashIsInvolved = from.systemKey() == AccountSystemKey.CASH
                || to.systemKey() == AccountSystemKey.CASH;
        if (!cashIsInvolved) {
            return;
        }
        Money limit = settings.requireEurAmount(SettingKeys.CASH_PAYMENT_LIMIT);
        if (amount.compareTo(limit) >= 0) {
            throw new InvalidBankTransferException(
                    "A cash movement of " + amount + " reaches the legal cash limit of " + limit
                            + " and is blocked. Under N. 5301/2026 the penalty runs to double the "
                            + "cash amount, so this is refused rather than flagged.");
        }
    }

    private BankTransfer load(long transferId) {
        return transfers.findById(transferId)
                .orElseThrow(() -> new BankTransferNotFoundException(transferId));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private BankTransferView toView(BankTransfer transfer) {
        AccountView from = chartOfAccounts.requireAccount(transfer.getFromAccountId());
        AccountView to = chartOfAccounts.requireAccount(transfer.getToAccountId());
        return new BankTransferView(
                transfer.getId(),
                from.id(),
                from.name(),
                to.id(),
                to.name(),
                transfer.getTransferDate(),
                transfer.getAmount(),
                transfer.getReference(),
                transfer.getDescription(),
                transfer.getJournalEntryId());
    }
}
