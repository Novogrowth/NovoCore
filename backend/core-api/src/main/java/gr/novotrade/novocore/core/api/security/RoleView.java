package gr.novotrade.novocore.core.api.security;

import gr.novotrade.novocore.core.api.shared.Mandatory;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A role and everything it may do. The permission decision itself lives here.
 *
 * <p>Deliberately a value object with the logic on it, rather than a data bag plus a service that
 * interprets it. Every "may this person see this?" question is answered by one of the methods
 * below, so there is a single implementation of the rule instead of one per caller — and because
 * it holds no dependencies, the whole permission model is testable without a database or a
 * security context.
 *
 * @param fullAccess when true, every section is {@link AccessLevel#FULL} and no field is hidden,
 *     regardless of {@code sectionGrants}. This is how Owner and Admin are expressed. It matters
 *     that it is a flag and not a seeded grant per section: a section added in a later release is
 *     then automatically visible to them, where a stored row per section would silently leave the
 *     owner locked out of new parts of their own system.
 * @param systemRole when true, the role's permissions may not be edited and it may not be
 *     deleted. Protects against removing {@link Section#USERS_AND_ROLES} from the only role that
 *     has it and locking everyone out irrecoverably.
 * @param sectionGrants explicit grants. Anything absent is {@link AccessLevel#NONE} — access is
 *     default-deny.
 * @param restrictedFields fields hidden from this role within sections it can otherwise see.
 */
public record RoleView(
        long id,
        @Mandatory String name,
        String description,
        boolean fullAccess,
        boolean systemRole,
        boolean active,
        @Mandatory Map<Section, AccessLevel> sectionGrants,
        @Mandatory Set<ProtectedField> restrictedFields) {

    public RoleView {
        Objects.requireNonNull(name, "name");
        sectionGrants = Map.copyOf(Objects.requireNonNull(sectionGrants, "sectionGrants"));
        // Copied into an EnumSet rather than passed through Set.copyOf, so membership tests are
        // a bit vector rather than hashing. EnumSet.copyOf(Collection) rejects an empty
        // non-EnumSet argument, hence addAll into an empty set instead.
        Objects.requireNonNull(restrictedFields, "restrictedFields");
        Set<ProtectedField> fields = EnumSet.noneOf(ProtectedField.class);
        fields.addAll(restrictedFields);
        restrictedFields = Collections.unmodifiableSet(fields);
    }

    /**
     * This role's access to a section.
     *
     * <p>An inactive role grants nothing. That is belt and braces alongside deactivating the
     * user — disabling a role should not depend on remembering to disable everyone holding it.
     */
    public AccessLevel accessTo(Section section) {
        Objects.requireNonNull(section, "section");
        if (!active) {
            return AccessLevel.NONE;
        }
        if (fullAccess) {
            return AccessLevel.FULL;
        }
        return sectionGrants.getOrDefault(section, AccessLevel.NONE);
    }

    public boolean canView(Section section) {
        return accessTo(section).allowsView();
    }

    public boolean canEdit(Section section) {
        return accessTo(section).allowsEdit();
    }

    /** @throws SectionAccessDeniedException if this role cannot view the section */
    public void requireView(Section section) {
        if (!canView(section)) {
            throw new SectionAccessDeniedException(name, section, AccessLevel.VIEW);
        }
    }

    /** @throws SectionAccessDeniedException if this role cannot change the section */
    public void requireEdit(Section section) {
        if (!canEdit(section)) {
            throw new SectionAccessDeniedException(name, section, AccessLevel.FULL);
        }
    }

    /**
     * Whether this role may see a specific protected field.
     *
     * <p>Section access is checked first: a role that cannot view Products does not see a
     * product's cost, whether or not that field is individually restricted. Field restrictions
     * only ever narrow, never widen — so a field cannot be made visible by leaving it off the
     * restriction list if the section itself is denied.
     */
    public boolean canSee(ProtectedField field) {
        Objects.requireNonNull(field, "field");
        return canView(field.section()) && !restrictedFields.contains(field);
    }

    /**
     * The fields hidden from this role within one section, for a caller building a response.
     *
     * <p>Returns empty for a full-access role, and — importantly — empty for a section this role
     * cannot see at all, because in that case there is no response to redact.
     */
    public Set<ProtectedField> hiddenFieldsIn(Section section) {
        Objects.requireNonNull(section, "section");
        if (fullAccess || !canView(section)) {
            return Set.of();
        }
        return restrictedFields.stream()
                .filter(field -> field.section() == section)
                .collect(() -> EnumSet.noneOf(ProtectedField.class), Set::add, Set::addAll);
    }

    /** Sections this role can see at all, for building a navigation menu. */
    public Set<Section> visibleSections() {
        return EnumSet.allOf(Section.class).stream()
                .filter(this::canView)
                .collect(() -> EnumSet.noneOf(Section.class), Set::add, Set::addAll);
    }
}
