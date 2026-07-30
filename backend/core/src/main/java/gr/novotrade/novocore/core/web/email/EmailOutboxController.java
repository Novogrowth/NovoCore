package gr.novotrade.novocore.core.web.email;

import gr.novotrade.novocore.core.api.email.EmailAttachmentContent;
import gr.novotrade.novocore.core.api.email.EmailSender;
import gr.novotrade.novocore.core.api.email.EmailStatus;
import gr.novotrade.novocore.core.api.email.QueuedEmailNotFoundException;
import gr.novotrade.novocore.core.api.email.QueuedEmailView;
import gr.novotrade.novocore.core.api.email.SentEmailAttachmentView;
import gr.novotrade.novocore.core.api.security.AccessLevel;
import gr.novotrade.novocore.core.api.security.CurrentUser;
import gr.novotrade.novocore.core.api.security.Section;
import gr.novotrade.novocore.core.web.InvalidRequestException;
import gr.novotrade.novocore.core.web.ListResponse;
import gr.novotrade.novocore.core.web.Requires;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The email outbox: what was sent, what failed, and the files that went with it.
 *
 * <h2>Q44, both halves, answered here</h2>
 *
 * <p><strong>The section half.</strong> {@link Section#EMAIL_OUTBOX} is new and deliberately not
 * folded into {@code SETTINGS}: changing the SMTP password and reading who was emailed about what
 * are different grants. Message bodies are already absent from {@link QueuedEmailView} by design, so
 * what this section governs is recipients, subjects, delivery state and attachments — a
 * customer-correspondence trail.
 *
 * <p><strong>The access-path half.</strong> Downloading a <em>referenced</em> attachment re-checks
 * the caller against the section governing the record the document belongs to, in addition to this
 * one. That check lives in {@code EmailSenderImpl}, not here, so every future caller inherits it —
 * and {@code EmailSender.downloadAttachment} takes the viewer as a required parameter with no
 * unchecked overload beside it, because an unchecked path left available is the path that eventually
 * gets called.
 *
 * <p>The rule it enforces, stated once: <strong>an email having been sent to someone does not change
 * who may see the source document afterwards.</strong> Without it, a role with the outbox but not
 * {@code PURCHASING} could read a supplier invoice's PDF out of the email that sent it — the outbox
 * as a second, weaker way in.
 *
 * <h2>Read-only, with one exception that is stated rather than smuggled</h2>
 *
 * <p>{@code POST /{id}/retry} is the only write. Re-queueing is deliberately manual — a message that
 * exhausted its retries failed for a reason that is probably still true — but without a route the
 * only way to retry one is a database session, which is a worse answer than an audited endpoint.
 */
@RestController
@Requires(section = Section.EMAIL_OUTBOX)
class EmailOutboxController {

    /** Enough to see what is going on without turning a query into a data export. */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final EmailSender email;
    private final CurrentUser currentUser;

    EmailOutboxController(EmailSender email, CurrentUser currentUser) {
        this.email = email;
        this.currentUser = currentUser;
    }

    /**
     * Failed or pending messages.
     *
     * <p>A query over the outbox, not a second copy of it — the outbox row <em>is</em> the state.
     * That is the distinction step 9 drew when it rejected a review queue for rounding differences:
     * a queue is a second copy that goes stale, a filter is not.
     *
     * <p>A message that gave up stays {@code FAILED} and queryable rather than being dropped, which
     * is what makes this list the one somebody actually has to look at.
     */
    @GetMapping(path = "/api/email/outbox", produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<QueuedEmailView> outbox(
            @RequestParam EmailStatus status,
            @RequestParam(required = false) Integer limit) {

        int bounded = Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT);
        return ListResponse.of(switch (status) {
            case FAILED -> email.failed(bounded);
            case PENDING -> email.pending(bounded);
            // SENT is not a filter the service offers, and inventing one here by listing everything
            // and discarding rows would be a query pretending to be a query.
            default -> throw new InvalidRequestException(
                    "status must be FAILED or PENDING; " + status + " is not a supported filter");
        });
    }

    @GetMapping(path = "/api/email/outbox/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    QueuedEmailView message(@PathVariable long id) {
        return email.find(id).orElseThrow(() -> new QueuedEmailNotFoundException(id));
    }

    /**
     * What was attached, whether or not the bytes can still be produced.
     *
     * <p>An entry whose file has since been deleted or pruned reports {@code available == false}
     * with a reason, rather than disappearing from the list: the message really did go out with that
     * file on it, and the history should go on saying so.
     */
    @GetMapping(path = "/api/email/outbox/{id}/attachments",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ListResponse<SentEmailAttachmentView> attachments(@PathVariable long id) {
        return ListResponse.of(email.attachmentsOf(id));
    }

    /**
     * The bytes of one attachment — <strong>the Q44 route</strong>.
     *
     * <p>Two checks, not one: {@code EMAIL_OUTBOX} from the class declaration above, and then the
     * source record's own section inside {@code downloadAttachment}. An inline attachment has no
     * source record and is governed by the first check alone.
     *
     * <p>Served as an attachment download with the filename it was sent under.
     * {@code APPLICATION_OCTET_STREAM} rather than the stored content type is deliberate: this
     * returns a file somebody uploaded, and letting the browser render it inline as, say, HTML would
     * make the outbox a route for serving stored content in the application's own origin.
     */
    @GetMapping(path = "/api/email/attachments/{id}/content")
    ResponseEntity<Resource> attachmentContent(@PathVariable long id) {
        EmailAttachmentContent content =
                email.downloadAttachment(id, currentUser.require().role());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(content.filename())
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.content().length)
                .body(new ByteArrayResource(content.content()));
    }

    /**
     * Puts a failed message back in the queue for one more round of attempts.
     *
     * <p>Nothing re-queues automatically, and that is the point: whatever was wrong — a bad password,
     * a rejected recipient — is still wrong until somebody has done something about it. This route
     * is somebody saying they have.
     */
    @PostMapping(path = "/api/email/outbox/{id}/retry",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Requires(section = Section.EMAIL_OUTBOX, level = AccessLevel.FULL)
    QueuedEmailView retry(@PathVariable long id) {
        return email.retry(id);
    }
}
