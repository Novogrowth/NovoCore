package gr.novotrade.novocore.core.ledger;

import gr.novotrade.novocore.core.api.account.BalanceSide;
import gr.novotrade.novocore.core.api.ledger.VatDimension;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
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
import java.math.BigDecimal;
import java.util.Currency;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One side of one line: an account, a side, and a positive amount.
 *
 * <p><strong>A named side, never a signed amount.</strong> Representing a credit as a negative debit
 * makes rule 6 a question of whether every producer of a line remembered to negate; the named side makes
 * the balance check a sum per side, which no accidental sign can satisfy. {@code amount > 0} is a CHECK.
 *
 * <p><strong>{@code accountId} is a plain id, not an association.</strong> {@code Account} is
 * package-private within its own slice of the core, so this is the only option available — the same
 * pattern {@code ChargeType} established, and the reason ADR 0003's boundary holds between slices of the
 * core and not merely between the core and its adapters. The foreign key still exists in the database.
 * Consequently a projection needing an account's name resolves it through
 * {@code ChartOfAccountsService}, once per read rather than once per line.
 */
@Entity
@Table(name = "journal_line")
class JournalLine extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private JournalEntry entry;

    /** Position within the entry, so a listing is stable rather than dependent on insertion order. */
    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 6)
    private BalanceSide side;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    /** {@code char(3)}, so the JDBC type has to be stated — see {@code Product.sellingPriceCurrency}. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "amount_currency", nullable = false, length = 3)
    private String amountCurrency;

    @Column(name = "description", length = 500)
    private String description;

    /** Required on a Control-account line, permitted elsewhere. Enforced by trigger as well. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sub_ledger_type", length = 20)
    private SubLedgerType subLedgerType;

    @Column(name = "sub_ledger_id")
    private Long subLedgerId;

    /** Q14. Permitted only on the two VAT accounts; the trigger refuses it anywhere else. */
    @Column(name = "vat_class_id")
    private Long vatClassId;

    @Column(name = "taxable_base")
    private BigDecimal taxableBase;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "taxable_base_currency", length = 3)
    private String taxableBaseCurrency;

    /** For JPA only. */
    protected JournalLine() {
    }

    JournalLine(long accountId, BalanceSide side, Money amount, String description,
            SubLedgerRef subLedgerRef, VatDimension vat) {
        this.accountId = accountId;
        this.side = side;
        this.amount = amount.amount();
        this.amountCurrency = amount.currency().getCurrencyCode();
        this.description = description;
        if (subLedgerRef != null) {
            this.subLedgerType = subLedgerRef.type();
            this.subLedgerId = subLedgerRef.id();
        }
        if (vat != null) {
            this.vatClassId = vat.vatClassId();
            this.taxableBase = vat.taxableBase().amount();
            this.taxableBaseCurrency = vat.taxableBase().currency().getCurrencyCode();
        }
    }

    void attachTo(JournalEntry owner, int position) {
        this.entry = owner;
        this.lineNumber = position;
    }

    Long getId() {
        return id;
    }

    JournalEntry getEntry() {
        return entry;
    }

    int getLineNumber() {
        return lineNumber;
    }

    long getAccountId() {
        return accountId;
    }

    BalanceSide getSide() {
        return side;
    }

    Money getAmount() {
        return new Money(amount, Currency.getInstance(amountCurrency));
    }

    String getDescription() {
        return description;
    }

    /** Null when this line names no sub-ledger entity. */
    SubLedgerRef getSubLedgerRef() {
        return subLedgerType == null ? null : new SubLedgerRef(subLedgerType, subLedgerId);
    }

    /** Null on every line that is not a VAT line, which is nearly all of them. */
    VatDimension getVat() {
        if (vatClassId == null) {
            return null;
        }
        return new VatDimension(
                vatClassId, new Money(taxableBase, Currency.getInstance(taxableBaseCurrency)));
    }
}
