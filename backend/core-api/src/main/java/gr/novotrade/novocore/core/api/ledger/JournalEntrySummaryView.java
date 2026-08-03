package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One journal entry <strong>without its lines</strong> — a row in a paged ledger listing.
 *
 * <h2>Why the listing does not return {@link JournalEntryView}</h2>
 *
 * <p><strong>{@link JournalEntryView} refuses to exist without its lines.</strong> Its compact
 * constructor throws below two, deliberately, on the grounds that a posted entry always has at least
 * two and a line-less one is a projection bug. That is a good rule, and this type is what lets it
 * stay: the listing has a type of its own rather than weakening the one the rest of the ledger uses.
 *
 * <p><strong>And a listing that carried every line would be a different thing.</strong> A page of
 * fifty entries is a few hundred lines nobody asked for, each with its account, sub-ledger reference
 * and VAT dimension — on the one table in this system genuinely expected to grow unbounded over the
 * years the business runs. The lines are one request away at the single-entry route.
 *
 * <p>⚠️ <strong>A note on the reason this is <em>not</em> based on, because it is widely believed and
 * was written here first.</strong> The received wisdom is that a fetched collection forces Hibernate
 * to page in memory ({@code HHH000104}), loading the whole result set. <strong>Hibernate 7 does not
 * do that</strong> — measured, not assumed: it applies the limit in SQL and loads each row's
 * collection separately. Fetching the lines here would cost an N+1 per page rather than an
 * out-of-memory. Still worth avoiding, and worth stating accurately, because a justification that
 * turns out to be false is how a good decision gets reversed by whoever checks it.
 * {@code JournalEndpointIT} carries the measured figures.
 *
 * @param total the entry's debits, which are also its credits — <strong>one figure, not two</strong>.
 *     {@code CLAUDE.md} rule 6 makes them equal by construction, enforced by a deferred constraint
 *     trigger, so returning both would invite a reader to compare them and conclude something from a
 *     difference that cannot occur. Whoever wants the two halves separately wants the lines, and the
 *     single-entry route has them.
 * @param lineCount how many lines the entry has, so a listing can say "6 lines" without fetching
 *     them. Always at least two, for the reason above.
 * @param reversalOfEntryId the entry this one reverses, or null
 * @param reversedByEntryId the entry that reverses this one, or null. Derived on read from the
 *     reversing entry's own {@code reversal_of_id} — one direction is stored and the other is a
 *     query, because two columns pointing at each other are two columns that can disagree.
 */
public record JournalEntrySummaryView(
        long id,
        @Mandatory LocalDate entryDate,
        @Mandatory String description,
        @Mandatory JournalSource source,
        Long reversalOfEntryId,
        Long reversedByEntryId,
        @Mandatory Money total,
        int lineCount) {

    public JournalEntrySummaryView {
        Objects.requireNonNull(entryDate, "entryDate");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(total, "total");
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
     * <p>Same rule as {@link JournalEntryView#isAmendable()}, and stated the same way rather than
     * left for a caller to reconstruct from {@code source} — a listing that offers an Edit action has
     * to ask exactly the question the service will answer when the action is taken.
     */
    public boolean isAmendable() {
        return source.isAmendable() && !isReversal() && !isReversed();
    }
}
