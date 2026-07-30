package gr.novotrade.novocore.core.ledger;

import gr.novotrade.novocore.core.api.ledger.JournalEntryFilter;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns a {@link JournalEntryFilter} into the predicates a paged listing needs.
 *
 * <h2>Why this is a specification rather than one JPQL string with optional conditions</h2>
 *
 * <p>The obvious shape — {@code where (:from is null or e.entryDate >= :from) and ...} — was written
 * first and <strong>does not work on PostgreSQL</strong>. A bare parameter in {@code ? IS NULL} gives
 * the driver nothing to infer a type from, and the database refuses the statement outright:
 * {@code ERROR: could not determine data type of parameter $1}. It fails for every request, not only
 * the ones that omit a filter, so it is the kind of mistake a test finds immediately — which it did.
 *
 * <p>Casting each parameter ({@code cast(:from as LocalDate) is null}) would compile and run. It was
 * rejected because it fixes the symptom and keeps the shape: five dead conditions in the plan on
 * every request, and a reader having to work out that the casts are load-bearing rather than
 * decorative. Building only the predicates that were asked for produces the query somebody would
 * write by hand.
 *
 * <h2>The account and sub-ledger filters are EXISTS, not joins</h2>
 *
 * <p>This is the decision worth protecting. An entry with three lines on the named account must
 * appear <strong>once</strong>; a join returns it three times, which makes the page size mean
 * something different per row and the total count wrong. {@code distinct} would paper over the
 * duplicates and leave {@code count(*)} — and therefore "page 7 of 34" — still wrong.
 *
 * <p><strong>Nothing here fetches the lines, deliberately.</strong> The listing shows none, and
 * fetching them would cost an N+1 per page — five collection loads for a five-row page, measured
 * rather than assumed. {@code JournalEndpointIT.aPageLoadsOnlyItsOwnRows} asserts against Hibernate's
 * own statistics and fails if a fetch is added, because the response would stay correct and nothing
 * else would notice.
 */
final class JournalEntrySpecifications {

    private JournalEntrySpecifications() {
    }

    static Specification<JournalEntry> matching(JournalEntryFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("entryDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("entryDate"), filter.to()));
            }
            if (filter.source() != null) {
                predicates.add(builder.equal(root.get("source"), filter.source()));
            }
            if (filter.accountId() != null) {
                predicates.add(builder.exists(
                        lineExists(root, query, builder, line ->
                                builder.equal(line.get("accountId"), filter.accountId()))));
            }
            if (filter.subLedgerRef() != null) {
                predicates.add(builder.exists(
                        lineExists(root, query, builder, line -> builder.and(
                                builder.equal(line.get("subLedgerType"),
                                        filter.subLedgerRef().type()),
                                builder.equal(line.get("subLedgerId"),
                                        filter.subLedgerRef().id())))));
            }

            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * One account's lines over an optional period, with the entry fetched.
     *
     * <p><strong>The fetch is wanted here and is not on the entry listing</strong>, and the
     * difference is simply what the response contains: every ledger row displays the date, source and
     * description of the entry its line came from, so without this it is a query per line. The entry
     * listing displays no lines, so fetching them there would be pure cost.
     *
     * <p>{@code entry} is a {@code @ManyToOne} — one row per line either way — so this adds no rows
     * and the page size means exactly what it says.
     *
     * <p>⚠️ <strong>The fetch is applied only to the data query, never the count query.</strong>
     * {@code JpaSpecificationExecutor} runs the same specification twice — once selecting entities and
     * once selecting {@code count(*)} — and a fetch join on a {@code count} query is rejected by
     * Hibernate ({@code query specified join fetching, but the owner of the fetched association was
     * not present}). {@code query.getResultType()} is how the two are told apart. Without this the
     * page loads and the total blows up, which is a failure on the second half of a working response.
     */
    static Specification<JournalLine> linesOfAccount(
            long accountId, java.time.LocalDate from, java.time.LocalDate to) {
        return (root, query, builder) -> {
            if (query != null && Long.class != query.getResultType()) {
                root.fetch("entry");
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("accountId"), accountId));
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("entry").get("entryDate"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("entry").get("entryDate"), to));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** {@code exists (select 1 from JournalLine l where l.entry = <this entry> and …)}. */
    private static Subquery<Integer> lineExists(
            Root<JournalEntry> entry,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            java.util.function.Function<Root<JournalLine>, Predicate> condition) {

        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<JournalLine> line = subquery.from(JournalLine.class);
        return subquery.select(builder.literal(1))
                .where(builder.and(builder.equal(line.get("entry"), entry), condition.apply(line)));
    }
}
