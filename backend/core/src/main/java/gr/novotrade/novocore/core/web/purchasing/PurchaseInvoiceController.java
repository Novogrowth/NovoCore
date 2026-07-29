package gr.novotrade.novocore.core.web.purchasing;

import gr.novotrade.novocore.core.api.purchasing.GrIrMatchView;
import gr.novotrade.novocore.core.api.purchasing.NewPurchaseInvoice;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceLineView;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceService;
import gr.novotrade.novocore.core.api.purchasing.PurchaseInvoiceView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.web.ListResponse;
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
 * Purchase invoices — what a supplier charged us.
 *
 * <h2>Recorded, never issued</h2>
 *
 * <p>A purchase invoice is somebody else's document. It carries the supplier's own number, unique
 * per supplier among invoices that still stand, and it is <strong>immutable</strong> (ADR 0006):
 * corrected by a reversal, never edited. So there is no {@code PATCH} on this resource and no
 * {@code DELETE} — {@code POST /{id}/reversal} creates a new posting and both documents continue to
 * exist, which is what a reversal means and what a {@code DELETE} would misrepresent.
 *
 * <h2>Two documents, either order</h2>
 *
 * <p>Goods routinely arrive before their invoice (brief §6's myDATA-first import), so an invoice's
 * inventory lines debit <strong>GR/IR clearing</strong> rather than Inventory, and the Goods Receipt
 * is the inventory event. Matching happens when the second document is created; there is
 * deliberately no later matching operation, because it would need a journal entry belonging to no
 * document (Q41 is open for exactly that reason). The two halves are queryable instead —
 * {@code /purchase-invoice-lines/awaiting-delivery} and its counterpart on the receipt side — which
 * is what phase 8's Clearing Checks will read.
 */
@RestController
@Requires(section = Section.PURCHASING)
class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseInvoices;

    PurchaseInvoiceController(PurchaseInvoiceService purchaseInvoices) {
        this.purchaseInvoices = purchaseInvoices;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/purchase-invoices", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<PurchaseInvoiceView> invoices(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        if (supplierId != null) {
            return ListResponse.of(purchaseInvoices.ofSupplier(supplierId));
        }
        return ListResponse.of(purchaseInvoices.between(requireFrom(from), requireTo(to)));
    }

    @GetMapping(path = "/api/purchase-invoices/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    PurchaseInvoiceView invoice(@PathVariable long id) {
        return purchaseInvoices.require(id);
    }

    /** Which deliveries this invoice has been matched against, and for how much. */
    @GetMapping(path = "/api/purchase-invoices/{id}/gr-ir-matches",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<GrIrMatchView> matches(@PathVariable long id) {
        return ListResponse.of(purchaseInvoices.matchesOf(id));
    }

    /**
     * Invoice lines with goods still to arrive — one half of the GR/IR position.
     *
     * <p>A balance here is not an error: GR/IR is flagged {@code expected_to_clear} precisely
     * because a timing gap in either direction is normal. What matters is that it is visible.
     */
    @GetMapping(path = "/api/purchase-invoice-lines/awaiting-delivery",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<PurchaseInvoiceLineView> awaitingDelivery() {
        return ListResponse.of(purchaseInvoices.linesAwaitingDelivery());
    }

    /**
     * Invoices carrying a purchase price variance, with the period total.
     *
     * <p>A variance <strong>can only arise goods-first</strong> (ADR 0008): a receipt matched to an
     * existing invoice line takes its cost from that line, so invoice-first clears exactly. When the
     * delivery came first and the invoice then disagreed, the lot keeps the cost it was received at
     * and the difference posts here — because re-costing a lot that FIFO has already consumed into
     * posted COGS is the same problem as editing a posted entry, expressed as a number.
     */
    @GetMapping(path = "/api/purchase-invoices/variances",
            produces = MediaType.APPLICATION_JSON_VALUE)
    VarianceReport variances(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new VarianceReport(
                purchaseInvoices.variancesBetween(from, to),
                purchaseInvoices.totalVarianceBetween(from, to));
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/purchase-invoices",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseInvoiceView record(@RequestBody NewPurchaseInvoice request) {
        return purchaseInvoices.record(request);
    }

    /**
     * Reverses an invoice. Not a delete — both documents stand afterwards.
     *
     * <p>The reason is required rather than optional: a reversal that says nothing about why leaves
     * the ledger internally consistent and unexplainable.
     */
    @PostMapping(path = "/api/purchase-invoices/{id}/reversal",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.PURCHASING, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseInvoiceView reverse(@PathVariable long id, @RequestBody ReversalRequest request) {
        return purchaseInvoices.reverse(id, request.reversalDate(), request.reason());
    }

    // -------------------------------------------------------------------------------------------

    static LocalDate requireFrom(LocalDate from) {
        if (from == null) {
            throw new IllegalArgumentException(
                    "a date range needs 'from' and 'to', or name a supplierId instead");
        }
        return from;
    }

    static LocalDate requireTo(LocalDate to) {
        if (to == null) {
            throw new IllegalArgumentException(
                    "a date range needs 'from' and 'to', or name a supplierId instead");
        }
        return to;
    }

    record ReversalRequest(LocalDate reversalDate, String reason) {
    }

    /**
     * @param total the period's variance in one figure, so a reader does not have to add the list up
     *     and get a different answer
     */
    record VarianceReport(List<PurchaseInvoiceView> items, Money total) {
    }
}
