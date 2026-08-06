package gr.novotrade.novocore.app;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * The second {@link HttpTransport}: a real server, over real HTTPS, at a base URL given from outside.
 *
 * <p><strong>This is the implementation {@code HttpTransport} was extracted for and never got.</strong>
 * Its javadoc has said since step 15a that the seam exists so one scenario can run "under Failsafe
 * against a random test port, and — for the seed pass that populates the live Compose database —
 * against a real server over HTTPS". Step 15 shipped 15a and 15b and the seam kept exactly one
 * implementation, so the live Compose database was never populated by anything. See the F0 section of
 * {@code HISTORY.md}.
 *
 * <h2>Three decisions worth reading before changing this</h2>
 *
 * <p><strong>Certificates are not verified against a trust store.</strong> Caddy signs the development
 * site with its own internal CA, which is not in the JDK's. This is the same concession
 * {@code vite.config.ts} already makes with {@code secure: false}, for the same reason and on the same
 * hop, and it is confined to a class that cannot run without an explicitly supplied base URL.
 * <strong>Hostname verification is deliberately left on</strong> — the certificate really is issued for
 * {@code localhost}, so switching it off would buy nothing and would quietly permit pointing this at a
 * host whose certificate names somebody else.
 *
 * <p><strong>Errors are not thrown.</strong> {@code RestTemplate}'s default handler raises on 4xx and
 * 5xx; {@code TestRestTemplate}'s does not. Every caller above this line reads the status off the
 * {@link ResponseEntity} — {@code ApiClient.attemptLogin} is <em>about</em> a 401, and
 * {@code LiveSeedTest}'s emptiness check reads whatever it is given — so throwing here would change the
 * behaviour of the shared code depending on which transport it happened to be running under, which is
 * precisely what the seam exists to prevent.
 *
 * <p><strong>Redirects are not followed.</strong> {@link HttpClient} defaults to
 * {@link HttpClient.Redirect#NEVER}, which is what this wants: {@code /login} answers a status rather
 * than a redirect by design, and a client that quietly followed a 302 to an HTML page would report a
 * failed login as a success — the reason {@code SecurityConfiguration} answers the way it does.
 */
final class LiveHttpTransport implements HttpTransport {

    private final String baseUrl;
    private final RestTemplate rest;

    LiveHttpTransport(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.rest = new RestTemplate(new JdkClientHttpRequestFactory(client()));
        this.rest.setUriTemplateHandler(new DefaultUriBuilderFactory(this.baseUrl));
        this.rest.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
    }

    /** The target, so a refusal can name what it was pointed at rather than only that it refused. */
    String baseUrl() {
        return baseUrl;
    }

    @Override
    public ResponseEntity<String> exchange(String path, HttpMethod method, HttpEntity<?> entity) {
        return rest.exchange(path, method, entity, String.class);
    }

    private static HttpClient client() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {ACCEPT_CADDYS_INTERNAL_CA}, new SecureRandom());
            return HttpClient.newBuilder().sslContext(context).build();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build an HTTPS client for the seed pass.", e);
        }
    }

    /**
     * Named for what it is for rather than called {@code TRUST_ALL}, so that a reader who finds it
     * somewhere it does not belong can see immediately that it does not belong there.
     */
    private static final X509TrustManager ACCEPT_CADDYS_INTERNAL_CA = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };
}
