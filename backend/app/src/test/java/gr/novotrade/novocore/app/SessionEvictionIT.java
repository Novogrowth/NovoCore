package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.InvalidRoleException;
import gr.novotrade.novocore.core.api.security.NewRole;
import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.ProtectedField;
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
    // Step 16b: narrowing a ROLE, rather than moving a user between roles.
    //
    // Until this step none of the four tests below could pass, and none of them existed. Eviction
    // was wired into UserService.deactivate and UserService.changeRole only; RoleServiceImpl had no
    // UserSessions dependency at all. That was latent and harmless while role editing was direct
    // SQL — an UPDATE to role_section_grant never went through Java — and stops being harmless the
    // moment PUT /api/roles/{id}/grants/{section} exists.
    //
    // The asymmetry is the substance: taking access away has to take effect now, and giving it must
    // not cost a re-login. Both halves are asserted, because a control that evicts on every change
    // would pass a test that only checks the revoking half while making the system unusable.
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("revoking a section from a role ends the sessions of everyone holding it")
    void revokingAGrantEndsHoldersSessions() {
        long userId = userWith("evict.revoked", Section.CUSTOMERS, AccessLevel.VIEW);
        long roleId = users.require(userId).role().id();
        ApiClient.Session session = api.logIn("evict.revoked", PASSWORD);

        assertThat(session.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);

        roles.grant(roleId, Section.CUSTOMERS, AccessLevel.NONE);

        assertThat(session.get("/api/customers").getStatusCode())
                .as("""
                        Revoking a section is the operation an administrator reaches for to cut \
                        somebody off. Before step 16b it ended no session at all, so the person \
                        kept the access for up to a full session lifetime with nothing to say so.""")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("downgrading FULL to VIEW ends the session too — a partial revocation is a revocation")
    void downgradingAGrantEndsHoldersSessions() {
        long userId = userWith("evict.downgraded", Section.CUSTOMERS, AccessLevel.FULL);
        long roleId = users.require(userId).role().id();
        ApiClient.Session session = api.logIn("evict.downgraded", PASSWORD);

        assertThat(session.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);

        roles.grant(roleId, Section.CUSTOMERS, AccessLevel.VIEW);

        assertThat(session.get("/api/customers").getStatusCode())
                .as("FULL to VIEW takes away the ability to change customers. Testing only the "
                        + "revoke-to-NONE case would leave this passing against a check written as "
                        + "`newLevel == NONE` rather than `newLevel.isNarrowerThan(previous)`.")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("restricting a field ends the session; unrestricting it does not")
    void restrictingAFieldEndsTheSessionAndUnrestrictingDoesNot() {
        long userId = userWith("evict.restricted", Section.PRODUCTS, AccessLevel.VIEW);
        long roleId = users.require(userId).role().id();
        ApiClient.Session session = api.logIn("evict.restricted", PASSWORD);

        assertThat(session.get("/api/products").getStatusCode()).isEqualTo(HttpStatus.OK);

        roles.restrictField(roleId, ProtectedField.PRODUCT_SUPPLIER, true);

        assertThat(session.get("/api/products").getStatusCode())
                .as("a field restriction narrows what the session may see, and the session carries "
                        + "the role snapshot that decides it")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // The other direction, on a fresh session: removing a restriction gives more, so it must
        // not cost a re-login.
        ApiClient.Session renewed = api.logIn("evict.restricted", PASSWORD);
        assertThat(renewed.get("/api/products").getStatusCode()).isEqualTo(HttpStatus.OK);

        roles.restrictField(roleId, ProtectedField.PRODUCT_SUPPLIER, false);

        assertThat(renewed.get("/api/products").getStatusCode())
                .as("unrestricting widens; evicting here would punish somebody for being given more")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("re-restricting an already-restricted field evicts nobody — nothing was taken away")
    void aNoOpRestrictionEvictsNobody() {
        long userId = userWith("evict.norestrictchange", Section.PRODUCTS, AccessLevel.VIEW);
        long roleId = users.require(userId).role().id();
        roles.restrictField(roleId, ProtectedField.PRODUCT_SUPPLIER, true);

        ApiClient.Session session = api.logIn("evict.norestrictchange", PASSWORD);
        assertThat(session.get("/api/products").getStatusCode()).isEqualTo(HttpStatus.OK);

        roles.restrictField(roleId, ProtectedField.PRODUCT_SUPPLIER, true);

        assertThat(session.get("/api/products").getStatusCode())
                .as("""
                        The case that would bite in practice: an administrator re-saving a role \
                        editor form without changing anything, logging out everybody holding that \
                        role for no reason.""")
                .isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------------------------------------
    // The other half of the asymmetry: changes that must NOT end a session.
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("widening a grant leaves the session alone")
    void wideningAGrantKeepsTheSession() {
        long userId = userWith("evict.widened", Section.CUSTOMERS, AccessLevel.VIEW);
        long roleId = users.require(userId).role().id();
        ApiClient.Session session = api.logIn("evict.widened", PASSWORD);

        assertThat(session.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);

        roles.grant(roleId, Section.CUSTOMERS, AccessLevel.FULL);

        assertThat(session.get("/api/customers").getStatusCode())
                .as("being given more access must not cost a re-login")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a no-op grant evicts nobody")
    void aNoOpGrantEvictsNobody() {
        long userId = userWith("evict.regranted", Section.CUSTOMERS, AccessLevel.VIEW);
        long roleId = users.require(userId).role().id();
        ApiClient.Session session = api.logIn("evict.regranted", PASSWORD);

        roles.grant(roleId, Section.CUSTOMERS, AccessLevel.VIEW);

        assertThat(session.get("/api/customers").getStatusCode())
                .as("granting what was already granted changes nothing and must evict nobody — the "
                        + "reason AccessLevel.isNarrowerThan is false for equal levels")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("renaming a user and changing their language leave the session alone")
    void renameAndLanguageKeepTheSession() {
        long userId = userWith("evict.renamed", Section.CUSTOMERS, AccessLevel.VIEW);
        ApiClient.Session session = api.logIn("evict.renamed", PASSWORD);

        assertThat(session.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);

        users.rename(userId, "A New Display Name");
        assertThat(session.get("/api/customers").getStatusCode())
                .as("a rename takes nothing away").isEqualTo(HttpStatus.OK);

        users.changeLanguage(userId, "el-GR");
        assertThat(session.get("/api/customers").getStatusCode())
                .as("a display preference the backend never even reads (Q47b)")
                .isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------------------------------------------------
    // Two things that hold the reasoning in place rather than testing new behaviour.
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a password reset ends the sessions the old password was using")
    void changingAPasswordEndsTheSession() {
        long userId = userWith("evict.reset", Section.CUSTOMERS, AccessLevel.VIEW);
        ApiClient.Session session = api.logIn("evict.reset", PASSWORD);

        assertThat(session.get("/api/customers").getStatusCode()).isEqualTo(HttpStatus.OK);

        users.changePassword(userId, "a-brand-new-password-long-enough");

        assertThat(session.get("/api/customers").getStatusCode())
                .as("""
                        A reset is containment or an offboarding. Leaving the old sessions alive \
                        means the new password locks out only the legitimate owner while whoever \
                        prompted the reset keeps working — the opposite of the point.""")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Deactivating a role needs no eviction, and this is why — asserted rather than left in a
     * comment.
     *
     * <p>An inactive role grants nothing ({@code RoleView.accessTo}), so deactivating one revokes
     * everybody in it. It needs no session eviction only because it is <em>refused</em> while
     * anybody holds it. That makes this test the thing standing between the two facts: weaken the
     * refusal and this fails, instead of role deactivation quietly becoming unable to log anyone out.
     */
    @Test
    @DisplayName("a role with holders cannot be deactivated, which is why deactivation need not evict")
    void roleDeactivationIsRefusedWhileAnybodyHoldsIt() {
        long userId = userWith("evict.roleholder", Section.CUSTOMERS, AccessLevel.VIEW);
        long roleId = users.require(userId).role().id();

        assertThatExceptionOfType(InvalidRoleException.class)
                .isThrownBy(() -> roles.deactivate(roleId))
                .withMessageContaining("Move them to another role first");
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
