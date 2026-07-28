package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.email.EmailStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface QueuedEmailRepository extends JpaRepository<QueuedEmail, Long> {

    /**
     * Ids of the messages due to be attempted, oldest first, locked for this transaction.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} rather than a plain select. NovoCore runs one instance
     * with one scheduler thread and {@code fixedDelay}, so cycles cannot overlap and this is
     * currently belt and braces — but the cost is a clause, and the failure it prevents is
     * sending the same email twice, which is exactly the sort of thing that surfaces the first
     * time somebody runs a second instance during a migration.
     *
     * <p>Ids rather than entities, because a lock must not be held while attachment bytes are
     * loaded — and because the claim transaction is deliberately short: it commits before any
     * SMTP conversation begins, so no lock is held across the network.
     */
    @Query(value = """
            SELECT id
              FROM email_outbox
             WHERE status = 'PENDING'
               AND next_attempt_at <= :now
             ORDER BY next_attempt_at, id
             LIMIT :batchSize
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> claimDueIds(@Param("now") Instant now, @Param("batchSize") int batchSize);

    List<QueuedEmail> findByStatusOrderByCreatedAtDesc(EmailStatus status, Limit limit);

    List<QueuedEmail> findByStatusOrderByNextAttemptAtAscIdAsc(EmailStatus status, Limit limit);

    long countByStatus(EmailStatus status);
}
