import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import {
  AccessLevel,
  Section,
  type Me,
  type SalesInvoiceView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
// Imported for its side effect: without it every label renders as its raw key.
import '@/i18n'
import { OWNER_ROLE, aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { SalesInvoiceDetail } from './sales-invoice-detail'
import { SalesInvoicesList } from './sales-invoices-list'

/**
 * The sales invoice screens — **the first in this application over a SERVER-PAGED endpoint**.
 *
 * Most of what is asserted here is asserted nowhere else in the repository, because no earlier list
 * pages on the server: that a `page` block reaches the table, that only the three columns the
 * endpoint declares are sortable, and that the rest are plain text rather than dead buttons.
 *
 * ⚠️ **What a test over `msw` cannot tell you, and this file does not claim:** whether the backend
 * accepts what these screens build. The mock answers whatever it is given. That is F.1's
 * `F5WriteContractIT`, against a real server — and the record form's own body shape is proven there,
 * not here.
 */

const owner: Me = aUser({ id: 1, role: OWNER_ROLE, sections: everySectionAt(AccessLevel.FULL) })

/** A read-only role, to prove the record action is absent rather than present and doomed. */
const viewer: Me = aUser({
  id: 2,
  role: { id: 4, name: 'SALES_VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.SALES, level: AccessLevel.VIEW, available: true }],
})

const line = {
  id: 900,
  lineNumber: 0,
  lineType: 'PRODUCT' as const,
  productId: 41,
  productSku: 'ESP-001',
  quantity: '2.000000',
  unitPrice: { amount: '18.500000', currency: 'EUR' },
  netAmount: { amount: '37.00', currency: 'EUR' },
  vatAmount: { amount: '8.88', currency: 'EUR' },
  vatClassId: 3,
  soldSerialNumbers: [],
  components: [],
  bundle: false,
  exempt: false,
}

const invoice: SalesInvoiceView = {
  id: 500,
  customerId: 7,
  customerName: 'Καφεκοπτεία Σινιόρ',
  channel: 'ECOMMERCE',
  settlementMethod: 'ON_ACCOUNT',
  documentNumber: 'ΑΛΠ-1042',
  invoiceDate: '2026-07-20',
  netTotal: { amount: '37.00', currency: 'EUR' },
  vatTotal: { amount: '8.88', currency: 'EUR' },
  grossTotal: { amount: '45.88', currency: 'EUR' },
  roundingAmount: { amount: '0.00', currency: 'EUR' },
  roundingNeededReview: false,
  journalEntryId: 88,
  // ⚠️ Absent, and that is the permanent correct state in this phase rather than missing data:
  // Novocore never obtains a ΜΑΡΚ, and no route on the surface writes one.
  transmissionStatus: 'UNKNOWN',
  seriesId: 3,
  seriesAbbreviation: 'ΑΛΠW',
  lines: [line],
  inForce: true,
  reversal: false,
  reversed: false,
}

/** A second row with no series — every invoice recorded before R1b looks like this. */
const legacy: SalesInvoiceView = {
  ...invoice,
  id: 501,
  documentNumber: 'TEST-SI-2026-0001',
  seriesAbbreviation: undefined,
  seriesId: undefined,
}

let me: Me = owner

/**
 * Every URL the list asked for, query string included.
 *
 * ⚠️ `trackRequests` records `new URL(request.url).pathname` and therefore **drops the query**, so it
 * structurally cannot answer the two questions this screen most needs asked: whether a date range is
 * sent, and whether sorting goes to the server. It stays for what it is for — proving no writes —
 * and the query is captured here instead.
 */
let listUrls: string[] = []

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/sales-invoices', ({ request }) => {
    listUrls.push(request.url)
    return HttpResponse.json({
      items: [invoice, legacy],
      // The block that makes `DataTable` switch to server paging. No earlier screen sends one.
      page: { page: 0, size: 25, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
    })
  }),
  http.get('http://localhost/api/sales-invoices/500', () => HttpResponse.json(invoice)),
)

const requests = trackRequests(server)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  me = owner
  listUrls = []
  requests.reset()
})
afterAll(() => server.close())

