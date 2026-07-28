package gr.novotrade.novocore.core.api.inventory;

/**
 * Where an individually tracked unit stands. Brief §5's "status" on a serialized unit.
 *
 * <p>Only {@link #IN_STOCK} is reachable in step 6, and the other two are here rather than added
 * later because the stock count is written against this column from the start — it sums units that
 * are {@code IN_STOCK}, so the day a unit is sold the figure is already right without anyone
 * revisiting the query. Both remaining transitions need something that does not exist yet:
 *
 * <ul>
 *   <li>{@link #SOLD} needs the Sales Invoice that names the buyer — step 9. Brief §5 wants the
 *       customer/invoice link recorded on the unit at that point; step 6 deliberately does not carry
 *       a nullable customer id for it, because a unit marked sold to a customer with no document
 *       behind it is a claim nothing can substantiate.
 *   <li>{@link #WRITTEN_OFF} needs the write-off posting — step 7. See {@link WriteOffReason}.
 * </ul>
 */
public enum SerializedUnitStatus {

    /** Ours, and physically somewhere — see the unit's own {@link StockLocation}. */
    IN_STOCK,

    /** Gone to a customer. Set by the Sales Invoice in step 9, which is what names the buyer. */
    SOLD,

    /** Derecognised. Set by the write-off posting in step 7, which carries the reason. */
    WRITTEN_OFF;

    /** True when this unit still counts towards stock on hand. */
    public boolean isOnHand() {
        return this == IN_STOCK;
    }
}
