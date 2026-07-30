import { describe, expect, it } from 'vitest'

import spec from '../../../docs/api/openapi.json'

/**
 * Facts about the committed spec that the generated client depends on.
 *
 * The spec is the backend's artefact and this frontend does not edit it. What it can do is fail
 * loudly when something in it stops holding, rather than discovering it as an unexplained
 * compilation error inside 400 generated files.
 */

interface Operation {
  operationId?: string
  'x-novocore-section'?: string | null
  'x-novocore-level'?: string
}

const HTTP_VERBS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options']

function operations(): { route: string; operation: Operation }[] {
  const found: { route: string; operation: Operation }[] = []
  for (const [path, verbs] of Object.entries(spec.paths as Record<string, unknown>)) {
    for (const [verb, operation] of Object.entries(verbs as Record<string, Operation>)) {
      if (!HTTP_VERBS.includes(verb)) continue
      found.push({ route: `${verb.toUpperCase()} ${path}`, operation })
    }
  }
  return found
}

describe('the committed OpenAPI spec', () => {
  const all = operations()

  it('describes the surface the client is generated from', () => {
    expect(all.length).toBe(174)
  })

  it('declares a section and a level on every route', () => {
    // This is what the navigation tree and the permission gate are checked against. A route
    // missing them would silently drop out of those checks rather than fail them.
    const undeclared = all.filter(
      ({ operation }) =>
        !('x-novocore-section' in operation) || !('x-novocore-level' in operation),
    )
    expect(undeclared.map((entry) => entry.route)).toEqual([])
  })

  it('has exactly one known duplicate operationId, which is a backend defect', () => {
    /*
     * OpenAPI requires operationId to be unique. `InventoryController_writeOff` is used by both
     * `POST /api/inventory/write-offs` and `GET /api/inventory/write-offs/{id}`, because
     * OpenApiSpecIT derives the id from Controller_method and those are two Java methods of the
     * same name. orval.config.ts works around it by suffixing the HTTP verb.
     *
     * This assertion is written to fail in BOTH directions on purpose:
     *   - a NEW collision appears  → the workaround silently renamed something else, and the
     *     generated hook names moved without anyone deciding they should;
     *   - this collision is FIXED  → the workaround is dead code, and this test says so instead
     *     of leaving it to be found years later.
     */
    const byId = new Map<string, string[]>()
    for (const { route, operation } of all) {
      if (!operation.operationId) continue
      byId.set(operation.operationId, [...(byId.get(operation.operationId) ?? []), route])
    }

    const duplicates = [...byId.entries()]
      .filter(([, routes]) => routes.length > 1)
      .map(([operationId]) => operationId)

    expect(
      duplicates,
      'if this is now empty, the backend fixed it — delete the de-duplication block in orval.config.ts and this test',
    ).toEqual(['InventoryController_writeOff'])
  })
})
