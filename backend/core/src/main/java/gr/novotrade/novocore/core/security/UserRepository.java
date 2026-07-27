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

    /**
     * How many active users hold a full-access role.
     *
     * <p>Guards the last administrator: deactivating the only person who can administer the
     * system leaves no route back in through the application.
     */
    @Query("select count(u) from User u where u.active and u.role.fullAccess and u.role.active")
    long countActiveAdministrators();
}
