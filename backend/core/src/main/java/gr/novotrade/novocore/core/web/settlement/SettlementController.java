package gr.novotrade.novocore.core.web.settlement;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.settlement.AllocationView;
import gr.novotrade.novocore.core.api.settlement.CustomerCreditView;
import gr.novotrade.novocore.core.api.settlement.NewAllocation;
import gr.novotrade.novocore.core.api.settlement.NewSettlement;
import gr.novotrade.novocore.core.api.settlement.OpenItem;
import gr.novotrade.novocore.core.api.settlement.PartyType;
import gr.novotrade.novocore.core.api.settlement.SettlementService;
import gr.novotrade.novocore.core.api.settlement.SettlementView;
import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.web.InvalidRequestException;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receipts, payments, allocations and open items.
 *
 * <h2>Open item matching is a layer over AR/AP — allocations post nothing</h2>
 *
 * <p>ADR 0009. Both ends of an allocation are already in the same control account, so an entry for
 * it would debit and credit that account for the same amount. Three consequences shape this
 * controller and are worth having in front of you before reading it:
 *
 * <ul>
 *   <li><strong>No document stores an open amount and nothing stores a "paid" flag.</strong> Open
 *       items are computed, which is why {@code /open-items} is a query rather than a resource.
 *   <li><strong>An allocation can be released without touching a posted entry</strong>, which is
 *       what makes {@code PATCH /settlements/{id}} implementable at all.
 *   <li><strong>{@code DELETE /allocations/{id}} genuinely deletes a row</strong> — the one place
 *       in this schema that does, because an allocation is a statement about a current relationship
 *       rather than a record of an event.
 * </ul>
 *
 * <h2>The one document-shaped PATCH in the whole surface, and it is correct</h2>
 *
 * <p>ADR 0006 draws the line at whether a record exists <em>outside</em> NovoCore. Invoices and
 * credit notes are somebody else's documents and are corrected by reversal; receipts, payments and
 * transfers are our own and are edited in place, with the previous state written to the audit log.
 *
 * <p>Amending below the allocated total releases allocations <strong>most-recent-first</strong>,
 * reducing the last one partially and audit-logging every release. So this single route can cascade,
 * and the cascade is Q13's second half rather than a convenience.
 */
@RestController
@Requires(section = Section.SETTLEMENTS)
class SettlementController {

    private final SettlementService settlements;

    SettlementController(SettlementService settlements) {
        this.settlements = settlements;
    }

