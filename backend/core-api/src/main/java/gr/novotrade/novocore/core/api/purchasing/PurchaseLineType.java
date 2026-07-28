package gr.novotrade.novocore.core.api.purchasing;

/**
 * What a purchase invoice line buys, which decides where its net amount is debited.
 *
 * <p>Two values, and the split is not a categorisation scheme — it is the difference between a line
 * that will become stock and a line that will not. An {@link #INVENTORY} line debits GR/IR clearing
 * and waits for its Goods Receipt (ADR 0004); an {@link #EXPENSE} line debits an account the operator
 * named and is finished.
 *
 * <p><strong>Brief §7's four-way invoice categorisation is deliberately not built here.</strong> That
 * feature — suggest a category from the product, then from the supplier, then land it in
 * {@code Unclassified — Needs Review} — is about <em>choosing</em> the destination automatically, and
 * it belongs with the myDATA-first import that creates invoices nobody typed. Step 8 records an
 * invoice somebody is entering, so the destination is stated rather than inferred, and nothing lands
 * in the residual account by accident. The two categories brief §7 lists that are missing here, Fixed
 * Asset and Prepaid/Deferred, are {@link #EXPENSE} lines pointed at an asset account today; they earn
 * their own handling when the asset register starts posting, which is still open.
 */
public enum PurchaseLineType {

    /**
     * Goods that become stock. Debits GR/IR clearing rather than Inventory: ADR 0004 makes the Goods
     * Receipt the inventory event, so the invoice only ever states what is owed for the goods, never
     * that they arrived.
     */
    INVENTORY,

    /**
     * Anything that is not stock — electricity, rent, an accountant's fee, freight awaiting
     * allocation. Debits the account named on the line.
     */
    EXPENSE
}
