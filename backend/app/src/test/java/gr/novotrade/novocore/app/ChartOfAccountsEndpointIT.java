package gr.novotrade.novocore.app;

import static org.assertj.core.api.Assertions.assertThat;

import gr.novotrade.novocore.core.api.security.NewUser;
import gr.novotrade.novocore.core.api.security.RoleService;
import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.testsupport.PostgresTestContainerConfiguration;
import java.util.List;
import java.util.Optional;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
    private static final String SESSION_COOKIE = "NOVOCORESESSION";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";

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
        Session session = logIn(OWNER_USERNAME, OWNER_PASSWORD);

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
        Session session = logIn(STAFF_USERNAME, STAFF_PASSWORD);

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
        Session session = logIn(OWNER_USERNAME, OWNER_PASSWORD);

        // The concrete reason Q22 chose a session cookie over a token in web storage: HttpOnly
        // means a cross-site scripting bug cannot read the session identifier at all.
        assertThat(session.rawSessionCookie()).contains("HttpOnly");
        assertThat(session.rawSessionCookie()).contains("Secure");
        assertThat(session.rawSessionCookie()).containsIgnoringCase("SameSite=Strict");
    }

    @Test
    @DisplayName("a state-changing request without a CSRF token is refused")
    void csrfIsEnforced() {
        Session session = logIn(OWNER_USERNAME, OWNER_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SESSION_COOKIE + "=" + session.sessionId);
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
        Session session = logIn(OWNER_USERNAME, OWNER_PASSWORD);
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
        LoginAttempt attempt = attemptLogin(OWNER_USERNAME, "not-the-right-password");

        assertThat(attempt.response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(attempt.sessionCookie)
                .as("a failed login must not hand out an authenticated session")
                .isEmpty();
    }

    @Test
    @DisplayName("an unknown username is refused identically to a wrong password")
    void unknownUsernameLooksTheSame() {
        LoginAttempt wrongPassword = attemptLogin(OWNER_USERNAME, "not-the-right-password");
        LoginAttempt unknownUser = attemptLogin("nobody.at.all", "not-the-right-password");

        // Over HTTP as well as in the service: telling these apart is how an attacker works out
        // who has an account here.
        assertThat(unknownUser.response.getStatusCode())
                .isEqualTo(wrongPassword.response.getStatusCode());
        assertThat(unknownUser.response.getBody())
                .isEqualTo(wrongPassword.response.getBody());
    }

    // ---------------------------------------------------------------------------------------
    // Login plumbing
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

    private Session logIn(String username, String password) {
        LoginAttempt attempt = attemptLogin(username, password);
        String cookie = attempt.sessionCookie.orElseThrow(() -> new AssertionError(
                "Login as " + username + " produced no session cookie. Response: "
                        + attempt.response.getStatusCode() + ", body: "
                        + attempt.response.getBody()));
        return new Session(valueOf(cookie), cookie, attempt.csrfToken);
    }

    private LoginAttempt attemptLogin(String username, String password) {
        // The CSRF token cookie is written on the first response because deferred token loading
        // is switched off in SecurityConfiguration; without that there would be no token to send.
        ResponseEntity<String> initial = rest.getForEntity("/login", String.class);
        String csrfCookie = cookie(initial, CSRF_COOKIE).orElseThrow(() ->
                new AssertionError("No " + CSRF_COOKIE + " cookie on the login page response."));
        String csrfToken = valueOf(csrfCookie);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, CSRF_COOKIE + "=" + csrfToken);
        headers.add("X-XSRF-TOKEN", csrfToken);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);

        // Login answers 204 or 401 rather than redirecting, so the response — and its Set-Cookie
        // header — is directly observable. Relying on redirect behaviour would not be: Boot 4
        // dropped TestRestTemplate's ENABLE_REDIRECTS option, so whether a 302 is followed is no
        // longer something a test can pin down.
        ResponseEntity<String> response = rest.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);

        return new LoginAttempt(response, cookie(response, SESSION_COOKIE), csrfToken);
    }

    private static Optional<String> cookie(ResponseEntity<?> response, String name) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return Optional.empty();
        }
        return setCookies.stream()
                .filter(value -> value.startsWith(name + "="))
                // A logout writes an expiring cookie with an empty value; ignore those.
                .filter(value -> !value.startsWith(name + "=;"))
                .findFirst();
    }

    private static String valueOf(String setCookieHeader) {
        String withoutName = setCookieHeader.substring(setCookieHeader.indexOf('=') + 1);
        int end = withoutName.indexOf(';');
        return end < 0 ? withoutName : withoutName.substring(0, end);
    }

    private record LoginAttempt(
            ResponseEntity<String> response, Optional<String> sessionCookie, String csrfToken) {
    }

    /** An authenticated session, carried by hand so cookie attributes stay inspectable. */
    private final class Session {

        private final String sessionId;
        private final String rawSessionCookie;
        private final String csrfToken;

        private Session(String sessionId, String rawSessionCookie, String csrfToken) {
            this.sessionId = sessionId;
            this.rawSessionCookie = rawSessionCookie;
            this.csrfToken = csrfToken;
        }

        private String rawSessionCookie() {
            return rawSessionCookie;
        }

        private HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE,
                    SESSION_COOKIE + "=" + sessionId + "; " + CSRF_COOKIE + "=" + csrfToken);
            headers.add("X-XSRF-TOKEN", csrfToken);
            return headers;
        }

        private ResponseEntity<String> get(String path) {
            return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers()), String.class);
        }
    }
}
