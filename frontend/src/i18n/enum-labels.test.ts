import { describe, expect, it } from 'vitest'

import spec from '../../../docs/api/openapi.json'

import el from './locales/el/enums.json'
import en from './locales/en/enums.json'

/**
 * Every enum value the API can return has a label, in English and in Greek.
 *
 * Enum labels are the frontend's to own — the backend localises nothing (Q47(b)) and its enum
 * values are identifiers, not text. The failure this prevents is small and certain: a new value
 * added on the backend reaches an operator as `SHRINKAGE` or `ACS_COD`, in a screen that is
 * otherwise entirely in Greek, and nobody notices until a customer asks what it means.
 *
 * Reading the enums from the spec rather than listing them here is what makes that true of values
 * that do not exist yet.
 */

interface Schema {
  enum?: string[]
}

const schemas = spec.components.schemas as Record<string, Schema>

const enums = Object.entries(schemas)
  .filter(([, schema]) => Array.isArray(schema.enum))
  .map(([name, schema]) => ({ name, values: schema.enum ?? [] }))

const labels: Record<string, Record<string, string>> = { en, el }

describe('enum labels', () => {
  it('has enums to check', () => {
    // Guards against a spec move turning every assertion below into a vacuous pass.
    expect(enums.length).toBeGreaterThan(25)
  })

  it.each(['en', 'el'])('covers every enum value in %s', (language) => {
    const missing: string[] = []
    for (const { name, values } of enums) {
      for (const value of values) {
        const key = `${name}.${value}`
        if (!labels[language]?.[key]) missing.push(key)
      }
    }
    expect(missing, `add these to src/i18n/locales/${language}/enums.json`).toEqual([])
  })

  it.each(['en', 'el'])('has no %s label for a value the API no longer has', (language) => {
    const known = new Set(enums.flatMap(({ name, values }) => values.map((v) => `${name}.${v}`)))
    const orphans = Object.keys(labels[language] ?? {}).filter((key) => !known.has(key))
    expect(orphans, 'these name enum values that are not in the spec').toEqual([])
  })

  it('does not translate a Greek label to the English text', () => {
    // A weak but genuine check on copy-paste: identical strings are legitimate for proper nouns
    // (PayPal, Skroutz, SMTP), so this only requires that most labels actually differ.
    const identical = Object.entries(en).filter(([key, value]) => el[key as keyof typeof el] === value)
    expect(identical.length).toBeLessThan(Object.keys(en).length / 4)
  })
})
