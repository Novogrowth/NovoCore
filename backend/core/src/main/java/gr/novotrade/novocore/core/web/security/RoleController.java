package gr.novotrade.novocore.core.web.security;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Required;
import gr.novotrade.novocore.core.web.Requires;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles and what they grant.
 *
 * <h2>Roles are data; what they grant is code</h2>
 *
 * <p>{@link Section} and {@link ProtectedField} are enums, because which parts of the application
 * exist is decided by what has been built. Which sections a role holds is configuration, so creating
 * a role is an operation here rather than a migration — brief §7's "multiple custom roles from the
 * start". Both path variables below are bound to those enums, so an unknown section or field is
 * refused by Spring before any of our code runs and the accepted values appear in the OpenAPI
 * document.
 *
 * <h2>Narrowing a role ends its holders' sessions; widening one does not</h2>
 *
 * <p>The asymmetry is deliberate and load-bearing. Taking access away has to take effect now,
 * because the case it exists for is cutting somebody off — and until step 16b it did not, since
 * eviction was wired only into deactivating a user and moving one between roles. Giving access must
 * <em>not</em> cost a re-login, or an administrator's kindness becomes a penalty. See
 * {@code AccessLevel.isNarrowerThan} and {@code SessionEvictionIT}, which asserts both directions.
 *
 * <h2>Two refusals that are not about the role being edited</h2>
 *
 * <p>A caller may not edit the permissions of the role they themselves hold, and may not confer on
 * any section a level they do not hold there. Both exist because {@code USERS_AND_ROLES} is
 * grantable: without them, a role holding nothing but user administration could build itself a
 * second role carrying the journal and step into it. {@code PrivilegeEscalationIT} walks that
 * sequence and asserts it is cut at the grant.
 *
 * <p>Neither refusal can bite Owner or Admin. They are system roles, so they are refused earlier and
 * for a different reason — nobody may edit them at all, which is what stops
 * {@code USERS_AND_ROLES} being removed from the last role that has it.
 */
@RestController
@Requires(section = Section.USERS_AND_ROLES)
class RoleController {

    private final RoleService roles;
    private final UserService users;

    RoleController(RoleService roles, UserService users) {
        this.roles = roles;
        this.users = users;
    }

    // -------------------------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------------------------

    @GetMapping(path = "/api/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<RoleView> roles(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        boolean activeOnly = Boolean.TRUE.equals(active);
        if (search != null) {
            return ListResponse.of(roles.search(search, activeOnly));
        }
        return ListResponse.of(activeOnly ? roles.active() : roles.all());
    }

    @GetMapping(path = "/api/roles/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    RoleView role(@PathVariable long id) {
        return roles.require(id);
    }

    /**
     * Who holds this role — including deactivated accounts.
     *
     * <p>Exists because {@link #deactivate} is refused while anybody holds the role, and a refusal
     * naming a count and not the people is a dead end: the operator's next question is always "which
     * ones?". Deactivated accounts are included because they hold the role too and are equally in the
     * way of retiring it.
     */
    @GetMapping(path = "/api/roles/{id}/users", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<UserView> holders(@PathVariable long id) {
        // require() first, so an id naming no role is a 404 rather than an empty list that reads
        // like a role nobody holds.
        roles.require(id);
        return ListResponse.of(users.inRole(id));
    }

    // -------------------------------------------------------------------------------------------
    // Changing
    // -------------------------------------------------------------------------------------------

    /**
     * Creates a custom role with no grants at all — default-deny.
     *
     * <p>There is deliberately no way to create a full-access role: Owner and Admin already are, and
     * a second route to unlimited access is a second thing to audit. Creating a role and granting it
     * access are separate, individually audited acts, which is the more useful trail when the
     * question later is "when did this role gain Settings?".
     */
    @PostMapping(path = "/api/roles",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.CREATED)
    RoleView create(@RequestBody NewRole request) {
        return roles.create(request);
    }

    @PatchMapping(path = "/api/roles/{id}/name",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    RoleView rename(@PathVariable long id, @RequestBody NameRequest request) {
        return roles.rename(id, request.name());
    }

    /**
     * Sets this role's access to one section. {@link AccessLevel#NONE} removes the grant.
     *
     * <p>{@code PUT} rather than {@code POST} because it genuinely replaces one cell and is
     * idempotent: granting {@code VIEW} on {@code SALES} twice is the same state, and a no-op grant
     * evicts nobody precisely because nothing was taken away.
     */
    @PutMapping(path = "/api/roles/{id}/grants/{section}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    RoleView grant(
            @PathVariable long id,
            @PathVariable Section section,
            @RequestBody GrantRequest request) {
        return roles.grant(id, section, request.accessLevel());
    }

    /**
     * Hides a field from this role, or stops hiding it.
     *
     * <p>⚠️ <strong>Nothing is field-restricted anywhere today, and this route is not decorative.</strong>
     * V26 removed the three seeded restrictions because the business had no confidentiality need
     * behind them, expressed as deleting <em>data</em> rather than code — so restricting one again is
     * this call. The mechanism stays proven by roles the tests build at runtime.
     */
    @PutMapping(path = "/api/roles/{id}/field-restrictions/{field}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    RoleView restrictField(
            @PathVariable long id,
            @PathVariable ProtectedField field,
            @RequestBody FieldRestrictionRequest request) {
        return roles.restrictField(id, field, request.restricted());
    }

    /**
     * Deactivates a role. <strong>Refused while anybody still holds it.</strong>
     *
     * <p>Refused rather than cascading, because someone losing access with no explanation is a worse
     * outcome than being told to move them first (rule 7). {@link #holders} answers who. That refusal
     * is also why this needs no session eviction of its own: there is provably nobody to evict.
     */
    @PostMapping(path = "/api/roles/{id}/deactivate")
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        roles.deactivate(id);
    }

    @PostMapping(path = "/api/roles/{id}/reactivate")
    @Requires(section = Section.USERS_AND_ROLES, level = AccessLevel.FULL)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reactivate(@PathVariable long id) {
        roles.reactivate(id);
    }

    // -------------------------------------------------------------------------------------------

    record NameRequest(String name) {

        NameRequest {
            Required.text(name, "name");
        }
    }

    record GrantRequest(AccessLevel accessLevel) {

        GrantRequest {
            Required.field(accessLevel, "accessLevel");
        }
    }

    /**
     * @param restricted boxed, not a primitive. An omitted field on a primitive {@code boolean}
     *     arrives as {@code false} and would silently <em>remove</em> a restriction the caller never
     *     mentioned — a wrong answer that looks like a successful request, which is worse than the
     *     400 this produces instead.
     */
    record FieldRestrictionRequest(Boolean restricted) {

        FieldRestrictionRequest {
            Required.field(restricted, "restricted");
        }
    }
}
