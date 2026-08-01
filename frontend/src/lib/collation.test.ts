import { describe, expect, it } from 'vitest'

import {
  compareDecimal,
  compareMoney,
  compareText,
  RESOLVED_COLLATION_LOCALE,
} from './collation'

const sort = (values: string[]) => [...values].sort(compareText)

/**
 * The sample, and the two orders it can come out in.
 *
 * Chosen to contain every case that goes wrong under a byte comparison at once: mixed case, Latin
 * accents above the ASCII range, Greek in both cases, and a Greek accented capital (`Ά`, U+0386)
 * that sits *below* its unaccented form (`Α`, U+0391) in code-point order.
 */
const MIXED = [
  'apple',
  'Banana',
  'banana',
  'Apple',
  'Zebra',
  'zebra',
  'Ácme',
  'Öl',
  'Αθήνα',
  'αθήνα',
  'Άλφα',
  'Ωμέγα',
  'ζήτα',
  'Βήτα',
  'Ελλάς',
  'ελλάς',
]

/**
 * What PostgreSQL 17.10 returns for this sample under `ORDER BY x COLLATE "el-GR-x-icu"`.
 *
 * ⚠️ **Copied from a run against the live stack, not composed by hand.** It is the expectation in
 * the parity test below, so this array is the frontend's record of what the database will do — and
 * the reason a change to the collator that looks harmless here fails loudly.
 */
const AS_POSTGRES_ORDERS_IT = [
  'αθήνα',
  'Αθήνα',
  'Άλφα',
  'Βήτα',
  'ελλάς',
  'Ελλάς',
  'ζήτα',
  'Ωμέγα',
  'Ácme',
  'apple',
  'Apple',
  'banana',
  'Banana',
  'Öl',
  'zebra',
  'Zebra',
]

describe('the runtime this collator actually got', () => {
  /**
   * ⚠️ **The pin, asserted — `CLAUDE.md`'s "a test environment configured unlike the real one".**
   *
   * `Intl.Collator('el')` on a runtime built with small-icu resolves to the root locale instead of
   * failing. Everything below would still pass — the *comparisons* stay locale-aware — but the
   * Greek-before-Latin reordering, which is the decision this module exists to carry out, would
   * silently be gone. That is precisely the shape of the defect that shipped in S1: a green suite
   * describing a system nobody runs.
   *
   * So the resolution is asserted rather than assumed. A build on a stripped runtime fails here,
   * naming the cause, instead of quietly reordering every list in the application.
   */
  it('resolved to Greek, not to a root-locale fallback', () => {
    expect(RESOLVED_COLLATION_LOCALE).toBe('el')
  })
})

describe('ordering text the way a person reads it', () => {
  it('orders exactly as the database will, so the two halves cannot disagree', () => {
    expect(sort(MIXED)).toEqual(AS_POSTGRES_ORDERS_IT)
  })

  it('is not the order the runtime would have given on its own', () => {
    // Guards the guard: if this ever matches, the collator has stopped doing anything and every
    // assertion above would be passing for the wrong reason.
    expect(sort(MIXED)).not.toEqual([...MIXED].sort())
  })

  it('puts Greek before Latin, which is the decision rather than a default', () => {
    expect(sort(['Zebra', 'Ωμέγα'])).toEqual(['Ωμέγα', 'Zebra'])
  })

  it('does not put every capital before every lowercase', () => {
    // The single most visible symptom of byte order: `Zebra` ahead of `apple`.
    expect(sort(['Zebra', 'apple'])).toEqual(['apple', 'Zebra'])
  })

  it('files an accented word with its base letter, not after the whole alphabet', () => {
    expect(sort(['Ácme', 'Zebra', 'banana'])).toEqual(['Ácme', 'banana', 'Zebra'])
    expect(sort(['Άλφα', 'Ωμέγα', 'Βήτα'])).toEqual(['Άλφα', 'Βήτα', 'Ωμέγα'])
  })

  it('sorts the retail customer where a reader expects, not below every Latin row', () => {
    // The live seed, and the case that made this visible: under the database's byte order
    // `Πελάτης Λιανικής` came last, after all five TEST-CUSTOMER rows.
    expect(sort(['TEST-CUSTOMER-01 Wholesale', 'Πελάτης Λιανικής', 'TEST-CUSTOMER-02 Cafe'])).toEqual(
      ['Πελάτης Λιανικής', 'TEST-CUSTOMER-01 Wholesale', 'TEST-CUSTOMER-02 Cafe'],
    )
  })

  it('treats absent and empty as the same thing, and puts both last', () => {
    expect(sort(['zebra', '', 'apple'])).toEqual(['apple', 'zebra', ''])
    expect(compareText(undefined, 'apple')).toBeGreaterThan(0)
    expect(compareText('apple', undefined)).toBeLessThan(0)
    expect(compareText(undefined, null)).toBe(0)
  })
})

describe('ordering numbers that arrived as strings', () => {
  it('compares a wire decimal as a number', () => {
    // The defect this exists to prevent: as text, "9.00" sorts above "1234.56".
    expect(compareDecimal('9.00', '1234.56')).toBeLessThan(0)
    expect(compareDecimal('12.505000', '12.510000')).toBeLessThan(0)
    expect(compareDecimal('-5.00', '1.00')).toBeLessThan(0)
    expect(compareDecimal('10.00', '10.000000')).toBe(0)
  })

  it('keeps full precision rather than going through a float', () => {
    // Two values that are equal as IEEE-754 doubles and are not equal as decimals.
    expect(compareDecimal('0.1000000000000000055511151231257827', '0.1')).toBeGreaterThan(0)
  })

  it('puts an absent amount last', () => {
    expect(compareDecimal(undefined, '1.00')).toBeGreaterThan(0)
    expect(compareDecimal(undefined, undefined)).toBe(0)
  })
})

describe('ordering money', () => {
  const eur = (amount: string) => ({ amount, currency: 'EUR' })
  const usd = (amount: string) => ({ amount, currency: 'USD' })

  it('orders by amount within a currency', () => {
    expect(compareMoney(eur('9.00'), eur('1234.56'))).toBeLessThan(0)
  })

  it('groups by currency rather than comparing across one', () => {
    // 100 USD against 90 EUR has no answer without a rate nobody chose. Grouping says so.
    expect(compareMoney(eur('90.00'), usd('100.00'))).toBeLessThan(0)
    expect(compareMoney(usd('1.00'), eur('9999.00'))).toBeGreaterThan(0)
  })

  it('puts an absent amount last', () => {
    expect(compareMoney(undefined, eur('1.00'))).toBeGreaterThan(0)
    expect(compareMoney(eur('1.00'), undefined)).toBeLessThan(0)
  })
})
