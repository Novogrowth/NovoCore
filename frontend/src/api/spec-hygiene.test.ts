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
    expect(all.length).toBe(176)
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

  it('declares a unique operationId on every operation', () => {
    /*
     * **This test used to assert the opposite, and the change is the point.**
     *
     * It read *"has exactly one known duplicate operationId, which is a backend defect"* and pinned
     * `InventoryController_writeOff` — used by both `POST /api/inventory/write-offs` and
     * `GET /api/inventory/write-offs/{id}`, because OpenApiSpecIT derives the id from
     * Controller_method and those were two Java methods of the same name. `orval.config.ts` worked
     * around it by suffixing the HTTP verb, and this assertion was written to fail in BOTH
     * directions — including on the day the collision became empty, so that somebody came back and
     * deleted the workaround rather than leaving it to rot.
     *
     * **That day was 2026-08-03** (backend queue item 1) and this is that visit. The POST handler is
     * `createWriteOff`; the read keeps the singular noun. More importantly the CAUSE is closed:
     * `OpenApiSpecIT` now refuses to write a spec containing a duplicate, so this can no longer
     * arrive silently. The de-duplication block in `orval.config.ts` is deleted.
     *
     * Kept rather than removed with it, because the two guards catch different things: the backend
     * one stops an invalid spec being GENERATED, this one stops an invalid spec being CONSUMED —
     * and the frontend is where the symptom actually appeared, as twenty duplicate-identifier
     * errors in one generated file.
     */
    const byId = new Map<string, string[]>()
    for (const { route, operation } of all) {
      if (!operation.operationId) continue
      byId.set(operation.operationId, [...(byId.get(operation.operationId) ?? []), route])
    }

    const duplicates = [...byId.entries()]
      .filter(([, routes]) => routes.length > 1)
      .map(([operationId, routes]) => `${operationId} → ${routes.join(', ')}`)

    expect(
      duplicates,
      'OpenAPI requires operationId to be unique; orval will emit two hooks with one name and the client will not compile',
    ).toEqual([])
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

    // The id half still holds and is the larger half: 20 `long` ids across the surface stay
    // primitive deliberately, because a form always sends an id — it came from a select the
    // operator had to choose.
    expect(schemas.NewUser?.required).toContain('roleId')

    /*
     * ⚠️ **`NewProduct.serialTracked` is NO LONGER declared required, and that is a KNOWN,
     * TIME-BOXED REGRESSION rather than a drift.** Read this before "fixing" it.
     *
     * Backend queue item 7 (2026-08-03) boxed the seven boolean primitives, so the server now
     * answers `"serialTracked" is required and was not supplied.` instead of the field-less
     * `Cannot map null into type boolean` that broke product creation for every user. But
     * `OpenApiSchema` declares a component required when it `isPrimitive()`, and a boxed `Boolean`
     * is not — so the same edit that improved the MESSAGE removed the DECLARATION. Measured, not
     * reasoned: schemas declaring `required` went 78 → 75.
     *
     * **What is lost is the compile-time catch, not the refusal.** The server still refuses an
     * omitted flag, and more legibly than before. What `tsc` no longer does is refuse a TypeScript
     * caller that omits one.
     *
     * **What closes it is step 8a**, the `@Mandatory` annotation read by the same generator — which
     * is scheduled immediately after Q1 and BEFORE R1 precisely so this window is one step long.
     * No screen is built inside that window: the order is Q1 → 8a/8b → R1 → R2 → F5, and F5–F8 are
     * the steps that would send these bodies.
     *
     * ⚠️ **`product-create.tsx` must therefore keep sending `serialTracked` explicitly.** Its
     * comment was updated on 2026-08-03; it had claimed omission was a compile error, which was
     * true between 2026-08-01 and this change and is not true now.
     */
    expect(
      schemas.NewProduct?.required,
      'if serialTracked is declared again, 8a landed — delete this note and the explicit send in product-create.tsx',
    ).not.toContain('serialTracked')

    expect(
      declaring.length,
      'the number of schemas declaring required fields changed — read the comment above before updating this number',
    ).toBe(75)

    // The guarded half is NOT declared, and this is what says so out loud.
    expect(
      schemas.NewRole?.required,
      'NewRole.name is required by its compact constructor and cannot be seen by reflection — if this is now declared, the guarded half landed',
    ).toBeUndefined()
  })
})
