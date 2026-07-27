package gr.novotrade.novocore.core.support;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Who created a record and who last changed it. Extended by core entities holding
 * user-modifiable state.
 *
 * <p>Populated by Spring Data JPA auditing rather than a database trigger, deliberately: with
 * one writer there is no chance of Java and a trigger disagreeing about the value. The columns
 * are still {@code NOT NULL} with defaults in the schema, so a manual {@code psql} session
 * cannot leave them empty either.
 *
 * <p>The actor is a username string, not a reference to the user record. That keeps history
 * readable after a user is renamed or removed, which a foreign key would either prevent or
 * leave dangling. The same reasoning applies to the audit log.
 *
 * <p>This is not a substitute for the audit log. These columns say who touched a record last;
 * they overwrite themselves on the next change. The audit log is what retains the sequence.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
