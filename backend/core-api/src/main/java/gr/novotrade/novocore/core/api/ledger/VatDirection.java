package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;

/**
 * Which side of VAT a posted line is — <strong>Q14's "separate Output VAT and Input VAT accounts, never
 * netted"</strong>, read back off the ledger.
 *
 * <p>Derived from which VAT account the line posted to, not stored on the line. The account already says
 * it, and a second copy would be free to disagree.
 *
 * <p>The two are kept apart everywhere for a reason worth restating: netting them into a single "VAT
 * payable" figure destroys the two numbers the VAT return is actually made of, and it hides the case that
 * matters most — a period where reclaimable input VAT exceeds output VAT is a refund position, which looks
 * identical to a small liability once the two have been added together.
 */
public enum VatDirection {

    /** VAT charged on our sales. A liability until it is paid over. {@link AccountSystemKey#OUTPUT_VAT}. */
    OUTPUT(AccountSystemKey.OUTPUT_VAT),

    /**
     * VAT charged to us on purchases, reclaimable. Asset-side.
     * {@link AccountSystemKey#INPUT_VAT}.
     */
    INPUT(AccountSystemKey.INPUT_VAT);

    private final AccountSystemKey account;

    VatDirection(AccountSystemKey account) {
        this.account = account;
    }

    /** The account this direction posts to. */
    public AccountSystemKey account() {
        return account;
    }

    /** Which direction a VAT account's system key denotes, or null if the key is not a VAT account. */
    public static VatDirection ofAccount(AccountSystemKey systemKey) {
        for (VatDirection direction : values()) {
            if (direction.account == systemKey) {
                return direction;
            }
        }
        return null;
    }

    /** True when this system key is one of the two VAT accounts a VAT dimension may be attached to. */
    public static boolean isVatAccount(AccountSystemKey systemKey) {
        return ofAccount(systemKey) != null;
    }
}
