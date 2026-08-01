import { describe, expect, it } from 'vitest'

import { generatePassword } from './generated-password'

/**
 * The generated password, held to the two things that matter about it.
 *
 * It has to be **acceptable to the backend** — `PasswordPolicy` requires twelve characters and
 * refuses anything blank — and it has to be **different every time**, which is the property that
 * makes it worth generating at all rather than typing.
 */
describe('a generated password', () => {
  it('satisfies the backend policy with room to spare', () => {
    const password = generatePassword()
    // PasswordPolicy.MINIMUM_LENGTH is 12, MAXIMUM_LENGTH is 200.
    expect(password.length).toBeGreaterThanOrEqual(12)
    expect(password.length).toBeLessThanOrEqual(200)
    expect(password.trim()).toBe(password)
  })

  it('avoids glyphs that are read wrongly off a screen', () => {
    // The value's whole job is to survive being copied or dictated once. `0`/`O` and `1`/`l`/`I`
    // are how that fails, and a failed hand-off is another reset.
    for (let attempt = 0; attempt < 50; attempt++) {
      expect(generatePassword()).toMatch(/^[A-HJ-NP-Za-km-z2-9]+$/)
    }
  })

  it('is different every time', () => {
    // Not a strength test — no test can be one — but it does fail against a constant, a counter,
    // or a seeded generator that was never advanced.
    const drawn = new Set(Array.from({ length: 200 }, () => generatePassword()))
    expect(drawn.size).toBe(200)
  })
})
