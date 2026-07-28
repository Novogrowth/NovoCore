package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.account.AccountSystemKey;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;

/**
 * Whose sub-ledger a settlement moves against.
 *
 * <p>Independent of {@link SettlementDirection}, because all four combinations are real: a receipt
 * from a customer, a payment to a supplier, a refund <em>to</em> a customer against a credit note, and
 * a refund <em>from</em> a supplier. Folding the two into one enum would make the last two
 * unrepresentable, which is how a refund ends up recorded as a negative receipt.
 */
public enum PartyType {

    /** A customer. Their control account is Accounts receivable. */
    CUSTOMER(AccountSystemKey.ACCOUNTS_RECEIVABLE, SubLedgerType.CUSTOMER),

    /** A supplier. Their control account is Accounts payable. */
    SUPPLIER(AccountSystemKey.ACCOUNTS_PAYABLE, SubLedgerType.SUPPLIER);

    private final AccountSystemKey controlAccount;
    private final SubLedgerType subLedgerType;

    PartyType(AccountSystemKey controlAccount, SubLedgerType subLedgerType) {
        this.controlAccount = controlAccount;
        this.subLedgerType = subLedgerType;
    }

    public AccountSystemKey controlAccount() {
        return controlAccount;
    }

    public SubLedgerType subLedgerType() {
        return subLedgerType;
    }

    /** The sub-ledger reference every line against the control account has to carry. */
    public SubLedgerRef refTo(long partyId) {
        return SubLedgerRef.of(subLedgerType, partyId);
    }

    /** True when a document of this open-item kind belongs to this party's side of the ledger. */
    public boolean owns(OpenItemType openItemType) {
        return openItemType.isCustomerSide() == (this == CUSTOMER);
    }
}
