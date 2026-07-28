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

    private SettingKeys() {
    }
}
