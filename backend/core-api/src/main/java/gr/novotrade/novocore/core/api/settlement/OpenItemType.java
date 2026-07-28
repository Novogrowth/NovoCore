package gr.novotrade.novocore.core.api.settlement;

/**
 * The kinds of document that carry an open amount — brief §6's "each invoice has a computed open
 * amount".
 *
 * <p>An enum rather than a table, for {@code StockLocation}'s reason: each value has behaviour only
 * NovoCore can supply — which control account it sits behind, which direction its open amount faces,
 * and which sources may settle it. A row an operator added at runtime would be storable and unhandled.
 */
public enum OpenItemType {

    /**
     * A sale. Its open amount is what the customer still owes, and it exists only when the invoice's
     * settlement method left it open — a cash or clearing sale is born settled and has none.
     */
    SALES_INVOICE(true),

    /** A supplier's invoice. Its open amount is what we still owe them. */
    PURCHASE_INVOICE(false),

    /**
     * A credit note. <strong>Its open amount faces the other way</strong>: it is money owed back to
     * the customer, settled either by allocating it against one of their invoices — which posts
     * nothing, both sides being Accounts receivable — or by refunding it with an outgoing settlement.
     */
    CREDIT_NOTE(true);

    private final boolean customerSide;

    OpenItemType(boolean customerSide) {
        this.customerSide = customerSide;
    }

    /**
     * True when this document sits behind Accounts receivable, false when behind Accounts payable.
     *
     * <p>What makes an allocation's two ends checkable: a receipt from a customer cannot settle a
     * supplier's invoice, and the pairing rule is one comparison rather than a list of cases.
     */
    public boolean isCustomerSide() {
        return customerSide;
    }
}
