package gr.novotrade.novocore.core.api.security;

import java.util.Objects;

/**
 * Request to create a custom role.
 *
 * <p>Carries no grants: a new role starts with access to nothing and is granted sections
 * afterwards through {@link RoleService#grant}. Creating a role and granting it access are
 * separate, individually audited acts, which is the more useful trail when the question later is
 * "when did this role gain access to Settings?"
 */
public record NewRole(String name, String description) {

    public NewRole {
        Objects.requireNonNull(name, "name");
    }
}
