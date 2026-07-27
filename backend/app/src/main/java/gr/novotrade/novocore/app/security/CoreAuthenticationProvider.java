package gr.novotrade.novocore.app.security;

import gr.novotrade.novocore.core.api.security.UserService;
import gr.novotrade.novocore.core.api.security.UserView;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Authenticates against the core rather than against a hash the framework was handed.
 *
 * <p>The conventional arrangement is a {@code UserDetailsService} that loads the user including
 * its password hash, which {@code DaoAuthenticationProvider} then compares. That puts the hash on
 * the core's boundary and, from there, into every stack trace, debugger and object dump on the
 * authentication path. Here the plain password goes <em>in</em> to
 * {@link UserService#authenticate} and a user or nothing comes back, so no hash exists outside
 * the core to leak.
 *
 * <p>Every failure raises the same {@link BadCredentialsException}. The core already declines to
 * distinguish an unknown username from a wrong password from a deactivated account, and this
 * preserves that: telling them apart is how an attacker enumerates who works here. The
 * distinction is recorded in the audit log, which is where it legitimately belongs.
 */
@Component
class CoreAuthenticationProvider implements AuthenticationProvider {

    private final UserService users;

    CoreAuthenticationProvider(UserService users) {
        this.users = users;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        if (username == null || credentials == null) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        Optional<UserView> authenticated = users.authenticate(username, credentials.toString());
        if (authenticated.isEmpty()) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        NovoCorePrincipal principal = new NovoCorePrincipal(authenticated.get());
        // Credentials deliberately omitted from the resulting token: it goes into the session,
        // and a session holding the plain password would be readable for as long as the login
        // lasts.
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
