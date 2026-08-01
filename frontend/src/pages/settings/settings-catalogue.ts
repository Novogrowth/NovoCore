import { SettingsCatalog } from '@/api/generated/model'

/**
 * What each catalogued setting is, and which page it belongs on.
 *
 * **The backend's `SettingsCatalog` is an allowlist, not a view of the `setting` table**, and this
 * file is the frontend's statement of the same list. The table holds 33 rows; 18 are reachable over
 * HTTP. The other 15 are the whole `backup.*` namespace, which has **no route at all** — excluded as
 * a namespace rather than per key, because `backup.drive.*.folder-id` and `.client-id` are not
 * flagged secret and would arrive in the clear from any "redacted" listing. A settings screen that
 * expected to render whatever `GET /api/settings` returned would be right; one that expected to see
 * everything in the database would be wrong about what exists.
 *
 * ⚠️ **`{key}` in the URL is the ENUM CONSTANT NAME, not the dotted key.** `PUT
 * /api/settings/LEDGER_ROUNDING_THRESHOLD`, never `.../ledger.rounding.threshold`. There is no
 * custom converter registered, so Spring's default enum binding applies and an unknown segment is
 * refused before any of our code runs. The **response body**, confusingly, carries the *dotted* key
 * — so a screen keys its requests off one spelling and displays the other. That is why the two live
 * side by side in this file rather than one being derived from the other.
 *
 * **What is deliberately not here: labels and descriptions.** The backend sends a `description` per
 * row and it is written for exactly this screen. Copying those into the i18n bundle would create a
 * second source of truth that drifts, and the descriptions are long enough to matter — the
 * `cash.payment.limit` one explains a statute. The screen shows the server's own words; only the
 * short field label is translated.
 */

/** How a value is edited. Not on the wire — mirrored from the backend's `SettingType`. */
type SettingKind =
  | 'TEXT'
  | 'POSITIVE_INTEGER'
  | 'EUR_AMOUNT'
  | 'ROUNDING_MODE'
  | 'TRANSPORT_SECURITY'
  | 'RETENTION_DAYS'

export interface SettingSpec {
  /** The path segment. The enum constant, never the dotted key. */
  readonly constant: SettingsCatalog
  /** What the response body's `key` field holds, and what the screen shows as the row's name. */
  readonly key: string
  readonly kind: SettingKind
  /**
   * Readable and **never writable**, with the reason.
   *
   * ⚠️ Exactly one setting is this, and it is not a placeholder for "not built yet". See
   * `cash.payment.limit` below.
   */
  readonly readOnlyReason?: 'statutory'
  /**
   * Write-only. The value is never returned — not even redacted-with-a-length — so the screen can
   * report whether one is configured and nothing more.
   */
  readonly writeOnly?: true
}

/**
 * ⚠️ **Mirrored from `EmailTransportSecurity`, and hand-listed because it is not on the wire.**
 *
 * A setting's value is an opaque string in the OpenAPI document, so no generated enum exists for
 * this and `enum-labels.test.ts` cannot cover it either. The constants are `IMPLICIT_TLS`,
 * `STARTTLS`, `NONE`.
 *
 * ⚠️ **`SettingType`'s own javadoc named a fourth spelling, `TLS`, which is not a constant** — a
 * select built from that sentence would have offered an option the server refuses with a `422`.
 * Corrected in the backend during F4. The lesson is the general one: when a value's permitted set is
 * not in the spec, read the enum, not the prose about it.
 */
export const TRANSPORT_SECURITY_VALUES = ['IMPLICIT_TLS', 'STARTTLS', 'NONE'] as const

/**
 * `java.math.RoundingMode`, restricted to the four that mean something for money.
 *
 * The backend accepts any `RoundingMode` name — it validates with `RoundingMode.valueOf` — so
 * `UNNECESSARY` is accepted and would make every rounding operation throw the first time a residual
 * appeared. Offering all eight would be offering a way to break invoicing from a dropdown, so the
 * select offers the four that are defensible and the field stays a select rather than free text.
 *
 * ⚠️ Changing this affects **future** rounding only. A lot's carrying value deliberately does not
 * follow it (ADR 0015).
 */
export const ROUNDING_MODE_VALUES = ['HALF_UP', 'HALF_DOWN', 'HALF_EVEN', 'DOWN'] as const

/** The literal that means "keep everything" for a `RETENTION_DAYS` setting (Q43). */
export const RETENTION_FOREVER = 'FOREVER'

const spec = (
  constant: SettingsCatalog,
  key: string,
  kind: SettingKind,
  extra: Omit<SettingSpec, 'constant' | 'key' | 'kind'> = {},
): SettingSpec => ({ constant, key, kind, ...extra })

/**
 * Documents & Rounding — how money is rounded, what a cash payment may be, how large an attachment
 * may be. Four keys that share no namespace and do belong on one page: they are the settings an
 * administrator reviews when asking "how does this system handle a document".
 */
