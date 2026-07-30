package gr.novotrade.novocore.core.security;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.security.UserService}.
 */
interface UserRepository extends JpaRepository<User, Long> {

    List<User> findAllByOrderByUsernameAsc();

    List<User> findByActiveTrueOrderByUsernameAsc();

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleId(long roleId);

    List<User> findByRoleIdOrderByUsernameAsc(long roleId);

    /**
     * The ids of everyone holding a role — who has to be logged out when it is narrowed.
     *
     * <p>Ids rather than entities, and that is the point rather than an optimisation: the caller
     * needs nothing but the id, and returning entities from here would invite somebody to touch a
     * lazy association outside the transaction that loaded it — the trap {@code CLAUDE.md} names
     * alongside proxy self-invocation.
     *
     * <p><strong>Not restricted to active users.</strong> A deactivated account should have no
     * session, but "should have none" and "has none" are different claims, and the one place not to
     * assume the first is the code that exists to guarantee the second. {@code endAllFor} is
     * documented safe for a user with no sessions, so the wider query costs nothing.
     */
    @Query("select u.id from User u where u.role.id = :roleId")
    List<Long> findIdsByRoleId(long roleId);

    /**
     * How many active users hold a full-access role.
     *
     * <p>Guards the last administrator: deactivating the only person who can administer the
     * system leaves no route back in through the application.
     */
    @Query("select count(u) from User u where u.active and u.role.fullAccess and u.role.active")
    long countActiveAdministrators();
}
