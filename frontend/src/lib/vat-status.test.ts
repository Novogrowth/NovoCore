import { describe, expect, it } from 'vitest'

import { VatStatus } from '@/api/generated/model'

import { NEEDS_EXEMPTION_REASON, NEEDS_VAT_NUMBER } from './vat-status'

/**
 * The two rules mirrored from the backend enum, held to the enum itself.
 *
 * `VatStatus` reaches this client as a bare string enum: the spec carries the five values and
 * nothing about what each one requires, so the flags have to be restated on this side or the
 * screen offers combinations the backend refuses. Restating them means they can drift, and this is
 * the part of that drift a test can actually catch.
 *
 * ⚠️ **What it cannot catch:** a change to what an *existing* status requires — if `NON_EU_EXPORT`
 * started requiring an exemption reason tomorrow, nothing here would know. That needs the flags on
 * the wire, which is a backend change and is recorded in `PROGRESS.md`.
 */
describe('the VAT status rules mirrored from the backend', () => {
  it('names only statuses that exist', () => {
    const known = new Set<string>(Object.values(VatStatus))
    for (const status of [...NEEDS_VAT_NUMBER, ...NEEDS_EXEMPTION_REASON]) {
      expect(known, `${status} is not a VatStatus any more`).toContain(status)
    }
  })

  it('accounts for every status, so a new one cannot default to requiring nothing', () => {
    /*
     * The failure this exists for: the backend adds a sixth status with `requiresExemptionReason`
     * set, the enum regenerates, and this screen silently treats it as needing nothing — offering
     * a save that is refused, with no test going red. Listing all five here means a sixth fails
     * this line and forces someone to look up its flags.
     */
    const accountedFor = new Set<string>([
      VatStatus.DOMESTIC,
      VatStatus.INTRA_EU_B2B,
      VatStatus.NON_EU_EXPORT,
      VatStatus.EXEMPT,
      VatStatus.OTHER,
    ])
    expect(
      Object.values(VatStatus).filter((status) => !accountedFor.has(status)),
      'a VatStatus was added — check its requiresVatNumber / requiresExemptionReason flags in VatStatus.java and update vat-status-rules.ts',
    ).toEqual([])
  })

  it('matches VatStatus.java as it stands', () => {
    // DOMESTIC(false,false) INTRA_EU_B2B(true,false) NON_EU_EXPORT(false,false)
    // EXEMPT(false,true) OTHER(false,false)
    expect([...NEEDS_VAT_NUMBER]).toEqual([VatStatus.INTRA_EU_B2B])
    expect([...NEEDS_EXEMPTION_REASON]).toEqual([VatStatus.EXEMPT])
  })
})
