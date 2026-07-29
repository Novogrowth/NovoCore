package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.email.EmailStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Drops the inline copies of generated attachments on messages sent before {@code cutoff} —
     * Q43's 90 days.
     *
     * <p>Two restrictions carry the whole policy, and neither is incidental:
     *
     * <ul>
     *   <li><strong>{@code content_source = 'INLINE'}.</strong> A referenced attachment's bytes
     *       belong to {@code AttachmentService}; nulling {@code content} on one would achieve
     *       nothing (it is already null) but the intent matters — this statement must never be
     *       widened into something that deletes the referenced document itself.
     *   <li><strong>{@code status = 'SENT'}.</strong> A PENDING message still needs its bytes, and
     *       a FAILED one keeps them because retrying it is the reason it was kept at all.
     * </ul>
     *
     * <p>{@code content IS NOT NULL} keeps the statement idempotent, so a second run the next day
     * reports zero rather than re-writing rows it already cleared.
     *
     * <p>A bulk UPDATE rather than loading entities: this must not read a year of PDFs into memory
     * to set them to null, which is the one thing it exists to stop happening.
     */
    @Modifying
    @Query(value = """
            UPDATE email_outbox_attachment a
               SET content = NULL, updated_at = now(), updated_by = 'system'
              FROM email_outbox m
             WHERE a.email_outbox_id = m.id
               AND a.content_source  = 'INLINE'
               AND a.content IS NOT NULL
               AND m.status = 'SENT'
               AND m.sent_at < :cutoff
            """, nativeQuery = true)
    int pruneInlineAttachmentsSentBefore(@Param("cutoff") Instant cutoff);

    /**
     * Removes sent messages older than {@code cutoff} entirely, attachment rows following by
     * {@code ON DELETE CASCADE}.
     *
     * <p>Never runs under the answer Q43 gave — message retention is {@code FOREVER}. It exists so
     * the setting is real rather than decorative, and so that changing it later is a settings edit
     * and not a code change.
     *
     * <p>The one deletion in this schema besides {@code OpenItemAllocation}'s, and it is the
     * legitimate kind: {@code CLAUDE.md}'s no-delete stance governs records people rely on, and a
     * retention policy somebody set deliberately is the opposite of an accidental loss. SENT only —
     * a FAILED message is the list a human is supposed to be working through.
     */
    @Modifying
    @Query(value = """
            DELETE FROM email_outbox
             WHERE status = 'SENT'
               AND sent_at < :cutoff
            """, nativeQuery = true)
    int deleteSentBefore(@Param("cutoff") Instant cutoff);

    List<QueuedEmail> findByStatusOrderByCreatedAtDesc(EmailStatus status, Limit limit);

    List<QueuedEmail> findByStatusOrderByNextAttemptAtAscIdAsc(EmailStatus status, Limit limit);

    long countByStatus(EmailStatus status);
}
