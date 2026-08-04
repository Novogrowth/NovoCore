import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import {
  AccessLevel,
  type Me,
  type PurchaseDocumentSeriesView,
  type PurchaseDocumentTypeView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import {
  PurchaseDocumentSeriesCreate,
  PurchaseDocumentSeriesDetail,
  PurchaseDocumentSeriesList,
} from './purchase-document-series'

/**
 * Purchase document series.
 *
 * ⚠️ **The load-bearing tests here are ABSENCE tests about channel**, and they exist because
 * *"there is no route"* and *"the route silently does nothing"* look identical to an operator.
 * `purchase_document_series` has no channel column, `PurchaseDocumentSeriesView` has no channel
 * component and no route accepts one — channel is where a *sale* came from and never applies to a
 * purchase. R1a wrote a backend test asserting the column's absence for the same reason; this is
 * that argument one layer up.
 *
 * ⚠️ **The R2 freeze is unreachable on this side and that is asserted, not assumed.** `inUse` is
 * always `false`: nothing in the schema can reference a purchase series until F6.
 * `DocumentReferenceGraphIT` is what turns that day into a red build.
 */

const owner: Me = aUser({
  id: 1,
  role: { id: 1, name: 'OWNER', fullAccess: true, systemRole: true },
  sections: everySectionAt(AccessLevel.FULL),
})

const supplierInvoice: PurchaseDocumentTypeView = {
  id: 1,
  description: 'Τιμολόγιο Αγοράς',
  affectsStock: true,
  transfersStock: false,
  requiresMydataTransmission: true,
  sortCode: 20,
  active: true,
}

const series: PurchaseDocumentSeriesView = {
  id: 20,
  abbreviation: 'ΤΠΥ',
  description: 'Supplier invoice series',
  documentTypeId: 1,
  documentTypeDescription: 'Τιμολόγιο Αγοράς',
  getsMark: false,
  // ⚠️ Always false today — see the file note. Nothing can make it true before F6.
  inUse: false,
  sortCode: 10,
  active: true,
}

let me: Me = owner

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/purchase-document-series', () =>
    HttpResponse.json({ items: [series] }),
  ),
  http.get('http://localhost/api/purchase-document-series/20', () => HttpResponse.json(series)),
  http.get('http://localhost/api/purchase-document-types', () =>
    HttpResponse.json({ items: [supplierInvoice] }),
  ),
  http.post('http://localhost/api/purchase-document-series', () =>
    HttpResponse.json({ ...series, id: 50 }, { status: 201 }),
  ),
  http.patch('http://localhost/api/purchase-document-series/20/abbreviation', () =>
    HttpResponse.json(series),
  ),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  me = owner
  requests.reset()
})
afterAll(() => server.close())

function renderList() {
  return render(
    <AppQueryProvider>
      <MemoryRouter>
        <PurchaseDocumentSeriesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/purchase-document-series/20']}>
        <Routes>
          <Route
            path="/settings/purchase-document-series/:id"
            element={<PurchaseDocumentSeriesDetail />}
          />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/purchase-document-series/new']}>
        <Routes>
          <Route
            path="/settings/purchase-document-series/new"
            element={<PurchaseDocumentSeriesCreate />}
          />
          <Route path="/settings/purchase-document-series/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the purchase document series list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ΤΠΥ' })
    requests.expectNoWrites()
  })

  it('⚠️ has NO channel column — channel is where a SALE came from', async () => {
    renderList()
    await screen.findByRole('link', { name: 'ΤΠΥ' })
    // The sales list has one and this must not, ever. A purchase series carrying ECOMMERCE would
    // be storable, meaningless and indistinguishable from data.
    expect(screen.queryByText('Sales channel')).not.toBeInTheDocument()
    expect(screen.queryByText('Not a sales channel')).not.toBeInTheDocument()
  })
})

describe('one purchase document series', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: 'ΤΠΥ' })
    requests.expectNoWrites()
  })

  it('⚠️ offers NO channel control', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: 'ΤΠΥ' })
    expect(screen.queryByRole('button', { name: 'Edit Sales channel' })).not.toBeInTheDocument()
  })

  it('offers the three correctable fields — the freeze cannot fire on this side', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: 'ΤΠΥ' })
    // ⚠️ Enabled because `inUse` is false, and it is false BY CONSTRUCTION: no purchase document
    // carries a series before F6. The correction path exists anyway, because the abbreviation is
    // typed by hand now.
    expect(screen.getByRole('button', { name: 'Edit Abbreviation' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Edit Document type' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Edit ΜΑΡΚ' })).toBeEnabled()
  })
})

describe('creating a purchase document series', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create series' })
    requests.expectNoWrites()
  })

  it('⚠️ offers no channel field on the create form either', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create series' })
    expect(screen.queryByLabelText('Sales channel')).not.toBeInTheDocument()
  })
})
