package gr.novotrade.novocore.core.web.security;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import gr.novotrade.novocore.core.api.shared.Mandatory;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.api.shared.Required;
import gr.novotrade.novocore.core.web.Requires;
import java.util.EnumSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User accounts — the administration that was direct SQL until step 16b.
 *
 * <h2>Governed by a grantable section, not hard-coded to Owner and Admin</h2>
 *
 * <p>{@link Section#USERS_AND_ROLES} exists for exactly this, and brief §7 requires support for
 * multiple custom roles from the start — so a custom role can legitimately be given user
 * administration. In practice this is Owner-and-Admin-only today, because access is default-deny and
 * nothing else is granted the section; the difference is that it stays configuration rather than
 * becoming a special case in code.
 *
 * <p><strong>That decision has a consequence, and it is guarded rather than accepted.</strong>
 * Administering users would otherwise be a route to unlimited access — create an account in a
 * full-access role, log in as it. {@code UserServiceImpl} refuses to confer full access a caller does
 * not itself hold, and refuses to let anybody change their own role; {@code RoleServiceImpl} refuses
 * to confer, on any section, a level the caller does not hold there. The two together close a
 * compound path that neither closes alone — see {@code PrivilegeEscalationIT}, which walks it.
 *
 * <h2>No password ever leaves through here</h2>
 *
 * <p>{@link UserView} has no hash field and there is no accessor anywhere that returns one, so these
 * responses cannot leak one. {@link #changePassword} is write-only and answers 204 rather than
 * echoing the account back, because the one thing a caller might read from a password response is
 * the one thing that must not be in it.
 */
@RestController
@Requires(section = Section.USERS_AND_ROLES)
class UserController {

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    /**
     * Every account, or only the active ones.
     *
     * <p>Unpaged, and it carries the {@code ListResponse} envelope without a {@code page} block —
     * the "bounded by the business" tier. A business with tens of thousands of user accounts is not
     * this business, and if it ever became one this gains paging with no change to the shape a
     * frontend parses.
     */
    @GetMapping(path = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<UserView> users(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        boolean activeOnly = Boolean.TRUE.equals(active);
        if (search != null) {
            return ListResponse.of(users.search(search, activeOnly));
        }
        return ListResponse.of(activeOnly ? users.active() : users.all());
    }

    @GetMapping(path = "/api/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    UserView user(@PathVariable long id) {
        return users.require(id);
    }

    /**
     * Every section that exists, with whether anything is built behind it.
     *
     * <p>What a role editor renders its grid from. Distinct from {@code GET /api/me}, which reports
     * the <em>caller's own</em> levels: this is the catalogue, and an administrator configuring
     * somebody else's role needs the full list including sections they personally cannot see.
     *
     * <p>{@link Section#isAvailable()} is carried for the reason {@code /api/me} carries it — so a
     * screen can distinguish a section nobody has built yet from one this role simply lacks. A
     * reserved section can be granted and its grant stored, so the permission model is complete
     * before the features are.
     */
    @GetMapping(path = "/api/sections", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SectionView> sections() {
        return ListResponse.of(EnumSet.allOf(Section.class).stream()
                .map(section -> new SectionView(section, section.isAvailable()))
                .toList());
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    @PostMapping(path = "/api/users",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    UserView create(@RequestBody NewUser request) {
        return users.create(request);
    }

    @PatchMapping(path = "/api/users/{id}/display-name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    UserView rename(@PathVariable long id, @RequestBody DisplayNameRequest request) {
        return users.rename(id, request.displayName());
    }

    /**
     * Moves somebody to a different role. <strong>Ends their sessions.</strong>
     *
     * <p>The session holds the whole resolved role as a snapshot taken at login, so without eviction
     * a demotion would leave the old permissions in use for up to a full session lifetime. Refused
     * when the caller names themselves, and when the target role is full access and the caller's is
     * not — see the class javadoc.
     */
    @PatchMapping(path = "/api/users/{id}/role",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    UserView changeRole(@PathVariable long id, @RequestBody RoleRequest request) {
        return users.changeRole(id, request.roleId());
    }

    /**
     * Sets somebody's password. <strong>Ends their sessions.</strong>
     *
     * <p>An administrator resetting a password is, overwhelmingly, containment of a compromise or an
     * offboarding — so leaving the existing sessions alive would mean the new password locks out only
     * the legitimate owner while whoever prompted the reset keeps working.
     *
     * <p>⚠️ <strong>This is not a change-your-own-password route.</strong> It requires
     * {@code USERS_AND_ROLES} at {@code FULL} and does not ask for the current password, because an
     * administrator resetting an account they do not own has no way to supply one. Self-service
     * password change is a separate route with a separate shape and is not built.
     */
    @PatchMapping(path = "/api/users/{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@PathVariable long id, @RequestBody PasswordRequest request) {
        users.changePassword(id, request.password());
    }

    /**
     * Deactivates an account. <strong>Ends its sessions.</strong>
     *
     * <p>Not a delete: the audit log and every record's {@code created_by} refer to the username, and
     * those have to stay explicable. Refused for the last active full-access user, which would leave
     * nobody able to administer the system.
     */
    @PostMapping(path = "/api/users/{id}/deactivate")
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        users.deactivate(id);
    }

    @PostMapping(path = "/api/users/{id}/reactivate")
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        users.reactivate(id);
    }

    // -------------------------------------------------------------------------------------------

    /**
     * @param available whether anything is built behind this section yet — see {@link #sections()}
     */
    record SectionView(Section section, boolean available) {
    }

    record DisplayNameRequest(@Mandatory String displayName) {

        DisplayNameRequest {
            Required.text(displayName, "displayName");
        }
    }

    /**
     * @param roleId boxed, not a primitive, so an omitted field is a 400 naming it rather than a 0
     *     that becomes "role 0 does not exist" — a 404 about a role nobody mentioned
     */
    record RoleRequest(@Mandatory Long roleId) {

        RoleRequest {
            Required.field(roleId, "roleId");
        }
    }

    /**
     * The policy — 12 characters, no composition rules (Q29, NIST SP 800-63B) — is the core's to
     * state, so only presence is checked here.
     */
    record PasswordRequest(@Mandatory String password) {

        PasswordRequest {
            Required.text(password, "password");
        }

        /** Keeps the password out of logs and stack traces that print the record. */
        @Override
        public String toString() {
            return "PasswordRequest[password=<not shown>]";
        }
    }
}
