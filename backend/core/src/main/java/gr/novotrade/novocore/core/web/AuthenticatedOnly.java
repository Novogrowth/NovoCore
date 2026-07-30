package gr.novotrade.novocore.core.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a handler requires authentication and <strong>no section</strong>.
 *
 * <h2>Why this exists, and why it is not simply an exemption</h2>
 *
 * <p>{@link Requires} is mandatory on every {@code /api/**} handler, enforced at three layers,
 * because an undeclared endpoint is one nothing checks a permission for. Exactly one route cannot
 * satisfy it: <strong>{@code GET /api/me} is the route that tells a caller which sections they
 * have</strong>, so requiring a section to reach it is circular — a user with no grants at all
 * still has to be able to learn their own identity and discover that they have none.
 *
 * <p>The tempting answer is a special case inside the three checks: "unless the path is
 * {@code /api/me}". That is a hole, and holes widen — the next route that is awkward to classify
 * gets added to the condition, and the check quietly stops meaning what its name says. So instead
 * this is a <em>second declaration</em>, and the rule becomes: <strong>every {@code /api/**}
 * handler carries exactly one of {@link Requires} or {@code @AuthenticatedOnly}.</strong> A handler
 * with neither is still refused; a handler with both is a contradiction and is also refused.
 *
 * <p><strong>And the set of routes allowed to use it is itself asserted.</strong>
 * {@code WebAuthorizationRulesTest.onlyTheIdentityRouteIsSectionless} names them, and fails both on
 * a route that uses this without being listed <em>and</em> on a listing for a route that no longer
 * does — the same two-way check {@code assertEveryRouteCoveredExcept} applies to coverage excuses,
 * for the same reason: a list of exceptions nobody prunes is a list that stops describing reality.
 *
 * <h2>What it does not weaken</h2>
 *
 * <p>Authentication is still required — the filter chain answers 401 for an unauthenticated call to
 * {@code /api/**} and this changes nothing about that. What is skipped is only the section check,
 * which is why this must never appear on a route that returns anything beyond the caller's own
 * identity and permissions. Anything about another party, a document or an amount has a section,
 * and the fact that a route is inconvenient to classify is not evidence that it has none.
 *
 * @see Requires
 * @see SectionAccessInterceptor
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedOnly {

    /**
     * Why this route has no section. Required, and deliberately not defaulted: the argument is the
     * whole justification for the annotation, and one that has to be typed out is one somebody has
     * to be able to make.
     */
    String because();
}
