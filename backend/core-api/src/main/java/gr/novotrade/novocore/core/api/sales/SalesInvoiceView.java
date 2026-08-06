package gr.novotrade.novocore.core.api.sales;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A recorded sale, as everything outside the core sees it.
 *
 * <p><strong>No open amount here.</strong> What is still owed is
 * {@code SettlementService.openAmountOf(OpenItemRef.salesInvoice(id))}, computed from the allocations
 * against it — nothing anywhere holds a "paid" flag, for the reason no account holds a balance. It is
 * absent from this record rather than included so that a caller cannot read a stale figure out of a
 * projection built at some other moment.
 *
 * @param roundingAmount the difference between {@link #statedTotal()} and what NovoCore computed from
 *     the lines, posted to {@code Rounding differences} so Accounts receivable agrees with the
 *     document the customer holds. Either sign; zero on the ordinary invoice.
 * @param roundingNeededReview whether that difference exceeded {@code ledger.rounding.threshold} and
 *     therefore had to be accepted explicitly (Q15). Stored at the time rather than derived on read,
 *     because the threshold is operator-changeable and a later change must not retroactively alter
 *     which invoices somebody had to agree to.
 */
public record SalesInvoiceView(
        long id,
        long customerId,
        @Mandatory String customerName,
        @Mandatory SalesChannel channel,
        long paymentMethodId,
        @Mandatory String paymentMethodDescription,
        boolean settlesImmediately,
        @Mandatory String documentNumber,
        @Mandatory LocalDate invoiceDate,
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
        Long reversalOfInvoiceId,
        Long reversedByInvoiceId,
        Long mark,
        String uid,
        String qrUrl,
        @Mandatory TransmissionStatus transmissionStatus,
        Long seriesId,
        String seriesAbbreviation,
        @Mandatory List<SalesInvoiceLineView> lines) {

    public SalesInvoiceView {
        Objects.requireNonNull(customerName, "customerName");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(paymentMethodDescription, "paymentMethodDescription");
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(invoiceDate, "invoiceDate");
        Objects.requireNonNull(netTotal, "netTotal");
        Objects.requireNonNull(vatTotal, "vatTotal");
        Objects.requireNonNull(grossTotal, "grossTotal");
        Objects.requireNonNull(roundingAmount, "roundingAmount");
        Objects.requireNonNull(transmissionStatus, "transmissionStatus");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    /**
     * AADE's ΜΑΡΚ, when the document carries one.
     *
     * <p>⚠️ <strong>Empty is the normal state as of R1a and says nothing is wrong.</strong> Novocore
     * never obtains a ΜΑΡΚ itself — the document receives one at issuance, through Prosvasis Go
     * today and a certified Πάροχος at step 40 — and nothing in R1a records one. Present exactly
     * when {@link #transmissionStatus()} is {@link TransmissionStatus#TRANSMITTED}, which the
     * database enforces rather than leaves to convention.
     */
    public Optional<Long> markIfAny() {
        return Optional.ofNullable(mark);
    }

    /** The numbering series, when one is known. Empty on every invoice recorded before R1b. */
    public Optional<Long> seriesIdIfAny() {
        return Optional.ofNullable(seriesId);
    }

    public boolean isReversal() {
        return reversalOfInvoiceId != null;
    }

    public boolean isReversed() {
        return reversedByInvoiceId != null;
    }

    /** True when this invoice still stands — neither a reversing document nor one that was reversed. */
    public boolean isInForce() {
        return !isReversal() && !isReversed();
    }

    /**
     * True when the invoice was born fully settled and never becomes an open item.
     *
     * <p>Nothing may be allocated against such an invoice, because its open amount is zero from the
     * moment it is recorded.
     *
     * <p>⚠️ <strong>Since R4 this reads a component rather than asking an enum.</strong> Whether a
     * method settles immediately is derived from the KIND of the account it reconciles to — bank,
     * cash or partner clearing yes, the Accounts receivable control account no — and the service
     * computes it when it builds this view. Still one fact and still derived; the enum that used to
     * answer it no longer exists.
     */
    public boolean bornSettled() {
        return settlesImmediately;
    }

    public Optional<Money> statedTotalIfAny() {
        return Optional.ofNullable(statedTotal);
    }

    public Optional<String> roundingAcceptance() {
        return Optional.ofNullable(roundingAcceptedBy);
    }

    /** The lines that took stock out, in line order. */
    public List<SalesInvoiceLineView> linesThatConsumedStock() {
        return lines.stream()
                .filter(line -> line.stockConsumptionId() != null
                        || line.components().stream()
                                .anyMatch(component -> component.stockConsumptionId() != null))
                .toList();
    }
}
