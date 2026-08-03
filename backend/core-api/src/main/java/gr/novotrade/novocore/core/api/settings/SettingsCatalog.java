package gr.novotrade.novocore.core.api.settings;

import java.util.Arrays;
import java.util.Optional;

/**
 * The settings the API exposes — <strong>an allowlist, not a view over the table</strong>.
 *
 * <h2>Why the Settings API is not a key/value passthrough</h2>
 *
 * <p>{@code SettingsService} is deliberately untyped: {@code listRedacted()} returns every row and
 * {@code put(key, value)} accepts any key and any string. That is correct for a store the core reads
 * from, and it is not something to put a route in front of. A generic
 * {@code PUT /api/settings/{key}} over that service would let a caller
 *
 * <ul>
 *   <li>write {@code backup.drive.primary.refresh-token} — a credential this API must never touch;
 *   <li>invent brand-new keys, bounded only by the key-format CHECK;
 *   <li>set {@code ledger.rounding.threshold} to {@code "abc"}, which stores fine and then breaks
 *       the next document anybody records.
 * </ul>
 *
 * <p>So the route binds {@code {key}} to <strong>this enum</strong> rather than to a string. A key
 * that is not a constant here has no route at all: Spring refuses it before any of our code runs, the
 * exclusion is structural rather than a check somebody has to remember, and the exposed set appears
 * in the OpenAPI document. Same mechanism as the sort parameters, for the same reason.
 *
 * <h2>⚠️ The whole {@code backup.*} namespace is excluded, and that is deliberate</h2>
 *
 * <p>Not only the four credentials. Per-key exclusion would have to individually catch
 * {@code backup.drive.primary.folder-id} and {@code .client-id}, which are <strong>not</strong>
 * flagged secret in the table and would therefore be returned in the clear by any "redacted" listing
 * — and would be missed again by whoever adds a third destination. A namespace rule is one line, is
 * assertable ({@code SettingsCatalogTest}), and cannot rot.
 *
 * <p>It also keeps {@code backup.local-directory} and {@code backup.restore-check.database} off the
 * surface: a filesystem path and a database name, both consumed by a process that shells out to
 * {@code pg_restore}, are not values to make writable over HTTP in the same step that first exposes
 * Settings at all. The cost is stated rather than hidden — an operator cannot read the retention
 * policy over HTTP. Backup administration has no routes today either; this leaves it where it is
 * rather than half-exposing it.
 *
 * <p>The backup <em>encryption key</em> needs no exclusion: it is an environment variable with no row
 * in the table at all, because the table is inside the dump it decrypts (ADR 0013). A test asserts
 * that absence rather than trusting it.
 */
public enum SettingsCatalog {

    // ---------------------------------------------------------------------------------------
    // Ledger
    // ---------------------------------------------------------------------------------------

    /** Brief §6's configurable rounding threshold — the reason Settings exists at all. */
    LEDGER_ROUNDING_THRESHOLD(
            SettingKeys.LEDGER_ROUNDING_THRESHOLD, SettingType.EUR_AMOUNT, Access.READ_WRITE),

    /**
     * ⚠️ Changing this changes how future amounts round, and nothing already posted.
     *
     * <p>Worth knowing that a lot's carrying value deliberately does <em>not</em> follow it
     * (ADR 0015): a lot's value at two moments has to be measured the same way, and a setting
     * somebody can change cannot promise that. So this governs less than its name suggests.
     */
    LEDGER_ROUNDING_MODE(
            SettingKeys.LEDGER_ROUNDING_MODE, SettingType.ROUNDING_MODE, Access.READ_WRITE),

    /**
     * <strong>Readable, never writable over HTTP.</strong>
     *
     * <p>Not because the value is technically immutable — it is an ordinary row and SQL can change it
     * — but because it is a <em>statutory</em> limit (€500, N. 5301/2026, penalties to double the cash
     * amount). This is the one refusal in the design that offers no confirmation path, because the
     * confirmation nobody can give is legality. A screen that raised it would make breaking the law a
     * two-click operation with an audit entry that reads like configuration.
     *
     * <p>The read is here for the <em>administrator reviewing configuration</em>, not for the
     * operator who hit the refusal — they already have the figure, interpolated into the 422 and into
     * the invoice preview, with no {@code SETTINGS} grant needed. Omitting it entirely was considered
     * and rejected: a settings screen that silently lacks a setting that exists leaves an admin no way
     * to learn its value short of reading source.
     */
    CASH_PAYMENT_LIMIT(SettingKeys.CASH_PAYMENT_LIMIT, SettingType.EUR_AMOUNT, Access.READ_ONLY),

    // ---------------------------------------------------------------------------------------
    // Attachments
    // ---------------------------------------------------------------------------------------

    /** ⚠️ Raising this raises the size of every database backup — attachment content is in the dump. */
    ATTACHMENT_MAX_SIZE_BYTES(
            "attachment.max-size-bytes", SettingType.POSITIVE_INTEGER, Access.READ_WRITE),

    // ---------------------------------------------------------------------------------------
    // Email — the configuration CLAUDE.md requires to live here rather than inside a module
    // ---------------------------------------------------------------------------------------

    SMTP_HOST(SettingKeys.SMTP_HOST, SettingType.TEXT, Access.READ_WRITE),
    SMTP_PORT(SettingKeys.SMTP_PORT, SettingType.POSITIVE_INTEGER, Access.READ_WRITE),
    SMTP_USERNAME(SettingKeys.SMTP_USERNAME, SettingType.TEXT, Access.READ_WRITE),

