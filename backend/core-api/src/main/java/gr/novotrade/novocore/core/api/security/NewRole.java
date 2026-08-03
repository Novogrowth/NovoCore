package gr.novotrade.novocore.core.api.security;

import gr.novotrade.novocore.core.api.shared.Required;

/**
 * Request to create a custom role.
 *
 * <p>Carries no grants: a new role starts with access to nothing and is granted sections
 * afterwards through {@link RoleService#grant}. Creating a role and granting it access are
 * separate, individually audited acts, which is the more useful trail when the question later is
 * "when did this role gain access to Settings?"
 */
public record NewRole(String name, String description) {

    /**
     * ⚠️ <strong>{@code Objects.requireNonNull} until Q1 (2026-08-03)</strong> — see
     * {@link NewUser} for why that was the only thing its author could have written, and
     * {@code field} rather than {@code text} for why a blank name still reaches
     * {@code RoleServiceImpl} and is refused there with a 422.
     *
     * <p>{@code POST /api/roles} with an empty body is the <em>clean</em> case for this guard, and
     * worth knowing when reading {@code PermissionSweepIT.noRouteFailsOnAnEmptyBody}: on
     * {@code POST /api/users} the primitive {@code roleId} fails first, so that route never reaches
     * its own guard. This record has no primitive, so it does.
     */
    public NewRole {
        Required.field(name, "name");
    }
}
