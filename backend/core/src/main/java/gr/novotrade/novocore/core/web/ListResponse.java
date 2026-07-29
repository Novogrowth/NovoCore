package gr.novotrade.novocore.core.web;

import java.util.List;
import java.util.Objects;

/**
 * The envelope every list endpoint returns: {@code {"items": [...]}}.
 *
 * <p><strong>Not a bare JSON array, and the reason is paging.</strong> No list method on any core
 * service takes a limit or an offset today, and step 14 ships unpaged deliberately — adding paging
 * means changing every service method and its repository, which is not this step's work. But
 * {@code GET /api/products} genuinely returns every product in one response, and that is fine at
 * hundreds and not at tens of thousands.
 *
 * <p>A bare array cannot later gain a total, a cursor or a "some rows were redacted" flag without
 * breaking every caller that already parses it. An envelope can. It costs one level of nesting now
 * and buys the ability to act on the first list that is slow without a coordinated frontend change —
 * which is what makes "act when it is slow" a real plan rather than a deferral.
 *
 * <p>Single objects are <em>not</em> wrapped. The envelope answers a question — "is this all of
 * them?" — that a single object does not raise.
 */
public record ListResponse<T>(List<T> items) {

    public ListResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public static <T> ListResponse<T> of(List<T> items) {
        return new ListResponse<>(items);
    }
}
