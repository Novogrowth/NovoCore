package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The one chart-of-accounts endpoint, over real HTTP, with real authentication.
 *
 * <p>This is what the endpoint was built for. It proves three things that no unit test can: that
 * the filter chain refuses an unauthenticated call, that the section permission check refuses
 * Remote/Order Staff, and that the session cookie carries the attributes Q22's decision depends
 * on. Cookies are read from and written to headers by hand rather than left to a client cookie
 * jar, so the {@code Secure} attribute can be asserted without the test needing TLS.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.password=overridden-by-testcontainers",
            // Without an initial owner the application refuses to start on an empty user table,
            // which is the intended behaviour — so the test supplies one.
            "novocore.bootstrap.owner-username=" + ChartOfAccountsEndpointIT.OWNER_USERNAME,
            "novocore.bootstrap.owner-password=" + ChartOfAccountsEndpointIT.OWNER_PASSWORD,
        })
@Import(PostgresTestContainerConfiguration.class)
// Boot 4 no longer supplies TestRestTemplate implicitly with RANDOM_PORT.
@AutoConfigureTestRestTemplate
class ChartOfAccountsEndpointIT {

    static final String OWNER_USERNAME = "endpoint.owner";
    static final String OWNER_PASSWORD = "owner-password-long-enough";

    private static final String STAFF_USERNAME = "endpoint.staff";
    private static final String STAFF_PASSWORD = "staff-password-long-enough";

    private static final String ENDPOINT = "/api/chart-of-accounts";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserService users;

    @Autowired
    private RoleService roles;

    @Test
    @DisplayName("the initial owner account was created from configuration, not seeded")
    void ownerWasBootstrapped() {
        assertThat(users.findByUsername(OWNER_USERNAME)).isPresent();
        assertThat(users.findByUsername(OWNER_USERNAME).orElseThrow().role().name())
                .isEqualTo("OWNER");
    }

    @Test
    @DisplayName("an unauthenticated request gets 401, not a redirect to a login page")
    void unauthenticatedIsRejected() {
        ResponseEntity<String> response = rest.getForEntity(ENDPOINT, String.class);

        // A redirect would have the frontend's fetch() receive an HTML login page with status
        // 200 and try to parse it as JSON.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // The 401 from the entry point carries no body at all, which is the desired outcome —
        // asserted as "nothing leaked" rather than assuming a body exists to inspect.
        assertThat(response.getBody()).satisfiesAnyOf(
                body -> assertThat(body).isNull(),
                body -> assertThat(body).doesNotContain("Cash"));
    }

    @Test
    @DisplayName("the owner gets the chart of accounts")
    void ownerCanReadTheChart() {
        ApiClient.Session session = logIn(OWNER_USERNAME, OWNER_PASSWORD);

        ResponseEntity<String> response = session.get(ENDPOINT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Cash & Cash Equivalents")
                .contains("Rounding differences")
                // Proves the derived fields are serialised, not just the stored ones.
                .contains("CONTRA_ASSET");
    }

    @Test
    @DisplayName("Remote/Order Staff gets 403 — the section is not in their role")
    void remoteStaffIsForbidden() {
        createStaffUser();
        ApiClient.Session session = logIn(STAFF_USERNAME, STAFF_PASSWORD);

        ResponseEntity<String> response = session.get(ENDPOINT);

        // Authenticated but not authorised. 403 rather than 401, because logging in again would
        // not help.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody())
                .as("the refusal must not describe the permission model or confirm the contents")
                .doesNotContain("Cash")
                .doesNotContain("CHART_OF_ACCOUNTS");
    }

    @Test
    @DisplayName("the session cookie is HttpOnly, Secure and SameSite=Strict")
    void sessionCookieIsHardened() {
        ApiClient.Session session = logIn(OWNER_USERNAME, OWNER_PASSWORD);

        // The concrete reason Q22 chose a session cookie over a token in web storage: HttpOnly
        // means a cross-site scripting bug cannot read the session identifier at all.
        assertThat(session.rawSessionCookie()).contains("HttpOnly");
        assertThat(session.rawSessionCookie()).contains("Secure");
        assertThat(session.rawSessionCookie()).containsIgnoringCase("SameSite=Strict");
    }

    @Test
    @DisplayName("a state-changing request without a CSRF token is refused")
    void csrfIsEnforced() {
        ApiClient.Session session = logIn(OWNER_USERNAME, OWNER_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, ApiClient.SESSION_COOKIE + "=" + session.sessionId());
        // Deliberately omitting the X-XSRF-TOKEN header.
        ResponseEntity<String> response = rest.exchange(
                "/logout", HttpMethod.POST, new HttpEntity<>(headers), String.class);

        // Cookie authentication without CSRF protection means any site the user visits while
        // logged in can make their browser send an authenticated request.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("logging out invalidates the session")
    void logoutInvalidatesTheSession() {
        ApiClient.Session session = logIn(OWNER_USERNAME, OWNER_PASSWORD);
        assertThat(session.get(ENDPOINT).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> logout = rest.exchange(
                "/logout", HttpMethod.POST, new HttpEntity<>(session.headers()), String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Revocation is deleting the session — the property a self-contained token would not have.
        assertThat(session.get(ENDPOINT).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a wrong password produces 401 and no session")
    void wrongPasswordDoesNotAuthenticate() {
        ApiClient.LoginAttempt attempt = attemptLogin(OWNER_USERNAME, "not-the-right-password");

        assertThat(attempt.response().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(attempt.sessionCookie())
                .as("a failed login must not hand out an authenticated session")
                .isEmpty();
    }

    @Test
    @DisplayName("an unknown username is refused identically to a wrong password")
    void unknownUsernameLooksTheSame() {
        ApiClient.LoginAttempt wrongPassword = attemptLogin(OWNER_USERNAME, "not-the-right-password");
        ApiClient.LoginAttempt unknownUser = attemptLogin("nobody.at.all", "not-the-right-password");

        // Over HTTP as well as in the service: telling these apart is how an attacker works out
        // who has an account here.
        assertThat(unknownUser.response().getStatusCode())
                .isEqualTo(wrongPassword.response().getStatusCode());
        assertThat(unknownUser.response().getBody())
                .isEqualTo(wrongPassword.response().getBody());
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures. The login plumbing lives in ApiClient — see its javadoc for why it was extracted.
    // ---------------------------------------------------------------------------------------

    private void createStaffUser() {
        if (users.findByUsername(STAFF_USERNAME).isPresent()) {
            return;
        }
        users.create(new NewUser(
                STAFF_USERNAME,
                "Endpoint Staff",
                STAFF_PASSWORD,
                roles.requireByName("REMOTE_ORDER_STAFF").id()));
    }

    private ApiClient api() {
        return new ApiClient(rest);
    }

    private ApiClient.Session logIn(String username, String password) {
        return api().logIn(username, password);
    }

    private ApiClient.LoginAttempt attemptLogin(String username, String password) {
        return api().attemptLogin(username, password);
    }
}
