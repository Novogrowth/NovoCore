import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type OnChangeFn,
  type PaginationState,
  type SortingState,
} from '@tanstack/react-table'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { CaretLeftIcon, CaretRightIcon } from '@/components/icons'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

import { unwrapList, type TableData } from './list-response'
import { canSortColumn, textSorting } from './sorting'
import { DEFAULT_PAGE_SIZE, type ListStateHandle } from './use-list-state'

/**
 * One table for every list in NovoCore.
 *
 * It takes either shape a list endpoint can return — a paginated response or the whole array — and
 * pages accordingly. The switch is not a prop anyone sets: `useListState` reads the endpoint's
 * capability from the generated map, and the presence of a `page` object in the response confirms
 * it. So an endpoint that gains server-side paging on the backend gains it here on the next
 * `npm run api:generate`, with no component rewritten.
 */

/**
 * The unsorted state, shared.
 *
 * The same argument as `NO_ROWS` in `list-response.ts`, one state along: a `[]` literal is a new
 * identity every render, and this value is handed to the table as state.
 */
const NOT_SORTED: SortingState = []

/**
 * A column's id as TanStack will compute it: its own `id`, or the key it accesses.
 *
 * Needed because sorting state names a column by id, and half of these definitions give one
 * explicitly while the other half let it be derived from `accessorKey`.
 */
function columnIdOf<T>(column: ColumnDef<T, unknown>): string {
  return column.id ?? ('accessorKey' in column ? String(column.accessorKey) : '')
}

/**
 * Which column a backend sort constant belongs to.
 *
 * The reverse of `meta.sortKey`, so a sort restored from the request (a URL, a remembered
 * preference) lights up the right header. An unmatched key yields `''`, which matches no column —
 * the table then shows no sorted header, which is honest: the server is ordering by something this
 * screen does not display.
 */
function columnForSortKey<T>(columns: ColumnDef<T, unknown>[], sortKey: string): string {
  const match = columns.find((column) => column.meta?.sortKey === sortKey)
  return match === undefined ? '' : columnIdOf(match)
}

interface DataTableProps<T> {
  data: TableData<T>
  columns: ColumnDef<T, unknown>[]
  /** From `useListState(route)`. Absent for a table that never pages, such as a short lookup. */
  list?: ListStateHandle
  isLoading?: boolean
  emptyMessage?: string
  /** Rows per page when the table pages in the browser and no list handle owns the state. */
  pageSize?: number
  /** Stable row identity, so selection and expansion survive a refetch. */
  getRowId?: (row: T, index: number) => string
}

