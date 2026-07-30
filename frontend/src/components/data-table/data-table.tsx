import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  useReactTable,
  type ColumnDef,
  type OnChangeFn,
  type PaginationState,
} from '@tanstack/react-table'
import { CaretLeftIcon, CaretRightIcon } from '@phosphor-icons/react'
import { useTranslation } from 'react-i18next'

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
    columns,
    getCoreRowModel: getCoreRowModel(),
    ...(pagedByServer
      ? { manualPagination: true, pageCount: serverPage.totalPages ?? -1 }
      : { getPaginationRowModel: getPaginationRowModel() }),
    ...(pagination
      ? { state: { pagination } }
      : { initialState: { pagination: { pageIndex: 0, pageSize } } }),
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
                  <TableHead key={header.id}>
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
