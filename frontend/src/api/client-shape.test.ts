import { readFileSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import spec from '../../../docs/api/openapi.json'

/**
 * Every route is wired the right way round: a read is a query, a write is a mutation.
 *
 * ⚠️ **This test exists because that was once false for all 92 writes at the same time.** A single
 * line in `orval.config.ts` — `query: { useQuery: true }` — forced every operation into a query, so
 * `useProductControllerRename` was a *query hook*: a component that merely rendered it would have
 * sent the PATCH on mount, and again on every refetch, invalidation and window focus. Rendering a
 * screen would have written to the ledger.
 *
 * The config has been fixed. This asserts the **result** across all 175 operations, because a
 * config change is a cause and what matters is the effect — and because the next person to add an
 * orval option should find out immediately if it does this again.
 *
 * The discriminator is orval's own: it emits `get<Name>QueryOptions` for a query and
 * `get<Name>MutationOptions` for a mutation. One or the other, never both.
 */

const GENERATED = resolve(process.cwd(), 'src/api/generated/endpoints')
const HTTP_VERBS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options']

/** Every generated endpoint module, concatenated — the client as one body of text. */
function generatedSource(): string {
  const parts: string[] = []
  for (const directory of readdirSync(GENERATED)) {
    const folder = join(GENERATED, directory)
    for (const file of readdirSync(folder)) {
      if (file.endsWith('.ts')) parts.push(readFileSync(join(folder, file), 'utf8'))
    }
  }
  return parts.join('\n')
}

interface Operation {
  verb: string
  route: string
  operationId: string
}

/**
 * The operations, with the same de-duplication `orval.config.ts` applies.
 *
 * `InventoryController_writeOff` names two operations, which OpenAPI forbids; the config suffixes
 * the verb. Repeating that rule here rather than importing it keeps this test honest — it derives
 * the names it expects from the spec, not from the thing it is checking.
 */
function operations(): Operation[] {
  const found: Operation[] = []
  for (const [path, verbs] of Object.entries(spec.paths as Record<string, unknown>)) {
    for (const [verb, operation] of Object.entries(verbs as Record<string, { operationId?: string }>)) {
      if (!HTTP_VERBS.includes(verb)) continue
      if (!operation.operationId) continue
      found.push({ verb, route: `${verb.toUpperCase()} ${path}`, operationId: operation.operationId })
    }
  }

  const counts = new Map<string, number>()
  for (const operation of found) {
    counts.set(operation.operationId, (counts.get(operation.operationId) ?? 0) + 1)
  }

  return found.map((operation) =>
    (counts.get(operation.operationId) ?? 0) > 1
      ? {
          ...operation,
          operationId: `${operation.operationId}${operation.verb.charAt(0).toUpperCase()}${operation.verb.slice(1)}`,
        }
      : operation,
  )
}

/** `ProductController_rename` → `ProductControllerRename`, which is how orval names its helpers. */
function pascalName(operationId: string): string {
  const camel = operationId
    .replace(/_(.)/g, (_match, letter: string) => letter.toUpperCase())
    .replace(/_/g, '')
  return camel.charAt(0).toUpperCase() + camel.slice(1)
}

describe('the generated client', () => {
  const source = generatedSource()
  const all = operations()
  const reads = all.filter((operation) => operation.verb === 'get')
  const writes = all.filter((operation) => operation.verb !== 'get')

  it('covers the whole surface', () => {
    // An anti-vacuity guard: if the generated output moved, every assertion below would pass
    // against an empty string and report a green build for a check that examined nothing.
    expect(all.length).toBe(230)
    expect(source.length).toBeGreaterThan(100_000)
  })

  it('has writes to check, and knows how many', () => {
    // Stated as a number so that a route disappearing from the spec is visible here too.
    // 70 POST, 40 PATCH, 16 PUT, 8 DELETE as of 2026-08-03, after R1a. Every write was a query
    // before that was fixed.
    //
    // R1a added 54 operations (176 → 230) and 40 writes (94 → 134): 5 on the AADE invoice-type
    // codification, 3 on VAT exemption reasons (which became a statutory codification and gained
    // the three operations that contract permits), 11 each on the two document-type lists, 10 on
    // the sales series and 8 on the purchase series — the difference between those two being the
    // channel PUT/DELETE pair, which purchases deliberately do not have — and 6 on delivery
    // methods.
    expect(writes.length).toBe(134)
    expect(reads.length).toBe(96)
  })

  it('wires every write as a mutation', () => {
    const wrong = writes.filter(
      (operation) => !source.includes(`get${pascalName(operation.operationId)}MutationOptions`),
    )

    expect(
      wrong.map((operation) => operation.route),
      'these writes are not mutations — a component that renders one would send it',
    ).toEqual([])
  })

  it('wires no write as a query', () => {
    // The other direction, and the one that actually bit: a write generated as a query fires on
    // render. Checking only that a mutation exists would pass while both existed.
    const alsoQueries = writes.filter((operation) =>
      source.includes(`get${pascalName(operation.operationId)}QueryOptions`),
    )

    expect(
      alsoQueries.map((operation) => operation.route),
      'these writes are wired as queries and would fire on render',
    ).toEqual([])
  })

  it('wires every read as a query', () => {
    const wrong = reads.filter(
      (operation) => !source.includes(`get${pascalName(operation.operationId)}QueryOptions`),
    )
    expect(wrong.map((operation) => operation.route)).toEqual([])
  })

  it('exposes no useQuery hook for a write, by name', () => {
    /*
     * Belt and braces, at the level a screen actually touches. The checks above read orval's
     * internal helper names; this one reads the exported hook, because that is what a screen
     * imports and what would have to change for the defect to return unnoticed.
     */
    const offenders = writes.filter((operation) => {
      const hook = `use${pascalName(operation.operationId)}`
      // A query hook is declared `export function useX<TData = ...`; a mutation is
      // `export const useX = <TError ...`.
      return source.includes(`export function ${hook}<TData`)
    })

    expect(offenders.map((operation) => operation.route)).toEqual([])
  })
})
