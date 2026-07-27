package gr.novotrade.novocore.core.api.security;

import java.util.List;
import java.util.Optional;

/**
 * Roles and their permissions.
 *
 * <p>Roles are data, not code, because brief §7 requires support for multiple custom roles from
 * the start — so creating one is an operation here rather than a migration. {@link Section} and
 * {@link ProtectedField} stay enums, because which parts of the application exist is determined
 * by what has been built.
 *
 * <p>Two seeded roles are {@link RoleView#systemRole()} and cannot be edited or deleted: Owner
 * and Admin, both {@link RoleView#fullAccess()}. Without that, removing
 * {@link Section#USERS_AND_ROLES} from the last role that has it would lock everyone out of user
 * administration with no way back in through the application.
 */
public interface RoleService {

    /** Every role, active and inactive, by name. */
    List<RoleView> all();

    List<RoleView> active();

    Optional<RoleView> find(long id);

    /** @throws RoleNotFoundException if absent */
    RoleView require(long id);

    Optional<RoleView> findByName(String name);

    /** @throws RoleNotFoundException if absent */
    RoleView requireByName(String name);

    /**
     * Creates a custom role with no grants — default-deny, so a new role sees nothing until it is
     * given something.
     *
     * <p>Cannot create a full-access role: that is what the seeded Owner and Admin roles are for,
     * and a second route to unlimited access is a second thing to audit.
     *
     * @throws InvalidRoleException if the name is taken
     */
    RoleView create(NewRole request);

    /**
     * Sets this role's access to one section. {@link AccessLevel#NONE} removes the grant.
     *
     * @throws InvalidRoleException if the role is a system role
     */
    RoleView grant(long roleId, Section section, AccessLevel accessLevel);

    /**
     * Hides a field from this role, or stops hiding it.
     *
     * @throws InvalidRoleException if the role is a system role
     */
    RoleView restrictField(long roleId, ProtectedField field, boolean restricted);

    /** @throws InvalidRoleException if the name is taken or the role is a system role */
    RoleView rename(long roleId, String newName);

    /**
     * @throws InvalidRoleException if the role is a system role, or if any user still holds it —
     *     deactivating a role silently revokes the access of everyone in it
     */
    void deactivate(long roleId);

    void reactivate(long roleId);
}
