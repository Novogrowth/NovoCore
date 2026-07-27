package gr.novotrade.novocore.app.security;

import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.NotAuthenticatedException;
import gr.novotrade.novocore.core.api.security.UserView;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Answers "who is making this request?" from the Spring Security context.
 *
 * <p>The implementation of the seam described on {@link CurrentUser}: this is the only class that
 * knows both that Spring Security exists and what a NovoCore user is. The core asks the interface
 * and stays unaware of the framework, which is what lets an architecture test assert that
 * {@code core-api} never gains a Spring dependency.
 */
@Component
class SecurityContextCurrentUser implements CurrentUser {

    @Override
    public Optional<UserView> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        // Anonymous authentication is "authenticated" as far as the flag goes, so the principal
        // type is what actually distinguishes a logged-in user from an anonymous request.
        if (authentication.getPrincipal() instanceof NovoCorePrincipal principal) {
            return Optional.of(principal.user());
        }
        return Optional.empty();
    }

    @Override
    public UserView require() {
        return find().orElseThrow(NotAuthenticatedException::new);
    }

    @Override
    public Optional<String> username() {
        return find().map(UserView::username);
    }
}
