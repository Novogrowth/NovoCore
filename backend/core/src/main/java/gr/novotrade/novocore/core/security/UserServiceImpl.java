package gr.novotrade.novocore.core.security;

import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.InvalidUserException;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.UserNotFoundException;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserSessions;
import gr.novotrade.novocore.core.api.security.UserView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserServiceImpl implements UserService {

    private static final String ENTITY_TYPE = "User";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLog;

    /**
     * Required, not optional, and deliberately so.
     *
     * <p>An {@code ObjectProvider} with a no-op fallback would let the application start with
     * eviction silently doing nothing — a revoked user keeps working and nothing says why. This
     * fails to start instead, which is the same stance as the initial-owner bootstrap and the
     * database password. The core's own test context supplies a no-op implementation explicitly,
     * where it is true that no sessions exist to end.
     */
    private final UserSessions sessions;

    /**
     * Who is asking — for the guard on {@link #changeRole} that stops somebody moving themselves
     * into a role they built. Required, and empty-means-unattended, for the reasons
     * {@code RoleServiceImpl} states about its own copy.
     */
    private final CurrentUser currentUser;

    UserServiceImpl(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            AuditLogService auditLog, UserSessions sessions, CurrentUser currentUser) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.auditLog = auditLog;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public Optional<UserView> authenticate(String username, String rawPassword) {
        // Not readOnly: this writes an audit entry either way. A login trail that only records
        // successes is not much of a control.
        if (username == null || rawPassword == null) {
            return Optional.empty();
        }
        String normalised = normaliseUsername(username);
        Optional<User> found = users.findByUsername(normalised);

        // The password is verified even when the username is unknown, against a hash that cannot
        // match. Skipping the work would make a failed lookup measurably faster than a failed
        // password and turn response time into a username oracle.
        boolean passwordCorrect = found
                .map(user -> user.passwordMatches(passwordEncoder, rawPassword))
                .orElseGet(() -> {
                    passwordEncoder.matches(rawPassword, DUMMY_HASH);
                    return false;
                });

        if (found.isEmpty() || !passwordCorrect) {
            recordFailure(normalised, found.isEmpty() ? "unknown-username" : "bad-password");
            return Optional.empty();
        }

        User user = found.get();
        if (!user.isActive()) {
            recordFailure(normalised, "user-inactive");
            return Optional.empty();
        }
        if (!user.getRole().isActive()) {
            recordFailure(normalised, "role-inactive");
            return Optional.empty();
        }

        auditLog.recordSystemAction("user.login-succeeded", Map.of("username", normalised));
        return Optional.of(SecurityViews.toView(user));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserView> find(long id) {
        return users.findById(id).map(SecurityViews::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView require(long id) {
        return find(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserView> findByUsername(String username) {
        Objects.requireNonNull(username, "username");
        return users.findByUsername(normaliseUsername(username)).map(SecurityViews::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserView> all() {
        return users.findAllByOrderByUsernameAsc().stream().map(SecurityViews::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserView> active() {
        return users.findByActiveTrueOrderByUsernameAsc().stream()
                .map(SecurityViews::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserView> inRole(long roleId) {
        return users.findByRoleIdOrderByUsernameAsc(roleId).stream()
                .map(SecurityViews::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean noUsersExist() {
        return users.count() == 0;
    }

    @Override
    @Transactional
    public UserView create(NewUser request) {
        Objects.requireNonNull(request, "request");
        String username = requireUsername(request.username());
        String displayName = requireText(request.displayName(), "Display name");
        PasswordPolicy.check(request.rawPassword());

        if (users.existsByUsername(username)) {
            throw new InvalidUserException("Username '" + username + "' is already taken.");
        }
        Role role = requireActiveRole(request.roleId());
        refuseIfConferringFullAccessTheCallerLacks(role, "create an account in");

        User saved = users.save(new User(
                username, displayName, passwordEncoder.encode(request.rawPassword()), role));

        // No password, hash or length recorded — an audit log that hints at password contents is
        // a second place they leak from.
        auditLog.record("user.created", ENTITY_TYPE, String.valueOf(saved.getId()), Map.of(
                "username", username,
                "role", role.getName()));

        return SecurityViews.toView(saved);
    }

    @Override
    @Transactional
    public void changePassword(long id, String newRawPassword) {
        PasswordPolicy.check(newRawPassword);
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));

        user.replacePasswordHash(passwordEncoder.encode(newRawPassword));

        // A password reset is, overwhelmingly, either containment of a compromise or an
        // offboarding. Leaving the existing sessions alive would mean the new password locks out
        // only the legitimate owner, while whoever prompted the reset keeps working — which is the
        // opposite of what resetting it was for.
        int ended = sessions.endAllFor(id);

        auditLog.record("user.password-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "username", user.getUsername(),
                "sessionsEnded", String.valueOf(ended)));
    }

    @Override
    @Transactional
    public UserView rename(long id, String newDisplayName) {
        String displayName = requireText(newDisplayName, "Display name");
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));

        String previous = user.getDisplayName();
        user.rename(displayName);

        auditLog.record("user.renamed", ENTITY_TYPE, String.valueOf(id),
                Map.of("from", previous, "to", displayName));

        return SecurityViews.toView(user);
    }

    @Override
    @Transactional
    public UserView changeLanguage(long id, String languageTag) {
        // Validated before the lookup so a malformed tag is refused the same way whether or not the
        // id happens to exist — the alternative distinguishes them and answers 404 for a request
        // that was wrong on its own terms.
        String normalised = LanguageTag.normalise(languageTag);
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));

        String previous = user.getLanguage();
        user.chooseLanguage(normalised);

        // "(none)" rather than omitting the key: clearing a preference is a real change and an
        // audit entry that records only one side of it does not say what happened.
        auditLog.record("user.language-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "username", user.getUsername(),
                "from", previous == null ? "(none)" : previous,
                "to", normalised == null ? "(none)" : normalised));

        return SecurityViews.toView(user);
    }

    @Override
    @Transactional
    public UserView changeRole(long id, long roleId) {
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        Role newRole = requireActiveRole(roleId);
        Role previous = user.getRole();

        // The other half of the guard RoleServiceImpl.refuseIfCallerHolds applies. That one stops
        // somebody widening the role they hold; this stops them stepping into a wider one they
        // built. Either alone leaves the escalation open, because the two together are one act.
        // Unattended calls are unaffected: nobody is escalating when there is no caller.
        currentUser.find()
                .filter(caller -> caller.id() == id)
                .ifPresent(caller -> {
                    throw new InvalidUserException(
                            "You cannot change your own role. Moving yourself into a role you can "
                                    + "edit would let one person grant themselves any access in the "
                                    + "system. Ask another administrator to make this change.");
                });

        // Moving the last administrator out of a full-access role is the same lockout as
        // deactivating them, so it is checked the same way.
        if (previous.isFullAccess() && !newRole.isFullAccess() && user.isActive()
                && users.countActiveAdministrators() <= 1) {
            throw new InvalidUserException(
                    "'" + user.getUsername() + "' is the only active user with full access. "
                            + "Moving them to '" + newRole.getName() + "' would leave nobody able "
                            + "to administer the system. Give another user full access first.");
        }

        refuseIfConferringFullAccessTheCallerLacks(newRole, "move a user into");

        user.moveToRole(newRole);

        // The session holds a snapshot of the user taken at login, including their whole resolved
        // role, so somebody moved to a narrower role would keep the old permissions until their
        // session ended. Ending it is the only thing that makes the change take effect now.
        int ended = sessions.endAllFor(id);

        auditLog.record("user.role-changed", ENTITY_TYPE, String.valueOf(id), Map.of(
                "username", user.getUsername(),
                "from", previous.getName(),
                "to", newRole.getName(),
                "sessionsEnded", String.valueOf(ended)));

        return SecurityViews.toView(user);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (!user.isActive()) {
            return;
        }
        if (user.getRole().isFullAccess() && users.countActiveAdministrators() <= 1) {
            throw new InvalidUserException(
                    "'" + user.getUsername() + "' is the only active user with full access and "
                            + "cannot be deactivated: there would be no way back into user "
                            + "administration through the application.");
        }

        user.setActive(false);

        // The point of deactivating an account is that it stops working. Without this it does not:
        // the session outlives the deactivation by up to its whole lifetime, which is the wrong
        // answer for the two cases this exists for — a departing employee and a compromised
        // account. Inside the transaction, so a rolled-back deactivation does not log somebody out
        // of an account that is still active.
        int ended = sessions.endAllFor(id);

        auditLog.record("user.deactivated", ENTITY_TYPE, String.valueOf(id), Map.of(
                "username", user.getUsername(),
                "sessionsEnded", String.valueOf(ended)));
    }

    @Override
    @Transactional
    public void reactivate(long id) {
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (user.isActive()) {
            return;
        }

        user.setActive(true);
        auditLog.record("user.reactivated", ENTITY_TYPE, String.valueOf(id),
                Map.of("username", user.getUsername()));
    }

    /**
     * Refuses when the caller is putting somebody into a full-access role without holding one.
     *
     * <p><strong>The other half of the anti-escalation rule</strong>, and gated on the
     * {@code fullAccess} flag rather than per-section — which is the right shape here precisely
     * because the flag is not a set of grants. Owner and Admin are full access by flag and hold no
     * grant rows at all, so there is no per-section comparison to make against a role whose whole
     * meaning is "everything, including sections not built yet".
     *
     * <p>This matches the precedent {@code RoleService.create} already sets: no custom role can
     * <em>become</em> full access. Without this, that refusal was trivially sidestepped — a role
     * holding only {@code USERS_AND_ROLES:FULL} could not create a full-access role, but could
     * create an account in one of the two that already exist and log in as it.
     *
     * <p>The per-section rule in {@code RoleServiceImpl.refuseIfCallerCannotConferIt} closes the
     * other route to the same place: building a new custom role and granting it what the actor does
     * not hold. Both are needed. Neither alone closes the compound path, which is why
     * {@code PrivilegeEscalationIT} walks the whole sequence rather than testing each in isolation.
     *
     * <p>Unattended calls pass through, which is what lets the initial-owner bootstrap create the
     * first Owner when there is nobody to have authorised it.
     */
    private void refuseIfConferringFullAccessTheCallerLacks(Role role, String whatWasAttempted) {
        if (!role.isFullAccess()) {
            return;
        }
        Optional<UserView> caller = currentUser.find();
        if (caller.isEmpty() || caller.get().role().fullAccess()) {
            return;
        }
        throw new InvalidUserException(
                "You cannot " + whatWasAttempted + " '" + role.getName() + "', which has full "
                        + "access to everything, because your own role does not. Otherwise "
                        + "administering users would be a route to unlimited access: create an "
                        + "account in a full-access role, then log in as it. Ask an existing "
                        + "administrator.");
    }

    private Role requireActiveRole(long roleId) {
        Role role = roles.findById(roleId).orElseThrow(() ->
                new InvalidUserException("No role with id " + roleId + "."));
        if (!role.isActive()) {
            throw new InvalidUserException(
                    "Role '" + role.getName() + "' is inactive and cannot be assigned.");
        }
        return role;
    }

    private void recordFailure(String username, String reason) {
        // The reason is recorded here even though it is never returned to the caller: an
        // administrator investigating needs to tell a typo from a locked-out account, while a
        // client must not be able to.
        auditLog.recordSystemAction("user.login-failed",
                Map.of("username", username, "reason", reason));
    }

    private static String normaliseUsername(String username) {
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireUsername(String username) {
        String normalised = normaliseUsername(requireText(username, "Username"));
        if (!normalised.matches("[a-z0-9._-]{3,100}")) {
            throw new InvalidUserException(
                    "Username must be 3-100 characters of lower-case letters, digits, dot, "
                            + "underscore or hyphen.");
        }
        return normalised;
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new InvalidUserException(what + " must not be blank.");
        }
        return value.trim();
    }

    /**
     * A real BCrypt hash of a value nobody knows, used only to spend the same time verifying an
     * unknown username as a known one. Never matches anything a caller can supply.
     */
    private static final String DUMMY_HASH =
            "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
}
