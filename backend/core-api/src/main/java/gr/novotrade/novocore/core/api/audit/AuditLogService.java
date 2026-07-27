package gr.novotrade.novocore.core.api.audit;

import java.util.List;
import java.util.Map;

/**
 * Append-only record of who did what.
 *
 * <p>Distinct from the created/updated columns on a record: those say who last touched it,
 * this says what happened over time and survives the record changing again. It exists because
 * the permissions model means different people can do different things, and because several
 * core decisions are explicitly human confirmations — brief §6 and {@code CLAUDE.md} rule 7
 * require that ambiguity be suggested and confirmed rather than auto-resolved, and a
 * confirmation nobody can trace is not much of a control.
 *
 * <p>There is no update or delete, here or in the database, which refuses both by trigger.
 * Correct a mistaken entry by appending a further one.
 */
public interface AuditLogService {

    /** Records an action against a specific record. */
    void record(String action, String entityType, String entityId, Map<String, String> detail);

    /** Records an action with no detail. */
    void record(String action, String entityType, String entityId);

    /** Records an action not tied to a single record, such as a login or a backup run. */
    void recordSystemAction(String action, Map<String, String> detail);

    /** Most recent entries for one record, newest first. */
    List<AuditEntry> findForEntity(String entityType, String entityId, int limit);

    /** Most recent entries across everything, newest first. */
    List<AuditEntry> findRecent(int limit);
}
