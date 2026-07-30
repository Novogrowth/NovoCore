package gr.novotrade.novocore.core.support;

import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Translates the core's own {@link PageRequest} into Spring Data's {@code Pageable}, and back.
 *
 * <h2>Where the boundary actually sits</h2>
 *
 * <p>ADR 0003 keeps Spring Data out of {@code core-api}, which is why {@link PageRequest} and
 * {@link PageResponse} exist at all. It does <em>not</em> keep it out of {@code core} — the
 * repositories are Spring Data interfaces already. So the framework's paging is used where it is
 * good, inside the core, and never appears on a published signature. This class is the one place
 * the two meet, so the conversion is written once rather than in every service that pages.
 *
 * <h2>A sort key is mapped, never passed through</h2>
 *
 * <p>{@link #pageableFor} takes an explicit map from the <em>logical</em> field name a caller may
 * send to the <em>entity property</em> it orders by. A name that is not in the map is refused. So
 * nothing a caller supplies ever reaches a query, even though the routes already restrict the
 * parameter to an enum: the enum is the boundary check and this is the one that holds if a service
 * is ever called from somewhere else.
 *
 * <p><strong>Every ordering ends with the id.</strong> A sort on a non-unique column — an invoice
 * date, a customer name — leaves rows tied, and PostgreSQL is free to return tied rows in a
 * different order on each query. Two requests for successive pages could then show the same row
 * twice and skip another. The trailing id makes the order total, which is what makes a page a stable
 * answer rather than an approximate one.
 */
public final class SpringPaging {

    private SpringPaging() {
    }

    /**
     * @param request what the caller asked for
     * @param sortableFields logical name → entity property path, for every sort this list accepts
     * @param naturalOrder the property to order by when the caller names no sort. Never null: a page
     *     of an unordered result is not a stable answer.
     * @throws IllegalArgumentException if the request names a sort this list does not offer. An
     *     internal failure by design — the routes refuse an unknown value first, so reaching this
     *     means our own code asked for something that does not exist.
     */
    public static Pageable pageableFor(PageRequest request, Map<String, String> sortableFields,
            String naturalOrder) {

        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(naturalOrder, "naturalOrder");

        Sort sort;
        if (!request.isSorted()) {
            sort = Sort.by(Sort.Order.asc(naturalOrder));
        } else {
            String property = sortableFields.get(request.sort());
            if (property == null) {
                throw new IllegalArgumentException(
                        "Cannot sort by '" + request.sort() + "'. This list offers "
                                + sortableFields.keySet().stream().sorted().toList() + ".");
            }
            sort = Sort.by(request.direction().isDescending()
                    ? Sort.Order.desc(property)
                    : Sort.Order.asc(property));
        }

        // The tiebreaker, always, and in the same direction as the sort so a descending page reads
        // consistently. See the class javadoc for why a total order is not optional here.
        sort = sort.and(Sort.by(request.isSorted() && request.direction().isDescending()
                ? Sort.Order.desc("id")
                : Sort.Order.asc("id")));

        return org.springframework.data.domain.PageRequest.of(request.page(), request.size(), sort);
    }

    /** A Spring Data page of entities as a {@link PageResponse} of views. */
    public static <E, V> PageResponse<V> responseFrom(Page<E> page, Function<E, V> toView) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(toView, "toView");
        List<V> views = page.getContent().stream().map(toView).toList();
        return new PageResponse<>(
                views, page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
