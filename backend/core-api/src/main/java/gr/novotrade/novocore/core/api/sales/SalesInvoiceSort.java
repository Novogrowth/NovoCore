package gr.novotrade.novocore.core.api.sales;

/**
 * What a paged list of sales invoices may be ordered by.
 *
 * <h2>An enum, so the allowed values are a fact rather than a check</h2>
 *
 * <p>A {@code sort} parameter that reached a query as text would be an injection surface and would
 * couple the wire to the column names. As an enum: Spring refuses an unknown value at the boundary
 * before any of our code runs, the accepted values appear in the OpenAPI document so a frontend's
 * sort headers are typed rather than guessed, and a name that does not exist does not compile.
 *
 * <p>Deliberately short. Each entry is an ordering somebody would actually want on an invoice list,
 * and each has to be mapped to an entity property in {@code SalesInvoiceServiceImpl} — so this is a
 * list of orderings the service really offers, not every column that happens to exist.
 *
 * <p><strong>Whatever is chosen, the id breaks ties.</strong> None of these is unique — two invoices
 * share a date routinely — and without a total order PostgreSQL may return tied rows differently on
 * each query, so successive pages could repeat one row and skip another. {@code SpringPaging} adds
 * the tiebreaker to every ordering rather than leaving it to each caller.
 */
public enum SalesInvoiceSort {

    /** The document's own date. The default reading order of an invoice list. */
    INVOICE_DATE,

    /** Go's document number, lexically — which is chronological in practice, given how Go numbers. */
    DOCUMENT_NUMBER,

    /** When the row was recorded here, which is <em>not</em> the invoice date and often differs. */
    RECORDED_AT

    // Sorting by the invoice TOTAL is deliberately absent, and the reason is worth recording so it
    // is not added later without noticing. An invoice's gross is not a column: SalesInvoice.grossTotal()
    // sums its lines in Java and adds the rounding difference. Ordering by it in the database would
    // mean either a correlated subquery over the lines on every page, or storing a total that could
    // disagree with the lines — which is the second-copy-of-derived-state argument that keeps a
    // balance off an account and a stock figure off a product.
    //
    // Offering it by loading every invoice and sorting in memory would defeat the entire point of
    // paging this list. If it is genuinely wanted, that is a decision about storing the total, not a
    // value to add here.
}
