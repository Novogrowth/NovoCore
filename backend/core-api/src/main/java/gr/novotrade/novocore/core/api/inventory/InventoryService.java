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
 * <p><strong>{@link #receive} is the lower layer, not the door.</strong> ADR 0004 makes the Goods
 * Receipt the event that brings stock in, so ordinary operation goes through
 * {@code GoodsReceiptService.record}, which creates the lots <em>and</em> posts. This method stands to
 * that as {@code JournalService.post} stands to a typed transaction: it is how the core reaches the
 * lower layer, and it is not what a controller exposes. It posts nothing by itself, deliberately —
 * the Goods Receipt owns both halves of that transaction, the same way the write-off owns its own.
 *
 * <p><strong>Q17, answered (ADR 0008): aggregate stock may go negative, and it is flagged.</strong> A
 * sale posts even when there is not enough stock behind it — brief §6 already treats goods arriving
 * after their invoice as routine, so blocking a real sale over paperwork timing would contradict the
 * design ADR 0004 exists to support. {@link #consume} records the part FIFO could not fill as a
 * shortfall. A single lot still cannot go below zero, by CHECK; the negative lives on the consumption,
 * which is where the fact actually is, and {@link #stockOf} subtracts it so a product reads negative
 * rather than reading zero and hiding it.
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
     * <p>Brief §5: every purchase creates a lot. This is what a Goods Receipt calls (ADR 0004), and
     * the lot then carries the receipt line it came from. <strong>It posts nothing</strong> — the
     * Goods Receipt debits Inventory and credits GR/IR clearing for the lots it created, in the same
     * transaction, because it is the document that knows the supplier the clearing is against.
     *
     * <p>A lot created here with no {@code goodsReceiptLineId} is stock with no ledger entry behind
     * it, which is why nothing outside the core should be calling this: it exists for the Goods
     * Receipt and, later, for the phase 2b migration, which will bring in opening stock alongside its
     * own opening entry.
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

    /**
     * The lot one delivery line created — brief §5's source document reference, read the other way.
     *
     * <p>One-to-one, by UNIQUE constraint. The link is stored on the lot alone, so this is how the
     * purchasing slice gets from a receipt line to the stock it produced without a second copy of the
     * relationship living on the delivery.
     */
    Optional<InventoryLotView> findLotByReceiptLine(long goodsReceiptLineId);

    /** The same, for a whole delivery at once, so reading a receipt is not a query per line. */
    List<InventoryLotView> lotsFromReceiptLines(java.util.Collection<Long> goodsReceiptLineIds);

    /**
     * Reduces a lot to nothing, for the reversal of the Goods Receipt that created it (Q39).
     *
     * <p>Posts nothing, and is not a general-purpose operation: {@code GoodsReceiptService.reverse} is
     * the only caller, and it posts the mirror entry in the same transaction. Removing stock without
     * the entry would leave the balance sheet carrying goods that are not there — the write-off's rule,
     * applied to the other direction.
     *
     * <p>Refused once anything has happened to the lot, rather than partially undone: consumed,
     * written off, moved, or a serialized unit no longer in stock all mean the goods went somewhere,
     * and a delivery that has been dispersed cannot be un-made. The refusal names what got in the way,
     * because "reverse the receipt" and "write off what is left" are different corrections and the
     * operator has to know which one they are being pushed towards.
     *
     * @throws InvalidInventoryLotException if the lot has been touched since it was received
     * @throws InventoryLotNotFoundException if there is no such lot
     */
    void unreceive(long lotId);

    // ---------------------------------------------------------------------------------------
    // FIFO consumption — Section.INVENTORY. Q17, answered (ADR 0008)
    // ---------------------------------------------------------------------------------------

    /**
     * Takes stock out as a cost of sale, consuming lots FIFO, and posts the cost — in one transaction.
     *
     * <p><strong>One journal line per lot consumed</strong> (brief §6): debit {@code COST_OF_GOODS_SOLD}
     * carrying the lot's {@code SubLedgerRef}, credit {@code INVENTORY} carrying the same. The debit
     * carries a lot reference although COGS is a Standard rather than a Control account, because
     * knowing which lots a cost came out of is the whole reason for having lots — that distinction is
     * one the ledger deliberately permits.
     *
     * <p><strong>FIFO order is {@link #lotsOf}'s order</strong> — acquisition date, then id — so a
     * backdated receipt is consumed where it belongs rather than where it was typed, and there is one
     * definition of the order rather than one per caller. Only lots at a sellable location are
     * candidates: stock in Damaged Goods is still an asset, and selling it is not a decision a costing
     * rule gets to make quietly.
     *
     * <p><strong>Q17: a shortage does not block.</strong> What FIFO cannot fill is recorded as a
     * shortfall on the result, {@link #stockOf} subtracts it so the product genuinely reads negative,
     * and {@link #consumptionsWithShortfall()} is what a review reads. No cost is posted for the
     * unbacked part, because there is no lot to take one from and reaching for the last purchase price
     * would be the silent guess {@code CLAUDE.md} rule 7 forbids — so COGS is understated for as long
     * as the shortfall stands, and the flag is what says so.
     *
     * <p>Nothing is posted when everything consumed was carried at zero, or when nothing could be
     * filled at all. Both are real; {@link StockConsumptionView#costedNothing()} is how they read.
     *
     * @throws InvalidStockConsumptionException if the product is serial-tracked (that is a sale of a
     *     named unit and needs step 9's customer link — see {@link SerializedUnitStatus#SOLD}), a
     *     service, a bundle, or if the quantity has a fraction its unit of measure does not allow
     * @throws gr.novotrade.novocore.core.api.product.ProductNotFoundException if the product is unknown
     */
    StockConsumptionView consume(NewStockConsumption request);

    /**
     * Puts consumed stock back into the lots it came from, and posts the mirror entry.
     *
     * <p>Both halves together, for {@link #reverseWriteOff}'s reason, and the correction route for a
     * consumption that had a shortfall: reverse it, receive the stock that was missing, consume again.
     * That is what ADR 0008 means by refusing to retro-cost a shortfall — the fix is to redo the
     * consumption once there is something to cost it against, not to reach back into a posted figure.
     *
     * <p>Restores each lot by exactly what it gave, so FIFO order is undisturbed. Refused if a lot has
     * since been consumed to the point where restoring would put it above what it ever received.
     *
     * @throws InvalidStockConsumptionException if already reversed, itself a reversal, or a lot cannot
     *     take its quantity back
     * @throws StockConsumptionNotFoundException if there is no such consumption
     */
    StockConsumptionView reverseConsumption(long consumptionId, LocalDate reversalDate, String note);

    /** @throws StockConsumptionNotFoundException if absent */
    StockConsumptionView requireConsumption(long consumptionId);

    /** Every consumption of this product, oldest first — including reversals. */
    List<StockConsumptionView> consumptionsOf(long productId);

    /** Consumptions in a date range, oldest first. Includes reversals, for {@code writeOffsBetween}'s reason. */
    List<StockConsumptionView> consumptionsBetween(LocalDate from, LocalDate to);

    /**
     * Consumptions that drove stock negative and have not been reversed — <strong>Q17's flag</strong>.
     *
     * <p>The compensating control for allowing the sale in the first place, and the same shape as
     * {@link #lotsAt} for Damaged Goods: the model permits a state that needs a human to look at it,
     * so there is a query that finds it. Phase 8's Clearing Checks reads this.
     *
     * <p>Deliberately a query over a flag rather than a review queue. Q15's remainder — whether a
     * flagged item lives in a queue or on the record — is still open, and building a queue here would
     * answer it by accident for everything else in the system that gets flagged.
     */
    List<StockConsumptionView> consumptionsWithShortfall();

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
