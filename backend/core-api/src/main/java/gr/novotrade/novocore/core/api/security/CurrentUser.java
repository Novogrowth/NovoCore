package gr.novotrade.novocore.core.api.security;

import java.util.Optional;

/**
 * Who is making the current request.
 *
 * <p>An interface here, implemented against the security context in the {@code app} module,
 * because {@code core-api} carries no framework dependency — an architecture test asserts it
 * never gains one. This is the seam that keeps "who is logged in?" answerable from the core
 * without the core knowing that Spring Security exists.
 *
 * <p>Empty is a normal answer, not an error: unattended work has no user behind it. A scheduled
 * backup, a depreciation run and the Flyway seed are all genuinely actorless, which is why the
 * audit trail falls back to {@code system} rather than inventing a user.
 */
public interface CurrentUser {

    /** The authenticated user, or empty for an unauthenticated or unattended call. */
    Optional<UserView> find();

    /**
     * @throws NotAuthenticatedException when there is no authenticated user. For code paths that
     *     genuinely cannot proceed without one, so the failure names the problem instead of
     *     surfacing as a null further along.
     */
    UserView require();

    /** The username to record in audit columns and the audit log, or empty if unattended. */
    Optional<String> username();
}
