package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.RoleView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies every handler's {@link Requires} declaration before the handler runs.
 *
 * <p>One place where the permission decision is made for the whole REST surface, so there is one
 * implementation to get right rather than one per controller method. The decision itself is not
 * made here — it is delegated to {@link RoleView#requireView} and {@link RoleView#requireEdit},
 * which is where the permission model lives and where it is exhaustively tested without a database.
 * This class resolves the declaration and calls them.
 *
 * <p><strong>An undeclared handler is refused, not allowed.</strong> That is the whole reason this
 * is safe to use in place of step 4b's inline check: moving a check into a shared mechanism normally
 * trades "someone might write it wrong" for "someone might not write it at all", and the second
 * failure is silent. Here it is not — see {@link Requires} for the three layers that make the
 * declaration mandatory.
 *
 * <p>Registered against {@code /api/**} only. Spring Security's own {@code /login} and
 * {@code /logout} are not ours and have no section; the actuator endpoints are gated by the filter
 * chain and by Caddy.
 */
class SectionAccessInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SectionAccessInterceptor.class);

    private final CurrentUser currentUser;

    SectionAccessInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            // Static resources and the framework's own handlers. Nothing of ours reaches here.
            return true;
        }

        Requires declaration = declarationOn(handlerMethod);

        if (declaration == null && sectionlessDeclarationOn(handlerMethod) != null) {
            // An explicit "authenticated, no section" declaration — see AuthenticatedOnly for why
            // this is a second declaration rather than an exemption carved into the check below.
            // require() rather than find(): the guarantee this route still makes is that somebody
            // is logged in, and it must fail closed if the filter chain ever stops ensuring it.
            currentUser.require();
            return true;
        }

        if (declaration == null) {
            // Logged at ERROR because it is a defect in our code, not a caller's mistake, and
            // because the two checks that should have caught it earlier evidently did not.
            String name = handlerMethod.getBeanType().getSimpleName() + "."
                    + handlerMethod.getMethod().getName();
            log.error("No @Requires on {} — refusing {} {}",
                    name, request.getMethod(), request.getRequestURI());
            throw new UndeclaredEndpointException(name);
        }

        // require() rather than find(): a handler under /api/** with no authenticated user behind
        // it is a hole in the filter chain, and this fails closed naming that rather than
        // proceeding with an anonymous role.
        RoleView role = currentUser.require().role();
        if (declaration.level() == AccessLevel.FULL) {
            role.requireEdit(declaration.section());
        } else {
            role.requireView(declaration.section());
        }
        return true;
    }

    /**
     * The declaration governing one handler: the method's own, or failing that its controller's.
     *
     * <p>A method-level declaration replaces the class-level one rather than combining with it. The
     * common case is a class declaring its section once and a mutating method raising the level to
     * {@code FULL}, and "replaces" makes that read exactly as written at the point of use.
     */
    static Requires declarationOn(HandlerMethod handlerMethod) {
        Requires onMethod = handlerMethod.getMethodAnnotation(Requires.class);
        if (onMethod != null) {
            return onMethod;
        }
        return handlerMethod.getBeanType().getAnnotation(Requires.class);
    }

    /**
     * The {@link AuthenticatedOnly} declaration governing one handler, if it carries one.
     *
     * <p>Resolved with the same method-beats-class rule as {@link #declarationOn}, so the two
     * declarations behave identically and a reader does not have to hold two lookup rules.
     */
    static AuthenticatedOnly sectionlessDeclarationOn(HandlerMethod handlerMethod) {
        AuthenticatedOnly onMethod = handlerMethod.getMethodAnnotation(AuthenticatedOnly.class);
        if (onMethod != null) {
            return onMethod;
        }
        return handlerMethod.getBeanType().getAnnotation(AuthenticatedOnly.class);
    }
}
