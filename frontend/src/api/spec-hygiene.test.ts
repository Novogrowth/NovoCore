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

  it('declares every primitive component required, so a mandatory field is knowable from the contract', () => {
    /*
     * **This test used to assert the opposite, and the change is the point.**
     *
     * It read: *"declares required fields on nothing but Money and UnitCost, which is why a body can
     * be refused for a field the types call optional"* — pinning a defect rather than a guarantee,
     * and written to fail in both directions so that the day the backend started describing its
     * bodies, somebody came back here. **That day was 2026-08-01** and this is that visit.
     *
     * What changed: `OpenApiSchema.recordSchema` now marks a record's **primitive** components
     * required. A primitive cannot be null, so on a request it is mandatory —
     * `FAIL_ON_NULL_FOR_PRIMITIVES` refuses an absent one before any handler runs, which is what
     * broke product creation for every user (`NewProduct.serialTracked`) and would have broken
     * account creation the same way (`NewUser.roleId`) — and on a response it is always present, so
     * the same rule is accurate in both directions.
     *
     * **What is still NOT declared, so nobody reads this test as "the contract is now complete":**
     * a reference-typed field that a compact constructor requires (`Required.field` /
     * `requireNonNull`) is mandatory in fact and invisible to the generator, because reflection
     * cannot see inside a constructor body. 28 schemas have one. `NewRole.name` is the readable
     * example: `NewRole` declares no `required` list at all, and sending `{}` to `POST /api/roles`
     * is still refused. That half is queued as its own backend item.
     *
     * Still written to fail in both directions:
     *   - the count drops → the generator stopped declaring something it used to, and a client can
     *     once again omit a field that is mandatory in fact;
     *   - the count climbs a lot → the guarded half landed, and the frontend can stop working around
     *     it (see `product-create.tsx`, which sends `serialTracked` explicitly).
     */
    const schemas = spec.components.schemas as Record<string, { required?: string[] }>
    const declaring = Object.entries(schemas)
      .filter(([, schema]) => (schema.required?.length ?? 0) > 0)
      .map(([name]) => name)

    // The two hand-written value objects are still there, and they are the ones ADR 0005 cares
    // about: a Money without a currency is the defect that decision exists to prevent.
    expect(declaring).toContain('Money')
    expect(declaring).toContain('UnitCost')
    expect(schemas.Money?.required).toEqual(['amount', 'currency'])

    // The two that cost a screen between them, now declared rather than discovered.
    expect(schemas.NewProduct?.required).toContain('serialTracked')
    expect(schemas.NewUser?.required).toContain('roleId')

    expect(
      declaring.length,
      'the number of schemas declaring required fields changed — read the comment above before updating this number',
    ).toBe(78)

    // The guarded half is NOT declared, and this is what says so out loud.
    expect(
      schemas.NewRole?.required,
      'NewRole.name is required by its compact constructor and cannot be seen by reflection — if this is now declared, the guarded half landed',
    ).toBeUndefined()
  })
})
