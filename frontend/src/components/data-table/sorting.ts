import type { Row, RowData } from '@tanstack/react-table'

import { compareDecimal, compareMoney, compareText } from '@/lib/collation'

/**
 * The comparators a column may sort with, and the rule for which columns may sort at all.
 *
 * Ordering itself lives in `@/lib/collation` — this is the thin layer that hands it to the table.
 */

declare module '@tanstack/react-table' {
  // Both type parameters have to be restated to match the interface being augmented, and neither is
  // used by the member added below — which is what the rule is objecting to.
  // eslint-disable-next-line unused-imports/no-unused-vars
  interface ColumnMeta<TData extends RowData, TValue> {
    /**
     * The backend sort constant this column corresponds to, when the endpoint has one.
     *
     * Absent means "the server cannot order by this". That is not decoration: it is what
     * {@link canSortColumn} reads to decide whether a header on a **server-paged** list may be
     * clicked at all.
     */
    sortKey?: string
  }
}

/**
 * Text, in the order a person reads it. See `collation.ts` for what that means and why.
 *
 * **This is the table's default comparator**, set once on `DataTable`'s `defaultColumn` rather than
 * named on each column. A column that says nothing about how it sorts should sort the way a reader
 * expects; leaving TanStack's `'auto'` in place would give code-unit order to every column somebody
 * forgot to annotate, which is the same defect as the database's and just as quiet.
 */
export const textSorting = <TData extends RowData>(
  rowA: Row<TData>,
  rowB: Row<TData>,
  columnId: string,
): number =>
  compareText(rowA.getValue<string | undefined>(columnId), rowB.getValue<string | undefined>(columnId))

/** A bare wire decimal — a quantity or a rate — compared as a number, never as text. */
export const decimalSorting = <TData extends RowData>(
  rowA: Row<TData>,
  rowB: Row<TData>,
  columnId: string,
): number =>
  compareDecimal(rowA.getValue<string | undefined>(columnId), rowB.getValue<string | undefined>(columnId))

/** `Money` or `UnitCost`: grouped by currency, then ordered by amount. */
export const moneySorting = <TData extends RowData>(
  rowA: Row<TData>,
  rowB: Row<TData>,
  columnId: string,
): number =>
  compareMoney(
    rowA.getValue<{ amount?: string; currency?: string } | undefined>(columnId),
    rowB.getValue<{ amount?: string; currency?: string } | undefined>(columnId),
  )

/**
 * Whether a column may be sorted, given who is doing the paging.
 *
 * ⚠️ **This is the whole point of the module, and getting it wrong is silent.** The two cases are
 * not variations on a theme:
 *
 * - **The list is paged in the browser.** Every row is already here, so sorting them here sorts the
 *   list. Any column the screen marked sortable may sort.
 * - **The server paged it.** The browser holds one page out of many, and sorting *that* would
 *   reorder twenty-five rows and present the result as the order of four thousand — a table that
 *   looks perfectly convincing and answers the wrong question. So a column may sort **only** if the
 *   endpoint declares a matching `sort` value, in which case the request carries it and the server
 *   does the ordering. A column with no `sortKey`, or one the endpoint does not offer, renders as
 *   plain text rather than as a control that would mislead.
 *
 * Today this always takes the first branch — all five list endpoints return their rows whole, and
 * the generated capability map has `{ paged: false, sorts: [] }` for each. The second branch is
 * built and tested now because the day it starts applying is the day the backend adds paging, and
 * nothing on this side would change to mark the occasion.
 */
export function canSortColumn(
  sortKey: string | undefined,
  serverPaged: boolean,
  serverSorts: readonly string[],
): boolean {
  if (!serverPaged) return true
  return sortKey !== undefined && serverSorts.includes(sortKey)
}
