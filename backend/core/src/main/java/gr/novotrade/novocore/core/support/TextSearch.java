package gr.novotrade.novocore.core.support;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/**
 * Case- and accent-insensitive substring matching across a named set of columns, as a
 * {@link Specification} any entity can be searched with.
 *
 * <p>One mechanism, used by every searchable list. A service adopts it in one line:
 *
 * <pre>{@code
 * repository.findAll(
 *         TextSearch.matching(term, "sku", "name", "ean", "supplierSku"),
 *         Sort.by("sku"));
 * }</pre>
 *
 * <p>and composes it with anything else it already filters on:
 *
 * <pre>{@code
 * Specification.where(activeOnly ? Specifications.active() : null)
 *         .and(TextSearch.matching(term, "name"));
 * }</pre>
 *
 * <h2>Both sides are normalised by the database, so they cannot drift</h2>
 *
 * <p><strong>This is the decision worth protecting.</strong> The obvious shape is to normalise the
 * search term in Java — strip accents with {@code Normalizer}, lowercase, fold the sigma — and
 * compare it against a column the database normalised with {@code novocore_searchable}. That
 * compiles, passes an ASCII test, and is wrong: the two normalisations are different code with
 * different rules, and they disagree on characters that carry no combining mark at all.
 * {@code unaccent} maps {@code ø → o} and {@code đ → d} from its rules file; Java's NFD
 * decomposition does not, because there is nothing to decompose. So a term containing one would be
 * normalised one way, the stored value the other, and the row would simply never be found — with
 * nothing failing anywhere.
 *
 * <p>So the term goes through <em>the same SQL function</em> as the column. There is no Java copy of
 * the normalisation rules to keep in step, which means there is no way for them to fall out of step.
 * The one thing done in Java is escaping, below, which is not normalisation and is
 * locale-independent.
 *
 * <h2>The index this relies on</h2>
 *
 * <p>Each searched column needs a GIN trigram index on {@code novocore_searchable(column)} — see
 * {@code V28__substring_search.sql}, which explains why the function has to be {@code IMMUTABLE} and
 * why the one-argument {@code unaccent} cannot be used. Without the index the query is still
 * <em>correct</em>, just a sequential scan, so a missing one fails quietly rather than loudly;
 * {@code SearchIndexIT} is what turns that into a build failure by asserting that every column any
 * service searches has an index behind it.
 *
 * <p>A term shorter than three characters cannot yield a trigram, so PostgreSQL falls back to a scan
 * for those however many indexes exist. That is accepted rather than refused: on lists of this size
 * a scan is imperceptible, and refusing a two-character search would be a rule the operator has to
 * learn in order to explain why {@code AB} behaves differently from {@code ABC}.
 */
public final class TextSearch {

    /**
     * The SQL function from {@code V28__substring_search.sql}. Named once here so a rename is one
     * edit plus a migration rather than a hunt.
     */
    static final String NORMALISE = "novocore_searchable";

    /**
     * PostgreSQL's default, and what Hibernate renders for {@code like(x, y, escapeChar)}.
     *
     * <p>Doubled in the Java string literals below; it is a single backslash by the time it reaches
     * the database.
     */
    private static final char ESCAPE = '\\';

    private TextSearch() {
    }

    /**
     * Rows where <strong>any</strong> of the given properties contains the term, ignoring case,
     * accents and the Greek final-sigma/medial-sigma distinction.
     *
     * <p>A null or blank term matches <strong>everything</strong>, not nothing. That is what lets a
     * call site pass the parameter straight through from an absent query parameter without a
     * conditional of its own — "the operator did not search" and "the operator searched for nothing"
     * are the same request, and both mean the unfiltered list.
     *
     * @param term what the operator typed, raw. Wildcards in it are matched literally; see
     *     {@link #escapeLikeWildcards}.
     * @param properties entity property names, in the JPA sense. A dotted path
     *     ({@code "customer.name"}) is walked and works — <strong>note that it produces an inner
     *     join</strong>, so a row whose association is null drops out of the results entirely. Every
     *     column searched through this method is on the root entity.
     *     <p>⚠️ <strong>The dotted path was written for the transactional document lists and cannot
     *     serve them — established in F5, 2026-08-05.</strong> It needs a mapped association, and
     *     this codebase does not have one to use: {@code SalesInvoice.customerId} and
     *     {@code seriesId} are scalar {@code Long}s, as every cross-aggregate reference here is, so
     *     {@code "customer.name"} would not resolve at all. Use {@link #matchingRelated} for that,
     *     and see its javadoc for why a subquery is the right answer rather than a workaround.
     * @throws IllegalArgumentException if no property is named. An internal failure by design —
     *     this is our own call site asking to search nothing, never something a caller can send.
     */
    public static <E> Specification<E> matching(String term, String... properties) {
        Objects.requireNonNull(properties, "properties");
        if (properties.length == 0) {
            throw new IllegalArgumentException(
                    "TextSearch.matching needs at least one property to search.");
        }

        String trimmed = term == null ? null : term.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            // Not `null`: returning null works with Specification.where(...) and blows up when
            // somebody calls repository.findAll(spec, sort) with it directly. An always-true
            // predicate is correct in both positions.
            return (root, query, builder) -> builder.conjunction();
        }

        String pattern = "%" + escapeLikeWildcards(trimmed) + "%";

