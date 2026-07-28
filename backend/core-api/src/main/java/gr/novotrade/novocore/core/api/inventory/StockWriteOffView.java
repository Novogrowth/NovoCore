package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.Money;
import gr.novotrade.novocore.core.api.shared.Quantity;
import gr.novotrade.novocore.core.api.shared.UnitCost;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One recorded write-off, with the journal entry it posted.
 *
 * <p><strong>{@link #postedAmount()} is read off the entry, not recomputed.</strong> Extending the lot's
 * unit cost across the quantity again would give the same figure today and a different one after step 10:
 * brief §5 says a lot's unit cost includes allocated landed costs, so a freight allocation would silently
 * change what a past write-off appears to have cost. The entry is what actually happened.
 *
 * <p><strong>{@link #unitCost()} is the other half of that, stored since V19.</strong> The entry gives the
 * amount; it does not give the six-decimal cost the amount was extended from, and once step 10 could move
 * a lot's carrying cost that figure stopped being recoverable from the lot. It is what
 * {@code reverseWriteOff} compares against to find out whether the lot has been re-costed since — ADR
 * 0011.
 *
 * <p><strong>The journal entry is optional, and the case is real.</strong> A lot may have a unit cost of
 * zero — a supplier's free sample, promotional stock, a warranty replacement received at no charge — and
 * writing that off derecognises nothing, because nothing was carried. There is no entry to post, and
 * posting a zero-amount one is refused by the ledger for good reason. So the stock leaves and
 * {@link #journalEntryId()} is empty, which is the honest record of "gone, and it was never worth
 * anything".
 *
 * @param reversalOfWriteOffId set on a row that <em>restores</em> stock rather than removing it — the
 *     correction of a mistaken write-off. Mirrors the journal's own {@code reversal_of_id}, and like it,
 *     each write-off can be reversed at most once by UNIQUE constraint.
 * @param reversedByWriteOffId the write-off that reverses this one, or null. Derived on read, not stored:
 *     two columns pointing at each other are two columns that can disagree.
 */
public record StockWriteOffView(
        long id,
        long lotId,
        long productId,
        String productSku,
        Long serializedUnitId,
        String serialNumber,
        Quantity quantity,
        UnitCost unitCost,
        WriteOffReason reason,
        LocalDate writeOffDate,
        String note,
        Long journalEntryId,
        Money postedAmount,
        Long reversalOfWriteOffId,
        Long reversedByWriteOffId) {

    public StockWriteOffView {
        Objects.requireNonNull(productSku, "productSku");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitCost, "unitCost");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(writeOffDate, "writeOffDate");

        // The entry and the amount travel together: one without the other would leave a posted figure
        // nothing can be traced to, or an entry whose amount had to be looked up somewhere else.
        if ((journalEntryId == null) != (postedAmount == null)) {
            throw new IllegalArgumentException(
                    "Write-off " + id + " has journalEntryId=" + journalEntryId + " and postedAmount="
                            + postedAmount + ". Either both are present, or neither is — the second "
                            + "case being a zero-cost lot, which derecognises nothing and posts "
                            + "nothing.");
        }
        if ((serializedUnitId == null) != (serialNumber == null)) {
            throw new IllegalArgumentException(
                    "Write-off " + id + " names a serialized unit by id or by serial number but not "
                            + "both.");
        }
    }

    public boolean isSerialized() {
        return serializedUnitId != null;
    }

    /** Empty when the lot's carrying value was zero — see the class comment. */
    public Optional<Long> journalEntry() {
        return Optional.ofNullable(journalEntryId);
    }

    /** Empty for the same reason {@link #journalEntry()} can be. */
    public Optional<Money> postedValue() {
        return Optional.ofNullable(postedAmount);
    }

    public Optional<String> noteIfAny() {
        return Optional.ofNullable(note);
    }

    /** True when this row restores stock rather than removing it. */
    public boolean isReversal() {
        return reversalOfWriteOffId != null;
    }

    public boolean isReversed() {
        return reversedByWriteOffId != null;
    }

    /** True when nothing posted because the stock was carried at zero. */
    public boolean derecognisedNothing() {
        return journalEntryId == null;
    }
}
