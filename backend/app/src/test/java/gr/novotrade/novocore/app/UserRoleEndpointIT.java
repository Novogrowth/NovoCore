package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The eighteen user and role administration routes, over real HTTP.
 *
 * <p>Until step 16b this was direct SQL: {@code USERS_AND_ROLES} was a section with no routes at
 * all. So this covers a surface nothing had ever exercised, and the excuses in
 * {@code TradingQuarterOverHttpIT} name it as the test that does.
 *
 * <h2>Three things beyond "the routes work"</h2>
 *
 * <ul>
 *   <li><strong>The refusals are the right refusals.</strong> A 404 for an id naming nothing, not a
 *       400 — the shape of step 15's defect 9, where the whole email slice answered {@code 400 "Bad
 *       request."} where the rest of the surface answered 404. A 400 <em>with a reason</em> for a
 *       missing body field, never a 500.
 *   <li><strong>The guards apply over HTTP, not merely in the service.</strong> Self-modification
 *       and privilege escalation are refused through the real filter chain with a real session,
 *       which is where they will actually be attempted. {@code PrivilegeEscalationIT} asserts the
 *       compound path at the service layer; this asserts it is not bypassable at the boundary.
 *   <li><strong>An unparseable enum in the path says which values exist.</strong> These were the
 *       first enum-typed path variables on the surface, and they found a defect that had been
 *       latent on every enum query parameter since step 16a — see {@code WebExceptionHandler}.
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + UserRoleEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + UserRoleEndpointIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class UserRoleEndpointIT {

    static final String OWNER_USERNAME = "admin.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String PASSWORD = "a-password-long-enough";

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
    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("the user list, filtered to the active ones, and one by id")
        void listAndReadUsers() {
            UserView dormant = givenUser("admin.dormant", roleWith("ADMIN_R_DORMANT"));
            users.deactivate(dormant.id());

            JsonNode all = Json.ok(owner.get("/api/users"), "GET /api/users");
            assertThat(usernames(all)).contains(OWNER_USERNAME, "admin.dormant");

            JsonNode active = Json.ok(owner.get("/api/users?active=true"), "GET /api/users?active");
            assertThat(usernames(active))
                    .contains(OWNER_USERNAME)
                    .doesNotContain("admin.dormant");

            JsonNode one = Json.ok(
                    owner.get("/api/users/" + dormant.id()), "GET /api/users/{id}");
            assertThat(Json.text(one, "username")).isEqualTo("admin.dormant");
            assertThat(one.get("active").asBoolean()).isFalse();
        }

        /**
         * A user response must not carry a password hash — asserted against the bytes.
         *
         * <p>{@code UserView} has no such field, so this cannot fail today. It is here because the
         * claim is about what crosses the wire, and a future convenience field added to a view is
         * exactly the change that would break it silently.
         */
        @Test
        @DisplayName("no user response contains anything password-shaped")
        void noPasswordEverCrossesTheWire() {
            String body = owner.get("/api/users").getBody();

            assertThat(body).doesNotContainIgnoringCase("password");
            assertThat(body).doesNotContain("$2a$", "{bcrypt}");
        }

        @Test
        @DisplayName("?search= matches a username, a display name and a role description")
        void searchMatchesAnywhere() {
            // Both directions of the step's worked example on this surface: a term that begins the
            // value, and one that sits in the middle of it. Under the previous behaviour these two
            // lists had no text filter at all.
            givenUser("search.aristotelis", roleWith("ADMIN_R_SEARCH"));

            assertThat(usernames(Json.ok(
                    owner.get("/api/users?search=search."), "GET /api/users?search")))
                    .contains("search.aristotelis");
            assertThat(usernames(Json.ok(
                    owner.get("/api/users?search=totel"), "GET /api/users?search mid-string")))
                    .as("mid-string is the whole point; an exact filter would find nothing")
                    .contains("search.aristotelis");

            // The display name, which is the other searched column and the one an administrator
            // looking for a person actually knows. Greek, accented, and searched unaccented in
            // lowercase — so this covers the normalisation over HTTP as well as the column.
            users.create(new NewUser("search.bydisplay", "Αριστοτέλης Παπαδόπουλος",
                    PASSWORD, roleWith("ADMIN_R_SEARCH").id()));
            assertThat(owner.get("/api/users?search=παπαδοπουλ").getBody())
                    .as("the display name is searched, and accents and case are folded")
                    .contains("search.bydisplay");

            assertThat(owner.get("/api/roles?search=ADMIN_R_SEARCH").getBody())
                    .contains("ADMIN_R_SEARCH");
            assertThat(owner.get("/api/roles?search=R_SEARC").getBody())
                    .contains("ADMIN_R_SEARCH");
        }

        @Test
        @DisplayName("search combines with active, and an absent term is no filter")
        void searchCombinesAndDegrades() {
            UserView dormant = givenUser("search.dormant", roleWith("ADMIN_R_SEARCH_OFF"));
            users.deactivate(dormant.id());

            assertThat(usernames(Json.ok(
                    owner.get("/api/users?search=search.dormant"), "GET /api/users?search")))
                    .contains("search.dormant");
            assertThat(usernames(Json.ok(
                    owner.get("/api/users?search=search.dormant&active=true"),
                    "GET /api/users?search&active")))
                    .doesNotContain("search.dormant");

            // A blank term is the unfiltered list, not an empty one — so a screen can send the box's
            // contents unconditionally without a special case for "the operator cleared it".
            assertThat(usernames(Json.ok(owner.get("/api/users?search="), "GET /api/users?search=")))
                    .contains(OWNER_USERNAME);
        }

        @Test
        @DisplayName("the role list, one role by id, and who holds it")
        void listAndReadRoles() {
            RoleView role = roleWith("ADMIN_R_HOLDERS");
            UserView holder = givenUser("admin.holder", role);

            JsonNode all = Json.ok(owner.get("/api/roles"), "GET /api/roles");
            assertThat(names(all)).contains("OWNER", "ADMIN_R_HOLDERS");

            JsonNode one = Json.ok(owner.get("/api/roles/" + role.id()), "GET /api/roles/{id}");
            assertThat(Json.text(one, "name")).isEqualTo("ADMIN_R_HOLDERS");

            JsonNode holders = Json.ok(
                    owner.get("/api/roles/" + role.id() + "/users"), "GET /api/roles/{id}/users");
            assertThat(usernames(holders)).containsExactly(holder.username());
        }

        @Test
        @DisplayName("the section catalogue lists every section, with what is built behind it")
        void sectionCatalogue() {
            JsonNode sections = Json.ok(owner.get("/api/sections"), "GET /api/sections");

            assertThat(sections.get("items")).hasSize(Section.values().length);

            // The distinction the route exists for: a reserved section is present and flagged,
            // rather than absent — so a role editor can grey it out instead of pretending it is
            // not part of the model.
            JsonNode reserved = itemNamed(sections, "section", Section.SALES_ORDER_FULFILLMENT.name());
            assertThat(reserved.get("available").asBoolean()).isFalse();

            JsonNode built = itemNamed(sections, "section", Section.SALES.name());
            assertThat(built.get("available").asBoolean()).isTrue();
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("changing")
    class Changing {

        @Test
        @DisplayName("create a user, rename them, move their role, deactivate and reactivate")
        void userLifecycle() {
            RoleView first = roleWith("ADMIN_R_FIRST");
            RoleView second = roleWith("ADMIN_R_SECOND");

            ResponseEntity<String> created = owner.post("/api/users",
                    new NewUser("admin.lifecycle", "Lifecycle", PASSWORD, first.id()));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            long id = Json.read(created).get("id").asLong();

            JsonNode renamed = Json.ok(
                    owner.patchBody("/api/users/" + id + "/display-name",
                            Map.of("displayName", "Renamed Person")),
                    "PATCH display-name");
            assertThat(Json.text(renamed, "displayName")).isEqualTo("Renamed Person");

            JsonNode moved = Json.ok(
                    owner.patchBody("/api/users/" + id + "/role", Map.of("roleId", second.id())),
                    "PATCH role");
            assertThat(Json.text(moved.get("role"), "name")).isEqualTo("ADMIN_R_SECOND");

            assertThat(owner.post("/api/users/" + id + "/deactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(users.require(id).active()).isFalse();

            assertThat(owner.post("/api/users/" + id + "/reactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(users.require(id).active()).isTrue();
        }

        @Test
        @DisplayName("a password reset answers 204 and the new password is the one that works")
        void passwordReset() {
            UserView user = givenUser("admin.resettable", roleWith("ADMIN_R_RESET"));

            assertThat(owner.patchBody("/api/users/" + user.id() + "/password",
                            Map.of("password", "the-replacement-password"))
                    .getStatusCode())
                    .as("204 and no body — the one thing a caller might read from a password "
                            + "response is the one thing that must not be in it")
                    .isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(users.authenticate("admin.resettable", PASSWORD)).isEmpty();
            assertThat(users.authenticate("admin.resettable", "the-replacement-password"))
                    .isPresent();
        }

        @Test
        @DisplayName("create a role, rename it, grant a section, restrict a field, deactivate it")
        void roleLifecycle() {
            ResponseEntity<String> created = owner.post("/api/roles",
                    new NewRole("ADMIN_R_LIFECYCLE", "Created over HTTP"));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            long id = Json.read(created).get("id").asLong();

            // Born with nothing — default-deny, asserted rather than assumed.
            assertThat(Json.read(created).get("sectionGrants")).isEmpty();

            JsonNode renamed = Json.ok(
                    owner.patchBody("/api/roles/" + id + "/name", Map.of("name", "ADMIN_R_RENAMED")),
                    "PATCH role name");
            assertThat(Json.text(renamed, "name")).isEqualTo("ADMIN_R_RENAMED");

            JsonNode granted = Json.ok(
                    owner.putBody("/api/roles/" + id + "/grants/" + Section.CUSTOMERS,
                            Map.of("accessLevel", AccessLevel.VIEW)),
                    "PUT grant");
            assertThat(Json.text(granted.get("sectionGrants"), Section.CUSTOMERS.name()))
                    .isEqualTo(AccessLevel.VIEW.name());

            JsonNode restricted = Json.ok(
                    owner.putBody("/api/roles/" + id + "/field-restrictions/"
                                    + ProtectedField.PRODUCT_SUPPLIER,
                            Map.of("restricted", true)),
                    "PUT field restriction");
            assertThat(restricted.get("restrictedFields"))
                    .anyMatch(node -> ProtectedField.PRODUCT_SUPPLIER.name().equals(node.asString()));

            // Nobody holds it, so it can be retired.
            assertThat(owner.post("/api/roles/" + id + "/deactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(roles.require(id).active()).isFalse();

            assertThat(owner.post("/api/roles/" + id + "/reactivate", "").getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(roles.require(id).active()).isTrue();
        }

        @Test
        @DisplayName("revoking a grant is NONE, which removes it rather than storing a grant of nothing")
        void revokingRemovesTheGrant() {
            RoleView role = roleWith("ADMIN_R_REVOKE");
            roles.grant(role.id(), Section.CUSTOMERS, AccessLevel.FULL);

            JsonNode after = Json.ok(
                    owner.putBody("/api/roles/" + role.id() + "/grants/" + Section.CUSTOMERS,
                            Map.of("accessLevel", AccessLevel.NONE)),
                    "PUT grant NONE");

            assertThat(after.get("sectionGrants").has(Section.CUSTOMERS.name()))
                    .as("NONE removes the row; a stored grant that grants nothing is a second way "
                            + "to say the same thing and a second thing to get wrong")
                    .isFalse();
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("refusing")
    class Refusing {

        @Test
        @DisplayName("an id naming nothing is 404, not 400 — the shape of step 15's defect 9")
        void unknownIdsAreNotFound() {
            assertThat(owner.get("/api/users/999999999").getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(owner.get("/api/roles/999999999").getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(owner.get("/api/roles/999999999/users").getStatusCode())
                    .as("""
                            The holders route reads a list, so the tempting implementation returns \
                            an empty one — which reads as "a role nobody holds" rather than "no \
                            such role". It requires the role first for exactly that reason.""")
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("a missing body field is 400 naming the field, never a 500")
        void missingFieldsAreNamed() {
            RoleView role = roleWith("ADMIN_R_EMPTYBODY");

            ResponseEntity<String> noLevel = owner.put(
                    "/api/roles/" + role.id() + "/grants/" + Section.CUSTOMERS, "{}");
            assertThat(noLevel.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Json.read(noLevel).get("detail").asString()).contains("accessLevel");

            // The boxed-Boolean case. On a primitive this would arrive as false and silently REMOVE
            // a restriction the caller never mentioned — a wrong answer that looks like success.
            ResponseEntity<String> noFlag = owner.put(
                    "/api/roles/" + role.id() + "/field-restrictions/"
                            + ProtectedField.PRODUCT_SUPPLIER, "{}");
            assertThat(noFlag.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(Json.read(noFlag).get("detail").asString()).contains("restricted");
        }

        /**
         * The defect these routes found, asserted where it was found.
         *
         * <p>These were the first enum-typed <em>path variables</em> on the surface. Spring cannot
         * convert the value, raises {@code MethodArgumentTypeMismatchException} — which is an
         * {@code IllegalArgumentException} — and it fell through to the generic handler as a bare
         * {@code "Bad request."}. That is {@code CLAUDE.md}'s "wrong but non-empty value", the case
         * the three guards structurally cannot see, and it had been latent on every enum query
         * parameter since step 16a.
         */
        @Test
        @DisplayName("an unparseable enum in the path says which values are accepted")
        void unknownEnumValuesListTheAcceptedOnes() {
            RoleView role = roleWith("ADMIN_R_BADENUM");

            ResponseEntity<String> response = owner.putBody(
                    "/api/roles/" + role.id() + "/grants/JORUNAL",
                    Map.of("accessLevel", AccessLevel.VIEW));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            String detail = Json.read(response).get("detail").asString();
            assertThat(detail)
                    .as("a bare \"Bad request.\" leaves a frontend to guess the spelling of a "
                            + "section, which is how a route gets written against by trial and error")
                    .isNotEqualTo("Bad request.")
                    .contains("JORUNAL")
                    .contains(Section.JOURNAL.name());
        }

        @Test
        @DisplayName("a role with holders cannot be deactivated, and the message says to move them")
        void roleWithHoldersIsRefused() {
            RoleView role = roleWith("ADMIN_R_OCCUPIED");
            givenUser("admin.occupant", role);

            ResponseEntity<String> response = owner.post("/api/roles/" + role.id() + "/deactivate", "");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(Json.read(response).get("detail").asString())
                    .contains("Move them to another role first");
        }

        @Test
        @DisplayName("a system role cannot be edited through any of these routes")
        void systemRolesAreLocked() {
            RoleView ownerRole = roles.requireByName("OWNER");

            assertThat(owner.putBody("/api/roles/" + ownerRole.id() + "/grants/" + Section.SALES,
                            Map.of("accessLevel", AccessLevel.NONE))
                    .getStatusCode())
                    .as("""
                            This is what stops USERS_AND_ROLES being removed from the last role \
                            that has it, which would lock everyone out of user administration with \
                            no way back in through the application.""")
                    .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

            assertThat(owner.patchBody("/api/roles/" + ownerRole.id() + "/name",
                            Map.of("name", "RENAMED_OWNER"))
                    .getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        }

        @Test
        @DisplayName("a role without the section is refused, and told neither the section nor the level")
        void withoutTheSectionEverythingIsForbidden() {
            RoleView outsider = roleWith("ADMIN_R_OUTSIDER");
            roles.grant(outsider.id(), Section.CUSTOMERS, AccessLevel.FULL);
            givenUser("admin.outsider", outsider);
            ApiClient.Session session = api.logIn("admin.outsider", PASSWORD);

            assertThat(session.get("/api/users").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(session.get("/api/roles").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(session.get("/api/sections").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            ResponseEntity<String> refused = session.post("/api/roles",
                    new NewRole("ADMIN_R_SNEAKY", "Should never exist"));
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(Json.read(refused).get("detail").asString())
                    .as("a permission refusal stays generic on purpose — naming the section would "
                            + "map the application for somebody who cannot see it")
                    .doesNotContain("USERS_AND_ROLES")
                    .doesNotContain("FULL");

            assertThat(roles.findByName("ADMIN_R_SNEAKY")).isEmpty();
        }

        @Test
        @DisplayName("VIEW on the section reads but does not write")
        void viewCannotWrite() {
            RoleView auditor = roleWith("ADMIN_R_AUDITOR");
            roles.grant(auditor.id(), Section.USERS_AND_ROLES, AccessLevel.VIEW);
            givenUser("admin.auditor", auditor);
            ApiClient.Session session = api.logIn("admin.auditor", PASSWORD);

            assertThat(session.get("/api/users").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(session.post("/api/roles", new NewRole("ADMIN_R_NOPE", "no"))
                    .getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // -------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("the guards, through the real filter chain")
    class Guards {

        /**
         * The service-layer guards are asserted by {@code PrivilegeEscalationIT}. These assert they
         * are not bypassable at the boundary — which is where they will actually be attempted, and
         * the distinction step 15 established: nine defects, none of them reachable from the
         * service layer.
         */
        @Test
        @DisplayName("an administrator cannot edit the permissions of their own role")
        void cannotEditYourOwnRole() {
            RoleView administrators = roleWith("ADMIN_R_SELFEDIT");
            roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
            givenUser("admin.selfedit", administrators);
            ApiClient.Session session = api.logIn("admin.selfedit", PASSWORD);

            ResponseEntity<String> response = session.putBody(
                    "/api/roles/" + administrators.id() + "/grants/" + Section.JOURNAL,
                    Map.of("accessLevel", AccessLevel.FULL));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(Json.read(response).get("detail").asString())
                    .contains("your own role");
            assertThat(roles.require(administrators.id()).canView(Section.JOURNAL)).isFalse();
        }

        @Test
        @DisplayName("nor confer, on another role, a level they do not hold themselves")
        void cannotConferWhatYouDoNotHold() {
            RoleView administrators = roleWith("ADMIN_R_CONFER");
            roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
            givenUser("admin.confer", administrators);
            ApiClient.Session session = api.logIn("admin.confer", PASSWORD);

            RoleView target = roleWith("ADMIN_R_CONFER_TARGET");

            ResponseEntity<String> response = session.putBody(
                    "/api/roles/" + target.id() + "/grants/" + Section.JOURNAL,
                    Map.of("accessLevel", AccessLevel.FULL));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(Json.read(response).get("detail").asString())
                    .contains("your own role has NONE there");
            assertThat(roles.require(target.id()).canView(Section.JOURNAL)).isFalse();
        }

        @Test
        @DisplayName("nor create an account in a full-access role without holding one")
        void cannotPlantAnOwner() {
            RoleView administrators = roleWith("ADMIN_R_PLANT");
            roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
            givenUser("admin.plant", administrators);
            ApiClient.Session session = api.logIn("admin.plant", PASSWORD);

            RoleView ownerRole = roles.requireByName("OWNER");

            ResponseEntity<String> response = session.post("/api/users",
                    new NewUser("admin.plantedowner", "Planted", PASSWORD, ownerRole.id()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(Json.read(response).get("detail").asString())
                    .contains("full access to everything");
            assertThat(users.findByUsername("admin.plantedowner")).isEmpty();
        }

        @Test
        @DisplayName("nor change their own role")
        void cannotChangeYourOwnRole() {
            RoleView administrators = roleWith("ADMIN_R_SELFROLE");
            roles.grant(administrators.id(), Section.USERS_AND_ROLES, AccessLevel.FULL);
            UserView actor = givenUser("admin.selfrole", administrators);
            ApiClient.Session session = api.logIn("admin.selfrole", PASSWORD);

            RoleView other = roleWith("ADMIN_R_SELFROLE_OTHER");

            ResponseEntity<String> response = session.patchBody(
                    "/api/users/" + actor.id() + "/role", Map.of("roleId", other.id()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(Json.read(response).get("detail").asString())
                    .contains("your own role");
        }

        @Test
        @DisplayName("the owner is unaffected by all four — a guard that stops real work is worse")
        void theOwnerCanStillAdminister() {
            RoleView target = roleWith("ADMIN_R_OWNER_CAN");

            assertThat(owner.putBody("/api/roles/" + target.id() + "/grants/" + Section.JOURNAL,
                            Map.of("accessLevel", AccessLevel.FULL))
                    .getStatusCode())
                    .as("full access means FULL everywhere, including sections with no grant rows "
                            + "and sections added in future")
                    .isEqualTo(HttpStatus.OK);

            ResponseEntity<String> created = owner.post("/api/users",
                    new NewUser("admin.byowner", "By Owner", PASSWORD,
                            roles.requireByName("OWNER").id()));
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // -------------------------------------------------------------------------------------------

    private RoleView roleWith(String name) {
        return roles.findByName(name)
                .orElseGet(() -> roles.create(new NewRole(name, "Created by UserRoleEndpointIT")));
    }

    private UserView givenUser(String username, RoleView role) {
        return users.findByUsername(username)
                .orElseGet(() -> users.create(
                        new NewUser(username, username, PASSWORD, role.id())));
    }

    private static java.util.List<String> usernames(JsonNode listResponse) {
        return fieldValues(listResponse, "username");
    }

    private static java.util.List<String> names(JsonNode listResponse) {
        return fieldValues(listResponse, "name");
    }

    private static java.util.List<String> fieldValues(JsonNode listResponse, String field) {
        java.util.List<String> values = new java.util.ArrayList<>();
        listResponse.get("items").forEach(item -> values.add(item.get(field).asString()));
        return values;
    }

    private static JsonNode itemNamed(JsonNode listResponse, String field, String value) {
        for (JsonNode item : listResponse.get("items")) {
            if (value.equals(item.get(field).asString())) {
                return item;
            }
        }
        throw new AssertionError("No item with " + field + "=" + value + " in " + listResponse);
    }
}
