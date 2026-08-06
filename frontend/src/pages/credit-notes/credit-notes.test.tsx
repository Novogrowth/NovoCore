import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import {
  AccessLevel,
  Section,
  type CreditNotePreview,
  type CreditNoteView,
  type Me,
  type SalesInvoiceView,
} from '@/api/generated/model'
import { AppQueryProvider } from '@/auth/query-client'
// Imported for its side effect: without it every label renders as its raw key.
import '@/i18n'
import { OWNER_ROLE, aUser, everySectionAt } from '@/test/fixtures'
import { trackRequests } from '@/test/requests'

import { CreditNoteDetail } from './credit-note-detail'
import { CreditNoteRecord } from './credit-note-record'
import { CreditNotesList } from './credit-notes-list'

/**
 * The credit note screens.
 *
 * <h2>⭐ The contrast with the sales invoice screens is what this file exists to pin</h2>
 *
 * They look almost identical and behave differently in one important way: `GET /api/credit-notes`
 * is **unpaged and declares no sort constants**, so this list sorts **in the browser**, while the
 * invoice list sorts **through the request**. A test that only proved "clicking a header reorders
 * the rows" would pass on both and distinguish nothing — so the assertion here is that **no `sort=`
 * reaches the server**, which is the half that could regress silently the day somebody copies a
 * `meta.sortKey` across from the neighbouring file.
 *
 * <h2>⚠️ The record form is covered THINLY, on purpose</h2>
 *
 * Owner decision, 2026-08-05: nobody will ever type a credit note, so that form is a test harness
 * for the recording path and the polished version was explicitly ruled out. What is asserted about
 * it is what would be a defect in the **path** — that the derived fields are shown as derived rather
 * than as empty inputs, and that the preview-then-accept sequence holds. Not its ergonomics.
 */

const owner: Me = aUser({ id: 1, role: OWNER_ROLE, sections: everySectionAt(AccessLevel.FULL) })

const viewer: Me = aUser({
  id: 2,
  role: { id: 4, name: 'SALES_VIEWER', fullAccess: false, systemRole: false },
  sections: [{ section: Section.SALES, level: AccessLevel.VIEW, available: true }],
})

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
  transmissionStatus: 'UNKNOWN',
  seriesId: 3,
  seriesAbbreviation: 'ΑΛΠW',
  inForce: true,
  reversal: false,
  reversed: false,
  lines: [
    {
      id: 900,
      lineNumber: 0,
      lineType: 'PRODUCT',
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
    },
  ],
}

/** Goods genuinely came back on this one. */
const returned: CreditNoteView = {
  id: 600,
  salesInvoiceId: 500,
  salesInvoiceNumber: 'ΑΛΠ-1042',
  customerId: 7,
  customerName: 'Καφεκοπτεία Σινιόρ',
  channel: 'ECOMMERCE',
  settlementMethod: 'ON_ACCOUNT',
  documentNumber: 'ΠΤ-2001',
  creditNoteDate: '2026-07-25',
  netTotal: { amount: '18.50', currency: 'EUR' },
  vatTotal: { amount: '4.44', currency: 'EUR' },
  grossTotal: { amount: '22.94', currency: 'EUR' },
  roundingAmount: { amount: '0.00', currency: 'EUR' },
  roundingNeededReview: false,
  journalEntryId: 91,
  inForce: true,
  reversal: false,
  reversed: false,
  /*
   * ⚠️ TWO lines, one of each — and that is the fixture doing work rather than being thorough. A
   * note crediting several lines can genuinely have goods back on one and not the other (a return
   * plus a price correction on the same document), so a fixture with one line could not tell a
   * screen that names both states from one that renders a tick and a blank.
   */
  lines: [
    {
      id: 950,
      lineNumber: 0,
      salesInvoiceLineId: 900,
      productId: 41,
      productSku: 'ESP-001',
      quantity: '1.000000',
      unitPrice: { amount: '18.500000', currency: 'EUR' },
      netAmount: { amount: '18.50', currency: 'EUR' },
      vatAmount: { amount: '4.44', currency: 'EUR' },
      vatClassId: 3,
      stockReturned: true,
    },
    {
      id: 951,
      lineNumber: 1,
      salesInvoiceLineId: 901,
      productId: 42,
      productSku: 'FIL-002',
      quantity: '1.000000',
      unitPrice: { amount: '2.000000', currency: 'EUR' },
      netAmount: { amount: '2.00', currency: 'EUR' },
      vatAmount: { amount: '0.48', currency: 'EUR' },
      vatClassId: 3,
      description: 'Overcharged by 2.00',
      // A price correction: money back, nothing came back.
      stockReturned: false,
    },
  ],
}

