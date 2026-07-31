import { useSupplierControllerSuppliers } from '@/api/generated/endpoints/supplier/supplier'
import { useTaxLookupControllerVatClasses } from '@/api/generated/endpoints/tax-lookup/tax-lookup'
import { useUnitOfMeasureControllerUnitsOfMeasure } from '@/api/generated/endpoints/unit-of-measure/unit-of-measure'
import { Section } from '@/api/generated/model'
import { usePermissions } from '@/auth/permissions'

/**
 * Reference data a screen needs but does not own — and the rule for asking for it.
 *
 * **Standing policy: never fire a request for data the current grants do not cover.**
 *
 * A screen is not the same thing as a section. A product references a VAT class and a supplier, and
 * those live under `TAX_AND_CHARGES` and `SUPPLIERS` — so `REMOTE_ORDER_STAFF`, which holds
 * `PRODUCTS` at `VIEW` and nothing else, would receive two 403s for reference data it never asked
 * to see. Firing them anyway produces error toasts about things the operator did not do, and
 * teaches everyone to ignore error toasts.
 *
 * The same applies to every screen that references a party or a tax figure — invoices, receipts,
 * goods receipts — which is why the rule lives here rather than in the products screen.
 *
 * **What a screen shows when a lookup is not permitted is the raw identifier**, never a blank: "VAT
 * class 3" is honest and traceable, while an empty cell says the product has no VAT class, which is
 * false.
 */

export interface Lookup<T> {
  items: T[]
  /** False when the role holds no grant for this reference data. Nothing was requested. */
  permitted: boolean
  isLoading: boolean
}

/** Units of measure. Governed by `PRODUCTS`, so any product screen can always read them. */
export function useUnitsOfMeasure(): Lookup<{ id?: number; name?: string; code?: string }> {
  const permissions = usePermissions()
  const permitted = permissions.canView(Section.PRODUCTS)

  const query = useUnitOfMeasureControllerUnitsOfMeasure(
    { active: true },
    { query: { enabled: permitted, staleTime: 5 * 60_000 } },
  )

  return { items: query.data?.items ?? [], permitted, isLoading: query.isLoading }
}

/** VAT classes. Governed by `TAX_AND_CHARGES` — a different section from the screens that use it. */
export function useVatClasses(): Lookup<{ id?: number; description?: string }> {
  const permissions = usePermissions()
  const permitted = permissions.canView(Section.TAX_AND_CHARGES)

  const query = useTaxLookupControllerVatClasses(
    { active: true },
    { query: { enabled: permitted, staleTime: 5 * 60_000 } },
  )

  return { items: query.data?.items ?? [], permitted, isLoading: query.isLoading }
}

/** Suppliers. Governed by `SUPPLIERS`. */
export function useSuppliers(): Lookup<{ id?: number; name?: string }> {
  const permissions = usePermissions()
  const permitted = permissions.canView(Section.SUPPLIERS)

  const query = useSupplierControllerSuppliers(
    { active: true },
    { query: { enabled: permitted, staleTime: 5 * 60_000 } },
  )

  return { items: query.data?.items ?? [], permitted, isLoading: query.isLoading }
}

/**
 * The name for an id, or the id itself when the name cannot be looked up.
 *
 * Three outcomes, kept distinct: no id at all is "not set"; an id with a permitted lookup is a
 * name; an id without one is the id, so the operator can still say which VAT class it is and ask
 * somebody who can see it.
 */
export function nameFor<T extends { id?: number }>(
  lookup: Lookup<T>,
  id: number | undefined,
  name: (item: T) => string | undefined,
): string | undefined {
  if (id === undefined) return undefined
  if (!lookup.permitted) return `#${id}`
  const found = lookup.items.find((item) => item.id === id)
  return found ? (name(found) ?? `#${id}`) : `#${id}`
}

/**
 * The same reference data, shaped for a select.
 *
 * The value is the id as text because that is what a `<select>` carries; the label falls back to
 * `#id` on the same reasoning as {@link nameFor} — a row with no name is still a row somebody can
 * quote to whoever can see it, and an empty option is one nobody can choose deliberately. An item
 * with no id at all is dropped rather than given a value of `"undefined"`.
 */
export function idOptions<T extends { id?: number }>(
  items: readonly T[],
  label: (item: T) => string | undefined,
): { value: string; label: string }[] {
  return items
    .filter((item) => item.id !== undefined)
    .map((item) => ({ value: String(item.id), label: label(item) ?? `#${item.id}` }))
}
