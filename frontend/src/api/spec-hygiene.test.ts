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
    // 176 before R1a, which added 54; 230 before R2, which added 7 on 2026-08-04 — see
    // `client-shape.test.ts` for the breakdown by route group.
    expect(all.length).toBe(247)
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

  it('declares every mandatory component required, primitives by inference and the rest by declaration', () => {
    /*
     * **This test has now been rewritten twice, and each rewrite is a step of the same argument.**
     *
     * v1 pinned a defect: *"declares required fields on nothing but Money and UnitCost, which is why
     * a body can be refused for a field the types call optional"*. v2 (2026-08-01) recorded the
     * primitive half landing, and said in writing that the guarded half was still missing. **This is
     * v3, 2026-08-03, and the guarded half has landed** — which is exactly the visit v2 was written
     * to provoke.
     *
     * The contract is now built from two rules rather than one:
     *   - a **primitive** component is required **by inference**. It cannot be null, so on a request
     *     `FAIL_ON_NULL_FOR_PRIMITIVES` refuses an absent one before any handler runs — the defect
     *     that broke product creation for every user — and on a response it is always present.
     *   - a **reference-typed** component required by a compact constructor is required **by
     *     declaration**: `@Mandatory`, read by the same generator, cross-checked against the
     *     canonical constructor's bytecode by `MandatoryDeclarationRulesTest` so the declaration
     *     cannot drift from the guard.
     *
     * **What that bought, and it is why the number below moved so far:** 339 always-present
     * components across 114 records were described as optional and are not any more. 204 of them are
     * on **response** views, so `tsc` now enforces test-fixture completeness — the third row of
     * backend item 9's table, the one no frontend test could ever close honestly because every other
     * candidate source of truth about the wire is hand-authored.
     *
     * **What is still NOT declared, deliberately, so nobody reads this as "the contract is now
     * complete":**
     *   - a component whose requirement is **conditional** carries `@ConditionallyMandatory` and is
     *     left out. `NewPurchaseInvoiceLine` has five such fields of which at most three can ever be
     *     present, selected by `type`; no `required` list can express that, and pretending otherwise
     *     would publish a contract contradicting itself.
     *   - a component made mandatory by an inline `if (x === null) throw` is invisible to the
     *     cross-check and therefore not declared. `EmailMessage.subject` and `.body` are the known
     *     cases; neither is on this surface.
     *
     * Both leave the list **incomplete rather than wrong**, which is the right side of that trade.
     *
     * Still written to fail in both directions:
     *   - the count drops → the generator stopped declaring something it used to, and a client can
     *     once again omit a field that is mandatory in fact;
     *   - the count climbs → something new was declared, and it is worth one look to confirm it is
     *     genuinely never absent rather than merely usually set.
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
     * ⚠️ **Item 7's regression is CLOSED, and this block is what proves it by name rather than by
     * count.** Between 2026-08-01 and 2026-08-03 these eight flags were declared required by
     * accident of being primitives; Q1's item 7 boxed them to improve the refusal message
     * (`"serialTracked" is required and was not supplied.` instead of a field-less `Cannot map null
     * into type boolean`) and removed the declaration as a side effect, taking schemas declaring
     * `required` from 78 to 75. 8a put the declaration back deliberately.
     *
     * **Checked individually and not through the total**, because a count cannot say WHICH field
     * came back — and the whole failure this closes was one specific field on one specific form.
     *
     * ⚠️ **Seven, not eight.** The eighth boxed boolean is
     * `NewVatExemptionReason.inputVatDeductible`, and it has no schema here at all:
     * `/api/vat-exemption-reasons` is GET-only, so the record never reaches this document. It is
     * confirmed annotated and guarded on the backend instead, by
     * `MandatoryDeclarationRulesTest`. Documents saying "the eight" were corrected on 2026-08-03.
     */
    const boxedBooleans: [string, string][] = [
      ['NewProduct', 'serialTracked'],
      ['SerialTrackingRequest', 'serialTracked'],
      ['NewUnitOfMeasure', 'fractionalQuantityAllowed'],
      ['NewAccount', 'expectedToClear'],
      ['NewSettlement', 'remainderBecomesCustomerCredit'],
      ['NewCreditNoteLine', 'stockReturned'],
      ['NewPurchaseInvoiceLine', 'reverseCharge'],
    ]
    for (const [schema, field] of boxedBooleans) {
      expect(
        schemas[schema]?.required,
        `${schema}.${field} is refused by the server when omitted; if the contract stops saying so, tsc stops refusing a caller that omits it — which is exactly what broke product creation`,
      ).toContain(field)
    }

    /*
     * The guarded half, which v2 of this test asserted was MISSING. `NewRole.name` is required by
     * `Required.text` in its compact constructor, is invisible to reflection, and is now declared
     * because `@Mandatory` says so and the bytecode cross-check agrees.
     */
    expect(
      schemas.NewRole?.required,
      'NewRole.name is required by its compact constructor; 8a is what made that knowable from the contract',
    ).toContain('name')

    /*
     * ⚠️ **The four schema-name collisions Q1-a raised are split, and this is the one that was
     * never merely latent.** Seven distinct `NameRequest` records across seven controllers resolved
     * to ONE schema serving NINE operations. Two of them guard `name` and five do not — so the
     * moment 8a declared guards, the single merged schema would have been wrong for at least two of
     * the nine whichever way it went. `OpenApiSchema.claim` now refuses a collision outright.
     */
    expect(schemas.NameRequest, 'the merged NameRequest schema must no longer exist').toBeUndefined()
    expect(schemas.RoleNameRequest?.required).toContain('name')
    expect(schemas.UnitOfMeasureNameRequest?.required).toContain('name')
    expect(
      schemas.CustomerNameRequest?.required,
      'CustomerController.NameRequest has no compact constructor, so its name is NOT required — which is the disagreement the merged schema was hiding',
    ).toBeUndefined()

    /*
     * 75 before 8a, 143 after it, and 167 after R1a — which added 24 schemas that declare
     * `required`, every one of them a new record rather than a change to an existing contract.
     *
     * 170 after R2 (2026-08-04): three new request records — `AbbreviationRequest`,
     * `SeriesDocumentTypeRequest`, `GetsMarkRequest`. ⚠️ The three views R2 also changed
     * (`SalesDocumentSeriesView`, `PurchaseDocumentSeriesView`, `DeliveryMethodView`) each gained a
     * required component, `inUse`, but were ALREADY declaring — so they move the property count and
     * not this one. That is why +7 routes and +3 here is the right arithmetic rather than a
     * mismatch.
     *
     * 175 after R2b (2026-08-04): `SortCodeRequest`, `PaymentMethodView`, and the two payment-method
     * request records, plus `PaymentMethodSortCodeRequest`. The four document records that gained
     * `sortCode` were ALREADY declaring, so they move the property count and not this one.
     *
     * ⚠️ The direction matters more than the number. A DROP here means a record stopped guarding
     * something, which makes every consumer's non-optional field a lie; a RISE is ordinary.
     */
    expect(
      declaring.length,
      'the number of schemas declaring required fields changed — read the comment above before updating this number',
    ).toBe(175)
  })
})
