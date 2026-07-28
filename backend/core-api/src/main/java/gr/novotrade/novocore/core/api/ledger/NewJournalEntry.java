package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Request to post a journal entry — the raw layer brief §6's typed transactions generate.
 *
 * <p><strong>Balance is checked here, in the service, and by a deferred constraint trigger.</strong>
 * Three statements of {@code CLAUDE.md} rule 6, deliberately: this one turns a mistake into a message
 * before a database round trip, and the trigger is the guarantee — it holds against a {@code psql}
 * session and against any future adapter, which is what "structurally" in rule 6 means.
 *
 * <p><strong>A reversal is posted through here too</strong>, with {@link #reversalOfEntryId} set, rather
 * than through a separate write path. That is what lets a service which owns state outside the ledger —
 * the inventory write-off restoring a lot's quantity, later a Receipt releasing its allocations — reverse
 * its own entry without the ledger needing a second, unguarded entry point. The lines of a reversal are
 * validated to be an exact mirror of the original, so this path is no weaker than
 * {@code JournalService.reverse}; it is the same rule reached from the other side.
 *
 * @param entryDate the accounting date, which is what reports slice by. Distinct from {@code created_at}:
 *     backdating is ordinary, and there is no period locking (brief §6) to stop it.
 * @param description required. A ledger listing of entries with no descriptions is unreadable, and the
 *     one thing a later reader cannot reconstruct is what somebody meant.
 * @param reversalOfEntryId the entry this one reverses, or null. An entry can be reversed at most once —
 *     a UNIQUE constraint, not a check the service alone makes.
 */
public record NewJournalEntry(
        LocalDate entryDate,
        String description,
        JournalSource source,
        Long reversalOfEntryId,
        List<NewJournalLine> lines) {

    /** Two: an entry needs at least one debit and one credit to balance at a non-zero amount. */
    public static final int MINIMUM_LINES = 2;

    public NewJournalEntry {
        Objects.requireNonNull(entryDate, "entryDate");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "A journal entry needs a description. It is the only part of an entry a later "
                            + "reader cannot reconstruct from the numbers.");
        }
        description = description.trim();

        if (lines.size() < MINIMUM_LINES) {
            throw new IllegalArgumentException(
                    "A journal entry needs at least " + MINIMUM_LINES + " lines, and this one has "
                            + lines.size() + ". One line cannot balance against anything.");
        }
        if (reversalOfEntryId != null && reversalOfEntryId <= 0) {
            throw new IllegalArgumentException(
                    "reversalOfEntryId must be a positive NovoCore id, got " + reversalOfEntryId);
        }
    }

    public static NewJournalEntry of(LocalDate entryDate, String description, JournalSource source,
            List<NewJournalLine> lines) {
        return new NewJournalEntry(entryDate, description, source, null, lines);
    }

    /**
     * The mirror of an existing entry, for a service reversing its own document.
     *
     * <p>Takes the lines to mirror rather than an entry id, because the caller has already loaded the
     * entry it is reversing. The service validates that these really are the original's mirror.
     */
    public static NewJournalEntry reversalOf(long originalEntryId, LocalDate reversalDate,
            String description, JournalSource source, List<NewJournalLine> mirroredLines) {
        return new NewJournalEntry(
                reversalDate, description, source, originalEntryId, mirroredLines);
    }

    public Optional<Long> reversedEntry() {
        return Optional.ofNullable(reversalOfEntryId);
    }

    public boolean isReversal() {
        return reversalOfEntryId != null;
    }

    /**
     * Whether the lines balance. Computed rather than asserted here so that the service can produce
     * {@link UnbalancedJournalEntryException} with both totals in it, which is what a person needs to
     * find the missing line.
     */
    public boolean balances() {
        return totalDebits().equals(totalCredits());
    }

    public Money totalDebits() {
        return total(true);
    }

    public Money totalCredits() {
        return total(false);
    }

    private Money total(boolean debits) {
        // Money.plus refuses to combine two currencies, so a mixed-currency entry fails here rather
        // than producing a total in whichever currency came first (ADR 0005).
        Money running = null;
        for (NewJournalLine line : lines) {
            if (line.isDebit() != debits) {
                continue;
            }
            running = running == null ? line.amount() : running.plus(line.amount());
        }
        return running == null ? Money.zero(currency()) : running;
    }

    /** The currency of the entry, taken from its first line. All lines must agree; the service checks. */
    public java.util.Currency currency() {
        return lines.getFirst().amount().currency();
    }
}
