package gr.novotrade.novocore.core.api.inventory;

/**
 * Why stock was written off. <strong>Q25, answered: a fixed enum, not free text.</strong>
 *
 * <p>This discharges the step 3 obligation. The chart of accounts deliberately has <em>one</em>
 * {@code Inventory write-off / shrinkage} account rather than three, on the grounds that which of
 * shrinkage, damage or expiry a write-off was belongs on the transaction and not in the chart. That
 * only holds if the transaction actually carries it: with no reason field the single account is
 * strictly less informative than three would have been, and the argument for choosing it disappears.
 *
 * <p><strong>Why an enum and not free text.</strong> The distinction exists to be reported on — a
 * quarter's shrinkage against a quarter's expiry is a purchasing question and a security question
 * respectively, and they get different answers. Free text gives "damaged", "Damaged", "broken in
 * transit" and "ΦΘΟΡΑ" as four separate categories, which is the same as having none. Reportability
 * is the entire point, so the values are code.
 *
 * <p><strong>{@link #OTHER} exists so nobody is forced to lie.</strong> A reason list with no
 * residual gets the nearest-looking value picked, which corrupts the four categories that matter
 * rather than isolating the one that does not fit. Nothing defaults to it — the same stance
 * {@code VatStatus.OTHER} takes.
 *
 * <p><strong>Nothing consumes this yet, and that is deliberate.</strong> A write-off derecognises an
 * asset, so it is a posting, and the journal engine is step 7. Reducing a lot's quantity in step 6
 * without the entry would leave the stock gone and the balance sheet still carrying it — worse than
 * not building it. So the enum lands here with the lots it describes, and the transaction that
 * carries it is a step 7 obligation. Note also brief §5's exception, which that step has to honour:
 * a <em>serialized</em> write-off names the specific unit and uses its own actual cost, with no FIFO
 * logic, because a serialized unit is not pooled stock.
 */
public enum WriteOffReason {

    /** Stock that is simply not there — miscounting, loss, theft. The count correction case. */
    SHRINKAGE,

    /** Physically damaged, whether by us, a courier or a customer. */
    DAMAGE,

    /** Past its date. The reason coffee needs {@code roastDate} on the lot in the first place. */
    EXPIRY,

    /**
     * None of the above, honestly recorded.
     *
     * <p>Present so the four categories above stay clean. If this starts appearing often, the fix is
     * a new value with a name, not a comment field.
     */
    OTHER
}
