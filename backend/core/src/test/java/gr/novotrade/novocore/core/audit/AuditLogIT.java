package gr.novotrade.novocore.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import gr.novotrade.novocore.core.AbstractCoreIntegrationTest;
import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogIT extends AbstractCoreIntegrationTest {

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("records an entry with actor, action and detail")
    void recordsAnEntry() {
        auditLog.record("test.action", "TestEntity", "entity-1",
                Map.of("field", "value", "another", "thing"));

        List<AuditEntry> entries = auditLog.findForEntity("TestEntity", "entity-1", 10);

        assertThat(entries).hasSize(1);
        AuditEntry entry = entries.getFirst();
        assertThat(entry.action()).isEqualTo("test.action");
        assertThat(entry.actor()).isEqualTo("system");
        assertThat(entry.occurredAt()).isNotNull();
        assertThat(entry.detail()).containsEntry("field", "value").containsEntry("another", "thing");
    }

    @Test
    @DisplayName("records an entry with no detail")
    void recordsWithoutDetail() {
        auditLog.record("test.no-detail", "TestEntity", "entity-2");

        AuditEntry entry = auditLog.findForEntity("TestEntity", "entity-2", 10).getFirst();
        assertThat(entry.detail()).isEmpty();
    }

    @Test
    @DisplayName("records a system action not tied to a record")
    void recordsSystemAction() {
        auditLog.recordSystemAction("test.system-action", Map.of("outcome", "ok"));

        assertThat(auditLog.findRecent(50))
                .anyMatch(entry -> entry.action().equals("test.system-action")
                        && entry.entityType().equals("System")
                        && entry.entityId() == null);
    }

    @Test
    @DisplayName("returns newest first")
    void returnsNewestFirst() {
        auditLog.record("test.first", "OrderedEntity", "entity-3");
        auditLog.record("test.second", "OrderedEntity", "entity-3");
        auditLog.record("test.third", "OrderedEntity", "entity-3");

        assertThat(auditLog.findForEntity("OrderedEntity", "entity-3", 10))
                .extracting(AuditEntry::action)
                .containsExactly("test.third", "test.second", "test.first");
    }

    @Test
    @DisplayName("a non-positive limit is rejected rather than read as unlimited")
    void nonPositiveLimitRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> auditLog.findRecent(0))
                .withMessageContaining("positive");
    }

    @Test
    @DisplayName("PostgreSQL refuses to UPDATE an audit entry, not merely the service layer")
    void databaseRefusesUpdate() throws SQLException {
        // The guarantee has to hold against a psql session, not only against code paths that
        // happen to go through the service. Without the trigger, "append-only" would just mean
        // "we did not write an update method yet".
        auditLog.record("test.immutable", "ImmutableEntity", "entity-4");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> jdbc.update(
                        "UPDATE audit_log SET action = 'tampered' WHERE entity_id = 'entity-4'"))
                .withMessageContaining("append-only");

        assertThat(auditLog.findForEntity("ImmutableEntity", "entity-4", 10).getFirst().action())
                .isEqualTo("test.immutable");
    }

    @Test
    @DisplayName("PostgreSQL refuses to DELETE an audit entry")
    void databaseRefusesDelete() {
        auditLog.record("test.undeletable", "UndeletableEntity", "entity-5");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> jdbc.update(
                        "DELETE FROM audit_log WHERE entity_id = 'entity-5'"))
                .withMessageContaining("append-only");

        assertThat(auditLog.findForEntity("UndeletableEntity", "entity-5", 10)).hasSize(1);
    }
}
