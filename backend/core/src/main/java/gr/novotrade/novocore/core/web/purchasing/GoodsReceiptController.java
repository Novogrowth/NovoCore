package gr.novotrade.novocore.core.web.purchasing;

import static gr.novotrade.novocore.core.web.purchasing.PurchaseInvoiceController.requireFrom;
import static gr.novotrade.novocore.core.web.purchasing.PurchaseInvoiceController.requireTo;

import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptLineView;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptService;
import gr.novotrade.novocore.core.api.purchasing.GoodsReceiptView;
import gr.novotrade.novocore.core.api.purchasing.NewGoodsReceipt;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.web.purchasing.PurchaseInvoiceController.ReversalRequest;
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
 * Goods receipts — the inventory event.
 *
 * <p><strong>This is the only route in the whole surface that creates stock</strong>, and that is
 * the design rather than an accident. {@code InventoryService.receive} exists and deliberately has
 * no HTTP route of its own: it creates a lot and posts nothing, so reaching it directly would leave
 * the Inventory control account disagreeing with what the lots carry. A goods receipt calls it and
 * posts the entry in the same transaction — debit Inventory once per lot, credit GR/IR against the
 * supplier. An architecture rule enforces the distinction.
 *
 * <p><strong>Immutable, like the invoice</strong> (Q39, ADR 0008): corrected by a reversal that
 * un-receives the lots and posts the mirror together, and refused outright once those lots have been
 * touched. Same principle as the purchase price variance — a posting reflecting a physically
 * verified event does not change once other things depend on it.
 *
 * <p>Reversing a delivery of serial-tracked machines moves their units to {@code UNRECEIVED}, which
 * is the one status that does <em>not</em> keep its serial number: the commonest reason to reverse a
 * delivery is that the numbers were entered wrong.
 */
@RestController
@Requires(section = Section.PURCHASING)
class GoodsReceiptController {

    private final GoodsReceiptService goodsReceipts;

    GoodsReceiptController(GoodsReceiptService goodsReceipts) {
        this.goodsReceipts = goodsReceipts;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/goods-receipts", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<GoodsReceiptView> receipts(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        if (supplierId != null) {
            return ListResponse.of(goodsReceipts.ofSupplier(supplierId));
        }
        return ListResponse.of(goodsReceipts.between(requireFrom(from), requireTo(to)));
    }

    @GetMapping(path = "/api/goods-receipts/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    GoodsReceiptView receipt(@PathVariable long id) {
        return goodsReceipts.require(id);
    }

    /** The delivery a lot came from — the lot's source document, added in step 8. */
    @GetMapping(path = "/api/goods-receipts/by-lot/{lotId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<GoodsReceiptView> byLot(@PathVariable long lotId) {
        return ListResponse.of(goodsReceipts.findByLot(lotId).map(List::of).orElseGet(List::of));
    }

    /** Delivered lines with no invoice yet — the other half of the GR/IR position. */
    @GetMapping(path = "/api/goods-receipt-lines/awaiting-invoice",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<GoodsReceiptLineView> awaitingInvoice() {
        return ListResponse.of(goodsReceipts.linesAwaitingInvoice());
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    /**
     * Records a delivery: one lot per line, matched against an invoice line where one exists.
     *
     * <p>A line naming a {@code purchaseInvoiceLineId} takes its cost <em>from</em> that invoice and
     * is refused if it restates one — which is why invoice-first clears GR/IR exactly and produces
     * no variance.
     */
    @PostMapping(path = "/api/goods-receipts",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    GoodsReceiptView record(@RequestBody NewGoodsReceipt request) {
        return goodsReceipts.record(request);
    }

    @PostMapping(path = "/api/goods-receipts/{id}/reversal",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    GoodsReceiptView reverse(@PathVariable long id, @RequestBody ReversalRequest request) {
        return goodsReceipts.reverse(id, request.reversalDate(), request.reason());
    }
}
