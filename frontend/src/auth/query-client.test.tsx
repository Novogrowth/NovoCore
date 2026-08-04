import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { AccessLevel, type Me, type SalesDocumentTypeView } from '@/api/generated/model'
import '@/i18n'
import {
  SalesDocumentTypeCreate,
  SalesDocumentTypesList,
} from '@/pages/sales-document-types/sales-document-types'
import { aUser, everySectionAt } from '@/test/fixtures'

import { AppQueryProvider, createQueryClient } from './query-client'

/**
 * ⚠️ **A list must show a record that was just created — and for thirteen create forms it did not.**
 *
 * <h2>The defect, which is older than the step that found it</h2>
 *
 * Measured 2026-08-04 (R2b): **not one** of this application's create forms invalidated its list.
 * They mutate and navigate, and `staleTime` is 30 seconds — so a list revisited inside that window
 * is served from cache with the new row missing, and a list revisited after it looks fine. **The bug
 * heals itself in half a minute**, which is why seven screens shipped with it and why it reads as
 * intermittent rather than as a bug.
 *
 * R2 did not introduce it. R2 **copied the pattern faithfully, including the defect** — and then made
 * it constant, because its screens are the first ones somebody creates fifty records in.
 *
 * <h2>⚠️ Why there are TWO tests here, and why the structural one is the important one</h2>
 *
 * The fix is a single global `MutationCache.onSuccess`. That is the right shape — thirteen copies of
 * a line that must never be forgotten is what produced the defect — but it has a consequence for
 * testing that is easy to miss: **with the fix global, deleting it leaves every screen test in this
 * repository passing.** No per-screen test can see it, because no screen contains it.
 *
 * So:
 *
 * - **`the client wires…`** asserts the handler is *present*. Cheap, and it is what turns removing
 *   the block into a red build.
 * - **`a created row appears…`** asserts it *works*, end to end, through a real
 *   {@link createQueryClient} with its real 30-second `staleTime` — not a test client with caching
 *   turned off, which would pass against the defect and prove nothing.
 *
 * ⭐ **Both were proven red against the pre-fix behaviour before being accepted.**
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

const existing: SalesDocumentTypeView = {
  id: 1,
  description: 'Απόδειξη Λιανικής',
  affectsStock: true,
  transfersStock: true,
  requiresMydataTransmission: true,
  sortCode: 10,
  active: true,
}

const created: SalesDocumentTypeView = {
  id: 2,
  description: 'Τιμολόγιο Πώλησης',
  affectsStock: false,
  transfersStock: false,
  requiresMydataTransmission: true,
  sortCode: 20,
  active: true,
}

/** The server's rows. The POST handler appends, exactly as a real backend would. */
let rows: SalesDocumentTypeView[] = [existing]

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(owner)),
  http.get('http://localhost/api/sales-document-types', () => HttpResponse.json({ items: rows })),
  http.get('http://localhost/api/aade-invoice-types', () => HttpResponse.json({ items: [] })),
  http.post('http://localhost/api/sales-document-types', () => {
    rows = [...rows, created]
    return HttpResponse.json(created, { status: 201 })
  }),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  rows = [existing]
})
afterAll(() => server.close())

describe('the shared query client', () => {
  it('wires a mutation-cache handler that invalidates after every successful write', () => {
    /*
     * ⚠️ Structural, and it is the one that cannot be replaced by a screen test.
     *
     * Every other assertion in this repository would still pass with the handler deleted, because
     * the handler is not in any screen — it is in the client all thirteen of them share. This is the
     * assertion that makes deleting it a red build.
     */
    const client = createQueryClient()

    expect(
      typeof client.getMutationCache().config.onSuccess,
      'the global invalidate-after-write handler is gone — see query-client.tsx. Removing it ' +
        'silently reintroduces the stale-list defect in all thirteen create forms at once, and no ' +
        'screen test can see it.',
    ).toBe('function')
  })

  it('a created row appears in the list without a manual refresh', async () => {
    /*
     * ⚠️ END TO END, THROUGH THE REAL CLIENT — `createQueryClient()`, with its real 30-second
     * `staleTime`. A test client with `staleTime: 0` would refetch on every mount and pass happily
     * against the defect, which is the trap this test exists to avoid falling into.
     *
     * The sequence is the owner's: see the list, add a type, come back. The "come back" is what was
     * broken — inside 30 seconds the cached list was served and the new row was simply absent.
     */
    const client = createQueryClient()
    const user = userEvent.setup()

    const app = (initialEntry: string) =>
      render(
        <AppQueryProvider client={client}>
          <MemoryRouter initialEntries={[initialEntry]}>
            <Routes>
              <Route path="/settings/sales-document-types" element={<SalesDocumentTypesList />} />
              <Route
                path="/settings/sales-document-types/new"
                element={<SalesDocumentTypeCreate />}
              />
              <Route path="/settings/sales-document-types/:id" element={<p>the detail page</p>} />
            </Routes>
          </MemoryRouter>
        </AppQueryProvider>,
      )

    // 1. The list, which populates the cache with one row.
    const list = app('/settings/sales-document-types')
    await screen.findByRole('link', { name: 'Απόδειξη Λιανικής' })
    expect(screen.queryByRole('link', { name: 'Τιμολόγιο Πώλησης' })).not.toBeInTheDocument()
    list.unmount()

    // 2. The create form. Filling it is the whole point — the invalidation has to be driven by a
    //    real mutation rather than by calling the handler directly.
    const form = app('/settings/sales-document-types/new')
    await screen.findByRole('button', { name: 'Create document type' })
    await user.type(screen.getByLabelText('Description'), 'Τιμολόγιο Πώλησης')
    await user.type(screen.getByLabelText('Sort code'), '20')
    await user.click(screen.getAllByRole('button', { name: 'Yes' })[0]!)
    await user.click(screen.getAllByRole('button', { name: 'No' })[1]!)
    await user.click(screen.getAllByRole('button', { name: 'Yes' }).at(-1)!)
    await user.click(screen.getByRole('button', { name: 'Create document type' }))
    await screen.findByText('the detail page')
    form.unmount()

    // 3. Back to the list, well inside the 30-second stale window.
    app('/settings/sales-document-types')

    expect(
      await screen.findByRole('link', { name: 'Τιμολόγιο Πώλησης' }),
      'the list is serving a cached response that predates the create',
    ).toBeInTheDocument()
  })
})
