package gr.novotrade.novocore.core.api.shared;

import java.util.List;
import java.util.Objects;

/**
 * One page of rows, and enough about the whole list to render a table around it.
 *
 * <p>The {@code totalElements} count is the reason this is not a cursor: an accounting screen shows
 * "page 7 of 34" and a row count, and both are questions about the list rather than about the page.
 * They cost a second query, which is the price of the answer.
 *
 * @param items the rows on this page, in the order asked for
 * @param page which page this is, zero-based, echoed back so a client holding several responses can
 *     tell them apart without tracking what it asked for
 * @param size the page size <em>asked for</em>, not the number returned. The last page holds fewer,
 *     and a client that read this as "how many are here" would think it had reached the end early —
 *     {@code items.size()} is the count of what arrived.
 * @param totalElements how many rows the whole list has
 */
public record PageResponse<T>(@Mandatory List<T> items, int page, int size, long totalElements) {

    public PageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (page < 0) {
            throw new IllegalArgumentException("page cannot be negative; got " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1; got " + size);
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException(
                    "totalElements cannot be negative; got " + totalElements);
        }
    }

    /** How many pages there are. At least 1, so an empty list is "page 1 of 1" and not "of 0". */
    public int totalPages() {
        return totalElements == 0 ? 1 : (int) ((totalElements + size - 1) / size);
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    /** Maps the rows, keeping the paging figures — for turning entities into views. */
    public <R> PageResponse<R> map(java.util.function.Function<T, R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new PageResponse<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }

    public static <T> PageResponse<T> of(
            List<T> items, PageRequest request, long totalElements) {
        return new PageResponse<>(items, request.page(), request.size(), totalElements);
    }
}
