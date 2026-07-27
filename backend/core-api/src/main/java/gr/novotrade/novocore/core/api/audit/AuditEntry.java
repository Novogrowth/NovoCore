package gr.novotrade.novocore.core.api.audit;

import java.time.Instant;
import java.util.Map;

/**
 * One recorded action.
 *
 * @param actor the username at the time, stored as text rather than a user reference so the
 *     entry stays truthful and readable after that user is renamed or removed
 * @param entityId null for actions not tied to a single record
 * @param detail never contains a secret value; the settings service records that a credential
 *     changed, never what it changed to
 */
public record AuditEntry(
        long id,
        Instant occurredAt,
        String actor,
        String action,
        String entityType,
        String entityId,
        Map<String, String> detail) {
}
