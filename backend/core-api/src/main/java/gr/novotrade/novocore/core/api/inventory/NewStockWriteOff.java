package gr.novotrade.novocore.core.api.inventory;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Quantity;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Request to write stock off — <strong>the step 3 and step 6 obligation, discharged</strong>.
 *
 * <p>The chart of accounts has <em>one</em> {@code Inventory write-off / shrinkage} account rather than
 * three, on the grounds that which of shrinkage, damage or expiry a write-off was belongs on the
 * transaction. This is that transaction, and {@link WriteOffReason} is why it exists.
 *
 * <p><strong>Two shapes, and the write-off always names its lot.</strong> Pooled stock names a lot and a
 * quantity; serial-tracked stock names one unit. Brief §5's exception — a serialized write-off uses the
 * named unit's own actual cost with no FIFO logic — is honoured by construction here, because nothing in
 * step 7 picks a lot for the caller. FIFO consumption arrives in step 8, and that is where the distinction
 * starts doing work.
 *
 * @param writeOffDate the accounting date the loss is recognised on. Defaults are not supplied: a stock
 *     count finished on the 30th and entered on the 3rd belongs to the 30th, and there is no period
 *     locking to make that a problem.
 * @param note optional narrative — which units were missing, which courier dropped the box. Deliberately
 *     <em>not</em> a substitute for a reason: {@link WriteOffReason} says that if {@code OTHER} starts
 *     appearing often the fix is a new value with a name rather than free text, and that stands. This is
 *     for the detail a category cannot carry, and it is optional for every reason including {@code OTHER}
 *     so that nothing is ever blocked on prose.
 */
public record NewStockWriteOff(
        long lotId,
        Long serializedUnitId,
        Quantity quantity,
        @Mandatory WriteOffReason reason,
        @Mandatory LocalDate writeOffDate,
        String note) {

    public NewStockWriteOff {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(writeOffDate, "writeOffDate");
        note = (note == null || note.isBlank()) ? null : note.trim();

        if (serializedUnitId != null && quantity != null) {
            throw new IllegalArgumentException(
                    "A serialized write-off names one unit, so it has no separate quantity: the unit "
                            + "is the quantity. Supplying both leaves two numbers that can disagree.");
        }
        if (serializedUnitId == null && quantity == null) {
            throw new IllegalArgumentException(
                    "A write-off needs either a quantity (pooled stock) or a serialized unit id.");
        }
        if (serializedUnitId != null && serializedUnitId <= 0) {
            throw new IllegalArgumentException(
                    "serializedUnitId must be a positive NovoCore id, got " + serializedUnitId);
        }
    }

    /** Pooled stock: a quantity out of a named lot. */
    public static NewStockWriteOff pooled(long lotId, Quantity quantity, WriteOffReason reason,
            LocalDate writeOffDate) {
        return new NewStockWriteOff(lotId, null, quantity, reason, writeOffDate, null);
    }

    /** Serial-tracked stock: one named unit, at its own actual cost (brief §5). */
    public static NewStockWriteOff unit(long lotId, long serializedUnitId, WriteOffReason reason,
            LocalDate writeOffDate) {
        return new NewStockWriteOff(lotId, serializedUnitId, null, reason, writeOffDate, null);
    }

    /** The same request with a narrative. */
    public NewStockWriteOff withNote(String newNote) {
        return new NewStockWriteOff(
                lotId, serializedUnitId, quantity, reason, writeOffDate, newNote);
    }

    public boolean isSerialized() {
        return serializedUnitId != null;
    }

    public Optional<String> noteIfAny() {
        return Optional.ofNullable(note);
    }
}
