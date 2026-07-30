package gr.novotrade.novocore.core.api.ledger;

import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import java.time.LocalDate;
import java.util.Optional;

/**
 * What to narrow a journal listing to. Every field is optional; all of them combine with AND.
 *
 * <p>A record rather than a growing list of overloads, so that adding a filter later is a field
 * rather than a fifth {@code pageOfEntries(...)}. The same reasoning that made
 * {@code SpringPaging.pageableFor} take a map rather than a sort argument per caller.
 *
 * <p><strong>Nothing here is required, including the date range</strong> — unlike
 * {@code /api/sales-invoices}, which demands one. A ledger screen's landing view is "everything, most
 * recent first", and the list is paged, so an unbounded query is already bounded by the page.
 * Requiring a range would force a frontend to invent one and would make the first thing an operator
 * sees depend on what it invented.
 *
 * @param from earliest {@code entryDate}, inclusive, or null for no lower bound
 * @param to latest {@code entryDate}, inclusive, or null for no upper bound
 * @param accountId entries with <strong>at least one line</strong> on this account, or null for any.
 *     Note this returns whole entries, not lines: an entry touching the account appears with its
 *     total, not with the part that touched it. The account <em>ledger</em> — the lines themselves,
 *     with a running position — is {@code pageOfLines}, which is a different screen.
 * @param source what produced the entry, or null for any. All ten values are real and posting today;
 *     the six named in brief §6 as the typed transactions are not the whole list, because
 *     {@code GOODS_RECEIPT}, {@code CREDIT_NOTE}, {@code FREIGHT_ALLOCATION} and
 *     {@code INVENTORY_WRITE_OFF} post as well and are all things an accountant filters for.
 * @param subLedgerRef entries with a line referencing one customer, supplier, lot or asset, or null
 *     for any. This is what makes a Control account reconcilable rather than merely declared to be.
 */
public record JournalEntryFilter(
        LocalDate from,
        LocalDate to,
        Long accountId,
        JournalSource source,
        SubLedgerRef subLedgerRef) {

    /** No narrowing at all — the whole ledger, newest page first. */
    public static JournalEntryFilter unfiltered() {
        return new JournalEntryFilter(null, null, null, null, null);
    }

    public JournalEntryFilter {
        // The range is checked here rather than in the service so that a backwards range is refused
        // identically wherever this is built. It is the one combination that is wrong on its own
        // terms rather than merely selective: `from` after `to` matches nothing, and answering an
        // empty page would read as "no entries in that period" — a wrong answer that looks like data.
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "The date range runs backwards: from " + from + " is after to " + to
                            + ". Nothing can match it, so this is a mistake in the request rather "
                            + "than a period with no entries in it.");
        }
    }

    public Optional<LocalDate> earliest() {
        return Optional.ofNullable(from);
    }

    public Optional<LocalDate> latest() {
        return Optional.ofNullable(to);
    }

    /** True when this asks for the whole ledger, which is the ordinary first request from a screen. */
    public boolean isUnfiltered() {
        return from == null && to == null && accountId == null && source == null
                && subLedgerRef == null;
    }
}