    /**
     * <strong>Write-only.</strong> Settable, never returned — not even redacted-with-a-length.
     *
     * <p>It is the one setting no migration seeds, because a migration is a file in git and a
     * credential in git is in git permanently. It arrives once by environment variable; this is how it
     * is rotated afterwards without another one-off deployment.
     */
    SMTP_PASSWORD(SettingKeys.SMTP_PASSWORD, SettingType.TEXT, Access.WRITE_ONLY),

    /**
     * ⚠️ A boolean would not do: implicit TLS on 465 and STARTTLS on 587 are not interchangeable, and
     * a STARTTLS client pointed at 465 <em>hangs</em> rather than failing.
     */
    SMTP_TRANSPORT_SECURITY(
            SettingKeys.SMTP_TRANSPORT_SECURITY, SettingType.TRANSPORT_SECURITY, Access.READ_WRITE),

    SMTP_FROM_ADDRESS(SettingKeys.SMTP_FROM_ADDRESS, SettingType.TEXT, Access.READ_WRITE),
    SMTP_FROM_NAME(SettingKeys.SMTP_FROM_NAME, SettingType.TEXT, Access.READ_WRITE),

    /**
     * ⚠️ Required, not optional — the sending address is an unmonitored send-only mailbox, so a blank
     * here routes every customer reply into a mailbox nobody opens. A failure with no symptom.
     */
    SMTP_REPLY_TO(SettingKeys.SMTP_REPLY_TO, SettingType.TEXT, Access.READ_WRITE),

    EMAIL_MAX_ATTEMPTS(
            SettingKeys.EMAIL_MAX_ATTEMPTS, SettingType.POSITIVE_INTEGER, Access.READ_WRITE),
    EMAIL_RETRY_BACKOFF_SECONDS(
            SettingKeys.EMAIL_RETRY_BACKOFF_SECONDS, SettingType.POSITIVE_INTEGER,
            Access.READ_WRITE),
    EMAIL_RETRY_BACKOFF_MAX_SECONDS(
            SettingKeys.EMAIL_RETRY_BACKOFF_MAX_SECONDS, SettingType.POSITIVE_INTEGER,
            Access.READ_WRITE),
    EMAIL_DISPATCH_BATCH_SIZE(
            SettingKeys.EMAIL_DISPATCH_BATCH_SIZE, SettingType.POSITIVE_INTEGER, Access.READ_WRITE),

    /** Q43: {@code FOREVER}. Cheap since V21 — a year of sent-mail history is rows, not megabytes. */
    EMAIL_RETENTION_MESSAGE_DAYS(
            SettingKeys.EMAIL_RETENTION_MESSAGE_DAYS, SettingType.RETENTION_DAYS,
            Access.READ_WRITE),

    /** Q43: 90 days. The only unbounded growth the outbox has — generated PDFs that exist nowhere else. */
    EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS(
            SettingKeys.EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS, SettingType.RETENTION_DAYS,
            Access.READ_WRITE),

    /** The namespace no catalogue entry may name — see the class javadoc. */
    /**
     * The establishment (branch) number this installation issues documents from — R1a.
     *
     * <p>{@code READ_WRITE} and typed {@code TEXT} rather than {@code POSITIVE_INTEGER}: head
     * office is {@code "0"}, which a positive-integer type would refuse, and a branch number is an
     * identifier rather than a quantity — nothing computes with it.
     *
     * <p>⚠️ Editable on purpose. An establishment number that lives in a Java constant cannot be
     * corrected by the person who knows it is wrong.
     */
    COMPANY_BRANCH_NUMBER(
            SettingKeys.COMPANY_BRANCH_NUMBER, SettingType.TEXT, Access.READ_WRITE),

    /**
     * Which AADE specification version the seeded statutory codifications correspond to — R1a.
     *
     * <p>⚠️ {@code READ_ONLY}, and that is the point of it. The value describes what
     * <strong>Flyway seeded</strong>; editing it through a form would make it claim a version the
     * rows do not correspond to, which is worse than having no marker at all. It moves when a
     * migration moves the seed.
     */
    AADE_SPEC_VERSION(SettingKeys.AADE_SPEC_VERSION, SettingType.TEXT, Access.READ_ONLY),

    ;

    public static final String EXCLUDED_NAMESPACE = "backup.";

    private final String key;
    private final SettingType type;
    private final Access access;

    SettingsCatalog(String key, SettingType type, Access access) {
        this.key = key;
        this.type = type;
        this.access = access;
    }

    public String key() {
        return key;
    }

    public SettingType type() {
        return type;
    }

    public boolean isWritable() {
        return access != Access.READ_ONLY;
    }

    /** True when the value is settable but never returned — {@link #SMTP_PASSWORD}. */
    public boolean isWriteOnly() {
        return access == Access.WRITE_ONLY;
    }

    /**
     * Whether this value is a credential, and therefore never written to the audit log either.
     *
     * <p>Currently the same set as {@link #isWriteOnly()}, and kept as its own question because they
     * are different claims: one is about what a response may contain, the other about what the audit
     * trail may record.
     */
    public boolean isSecret() {
        return access == Access.WRITE_ONLY;
    }

    /**
     * Refuses a value this setting cannot hold.
     *
     * @throws SettingValueException naming the key, what was expected and what arrived
     */
    public void validate(String value) {
        type.validate(key, value);
    }

    /** The catalogue entry for a key, or empty when the key is not exposed at all. */
    public static Optional<SettingsCatalog> forKey(String key) {
        return Arrays.stream(values()).filter(entry -> entry.key.equals(key)).findFirst();
    }

    private enum Access {
        READ_WRITE,
        /** Readable, never settable through the API. */
        READ_ONLY,
        /** Settable, never returned. */
        WRITE_ONLY
    }
}
