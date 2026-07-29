package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.Section;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which {@link Section} a handler belongs to, and at what {@link AccessLevel}.
 *
 * <p><strong>Why this exists at all.</strong> Step 4b wrote its permission check inline —
 * {@code currentUser.require().requireView(Section.CHART_OF_ACCOUNTS)} — and said in its own javadoc
 * that with many controllers it should become a shared interceptor. This is that step, and the
 * reasoning behind the original choice has to survive it.
 *
 * <p>4b's argument was that a typed enum constant cannot be misspelled where the string inside a
 * {@code @PreAuthorize("...")} can, and a misspelled expression that fails open is the worst outcome
 * available. That property is kept here: this annotation takes enum constants, so a section that does
 * not exist does not compile.
 *
 * <p><strong>What an annotation introduces, and how it is answered.</strong> An inline call is
 * visible in the method body; an annotation can simply be left off, and a handler with no declaration
 * would then be reachable by anyone authenticated. That failure is silent and looks exactly like
 * working code — the same shape as the proxy self-invocation trap {@code CLAUDE.md} names. So the
 * declaration is not optional and its absence is not a default:
 *
 * <ul>
 *   <li>{@code SectionAccessInterceptor} <strong>refuses</strong> any {@code /api/**} handler that
 *       carries no declaration, rather than letting it through.
 *   <li>{@code EndpointDeclarationCheck} <strong>fails application startup</strong> if any mapped
 *       {@code /api/**} handler carries no declaration, so the mistake is found at boot rather than
 *       by whoever calls the endpoint.
 *   <li>{@code WebAuthorizationRulesTest} <strong>fails the build</strong> for the same condition, so
 *       it is normally found before the application is ever started.
 * </ul>
 *
 * <p>Three layers for one rule is deliberate. The ArchUnit rule is the fastest feedback and cannot
 * see Spring's actual handler mapping; the startup check sees the real mapping and runs later; the
 * interceptor is what holds if both are somehow bypassed. They fail at different times and catch
 * different mistakes.
 *
 * <p><strong>Placement.</strong> On a method, or on the controller class to cover every handler in
 * it. A method-level declaration wins outright over the class-level one — it does not merge with it,
 * because a rule that combines two declarations is a rule someone has to reason about at the call
 * site.
 *
 * @see SectionAccessInterceptor
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Requires {

    /** The section this handler reads or changes. */
    Section section();

    /**
     * What the caller must be able to do in that section.
     *
     * <p>{@link AccessLevel#VIEW} for a read, {@link AccessLevel#FULL} for anything that changes
     * state. {@link AccessLevel#NONE} is meaningless here and is rejected at startup rather than
     * quietly permitting everything.
     */
    AccessLevel level() default AccessLevel.VIEW;
}
