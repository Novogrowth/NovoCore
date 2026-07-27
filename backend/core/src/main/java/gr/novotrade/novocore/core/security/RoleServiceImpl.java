package gr.novotrade.novocore.core.security;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.InvalidRoleException;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleNotFoundException;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RoleServiceImpl implements RoleService {

    private static final String ENTITY_TYPE = "Role";

    private final RoleRepository roles;
    private final UserRepository users;
    private final AuditLogService auditLog;

    RoleServiceImpl(RoleRepository roles, UserRepository users, AuditLogService auditLog) {
        this.roles = roles;
        this.users = users;
        this.auditLog = auditLog;
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

        AccessLevel previous = role.getSectionGrants().getOrDefault(section, AccessLevel.NONE);
        role.grant(section, accessLevel);

        auditLog.record("role.granted", ENTITY_TYPE, String.valueOf(roleId), Map.of(
                "role", role.getName(),
                "section", section.name(),
                "from", previous.name(),
                "to", accessLevel.name()));

        return SecurityViews.toView(role);
    }

    @Override
    @Transactional
    public RoleView restrictField(long roleId, ProtectedField field, boolean restricted) {
        Objects.requireNonNull(field, "field");
        Role role = editableRole(roleId, "change its field restrictions");

        role.restrictField(field, restricted);

        auditLog.record("role.field-restriction-changed", ENTITY_TYPE, String.valueOf(roleId),
                Map.of(
                        "role", role.getName(),
                        "field", field.name(),
                        "restricted", String.valueOf(restricted)));

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

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidRoleException(what + " must not be blank.");
        }
        return value.trim();
    }
}