function renderList() {
  return render(
    <AppQueryProvider>
      <MemoryRouter>
        <SalesInvoicesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/sales/invoices/500']}>
        <Routes>
          <Route path="/sales/invoices/:id" element={<SalesInvoiceDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

describe('the sales invoice list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByText('ΑΛΠ-1042')
    requests.expectNoWrites()
  })

  it('asks for a date range, because the endpoint refuses a call without one', async () => {
    renderList()
    await screen.findByText('ΑΛΠ-1042')

    // The screen cannot open on "everything": GET /api/sales-invoices answers 400 to a call with
    // neither a range nor a customerId. So the default is a precondition, not a convenience.
    expect(listUrls, 'the list must have asked for something').not.toHaveLength(0)
    expect(listUrls[0]).toContain('from=')
    expect(listUrls[0]).toContain('to=')
    // 1 January of the current year — the decided default.
    expect(listUrls[0]).toContain(`from=${new Date().getFullYear()}-01-01`)
  })

  it('sorts through the request, on the three keys the endpoint declares', async () => {
    const user = userEvent.setup()
    renderList()
    await screen.findByText('ΑΛΠ-1042')

    await user.click(screen.getByRole('button', { name: /Number/i }))

    // ⚠️ The assertion that matters is `sort=` reaching the SERVER. A server-paged list must never
    // sort in the browser: ordering the rows in hand and presenting them as the order of the whole
    // table is convincing and wrong.
    await waitFor(() => {
      expect(listUrls.some((url) => url.includes('sort=DOCUMENT_NUMBER'))).toBe(true)
    })
  })

  it('offers no sort on a column the endpoint cannot order by', async () => {
    renderList()
    await screen.findByText('ΑΛΠ-1042')

    // Customer is deliberately not a sort key — the endpoint filters by customerId instead, and a
    // CUSTOMER_NAME sort would drag the collation obligation in with it. It must render as plain
    // text, NOT as a disabled button: a control that is present and inert invites clicking.
    expect(screen.queryByRole('button', { name: /Customer/i })).toBeNull()
    expect(screen.getByText('Customer')).toBeInTheDocument()
  })

  it('shows an invoice with no series as unset rather than dropping it', async () => {
    renderList()
    // Every invoice recorded before R1b has a null series. It has to appear in its own list.
    expect(await screen.findByText('TEST-SI-2026-0001')).toBeInTheDocument()
  })

  it('hides the record action from a role that may only view', async () => {
    me = viewer
    renderList()
    await screen.findByText('ΑΛΠ-1042')
    expect(screen.queryByRole('link', { name: /Record invoice/i })).toBeNull()
  })
})

describe('the sales invoice detail', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })
    requests.expectNoWrites()
  })

  /**
   * ⚠️ The absence test this screen most needs.
   *
   * A posted document is immutable (ADR 0006) and the backend has no route to change one — measured
   * at the wire, `PATCH` answers 404 and `DELETE` answers 405. So there must be **no Edit control at
   * all**, for a FULL-access role. Not a disabled one: `editable: false` means *"your role may not"*,
   * which would tell an owner something false.
   */
  it('offers no Edit control anywhere, for a FULL-access role', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })
    expect(screen.queryAllByRole('button', { name: /^Edit /i })).toHaveLength(0)
  })

  it('says why the statutory identifiers are empty, rather than showing bare dashes', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })
    expect(screen.getByText(/Novocore never obtains a ΜΑΡΚ/i)).toBeInTheDocument()
  })

  it('labels the channel as coming from the series', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })
    // One fact, not two. A reader who saw them as independent would ask why they cannot be edited
    // separately, and the answer is that the channel IS the series'.
    expect(screen.getByText(/Comes from the series/i)).toBeInTheDocument()
  })

  it('will not send a reversal without a reason', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: /ΑΛΠ-1042/ })

    await user.click(screen.getByRole('button', { name: 'Reverse' }))

    // `ReversalCommand.reason` is guarded by Required.text, and the record argues why: a reversal
    // that says nothing about why leaves the ledger internally consistent and unexplainable.
    expect(screen.getByRole('button', { name: /Reverse this invoice/i })).toBeDisabled()
    requests.expectNoWrites()
  })
})
