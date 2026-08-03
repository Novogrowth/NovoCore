package gr.novotrade.novocore.core.web.inventory;

import gr.novotrade.novocore.core.api.inventory.InventoryLotView;
import gr.novotrade.novocore.core.api.inventory.InventoryService;
import gr.novotrade.novocore.core.api.inventory.NewStockWriteOff;
import gr.novotrade.novocore.core.api.inventory.SerializedUnitView;
import gr.novotrade.novocore.core.api.inventory.StockConsumptionView;
import gr.novotrade.novocore.core.api.inventory.StockLocation;
import gr.novotrade.novocore.core.api.inventory.StockWriteOffView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.InvalidInputException;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.web.Requires;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory lots, serialized units, consumptions and write-offs.
 *
 * <h2>What is deliberately absent, and why it is the most important thing here</h2>
 *
 * <p>{@code InventoryService} has six methods that this controller does not expose and an
 * architecture rule forbids it from calling: {@code receive}, {@code unreceive}, {@code consume},
 * {@code reverseConsumption}, {@code applyLandedCost} and {@code removeLandedCost}. They are the
 * <strong>lower layer</strong> — each moves stock and posts nothing on its own, because the document
 * service that calls them posts the journal entry in the same transaction.
 *
 * <p>An HTTP route to {@code receive} would create a lot with no document and no posting; a route to
 * {@code consume} would take stock out with no sale behind it. Either leaves the Inventory control
 * account disagreeing with what the lots carry — the invariant ADR 0015 restored and
 * {@code WholeScenarioIT} sweeps for. <strong>Stock moves through documents: a Goods Receipt, a
 * Sales Invoice, a Freight Allocation, or the write-off below.</strong>
 *
 * <h2>Write-offs are here because a write-off is itself a document</h2>
 *
 * <p>It reduces the lot or the named unit <em>and posts</em>, in one transaction — either alone
 * being worse than neither. It carries its {@code WriteOffReason}, which is the entire reason step 3
 * chose one write-off account over three.
 *
 * <p>Two behaviours worth knowing before calling it: a lot carried at zero cost posts no entry and
 * the stock still leaves (a free sample derecognises nothing, and the ledger rightly refuses a
 * zero-amount line); and a <em>serialized</em> write-off uses the named unit's own actual cost with
 * no FIFO, by construction, since a write-off always names its lot.
 *
 * <h2>Section.INVENTORY, not PRODUCTS</h2>
 *
 * <p>Everything here carries a unit cost. Stock <em>levels</em> are a product-level read and live on
 * the product route; a <em>lot</em> is what says what those units cost, which is exactly what
 * {@code PRODUCT_LAST_PURCHASE_PRICE} keeps from Remote/Order Staff.
 */
@RestController
@Requires(section = Section.INVENTORY)
class InventoryController {

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    // -------------------------------------------------------------------------------------------
    // Lots
    // -------------------------------------------------------------------------------------------

    /**
     * Lots, by product or by location.
     *
     * <p>{@code location=DAMAGED_GOODS} is the query phase 8's Clearing Checks needs: moving a lot
     * there posts nothing, so the stock is unsellable and still carried as an asset at full cost
     * until somebody writes it off. Nothing forces that second step, which is why lots ageing there
     * have to be surfaced.
     */
    @GetMapping(path = "/api/inventory/lots", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<InventoryLotView> lots(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockLocation location,
            @RequestParam(required = false) Boolean open) {

        if (productId != null && location != null) {
            throw new InvalidInputException(
                    "productId and location are alternative lookups; name one.");
        }
        if (location != null) {
            return ListResponse.of(inventory.lotsAt(location));
        }
        if (productId != null) {
            return ListResponse.of(Boolean.TRUE.equals(open)
                    ? inventory.openLotsOf(productId)
                    : inventory.lotsOf(productId));
        }
        throw new InvalidInputException("name a productId or a location");
    }

    @GetMapping(path = "/api/inventory/lots/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    InventoryLotView lot(@PathVariable long id) {
        return inventory.requireLot(id);
    }

    /** Lots whose cost has been raised by an allocated landed cost (ADR 0010). */
    @GetMapping(path = "/api/inventory/lots/with-landed-cost",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<InventoryLotView> lotsWithLandedCost() {
        return ListResponse.of(inventory.lotsWithAllocatedLandedCost());
    }

    /**
     * Moves a whole lot to another location. <strong>Posts nothing</strong> — deliberately.
     *
     * <p>A location is a state of stock, not a place on a map, and moving to Damaged Goods does not
     * impair the asset: the stock is unsellable and still worth what it cost until a write-off says
     * otherwise. The compensating control is the ageing query above, not a posting here.
     *
     * <p>Refused for a serial-tracked lot: those store their location per unit, because three
     * machines received together are one lot and one going out for repair must not move the others.
     */
    @PostMapping(path = "/api/inventory/lots/{id}/location",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.INVENTORY, level = AccessLevel.FULL)
    InventoryLotView moveLot(@PathVariable long id, @RequestBody LocationRequest request) {
        return inventory.moveLot(id, request.location());
    }

    // -------------------------------------------------------------------------------------------
    // Serialized units
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/inventory/lots/{id}/units", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SerializedUnitView> unitsOfLot(@PathVariable long id) {
        return ListResponse.of(inventory.unitsOf(id));
    }

    /** Units by product, by location, or by serial number — which is unique across all stock. */
    @GetMapping(path = "/api/inventory/units", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SerializedUnitView> units(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockLocation location,
            @RequestParam(required = false) String serialNumber) {

        int lookups = (productId == null ? 0 : 1) + (location == null ? 0 : 1)
                + (serialNumber == null ? 0 : 1);
        if (lookups != 1) {
            throw new InvalidInputException(
                    "name exactly one of productId, location or serialNumber");
        }
        if (serialNumber != null) {
            return ListResponse.of(inventory.findUnitBySerialNumber(serialNumber)
                    .map(List::of).orElseGet(List::of));
        }
        if (location != null) {
            return ListResponse.of(inventory.unitsAt(location));
        }
        if (productId != null) {
            return ListResponse.of(inventory.unitsOfProduct(productId));
        }
        throw new IllegalStateException("unreachable: exactly one lookup was required above");
    }

    /** Refused for a unit that is not on hand — a sold machine does not move to a shelf. */
    @PostMapping(path = "/api/inventory/units/{id}/location",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.INVENTORY, level = AccessLevel.FULL)
    SerializedUnitView moveUnit(@PathVariable long id, @RequestBody LocationRequest request) {
        return inventory.moveUnit(id, request.location());
    }

    // -------------------------------------------------------------------------------------------
    // Consumptions — read-only here; they are created by a sale
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/inventory/consumptions", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<StockConsumptionView> consumptions(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        if (productId != null) {
            return ListResponse.of(inventory.consumptionsOf(productId));
        }
        if (from == null || to == null) {
            throw new InvalidInputException(
                    "a date range needs 'from' and 'to', or name a productId instead");
        }
        return ListResponse.of(inventory.consumptionsBetween(from, to));
    }

