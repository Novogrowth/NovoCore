package gr.novotrade.novocore.core.security;

import gr.novotrade.novocore.core.api.security.RoleView;
import gr.novotrade.novocore.core.api.security.UserView;
import java.util.Map;
import java.util.Set;

/**
 * Entity-to-view conversion for this package, in one place.
 *
 * <p>Shared by {@code UserServiceImpl} and {@code RoleServiceImpl} rather than duplicated in
 * both, because a role converted two slightly different ways is a permission bug waiting to
 * happen — the kind of small duplication {@code CLAUDE.md}'s code-quality section warns
 * accumulates unnoticed.
 */
final class SecurityViews {

    private SecurityViews() {
    }

    static RoleView toView(Role role) {
        return new RoleView(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isFullAccess(),
                role.isSystemRole(),
                role.isActive(),
                // Defensive copies: these are Hibernate-managed collections, and handing them
                // out live would let a caller mutate a role's permissions with no audit entry
                // and no save.
                Map.copyOf(role.getSectionGrants()),
                Set.copyOf(role.getRestrictedFields()));
    }

    static UserView toView(User user) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getLanguage(),
                toView(user.getRole()),
                user.isActive());
    }
}
