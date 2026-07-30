/**
 * Fails if the production build would fetch anything from the internet.
 *
 * NovoCore runs on the shop's own network, behind its own reverse proxy. A font, script or
 * stylesheet loaded from a CDN is a dependency on somebody else's uptime for an application whose
 * whole point is that the business owns it — and on a bad day it is a shop that cannot invoice
 * because a font server is unreachable.
 *
 * Run after `npm run build`. It refuses to pass on a missing `dist/`, because a check that quietly
 * examines nothing is worse than no check: it reports green for a build it never saw.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const DIST = resolve(here, '../dist')

/** Text formats worth reading. A woff2 has no URLs in it. */
const TEXTUAL = /\.(html|css|js|json|map|svg)$/i

/**
 * References that would actually FETCH from another origin.
 *
 * Matching any URL-shaped text was the first attempt and it does not work: React, React Router,
 * i18next, Base UI and Tailwind all embed documentation links in their error messages, so the
 * check reported five external origins for a build that requests none. A rule that cries wolf is
 * one somebody deletes.
 *
 * So each pattern names a context where a URL is loaded rather than mentioned — an attribute, a
 * stylesheet reference, an import, a request — and a link inside a string stays what it is: text.
 */
const FETCHING_CONTEXTS = [
  { what: 'HTML attribute', pattern: /(?:src|href)\s*=\s*["'](?:https?:)?\/\/[^"']+/gi },
  { what: 'CSS url()', pattern: /url\(\s*["']?(?:https?:)?\/\/[^)"']+/gi },
  { what: 'CSS @import', pattern: /@import\s+(?:url\()?\s*["'](?:https?:)?\/\/[^"']+/gi },
  { what: 'module import', pattern: /\bfrom\s*["'](?:https?:)?\/\/[^"']+/gi },
  { what: 'dynamic import', pattern: /\bimport\s*\(\s*["'](?:https?:)?\/\/[^"']+/gi },
  { what: 'fetch', pattern: /\bfetch\s*\(\s*["'](?:https?:)?\/\/[^"']+/gi },
]

/** `http://www.w3.org/2000/svg` is an XML namespace: an identifier, never requested. */
const NAMESPACES = ['www.w3.org', 'schema.org']

function filesIn(directory) {
  const found = []
  for (const entry of readdirSync(directory)) {
    const path = join(directory, entry)
    if (statSync(path).isDirectory()) found.push(...filesIn(path))
    else if (TEXTUAL.test(entry)) found.push(path)
  }
  return found
}

let distFiles
try {
  distFiles = filesIn(DIST)
} catch {
  console.error('check-no-cdn: dist/ is missing. Run `npm run build` first.')
  process.exit(1)
}

if (distFiles.length === 0) {
  console.error('check-no-cdn: dist/ contains no readable files. Nothing was checked.')
  process.exit(1)
}

const findings = []
for (const file of distFiles) {
  const content = readFileSync(file, 'utf8')
  for (const { what, pattern } of FETCHING_CONTEXTS) {
    for (const match of content.matchAll(pattern)) {
      const reference = match[0]
      if (NAMESPACES.some((allowed) => reference.includes(allowed))) continue
      findings.push(`${file.replace(DIST, 'dist')}: ${what} → ${reference.slice(0, 120)}`)
    }
  }
}

if (findings.length > 0) {
  console.error('check-no-cdn: the build references external origins:')
  for (const finding of [...new Set(findings)]) console.error(`  ${finding}`)
  process.exit(1)
}

console.log(`check-no-cdn: ${distFiles.length} built files, no external origins.`)
