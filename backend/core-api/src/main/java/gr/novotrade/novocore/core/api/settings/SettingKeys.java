package gr.novotrade.novocore.core.api.settings;

/**
 * Setting keys used by the core. Constants rather than loose strings, so a typo is a compile
 * error and every reader of a setting can be found by its usages.
 *
 * <p>The SMTP keys were declared here from step 2 with no values behind them. Step 11 seeds
 * every one of them except {@link #SMTP_PASSWORD}, which is <strong>never</strong> seeded by a
 * migration: a migration is a file in git, and a credential in git is in git permanently. It is
 * written into Settings once from {@code NOVOCORE_SMTP_PASSWORD}, the same route the first
 * owner's password takes.
 */
public final class SettingKeys {

    /**
     * Absolute residual at or below which a rounding difference posts automatically to the
     * Rounding account. Larger differences are flagged for review instead (brief §6).
     */
    public static final String LEDGER_ROUNDING_THRESHOLD = "ledger.rounding.threshold";

    /** {@link java.math.RoundingMode} name used wherever a monetary value must be rounded. */
    public static final String LEDGER_ROUNDING_MODE = "ledger.rounding.mode";

    /**
     * Cash payments at or above this amount are hard-blocked — the Greek legal cash limit
     * under N. 5301/2026, where penalties reach double the cash amount (brief §6).
     */
    public static final String CASH_PAYMENT_LIMIT = "cash.payment.limit";

    public static final String SMTP_HOST = "smtp.host";
    public static final String SMTP_PORT = "smtp.port";
    public static final String SMTP_USERNAME = "smtp.username";

    /**
     * The SMTP password. Marked secret, so it is redacted from API responses and never written
     * to the audit log. <strong>Not seeded by any migration</strong> — see the class comment.
     */
    public static final String SMTP_PASSWORD = "smtp.password";

    /**
     * A {@link gr.novotrade.novocore.core.api.email.EmailTransportSecurity} name.
     *
     * <p>Replaces the boolean {@code smtp.start-tls} declared in step 2 and never seeded or
     * read. A boolean has two states and there are three, and the two encrypted ones are not
     * interchangeable: our own server is implicit TLS on 465, and a client configured for
     * STARTTLS against that port hangs rather than failing. Renaming cost nothing because
     * nothing had ever written a value under the old key.
     */
    public static final String SMTP_TRANSPORT_SECURITY = "smtp.transport-security";

    /** The address every message is sent from. An unmonitored mailbox — see {@link #SMTP_REPLY_TO}. */
    public static final String SMTP_FROM_ADDRESS = "smtp.from-address";

    /** Display name shown beside {@link #SMTP_FROM_ADDRESS}, e.g. "Java Jives". */
    public static final String SMTP_FROM_NAME = "smtp.from-name";

    /**
     * Where replies go. Applied to <strong>every</strong> outgoing message and required, not
     * optional.
     *
     * <p>Required because the sending address is an unmonitored send-only mailbox. Treating this
     * as optional would mean a missing setting silently routing every customer reply into a
     * mailbox nobody opens — a failure with no symptom, which is the kind this codebase refuses
     * by default rather than defaults its way through.
     */
    public static final String SMTP_REPLY_TO = "smtp.reply-to";

    /**
     * How many times a message is attempted before it is marked failed and left for a human.
     */
    public static final String EMAIL_MAX_ATTEMPTS = "email.max-attempts";

    /**
     * Delay before the second attempt, in seconds. Each further attempt doubles it, capped by
     * {@link #EMAIL_RETRY_BACKOFF_MAX_SECONDS}.
     */
    public static final String EMAIL_RETRY_BACKOFF_SECONDS = "email.retry.backoff-seconds";

    /** Ceiling on the exponential backoff, in seconds. */
    public static final String EMAIL_RETRY_BACKOFF_MAX_SECONDS = "email.retry.backoff-max-seconds";

    /** How many messages one dispatch cycle claims. Keeps a backlog from monopolising the poller. */
    public static final String EMAIL_DISPATCH_BATCH_SIZE = "email.dispatch.batch-size";

    /**
     * How long a sent message's <em>row</em> is kept — recipients, subject, status, error, and the
     * metadata of whatever it attached. Either {@value #RETENTION_FOREVER} or a number of days.
     *
     * <p>Answered as {@value #RETENTION_FOREVER} (Q43). These rows are cheap: since V21 an emailed
     * document is referenced rather than copied, so a year of sent-email history is rows, not
     * megabytes. The mechanism honours a number if one is ever set, because a setting that only
     * accepts one value is not a setting.
     */
    public static final String EMAIL_RETENTION_MESSAGE_DAYS = "email.retention.message-days";

    /**
     * How long the <em>inline</em> copy of a generated attachment is kept, in days, before its
     * bytes are dropped and the history entry reports the file as no longer available.
     *
     * <p>Answered as 90 (Q43). This is the only unbounded growth the outbox has: a Purchase Order
     * PDF or a report exists nowhere else, and was never meant to be permanent.
     *
     * <p><strong>Referenced attachments are never touched by this.</strong> Their bytes live in
     * {@code AttachmentService} under its own lifecycle, and the outbox holds a reference, not a
     * copy — deleting them here would delete somebody else's document. The pruning statement is
     * restricted to {@code content_source = 'INLINE'} for exactly that reason.
     */
    public static final String EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS =
            "email.retention.inline-attachment-days";

    /** The value that means "never prune" — spelled out rather than encoded as blank or zero. */
    public static final String RETENTION_FOREVER = "FOREVER";

    private SettingKeys() {
    }
}
