package gr.novotrade.novocore.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactions;

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

    // -----------------------------------------------------------------------------------------
    // The property the whole REQUIRES_NEW arrangement exists for
    // -----------------------------------------------------------------------------------------

    /**
     * An audit entry must survive the rollback of the operation it describes.
     *
     * <p>This is the reason {@code record} is {@code @Transactional(propagation = REQUIRES_NEW)},
     * and until now nothing tested it — which is exactly how it came to be silently untrue for
     * two of the three overloads from the day they were written. They were unannotated and called
     * the annotated one on {@code this}, so Spring's proxy was bypassed, the new transaction was
     * never started, and every entry written through them was rolled back together with the very
     * operation it was recording. A rejected journal entry or a refused permission is precisely
     * what you most want recorded, and precisely what was being lost.
     *
     * <p><strong>All three overloads are exercised, deliberately.</strong> The structural ArchUnit
     * rule that found the bug cannot protect this: removing the annotation from an overload and
     * calling the private {@code write} directly would be perfectly clean self-invocation-wise and
     * would reintroduce the defect in full. Only these assertions would notice.
     */
    @Test
    @DisplayName("an entry survives the rollback of the operation it records — all three overloads")
    void entriesSurviveTheRollbackOfTheirOperation() {
        String entityId = "rolled-back-" + java.util.UUID.randomUUID();

        assertThatIllegalStateException().isThrownBy(() ->
                transactions.executeWithoutResult(tx -> {
                    // The four-argument overload was always correct.
                    auditLog.record("test.rollback.detailed", "Probe", entityId,
                            Map.of("attempted", "yes"));
                    // These two were not.
                    auditLog.record("test.rollback.plain", "Probe", entityId);
                    auditLog.recordSystemAction("test.rollback.system",
                            Map.of("probe", entityId));

                    throw new IllegalStateException("the operation this was recording failed");
                }));

        assertThat(auditLog.findForEntity("Probe", entityId, 10))
                .as("all three entries must outlive the transaction that was rolled back")
                .extracting(AuditEntry::action)
                .containsExactlyInAnyOrder("test.rollback.detailed", "test.rollback.plain");

        assertThat(auditLog.findRecent(200))
                .as("recordSystemAction is the third route in, and was broken in the same way")
                .anyMatch(entry -> entry.action().equals("test.rollback.system")
                        && entityId.equals(entry.detail().get("probe")));
    }

    @Test
    @DisplayName("the entry is committed before the caller's transaction ends, not merely after")
    void theEntryIsVisibleWhileTheCallerIsStillOpen() {
        // The mechanism, not just the outcome. A separate transaction that has already committed
        // is readable from outside the caller's — which is what makes surviving a rollback
        // possible at all, and distinguishes REQUIRES_NEW from "we happened not to roll back".
        String entityId = "committed-early-" + java.util.UUID.randomUUID();

        transactions.executeWithoutResult(tx -> {
            auditLog.record("test.visible-early", "Probe", entityId);

            Long visible = jdbcOutsideThisTransaction(
                    "SELECT count(*) FROM audit_log WHERE entity_id = ?", entityId);
            assertThat(visible)
                    .as("the audit entry has already been committed by its own transaction")
                    .isEqualTo(1L);
        });
    }

    /**
     * Counts through a connection of its own, so the read cannot see the caller's uncommitted work.
     *
     * <p>A {@code JdbcTemplate} inside the test's transaction would join it and see everything,
     * which would make the assertion above pass whether or not {@code REQUIRES_NEW} was applied.
     */
    private Long jdbcOutsideThisTransaction(String sql, String parameter) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (var results = statement.executeQuery()) {
                results.next();
                return results.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
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
