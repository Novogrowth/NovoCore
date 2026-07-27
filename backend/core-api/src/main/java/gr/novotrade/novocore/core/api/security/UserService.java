package gr.novotrade.novocore.core.api.security;

import java.util.List;
import java.util.Optional;

/**
 * User accounts and password verification.
 *
 * <p><strong>Password hashes never leave the core.</strong> {@link #authenticate} takes the plain
 * password and returns the user or nothing, so hashing and comparison both happen in here. The
 * conventional Spring arrangement — a {@code UserDetailsService} handing the hash out for the
 * framework to verify — would put the hash on the boundary and, by extension, into every stack
 * trace and debugger that touches the authentication path. Nothing outside the core can obtain
 * one, which is a stronger guarantee than remembering not to log it.
 */
public interface UserService {

    /**
     * Verifies a username and password.
     *
     * <p>Returns empty for an unknown username, a wrong password, a deactivated user and a user
     * whose role has been deactivated — without distinguishing between them. The caller has no
     * legitimate use for the difference, and telling it apart is how an attacker confirms which
     * usernames exist. Both outcomes are written to the audit log, which is where the distinction
     * legitimately lives.
     */
    Optional<UserView> authenticate(String username, String rawPassword);

    Optional<UserView> find(long id);

    /** @throws UserNotFoundException if absent */
    UserView require(long id);

    Optional<UserView> findByUsername(String username);

    /** Every account, active and inactive, by username. */
    List<UserView> all();

    List<UserView> active();

    /** True when no account exists at all — the condition the initial-owner bootstrap tests. */
    boolean noUsersExist();

    /**
     * @throws InvalidUserException if the username is taken, the password fails the policy, or the
     *     role does not exist or is inactive
     */
    UserView create(NewUser request);

    /**
     * Replaces a user's password.
     *
     * @throws InvalidUserException if the new password fails the policy
     */
    void changePassword(long id, String newRawPassword);

    UserView rename(long id, String newDisplayName);

    /**
     * Moves a user to a different role.
     *
     * @throws InvalidUserException if the role does not exist or is inactive
     */
    UserView changeRole(long id, long roleId);

    /**
     * Deactivates an account. Not a delete: the audit log and every record's {@code created_by}
     * refer to the username, and those must stay explicable.
     *
     * @throws InvalidUserException if this is the last active user holding a full-access role,
     *     which would leave nobody able to administer the system
     */
    void deactivate(long id);

    void reactivate(long id);
}
