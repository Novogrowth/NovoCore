import { useCallback, useMemo, useState } from 'react'

import { isServerPaged, serverSorts } from './list-response'

/**
 * The paging and sorting a list screen is currently showing, and the query parameters that follow
 * from it.
 *
 * A screen calls this with its route, spreads `params` into its generated hook, and hands `state`
 * to the table. When the backend gives that endpoint paging, `npm run api:generate` flips the
 * capability and the same screen starts paging on the server — the call site does not change.
 */

export interface ListState {
  page: number
  size: number
  sort?: string
  direction?: 'ASC' | 'DESC'
}

export const DEFAULT_PAGE_SIZE = 25

export interface ListStateHandle {
  state: ListState
  setPage: (page: number) => void
  setSize: (size: number) => void
  setSort: (sort: string | undefined, direction?: 'ASC' | 'DESC') => void
  /**
   * What to send with the request.
   *
   * Empty for an endpoint that does not page: sending `page` and `size` to a route that never
   * declared them is at best ignored and at worst refused, and the spec is what says which it is.
   */
  params: Record<string, string | number>
  serverPaged: boolean
  /**
   * The `sort` values this endpoint accepts, from the generated capability map. Empty when it does
   * not sort on the server.
   *
   * Exposed because a **server-paged** table must not offer a sort the server cannot perform — see
   * `canSortColumn` in `./sorting.ts` for what goes wrong if it does.
   */
  serverSorts: readonly string[]
}

export function useListState(route: string, initial?: Partial<ListState>): ListStateHandle {
  const serverPaged = isServerPaged(route)
  const allowedSorts = serverSorts(route)

  const [state, setState] = useState<ListState>({
    page: initial?.page ?? 0,
    size: initial?.size ?? DEFAULT_PAGE_SIZE,
    ...(initial?.sort ? { sort: initial.sort } : {}),
    ...(initial?.direction ? { direction: initial.direction } : {}),
  })

  /*
   * ⚠️ **Every setter here returns the SAME state object when nothing changed, and must.**
   *
   * React bails out of a re-render only when the next state is `Object.is`-equal to the current
   * one, so `setState((s) => ({ ...s, page }))` re-renders unconditionally — including when it was
   * asked to set the page it is already on. That is not hypothetical: `useReactTable` calls
   * `resetPageIndex()` whenever it rebuilds its row model, which reaches `setPage(0)` on a table
   * already showing page 0. With an unconditional setter that render is the input to the next
   * rebuild, and the tab hangs.
   *
   * `list-state-loop.test.tsx` drives the real table through a filter change and fails on the
   * render count, against either half of this.
   */
  const setPage = useCallback(
    (page: number) => setState((s) => (s.page === page ? s : { ...s, page })),
    [],
  )

  // Changing the page size while on page 7 of 8 can land past the end of the data. Going back to
  // the first page is the only answer that is right for every dataset.
  const setSize = useCallback(
    (size: number) =>
      setState((s) => (s.size === size && s.page === 0 ? s : { ...s, size, page: 0 })),
    [],
  )

  const setSort = useCallback(
    (sort: string | undefined, direction: 'ASC' | 'DESC' = 'ASC') => {
      setState((s) =>
        s.sort === sort && s.direction === direction && s.page === 0
          ? s
          : { ...s, sort, direction, page: 0 },
      )
    },
    [],
  )

  const params = useMemo(() => {
    if (!serverPaged) return {}
    const query: Record<string, string | number> = { page: state.page, size: state.size }
    // A sort value the endpoint does not declare would be refused; the generated map is what
    // knows which ones it declares.
    if (state.sort && allowedSorts.includes(state.sort)) {
      query.sort = state.sort
      query.direction = state.direction ?? 'ASC'
    }
    return query
  }, [serverPaged, state, allowedSorts])

  return { state, setPage, setSize, setSort, params, serverPaged, serverSorts: allowedSorts }
}
