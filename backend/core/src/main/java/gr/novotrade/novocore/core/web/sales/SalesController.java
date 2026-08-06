package gr.novotrade.novocore.core.web.sales;

import gr.novotrade.novocore.core.api.sales.CreditNotePreview;
import gr.novotrade.novocore.core.api.sales.CreditNoteService;
import gr.novotrade.novocore.core.api.sales.CreditNoteView;
import gr.novotrade.novocore.core.api.sales.NewCreditNote;
import gr.novotrade.novocore.core.api.sales.NewSalesInvoice;
import gr.novotrade.novocore.core.api.sales.SalesInvoicePreview;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceService;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceSort;
import gr.novotrade.novocore.core.api.sales.SalesInvoiceView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.SortDirection;
import gr.novotrade.novocore.core.api.shared.InvalidInputException;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Paging;
import gr.novotrade.novocore.core.web.Requires;
import gr.novotrade.novocore.core.web.ReversalCommand;
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
 * Sales invoices and credit notes.
 *
 * <h2>Recorded, not issued — Go is the system of record until phase 11</h2>
 *
 * <p>{@code documentNumber} is Go's and {@code statedTotal} is what Go's document says. NovoCore
 * computes the sale independently and compares; the two are allowed to differ by a rounding
 * difference and not by more.
 *
 * <h2>Rounding is confirmed at entry, and there is deliberately no review queue</h2>
 *
 * <p>A difference at or below {@code ledger.rounding.threshold} posts automatically. Above it,
 * <strong>the document is refused</strong> until somebody accepts it — which is why
 * {@code roundingAcceptedBy} and {@code roundingNote} are fields on the request rather than a
 * separate endpoint. The acceptance is stored on the record.
 *
 * <p>Step 9 rejected a review queue explicitly, on the grounds that a queue is a second copy of
 * state that goes stale, and the generalisation is worth keeping in mind when adding to this
 * surface: <em>visible-at-entry is confirmed at entry; a consequence the operator cannot fix is
 * flagged and queried.</em> So a 422 here is not a failure of the API — it is the mechanism.
 *
 * <h2>A sale posts twice, and consumes stock without a route saying so</h2>
 *
 * <p>Revenue here, cost of goods sold from {@code InventoryService}, linked by the consumption the
 * line points at. That is why {@code consume} has no HTTP route of its own: this is the document
 * that drives it, in one transaction.
 */
@RestController
@Requires(section = Section.SALES)
class SalesController {

    private final SalesInvoiceService salesInvoices;
    private final CreditNoteService creditNotes;

    SalesController(SalesInvoiceService salesInvoices, CreditNoteService creditNotes) {
        this.salesInvoices = salesInvoices;
        this.creditNotes = creditNotes;
    }

    // -------------------------------------------------------------------------------------------
    // Sales invoices
    // -------------------------------------------------------------------------------------------

    /**
     * Sales invoices, <strong>paged</strong>, by customer or by date range.
     *
     * <p>Tier A: a quarter of real invoicing is thousands of rows and a year is tens of thousands, so
     * this list is paged rather than returned whole. The response carries a {@code page} block; the
     * lists that are bounded by the business (products, customers) carry the same envelope without
     * one, and the fixed lookups (VAT classes, the chart of accounts) are never paged. One shape for
     * the table component, and the difference visible in the type.
     *
     * <p>{@code sort} is bound to {@link SalesInvoiceSort}, so an unknown value is refused by Spring
     * before any of our code runs and the accepted values appear in the OpenAPI document. Whichever
     * is chosen, the id breaks ties — see {@code SpringPaging} for why a page of a non-total ordering
     * is not a stable answer.
     */
    @GetMapping(path = "/api/sales-invoices", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SalesInvoiceView> invoices(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) SalesInvoiceSort sort,
            @RequestParam(required = false) SortDirection direction) {

        PageRequest pageRequest = Paging.of(page, size, sort, direction);
        if (customerId != null) {
            return ListResponse.of(salesInvoices.pageOfCustomer(customerId, search, pageRequest));
        }
        return ListResponse.of(salesInvoices.pageBetween(
                requireRange(from), requireRange(to), search, pageRequest));
    }

    @GetMapping(path = "/api/sales-invoices/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    SalesInvoiceView invoice(@PathVariable long id) {
        return salesInvoices.require(id);
    }

    /**
     * Invoices where a rounding difference had to be accepted, with the period total.
     *
     * <p>The difference always posts, so AR agrees with the document the customer is holding. This
     * is the list that says how often that happened and for how much — which is the question a
     * threshold setting exists to be answerable about.
     */
    @GetMapping(path = "/api/sales-invoices/rounding-differences",
            produces = MediaType.APPLICATION_JSON_VALUE)
    RoundingReport roundingDifferences(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new RoundingReport(
                salesInvoices.withAcceptedRoundingDifference(from, to),
                salesInvoices.totalRoundingBetween(from, to));
    }