export function DataTable<T>({
  data,
  columns,
  list,
  isLoading = false,
  emptyMessage,
  pageSize = DEFAULT_PAGE_SIZE,
  getRowId,
}: DataTableProps<T>) {
  const { t } = useTranslation()
  const { rows, serverPage } = unwrapList(data)

  // The server paged it if it said so in the response. `list?.serverPaged` is what the spec says
  // the endpoint can do; this is what the endpoint actually did, and they only disagree while a
  // backend change is landing.
  const pagedByServer = serverPage !== undefined

  /*
   * Which side sorts, and it follows from which side pages.
   *
   * Sorting rows the browser happens to be holding is only the same thing as sorting the list when
   * the browser is holding all of them. Once the server pages, a client sort would reorder one page
   * and present it as the order of the whole table — convincing, and wrong. So a server-paged list
   * sorts through the request, using the values the endpoint declares, and a column it does not
   * declare stops being sortable rather than sorting a page. `canSortColumn` states this once.
   *
   * ⚠️ The capability, not the response, decides — unlike paging above. Paging can be read back off
   * the answer because a paged response carries a `page` object; **an ordering leaves no trace in
   * the response at all**, so there is nothing to confirm it with. The generated map is the only
   * thing that knows, which is also why it must be regenerated when the backend changes.
   */
  const sortedByServer = list?.serverPaged ?? false
  const serverSorts = list?.serverSorts ?? []
  const { sort, direction } = list?.state ?? {}

  const sortableColumns = columns.map((column) => {
    const id = columnIdOf(column)
    const canSort = canSortColumn(column.meta?.sortKey, sortedByServer, serverSorts)
    return canSort ? column : { ...column, id, enableSorting: false }
  })

  /*
   * A client sort's state lives here rather than in `useListState`, deliberately: it is not part of
   * the request, so putting it there would make it part of the query key and refetch the list to
   * reorder rows already in hand. A server sort's state does live there, because there it *is* the
   * request.
   */
  const [clientSorting, setClientSorting] = useState<SortingState>([])

  /*
   * ⚠️ **Memoised because it is table state, and table state must not be freshly allocated.**
   *
   * This is the rule `frontend/README.md` states for filter state, arriving at a second door.
   * `getSortedRowModel` memoises on the *identity* of `state.sorting`, and its `onChange` calls
   * `_autoResetPageIndex()` — so a new array every render re-runs it every render. Today that
   * happens to stop there, because `autoResetPageIndex` defaults to `!manualPagination` and this
   * branch always sets `manualPagination`. Depending on that is how the original render loop got
   * built: a chain that terminates for a reason nobody wrote down, until one link changes.
   */
  const serverSorting = useMemo<SortingState>(
    () =>
      sort === undefined
        ? NOT_SORTED
        : [{ id: columnForSortKey(columns, sort), desc: direction === 'DESC' }],
    // `columns` is rebuilt by the screen on every render, so naming it here would defeat the memo
    // and reintroduce exactly what this exists to prevent. The lookup only depends on the sort key.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [sort, direction],
  )

  const sorting = sortedByServer ? serverSorting : clientSorting

  const onSortingChange: OnChangeFn<SortingState> = (updater) => {
    const next = typeof updater === 'function' ? updater(sorting) : updater
    if (!sortedByServer) {
      setClientSorting(next)
      // Reordering while on page 3 leaves the reader looking at rows that have nothing to do with
      // what they clicked. `setPage` is a no-op when already on page 0 — see `use-list-state.ts`.
      list?.setPage(0)
      return
    }
    const first = next[0]
    if (first === undefined) {
      list?.setSort(undefined)
      return
    }
    const sortKey = columns.find((column) => columnIdOf(column) === first.id)?.meta?.sortKey
    list?.setSort(sortKey, first.desc ? 'DESC' : 'ASC')
  }

  /*
   * Three arrangements, and only one of them is a choice made here.
   *
   * The server paged it   → the table is told the page count and asks for pages; it must not
   *                         conclude there is one page from the one page of data it can see.
   * Paging in the browser,
   *   with a list handle  → the screen owns the page number, so it survives a refetch and can be
   *                         put in the URL later.
   *   without one         → the table keeps its own. A short lookup list needs no ceremony.
   */
  const pagination: PaginationState | undefined = pagedByServer
    ? { pageIndex: serverPage.page ?? 0, pageSize: serverPage.size ?? pageSize }
    : list
      ? { pageIndex: list.state.page, pageSize: list.state.size }
      : undefined

  const onPaginationChange: OnChangeFn<PaginationState> | undefined = list
    ? (updater) => {
        const current = { pageIndex: list.state.page, pageSize: list.state.size }
        const next = typeof updater === 'function' ? updater(current) : updater
        if (next.pageSize !== list.state.size) list.setSize(next.pageSize)
        else list.setPage(next.pageIndex)
      }
    : undefined

  const table = useReactTable({
    data: rows as T[],
    columns: sortableColumns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange,
    /*
     * Two defaults, and each is here rather than on every column because the cost of forgetting it
     * on one column is a table that is quietly wrong in one place.
     *
     * `sortingFn` — the collator. TanStack's own default is `'auto'`, which picks a comparator from
     * the first value it sees and lands on code-unit order for text: the exact ordering this step
     * exists to stop showing people.
     *
     * `sortUndefined` — "not set" goes last **in both directions**. A descending sort that opens on
     * a screen of blanks reads as a broken table, and the blanks are never what was asked for.
     */
    defaultColumn: { sortingFn: textSorting, sortUndefined: 'last' },
    /*
     * ⚠️ **Every column starts ascending, and this is a correctness fix rather than a preference.**
     *
     * Left alone, TanStack decides a column's first direction with `getAutoSortDir()`, which reads
     * **the value in row zero**: a string starts ascending, anything else starts descending. So the
     * direction of a user's first click depends on which record happens to be at the top of the
     * list at that moment — and a column whose first row is empty flips direction when the data
     * changes underneath it, with no rule anybody could infer from the screen. The header's own
     * label would follow it, so the control would be announcing a different action on Tuesday.
     *
     * Ascending first, always. Descending is one more click away and is always reachable.
     */
    sortDescFirst: false,
    ...(sortedByServer ? { manualSorting: true } : {}),
    ...(pagedByServer
      ? { manualPagination: true, pageCount: serverPage.totalPages ?? -1 }
      : { getPaginationRowModel: getPaginationRowModel() }),
    ...(pagination
      ? { state: { pagination, sorting } }
      : { state: { sorting }, initialState: { pagination: { pageIndex: 0, pageSize } } }),
    ...(onPaginationChange ? { onPaginationChange } : {}),
    ...(getRowId ? { getRowId } : {}),
  })

  const pageCount = pagedByServer ? (serverPage.totalPages ?? 1) : table.getPageCount()
  const pageIndex = table.getState().pagination.pageIndex

  /*
   * A server-paged response with no list handle has nowhere to put the next page number, so the
   * buttons would render enabled and do nothing — a control that looks broken rather than one that
   * is absent. Showing the position without the buttons is the honest version: the caller passes a
   * handle from `useListState` when it wants the table to page.
   */
  const canPage = !pagedByServer || list !== undefined
  const showPager = pageCount > 1

  return (
    <div className="space-y-3">
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    // The cell carries the sort state, not the button: `aria-sort` is defined on
                    // the column header cell, and a screen reader announces it when the row is
                    // read rather than only when the control is focused.
                    aria-sort={
                      !header.column.getCanSort()
                        ? undefined
                        : header.column.getIsSorted() === 'asc'
                          ? 'ascending'
                          : header.column.getIsSorted() === 'desc'
                            ? 'descending'
                            : 'none'
                    }
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 3 }).map((_, rowIndex) => (
                <TableRow key={`skeleton-${rowIndex}`}>
                  {columns.map((_column, columnIndex) => (
                    <TableCell key={columnIndex}>
                      <Skeleton className="h-4 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="text-muted-foreground h-24 text-center">
                  {emptyMessage ?? t('table.empty')}
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {showPager && (
        <div className="flex items-center justify-between gap-4">
          <span className="text-muted-foreground text-sm">
            {t('table.page', { page: pageIndex + 1, pages: pageCount })}
            {/* Which side did the paging. Worth showing while most endpoints do not page: a
                table of 4,000 rows paged in the browser is correct but slow, and that is a
                backend item rather than a mystery. */}
            <span className="ml-2 text-xs opacity-70">
              {pagedByServer ? t('table.serverPaged') : t('table.clientPaged')}
            </span>
          </span>
          {canPage && (
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => table.previousPage()}
                disabled={!table.getCanPreviousPage()}
              >
                <CaretLeftIcon /> {t('table.previous')}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => table.nextPage()}
                disabled={pagedByServer ? !(serverPage.hasNext ?? false) : !table.getCanNextPage()}
              >
                {t('table.next')} <CaretRightIcon />
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
