package gr.novotrade.novocore.core.audit;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Core-internal. Reached only through
 * {@link gr.novotrade.novocore.core.api.audit.AuditLogService}; an adapter or module cannot
 * see this type at all, since it is not on their classpath.
 *
 * <p>Read and insert only. No update or delete method exists here, and the database would
 * refuse them regardless.
 */
interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, String entityId, Limit limit);

    List<AuditLogEntry> findAllByOrderByOccurredAtDesc(Limit limit);
}
