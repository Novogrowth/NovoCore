import { describe, expect, it } from 'vitest'

import { SettingsCatalog } from '@/api/generated/model'

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

  it('has exactly one never-writable key, and it is the statutory cash limit', () => {
    // Not a count for its own sake. `cash.payment.limit` having no write route is a decision with a
    // statute behind it; a second key acquiring `readOnlyReason` would mean somebody used it as a
    // convenient way to say "not built yet", which is the distinction this whole screen turns on.
    const readOnly = ALL_SETTINGS.filter((entry) => entry.readOnlyReason !== undefined)
    expect(readOnly.map((entry) => entry.key)).toEqual(['cash.payment.limit'])
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
