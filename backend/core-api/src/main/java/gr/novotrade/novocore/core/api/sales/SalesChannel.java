package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;

/**
 * Which of the three revenue streams a sale belongs to.
 *
 * <p><strong>Channel exists nowhere in the model except in which account gets credited</strong>, and
 * that is deliberate rather than an omission. Step 3 split both Sales and Sales returns three ways
 * precisely so that return rate stays visible per channel; a single Sales account with a channel
 * column beside it would put the same information somewhere the trial balance cannot see.
 *
 * <p>Named to match brief §5's per-channel customer matching — walk-in/phone, website, Skroutz —
 * with <em>Store &amp; Phone</em> spelled out rather than left to a convention about where phone
 * orders go.
 *
 * <p>A {@code SERVICE} product ignores this and credits {@code Services} instead
 * ({@link AccountSystemKey#SERVICES_INCOME}). That is not an exception to the channel rule so much as
 * a different question: a repair carried out in the shop is not a product sale through a channel, and
 * folding it into one would make the channel figures answer something nobody asked.
 */
public enum SalesChannel {

    /** Walk-in and telephone orders. Phone is named rather than assumed. */
    STORE_AND_PHONE(
            AccountSystemKey.SALES_STORE_AND_PHONE,
            AccountSystemKey.SALES_RETURNS_STORE_AND_PHONE),

    /** The WooCommerce shop at javajives.gr. */
    ECOMMERCE(
            AccountSystemKey.SALES_ECOMMERCE,
            AccountSystemKey.SALES_RETURNS_ECOMMERCE),

    /** The Skroutz marketplace. */
    SKROUTZ(
            AccountSystemKey.SALES_SKROUTZ,
            AccountSystemKey.SALES_RETURNS_SKROUTZ);

    private final AccountSystemKey revenueAccount;
    private final AccountSystemKey returnsAccount;

    SalesChannel(AccountSystemKey revenueAccount, AccountSystemKey returnsAccount) {
        this.revenueAccount = revenueAccount;
        this.returnsAccount = returnsAccount;
    }

    /** The account a sale through this channel credits. */
    public AccountSystemKey revenueAccount() {
        return revenueAccount;
    }

    /**
     * The contra-revenue account a credit note against this channel debits.
     *
     * <p>Contra-revenue rather than a reduction of {@link #revenueAccount()}: netting a return into
     * Sales would make gross revenue and return rate unreadable from the ledger, which is the whole
     * reason step 3 created three of these.
     */
    public AccountSystemKey returnsAccount() {
        return returnsAccount;
    }
}
