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

    /**
     * Roles whose name or description contains the term anywhere, ignoring case and accents.
     *
     * <p>The description is searched because it is where the answer to "which role lets somebody do
     * X" actually lives — a name alone rarely says.
     *
     * @param term matched as a substring; null or blank means no filter. Wildcards are literal.
     * @param activeOnly whether to restrict to active roles, combining with the term
     */
    List<RoleView> search(String term, boolean activeOnly);

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
     * Changes what this role is for, in words. Confers nothing.
     *
     * <p>⚠️ <strong>Added in Q1 (2026-08-03), backend queue item 5.</strong> A description could be
     * set at creation and never changed — there was no route, no service method, and no setter on
     * the entity — so correcting a typo meant creating a second role, moving every holder across and
     * deactivating the first. The asymmetry had no argument behind it anywhere in the code or in the
     * step 16b proposal.
     *
     * <p><strong>Deliberately not general role editing.</strong> Name, grants and field
     * restrictions each keep their own operation, because each is separately audited and two of them
     * change what somebody can do. This one does not.
     *
     * @param description blank or null clears it, which is a state {@link NewRole} already permits
     * @throws InvalidRoleException if the role is a system role
     */
    RoleView describe(long roleId, String description);

    /**
     * @throws InvalidRoleException if the role is a system role, or if any user still holds it —
     *     deactivating a role silently revokes the access of everyone in it
     */
    void deactivate(long roleId);

    void reactivate(long roleId);
}
