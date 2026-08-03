package gr.novotrade.novocore.core.api.purchasing;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A posted landed-cost allocation — brief §4's freight allocated into the lots it delivered.
 *
 * <p>Immutable once posted, corrected by reversal (ADR 0010). A reversing document carries
 * {@link #reversalOfId} and <strong>no lines of its own</strong>: everything a reader needs is on the
 * original, which stays exactly as posted, and duplicating its lines would give one allocation two
 * statements of itself — the stance {@code PurchaseInvoice} already takes.
 *
 * @param amount the whole cost this document allocated. The sum of its lines, never a stored total.
 * @param capitalised how much of it raised a lot's carrying cost
 * @param variance how much of it went to {@code Landed cost variance} because the stock it belonged
 *     to had already left. Q18's answer, as a figure.
 */
public record FreightAllocationView(
        long id,
        long purchaseInvoiceLineId,
        long purchaseInvoiceId,
        String supplierInvoiceNumber,
        @Mandatory LocalDate allocationDate,
        String description,
        @Mandatory Money amount,
        @Mandatory Money capitalised,
        @Mandatory Money variance,
        long journalEntryId,
        Long reversalOfId,
        Long reversedByAllocationId,
        @Mandatory List<FreightAllocationLineView> lines) {

    public FreightAllocationView {
        Objects.requireNonNull(allocationDate, "allocationDate");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(capitalised, "capitalised");
        Objects.requireNonNull(variance, "variance");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
    }

    public boolean isReversal() {
        return reversalOfId != null;
    }

    public boolean isReversed() {
        return reversedByAllocationId != null;
    }

    /** Whether this document is still part of the picture — neither a reversal nor reversed. */
    public boolean stands() {
        return !isReversal() && !isReversed();
    }

    public Optional<String> descriptionIfAny() {
        return Optional.ofNullable(description);
    }

    /**
     * Whether any of this allocation landed in {@code Landed cost variance} rather than on a lot.
     *
     * <p>Worth surfacing rather than leaving to be inferred from two amounts: it says the freight
     * arrived after some of the goods had already been sold, which is a purchasing-process fact and
     * not an accounting error.
     */
    public boolean hasVariance() {
        return !variance.isZero();
    }
}
