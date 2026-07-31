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

  it('declares required fields on nothing but Money and UnitCost, which is why a body can be refused for a field the types call optional', () => {
    /*
     * **The spec does not say what a request body requires.** 185 schemas, 71 operations with a
     * body, and `required` appears on exactly two value objects. So every generated request type
     * is `Partial`-shaped, and a call site cannot tell a mandatory field from an optional one on
     * any write route in the application.
     *
     * That is not cosmetic, and it has already cost a screen. `NewProduct.serialTracked` is
     * generated as `serialTracked?: boolean` and is mandatory in fact: it is a primitive `boolean`
     * on a Java record, Jackson hands an absent creator property to the constructor as null, and
     * `FAIL_ON_NULL_FOR_PRIMITIVES` refuses it. **Product creation failed for every user, every
     * time**, with `400 "Malformed request body: Cannot map null into type boolean"` — a message
     * naming no field, from a route the spec said the request satisfied. `product-create.tsx`
     * sends the field explicitly until this is fixed.
     *
     * Written to fail in BOTH directions, like the collision above:
     *   - a schema starts declaring `required` → the backend is describing its bodies, so go back
     *     to the workarounds and the generated types and decide what is now knowable;
     *   - one of these two stops   → a value object quietly became optional, and `Money` without a
     *     currency is the defect ADR 0005 exists to prevent.
     */
    const declaring = Object.entries(spec.components.schemas as Record<string, { required?: string[] }>)
      .filter(([, schema]) => (schema.required?.length ?? 0) > 0)
      .map(([name]) => name)
      .sort()

    expect(
      declaring,
      'the set of schemas declaring required fields changed — see the comment above before updating this list',
    ).toEqual(['Money', 'UnitCost'])
  })
})
