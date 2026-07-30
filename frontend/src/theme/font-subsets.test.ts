import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const require = createRequire(import.meta.url)

/**
 * The font has to be able to render Greek.
 *
 * This is not a hypothetical: the scaffold this frontend started from bundled Geist, whose
 * Fontsource package ships latin, latin-ext, cyrillic and vietnamese and NO GREEK AT ALL. Half of
 * NovoCore's text is Greek, so every Greek character would have rendered as a missing-glyph box —
 * a failure that looks like a styling problem and is actually an unusable application.
 *
 * A font is chosen once and then forgotten about, which is exactly the kind of decision that needs
 * a test rather than a note. Swapping Manrope for something without Greek coverage fails here.
 */
describe('the bundled font', () => {
  const metadata = require('@fontsource-variable/manrope/metadata.json') as {
    family: string
    subsets: string[]
  }

  it('covers Greek as well as Latin', () => {
    expect(metadata.subsets).toContain('greek')
    expect(metadata.subsets).toContain('latin')
  })

  it('ships the two subset files index.css declares faces for', () => {
    // The @font-face rules name these files directly rather than importing the package's own
    // index.css, which would pull in all six subsets. If Fontsource ever renames them, the build
    // would fail on a missing asset — this says so first, and says why.
    expect(() =>
      require.resolve('@fontsource-variable/manrope/files/manrope-latin-wght-normal.woff2'),
    ).not.toThrow()
    expect(() =>
      require.resolve('@fontsource-variable/manrope/files/manrope-greek-wght-normal.woff2'),
    ).not.toThrow()
  })

  it('is the font the stylesheet actually asks for', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf8')
    expect(css).toContain(`'${metadata.family} Variable'`)
    expect(css).toContain('manrope-greek-wght-normal.woff2')

    // Geist is gone, and stays gone. Comments are stripped first: this file explains why Geist
    // was dropped, and a check that cannot tell an explanation from a declaration would forbid
    // recording the reason — which is the part worth keeping.
    const declarations = css.replace(/\/\*[\s\S]*?\*\//g, '')
    expect(declarations).not.toMatch(/geist/i)
  })
})
