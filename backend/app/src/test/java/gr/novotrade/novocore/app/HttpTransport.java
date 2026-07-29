package gr.novotrade.novocore.app;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * How {@link ApiClient} actually puts a request on the wire.
 *
 * <p><strong>Why this seam exists.</strong> Step 15's scenario has to be able to run in two places
 * from one definition: under Failsafe against a random test port, and — for the seed pass that
 * populates the live Compose database — against a real server over HTTPS. Those differ in exactly
 * one respect, which is how bytes get sent, and everything above this line (logging in, carrying the
 * session cookie, echoing the CSRF token, sweeping the response) is identical.
 *
 * <p>Without the seam the alternative is a second copy of the scenario written against a different
 * client, which is the shape {@code CLAUDE.md}'s code-quality section says decays: the copy is what
 * stops being updated.
 *
 * <p>It returns Spring's {@link ResponseEntity} rather than a type of its own, deliberately. Every
 * existing endpoint test asserts against {@code ResponseEntity} — including on the raw
 * {@code Set-Cookie} header, which is the whole reason these tests do not use MockMvc — and a
 * bespoke response type would have meant rewriting all of them to gain nothing.
 */
interface HttpTransport {

    /**
     * @param path a server-relative path, e.g. {@code /api/products}; the implementation supplies
     *             whatever prefix its target needs
     */
    ResponseEntity<String> exchange(String path, HttpMethod method, HttpEntity<?> entity);

    /** The transport the endpoint tests use: Boot's own client, already pointed at the test port. */
    static HttpTransport of(TestRestTemplate rest) {
        return (path, method, entity) -> rest.exchange(path, method, entity, String.class);
    }
}
