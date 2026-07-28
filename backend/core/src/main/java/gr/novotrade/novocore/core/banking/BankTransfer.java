package gr.novotrade.novocore.core.banking;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

/**
 * Money moved between two of our own accounts.
 *
 * <p><strong>No "Inter Account Transfers" account.</strong> Brief §4 drops it — Manager had one, under
 * Equity, which is the error the brief corrects. A transfer is two asset-account lines and nothing
 * more, which is also why {@code JournalSource.BANK_TRANSFER} is the one document-shaped transaction
 * reversible through the ledger alone: it allocates against nothing and moves no stock, so its entry is
 * the whole of it.
 *
 * <p>Editable in place (Q13), because it is our own record of money moving.
 */
@Entity
@Table(name = "bank_transfer")
class BankTransfer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private Long toAccountId;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "amount_currency", nullable = false, length = 3)
    private String amountCurrency;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    /** For JPA only. */
    protected BankTransfer() {
    }

    BankTransfer(long fromAccountId, long toAccountId, LocalDate transferDate, Money amount,
            String reference, String description) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.transferDate = transferDate;
        this.amount = amount.amount();
        this.amountCurrency = amount.currency().getCurrencyCode();
        this.reference = reference;
        this.description = description;
    }

    void postedAs(long entryId) {
        this.journalEntryId = entryId;
    }

    void amend(long newFromAccountId, long newToAccountId, LocalDate newTransferDate,
            Money newAmount, String newReference, String newDescription) {
        this.fromAccountId = newFromAccountId;
        this.toAccountId = newToAccountId;
        this.transferDate = newTransferDate;
        this.amount = newAmount.amount();
        this.amountCurrency = newAmount.currency().getCurrencyCode();
        this.reference = newReference;
        this.description = newDescription;
    }

    Long getId() {
        return id;
    }

    Long getFromAccountId() {
        return fromAccountId;
    }

    Long getToAccountId() {
        return toAccountId;
    }

    LocalDate getTransferDate() {
        return transferDate;
    }

    Money getAmount() {
        return Money.of(amount, Currency.getInstance(amountCurrency));
    }

    String getReference() {
        return reference;
    }

    String getDescription() {
        return description;
    }

    Long getJournalEntryId() {
        return journalEntryId;
    }
}
