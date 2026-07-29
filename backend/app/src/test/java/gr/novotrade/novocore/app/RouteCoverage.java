package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * <strong>Which of the REST surface a scenario actually drove, and — the point — which it did
 * not.</strong>
 *
 * <p>Step 15's narrative will exercise most of the API and not all of it. A validation that covers
 * seventy routes of a hundred and thirty-three and reports "validated" is worse than one that names
 * the sixty-three it missed, because the first reads as complete coverage to everyone who did not
 * write it. So this records what was reached, and {@link #assertEveryRouteCoveredExcept} fails
 * unless every route not reached appears in an explicit list with a reason attached. That is
 * {@code CLAUDE.md}'s no-silent-caps discipline applied to coverage.
 *
 * <p><strong>The denominator comes from Spring's own handler mapping</strong>, exactly as
 * {@code EndpointDeclarationCheck} reads it, so it cannot drift from the routes that really exist. A
 * route added in a later step is uncovered — and therefore reported — the day it appears, without
 * anyone remembering to update a list.
 *
 * <p><strong>What counts as covered: the handler method ran.</strong> This interceptor is registered
 * at {@code LOWEST_PRECEDENCE}, so it sits behind {@code SectionAccessInterceptor} and its
 * {@code preHandle} is reached only for requests that got past the permission check. That is
 * deliberate and it is load-bearing: step 15 sweeps every route as a restricted role expecting 403,
 * and if those counted, one permission sweep would mark the whole surface covered and the ledger
 * would report success while proving nothing. A 404 or a 422 <em>does</em> count — the handler ran
 * and refused, which is a genuine exercise of the route and exactly what the refusal matrix is for.
 *
 * <p><strong>It is a property of the in-process run only.</strong> A seed pass against a remote
 * server has no interceptor to record through, so coverage is reported by the automated test and by
 * nothing else. Stated here so its absence there is not later read as full coverage.
 */
class RouteCoverage implements HandlerInterceptor {

    /** A route as this ledger names it: {@code GET /api/products/{id}}. */
    record Route(String method, String pattern) implements Comparable<Route> {

        @Override
        public String toString() {
            return method + " " + pattern;
        }

        @Override
        public int compareTo(Route other) {
            int byPattern = pattern.compareTo(other.pattern);
            return byPattern != 0 ? byPattern : method.compareTo(other.method);
        }
    }

    private static final String GOVERNED_PREFIX = "/api/";

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final Set<Route> reached = ConcurrentHashMap.newKeySet();

    RouteCoverage(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern != null && pattern.toString().startsWith(GOVERNED_PREFIX)) {
                reached.add(new Route(request.getMethod(), pattern.toString()));
            }
        }
        return true;
    }

    /** Every route under {@code /api/**} that exists, read from the live handler mapping. */
    Set<Route> allRoutes() {
        Set<Route> routes = new TreeSet<>();
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                    : mapping.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = entry.getKey();
                PathPatternsRequestCondition paths = info.getPathPatternsCondition();
                if (paths == null) {
                    continue;
                }
                Set<String> methods = new LinkedHashSet<>();
                info.getMethodsCondition().getMethods()
                        .forEach(method -> methods.add(method.name()));
                if (methods.isEmpty()) {
                    // A mapping that names no method answers all of them. Recorded as ANY rather
                    // than expanded into seven routes nobody wrote, so the ledger stays readable
                    // and an unmethodded mapping is visible as the oddity it is.
                    methods.add("ANY");
                }
                for (String pattern : paths.getPatternValues()) {
                    if (!pattern.startsWith(GOVERNED_PREFIX)) {
                        continue;
                    }
                    methods.forEach(method -> routes.add(new Route(method, pattern)));
                }
            }
        }
        return routes;
    }

    Set<Route> covered() {
        return new TreeSet<>(reached);
    }

    Set<Route> uncovered() {
        Set<Route> uncovered = new TreeSet<>(allRoutes());
        uncovered.removeAll(reached);
        return uncovered;
    }

    void reset() {
        reached.clear();
    }

    /** A one-line summary suitable for logging at the end of a scenario. */
    String summary() {
        int total = allRoutes().size();
        int covered = total - uncovered().size();
        return String.format("routes covered: %d/%d (%.0f%%)",
                covered, total, total == 0 ? 0.0 : covered * 100.0 / total);
    }

    /**
     * The assertion this class exists for.
     *
     * @param excused route to reason, for every route the scenario deliberately does not drive.
     *                A reason is required rather than a bare list, because "why is this not
     *                covered" is the question a reader of the ledger actually has.
     * @throws AssertionError if a route is neither covered nor excused, <em>or</em> if an excuse
     *                        names a route that does not exist or that was in fact covered
     */
    void assertEveryRouteCoveredExcept(Map<String, String> excused) {
        Set<Route> all = allRoutes();
        Set<String> allNames = new TreeSet<>();
        all.forEach(route -> allNames.add(route.toString()));

        // An excuse for a route that no longer exists is a stale excuse, and one for a route that
        // was covered anyway is a claim about the scenario that is not true. Both quietly inflate
        // apparent coverage, so both fail here rather than being tolerated.
        List<String> stale = new ArrayList<>();
        Set<String> coveredNames = new TreeSet<>();
        covered().forEach(route -> coveredNames.add(route.toString()));
        for (String name : excused.keySet()) {
            if (!allNames.contains(name)) {
                stale.add(name + " — excused, but no such route exists");
            } else if (coveredNames.contains(name)) {
                stale.add(name + " — excused, but the scenario covered it; delete the excuse");
            }
        }
        assertThat(stale).as("excuses that no longer describe reality").isEmpty();

        List<String> unexplained = new ArrayList<>();
        for (Route route : uncovered()) {
            if (!excused.containsKey(route.toString())) {
                unexplained.add(route.toString());
            }
        }

        assertThat(unexplained)
                .as("""
                        Routes this scenario never drove and never excused (%s). Either exercise \
                        them, or add each to the exclusion map with a reason — a coverage figure \
                        with silent gaps in it reads as complete to everyone who did not write it.""",
                        summary())
                .isEmpty();
    }

    /** For a caller that wants to log or attach the detail rather than assert on it. */
    Collection<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add(summary());
        uncovered().forEach(route -> lines.add("  not covered: " + route));
        return lines;
    }
}
