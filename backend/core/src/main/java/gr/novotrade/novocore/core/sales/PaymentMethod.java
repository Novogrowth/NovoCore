package gr.novotrade.novocore.core.sales;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A payment method the business authored.
 *
 * <p>⚠️ <strong>R4 replaced a seed-only row keyed by an enum constant with a user-created row.</strong>
 * V35's argument for having no surrogate id was sound and its premise is gone: the enum constant was
 * the identity because the rows were a fixed set, and a user-created row is a member of no enum.
 *
 * <p>⚠️ <strong>Both references are mandatory and neither is a convenience.</strong> The AADE article
 * supplies the myDATA code — which is therefore <em>not</em> stored here, so nothing can disagree —
 * and the account is where the money lands. <em>Two POS terminals can share AADE code 7 and settle
 * into different bank accounts.</em>
 *
 * <p>⚠️ <strong>There is no {@code settles_immediately} column.</strong> A method settles immediately
 * when the account it names is a bank, cash or partner-clearing account, and does not when it names
 * the Accounts receivable control account. One fact, derived where it is needed.
 *
 * <p>⚠️ <strong>Ids rather than {@code @ManyToOne}</strong>, deliberately: this entity is
 * core-internal and its service resolves both references through the owning services, so a lazy
 * association here would buy nothing and could blow up outside the transaction — the trap
 * {@code CLAUDE.md} names beside proxy self-invocation.
 */
@Entity
@Table(name = "payment_method")
class PaymentMethod extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "abbreviation", nullable = false, length = 20)
    private String abbreviation;

    @Column(name = "description", nullable = false, length = 120)
    private String description;

    @Column(name = "aade_payment_method_id", nullable = false)
    private Long aadePaymentMethodId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** Ordering only, freely editable, and NOT subject to the in-use freeze. See {@code V34}. */
    @Column(name = "sort_code", nullable = false)
    private int sortCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected PaymentMethod() {
    }

    PaymentMethod(String abbreviation, String description, long aadePaymentMethodId, long accountId,
            int sortCode) {
        this.abbreviation = abbreviation;
        this.description = description;
        this.aadePaymentMethodId = aadePaymentMethodId;
        this.accountId = accountId;
        this.sortCode = sortCode;
    }

    Long getId() {
        return id;
    }

    String getAbbreviation() {
        return abbreviation;
    }

    String getDescription() {
        return description;
    }

    Long getAadePaymentMethodId() {
        return aadePaymentMethodId;
    }

    Long getAccountId() {
        return accountId;
    }

    int getSortCode() {
        return sortCode;
    }

    boolean isActive() {
        return active;
    }

    void changeAbbreviation(String newAbbreviation) {
        this.abbreviation = newAbbreviation;
    }

    void describe(String newDescription) {
        this.description = newDescription;
    }

    void changeArticle(long newArticleId) {
        this.aadePaymentMethodId = newArticleId;
    }

    void changeAccount(long newAccountId) {
        this.accountId = newAccountId;
    }

    void changeSortCode(int newSortCode) {
        this.sortCode = newSortCode;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
