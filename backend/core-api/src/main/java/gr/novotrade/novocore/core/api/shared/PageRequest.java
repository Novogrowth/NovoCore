package gr.novotrade.novocore.core.api.shared;

import java.util.Objects;

/**
 * Which slice of a list to return, and in what order.
 *
 * <h2>Ours, not Spring Data's</h2>
 *
 * <p>{@code org.springframework.data.domain.Pageable} would do this, and {@code core-api} may not
 * depend on Spring Data — ADR 0003, enforced by an architecture test. The same constraint that gave
 * {@code ChargeType} plain {@code Long} ids instead of JPA associations. It is a few lines and it
 * keeps the boundary intact.
 *
 * <h2>Offset paging with a total, not a cursor</h2>
 *
 * <p>Decided on what these screens are: an accounting table needs <em>"page 7 of 34"</em> and a row
 * count, which a cursor cannot produce, and it needs to sort by an arbitrary column, which would
 * mean a different cursor per column. Deep-offset cost is irrelevant at this scale — one self-hosted
 * PostgreSQL and tens of thousands of rows. <strong>The trigger to revisit is a table past roughly a
 * million rows</strong>, which is years away and would be a decision taken on evidence rather than a
 * surprise.
 *
 * <h2>The sort key is a name, and the service decides what names exist</h2>
 *
 * <p>{@code sort} is a <em>logical field name</em>, never a column and never anything that reaches
 * SQL. Each service maps the names it accepts onto its own ordering and <strong>refuses everything
 * else</strong>; the routes bind the parameter to a per-endpoint enum, so an invalid value is
 * refused at the boundary and the valid ones appear in the OpenAPI document. A free-text sort
 * parameter that reached a query would be both an injection surface and a coupling of the wire to
 * the schema.
 *
 * @param page zero-based. Page 0 is the first.
 * @param size how many rows. Bounded — see {@link #MAX_SIZE}.
 * @param sort the logical field to order by, or null for the list's natural order (which every
 *     paged query defines, because a page of an unordered result is not a stable answer)
 * @param direction which way; ignored when {@code sort} is null
 */
public record PageRequest(int page, int size, String sort, SortDirection direction) {

    /** The default when a caller names no size. Large enough for a screen, small enough to be free. */
    public static final int DEFAULT_SIZE = 50;

    /**
     * The most rows one request may ask for.
     *
     * <p>Bounded rather than unbounded because the whole point of this type is that a list cannot be
     * asked to return everything once it is large. A caller asking for more is <strong>refused
     * rather than quietly given this many</strong> — silently returning fewer rows than asked for is
     * how a client comes to believe it has seen the whole list.
     */
    public static final int MAX_SIZE = 500;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page numbers are zero-based and cannot be negative; got " + page + ".");
        }
        if (size < 1) {
            throw new IllegalArgumentException(
                    "A page must have at least one row; got " + size + ".");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "A page may hold at most " + MAX_SIZE + " rows; got " + size + ". Ask for "
                            + "successive pages rather than one large one — this bound is what "
                            + "makes a large list safe to expose at all.");
        }
        if (sort != null && sort.isBlank()) {
            throw new IllegalArgumentException(
                    "Sort field is blank. Omit it for the natural order rather than sending \"\".");
        }
        direction = sort == null ? null : Objects.requireNonNullElse(direction, SortDirection.ASC);
    }

    /** The first page at the default size, in the list's natural order. */
    public static PageRequest firstPage() {
        return new PageRequest(0, DEFAULT_SIZE, null, null);
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, null, null);
    }

    public static PageRequest of(int page, int size, String sort, SortDirection direction) {
        return new PageRequest(page, size, sort, direction);
    }

    /** How many rows to skip. {@code long} because {@code page * size} overflows an int sooner. */
    public long offset() {
        return (long) page * size;
    }

    public boolean isSorted() {
        return sort != null;
    }
}
