package gr.novotrade.novocore.app.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Authentication and session handling.
 *
 * <p><strong>Server-side sessions with an HttpOnly cookie, not JWT</strong> (Q22, approved). For a
 * single self-hosted application this is the simpler and safer choice: revocation is deleting the
 * session, so a dismissed employee's access ends immediately, where a self-contained token stays
 * valid until it expires unless a revocation list is built — which is a session table with extra
 * steps. Nothing here is stateless-scale-sensitive; there is one instance.
 *
 * <p>The cookie's own attributes — {@code HttpOnly}, {@code SameSite=Strict}, {@code Secure} — are
 * set in {@code application.yml}, because they are servlet container settings rather than Spring
 * Security ones. {@code HttpOnly} is what stops a cross-site scripting bug from being able to read
 * the session identifier at all.
 *
 * <p><strong>No login controller is written here.</strong> Authentication uses Spring Security's
 * own {@code /login} and {@code /logout} endpoints, so this step adds no hand-written API surface
 * beyond the single chart-of-accounts endpoint it exists to validate. The real login screen is
 * frontend work.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CoreAuthenticationProvider provider)
            throws Exception {

        // Opting out of deferred CSRF token loading, so the XSRF-TOKEN cookie is written on the
        // first response rather than only once something reads the token. Without this a client
        // has no token to send on its first state-changing request, and the failure looks like a
        // mysterious 403 on login rather than a missing cookie.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .authenticationProvider(provider)

                .authorizeHttpRequests(requests -> requests
                        // The container runtime and Caddy poll these before anyone can log in.
                        // Caddy already refuses /actuator* from outside, so this is not a public
                        // surface despite being unauthenticated here.
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info")
                        .permitAll()
                        // Everything else, including the one chart-of-accounts endpoint.
                        .anyRequest().authenticated())

                // CSRF matters precisely because authentication is a cookie: without it, any site
                // the user visits while logged in can make the browser send an authenticated
                // request. The token is readable by JavaScript by design — it has to be, for the
                // frontend to echo it back — and that is safe, because an attacker's site cannot
                // read a cookie belonging to another origin.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // A new session identifier on login, so a fixated identifier planted
                        // before authentication does not become an authenticated one.
                        .sessionFixation(fixation -> fixation.newSession()))

                // Framework-provided endpoints; no controller of ours is involved.
                //
                // Status codes rather than the default redirects. A frontend calling fetch()
                // cannot do anything useful with a 302 to a login page — it would follow it
                // transparently and receive HTML with status 200, which looks like success. So
                // login answers 204 with the session cookie, or 401. Same reasoning as the
                // authentication entry point below.
                .formLogin(login -> login
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .failureHandler((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .deleteCookies("NOVOCORESESSION")
                        .invalidateHttpSession(true))

                .exceptionHandling(exceptions -> exceptions
                        // An unauthenticated API call gets 401, not a redirect to a login page.
                        // The default redirect would have the frontend's fetch() receive an HTML
                        // page with status 200 and try to parse it as JSON.
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**")))

                .headers(headers -> headers
                        // HSTS is set by Caddy, which is what terminates TLS; setting it here as
                        // well would mean two places to change one policy.
                        .httpStrictTransportSecurity(hsts -> hsts.disable())
                        .contentTypeOptions(options -> {
                        })
                        .frameOptions(frame -> frame.deny()));

        return http.build();
    }
}
