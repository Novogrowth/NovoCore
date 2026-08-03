package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A credit note, as everything outside the core sees it.
 *
 * <p>Its open amount faces the other way from an invoice's (brief §6: "credit note =
 * negative-open-amount document"): it can be allocated against the invoice it corrects, reducing both
 * open amounts and posting nothing, or refunded by an outgoing settlement to the customer, which
 * posts. As with the invoice, that figure is not carried here — it is
 * {@code SettlementService.openAmountOf(OpenItemRef.creditNote(id))}, computed from the allocations.
 */
public record CreditNoteView(
        long id,
        long salesInvoiceId,
        @Mandatory String salesInvoiceNumber,
        long customerId,
        @Mandatory String customerName,
        @Mandatory SalesChannel channel,
        SettlementMethod settlementMethod,
        @Mandatory String documentNumber,
        @Mandatory LocalDate creditNoteDate,
        String description,
        @Mandatory Money netTotal,
        @Mandatory Money vatTotal,
        @Mandatory Money grossTotal,
        Money statedTotal,
        @Mandatory Money roundingAmount,
        boolean roundingNeededReview,
        String roundingAcceptedBy,
        Instant roundingAcceptedAt,
        String roundingNote,
        long journalEntryId,
        Long reversalOfCreditNoteId,
        Long reversedByCreditNoteId,
        @Mandatory List<CreditNoteLineView> lines) {

    public CreditNoteView {
        Objects.requireNonNull(salesInvoiceNumber, "salesInvoiceNumber");
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(creditNoteDate, "creditNoteDate");
        Objects.requireNonNull(netTotal, "netTotal");
        Objects.requireNonNull(vatTotal, "vatTotal");
        Objects.requireNonNull(grossTotal, "grossTotal");
        Objects.requireNonNull(roundingAmount, "roundingAmount");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    public boolean isReversal() {
        return reversalOfCreditNoteId != null;
    }

    /**
     * True when the invoice this corrects settled immediately — a cash, POS or partner-channel
     * sale that never touched Accounts receivable.
     *
     * <p>Carried here so the open-item layer can exclude such a credit note exactly as it excludes
     * the invoice. Since step 15 a born-settled credit note credits the settlement account rather
     * than AR, so counting it as an open item would recreate, in the opposite direction, the very
     * AR-versus-open-items discrepancy that change removed.
     */
    public boolean bornSettled() {
        return settlementMethod.settlesImmediately();
    }

    public boolean isReversed() {
        return reversedByCreditNoteId != null;
    }

    public boolean isInForce() {
        return !isReversal() && !isReversed();
    }

    /** True when any line brought goods back into stock. */
    public boolean returnedStock() {
        return lines.stream().anyMatch(CreditNoteLineView::stockReturned);
    }

    public Optional<Money> statedTotalIfAny() {
        return Optional.ofNullable(statedTotal);
    }

    public Optional<String> roundingAcceptance() {
        return Optional.ofNullable(roundingAcceptedBy);
    }
}
