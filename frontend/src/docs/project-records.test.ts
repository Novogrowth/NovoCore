import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * The guards for U2a's `PROGRESS.md` / `HISTORY.md` split.
 *
 * <p>`PROGRESS.md` was 9,577 lines and the first file every session read, with live status in
 * EIGHT places inside it. U2a split it: `PROGRESS.md` is live only and carries the single status
 * table; `HISTORY.md` is append-only, indexed by step id, and headed *not authoritative for current
 * state*. Nothing in that arrangement is enforced by the structure itself — a half-finished move
 * leaves a step's record in both files, and a second status table is one edit away. This file is
 * what makes each of those a red build.
 *
 * WHY THESE LIVE IN THE FRONTEND SUITE. They guard `docs/`, not the frontend, and they are here for
 * one reason: this suite already reads files under `docs/` (`spec-hygiene.test.ts`,
 * `client-shape.test.ts`), and it is the fast one. The backend suite would have done as well and
 * has no better claim.
 *
 * ⚠️ AND THEY ONLY RUN BECAUSE OF THE LAST TEST IN THIS FILE. Until 2026-08-06 neither CI workflow
 * triggered on `docs/*.md`, so a docs-only edit — which is exactly the change these guards exist
 * for — would have run none of them. That is not a hypothetical: the filter was measured before the
 * change and matched neither file. `runsOnADocsOnlyChange` is what stops that being re-introduced.
 *
 * ⚠️ M1 WAS CONSIDERED AND REJECTED, and this note is here so nobody adds it. The obvious fifth
 * guard is "HISTORY.md must contain no live-status language" — a pattern over `🟡`, `**Current**`,
 * `is next`. It would fire constantly on history legitimately RECORDING those words: R4's entry
 * says `🟡 CURRENT from 2026-08-06`, and that is a correct historical statement. `CLAUDE.md`'s own
 * rule is that a check which cries wolf is one somebody deletes — so the container carries the
 * warning (`historySaysItIsNotAuthoritative`) and the contents are left alone.
 */

const REPO = resolve(process.cwd(), '..')
const PROGRESS_PATH = join(REPO, 'docs/PROGRESS.md')
const HISTORY_PATH = join(REPO, 'docs/HISTORY.md')

/*
 * Read inside the tests, not at module load. If one of these files goes missing the reference guard
 * below should report it as a dangling citation — which it cannot do if importing this file has
 * already thrown. Proven by running that control with `HISTORY.md` renamed.
 */
const progress = () => readFileSync(PROGRESS_PATH, 'utf8')
const history = () => readFileSync(HISTORY_PATH, 'utf8')

/** Every `##` heading in a document, as its text without the marker. */
function sectionHeadings(markdown: string): string[] {
  return markdown
    .split('\n')
    .filter((line) => line.startsWith('## '))
    .map((line) => line.slice(3).trim())
}

/** The rows of the first table whose header row matches, as arrays of trimmed cells. */
function tableRows(markdown: string, header: RegExp): string[][] {
  const lines = markdown.split('\n')
  const start = lines.findIndex((line) => header.test(line))
  if (start === -1) return []
  const rows: string[][] = []
  // +2 skips the header row and the `|---|` separator beneath it.
  for (let i = start + 2; i < lines.length; i++) {
    const line = lines[i]
    if (!line?.startsWith('|')) break
    rows.push(
      line
        .split('|')
        .slice(1, -1)
        .map((cell) => cell.trim()),
    )
  }
  return rows
}

/** The first cell of a table row, or the empty string — the seam row has none. */
function firstCell(row: string[]): string {
  return stepId(row[0] ?? '')
}

const STATUS_TABLE_HEADER = /^\|\s*Step\s*\|\s*What\s*\|\s*Status\s*\|\s*$/
const HISTORY_INDEX_HEADER = /^\|\s*Step\s*\|\s*Date\s*\|\s*What it was\s*\|\s*$/

