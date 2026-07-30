package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@code GET /api/me} and {@code PATCH /api/me/language} — the identity route the frontend starts
 * from.
 *
 * <p><strong>What this exists to prove, beyond "it returns 200".</strong> Three things, and the
 * first is the reason the route is declared {@code @AuthenticatedOnly} at all:
 *
 * <ul>
 *   <li><strong>A role granted nothing still reaches it.</strong> If it needed a section, a user
 *       with no grants would authenticate successfully and then be unable to discover anything —
 *       including that they have no access. The frontend would have a session and no way to render
 *       a result.
 *   <li><strong>It reports every section, not only the visible ones</strong>, with
 *       {@code available} alongside the level, so a UI can tell "you may not see this" from "this
 *       is not built yet". Two states that look identical to a user and have different fixes.
 *   <li><strong>It says nothing about anyone else.</strong> That is the whole justification for it
 *       having no section, so it is asserted rather than assumed.
 * </ul>
 *
 * <p>{@code PermissionSweepIT.sectionlessRoutesAreReachableByEveryone} asserts the first point
 * again from the other direction — over the live route table rather than a hand-written path — so a
 * future {@code /api/me/...} route cannot appear outside the section model without one of the two
 * noticing.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + MeIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + MeIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class MeIT {

    static final String OWNER_USERNAME = "me.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String DESTITUTE_USERNAME = "me.nogrants";
    private static final String DESTITUTE_PASSWORD = "nogrants-password-long-enough";

    private static final String CLERK_USERNAME = "me.clerk";
    private static final String CLERK_PASSWORD = "clerk-password-long-enough";

    @Autowired private TestRestTemplate rest;
    @Autowired private UserService users;
    @Autowired private RoleService roles;

    private ApiClient api;
    private ApiClient.Session owner;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
        owner = api.logIn(OWNER_USERNAME, OWNER_PASSWORD);
    }

    // -------------------------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the owner sees their identity, their role, and every section at FULL")
    void theOwnerSeesEverything() {
        JsonNode me = Json.ok(owner.get("/api/me"), "GET /api/me");

        assertThat(Json.text(me, "username")).isEqualTo(OWNER_USERNAME);
        assertThat(me.get("active").asBoolean()).isTrue();
        assertThat(Json.text(me.get("role"), "name")).isEqualTo("OWNER");
        assertThat(me.get("role").get("fullAccess").asBoolean()).isTrue();
        assertThat(me.get("role").get("systemRole").asBoolean()).isTrue();

        // Every section, including the reserved ones — the list is the enum, not the grants.
        assertThat(sectionNames(me))
                .containsExactlyInAnyOrderElementsOf(
                        java.util.Arrays.stream(Section.values()).map(Enum::name).toList());

        // full_access is a flag rather than stored grants precisely so a section added later is
        // FULL for the owner without anybody inserting a row. This is that promise, over HTTP.
        for (JsonNode section : me.get("sections")) {
            assertThat(Json.text(section, "level"))
                    .as("the owner's level on %s", Json.text(section, "section"))
                    .isEqualTo(AccessLevel.FULL.name());
        }
    }

    @Test
    @DisplayName("a reserved section is reported as unavailable rather than hidden")
    void reservedSectionsAreDistinguishable() {
        JsonNode me = Json.ok(owner.get("/api/me"), "GET /api/me");

        // SALES_ORDER_FULFILLMENT is granted (the owner has everything) and has nothing built
        // behind it. Without `available` those two facts are indistinguishable from a section that
        // simply is not there, and a navigation menu would offer a link to nothing.
        JsonNode reserved = sectionNamed(me, Section.SALES_ORDER_FULFILLMENT);
        assertThat(reserved.get("available").asBoolean())
                .as("a reserved section must say it is not built yet")
                .isFalse();
        assertThat(Json.text(reserved, "level"))
                .as("...while still reporting the level, which is a separate question")
                .isEqualTo(AccessLevel.FULL.name());

        assertThat(sectionNamed(me, Section.SALES).get("available").asBoolean())
                .as("a built section must not be flagged unavailable")
                .isTrue();
    }

    /**
     * The assertion the whole {@code @AuthenticatedOnly} design exists for.
     *
     * <p>A user whose role holds nothing at all must still be able to ask who they are. If this
     * answered 403, the only way a frontend could distinguish "logged in with no access" from "not
     * logged in" would be to guess.
     */
    @Test
    @DisplayName("a role granted nothing reaches /api/me and is told, accurately, that it has nothing")
    void aRoleWithNoGrantsStillLearnsWhoItIs() {
        ApiClient.Session destitute = destituteSession();

        ResponseEntity<String> response = destitute.get("/api/me");
        assertThat(response.getStatusCode())
                .as("the route that reports your permissions cannot itself require one")
                .isEqualTo(HttpStatus.OK);

        JsonNode me = Json.read(response);
        assertThat(Json.text(me, "username")).isEqualTo(DESTITUTE_USERNAME);
        assertThat(me.get("role").get("fullAccess").asBoolean()).isFalse();

        for (JsonNode section : me.get("sections")) {
            assertThat(Json.text(section, "level"))
                    .as("a role with no grants must hold NONE everywhere, not a default")
                    .isEqualTo(AccessLevel.NONE.name());
        }

        // And the refusal it does get elsewhere is real, so this is not a session that happens to
        // be privileged.
        assertThat(destitute.get("/api/customers").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a partially granted role gets its real levels, not a summary")
    void aPartialRoleSeesItsActualGrants() {
        ApiClient.Session clerk = clerkSession();
        JsonNode me = Json.ok(clerk.get("/api/me"), "GET /api/me");

        assertThat(Json.text(sectionNamed(me, Section.CUSTOMERS), "level"))
                .isEqualTo(AccessLevel.FULL.name());
        assertThat(Json.text(sectionNamed(me, Section.PRODUCTS), "level"))
                .isEqualTo(AccessLevel.VIEW.name());
        assertThat(Json.text(sectionNamed(me, Section.JOURNAL), "level"))
                .as("an ungranted section is NONE, not absent — reported even though JOURNAL has no "
                        + "routes yet, because /api/me describes the permission model rather than "
                        + "the URL space")
                .isEqualTo(AccessLevel.NONE.name());

        // The levels are not decorative. SUPPLIERS rather than JOURNAL or USERS_AND_ROLES: those
        // two sections have no HTTP surface at all as of step 14, so a request to them would 404
        // and prove nothing about permissions.
        assertThat(clerk.get("/api/products").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clerk.get("/api/suppliers").getStatusCode())
                .as("SUPPLIERS is reported NONE and really is refused")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(Json.text(sectionNamed(me, Section.SUPPLIERS), "level"))
                .isEqualTo(AccessLevel.NONE.name());
    }

    @Test
    @DisplayName("/api/me says nothing about anybody else — the reason it needs no section")
    void itDisclosesNothingAboutOtherPeople() {
        // Two other accounts exist by now.
        clerkSession();
        destituteSession();

        String body = owner.get("/api/me").getBody();

        assertThat(body)
                .as("the identity route must describe the caller and no one else, or its lack of a "
                        + "section would be a way around the USERS_AND_ROLES grant")
                .doesNotContain(CLERK_USERNAME)
                .doesNotContain(DESTITUTE_USERNAME);
        // Whatever else changes about this response, a password hash must never be in it. The core
        // has no accessor that could produce one; this is the belt to that braces.
        assertThat(body).doesNotContain("{bcrypt}").doesNotContain("passwordHash");
    }

    /**
     * {@code /api/me} reads the user record, not the session's login-time snapshot.
     *
     * <p>{@code CurrentUser} returns the {@code UserView} captured at login and held in the security
     * context. A grant added mid-session is therefore invisible to it, and this route reporting a
     * stale permission set would tell a frontend to render a menu the operator no longer has — or to
     * omit one they were just given, with nothing to indicate either.
     *
     * <p><strong>Proven by making the change behind the session's back</strong> and asking again on
     * the same session. Against a snapshot-reading implementation this fails, which is exactly how
     * it was found.
     */
    @Test
    @DisplayName("a grant added mid-session is visible without logging in again")
    void grantsAreReadFreshRatherThanFromTheSession() {
        ApiClient.Session clerk = clerkSession();

        assertThat(Json.text(
                sectionNamed(Json.ok(clerk.get("/api/me"), "GET /api/me"), Section.FIXED_ASSETS),
                "level"))
                .isEqualTo(AccessLevel.NONE.name());

        // Granted through the service, so this session's stored principal knows nothing about it.
        roles.grant(users.findByUsername(CLERK_USERNAME).orElseThrow().role().id(),
                Section.FIXED_ASSETS, AccessLevel.VIEW);

        assertThat(Json.text(
                sectionNamed(Json.ok(clerk.get("/api/me"), "GET /api/me"), Section.FIXED_ASSETS),
                "level"))
                .as("/api/me must report what the user record says now, not what it said at login")
                .isEqualTo(AccessLevel.VIEW.name());
    }

    @Test
    @DisplayName("unauthenticated, /api/me is 401 — not an empty identity")
    void unauthenticatedIsRefused() {
        assertThat(rest.getForEntity("/api/me", String.class).getStatusCode())
                .as("no section is required; authentication still is")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------------------------------------------------
    // Language
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("language is absent until chosen, then present, then absent again when cleared")
    void languageRoundTrips() {
        // Its own account, because the assertion below is about the state a *new* user starts in
        // and every other test in this class shares its users across the whole run. Reusing one
        // would make this pass or fail on execution order, which is not a property of the API.
        ApiClient.Session fresh = freshSession("roundtrip");

        // Absent, not null and not "": Jackson omits nulls across this whole API, and the
        // convention a client codes against is that a missing key means "not set".
        assertThat(Json.ok(fresh.get("/api/me"), "GET /api/me").has("language"))
                .as("a language nobody has chosen is omitted, never sent as \"\" or a guessed default")
                .isFalse();

        JsonNode afterSet = Json.ok(
                fresh.patch("/api/me/language", "{\"language\":\"el-GR\"}"), "PATCH language");
        assertThat(Json.text(afterSet, "language")).isEqualTo("el-GR");

        // It survives a new request rather than only being echoed back.
        assertThat(Json.text(Json.ok(fresh.get("/api/me"), "GET /api/me"), "language"))
                .isEqualTo("el-GR");

        // Clearing is allowed: "has not chosen" is a state somebody may return to.
        JsonNode afterClear = Json.ok(
                fresh.patch("/api/me/language", "{\"language\":null}"), "PATCH language");
        assertThat(afterClear.has("language"))
                .as("cleared means absent again, matching the state every account starts in")
                .isFalse();
    }

    @Test
    @DisplayName("a language tag is normalised, so one preference has one spelling")
    void languageIsNormalised() {
        ApiClient.Session fresh = freshSession("normalise");

        assertThat(Json.text(
                Json.ok(fresh.patch("/api/me/language", "{\"language\":\"EL-gr\"}"), "PATCH"),
                "language"))
                .as("casing is not a second preference")
                .isEqualTo("el-GR");

        assertThat(Json.text(
                Json.ok(fresh.patch("/api/me/language", "{\"language\":\"  en  \"}"), "PATCH"),
                "language"))
                .isEqualTo("en");
    }

    @Test
    @DisplayName("a value that is not a language tag is refused, and the message says what one is")
    void aBadLanguageTagIsRefusedWithAnExplanation() {
        ApiClient.Session fresh = freshSession("badtag");

        ResponseEntity<String> response = fresh.patch("/api/me/language", "{\"language\":\"Greek\"}");

        // 422, carrying its message: the request parsed fine and a rule refused it, and an operator
        // who cannot see why cannot fix it. This is the anti-pattern CLAUDE.md names — a refusal
        // that answers a bare "Bad request." is a refusal nobody can act on.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody())
                .as("the refusal must say what a language tag looks like")
                .contains("Greek")
                .contains("el-GR");

        // The refusal changed nothing.
        assertThat(Json.ok(fresh.get("/api/me"), "GET /api/me").has("language")).isFalse();
    }

    @Test
    @DisplayName("a user with no grants at all can still set their own language")
    void changingYourOwnLanguageNeedsNoAdministrativeGrant() {
        ApiClient.Session destitute = destituteSession();

        assertThat(destitute.patch("/api/me/language", "{\"language\":\"en\"}").getStatusCode())
                .as("gating a personal display preference behind user administration would mean "
                        + "nobody could set their own language without being able to edit everyone")
                .isEqualTo(HttpStatus.OK);

        // The session really is powerless, so the 200 above is a property of the route and not of
        // this caller. USERS_AND_ROLES would be the pointed comparison, but it has no routes as of
        // step 14 — a request to it would 404 and say nothing about permissions.
        assertThat(destitute.get("/api/customers").getStatusCode())
                .as("...and this session is genuinely granted nothing")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -------------------------------------------------------------------------------------------

    private static List<String> sectionNames(JsonNode me) {
        List<String> names = new ArrayList<>();
        me.get("sections").forEach(section -> names.add(Json.text(section, "section")));
        return names;
    }

    private static JsonNode sectionNamed(JsonNode me, Section wanted) {
        for (JsonNode section : me.get("sections")) {
            if (wanted.name().equals(Json.text(section, "section"))) {
                return section;
            }
        }
        throw new AssertionError("/api/me did not report " + wanted + " at all. Every section is "
                + "reported, including ones the caller cannot see — see MeController.");
    }

    /**
     * An account nothing else in this class touches, for assertions about a user's starting state.
     *
     * <p>These tests share a database and a set of users across the whole class, so a test that
     * asserts "no language has been chosen" would otherwise pass or fail on execution order — a
     * property of the run, not of the API.
     */
    private ApiClient.Session freshSession(String suffix) {
        return sessionFor("me.fresh." + suffix, "fresh-password-long-enough",
                "ME_IT_FRESH_" + suffix.toUpperCase(java.util.Locale.ROOT), role -> role);
    }

    /** A role holding nothing at all. Created with no grants, and deliberately given none. */
    private ApiClient.Session destituteSession() {
        return sessionFor(DESTITUTE_USERNAME, DESTITUTE_PASSWORD, "ME_IT_NO_GRANTS", role -> role);
    }

    /** Customers in full, Products view-only — enough to tell real levels from a summary. */
    private ApiClient.Session clerkSession() {
        return sessionFor(CLERK_USERNAME, CLERK_PASSWORD, "ME_IT_CLERK", role -> {
            RoleView granted = roles.grant(role.id(), Section.CUSTOMERS, AccessLevel.FULL);
            return roles.grant(granted.id(), Section.PRODUCTS, AccessLevel.VIEW);
        });
    }

    private ApiClient.Session sessionFor(
            String username, String password, String roleName,
            java.util.function.UnaryOperator<RoleView> grants) {

        if (users.findByUsername(username).isEmpty()) {
            RoleView role = grants.apply(roles.create(
                    new NewRole(roleName, roleName + " — created by MeIT")));
            users.create(new NewUser(username, username, password, role.id()));
        }
        return api.logIn(username, password);
    }
}
