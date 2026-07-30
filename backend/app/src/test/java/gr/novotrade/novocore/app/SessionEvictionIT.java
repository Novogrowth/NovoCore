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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

/**
 * Revoking access ends the session that holds it, immediately.
 *
 * <h2>What this is defending, and why a test was necessary rather than sufficient reasoning</h2>
 *
 * <p>Authentication is a session cookie and the authenticated principal is a snapshot of the user
 * taken at login. Before this, <strong>deactivating an account did not log it out</strong>: the
 * session kept working for up to its full lifetime, and the operator who deactivated it had no
 * indication of that. Same for moving somebody to a narrower role.
 *
 * <p>That is the wrong failure for the two situations the control exists for — cutting off a
 * departing employee, and containing a compromised account — and a short-lived cache would only
 * have shrunk the window rather than closing it.
 *
 * <p><strong>Every assertion here is made over real HTTP on a session that was really logged in</strong>,
 * because the whole defect was that the service layer and the session disagreed. A test that called
 * {@code deactivate} and then asked the service whether the user was active would have passed
 * against the broken version.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            "novocore.bootstrap.owner-username=" + SessionEvictionIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + SessionEvictionIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
@AutoConfigureTestRestTemplate
class SessionEvictionIT {

    static final String OWNER_USERNAME = "evict.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String PASSWORD = "victim-password-long-enough";

    @Autowired private TestRestTemplate rest;
    @Autowired private UserService users;
    @Autowired private RoleService roles;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
    }

    @Test
    @DisplayName("deactivating an account ends its session on the very next request")
    void deactivationEndsTheSession() {
        long userId = userWith("evict.deactivated", Section.CUSTOMERS, AccessLevel.VIEW);
        ApiClient.Session session = api.logIn("evict.deactivated", PASSWORD);

        assertThat(session.get("/api/customers").getStatusCode())
                .as("the session works before anything is revoked")
                .isEqualTo(HttpStatus.OK);

        users.deactivate(userId);

        assertThat(session.get("/api/customers").getStatusCode())
                .as("""
                        The point of deactivating an account is that it stops working. Before \
                        session eviction this answered 200 for up to the whole session lifetime, \
                        because the principal was a snapshot taken at login — which is exactly \
                        wrong for cutting off a departing employee.""")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The narrower half: a demotion takes effect at once, not at the next login.
     *
     * <p>Worth asserting separately from deactivation because it fails differently. A deactivated
     * user cannot log in again at all, so the window closes by itself eventually; a demoted one
     * carries the <em>old role's</em> permissions in their session and goes on using them, which no
     * subsequent event corrects.
     */
    @Test
    @DisplayName("moving a user to a narrower role ends their session, so the old grants go with it")
    void changingRoleEndsTheSession() {
        long userId = userWith("evict.demoted", Section.CUSTOMERS, AccessLevel.FULL);
        ApiClient.Session session = api.logIn("evict.demoted", PASSWORD);

        assertThat(session.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);

        RoleView narrower = roles.create(new NewRole(
                "EVICT_NARROWER", "Holds nothing at all — created by SessionEvictionIT"));
        users.changeRole(userId, narrower.id());

        assertThat(session.get("/api/customers").getStatusCode())
                .as("the old role's grants must not outlive the move to a new one")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // And logging in again gives the new role's access, which is none — so the eviction did not
        // merely inconvenience them, it applied the change.
        assertThat(api.logIn("evict.demoted", PASSWORD).get("/api/customers").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("every session of that user ends, not just one")
    void allSessionsEnd() {
        long userId = userWith("evict.multi", Section.CUSTOMERS, AccessLevel.VIEW);

        // Two independent logins, as the same person on a laptop and a phone. Ending one and
        // leaving the other is the failure worth ruling out: it looks like the control worked.
        ApiClient.Session first = api.logIn("evict.multi", PASSWORD);
        ApiClient.Session second = api.logIn("evict.multi", PASSWORD);

        assertThat(first.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);

        users.deactivate(userId);

        assertThat(first.get("/api/customers").getStatusCode())
                .as("the first session").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(second.get("/api/customers").getStatusCode())
                .as("the second session — a control that ends one device and not the other is worse "
                        + "than none, because it reads as success")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("one user's eviction leaves everybody else logged in")
    void othersAreUnaffected() {
        long victimId = userWith("evict.victim", Section.CUSTOMERS, AccessLevel.VIEW);
        userWith("evict.bystander", Section.CUSTOMERS, AccessLevel.VIEW);

        ApiClient.Session victim = api.logIn("evict.victim", PASSWORD);
        ApiClient.Session bystander = api.logIn("evict.bystander", PASSWORD);
        assertThat(victim.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bystander.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);

        users.deactivate(victimId);

        assertThat(victim.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bystander.get("/api/customers").getStatusCode())
                .as("eviction is per user; logging everyone out would be a denial of service "
                        + "dressed as a security control")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Deactivating a user with no session must not fail — it is the ordinary case.
     *
     * <p>Somebody who has never logged in, or whose session already timed out, is the majority of
     * deactivations. If eviction threw or reported a problem there, the operation that matters would
     * fail for a reason that is not a problem at all.
     */
    @Test
    @DisplayName("deactivating a user who is not logged in is uneventful")
    void deactivatingSomebodyWithNoSessionIsFine() {
        long userId = userWith("evict.neverloggedin", Section.CUSTOMERS, AccessLevel.VIEW);

        users.deactivate(userId);

        assertThat(users.require(userId).active()).isFalse();
        // And they cannot then log in, which is the deactivation working on its own terms.
        assertThat(users.authenticate("evict.neverloggedin", PASSWORD)).isEmpty();
    }

    // -------------------------------------------------------------------------------------------

    private long userWith(String username, Section section, AccessLevel level) {
        return users.findByUsername(username).map(existing -> existing.id()).orElseGet(() -> {
            RoleView role = roles.create(new NewRole(
                    "EVICT_" + username.toUpperCase(java.util.Locale.ROOT).replace('.', '_'),
                    "Created by SessionEvictionIT"));
            roles.grant(role.id(), section, level);
            return users.create(new NewUser(username, username, PASSWORD, role.id())).id();
        });
    }
}
