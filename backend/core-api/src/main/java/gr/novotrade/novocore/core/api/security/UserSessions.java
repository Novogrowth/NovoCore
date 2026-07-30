package gr.novotrade.novocore.core.api.security;

/**
 * Ends a user's logged-in sessions, immediately.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Authentication is a session cookie, and the authenticated principal is a snapshot of the user
 * taken at login. Without this, <strong>deactivating an account does not log it out</strong>: the
 * session keeps working until it expires, for up to its full lifetime, and the operator who
 * deactivated it gets no indication of that. The same is true of moving somebody to a less
 * privileged role.
 *
 * <p>That is the wrong failure for the two cases this most matters in — cutting off a departing
 * employee, and containing a compromised account. Both need the access gone <em>now</em>, not
 * eventually.
 *
 * <p><strong>Eviction rather than a cache with a short life.</strong> A time-boxed refresh of the
 * principal would shrink the window and not close it, and a window is exactly what must not exist
 * here: "revoked, but still working for another minute" is not a meaningfully better answer than
 * "revoked, but still working for another hour" when the account is being cut off deliberately.
 *
 * <h2>The seam</h2>
 *
 * <p>An interface here, implemented against the servlet container in {@code app}, for the reason
 * {@link CurrentUser} documents: {@code core-api} carries no framework dependency and an
 * architecture test asserts it never gains one. The core says <em>end this user's sessions</em>; how
 * a session is ended is not its business.
 *
 * <p><strong>There is deliberately no no-op default.</strong> An implementation must be supplied or
 * the application does not start — the same stance as the initial-owner bootstrap and the database
 * password. A security control that quietly does nothing when its implementation is missing is the
 * failure mode this codebase has already refused once, when a permissive fallback {@code CurrentUser}
 * bean was considered and rejected in step 4b.
 */
public interface UserSessions {

    /**
     * Ends every logged-in session belonging to one user.
     *
     * <p>Called <strong>inside</strong> the transaction that revoked the access, so a rolled-back
     * deactivation cannot leave the person logged out of an account that is still active — and, more
     * importantly, a committed one cannot leave them logged in.
     *
     * <p>Must be safe to call for a user with no sessions, which is the ordinary case: answering
     * zero is not an error.
     *
     * @param userId the user whose sessions end
     * @return how many were ended, for the audit trail. The number is worth recording because "the
     *     account was deactivated and three sessions were killed" and "…and none were" describe
     *     genuinely different situations to whoever reads the log afterwards.
     */
    int endAllFor(long userId);
}
