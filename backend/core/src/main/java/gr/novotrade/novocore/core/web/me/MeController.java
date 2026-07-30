package gr.novotrade.novocore.core.web.me;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import gr.novotrade.novocore.core.web.AuthenticatedOnly;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the caller is, and what they may reach.
 *
 * <h2>The one route on the surface with no section, and why that is not a loophole</h2>
 *
 * <p>Every other {@code /api/**} handler declares a {@link Section}. This one cannot: it is the
 * route that <em>tells</em> a caller which sections they hold, so gating it behind one would be
 * circular — a user granted nothing must still be able to log in, learn who they are, and be shown
 * an application that correctly contains nothing.
 *
 * <p>It is declared {@link AuthenticatedOnly} rather than exempted inside the three checks that
 * make {@code @Requires} mandatory, and the set of routes permitted to do that is asserted by
 * {@code WebAuthorizationRulesTest.onlyTheIdentityRouteIsSectionless}. So this is an exception that
 * is written down, bounded, and fails the build if it spreads.
 *
 * <h2>Every section, not only the visible ones</h2>
 *
 * <p>{@link #sections} returns an entry for <strong>every</strong> {@code Section}, carrying both
 * the caller's resolved {@link AccessLevel} and {@link Section#isAvailable()}. Returning only what
 * the caller can see would collapse two states a UI must distinguish and that
 * {@code Section.isAvailable()} exists precisely to separate: <em>you may not see this</em> and
 * <em>this has not been built yet</em>. They look identical to a user and have entirely different
 * fixes — one is a call to an administrator, the other is a wait for a release.
 *
 * <h2>What the frontend must not do with this</h2>
 *
 * <p>{@code hiddenFields} and the grants below are <strong>rendering hints</strong>. The backend
 * redacts and refuses on every request regardless of what any client believes, and nothing here may
 * be treated as the enforcement point. A client that decided for itself what to show would be
 * exactly one bug away from showing it.
 */
@RestController
@AuthenticatedOnly(because =
        "this is the route that tells a caller which sections they hold, so requiring a section to "
                + "reach it would be circular — a user with no grants must still be able to learn "
                + "their own identity and discover that they have none. It returns nothing about "
                + "any other party, document or amount.")
class MeController {

    private final CurrentUser currentUser;
    private final UserService users;

    MeController(CurrentUser currentUser, UserService users) {
        this.currentUser = currentUser;
        this.users = users;
    }

    /**
     * <strong>Read from the database, not from the session.</strong>
     *
     * <p>{@code CurrentUser} returns the {@code UserView} captured when this session logged in and
     * stored in the security context, so it is a snapshot that can be up to a session's lifetime
     * old. For most routes that only affects the permission check; for <em>this</em> one it would
     * mean the route whose entire job is to report the caller's current identity and grants
     * reporting yesterday's — a screen showing a section the operator no longer has, or missing one
     * they were just given, with no way to tell.
     *
     * <p>So the id comes from the session and everything else comes from the user record. One extra
     * read on a route a client calls once per page load.
     *
     * <p>⚠️ <strong>This does not fix the general case, and the general case is a real defect.</strong>
     * {@code SectionAccessInterceptor} still checks the session's snapshot, so revoking a grant,
     * changing someone's role or deactivating an account does not take effect on a live session
     * until it ends. Fixing that properly is a decision about per-request freshness with real cost
     * attached, not something to slip in behind an identity endpoint — it is recorded in
     * {@code PROGRESS.md} rather than quietly half-solved here.
     */
    @GetMapping(path = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
    Me me() {
        return describe(users.require(currentUser.require().id()));
    }

    /**
     * Records which language this person wants the interface in.
     *
     * <p><strong>A hint the backend stores and never reads.</strong> Q47(b) settled that NovoCore
     * localises none of its own messages, so nothing about a response changes as a result of this —
     * stated plainly so nobody later expects a validation message in Greek because this was set.
     *
     * <p>{@code PATCH} on the caller's own record, with no id in the path: a user changing their own
     * preference needs no {@code USERS_AND_ROLES} grant, and taking an id here would create a second
     * route to editing other people that is not governed by that section.
     *
     * <p>A null or blank {@code language} clears the preference rather than being refused —
     * "has not chosen" is a state a person is entitled to return to, and it is the state every
     * account starts in.
     */
    @PatchMapping(path = "/api/me/language",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    Me changeLanguage(@RequestBody LanguageRequest request) {
        // The id comes from the session, never from the request. See the javadoc above.
        return describe(users.changeLanguage(currentUser.require().id(), request.language()));
    }

    // -------------------------------------------------------------------------------------------

    private static Me describe(UserView user) {
        RoleView role = user.role();

        List<SectionAccess> sections = EnumSet.allOf(Section.class).stream()
                .map(section -> new SectionAccess(
                        section, user.canView(section) ? role.accessTo(section) : AccessLevel.NONE,
                        section.isAvailable()))
                .toList();

        return new Me(
                user.id(),
                user.username(),
                user.displayName(),
                user.language(),
                user.active(),
                new Role(role.id(), role.name(), role.description(), role.fullAccess(),
                        role.systemRole()),
                sections,
                restrictedFieldsOf(user));
    }

    /**
     * The protected fields this user cannot see, across every section.
     *
     * <p>Read through {@link UserView#canSee} rather than off {@code RoleView.restrictedFields}
     * directly, so a field inside a section the user cannot view at all is reported as hidden —
     * which is what the permission model actually does, and the difference an inactive user or an
     * ungranted section makes.
     *
     * <p><strong>Empty for every role today.</strong> V26 removed the three seeded restrictions, so
     * this is currently always empty for a role that can see Products at all. The mechanism is
     * intact and the field is carried rather than omitted, because the day a restriction returns is
     * not the day a frontend should have to start handling a new key.
     */
    private static Set<ProtectedField> restrictedFieldsOf(UserView user) {
        return EnumSet.allOf(ProtectedField.class).stream()
                .filter(field -> !user.canSee(field))
                .collect(() -> EnumSet.noneOf(ProtectedField.class), Set::add, Set::addAll);
    }

    // -------------------------------------------------------------------------------------------

    /**
     * @param language the chosen BCP 47 tag, <strong>absent when none has been chosen</strong> — the
     *     API omits null fields, so a client reads "no key" as "this person has not chosen" and
     *     applies its own default
     */
    record Me(
            long id,
            String username,
            String displayName,
            String language,
            boolean active,
            Role role,
            List<SectionAccess> sections,
            Set<ProtectedField> restrictedFields) {
    }

    record Role(long id, String name, String description, boolean fullAccess, boolean systemRole) {
    }

    /**
     * @param level what this caller may do here; {@link AccessLevel#NONE} where they may do nothing
     * @param available whether anything is built behind this section yet. Distinguishes "you may not
     *     see this" from "this does not exist yet" — see the class javadoc.
     */
    record SectionAccess(Section section, AccessLevel level, boolean available) {
    }

    /** @param language a BCP 47 tag, or null/blank to clear the preference */
    record LanguageRequest(String language) {
    }
}