/** A step id as written in a table cell, with the bold markers taken off. */
function stepId(cell: string): string {
  return cell.replaceAll('*', '').trim()
}

/**
 * Does this `##` heading introduce the given step's section?
 *
 * <p>Anchored, so `3` matches `Step 3 — done` and neither `Step 3b — done` (no word boundary
 * between `3` and `b`) nor `Step 13 — done` (the id must follow `Step ` immediately). A substring
 * test gets both of those wrong, which is why this is a regex and not `includes`.
 */
function introducesStep(heading: string, id: string): boolean {
  const escaped = id.replaceAll(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return new RegExp(String.raw`^▶?\s*(?:Step\s+)?${escaped}\b`).test(heading)
}

describe('the PROGRESS / HISTORY split', () => {
  it('keeps every section in exactly one file — none appears in both', () => {
    const inProgress = new Set(sectionHeadings(progress()))
    const inBoth = sectionHeadings(history()).filter((heading) => inProgress.has(heading))

    // The failure this catches is a half-finished close-out: the step's record copied into
    // HISTORY.md and not removed from PROGRESS.md, leaving two records of one thing — which is
    // the exact drift the split existed to end.
    expect(inBoth).toEqual([])
  })

  it('indexes every HISTORY section, and gives every index row a section', () => {
    const indexRows = tableRows(history(), HISTORY_INDEX_HEADER)
    // The seam row marks where the file's chronological direction reverses; it names no step.
    const steps = indexRows.filter((row) => firstCell(row) !== '')
    // Every `##` in HISTORY is a step's record except the index's own heading.
    const sections = sectionHeadings(history()).filter(
      (heading) => !heading.startsWith('Index — by step identifier'),
    )

    // Exactly one section per indexed step, and no section without an index row. Asserted as an
    // equality rather than a floor, so an unindexed section fails as loudly as a missing one.
    expect(sections.length).toBe(steps.length)
    expect(indexRows.length).toBe(steps.length + 1)
  })

  it("keeps the current step's own section in PROGRESS, not in HISTORY", () => {
    const current = tableRows(progress(), STATUS_TABLE_HEADER)
      .filter((row) => row[2]?.includes('🟡'))
      .map((row) => firstCell(row))

    // A positive control: if nothing is marked current, this test measures nothing and the two
    // assertions below would pass vacuously.
    expect(current.length).toBeGreaterThan(0)

    for (const id of current) {
      expect(sectionHeadings(progress()).filter((h) => introducesStep(h, id))).toHaveLength(1)
      expect(sectionHeadings(history()).filter((h) => introducesStep(h, id))).toHaveLength(0)
    }
  })

  it('has exactly one status table, and it is in PROGRESS', () => {
    const statusTablesIn = (markdown: string) =>
      markdown.split('\n').filter((line) => STATUS_TABLE_HEADER.test(line)).length

    expect(statusTablesIn(progress())).toBe(1)
    expect(statusTablesIn(history())).toBe(0)

    // And HISTORY's index carries no status column, deliberately — a second table with a status is
    // how this project's records came to disagree in the first place.
    expect(history()).toContain('| Step | Date | What it was |')
  })

  it('says in HISTORY that it is not authoritative for current state', () => {
    // Cheap, and what makes deleting the warning a red build rather than a silent loss. The header
    // is the only thing framing every figure inside an 8,000-line append-only file.
    expect(history()).toContain('THIS FILE IS NOT AUTHORITATIVE FOR CURRENT STATE')
    expect(history()).toContain('docs/PROGRESS.md')
  })
})

/**
 * Files that cite a project record, and the directories not worth walking to find them.
 *
 * <p>Scoped rather than repo-wide: these are the trees that actually carry citations, and a walk
 * of `node_modules` or `target` would cost more than the guard is worth.
 */
const SEARCH_ROOTS = ['CLAUDE.md', '.gitignore', 'docs', 'docker', 'frontend/src', 'frontend/README.md', 'backend']
const SKIP_DIRS = new Set(['node_modules', 'target', 'dist', '.git', 'generated'])
const SEARCH_EXTENSIONS = ['.md', '.java', '.sql', '.ts', '.tsx', '.gitignore']

function filesToSearch(): string[] {
  const found: string[] = []
  const walk = (path: string) => {
    if (!existsSync(path)) return
    if (statSync(path).isFile()) {
      if (SEARCH_EXTENSIONS.some((ext) => path.endsWith(ext))) found.push(path)
      return
    }
    for (const entry of readdirSync(path)) {
      if (SKIP_DIRS.has(entry)) continue
      walk(join(path, entry))
    }
  }
  for (const root of SEARCH_ROOTS) walk(join(REPO, root))
  return found
}

describe('references to a project record', () => {
  it('always name a file that exists', () => {
    /*
     * ⚠️ SCOPED TO THE TWO RECORD FILES, AND THE FIRST DRAFT WAS NOT — which is why this comment
     * exists rather than a wider pattern.
     *
     * The draft matched every `docs/*.md` citation. On its first run it reported five failures,
     * and every one was CORRECT PROSE: `CLAUDE.md`, `HISTORY.md` and the primer each record that
     * `docs/novocore-frontend-roadmap.md` and `docs/PROJECT_STATE_SUMMARY.md` were DELETED, on
     * purpose, in U1. A sentence saying "X was deleted" names a file that must not exist, so the
     * broad guard fired on the documents doing exactly the right thing.
     *
     * That is the cries-wolf shape M1 was rejected for, arriving from a second direction, and the
     * fix is not an allowlist — allowlists decay, and this one would have started with two entries
     * that are correct forever. The guard is narrowed to the two files the split created, which is
     * the risk it was written for: one of them renamed, moved or deleted out from under 110
     * citations.
     */
    const cited = /(?:docs\/)?\b(?:PROGRESS|HISTORY)\.md/g
    const dangling: string[] = []

    for (const file of filesToSearch()) {
      const text = readFileSync(file, 'utf8')
      for (const [reference] of text.matchAll(cited)) {
        const candidates = reference.includes('/')
          ? [join(REPO, reference)]
          : [join(REPO, 'docs', reference), join(REPO, reference)]
        if (!candidates.some(existsSync)) {
          dangling.push(`${file.slice(REPO.length + 1)} -> ${reference}`)
        }
      }
    }

    expect(dangling).toEqual([])
  })

  /*
   * ⚠️ THE LIMIT OF THE GUARD ABOVE, stated here rather than left to be discovered.
   *
   * It checks that a citation names a file that EXISTS. It cannot check that the content behind
   * the name exists. The standing worked example is `frontend/src/auth/permissions.ts:95`, which
   * says the real fix "is a backend change and is noted in PROGRESS.md" — measured on 2026-08-06:
   * nothing matching that note is in PROGRESS.md or HISTORY.md, and the reference predates the
   * split. The filename is right, so this guard passes, and the citation is still wrong.
   *
   * That residual is deliberate. Closing it would mean asserting that some phrase appears in a
   * document, which is a pin on prose and decays into exactly the cries-wolf check M1 was rejected
   * for. The honest remedies are to write the note or delete the claim, and neither is a test's.
   */
})

describe('the CI path filter', () => {
  it('runs the frontend workflow on a docs-only change', () => {
    const workflow = readFileSync(join(REPO, '.github/workflows/frontend.yml'), 'utf8')
    const blocks = [...workflow.matchAll(/paths:\n((?:\s+- '[^']+'\n)+)/g)].map((match) =>
      [...(match[1] ?? '').matchAll(/- '([^']+)'/g)].map((entry) => entry[1]),
    )

    // Both trigger blocks: `push` and `pull_request`. If one is missing, the count says so.
    expect(blocks).toHaveLength(2)

    for (const globs of blocks) {
      // Literal paths, so this is exact matching rather than a pattern that might drift.
      expect(globs).toContain('docs/PROGRESS.md')
      expect(globs).toContain('docs/HISTORY.md')
    }
  })
})
