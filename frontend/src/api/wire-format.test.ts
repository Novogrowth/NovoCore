import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * Every monetary and quantity field in the generated client is a string.
 *
 * `wire-format.assert.ts` proves this for the four types by name, at compile time. This proves it
 * for **every field of every model**, including ones added by a future endpoint that nobody wrote
 * an assertion for — which is the case the hand-written file cannot cover.
 *
 * The failure it guards against is not theoretical. A JSON number is an IEEE-754 double in a
 * JavaScript client: `12.10` does not survive the round trip, and a ledger that disagrees with
 * itself by a cent is a ledger nobody can close a month with.
 */
const MODEL_DIR = resolve(process.cwd(), 'src/api/generated/model')

/** Field names that carry a value, rather than an identifier or a count. */
const MONETARY = /(amount|price|cost|quantity|rate|balance|debit|credit|total|value)/i

/**
 * `vatClassId`, `customerCreditId` and their kin match MONETARY on the word before `Id` and are
 * genuinely integers — a database identifier, not a value. Excluded by shape rather than by a
 * list, so a new `...Id` needs no maintenance here.
 */
const IDENTIFIER = /Ids?$/

/** Paging counts. Real integers, and named in PageInfo rather than guessed at. */
const PAGING = new Set(['totalElements', 'totalPages', 'page', 'size'])

interface Field {
  file: string
  name: string
  type: string
}

function declaredFields(): Field[] {
  const fields: Field[] = []
  for (const file of readdirSync(MODEL_DIR)) {
    if (!file.endsWith('.ts')) continue
    const source = readFileSync(resolve(MODEL_DIR, file), 'utf8')
    for (const match of source.matchAll(/^\s*(\w+)\??:\s*([^;]+);/gm)) {
      const [, name, type] = match
      if (!name || !type) continue
      fields.push({ file, name, type: type.trim() })
    }
  }
  return fields
}

describe('the generated API client', () => {
  const fields = declaredFields()

  it('has models to check at all', () => {
    // A guard on the guard: if the generated output moved, every assertion below would pass
    // vacuously and report a green build for a check that examined nothing.
    expect(fields.length).toBeGreaterThan(500)
  })

  it('types no monetary or quantity field as a number', () => {
    const violations = fields.filter(
      (field) =>
        /\bnumber\b/.test(field.type) &&
        MONETARY.test(field.name) &&
        !IDENTIFIER.test(field.name) &&
        !PAGING.has(field.name),
    )

    expect(
      violations.map((field) => `${field.file}: ${field.name}: ${field.type}`),
      'a value that is money or a quantity must be a string on the wire — see CLAUDE.md rule 5',
    ).toEqual([])
  })

  it('keeps Money and UnitCost as an amount string plus a currency', () => {
    for (const model of ['money.ts', 'unitCost.ts']) {
      const source = readFileSync(resolve(MODEL_DIR, model), 'utf8')
      expect(source).toMatch(/amount:\s*string;/)
      expect(source).toMatch(/currency:\s*string;/)
    }
  })

  it('keeps Quantity and Rate as bare strings', () => {
    for (const model of ['quantity.ts', 'rate.ts']) {
      const source = readFileSync(resolve(MODEL_DIR, model), 'utf8')
      expect(source).toMatch(/=\s*string;/)
    }
  })
})
