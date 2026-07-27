package gr.novotrade.novocore.app.security;

import gr.novotrade.novocore.core.api.security.UserView;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user as Spring Security sees it, wrapping the core's {@link UserView}.
 *
 * <p><strong>{@link #getPassword()} returns null.</strong> That is not an oversight. The core
 * verifies passwords itself and never hands a hash out, so there is nothing to put here — and
 * because this object is what lives in the session, a hash stored on it would be serialised into
 * the session store for the lifetime of every login.
 *
 * <p>Authorities are derived from the role name for completeness and for anything that expects
 * them, but authorisation decisions are <em>not</em> made from them. They are made by asking
 * {@link UserView} about a {@link gr.novotrade.novocore.core.api.security.Section}, because a
 * role's grants are data and cannot be expressed as a fixed authority string.
 */
public final class NovoCorePrincipal implements UserDetails {

    private final UserView user;

    public NovoCorePrincipal(UserView user) {
        this.user = user;
    }

    public UserView user() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
    }

    /** Always null — see the class javadoc. */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    @Override
    public boolean isEnabled() {
        return user.active() && user.role().active();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public String toString() {
        return "NovoCorePrincipal[" + user.username() + ", role=" + user.role().name() + "]";
    }
}