    // -------------------------------------------------------------------------------------------
    // Settlements
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/settlements", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SettlementView> settlements(
            @RequestParam(required = false) PartyType partyType,
            @RequestParam(required = false) Long partyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        if (partyType != null && partyId != null) {
            return ListResponse.of(settlements.ofParty(partyType, partyId));
        }
        if (partyType != null || partyId != null) {
            throw new InvalidRequestException("partyType and partyId go together; name both.");
        }
        if (from == null || to == null) {
            throw new InvalidRequestException(
                    "a date range needs 'from' and 'to', or name a party instead");
        }
        return ListResponse.of(settlements.between(from, to));
    }

    @GetMapping(path = "/api/settlements/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    SettlementView settlement(@PathVariable long id) {
        return settlements.require(id);
    }

    /** Money received or paid that has not been matched to a document yet. */
    @GetMapping(path = "/api/settlements/unallocated", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SettlementView> unallocated() {
        return ListResponse.of(settlements.withUnallocatedAmount());
    }

    /**
     * Records a receipt or a payment. The direction is in the body, because they share one table.
     *
     * <p>All four combinations of direction and party are real, refunds included; the trigger to
     * split them would be the first column belonging to one and not the other, and there is none.
     *
     * <p>Two behaviours to expect from the service: <strong>cash is hard-blocked above €500</strong>
     * (N. 5301/2026) — the one refusal in this design that offers no confirmation, because the
     * confirmation nobody can give is legality — and an overpayment becomes a standalone customer
     * credit <strong>only when the caller says so</strong> ({@code remainderBecomesCustomerCredit}),
     * because "overpaid" and "unmatched remittance" are different facts and guessing between them is
     * exactly what {@code CLAUDE.md} rule 7 forbids.
     */
    @PostMapping(path = "/api/settlements",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SETTLEMENTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    SettlementView record(@RequestBody NewSettlement request) {
        return settlements.record(request);
    }

    /** Edits a settlement in place. See the class javadoc — this is ADR 0006's editable half. */
    @PatchMapping(path = "/api/settlements/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SETTLEMENTS, level = AccessLevel.FULL)
    SettlementView amend(@PathVariable long id, @RequestBody AmendRequest request) {
        return settlements.amend(id, request.accountId(), request.settlementDate(),
                request.amount(), request.reference(), request.description());
    }

    // -------------------------------------------------------------------------------------------
    // Allocations
    // -------------------------------------------------------------------------------------------

    /** Matches a settlement against open documents. Posts nothing — see the class javadoc. */
    @PostMapping(path = "/api/settlements/{id}/allocations",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SETTLEMENTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    SettlementView allocate(@PathVariable long id, @RequestBody AllocationsRequest request) {
        return settlements.allocate(id, request.allocations());
    }

    /** Applies a credit note against an invoice. */
    @PostMapping(path = "/api/credit-notes/{id}/allocations",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SETTLEMENTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    AllocationView allocateCreditNote(
            @PathVariable long id, @RequestBody TargetedAllocationRequest request) {
        return settlements.allocateCreditNote(id, request.salesInvoiceId(), request.amount());
    }

    /** Applies unallocated customer credit against an invoice. */
    @PostMapping(path = "/api/customer-credits/{id}/allocations",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.SETTLEMENTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    AllocationView allocateCustomerCredit(
            @PathVariable long id, @RequestBody TargetedAllocationRequest request) {
        return settlements.allocateCustomerCredit(id, request.salesInvoiceId(), request.amount());
    }

    /**
     * Releases an allocation. <strong>The only DELETE in this API that removes a row</strong>, and
     * it is honest: an allocation states a current relationship, so unsaying it is a deletion rather
     * than a reversal. Nothing was posted for it, so nothing has to be un-posted.
     */
    @DeleteMapping(path = "/api/allocations/{id}")
    @Requires(section = Section.SETTLEMENTS, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable long id) {
        settlements.release(id);
    }

    // -------------------------------------------------------------------------------------------
    // Open items and customer credit
    // -------------------------------------------------------------------------------------------

    /**
     * What is still owed, by party or across a whole side.
     *
     * <p>Computed, never stored: no document carries an open amount and nothing carries a "paid"
     * flag, so this cannot go stale the way a status column would.
     */
    @GetMapping(path = "/api/open-items", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<OpenItem> openItems(
            @RequestParam PartyType partyType, @RequestParam(required = false) Long partyId) {
        return ListResponse.of(partyId == null
                ? settlements.allOpenItems(partyType)
                : settlements.openItemsFor(partyType, partyId));
    }

    /**
     * Customer credit documents — an overpayment that was deliberately turned into one (Q16).
     *
     * <p>They <strong>post nothing</strong>: the money is already in AR. A standalone document
     * exists so the credit is a thing somebody can point at, rather than a bare balance nobody can
     * explain.
     */
    @GetMapping(path = "/api/customer-credits", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<CustomerCreditView> customerCredits(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Boolean open) {

        if (customerId != null) {
            return ListResponse.of(settlements.customerCreditsOf(customerId));
        }
        if (Boolean.TRUE.equals(open)) {
            return ListResponse.of(settlements.openCustomerCredits());
        }
        throw new InvalidRequestException("name a customerId, or ask for open=true");
    }

    // -------------------------------------------------------------------------------------------

    record AllocationsRequest(List<NewAllocation> allocations) {
    }

    record TargetedAllocationRequest(long salesInvoiceId, Money amount) {
    }

    record AmendRequest(
            long accountId,
            LocalDate settlementDate,
            Money amount,
            String reference,
            String description) {
    }
}
