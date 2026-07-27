package gr.novotrade.novocore.core.api.account;

/**
 * How an account behaves, as distinct from what it is. {@link AccountType} decides which
 * statement an account lands on and which side is normal; kind decides what the system is
 * allowed to do with it.
 *
 * <p>The two are independent on purpose. Cash and Inventory are both {@code ASSET}, but one is
 * selectable as a payment destination and the other requires a lot reference on every line.
 * Collapsing kind into type would need a type per combination, and the seed already contains an
 * account that is {@code CONTRA_ASSET} <em>and</em> {@link #CONTROL} — accumulated depreciation.
 */
public enum AccountKind {

    /** Ordinary account. Posts directly, no sub-ledger, no special handling. */
    STANDARD,

    /**
     * A real bank account or the physical cash box. Selectable as the money side of a Receipt,
     * Payment or Bank Transfer, and the accounts a bank aggregator feed reconciles against
     * (brief §8).
     */
    BANK_CASH,

    /**
     * Money held by a third party on our behalf until they remit it — Skroutz, ACS Courier, the
     * POS provider, and PayPal and Stripe.
     *
     * <p>Distinct from {@link #BANK_CASH} because the balance is not ours to spend and clears
     * only against the partner's own remittance report, not against a bank statement line
     * (brief §6). Clearing Checks in phase 8 monitors these for balances that stop moving.
     */
    PARTNER_CLEARING,

    /**
     * A control account whose detail lives in a sub-ledger: Accounts Receivable behind
     * customers, Accounts Payable behind suppliers, Inventory behind lots, Fixed Assets behind
     * assets, and the GR/IR clearing account behind suppliers (ADR 0004).
     *
     * <p>A Control account declares which
     * {@link gr.novotrade.novocore.core.api.shared.SubLedgerType} sits behind it, and every
     * journal line touching it must carry a reference of that type. That is what stops a
     * customer reference landing on an Accounts Payable line, and what makes the control
     * account's balance reconcilable against the sum of its sub-ledger by construction rather
     * than by a periodic check.
     */
    CONTROL;

    /** True for the kinds a Receipt, Payment or Bank Transfer may name as its money side. */
    public boolean isSettlementTarget() {
        return this == BANK_CASH || this == PARTNER_CLEARING;
    }

    /** True when journal lines on this account must carry a sub-ledger reference. */
    public boolean requiresSubLedgerReference() {
        return this == CONTROL;
    }
}
