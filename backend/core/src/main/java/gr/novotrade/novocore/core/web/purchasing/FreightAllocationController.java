package gr.novotrade.novocore.core.web.purchasing;

import gr.novotrade.novocore.core.api.purchasing.FreightAllocationService;
import gr.novotrade.novocore.core.api.purchasing.FreightAllocationView;
import gr.novotrade.novocore.core.api.purchasing.NewFreightAllocation;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceLineView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.web.purchasing.PurchaseInvoiceController.ReversalRequest;
import java.time.LocalDate;
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
 * Freight and landed cost — what finally takes money <em>out</em> of
 * {@code Freight / Landed Cost — Unallocated}.
 *
 * <p>That account was created in V4 and flagged {@code expected_to_clear}; step 8 gave a carrier's
 * invoice somewhere to land, and until step 10 nothing had ever taken anything out of it. These
 * routes are what make it clearable from a browser rather than only from a test — which is the
 * reason they are here despite the outline treating them as optional.
 *
 * <h2>Q18, as ADR 0010: a lot's share splits by what is still in the lot</h2>
 *
 * <p>The part belonging to stock on hand raises that lot's unit cost. The part belonging to stock
 * already gone posts to <strong>{@code Landed cost variance}</strong> — those units are not in the
 * lot to carry it, and the COGS that took them out is immutable. A fully-sold lot may still be named
 * and all of its share goes to variance; that is the case Q18 exists for, not an edge case.
 *
 * <p><strong>Lots are always named, never inferred</strong> ({@code CLAUDE.md} rule 7). Allocation
 * divides by each lot's frozen <em>received</em> cost, so two freight invoices covering one shipment
 * split the same lots in the same proportion whichever order they are entered in. Refused: a
 * zero-cost lot, a lot from a reversed receipt, an over-allocation, a currency mismatch.
 *
 * <p>Immutable, and reversal refuses once the lots have moved.
 */
@RestController
@Requires(section = Section.PURCHASING)
class FreightAllocationController {

    private final FreightAllocationService freightAllocations;

    FreightAllocationController(FreightAllocationService freightAllocations) {
        this.freightAllocations = freightAllocations;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/freight-allocations", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<FreightAllocationView> allocations(
            @RequestParam(required = false) Long purchaseInvoiceLineId,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        if (purchaseInvoiceLineId != null && lotId != null) {
            throw new IllegalArgumentException(
                    "purchaseInvoiceLineId and lotId are alternative lookups; name one.");
        }
        if (purchaseInvoiceLineId != null) {
            return ListResponse.of(
                    freightAllocations.ofPurchaseInvoiceLine(purchaseInvoiceLineId));
        }
        if (lotId != null) {
            return ListResponse.of(freightAllocations.ofLot(lotId));
        }
        return ListResponse.of(freightAllocations.between(
                PurchaseInvoiceController.requireFrom(from), PurchaseInvoiceController.requireTo(to)));
    }

    @GetMapping(path = "/api/freight-allocations/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    FreightAllocationView allocation(@PathVariable long id) {
        return freightAllocations.require(id);
    }

    /** Carrier invoice lines with freight still sitting unallocated. */
    @GetMapping(path = "/api/purchase-invoice-lines/awaiting-allocation",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<PurchaseInvoiceLineView> awaitingAllocation() {
        return ListResponse.of(freightAllocations.linesAwaitingAllocation());
    }

    /**
     * How much of one carrier invoice line is still unallocated.
     *
     * <p>An allocation names the purchase invoice expense line it spends, which is what keeps this
     * answerable — and what stops the unallocated account being credited below what was debited into
     * it.
     */
    @GetMapping(path = "/api/purchase-invoice-lines/{id}/unallocated-amount",
            produces = MediaType.APPLICATION_JSON_VALUE)
    UnallocatedAmount unallocatedAmount(@PathVariable long id) {
        return new UnallocatedAmount(id, freightAllocations.unallocatedAmountOf(id));
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/freight-allocations",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    FreightAllocationView allocate(@RequestBody NewFreightAllocation request) {
        return freightAllocations.allocate(request);
    }

    /**
     * Reverses an allocation, if the lots have not moved since.
     *
     * <p>The guard is not squeamishness: once stock has left a re-costed lot, un-allocating would
     * have to decide what the departed units should retrospectively have cost, and the COGS that
     * took them out is immutable. It refuses and names a remedy that a test walks end to end.
     */
    @PostMapping(path = "/api/freight-allocations/{id}/reversal",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    FreightAllocationView reverse(@PathVariable long id, @RequestBody ReversalRequest request) {
        return freightAllocations.reverse(id, request.reversalDate(), request.reason());
    }

    // -------------------------------------------------------------------------------------------

    record UnallocatedAmount(long purchaseInvoiceLineId, Money unallocated) {
    }
}
