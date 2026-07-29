package gr.novotrade.novocore.core.web;

/**
 * A handler under {@code /api/**} carries no {@link Requires} declaration.
 *
 * <p>This is a programming error, not an operator one, and it is reported as a refusal rather than
 * as a server error on purpose: the caller must not be told that an endpoint exists but is
 * misconfigured, and the request must not succeed. Fail closed, say nothing, log loudly.
 *
 * <p>It should be unreachable in a built application — {@code EndpointDeclarationCheck} refuses to
 * let the context start with such a handler, and an architecture test fails the build before that.
 * It exists for the same reason {@code WebExceptionHandler} handles
 * {@code NotAuthenticatedException} that the filter chain should already have prevented: the last
 * layer is the one that has to hold when the earlier ones have been changed.
 */
class UndeclaredEndpointException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UndeclaredEndpointException(String handler) {
        super("Handler " + handler + " has no @Requires declaration, so no section could be "
                + "checked. Refusing the request. Add @Requires(section = ..., level = ...) to the "
                + "method or its controller class.");
    }
}
