package gr.novotrade.novocore.core.settlement;

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
 * Unallocated customer credit as a standalone document — <strong>Q16, answered</strong>.
 *
 * <p><strong>It posts nothing.</strong> The money is already in Accounts receivable: the receipt that
 * overpaid put it there. This document says whose it is and that it is available to settle something
 * later, which is the open-item layer's job; a journal entry for it would debit and credit AR for the
 * same amount.
 *
 * <p>Created only when the caller states that the remainder is credit. A receipt whose allocations come
 * to less than its amount has two entirely different meanings — the customer overpaid, or nobody has
 * finished matching a remittance — and guessing between them is what rule 7 forbids.
 */
@Entity
@Table(name = "customer_credit")
class CustomerCredit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** Required: credit arises from money actually received, never conjured. */
    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "credit_date", nullable = false)
    private LocalDate creditDate;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "amount_currency", nullable = false, length = 3)
    private String amountCurrency;

    @Column(name = "description", length = 500)
    private String description;

    /** For JPA only. */
    protected CustomerCredit() {
    }

    CustomerCredit(long customerId, long settlementId, LocalDate creditDate, Money amount,
            String description) {
        this.customerId = customerId;
        this.settlementId = settlementId;
        this.creditDate = creditDate;
        this.amount = amount.amount();
        this.amountCurrency = amount.currency().getCurrencyCode();
        this.description = description;
    }

    void changeAmount(Money newAmount) {
        this.amount = newAmount.amount();
        this.amountCurrency = newAmount.currency().getCurrencyCode();
    }

    Long getId() {
        return id;
    }

    Long getCustomerId() {
        return customerId;
    }

    Long getSettlementId() {
        return settlementId;
    }

    LocalDate getCreditDate() {
        return creditDate;
    }

    Money getAmount() {
        return Money.of(amount, Currency.getInstance(amountCurrency));
    }

    String getDescription() {
        return description;
    }
}
