import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import {
  AadeInvoiceGroup,
  AccessLevel,
  type AadeInvoiceTypeView,
  type Me,
  type PurchaseDocumentTypeView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
import '@/i18n'
import { aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import {
  PurchaseDocumentTypeCreate,
  PurchaseDocumentTypeDetail,
  PurchaseDocumentTypesList,
} from './purchase-document-types'

/**
 * Purchase document types — the sales screen, with the AADE picker asking for the other side.
 *
 * ⚠️ **The one assertion worth having here is the `side=RECEIVED` request**, because it is the
 * only visible difference and it is enforced by the backend: `PurchaseDocumentTypeServiceImpl`
 * refuses a purchase type naming an issuer-side code, so a picker offering all 55 would be mostly
 * certain refusals.
 *
 * ⚠️ **R2 changes no purchase-document-type behaviour — that is F6's.** This is a screen over
 * routes R1a already shipped.
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
  aadeInvoiceTypeId: 41,
  aadeInvoiceTypeCode: '14.1',
  sortCode: 30,
  active: true,
}

const receivedCodes: AadeInvoiceTypeView[] = [
  {
    id: 41,
    code: '14.1',
    description: 'Τιμολόγιο / Ενδοκοινοτικές Αποκτήσεις',
    group: AadeInvoiceGroup.RECIPIENT_MATCHED,
    active: true,
  },
]

let me: Me = owner
/** Every `side` the picker asked for, so the narrowing can be asserted rather than assumed. */
let sidesRequested: (string | null)[] = []

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/purchase-document-types', () =>
    HttpResponse.json({ items: [supplierInvoice] }),
  ),
  http.get('http://localhost/api/purchase-document-types/1', () =>
    HttpResponse.json(supplierInvoice),
  ),
  http.get('http://localhost/api/aade-invoice-types', ({ request }) => {
    sidesRequested.push(new URL(request.url).searchParams.get('side'))
    return HttpResponse.json({ items: receivedCodes })
  }),
  http.post('http://localhost/api/purchase-document-types', () =>
    HttpResponse.json({ ...supplierInvoice, id: 30 }, { status: 201 }),
  ),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  me = owner
  sidesRequested = []
  requests.reset()
})
afterAll(() => server.close())

function renderList() {
  return render(
    <AppQueryProvider>
      <MemoryRouter>
        <PurchaseDocumentTypesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/purchase-document-types/1']}>
        <Routes>
          <Route
            path="/settings/purchase-document-types/:id"
            element={<PurchaseDocumentTypeDetail />}
          />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderCreate() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/settings/purchase-document-types/new']}>
        <Routes>
          <Route
            path="/settings/purchase-document-types/new"
            element={<PurchaseDocumentTypeCreate />}
          />
          <Route path="/settings/purchase-document-types/:id" element={<p>created</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the purchase document type list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByRole('link', { name: 'Τιμολόγιο Αγοράς' })
    requests.expectNoWrites()
  })
})

describe('one purchase document type', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: 'Τιμολόγιο Αγοράς' })
    requests.expectNoWrites()
  })

  it('⚠️ asks the AADE codification for RECEIVED, never the whole 55', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: 'Τιμολόγιο Αγοράς' })
    // The backend refuses an issuer-side code on a purchase type. Offering all 55 would build a
    // picker most of whose options answer 422.
    expect(sidesRequested).toContain('RECEIVED')
    expect(sidesRequested).not.toContain('ISSUED')
  })
})

describe('creating a purchase document type', () => {
  it('sends no write merely by rendering', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create document type' })
    requests.expectNoWrites()
  })

  it('⚠️ asks for RECEIVED on the create form too', async () => {
    renderCreate()
    await screen.findByRole('button', { name: 'Create document type' })
    // The submit button needs no data, so it renders before the picker's query fires. Asserting
    // immediately would race it — and would pass against a screen that asked for nothing at all.
    await waitFor(() => expect(sidesRequested).toContain('RECEIVED'))
    expect(sidesRequested).not.toContain('ISSUED')
  })
})