export const DOCUMENT_SETTINGS: readonly SettingSpec[] = [
  spec(SettingsCatalog.LEDGER_ROUNDING_THRESHOLD, 'ledger.rounding.threshold', 'EUR_AMOUNT'),
  spec(SettingsCatalog.LEDGER_ROUNDING_MODE, 'ledger.rounding.mode', 'ROUNDING_MODE'),
  /*
   * ⚠️ The one setting with no write route, and the reason is not technical.
   *
   * It is an ordinary row and SQL can change it. It is never writable over HTTP because it is a
   * STATUTORY limit — €500, N. 5301/2026, with penalties to double the cash amount — and a screen
   * that raised it would make breaking the law a two-click operation with an audit entry that reads
   * like configuration. This is the one refusal in the design that offers no confirmation path,
   * because the confirmation nobody can give is legality.
   *
   * It is READ here rather than omitted, deliberately: the read serves the administrator reviewing
   * configuration. The operator who actually hit the refusal already has the figure interpolated
   * into the 422 and into the invoice preview, with no SETTINGS grant needed.
   */
  spec(SettingsCatalog.CASH_PAYMENT_LIMIT, 'cash.payment.limit', 'EUR_AMOUNT', {
    readOnlyReason: 'statutory',
  }),
  spec(SettingsCatalog.ATTACHMENT_MAX_SIZE_BYTES, 'attachment.max-size-bytes', 'POSITIVE_INTEGER'),
]

/**
 * Email / SMTP — the server, the identity messages are sent under, and how the dispatcher retries.
 *
 * Twelve keys on one page rather than two, because they fail together: an operator asking "why has
 * nothing been sent" needs the credentials and the retry policy in one place.
 */
export const EMAIL_SETTINGS: readonly SettingSpec[] = [
  spec(SettingsCatalog.SMTP_HOST, 'smtp.host', 'TEXT'),
  spec(SettingsCatalog.SMTP_PORT, 'smtp.port', 'POSITIVE_INTEGER'),
  spec(SettingsCatalog.SMTP_TRANSPORT_SECURITY, 'smtp.transport-security', 'TRANSPORT_SECURITY'),
  spec(SettingsCatalog.SMTP_USERNAME, 'smtp.username', 'TEXT'),
  /*
   * Write-only. The value is never returned, so there is nothing to display and no "show it again".
   * This route is how the password is rotated; the screen reports configured / never configured and
   * offers to replace it. `SettingView.value` is `""` with no timestamps when no row exists, which
   * is how the two states are told apart.
   */
  spec(SettingsCatalog.SMTP_PASSWORD, 'smtp.password', 'TEXT', { writeOnly: true }),
  spec(SettingsCatalog.SMTP_FROM_ADDRESS, 'smtp.from-address', 'TEXT'),
  spec(SettingsCatalog.SMTP_FROM_NAME, 'smtp.from-name', 'TEXT'),
  spec(SettingsCatalog.SMTP_REPLY_TO, 'smtp.reply-to', 'TEXT'),
  spec(SettingsCatalog.EMAIL_MAX_ATTEMPTS, 'email.max-attempts', 'POSITIVE_INTEGER'),
  spec(SettingsCatalog.EMAIL_RETRY_BACKOFF_SECONDS, 'email.retry.backoff-seconds', 'POSITIVE_INTEGER'),
  spec(
    SettingsCatalog.EMAIL_RETRY_BACKOFF_MAX_SECONDS,
    'email.retry.backoff-max-seconds',
    'POSITIVE_INTEGER',
  ),
  spec(SettingsCatalog.EMAIL_DISPATCH_BATCH_SIZE, 'email.dispatch.batch-size', 'POSITIVE_INTEGER'),
]

/**
 * Retention — how long sent messages and their inline attachment bytes are kept.
 *
 * ⚠️ **Nothing here governs backups.** Backup retention is `backup.retention.*`, which has no route
 * at all, so this page cannot show it and must not imply it does. That is a namespace exclusion
 * rather than a missing feature.
 */
export const RETENTION_SETTINGS: readonly SettingSpec[] = [
  spec(SettingsCatalog.EMAIL_RETENTION_MESSAGE_DAYS, 'email.retention.message-days', 'RETENTION_DAYS'),
  spec(
    SettingsCatalog.EMAIL_RETENTION_INLINE_ATTACHMENT_DAYS,
    'email.retention.inline-attachment-days',
    'RETENTION_DAYS',
  ),
]

/**
 * Every catalogued setting, in page order.
 *
 * `settings-catalogue.test.ts` asserts this covers `SettingsCatalog` exactly — no key on two pages,
 * none missing. Without it, a key the backend adds lands on no page and is silently unreachable,
 * which is the same failure as a nav item nobody can see.
 */
export const ALL_SETTINGS: readonly SettingSpec[] = [
  ...DOCUMENT_SETTINGS,
  ...EMAIL_SETTINGS,
  ...RETENTION_SETTINGS,
]
