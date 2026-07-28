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
    WRITTEN_OFF,

    /**
     * The Goods Receipt that brought this unit in was reversed, so it was never really ours (Q39,
     * ADR 0008).
     *
     * <p>A fourth value rather than reusing one of the three above, because none of them is true: the
     * unit is not in stock, it was not sold, and calling it {@link #WRITTEN_OFF} would put a loss that
     * never happened into the shrinkage report the single write-off account was chosen over three for.
     *
     * <p><strong>This status alone does not hold its serial number.</strong> Serial uniqueness across
     * all stock exists to catch a duplicate scan, and the commonest reason to reverse a delivery is
     * that it was entered wrong — so re-entering the same machines correctly must not be blocked by
     * the mistake. The unique index is partial for exactly that.
     */
    UNRECEIVED;

    /** True when this unit still counts towards stock on hand. */
    public boolean isOnHand() {
        return this == IN_STOCK;
    }

    /**
     * True when this unit's serial number still belongs to it and blocks a second record.
     *
     * <p>Everything except {@link #UNRECEIVED}. Stated here rather than as a list at each call site,
     * so the Java check and the partial unique index behind it read the same rule.
     */
    public boolean holdsItsSerialNumber() {
        return this != UNRECEIVED;
    }
}
