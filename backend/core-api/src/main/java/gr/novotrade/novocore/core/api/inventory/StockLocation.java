package gr.novotrade.novocore.core.api.inventory;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Where a piece of stock physically is. Brief §5's Location, on the lot for pooled stock and on the
 * individual unit for serialized stock — it lives wherever the quantity does.
 *
 * <p><strong>Q7, answered: sellable stock is the Inventory location only.</strong> Damaged Goods and
 * Service are both excluded. That is the whole reason this is not one number: a product can have
 * seven in total and none you may sell.
 *
 * <p><strong>An enum, not a runtime-editable table</strong> — the opposite call from {@code VatClass}
 * and {@code UnitOfMeasure}, and for the reason {@code ProductType} is an enum: every value here has
 * behaviour attached that only NovoCore can supply. {@link #INVENTORY} is what sellability is
 * computed from, and {@link #DAMAGED_GOODS} is what phase 8's Clearing Checks have to single out.
 * A row an operator added at runtime would be storable and unhandled — nothing would know whether
 * stock there may be sold, and the safe default (not sellable) would silently hide stock instead.
 *
 * <p>Named {@code StockLocation} rather than {@code Location}, deliberately: a bare {@code Location}
 * reads as an address, and Customer and Supplier are going to need one of those (Q37).
 *
 * <p><strong>Not a warehouse.</strong> These three are states of stock, not places on a map. Several
 * physical warehouses are a different concept and are not in the brief; if they ever arrive, they
 * arrive alongside this rather than as extra values in it.
 */
public enum StockLocation {

    /**
     * On the shelf and available to sell. The only sellable location (Q7).
     */
    INVENTORY(true),

    /**
     * Held for or inside a repair — a customer's machine awaiting parts, a unit pulled from stock to
     * complete a service job. Still ours and still an asset, but not available to sell.
     */
    SERVICE(false),

    /**
     * Damaged, expired or otherwise unsellable, and still carried at cost.
     *
     * <p><strong>Moving stock here posts nothing</strong> — the step 3 decision. The stock stops
     * being sellable and stays an asset at full cost; only a write-off derecognises it. Nothing in
     * the model forces that second step, which is why <strong>phase 8's Clearing Checks must surface
     * lots aging here</strong>: without it the balance sheet carries worthless stock at cost
     * indefinitely. {@code InventoryService.lotsAt} and {@code unitsAt} are what that check reads.
     */
    DAMAGED_GOODS(false);

    private final boolean sellable;

    StockLocation(boolean sellable) {
        this.sellable = sellable;
    }

    /**
     * Whether stock at this location may be sold (Q7).
     *
     * <p>Brief §5: sellability is never category-based. It is governed by stock at a sellable
     * location and, outside the core, by an active Woo listing — so this half of the rule lives
     * here and is stated once.
     */
    public boolean isSellable() {
        return sellable;
    }

    /**
     * The locations {@code sellableStock} sums over.
     *
     * <p>Derived from the flag rather than written out, so that a second sellable location arriving
     * is one edit above and not a search for every place {@code INVENTORY} was hardcoded.
     */
    public static Set<StockLocation> sellableLocations() {
        EnumSet<StockLocation> sellable = EnumSet.noneOf(StockLocation.class);
        Arrays.stream(values()).filter(StockLocation::isSellable).forEach(sellable::add);
        return Collections.unmodifiableSet(sellable);
    }
}
