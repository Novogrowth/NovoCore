package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.shared.InvalidInputException;
import gr.novotrade.novocore.core.api.shared.PageRequest;
import gr.novotrade.novocore.core.api.shared.SortDirection;

/**
 * Builds a {@link PageRequest} from query parameters, in one place for every paged route.
 *
 * <h2>It exists because of the named anti-pattern, not for convenience</h2>
 *
 * <p>{@code PageRequest}'s constructor throws {@link IllegalArgumentException} — correctly, because
 * from the core's point of view a negative page size <em>is</em> a programming error in whatever
 * called it. But from a route's point of view it is a caller sending {@code ?size=0}, and
 * {@code WebExceptionHandler} turns an {@code IllegalArgumentException} into a bare
 * {@code 400 "Bad request."} with the explanation deliberately discarded.
 *
 * <p>That is exactly the shape {@code CLAUDE.md} names: <em>a client's mistake raised as a
 * programming error</em>, which bit three times inside step 15 alone. So every paged route funnels
 * through here, and here the failure is re-raised as {@link InvalidInputException}, whose message
 * does reach the caller. Left to each controller, this would be got right in most of them.
 *
 * <h2>The sort parameter is an enum at the boundary</h2>
 *
 * <p>{@link #of} takes an enum constant, never a string. Spring converts the query parameter to it,
 * so an unknown value is refused before any of our code runs, the accepted values appear in the
 * OpenAPI document, and nothing that could reach a query is ever built from caller-supplied text.
 * A free-text sort parameter would be an injection surface and would couple the wire to the schema.
 */
public final class Paging {

    private Paging() {
    }

    /**
     * @param page zero-based, or null for the first
     * @param size rows per page, or null for {@link PageRequest#DEFAULT_SIZE}
     * @param sort the field to order by as a per-endpoint enum constant, or null for the list's
     *     natural order
     * @param direction which way, or null for ascending when sorted
     * @throws InvalidInputException if the numbers are out of range, <strong>carrying the reason
     *     the core gave</strong> rather than a bare "Bad request."
     */
    public static PageRequest of(
            Integer page, Integer size, Enum<?> sort, SortDirection direction) {
        try {
            return new PageRequest(
                    page == null ? 0 : page,
                    size == null ? PageRequest.DEFAULT_SIZE : size,
                    sort == null ? null : sort.name(),
                    direction);
        } catch (IllegalArgumentException outOfRange) {
            // The core's message names the bound and why it exists, which is precisely what a
            // caller needs and precisely what a bare 400 would throw away.
            throw new InvalidInputException(outOfRange.getMessage());
        }
    }
}
