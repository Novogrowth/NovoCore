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
 * <h2>myDATA payment-method codes — annex 8.12</h2>
 *
 * <p>Each value carries AADE's payment-method code where one exists, from annex 8.12 of the myDATA
 * v2.0.1 ERP specification (read from a rasterised page; the eight statutory values are 1 Επαγ.
 * Λογαριασμός Πληρωμών Ημεδαπής, 2 Αλλοδαπής, 3 Μετρητά, 4 Επιταγή, 5 Επί Πιστώσει, 6 Web Banking,
 * 7 POS / e-POS, 8 Άμεσες Πληρωμές IRIS).
 *
 * <p><strong>A constructor argument and no migration</strong>, because this enum is not a table.
 * The alternative — a lookup row per value — would relocate the problem rather than solve it: the
 * behaviour that makes these values an enum at all still could not live in a row.
 *
 * <p>⚠️ <strong>THREE values have no code, not one.</strong> {@link #ACS_COD}, {@link #PAYPAL} and
 * {@link #STRIPE} map to nothing in an eight-value statutory list that knows about domestic and
 * foreign professional payment accounts, cash, cheque, credit, web banking, POS and IRIS — and
 * about none of a courier's cash-on-delivery, PayPal or Stripe. <strong>That is a finding about
 * AADE's list rather than a gap in our knowledge of it</strong>, and all three are listed open. A
 * transmitting caller must refuse a value with no code rather than substituting a plausible one:
 * {@link #requireMydataPaymentCode()} is the way to do that.
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
    CASH(AccountSystemKey.CASH, true, true, 3),

    /** A card at the terminal. Held in POS partner clearing until the acquirer remits. */
    CARD_POS(AccountSystemKey.PARTNER_CLEARING_POS, true, false, 7),

    /** A Skroutz marketplace order, held in Skroutz clearing until Skroutz remits. */
    SKROUTZ(AccountSystemKey.PARTNER_CLEARING_SKROUTZ, true, false, 5),

    /** Cash on delivery collected by ACS, held in courier clearing until ACS remits. */
    ACS_COD(AccountSystemKey.PARTNER_CLEARING_ACS, true, false, null),

    /** PayPal. Processor fees post as expense when the remittance clears, per the step 3 decision. */
    PAYPAL(AccountSystemKey.PAYPAL, true, false, null),

    /** Stripe. Same treatment as {@link #PAYPAL}. */
    STRIPE(AccountSystemKey.STRIPE, true, false, null),

    /**
     * The customer says they will transfer the money to one of our bank accounts.
     *
     * <p>Stays open against Accounts receivable — see the class note. Which bank account it lands in
     * is not known at invoice time and is not guessed; the Receipt names it when the money arrives.
     */
    BANK_DEPOSIT(null, false, false, 1),

    /**
     * Credit terms. Stays open against Accounts receivable until a Receipt settles it.
     *
     * <p>Named {@code ON_ACCOUNT} rather than {@code CREDIT}, because "credit" already means two
     * other things nearby — the credit side of an entry, and a credit note — and a value whose name
     * collides with the ledger's own vocabulary is read wrong at a glance.
     */
    ON_ACCOUNT(null, false, false, 5);

    private final AccountSystemKey settlementAccount;
    private final boolean settlesImmediately;
    private final boolean subjectToCashLimit;
    private final Integer mydataPaymentCode;

    SettlementMethod(AccountSystemKey settlementAccount, boolean settlesImmediately,
            boolean subjectToCashLimit, Integer mydataPaymentCode) {
        this.settlementAccount = settlementAccount;
        this.settlesImmediately = settlesImmediately;
        this.subjectToCashLimit = subjectToCashLimit;
        this.mydataPaymentCode = mydataPaymentCode;
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

    /**
     * AADE's annex 8.12 payment-method code, or empty where the list has none for this method.
     *
     * <p>Empty means <strong>"no mapping exists"</strong> and not "not filled in yet" — the same
     * distinction {@code vat_exemption_reason.mydata_code} draws, and for the same reason: a caller
     * transmitting to myDATA must refuse it rather than sending something that looks close.
     */
    public Optional<Integer> mydataPaymentCode() {
        return Optional.ofNullable(mydataPaymentCode);
    }

    /**
     * The code, for a caller about to transmit one.
     *
     * <p>Throws rather than returning a substitute, because a document transmitted under the wrong
     * payment method is a misdeclared filing that looks successful. The failure names the method
     * that has no mapping instead of surfacing as an AADE rejection later, or worse, not at all.
     */
    public int requireMydataPaymentCode() {
        if (mydataPaymentCode == null) {
            throw new IllegalStateException(
                    name() + " has no myDATA payment-method code. AADE's annex 8.12 list has "
                            + "eight values and none of them is a courier cash-on-delivery, "
                            + "PayPal or Stripe. Supply the mapping rather than sending a "
                            + "plausible substitute.");
        }
        return mydataPaymentCode;
    }
}
