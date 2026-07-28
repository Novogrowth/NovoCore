package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Inventory lots and the stock levels derived from them.
 *
 * <p><strong>Two audiences, two sections.</strong> Stock <em>levels</em> are a Products-level read —
 * an order picker needs to know whether there are three left, and Remote/Order Staff has VIEW on
 * {@link Section#PRODUCTS}. Lot <em>detail</em> is not: a lot carries its unit cost, and cost is
 * exactly what {@code PRODUCT_LAST_PURCHASE_PRICE} exists to keep away from that role. So the
 * lot-level reads below are governed by {@link Section#INVENTORY}, which no role is granted by
 * default. Whatever exposes these over HTTP must call {@code requireView} with the right one of the
 * two — the section each method belongs to is stated on it.
 *
 * <p><strong>What this does not do.</strong> Nothing consumes a lot through a sale. FIFO consumption, the
 * Goods Receipt that will create lots in earnest, and the source-document reference all belong to
 * step 8 (ADR 0004). The <em>write-off</em> that carries a {@link WriteOffReason} was step 7's, because
 * it derecognises an asset and therefore posts, and it is built — see the write-off section below.
 *
 * <p><strong>Whether stock may go negative is still open (Q17)</strong> and is not decided here. A
 * single lot cannot go below zero — that is a CHECK constraint — but the aggregate policy belongs
 * with the consumption that could breach it, in step 8.
 */
public interface InventoryService {

    // ---------------------------------------------------------------------------------------
    // Stock levels — Section.PRODUCTS
    // ---------------------------------------------------------------------------------------

    /**
     * How much of this product is where, plus the sellable figure (Q7).
     *
     * <p>Computed on every read from lots and serialized units; nothing is stored (brief §5). For a
     * bundle the answer comes from its components, since a bundle has no stock of its own — how many
     * could be assembled, per location, limited by whichever component runs out first.
     *
     * @throws StockNotApplicableException if the product is a service, or a bundle with no stocked
     *     components. Deliberately not zero — see that exception.
     * @throws gr.novotrade.novocore.core.api.product.ProductNotFoundException if there is no such
     *     product
     */
    StockLevels stockOf(long productId);

    /**
     * The sellable quantity alone — the Inventory location, excluding Damaged Goods and Service.
     *
     * <p>The other half of brief §5's sellability rule (an active WooCommerce listing) belongs to
     * that adapter; this is the half the core owns.
     *
     * @throws StockNotApplicableException as {@link #stockOf}
     */
    Quantity sellableStockOf(long productId);

    /**
     * Stock levels for several products at once, skipping any that have no stock concept.
     *
     * <p>Present so a product list does not turn into one query per row. Products that would throw
     * from {@link #stockOf} are absent from the result rather than represented by zero — the same
     * distinction, kept at scale.
     */
    List<StockLevels> stockOfAll(List<Long> productIds);

    // ---------------------------------------------------------------------------------------
    // Lots — Section.INVENTORY
    // ---------------------------------------------------------------------------------------

    /**
     * Records stock arriving, as a new lot.
     *
     * <p>Brief §5: every purchase creates a lot. From step 8 this is what a Goods Receipt calls
     * (ADR 0004), and the lot will then carry the receipt it came from; until then it stands alone.
     *
     * <p>A serialized request creates the lot and all of its units in one transaction, which is what
     * makes "the unit count is the quantity" true by construction rather than by a later check.
     *
     * @throws InvalidInventoryLotException if the product is unknown, inactive, a service, or a
     *     bundle; if the request's shape disagrees with whether the product is serial-tracked; if the
     *     quantity is not positive or has a fraction the product's unit of measure does not allow; or
     *     if a serial number is duplicated in the request or already held by another unit
     */
    InventoryLotView receive(NewInventoryLot request);

    Optional<InventoryLotView> findLot(long lotId);

    /** @throws InventoryLotNotFoundException if absent */
    InventoryLotView requireLot(long lotId);

    /**
     * Every lot of this product, oldest first.
     *
     * <p>Ordered by acquisition date then id, which is FIFO order — the order step 8 will consume
     * them in, stated here so there is one definition of it rather than one per caller. Includes
     * exhausted lots, because a lot that is empty is still the history of what was bought.
     */
    List<InventoryLotView> lotsOf(long productId);

    /** Lots of this product with something still in them, oldest first. FIFO's candidate list. */
    List<InventoryLotView> openLotsOf(long productId);

    /**
     * Lots holding stock at one location, oldest first.
     *
     * <p><strong>This is what phase 8's Damaged Goods check reads.</strong> The step 3 decision keeps
     * a move to {@link StockLocation#DAMAGED_GOODS} posting-free, so nothing in the model forces the
     * write-off that eventually derecognises it; a Clearing Check surfacing lots aging there is the
     * agreed compensating control, and this is the query it needs. Serial-tracked lots appear when any
     * of their on-hand units is at the location.
     */
    List<InventoryLotView> lotsAt(StockLocation location);

    /**
     * Moves pooled stock to another location. Posts nothing — the step 3 decision.
     *
     * <p>Stock at Damaged Goods is unsellable and still an asset at cost. Impairing it on the move
     * would contradict brief §5's plain Location model, so the loss is recognised by the write-off
     * and by nothing else.
     *
     * @throws InvalidInventoryLotException if the lot is serial-tracked — move its units instead, one
     *     machine going out for repair does not move the others — or if the lot is exhausted
     */
    InventoryLotView moveLot(long lotId, StockLocation destination);

    /**
     * The unit cost of the most recent lot of this product, which is Q6's "last purchase price,
     * computed rather than stored".
     *
     * <p><strong>Step 10 obligation.</strong> This is the purchase price only while nothing has been
     * allocated onto a lot. Brief §5 says a lot's unit cost includes allocated landed costs, so once
     * step 10 exists a freight allocation will move this figure away from what the supplier actually
     * charged, and the last <em>purchase</em> price will have to come from the purchase invoice line
     * instead. Recorded here because the value is right today and the day it stops being right is
     * knowable in advance.
     *
     * <p>Behind {@code PRODUCT_LAST_PURCHASE_PRICE} when read through {@code ProductView}; reading it
     * here is a {@link Section#INVENTORY} operation for the same reason.
     */
    Optional<UnitCost> lastPurchaseCostOf(long productId);

    // ---------------------------------------------------------------------------------------
    // Serialized units — Section.INVENTORY
    // ---------------------------------------------------------------------------------------

    /** The units in a lot, by serial number. Empty for pooled stock, which has no units. */
    List<SerializedUnitView> unitsOf(long lotId);

    /** Every unit of this product across its lots, on hand or not, by serial number. */
    List<SerializedUnitView> unitsOfProduct(long productId);

    /**
     * One unit by its serial number, for a scanner or a warranty lookup.
     *
     * <p>Empty rather than throwing: an unrecognised serial is an ordinary outcome, the same as an
     * unrecognised barcode.
     *
     * <p><strong>Serial numbers are unique across all stock</strong>, which is a slightly stronger
     * claim than the world supports — two manufacturers could in principle issue the same string. It
     * is still the right constraint: within one business's stock, the same serial appearing twice is
     * overwhelmingly a duplicate scan or a unit received twice, and catching that is worth more than
     * accommodating a collision nobody has seen. If a genuine one ever appears it becomes a
     * per-product uniqueness rule, deliberately, rather than being discovered as a silent overwrite.
     */
    Optional<SerializedUnitView> findUnitBySerialNumber(String serialNumber);

    /** @throws SerializedUnitNotFoundException if absent */
    SerializedUnitView requireUnit(long unitId);

    /**
     * Moves one unit to another location. Posts nothing, for the reason {@link #moveLot} gives.
     *
     * @throws InvalidInventoryLotException if the unit is no longer on hand — a sold or written-off
     *     unit is not ours to move
     */
    SerializedUnitView moveUnit(long unitId, StockLocation destination);

    /** Units on hand at one location, by serial number. The serialized half of {@link #lotsAt}. */
    List<SerializedUnitView> unitsAt(StockLocation location);

    // ---------------------------------------------------------------------------------------
    // Write-offs — Section.INVENTORY
    // ---------------------------------------------------------------------------------------

    /**
     * Writes stock off, reducing the lot and posting the loss in one transaction.
     *
     * <p><strong>This is what the single {@code Inventory write-off / shrinkage} account was chosen
     * against three for.</strong> The reason lives on the transaction, and it lives there because it has to
     * be reportable: a quarter's shrinkage is a security question and a quarter's expiry is a purchasing
     * one, and they get different answers.
     *
     * <p>Posts a debit to {@code INVENTORY_WRITE_OFF} and a credit to {@code INVENTORY}, both carrying the
     * lot's {@code SubLedgerRef} — the credit because Inventory is a Control account and must, the debit
     * because knowing which lot a loss came out of is the point of having lots. The amount is the lot's
     * unit cost extended across the quantity, rounded once with the mode from
     * {@code SettingKeys.LEDGER_ROUNDING_MODE}.
     *
     * <p><strong>Nothing is posted when the lot's unit cost is zero</strong>, and the stock still leaves. A
     * free sample derecognises nothing because nothing was carried, and a zero-amount journal entry is
     * refused by the ledger. {@link StockWriteOffView#derecognisedNothing()} is how that case reads.
     *
     * <p>Moving a lot to {@link StockLocation#DAMAGED_GOODS} remains posting-free (the step 3 decision).
     * This is the second step nothing forces, which is why phase 8's Clearing Checks has to surface lots
     * aging there — see {@link #lotsAt}.
     *
     * @throws InvalidStockWriteOffException if the quantity is not positive, exceeds what the lot has
     *     left, or has a fraction the product's unit of measure does not allow; if the request's shape
     *     disagrees with whether the lot is serial-tracked; or if the named unit is not on hand
     * @throws InventoryLotNotFoundException if there is no such lot
     * @throws SerializedUnitNotFoundException if there is no such unit, or it belongs to another lot
     */
    StockWriteOffView writeOff(NewStockWriteOff request);

    /**
     * Reverses a write-off: restores the quantity or the unit's status, and posts the mirror entry.
     *
     * <p>Both halves together, because either alone is worse than neither — restoring the stock without
     * the entry leaves the goods on the shelf and the loss still in the accounts, and reversing the entry
     * without the stock does the opposite. This is also why
     * {@code JournalService.reverse} refuses an {@code INVENTORY_WRITE_OFF} entry outright and names this
     * method instead: the ledger cannot see the quantity it would be stranding.
     *
     * <p>A write-off with no journal entry (the zero-cost case) reverses the stock and posts nothing, for
     * the same reason it posted nothing.
     *
     * @param reversalDate the accounting date of the restoration. A new fact, so normally today rather
     *     than the original's date.
     * @throws InvalidStockWriteOffException if the write-off has already been reversed, is itself a
     *     reversal, or if restoring the quantity would put the lot above what it originally received
     * @throws StockWriteOffNotFoundException if there is no such write-off
     */
    StockWriteOffView reverseWriteOff(long writeOffId, LocalDate reversalDate, String note);

    /** @throws StockWriteOffNotFoundException if absent */
    StockWriteOffView requireWriteOff(long writeOffId);

    /** Every write-off against one lot, oldest first — including the reversals. */
    List<StockWriteOffView> writeOffsOf(long lotId);

    /**
     * Write-offs in a date range, oldest first — what a shrinkage-by-reason report reads.
     *
     * <p>Includes reversals, which is deliberate: a report that silently dropped them would show a loss
     * that was corrected as though it stood, and netting them out is the reader's decision rather than
     * this query's.
     */
    List<StockWriteOffView> writeOffsBetween(LocalDate from, LocalDate to);
}