/** A price correction: money back, nothing came back. Sorts BEFORE the other under `compareText`. */
const priceFix: CreditNoteView = {
  ...returned,
  id: 601,
  documentNumber: 'ΑΠΤ-1',
  lines: [{ ...returned.lines[0]!, id: 951, stockReturned: false }],
}

const settledPreview: CreditNotePreview = {
  lines: [],
  net: { amount: '18.50', currency: 'EUR' },
  vat: { amount: '4.44', currency: 'EUR' },
  gross: { amount: '22.94', currency: 'EUR' },
  roundingDifference: { amount: '0.00', currency: 'EUR' },
  roundingThreshold: { amount: '0.03', currency: 'EUR' },
  roundingNeedsAcceptance: false,
  payable: { amount: '22.94', currency: 'EUR' },
}

const needsAcceptancePreview: CreditNotePreview = {
  ...settledPreview,
  statedTotal: { amount: '23.10', currency: 'EUR' },
  roundingDifference: { amount: '0.16', currency: 'EUR' },
  roundingNeedsAcceptance: true,
  payable: { amount: '23.10', currency: 'EUR' },
}

let me: Me = owner

/** Every URL the note list asked for — `trackRequests` drops the query string. */
let listUrls: string[] = []

const server = setupServer(
  http.get('http://localhost/api/me', () => HttpResponse.json(me)),
  http.get('http://localhost/api/credit-notes', ({ request }) => {
    listUrls.push(request.url)
    // ⚠️ NO `page` block. That absence is what makes `DataTable` page and sort in the browser, and
    // it is the endpoint's real shape — `{paged: false, sorts: []}` in the generated map.
    return HttpResponse.json({ items: [returned, priceFix] })
  }),
  http.get('http://localhost/api/credit-notes/600', () => HttpResponse.json(returned)),
  http.get('http://localhost/api/sales-invoices', () => HttpResponse.json({ items: [invoice] })),
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
        <CreditNotesList />
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderDetail() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/sales/credit-notes/600']}>
        <Routes>
          <Route path="/sales/credit-notes/:id" element={<CreditNoteDetail />} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

function renderRecord() {
  return render(
    <AppQueryProvider>
      <MemoryRouter initialEntries={['/sales/credit-notes/new']}>
        <Routes>
          <Route path="/sales/credit-notes/new" element={<CreditNoteRecord />} />
          <Route path="/sales/credit-notes/:id" element={<p>recorded</p>} />
        </Routes>
      </MemoryRouter>
    </AppQueryProvider>,
  )
}

async function chooseFirstOption(user: ReturnType<typeof userEvent.setup>, label: string) {
  await user.click(screen.getByLabelText(label))
  const options = await screen.findAllByRole('option')
  await user.click(options[0]!)
}

describe('the credit note list', () => {
  it('sends no write merely by rendering', async () => {
    renderList()
    await screen.findByText('ΠΤ-2001')
    requests.expectNoWrites()
  })

  it('asks for a date range, because the endpoint refuses a call without one', async () => {
    renderList()
    await screen.findByText('ΠΤ-2001')

    // `SalesController.notes` calls `requireRange(from)` when neither customerId nor salesInvoiceId
    // is given, and answers 400. Same precondition as the invoice list, reached independently.
    expect(listUrls).not.toHaveLength(0)
    expect(listUrls[0]).toContain(`from=${new Date().getFullYear()}-01-01`)
    expect(listUrls[0]).toContain('to=')
  })

  /**
   * ⚠️ The assertion that distinguishes this screen from its neighbour.
   *
   * The invoice list must sort through the request; this one must NOT, because the endpoint returns
   * every row and declares no sort constants. Ordering in the browser here answers exactly the
   * question it appears to answer.
   *
   * ⚠️ **This comment used to end "the failure this pins is somebody copying a `meta.sortKey` across
   * from `sales-invoice-columns.tsx`". That was false, and it was caught by injecting exactly that
   * defect and watching all 15 tests pass.** `canSortColumn` returns `true` unconditionally when the
   * list is not server-paged, so a stray `sortKey` on an unpaged list is **inert**: nothing reads
   * it, no request carries it, and there is no behaviour for a test to see. It would begin to matter
   * only the day this endpoint gains paging — which is the standing obligation
   * `frontend/README.md` already counts, and is not a thing a screen test can hold.
   *
   * **What this test does pin, which is worth pinning:** the rows reorder in hand, and **no `sort=`
   * ever reaches the server** — so if this list is ever wired to send one, it goes red.
   */
  it('sorts in the browser and sends no sort to the server', async () => {
    const user = userEvent.setup()
    renderList()
    await screen.findByText('ΠΤ-2001')

    const before = listUrls.length
    await user.click(screen.getByRole('button', { name: /Number/i }))

    // Reordered in hand: ΑΠΤ-1 sorts before ΠΤ-2001 under the Greek collator.
    await waitFor(() => {
      const rows = screen.getAllByRole('row')
      expect(within(rows[1]!).getByText('ΑΠΤ-1')).toBeInTheDocument()
    })
    // …and nothing was asked of the server: no new request, and no `sort=` in any of them.
    expect(listUrls).toHaveLength(before)
    expect(listUrls.some((url) => url.includes('sort='))).toBe(false)
  })

  it('links each note to the invoice it credits', async () => {
    renderList()
    await screen.findByText('ΠΤ-2001')
    /*
     * ⭐ The one column a credit note has that an invoice does not. A note only exists against a
     * sale — `salesInvoiceId` is mandatory on `NewCreditNote` — so reaching the sale is not a
     * convenience, it is how the document is read at all.
     */
    const link = screen.getAllByRole('link', { name: 'ΑΛΠ-1042' })[0]!
    expect(link).toHaveAttribute('href', '/sales/invoices/500')
  })

  it('hides the record action from a role that may only view', async () => {
    me = viewer
    renderList()
    await screen.findByText('ΠΤ-2001')
    expect(screen.queryByRole('link', { name: /Record credit note/i })).toBeNull()
  })
})

describe('the credit note detail', () => {
  it('sends no write merely by rendering', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΠΤ-2001/ })
    requests.expectNoWrites()
  })

  it('offers no Edit control anywhere, for a FULL-access role', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΠΤ-2001/ })
    /*
     * Immutable for two reasons, and the second is the one specific to sales documents: the note
     * exists outside Novocore. A disabled control would say "your role may not", which is false.
     */
    expect(screen.queryAllByRole('button', { name: /^Edit /i })).toHaveLength(0)
  })

  it('labels the customer, channel and settlement method as coming from the invoice', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΠΤ-2001/ })
    /*
     * `NewCreditNote` has no component for any of the three. A reader who saw them as independently
     * settable would ask why they cannot be edited — and the answer is that they are the invoice's.
     */
    expect(screen.getAllByText('From the invoice.').length).toBeGreaterThanOrEqual(3)
  })

  it('says per line whether goods came back, in words rather than as a blank', async () => {
    renderDetail()
    await screen.findByRole('heading', { name: /ΠΤ-2001/ })
    /*
     * ⚠️ `stockReturned` decides whether inventory moves, and it is per LINE: a note crediting three
     * lines can have goods back on one. "Nothing came back" is a positive statement about a price
     * correction, so both states are named — an unticked box says the same thing as a box nobody
     * looked at.
     */
    expect(screen.getByText('Goods returned')).toBeInTheDocument()
    expect(screen.getByText('Nothing came back')).toBeInTheDocument()
  })

  it('will not send a reversal without a reason', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: /ΠΤ-2001/ })

    await user.click(screen.getByRole('button', { name: 'Reverse' }))

    expect(screen.getByRole('button', { name: /Reverse this credit note/i })).toBeDisabled()
    requests.expectNoWrites()
  })
})

