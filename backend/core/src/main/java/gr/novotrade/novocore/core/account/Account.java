package gr.novotrade.novocore.core.account;

import gr.novotrade.novocore.core.api.account.AccountKind;
import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.account.AccountType;
import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One account in the chart.
 *
 * <p><strong>No normal-balance-side field.</strong> Brief §5's draft field list has one; it is
 * deliberately absent. The side is derived from {@link AccountType} by {@link #normalBalance()},
 * so an account whose type and side disagree cannot be represented — a stored side would let a
 * fixed-asset account be saved as credit-normal and misreport the balance sheet with nothing to
 * catch it.
 *
 * <p>{@code type} and {@code kind} are independent dimensions rather than one enum. Accumulated
 * depreciation is {@link AccountType#CONTRA_ASSET} <em>and</em> {@link AccountKind#CONTROL}, and
 * collapsing them would need a value per combination.
 *
 * <p>The balance is not here either. It is the sum of the account's journal lines, computed on
 * read once the ledger exists in step 7. A stored running balance is the classic thing that
 * drifts from the entries it is supposed to summarise.
 */
@Entity
@Table(name = "account")
class Account extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Always null for now — codes and ΕΛΠ mapping come from the accountant later. Nothing may
     * key off it in the meantime; {@link #systemKey} is what code uses.
     */
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false, length = 20)
    private AccountKind kind;

    /** Present exactly when {@link #kind} is {@link AccountKind#CONTROL}; the database enforces it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sub_ledger_type", length = 20)
    private SubLedgerType subLedgerType;

    /** Set only by the seed migration. Never assigned by application code — see the enum's javadoc. */
    @Enumerated(EnumType.STRING)
    @Column(name = "system_key", length = 60)
    private AccountSystemKey systemKey;

    // Lazy: the group is only needed when the chart is assembled, and the service loads groups
    // with their own query rather than walking here per account.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private AccountGroup group;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "expected_to_clear", nullable = false)
    private boolean expectedToClear;

    @Column(name = "elp_code", length = 20)
    private String elpCode;

    /** For JPA only. */
    protected Account() {
    }

    Account(String name, AccountType type, AccountKind kind, SubLedgerType subLedgerType,
            AccountGroup group, int displayOrder, boolean expectedToClear) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.subLedgerType = subLedgerType;
        this.group = group;
        this.displayOrder = displayOrder;
        this.expectedToClear = expectedToClear;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    AccountType getType() {
        return type;
    }

    AccountKind getKind() {
        return kind;
    }

    SubLedgerType getSubLedgerType() {
        return subLedgerType;
    }

    AccountSystemKey getSystemKey() {
        return systemKey;
    }

    AccountGroup getGroup() {
        return group;
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    boolean isActive() {
        return active;
    }

    boolean isExpectedToClear() {
        return expectedToClear;
    }

    String getElpCode() {
        return elpCode;
    }

    /** Derived from {@link #getType()}, never stored. */
    BalanceSide normalBalance() {
        return type.normalBalance();
    }

    /** True for the accounts NovoCore's own posting rules locate by key. */
    boolean isSystemAccount() {
        return systemKey != null;
    }

    void rename(String newName) {
        this.name = newName;
    }

    void moveTo(int newDisplayOrder) {
        this.displayOrder = newDisplayOrder;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }

    /**
     * Records the ΕΛΠ mapping once the accountant supplies it.
     *
     * <p>Blank is normalised to null so that "no mapping" has exactly one representation — the
     * unique index on {@code code} treats {@code ''} as a real value, and two accounts sharing it
     * would collide for a reason nobody would guess from the error.
     */
    void setElpCode(String newElpCode) {
        this.elpCode = (newElpCode == null || newElpCode.isBlank()) ? null : newElpCode.trim();
    }
}
