package gr.novotrade.novocore.core.web;

import gr.novotrade.novocore.core.api.security.NotAuthenticatedException;
import gr.novotrade.novocore.core.api.security.SectionAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the core's permission exceptions into HTTP status codes.
 *
 * <p>This lives here rather than as {@code @ResponseStatus} annotations on the exceptions
 * themselves, because those exceptions are in {@code core-api}, which is not permitted a Spring
 * dependency — an architecture test enforces it. Mapping a domain failure to a transport concern
 * is the web layer's job anyway.
 *
 * <p><strong>The response body says nothing specific.</strong> The exception messages name the
 * role, the section and what was required, which is useful to an administrator reading the log and
 * is exactly what should not be returned to a caller who has just been refused: it confirms the
 * section exists and describes the permission model. So the detail is logged and the client gets a
 * bare status.
 */
@RestControllerAdvice
class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    /**
     * 403 — authenticated, but this role may not see the section.
     *
     * <p>Distinct from the 401 the filter chain returns for an unauthenticated call: logging in
     * again would not help here.
     */
    @ExceptionHandler(SectionAccessDeniedException.class)
    ProblemDetail sectionAccessDenied(SectionAccessDeniedException exception) {
        log.warn("Refused: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied.");
    }

    /**
     * 401 — no authenticated user.
     *
     * <p>Normally unreachable, since the filter chain rejects unauthenticated requests to
     * {@code /api/**} first. It exists so that a future endpoint accidentally left out of the
     * authenticated matcher fails closed here rather than throwing a 500 and leaking a stack
     * trace.
     */
    @ExceptionHandler(NotAuthenticatedException.class)
    ProblemDetail notAuthenticated(NotAuthenticatedException exception) {
        log.warn("Unauthenticated request reached a controller: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }
}
