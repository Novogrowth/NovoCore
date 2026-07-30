package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Refuses to let the application start if any {@code /api/**} handler carries no {@link Requires}
 * declaration, or carries a meaningless one.
 *
 * <p><strong>Why this exists alongside an architecture test that checks the same thing.</strong>
 * They see different things. The architecture test reads annotations off the bytecode and fails the
 * build, which is the fastest feedback and the one that stops a mistake reaching anybody — but it
 * cannot see Spring's actual handler mapping, so a route registered some other way is invisible to
 * it. This runs against the real mapping, after the context is built, and therefore covers exactly
 * what the build-time rule cannot.
 *
 * <p>It throws from a {@link ContextRefreshedEvent} listener, which aborts the refresh — so the
 * application does not start rather than starting with an unguarded endpoint. That is the same
 * stance as {@code InitialOwnerBootstrap}, which refuses to start on an empty user table: a system
 * that will not start names its problem, where one that starts in a degraded state does not.
 *
 * <p>It is a no-op outside a servlet web context, which is how the core's own tests avoid it.
 */
@Component
class EndpointDeclarationCheck implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(EndpointDeclarationCheck.class);

    /** Only our own API is governed by sections; the framework's login endpoints are not ours. */
    private static final String GOVERNED_PREFIX = "/api/";

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    EndpointDeclarationCheck(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Every mapping, not one. There are normally two — Spring MVC's own and the actuator's
        // controllerEndpointHandlerMapping — so asking for "the" mapping fails outright with
        // NoUniqueBeanDefinitionException, which is how this was found. Checking all of them is
        // also the stricter answer: a route under /api/** registered by any mapping is governed,
        // and choosing one by name would leave the others unexamined without saying so.
        List<RequestMappingHandlerMapping> mappings = handlerMappings.stream().toList();
        if (mappings.isEmpty()) {
            // Not a servlet web context — the core's own integration tests, for instance.
            return;
        }

        List<String> problems = new ArrayList<>();
        int governed = 0;

        for (RequestMappingHandlerMapping mapping : mappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                    : mapping.getHandlerMethods().entrySet()) {

                Set<String> paths = pathsOf(entry.getKey());
                if (paths.stream().noneMatch(path -> path.startsWith(GOVERNED_PREFIX))) {
                    continue;
                }
                governed++;

                HandlerMethod handlerMethod = entry.getValue();
                String name = handlerMethod.getBeanType().getSimpleName() + "."
                        + handlerMethod.getMethod().getName() + " " + paths;

                Requires declaration = SectionAccessInterceptor.declarationOn(handlerMethod);
                AuthenticatedOnly sectionless =
                        SectionAccessInterceptor.sectionlessDeclarationOn(handlerMethod);

                if (declaration != null && sectionless != null) {
                    // A contradiction: one says "this belongs to a section", the other says "it
                    // belongs to none". Refusing both is the only safe reading, because guessing
                    // which was meant is how a route ends up governed by the weaker of the two.
                    problems.add(name + " — carries both @Requires and @AuthenticatedOnly, which "
                            + "contradict each other; a handler declares exactly one");
                } else if (sectionless != null) {
                    if (sectionless.because().isBlank()) {
                        problems.add(name + " — @AuthenticatedOnly with a blank 'because'; a route "
                                + "outside the section model must say why it is outside it");
                    }
                } else if (declaration == null) {
                    problems.add(name + " — no @Requires declaration");
                } else if (declaration.level() == AccessLevel.NONE) {
                    // NONE would mean "grants nothing", which as a requirement is satisfied by
                    // every role. An endpoint declaring it would be readable by anyone
                    // authenticated, which is precisely what a declaration is meant to prevent.
                    problems.add(name
                            + " — @Requires(level = NONE) is meaningless; use VIEW or FULL");
                } else if (!declaration.section().isAvailable()) {
                    // A reserved section has nothing built behind it, so a live endpoint claiming
                    // one is either a typo or an endpoint that arrived before its feature.
                    problems.add(name + " — section " + declaration.section()
                            + " is reserved (Section.isAvailable() is false)");
                }
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start: " + problems.size() + " endpoint(s) under " + GOVERNED_PREFIX
                            + "** are not properly declared. Every such handler must carry exactly "
                            + "one of @Requires(section = ..., level = ...) or "
                            + "@AuthenticatedOnly(because = ...), on the method or its controller "
                            + "class, because an undeclared endpoint is one nothing checks a "
                            + "permission for.\n  " + String.join("\n  ", problems));
        }

        log.info("Endpoint declaration check passed: {} handlers under {}** all declare a section.",
                governed, GOVERNED_PREFIX);
    }

    private static Set<String> pathsOf(RequestMappingInfo info) {
        PathPatternsRequestCondition condition = info.getPathPatternsCondition();
        return condition == null ? Set.of() : condition.getPatternValues();
    }
}
