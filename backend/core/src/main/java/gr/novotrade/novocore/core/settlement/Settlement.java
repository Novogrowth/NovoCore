package gr.novotrade.novocore.core.settlement;

import gr.novotrade.novocore.core.api.settlement.PartyType;
import gr.novotrade.novocore.core.api.settlement.SettlementDirection;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

/**
 * A Receipt or a Payment — money moving between one of our accounts and one counterparty's sub-ledger.
 *
 * <p><strong>One entity for both</strong>, because structurally they are one thing and every column and
 * rule would be duplicated in a second copy. {@link #direction} decides the side of the entry and which
 * {@code JournalSource} it carries, so Q13's per-source correction policy is untouched and the ledger
 * cannot tell they share a table. The trigger for splitting them is the first column that belongs to
 * one and not the other; there is none.
 *
 * <p><strong>Editable in place (Q13)</strong>, unlike an invoice: this is our own record of money
 * moving, and a mistyped amount is a correction rather than a re-issue. Hence the setters, and hence no
 * reversal columns.
 */
@Entity
@Table(name = "settlement")
class Settlement extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private SettlementDirection direction;

    /** Polymorphic, so no foreign key; the referenced row is checked to exist by trigger. */
    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 20)
    private PartyType partyType;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    /** Our account the money moved through — a bank account, the cash box, a clearing account. */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

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
    protected Settlement() {
    }

    Settlement(SettlementDirection direction, PartyType partyType, long partyId, long accountId,
            LocalDate settlementDate, Money amount, String reference, String description) {
        this.direction = direction;
        this.partyType = partyType;
        this.partyId = partyId;
        this.accountId = accountId;
        this.settlementDate = settlementDate;
        this.amount = amount.amount();
        this.amountCurrency = amount.currency().getCurrencyCode();
        this.reference = reference;
        this.description = description;
    }

    void postedAs(long entryId) {
        this.journalEntryId = entryId;
    }

    /** Q13's editable half. The previous state is written to the audit log before this is called. */
    void amend(long newAccountId, LocalDate newSettlementDate, Money newAmount, String newReference,
            String newDescription) {
        this.accountId = newAccountId;
        this.settlementDate = newSettlementDate;
        this.amount = newAmount.amount();
        this.amountCurrency = newAmount.currency().getCurrencyCode();
        this.reference = newReference;
        this.description = newDescription;
    }

    Long getId() {
        return id;
    }

    SettlementDirection getDirection() {
        return direction;
    }

    PartyType getPartyType() {
        return partyType;
    }

    Long getPartyId() {
        return partyId;
    }

    Long getAccountId() {
        return accountId;
    }

    LocalDate getSettlementDate() {
        return settlementDate;
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
