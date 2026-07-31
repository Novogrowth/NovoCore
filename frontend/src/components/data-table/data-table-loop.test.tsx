import { useQuery } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ColumnDef } from '@tanstack/react-table'
import { delay, http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { Component, useState, type ReactNode } from 'react'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { apiRequest } from '@/api/http'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'

import { DataTable } from './data-table'
import { unwrapList } from './list-response'
import { useListState } from './use-list-state'

/**
 * A table must not re-render itself.
 *
 * **The defect this exists for wedged the whole tab, not just this component.** Changing a filter
 * changes the query key, so the query holds no data while it refetches; `unwrapList` answered that
 * with a **freshly allocated** `[]`; `useReactTable` memoises its core row model on the identity of
 * `data`, and rebuilding that model calls `_autoResetPageIndex()`, which reaches `setPage(0)` on a
 * table already on page 0; and `useListState`'s setter returned a **new state object anyway**, so
 * React could not bail out. The re-render allocated the next `[]` and the cycle closed.
 *
 * React flushes that cycle in a microtask, so it does not merely make the page slow — in a real
 * browser **the event loop never runs again.** The in-flight response that would have ended it can
 * never be delivered, every click and keystroke after it is discarded, and the browser reports the
 * tab as unresponsive. It was reproduced in headless Chrome and Firefox against the running stack,
 * and it had nothing to do with how many rows there were: an empty response wedged identically.
 *
 * Three things about the shape of this test are deliberate, and each was necessary to make it fail
 * against the defect rather than pass:
 *
 * - **The handler waits 50 ms, and that is load-bearing.** Answered instantly, `msw` resolves
 *   inside the same microtask checkpoint the reset is queued on, so the query holds no data for
 *   exactly one render and the cycle never closes — measured at 3 renders with the defect fully
 *   present. With a delay that a real network makes unavoidable, the same code renders **84**
 *   times against 4 when fixed. A test for this that does not wait cannot fail.
 * - **The counter throws** rather than letting the test assert afterwards. A test that only counted
 *   would hang instead of failing, because the loop starves the timers `waitFor` runs on. Throwing
 *   unwinds to the boundary, unmounts the subtree, and gives the event loop back.
 * - **The screen below is a stand-in, not Products.** Every list screen is built from these two
 *   pieces, so the guard belongs to the pieces. `products.test.tsx` covers the real filter on the
 *   real screen; this covers the mechanism for every screen built after it.
 */

/*
 * Four renders is what one filter change costs when this is working, and 84 is what it cost when it
 * was not. A budget in between, nowhere near either, cannot fail for an ordinary extra render —
 * which is what keeps it from being a test somebody deletes.
 */
const RENDER_BUDGET = 25

/** Long enough that the response cannot land inside the microtask the reset is queued on. */
const RESPONSE_MS = 50

interface Row {
  id: number
  name: string
}

const columns: ColumnDef<Row, unknown>[] = [
  { accessorKey: 'id', header: 'Id' },
  { accessorKey: 'name', header: 'Name' },
]

/** Active-only answers with one row and everything with another, so a refetch is visible. */
const server = setupServer(
  http.get('http://localhost/api/things', async ({ request }) => {
    await delay(RESPONSE_MS)
    const active = new URL(request.url).searchParams.get('active')
    return HttpResponse.json({
      items: active === 'true' ? [{ id: 1, name: 'Active row' }] : [{ id: 2, name: 'Every row' }],
    })
  }),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

class RenderLoop extends Error {}

let renders = 0
let caught: Error | undefined

/**
 * The shape every list screen has: a filter in local state, `useListState`, one query whose key
 * carries the filter, and `DataTable`. Written out rather than imported so this test keeps working
 * when the screens change.
 */
function FilteredTable() {
  /*
   * Counting renders means writing to a module variable from inside one, which React Compiler's
   * `globals` rule exists to stop — for good reason, since it is exactly what makes a component
   * unmemoisable. Here it is the measurement, and there is no pure way to take it: a ref written
   * during render is the same impurity with the warning switched off.
   */
  // eslint-disable-next-line react-hooks/globals
  renders += 1
  if (renders > RENDER_BUDGET) {
    throw new RenderLoop(`rendered ${renders} times for one filter change`)
  }

  const [activeOnly, setActiveOnly] = useState(true)
  const list = useListState('GET /api/things')

  const query = useQuery({
    // The generated clients key on the url and the parameters, so a filter change is a key change
    // — and a key change is what leaves the query with no data while it refetches.
    queryKey: ['/api/things', { active: activeOnly, ...list.params }],
    queryFn: () =>
      apiRequest<{ items: Row[] }>({
        url: 'http://localhost/api/things',
        method: 'GET',
        params: { active: activeOnly },
      }),
  })

  return (
    <>
      <button onClick={() => setActiveOnly((previous) => !previous)}>Toggle</button>
      <DataTable
        data={query.data}
        columns={columns}
        list={list}
        isLoading={query.isLoading}
        getRowId={(row) => String(row.id)}
      />
    </>
  )
}

/**
 * Catches the counter's throw so the test can assert on it.
 *
 * The error is held in real component state, not only in the module variable: a boundary whose
 * `getDerivedStateFromError` changes nothing re-renders its children unchanged, they throw again,
 * and React eventually gives up and unmounts the whole tree — which fails the test for the wrong
 * reason and says nothing about how many renders there were.
 */
class Boundary extends Component<{ children: ReactNode }, { error?: Error }> {
  override state: { error?: Error } = {}

  static getDerivedStateFromError(error: Error) {
    caught = error
    return { error }
  }

  override render() {
    return this.state.error ? <p>{this.state.error.message}</p> : this.props.children
  }
}

function renderTable() {
  renders = 0
  caught = undefined
  return render(
    <AppQueryProvider>
      <Boundary>
        <FilteredTable />
      </Boundary>
    </AppQueryProvider>,
  )
}

/** The budget covers the filter change alone, not the mount that preceded it. */
async function toggleFilter(user: ReturnType<typeof userEvent.setup>) {
  renders = 0
  await user.click(screen.getByRole('button', { name: 'Toggle' }))
}

describe('a table over a query whose parameters change', () => {
  it('settles instead of re-rendering itself for ever', async () => {
    const user = userEvent.setup()
    renderTable()
    await screen.findByText('Active row')

    await toggleFilter(user)

    // Either the refetch lands, or the budget trips and the boundary catches. Waiting on both is
    // what makes this fail rather than hang when the loop is present.
    await waitFor(() => expect(caught ?? screen.queryByText('Every row')).toBeTruthy())

    expect(caught?.message, 'the table re-rendered itself in a loop').toBeUndefined()
    expect(renders).toBeLessThan(RENDER_BUDGET)
  })

  it('does not re-render for ever when the response is empty either', async () => {
    // The wedge was never about row data. Every check before the development database had any
    // products saw exactly this response, and it wedged on the same toggle.
    server.use(
      http.get('http://localhost/api/things', async () => {
        await delay(RESPONSE_MS)
        return HttpResponse.json({ items: [] })
      }),
    )

    const user = userEvent.setup()
    renderTable()
    await screen.findByText('Nothing to show.')

    await toggleFilter(user)
    await waitFor(() => expect(caught ?? screen.queryByText('Nothing to show.')).toBeTruthy())

    expect(caught?.message, 'the table re-rendered itself in a loop').toBeUndefined()
    expect(renders).toBeLessThan(RENDER_BUDGET)
  })
})

/**
 * The half that the behavioural test above cannot attribute.
 *
 * Either invariant alone breaks the cycle, so reverting one of them still passes the render-budget
 * test — it only fails when both are gone. Each is a one-line rule that reads as an arbitrary style
 * choice to whoever finds it next, so each is stated on its own where it can fail by itself. The
 * setter's three live in `use-list-state.test.ts`, next to the rest of that hook's behaviour.
 */
describe('the empty result, stated on its own', () => {
  it('is the same array every time, not an equal one', () => {
    expect(unwrapList(undefined).rows).toBe(unwrapList(undefined).rows)
    expect(unwrapList({ items: undefined }).rows).toBe(unwrapList(undefined).rows)
  })
})
