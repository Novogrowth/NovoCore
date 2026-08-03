import { describe, expect, it } from 'vitest'

import { SettingsCatalog } from '@/api/generated/model'
import el from '@/i18n/locales/el/common.json'
import en from '@/i18n/locales/en/common.json'

import {
  ALL_SETTINGS,
  DOCUMENT_SETTINGS,
  EMAIL_SETTINGS,
  RETENTION_SETTINGS,
  ROUNDING_MODE_VALUES,
  TRANSPORT_SECURITY_VALUES,
} from './settings-catalogue'

/**
 * The catalogue is a hand-written mirror of a backend enum, so something has to hold the two
 * together. These are the assertions that make adding a key on the backend a build failure here
 * rather than a row that quietly appears on no page at all.
 */
describe('the settings catalogue', () => {
  it('covers every catalogued key exactly once', () => {
    // The failure this prevents: the backend adds a nineteenth key, `GET /api/settings` returns it,
    // and no page renders it — unreachable, with nothing broken anywhere. The reverse also fails
    // here: a key removed on the backend leaves a row whose PUT 404s.
    expect([...ALL_SETTINGS.map((entry) => entry.constant)].sort()).toEqual(
      Object.values(SettingsCatalog).sort(),
    )
  })

  it('puts every key on exactly one page', () => {
    const pages = [DOCUMENT_SETTINGS, EMAIL_SETTINGS, RETENTION_SETTINGS]
    expect(pages.reduce((total, page) => total + page.length, 0)).toBe(ALL_SETTINGS.length)
    expect(new Set(ALL_SETTINGS.map((entry) => entry.key)).size).toBe(ALL_SETTINGS.length)
  })

  it('names every never-writable key AND why, so the flag cannot become "not built yet"', () => {
    /*
     * Not a count for its own sake. `cash.payment.limit` having no write route is a decision with a
     * statute behind it, and this test exists so that a second key acquiring `readOnlyReason` is a
     * failure somebody has to look at rather than a silent widening.
     *
     * ⚠️ It did its job during R1a. `aade.spec-version` was added carrying `'statutory'` — reusing
     * the flag because it was the one that existed — and this assertion failed. A specification
     * version is not set by law; it is DERIVED from what a migration seeded, and editing it would
     * not change a single row, only make the marker lie about the rows that are there. So the
     * reason is a second value rather than the same one, and the screen shows a different
     * explanation for each.
     *
     * Asserted as key→reason PAIRS and not as a list of keys: a list would have gone green the
     * moment the wrong reason was attached to the right key, which is exactly the mistake made.
     */
    const readOnly = ALL_SETTINGS.filter((entry) => entry.readOnlyReason !== undefined).map(
      (entry) => [entry.key, entry.readOnlyReason] as const,
    )

    expect(readOnly).toEqual([
      ['cash.payment.limit', 'statutory'],
      ['aade.spec-version', 'derived'],
    ])
  })

  it.each([
    ['en', en],
    ['el', el],
  ])('has a %s label for every key, so none falls back to its dotted name', (_locale, bundle) => {
    /*
     * ⚠️ Added by R1a, because the fallback is SILENT and that is the whole problem.
     *
     * `SettingRow` resolves its label with `t('settings.key.<dotted key>', { defaultValue: key })`,
     * so a key with no translation renders as `company.branch-number` on the screen and in the
     * "Edit …" button's accessible name. Nothing errors, nothing warns, and the row looks like a
     * row somebody forgot to finish — which is exactly what R1a shipped for one commit until a
     * screen test happened to assert a button by name.
     *
     * ⚠️ It is deliberately NOT covered by `enum-labels.test.ts`. That one checks the
     * `SettingsCatalog.*` enum labels, which are a different set of strings used in a different
     * place; both existed for these two keys and only one of them was the one the row reads.
     */
    const missing = ALL_SETTINGS.map((entry) => `settings.key.${entry.key}`).filter(
      (key) => !(key in bundle),
    )

    expect(missing, `add these to src/i18n/locales/${_locale}/common.json`).toEqual([])
  })

  it('has exactly one write-only key, and it is the SMTP password', () => {
    const writeOnly = ALL_SETTINGS.filter((entry) => entry.writeOnly === true)
    expect(writeOnly.map((entry) => entry.key)).toEqual(['smtp.password'])
  })

  it('offers no transport security the backend would refuse', () => {
    /*
     * ⚠️ The assertion that pins F4's backend finding.
     *
     * `SettingType`'s javadoc listed `TLS`, which is not a constant of `EmailTransportSecurity` —
     * the real one is `IMPLICIT_TLS`. A select built from that sentence offers an option every save
     * refuses with a 422 naming the accepted set. The value is an opaque string in the spec, so no
     * generated enum exists and nothing else in this repository can catch it.
     */
    expect(TRANSPORT_SECURITY_VALUES).toEqual(['IMPLICIT_TLS', 'STARTTLS', 'NONE'])
    expect(TRANSPORT_SECURITY_VALUES).not.toContain('TLS')
  })

  it('offers no rounding mode that would break invoicing', () => {
    // The backend accepts any `RoundingMode` name, including UNNECESSARY — which makes every
    // rounding operation throw the first time a residual appears. A dropdown must not offer it.
    expect(ROUNDING_MODE_VALUES).not.toContain('UNNECESSARY')
    expect(ROUNDING_MODE_VALUES).toContain('HALF_UP')
  })
})
