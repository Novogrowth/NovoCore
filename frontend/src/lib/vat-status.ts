import { VatStatus } from '@/api/generated/model'

/**
 * Which VAT statuses need what, mirrored from the backend enum.
 *
 * ⚠️ **This duplicates `VatStatus`'s own two flags and can therefore drift.** It is here because the
 * flags are not in the spec: `VatStatus` is serialised as a bare string enum, so a client is told
 * the five values and nothing about what each one requires. The alternative is a screen that offers
 * a combination the backend will refuse and reports the refusal afterwards, which is worse — the
 * operator has already chosen by then.
 *
 * `vat-status.test.ts` pins both sets against the spec's enum, so a value **added** to
 * `VatStatus` fails here rather than silently defaulting to "requires nothing". It cannot catch a
 * change to what an existing value requires; that would need the flags on the wire, which is a
 * backend change and is noted in `HISTORY.md`.
 *
 * From `VatStatus.java`: `DOMESTIC(false, false)`, `INTRA_EU_B2B(true, false)`,
 * `NON_EU_EXPORT(false, false)`, `EXEMPT(false, true)`, `OTHER(false, false)`.
 */

/** `requiresVatNumber` — the status is not meaningful without a counterparty VAT number. */
export const NEEDS_VAT_NUMBER: ReadonlySet<VatStatus> = new Set([VatStatus.INTRA_EU_B2B])

/** `requiresExemptionReason` — the status is not meaningful without a named exemption article. */
export const NEEDS_EXEMPTION_REASON: ReadonlySet<VatStatus> = new Set([VatStatus.EXEMPT])
