package gr.novotrade.novocore.core.api.ledger;

/**
 * What a paged list of journal entries may be ordered by.
 *
 * <p>An enum for {@code SalesInvoiceSort}'s reasons: a {@code sort} parameter reaching a query as
 * text would be an injection surface and would couple the wire to the column names, while as an enum
 * Spring refuses an unknown value at the boundary and the accepted values appear in the OpenAPI
 * document.
 *
 * <p><strong>Whatever is chosen, the id breaks ties.</strong> None of these is unique — a day's
 * postings share a date routinely, and a busy day shares a source too — so without a total order
 * PostgreSQL may return tied rows differently on each query and successive pages could repeat one row
 * while skipping another. {@code SpringPaging} adds the tiebreaker to every ordering.
 */
public enum JournalEntrySort {

    /**
     * The accounting date — the natural order of a ledger, and the default.
     *
     * <p>Not {@link #RECORDED_AT}: an entry backdated into an earlier period belongs where it is
     * dated, which is the whole reason the two are separate columns.
     */
    ENTRY_DATE,

    /**
     * When the row was written here, which is <em>not</em> the entry date and often differs.
     *
     * <p>The ordering behind "what did somebody type in yesterday?" — a different question from
     * "what happened in the business yesterday?", and one an operator reviewing a colleague's work
     * genuinely asks.
     */
    RECORDED_AT,

    /** Groups the listing by what produced each entry. */
    SOURCE

    // Sorting by the entry's TOTAL is deliberately absent, for the reason SalesInvoiceSort records
    // about an invoice's gross. An entry's total is not a column: there is no total_debits column on
    // journal_entry, on purpose, because it would be a second copy of what the lines say and free to
    // disagree with them — the same argument that keeps a balance off an account.
    //
    // Ordering by it would therefore mean either a correlated subquery over the lines on every page,
    // or storing the total after all. Offering it by loading every entry and sorting in memory would
    // defeat the entire point of paging the one table expected to grow unbounded. If it is genuinely
    // wanted, that is a decision about storing totals, not a value to add here.
}
