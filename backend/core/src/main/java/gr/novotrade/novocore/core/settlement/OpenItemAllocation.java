package gr.novotrade.novocore.core.settlement;

import gr.novotrade.novocore.core.api.settlement.AllocationSourceType;
import gr.novotrade.novocore.core.api.settlement.OpenItemRef;
import gr.novotrade.novocore.core.api.settlement.OpenItemType;
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
import java.util.Currency;

/**
 * One allocation: this much of that source settled this much of that document.
 *
 * <p><strong>It posts nothing.</strong> Open item matching is a layer over Accounts receivable and
 * Accounts payable, not a second ledger beside them — the invoice posted, the receipt posted, and
 * saying which one paid the other would debit and credit the same control account for the same amount.
 * That is what makes an allocation freely reducible and releasable without touching a posted entry,
 * and therefore what makes Q13's second half implementable at all.
 *
 * <p>Both ends are polymorphic because there are genuinely three kinds of source and three kinds of
 * target; a nullable foreign key per combination would be nine columns of which seven are always null.
 * Existence is checked by trigger, as a journal line's sub-ledger reference is.
 */
@Entity
@Table(name = "open_item_allocation")
class OpenItemAllocation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private AllocationSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private OpenItemType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /**
     * The order this was applied in, ascending and unique per source.
     *
     * <p><strong>Q13's second half depends on it</strong>: editing a receipt below its allocated total
     * releases allocations most-recent-first, and {@code created_at} cannot answer which was last —
     * several created in one transaction share a timestamp to the microsecond.
     */
    @Column(name = "allocation_order", nullable = false)
    private int allocationOrder;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "amount_currency", nullable = false, length = 3)
    private String amountCurrency;

    /** For JPA only. */
    protected OpenItemAllocation() {
    }

    OpenItemAllocation(AllocationSourceType sourceType, long sourceId, OpenItemRef target,
            int allocationOrder, Money amount) {
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.targetType = target.type();
        this.targetId = target.id();
        this.allocationOrder = allocationOrder;
        this.amount = amount.amount();
        this.amountCurrency = amount.currency().getCurrencyCode();
    }

    /** Reduces an allocation rather than releasing it, when part of it still fits. */
    void reduceTo(Money newAmount) {
        this.amount = newAmount.amount();
        this.amountCurrency = newAmount.currency().getCurrencyCode();
    }

    Long getId() {
        return id;
    }

    AllocationSourceType getSourceType() {
        return sourceType;
    }

    Long getSourceId() {
        return sourceId;
    }

    OpenItemType getTargetType() {
        return targetType;
    }

    Long getTargetId() {
        return targetId;
    }

    OpenItemRef getTarget() {
        return new OpenItemRef(targetType, targetId);
    }

    int getAllocationOrder() {
        return allocationOrder;
    }

    Money getAmount() {
        return Money.of(amount, Currency.getInstance(amountCurrency));
    }
}
