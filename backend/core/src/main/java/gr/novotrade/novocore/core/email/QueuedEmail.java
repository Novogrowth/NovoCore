package gr.novotrade.novocore.core.email;

import gr.novotrade.novocore.core.api.attachment.AttachmentMetadata;
import gr.novotrade.novocore.core.api.attachment.AttachmentService;
import gr.novotrade.novocore.core.api.email.EmailAttachment;
import gr.novotrade.novocore.core.api.email.EmailAttachmentContent;
import gr.novotrade.novocore.core.api.email.EmailBodyFormat;
import gr.novotrade.novocore.core.api.email.EmailMessage;
import gr.novotrade.novocore.core.api.email.EmailStatus;
import gr.novotrade.novocore.core.api.email.QueuedEmailView;
import gr.novotrade.novocore.core.api.email.SentEmailAttachmentView;
import gr.novotrade.novocore.core.support.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One message in the outbox.
 *
 * <p>The state machine lives here rather than in the dispatcher, so the rules about what may
 * follow what are stated once and the database's CHECK constraints have a single counterpart in
 * Java to agree with — the same arrangement as {@code JournalSource.isAmendable} and
 * {@code journal_source_is_amendable}.
 */
@Entity
@Table(name = "email_outbox")
class QueuedEmail extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_addresses", nullable = false)
    private String[] toAddresses;

    @Column(name = "cc_addresses", nullable = false)
    private String[] ccAddresses;

    @Column(name = "bcc_addresses", nullable = false)
    private String[] bccAddresses;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_format", nullable = false, length = 20)
    private EmailBodyFormat bodyFormat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_from", length = 320)
    private String sentFrom;

    @Column(name = "reply_to", length = 320)
    private String replyTo;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attachmentOrder ASC")
    private List<QueuedEmailAttachment> attachments = new ArrayList<>();

    protected QueuedEmail() {
    }

    /**
     * @param storedDocuments metadata for every {@code EmailAttachment.stored} attachment in the
     *     message, keyed by document id, read by the caller before this is constructed. Passed in
     *     rather than looked up here so the entity calls no service: resolving the references is
     *     part of validating the caller's request, and it belongs where that request is refused.
     */
    QueuedEmail(EmailMessage message, int maxAttempts, Instant dueAt,
            Map<Long, AttachmentMetadata> storedDocuments) {
        this.toAddresses = message.to().toArray(String[]::new);
        this.ccAddresses = message.cc().toArray(String[]::new);
        this.bccAddresses = message.bcc().toArray(String[]::new);
        this.subject = message.subject();
        this.body = message.body();
        this.bodyFormat = message.format();
        this.status = EmailStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        // Due immediately. The dispatcher's next cycle picks it up; nothing waits on it here.
        this.nextAttemptAt = dueAt;

        int order = 0;
        for (EmailAttachment attachment : message.attachments()) {
            AttachmentMetadata document = attachment.isStored()
                    ? storedDocuments.get(attachment.storedAttachmentId())
                    : null;
            this.attachments.add(
                    new QueuedEmailAttachment(this, attachment, document, order++));
        }
    }

    /**
     * Claims this message for an attempt, and is committed <em>before</em> the SMTP conversation
     * starts.
     *
     * <p>{@code retryAfter} moves the message's due time forward straight away, so a process
     * killed mid-send leaves a row that becomes due again later. The two alternatives are both
     * worse: leaving the due time alone means the next cycle picks it up immediately and keeps
     * doing so, and clearing it means a crash loses the message silently.
     *
     * <p>The consequence, stated rather than hidden: a crash between the SMTP server accepting a
     * message and this row being marked sent produces a duplicate email on the retry. That is the
     * right direction to fail — a customer receiving an order confirmation twice is a nuisance,
     * receiving it never is a lost order — and it is the standard outbox trade-off. Avoiding it
     * would mean holding a database transaction open across a network conversation.
     */
    void attemptStarting(Instant now, String sentFrom, String replyTo, Duration retryAfter) {
        this.attempts++;
        this.lastAttemptAt = now;
        this.sentFrom = sentFrom;
        this.replyTo = replyTo;
        this.nextAttemptAt = now.plus(retryAfter);
    }

    void markSent(Instant now) {
        this.status = EmailStatus.SENT;
        this.sentAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /**
     * Records a failed attempt. The message stays {@link EmailStatus#PENDING} with the due time
     * {@link #attemptStarting} already set, unless it has run out of road.
     *
     * @param permanent true when retrying cannot help — a recipient the server rejected outright.
     *     Retrying a malformed address four more times produces four more identical rejections
     *     and delays the moment anyone notices.
     */
    void markAttemptFailed(String error, boolean permanent) {
        this.lastError = truncate(error);
        if (permanent || this.attempts >= this.maxAttempts) {
            this.status = EmailStatus.FAILED;
            this.nextAttemptAt = null;
        }
    }

    /**
     * Puts a failed message back in the queue, giving it a fresh allowance of attempts.
     *
     * <p>Resets the counter rather than adding one attempt: somebody has retried this
     * deliberately, presumably having fixed whatever was wrong, and a message that gets one
     * grudging attempt per intervention makes the fix hard to confirm.
     */
    void requeue(Instant now, int maxAttempts) {
        if (this.status != EmailStatus.FAILED) {
            throw new IllegalStateException(
                    "Message %d is %s, not FAILED — only a failed message can be retried."
                            .formatted(id, status));
        }
        this.status = EmailStatus.PENDING;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = now;
        // last_attempt_at is cleared to satisfy the attempted-iff-last-attempt constraint, and
        // because the attempt count it belonged to has gone. last_error is kept: it is the
        // reason somebody is retrying, and losing it on the way in would be unhelpful.
        this.lastAttemptAt = null;
        this.sentFrom = null;
        this.replyTo = null;
    }

    boolean isDue(Instant now) {
        return status == EmailStatus.PENDING && nextAttemptAt != null
                && !nextAttemptAt.isAfter(now);
    }

    Long getId() {
        return id;
    }

    List<String> getTo() {
        return List.of(toAddresses);
    }

    List<String> getCc() {
        return List.of(ccAddresses);
    }

    List<String> getBcc() {
        return List.of(bccAddresses);
    }

    String getSubject() {
        return subject;
    }

    String getBody() {
        return body;
    }

    EmailBodyFormat getBodyFormat() {
        return bodyFormat;
    }

    EmailStatus getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }

    int getMaxAttempts() {
        return maxAttempts;
    }

    List<QueuedEmailAttachment> getAttachments() {
        return attachments;
    }

    /**
     * What the sent-email history shows for this message's attachments — referenced and inline
     * alike, in the order they were sent, including any whose file is no longer available.
     */
    List<SentEmailAttachmentView> attachmentViews() {
        return attachments.stream().map(QueuedEmailAttachment::toView).toList();
    }

    /**
     * Every attachment's bytes, resolved from wherever they live, for the message going out.
     *
     * <p>Throws if any of them cannot be produced, and that is the intended behaviour: a message
     * whose attachment has vanished between queueing and sending fails visibly rather than
     * arriving with the file quietly missing, which is the one failure a recipient could not
     * detect. The dispatcher's claim loop turns it into a permanently failed message naming the
     * file.
     */
    List<EmailAttachmentContent> resolveAttachments(AttachmentService documents) {
        return attachments.stream()
                .map(attachment -> attachment.resolveContent(documents))
                .toList();
    }

    QueuedEmailView toView() {
        return new QueuedEmailView(
                id,
                getTo(),
                getCc(),
                getBcc(),
                subject,
                attachments.size(),
                status,
                attempts,
                maxAttempts,
                nextAttemptAt,
                lastAttemptAt,
                sentAt,
                lastError,
                sentFrom,
                replyTo,
                getCreatedAt(),
                getCreatedBy());
    }

    /**
     * Keeps a stack-trace-sized failure from becoming the largest thing in the table. The first
     * line of a mail failure is the part that says what went wrong; the rest is Jakarta Mail's
     * own frames.
     */
    private static String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "(no message)";
        }
        return error.length() <= 2000 ? error : error.substring(0, 2000) + "…";
    }
}
