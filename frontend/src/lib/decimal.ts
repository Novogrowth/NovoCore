import Decimal from 'decimal.js'

import type { Money, Quantity, Rate, UnitCost } from '@/api/generated/model'

/**
 * Decimal arithmetic and formatting for values that came off the wire.
 *
 * CLAUDE.md rule 5: money is `BigDecimal` on the backend and never a float, anywhere, ever. On this
 * side of the wire that means `Decimal` and strings — a JSON number is an IEEE-754 double, and
 * `12.10` does not survive the round trip through one. The API refuses a number outright, so this
 * is not merely a display concern: parse a value into a float and the request built from it is
 * rejected, or worse, accepted a cent out.
 *
 * `parseFloat` and `parseInt` are banned by ESLint. This module is the alternative.
 */

/**
 * Enough precision that no intermediate result is ever the reason a total is wrong.
 *
 * The wire scales are 2 (money) and 6 (unit cost, quantity, rate); 40 significant digits leaves
 * room for multiplying a six-decimal quantity by a six-decimal unit cost and still rounding once,
 * at the end, on purpose.
 */
Decimal.set({ precision: 40, toExpNeg: -30, toExpPos: 40 })

export { Decimal }

/** U+00A0, written as a code point so no invisible character lives in this file. */
const NBSP = String.fromCharCode(0xa0)

/** How many decimals each wire type carries. The backend's scales, not preferences. */
export const SCALE = {
  money: 2,
  unitCost: 6,
  quantity: 6,
  rate: 6,
} as const

export type WireScale = keyof typeof SCALE

/** What the API will accept: an optional sign, digits, and an optional decimal part. */
const WIRE_FORMAT = /^-?\d+(\.\d+)?$/

export function isWireDecimal(value: string): boolean {
  return WIRE_FORMAT.test(value)
}

/**
 * Reads a value that came off the wire.
 *
 * Throws rather than returning NaN: a malformed amount from the API is not a value to render as
 * blank, it is a contract violation, and the fail-loud rule applies to what we receive as much as
 * to what we send.
 */
export function fromWire(value: string): Decimal {
  if (!isWireDecimal(value)) {
    throw new Error(`Not a wire decimal: ${JSON.stringify(value)}`)
  }
  return new Decimal(value)
}

/**
 * Renders a value in the canonical form the API expects: a plain string at the type's own scale,
 * with no grouping, no currency symbol and no locale in sight.
 *
 * `toFixed` on `Decimal` is exact — this is not `Number.prototype.toFixed`.
 */
export function toWire(value: Decimal, scale: WireScale): string {
  return value.toFixed(SCALE[scale])
}

/**
 * Reads what a person typed.
 *
 * Accepts the comma as a decimal separator, because a Greek keyboard produces one and an operator
 * typing `1,50` means one and a half. Returns undefined for anything that is not a number yet —
 * an empty field, a lone `-`, `1.` mid-typing — which is a normal state during editing rather
 * than an error to shout about.
 *
 * Grouping separators are deliberately NOT accepted: `1.234` is 1.234 in one locale and 1234 in
 * another, and no amount of guessing makes that safe. Inputs therefore never display grouping
 * either, so there is nothing to paste back in.
 */
export function parseTyped(input: string): Decimal | undefined {
  const trimmed = input.trim()
  if (trimmed === '') return undefined

  const normalised = trimmed.replace(',', '.')

  // A second separator means the value is ambiguous or malformed. Refuse both readings.
  if ((trimmed.match(/[.,]/g)?.length ?? 0) > 1) return undefined
  if (!/^-?(\d+(\.\d*)?|\.\d+)$/.test(normalised)) return undefined

  try {
    const value = new Decimal(normalised)
    return value.isFinite() ? value : undefined
  } catch {
    return undefined
  }
}

/**
 * Whether a string is made only of characters that can occur in a decimal.
 *
 * Deliberately looser than `parseTyped`: it allows `1.234,56`, which is not a number this
 * application will accept. That is the point. A field that silently dropped the second separator
 * would turn `1.234,56` into `1.23456` as the remaining keystrokes arrived — the exact corruption
 * refusing to guess exists to prevent. So the text is allowed to stand, and it is `parseTyped`
 * returning undefined that makes the field invalid and stops the value being used.
 *
 * Letters and symbols are refused outright, because no reading of them is a number at all.
 */
export function isTypeable(input: string): boolean {
  return /^-?[\d.,]*$/.test(input)
}

/**
 * Formats for display in the active language.
 *
 * Greek renders `1.234,56` and English `1,234.56`; both are produced from the `Decimal`'s exact
 * digits rather than by converting to a `Number` first, which is the step that would lose the
 * precision everything else here exists to protect.
 */
export function formatDecimal(value: Decimal, scale: WireScale, locale: string): string {
  const fixed = value.toFixed(SCALE[scale])
  const negative = fixed.startsWith('-')
  const [whole = '0', fraction = ''] = (negative ? fixed.slice(1) : fixed).split('.')

  const groupedWhole = new Intl.NumberFormat(locale, { useGrouping: true }).format(BigInt(whole))
  const decimalSeparator = new Intl.NumberFormat(locale)
    .formatToParts(1.1)
    .find((part) => part.type === 'decimal')?.value ?? '.'

  const rendered = fraction ? `${groupedWhole}${decimalSeparator}${fraction}` : groupedWhole
  return negative ? `-${rendered}` : rendered
}

/** `1.234,56 €` in Greek, `€1,234.56` in English — the currency the value actually carries. */
export function formatMoney(money: Money | UnitCost, locale: string, scale: WireScale = 'money'): string {
  const amount = formatDecimal(fromWire(money.amount), scale, locale)
  const symbol =
    new Intl.NumberFormat(locale, { style: 'currency', currency: money.currency })
      .formatToParts(0)
      .find((part) => part.type === 'currency')?.value ?? money.currency

  // Greek writes the symbol after the number, English before it. Asking Intl where it put the
  // symbol is more reliable than a list of locales maintained here.
  const symbolFirst =
    new Intl.NumberFormat(locale, { style: 'currency', currency: money.currency })
      .formatToParts(0)[0]?.type === 'currency'

  // A non-breaking space, written as an escape so it is visible in review: an amount must
  // never be separated from its currency symbol by a line break.
  return symbolFirst ? `${symbol}${amount}` : `${amount}${NBSP}${symbol}`
}

export function formatQuantity(quantity: Quantity, locale: string): string {
  return formatDecimal(fromWire(quantity), 'quantity', locale)
}

/** Rates are stored as a percentage — 24% is `'24.000000'`, not `'0.24'`. */
export function formatRate(rate: Rate, locale: string): string {
  return `${formatDecimal(fromWire(rate), 'rate', locale)}%`
}

/** Builds the wire shape from an amount the user entered. Currency is never defaulted. */
export function toMoney(amount: Decimal, currency: string): Money {
  return { amount: toWire(amount, 'money'), currency }
}

export function toUnitCost(amount: Decimal, currency: string): UnitCost {
  return { amount: toWire(amount, 'unitCost'), currency }
}