    /**
     * Consumptions that could not be filled — the review list for negative stock (Q17).
     *
     * <p>A sale may drive aggregate stock negative and is not blocked: the unfilled part is recorded
     * as a shortfall, {@code stockOf} subtracts it so the product genuinely reads negative, and
     * <strong>no COGS is posted for it</strong>. A later receipt does not retro-cost it either — the
     * correction is to reverse and consume again, which is what somebody reading this list decides.
     */
    @GetMapping(path = "/api/inventory/consumptions/with-shortfall",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<StockConsumptionView> consumptionsWithShortfall() {
        return ListResponse.of(inventory.consumptionsWithShortfall());
    }

    @GetMapping(path = "/api/inventory/consumptions/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    StockConsumptionView consumption(@PathVariable long id) {
        return inventory.requireConsumption(id);
    }

    // -------------------------------------------------------------------------------------------
    // Write-offs
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/inventory/write-offs", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<StockWriteOffView> writeOffs(
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        if (lotId != null) {
            return ListResponse.of(inventory.writeOffsOf(lotId));
        }
        if (from == null || to == null) {
            throw new InvalidInputException(
                    "a date range needs 'from' and 'to', or name a lotId instead");
        }
        return ListResponse.of(inventory.writeOffsBetween(from, to));
    }

    @GetMapping(path = "/api/inventory/write-offs/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    StockWriteOffView writeOff(@PathVariable long id) {
        return inventory.requireWriteOff(id);
    }

    /**
     * Writes stock off. Reduces the lot or the named unit <strong>and posts</strong>, together.
     *
     * <p>The reason is not optional and not free text: {@code SHRINKAGE} / {@code DAMAGE} /
     * {@code EXPIRY} / {@code OTHER}. Reportability is the whole argument for one write-off account
     * rather than three, and it disappears if the reason can be left off.
     *
     * <p>⚠️ <strong>{@code createWriteOff} and not {@code writeOff}, which is backend queue item
     * 1.</strong> It was {@code writeOff}, the same name as the single read above, and
     * {@code OpenApiSpecIT} derives an {@code operationId} as {@code Controller_method} — so the
     * document declared one id for two operations, which OpenAPI forbids. Nothing complained: the
     * defect surfaced when step 16 generated a TypeScript client from it and the output did not
     * compile, twenty duplicate-identifier errors in one file. <strong>The read keeps the singular
     * noun</strong>, matching {@code role}/{@code roles} and every other pair on this surface; the
     * write takes the {@code create…} form {@code RoleController} and {@code UserController} already
     * use. {@code OpenApiSpecIT} now refuses a duplicate rather than writing one out.
     */
    @PostMapping(path = "/api/inventory/write-offs",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.INVENTORY, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    StockWriteOffView createWriteOff(@RequestBody NewStockWriteOff request) {
        return inventory.writeOff(request);
    }

    /**
     * Reverses a write-off: restores the quantity or the unit status and posts the mirror together.
     *
     * <p>Refused on a lot that has been re-costed by a landed-cost allocation since (ADR 0011): a
     * reversal claims the movement never happened, which would mean the allocation computed the
     * wrong <em>split</em>, not merely the wrong counterpart. A stock <em>return</em> is the case
     * that catches up instead — the asymmetry is deliberate and should not be smoothed away.
     */
    @PostMapping(path = "/api/inventory/write-offs/{id}/reversal",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.INVENTORY, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    StockWriteOffView reverseWriteOff(
            @PathVariable long id, @RequestBody WriteOffReversalRequest request) {
        return inventory.reverseWriteOff(id, request.reversalDate(), request.note());
    }

    // -------------------------------------------------------------------------------------------

    record LocationRequest(@Mandatory StockLocation location) {

        LocationRequest {
            Required.field(location, "location");
        }
    }

    /** The note is genuinely optional; the date is not. */
    record WriteOffReversalRequest(@Mandatory LocalDate reversalDate, String note) {

        WriteOffReversalRequest {
            Required.field(reversalDate, "reversalDate");
        }
    }
}