describe('the credit note record form', () => {
  it('sends no write merely by rendering', async () => {
    renderRecord()
    await screen.findByRole('heading', { name: 'Record credit note' })
    requests.expectNoWrites()
  })

  it('fills the lines from the chosen invoice and shows what is derived from it', async () => {
    const user = userEvent.setup()
    renderRecord()
    await screen.findByRole('heading', { name: 'Record credit note' })

    await chooseFirstOption(user, 'Invoice credited')

    // The invoice's line arrives as a draft to correct, rather than as a blank line to key.
    expect(await screen.findByText('ESP-001')).toBeInTheDocument()
    /*
     * ⚠️ Derived and SHOWN as derived. Customer, channel, settlement method and series are facts
     * about the sale being credited; `NewCreditNote` carries none of them, so an empty input for
     * any of them would invite an answer the server discards.
     */
    expect(screen.getByText('Καφεκοπτεία Σινιόρ')).toBeInTheDocument()
    expect(screen.getByText('E-commerce')).toBeInTheDocument()
    expect(screen.getByText(/come from the invoice being credited/i)).toBeInTheDocument()
    requests.expectNoWrites()
  })

  it('shows the acceptance control only when the preview asks, and blocks Record until it is filled', async () => {
    server.use(
      http.post('http://localhost/api/credit-notes/preview', () =>
        HttpResponse.json(needsAcceptancePreview),
      ),
    )

    const user = userEvent.setup()
    renderRecord()
    await screen.findByRole('heading', { name: 'Record credit note' })

    await chooseFirstOption(user, 'Invoice credited')
    await screen.findByText('ESP-001')
    await user.type(screen.getByLabelText('Document number'), 'ΠΤ-2002')

    expect(screen.queryByLabelText('Accepted by')).toBeNull()

    await user.click(screen.getByRole('button', { name: 'Preview' }))

    expect(await screen.findByLabelText('Accepted by')).toBeInTheDocument()
    // The form is otherwise complete, so the disablement is the acceptance and nothing else.
    expect(screen.getByRole('button', { name: 'Record credit note' })).toBeDisabled()

    await user.type(screen.getByLabelText('Accepted by'), 'Kostas')
    expect(screen.getByRole('button', { name: 'Record credit note' })).toBeEnabled()
  })

  it('sends the invoice and its line, and navigates to the recorded note', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.post('http://localhost/api/credit-notes/preview', () =>
        HttpResponse.json(settledPreview),
      ),
      http.post('http://localhost/api/credit-notes', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...returned, id: 777 }, { status: 201 })
      }),
    )

    const user = userEvent.setup()
    renderRecord()
    await screen.findByRole('heading', { name: 'Record credit note' })

    await chooseFirstOption(user, 'Invoice credited')
    await screen.findByText('ESP-001')
    await user.type(screen.getByLabelText('Document number'), 'ΠΤ-2002')

    await user.click(screen.getByRole('button', { name: 'Record credit note' }))
    await waitFor(() => expect(body).toBeDefined())

    expect(body!.salesInvoiceId).toBe(500)
    // Each line names the INVOICE LINE it credits, never a product — the form could not offer free
    // line entry even if somebody wanted one.
    expect(body!.lines).toEqual([
      {
        salesInvoiceLineId: 900,
        quantity: '2.000000',
        unitPrice: { amount: '18.500000', currency: 'EUR' },
        // Not defaulted to true: only the operator knows whether this credits a return or a price.
        stockReturned: false,
      },
    ])
    // The three fields the note takes from the invoice are absent from the body entirely.
    expect(Object.hasOwn(body!, 'customerId')).toBe(false)
    expect(Object.hasOwn(body!, 'channel')).toBe(false)
    expect(Object.hasOwn(body!, 'settlementMethod')).toBe(false)

    expect(await screen.findByText('recorded')).toBeInTheDocument()
  })

  it('renders the server’s refusal instead of failing silently', async () => {
    server.use(
      http.post('http://localhost/api/credit-notes', () =>
        HttpResponse.json(
          {
            status: 422,
            detail: 'Line 1 credits 3 but only 2 were sold and 0 already credited.',
          },
          { status: 422 },
        ),
      ),
    )

    const user = userEvent.setup()
    renderRecord()
    await screen.findByRole('heading', { name: 'Record credit note' })

    await chooseFirstOption(user, 'Invoice credited')
    await screen.findByText('ESP-001')
    await user.type(screen.getByLabelText('Document number'), 'ΠΤ-2002')
    await user.click(screen.getByRole('button', { name: 'Record credit note' }))

    // Crediting more than was sold is cumulative across notes, so this refusal is one an operator
    // will genuinely meet — and it is only useful if the sentence reaches the screen.
    expect(await screen.findByText(/only 2 were sold/i)).toBeInTheDocument()
  })
})
