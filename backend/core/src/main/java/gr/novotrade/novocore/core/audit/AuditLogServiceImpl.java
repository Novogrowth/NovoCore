package gr.novotrade.novocore.core.audit;

import gr.novotrade.novocore.core.api.audit.AuditEntry;
import gr.novotrade.novocore.core.api.audit.AuditLogService;
import gr.novotrade.novocore.core.support.CoreInfrastructureConfiguration;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditLogServiceImpl implements AuditLogService {

    /** Guards against an unbounded query becoming an accidental full-table read. */
    private static final int MAX_LIMIT = 500;

    private final AuditLogRepository repository;
    private final AuditorAware<String> auditorAware;
    private final Clock clock;

    AuditLogServiceImpl(AuditLogRepository repository, AuditorAware<String> auditorAware,
            Clock clock) {
        this.repository = repository;
        this.auditorAware = auditorAware;
        this.clock = clock;
    }

    /**
     * Written in its own transaction.
     *
     * <p>{@code REQUIRES_NEW} is the point: an audit entry must survive the rollback of the
     * operation it describes. A rejected journal entry or a refused permission is exactly what
     * you want recorded, and joining the caller's transaction would discard the evidence along
     * with the attempt.
     *
     * <p><strong>Every public overload carries the annotation, and none of them calls another.</strong>
     * They delegate to the private {@link #write} instead, and that is a correctness fix rather
     * than tidiness. The two convenience overloads below used to be unannotated and to call this
     * method directly — a self-invocation, which never goes through the proxy, so their
     * {@code REQUIRES_NEW} was silently not applied and entries written through them joined the
     * caller's transaction and <em>were rolled back with the very operation they were recording</em>.
     * Exactly the failure this annotation exists to prevent, in the one place that is supposed to
     * be the record of last resort. Found by the ArchUnit self-invocation rule.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId,
            Map<String, String> detail) {
        write(action, entityType, entityId, detail);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId) {
        write(action, entityType, entityId, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystemAction(String action, Map<String, String> detail) {
        write(action, "System", null, detail);
    }

    /**
     * The one place an entry is actually written.
     *
     * <p>Private and unannotated on purpose: the transaction is opened by whichever public method
     * the caller came through, and a helper that carried its own annotation would be back to
     * promising something a direct call cannot deliver.
     */
    private void write(String action, String entityType, String entityId,
            Map<String, String> detail) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
        repository.save(new AuditLogEntry(
                clock.instant(),
                currentActor(),
                action,
                entityType,
                entityId,
                detail == null || detail.isEmpty() ? null : Map.copyOf(detail)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> findForEntity(String entityType, String entityId, int limit) {
        Objects.requireNonNull(entityType, "entityType");
        return repository
                .findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        entityType, entityId, Limit.of(boundedLimit(limit)))
                .stream()
                .map(AuditLogServiceImpl::toEntry)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> findRecent(int limit) {
        return repository.findAllByOrderByOccurredAtDesc(Limit.of(boundedLimit(limit))).stream()
                .map(AuditLogServiceImpl::toEntry)
                .toList();
    }

    private String currentActor() {
        return Optional.ofNullable(auditorAware.getCurrentAuditor())
                .flatMap(auditor -> auditor)
                .orElse(CoreInfrastructureConfiguration.SYSTEM_ACTOR);
    }

    private static int boundedLimit(int requested) {
        if (requested <= 0) {
            throw new IllegalArgumentException("limit must be positive, got " + requested);
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static AuditEntry toEntry(AuditLogEntry entity) {
        return new AuditEntry(
                entity.getId(),
                entity.getOccurredAt(),
                entity.getActor(),
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getDetail());
    }
}
