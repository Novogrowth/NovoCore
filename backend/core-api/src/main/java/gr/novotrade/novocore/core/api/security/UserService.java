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

    /**
     * Accounts whose username or display name contains the term anywhere, ignoring case and accents.
     *
     * <p>The display name is searched as well as the username on purpose: an administrator looking
     * for somebody knows their name, and may well not know what username they were given.
     *
     * <p>Nothing about a password is searchable, which is not worth saying except that the hash is a
     * column on this table and its absence from the searched set is a decision rather than an
     * oversight.
     *
     * @param term matched as a substring; null or blank means no filter. Wildcards are literal.
     * @param activeOnly whether to restrict to active accounts, combining with the term
     */
    List<UserView> search(String term, boolean activeOnly);

    /**
     * Everyone holding a role, active and inactive, by username.
     *
     * <p>Exists because {@link RoleService#deactivate} refuses while any user still holds the role,
     * and an operator who hits that refusal has to be able to find out <em>who</em> — a refusal
     * naming a count and not the people is a dead end. Includes deactivated accounts, because they
     * hold the role too and are equally in the way of deactivating it.
     */
    List<UserView> inRole(long roleId);

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
     * Records which language this person wants the interface in.
     *
     * <p><strong>Nothing in the core reads it.</strong> The backend localises none of its own
     * messages (Q47b), so this is stored for a frontend to act on and for no other purpose — stated
     * here so nobody later assumes an error message will honour it.
     *
     * <p>The tag is normalised before storage ({@code el-gr} and {@code EL-GR} are one preference),
     * and its <em>shape</em> is checked while the set of languages deliberately is not: the backend
     * supports no particular language, so it is in no position to refuse one.
     *
     * @param languageTag a BCP 47 tag such as {@code "el"} or {@code "el-GR"}, or null/blank to
     *     clear the preference — which is allowed, because "has not chosen" is a state a user is
     *     entitled to return to
     * @throws InvalidUserException if the value is not a language tag
     * @throws UserNotFoundException if there is no such user
     */
    UserView changeLanguage(long id, String languageTag);

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
