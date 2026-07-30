package gr.novotrade.novocore.core.api.security;

import java.util.Objects;
import java.util.Optional;

/**
 * A user account, with its role's permissions already resolved.
 *
 * <p><strong>No password hash.</strong> There is deliberately no field for it and no accessor
 * anywhere that returns one. Verification happens inside the core, via
 * {@link UserService#authenticate}, so the hash never crosses the core's boundary at all — an
 * adapter, a controller or a log statement cannot leak what it cannot obtain.
 *
 * <p>The role is embedded rather than referenced by id because every request needs the
 * permissions, and a view that forced a second lookup to answer "may I?" would end up cached
 * somewhere it should not be.
 *
 * @param role never null; every user has exactly one role
 * @param language a BCP 47 tag such as {@code "el"} or {@code "el-GR"}, or <strong>null where this
 *     person has not chosen one</strong> — a real answer rather than a missing value, which is why
 *     there is no default (migration V27). It is a <em>frontend</em> display preference: the
 *     backend localises nothing and every message it produces is English (Q47b), so nothing in the
 *     core reads this. The day that changes, the supported set becomes an enum rather than a tag.
 */
public record UserView(
        long id,
        String username,
        String displayName,
        String language,
        RoleView role,
        boolean active) {

    public UserView {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(role, "role");
        // language is deliberately not required: null is the state of every account until
        // somebody picks one, and there is nothing to default it to.
    }

    /**
     * Whether this user may see a section. False for a deactivated user whatever their role says.
     */
    public boolean canView(Section section) {
        return active && role.canView(section);
    }

    public boolean canEdit(Section section) {
        return active && role.canEdit(section);
    }

    /** @throws SectionAccessDeniedException if this user may not view the section */
    public void requireView(Section section) {
        if (!active) {
            throw new SectionAccessDeniedException(
                    "deactivated user " + username, section, AccessLevel.VIEW);
        }
        role.requireView(section);
    }

    /** @throws SectionAccessDeniedException if this user may not change the section */
    public void requireEdit(Section section) {
        if (!active) {
            throw new SectionAccessDeniedException(
                    "deactivated user " + username, section, AccessLevel.FULL);
        }
        role.requireEdit(section);
    }

    /** Whether this user may see a specific protected field. */
    public boolean canSee(ProtectedField field) {
        return active && role.canSee(field);
    }

    /** The chosen language, or empty where none has been chosen — never a substituted default. */
    public Optional<String> languageIfChosen() {
        return Optional.ofNullable(language);
    }
}
