package gr.novotrade.novocore.core.document;

import gr.novotrade.novocore.core.support.TextSearch;
import org.springframework.data.jpa.domain.Specification;

/**
 * Searching a sales document by the series it was numbered in.
 *
 * <p>The twin of {@code CustomerSearch}, and it exists for the same reason: {@link
 * SalesDocumentSeries} is package-private, and which of its columns identify it to somebody typing
 * in a search box is this package's question rather than the sales package's.
 *
 * <p>⚠️ <strong>{@code sales_invoice.series_id} is NULLABLE</strong> — every invoice recorded before
 * R1b has none — which is exactly why this is a subquery and an {@code IN}, not a join. A null id
 * yields UNKNOWN and simply fails to match; a join would have removed those rows from the result
 * altogether, so searching for anything would have silently hidden the whole of the pre-R1b history.
 *
 * @see TextSearch#matchingRelated
 */
public final class SalesDocumentSeriesSearch {

    /**
     * Both human-readable columns. An operator may type the short form printed on the document
     * ({@code ΑΛΠW}) or the words they remember from the settings screen, and neither is more
     * correct than the other.
     *
     * <p>The channel and the ΜΑΡΚ flag are deliberately not searched: they are enum-shaped facts a
     * list filters on, not text somebody types.
     */
    private static final String[] IDENTIFYING = {"abbreviation", "description"};

    private SalesDocumentSeriesSearch() {
    }

    /** Rows of {@code E} whose series matches the term; always-true for a blank term. */
    public static <E> Specification<E> matchingSeries(String term, String idProperty) {
        return TextSearch.matchingRelated(term, idProperty, SalesDocumentSeries.class, IDENTIFYING);
    }
}
