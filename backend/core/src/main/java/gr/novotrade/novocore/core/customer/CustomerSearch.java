package gr.novotrade.novocore.core.customer;

import gr.novotrade.novocore.core.support.TextSearch;
import org.springframework.data.jpa.domain.Specification;

/**
 * Searching <em>another</em> entity by the customer it points at.
 *
 * <h2>Why this exists rather than the caller naming {@code Customer.class} itself</h2>
 *
 * <p>A sales document's searchable fields include the customer's name and VAT number, which are on
 * the customer rather than on the document (target-list row 8). Reaching them needs a subquery over
 * {@link Customer} — and <strong>{@code Customer} is package-private, deliberately</strong>, like
 * every entity in this core: a package exposes its service and its views, never its mapped class.
 * That rule is worth more than the convenience of naming it from
 * {@code SalesInvoiceServiceImpl}, so the subquery is built here, where the entity is visible, and
 * handed out as a {@link Specification} the caller composes.
 *
 * <p><strong>It also puts the column list where it belongs.</strong> Which columns identify a
 * customer to somebody typing in a search box is the customer package's question, not the sales
 * package's, and stating it in one place is what stops the two drifting.
 *
 * @see TextSearch#matchingRelated for why a subquery rather than a mapped association or a
 *     denormalised copy
 */
public final class CustomerSearch {

    /**
     * ⚠️ <strong>Narrower than {@code CustomerServiceImpl.SEARCHABLE}, and not by accident.</strong>
     * The Customers screen also searches email and phone, which were added beyond the specified list
     * and kept because they are a filter box's most useful columns there. On a <em>document</em>
     * list they are not: nobody looks up an invoice by the customer's phone number, and every extra
     * column is another subquery branch on a list that is already paged over a large table.
     *
     * <p>📌 <strong>Customer code belongs here and does not exist.</strong> Row 8 names it;
     * {@code customer.code} is not a column. D1 adds it, and when it does, this array is where it
     * goes — one edit, and both document lists gain it.
     */
    private static final String[] IDENTIFYING = {"name", "vatNumber"};

    private CustomerSearch() {
    }

    /**
     * Rows of {@code E} whose customer matches the term.
     *
     * <p>Always-true for a blank or absent term, so it can be OR-ed into a larger predicate and mean
     * <em>no filter</em> — the semantics {@code TextSearch} documents for every part of a search.
     *
     * @param idProperty the property on {@code E} holding the customer id — {@code "customerId"} on
     *     both {@code SalesInvoice} and {@code CreditNote}
     */
    public static <E> Specification<E> matchingCustomer(String term, String idProperty) {
        return TextSearch.matchingRelated(term, idProperty, Customer.class, IDENTIFYING);
    }
}
