import { describe, expect, it } from 'vitest'

import {
  Decimal,
  formatDecimal,
  formatMoney,
  formatUnitCost,
  formatQuantity,
  formatRate,
  fromWire,
  isTypeable,
  parseTyped,
  toMoney,
  toWire,
} from './decimal'

describe('reading values off the wire', () => {
  it('keeps a value a float cannot hold', () => {
    // The whole reason for this module. 0.1 + 0.2 is 0.30000000000000004 as doubles.
    const total = fromWire('0.1').plus(fromWire('0.2'))
    expect(toWire(total, 'money')).toBe('0.30')
    expect(total.equals(new Decimal('0.3'))).toBe(true)
  })

  it('keeps a large amount exact', () => {
    // 9007199254740993 is the first integer a double cannot represent.
    expect(toWire(fromWire('9007199254740993.01'), 'money')).toBe('9007199254740993.01')
  })

  it('refuses a value that is not a wire decimal', () => {
    // Fail loud. A malformed amount from the API is a contract violation, not a blank cell.
    expect(() => fromWire('1,50')).toThrow()
    expect(() => fromWire('')).toThrow()
    expect(() => fromWire('12.50 EUR')).toThrow()
    expect(() => fromWire('1e5')).toThrow()
  })
})

describe('parsing what a person typed', () => {
  it('accepts a full stop', () => {
    expect(parseTyped('12.50')?.toString()).toBe('12.5')
  })

  it('accepts a comma, because a Greek keyboard produces one', () => {
    expect(parseTyped('12,50')?.toString()).toBe('12.5')
    expect(parseTyped('0,001')?.toString()).toBe('0.001')
  })

  it('accepts a negative value', () => {
    expect(parseTyped('-4,25')?.toString()).toBe('-4.25')
  })

  it('treats a half-typed value as not-yet-a-number rather than an error', () => {
    for (const partial of ['', ' ', '-', '.', ',']) {
      expect(parseTyped(partial), `"${partial}"`).toBeUndefined()
    }
  })

  it('reads a trailing separator as the number before it', () => {
    // `12,` is what a field contains for as long as it takes to type the next character. Reading
    // it as 12 means the form holds a valid value throughout, and blurring there canonicalises to
    // 12.00 rather than clearing what was typed.
    expect(parseTyped('12.')?.toString()).toBe('12')
    expect(parseTyped('12,')?.toString()).toBe('12')
  })

  it('refuses a second separator instead of guessing which one is grouping', () => {
    // 1.234,56 and 1,234.56 are the same number written two ways, and 1.234 is two different
    // numbers depending on the locale. Guessing here is how a €1,234 payment becomes €1.23.
    expect(parseTyped('1.234,56')).toBeUndefined()
    expect(parseTyped('1,234.56')).toBeUndefined()
    expect(parseTyped('1.234')?.toString()).toBe('1.234')
  })

  it('refuses text', () => {
    for (const rubbish of ['abc', '12abc', '1 2', '--1', '1-']) {
      expect(parseTyped(rubbish), rubbish).toBeUndefined()
    }
  })

  it('allows the states a field passes through while being typed', () => {
    for (const step of ['', '-', '1', '12', '12,', '12,5', '12,50']) {
      expect(isTypeable(step), `"${step}"`).toBe(true)
    }
    expect(isTypeable('abc')).toBe(false)
    expect(isTypeable('12€')).toBe(false)
  })

  it('lets an ambiguous value stand rather than reshaping it', () => {
    // `1.234,56` is typeable but not parseable. Dropping the comma as it was typed would leave
    // `1.23456` — a different number, arrived at silently. It stays on screen, and stays invalid.
    expect(isTypeable('1.234,56')).toBe(true)
    expect(parseTyped('1.234,56')).toBeUndefined()
  })
})

describe('writing values back to the wire', () => {
  it('pads to the scale of the type', () => {
    expect(toWire(new Decimal('12.5'), 'money')).toBe('12.50')
    expect(toWire(new Decimal('3'), 'quantity')).toBe('3.000000')
    expect(toWire(new Decimal('24'), 'rate')).toBe('24.000000')
    expect(toWire(new Decimal('12.505'), 'unitCost')).toBe('12.505000')
  })

  it('rounds half up to the scale, once', () => {
    expect(toWire(new Decimal('12.345'), 'money')).toBe('12.35')
    expect(toWire(new Decimal('12.344'), 'money')).toBe('12.34')
  })

  it('never emits grouping, a currency symbol or a locale separator', () => {
    expect(toWire(new Decimal('1234567.891'), 'money')).toBe('1234567.89')
  })

  it('builds Money with the currency it was given, never a default', () => {
    expect(toMoney(new Decimal('12.5'), 'EUR')).toEqual({ amount: '12.50', currency: 'EUR' })
    expect(toMoney(new Decimal('12.5'), 'GBP').currency).toBe('GBP')
  })
})

describe('formatting for display', () => {
  it('uses the comma as the decimal separator in Greek', () => {
    expect(formatDecimal(new Decimal('1234.5'), 'money', 'el-GR')).toBe('1.234,50')
  })

  it('uses the full stop in English', () => {
    expect(formatDecimal(new Decimal('1234.5'), 'money', 'en-GB')).toBe('1,234.50')
  })

  it('preserves the scale of the type rather than trimming zeros', () => {
    expect(formatQuantity('3.000000', 'en-GB')).toBe('3.000000')
    expect(formatRate('24.000000', 'el-GR')).toBe('24,000000%')
  })

  it('keeps a negative value negative', () => {
    expect(formatDecimal(new Decimal('-0.05'), 'money', 'el-GR')).toBe('-0,05')
  })

  it('shows the currency the value carries', () => {
    const greek = formatMoney({ amount: '1234.50', currency: 'EUR' }, 'el-GR')
    expect(greek).toContain('1.234,50')
    expect(greek).toContain('€')

    const english = formatMoney({ amount: '1234.50', currency: 'EUR' }, 'en-GB')
    expect(english).toContain('1,234.50')
  })

  it('keeps a unit cost at six decimals rather than rounding it to money', () => {
    // A union parameter with a money-scale default rendered this as €12.51 — a wrong number on
    // screen, no rounding indicated, no compile error. Unit costs have their own function now.
    expect(formatUnitCost({ amount: '12.505000', currency: 'EUR' }, 'en-GB')).toContain('12.505000')
    expect(formatMoney({ amount: '12.50', currency: 'EUR' }, 'en-GB')).toContain('12.50')
  })

  it('does not put a minus sign in front of nothing', () => {
    // -0.001 at money scale rounds away to zero, and zero has no sign. "-0,00" in a report reads
    // as a real negative to whoever is looking at it.
    expect(formatDecimal(new Decimal('-0.001'), 'money', 'el-GR')).toBe('0,00')
    // A value that survives the rounding keeps its sign.
    expect(formatDecimal(new Decimal('-0.005'), 'money', 'el-GR')).toBe('-0,01')
  })

  it('formats a big amount without reaching for a float', () => {
    expect(formatDecimal(new Decimal('9007199254740993.01'), 'money', 'en-GB')).toBe(
      '9,007,199,254,740,993.01',
    )
  })
})
