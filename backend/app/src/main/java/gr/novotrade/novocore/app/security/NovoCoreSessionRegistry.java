package gr.novotrade.novocore.app.security;

import gr.novotrade.novocore.core.api.security.UserSessions;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * Which sessions belong to which user, so that revoking access can end them.
 *
 * <h2>Why this is ours rather than Spring Security's {@code SessionRegistry}</h2>
 *
 * <p>The framework's registry keys its map by the <strong>principal object</strong> and finds a
 * user's sessions with {@code getAllSessions(principal)} — so lookup depends on
 * {@code NovoCorePrincipal} equality. Our principal wraps a {@code UserView}, which carries the
 * user's display name, language and their whole resolved role. Any of those changing would change
 * the key, and the sessions registered under the old one would become unreachable: eviction would
 * report success while ending nothing.
 *
 * <p>That is a silent failure in a security control, which is the category this codebase treats as
 * worse than a loud one. Keying by <strong>user id</strong> instead — a long that never changes —
 * removes the question entirely. The cost is this class; it is about sixty lines and it cannot
 * develop that fault.
 *
 * <h2>How registration and cleanup happen</h2>
 *
 * <p>Registered on authentication success, where the request (and therefore the session) is in hand.
 * Removed on {@link HttpSessionDestroyedEvent}, which the container publishes for a logout, an
 * eviction and an ordinary timeout alike — so entries do not accumulate for sessions that are long
 * gone. That event requires {@code HttpSessionEventPublisher}, registered in
 * {@code SecurityConfiguration}; without it this map would grow for the life of the process.
 *
 * <h2>What it does not do</h2>
 *
 * <p>It is <strong>per-process and in memory</strong>. One JVM, one self-hosted instance — the same
 * assumption the rest of the deployment makes. If NovoCore is ever run as more than one instance,
 * this stops being sufficient and the sessions have to move somewhere shared; that is a real
 * limitation and it is stated here rather than discovered when a second instance is started.
 */
@Component
class NovoCoreSessionRegistry
        implements UserSessions, ApplicationListener<HttpSessionDestroyedEvent> {

    private static final Logger log = LoggerFactory.getLogger(NovoCoreSessionRegistry.class);

    /** userId → sessionId → session. Both levels concurrent; logins and evictions race freely. */
    private final Map<Long, Map<String, HttpSession>> byUser = new ConcurrentHashMap<>();

    void register(long userId, HttpSession session) {
        byUser.computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
    }

    @Override
    public int endAllFor(long userId) {
        Map<String, HttpSession> sessions = byUser.remove(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }

        int ended = 0;
        for (HttpSession session : List.copyOf(sessions.values())) {
            try {
                session.invalidate();
                ended++;
            } catch (IllegalStateException alreadyGone) {
                // Already invalidated — by a logout racing this, or by the container timing it out
                // between the read above and here. The session is ended either way, which is the
                // outcome asked for, so this is not a failure and must not abort the rest.
                log.debug("Session {} was already invalid while ending sessions for user {}",
                        session.getId(), userId);
            }
        }

        // At INFO and not DEBUG: this is a security event. Somebody reading the log after an
        // account was cut off should be able to see that the sessions really went with it.
        log.info("Ended {} session(s) for user {}", ended, userId);
        return ended;
    }

    /**
     * Drops a destroyed session from the map.
     *
     * <p>Scans rather than looking up by user id, because the event carries only the session and its
     * security context may already be cleared by the time it arrives. With a handful of staff
     * accounts this is a trivial walk, and getting it wrong in the other direction — leaving entries
     * behind — would mean holding invalidated sessions for the life of the process.
     */
    @Override
    public void onApplicationEvent(HttpSessionDestroyedEvent event) {
        String sessionId = event.getId();
        List<Long> emptied = new ArrayList<>();

        byUser.forEach((userId, sessions) -> {
            if (sessions.remove(sessionId) != null && sessions.isEmpty()) {
                emptied.add(userId);
            }
        });
        // Only if still empty: a login for the same user may have arrived in between, and removing
        // a non-empty map would lose a live session and with it the ability to evict it.
        emptied.forEach(userId -> byUser.computeIfPresent(userId,
                (id, sessions) -> sessions.isEmpty() ? null : sessions));
    }

    /** How many sessions this user currently has. For tests and for diagnostics, not for policy. */
    int countFor(long userId) {
        Map<String, HttpSession> sessions = byUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }
}
