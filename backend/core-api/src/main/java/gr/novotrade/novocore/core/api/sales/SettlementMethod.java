package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import java.util.Optional;

/**
 * How a sale was paid for — <strong>brief §6's payment-method settlement automation</strong>.
 *
 * <p>It decides exactly one thing about a sales invoice: <em>which account the invoice debits</em>.
 * Everything else follows from that, and nothing about it is a guess.
 *
 * <ul>
 *   <li>A method that {@linkplain #settlesImmediately() settles immediately} debits its own account
 *       — the cash box, a partner clearing account, PayPal, Stripe — and the invoice is born fully
 *       settled with no open amount. There is nothing for a Receipt to allocate against, because the
 *       money has already arrived.
 *   <li>{@link #BANK_DEPOSIT} and {@link #ON_ACCOUNT} debit Accounts receivable, and the invoice is
 *       an open item until a Receipt allocates against it.
 * </ul>
 *
 * <p><strong>Bank deposit deliberately does <em>not</em> settle on entry</strong>, which brief §6
 * records as a decision taken against convenience: a customer saying they have made a transfer is
 * not the same as the money arriving, and marking the invoice paid on their word puts an unrecovered
 * receivable into cash. It stays open until the Bank Aggregator adapter confirms the matching
 * incoming transaction — roadmap phase 6, feeding the open-item layer step 9 builds.
 *
 * <p><strong>An enum rather than a lookup table</strong>, and that is the opposite call from
 * {@code VatClass} and {@code UnitOfMeasure} for the reason {@code StockLocation} is an enum: every
 * value here has behaviour only NovoCore can supply — the cash limit, whether an invoice is born
 * settled — so a row an operator added at runtime would be storable and unhandled. A second card
 * acquirer is a new value and a migration, deliberately, which is the same stance
 * {@link AccountSystemKey} takes on gaining one.
 */
public enum SettlementMethod {

    /**
     * Notes and coins, into the cash box. Settles immediately.
     *
     * <p><strong>Hard-blocked at or above {@code SettingKeys.CASH_PAYMENT_LIMIT}</strong> — €500,
     * the Greek legal cash limit actively enforced under N. 5301/2026, where penalties reach double
     * the cash amount. Blocked rather than flagged, which is the one place in this design where a
     * check refuses instead of asking for confirmation: the confirmation nobody can give is the
     * legality of the transaction.
     */
    CASH(AccountSystemKey.CASH, true, true),

    /** A card at the terminal. Held in POS partner clearing until the acquirer remits. */
    CARD_POS(AccountSystemKey.PARTNER_CLEARING_POS, true, false),

    /** A Skroutz marketplace order, held in Skroutz clearing until Skroutz remits. */
    SKROUTZ(AccountSystemKey.PARTNER_CLEARING_SKROUTZ, true, false),

    /** Cash on delivery collected by ACS, held in courier clearing until ACS remits. */
    ACS_COD(AccountSystemKey.PARTNER_CLEARING_ACS, true, false),

    /** PayPal. Processor fees post as expense when the remittance clears, per the step 3 decision. */
    PAYPAL(AccountSystemKey.PAYPAL, true, false),

    /** Stripe. Same treatment as {@link #PAYPAL}. */
    STRIPE(AccountSystemKey.STRIPE, true, false),

    /**
     * The customer says they will transfer the money to one of our bank accounts.
     *
     * <p>Stays open against Accounts receivable — see the class note. Which bank account it lands in
     * is not known at invoice time and is not guessed; the Receipt names it when the money arrives.
     */
    BANK_DEPOSIT(null, false, false),

    /**
     * Credit terms. Stays open against Accounts receivable until a Receipt settles it.
     *
     * <p>Named {@code ON_ACCOUNT} rather than {@code CREDIT}, because "credit" already means two
     * other things nearby — the credit side of an entry, and a credit note — and a value whose name
     * collides with the ledger's own vocabulary is read wrong at a glance.
     */
    ON_ACCOUNT(null, false, false);

    private final AccountSystemKey settlementAccount;
    private final boolean settlesImmediately;
    private final boolean subjectToCashLimit;

    SettlementMethod(AccountSystemKey settlementAccount, boolean settlesImmediately,
            boolean subjectToCashLimit) {
        this.settlementAccount = settlementAccount;
        this.settlesImmediately = settlesImmediately;
        this.subjectToCashLimit = subjectToCashLimit;
    }

    /**
     * The account an invoice paid this way debits, or empty when it debits Accounts receivable.
     *
     * <p>Present exactly when {@link #settlesImmediately()}, which is not two facts but one said two
     * ways — a method settles immediately <em>because</em> there is an account the money is already
     * in.
     */
    public Optional<AccountSystemKey> settlementAccount() {
        return Optional.ofNullable(settlementAccount);
    }

    /**
     * True when the invoice is born fully settled and never becomes an open item.
     *
     * <p>Which is also why nothing may be allocated against such an invoice: its open amount is zero
     * from the moment it is recorded, and an allocation against it would settle it twice.
     */
    public boolean settlesImmediately() {
        return settlesImmediately;
    }

    /** True when brief §6's Greek legal cash limit applies. Only {@link #CASH}. */
    public boolean subjectToCashLimit() {
        return subjectToCashLimit;
    }
}
