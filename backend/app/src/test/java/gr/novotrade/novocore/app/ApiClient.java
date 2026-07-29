package gr.novotrade.novocore.app;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Logging in and making authenticated calls, over real HTTP.
 *
 * <p>Extracted in step 14 because a second endpoint test would otherwise have copied eighty lines of
 * this. {@code CLAUDE.md}'s code-quality section is explicit that the architecture rules prevent
 * cross-component coupling and do nothing about intra-component duplication; two copies of the login
 * dance is exactly the shape that decays, because the second copy is the one that stops being
 * updated.
 *
 * <p><strong>Cookies are carried by hand rather than by a client cookie jar</strong>, deliberately.
 * A jar would hide the {@code Set-Cookie} header, and {@code Secure} / {@code HttpOnly} /
 * {@code SameSite} are attributes a test has to be able to assert on — without needing TLS to do it.
 *
 * <p><strong>Step 15a moved the wire onto {@link HttpTransport}</strong>, so one scenario can run
 * both under Failsafe and against a live server, and <strong>put {@link JsonNumberSweep} on every
 * response</strong>. The sweep is here rather than at the call sites for the reason
 * {@code WebConfiguration} gives for registering the money serialiser globally: a check applied per
 * call is forgotten on the call that mattered.
 */
final class ApiClient {

    static final String SESSION_COOKIE = "NOVOCORESESSION";
    static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final HttpTransport transport;

    ApiClient(TestRestTemplate rest) {
        this(HttpTransport.of(rest));
    }

    ApiClient(HttpTransport transport) {
        this.transport = transport;
    }

    /** @throws AssertionError if the login did not produce a session */
    Session logIn(String username, String password) {
        LoginAttempt attempt = attemptLogin(username, password);
        String cookie = attempt.sessionCookie().orElseThrow(() -> new AssertionError(
                "Login as " + username + " produced no session cookie. Response: "
                        + attempt.response().getStatusCode() + ", body: "
                        + attempt.response().getBody()));
        return new Session(valueOf(cookie), cookie, attempt.csrfToken());
    }

    /** The raw attempt, for tests that are about failing to log in. */
    LoginAttempt attemptLogin(String username, String password) {
        // The CSRF token cookie is written on the first response because deferred token loading is
        // switched off in SecurityConfiguration; without that there would be no token to send.
        ResponseEntity<String> initial =
                exchange("/login", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()));
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
        ResponseEntity<String> response = exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(form, headers));

        return new LoginAttempt(response, cookie(response, SESSION_COOKIE), csrfToken);
    }

    /**
     * The one place a request leaves this class, which is why the sweep is here: every response
     * this client ever returns has been checked for a decimal that arrived as a JSON number.
     */
    ResponseEntity<String> exchange(String path, HttpMethod method, HttpEntity<?> entity) {
        ResponseEntity<String> response = transport.exchange(path, method, entity);
        JsonNumberSweep.check(method, path,
                response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE), response.getBody());
        return response;
    }

    static Optional<String> cookie(ResponseEntity<?> response, String name) {
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

    static String valueOf(String setCookieHeader) {
        String withoutName = setCookieHeader.substring(setCookieHeader.indexOf('=') + 1);
        int end = withoutName.indexOf(';');
        return end < 0 ? withoutName : withoutName.substring(0, end);
    }

    record LoginAttempt(
            ResponseEntity<String> response, Optional<String> sessionCookie, String csrfToken) {
    }

    /** An authenticated session. Every call carries the session cookie and the CSRF token. */
    final class Session {

        private final String sessionId;
        private final String rawSessionCookie;
        private final String csrfToken;

        private Session(String sessionId, String rawSessionCookie, String csrfToken) {
            this.sessionId = sessionId;
            this.rawSessionCookie = rawSessionCookie;
            this.csrfToken = csrfToken;
        }

        String rawSessionCookie() {
            return rawSessionCookie;
        }

        String sessionId() {
            return sessionId;
        }

        HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE,
                    SESSION_COOKIE + "=" + sessionId + "; " + CSRF_COOKIE + "=" + csrfToken);
            headers.add("X-XSRF-TOKEN", csrfToken);
            return headers;
        }

        ResponseEntity<String> get(String path) {
            return exchange(path, HttpMethod.GET, new HttpEntity<>(headers()));
        }

        ResponseEntity<String> post(String path, String jsonBody) {
            return send(path, HttpMethod.POST, jsonBody);
        }

        ResponseEntity<String> patch(String path, String jsonBody) {
            return send(path, HttpMethod.PATCH, jsonBody);
        }

        ResponseEntity<String> put(String path, String jsonBody) {
            return send(path, HttpMethod.PUT, jsonBody);
        }

        ResponseEntity<String> delete(String path) {
            return exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers()));
        }

        private ResponseEntity<String> send(String path, HttpMethod method, String jsonBody) {
            HttpHeaders headers = headers();
            if (jsonBody != null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            return exchange(path, method, new HttpEntity<>(jsonBody, headers));
        }
    }
}
