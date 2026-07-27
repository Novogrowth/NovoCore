package gr.novotrade.novocore.core.api.shared;

/**
 * The kinds of entity that can sit behind a Control account as its sub-ledger.
 *
 * <p>From brief §4 and §5: Accounts Receivable, Accounts Payable, Inventory and Fixed Assets
 * are each a single control account, with Customers, Suppliers, Products/Lots and Assets as
 * the detail behind them. A Control account's declared type fixes what its journal lines may
 * reference, so a customer reference cannot end up on an Accounts Payable line.
 */
public enum SubLedgerType {

    /** Behind Accounts Receivable. */
    CUSTOMER,

    /** Behind Accounts Payable, and behind the GR/IR clearing account (ADR 0004). */
    SUPPLIER,

    /**
     * Behind Inventory. Deliberately the lot rather than the product: brief §6 requires one
     * journal line per lot consumed, because FIFO costing needs to know which lot a cost came
     * from, and a product-level reference would lose exactly that.
     */
    INVENTORY_LOT,

    /** Behind Fixed assets at cost and Fixed assets accumulated depreciation. */
    ASSET
}