        return (root, query, builder) -> {
            // One bound parameter, normalised by the database. builder.literal renders as a bind
            // parameter under Hibernate's default literal handling, so the term never reaches the
            // statement text.
            Expression<String> needle =
                    builder.function(NORMALISE, String.class, builder.literal(pattern));

            List<Predicate> anyColumn = new ArrayList<>(properties.length);
            for (String property : properties) {
                Expression<String> haystack =
                        builder.function(NORMALISE, String.class, path(root, property));
                anyColumn.add(builder.like(haystack, needle, ESCAPE));
            }
            return builder.or(anyColumn.toArray(new Predicate[0]));
        };
    }

    /**
     * Rows whose <strong>counterparty or series</strong> matches the term — reached through a scalar
     * id rather than through a mapped association.
     *
     * <h2>Why a subquery, and why not the other two options</h2>
     *
     * <p>A document's searchable fields include things that are not on the document: a customer's
     * name and VAT number, a series' abbreviation. Target-list row 8 names them. Three ways exist
     * and two are wrong here:
     *
     * <ul>
     *   <li><strong>A mapped association plus {@link #matching}'s dotted path.</strong> Rejected:
     *       every cross-aggregate reference in this core is a scalar id, deliberately, and adding a
     *       {@code @ManyToOne} to one entity purely so a search string can walk it changes the
     *       model to suit a query. It also produces an <strong>inner join</strong>, which would drop
     *       a document whose reference is null — harmless for {@code customer_id}, which is
     *       {@code NOT NULL}, and <em>not</em> harmless for {@code series_id}, which is nullable on
     *       every invoice recorded before R1b.
     *   <li><strong>Denormalising the customer's name onto the document.</strong> Rejected: a second
     *       copy of a fact, stale the first time somebody corrects a customer's name — the same
     *       objection {@code V16} raised against a {@code superseded} flag beside
     *       {@code reversal_of_id}.
     *   <li><strong>A subquery on the id.</strong> What this does. No model change, no copy, and a
     *       row with a null reference simply does not match rather than disappearing.
     * </ul>
     *
     * <p><strong>It is meant to be OR-ed with {@link #matching}</strong>, which is why a blank term
     * yields an always-true predicate here too: {@code matching(blank).or(matchingRelated(blank))}
     * has to mean <em>no filter</em>, exactly as each half does alone.
     *
     * <p>The related columns still need their own GIN trigram indexes — the subquery is a search
     * like any other. {@code customer.name} and {@code customer.vat_number} have had theirs since
     * {@code V28}; the series columns gained theirs in {@code V36}.
     *
     * @param term what the operator typed, raw
     * @param idProperty the scalar id property on the root entity — {@code "customerId"}
     * @param relatedType the entity that id points at
     * @param relatedProperties properties on that entity to match, in the JPA sense
     */
    public static <E, R> Specification<E> matchingRelated(String term, String idProperty,
            Class<R> relatedType, String... relatedProperties) {
        Objects.requireNonNull(idProperty, "idProperty");
        Objects.requireNonNull(relatedType, "relatedType");
        if (relatedProperties.length == 0) {
            throw new IllegalArgumentException(
                    "TextSearch.matchingRelated needs at least one property to search.");
        }

        String trimmed = term == null ? null : term.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return (root, query, builder) -> builder.conjunction();
        }
        String pattern = "%" + escapeLikeWildcards(trimmed) + "%";

        return (root, query, builder) -> {
            if (query == null) {
                // Not reachable from findAll(spec, pageable); stated so a caller who reaches it some
                // other way gets this rather than a NullPointerException three frames down.
                throw new IllegalStateException(
                        "TextSearch.matchingRelated needs a CriteriaQuery to attach a subquery to.");
            }
            Subquery<Long> matchingIds = query.subquery(Long.class);
            Root<R> related = matchingIds.from(relatedType);
            Expression<String> needle =
                    builder.function(NORMALISE, String.class, builder.literal(pattern));

            List<Predicate> anyColumn = new ArrayList<>(relatedProperties.length);
            for (String property : relatedProperties) {
                Expression<String> haystack =
                        builder.function(NORMALISE, String.class, related.get(property));
                anyColumn.add(builder.like(haystack, needle, ESCAPE));
            }
            matchingIds.select(related.get("id")).where(builder.or(anyColumn.toArray(new Predicate[0])));

            // `in` rather than `exists`: a null id yields UNKNOWN, which is false in a WHERE — so an
            // invoice with no series is simply not matched BY THIS predicate, and stays in the result
            // through whatever it is OR-ed with. An inner join would have removed it from the query.
            return root.get(idProperty).in(matchingIds);
        };
    }

    /**
     * A LIKE pattern that means the operator's text literally.
     *
     * <p>Without this, typing {@code 50%} matches every row and typing {@code A_B} matches
     * {@code AxB}. Neither is an attack — the term is a bound parameter either way — but both are a
     * search that quietly answers a different question from the one asked, which is the failure mode
     * this codebase spends most of its effort on.
     *
     * <p>The escape character goes first, or it would escape the escapes added after it.
     */
    static String escapeLikeWildcards(String term) {
        return term.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** Walks a dotted property path; a single segment is the ordinary case. */
    private static Path<?> path(Path<?> root, String property) {
        Path<?> current = root;
        for (String segment : property.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }
}
