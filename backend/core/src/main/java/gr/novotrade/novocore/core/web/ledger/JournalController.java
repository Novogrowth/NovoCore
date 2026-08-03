package gr.novotrade.novocore.core.web.ledger;

import gr.novotrade.novocore.core.api.ledger.JournalEntryFilter;
import gr.novotrade.novocore.core.api.ledger.JournalEntrySort;
import gr.novotrade.novocore.core.api.ledger.JournalEntrySummaryView;
import gr.novotrade.novocore.core.api.ledger.JournalEntryView;
import gr.novotrade.novocore.core.api.ledger.JournalLineView;
import gr.novotrade.novocore.core.api.ledger.JournalService;
import gr.novotrade.novocore.core.api.ledger.JournalSource;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.SortDirection;
import gr.novotrade.novocore.core.api.shared.SubLedgerRef;
import gr.novotrade.novocore.core.api.shared.SubLedgerType;
import gr.novotrade.novocore.core.api.shared.InvalidInputException;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Paging;
import gr.novotrade.novocore.core.web.Requires;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the journal — the listing {@link Section#JOURNAL} had no route for until step 16b.
 *
 * <h2>Reads only, deliberately</h2>
 *
 * <p>{@code postManualEntry}, {@code amend} and {@code reverse} all exist on the service and have no
 * route here. A manual journal entry <em>screen</em> is a design conversation — a line editor, an
 * account picker, a running balance as you type, and a decision about what happens to a half-written
 * entry — and smuggling the write routes in behind a listing would settle those questions by
 * accident. This step is the listing that was asked for.
 *
 * <h2>Granting this section is close to granting everything</h2>
 *
 * <p>An entry's lines carry customer, supplier, lot and asset references, and therefore the cost of
 * every purchase and the value of every sale. That is why {@code JOURNAL} is separate from
 * {@code CHART_OF_ACCOUNTS} — seeing the list of accounts is close to harmless, seeing what has
 * posted to them is every financial figure in the business — and why no role is granted it by
 * default.
 */
@RestController
@Requires(section = Section.JOURNAL)
class JournalController {

    private final JournalService journal;

    JournalController(JournalService journal) {
        this.journal = journal;
    }

    /**
     * Journal entries, <strong>paged</strong>, with the lines left out.
     *
     * <p>The journal is the one table in this system genuinely expected to grow unbounded over the
     * years the business runs, so this is paged from the start rather than joining the list of
     * unpaged reads that will need retrofitting. Each row carries the entry's total and its line
     * count; the lines themselves are one request away at {@code /api/journal-entries/{id}}.
     *
     * <p><strong>Every filter is optional, including the date range</strong> — unlike
     * {@code /api/sales-invoices}, which demands one. A ledger screen's landing view is "everything,
     * newest first", and the page bounds the query already.
     *
     * <p>{@code source} accepts all ten {@link JournalSource} values, not the six brief §6 names as
     * the typed transactions: goods receipts, credit notes, freight allocations and inventory
     * write-offs post too, and are all things somebody filters a ledger for.
     *
     * <p>A sub-ledger filter needs <strong>both</strong> parts or neither — a type with no id would
     * mean "every customer", which is what omitting the filter already means, and an id with no type
     * names nothing at all.
     */
    @GetMapping(path = "/api/journal-entries", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<JournalEntrySummaryView> entries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) JournalSource source,
            @RequestParam(required = false) SubLedgerType subLedgerType,
            @RequestParam(required = false) Long subLedgerId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) JournalEntrySort sort,
            @RequestParam(required = false) SortDirection direction) {

        PageRequest pageRequest = Paging.of(page, size, sort, direction);
        return ListResponse.of(journal.pageOfEntries(
                filterOf(from, to, accountId, source, subLedgerOf(subLedgerType, subLedgerId)),
                pageRequest));
    }

    @GetMapping(path = "/api/journal-entries/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    JournalEntryView entry(@PathVariable long id) {
        return journal.requireEntry(id);
    }

    /**
     * One account's lines, <strong>paged</strong> — the account ledger.
     *
     * <p>Under {@code JOURNAL} rather than {@code CHART_OF_ACCOUNTS}, despite the {@code /api/accounts}
     * prefix the rest of which is the chart. That is the section split working as intended rather
     * than an inconsistency: this returns what has posted to the account, which is the thing
     * {@code JOURNAL} exists to govern. {@code PermissionSweepIT}'s prefix table carries the
     * exception explicitly.
     *
     * <p>Lines rather than a ledger type with a running balance, because a running balance depends on
     * where the reader started and would silently restart at every page boundary. The opening figure
     * is the account's balance at the day before {@code from}, and the report that puts the two
     * together is phase 8's.
     */
    @GetMapping(path = "/api/accounts/{id}/ledger", produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.JOURNAL)
    ListResponse<JournalLineView> ledger(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) JournalEntrySort sort,
            @RequestParam(required = false) SortDirection direction) {

        requireOrderedRange(from, to);
        return ListResponse.of(
                journal.pageOfLines(id, from, to, Paging.of(page, size, sort, direction)));
    }

    // -------------------------------------------------------------------------------------------

    /**
     * Builds the filter, <strong>re-raising a backwards date range as a client mistake</strong>.
     *
     * <p>Exactly the shape {@link Paging#of} exists for, and for the same reason. From the core's
     * point of view a range running backwards <em>is</em> a programming error in whatever built it, so
     * {@link JournalEntryFilter} raises {@code IllegalArgumentException} — correctly. From a route's
     * point of view it is a caller sending {@code ?from=2026-06-30&to=2026-01-01}, and
     * {@code WebExceptionHandler} turns an {@code IllegalArgumentException} into a bare
     * {@code 400 "Bad request."} with the explanation deliberately discarded.
     *
     * <p>That is {@code CLAUDE.md}'s named anti-pattern — a client's mistake raised as a programming
     * error — and it is specifically the residual the entry for it warns about: <em>"a wrong but
     * non-empty value … a date range running backwards"</em>, the case none of the three guards can
     * see. It reached the caller as {@code "Bad request."} until a test asked for the message.
     */
    private static JournalEntryFilter filterOf(
            LocalDate from, LocalDate to, Long accountId, JournalSource source, SubLedgerRef ref) {
        try {
            return new JournalEntryFilter(from, to, accountId, source, ref);
        } catch (IllegalArgumentException backwards) {
            throw new InvalidInputException(backwards.getMessage());
        }
    }

    /**
     * The same for the account ledger, whose range is checked in the service rather than in a record.
     *
     * <p>Two call sites and one rule, so it is stated twice rather than made general: a shared helper
     * over "any IllegalArgumentException from the ledger" would start converting genuine programming
     * errors into 400s carrying internal messages, which is the failure this whole pattern exists to
     * avoid — in the other direction.
     */
    private static void requireOrderedRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidInputException(
                    "The date range runs backwards: from " + from + " is after to " + to
                            + ". Nothing can match it, so this is a mistake in the request rather "
                            + "than a period with no entries in it.");
        }
    }

    /**
     * Both halves of a sub-ledger filter, or neither.
     *
     * <p>Raised as an {@link InvalidInputException} rather than an {@code IllegalArgumentException},
     * so the caller is told which half is missing instead of receiving a bare {@code "Bad request."} —
     * the named anti-pattern that bit three times in step 15 alone.
     */
    private static SubLedgerRef subLedgerOf(SubLedgerType type, Long id) {
        if (type == null && id == null) {
            return null;
        }
        if (type == null || id == null) {
            throw new InvalidInputException(
                    "A sub-ledger filter needs both 'subLedgerType' and 'subLedgerId'; got "
                            + (type == null ? "only an id" : "only a type")
                            + ". A type on its own would mean every customer, which is what omitting "
                            + "the filter already does.");
        }
        return new SubLedgerRef(type, id);
    }
}
