package gr.novotrade.novocore.core.api.settlement;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A document with something still outstanding on it — brief §6's computed open amount.
 *
 * <p>Computed on every read from the document's gross less what has been allocated against it.
 * Nothing anywhere holds a "paid" flag or a stored open figure, for the reason no account holds a
 * balance: two numbers that must agree are two numbers that can disagree.
 *
 * @param documentDate what an aged debtors report slices by, and what "oldest first" means when a
 *     receipt is being applied across several invoices.
 */
public record OpenItem(
        @Mandatory OpenItemRef ref,
        @Mandatory String documentNumber,
        @Mandatory LocalDate documentDate,
        long partyId,
        @Mandatory String partyName,
        @Mandatory Money grossAmount,
        @Mandatory Money allocatedAmount,
        @Mandatory Money openAmount) {

    public OpenItem {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(documentDate, "documentDate");
        Objects.requireNonNull(partyName, "partyName");
        Objects.requireNonNull(grossAmount, "grossAmount");
        Objects.requireNonNull(allocatedAmount, "allocatedAmount");
        Objects.requireNonNull(openAmount, "openAmount");
    }

    public boolean isFullySettled() {
        return openAmount.isZero();
    }

    /** True when nothing has been applied to it at all — an untouched invoice. */
    public boolean isUntouched() {
        return allocatedAmount.isZero();
    }
}
