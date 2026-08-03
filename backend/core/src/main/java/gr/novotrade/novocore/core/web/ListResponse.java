package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.api.shared.PageResponse;
import java.util.List;
import java.util.Objects;

/**
 * The envelope every list endpoint returns: {@code {"items": [...]}}, plus {@code "page"} when the
 * list is paged.
 *
 * <p><strong>Not a bare JSON array, and the reason was paging.</strong> Step 14 shipped unpaged
 * deliberately and said so, on the argument that a bare array cannot later gain a total or a cursor
 * without breaking every caller that already parses it, while an envelope can. That is the change
 * being made now, and it costs no existing caller anything: {@code page} is absent from an unpaged
 * response, and {@code default-property-inclusion: non_null} means absent rather than null — so
 * every response that was correct yesterday is byte-identical today.
 *
 * <h2>Three tiers of list, two shapes</h2>
 *
 * <p>Not every list needs paging, and paging the chart of accounts would be noise. But two different
 * <em>shapes</em> across the surface would fork the frontend's table component, which is the cost
 * worth avoiding. So:
 *
 * <ul>
 *   <li><strong>Unbounded</strong> — documents, lots, movements, the outbox. Tens of thousands of
 *       rows within a year of real data. Paged, with a {@code page} block.
 *   <li><strong>Bounded by the business</strong> — products, customers, suppliers, assets. Hundreds
 *       to low thousands. This envelope now, paging when it is wanted, <em>with no frontend
 *       change</em> — which is what makes deferring it a real plan rather than a deferral.
 *   <li><strong>Genuinely fixed</strong> — the chart of accounts (67), VAT classes (9), exemption
 *       reasons (29), units of measure. Never paged.
 * </ul>
 *
 * <p>The difference between the last two and the first is <strong>visible in the type</strong> — no
 * {@code page} block — so it is a documented difference rather than a silent one.
 *
 * <p>Single objects are still <em>not</em> wrapped. The envelope answers a question — "is this all of
 * them?" — that a single object does not raise.
 *
 * @param page present only on a paged list. Absent means "this is the whole list", which is a real
 *     answer for the two tiers that are bounded by something other than a page size.
 */
public record ListResponse<T>(@Mandatory List<T> items, PageInfo page) {

    public ListResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    /** A complete list — everything there is, with no paging block. */
    public static <T> ListResponse<T> of(List<T> items) {
        return new ListResponse<>(items, null);
    }

    /** One page of a larger list. */
    public static <T> ListResponse<T> of(PageResponse<T> page) {
        Objects.requireNonNull(page, "page");
        return new ListResponse<>(page.items(), new PageInfo(
                page.page(), page.size(), page.totalElements(), page.totalPages(),
                page.hasNext(), page.hasPrevious()));
    }

    /**
     * Where this page sits in the whole list.
     *
     * <p>{@code totalPages}, {@code hasNext} and {@code hasPrevious} are derived from the other
     * three and are sent anyway. Deriving them is trivial and getting them subtly wrong is not —
     * the off-by-one on the last page is a mistake worth making once, here, rather than once per
     * client. {@code totalPages} is at least 1, so an empty list reads as "page 1 of 1".
     *
     * @param size the size <strong>asked for</strong>, not the number of rows returned. The last
     *     page holds fewer; {@code items.length} is what arrived.
     */
    public record PageInfo(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious) {
    }
}
