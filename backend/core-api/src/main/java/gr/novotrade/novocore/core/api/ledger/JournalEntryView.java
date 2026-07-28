package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One posted journal entry with its lines.
 *
 * <p><strong>{@link #reversedByEntryId()} is not stored.</strong> It is resolved on read from the
 * reversing entry's own {@code reversal_of_id}, because two columns pointing at each other are two
 * columns that can disagree — the argument that keeps {@code normal_balance_side} off {@code account}
 * and a quantity off a serial-tracked lot. One direction is stored and the other is a query.
 *
 * @param reversalOfEntryId the entry this one reverses, or null
 * @param reversedByEntryId the entry that reverses this one, or null. Derived.
 */
public record JournalEntryView(
        long id,
        LocalDate entryDate,
        String description,
        JournalSource source,
        Long reversalOfEntryId,
        Long reversedByEntryId,
        List<JournalLineView> lines) {

    public JournalEntryView {
        Objects.requireNonNull(entryDate, "entryDate");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);

        if (lines.size() < NewJournalEntry.MINIMUM_LINES) {
            throw new IllegalArgumentException(
                    "Entry " + id + " has " + lines.size() + " lines. A posted entry always has at "
                            + "least " + NewJournalEntry.MINIMUM_LINES + ", so this is a projection "
                            + "bug rather than bad data — most likely lines were not fetched.");
        }
    }

    public Money totalDebits() {
        return total(true);
    }

    public Money totalCredits() {
        return total(false);
    }

    private Money total(boolean debits) {
        Money running = null;
        for (JournalLineView line : lines) {
            if (line.isDebit() != debits) {
                continue;
            }
            running = running == null ? line.amount() : running.plus(line.amount());
        }
        return running == null ? Money.zero(lines.getFirst().amount().currency()) : running;
    }

    /**
     * Always true for a posted entry, and worth being able to assert.
     *
     * <p>{@code CLAUDE.md} rule 6 is enforced by a deferred constraint trigger, so an unbalanced entry
     * cannot exist in the database. This method is how a test says so about real posted data rather than
     * about the code path that wrote it.
     */
    public boolean isBalanced() {
        return totalDebits().equals(totalCredits());
    }

    public boolean isReversal() {
        return reversalOfEntryId != null;
    }

    public boolean isReversed() {
        return reversedByEntryId != null;
    }

    public Optional<Long> reversedEntry() {
        return Optional.ofNullable(reversalOfEntryId);
    }

    public Optional<Long> reversingEntry() {
        return Optional.ofNullable(reversedByEntryId);
    }

    /**
     * Whether this entry may still be edited in place (Q13).
     *
     * <p>Its source has to allow it, and it must be neither a reversal nor already reversed: a reversal's
     * lines are defined as the mirror of another entry's, so editing either half would leave a pair that
     * no longer nets to zero while still claiming to.
     */
    public boolean isAmendable() {
        return source.isAmendable() && !isReversal() && !isReversed();
    }
}