    @PostMapping(path = "/api/sales-invoices",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    SalesInvoiceView record(@RequestBody NewSalesInvoice request) {
        return salesInvoices.record(request);
    }

    /**
     * Prices an invoice without recording it — what an entry screen shows while it is being filled
     * in.
     *
     * <p><strong>Same body as {@code POST /api/sales-invoices}</strong>, so a screen has one form
     * and one request type, and previewing then submitting cannot disagree about what was asked for.
     *
     * <p><strong>200, never 201.</strong> Nothing was created, and answering 201 would invite a
     * client to look for a {@code Location} that does not exist. Nothing is written at all — see
     * {@code SalesInvoicePreview} for why this is not implemented as a rolled-back post.
     *
     * <p><strong>{@code FULL}, not {@code VIEW}, and that is deliberate.</strong> This computes a
     * priced document from pricing and VAT configuration; served at {@code VIEW} it would be a
     * second, weaker path to exactly what {@code Section.SALES} exists to govern. The same argument
     * that makes a referenced email attachment re-check the record it belongs to (Q44).
     */
    @PostMapping(path = "/api/sales-invoices/preview",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    SalesInvoicePreview preview(@RequestBody NewSalesInvoice request) {
        return salesInvoices.preview(request);
    }

    @PostMapping(path = "/api/sales-invoices/{id}/reversal",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    SalesInvoiceView reverse(@PathVariable long id, @RequestBody ReversalCommand request) {
        return salesInvoices.reverse(id, request.reversalDate(), request.reason());
    }

    // -------------------------------------------------------------------------------------------
    // Credit notes
    // -------------------------------------------------------------------------------------------

    /**
     * Credit notes, by customer, by the invoice they correct, or by date.
     *
     * <p>Same section as sales rather than one of their own, because a credit note only exists
     * against an invoice: somebody recording one has to be able to read the sale it corrects.
     */
    @GetMapping(path = "/api/credit-notes", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<CreditNoteView> notes(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long salesInvoiceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false) String search) {

        if (customerId != null && salesInvoiceId != null) {
            throw new InvalidInputException(
                    "customerId and salesInvoiceId are alternative lookups; name one.");
        }
        if (salesInvoiceId != null) {
            return ListResponse.of(creditNotes.againstInvoice(salesInvoiceId, search));
        }
        if (customerId != null) {
            return ListResponse.of(creditNotes.ofCustomer(customerId, search));
        }
        return ListResponse.of(creditNotes.between(requireRange(from), requireRange(to), search));
    }

    @GetMapping(path = "/api/credit-notes/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    CreditNoteView note(@PathVariable long id) {
        return creditNotes.require(id);
    }

    /**
     * Records a credit note issued against an invoice.
     *
     * <p><strong>Records, never issues.</strong> The credit note was legally issued by the external
     * transmission path — Prosvasis Go today — and got its ΜΑΡΚ there; this route learns of it
     * afterwards. Renamed from {@code issue} on 2026-08-02 for that reason.
     *
     * <p>It debits the channel's {@code Sales returns} account rather than netting into revenue, so
     * return rate stays visible per channel; it <strong>always credits AR, even against a cash
     * sale</strong>; and it credits the VAT the sale actually charged rather than re-resolving the
     * rate, because the customer's override may have changed since.
     *
     * <p>A credit note that restores stock is <strong>not reversible</strong> — a return says the
     * goods came back, which is an event, not a mistake to be undone.
     */
    @PostMapping(path = "/api/credit-notes",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    CreditNoteView recordNote(@RequestBody NewCreditNote request) {
        return creditNotes.record(request);
    }

    /**
     * Computes a credit note without issuing it.
     *
     * <p>Same body, 200, nothing written — see {@code preview} on the invoice above for the whole
     * argument. The reason a credit note needs its own preview rather than sharing the invoice's:
     * <strong>it credits back the VAT the sale actually charged</strong>, read off the invoice line,
     * not what the precedence rule would resolve today. A client that recomputed it would return the
     * wrong VAT to every customer whose override has changed since they bought.
     */
    @PostMapping(path = "/api/credit-notes/preview",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    CreditNotePreview previewNote(@RequestBody NewCreditNote request) {
        return creditNotes.preview(request);
    }

    @PostMapping(path = "/api/credit-notes/{id}/reversal",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SALES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    CreditNoteView reverseNote(@PathVariable long id, @RequestBody ReversalCommand request) {
        return creditNotes.reverse(id, request.reversalDate(), request.reason());
    }

    // -------------------------------------------------------------------------------------------

    static LocalDate requireRange(LocalDate bound) {
        if (bound == null) {
            throw new InvalidInputException(
                    "a date range needs 'from' and 'to', or name a customerId instead");
        }
        return bound;
    }

    record RoundingReport(List<SalesInvoiceView> items, Money total) {
    }
}
