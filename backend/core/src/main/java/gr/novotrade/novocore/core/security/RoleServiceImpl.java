package gr.novotrade.novocore.core.security;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.InvalidRoleException;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleNotFoundException;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserSessions;
import gr.novotrade.novocore.core.api.security.UserView;
import gr.novotrade.novocore.core.support.Specifications;
import gr.novotrade.novocore.core.support.TextSearch;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RoleServiceImpl implements RoleService {

    /** What a substring search looks at. */
    private static final String[] SEARCHABLE = {"name", "description"};


    private static final String ENTITY_TYPE = "Role";

    private final RoleRepository roles;
    private final UserRepository users;
    private final AuditLogService auditLog;

    /**
     * Required, not an {@code ObjectProvider} — the same stance and for the same reason as
     * {@code UserServiceImpl}'s copy of it.
     *
     * <p>Until this step, narrowing a role ended nobody's session, because there was no route to
     * {@link #grant} at all: role editing was direct SQL, which never went through Java. Adding the
     * route without this would have made revoking a section a one-click operation that leaves every
     * holder of the role using it for up to a full session lifetime.
     */
    private final UserSessions sessions;

    /**
     * Who is asking — for the self-modification guard on {@link #grant} and {@link #restrictField}.
     *
     * <p>Required rather than resolved through an {@code ObjectProvider}, so that the deployable
     * application cannot start without one. The core's own test context declares an explicit
     * unattended implementation, where "there is no logged-in user" is simply true.
     *
     * <p><strong>An empty answer disables the guard, and that is correct rather than a hole.</strong>
     * Empty means unattended — the Flyway seed, the initial-owner bootstrap, a test arranging
     * fixtures. None of those is a user escalating their own privileges, which is the only thing the
     * guard exists to stop.
     */
    private final CurrentUser currentUser;

    RoleServiceImpl(RoleRepository roles, UserRepository users, AuditLogService auditLog,
            UserSessions sessions, CurrentUser currentUser) {
        this.roles = roles;
        this.users = users;
        this.auditLog = auditLog;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleView> all() {
        return roles.findAllByOrderByNameAsc().stream().map(SecurityViews::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleView> active() {
        return roles.findByActiveTrueOrderByNameAsc().stream()
                .map(SecurityViews::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleView> search(String term, boolean activeOnly) {
        return roles.findAll(
                        Specifications.<Role>activeOnly(activeOnly)
                                .and(TextSearch.matching(term, SEARCHABLE)),
                        Sort.by(Sort.Order.asc("name")))
                .stream()
                .map(SecurityViews::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleView> find(long id) {
        return roles.findById(id).map(SecurityViews::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleView require(long id) {
        return find(id).orElseThrow(() -> new RoleNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleView> findByName(String name) {
        Objects.requireNonNull(name, "name");
        return roles.findByNameIgnoreCase(name.trim()).map(SecurityViews::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleView requireByName(String name) {
        return findByName(name).orElseThrow(() -> new RoleNotFoundException(name));
    }

    @Override
    @Transactional
    public RoleView create(NewRole request) {
        Objects.requireNonNull(request, "request");
        String name = requireText(request.name(), "Role name");

        if (roles.existsByNameIgnoreCase(name)) {
            throw new InvalidRoleException("A role named '" + name + "' already exists.");
        }

        // Created with no grants at all — default-deny. There is deliberately no way to create a
        // full-access role: Owner and Admin already are, and a second route to unlimited access
        // is a second thing to have to audit.
        Role saved = roles.save(new Role(name, request.description()));

        auditLog.record("role.created", ENTITY_TYPE, String.valueOf(saved.getId()),
                Map.of("name", name));

        return SecurityViews.toView(saved);
    }

    @Override
    @Transactional
    public RoleView grant(long roleId, Section section, AccessLevel accessLevel) {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(accessLevel, "accessLevel");
        Role role = editableRole(roleId, "change its permissions");
        refuseIfCallerHolds(role, "change the permissions of");
        refuseIfCallerCannotConferIt(section, accessLevel);

        AccessLevel previous = role.getSectionGrants().getOrDefault(section, AccessLevel.NONE);
        role.grant(section, accessLevel);

        // Taking access away has to take effect now; giving it must not cost a re-login. See
        // AccessLevel.isNarrowerThan for why the asymmetry is the point.
        int ended = accessLevel.isNarrowerThan(previous) ? endSessionsOfHolders(role) : 0;

        auditLog.record("role.granted", ENTITY_TYPE, String.valueOf(roleId), Map.of(
                "role", role.getName(),
                "section", section.name(),
                "from", previous.name(),
                "to", accessLevel.name(),
                "sessionsEnded", String.valueOf(ended)));

        return SecurityViews.toView(role);
    }

    @Override
    @Transactional
    public RoleView restrictField(long roleId, ProtectedField field, boolean restricted) {
        Objects.requireNonNull(field, "field");
        Role role = editableRole(roleId, "change its field restrictions");
        refuseIfCallerHolds(role, "change the field restrictions of");

        // Restricting a field that is already restricted takes nothing away, so it is not a
        // narrowing and must not end anybody's session. Read before the change, because the change
        // is what makes the two states indistinguishable afterwards.
        boolean wasRestricted = role.getRestrictedFields().contains(field);
        role.restrictField(field, restricted);

        int ended = restricted && !wasRestricted ? endSessionsOfHolders(role) : 0;

        auditLog.record("role.field-restriction-changed", ENTITY_TYPE, String.valueOf(roleId),
                Map.of(
                        "role", role.getName(),
                        "field", field.name(),
                        "restricted", String.valueOf(restricted),
                        "sessionsEnded", String.valueOf(ended)));

        return SecurityViews.toView(role);
    }

    @Override
    @Transactional
    public RoleView rename(long roleId, String newName) {
        String name = requireText(newName, "Role name");
        Role role = editableRole(roleId, "be renamed");

        if (!role.getName().equalsIgnoreCase(name) && roles.existsByNameIgnoreCase(name)) {
            throw new InvalidRoleException("A role named '" + name + "' already exists.");
        }

        String previous = role.getName();
        role.rename(name);

        auditLog.record("role.renamed", ENTITY_TYPE, String.valueOf(roleId),
                Map.of("from", previous, "to", name));

        return SecurityViews.toView(role);
    }

    @Override
    @Transactional
    public void deactivate(long roleId) {
        Role role = editableRole(roleId, "be deactivated");
        if (!role.isActive()) {
            return;
        }

        // Refused rather than cascading. Deactivating a role silently revokes the access of
        // everyone holding it, and someone losing access with no explanation is a worse outcome
        // than being told to move them first (CLAUDE.md rule 7).
        //
        // This refusal is also why deactivation needs no session eviction, even though an inactive
        // role grants nothing (RoleView.accessTo): there is provably nobody holding it to evict.
        // Asserted by a test rather than left as a comment, so that weakening this refusal fails
        // loudly instead of quietly leaving deactivation unable to log anyone out.
        long holders = users.countByRoleId(roleId);
        if (holders > 0) {
            throw new InvalidRoleException(
                    "Role '" + role.getName() + "' still has " + holders + " user(s). Move them "
                            + "to another role first — deactivating this would revoke their "
                            + "access with nothing to say why.");
        }

        role.setActive(false);
        auditLog.record("role.deactivated", ENTITY_TYPE, String.valueOf(roleId),
                Map.of("name", role.getName()));
    }

    @Override
    @Transactional
    public void reactivate(long roleId) {
        Role role = roles.findById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId));
        if (role.isActive()) {
            return;
        }

        role.setActive(true);
        auditLog.record("role.reactivated", ENTITY_TYPE, String.valueOf(roleId),
                Map.of("name", role.getName()));
    }

    /**
     * Loads a role, refusing if it is a system role.
     *
     * @param whatWasAttempted completes the sentence "... cannot ___", so the message says what
     *     was refused rather than just that something was
     */
    private Role editableRole(long roleId, String whatWasAttempted) {
        Role role = roles.findById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId));
        if (role.isSystemRole()) {
            throw new InvalidRoleException(
                    "'" + role.getName() + "' is a system role and cannot " + whatWasAttempted
                            + ". Without this, removing USERS_AND_ROLES from the last role that "
                            + "has it would lock everyone out of user administration.");
        }
        return role;
    }

    /**
     * Ends every logged-in session belonging to anybody holding this role.
     *
     * <p>Called <strong>inside</strong> the transaction that narrowed it, which
     * {@link UserSessions} requires: a rolled-back revocation must not log people out of access
     * they still have, and — the half that matters more — a committed one must not leave them
     * logged in with the access it just took away.
     *
     * @return how many sessions ended, for the audit entry. "Narrowed, and three sessions were
     *     killed" and "narrowed, and none were" describe genuinely different situations to whoever
     *     reads the log afterwards.
     */
    private int endSessionsOfHolders(Role role) {
        int ended = 0;
        for (Long holderId : users.findIdsByRoleId(role.getId())) {
            ended += sessions.endAllFor(holderId);
        }
        return ended;
    }

    /**
     * Refuses when the caller is trying to edit the permissions of the role they themselves hold.
     *
     * <p><strong>The escalation this closes.</strong> {@code USERS_AND_ROLES} is a grantable
     * section, not an Owner-only hard-coding (see the step 16b proposal, §1.5), so a custom role can
     * legitimately be given user administration. Without this, such a role could grant itself
     * {@code JOURNAL} — every financial figure in the business — in one request, with nobody else
     * involved. Requiring a second person turns escalation into an act somebody else has to perform.
     *
     * <p><strong>It never bites Owner or Admin</strong>, who are system roles and are already
     * refused by {@code editableRole} before reaching here. So the guard cannot lock the last
     * administrator out of their own system, which is the failure that would make it worse than the
     * hole it closes.
     *
     * <p><strong>⚠️ This is not a complete anti-escalation control, and should not be read as one.</strong>
     * It stops a user widening <em>their own</em> role. It does not stop a user with
     * {@code USERS_AND_ROLES} creating a <em>new</em> account in a full-access role and logging in
     * as it, nor granting another role more than the granter holds. Closing those needs a different
     * and broader rule — "you may not grant access you do not yourself hold" — which is raised as a
     * decision rather than assumed here.
     */
    private void refuseIfCallerHolds(Role role, String whatWasAttempted) {
        Optional<UserView> caller = currentUser.find();
        Long roleId = role.getId();
        if (caller.isEmpty() || roleId == null || caller.get().role().id() != roleId) {
            return;
        }
        throw new InvalidRoleException(
                "You cannot " + whatWasAttempted + " '" + role.getName() + "', which is your own "
                        + "role. Changing the permissions of the role you hold would let one person "
                        + "grant themselves access nobody else approved. Ask another administrator "
                        + "to make this change.");
    }

    /**
     * Refuses when the caller is trying to confer more access on a section than they hold there.
     *
     * <p><strong>This is the half of the anti-escalation rule that {@code refuseIfCallerHolds}
     * cannot reach.</strong> That one stops somebody widening their own role. It does nothing about
     * a role holding only {@code USERS_AND_ROLES:FULL} creating a <em>second</em> role, granting it
     * {@code JOURNAL:FULL}, and putting somebody — or a new account — into it. Every step of that is
     * legitimate on its own; the sequence is a one-person path to every financial figure in the
     * business.
     *
     * <p><strong>Per-section, not on the {@code fullAccess} flag.</strong> Gating this on the flag
     * would have let exactly the sequence above through, because the actor never touches a
     * full-access role at any point in it. The rule that actually closes it is the ordinary one:
     * you cannot give away what you do not have.
     *
     * <p><strong>Read through {@code RoleView.accessTo}, never off the grant rows.</strong> Owner
     * and Admin carry no {@code role_section_grant} rows at all — their access is the
     * {@code fullAccess} flag — so a check reading stored grants directly would conclude the Owner
     * may grant nothing, and would keep concluding it for every section added in future. Asking the
     * view instead means a full-access actor holds {@code FULL} everywhere by construction, which is
     * what the flag already means everywhere else.
     *
     * <p><strong>Revoking is always allowed</strong>, including on a section the caller cannot see:
     * {@link AccessLevel#NONE} is narrower than everything, so this never refuses a de-escalation.
     * Taking access away is not an escalation and must not need the access being taken.
     */
    private void refuseIfCallerCannotConferIt(Section section, AccessLevel accessLevel) {
        Optional<UserView> caller = currentUser.find();
        if (caller.isEmpty()) {
            return;
        }
        AccessLevel callerHolds = caller.get().role().accessTo(section);
        if (!callerHolds.isNarrowerThan(accessLevel)) {
            return;
        }
        throw new InvalidRoleException(
                "You cannot grant " + accessLevel + " on " + section + ", because your own role has "
                        + callerHolds + " there. Access can only be passed on, never invented: "
                        + "otherwise administering users would be a route to every section in the "
                        + "system. Ask somebody who holds " + accessLevel + " on " + section + ".");
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidRoleException(what + " must not be blank.");
        }
        return value.trim();
    }
}
