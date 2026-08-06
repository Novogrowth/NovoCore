package gr.novotrade.novocore.core.security;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A user account.
 *
 * <p>Table {@code app_user}, not {@code user}: {@code USER} is a reserved word in SQL and
 * {@code SELECT * FROM user} returns the database session user in PostgreSQL rather than
 * erroring, so the mistake would be quiet.
 *
 * <p><strong>The password hash has no getter.</strong> Verification is
 * {@link #passwordMatches}, which takes the candidate and returns a boolean, so the hash cannot
 * be read out of the entity even from inside the core package. That keeps it out of anything that
 * reflects over the object, including a debugger watch expression or a logging framework
 * serialising an entity.
 *
 * <p>One role per user. Brief §7's "multiple custom roles" is read as the system supporting many
 * role <em>definitions</em>, not many roles per person — the natural reading for a company of
 * this size, and recorded in {@code HISTORY.md} as an interpretation to correct if wrong.
 */
@Entity
@Table(name = "app_user")
class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored lower-case, so lookups cannot be defeated by capitalisation. */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    // EAGER: the role's grants are needed on every authenticated request, and are read while
    // building the security principal — outside any transaction this entity was loaded in.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * A BCP 47 tag, or null meaning this person has not chosen one — a real answer, not a missing
     * value. See {@link LanguageTag} and migration V27 for why there is no default.
     */
    @Column(name = "language", length = 8)
    private String language;

    /** For JPA only. */
    protected User() {
    }

    User(String username, String displayName, String passwordHash, Role role) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
    }

    Long getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    String getDisplayName() {
        return displayName;
    }

    Role getRole() {
        return role;
    }

    boolean isActive() {
        return active;
    }

    String getLanguage() {
        return language;
    }

    /**
     * Whether a candidate password matches. The only way to interrogate the stored hash.
     *
     * @param encoder supplied by the caller rather than held here, because an entity should not
     *     carry a Spring bean
     */
    boolean passwordMatches(PasswordEncoder encoder, String rawPassword) {
        return encoder.matches(rawPassword, passwordHash);
    }

    void replacePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    void rename(String newDisplayName) {
        this.displayName = newDisplayName;
    }

    void moveToRole(Role newRole) {
        this.role = newRole;
    }

    void setActive(boolean nowActive) {
        this.active = nowActive;
    }

    /** @param normalisedTag already through {@link LanguageTag#normalise}; null clears the choice */
    void chooseLanguage(String normalisedTag) {
        this.language = normalisedTag;
    }
}
