package gr.novotrade.novocore.core.api.ledger;

/**
 * What produced a journal entry — brief §6's outer layer of the two-layer design, where typed
 * transactions generate raw balanced debit/credit entries.
 *
 * <p>The source is on the entry rather than inferred from its lines because it decides policy, not
 * presentation: <strong>Q13's correction rule is stated per source</strong>, and an entry has to know
 * which rule applies to it before anyone tries to change it.
 *
 * <p><strong>Q13, answered.</strong> Sales and Purchase Invoices — and by Q26 the credit note that
 * corrects one — are <em>immutable once posted</em>, corrected only by a reversing entry. Receipts,
 * Payments, Bank Transfers and Manual Journal Entries are <em>editable in place</em>, with the change
 * recorded in the audit log. The split is not arbitrary: an invoice is a document that has been issued
 * to somebody else and possibly transmitted to AADE, so its content is a matter of external record and
 * editing it would make NovoCore disagree with what the counterparty holds. A receipt is our own
 * record of money moving, and a mistyped amount on one is a correction rather than a re-issue.
 *
 * <p><strong>Q19, answered:</strong> all six typed transactions are Phase 1 scope. Six of the values
 * below therefore have <em>no producer yet</em> — they arrive with steps 8 and 9 — and are declared now
 * because the correction policy above is already decided for them and because the database's
 * {@code journal_entry_source_known} CHECK has to list them. A test asserts the enum and that CHECK
 * agree, so neither can gain a value the other does not have.
 *
 * <p><strong>{@code GOODS_RECEIPT} was deliberately absent until step 8</strong>, so that Q39 — is a
 * Goods Receipt amendable? — had to be answered rather than defaulted. It is answered (ADR 0008: no),
 * and the value exists.
 */
public enum JournalSource {

    /**
     * A purchase invoice. Immutable once posted (Q13).
     *
     * <p>Not reversible through the ledger alone: reversing one has to unwind the GR/IR clearing
     * position and the lots behind it (ADR 0004), which the ledger cannot see.
     */
    PURCHASE_INVOICE(false, false),

    /**
     * A Goods Receipt — the physical delivery verification that creates inventory lots and debits
     * Inventory against GR/IR clearing (ADR 0004).
     *
     * <p><strong>Q39, answered: immutable (ADR 0008).</strong> The same reasoning that made the
     * inventory write-off immutable, and stronger than the invoice's: this posting reflects a physical
     * stock movement, so editing the entry would change what the accounts say arrived without changing
     * the lots that arrived. Once a lot exists, FIFO order, its remaining quantity and its units all
     * depend on it.
     *
     * <p>Not reversible through the ledger alone, for the write-off's reason: reversing the money
     * without un-receiving the lots would leave stock on the shelf that the balance sheet no longer
     * carries. {@code GoodsReceiptService.reverse} does both in one transaction, and refuses outright
     * if anything has already happened to the lots.
     */
    GOODS_RECEIPT(false, false),

    /**
     * A sales invoice. Immutable once posted (Q13).
     *
     * <p>A <em>recording</em> transaction in Phase 1 rather than an issuing one: Prosvasis Go remains
     * the invoicing system of record until roadmap phase 11, so NovoCore records what Go issued. That
     * makes immutability doubly right — the document exists outside NovoCore and cannot be edited here.
     */
    SALES_INVOICE(false, false),

    /**
     * A credit note. <strong>Q26, answered: its own transaction type, not a negative Sales
     * Invoice</strong>, referencing the invoice it corrects and posting against the per-channel
     * {@code Sales returns} contra-revenue account. Immutable once issued — the same policy as the
     * invoice it corrects, for the same reason.
     */
    CREDIT_NOTE(false, false),

    /**
     * Money received. Editable in place (Q13).
     *
     * <p>Not reversible through the ledger alone, because a Receipt owns its allocations against
     * invoices (brief §6's open item matching) and reversing the money without releasing the
     * allocations would leave invoices reported as settled by a receipt that no longer exists.
     */
    RECEIPT(true, false),

