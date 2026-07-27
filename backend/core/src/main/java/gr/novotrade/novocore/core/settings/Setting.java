package gr.novotrade.novocore.core.settings;

import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One configuration value, keyed by name.
 *
 * <p>Values are text and are parsed by the service on read. See the settings migration for why
 * this is a plain key/value store rather than typed columns.
 */
@Entity
@Table(name = "setting")
class Setting extends AuditableEntity {

    @Id
    @Column(name = "setting_key", nullable = false, length = 100)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;

    /** Credentials. Redacted in anything returned from the API, never written to the audit log. */
    @Column(name = "secret", nullable = false)
    private boolean secret;

    @Column(name = "description")
    private String description;

    /** For JPA only. */
    protected Setting() {
    }

    Setting(String key, String value, boolean secret, String description) {
        this.key = key;
        this.value = value;
        this.secret = secret;
        this.description = description;
    }

    String getKey() {
        return key;
    }

    String getValue() {
        return value;
    }

    boolean isSecret() {
        return secret;
    }

    String getDescription() {
        return description;
    }

    void changeValue(String newValue) {
        this.value = newValue;
    }

    /**
     * Marks this setting as holding a credential. One-way on purpose: a value that has been
     * treated as secret should not become publicly readable by a later write, since it may
     * already be a real credential.
     */
    void markSecret() {
        this.secret = true;
    }
}
