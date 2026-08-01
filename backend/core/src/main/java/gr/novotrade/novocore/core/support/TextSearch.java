package gr.novotrade.novocore.core.support;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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
     *     join</strong>, so a row whose association is null drops out of the results entirely. That
     *     is why nothing in this step uses one: every column searched today is on the root entity.
     *     It is supported because the first transactional document list will want to search by
     *     customer name, and discovering then that the mechanism cannot express it would mean
     *     rewriting it.
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
