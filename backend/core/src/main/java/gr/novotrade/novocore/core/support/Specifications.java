package gr.novotrade.novocore.core.support;

import org.springframework.data.jpa.domain.Specification;

/**
 * Predicates shared by more than one slice.
 *
 * <p>Deliberately small. This is not a place to collect every specification in the system — a
 * predicate belongs next to the entity it is about, as {@code JournalEntrySpecifications} is. What
 * lands here is what is genuinely identical across slices, and today that is exactly one thing: five
 * entities carry the same {@code active} boolean with the same meaning, and five copies of
 * {@code builder.isTrue(root.get("active"))} is how they come to disagree about it.
 */
public final class Specifications {

    private Specifications() {
    }

    /**
     * Rows whose {@code active} flag is set.
     *
     * <p>Every entity this is used on has that column NOT NULL with a default, so there is no
     * three-valued case to think about.
     */
    public static <E> Specification<E> active() {
        return (root, query, builder) -> builder.isTrue(root.get("active"));
    }

    /**
     * {@link #active()} when asked for, and an always-true predicate otherwise.
     *
     * <p>The conditional lives here rather than at five call sites, and it returns a real predicate
     * rather than {@code null} for the reason {@code TextSearch.matching} gives: null composes with
     * {@code Specification.where} and fails when handed straight to {@code findAll}.
     */
    public static <E> Specification<E> activeOnly(boolean activeOnly) {
        return activeOnly ? active() : (root, query, builder) -> builder.conjunction();
    }
}
