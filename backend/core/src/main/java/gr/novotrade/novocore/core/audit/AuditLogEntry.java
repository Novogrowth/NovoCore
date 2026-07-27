package gr.novotrade.novocore.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of the audit log.
 *
 * <p>Immutable by construction: there are no setters, and the database refuses {@code UPDATE}
 * and {@code DELETE} on this table by trigger. An audit log that code can quietly rewrite is
 * not evidence of anything.
 *
 * <p>Does not extend {@link gr.novotrade.novocore.core.support.AuditableEntity} — created and
 * updated columns would be meaningless on a record that is written once and never touched, and
 * an "updated by" column on an append-only table would actively mislead.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, updatable = false, length = 100)
    private String actor;

    @Column(name = "action", nullable = false, updatable = false, length = 80)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 80)
    private String entityType;

    /** Null for actions not tied to one record. Text because Setting is keyed by name. */
    @Column(name = "entity_id", updatable = false, length = 120)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", updatable = false)
    private Map<String, String> detail;

    /** For JPA only. */
    protected AuditLogEntry() {
    }

    AuditLogEntry(Instant occurredAt, String actor, String action, String entityType,
            String entityId, Map<String, String> detail) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public Map<String, String> getDetail() {
        return detail == null ? Map.of() : Map.copyOf(detail);
    }
}
