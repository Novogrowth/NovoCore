package gr.novotrade.novocore.core.security;

import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.ProtectedField;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A role: a named set of section grants and field restrictions.
 *
 * <p>An entity because brief §7 requires multiple custom roles from the start. The things being
 * granted ({@link Section}, {@link ProtectedField}) stay enums, because they are determined by
 * what the software does, not by configuration.
 *
 * <p>Both collections are {@code EAGER}. That is a considered exception to preferring lazy: a
 * role is a handful of rows, and its grants are needed on every single authenticated request to
 * answer "may this person do this?". Lazy loading here would mean either an N+1 on the hot path
 * or a detached-collection failure when the permissions are read outside a transaction, which is
 * exactly where they are read — while building the security principal.
 */
@Entity
@Table(name = "app_role")
class Role extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 400)
    private String description;

    /**
     * Full access to everything, bypassing {@link #sectionGrants} entirely.
     *
     * <p>A flag rather than a grant per section, so that a section added in a later release is
     * automatically visible to Owner and Admin. Stored grants would leave them locked out of new
     * parts of their own system until someone remembered to add a row.
     */
    @Column(name = "full_access", nullable = false)
    private boolean fullAccess;

    /** Seeded and unmodifiable. Stops the last administering role being stripped of its access. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_section_grant",
            joinColumns = @JoinColumn(name = "role_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "section", length = 60, nullable = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", length = 10, nullable = false)
    private Map<Section, AccessLevel> sectionGrants = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_field_restriction",
            joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "protected_field", length = 80, nullable = false)
    private Set<ProtectedField> restrictedFields = new HashSet<>();

    /** For JPA only. */
    protected Role() {
    }

    Role(String name, String description) {
        this.name = name;
        this.description = description;
        this.fullAccess = false;
        this.systemRole = false;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    boolean isFullAccess() {
        return fullAccess;
    }

    boolean isSystemRole() {
        return systemRole;
    }

    boolean isActive() {
        return active;
    }

    Map<Section, AccessLevel> getSectionGrants() {
        return sectionGrants;
    }

    Set<ProtectedField> getRestrictedFields() {
        return restrictedFields;
    }

    void rename(String newName) {
        this.name = newName;
    }

    /**
     * ⚠️ <strong>This setter did not exist until Q1 (2026-08-03), which made the field
     * structurally unwritable rather than merely unrouted.</strong>
     *
     * <p>Backend queue item 5: {@code NewRole} took a description, nothing could change it, and the
     * only correction for a typo was to create a second role, move every holder across and
     * deactivate the first. Nothing in this class, {@code RoleServiceImpl} or the step 16b proposal
     * gave a reason for the asymmetry, and the parallel field on every other entity is editable.
     *
     * <p>A description is not a permission — changing it confers nothing — so none of the escalation
     * guards that make role editing careful apply. {@code editableRole} still does, so a system role
     * stays untouchable like every other role write.
     *
     * @param newDescription null to clear it, which is a state {@code NewRole} already permits
     */
    void describe(String newDescription) {
        this.description = newDescription;
    }

    /** {@link AccessLevel#NONE} removes the row rather than storing a grant that grants nothing. */
    void grant(Section section, AccessLevel accessLevel) {
        if (accessLevel == AccessLevel.NONE) {
            sectionGrants.remove(section);
        } else {
            sectionGrants.put(section, accessLevel);
        }
    }

    void restrictField(ProtectedField field, boolean restricted) {
        if (restricted) {
            restrictedFields.add(field);
        } else {
            restrictedFields.remove(field);
        }
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }
}
