import type { TFunction } from 'i18next'

import {
  SalesChannel,
  type AadeInvoiceTypeView,
  type SalesDocumentSeriesView,
  type SalesDocumentTypeView,
} from '@/api/generated/model'

/**
 * The vocabulary R2's six screens share — types, wire↔control conversions and label rules.
 *
 * ⚠️ **Separate from the components on purpose.** A module that exports both a component and a
 * helper breaks Fast Refresh, and the lint rule that says so was clean before R2. Everything here
 * is data or a pure function; the `.tsx` files beside it export components and nothing else.
 */

// ================================================================================================
// Document types
// ================================================================================================

/**
 * ⚠️ The purchase view is structurally identical to the sales one, so one type serves both screens.
 * If F6 gives `PurchaseDocumentTypeView` a component the sales one lacks, this alias stops
 * compiling — which is the right moment to split the screens rather than a surprise.
 */
export type DocumentTypeView = SalesDocumentTypeView

export interface NewDocumentTypeBody {
  description: string
  /** ⚠️ Required — the column is `NOT NULL`. Ordering only, and freely editable afterwards. */
  sortCode: number
  affectsStock?: boolean
  transfersStock?: boolean
  requiresMydataTransmission: boolean
  aadeInvoiceTypeId?: number
}

/** A type nobody has answered the stock question for. The backend calls these drafts. */
export function isDraft(type: DocumentTypeView): boolean {
  return type.affectsStock === undefined || type.transfersStock === undefined
}

/**
 * The two stock flags as a control value — ⚠️ **three states, not a boolean.**
 *
 * `affectsStock` and `transfersStock` are nullable and `null` does not mean `false`. It means nobody
 * has answered. R1b branches the consumption path on exactly this value: a type whose `affectsStock`
 * is `false` records a document and **silently consumes nothing** — no `stock_consumption` row, no
 * marker, nothing queryable (`CLAUDE.md`, *The document model* §6). A default of `false` would
 * decide that invisibly, on the operator's behalf.
 */
export type StockAnswer = 'yes' | 'no' | 'undecided'

export function toAnswer(value: boolean | undefined): StockAnswer {
  if (value === undefined) return 'undecided'
  return value ? 'yes' : 'no'
}

export function fromAnswer(answer: StockAnswer): boolean | undefined {
  if (answer === 'undecided') return undefined
  return answer === 'yes'
}

/**
 * ⚠️ The backend's rule, mirrored so the screen can stop a request whose refusal is certain: a type
 * that transfers stock necessarily affects it.
 *
 * As every mirror in this application, it prevents a pointless round-trip and does **not** replace
 * the refusal — the server's message is fuller and `Refusal` shows it whenever a request is sent.
 */
export function isCoherent(affects: StockAnswer, transfers: StockAnswer): boolean {
  return !(transfers === 'yes' && affects === 'no')
}

// ================================================================================================
// The AADE codification reference
// ================================================================================================

/** Not `''`: an empty option value is exactly the blank these screens avoid everywhere. */
export const NO_AADE_TYPE = 'none'

/** `4 — Για Μελλοντική Χρήση`. ⚠️ Never the description alone — see `aade-invoice-type-select`. */
export function aadeLabel(type: AadeInvoiceTypeView): string {
  return `${type.code} — ${type.description}`
}

/** The label for a type's current AADE code on a read-only row, or the "none" wording. */
export function aadeDisplay(
  code: string | undefined,
  types: readonly AadeInvoiceTypeView[],
  id: number | undefined,
  t: TFunction,
): string {
  if (id === undefined) return t('docTypes.aade.none')
  const found = types.find((type) => type.id === id)
  // The list route may not be permitted, in which case the view's own resolved code is still
  // there — `lookups.ts`'s rule: show what is known rather than a blank that says "not set".
  return found ? aadeLabel(found) : (code ?? `#${id}`)
}

// ================================================================================================
// Series
// ================================================================================================

/**
 * ⚠️ The purchase view is the sales one **minus `channel`**, so the sales type serves both and the
 * purchase screens never read that property. If F6 gives the purchase record something the sales
 * one lacks, this stops compiling — which is the right moment to split.
 */
export type SeriesView = SalesDocumentSeriesView

export interface NewSeriesBody {
  abbreviation: string
  description: string
  /** ⚠️ Required — the column is `NOT NULL`. Ordering only, and freely editable afterwards. */
  sortCode: number
  documentTypeId: number
  channel?: SalesChannel
  getsMark: boolean
  transformableIntoSeriesId?: number
}

/** ⚠️ Never blank. An absent channel is "this series is not a sales channel", which is a fact. */
export function channelLabel(t: TFunction, channel: SalesChannel | undefined): string {
  if (channel === undefined) return t('docSeries.channel.none')
  return t(`SalesChannel.${channel}`, { ns: 'enums' })
}

/** The channel select's value for "not a sales channel". Not `''`, which would read as blank. */
export const NO_CHANNEL = 'none'

/** The transformation target's "none" value. Same argument as {@link NO_CHANNEL}. */
export const NO_TARGET = 'none'

export function channelOptions(t: TFunction): { value: string; label: string }[] {
  return [
    { value: NO_CHANNEL, label: t('docSeries.channel.none') },
    ...Object.values(SalesChannel).map((channel) => ({
      value: channel,
      label: t(`SalesChannel.${channel}`, { ns: 'enums' }),
    })),
  ]
}

/** A select option's value back to a database id. Selects carry text; an id is a count. */
export function idOf(option: string): number {
  // eslint-disable-next-line no-restricted-syntax
  return Number(option)
}

/**
 * A typed sort code back to a number.
 *
 * ⚠️ **Not `MoneyInput` and not `<input type="number">`.** A sort code is a plain count, not a
 * decimal — none of `frontend/README.md`'s decimal rules apply, and `type="number"` is banned
 * outright. A text input restricted to digits is the honest control: it cannot produce a fraction,
 * a negative, or a locale-dependent separator.
 */
export function sortCodeOf(typed: string): number {
  // eslint-disable-next-line no-restricted-syntax
  return Number(typed)
}
