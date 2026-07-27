package gr.novotrade.novocore.core.api.account;

/**
 * What an account is, and — derived from that — which side is its normal balance.
 *
 * <p><strong>The normal balance side is derived here and never stored.</strong> Brief §5's draft
 * field list has it as a field on Account; it is deliberately not one. Two columns that must
 * agree ({@code ASSET} and "debit") are two columns that can disagree, and a fixed-asset account
 * accidentally saved as credit-normal would misreport the balance sheet with nothing to catch it.
 * Deriving it means the combination cannot exist.
 *
 * <p>That derivation is the whole reason the contra types exist. A type set of only
 * asset/liability/equity/income/expense forces every asset-classified account to be debit-normal,
 * which is wrong for exactly the accounts named below — and wrong in a way that produces
 * plausible-looking, materially incorrect statements rather than an error.
 */
public enum AccountType {

    /** Debit-normal. Cash, bank, inventory, receivables, fixed assets at cost. */
    ASSET(BalanceSide.DEBIT, StatementSection.BALANCE_SHEET),

    /**
     * An asset-classified account whose normal balance is a <em>credit</em> — accumulated
     * depreciation being the case that matters here.
     *
     * <p>Without this type, accumulated depreciation is either misclassified as a liability
     * (structurally wrong: it belongs against the asset it depreciates) or classified as
     * {@code ASSET} and therefore derived as debit-normal — which flips the sign of every
     * depreciation posting and reports fixed assets at roughly double their carrying value.
     * Both alternatives are silently wrong, so the type set carries this instead.
     */
    CONTRA_ASSET(BalanceSide.CREDIT, StatementSection.BALANCE_SHEET),

    /** Credit-normal. Payables, VAT and tax payable, loans, clearing accounts owed out. */
    LIABILITY(BalanceSide.CREDIT, StatementSection.BALANCE_SHEET),

    /** Credit-normal. Common stock, retained earnings. */
    EQUITY(BalanceSide.CREDIT, StatementSection.BALANCE_SHEET),

    /** Credit-normal. Sales by channel, services, subsidies, interest and other income. */
    INCOME(BalanceSide.CREDIT, StatementSection.PROFIT_AND_LOSS),

    /**
     * An income-classified account whose normal balance is a <em>debit</em> — sales returns and
     * allowances.
     *
     * <p>The same argument as {@link #CONTRA_ASSET}, one statement down. A credit note has to
     * reduce revenue, and it must remain visible as a return rather than disappearing into the
     * net of a channel's Sales account: return rate per channel is the reporting question this
     * answers. Typing returns as {@link #EXPENSE} would post them below the revenue line, so
     * gross revenue would overstate and the expense side would carry something that is not a
     * cost — which is why a seventh type is needed rather than reusing an existing one.
     */
    CONTRA_INCOME(BalanceSide.DEBIT, StatementSection.PROFIT_AND_LOSS),

    /** Debit-normal. COGS, selling, general, administrative, depreciation, finance costs. */
    EXPENSE(BalanceSide.DEBIT, StatementSection.PROFIT_AND_LOSS);

    private final BalanceSide normalBalance;
    private final StatementSection statementSection;

    AccountType(BalanceSide normalBalance, StatementSection statementSection) {
        this.normalBalance = normalBalance;
        this.statementSection = statementSection;
    }

    /**
     * The side on which this type of account normally carries its balance. Derived, never stored.
     */
    public BalanceSide normalBalance() {
        return normalBalance;
    }

    /** Which statement this type appears on. */
    public StatementSection statementSection() {
        return statementSection;
    }

    /**
     * True when this type reduces the class it is classified under rather than adding to it.
     *
     * <p>Reporting needs this to present the account as a deduction — accumulated depreciation
     * beneath the asset it relates to, sales returns beneath the revenue it reduces — instead of
     * as a positive line of its own.
     */
    public boolean isContra() {
        return this == CONTRA_ASSET || this == CONTRA_INCOME;
    }

    /**
     * The class this type belongs to for presentation, with a contra type folded into the class
     * it reduces. {@code CONTRA_ASSET} presents within assets, {@code CONTRA_INCOME} within
     * income.
     */
    public AccountType presentationClass() {
        return switch (this) {
            case CONTRA_ASSET -> ASSET;
            case CONTRA_INCOME -> INCOME;
            default -> this;
        };
    }
}