    /** Money paid. Editable in place (Q13), and not ledger-reversible, for {@link #RECEIPT}'s reason. */
    PAYMENT(true, false),

    /**
     * Money moved between two of our own accounts. Editable in place (Q13).
     *
     * <p>The one document-shaped transaction that <em>is</em> reversible through the ledger alone: it
     * allocates against nothing and moves no stock, so its entry is the whole of it. Brief §4 drops
     * Manager's "Inter Account Transfers" account for exactly this reason — a transfer is two
     * asset-account lines and nothing more.
     */
    BANK_TRANSFER(true, true),

    /**
     * A raw entry somebody wrote by hand. Editable in place (Q13).
     *
     * <p>The only source with no typed transaction above it: a manual entry <em>is</em> a journal entry,
     * which is why {@code JournalService.postManualEntry} is the one posting method that does not name a
     * source.
     */
    MANUAL_JOURNAL_ENTRY(true, true),

    /**
     * Stock derecognised without a sale — the write-off that carries a
     * {@link gr.novotrade.novocore.core.api.inventory.WriteOffReason}.
     *
     * <p>Immutable, and the reason is worth stating: a write-off reduced a lot's quantity, so editing
     * the entry would change the loss recognised without changing the stock it came out of. Correction
     * is {@code InventoryService.reverseWriteOff}, which restores the quantity and posts the mirror
     * entry together. Not reversible through the ledger alone, for the same reason.
     */
    INVENTORY_WRITE_OFF(false, false);

    private final boolean amendable;
    private final boolean reversibleThroughTheLedgerAlone;

    JournalSource(boolean amendable, boolean reversibleThroughTheLedgerAlone) {
        this.amendable = amendable;
        this.reversibleThroughTheLedgerAlone = reversibleThroughTheLedgerAlone;
    }

    /**
     * Whether a posted entry from this source may be edited in place — <strong>Q13</strong>.
     *
     * <p>Stated here once so that Java, the service layer and the database all read the same answer.
     * The database asks it through the {@code journal_source_is_amendable(varchar)} function, and a test
     * calls that function for every value of this enum and compares, so the two cannot drift.
     */
    public boolean isAmendable() {
        return amendable;
    }

    /**
     * Whether {@code JournalService.reverse} may reverse an entry from this source by itself.
     *
     * <p>False when the source owns state the ledger cannot see — a lot's remaining quantity, a
     * receipt's allocations, a serialized unit's status. For those, the owning service reverses by
     * undoing its own state and posting the mirror entry through {@code post} with
     * {@code reversalOfEntryId} set, which is validated to be an exact mirror. This is not a naming
     * convention: {@code reverse} refuses these sources outright and names the alternative.
     */
    public boolean isReversibleThroughTheLedgerAlone() {
        return reversibleThroughTheLedgerAlone;
    }

    /** True when correction means posting a reversing entry rather than editing this one. */
    public boolean requiresReversalToCorrect() {
        return !amendable;
    }

    /**
     * Whether a transaction from this source may consume stock FIFO — {@code InventoryService.consume}
     * refuses any other.
     *
     * <p>Only the sales invoice, today. Stated as a predicate rather than left implicit because
     * consumption reduces lots <em>and</em> posts cost of goods sold in one transaction, so allowing an
     * arbitrary source would let anything at all derecognise inventory as a cost of sale. A new consumer
     * has to opt in here, deliberately, which is the same stance {@code AccountSystemKey} takes on
     * gaining a value.
     *
     * <p>The write-off is absent on purpose: it derecognises stock too, but as a loss rather than a cost
     * of sale, and it has its own path with its own reason code. The credit note is absent because
     * returning stock is {@code reverseConsumption}, not a second consumption.
     */
    public boolean mayConsumeStock() {
        return this == SALES_INVOICE;
    }
}
