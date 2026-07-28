package gr.novotrade.novocore.core.api.email;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The state of one message in the outbox.
 *
 * <p><strong>The body is deliberately not here.</strong> The outbox is operational status —
 * what is stuck, what failed, and why — not an archive of correspondence. Exposing bodies would
 * make an email containing a customer's statement readable by anyone who can see the queue, and
 * whoever is unblocking a stuck message does not need to read it to do that.
 *
 * <p>{@code sentFrom} and {@code replyTo} are what was actually used on the attempt, not what
 * Settings says now. A message sent before someone corrected the sender address should still say
 * what it went out as.
 */
public record QueuedEmailView(
        long id,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        int attachmentCount,
        EmailStatus status,
        int attempts,
        int maxAttempts,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant sentAt,
        String lastError,
        String sentFrom,
        String replyTo,
        Instant createdAt,
        String createdBy) {

    /** When this message next becomes due. Empty once it is {@link EmailStatus#SENT} or failed. */
    public Optional<Instant> nextAttemptAtIfAny() {
        return Optional.ofNullable(nextAttemptAt);
    }

    /** Empty until something has gone wrong at least once. */
    public Optional<String> lastErrorIfAny() {
        return Optional.ofNullable(lastError);
    }

    public Optional<Instant> sentAtIfAny() {
        return Optional.ofNullable(sentAt);
    }

    /** Empty until the first attempt, since the sender is resolved at send time, not at queue time. */
    public Optional<String> sentFromIfAny() {
        return Optional.ofNullable(sentFrom);
    }

    public Optional<String> replyToIfAny() {
        return Optional.ofNullable(replyTo);
    }

    /** True when this message has given up and needs a human — see {@link EmailStatus#FAILED}. */
    public boolean needsAttention() {
        return status == EmailStatus.FAILED;
    }
}
