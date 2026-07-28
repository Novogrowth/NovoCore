package gr.novotrade.novocore.core.api.account;

/**
 * Stable machine identifiers for the accounts NovoCore's own logic has to locate.
 *
 * <p><strong>Why this exists.</strong> Account codes are deliberately blank (the accountant
 * supplies ΕΛΠ mapping later) and names are operator-editable, so neither is a safe handle. But
 * several posting rules name a specific account: brief §6 auto-posts a sub-threshold residual to
 * "the Rounding account", ADR 0004 absorbs the goods-received/invoice-received timing gap into
 * the GR/IR clearing account, brief §4 allocates freight out of Freight / Landed Cost —
 * Unallocated, and brief §7 lands an uncategorised invoice in Unclassified — Needs Review. The
 * alternative to a key like this is looking the account up by name, which breaks silently the
 * first time someone renames it — and renaming an account is a perfectly reasonable thing to do.
 *
 * <p>Only accounts that code refers to belong here. Most accounts have no key: Advertising
 * budget is chosen by a human, so it needs no machine identity. The set is deliberately small
 * and adding to it should be a deliberate act, because a key is a promise that the account
 * exists and cannot be deleted.
 *
 * <p>A keyed account may still be renamed and reordered freely. Its key, type and kind may not
 * change, and it cannot be deactivated — the posting rule that depends on it has no fallback.
 */
public enum AccountSystemKey {

    /**
     * Residual rounding differences at or below
     * {@code SettingKeys.LEDGER_ROUNDING_THRESHOLD} (brief §6). Legitimately carries a balance
     * on either side, which is why it is an expense account rather than being forced positive.
     */
    ROUNDING_DIFFERENCES,

    /**
     * Goods Received / Invoice Received clearing (ADR 0004). Absorbs the gap in either
     * direction when a Goods Receipt and its Purchase Invoice do not arrive together.
     */
    GOODS_RECEIVED_INVOICE_RECEIVED_CLEARING,

    /** Where an invoice lands when no category could be determined (brief §7). */
    UNCLASSIFIED_NEEDS_REVIEW,

    /**
     * Freight and duty land here on receipt, then allocate proportionally by value into the
     * relevant lots' unit cost, crediting this back to zero (brief §4).
     */
    FREIGHT_LANDED_COST_UNALLOCATED,

    /**
     * Inventory derecognised without a sale — shrinkage, damage, expiry. Separate from
     * {@link #COST_OF_GOODS_SOLD} so that sale-driven COGS stays uncontaminated, while both sit
     * in the COGS group so gross margin still reflects the loss.
     */
    INVENTORY_WRITE_OFF,

    /** Control account behind the customer sub-ledger. */
    ACCOUNTS_RECEIVABLE,

    /** Control account behind the supplier sub-ledger. */
    ACCOUNTS_PAYABLE,

    /** Control account behind the inventory lot sub-ledger. */
    INVENTORY,

    /** Control account behind the asset sub-ledger, at acquisition cost. */
    FIXED_ASSETS_AT_COST,

    /** The {@link AccountType#CONTRA_ASSET} counterpart to {@link #FIXED_ASSETS_AT_COST}. */
    FIXED_ASSETS_ACCUMULATED_DEPRECIATION,

    /**
     * Cost of goods sold. Keyed because FIFO consumption posts here automatically, one line per
     * lot consumed (brief §6).
     */
    COST_OF_GOODS_SOLD,

    /**
     * VAT charged on our sales, held until it is paid over. A liability.
     *
     * <p><strong>Q14, answered: output and input VAT are separate accounts and are never
     * netted.</strong> Step 3 seeded a single {@code VAT payable} account and said in the migration
     * that one was almost certainly insufficient; it was. Netting the two destroys the pair of
     * figures a VAT return is made of, and it hides the case that matters most — a period where
     * reclaimable input VAT exceeds output VAT is a refund position, and once added together that
     * looks identical to a small liability.
     *
     * <p>Keyed because the posting rule for every sales invoice line resolves it at runtime. See
     * {@link gr.novotrade.novocore.core.api.ledger.VatDirection}.
     */
    OUTPUT_VAT,

    /**
     * VAT charged to us on purchases, reclaimable against output VAT. Asset-side, per Q14.
     *
     * <p>An asset rather than a negative liability because that is what it is: a claim on the tax
     * authority. It is also what makes {@link #OUTPUT_VAT}'s balance mean "VAT we collected"
     * rather than "VAT we happen to owe on balance".
     */
    INPUT_VAT,

    /**
     * The expense side of a depreciation posting.
     *
     * <p>Added because step 5 flagged the gap: both fixed-asset control accounts carry a key, so a
     * depreciation entry could find the asset side of itself and would have had to look the expense
     * side up by name — which breaks the first time somebody renames it, the whole reason this enum
     * exists.
     *
     * <p><strong>Nothing posts here yet.</strong> Whether the periodic depreciation run is Phase 1
     * scope or only the register and the calculation is still open, and the statutory rates are
     * still with the accountant. The key is the handle that run will need; creating it now is not
     * building the run.
     */
    DEPRECIATION_EXPENSE
}
