package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.Quantity;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * How much of one product is where. <strong>The shape Q7 asked for: stock per location, plus a
 * computed sellable figure.</strong>
 *
 * <p>Stock is deliberately not one number, which is why V9 refused to put a column on {@code
 * product} for it. Seven on the shelf and seven in Damaged Goods are the same total and completely
 * different answers to "can I sell this?", and a single figure has to pick one of them to be wrong
 * about. Everything here is derived from lots and serialized units on every read — brief §5: never
 * stored.
 *
 * <p><strong>Every location is present, with zero where there is none.</strong> A caller asking for
 * a location it has no stock at gets zero rather than a missing key, because within a stocked product
 * "none in Damaged Goods" is a genuine zero. The dangerous confusion — zero versus "this product
 * does not have stock at all" — is handled one level up: asking a service for its stock throws
 * {@link StockNotApplicableException} rather than answering with an instance of this full of zeros.
 *
 * @param productId the product these levels are for
 * @param byLocation quantity at each {@link StockLocation}; never null, never partial
 */
public record StockLevels(long productId, Map<StockLocation, Quantity> byLocation) {

    public StockLevels {
        Objects.requireNonNull(byLocation, "byLocation");

        Map<StockLocation, Quantity> complete = new EnumMap<>(StockLocation.class);
        for (StockLocation location : StockLocation.values()) {
            complete.put(location, Quantity.ZERO);
        }
        byLocation.forEach((location, quantity) -> {
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(quantity, "quantity");
            complete.put(location, quantity);
        });
        byLocation = Collections.unmodifiableMap(complete);
    }

    /** Nothing anywhere. What a product with no lots yet has. */
    public static StockLevels empty(long productId) {
        return new StockLevels(productId, Map.of());
    }

    public Quantity at(StockLocation location) {
        Objects.requireNonNull(location, "location");
        return byLocation.get(location);
    }

    /**
     * The sellable figure (Q7) — the Inventory location only, excluding Damaged Goods and Service.
     *
     * <p>Summed over {@link StockLocation#sellableLocations()} rather than reading {@code INVENTORY}
     * directly, so the rule lives in one place. This is the half of brief §5's sellability rule the
     * core owns; the other half, an active WooCommerce listing, belongs to that adapter.
     */
    public Quantity sellable() {
        Quantity total = Quantity.ZERO;
        for (StockLocation location : StockLocation.sellableLocations()) {
            total = total.plus(at(location));
        }
        return total;
    }

    /** Everything on hand, sellable or not. What the Inventory control account reconciles against. */
    public Quantity total() {
        Quantity total = Quantity.ZERO;
        for (Quantity quantity : byLocation.values()) {
            total = total.plus(quantity);
        }
        return total;
    }

    /** True when there is sellable stock. The question brief §5's sellability rule actually asks. */
    public boolean hasSellableStock() {
        return sellable().isPositive();
    }

    /**
     * True when there is nothing anywhere.
     *
     * <p>Distinct from {@code !hasSellableStock()}: stock sitting entirely in Damaged Goods is not
     * sellable and is not absent, and telling a back-in-stock reminder the difference matters.
     */
    public boolean isEmpty() {
        return total().isZero();
    }
}
